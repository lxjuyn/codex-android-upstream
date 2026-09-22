package com.cy.codex

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cy.codex.chatwidget.AgentNotice
import com.cy.codex.chatwidget.ApprovalNoticeKind
import com.cy.codex.diff.TurnDiffAccumulator
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.FileChangeItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.PlanItem
import com.cy.codex.protocol.protocol.item.ReasoningItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.DiagnosticSeverity
import com.cy.codex.protocol.protocol.v2.FileUpdateChange
import com.cy.codex.protocol.protocol.v2.QueuedSubmission
import com.cy.codex.protocol.protocol.v2.SortDirection
import com.cy.codex.protocol.protocol.v2.ThreadResumeInitialTurnsPageParams
import com.cy.codex.protocol.protocol.v2.ThreadResumeParams
import com.cy.codex.protocol.protocol.v2.ThreadSettingsUpdateParams
import com.cy.codex.protocol.protocol.v2.ThreadReadResponse
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.protocol.protocol.v2.ThreadTurnsListParams
import com.cy.codex.protocol.protocol.v2.TurnItemsView
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.UserInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** The wire's "no tier preference" value (`SERVICE_TIER_DEFAULT_REQUEST_VALUE` upstream). */
private const val ServiceTierDefault = "default"

/** Turns in the bounded first screen `thread/resume` returns; older turns stay a click away. */
private const val FirstScreenTurnLimit = 20

/** Turns per "load earlier" page; the server clamps whatever the client asks for. */
private const val EarlierPageSize = 25

/**
 * The reducer that owns one open thread.
 *
 * Mirrors `codex-rs/tui/src/chatwidget.rs`. Everything the user does to a session goes through
 * [action]; everything the server says arrives through [SessionEvent] and lands in [state]. The
 * widget only sends commands through the application server client.
 */
