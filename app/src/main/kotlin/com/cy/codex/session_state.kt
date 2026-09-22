package com.cy.codex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.DynamicToolCallItem
import com.cy.codex.protocol.protocol.item.FileChangeItem
import com.cy.codex.protocol.protocol.item.ImageGenerationItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.PlanItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.item.TurnSeparatorItem
import com.cy.codex.protocol.protocol.v2.CollabAgentToolCallStatus
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codex.protocol.protocol.v2.DynamicToolCallStatus
import com.cy.codex.protocol.protocol.v2.McpToolCallStatus
import com.cy.codex.protocol.protocol.v2.PatchApplyStatus
import com.cy.codex.protocol.protocol.v2.AccountReadResponse
import com.cy.codex.protocol.protocol.v2.AccountUsage
import com.cy.codex.protocol.protocol.v2.AppInfo
import com.cy.codex.protocol.protocol.v2.ApprovalsReviewer
import com.cy.codex.protocol.protocol.v2.CollaborationModeEntry
import com.cy.codex.protocol.protocol.v2.ConfigReadResponse
import com.cy.codex.protocol.protocol.v2.ConfigWriteResponse
import com.cy.codex.protocol.protocol.v2.ConfigSnapshot
import com.cy.codex.protocol.protocol.v2.DiagnosticSeverity
import com.cy.codex.protocol.protocol.v2.ExperimentalFeatureEntry
import com.cy.codex.protocol.protocol.v2.ExternalAgentConfigImportHistory
import com.cy.codex.protocol.protocol.v2.ExternalAgentConfigMigrationItem
import com.cy.codex.protocol.protocol.v2.FuzzyFileSearchResult
import com.cy.codex.protocol.protocol.v2.HookErrorInfo
import com.cy.codex.protocol.protocol.v2.HookMetadata
import com.cy.codex.protocol.protocol.v2.LoginAccountResponse
import com.cy.codex.protocol.protocol.v2.MarketplaceEntry
import com.cy.codex.protocol.protocol.v2.McpServerStatusEntry
import com.cy.codex.protocol.protocol.v2.MemoryStatusResponse
import com.cy.codex.protocol.protocol.v2.ModelPreset
import com.cy.codex.protocol.protocol.v2.AppSummary
import com.cy.codex.protocol.protocol.v2.PlanStep
import com.cy.codex.protocol.protocol.v2.PluginAuthPolicy
import com.cy.codex.protocol.protocol.v2.PluginEntry
import com.cy.codex.protocol.protocol.v2.PluginShareEntry
import com.cy.codex.protocol.protocol.v2.ProjectEntry
import com.cy.codex.protocol.protocol.v2.QueuedSubmission
import com.cy.codex.protocol.protocol.v2.AccountRateLimits
import com.cy.codex.protocol.protocol.v2.RemoteControlClient
import com.cy.codex.protocol.protocol.v2.RemoteControlStatus
import com.cy.codex.protocol.protocol.v2.ServerDiagnosticsResponse
import com.cy.codex.protocol.protocol.v2.SkillEntry
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.ThreadGoalUpdated
import com.cy.codex.protocol.protocol.v2.ThreadListing
import com.cy.codex.protocol.protocol.v2.ThreadSection
import com.cy.codex.protocol.protocol.v2.ThreadSessionState
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.protocol.protocol.v2.ThreadTokenUsage
import com.cy.codex.protocol.protocol.v2.UserInput
import com.cy.codex.protocol.protocol.v2.UserVerificationEnrollResponse
import com.cy.codex.protocol.protocol.v2.UserVerificationStatusResponse
import com.cy.codex.protocol.protocol.v2.WindowsSandboxReadiness

/**
 * Canonical session state, shared by the transcript, the status card and the sidebar.
 *
 * Mirrors `codex-rs/tui/src/session_state.rs` plus the observable half of `chatwidget.rs`: the
 * TUI keeps `ThreadSessionState` as the shape app orchestration reads and the chat widget as the
 * thing that mutates it. On the phone both live here because Compose reads them directly.
 */
class SessionState {

    /** Thread handshake state: which thread is open and whether its history has arrived. */
    var threadId by mutableStateOf("")
        private set

    var open by mutableStateOf(false)
        private set

    var loading by mutableStateOf(false)
        private set

    var config by mutableStateOf(ThreadSessionState(threadId = ""))
        private set

    var status by mutableStateOf<ThreadStatus>(ThreadStatus.NotLoaded)
        private set

    var running by mutableStateOf(false)
        private set

    /** When the running turn started, for the activity indicator's timer. */
    var turnStartedAtMs by mutableStateOf<Long?>(null)
        private set

