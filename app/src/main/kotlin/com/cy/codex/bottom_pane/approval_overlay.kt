package com.cy.codex.bottom_pane

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.ElicitationAction
import com.cy.codex.protocol.protocol.v2.CommandAction
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalDecision
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalParams
import com.cy.codex.protocol.protocol.v2.McpElicitationRequest
import com.cy.codex.protocol.protocol.v2.DynamicToolCallParams
import com.cy.codex.protocol.protocol.v2.DynamicToolCallResponse
import com.cy.codex.protocol.protocol.v2.FileChangeApprovalDecision
import com.cy.codex.protocol.protocol.v2.FileChangeApprovalParams
import com.cy.codex.protocol.protocol.v2.FileUpdateChange
import com.cy.codex.protocol.protocol.v2.PermissionsApprovalDecision
import com.cy.codex.protocol.protocol.v2.PermissionsApprovalParams
import com.cy.codex.FileDiffRow
import com.cy.codex.fileDiffOf
import com.cy.codex.parseUnifiedDiff
import com.cy.codex.label
import com.cy.codex.ButtonRole
import com.cy.codex.CodexButton
import com.cy.codex.theme.HideStatusBarInWindow
import com.cy.codex.ModalSheet
import com.cy.codex.Motion
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * The approval dialog.
 *
 * Mirrors `codex-rs/tui/src/bottom_pane/approval_overlay.rs` and its `apply_patch_header` /
 * `mcp_server_elicitation` siblings. The TUI can only draw this as a pane over the transcript; here
 * it is a real modal dialog, because the turn genuinely stops until one of these is answered — a
 * card at the bottom of the screen competes with the composer and the status card for attention,
 * and it disappears under them on a short window.
 *
 * The dialog is deliberately *not* dismissible: [WindowDialog] is given no `onDismissRequest`, so a
 * tap on the dim layer or a back gesture leaves it exactly where it was. A stray tap must never
 * answer a request — answering is a decision, and every decision here is made by pressing a button
 * that says what it does.
 *
 * ## Layout
 *
 * The dialog is an identity header, a scrolling body of labelled blocks, and a pinned footer:
 *
 * ```
 *  ┌ ● Command execution approval ─────────────────┐   icon + title + summary
 *  │ Codex wants to run a command in your workspace│
 *  ├───────────────────────────────────────────────┤
 *  │ COMMAND                                       │   every block is a label plus its value,
 *  │ ┌ $ ./gradlew :app:assembleDebug ───────────┐ │   so the reader never has to guess what a
 *  │ └──────────────────────────────────────────-┘ │   piece of text is
 *  │ DIRECTORY                                     │
 *  │ /home/cy/Project/codex-android                │
 *  ├───────────────────────────────────────────────┤
 *  │ [ Allow ]  [ Always allow ]  Decline  Cancel  │   filled primary, outlined, then flat
 *  └───────────────────────────────────────────────┘
 * ```
 *
 * The three parts are proportional to what they are for: the header is one glance, the body is what
 * is actually being decided and therefore scrolls, and the footer is pinned because the button that
 * unblocks the turn must not be the thing that scrolled away. The old layout put the title and the
 * summary in the library's centred header and the fields in a 72dp label column underneath it; on a
 * 372dp content width that column was a fifth of the dialog and the values wrapped inside the rest.
 * Blocks stack the label above its value instead, which needs no column width at all and keeps the
 * left edge of every line the same.
 */
