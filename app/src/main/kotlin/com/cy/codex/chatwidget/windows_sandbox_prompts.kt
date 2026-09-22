package com.cy.codex.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.R
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.protocol.v2.WindowsSandboxReadiness
import com.cy.codex.protocol.protocol.v2.WindowsSandboxSetupCompletedNotification
import com.cy.codex.protocol.protocol.v2.WindowsSandboxSetupMode
import com.cy.codex.statusDotColor
import com.cy.codex.successColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The Windows sandbox: whether the host can confine a turn, and the two ways to set it up.
 *
 * Mirrors `codex-rs/tui/src/chatwidget/windows_sandbox_prompts.rs`, which asks the user the one
 * question the protocol cannot answer for them. `windowsSandbox/setupStart` takes a mode, and the
 * two modes are not a preference. The elevated one raises a UAC prompt, and that is what lets it
 * install the pieces the sandbox needs; the unelevated one cannot install anything and can only
 * start what is already on the machine. The protocol exposes both because only the user can decide
 * whether to approve that prompt, so this page offers both rather than choosing on their behalf.
 *
 * This is a Windows-only capability and is not offered by the Android runtime's navigation.
 *
 * The page keeps its own copy of the readiness answer as well as reading `CatalogState`, because
 * the catalog copy is only written when a completion notification arrives: without a local read the
 * first visit would show nothing until a setup had already run. The local copy is read on entry and
 * re-read after every completion, which is also what makes the card follow the new state without
 * leaving the page.
 */