    /** True while a hidden turn is generating this thread's automatic title. */
    var titleGenerationPending by mutableStateOf(false)
        private set

    /** Git branch / PR / diff totals of the session workspace, refreshed per turn. */
    var gitSummary by mutableStateOf<com.cy.codex.app.GitSummary?>(null)
        private set

    fun applyGitSummary(value: com.cy.codex.app.GitSummary?) {
        gitSummary = value
    }

    /**
     * A safety stop waiting on the user, or null.
     *
     * While it is set the composer is blocked and a new turn only starts through the explicit
     * continue action, the way `chatwidget/misalignment_policy.rs` gates input.
     */
    var misalignment by mutableStateOf<com.cy.codex.protocol.protocol.v2.MisalignmentErrorDetails?>(null)
        private set

    fun applyMisalignment(value: com.cy.codex.protocol.protocol.v2.MisalignmentErrorDetails?) {
        misalignment = value
    }

    fun markTitleGenerationPending(value: Boolean) {
        titleGenerationPending = value
    }

    /**
     * The hook currently executing, named the way the indicator shows it.
     *
     * `hook/started` and `hook/completed` carry a whole run summary; the detail line only needs a
     * name, and the last started hook wins while several run in parallel.
     */
    var hookStatus by mutableStateOf<String?>(null)
        private set

    /** Items in arrival order; deltas mutate the item they name in place. */
    val items = mutableStateListOf<ThreadItem>()

    /**
     * Monotonic revision of the [items] list, bumped by every mutation of it.
     *
     * Composables that fold the whole transcript into a derived list (the agent roster) key that
     * fold on this counter instead of reading the list themselves: reading the list in a screen's
     * scope subscribes the whole screen to every streaming delta, while this counter lets the fold
     * live in a derived state that only notifies its readers when the folded value changed.
     */
    var itemsRevision by mutableIntStateOf(0)
        private set

    /** The plan the agent is working through, from `turn/plan/updated`. */
    val plan = mutableStateListOf<PlanStep>()

    /**
     * Whole-turn diff, folded from `turn/diff/updated`.
     *
     * Identity comparison is deliberate: the accumulator returns the very same list while the
     * payload is unchanged and a fresh one as soon as an append changed a file, so an identity
     * check is both cheaper than comparing every line and exactly the invalidation the readers
     * need.
     */
    var turnDiff by mutableStateOf<List<FileDiff>>(emptyList(), referentialEqualityPolicy())
        private set

    var usage by mutableStateOf(ThreadTokenUsage.Empty)
        private set

    /** Objective of the running goal, when goal mode is on. */
    var goal by mutableStateOf<ThreadGoalUpdated?>(null)
        private set

    /**
     * Messages the user submitted while a turn was already running.
     *
     * The queue belongs to the server — `thread/queue/changed` is only a poke, so this list is
     * whatever the last `thread/queue/list` returned. Keeping the ids (rather than bare text) is
     * what lets the queue rows be reordered and deleted.
     */
    val queued = mutableStateListOf<QueuedSubmission>()

    /** Diagnostics surfaced as transcript notices. */
    val diagnostics = mutableStateListOf<SessionDiagnostic>()

    /**
     * Model slugs whose fallback-metadata warning has already been shown.
     *
     * Mirrors `chatwidget/warnings.rs`: the server repeats that warning once per turn and the slug
     * is the only part that varies, so it is deduplicated by slug. Every other diagnostic is kept
     * as-is.
     */
    private val fallbackModelMetadataSlugs = mutableSetOf<String>()

    /** Model label shown on the status card; reroutes update it. */
    var activeModelLabel by mutableStateOf("")

    /**
     * Files and images attached to this thread but not yet sent.
     *
     * The list belongs to the server — `thread/attachment/add` answers with the stored record — so
     * this is whatever the last read or write returned, never a locally invented entry.
     */
    val attachments = mutableStateListOf<com.cy.codex.protocol.protocol.v2.ThreadAttachment>()

    /** Long-running terminals the session started; the footer lists them. */
    val backgroundTerminals =
        mutableStateListOf<com.cy.codex.protocol.protocol.v2.ThreadBackgroundTerminal>()

    /**
     * Text the composer is holding.
     *
     * Held here rather than in the composer's own `remember` because two other things write it: a
     * slash command that prefills an argument, and a transcript row that offers "quote this". Both
     * outlive the composer's composition, so the draft has to.
     */
    var composerDraft by mutableStateOf("")
        private set