@Composable
fun ApprovalDialog(
    request: ApprovalRequest?,
    onDecision: (ApprovalRequest, ApprovalResponse) -> Unit,
    remainingQueue: Int = 0,
    busy: Boolean = false,
    error: String? = null,
    /** Resolves the patch behind a file-change request, which names its item but not its diff. */
    patchChanges: (ApprovalRequest) -> List<FileUpdateChange> = { emptyList() },
) {
    // The dialog outlives the request by one exit animation, so the last one is kept mounted:
    // clearing it would blank the card out from under the transition.
    var lastRequest by remember { mutableStateOf<ApprovalRequest?>(null) }
    if (request != null) lastRequest = request
    val shown = request ?: lastRequest
    // The same rule for the file-change diff, which lives on the item rather than on the request:
    // it is resolved while the request is live and kept through the exit animation.
    var lastChanges by remember { mutableStateOf<List<FileUpdateChange>>(emptyList()) }
    if (request != null) lastChanges = patchChanges(request)

    // One decision per request. Without this the exit animation is a window in which a second tap
    // answers a request the server has already resolved.

    ModalSheet(
        show = request != null,
        // A refusal to dismiss, and the reason the approval is a sheet rather than a plain modal:
        // a stray tap must never answer a request. `allowDismiss = false` is what makes the drag,
        // the scrim and the back gesture all leave the sheet exactly where it was.
        allowDismiss = false,
        onDismiss = {},
        // Both lines are drawn by this file rather than by a header block, so the icon, the title and
        // the summary share one left edge with the body underneath them.
        title = null,
        subtitle = null,
    ) {
        HideStatusBarInWindow()
        if (shown != null) {
            ApprovalBody(
                request = shown,
                decide = { response ->
                    if (!busy) {
                        onDecision(shown, response)
                    }
                },
                remainingQueue = remainingQueue,
                busy = busy,
                patchChanges = lastChanges,
            )
            error?.let { Text(it, color = MiuixTheme.colorScheme.error, fontSize = UiType.Meta) }
        }
    }
}

/**
 * The scrolling part of a dialog body.
 *
 * The dialog lays its content out with wrap-content height, so a plain `weight` here is measured
 * against an unbounded maximum: the body grew to its full height, the dialog clipped it, and a
 * two-question form simply lost its second question with no way to scroll to it. Bounding the height
 * against the window is what makes the scroll modifier do something.
 *
 * The bound is the dialog's own budget. A modal is a header, this body and a footer, and the footer
 * has to stay visible whatever is in the body: it carries the button that unblocks the turn. So the
 * body takes a fixed share of the window — [UiConsts.DialogBodyMaxHeightFraction] — and everything
 * else is left for the two fixed parts, which is why that share is well under half even though the
 * dialog is allowed two thirds.
 */
@Composable
internal fun ApprovalScrollBody(
    maxHeightFraction: Float = UiConsts.DialogBodyMaxHeightFraction,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = (windowHeight * maxHeightFraction).coerceAtLeast(UiConsts.DialogBodyMinHeight))
            .verticalScroll(rememberScrollState())
            // A hair of room under the last line, so a body that is exactly at the cap does not
            // end with a half-drawn glyph against the clip edge.
            .padding(bottom = UiConsts.Space2),
        content = content,
    )
}

@Composable
private fun ApprovalBody(
    request: ApprovalRequest,
    decide: (ApprovalResponse) -> Unit,
    remainingQueue: Int,
    busy: Boolean,
    patchChanges: List<FileUpdateChange>,
) {
    // Every family keeps its answer pinned under the scrolling body: on a two-question form or a
    // sixty-line patch the button that unblocks the turn must not be the thing that scrolled away.
    // The two form families bring their own footer, because their primary action also has a
    // not-yet-valid state that has to stay visible while it is disabled.
    Column(modifier = Modifier.fillMaxWidth()) {
        when (request) {
            is ApprovalRequest.UserInput -> RequestUserInputForm(
                request = request,
                onSubmit = { decide(ApprovalResponse.UserInput(it)) },
                onCancel = { decide(ApprovalResponse.UserInput(emptyList())) },
                busy = busy,
            )

            is ApprovalRequest.Elicitation -> McpElicitationForm(
                request = request,
                onSubmit = { decide(ApprovalResponse.Elicitation(ElicitationAction.Accept, it)) },
                onDecline = { decide(ApprovalResponse.Elicitation(ElicitationAction.Decline)) },
                busy = busy,
            )

            else -> {
                ApprovalHeader(request = request, patchChanges = patchChanges)
                Spacer(Modifier.height(UiConsts.DialogHeaderGap))
                ApprovalScrollBody {
                    when (request) {
                        is ApprovalRequest.Exec -> ExecBody(request.params)
                        is ApprovalRequest.ApplyPatch -> PatchBody(request.params, patchChanges)
                        is ApprovalRequest.Permissions -> PermissionsBody(request.params)
                        is ApprovalRequest.DynamicTool -> DynamicToolBody(request.params)
                        else -> Unit
                    }
                }
                Spacer(Modifier.height(UiConsts.DialogFooterGap))
                DecisionRow(decisionsFor(request, decide), busy = busy)
                RemainingQueueLine(remainingQueue)
            }
        }
    }
}

