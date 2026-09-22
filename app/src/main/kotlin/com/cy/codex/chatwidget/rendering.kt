package com.cy.codex.chatwidget

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.AppEvent
import com.cy.codex.CodexApp
import com.cy.codex.CodexButton
import com.cy.codex.ComposerHistory
import com.cy.codex.MarkdownStream
import com.cy.codex.Motion
import com.cy.codex.R
import com.cy.codex.SessionDiagnostic
import com.cy.codex.SessionState
import com.cy.codex.SquircleShape
import com.cy.codex.Surface
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.app.AgentPickerSheet
import com.cy.codex.app.AgentRosterEntry
import com.cy.codex.app.AgentsOverview
import com.cy.codex.app.rememberAgentRoster
import com.cy.codex.app.sessionStatusReport
import com.cy.codex.app.withThreadMetadata
import com.cy.codex.bottom_pane.ApprovalDialog
import com.cy.codex.bottom_pane.ApprovalNoticeBar
import com.cy.codex.bottom_pane.Composer
import com.cy.codex.bottom_pane.TurnActivityBar
import com.cy.codex.bottom_pane.activeToolDetail
import com.cy.codex.bottom_pane.isConnectorAuth
import com.cy.codex.chatwidget.QueuedMessages
import com.cy.codex.floatingSurface
import com.cy.codex.glassTint
import com.cy.codex.history_cell.CommandExecutionCell
import com.cy.codex.history_cell.DiagnosticCell
import com.cy.codex.history_cell.ThreadItemCell
import com.cy.codex.history_cell.commandActionLabel
import com.cy.codex.history_cell.isExploringCall
import com.cy.codex.perf.IdentityKeys
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.AttachmentType
import com.cy.codex.protocol.protocol.v2.CollaborationMode
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codex.protocol.protocol.v2.ThreadAttachment
import com.cy.codex.protocol.protocol.v2.UserInput
import com.cy.codex.CollapsibleSection
import com.cy.codex.copyToClipboard
import com.cy.codex.raisedSurface
import java.io.File
import kotlinx.coroutines.launch
import com.cy.codex.status.DiffCard
import com.cy.codex.status.StatusCard
import com.cy.codex.status.StatusCardButton
import com.cy.codex.status.StatusPanelState
import com.cy.codex.statusDotColor
import com.cy.codex.statusPillSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The chat surface: transcript, drawer, status card, approval cards and composer.
 *
 * Mirrors `codex-rs/tui/src/chatwidget.rs` rendered as one screen. Every widget here is fed from
 * [com.cy.codex.SessionState], and everything the user does is expressed as an
 * [com.cy.codex.AppEvent] handed to [CodexApp.onAppEvent] — the screen itself owns only
 * presentation state (which drawer is open, which sheet is up, what is typed).
 */