    /**
     * Local images the draft is holding, in placeholder order.
     *
     * The `[Image #N]` placeholder lives in [composerDraft]; this list is what turns it into a
     * `UserInput.LocalImage` on submission. Draft-level, unlike [attachments], which the server
     * owns for the thread.
     */
    val composerImages = mutableStateListOf<ComposerImageAttachment>()

    fun applyDraft(text: String) {
        composerDraft = text
        // A placeholder that was edited away drops its attachment, the way deleting the atomic
        // element in the TUI's textarea does. Backspace over `[Image #1]` therefore detaches it
        // without a separate gesture.
        if (composerImages.any { it.placeholder !in text }) {
            composerImages.removeAll { it.placeholder !in text }
        }
    }

    /** Stage [path] and return the placeholder the caller inserts into the draft. */
    fun addComposerImage(path: String): String {
        val placeholder = imagePlaceholder(composerImages.size + 1)
        composerImages.add(ComposerImageAttachment(path, placeholder))
        return placeholder
    }

    /**
     * Drop the image at [path] (if any) and renumber the rest.
     *
     * Renumbering rewrites every remaining placeholder in the draft, the way the TUI's
     * `relabel_local_images` keeps the labels contiguous after a removal.
     */
    fun removeComposerImage(path: String) {
        val index = composerImages.indexOfFirst { it.path == path }
        if (index < 0) return
        val removed = composerImages.removeAt(index)
        var text = composerDraft.replace(removed.placeholder, "")
        for (i in composerImages.indices) {
            val image = composerImages[i]
            val next = imagePlaceholder(i + 1)
            if (next != image.placeholder) {
                text = text.replace(image.placeholder, next)
                composerImages[i] = image.copy(placeholder = next)
            }
        }
        applyDraft(text)
    }

    fun clearComposerImages() {
        composerImages.clear()
    }

    /**
     * Build the inputs the current draft would submit.
     *
     * One `LocalImage` per placeholder still present in the text, then the text itself carrying the
     * placeholder byte ranges. Empty text with no images yields an empty list, which is the
     * "nothing to send" the submit path refuses.
     */
    fun pendingTurnInputs(): List<UserInput> {
        val text = composerDraft
        val elements = placeholderTextElements(text, composerImages.map { it.placeholder })
        val kept = composerImages.filter { image -> elements.any { it.placeholder == image.placeholder } }
        val textInput = if (text.isEmpty()) emptyList() else listOf(UserInput.Text(text, elements))
        return kept.map { UserInput.LocalImage(it.path) } + textInput
    }

    /** Transcript text currently streaming into the last agent message, if any. */
    var streamingItemId by mutableStateOf<String?>(null)
        private set

    /**
     * Incremental markdown buffers, keyed by the item id the deltas name.
     *
     * A delta appends here instead of replacing the item: `item.copy(text = item.text + delta)`
     * copied the whole answer on every token, and the renderer had to re-parse the result. The
     * buffer owns the parsed blocks, so a delta costs the tail block and nothing else. Entries are
     * dropped when the item completes or the thread changes; a snapshot write (history refresh)
     * leaves them alone because the snapshot can be older than the stream.
     */
    private val streamBuffers = mutableStateMapOf<String, MarkdownStream>()

    internal fun stream(id: String): MarkdownStream? = streamBuffers[id]

    /** Whole streamed text, materialized only when an item completes without a text body. */
    internal fun streamText(id: String): String? = streamBuffers[id]?.text

    internal fun endStream(id: String) {
        streamBuffers.remove(id)
    }