/**
 * Identity of the request: what kind of decision this is, in one line, plus why it is being asked.
 *
 * The icon is the only place a request family is colour-coded, and it is colour-coded by *kind*
 * rather than by severity: reading a command, writing files, changing permissions and calling a
 * tool are four different things, and a warning triangle on all four would say nothing.
 */
@Composable
private fun ApprovalHeader(request: ApprovalRequest, patchChanges: List<FileUpdateChange>) {
    val colors = MiuixTheme.colorScheme
    val accent = approvalAccent(request)
    val title = approvalTitle(request)
    val summary = approvalSummary(request, patchChanges)
    val shape = remember { RoundedCornerShape(UiConsts.CornerControl) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(UiConsts.DialogIconBox)
                .background(accent.copy(alpha = 0.14f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = approvalIcon(request),
                contentDescription = null,
                modifier = Modifier.size(UiConsts.IconRow),
                tint = accent,
            )
        }
        Spacer(Modifier.width(UiConsts.Space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = UiType.DialogTitle,
                lineHeight = UiType.DialogTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = UiConsts.Space3),
                fontSize = UiType.DialogSummary,
                lineHeight = UiType.DialogSummaryLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Headline of the dialog: what is being asked, in the protocol's own terms. */
@Composable
@ReadOnlyComposable
private fun approvalTitle(request: ApprovalRequest): String = when (request) {
    is ApprovalRequest.Exec -> stringResource(R.string.approval_overlay_exec_title)
    is ApprovalRequest.ApplyPatch -> stringResource(R.string.approval_overlay_patch_title)
    is ApprovalRequest.Permissions -> stringResource(R.string.approval_overlay_permissions_title)
    is ApprovalRequest.UserInput -> stringResource(R.string.approval_overlay_user_input_title)
    is ApprovalRequest.Elicitation -> {
        val schemaTitle = (request.params as? McpElicitationRequest.Form)?.requestedSchema?.title.orEmpty()
        if (schemaTitle.isBlank()) {
            stringResource(R.string.approval_overlay_elicitation_title_fallback)
        } else {
            schemaTitle
        }
    }

    is ApprovalRequest.DynamicTool -> stringResource(R.string.approval_overlay_dynamic_tool_title)

    // These three are answered by the reducer before they can reach this dialog — they are
    // host-to-server handshakes, not decisions. They still need a rendering so the `when` stays
    // exhaustive: a card appearing for one of them is the visible symptom of the auto-answer path
    // breaking, which is exactly when someone needs to see it.
    is ApprovalRequest.ChatgptAuthTokensRefresh -> stringResource(R.string.approval_overlay_tokens_title)
    is ApprovalRequest.AttestationGenerate -> stringResource(R.string.approval_overlay_attestation_title)
    is ApprovalRequest.CurrentTimeRead -> stringResource(R.string.approval_overlay_clock_title)
}

/** Second line: why, when the server said why, and what it is otherwise. */
@Composable
@ReadOnlyComposable
private fun approvalSummary(
    request: ApprovalRequest,
    patchChanges: List<FileUpdateChange>,
): String = when (request) {
    is ApprovalRequest.Exec -> request.params.reason?.takeIf { it.isNotBlank() }
        ?: request.params.cwd?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.approval_overlay_exec_summary_fallback)

    is ApprovalRequest.ApplyPatch -> request.params.reason?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.approval_overlay_patch_summary, patchChanges.size)

    is ApprovalRequest.Permissions -> request.params.reason?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.approval_overlay_permissions_summary_fallback)

    is ApprovalRequest.UserInput -> {
        val total = request.params.questions.size
        if (total == 0) {
            stringResource(R.string.approval_overlay_user_input_summary_waiting)
        } else {
            stringResource(R.string.approval_overlay_user_input_summary_questions, total)
        }
    }

    is ApprovalRequest.Elicitation ->
        stringResource(R.string.approval_overlay_elicitation_summary, request.params.serverName)

    is ApprovalRequest.DynamicTool -> request.params.namespace?.takeIf { it.isNotBlank() }
        ?.let {
            stringResource(
                R.string.approval_overlay_dynamic_tool_qualified,
                it,
                request.params.tool,
            )
        }
        ?: stringResource(R.string.approval_overlay_dynamic_tool_summary_fallback)

    is ApprovalRequest.ChatgptAuthTokensRefresh -> request.reason?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.approval_overlay_tokens_summary)

    is ApprovalRequest.AttestationGenerate -> stringResource(R.string.approval_overlay_attestation_summary)
    is ApprovalRequest.CurrentTimeRead -> stringResource(R.string.approval_overlay_clock_summary)
}