@Composable
fun ChatScreen(
    app: CodexApp,
    modifier: Modifier = Modifier,
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    sidebarExpanded: Boolean,
    onSidebarExpandedChange: (Boolean) -> Unit,
    projectsCollapsed: Boolean,
    onToggleProjects: () -> Unit,
    expandedProjects: Set<String>,
    onToggleProject: (String) -> Unit,
    topBlurHeight: Dp = 52.dp,
    bottomBlurHeight: Dp = 78.dp,
    topBlurRadius: Float = 14f,
    bottomBlurRadius: Float = 16f,
    panelTopOffset: Dp = 56.dp,
    composerGap: Dp = 8.dp,
    minPanelHeight: Dp = 240.dp,
    minDiffHeight: Dp = 300.dp,
    maxDiffHeight: Dp = 560.dp,
    statusCardMaxHeight: Dp = 560.dp,
    // Every transition here reads its duration from Motion: the panel, the diff card and the
    // queued banner used to enter at 160 / 170 / 180ms, which is three values nobody can tell
    // apart on purpose.
    panelEnterDurationMs: Int = Motion.EnterMs,
    panelExitDurationMs: Int = Motion.ExitMs,
    diffEnterDurationMs: Int = Motion.EnterMs,
    diffExitDurationMs: Int = Motion.ExitMs,
    queuedEnterDurationMs: Int = Motion.EnterMs,
    queuedExitDurationMs: Int = Motion.ExitMs,
) {
    val session = app.widget.state
    val threads = app.threads
    // `OpenDocument` rather than `GetContent`: the protocol takes an identity key the server can
    // read back later, and a document uri is one the app keeps a grant for across a restart.
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            app.importAttachment(uri)
        }
    }
    // `/export` with no path asks the system save dialog for a destination; the request flag is
    // consumed before launching so a recomposition cannot open it twice.
    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri != null) {
            app.exportTranscriptTo(uri)
        }
    }
    LaunchedEffect(app.exportTranscriptRequest) {
        if (app.exportTranscriptRequest) {
            app.consumeTranscriptExportRequest()
            exportPicker.launch(app.transcriptExportFileName())
        }
    }
    val colors = MiuixTheme.colorScheme
    var overviewOpen by remember { mutableStateOf(false) }
    // `show` stays true while the sheet animates out: it calls back when it has actually left, and
    // clearing the flag on the request instead would cut the exit off mid-slide.
    var overviewLeaving by remember { mutableStateOf(false) }
    val panelState = remember { StatusPanelState() }

    val mainAgentLabel = stringResource(R.string.agent_roster_main_label)
    val subAgentNameFormat = stringResource(R.string.agent_roster_sub_agent_name)
    // The roster is folded out of the whole transcript, so it is derived through
    // [rememberAgentRoster] rather than read here: reading the items list in this scope would
    // subscribe the entire screen to every streaming delta, and only the memo's folded value may
    // invalidate this screen.
    val roster = rememberAgentRoster(session, mainAgentLabel, subAgentNameFormat)
    val approval = app.widget.currentApproval
    // Names the thread the way the sidebar and the resume picker do, for the cross-thread
    // approval notice; the state read happens where the notice renders.
    val threadNameOf: (String) -> String = { threadId ->
        threads.threads.firstOrNull { it.id == threadId }
            ?.let { thread ->
                thread.name?.takeIf { it.isNotBlank() }
                    ?: thread.preview.take(48).takeIf { it.isNotBlank() }
            }
            ?: threadId.take(8)
    }
    val backdrop = rememberLayerBackdrop {
        drawRect(colors.background)
        drawContent()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // The transcript column. On a wide window it is inset by the drawer's width *at all times* —
        // centred while the drawer is shut, pushed across when it opens — so the text is wrapped once
        // and never again. Opening the drawer then costs one composited translation instead of a
        // full re-measure of every cell in the list. On a narrow window there is no room for a second
        // column, so the drawer goes back over the text the way it always did.
        val wide = maxWidth >= UiConsts.WideContentBreakpoint
        val drawerWidth = minOf(UiConsts.SidebarWidth, UiConsts.SidebarWidthCap)
        // Where the column's left edge sits while the drawer is open: clear of the drawer by
        // [UiConsts.ContentGap], not flush against it.
        val contentStart = (UiConsts.ScreenMargin + drawerWidth + UiConsts.ContentGap)
            .coerceAtMost(maxWidth)
        val contentWidth = if (wide) {
            (maxWidth - contentStart - UiConsts.ScreenMargin).coerceAtLeast(UiConsts.MinContentWidth)
        } else {
            maxWidth
        }
        // Rest is centred; the open position is the same column translated, so the text is never
        // re-measured — the width above does not depend on whether the drawer is open.
        val centredStart = (maxWidth - contentWidth) / 2
        val contentShift by animateDpAsState(
            targetValue = if (wide && sidebarExpanded) contentStart - centredStart else 0.dp,
            animationSpec = Motion.PanelDp,
            label = "contentShift",
        )
        // The backdrop layer spans the *window*, not the column. `layerBackdrop` only captures what
        // is drawn inside it, and everything that samples it — the composer across its full width,
        // the two progressive-blur bands — reaches past the column's edges. A layer as wide as the
        // column left those samples reading outside the captured texture, which is what put flat
        // colour blocks in the composer's glass. The background rect keeps the capture opaque under
        // the text, so the blur has no transparent pixels to smear colour into.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(contentWidth)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = contentShift.toPx() },
            ) {
                TranscriptPane(
                    app = app,
                    session = session,
                    topInset = topInset,
                    bottomInset = bottomInset,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Top and bottom progressive blur: the transcript fades under the floating chrome instead
        // of being cut off by it.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topInset + topBlurHeight)
                .progressiveTextureBlur(
                    backdrop = backdrop,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    blurRadius = topBlurRadius,
                    gradient = ProgressiveBlur.Top.copy(endFraction = 0.92f),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomInset + bottomBlurHeight)
                .progressiveTextureBlur(
                    backdrop = backdrop,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    blurRadius = bottomBlurRadius,
                    gradient = ProgressiveBlur.Bottom.copy(endFraction = 0.92f),
                ),
        )

        // A drop after the first load keeps the transcript; this banner is the retry. It sits just
        // above the composer rather than at the top so it does not fight the status-card button.
        app.connectionLostMessage?.let { message ->
            ConnectionBanner(
                message = message,
                onRetry = app::reconnect,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = UiConsts.ScreenMargin,
                        end = UiConsts.ScreenMargin,
                        bottom = bottomInset + UiConsts.PromptBarHeight + UiConsts.ScreenMargin,
                    ),
            )
        }

        StatusCardButton(
            open = panelState.open,
            onClick = { panelState.toggle() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = UiConsts.ScreenMargin, top = topInset + UiConsts.ScreenMargin),
        )

        // The right-hand panels. Two cards, not one: the diff card is *added* to the left of the
        // status card, so the card the user was reading keeps its size and its place under the
        // button. Widening one card meant the section column slid sideways as the diff opened and
        // slid back as it closed, and the row that had just been tapped was no longer where it was
        // tapped.
        val panelMax = maxWidth - UiConsts.ScreenMargin * 2
        val statusWidth = minOf(UiConsts.StatusPanelWidth, panelMax)
        // Both cards fit side by side on anything tablet-shaped; on a narrow window the diff takes
        // the whole strip and the status card steps aside rather than being pushed off-screen.
        val sideBySide = panelMax >= statusWidth + UiConsts.PanelGap + UiConsts.MinDiffPaneWidth
        val diffWidth = if (sideBySide) {
            minOf(UiConsts.DiffPaneWidth, panelMax - statusWidth - UiConsts.PanelGap)
        } else {
            minOf(UiConsts.DiffPaneWidth, panelMax)
        }
        // Measured by the status card's layout and read by the diff card's own content below, so a
        // status remeasure invalidates the diff pane instead of this whole screen.
        val statusHeight = remember { mutableStateOf(0.dp) }

        AnimatedVisibility(
            visible = panelState.open,
            enter = fadeIn(tween(panelEnterDurationMs, easing = Motion.EnterEasing)) + scaleIn(
                initialScale = 0.9f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = Motion.Panel,
            ),
            exit = fadeOut(tween(panelExitDurationMs, easing = Motion.ExitEasing)) + scaleOut(
                targetScale = 0.94f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = tween(panelExitDurationMs, easing = Motion.ExitEasing),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    end = UiConsts.ScreenMargin,
                    top = topInset + UiConsts.ScreenMargin + panelTopOffset,
                ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiConsts.PanelGap),
                verticalAlignment = Alignment.Top,
            ) {
                // Read inside the panel's scope: the whole-turn diff is replaced on every
                // `turn/diff/updated`, and a card in here is a cheaper thing to rebuild than the
                // screen that hosts it.
                val paneFile = session.turnDiff.firstOrNull { it.path == panelState.paneFilePath }
                val diffOpen = panelState.openFilePath != null
                AnimatedVisibility(
                    visible = diffOpen && paneFile != null,
                    // Grows out of the status card's edge, leftwards: the new card is the one that
                    // moves, and the card beside it is the anchor it moves away from.
                    enter = expandHorizontally(
                        expandFrom = Alignment.End,
                        animationSpec = tween(diffEnterDurationMs, easing = Motion.EnterEasing),
                    ) + fadeIn(tween(diffEnterDurationMs, easing = Motion.EnterEasing)),
                    exit = shrinkHorizontally(
                        shrinkTowards = Alignment.End,
                        animationSpec = tween(diffExitDurationMs, easing = Motion.ExitEasing),
                    ) + fadeOut(tween(diffExitDurationMs, easing = Motion.ExitEasing)),
                ) {
                    // The last opened file survives the close, so the card still has something to
                    // draw while it shrinks away.
                    paneFile?.let { file ->
                        DiffCard(
                            file = file,
                            siblings = session.turnDiff,
                            onClose = panelState::closeFile,
                            cwd = session.config.cwd,
                            width = diffWidth,
                            height = statusHeight.value.coerceIn(minDiffHeight, maxDiffHeight),
                        )
                    }
                }
                if (!diffOpen || sideBySide) {
                    StatusCard(
                        state = panelState,
                        session = session.config,
                        titlePending = session.titleGenerationPending,
                        gitSummary = session.gitSummary,
                        status = session.status,
                        usage = session.usage,
                        turnDiff = session.turnDiff,
                        plan = session.plan,
                        roster = roster,
                        items = session.items,
                        width = statusWidth,
                        maxHeight = statusCardMaxHeight,
                        models = app.catalog.models,
                        rateLimits = app.catalog.rateLimits,
                        rateLimitsUpdatedAt = app.catalog.rateLimitsUpdatedAtMs.takeIf { it > 0L },
                        onModel = { app.onAppEvent(AppEvent.SetModel(it)) },
                        onEffort = { app.onAppEvent(AppEvent.SetReasoningEffort(it)) },
                        onPolicy = { app.onAppEvent(AppEvent.SetApprovalPolicy(it)) },
                        onReviewer = { app.onAppEvent(AppEvent.SetApprovalsReviewer(it)) },
                        autoReviewAvailable = app.catalog.autoReviewAvailable,
                        onServiceTier = { app.onAppEvent(AppEvent.SetServiceTier(it)) },
                        planAvailable = app.catalog.collaborationModes.any { it.mode == CollaborationMode.Plan },
                        onCollaborationMode = { app.onAppEvent(AppEvent.SetCollaborationMode(it)) },
                        onCompact = { app.onAppEvent(AppEvent.CompactThread(session.threadId)) },
                        onOpenAgents = { overviewOpen = true },
                        onOpenAgent = { threadId -> app.openSurface(Surface.SubAgentThread(threadId)) },
                        onOpenAgentInfo = { threadId -> app.openSurface(Surface.SubAgent(threadId)) },
                        modifier = Modifier.onSizeChanged {
                            statusHeight.value = with(density) { it.height.toDp() }
                        },
                    )
                }
            }
        }

        // The composer only steps aside for the drawer, and only on a wide window: it keeps the full
        // width of the transcript column otherwise. It is a single bar, so re-measuring *it* is cheap
        // — the point of holding the transcript's width fixed is that the list underneath is not
        // re-measured with it.
        val promptBarStartInset by animateDpAsState(
            // The composer lines up with the column, so it starts at the same place the column does.
            targetValue = if (wide && sidebarExpanded) contentStart - UiConsts.ScreenMargin else 0.dp,
            animationSpec = Motion.PanelDp,
            label = "promptBarStartInset",
        )

        ComposerDock(
            app = app,
            session = session,
            threadNameOf = threadNameOf,
            promptBarStartInset = promptBarStartInset,
            backdrop = backdrop,
            onAttach = { attachmentPicker.launch(arrayOf("*/*")) },
            composerGap = composerGap,
            queuedEnterDurationMs = queuedEnterDurationMs,
            queuedExitDurationMs = queuedExitDurationMs,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // `AgentPickerSheet` is the roster as a filterable list; it has no trigger yet — the status
        // card's agent section opens the overview instead — so it is not mounted here.
        AgentsOverviewPane(
            app = app,
            session = session,
            roster = roster,
            show = overviewOpen || overviewLeaving,
            onSelect = { threadId -> app.openSurface(Surface.SubAgentThread(threadId)); overviewLeaving = true },
            onDismiss = { overviewLeaving = true },
            onDismissFinished = {
                overviewLeaving = false
                overviewOpen = false
            },
        )

        // The drawer is painted last and sized to the window: it is a drawer, so the transcript and
        // the composer both pass underneath it and it reaches the bottom edge instead of stopping
        // short of the thing it is covering. The composer still steps aside (promptBarStartInset),
        // so nothing the user has to reach is hidden behind it.
        SidebarPanel(
            expanded = sidebarExpanded,
            onExpandedChange = onSidebarExpandedChange,
            actions = SidebarModel.actions(),
            onAction = { entry -> openSurfaceFor(app, entry.id) },
            projects = SidebarModel.projects(threads, includeArchived = false),
            projectsCollapsed = projectsCollapsed,
            onToggleProjects = onToggleProjects,
            expandedProjects = expandedProjects,
            onToggleProject = onToggleProject,
            selectedSessionId = session.threadId,
            onSessionSelected = app::openThread,
            onOpenSettings = { app.openSurface(Surface.Settings) },
            panelWidth = minOf(UiConsts.SidebarWidth, UiConsts.SidebarWidthCap),
            collapsedWidth = UiConsts.ChipSize,
            collapsedHeight = UiConsts.ChipSize,
            // Same bottom line as the composer: the drawer and the composer are the two pieces
            // anchored to the bottom of the transcript, and they end together.
            maxPanelHeight = (
                maxHeight - topInset - UiConsts.ScreenMargin * 2
                ).coerceAtLeast(minPanelHeight),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = UiConsts.ScreenMargin, top = topInset + UiConsts.ScreenMargin),
        )

        // Window-level, so it is on screen whatever else is open: the turn is blocked on it, and a
        // request that only showed up inside a collapsed panel would read as a hung session.
        ApprovalDialog(
            request = approval,
            busy = app.widget.answeringApproval,
            error = app.widget.approvalError,
            onDecision = { request, response -> onApprovalDecision(app, request, response) },
            remainingQueue = (app.widget.approvalQueueSize - 1).coerceAtLeast(0),
            // The patch is not on the request; it is recovered from the item the request names.
            patchChanges = { request -> app.widget.fileChangeChanges(request.itemId) },
        )
        if (app.goalMenuOpen) {
            val goal = session.goal
            GoalSheet(
                goal = goal,
                onSet = { objective ->
                    // Creating a goal leaves the status to the server; editing one keeps the
                    // state the goal already had (`edited_goal_status` upstream).
                    app.onAppEvent(
                        AppEvent.SetGoal(
                            objective = objective,
                            status = goal?.let { editedGoalStatus(it.status) },
                        ),
                    )
                },
                onSetStatus = { status -> app.onAppEvent(AppEvent.SetGoal(status = status)) },
                onClear = { app.onAppEvent(AppEvent.ClearGoal) },
                onDismiss = { app.goalMenuOpen = false },
            )
        }
        if (app.copyMenuOpen) {
            CopySheet(
                response = session.items.lastOrNull { it is AgentMessageItem } as? AgentMessageItem,
                status = sessionStatusReport(app),
                onDismiss = { app.copyMenuOpen = false },
            )
        }
        app.trustRequest?.let { request ->
            TrustProjectSheet(
                path = request.path,
                onTrust = app::grantTrust,
                onDismiss = app::dismissTrust,
            )
        }
        app.rateLimitNudge?.let { nudge ->
            RateLimitNudgeSheet(
                nudge = nudge,
                onSwitch = app::switchToRateLimitModel,
                onKeep = app::dismissRateLimitNudge,
                onNever = app::hideRateLimitNudgeForever,
            )
        }
    }
}