class ChatWidget(
    private val client: AppServerClient,
    private val scope: CoroutineScope,
    val state: SessionState = SessionState(),
) {
    /**
     * Server requests waiting for a decision, oldest first.
     *
     * Snapshot-backed on purpose. The dialog and the transcript are both composed from this queue,
     * and a plain deque gives a composable that reads it nothing to observe: the request would sit
     * here until some *unrelated* state change (opening the status panel, a streaming delta) forced
     * a recomposition. That is exactly how an approval used to disappear into a blocked turn.
     */
    private val pendingApprovals = mutableStateListOf<ApprovalRequest>()

    /**
     * Notices the app turns into system notifications.
     *
     * The widget knows *when* something happened (a turn ended, an approval became blocking) but
     * has no Context, so it emits a structured event and [CodexApp] formats and posts it. A small
     * buffer with `tryEmit` on purpose: an alert is best-effort and must never suspend a reducer.
     */
    private val _notices = MutableSharedFlow<AgentNotice>(extraBufferCapacity = 32)
    val notices: SharedFlow<AgentNotice> = _notices.asSharedFlow()

    /** Incremented on every reset so a stale collection job can recognise itself. */
    private var subscription: Job? = null
    private var loadJob: Job? = null
    private var loadVersion = 0
    private var eventRevision = 0L

    /**
     * Turns that have already completed.
     *
     * Used to drop approvals that arrive late: a request can be emitted just as its turn finishes,
     * and a card for a finished turn can never be answered meaningfully. Bounded because a session
     * only ever needs the recent past.
     */
    private val finishedTurns = ArrayDeque<String>()

    /**
     * The accumulating `turn/diff/updated` payload of the current turn.
     *
     * The server re-sends the whole diff on every notification, so the accumulator re-parses only
     * the appended tail and keeps the parsed `FileDiff` of every unchanged file. Reset wherever the
     * turn or the thread changes, so a later turn never extends the previous turn's payload.
     */
    private val turnDiff = TurnDiffAccumulator()

    /**
     * Patch content for a file-change item, as last announced by the stream.
     *
     * `item/fileChange/requestApproval` identifies the item but carries no diff, and
     * `item/fileChange/patchUpdated` can arrive before `item/started` — so this is where the two
     * are matched up. The item wins when it is present because it is the authoritative copy;
     * upstream recovers the same way in `tui/src/app/file_change_approvals.rs`.
     */
    private val patchChanges = mutableStateMapOf<String, List<FileUpdateChange>>()

    /** The patch under review for [itemId], or empty when nothing has arrived yet. */
    fun fileChangeChanges(itemId: String): List<FileUpdateChange> =
        (state.item(itemId) as? FileChangeItem)?.changes?.takeIf { it.isNotEmpty() }
            ?: patchChanges[itemId].orEmpty()

    /**
     * Streaming deltas waiting for the next commit tick.
     *
     * A server can emit a delta per token, and committing each one recomposes the transcript row
     * and the tail block with it. Deltas are therefore accumulated here and applied together at
     * [Motion.StreamCommitIntervalMs], which is the cadence the transcript renders at; an item that
     * completes flushes first, so nothing is lost at the end of a message.
     */
    private enum class DeltaKind { AgentMessage, Plan, Reasoning, CommandOutput, McpProgress }

    /**
     * One item's pending deltas, in arrival order.
     *
     * A batch is not one string per item: `aggregatedOutput` and a reasoning summary are appended
     * in arrival order across kinds, so a command's stdout and its terminal interaction must replay
     * in the order the server sent them. Adjacent deltas of the same kind are concatenated; a kind
     * change starts a new chunk.
     */
    private class PendingDeltas {
        val chunks = mutableListOf<Pair<DeltaKind, StringBuilder>>()

        fun append(kind: DeltaKind, delta: String) {
            val last = chunks.lastOrNull()
            if (last != null && last.first == kind) last.second.append(delta)
            else chunks += kind to StringBuilder(delta)
        }
    }

    private val pendingDeltas = linkedMapOf<String, PendingDeltas>()
    private var deltaFlushJob: Job? = null

    /**
     * A bounded, per-thread queue of the notifications worth replaying on switch.
     *
     * Bounded twice over: per thread so one noisy agent cannot grow without limit, and by thread
     * count so a session that spawns many agents keeps only the most recently active ones. The
     * oldest thread is evicted first, and an evicted thread simply opens without replay — the same
     * behaviour as before the buffer existed, never a wrong transcript.
     */
    private class ForeignEventBuffer(
        private val perThreadCapacity: Int = 256,
        private val maxThreads: Int = 8,
    ) {
        private val threads = LinkedHashMap<String, ArrayDeque<AppServerEvent>>()

        fun record(threadId: String, event: AppServerEvent) {
            if (!replayable(event)) return
            // Re-insert so the eviction order is least-recently-active, not least-recently-created.
            val queued = threads.remove(threadId) ?: ArrayDeque()
            threads[threadId] = queued
            queued.addLast(event)
            while (queued.size > perThreadCapacity) queued.removeFirst()
            while (threads.size > maxThreads) threads.remove(threads.keys.first())
        }

        fun drain(threadId: String): List<AppServerEvent> = threads.remove(threadId)?.toList().orEmpty()

        private fun replayable(event: AppServerEvent): Boolean = when (event) {
            // A completed item carries its full body, so replaying it cannot duplicate anything:
            // upsert by id replaces the snapshot copy with the same immutable content.
            is AppServerEvent.ItemCompleted,
            is AppServerEvent.TurnStarted,
            is AppServerEvent.TurnCompleted,
            is AppServerEvent.ErrorEvent,
            is AppServerEvent.WarningEvent,
            is AppServerEvent.GuardianWarningEvent,
            is AppServerEvent.ThreadClosed,
            -> true

            else -> false
        }
    }

    /** Turns that already produced their one safety-buffering notice, bounded like [finishedTurns]. */
    private val safetyBufferedTurns = LinkedHashSet<String>()

    /** Threads with an automatic title generation in flight. */
    private val titleRequests = mutableSetOf<String>()

    /** The manual recap request, if one is running; a second `/recap` is ignored. */
    private var recapJob: Job? = null

    /**
     * Automatic recap bookkeeping, mirroring `app/recap.rs`.
     *
     * Automatic recaps are generated silently once the app has been in the background for 30
     * minutes with enough completed turns; `/recap` still runs on demand.
     */
    private val recap = com.cy.codex.app.RecapScheduler()

    /** The background git/PR probe for the status card. */
    private var gitSummaryJob: Job? = null

    /**
     * When the composer last changed, on a monotonic clock.
     *
     * An approval that lands mid-sentence must not take the keyboard away, so a request waits for
     * typing to be idle for [ApprovalTypingIdleDelayMs] before the dialog appears — the same
     * one-second gate upstream keeps in `tui/src/bottom_pane/mod.rs`. Monotonic because a wall-clock
     * adjustment must not turn a live keystroke into an old one; zero means "nothing typed yet".
     */
    private var composerActiveAtMs = 0L

    /** Cancels the pending promotion when a new request or a keystroke restarts the idle window. */
    private var promotionJob: Job? = null

    /**
     * Requests from a thread other than the open one.
     *
     * They cannot be answered from this transcript — the dialog belongs to the thread on screen —
     * so they are kept apart and surfaced as a banner that switches threads. Upstream lists them
     * above the composer the same way (`bottom_pane/pending_thread_approvals.rs`).
     */
    private val otherApprovals = mutableStateListOf<ApprovalRequest>()

    /**
     * Replayable notifications from threads that are not on screen.
     *
     * The server keeps streaming while the user reads another thread, and only a snapshot is read
     * on switch: transient diagnostics, turn boundaries and item completions that happened in
     * between are not in it. This buffer holds that subset per thread and [open] drains it after
     * the snapshot. Content deltas are deliberately not held — the snapshot already contains their
     * result, so replaying them would append the same text twice. Upstream has the same split in
     * `app/thread_event_buffer.rs` + `app/replay_filter.rs`, at the scale of a whole event store.
     */
    private val foreignEvents = ForeignEventBuffer()

    /** In-flight auto reviews, arrival order, for the aggregated review footer. */
    private val reviewsInFlight = mutableStateListOf<PendingReview>()

    /** Recent auto-review denials the user may override once; newest first, bounded. */
    private val autoReviewDenials = mutableStateListOf<AutoReviewDenial>()

    /** The request the dialog is showing; only the head of the queue is ever on screen. */
    var currentApproval by mutableStateOf<ApprovalRequest?>(null)
        private set

    var answeringApproval by mutableStateOf(false)
        private set
    var approvalError by mutableStateOf<String?>(null)
        private set

    /**
     * Set once anything arrives from the live stream for the thread being loaded.
     *
     * `thread/read` is issued while opening, so what it returns is a snapshot taken at request
     * time; this flag says whether the stream has since overtaken it. See [open].
     */
    private var streamOvertookLoad = false

    /**
     * Cursor into the turns older than what [open] loaded.
     *
     * `thread/resume` answers with a bounded `initialTurnsPage` when the client asks for one; the
     * transcript uses the page's `nextCursor` to offer "load earlier" and replaces it with each
     * subsequent page's own cursor. Null means there is nothing older on the server, so the button
     * is absent rather than disabled.
     */
    var nextTurnCursor by mutableStateOf<String?>(null)
        private set

    var loadingEarlier by mutableStateOf(false)
        private set

    /** Whether a "load earlier" affordance should be on screen right now. */
    val canLoadEarlier: Boolean get() = nextTurnCursor != null && !loadingEarlier

    /** How many requests are actually on screen, i.e. eligible for the "in queue" line. */
    val approvalQueueSize: Int get() = if (currentApproval == null) 0 else pendingApprovals.size

    /**
     * Whether [threadId] is a side conversation.
     *
     * A side conversation forks its parent and inherits the parent's persisted dynamic tool specs
     * (`thread/fork` has no `dynamicTools` field), so delegation is refused at call time rather
     * than filtered out of the advertised spec.
     */
    var isSideThread: (String) -> Boolean = { false }

    /** Per-thread counts of approvals waiting in threads that are not open. */
    val otherThreadApprovals: List<ForeignApproval>
        get() = otherApprovals.groupBy { it.threadId }.map { (threadId, requests) ->
            ForeignApproval(threadId, requests.size)
        }

    val pendingReviews: List<PendingReview> get() = reviewsInFlight
    val approvalDenials: List<AutoReviewDenial> get() = autoReviewDenials

    /**
     * Record a composer edit.
     *
     * Called on every text change. When the head of the queue is waiting out the idle window, the
     * deadline moves to one second from this keystroke, so a user who keeps typing is never
     * interrupted.
     */
    fun noteComposerActivity() {
        composerActiveAtMs = monotonicMs()
        if (currentApproval == null && pendingApprovals.isNotEmpty() && promotionJob != null) {
            schedulePromotion()
        }
    }

    private fun monotonicMs(): Long = System.nanoTime() / 1_000_000

    /** Milliseconds until the composer has been idle long enough to show an approval. */
    private fun typingIdleRemainingMs(): Long {
        if (composerActiveAtMs == 0L) return 0L
        return composerActiveAtMs + ApprovalTypingIdleDelayMs - monotonicMs()
    }

    private fun schedulePromotion() {
        promotionJob?.cancel()
        val remaining = typingIdleRemainingMs()
        promotionJob = scope.launch {
            if (remaining > 0) delay(remaining)
            promotionJob = null
            // Only the head is promoted; the rest is the modal's own queue.
            if (currentApproval == null) currentApproval = pendingApprovals.firstOrNull()
        }
    }

    /**
     * Show the head unless the composer is still being typed in.
     *
     * A request that arrives while nothing has been typed (or after the idle window) appears at
     * once; otherwise the dialog waits and [noteComposerActivity] moves the deadline with each
     * keystroke.
     */
    private fun promoteApprovalIfIdle() {
        if (currentApproval != null) return
        if (typingIdleRemainingMs() > 0) {
            schedulePromotion()
        } else {
            promotionJob?.cancel()
            promotionJob = null
            currentApproval = pendingApprovals.firstOrNull()
        }
    }

    /** Approvals queued for a thread that is not open, adopted when that thread is opened. */
    private fun adoptOtherThreadApprovals() {
        if (state.threadId.isBlank()) return
        val mine = otherApprovals.filter { it.threadId == state.threadId }
        if (mine.isEmpty()) return
        otherApprovals.removeAll(mine)
        mine.forEach { request ->
            if (pendingApprovals.none { it.requestId == request.requestId }) pendingApprovals.add(request)
        }
        promoteApprovalIfIdle()
    }

    fun attach() {
        subscription?.cancel()
        // Subscribing has to be finished by the time this returns. The transport has no replay
        // buffer on purpose — a socket delivers a notification exactly once — so anything emitted
        // between `attach()` and the collectors' first dispatch would be lost. Starting the
        // collectors undispatched runs their `collect` registration on this very stack frame.
        subscription = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                client.requests.collect { request -> onApprovalRequest(request) }
            }
            launch(start = CoroutineStart.UNDISPATCHED) {
                client.events.collect { event -> apply(event) }
            }
        }
    }

    fun detach() {
        flushDeltas()
        subscription?.cancel()
        subscription = null
    }

    fun connectionLost() {
        flushDeltas()
        resetApprovalState()
        patchChanges.clear()
        state.applyStatus(ThreadStatus.NotLoaded)
    }

    /** Drop every approval-scoped queue and notice; the thread they belonged to is gone. */
    private fun resetApprovalState() {
        promotionJob?.cancel()
        promotionJob = null
        pendingApprovals.clear()
        currentApproval = null
        otherApprovals.clear()
        reviewsInFlight.clear()
        autoReviewDenials.clear()
        answeringApproval = false
        approvalError = null
    }

    /** Open a thread and load its history. */
    fun open(threadId: String, onLoaded: (Result<ThreadReadResponse>) -> Unit = {}) {
        loadJob?.cancel()
        val version = ++loadVersion
        dropDeltas()
        // Everything approval-scoped belongs to the old thread. `otherApprovals` survives the reset
        // by one step: a request for the thread being opened is adopted back into the queue once
        // `beginLoad` has made that thread current.
        promotionJob?.cancel()
        promotionJob = null
        pendingApprovals.clear()
        currentApproval = null
        reviewsInFlight.clear()
        autoReviewDenials.clear()
        answeringApproval = false
        approvalError = null
        state.beginLoad(threadId)
        adoptOtherThreadApprovals()
        turnDiff.reset()
        patchChanges.clear()
        streamOvertookLoad = false
        nextTurnCursor = null
        loadingEarlier = false
        recap.resetForNewThread()
        loadJob = scope.launch {
            val resumed = client.resumeThread(
                ThreadResumeParams(
                    threadId = threadId,
                    // The metadata-only resume plus one bounded page is what keeps a long thread
                    // from replaying its whole rollout on open.
                    excludeTurns = true,
                    initialTurnsPage = ThreadResumeInitialTurnsPageParams(
                        limit = FirstScreenTurnLimit,
                        sortDirection = SortDirection.Desc,
                        itemsView = TurnItemsView.Full,
                    ),
                ),
            )
            if (version != loadVersion) return@launch
            var streamedStatus: ThreadStatus? = null
            resumed.onSuccess { session ->
                streamedStatus = state.status.takeIf { streamOvertookLoad && it != ThreadStatus.NotLoaded }
                state.bindThread(threadId, session)
                if (streamedStatus != null) state.applyStatus(streamedStatus)
                // Read after `bindThread` so a stale resume from a cancelled load cannot plant a
                // cursor for the thread that replaced it; the version check above already guards
                // the common race, and this keeps the invariant local to the success branch.
                if (version == loadVersion) nextTurnCursor = session.initialTurnsPage?.nextCursor
            }
                .onFailure { error ->
                    state.failLoad(error.message)
                    state.addDiagnostic(
                        SessionDiagnostic(
                            severity = DiagnosticSeverity.Error,
                            code = DiagnosticCode.ThreadLoadFailed,
                            detail = error.message,
                        ),
                    )
                }
            resumed.exceptionOrNull()?.let { error ->
                onLoaded(Result.failure(error))
                return@launch
            }
            val session = resumed.getOrNull()
            val row = session?.thread
            val page = session?.initialTurnsPage
            var loadedTurns = emptyList<com.cy.codex.protocol.protocol.v2.Turn>()
            if (version == loadVersion && row != null && page != null) {
                // Separators after finished turns are rebuilt from the page's turns: the server
                // does not store the divider this client draws.
                val transcript = com.cy.codex.history_cell.transcriptWithSeparators(page.turns)
                applyLoadedTranscript(transcript)
                if (streamedStatus == null) state.applyStatus(row.status)
                loadedTurns = page.turns
                onLoaded(Result.success(ThreadReadResponse(row, transcript, page.turns)))
            } else {
                // A server without `initialTurnsPage` still gets the full read it always got.
                val history = client.readThread(com.cy.codex.protocol.protocol.v2.ThreadReadParams(threadId))
                if (version != loadVersion) return@launch
                history.onSuccess { response ->
                    if (version != loadVersion) return@onSuccess
                    val transcript = if (response.turns.isNotEmpty()) {
                        com.cy.codex.history_cell.transcriptWithSeparators(response.turns)
                    } else {
                        response.items
                    }
                    applyLoadedTranscript(transcript)
                    response.turns.lastOrNull()?.usage?.let(state::applyUsage)
                    state.applyStatus(response.thread.status)
                    loadedTurns = response.turns
                }.onFailure { error ->
                    if (version != loadVersion) return@onFailure
                    state.addDiagnostic(SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = DiagnosticCode.ThreadLoadFailed,
                        detail = error.message,
                    ))
                }
                onLoaded(history)
            }
            if (version != loadVersion) return@launch
            // Events that arrived while another thread was open are applied after the snapshot, so
            // content comes from the server exactly once and only the snapshot-less facts — a turn
            // that ended, a diagnostic, an item that completed — come from the buffer.
            foreignEvents.drain(threadId).forEach { buffered ->
                if (version != loadVersion) return@launch
                apply(buffered)
            }
            // Seeded after the replay so a buffered `TurnCompleted` is not counted twice; the recap
            // schedule then reflects the loaded transcript.
            recap.seedFromTurns(loadedTurns, monotonicMs())
            scheduleRecapCheck()
            refreshQueue(threadId)
            client.getGoal(threadId).onSuccess {
                if (version == loadVersion) state.applyGoal(it)
            }
            refreshGitSummary(threadId)
        }
    }

    /**
     * Apply a history snapshot, live-stream-aware.
     *
     * If the stream got there first, the snapshot describes a thread that has already moved on and
     * may only fill the gaps it left. Applying it wholesale replaced freshly streamed bodies with
     * the older copies the snapshot was taken from — a plan whose deltas had arrived collapsed back
     * to an empty card.
     */
    private fun applyLoadedTranscript(transcript: List<ThreadItem>) {
        if (streamOvertookLoad) {
            transcript.forEach(state::addIfAbsent)
        } else {
            transcript.forEach(state::upsert)
        }
    }

    /**
     * Fetch one page of turns older than the transcript's first row.
     *
     * `thread/turns/list` pages backwards: the request's cursor names the oldest loaded turn, the
     * page comes back newest-first, and its `nextCursor` names the next-older page. The page is
     * therefore reversed before it is prepended (with separators rebuilt, exactly like the first
     * screen), and a failure leaves the cursor in place so the button retries rather than
     * disappearing.
     */
    fun loadEarlier() {
        val cursor = nextTurnCursor ?: return
        if (loadingEarlier) return
        val threadId = state.threadId
        if (threadId.isBlank()) return
        loadingEarlier = true
        scope.launch {
            client.listThreadTurns(
                ThreadTurnsListParams(
                    threadId = threadId,
                    cursor = cursor,
                    limit = EarlierPageSize,
                    sortDirection = SortDirection.Desc,
                    itemsView = TurnItemsView.Full,
                ),
            ).onSuccess { page ->
                if (state.threadId != threadId) return@onSuccess
                state.prepend(com.cy.codex.history_cell.transcriptWithSeparators(page.turns.asReversed()))
                nextTurnCursor = page.nextCursor
            }.onFailure { error ->
                if (state.threadId != threadId) return@onFailure
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = DiagnosticCode.ThreadLoadFailed,
                        detail = error.message,
                    ),
                )
            }
            loadingEarlier = false
        }
    }

    /** Start a fresh thread in [cwd]. */
    fun newThread(cwd: String) {
        resetApprovalState()
        scope.launch {
            client.startThread(
                com.cy.codex.protocol.protocol.v2.ThreadStartParams(
                    cwd = cwd,
                    dynamicTools = DynamicTools.specs(),
                ),
            )
                .onSuccess { session ->
                    dropDeltas()
                    state.beginLoad(session.threadId)
                    turnDiff.reset()
                    patchChanges.clear()
                    recap.resetForNewThread()
                    state.bindThread(session.threadId, session)
                    // A brand-new thread starts with one tip, the way the TUI shows one in its
                    // startup session cell.
                    maybeShowStartupTip()
                }
                .onFailure { error ->
                    state.addDiagnostic(
                        SessionDiagnostic(
                            severity = DiagnosticSeverity.Error,
                            code = DiagnosticCode.NewThreadFailed,
                            detail = error.message,
                        ),
                    )
                }
        }
    }

    // ---------------------------------------------------------------------------------------
    // User actions
    // ---------------------------------------------------------------------------------------

    /**
     * Reduce one user action.
     *
     * Exhaustive on purpose, with no `else`. The events this widget does not own are listed at the
     * bottom rather than caught by a catch-all, so adding a variant to [AppEvent] without deciding
     * which reducer owns it is a compile error here — which is exactly the check that was missing
     * when Fork / Rename / Archive / Delete rendered in the session list and did nothing at all.
     */
    fun action(event: AppEvent) {
        when (event) {
            // ---- thread lifecycle ---------------------------------------------
            is AppEvent.NewThread -> newThread(event.cwd ?: state.config.cwd)
            is AppEvent.ResumeThread -> open(event.threadId)
            is AppEvent.ForkThread -> request({
                client.forkThread(com.cy.codex.protocol.protocol.v2.ThreadForkParams(event.threadId))
            }) { bind(it) }

            is AppEvent.ArchiveThread -> request {
                if (event.archived) client.archiveThread(event.threadId) else
                    client.unarchiveThread(event.threadId)
            }

            is AppEvent.DeleteThread -> request { client.deleteThread(event.threadId) }
            is AppEvent.RenameThread -> request { client.setThreadName(event.threadId, event.name) }
            is AppEvent.CompactThread -> request { client.compactThread(event.threadId) }
            is AppEvent.RevertThread -> request { client.revertThread(event.threadId, event.itemId) }

            is AppEvent.MoveThreadToSection -> request {
                client.moveThreadToSection(event.threadId, event.sectionId)
            }

            is AppEvent.RunShellCommand -> request {
                client.runShellCommand(event.threadId, event.command).onSuccess {
                    if (state.threadId != event.threadId) return@onSuccess
                    // Both spellings are cleared: `/shell cmd`, and the `!cmd` escape the composer
                    // now recognizes.
                    if (state.composerDraft.startsWith("/shell ") || state.composerDraft.startsWith("!")) {
                        state.applyDraft("")
                    }
                }
            }

            is AppEvent.ApproveGuardianDeniedAction -> request({
                client.approveGuardianDeniedAction(event.threadId, event.itemId)
            }) {
                autoReviewDenials.removeAll { it.itemId == event.itemId }
            }

            is AppEvent.DismissAutoReviewDenial ->
                autoReviewDenials.removeAll { it.itemId == event.itemId }

            // ---- turns ----------------------------------------------------------
            is AppEvent.SubmitUserMessage -> submitInput(event.inputs)
            is AppEvent.AnswerAsyncQuestion -> submitInput(listOf(UserInput.Text(event.text)), clearDraft = false)
            AppEvent.InterruptTurn -> interrupt()
            is AppEvent.ResolveApproval -> resolve(event.requestId, event.response)
            is AppEvent.DismissApproval -> dismiss(event.requestId)

            AppEvent.GenerateRecap -> generateRecap(com.cy.codex.app.RecapTrigger.Manual)
            AppEvent.ContinueMisalignment -> continueMisalignment()

            is AppEvent.SetGoal -> request({
                client.setGoal(
                    com.cy.codex.protocol.protocol.v2.ThreadGoalSetParams(
                        threadId = state.threadId,
                        objective = event.objective,
                        status = event.status,
                    ),
                )
            }) { state.applyGoal(it) }

            AppEvent.ClearGoal -> request({ client.clearGoal(state.threadId) }) { state.applyGoal(null) }

            // ---- server-side queue ----------------------------------------------
            is AppEvent.StartQueuedMessage -> request { client.startQueued(state.threadId, event.queuedId) }
            is AppEvent.DeleteQueuedMessage -> request { client.deleteQueued(state.threadId, event.queuedId) }

            is AppEvent.UpdateQueuedMessage -> request {
                client.updateQueued(state.threadId, event.queuedId, event.inputs)
            }

            is AppEvent.ClearQueue -> request({
                // No bulk endpoint exists, so dropping the queue means dropping every entry. The
                // list is re-read once at the end rather than after each delete.
                state.queued.map { it.id }.forEach { id -> client.deleteQueued(state.threadId, id) }
                client.listQueue(state.threadId)
            }) { replaceQueue(it) }

            is AppEvent.MoveQueuedMessage -> request {
                val ids = state.queued.map { it.id }.toMutableList()
                val from = ids.indexOf(event.queuedId)
                if (from < 0) return@request Result.success(Unit)
                val to = (from + event.delta).coerceIn(0, ids.lastIndex)
                if (to == from) return@request Result.success(Unit)
                ids.add(to, ids.removeAt(from))
                client.reorderQueue(state.threadId, ids)
            }

            // ---- settings --------------------------------------------------------
            is AppEvent.SetModel -> request({
                client.updateThreadSettings(state.threadId, model = event.model)
            }) {
                state.applyConfig(state.config.copy(model = event.model, modelDisplayName = event.model))
            }

            is AppEvent.SetReasoningEffort -> request({
                client.updateThreadSettings(state.threadId, effort = event.effort)
            }) { state.applyConfig(state.config.copy(reasoningEffort = event.effort)) }

            is AppEvent.SetApprovalPolicy -> request({
                client.updateThreadSettings(state.threadId, approvalPolicy = event.policy)
            }) { state.applyConfig(state.config.copy(approvalPolicy = event.policy)) }

            is AppEvent.SetApprovalsReviewer -> request({
                client.updateThreadSettingsFull(
                    ThreadSettingsUpdateParams(
                        threadId = state.threadId,
                        approvalsReviewer = event.reviewer,
                    ),
                )
            }) { state.applyConfig(state.config.copy(approvalsReviewer = event.reviewer)) }

            is AppEvent.SetCollaborationMode -> request({
                client.updateThreadSettingsFull(
                    ThreadSettingsUpdateParams(
                        threadId = state.threadId,
                        collaborationMode = event.mode,
                    ),
                )
            }) { state.applyConfig(state.config.copy(collaborationMode = event.mode)) }

            is AppEvent.SetServiceTier -> request({
                client.updateThreadSettingsFull(
                    ThreadSettingsUpdateParams(
                        threadId = state.threadId,
                        // The wire spells "no preference" as the literal `default`, not as a missing
                        // field; `SERVICE_TIER_DEFAULT_REQUEST_VALUE` upstream.
                        serviceTier = event.tier ?: ServiceTierDefault,
                    ),
                )
            }) { state.applyConfig(state.config.copy(serviceTier = event.tier)) }

            // ---- attachments and background terminals -----------------------------
            is AppEvent.RemoveAttachment -> request({
                client.removeAttachment(event.threadId, event.type, event.identityKey)
            }) { state.attachments.removeAll { it.identityKey == event.identityKey } }

            is AppEvent.TerminateBackgroundTerminal -> request({
                client.terminateBackgroundTerminal(event.threadId, event.processId)
            }) { state.backgroundTerminals.removeAll { it.processId == event.processId } }

            is AppEvent.CleanBackgroundTerminals -> request({
                client.cleanBackgroundTerminals(event.threadId)
            }) { state.backgroundTerminals.clear() }

            // ---- composer ---------------------------------------------------------
            //
            // A paste that is exactly one local image path is staged as an attachment instead of
            // typed out: the placeholder `[Image #N]` goes where the path would have been. The
            // path must exist and fit the transport limit, which is why the check lives here and
            // not in the pure diff.
            is AppEvent.SetComposerDraft -> {
                val pasted = detectPastedImagePath(state.composerDraft, event.text)
                if (pasted == null) {
                    state.applyDraft(event.text)
                } else {
                    val file = java.io.File(pasted.path)
                    when {
                        !file.isFile -> state.applyDraft(event.text)
                        file.length() > MaxComposerImageBytes -> {
                            state.applyDraft(event.text)
                            state.addDiagnostic(
                                SessionDiagnostic(
                                    severity = DiagnosticSeverity.Warning,
                                    code = DiagnosticCode.ImageTooLarge,
                                    args = listOf(file.name),
                                ),
                            )
                        }

                        else -> {
                            val placeholder = state.addComposerImage(pasted.path)
                            state.applyDraft(event.text.replaceRange(pasted.start, pasted.end, "$placeholder "))
                        }
                    }
                }
            }

            is AppEvent.RemoveComposerImage -> state.removeComposerImage(event.path)

            is AppEvent.SubmitSlashCommand,

            // ---- owned by `CodexApp` ----------------------------------------------
            //
            // Listed rather than caught by an `else`, so a new event has to be classified. These
            // never reach here: `CodexApp.onAppEvent` handles them before forwarding.
            is AppEvent.ToggleSideConversation,
            is AppEvent.ReloadAccount,
            is AppEvent.ReloadRateLimits,
            is AppEvent.ReloadUsage,
            is AppEvent.ReloadConfig,
            is AppEvent.ReloadAgentThreads,
            is AppEvent.StopThreadTurn,
            is AppEvent.ReloadSkills,
            is AppEvent.ReloadPlugins,
            is AppEvent.ReloadPluginShares,
            is AppEvent.ReloadApps,
            is AppEvent.ReloadHooks,
            is AppEvent.SetHookTrust,
            is AppEvent.SetHookEnabled,
            is AppEvent.SetMemorySettings,
            is AppEvent.ReloadMcpServers,
            is AppEvent.ReloadProjects,
            is AppEvent.ReloadEnvironments,
            is AppEvent.ReloadMemories,
            is AppEvent.ReloadRealtimeVoices,
            is AppEvent.ReloadUserVerification,
            is AppEvent.ReloadRemoteControl,
            is AppEvent.ReloadDiagnostics,
            is AppEvent.ReloadExternalAgentConfig,
            is AppEvent.ReloadGoal,
            is AppEvent.RefreshThreadList,
            is AppEvent.SetThreadListScope,
            is AppEvent.InstallPlugin,
            is AppEvent.UninstallPlugin,
            is AppEvent.SetPluginEnabled,
            is AppEvent.AddMarketplace,
            is AppEvent.RemoveMarketplace,
            is AppEvent.UpgradeMarketplace,
            is AppEvent.ReconcilePlugins,
            is AppEvent.SavePluginShare,
            is AppEvent.DeletePluginShare,
            is AppEvent.CheckoutPluginShare,
            is AppEvent.UpdatePluginShareTargets,
            is AppEvent.SetSkillEnabled,
            is AppEvent.SetSkillExtraRoots,
            is AppEvent.SetAppInstalled,
            is AppEvent.McpLogin,
            is AppEvent.ReloadMcpConfig,
            is AppEvent.SetMcpEventStream,
            is AppEvent.Login,
            is AppEvent.CancelLogin,
            is AppEvent.Logout,
            is AppEvent.ConsumeResetCredit,
            is AppEvent.SendAddCreditsNudgeEmail,
            is AppEvent.BedrockDiscover,
            is AppEvent.BedrockSetup,
            is AppEvent.CreateSection,
            is AppEvent.RenameSection,
            is AppEvent.DeleteSection,
            is AppEvent.CreateProject,
            is AppEvent.UpdateProject,
            is AppEvent.DeleteProject,
            is AppEvent.MoveProject,
            is AppEvent.ImportProject,
            is AppEvent.AddEnvironment,
            is AppEvent.SetRemoteControlEnabled,
            is AppEvent.StartRemoteControlPairing,
            is AppEvent.PollRemoteControlPairing,
            is AppEvent.RevokeRemoteControlClient,
            is AppEvent.EnrollUserVerification,
            is AppEvent.VerifyUserVerification,
            is AppEvent.CancelUserVerification,
            is AppEvent.DeleteUserVerification,
            is AppEvent.StartRealtime,
            is AppEvent.StopRealtime,
            is AppEvent.AppendRealtimeText,
            is AppEvent.AppendRealtimeSpeech,
            is AppEvent.AppendRealtimeAudio,
            is AppEvent.IncrementElicitation,
            is AppEvent.DecrementElicitation,
            is AppEvent.StartReview,
            is AppEvent.ResetMemory,
            is AppEvent.DetectExternalAgentConfig,
            is AppEvent.ImportExternalAgentConfig,
            is AppEvent.UploadFeedback,
            is AppEvent.WindowsSandboxSetupStart,
            is AppEvent.WriteConfigValue,
            is AppEvent.WriteConfigBatch,
            is AppEvent.SetExperimentalFeature,
            -> Unit
        }
    }

    /**
     * Run one request against the open thread, and report a failure where the user can see it.
     *
     * These used to be bare `scope.launch { client.… }` calls whose `Result` was discarded, so a
     * rename the server refused looked exactly like one it accepted.
     */
    private fun request(block: suspend () -> Result<*>) {
        request(block, then = {})
    }

    /**
     * Run one request and fold a successful answer.
     *
     * `then` is a suspend lambda rather than the `onSuccess` receiver so that a continuation may
     * itself talk to the server — following a rename with a re-read, for instance — without the
     * call site having to nest another `launch`.
     */
    private fun <T> request(block: suspend () -> Result<T>, then: suspend (T) -> Unit) {
        scope.launch {
            block()
                .onSuccess { then(it) }
                .onFailure { error ->
                    state.addDiagnostic(
                        SessionDiagnostic(severity = DiagnosticSeverity.Warning, message = error.message),
                    )
                }
        }
    }

    /** Bind a thread a lifecycle call just produced — a fork, which is a new thread id. */
    fun bind(session: com.cy.codex.protocol.protocol.v2.ThreadSessionState) {
        loadJob?.cancel()
        loadVersion++
        dropDeltas()
        resetApprovalState()
        state.beginLoad(session.threadId)
        turnDiff.reset()
        patchChanges.clear()
        recap.resetForNewThread()
        state.bindThread(session.threadId, session)
    }

    /**
     * Put one startup tip at the top of a fresh conversation.
     *
     * Mirrors `tui/src/tooltips.rs`: gated by the `show_tooltips` appearance setting, picked at
     * random, and only on a conversation that has no messages yet.
     */
    private fun maybeShowStartupTip() {
        if (!com.cy.codex.theme.Appearance.showTooltips) return
        if (state.items.isNotEmpty()) return
        val tip = Tooltips.random() ?: return
        state.upsert(
            com.cy.codex.protocol.protocol.item.TipItem("tip-${state.threadId}", tip),
        )
    }

    fun clear() {
        loadJob?.cancel()
        loadVersion++
        resetApprovalState()
        patchChanges.clear()
        dropDeltas()
        turnDiff.reset()
        recap.resetForNewThread()
        state.clear()
    }

    private fun replaceQueue(queued: List<QueuedSubmission>) {
        state.queued.clear()
        state.queued.addAll(queued)
    }

    private fun dropDeltas() {
        deltaFlushJob?.cancel()
        deltaFlushJob = null
        pendingDeltas.clear()
    }

    /** Buffer one streaming delta until the next commit tick. */
    private fun appendDelta(itemId: String, kind: DeltaKind, delta: String) {
        pendingDeltas.getOrPut(itemId) { PendingDeltas() }.append(kind, delta)
        if (deltaFlushJob == null) {
            deltaFlushJob = scope.launch {
                delay(Motion.StreamCommitIntervalMs)
                flushDeltas()
            }
        }
    }

    /**
     * Apply every buffered delta now.
     *
     * The tick calls this after [Motion.StreamCommitIntervalMs]; item completion and turn end call
     * it directly so a message never loses its tail to a pending flush.
     */
    internal fun flushDeltas() {
        deltaFlushJob = null
        if (pendingDeltas.isEmpty()) return
        val pending = pendingDeltas.toMap()
        pendingDeltas.clear()
        for ((itemId, batch) in pending) {
            for ((kind, text) in batch.chunks) {
                val delta = text.toString()
                when (kind) {
                    DeltaKind.AgentMessage -> state.appendAgentDelta(itemId, delta)
                    DeltaKind.Plan -> state.appendPlanDelta(itemId, delta)
                    DeltaKind.Reasoning -> appendReasoningText(itemId, delta)
                    DeltaKind.CommandOutput -> appendCommandOutput(itemId, delta)
                    DeltaKind.McpProgress -> appendMcpProgress(itemId, delta)
                }
            }
        }
    }

    private fun submitInput(inputs: List<UserInput>, clearDraft: Boolean = true) {
        val hasText = inputs.filterIsInstance<UserInput.Text>().any { it.text.isNotBlank() }
        val hasMedia = inputs.any { it !is UserInput.Text }
        if ((!hasText && !hasMedia) || !state.open || state.loading) return
        // Viewing a parent-owned sub-agent: the transcript is readable, input is not.
        if (state.config.blocksDirectInput) return
        val threadId = state.threadId
        if (state.running) {
            request({ client.addToQueue(threadId, inputs) }) {
                if (clearDraft && state.threadId == threadId) clearComposer()
                refreshQueue(threadId)
            }
            return
        }
        state.applyStatus(ThreadStatus.Active())
        scope.launch {
            client.startTurn(threadId, inputs).onSuccess {
                if (clearDraft && state.threadId == threadId) clearComposer()
            }.onFailure { error ->
                if (state.threadId != threadId) return@onFailure
                state.applyStatus(ThreadStatus.Idle)
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = DiagnosticCode.SendFailed,
                        detail = error.message,
                    ),
                )
            }
        }
    }

    /**
     * Refresh the branch/PR/diff summary the status card shows.
     *
     * Mirrors `tui/src/branch_summary.rs`: `git` and `gh` run through `command/exec` in the
     * session workspace, and failures are silent because the card simply omits the line.
     */
    private fun refreshGitSummary(threadId: String) {
        val cwd = state.config.cwd
        if (threadId.isBlank() || cwd.isBlank()) return
        gitSummaryJob?.cancel()
        gitSummaryJob = scope.launch {
            val summary = loadGitSummary(client, cwd)
            if (state.threadId == threadId) state.applyGitSummary(summary)
        }
    }

    /** Clear the draft and any staged local images after a submission consumed them. */
    private fun clearComposer() {
        state.applyDraft("")
        state.clearComposerImages()
    }

    /** Re-read the server's queue for [threadId] and replace the local copy. */
    private fun refreshQueue(threadId: String) {
        scope.launch {
            client.listQueue(threadId).onSuccess {
                if (state.threadId == threadId) replaceQueue(it)
            }
        }
    }

    private fun interrupt() {
        scope.launch {
            client.interruptTurn(state.threadId).onFailure { error ->
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Warning,
                        code = DiagnosticCode.InterruptFailed,
                        detail = error.message,
                    ),
                )
            }
        }
    }

    private fun resolve(requestId: com.cy.codex.protocol.protocol.RequestId, response: ApprovalResponse) {
        if (answeringApproval) return
        answeringApproval = true
        approvalError = null
        scope.launch {
            try {
                client.respond(requestId, response)
                pendingApprovals.removeAll { it.requestId == requestId }
                // The modal already has the user's attention, so the next queued request follows
                // immediately instead of waiting out the typing window again.
                if (currentApproval?.requestId == requestId) currentApproval = pendingApprovals.firstOrNull()
                if (pendingApprovals.isEmpty() && state.running) state.applyStatus(ThreadStatus.Active())
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                approvalError = error.message
            } finally {
                answeringApproval = false
            }
        }
    }

    private fun dismiss(requestId: com.cy.codex.protocol.protocol.RequestId) {
        pendingApprovals.removeAll { it.requestId == requestId }
        otherApprovals.removeAll { it.requestId == requestId }
        if (currentApproval?.requestId == requestId) currentApproval = pendingApprovals.firstOrNull()
    }

    private fun onApprovalRequest(request: ApprovalRequest) {
        // Three of the eleven server requests are not decisions: the server is asking the *host* for
        // something only the host can produce. Showing them as cards would put a "sign in again"
        // dialog in front of the user for something they cannot answer, and would block the turn
        // behind it. They are answered here and never enter the queue.
        when (request) {
            is ApprovalRequest.CurrentTimeRead -> {
                scope.launch {
                    client.respond(request.requestId, ApprovalResponse.CurrentTime(System.currentTimeMillis()))
                }
                return
            }

            is ApprovalRequest.DynamicTool -> {
                // Dynamic tools are the model delegating work to this client; they execute
                // immediately and never surface as an approval card, the way `dynamic_tools.rs`
                // answers them. The answer is a `DynamicToolCallResponse`, not a user decision.
                val calling = request.threadId
                val cwd = state.config.cwd
                val model = state.config.model
                scope.launch {
                    val result = executeDynamicTool(
                        client = client,
                        callingThreadId = calling,
                        cwd = cwd,
                        model = model,
                        params = request.params,
                        isSideThread = isSideThread(calling),
                    )
                    runCatching {
                        client.respond(
                            request.requestId,
                            ApprovalResponse.DynamicTool(result),
                        )
                    }
                }
                return
            }

            is ApprovalRequest.AttestationGenerate -> {
                scope.launch {
                    // A real host signs the nonce with the platform attestation key. The client has
                    // no keystore integration, so the reply is well-formed but unverifiable — which
                    // is the honest answer, and the server rejects it rather than the UI pretending.
                    client.respond(request.requestId, ApprovalResponse.Attestation(token = ""))
                }
                return
            }

            is ApprovalRequest.ChatgptAuthTokensRefresh -> {
                scope.launch {
                    // The tokens live in the account store, not here; re-reading the account is what
                    // produces them, and an empty reply tells the server to fall back to its own
                    // refresh path instead of waiting on a request nobody will answer.
                    client.respond(
                        request.requestId,
                        ApprovalResponse.Tokens(accessToken = "", chatgptAccountId = ""),
                    )
                }
                return
            }

            is ApprovalRequest.Exec,
            is ApprovalRequest.ApplyPatch,
            is ApprovalRequest.Permissions,
            is ApprovalRequest.UserInput,
            is ApprovalRequest.Elicitation,
            is ApprovalRequest.DynamicTool,
            -> Unit
        }

        // One alert per request, before the queue dedup below: a request for another thread still
        // blocks that agent and is exactly what a backgrounded user should hear about.
        _notices.tryEmit(
            AgentNotice.Approval(
                threadId = request.threadId,
                kind = approvalNoticeKind(request),
                detail = approvalNoticeDetail(request),
            ),
        )

        if (pendingApprovals.any { it.requestId == request.requestId }) return
        if (otherApprovals.any { it.requestId == request.requestId }) return
        // A request whose turn already finished is stale: the server resolved it while this client
        // was not listening. Showing it would offer a decision with no effect.
        if (request.turnId != null && request.turnId in finishedTurns) return
        // A request for another thread must not take over this transcript. It is tracked for the
        // cross-thread banner, and adopted if the user switches to that thread.
        if (request.threadId.isNotBlank() && request.threadId != state.threadId) {
            otherApprovals.add(request)
            return
        }
        pendingApprovals.add(request)
        state.applyStatus(
            ThreadStatus.Active(
                listOf(
                    com.cy.codex.protocol.protocol.v2.ThreadActiveFlag.WaitingOnApproval,
                ),
            ),
        )
        // The dialog waits out the composer's idle window; a request that arrives while nothing has
        // been typed (or after the window) is shown at once.
        promoteApprovalIfIdle()
    }

    // ---------------------------------------------------------------------------------------
    // Server events
    // ---------------------------------------------------------------------------------------

    private fun apply(event: AppServerEvent) {
        val eventThread = event.threadId
        if (eventThread != null && eventThread != state.threadId) {
            foreignEvents.record(eventThread, event)
            return
        }
        eventRevision++
        streamOvertookLoad = true
        when (event) {
            is AppServerEvent.ItemStarted -> state.upsert(event.item)
            is AppServerEvent.ItemCompleted -> onItemCompleted(event.item)
            is AppServerEvent.AgentMessageDelta ->
                appendDelta(event.delta.itemId, DeltaKind.AgentMessage, event.delta.delta)

            is AppServerEvent.PlanDelta ->
                appendDelta(event.delta.itemId, DeltaKind.Plan, event.delta.delta)

            is AppServerEvent.ReasoningTextDelta ->
                appendDelta(event.delta.itemId, DeltaKind.Reasoning, event.delta.delta)

            is AppServerEvent.ReasoningSummaryDelta ->
                appendDelta(event.delta.itemId, DeltaKind.Reasoning, event.delta.delta)

            is AppServerEvent.ReasoningSummaryPartAdded ->
                appendDelta(event.delta.itemId, DeltaKind.Reasoning, "\n\n")

            is AppServerEvent.CommandOutputDelta ->
                appendDelta(event.delta.itemId, DeltaKind.CommandOutput, event.delta.delta)

            is AppServerEvent.CommandTerminalInteraction ->
                appendDelta(event.delta.itemId, DeltaKind.CommandOutput, event.delta.stdin)

            is AppServerEvent.FileChangeOutputDelta -> Unit
            is AppServerEvent.McpToolProgress ->
                appendDelta(event.delta.itemId, DeltaKind.McpProgress, event.delta.message)

            // The bridge dropped events, so deltas may have gone missing. Anything the transcript
            // holds from the stream is untrustworthy; re-reading the thread is the same repair a
            // compact or a revert uses.
            is AppServerEvent.TransportLagged -> {
                dropDeltas()
                if (state.threadId.isNotEmpty()) {
                    refreshHistory(state.threadId)
                    refreshQueue(state.threadId)
                }
            }
            is AppServerEvent.TurnStarted -> {
                state.applyStatus(ThreadStatus.Active())
                state.applyStreaming(null)
                // A new turn clears a previous safety stop; the gate is per turn.
                state.applyMisalignment(null)
            }

            is AppServerEvent.TurnCompleted -> onTurnCompleted(event)
            is AppServerEvent.TurnDiffUpdatedEvent ->
                state.applyTurnDiff(turnDiff.apply(event.delta.diff))

            is AppServerEvent.TurnPlanUpdatedEvent -> state.applyPlan(event.delta.plan)
            is AppServerEvent.ThreadStartedEvent -> state.bindThread(event.thread.threadIdOr(event.threadId), state.config)
            is AppServerEvent.ThreadStatusChangedEvent -> state.applyStatus(event.delta.status)
            is AppServerEvent.ThreadTokenUsageEvent -> state.applyUsage(event.delta.usage)
            is AppServerEvent.ThreadSettingsUpdatedEvent -> {
                val delta = event.delta
                state.applyConfig(
                    state.config.copy(
                        model = delta.model ?: state.config.model,
                        modelDisplayName = delta.model ?: state.config.modelDisplayName,
                        reasoningEffort = delta.reasoningEffort ?: state.config.reasoningEffort,
                        approvalPolicy = delta.approvalPolicy ?: state.config.approvalPolicy,
                        approvalsReviewer = delta.approvalsReviewer ?: state.config.approvalsReviewer,
                        collaborationMode = delta.collaborationMode ?: state.config.collaborationMode,
                        serviceTier = delta.serviceTier ?: state.config.serviceTier,
                    ),
                )
            }

            is AppServerEvent.ThreadGoalUpdatedEvent -> state.applyGoal(event.delta)
            is AppServerEvent.ThreadGoalCleared -> state.applyGoal(null)
            is AppServerEvent.ThreadNameUpdatedEvent ->
                state.applyConfig(state.config.copy(threadName = event.delta.name))

            is AppServerEvent.ThreadQueueChangedEvent -> refreshQueue(event.threadId)

            is AppServerEvent.ThreadCompacted -> refreshHistory(event.threadId)
            is AppServerEvent.ThreadRevertedEvent -> refreshHistory(event.threadId)

            // The five diagnostic notifications are separate methods on the wire with different
            // payloads, so they are folded into the transcript one by one rather than through a
            // shared "diagnostic" shape that would have to drop `path`.
            is AppServerEvent.ErrorEvent -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Error,
                    message = event.delta.error.message,
                    detail = event.delta.error.additionalDetails,
                ),
            )

            is AppServerEvent.WarningEvent -> state.addDiagnostic(
                SessionDiagnostic(DiagnosticSeverity.Warning, event.delta.message),
            )

            is AppServerEvent.ConfigWarningEvent -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    message = event.delta.summary,
                    detail = listOfNotNull(event.delta.path, event.delta.details).joinToString(" · "),
                ),
            )

            is AppServerEvent.GuardianWarningEvent -> state.addDiagnostic(
                SessionDiagnostic(DiagnosticSeverity.Warning, event.delta.message),
            )

            is AppServerEvent.DeprecationNoticeEvent -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    message = event.delta.summary,
                    detail = event.delta.details,
                ),
            )

            is AppServerEvent.WorldWritableWarning -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    code = DiagnosticCode.WorldWritable,
                    args = listOf(
                        event.delta.samplePaths.firstOrNull().orEmpty(),
                        event.delta.extraCount.toString(),
                    ),
                ),
            )

            is AppServerEvent.RequestResolved -> dismiss(
                com.cy.codex.protocol.protocol.RequestId(event.delta.requestId),
            )

            is AppServerEvent.ModelReroutedEvent -> {
                state.activeModelLabel = event.delta.toModel
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Warning,
                        code = DiagnosticCode.ModelSwitched,
                        args = listOf(event.delta.toModel),
                        detail = event.delta.reason,
                    ),
                )
            }

            // `item/fileChange/patchUpdated` carries the patch as it grows. The item may not have
            // been started yet, in which case the later `item/started` brings the same content; the
            // update is kept by id either way so an approval arriving in between still has a diff.
            is AppServerEvent.FileChangePatchUpdated -> {
                patchChanges[event.delta.itemId] = event.delta.changes
                val item = state.item(event.delta.itemId) as? FileChangeItem
                if (item != null) state.upsert(item.copy(changes = event.delta.changes))
            }

            // The review lifecycle does not render per item; in-flight reviews are aggregated into
            // the composer's approval notice, and a denial is remembered there so it can be
            // overridden once.
            is AppServerEvent.AutoApprovalReviewStarted -> onReviewStarted(event.delta)
            is AppServerEvent.AutoApprovalReviewCompleted -> onReviewCompleted(event.delta)

            is AppServerEvent.StrictReviewRequired -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    message = event.delta.reason,
                    code = DiagnosticCode.StrictReviewRequired,
                ),
            )

            // A hook that failed is worth a notice; one that succeeded is not, or a session with
            // hooks on would fill the transcript with a line per tool call.
            is AppServerEvent.HookCompleted -> {
                state.applyHookCompleted()
                if (event.delta.run.failed) {
                    state.addDiagnostic(
                        SessionDiagnostic(
                            severity = DiagnosticSeverity.Warning,
                            message = event.delta.run.statusMessage,
                            code = DiagnosticCode.HookFailed,
                            args = listOf(event.delta.run.eventName.ifEmpty { event.delta.run.id }),
                        ),
                    )
                }
            }

            is AppServerEvent.HookStarted -> state.applyHookStarted(
                event.delta.run.statusMessage?.takeIf { it.isNotBlank() }
                    ?: event.delta.run.eventName.ifBlank { event.delta.run.id },
            )

            is AppServerEvent.McpOauthLoginCompleted -> if (!event.delta.success) {
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        message = event.delta.error,
                        code = DiagnosticCode.McpLoginFailed,
                        args = listOf(event.delta.name),
                    ),
                )
            }

            // ---------------------------------------------------------------------------------
            // Everything below belongs to a surface this reducer does not own. The transcript is
            // the only thing `ChatWidget` drives; the catalogs, the terminal drawer and the
            // realtime session are `CodexApp`'s. They are listed explicitly rather than caught by
            // an `else` so that adding a notification to the protocol is a compile error here
            // until someone decides which of the two owns it.
            // ---------------------------------------------------------------------------------
            is AppServerEvent.McpStartupStatusEvent,
            is AppServerEvent.SkillsChanged,
            is AppServerEvent.AppListUpdated,
            is AppServerEvent.AccountUpdated,
            is AppServerEvent.AccountLoginCompleted,
            is AppServerEvent.RateLimitsUpdatedEvent,
            is AppServerEvent.ThreadAttachmentUpdated,
            is AppServerEvent.TurnModerationMetadata,
            is AppServerEvent.ThreadProjectUpdated,
            is AppServerEvent.EnvironmentConnected,
            is AppServerEvent.EnvironmentDisconnected,
            is AppServerEvent.ModelVerification,
            is AppServerEvent.ModelProviderAuthRecovery,
            is AppServerEvent.McpServerEvent,
            is AppServerEvent.ProjectChanged,
            is AppServerEvent.RemoteControlStatusChanged,
            is AppServerEvent.FsChangedEvent,
            is AppServerEvent.CommandExecOutput,
            is AppServerEvent.ProcessOutputDelta,
            is AppServerEvent.ProcessExited,
            is AppServerEvent.FuzzySearchUpdated,
            is AppServerEvent.FuzzySearchCompleted,
            is AppServerEvent.ExternalAgentImportProgress,
            is AppServerEvent.ExternalAgentImportCompleted,
            is AppServerEvent.WindowsSandboxSetupCompleted,
            is AppServerEvent.RealtimeStarted,
            is AppServerEvent.RealtimeClosed,
            is AppServerEvent.RealtimeError,
            is AppServerEvent.RealtimeSdp,
            is AppServerEvent.RealtimeItemAdded,
            is AppServerEvent.RealtimeItemStarted,
            is AppServerEvent.RealtimeItemCompleted,
            is AppServerEvent.RealtimeItemTranscriptDelta,
            is AppServerEvent.RealtimeTranscriptDelta,
            is AppServerEvent.RealtimeTranscriptDone,
            is AppServerEvent.RealtimeOutputAudioDelta,
            -> Unit

            is AppServerEvent.ModelSafetyBufferingUpdated -> onSafetyBuffering(event.delta)

            is AppServerEvent.ThreadClosed -> {
                state.endAllStreams()
                state.applyStatus(ThreadStatus.NotLoaded)
            }
            is AppServerEvent.ThreadArchived, is AppServerEvent.ThreadUnarchived,
            is AppServerEvent.ThreadDeleted,
            -> Unit
        }
    }

    /**
     * Fold an in-flight auto review into the aggregated review notice.
     *
     * Parallel reviews are aggregated the way upstream's `PendingGuardianReviewStatus` does it: one
     * entry per review, keyed by the review id so an update replaces rather than duplicates.
     */
    private fun onReviewStarted(delta: com.cy.codex.protocol.protocol.v2.GuardianApprovalReviewNotification) {
        if (delta.status != "inProgress") return
        val detail = reviewActionSummary(delta.action) ?: return
        val index = reviewsInFlight.indexOfFirst { it.id == delta.reviewId }
        val entry = PendingReview(delta.reviewId, detail)
        if (index >= 0) reviewsInFlight[index] = entry else reviewsInFlight.add(entry)
    }

    /**
     * Drop a finished review and remember a denial so it can be overridden once.
     *
     * A denial is the one review outcome the user can act on: `thread/approveGuardianDeniedAction`
     * needs the serialized assessment, which the client cached under the target item id.
     */
    private fun onReviewCompleted(delta: com.cy.codex.protocol.protocol.v2.GuardianApprovalReviewNotification) {
        reviewsInFlight.removeAll { it.id == delta.reviewId }
        if (delta.status != "denied" || delta.itemId.isBlank()) return
        autoReviewDenials.removeAll { it.itemId == delta.itemId }
        autoReviewDenials.add(
            0,
            AutoReviewDenial(
                threadId = delta.threadId,
                id = delta.reviewId,
                itemId = delta.itemId,
                summary = reviewActionSummary(delta.action).orEmpty(),
                rationale = delta.rationale,
            ),
        )
        while (autoReviewDenials.size > MaxRememberedDenials) {
            autoReviewDenials.removeAt(autoReviewDenials.lastIndex)
        }
    }

    /**
     * Note the safety buffer once per turn.
     *
     * The notification can repeat while the turn waits, and the TUI only re-shows its transient
     * menu when the retry offer changes. The phone has no retry affordance, so one informational
     * notice per turn is the whole behaviour: `showBufferingUi == false` only dismisses the menu.
     */
    private fun onSafetyBuffering(delta: com.cy.codex.protocol.protocol.v2.ModelSafetyBufferingUpdatedNotification) {
        if (!delta.showBufferingUi) return
        if (delta.turnId.isEmpty() || delta.turnId in finishedTurns) return
        if (!safetyBufferedTurns.add(delta.turnId)) return
        while (safetyBufferedTurns.size > MaxRememberedTurns) {
            safetyBufferedTurns.remove(safetyBufferedTurns.first())
        }
        state.addDiagnostic(
            SessionDiagnostic(
                severity = DiagnosticSeverity.Info,
                code = DiagnosticCode.SafetyBuffering,
            ),
        )
    }

    private fun onItemCompleted(item: ThreadItem) {        // Any delta still waiting for its tick belongs to this item; the authoritative text that
        // follows would otherwise be overwritten by a late flush with an older prefix.
        flushDeltas()
        // The completed item carries the authoritative text; the streamed buffer is only a stand-in
        // for the case where it does not (a server that completes an item without a text body).
        state.upsert(withStreamedText(item))
        state.endStream(item.id)
        if (state.streamingItemId == item.id) state.applyStreaming(null)
        // The first completed user message is what a thread title is generated from; the hidden
        // turn itself completes later and arrives as a foreign event this widget never renders.
        if (item is com.cy.codex.protocol.protocol.item.UserMessageItem) maybeGenerateTitle()
        // A finished item can never still be waiting for a decision: either this client answered it,
        // another client did (the server says so through `serverRequest/resolved`), or the server
        // resolved it itself. Leaving the card queued would show the user a decision that no longer
        // has any effect, so the queue is pruned on every completion.
        dropResolvedApprovals(item)
    }

    /**
     * Remove pending requests whose item has finished.
     *
     * Only the item id is needed: the request and the item it belongs to share it, which is what
     * makes this check possible without tracking a separate correlation table.
     */
    private fun dropResolvedApprovals(item: ThreadItem) {
        val settled = when (item) {
            is CommandExecutionItem -> item.status != com.cy.codex.protocol.protocol.v2.CommandExecutionStatus.InProgress
            is com.cy.codex.protocol.protocol.item.FileChangeItem ->
                item.status != com.cy.codex.protocol.protocol.v2.PatchApplyStatus.InProgress

            is McpToolCallItem -> item.status != com.cy.codex.protocol.protocol.v2.McpToolCallStatus.InProgress
            else -> false
        }
        if (!settled) return
        pendingApprovals.removeAll { it.itemId == item.id }
        syncCurrentApproval()
    }

    /**
     * Show the next request after the one on screen was removed from the queue underneath it.
     *
     * Called only when a request is guaranteed gone; a head that is merely waiting out the typing
     * window has `currentApproval == null` already and must not be promoted early.
     */
    private fun syncCurrentApproval() {
        val shown = currentApproval ?: return
        if (pendingApprovals.none { it.requestId == shown.requestId }) {
            currentApproval = pendingApprovals.firstOrNull()
        }
    }

    /**
     * Generate a recap and replace its cell with the answer.
     *
     * Mirrors `app/recap.rs`: the prompt carries only user/assistant exchange, and the result is a
     * client-local transcript cell rather than anything the server persists. Manual requests show a
     * loading cell and report failures; automatic ones are silent and retry once per turn revision.
     */
    private fun generateRecap(trigger: com.cy.codex.app.RecapTrigger) {
        if (recapJob?.isActive == true) return
        if (trigger == com.cy.codex.app.RecapTrigger.Automatic && !com.cy.codex.app.RecapSettings.autoRecap) return
        if (!recap.beginInFlight(trigger)) return
        val threadId = state.threadId
        val history = com.cy.codex.app.recapHistory(state.items)
        if (history == null) {
            recap.finishInFlight()
            if (trigger == com.cy.codex.app.RecapTrigger.Manual) {
                state.addDiagnostic(SessionDiagnostic(severity = DiagnosticSeverity.Info, code = DiagnosticCode.RecapNoHistory))
            }
            return
        }
        val capturedCompletedTurns = recap.completedTurns
        val capturedRevision = recap.turnRevision
        val itemId = "recap-$threadId-${System.currentTimeMillis()}"
        if (trigger == com.cy.codex.app.RecapTrigger.Manual) {
            state.upsert(com.cy.codex.protocol.protocol.item.RecapItem(itemId, text = null))
        }
        recapJob = scope.launch {
            try {
                val result = structuredTurn(
                    client = client,
                    cwd = state.config.cwd,
                    model = state.config.model,
                    developerInstructions = null,
                    prompt = com.cy.codex.app.RecapPromptPrefix + history,
                    outputSchema = com.cy.codex.app.recapOutputSchema(),
                )
                val parsed = com.cy.codex.app.parseRecap(result.getOrNull())
                // Freshness mirrors `handle_generated_recap`: a result computed from a transcript
                // that has since moved on (thread switched, a turn started, focus regained, the
                // setting turned off) is dropped.
                val stale = state.threadId != threadId ||
                    state.running ||
                    recap.completedTurns != capturedCompletedTurns ||
                    recap.turnRevision != capturedRevision ||
                    (
                        trigger == com.cy.codex.app.RecapTrigger.Automatic &&
                            !(com.cy.codex.app.RecapSettings.autoRecap && recap.shouldGenerate(monotonicMs()))
                        )
                if (stale) {
                    if (trigger == com.cy.codex.app.RecapTrigger.Manual) state.remove(itemId)
                    return@launch
                }
                when {
                    parsed != null -> {
                        recap.markRecapped(capturedCompletedTurns)
                        state.upsert(
                            com.cy.codex.protocol.protocol.item.RecapItem(
                                id = itemId,
                                text = parsed.summary,
                                nextAction = parsed.nextAction,
                                failed = false,
                            ),
                        )
                    }

                    trigger == com.cy.codex.app.RecapTrigger.Manual ->
                        state.upsert(
                            com.cy.codex.protocol.protocol.item.RecapItem(id = itemId, text = null, failed = true),
                        )

                    else -> recap.scheduleRetry(scope, threadId, capturedRevision) { onRecapCheck(it) }
                }
            } finally {
                recap.finishInFlight()
                recapJob = null
            }
        }
    }

    /** The deferred automatic check; the widget's state may have moved while the timer was armed. */
    private fun onRecapCheck(threadId: String) {
        if (!com.cy.codex.app.RecapSettings.autoRecap) return
        if (!state.open || state.threadId != threadId || state.running) return
        if (!recap.shouldGenerate(monotonicMs())) return
        generateRecap(com.cy.codex.app.RecapTrigger.Automatic)
    }

    private fun scheduleRecapCheck() {
        recap.scheduleCheck(
            scope = scope,
            threadId = state.threadId,
            nowMs = monotonicMs(),
            enabled = com.cy.codex.app.RecapSettings.autoRecap,
        ) { onRecapCheck(it) }
    }

    /**
     * The Android equivalent of terminal focus, for the auto-recap idle clock.
     *
     * Focus gained cancels the timer and abandons an in-flight automatic recap; focus lost starts
     * the idle window and re-arms the scheduler.
     */
    fun noteForegroundChanged(inForeground: Boolean) {
        if (inForeground) {
            recap.noteFocusGained()
        } else {
            recap.noteFocusLost(monotonicMs())
            scheduleRecapCheck()
        }
    }

    /**
     * Generate and apply an automatic title once the thread has its first user message.
     *
     * Mirrors `app/thread_title.rs`: the request runs on an ephemeral structured thread, only sets
     * a name the thread still does not have, and a manual rename wins because the name is re-checked
     * when the generated one comes back.
     */
    private fun maybeGenerateTitle() {
        val threadId = state.threadId
        if (threadId.isBlank() || !state.open) return
        if (!state.config.threadName.isNullOrBlank()) return
        if (!titleRequests.add(threadId)) return
        state.markTitleGenerationPending(true)
        scope.launch {
            try {
                val prompt = firstUserMessageText(state.items) ?: return@launch
                val result = structuredTurn(
                    client = client,
                    cwd = state.config.cwd,
                    model = state.config.model,
                    developerInstructions = null,
                    prompt = com.cy.codex.app.threadTitlePrompt(prompt),
                    outputSchema = com.cy.codex.app.threadTitleOutputSchema(),
                    effort = com.cy.codex.protocol.protocol.v2.ReasoningEffort.Low,
                )
                val title = com.cy.codex.app.parseThreadTitle(result.getOrNull()) ?: return@launch
                if (state.threadId != threadId || !state.config.threadName.isNullOrBlank()) return@launch
                client.setThreadName(threadId, title).onSuccess {
                    if (state.threadId == threadId) {
                        state.applyConfig(state.config.copy(threadName = title))
                    }
                }
            } finally {
                titleRequests.remove(threadId)
                if (state.threadId == threadId) state.markTitleGenerationPending(false)
            }
        }
    }

    /**
     * Re-submit the safety stop's steer with the misalignment override.
     *
     * Mirrors `app/misalignment_policy.rs`: the steer text is the continuation message, a new turn
     * needs the override metadata, and the gate only lifts once that turn starts. The steer is
     * bounded to 1,024 characters, and a stop without one cannot be continued.
     */
    private fun continueMisalignment() {
        val steer = state.misalignment?.steer?.message
            ?.takeIf { it.isNotBlank() && it.length <= 1024 }
            ?: return
        val threadId = state.threadId
        state.applyMisalignment(null)
        state.applyStatus(ThreadStatus.Active())
        scope.launch {
            client.startTurn(
                threadId = threadId,
                inputs = listOf(UserInput.Text(steer)),
                clientMetadata = mapOf(
                    "misalignment_override" to "{\"timestamp\":${System.currentTimeMillis()}}",
                ),
            ).onFailure { error ->
                if (state.threadId != threadId) return@onFailure
                state.applyStatus(ThreadStatus.Idle)
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = DiagnosticCode.SendFailed,
                        detail = error.message,
                    ),
                )
            }
        }
    }

    private fun onTurnCompleted(event: AppServerEvent.TurnCompleted) {
        flushDeltas()
        state.applyStatus(ThreadStatus.Idle)
        // Recap bookkeeping sees the turn's final status before any deferred check runs; the timer
        // is re-armed from the new completion count and idle deadline.
        recap.noteTurnFinished(event.status, monotonicMs())
        scheduleRecapCheck()
        // A safety stop is not an ordinary failure: it holds input until the user reviews or
        // confirms continuing, and the server dropped whatever was queued.
        state.applyMisalignment(event.misalignment)
        if (event.misalignment != null) {
            state.queued.clear()
            refreshQueue(event.threadId)
        }
        // The agent is now waiting for the user; announce that only when nothing else will start a
        // turn on its own. The preview is the first line of the last answer, the same thing the
        // TUI puts in its `AgentTurnComplete` payload.
        _notices.tryEmit(
            AgentNotice.TurnComplete(
                threadId = event.threadId,
                preview = (state.items.lastOrNull { it is AgentMessageItem } as? AgentMessageItem)
                    ?.text
                    ?.lineSequence()
                    ?.firstOrNull { it.isNotBlank() }
                    ?.take(200),
            ),
        )
        // An interrupted turn may never complete its last item; its buffered deltas are folded back
        // into the item before the stream flag is cleared.
        state.settleStreams()
        state.applyStreaming(null)
        // A completed turn gets the same divider upstream draws after the final answer. Its label
        // is built from the notification's own duration and completion time, so a turn loaded from
        // history (which rebuilds this from `Turn.durationMs`) and a live one read the same.
        if (event.status == TurnStatus.Completed) {
            val label = com.cy.codex.history_cell.finalMessageSeparatorLabel(
                elapsedSeconds = event.durationMs?.let { it / 1000 },
                completedAtMillis = event.completedAt,
            )
            if (label != null) {
                state.appendTurnSeparator(
                    com.cy.codex.protocol.protocol.item.TurnSeparatorItem(
                        id = com.cy.codex.history_cell.TurnSeparatorIdPrefix + event.turnId,
                        label = label,
                    ),
                )
            }
        }
        refreshQueue(event.threadId)
        // Every approval belongs to the turn that asked for it. Card families with no item of their
        // own (`request_user_input`, permissions, elicitation) are settled exactly this way: when the
        // turn ends, nothing is waiting any more.
        finishedTurns.addLast(event.turnId)
        while (finishedTurns.size > MaxRememberedTurns) finishedTurns.removeFirst()
        pendingApprovals.removeAll { it.turnId == event.turnId }
        syncCurrentApproval()
        if (event.status != TurnStatus.Completed) {
            // Anything still running when an interrupted or failed turn ends will never receive its
            // `item/completed`, so the cards are closed out here rather than left spinning.
            state.failInProgressItems()
            // The notice is named by code, not by text: the status label is a string resource and
            // this reducer is not composable. `TurnFinished` carries the status so the transcript
            // can name it through the label it already has.
            val (code, args) = when (event.status) {
                TurnStatus.Interrupted -> DiagnosticCode.TurnInterrupted to emptyList()
                TurnStatus.Failed -> DiagnosticCode.TurnFailed to emptyList()
                else -> DiagnosticCode.TurnFinished to listOf(event.status.name)
            }
            state.addDiagnostic(
                SessionDiagnostic(
                    severity = if (event.status == TurnStatus.Interrupted) {
                        DiagnosticSeverity.Warning
                    } else {
                        DiagnosticSeverity.Error
                    },
                    code = code,
                    args = args,
                    detail = event.error,
                ),
            )
        }
        // The turn is over, so the payload it accumulated is never extended again. [SessionState]
        // keeps the parsed diff for the status card; only the accumulator's memory is released.
        turnDiff.reset()
        patchChanges.clear()
        // The working tree may have moved during the turn; the status line follows it.
        refreshGitSummary(event.threadId)
    }

    private fun refreshHistory(threadId: String) {
        val version = loadVersion
        scope.launch {
            repeat(3) {
                val revision = eventRevision
                val response = client.readThread(com.cy.codex.protocol.protocol.v2.ThreadReadParams(threadId)).getOrElse {
                    if (version == loadVersion) state.addDiagnostic(SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = DiagnosticCode.ThreadLoadFailed,
                        detail = it.message,
                    ))
                    return@launch
                }
                if (version != loadVersion || state.threadId != threadId) return@launch
                if (revision == eventRevision) {
                    state.items.clear()
                    // This snapshot follows a compact or revert, so the old page cursor names a
                    // turn that may no longer exist; the transcript is whole again after this read.
                    nextTurnCursor = null
                    val transcript = if (response.turns.isNotEmpty()) {
                        com.cy.codex.history_cell.transcriptWithSeparators(response.turns)
                    } else {
                        response.items
                    }
                    transcript.forEach(state::upsert)
                    state.applyStatus(response.thread.status)
                    // A snapshot can be older than the live stream, so the buffer for the item
                    // still streaming is kept; only buffers for items this client no longer has
                    // are dropped.
                    val keep = response.items.mapTo(mutableSetOf()) { it.id }
                    state.streamingItemId?.let(keep::add)
                    state.retainStreams(keep)
                    return@launch
                }
            }
        }
    }

    /**
     * Fill in a text body from the item's stream when the completion event left it empty.
     *
     * This materializes the buffer once, at the end of a message, which is the only point where the
     * whole text is needed: until then the transcript renders the parsed blocks instead.
     */
    private fun withStreamedText(item: ThreadItem): ThreadItem = when {
        item is AgentMessageItem && item.text.isEmpty() ->
            state.streamText(item.id)?.let { item.copy(text = it) } ?: item

        item is PlanItem && item.text.isEmpty() ->
            state.streamText(item.id)?.let { item.copy(text = it) } ?: item

        else -> item
    }

    private fun appendReasoningText(itemId: String, delta: String) {
        val item = state.item(itemId) as? ReasoningItem
        if (item == null) {
            state.upsert(ReasoningItem(id = itemId, summary = listOf(delta)))
            return
        }
        val summary = item.summary.toMutableList()
        if (summary.isEmpty()) summary.add(delta) else summary[summary.lastIndex] += delta
        state.upsert(item.copy(summary = summary))
    }

    private fun appendCommandOutput(itemId: String, delta: String) {
        val item = state.item(itemId) as? CommandExecutionItem ?: return
        state.upsert(item.copy(aggregatedOutput = (item.aggregatedOutput ?: "") + delta))
    }

    private fun appendMcpProgress(itemId: String, delta: String) {
        val item = state.item(itemId) as? McpToolCallItem ?: return
        state.upsert(item.copy(result = (item.result ?: "") + delta))
    }

    private companion object {
        /** How many completed turns are remembered for late-approval filtering. */
        const val MaxRememberedTurns = 8

        /** How long the composer must be untouched before an approval dialog may appear. */
        const val ApprovalTypingIdleDelayMs = 1_000L

        /** How many auto-review denials stay overridable, mirroring `auto_review_denials.rs`. */
        const val MaxRememberedDenials = 10
    }
}