/** The glyph of a request family. */
private fun approvalIcon(request: ApprovalRequest): ImageVector = when (request) {
    is ApprovalRequest.Exec -> MiuixIcons.Play
    is ApprovalRequest.ApplyPatch -> MiuixIcons.Notes
    is ApprovalRequest.Permissions -> MiuixIcons.Lock
    is ApprovalRequest.DynamicTool -> MiuixIcons.Settings
    is ApprovalRequest.UserInput -> MiuixIcons.Notes
    is ApprovalRequest.Elicitation -> MiuixIcons.MindMap
    is ApprovalRequest.ChatgptAuthTokensRefresh,
    is ApprovalRequest.AttestationGenerate,
    is ApprovalRequest.CurrentTimeRead,
    -> MiuixIcons.Lock
}

/**
 * Accent of a request family.
 *
 * A file change and a permission change are the two that leave something behind, so they take the
 * error colour; running a command and calling a tool are recoverable and take the accent.
 */
@Composable
private fun approvalAccent(request: ApprovalRequest): Color = when (request) {
    is ApprovalRequest.ApplyPatch, is ApprovalRequest.Permissions -> MiuixTheme.colorScheme.error
    else -> MiuixTheme.colorScheme.primary
}

/** The pills each decision family ends with, in the order the TUI lists them. */
@Composable
private fun decisionsFor(
    request: ApprovalRequest,
    decide: (ApprovalResponse) -> Unit,
): List<DecisionAction> = when (request) {
    is ApprovalRequest.Exec -> {
        val available = request.params.availableDecisions.ifEmpty {
            listOf(
                CommandExecutionApprovalDecision.Accept,
                CommandExecutionApprovalDecision.Decline,
            )
        }
        available.map { decision ->
            DecisionAction(
                label = decision.label(),
                role = when (decision) {
                    CommandExecutionApprovalDecision.Accept -> ButtonRole.Primary
                    CommandExecutionApprovalDecision.AcceptForSession,
                    is CommandExecutionApprovalDecision.AcceptWithExecpolicyAmendment,
                    is CommandExecutionApprovalDecision.ApplyNetworkPolicyAmendment,
                    -> ButtonRole.Secondary

                    else -> ButtonRole.Destructive
                },
                onClick = { decide(ApprovalResponse.CommandExecution(decision)) },
            )
        }
    }

    is ApprovalRequest.ApplyPatch -> listOf(
        DecisionAction(FileChangeApprovalDecision.Accept.label(), role = ButtonRole.Primary) {
            decide(ApprovalResponse.FileChange(FileChangeApprovalDecision.Accept))
        },
        DecisionAction(FileChangeApprovalDecision.AcceptForSession.label()) {
            decide(ApprovalResponse.FileChange(FileChangeApprovalDecision.AcceptForSession))
        },
        DecisionAction(FileChangeApprovalDecision.Decline.label(), role = ButtonRole.Destructive) {
            decide(ApprovalResponse.FileChange(FileChangeApprovalDecision.Decline))
        },
        DecisionAction(FileChangeApprovalDecision.Cancel.label(), role = ButtonRole.Destructive) {
            decide(ApprovalResponse.FileChange(FileChangeApprovalDecision.Cancel))
        },
    )

    is ApprovalRequest.Permissions -> listOf(
        DecisionAction(PermissionsApprovalDecision.Accept.label(), role = ButtonRole.Primary) {
            decide(ApprovalResponse.Permissions(PermissionsApprovalDecision.Accept))
        },
        DecisionAction(PermissionsApprovalDecision.AcceptForSession.label()) {
            decide(ApprovalResponse.Permissions(PermissionsApprovalDecision.AcceptForSession))
        },
        DecisionAction(PermissionsApprovalDecision.Decline.label(), role = ButtonRole.Destructive) {
            decide(ApprovalResponse.Permissions(PermissionsApprovalDecision.Decline))
        },
    )

    is ApprovalRequest.DynamicTool -> listOf(
        // A dynamic tool call carries no decision enum of its own, so it borrows the two labels that
        // mean "run it" and "do not": the command-execution decisions. The elicitation actions would
        // be wrong here — their accept label is the form's *submit*, not an allow.
        DecisionAction(CommandExecutionApprovalDecision.Accept.label(), role = ButtonRole.Primary) {
            decide(ApprovalResponse.DynamicTool(DynamicToolCallResponse(success = true)))
        },
        DecisionAction(CommandExecutionApprovalDecision.Decline.label(), role = ButtonRole.Destructive) {
            decide(ApprovalResponse.DynamicTool(DynamicToolCallResponse(success = false)))
        },
    )

    is ApprovalRequest.UserInput, is ApprovalRequest.Elicitation -> emptyList()

    // Host handshakes have no user-facing decision; see `approvalTitle`.
    is ApprovalRequest.ChatgptAuthTokensRefresh,
    is ApprovalRequest.AttestationGenerate,
    is ApprovalRequest.CurrentTimeRead,
    -> emptyList()
}