/**
 * The transcript surface: either the empty-runtime screen or the live transcript.
 *
 * Split out of [ChatScreen] so the session's transcript state — the item list, the diagnostics,
 * the plan and the streaming id — is read inside this scope. A streaming write then invalidates
 * this pane instead of the composer, the status panels and the drawer that surround it.
 */
@Composable
private fun TranscriptPane(
    app: CodexApp,
    session: SessionState,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val items = session.items
    val diagnostics = session.diagnostics
    // The empty/loading decision is a derived boolean: the underlying lists are written on every
    // delta, and this pane should only recompose when the decision itself flips.
    val runtimeEmpty by remember(session) {
        derivedStateOf {
            (!session.open && !session.loading) ||
                (session.open && items.isEmpty() && diagnostics.isEmpty() && !session.running)
        }
    }
    if (!app.startupReady || runtimeEmpty) {
        RuntimeTranscript(
            app = app,
            modifier = modifier.padding(
                horizontal = UiConsts.ScreenMargin,
                vertical = UiConsts.TranscriptTopInset + UiConsts.PromptBarHeight,
            ),
        )
        return
    }
    // Remembered so a recomposition of this pane (a status flip, say) does not hand the list a new
    // lambda and force the rows to be rebuilt with it.
    val isStreaming: (ThreadItem) -> Boolean = remember(session) {
        // Deferred: the transcript asks per row whether it is the streaming one, so the reads of
        // `running` and `streamingItemId` belong to the row's scope, not to this pane's.
        { item -> session.running && item.id == session.streamingItemId }
    }
    // The markdown buffer a row renders while its deltas are still arriving; looked up per row so
    // only the row that owns the stream reads that entry of the map.
    val streamFor: (String) -> MarkdownStream? = remember(session) { { id -> session.stream(id) } }
    Transcript(
        items = items,
        diagnostics = diagnostics,
        isStreaming = isStreaming,
        streamFor = streamFor,
        plan = session.plan,
        loading = session.loading,
        empty = !session.open && !session.loading,
        cwd = session.config.cwd,
        onOpenAgent = { threadId -> app.openSurface(Surface.SubAgentThread(threadId)) },
        onOpenAgentInfo = { threadId -> app.openSurface(Surface.SubAgent(threadId)) },
        onAnswerQuestion = { text -> app.onAppEvent(AppEvent.AnswerAsyncQuestion(text)) },
        canLoadEarlier = app.widget.canLoadEarlier,
        loadingEarlier = app.widget.loadingEarlier,
        onLoadEarlier = app.widget::loadEarlier,
        contentPadding = PaddingValues(
            start = UiConsts.TranscriptGutter,
            end = UiConsts.TranscriptGutter,
            top = topInset + UiConsts.TranscriptTopInset,
            bottom = bottomInset + UiConsts.PromptBarHeight + UiConsts.ScreenMargin * 2 +
                UiConsts.TranscriptBottomInset,
        ),
    )
}