private fun com.cy.codex.protocol.protocol.v2.Thread.threadIdOr(fallback: String): String =
    id.ifEmpty { fallback }

/** Which approval-notification wording a server request earns. */
private fun approvalNoticeKind(request: ApprovalRequest): ApprovalNoticeKind = when (request) {
    is ApprovalRequest.Exec -> ApprovalNoticeKind.Command
    is ApprovalRequest.ApplyPatch -> ApprovalNoticeKind.FileChange
    is ApprovalRequest.Elicitation -> ApprovalNoticeKind.Elicitation
    else -> ApprovalNoticeKind.Other
}

/** The command, path or server the notification names, when the request carries one. */
private fun approvalNoticeDetail(request: ApprovalRequest): String? = when (request) {
    is ApprovalRequest.Exec -> request.params.command
    is ApprovalRequest.ApplyPatch -> request.params.grantRoot
    is ApprovalRequest.Elicitation -> request.params.serverName
    else -> null
}

/**
 * One-line summary of the action an auto review is judging.
 *
 * Mirrors `tui/src/auto_review_denials.rs::action_summary`: the strings are the wire's own nouns
 * (commands, paths, hosts), so they stay in the action's language rather than being translated.
 * Returns `null` for a shape this client does not know, which keeps it out of the review notice
 * instead of showing an empty bullet.
 */
private fun reviewActionSummary(action: kotlinx.serialization.json.JsonElement?): String? {
    val o = action as? kotlinx.serialization.json.JsonObject ?: return null
    fun str(key: String): String? =
        (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    fun strs(key: String): List<String> =
        (o[key] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content }

    val type = str("type") ?: return null
    return when (type) {
        "command" -> str("command")
        "execve" -> {
            val program = str("program") ?: return null
            (listOf(program) + strs("argv")).joinToString(" ")
        }

        "writeStdin" -> {
            val processId = str("processId") ?: return null
            "send input to terminal $processId: ${str("stdin").orEmpty()}"
        }

        "applyPatch" -> {
            val files = strs("files")
            when (files.size) {
                0 -> "apply_patch"
                1 -> "apply_patch touching ${files[0]}"
                else -> "apply_patch touching ${files.size} files"
            }
        }

        "networkAccess" -> "network access to ${str("target") ?: str("host") ?: return null}"
        "mcpToolCall" -> {
            val tool = str("toolName") ?: return null
            val label = str("connectorName") ?: str("server") ?: return null
            "MCP $tool on $label"
        }

        "requestPermissions" -> str("reason")?.let { "permission request: $it" } ?: "permission request"
        else -> null
    }
}