    /**
     * Freeze every buffer that is still open into its item and drop it.
     *
     * An interrupted turn can end without `item/completed`, and an item whose text never lands in
     * the list would be invisible to everything that reads `ThreadItem.text` — copy, search, the
     * session preview. This materializes the buffer once, at the only moment the streaming ends
     * without the item saying so itself.
     */
    internal fun settleStreams() {
        if (streamBuffers.isEmpty()) return
        for ((id, stream) in streamBuffers) {
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) continue
            val filled = when (val item = items[index]) {
                is AgentMessageItem -> if (item.text.isEmpty()) item.copy(text = stream.text) else null
                is PlanItem -> if (item.text.isEmpty()) item.copy(text = stream.text) else null
                else -> null
            }
            if (filled != null) {
                items[index] = filled
                itemsRevision++
            }
        }
        streamBuffers.clear()
    }

    internal fun endAllStreams() {
        streamBuffers.clear()
    }

    /** Drop buffers for items a history refresh no longer has, keeping the live one. */
    internal fun retainStreams(ids: Set<String>) {
        streamBuffers.keys.retainAll(ids)
    }

    /** Append one agent-message delta and make its item the streaming one. */
    internal fun appendAgentDelta(itemId: String, delta: String) {
        streamBuffers.getOrPut(itemId) { MarkdownStream() }.append(delta)
        if (items.none { it.id == itemId }) {
            items.add(AgentMessageItem(id = itemId, text = ""))
            itemsRevision++
        }
        applyStreaming(itemId)
    }

    /** Append one plan-text delta; the plan body is markdown just like an agent message. */
    internal fun appendPlanDelta(itemId: String, delta: String) {
        streamBuffers.getOrPut(itemId) { MarkdownStream() }.append(delta)
        if (items.none { it.id == itemId }) {
            items.add(PlanItem(id = itemId, text = ""))
            itemsRevision++
        }
    }

    fun bindThread(id: String, state: ThreadSessionState) {
        threadId = id
        config = state
        open = true
        loading = false
        status = ThreadStatus.Idle
        activeModelLabel = state.modelDisplayName
    }

    fun beginLoad(id: String) {
        threadId = id
        open = false
        loading = true
        running = false
        streamingItemId = null
        streamBuffers.clear()
        attachments.clear()
        backgroundTerminals.clear()
        items.clear()
        itemsRevision++
        plan.clear()
        turnDiff = emptyList()
        usage = ThreadTokenUsage.Empty
        goal = null
        queued.clear()
        diagnostics.clear()
        fallbackModelMetadataSlugs.clear()
        misalignment = null
        gitSummary = null
        status = ThreadStatus.NotLoaded
    }

    fun failLoad(message: String?) {
        loading = false
        open = false
        status = ThreadStatus.SystemError(message.orEmpty())
    }

    fun clear() {
        beginLoad("")
        loading = false
        config = config.copy(threadId = "")
    }

    fun applyStatus(value: ThreadStatus) {
        val wasRunning = running
        status = value
        running = value is ThreadStatus.Active
        if (running && !wasRunning) turnStartedAtMs = System.currentTimeMillis()
        if (!running) turnStartedAtMs = null
    }

    /** Remember a running hook for the activity indicator. */
    fun applyHookStarted(name: String?) {
        hookStatus = name?.takeIf { it.isNotBlank() }
    }

    /** The named hook finished; clear it only when it is still the one on display. */
    fun applyHookCompleted() {
        hookStatus = null
    }

    fun applyConfig(value: ThreadSessionState) {
        config = value
        activeModelLabel = value.modelDisplayName
    }

    fun applyUsage(value: ThreadTokenUsage) {
        usage = value
    }

    fun applyTurnDiff(value: List<FileDiff>) {
        turnDiff = value
    }

    fun applyPlan(value: List<PlanStep>) {
        plan.clear()
        plan.addAll(value)
    }

    fun applyGoal(value: ThreadGoalUpdated?) {
        goal = value
    }

    fun applyStreaming(id: String?) {
        streamingItemId = id
    }

    /**
     * Drop one item by id.
     *
     * Used when a manual recap's loading cell must disappear because the result turned out to be
     * stale; nothing the server owns is ever removed from the transcript.
     */
    fun remove(itemId: String) {
        if (items.removeAll { it.id == itemId }) itemsRevision++
    }

    /** Append or replace one item, preserving arrival order. */
    fun upsert(item: ThreadItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index < 0) items.add(item) else items[index] = item
        itemsRevision++
    }

    /**
     * Add [item] only when the transcript has never seen its id.
     *
     * Used for a `thread/read` snapshot that lands while the thread is still streaming. Every item
     * already in the list arrived through the live stream, so the local copy is at least as new as
     * the snapshot's; the snapshot is only good for the history this client has not seen yet.
     */
    fun addIfAbsent(item: ThreadItem) {
        if (items.none { it.id == item.id }) {
            items.add(item)
            itemsRevision++
        }
    }

    /**
     * Put older items in front of the transcript, as a `thread/items/list` page does.
     *
     * Items already present are dropped: a backwards page can overlap the snapshot `thread/read`
     * returned, and the copy already here is the one the live stream has been reconciled against.
     */
    fun prepend(older: List<ThreadItem>) {
        val fresh = older.filter { candidate -> items.none { it.id == candidate.id } }
        if (fresh.isEmpty()) return
        items.addAll(0, fresh)
        itemsRevision++
    }

    fun item(id: String): ThreadItem? = items.firstOrNull { it.id == id }

    /** Append the divider that closes a turn; a second one for the same turn is dropped. */
    fun appendTurnSeparator(item: TurnSeparatorItem) {
        if (items.any { it.id == item.id }) return
        items.add(item)
        itemsRevision++
    }

    /**
     * Mark every still-running tool item failed.
     *
     * An interrupted or failed turn can end without `item/completed` for the tools it had open,
     * and without this the cards stay "Running" forever. Upstream does the same to its active cell
     * in `finalize_active_cell_as_failed` when `finalize_turn` runs on an error path.
     */
    fun failInProgressItems() {
        var changed = false
        for (index in items.indices) {
            val failed = when (val item = items[index]) {
                is CommandExecutionItem ->
                    if (item.status == CommandExecutionStatus.InProgress) item.copy(status = CommandExecutionStatus.Failed) else null

                is FileChangeItem ->
                    if (item.status == PatchApplyStatus.InProgress) item.copy(status = PatchApplyStatus.Failed) else null

                is McpToolCallItem ->
                    if (item.status == McpToolCallStatus.InProgress) item.copy(status = McpToolCallStatus.Failed) else null

                is DynamicToolCallItem ->
                    if (item.status == DynamicToolCallStatus.InProgress) item.copy(status = DynamicToolCallStatus.Failed) else null

                is CollabAgentToolCallItem ->
                    if (item.status == CollabAgentToolCallStatus.InProgress) item.copy(status = CollabAgentToolCallStatus.Failed) else null

                is ImageGenerationItem ->
                    if (item.status == DynamicToolCallStatus.InProgress) item.copy(status = DynamicToolCallStatus.Failed) else null

                else -> null
            }
            if (failed != null) {
                items[index] = failed
                changed = true
            }
        }
        if (changed) itemsRevision++
    }

    fun addDiagnostic(diagnostic: SessionDiagnostic) {
        val slug = fallbackModelMetadataWarningSlug(diagnostic.message)
        if (slug != null && !fallbackModelMetadataSlugs.add(slug)) return
        diagnostics.add(diagnostic)
    }
}