/**
 * The in-transcript "connection lost" banner and its retry.
 *
 * Mirrors the backend banner the TUI shows on disconnect: the session stays readable, and the one
 * action that helps — reconnect — is on the notice instead of replacing the screen.
 */
@Composable
private fun ConnectionBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { SquircleShape(UiConsts.PanelCorner) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .floatingSurface(shape = shape, tint = glassTint(0.94f), elevation = UiConsts.PanelElevation)
            .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.connection_banner_title),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Text(
                text = message,
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(UiConsts.Space10))
        CodexButton(stringResource(R.string.connection_banner_retry), onRetry, size = com.cy.codex.CodexButtonSize.Compact)
    }
}

@Composable
private fun RuntimeTranscript(app: CodexApp, modifier: Modifier) {
    val session = app.widget.state
    val colors = MiuixTheme.colorScheme
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space12),
        ) {
            Text("Codex", fontSize = UiType.Display, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            when {
                app.startupLoading || app.creatingThread -> {
                    Text(stringResource(if (app.creatingThread) R.string.runtime_creating_thread else R.string.runtime_starting))
                    // A genuine spinner, and the only animation this screen runs: it is composed
                    // only while the runtime is starting or a thread is being created, so an idle
                    // chat never holds it.
                    LinearProgressIndicator(modifier = Modifier.width(160.dp))
                }
                app.startupError != null -> {
                    Text(stringResource(R.string.runtime_startup_failed), color = colors.error)
                    Text(app.startupError.orEmpty(), fontSize = UiType.Meta, color = colors.onSurfaceVariantSummary)
                    CodexButton(stringResource(R.string.runtime_retry), app::bootstrap)
                }
                !session.open && session.threadId.isNotBlank() -> {
                    Text(session.diagnostics.lastOrNull()?.detail.orEmpty(), fontSize = UiType.Meta, color = colors.error)
                    CodexButton(stringResource(R.string.runtime_retry), { app.openThread(session.threadId) })
                    CodexButton(stringResource(R.string.runtime_new_thread), { app.onAppEvent(AppEvent.NewThread()) })
                }
                app.catalog.account.account == null -> {
                    CodexButton(stringResource(R.string.runtime_sign_in), { app.openSurface(Surface.Account) })
                }
                else -> {
                    Text(stringResource(R.string.runtime_ready), fontSize = UiType.CardTitle, color = colors.onSurfaceVariantSummary)
                    Text(session.config.cwd.ifBlank { app.defaultWorkspace }, fontSize = UiType.Meta, color = colors.onSurfaceVariantSummary)
                }
            }
        }
    }
}

private fun onApprovalDecision(app: CodexApp, request: ApprovalRequest, response: ApprovalResponse) {
    app.onAppEvent(AppEvent.ResolveApproval(request.requestId, response))
    // A completed connector sign-in invalidates the app catalog: upstream asks for a forced
    // connector refresh on the same accept (`app_link_view.rs:complete_external_flow_and_close`).
    if (request is ApprovalRequest.Elicitation &&
        request.params.isConnectorAuth() &&
        response is ApprovalResponse.Elicitation &&
        response.action == com.cy.codex.protocol.ElicitationAction.Accept
    ) {
        app.onAppEvent(AppEvent.ReloadApps)
    }
}

/**
 * One routing table for every "go to this page" id in the app.
 *
 * The drawer and the settings page both name destinations by id, and both have to land on the same
 * page: the entries moved out of the drawer into settings, and a routing table per caller is how the
 * two drift apart.
 */
internal fun openSurfaceFor(app: CodexApp, id: String) {
    // The three routes that need a subject take it from the open session rather than from the id:
    // there is exactly one open thread, and a caller that had to pass its id would be able to pass
    // one that is no longer open.
    val threadId = app.widget.state.threadId
    val cwd = app.widget.state.config.cwd.ifBlank { app.defaultWorkspace }
    when (id) {
        "new" -> app.onAppEvent(AppEvent.NewThread())
        "workspace" -> app.openSurface(Surface.WorkspacePicker)
        "sessions" -> {
            app.onAppEvent(AppEvent.SetThreadListScope(false))
            app.openSurface(Surface.Sessions)
        }
        "mcp" -> app.openSurface(Surface.McpServers)
        "skills" -> app.openSurface(Surface.Skills)
        "plugins" -> app.openSurface(Surface.Plugins)
        "hooks" -> app.openSurface(Surface.Hooks)
        "apps" -> app.openSurface(Surface.Apps)
        "settings" -> app.openSurface(Surface.Settings)
        "account" -> app.openSurface(Surface.Account)
        "archived" -> {
            app.onAppEvent(AppEvent.SetThreadListScope(true))
            app.openSurface(Surface.Sessions)
        }
        "projects" -> app.openSurface(Surface.Projects)
        "remote_control" -> app.openSurface(Surface.RemoteControl)
        "verification" -> app.openSurface(Surface.UserVerification)
        "plugin_shares" -> app.openSurface(Surface.PluginShares)
        "memories" -> app.openSurface(Surface.Memories)
        "migration" -> app.openSurface(Surface.ExternalAgentImport)
        "bedrock" -> app.openSurface(Surface.Bedrock)
        "diagnostics" -> app.openSurface(Surface.Diagnostics)
        "sandbox" -> app.openSurface(Surface.WindowsSandbox)
        "files" -> app.openSurface(Surface.FileBrowser(cwd, picking = false))
        "exec" -> app.openSurface(Surface.ExecCommand)
        "terminals" -> app.openSurface(Surface.BackgroundTerminals)
        "realtime" -> app.openSurface(Surface.Realtime)
        "review" -> app.onAppEvent(AppEvent.SubmitSlashCommand("review", ""))
        "goal" -> app.onAppEvent(AppEvent.SubmitSlashCommand("goal", ""))
        "history" -> app.openSurface(Surface.ThreadHistory)
        else -> Unit
    }
    // `threadId` is read for the same reason the routes above are: a page that needs the open
    // session takes it from the app, and the compiler should see that this table depends on it.
    if (threadId.isEmpty()) return
}