/**
 * One `label: value` pair, stacked.
 *
 * Two lines rather than two columns: a fixed label column has to be sized for the longest label
 * (`Permission rule`), which costs a fifth of a phone-width dialog, and a value that wraps inside
 * the remainder reads as a paragraph that happens to start halfway across. Above the value, the
 * label is an eyebrow and the value owns the full width.
 */
@Composable
internal fun FieldBlock(
    label: String,
    value: String,
    accent: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        FieldLabel(label)
        Spacer(Modifier.height(UiConsts.Space3))
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            fontSize = UiType.Body,
            lineHeight = UiType.BodyLine,
            color = if (accent) colors.primary else colors.onSurface,
        )
    }
}

/** The eyebrow above a block: small, tracked out, and never the thing being read. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = UiType.Badge,
        lineHeight = UiType.BadgeLine,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A labelled block whose value is a monospace command line. */
@Composable
private fun CommandBlock(command: String?) {
    FieldLabel(stringResource(R.string.approval_overlay_field_command))
    Spacer(Modifier.height(UiConsts.Space5))
    if (command.isNullOrBlank()) {
        MutedLine(stringResource(R.string.approval_overlay_command_unavailable))
    } else {
        CodeRow(text = command)
    }
}

/** A labelled block whose value is a paragraph. */
@Composable
private fun TextBlock(label: String, value: String, accent: Boolean = false) {
    FieldBlock(label = label, value = value, accent = accent)
}