/**
 * The model slug of a `Model metadata for ...` warning, or `null` for any other message.
 *
 * The prefix and suffix are the exact strings `codex-rs/core/src/session/turn_context.rs` builds,
 * and the slug between them is what upstream deduplicates on.
 */
internal fun fallbackModelMetadataWarningSlug(message: String?): String? {
    val text = message ?: return null
    if (!text.startsWith(FallbackModelMetadataPrefix) || !text.endsWith(FallbackModelMetadataSuffix)) {
        return null
    }
    return text.removePrefix(FallbackModelMetadataPrefix)
        .removeSuffix(FallbackModelMetadataSuffix)
        .ifEmpty { null }
}

private const val FallbackModelMetadataPrefix = "Model metadata for `"
private const val FallbackModelMetadataSuffix =
    "` not found. Defaulting to fallback metadata; this can degrade performance and cause issues."

/**
 * Which notice a [SessionDiagnostic] is, when the wording is the client's own.
 *
 * The reducer is not composable, so it cannot read a string resource: it records *which* notice
 * happened, and the transcript resolves the code — plus [SessionDiagnostic.args] — through
 * `R.string.chatwidget_diagnostic_*` while it composes the cell. Text that came off the wire stays
 * in [SessionDiagnostic.message] instead, because no resource can name it.
 */
enum class DiagnosticCode {
    /** Opening a thread failed. */
    ThreadLoadFailed,

    /** Creating a thread failed. */
    NewThreadFailed,

    /** Starting a turn failed. */
    SendFailed,

    /** Interrupting a turn failed. */
    InterruptFailed,

    /** The server rerouted the turn to another model. */
    ModelSwitched,

    /** The turn was interrupted. */
    TurnInterrupted,

    /** The turn failed. */
    TurnFailed,

    /** The turn ended with a status that is neither of the two above. */
    TurnFinished,

    /**
     * A directory below the working root is writable by every user on the machine.
     *
     * The server scans for this on Windows and reports the first offending path plus how many more
     * it found, which is why the notice takes two arguments.
     */
    WorldWritable,

    /** The review policy demanded a stricter review before this turn could proceed. */
    StrictReviewRequired,

    /** A hook failed; the notice names it. */
    HookFailed,

    /** An MCP server's OAuth flow failed; the notice names the server. */
    McpLoginFailed,

    /**
     * The server put the turn behind its safety buffer.
     *
     * This is a pause rather than a failure: the notice exists so a quiet turn does not read as a
     * hung one.
     */
    SafetyBuffering,

    /**
     * A rate-limit window crossed a warning threshold.
     *
     * Arguments are the percent still available and the window label, because "less than 10% of the
     * primary limit left" needs both.
     */
    RateLimitWarning,

    /** A rate-limit window hit 100%; requests queue until it resets. The argument is its label. */
    RateLimitReached,

    /** A pasted image exceeded the 32 MiB transport limit; the notice names the file. */
    ImageTooLarge,