/**
 * One row of the transcript after folding.
 *
 * A run of exploring commands (reads, listings, searches) collapses into one [exposed] row, which
 * is what the TUI's `ExecCell` does; everything else is a single item. [indices] point into the
 * session's item list so each row still reads its own element in its own scope.
 */
internal data class TranscriptRow(
    val key: String,
    val indices: List<Int>,
    val exposed: Boolean,
)

/** Fold a run of exploring commands into one row, leaving every other item on its own. */
internal fun foldTranscriptRows(items: List<ThreadItem>): List<TranscriptRow> {
    val rows = ArrayList<TranscriptRow>()
    var index = 0
    while (index < items.size) {
        val item = items[index]
        if (item is CommandExecutionItem && item.isExploringCall()) {
            var end = index + 1
            while (end < items.size && (items[end] as? CommandExecutionItem)?.isExploringCall() == true) {
                end++
            }
            rows += TranscriptRow("explored:${item.id}", (index until end).toList(), exposed = true)
            index = end
        } else {
            rows += TranscriptRow(item.id, listOf(index), exposed = false)
            index++
        }
    }
    return rows
}

/**
 * The collapsed `Explored` group.
 *
 * The TUI shows only the header because the full transcript is one keystroke away; a phone has no
 * second surface, so the group expands to the per-command cards it stands for.
 */
@Composable
private fun ExploredGroupRow(
    commands: List<CommandExecutionItem>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = commands.any { it.status == CommandExecutionStatus.InProgress }
    val labels = commands.mapNotNull { command ->
        command.commandActions.firstOrNull()?.let { label -> commandActionLabel(label) }
    }
    val summary = labels.take(3).joinToString(" · ") +
        if (labels.size > 3) " +${labels.size - 3}" else ""
    CollapsibleSection(
        title = stringResource(
            if (active) R.string.exec_cell_exploring else R.string.exec_cell_explored,
        ),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        subtitle = summary.ifEmpty { null },
        modifier = modifier,
    ) {
        commands.forEach { command -> CommandExecutionCell(command) }
    }
}

/**
 * The transcript.
 *
 * Mirrors the history viewport of `codex-rs/tui/src/chatwidget.rs`: a scrollable list of items,
 * sticky to the bottom while a turn streams, plus the session's diagnostics as notices. The plan
 * checklist is rendered inline where the `PlanItem` sits, so the transcript reads in order.
 */
@Composable
internal fun Transcript(
    items: List<ThreadItem>,
    diagnostics: List<SessionDiagnostic>,
    isStreaming: (ThreadItem) -> Boolean,
    streamFor: (String) -> MarkdownStream?,
    plan: List<com.cy.codex.protocol.protocol.v2.PlanStep>,
    loading: Boolean,
    empty: Boolean,
    cwd: String?,
    onOpenAgent: (String) -> Unit,
    onOpenAgentInfo: (String) -> Unit,
    onAnswerQuestion: (String) -> Unit,
    canLoadEarlier: Boolean,
    loadingEarlier: Boolean,
    onLoadEarlier: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    itemGap: Dp = 18.dp,
    planGap: Dp = 10.dp,
) {
    val listState = rememberLazyListState()
    AutoPager(listState = listState)

    if (empty) {
        EmptyTranscript(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding)
        return
    }

    // Diagnostics have content equality, so a content hash is not a key: two identical notices
    // would collide. This allocates one identity per notice instead, and prunes entries for
    // notices the bounded list has already evicted.
    val diagnosticKeys = remember { IdentityKeys<SessionDiagnostic>() }
    // The fold is a derived value so a streaming write inside a row does not rewrite it: only an
    // item insertion or replacement changes the list it reads.
    val rowsState = remember(items) { derivedStateOf { foldTranscriptRows(items) } }
    val rows = rowsState.value

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(itemGap),
    ) {
        // The page pager sits above the first row, not in the top padding: it belongs to the
        // transcript's scroll content, so a reader who tapped it stays put when the page lands.
        if (canLoadEarlier || loadingEarlier) {
            item(key = "load-earlier") {
                LoadEarlierRow(loading = loadingEarlier, onLoad = onLoadEarlier)
            }
        }
        // The list is read here, not copied: the count and the keys re-read it when it changes,
        // and each row reads its own element inside its own scope, so a delta that replaces one
        // element does not rebuild the screen around the list.
        items(count = rows.size, key = { rows[it].key }) { index ->
            val row = rows[index]
            val item = row.indices.firstOrNull()?.let { items.getOrNull(it) } ?: return@items
            Column(modifier = Modifier.fillMaxWidth()) {
                if (row.exposed) {
                    ExploredGroupRow(
                        commands = row.indices.mapNotNull { items.getOrNull(it) as? CommandExecutionItem },
                    )
                } else {
                    ThreadItemCell(
                        item = item,
                        stream = streamFor(item.id),
                        streaming = isStreaming(item),
                        assistantLabel = stringResource(R.string.chat_transcript_assistant_label),
                        cwd = cwd,
                        onOpenAgent = onOpenAgent,
                        onOpenAgentInfo = onOpenAgentInfo,
                        onAnswerQuestion = onAnswerQuestion,
                    )
                }
                if (item is AgentMessageItem && plan.isNotEmpty() && index == rows.lastIndex) {
                    Spacer(Modifier.height(planGap))
                    PlanTimeline(steps = plan)
                }
            }
        }
        items(
            count = diagnostics.size,
            key = { index -> diagnosticKeys.keyOf(diagnostics[index], diagnostics) },
        ) { index ->
            DiagnosticCell(diagnostic = diagnostics[index])
        }
        if (loading) {
            item(key = "loading") { LoadingRow() }
        }
    }
}