@Composable
fun WindowsSandboxScreen(
    catalog: CatalogState,
    client: AppServerClient,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var readiness by remember { mutableStateOf<WindowsSandboxReadiness?>(null) }
    var outcome by remember { mutableStateOf<WindowsSandboxSetupCompletedNotification?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<WindowsSandboxSetupMode?>(null) }
    var loading by remember { mutableStateOf(true) }
    // Bumped by the recheck button. The effect keys on it, so a recheck runs the same code path as
    // the read this page opened with instead of a second one that could drift away from it.
    var generation by remember { mutableStateOf(0) }

    fun read() {
        scope.launch {
            loading = true
            client
                .windowsSandboxReadiness()
                .onSuccess {
                    readiness = it.status
                    failure = null
                }
                .onFailure { failure = it.message }
            loading = false
        }
    }

    LaunchedEffect(generation) { read() }

    // A setup completion is the only place an outcome can arrive from: `windowsSandbox/setupStart`
    // answers as soon as the attempt begins, so success and the error string come back here. The
    // readiness is re-read on the same event because a finished setup is exactly what changes it.
    LaunchedEffect(client) {
        client.events.collect { event ->
            if (event !is AppServerEvent.WindowsSandboxSetupCompleted) return@collect
            outcome = event.delta
            pending = null
            read()
        }
    }

    // Both buttons stay enabled while an attempt is in flight. The start call answers before the
    // work is done, so a button disabled until the completion notification would lock the page
    // behind a notification that a refused start never sends.
    val request: (WindowsSandboxSetupMode) -> Unit = { mode ->
        pending = mode
        outcome = null
        onEvent(AppEvent.WindowsSandboxSetupStart(mode, null))
    }

    // The local answer wins over the catalog's, which is only ever as new as the last notification;
    // the catalog is the fallback for the frame before this page's own read comes back.
    val status = readiness ?: catalog.windowsSandboxReadiness
    val completed = outcome
    val started = pending
    val setupValue =
        when {
            completed != null && completed.success ->
                stringResource(R.string.windows_sandbox_setup_ok, completed.mode.label())

            completed != null ->
                completed.error ?: stringResource(R.string.windows_sandbox_setup_failed)

            started != null ->
                stringResource(R.string.windows_sandbox_setup_starting, started.label())
            else -> ""
        }
    val setupTint =
        when {
            completed?.success == true -> successColor()
            completed != null -> colors.error
            else -> null
        }
    val statusLabel = if (status != null) status.label() else null

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.windows_sandbox_title),
            summary = stringResource(R.string.windows_sandbox_subtitle),
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.windows_sandbox_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            insideMargin = PaddingValues(14.dp, 10.dp),
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
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
            ) {
                BasicComponent(
                    title = stringResource(R.string.windows_sandbox_readiness_card),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    endActions = {
                        if (statusLabel != null) {
                            Text(
                                text = statusLabel,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    },
                )

                BasicComponent(
                    title = stringResource(R.string.windows_sandbox_fact_readiness),
                    endActions = {
                        Text(
                            text =
                                when {
                                    status != null -> status.label()
                                    loading -> stringResource(R.string.windows_sandbox_reading)
                                    else -> stringResource(R.string.windows_sandbox_unknown)
                                }.ifEmpty { "—" },
                            color =
                                (if (status != null) statusDotColor(status.tone()) else null)
                                    ?: MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            fontSize = UiType.Detail,
                        )
                    },
                    insideMargin =
                        PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                BasicComponent(
                    title = stringResource(R.string.windows_sandbox_fact_meaning),
                    endActions = {
                        Text(
                            text = if (status != null) status.detail() else "".ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            fontSize = UiType.Detail,
                        )
                    },
                    insideMargin =
                        PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                BasicComponent(
                    title = stringResource(R.string.windows_sandbox_fact_setup),
                    endActions = {
                        Text(
                            text = setupValue.ifEmpty { "—" },
                            color = (setupTint) ?: MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            fontSize = UiType.Detail,
                        )
                    },
                    insideMargin =
                        PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                )
                if (failure != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                    Text(
                        text = failure.orEmpty(),
                        modifier =
                            Modifier.padding(
                                horizontal = UiConsts.Space4,
                                vertical = UiConsts.Space8,
                            ),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.error,
                    )
                }
            }
            Card(
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
            ) {
                BasicComponent(
                    title = stringResource(R.string.windows_sandbox_setup_card),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                Text(
                    text = stringResource(R.string.windows_sandbox_setup_detail),
                    modifier =
                        Modifier.padding(
                            horizontal = UiConsts.Space4,
                            vertical = UiConsts.Space4,
                        ),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                )
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space6),
                    horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                ) {
                    Button(
                        onClick = { request(WindowsSandboxSetupMode.Elevated) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.windows_sandbox_setup_elevated),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = { request(WindowsSandboxSetupMode.Unelevated) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.windows_sandbox_setup_unelevated),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.windows_sandbox_setup_host_note),
                    modifier =
                        Modifier.padding(
                            horizontal = UiConsts.Space4,
                            vertical = UiConsts.Space2,
                        ),
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Button(
                onClick = { generation++ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.windows_sandbox_recheck),
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Name the readiness the way the card's status row shows it.
 *
 * The wire values (`ready`, `notConfigured`, `updateRequired`) are the protocol's spelling, not the
 * user's; this is the one place that turns them into words, so the card header and the status row
 * cannot end up naming the same state two different ways.
 */
@Composable
@ReadOnlyComposable
private fun WindowsSandboxReadiness.label(): String =
    stringResource(
        when (this) {
            WindowsSandboxReadiness.Ready -> R.string.windows_sandbox_readiness_ready
            WindowsSandboxReadiness.NotConfigured ->
                R.string.windows_sandbox_readiness_not_configured
            WindowsSandboxReadiness.UpdateRequired ->
                R.string.windows_sandbox_readiness_update_required
        }
    )

/**
 * Say what the state means for the host, because the state's name alone does not.
 *
 * "Update required" and "not configured" both read as "something is missing", and they differ in
 * what can be done about it: one is what this page's setup buttons are for, the other is not.
 */
@Composable
@ReadOnlyComposable
private fun WindowsSandboxReadiness.detail(): String =
    stringResource(
        when (this) {
            WindowsSandboxReadiness.Ready -> R.string.windows_sandbox_detail_ready
            WindowsSandboxReadiness.NotConfigured -> R.string.windows_sandbox_detail_not_configured
            WindowsSandboxReadiness.UpdateRequired ->
                R.string.windows_sandbox_detail_update_required
        }
    )

/**
 * Colour the readiness the way every other status in the app is coloured.
 *
 * `Ready` is the only state that lets a turn run confined, so it is the only "done". The other two
 * are both "something still has to happen" and are separated because only one of them is something
 * a setup started from here could fix.
 */
private fun WindowsSandboxReadiness.tone(): ThreadStatusTone =
    when (this) {
        WindowsSandboxReadiness.Ready -> ThreadStatusTone.Done
        WindowsSandboxReadiness.NotConfigured -> ThreadStatusTone.Waiting
        WindowsSandboxReadiness.UpdateRequired -> ThreadStatusTone.Failed
    }

/** Name the setup mode the outcome row reports; the wire values are `elevated` and `unelevated`. */
@Composable
@ReadOnlyComposable
private fun WindowsSandboxSetupMode.label(): String =
    stringResource(
        when (this) {
            WindowsSandboxSetupMode.Elevated -> R.string.windows_sandbox_mode_elevated
            WindowsSandboxSetupMode.Unelevated -> R.string.windows_sandbox_mode_unelevated
        }
    )