    /** `/recap` ran before any user/assistant exchange existed. */
    RecapNoHistory,
}

/** One warning or error the transcript shows as a notice cell. */
data class SessionDiagnostic(
    val severity: DiagnosticSeverity,
    /**
     * Text the transport supplied, e.g. `error.message`, or `null` when the notice is the client's
     * own wording and [code] names it.
     */
    val message: String? = null,
    val detail: String? = null,
    /** The notice the client is reporting, for text that has a string resource. */
    val code: DiagnosticCode? = null,
    /**
     * Positional format arguments for the resource [code] resolves to, in placeholder order.
     *
     * `TurnFinished` carries the `TurnStatus` enum constant name, so the UI can name the status
     * through the label it already has instead of the model reaching for one.
     */
    val args: List<String> = emptyList(),
)

/** Another thread blocked on a decision the open transcript cannot answer. */
data class ForeignApproval(val threadId: String, val count: Int)

/** One auto-review that is deciding a request right now. */
data class PendingReview(val id: String, val detail: String)

/**
 * An auto-review denial the user may override for one retry.
 *
 * Mirrors `chatwidget/auto_review_denials.rs`: only the most recent entries are kept, and
 * `thread/approveGuardianDeniedAction` needs the serialized assessment the client cached when the
 * review completed — [itemId] is how that cache is addressed.
 */
data class AutoReviewDenial(
    val threadId: String,
    val id: String,
    val itemId: String,
    val summary: String,
    val rationale: String? = null,
)

/**
 * How far an `externalAgentConfig/import` has got.
 *
 * A local shape rather than a protocol one: the two import notifications are the only pair on the
 * wire that carry progress without a payload type of their own, so there is nothing to reuse.
 */
data class ImportProgress(val imported: Int, val total: Int, val label: String)

/**
 * Catalog state behind the settings, MCP, skill and plugin pages.
 *
 * Mirrors the pickers the TUI opens from `bottom_pane/{model_popups,permission_popups,
 * settings_popups,plugin_catalog}.rs`; the phone keeps them in one place because the surfaces are
 * separate screens rather than mutually exclusive overlays.
 */
/** The post-install connector setup: which plugin, which apps, and when auth is requested. */
data class PluginInstallAuthFlow(
    val pluginName: String,
    val apps: List<AppSummary>,
    val authPolicy: PluginAuthPolicy,
)

class CatalogState {
    var models by mutableStateOf<List<ModelPreset>>(emptyList())
    var experimentalFeatures by mutableStateOf<List<ExperimentalFeatureEntry>>(emptyList())
    var mcpServers by mutableStateOf<List<McpServerStatusEntry>>(emptyList())

    /** Live `mcpServer/startupStatus/updated` broadcasts; an entry leaves once the server is ready. */
    var mcpStartup by mutableStateOf<Map<String, com.cy.codex.protocol.protocol.v2.McpStartupStatusUpdated>>(emptyMap())
    var skills by mutableStateOf<List<SkillEntry>>(emptyList())
    var plugins by mutableStateOf<List<PluginEntry>>(emptyList())
    var apps by mutableStateOf<List<AppInfo>>(emptyList())
    var hooks by mutableStateOf<List<HookMetadata>>(emptyList())

    /** Parse warnings/errors from the last `hooks/list`; surfaces the ones [hooks] cannot show. */
    var hookWarnings by mutableStateOf<List<String>>(emptyList())
    var hookErrors by mutableStateOf<List<HookErrorInfo>>(emptyList())

    /**
     * Lifetime token usage per thread, straight from `thread/tokenUsage/updated`.
     *
     * Kept for every thread the server reports, not just the open one: the agents dashboard needs
     * subagent usage while a different thread is streaming, and the usage event carries its thread
     * id precisely so clients can do that.
     */
    var threadUsage by mutableStateOf<Map<String, ThreadTokenUsage>>(emptyMap())

    /** Subagent threads of the open thread, from a `thread/list` scoped by `ancestorThreadId`. */
    var agentThreads by mutableStateOf<List<Thread>>(emptyList())

    /**
     * The connector setup a `plugin/install` asked for, or null when none is pending.
     *
     * `plugin/install` answers with `appsNeedingAuth`; the install is complete, but the plugin
     * cannot run until those apps are authorized, so the page walks the user through them.
     */
    var pluginInstallAuth by mutableStateOf<PluginInstallAuthFlow?>(null)

    /** Files from the last `fuzzyFileSearch/sessionUpdated`, for the composer's `@` popup. */
    var mentionFiles by mutableStateOf<List<FuzzyFileSearchResult>>(emptyList())