/**
 * "Load earlier" as a row at the top of the transcript.
 *
 * Centered and compact: it is a pager for history the reader has scrolled away from, not a primary
 * action, and a full-width button would read as one.
 */
@Composable
private fun LoadEarlierRow(loading: Boolean, onLoad: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CodexButton(
            text = stringResource(
                if (loading) R.string.transcript_load_earlier_loading else R.string.transcript_load_earlier,
            ),
            onClick = onLoad,
            enabled = !loading,
            size = com.cy.codex.CodexButtonSize.Compact,
        )
    }
}

/**
 * The transcript's auto-pager: keeps the newest row against the bottom of the viewport as a turn
 * writes into it, and leaves the page alone the moment the reader is not at the bottom.
 *
 * Two things make this more than "scroll to the last item when the list grows", which is what the
 * transcript used to do. The newest row is usually the row that is already there, growing: a delta
 * lands *inside* it, so the item count never changes and an effect keyed on the count sits still
 * while the newest line slides under the fold. And content arriving moves the viewport off the
 * bottom by itself, so "not at the bottom" cannot be read as "the reader left" — a pager that read
 * the two the same way would stop following the moment it started to keep up.
 *
 * It is therefore driven by both the layout and the source of the movement. Every remeasure brings
 * the newest row back, but only while the pager is pinned; the pager is pinned as long as the reader
 * has not scrolled away, and a scroll *they* drove — a drag, a fling, a keyboard or an accessibility
 * scroll — unpins it until the bottom is theirs again. Reading back through a running turn is
 * therefore a page that stays put: the newest content keeps arriving below the fold, and the
 * transcript returns to it only when the reader does.
 */
@Composable
private fun AutoPager(listState: LazyListState) {
    // A touch down ends the follow, not the scroll it turns into: the reader's intent is known the
    // moment their finger lands, and waiting for the drag to travel would let the follow fight the
    // gesture for its first frames.
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    var pinned by remember { mutableStateOf(true) }
    // Set while this pager is the one moving the list, so its own scroll is not read as the
    // reader's — which is the one way a follower could unpin itself.
    var parking by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.atNewestRow() }.collect { atNewest ->
            if (atNewest) {
                pinned = true
            } else if (!parking && (dragging || listState.isScrollInProgress)) {
                // The viewport left the bottom under a scroll the reader drove. Whatever drove it —
                // a drag, a fling, a trackpad or an accessibility action — the page it landed on is
                // the page they asked for, and it stays there.
                pinned = false
            }
        }
    }

    LaunchedEffect(listState) {
        // Keyed on the layout rather than on the item count, because the event being followed is a
        // remeasure: that is what a delta landing in the newest row produces.
        snapshotFlow { listState.layoutInfo }.collect {
            if (pinned && !dragging) {
                parking = true
                try {
                    listState.parkOnNewestRow()
                } finally {
                    parking = false
                }
            }
        }
    }
}

/** Whether the transcript is showing the end of its content: the bottom edge of the newest row. */
private fun LazyListState.atNewestRow(): Boolean {
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull() ?: return true
    return last.index == layout.totalItemsCount - 1 &&
        last.offset + last.size <= layout.viewportEndOffset - layout.afterContentPadding + 1
}

/**
 * Brings the transcript's newest row to the bottom of the viewport.
 *
 * Three cases, and they are three because the distance is not the same thing as the motion. A row
 * already on screen is the one that is growing, and it is moved without animation, because an
 * animation per delta is cancelled by the next delta and reads as a stutter. A row that arrived as
 * the next row is a card away and is animated — that is the page turn — and the rest of the
 * distance to its bottom edge is animated with it, so a row taller than the viewport does not slide
 * to its top and then jump. A row further down than that is a transcript that just loaded, and that
 * is jumped rather than slid through, because animating a screen of history is a ride nobody asked
 * for.
 */
private suspend fun LazyListState.parkOnNewestRow() {
    val layout = layoutInfo
    val newest = layout.totalItemsCount - 1
    if (newest < 0) return
    val visible = layout.visibleItemsInfo.lastOrNull() ?: return

    val paging = visible.index == newest - 1
    when {
        paging -> animateScrollToItem(newest)
        visible.index < newest -> scrollToItem(newest)
    }

    // Land on the row's bottom edge rather than on its top: the newest row is often taller than the
    // viewport, and the line being written is the last one. The trailing content padding is the gap
    // under the list, so the row ends exactly where the content ends.
    val end = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val distance = end.offset + end.size -
        (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding)
    if (distance < 1) return
    if (paging) animateScrollBy(distance.toFloat()) else scrollBy(distance.toFloat())
}

@Composable
private fun LoadingRow(
    corner: Dp = UiConsts.CornerControl,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    iconSize: Dp = 15.dp,
    iconGap: Dp = 10.dp,
    textSize: TextUnit = UiType.Subtitle,
    textLineHeight: TextUnit = UiType.SheetTitle,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(raisedSurface())
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Refresh,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = colors.primary,
        )
        Spacer(Modifier.width(iconGap))
        Text(
            text = stringResource(R.string.chat_loading_history),
            fontSize = textSize,
            lineHeight = textLineHeight,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun EmptyTranscript(
    modifier: Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    iconBoxSize: Dp = UiConsts.IconBoxLarge,
    iconBoxCorner: Dp = UiConsts.CornerCard,
    iconSize: Dp = UiConsts.IconHeaderSmall,
    iconGap: Dp = 14.dp,
    titleSize: TextUnit = UiType.Display,
    titleLineHeight: TextUnit = UiType.DisplayLine,
    titleGap: Dp = 6.dp,
    hintSize: TextUnit = UiType.CardTitle,
    hintLineHeight: TextUnit = UiType.MessageLine,
    hintGap: Dp = 2.dp,
    slashHintSize: TextUnit = UiType.Subtitle,
    slashHintLineHeight: TextUnit = UiType.SheetTitle,
) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier.padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(iconBoxSize)
                    .clip(SquircleShape(iconBoxCorner))
                    .background(raisedSurface()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Community,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = colors.primary,
                )
            }
            Spacer(Modifier.height(iconGap))
            Text(
                text = stringResource(R.string.chat_empty_title),
                fontSize = titleSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(titleGap))
            Text(
                text = stringResource(R.string.chat_empty_hint),
                fontSize = hintSize,
                lineHeight = hintLineHeight,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(hintGap))
            Text(
                text = stringResource(R.string.chat_empty_slash_hint),
                fontSize = slashHintSize,
                lineHeight = slashHintLineHeight,
                color = colors.onSurfaceVariantSummary.copy(alpha = 0.8f),
            )
        }
    }
}