/** Body of a command-execution request: the command, then where and why it runs. */
@Composable
private fun ExecBody(params: CommandExecutionApprovalParams) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiConsts.DialogFieldGap),
    ) {
        CommandBlock(params.command)
        params.cwd?.takeIf { it.isNotBlank() }?.let {
            TextBlock(label = stringResource(R.string.approval_overlay_field_directory), value = it)
        }
        params.reason?.takeIf { it.isNotBlank() }?.let {
            TextBlock(label = stringResource(R.string.approval_overlay_field_reason), value = it)
        }
        if (params.commandActions.isNotEmpty()) {
            // One at a time: `joinToString` is not inline, so a composable cannot be called from
            // inside its transform.
            val actions = params.commandActions.map { commandActionLabel(it) }
            TextBlock(
                label = stringResource(R.string.approval_overlay_field_actions),
                value = actions.joinToString(stringResource(R.string.approval_overlay_list_separator)),
            )
        }
        params.proposedExecpolicyAmendment?.takeIf { it.isNotEmpty() }?.let { rules ->
            TextBlock(
                label = stringResource(R.string.approval_overlay_field_permission_rule),
                value = rules.joinToString(stringResource(R.string.approval_overlay_list_separator)),
                accent = true,
            )
        }
        if (params.proposedNetworkPolicyAmendments.isNotEmpty()) {
            TextBlock(
                label = stringResource(R.string.approval_overlay_field_network_rule),
                value = params.proposedNetworkPolicyAmendments.joinToString(
                    stringResource(R.string.approval_overlay_list_separator),
                ) { amendment -> "${amendment.action.wire} ${amendment.host}" },
                accent = true,
            )
        }
    }
}

/** Short label for a parsed shell action, mirroring `command_can_run`'s summaries. */
@Composable
@ReadOnlyComposable
private fun commandActionLabel(action: CommandAction): String = when (action) {
    is CommandAction.Read -> stringResource(R.string.approval_overlay_action_read, action.path)
    is CommandAction.ListFiles -> action.path?.let {
        stringResource(R.string.approval_overlay_action_list, it)
    } ?: stringResource(R.string.approval_overlay_action_list_directory)

    is CommandAction.Search -> buildString {
        append(stringResource(R.string.approval_overlay_action_search))
        action.query?.takeIf { it.isNotBlank() }?.let {
            append(stringResource(R.string.approval_overlay_action_search_query, it))
        }
        action.path?.takeIf { it.isNotBlank() }?.let {
            append(stringResource(R.string.approval_overlay_action_search_path, it))
        }
    }

    is CommandAction.Unknown -> action.command
}

/**
 * Body of a file-change request.
 *
 * One ledger line states the shape of the whole patch — how many files, how many lines either way —
 * and the cards under it carry the detail. The old header said only `3 files` and left the reader to
 * open each card to find out whether the change was a rename or a rewrite.
 */
@Composable
private fun PatchBody(params: FileChangeApprovalParams, changes: List<FileUpdateChange>) {
    val colors = MiuixTheme.colorScheme
    val files = remember(changes) {
        changes.map { change ->
            change.path to fileDiffOf(change.path, parseUnifiedDiff(change.diff))
        }
    }
    // Each file owns its disclosure state; the first file opens so the dialog leads with its diff.
    var showAll by remember(changes) { mutableStateOf(false) }
    val expanded = remember(changes) {
        mutableStateMapOf<String, Boolean>().apply {
            files.firstOrNull()?.let { put(it.first, true) }
        }
    }
    val shown = if (showAll) files else files.take(UiConsts.PatchPreviewFiles)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiConsts.DialogFieldGap),
    ) {
        params.grantRoot?.takeIf { it.isNotBlank() }?.let {
            TextBlock(
                label = stringResource(R.string.approval_overlay_field_grant_root),
                value = it,
                accent = true,
            )
        }
        FieldLabel(stringResource(R.string.approval_overlay_field_changes))
        if (files.isEmpty()) {
            Spacer(Modifier.height(UiConsts.Space3))
            MutedLine(stringResource(R.string.approval_overlay_patch_body_unavailable))
        } else {
            Spacer(Modifier.height(UiConsts.Space5))
            Text(
                text = stringResource(
                    R.string.approval_overlay_changes_summary,
                    files.size,
                    files.sumOf { it.second.additions },
                    files.sumOf { it.second.removals },
                ),
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(UiConsts.Space8))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                shown.forEach { (path, diff) ->
                    FileDiffRow(
                        file = diff,
                        expanded = expanded[path] == true,
                        onToggle = { expanded[path] = expanded[path] != true },
                        cwd = params.grantRoot,
                        bodyMaxLines = UiConsts.PatchBodyMaxLines,
                        corner = UiConsts.CornerControl,
                    )
                }
            }
            if (files.size > shown.size) {
                Spacer(Modifier.height(UiConsts.Space8))
                FlatDisclosure(
                    label = stringResource(
                        R.string.approval_overlay_changes_more,
                        files.size - shown.size,
                    ),
                    onClick = { showAll = true },
                )
            }
        }
    }
}