    /** Whether a debounced session update is in flight; the popup may show a spinner for it. */
    var mentionSearching by mutableStateOf(false)
    var account by mutableStateOf(AccountReadResponse(requiresOpenaiAuth = false))
    var rateLimits by mutableStateOf(AccountRateLimits())

    /**
     * When [rateLimits] was last refreshed, on the wall clock.
     *
     * Rate-limit windows come from the server with a reset time but no fetch stamp, so without this
     * a card left open overnight shows yesterday's percentages as if they were current.
     */
    var rateLimitsUpdatedAtMs by mutableLongStateOf(0L)
    var usage by mutableStateOf(AccountUsage())
    var usageLoaded by mutableStateOf(false)

    /**
     * The config stack behind the settings page.
     *
     * The whole `config/read` response is kept, not just the merged body, because the page's point
     * is showing *which* layer a value came from — a merged-only copy cannot answer that, and
     * [ConfigReadResponse.origins] is the only thing that can.
     */
    var config by mutableStateOf(ConfigReadResponse())

    /** The readable projection of the merged config; see [ConfigSnapshot.from]. */
    val configSnapshot: ConfigSnapshot get() = config.snapshot

    /**
     * The last `config/…/write` result.
     *
     * Kept because `WriteStatus.OkOverridden` is the only signal that a saved value is being
     * shadowed by a managed layer; without it the page would report success for an edit that has
     * no effect.
     */
    var lastWrite by mutableStateOf<ConfigWriteResponse?>(null)

    /** Marketplaces the account can install plugins from. */
    var marketplaces by mutableStateOf<List<MarketplaceEntry>>(emptyList())

    /** A sign-in waiting for the browser or a device code. */
    var pendingLogin by mutableStateOf<LoginAccountResponse?>(null)
    var loginLoading by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)
    var openedLoginId: String? = null

    /** Collaboration modes the server offers; `null` until `collaborationMode/list` answers. */
    var collaborationModes by mutableStateOf<List<CollaborationModeEntry>>(emptyList())

    /**
     * Reviewers `configRequirements/read` permits, or `null` when policy does not restrict them.
     *
     * `null` and "empty list" mean different things: the first is unrestricted, the second allows
     * no reviewer at all.
     */
    var allowedApprovalsReviewers by mutableStateOf<List<ApprovalsReviewer>?>(null)

    /** Whether `[features] guardian_approval` is on, which is what makes AutoReview offerable. */
    val guardianApprovalEnabled: Boolean get() = configSnapshot.features["guardian_approval"] == true

    /** AutoReview may be selected only when the feature is on and policy allows the value. */
    val autoReviewAvailable: Boolean
        get() = guardianApprovalEnabled &&
            (allowedApprovalsReviewers?.contains(ApprovalsReviewer.AutoReview) ?: true)

    // ---- projects and environments ---------------------------------------------
    //
    // `project/…` and `environment/…` have no TUI counterpart — the terminal works in directories
    // and has nowhere to show a saved list — so these pages are the protocol's own surface rather
    // than a port of anything. The sidebar's groups are still derived from `cwd`; a project row is
    // the *saved* form of one, and the two coexist.
    var projects by mutableStateOf<List<ProjectEntry>>(emptyList())
    /**
     * Environment ids this client has learned about.
     *
     * Ids, not records: the protocol has no "list every environment" call — `environment/info` and
     * `environment/status` each take one id — so a list can only be assembled from ids the client
     * was told about, through `thread/environment/connected` or an `environment/add` of its own.
     */
    var environments by mutableStateOf<List<String>>(emptyList())

    // ---- plugin shares ---------------------------------------------------------
    /** Plugins this account has published, and the checkouts of them that exist locally. */
    var pluginShares by mutableStateOf<List<PluginShareEntry>>(emptyList())

    /** Entries the last `plugin/reconcile` reported as added, removed or rewritten. */
    var reconciledPlugins by mutableStateOf<List<String>>(emptyList())

    /** Entries the last `marketplace/upgrade` reported as changed. */
    var upgradedMarketplaces by mutableStateOf<List<String>>(emptyList())

    /**
     * Where the last `plugin/share/checkout` landed.
     *
     * The call answers with a path and nothing else reads it, so without this the page could not
     * tell the user where the checkout went — the one fact the call exists to produce.
     */
    var pluginCheckoutPath by mutableStateOf<String?>(null)

    // ---- memory ----------------------------------------------------------------
    /** `memory/status`; `null` until asked, which is what lets the page show a loading state. */
    var memories by mutableStateOf<MemoryStatusResponse?>(null)

    // ---- realtime voice --------------------------------------------------------
    /** Voices `thread/realtime/listVoices` offers; empty means the session is off. */
    var realtimeVoices by mutableStateOf<List<String>>(emptyList())

    // ---- user verification -----------------------------------------------------
    /** Local credential readiness; `null` until asked. */
    var userVerification by mutableStateOf<UserVerificationStatusResponse?>(null)

    /** The public half of the credential the last `userVerification/enroll` created. */
    var userVerificationCredential by mutableStateOf<UserVerificationEnrollResponse?>(null)

    // ---- remote control --------------------------------------------------------
    var remoteControl by mutableStateOf<RemoteControlStatus?>(null)
    var remoteControlClients by mutableStateOf<List<RemoteControlClient>>(emptyList())

    /** The code a pairing attempt is waiting on, or `null` when nothing is pending. */
    var remoteControlPairingCode by mutableStateOf<String?>(null)
    var remoteControlPairingClaimed by mutableStateOf<Boolean?>(null)

    // ---- diagnostics -----------------------------------------------------------
    var diagnostics by mutableStateOf<ServerDiagnosticsResponse?>(null)

    // ---- external agent migration ----------------------------------------------
    var externalAgentConfig by mutableStateOf<List<ExternalAgentConfigMigrationItem>>(emptyList())
    var externalAgentConnectors by mutableStateOf<List<com.cy.codex.protocol.protocol.v2.ExternalAgentDetectedConnectorCandidate>>(emptyList())
    var externalAgentImportHistories by mutableStateOf<List<ExternalAgentConfigImportHistory>>(
        emptyList(),
    )

    /** Progress of a running `externalAgentConfig/import`, or `null` between runs. */
    var externalAgentImport by mutableStateOf<ImportProgress?>(null)

    // ---- windows sandbox -------------------------------------------------------
    /** `windowsSandbox/readiness`; `null` until asked, and only ever non-null on Windows. */
    var windowsSandboxReadiness by mutableStateOf<WindowsSandboxReadiness?>(null)

    fun modelPreset(id: String): ModelPreset? = models.firstOrNull { it.id == id || it.model == id }

    companion object {
        /** Dotted paths the config-sources card lists, in display order. */
        val RenderedConfigKeys = listOf(
            "model",
            "model_provider",
            "model_reasoning_effort",
            "approval_policy",
            "sandbox_mode",
            "sandbox_workspace_write.network_access",
            "history",
            "tui_alternate_screen",
            "notifications",
        )
    }
}