/** Small status chip reused by the transcript header rows. */
@Composable
internal fun StatusChip(
    label: String,
    tone: ThreadStatusTone,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
    dotSize: Dp = 6.dp,
    dotGap: Dp = 6.dp,
    textSize: TextUnit = UiType.RowDetail,
    textLineHeight: TextUnit = UiType.Message,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(statusPillSurface(tone))
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(statusDotColor(tone)),
        )
        Spacer(Modifier.width(dotGap))
        Text(
            text = label,
            fontSize = textSize,
            lineHeight = textLineHeight,
            color = statusDotColor(tone),
            maxLines = 1,
        )
    }
}

/** Cache-relative name of the file `ACTION_EDIT` edits; matches `res/xml/file_paths.xml`. */
private const val ComposerDraftFile = "drafts/composer.txt"

/**
 * The queued-message tray and the composer.
 *
 * Its reads — the draft, the queue, the running flag — live here rather than in [ChatScreen], so a
 * keystroke, a queue change or a turn starting does not recompose the transcript and the panels
 * behind it.
 */
@Composable
private fun ComposerDock(
    app: CodexApp,
    session: SessionState,
    threadNameOf: (String) -> String,
    promptBarStartInset: Dp,
    backdrop: Backdrop,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier,
    composerGap: Dp = 8.dp,
    queuedEnterDurationMs: Int = Motion.EnterMs,
    queuedExitDurationMs: Int = Motion.ExitMs,
) {
    // The draft is read from the session rather than kept in a `remember`, because two other things
    // write it — a slash command that prefills an argument, and a transcript row that offers to
    // quote itself — and both outlive this composable's own state.
    val prompt = session.composerDraft
    val onPromptChange: (String) -> Unit = { app.onAppEvent(AppEvent.SetComposerDraft(it)) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The safety-stop findings currently open in their sheet, if any.
    var misalignmentReview by remember { mutableStateOf<com.cy.codex.protocol.protocol.v2.MisalignmentErrorDetails?>(null) }
    // The draft as it was when the editor launched, so an editor that saves nothing cannot wipe
    // what was typed. The result code is deliberately ignored: several editors return CANCELED
    // while still having written the file.
    var editorOriginal by remember { mutableStateOf<String?>(null) }
    val externalEditor = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val original = editorOriginal
        editorOriginal = null
        val edited = runCatching { File(context.cacheDir, ComposerDraftFile).readText() }.getOrNull()
        if (edited != null && edited != original) onPromptChange(edited)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = UiConsts.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(composerGap),
    ) {
        ApprovalNoticeBar(
            foreign = app.widget.otherThreadApprovals,
            threadName = threadNameOf,
            reviews = app.widget.pendingReviews,
            denials = app.widget.approvalDenials,
            onOpenThread = app::openThread,
            onApproveDenial = { denial ->
                app.onAppEvent(AppEvent.ApproveGuardianDeniedAction(denial.threadId, denial.itemId))
            },
            onDismissDenial = { denial -> app.onAppEvent(AppEvent.DismissAutoReviewDenial(denial.itemId)) },
            modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
        )
        AnimatedVisibility(
            visible = session.queued.isNotEmpty(),
            enter = fadeIn(tween(queuedEnterDurationMs, easing = Motion.EnterEasing)) +
                expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = tween(queuedEnterDurationMs, easing = Motion.EnterEasing),
                ),
            exit = fadeOut(tween(queuedExitDurationMs, easing = Motion.ExitEasing)) +
                shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = tween(queuedExitDurationMs, easing = Motion.ExitEasing),
                ),
        ) {
            AttachmentTray(
                attachments = session.attachments,
                onRemove = { attachment ->
                    app.onAppEvent(
                        AppEvent.RemoveAttachment(
                            threadId = session.threadId,
                            type = AttachmentType.fromWire(attachment.attachmentType),
                            identityKey = attachment.identityKey,
                        ),
                    )
                },
                modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
            )
            QueuedMessages(
                messages = session.queued,
                onStart = { entry -> app.onAppEvent(AppEvent.StartQueuedMessage(entry.id)) },
                onMove = { entry, delta -> app.onAppEvent(AppEvent.MoveQueuedMessage(entry.id, delta)) },
                onRemove = { entry -> app.onAppEvent(AppEvent.DeleteQueuedMessage(entry.id)) },
                // The non-text inputs are carried across untouched: the sheet edits the body,
                // and a queued image is not something a text field can have an opinion about.
                onEdit = { entry, body ->
                    val kept = entry.input.filterNot { it is UserInput.Text }
                    app.onAppEvent(
                        AppEvent.UpdateQueuedMessage(
                            queuedId = entry.id,
                            inputs = listOf(UserInput.Text(body)) + kept,
                        ),
                    )
                },
                onClear = { app.onAppEvent(AppEvent.ClearQueue) },
                onDismiss = {},
                modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
            )
        }

        // A safety stop blocks the composer until the user reviews it or confirms continuing.
        session.misalignment?.let { details ->
            MisalignmentBar(
                details = details,
                onReview = { misalignmentReview = details },
                onContinue = { app.onAppEvent(AppEvent.ContinueMisalignment) },
                modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
            )
        }
        misalignmentReview?.let { details ->
            MisalignmentReviewSheet(
                details = details,
                onDismiss = { misalignmentReview = null },
            )
        }

        // A side conversation has no sidebar row of its own, so the one piece of context the user
        // needs — where it came from, and how to leave — lives on a strip above the composer.
        app.sideParentOf(session.threadId)?.let { parent ->
            SideConversationBanner(
                parentLabel = threadNameOf(parent),
                onBack = { app.onAppEvent(AppEvent.ToggleSideConversation()) },
                modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
            )
        }

        // The live turn status sits directly above the composer, like the TUI's status indicator:
        // the duration, the tool that is running, and the hook on display right now.
        TurnActivityBar(
            running = session.running,
            startedAtMs = session.turnStartedAtMs,
            detail = remember(session.itemsRevision) { activeToolDetail(session.items) },
            hookStatus = session.hookStatus,
            modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
        )

        ComposerImageTray(
            images = session.composerImages,
            onRemove = { image -> app.onAppEvent(AppEvent.RemoveComposerImage(image.path)) },
            modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
        )

        Composer(
            value = prompt,
            onValueChange = onPromptChange,
            // An approval dialog must not steal the keyboard mid-sentence; the widget uses this
            // signal to hold the dialog for a second after the last edit.
            onActivity = app.widget::noteComposerActivity,
            onSubmit = {
                // The staged images are part of the submission: the widget assembles them from the
                // draft so a placeholder the user deleted cannot resurrect its file.
                val inputs = session.pendingTurnInputs()
                if (inputs.isNotEmpty()) {
                    app.onAppEvent(AppEvent.SubmitUserMessage(inputs))
                }
            },
            onInterrupt = { app.onAppEvent(AppEvent.InterruptTurn) },
            onAttach = onAttach,
            history = ComposerHistory.entries,
            onCopyLastResponse = {
                val last = session.items.lastOrNull { it is AgentMessageItem } as? AgentMessageItem
                if (last == null || last.text.isBlank()) {
                    scope.launch {
                        app.snackbar.showSnackbar(context.getString(R.string.composer_copy_last_empty))
                    }
                } else {
                    copyToClipboard(context, last.text, context.getString(R.string.copy_sheet_whole_response))
                }
            },
            onOpenExternalEditor = {
                val file = File(context.cacheDir, ComposerDraftFile)
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeText(prompt)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_EDIT)
                        .setDataAndType(uri, "text/plain")
                        .addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    editorOriginal = prompt
                    externalEditor.launch(intent)
                }.onFailure {
                    scope.launch {
                        app.snackbar.showSnackbar(
                            context.getString(R.string.composer_external_editor_failed),
                        )
                    }
                }
            },
            running = session.running,
            enabled = app.startupReady && !session.loading && !app.creatingThread &&
                !session.config.blocksDirectInput && session.misalignment == null,
            hint = when {
                session.config.blocksDirectInput ->
                    stringResource(R.string.chat_composer_hint_parent_owned)
                // The same shell-mode signal upstream shows in its footer.
                prompt.startsWith("!") -> stringResource(R.string.chat_composer_hint_shell)
                session.open -> stringResource(R.string.chat_composer_hint_open)
                else -> stringResource(R.string.chat_composer_hint_empty)
            },
            queuedCount = session.queued.size,
            slashSuggestions = if (prompt.startsWith("/")) {
                SidebarModel.slashSuggestions(
                    query = prompt,
                    planAvailable = app.catalog.collaborationModes.any {
                        it.mode == com.cy.codex.protocol.protocol.v2.CollaborationMode.Plan
                    },
                )
            } else {
                emptyList()
            },
            onSuggestionPicked = { command ->
                // A command that takes no argument is dispatched on the spot rather than typed
                // out and submitted: `/clear` with a trailing space is a draft nobody wants,
                // and the TUI runs it the moment it is picked.
                if (command.takesArgument) {
                    onPromptChange(command.command + " ")
                } else {
                    app.onAppEvent(AppEvent.SubmitSlashCommand(command.command, ""))
                }
            },
            mentionSuggestions = app.mentionSuggestions,
            // The composer already spliced the picked text into the draft; this hook exists for
            // surfaces that want to react to the mention itself (nothing does yet).
            onMentionPicked = {},
            onMentionQueryChange = app::onMentionQueryChange,
            // Only enabled skills are offered: a disabled skill mentioned by name would resolve to
            // nothing, and the popup is the one place that can say so before the turn is sent.
            skillCandidates = app.catalog.skills.filter { it.enabled }.map { it.name },
            onSkillPicked = {},
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = (promptBarStartInset + UiConsts.ScreenMargin).coerceAtLeast(0.dp),
                    end = UiConsts.ScreenMargin,
                ),
        )
    }
}