/** Body of a permissions request: where it applies, then the checklist of what it grants. */
@Composable
private fun PermissionsBody(params: PermissionsApprovalParams) {
    val permissions = params.permissions
    val checklist = buildList {
        if (permissions.network) add(stringResource(R.string.approval_overlay_permission_network))
        if (permissions.shell) add(stringResource(R.string.approval_overlay_permission_shell))
        if (permissions.fileSystemRead.isNotEmpty()) {
            add(
                stringResource(
                    R.string.approval_overlay_permission_read,
                    permissions.fileSystemRead.joinToString(
                        stringResource(R.string.approval_overlay_list_separator),
                    ),
                ),
            )
        }
        if (permissions.fileSystemWrite.isNotEmpty()) {
            add(
                stringResource(
                    R.string.approval_overlay_permission_write,
                    permissions.fileSystemWrite.joinToString(
                        stringResource(R.string.approval_overlay_list_separator),
                    ),
                ),
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiConsts.DialogFieldGap),
    ) {
        if (params.cwd.isNotBlank()) {
            TextBlock(label = stringResource(R.string.approval_overlay_field_directory), value = params.cwd)
        }
        FieldLabel(stringResource(R.string.approval_overlay_field_permissions))
        if (checklist.isEmpty()) {
            Spacer(Modifier.height(UiConsts.Space3))
            MutedLine(stringResource(R.string.approval_overlay_permissions_empty))
        } else {
            Spacer(Modifier.height(UiConsts.Space5))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space7),
            ) {
                checklist.forEach { CheckRow(it) }
            }
        }
    }
}

/** One granted item of the permission checklist. */
@Composable
private fun CheckRow(text: String) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = MiuixIcons.Ok,
            contentDescription = null,
            modifier = Modifier
                .padding(top = UiConsts.Space1)
                .size(UiConsts.IconInline),
            tint = colors.primary,
        )
        Spacer(Modifier.width(UiConsts.Space8))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = UiType.Body,
            lineHeight = UiType.BodyLine,
            color = colors.onSurface,
        )
    }
}

/** Body of a dynamic tool call: which tool, and with what arguments. */
@Composable
private fun DynamicToolBody(params: DynamicToolCallParams) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiConsts.DialogFieldGap),
    ) {
        FieldBlock(label = stringResource(R.string.approval_overlay_field_tool), value = params.tool)
        if (params.arguments.isNotBlank()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                FieldLabel(stringResource(R.string.approval_overlay_field_input))
                Spacer(Modifier.height(UiConsts.Space5))
                CodeRow(text = params.arguments)
            }
        }
    }
}

/** The line an empty block shows where its value would have been. */
@Composable
private fun MutedLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** A flat text affordance, used where a third button would crowd the footer out. */
@Composable
private fun FlatDisclosure(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UiConsts.Space6),
        fontSize = UiType.Action,
        lineHeight = UiType.ActionLine,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.primary,
        maxLines = 1,
    )
}

/**
 * Monospace block for a command line or a tool argument blob.
 *
 * The `$` sits outside the block's own text so it can take the accent colour, and the block itself
 * is the same one [CodeRow] paints for a tool argument.
 */
@Composable
internal fun CodeRow(
    text: String,
    cornerRadius: Dp = UiConsts.CornerControl,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(codeSurface(), RoundedCornerShape(cornerRadius))
            .padding(
                horizontal = UiConsts.Space12,
                vertical = UiConsts.Space10,
            ),
    ) {
        Text(
            text = stringResource(R.string.approval_overlay_command_prompt),
            fontSize = UiType.Badge,
            lineHeight = UiType.BadgeLine,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = colors.primary,
            maxLines = 1,
        )
        Spacer(Modifier.height(UiConsts.Space4))
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            fontSize = UiType.Code,
            lineHeight = UiType.CodeLine,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurface,
        )
    }
}