/**
 * The sidebar's thread list.
 *
 * Mirrors `codex-rs/tui/src/app/loaded_threads.rs` and `app/session_picker.rs`: threads are grouped
 * by working directory (the phone's stand-in for a project) and the group a thread belongs to is
 * derived from `cwd`, not stored on the thread.
 */
class ThreadListState {
    var threads by mutableStateOf<List<Thread>>(emptyList())
    var sections by mutableStateOf<List<ThreadSection>>(emptyList())

    /**
     * Ids of the rows that came from the archived half of the listing.
     *
     * The wire's `Thread` has no archived flag: the scope a row was fetched under is the only thing
     * that knows, so the client keeps it next to the list rather than inventing a field.
     */
    var archivedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var includeArchived by mutableStateOf(false)
    var loading by mutableStateOf(false)

    fun applyListing(listing: ThreadListing) {
        threads = listing.threads
        archivedIds = listing.archivedIds
    }

    fun markArchived(threadId: String, archived: Boolean) {
        archivedIds = if (archived) archivedIds + threadId else archivedIds - threadId
    }

    /**
     * Group threads by working directory, newest first, matching the sidebar's project groups.
     *
     * A thread with no working directory is keyed by `null` rather than by a placeholder group name:
     * the name of that group is user-visible text, and user-visible text only exists as a string
     * resource, which a model outside composition cannot read. [ProjectGroup.path] is `null` for that
     * group and the UI names it.
     */
    fun grouped(): List<ProjectGroup> = threads
        .groupBy { it.cwd.ifEmpty { null } }
        .map { (cwd, threadsInGroup) ->
            ProjectGroup(
                id = cwd.orEmpty(),
                name = cwd?.substringAfterLast('/')?.ifEmpty { cwd }.orEmpty(),
                path = cwd,
                threads = threadsInGroup.sortedByDescending { it.updatedAt },
            )
        }
        .sortedByDescending { group -> group.threads.maxOfOrNull { it.updatedAt } ?: 0L }
}

data class ProjectGroup(
    val id: String,
    val name: String,
    /** Working directory the group was keyed by, or `null` when the threads have none recorded. */
    val path: String?,
    val threads: List<Thread>,
)