/**
 * The agent overview sheet, with its reads scoped away from [ChatScreen].
 *
 * The token total moves while a turn streams; the roster read is already the memoized fold, so
 * this indirection keeps the usage updates from invalidating the chat screen around the sheet.
 * Per-thread usage and subagent liveness are folded in here for the same reason: the sheet is
 * rebuilt from them, not the screen behind it.
 */
@Composable
private fun AgentsOverviewPane(
    app: CodexApp,
    session: SessionState,
    roster: List<AgentRosterEntry>,
    show: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val usage = app.catalog.threadUsage
    val listedThreads = app.catalog.agentThreads + app.threads.threads
    val entries = remember(roster, usage, listedThreads) {
        val byId = listedThreads.associateBy { it.id }
        roster.map { agent -> agent.withThreadMetadata(byId[agent.threadId], usage[agent.threadId]) }
    }
    AgentsOverview(
        show = show,
        roster = entries,
        activeThreadId = session.threadId,
        onSelect = onSelect,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
        totalTokens = entries.sumOf { it.tokens.toLong() },
    )
}

/** "Side conversation · from <parent>" with the way back to the parent thread. */
@Composable
private fun SideConversationBanner(
    parentLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiConsts.CornerChip))
            .background(colors.primary.copy(alpha = 0.10f))
            .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.side_conversation_banner, parentLabel),
            modifier = Modifier.weight(1f),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.onSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.side_conversation_back),
            modifier = Modifier
                .clip(RoundedCornerShape(UiConsts.CornerChip))
                .clickable(onClick = onBack)
                .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space4),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.primary,
            maxLines = 1,
        )
    }
}

/**
 * The local images the draft is holding, as removable `[Image #N]` chips.
 *
 * The placeholder also sits in the draft text, so this row is a second, visible handle on the
 * same attachment: tapping the close icon deletes both.
 */
@Composable
private fun ComposerImageTray(
    images: List<com.cy.codex.ComposerImageAttachment>,
    onRemove: (com.cy.codex.ComposerImageAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        images.forEach { image ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(UiConsts.CornerChip))
                    .background(colors.primary.copy(alpha = 0.12f))
                    .padding(start = UiConsts.Space10, end = UiConsts.Space4, top = UiConsts.Space3, bottom = UiConsts.Space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = image.placeholder,
                    fontSize = UiType.Chip,
                    lineHeight = UiType.ChipLine,
                    color = colors.onSurface,
                    maxLines = 1,
                )
                IconButton(
                    onClick = { onRemove(image) },
                    minWidth = UiConsts.IconButtonCompact,
                    minHeight = UiConsts.IconButtonCompact,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Close,
                        contentDescription = stringResource(R.string.composer_remove_attachment),
                        modifier = Modifier.size(UiConsts.IconInline),
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/**
 * The files and images attached to the open thread.
 *
 * Between the queue and the composer, because that is the order they are sent in: queued messages
 * go first, then whatever the tray holds when the next turn starts. Nothing renders when it is
 * empty — an empty tray is a row of chrome that says "nothing here" on every thread that has no
 * attachments, which is most of them.
 */
@Composable
private fun AttachmentTray(
    attachments: List<ThreadAttachment>,
    onRemove: (ThreadAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val shape = remember { SquircleShape(UiConsts.PanelCorner) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .floatingSurface(shape = shape, tint = glassTint(0.94f), elevation = UiConsts.PanelElevation)
            .clip(shape)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = UiConsts.Space10, vertical = UiConsts.Space7),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(UiConsts.CornerChip))
                    .background(colors.primary.copy(alpha = 0.12f))
                    .padding(start = UiConsts.Space8, end = UiConsts.Space4, top = UiConsts.Space3, bottom = UiConsts.Space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = attachment.identityKey.ifEmpty { attachment.id },
                    fontSize = UiType.Chip,
                    lineHeight = UiType.ChipLine,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = { onRemove(attachment) },
                    minWidth = UiConsts.IconButtonCompact,
                    minHeight = UiConsts.IconButtonCompact,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Close,
                        contentDescription = stringResource(R.string.composer_remove_attachment),
                        modifier = Modifier.size(UiConsts.IconInline),
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}
