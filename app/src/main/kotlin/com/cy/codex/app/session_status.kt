package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cy.codex.CodexApp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.copyToClipboard
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.Account
import com.cy.codex.protocol.protocol.v2.AddCreditsNudgeCreditType
import com.cy.codex.protocol.protocol.v2.AddCreditsNudgeEmailStatus
import com.cy.codex.protocol.protocol.v2.ThreadUsage
import com.cy.codex.raisedSurface
import com.cy.codex.status.accessSummary
import com.cy.codex.status.agentsSummary
import com.cy.codex.status.formatCreditMicros
import com.cy.codex.status.formatEstimatedUsdMicros
import com.cy.codex.status.formatTokens
import com.cy.codex.warningColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/status` as a page over the open thread.
 *
 * The terminal prints one `SessionHeader` cell (`status/card.rs`); the phone already has the status
 * card as a transcript pane, so this page exists for the facts the card does not carry: the thread
 * id, the CLI version that created it, the working directory, and the estimated credit/cost usage
 * that `account/usage/read` answers only when asked by thread id. `server/diagnostics` keeps its
 * own page for process-level facts.
 */
@Composable
fun SessionStatusScreen(
    app: CodexApp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val session = app.widget.state
    val config = session.config
    val usage = session.usage
    val knownThreads = app.catalog.agentThreads + app.threads.threads
    val thread = knownThreads.firstOrNull { it.id == session.threadId }
    val serverVersion =
        knownThreads.maxByOrNull { it.updatedAt }?.cliVersion?.takeIf { it.isNotBlank() }
    var estimate by remember(session.threadId) { mutableStateOf<ThreadUsage?>(null) }
    var estimateFailed by remember(session.threadId) { mutableStateOf(false) }
    var nudging by remember { mutableStateOf(false) }
    var nudgeMessage by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session.threadId) {
        if (session.threadId.isBlank()) return@LaunchedEffect
        app.client
            .readThreadUsage(session.threadId)
            .onSuccess { estimate = it }
            .onFailure { estimateFailed = true }
    }

    val contextWindow = usage.modelContextWindow?.takeIf { it > 0 }
    val spend = app.catalog.rateLimits.rateLimits
    val account = app.catalog.account.account
    val context = LocalContext.current
    val report = sessionStatusReport(app)
    val copyLabel = stringResource(R.string.clipboard_copy_status)

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.session_status_title),
            summary = config.displayName,
            startAction = { StatusBackButton(onBack) },
            endActions = {
                if (report != null) {
                    IconButton(
                        onClick = { copyToClipboard(context, report, copyLabel) },
                        minWidth = UiConsts.IconButtonSize,
                        minHeight = UiConsts.IconButtonSize,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Copy,
                            contentDescription = copyLabel,
                            modifier = Modifier.size(UiConsts.IconHeader),
                            tint = colors.primary,
                        )
                    }
                }
            },
        )
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiConsts.ScreenMargin)
                    .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                colors =
                    CardDefaults.defaultColors(
                        color = raisedSurface(),
                        contentColor = MiuixTheme.colorScheme.onSurface,
                    ),
            ) {
                BasicComponent(
                    title = stringResource(R.string.session_status_title),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                BasicComponent(
                    title = stringResource(R.string.session_status_thread_id),
                    endActions = {
                        Text(
                            text = session.threadId.ifEmpty { "—" },
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.session_status_status),
                    endActions = {
                        Text(
                            text = session.status.label().ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                thread
                    ?.cliVersion
                    ?.takeIf { it.isNotBlank() }
                    ?.let { version ->
                        // A thread remembers the version that created it; when the server has moved
                        // on,
                        // the mismatch is worth flagging before the user wonders about odd
                        // behaviour.
                        val stale = serverVersion != null && serverVersion != version
                        BasicComponent(
                            title = stringResource(R.string.session_status_created_by),
                            endActions = {
                                Text(
                                    text =
                                        if (stale) {
                                                stringResource(
                                                    R.string.session_status_version_mismatch,
                                                    version,
                                                    serverVersion,
                                                )
                                            } else {
                                                version
                                            }
                                            .ifEmpty { "—" },
                                    fontFamily = FontFamily.Monospace,
                                    color =
                                        if (stale) warningColor()
                                        else null ?: MiuixTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                )
                            },
                        )
                    }
                config.forkedFromId?.let { origin ->
                    BasicComponent(
                        title = stringResource(R.string.status_card_forked_from_label),
                        endActions = {
                            Text(
                                text = origin.ifEmpty { "—" },
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                        },
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                colors =
                    CardDefaults.defaultColors(
                        color = raisedSurface(),
                        contentColor = MiuixTheme.colorScheme.onSurface,
                    ),
            ) {
                BasicComponent(
                    title = stringResource(R.string.session_status_directory),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                BasicComponent(
                    title = stringResource(R.string.session_status_directory),
                    endActions = {
                        Text(
                            text = config.cwd.ifEmpty { "—" },
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                config.gitBranch
                    ?.takeIf { it.isNotBlank() }
                    ?.let { branch ->
                        BasicComponent(
                            title = stringResource(R.string.session_status_branch),
                            endActions = {
                                Text(
                                    text = branch.ifEmpty { "—" },
                                    fontFamily = FontFamily.Monospace,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                )
                            },
                        )
                    }
                if (config.workspaceRoots.isNotEmpty()) {
                    BasicComponent(
                        title = stringResource(R.string.session_status_workspace_roots),
                        endActions = {
                            Text(
                                text = config.workspaceRoots.joinToString("\n").ifEmpty { "—" },
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                        },
                    )
                }
                BasicComponent(
                    title = stringResource(R.string.status_card_agents_md_label),
                    endActions = {
                        Text(
                            text = agentsSummary(config).ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                colors =
                    CardDefaults.defaultColors(
                        color = raisedSurface(),
                        contentColor = MiuixTheme.colorScheme.onSurface,
                    ),
            ) {
                BasicComponent(
                    title = stringResource(R.string.status_card_model_title),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Tasks,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                BasicComponent(
                    title = stringResource(R.string.status_card_model_label),
                    endActions = {
                        Text(
                            text = config.modelDisplayName.ifEmpty { config.model }.ifEmpty { "—" },
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.status_card_model_provider_label),
                    endActions = {
                        Text(
                            text = config.modelProviderId.ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.status_card_reasoning_label),
                    endActions = {
                        Text(
                            text = config.reasoningEffort.label().ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.status_card_service_tier_label),
                    endActions = {
                        Text(
                            text =
                                config.serviceTier
                                    ?: stringResource(R.string.status_card_service_tier_default)
                                        .ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.status_card_collaboration_label),
                    endActions = {
                        Text(
                            text = config.collaborationMode.label().ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.status_card_approval_label),
                    endActions = {
                        Text(
                            text = config.approvalPolicy.label().ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.status_card_reviewer_label),
                    endActions = {
                        Text(
                            text = config.approvalsReviewer.label().ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.status_card_access_label),
                    endActions = {
                        Text(
                            text = accessSummary(config).ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                colors =
                    CardDefaults.defaultColors(
                        color = raisedSurface(),
                        contentColor = MiuixTheme.colorScheme.onSurface,
                    ),
            ) {
                BasicComponent(
                    title = stringResource(R.string.session_status_usage_title),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                BasicComponent(
                    title = stringResource(R.string.session_status_tokens_total),
                    endActions = {
                        Text(
                            text = formatTokens(usage.total.totalTokens).ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.session_status_tokens_input),
                    endActions = {
                        Text(
                            text = formatTokens(usage.total.inputTokens).ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.session_status_tokens_output),
                    endActions = {
                        Text(
                            text = formatTokens(usage.total.outputTokens).ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.session_status_tokens_cached),
                    endActions = {
                        Text(
                            text = formatTokens(usage.total.cachedInputTokens).ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.session_status_tokens_reasoning),
                    endActions = {
                        Text(
                            text = formatTokens(usage.total.reasoningOutputTokens).ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.session_status_context),
                    endActions = {
                        Text(
                            text =
                                stringResource(
                                    R.string.status_card_usage_percent,
                                    (usage.usedFraction * 100).toInt(),
                                ) +
                                    " / " +
                                    (contextWindow?.let { formatTokens(it) }
                                            ?: stringResource(R.string.status_card_none))
                                        .ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                val estimateValue = estimate?.let { line ->
                    val credits = formatCreditMicros(line.estimatedUsageCreditsMicros)
                    formatEstimatedUsdMicros(line.estimatedUsageUsdMicros)?.let { usd ->
                        "$credits credits · $usd"
                    } ?: "$credits credits"
                }
                if (estimateValue != null) {
                    BasicComponent(
                        title = stringResource(R.string.session_status_estimated_usage),
                        endActions = {
                            Text(
                                text = estimateValue.ifEmpty { "—" },
                                color = MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                        },
                    )
                } else if (estimateFailed) {
                    Text(
                        text = stringResource(R.string.session_status_load_failed),
                        modifier =
                            Modifier.padding(
                                horizontal = UiConsts.Space4,
                                vertical = UiConsts.Space7,
                            ),
                        fontSize = UiType.Footnote,
                        lineHeight = UiType.FootnoteLine,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
                spend.individualLimit?.let { limit ->
                    BasicComponent(
                        title = stringResource(R.string.session_status_spend_control),
                        endActions = {
                            Text(
                                text =
                                    stringResource(
                                            R.string.session_status_spend_control_value,
                                            limit.used,
                                            limit.limit,
                                            limit.remainingPercent,
                                        )
                                        .ifEmpty { "—" },
                                color =
                                    if (spend.spendControlReached == true) colors.error
                                    else null ?: MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                        },
                    )
                }
                if (spend.spendControlReached == true && spend.individualLimit == null) {
                    BasicComponent(
                        title = stringResource(R.string.session_status_spend_control),
                        endActions = {
                            Text(
                                text =
                                    stringResource(R.string.session_status_spend_control_reached)
                                        .ifEmpty { "—" },
                                color = colors.error ?: MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                        },
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                colors =
                    CardDefaults.defaultColors(
                        color = raisedSurface(),
                        contentColor = MiuixTheme.colorScheme.onSurface,
                    ),
            ) {
                BasicComponent(
                    title = stringResource(R.string.session_status_account),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.session_status_plan),
                    endActions = {
                        Text(
                            text = planLabel(account).ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                spend.credits?.let { credits ->
                    BasicComponent(
                        title = stringResource(R.string.status_card_credits_label),
                        endActions = {
                            Text(
                                text =
                                    if (credits.unlimited) {
                                            stringResource(R.string.status_card_credits_unlimited)
                                        } else {
                                            credits.balance
                                                ?: stringResource(R.string.status_card_credits_none)
                                        }
                                        .ifEmpty { "—" },
                                color = MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                        },
                    )
                }
                spend.primary?.let { window ->
                    BasicComponent(
                        title = stringResource(R.string.status_card_rate_primary),
                        endActions = {
                            Text(
                                text =
                                    stringResource(
                                            R.string.status_card_usage_percent,
                                            window.usedPercent.toInt(),
                                        )
                                        .ifEmpty { "—" },
                                color = MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                        },
                    )
                }
                spend.secondary?.let { window ->
                    BasicComponent(
                        title = stringResource(R.string.status_card_rate_secondary),
                        endActions = {
                            Text(
                                text =
                                    stringResource(
                                            R.string.status_card_usage_percent,
                                            window.usedPercent.toInt(),
                                        )
                                        .ifEmpty { "—" },
                                color = MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                        },
                    )
                }
                // The TUI's nudge CTA comes from a backend banner, which this client does not
                // parse;
                // the state that banner reacts to — a reached spend control or an empty credit
                // balance — is already on this page, so the action lives next to it instead.
                val nudgeType =
                    when {
                        spend.spendControlReached == true -> AddCreditsNudgeCreditType.UsageLimit
                        spend.credits?.let { !it.unlimited && !it.hasCredits } == true ->
                            AddCreditsNudgeCreditType.Credits
                        else -> null
                    }
                if (nudgeType != null) {
                    Spacer(Modifier.height(UiConsts.Space10))
                    Button(
                        onClick = {
                            nudging = true
                            nudgeMessage = null
                            scope.launch {
                                app.client
                                    .sendAddCreditsNudgeEmail(nudgeType)
                                    .onSuccess { response ->
                                        nudgeMessage =
                                            when (response.status) {
                                                AddCreditsNudgeEmailStatus.Sent ->
                                                    R.string.status_card_add_credits_sent
                                                AddCreditsNudgeEmailStatus.CooldownActive ->
                                                    R.string.status_card_add_credits_cooldown
                                            }
                                    }
                                    .onFailure {
                                        nudgeMessage = R.string.status_card_add_credits_failed
                                    }
                                nudging = false
                            }
                        },
                        modifier = Modifier,
                        enabled = !nudging,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(text = stringResource(R.string.status_card_add_credits), maxLines = 1)
                    }
                    nudgeMessage?.let { message ->
                        Text(
                            text = stringResource(message),
                            modifier =
                                Modifier.padding(
                                    horizontal = UiConsts.Space4,
                                    vertical = UiConsts.Space7,
                                ),
                            fontSize = UiType.Footnote,
                            lineHeight = UiType.FootnoteLine,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A plain-text snapshot of the page, for the clipboard.
 *
 * Mirrors the "Whole status" entry of the TUI's copy picker (`chatwidget/interaction.rs`): the same
 * facts, one per line. It deliberately skips the estimated usage, which the page fetches
 * asynchronously, so copying never waits on a request.
 */
@Composable
fun sessionStatusReport(app: CodexApp): String? {
    val session = app.widget.state
    if (session.threadId.isBlank()) return null
    val context = LocalContext.current
    fun text(id: Int) = context.getString(id)
    val config = session.config
    val usage = session.usage
    val spend = app.catalog.rateLimits.rateLimits
    val thread =
        (app.catalog.agentThreads + app.threads.threads).firstOrNull { it.id == session.threadId }
    val plan = planLabel(app.catalog.account.account)
    return buildString {
        appendLine(text(R.string.session_status_title))
        appendLine("${text(R.string.session_status_thread_id)}: ${session.threadId}")
        appendLine("${text(R.string.session_status_status)}: ${session.status.label()}")
        thread
            ?.cliVersion
            ?.takeIf { it.isNotBlank() }
            ?.let { version ->
                appendLine("${text(R.string.session_status_created_by)}: $version")
            }
        config.forkedFromId?.let {
            appendLine("${text(R.string.status_card_forked_from_label)}: $it")
        }
        appendLine("${text(R.string.session_status_directory)}: ${config.cwd}")
        config.gitBranch
            ?.takeIf { it.isNotBlank() }
            ?.let {
                appendLine("${text(R.string.session_status_branch)}: $it")
            }
        appendLine(
            "${text(R.string.status_card_model_label)}: ${config.modelDisplayName.ifEmpty { config.model }}"
        )
        appendLine("${text(R.string.status_card_model_provider_label)}: ${config.modelProviderId}")
        appendLine(
            "${text(R.string.status_card_reasoning_label)}: ${config.reasoningEffort.label()}"
        )
        appendLine("${text(R.string.status_card_approval_label)}: ${config.approvalPolicy.label()}")
        appendLine("${text(R.string.status_card_access_label)}: ${accessSummary(config)}")
        appendLine(
            "${text(R.string.session_status_tokens_total)}: ${formatTokens(usage.total.totalTokens)}"
        )
        appendLine(
            "${text(R.string.session_status_context)}: " + "${(usage.usedFraction * 100).toInt()}%"
        )
        spend.primary?.let {
            appendLine("${text(R.string.status_card_rate_primary)}: ${it.usedPercent.toInt()}%")
        }
        spend.secondary?.let {
            appendLine("${text(R.string.status_card_rate_secondary)}: ${it.usedPercent.toInt()}%")
        }
        spend.credits?.let { credits ->
            val balance =
                when {
                    credits.unlimited -> text(R.string.status_card_credits_unlimited)
                    else -> credits.balance ?: text(R.string.status_card_credits_none)
                }
            appendLine("${text(R.string.status_card_credits_label)}: $balance")
        }
        appendLine("${text(R.string.session_status_plan)}: $plan")
    }
}

/** The account's plan name, or the sign-in state when there is no account. */
@Composable
private fun planLabel(account: Account?): String =
    when (account) {
        is Account.Chatgpt -> account.planType
        is Account.ApiKey -> stringResource(R.string.account_plan_api_key)
        is Account.AmazonBedrock -> stringResource(R.string.account_plan_bedrock)
        null -> stringResource(R.string.account_plan_signed_out)
    }

@Composable
private fun StatusBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        minWidth = UiConsts.IconButtonSize,
        minHeight = UiConsts.IconButtonSize,
    ) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.session_status_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}
