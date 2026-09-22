package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.AppEvent
import com.cy.codex.ButtonRole
import com.cy.codex.CatalogState
import com.cy.codex.CodexButton
import com.cy.codex.CodexDivider
import com.cy.codex.EmptyState
import com.cy.codex.R
import com.cy.codex.SectionCard
import com.cy.codex.SurfaceBackButton
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.ValueRow
import com.cy.codex.protocol.protocol.v2.ServerDiagnosticsGauge
import com.cy.codex.protocol.protocol.v2.ServerDiagnosticsProcess
import com.cy.codex.protocol.protocol.v2.ServerDiagnosticsResponse
import com.cy.codex.usageColor
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * What the server says about itself, plus the way to report it when that is not enough.
 *
 * Mirrors `codex-rs/tui/src/debug_config.rs` for the facts and the feedback path of
 * `bottom_pane/feedback_view.rs` for the action. The TUI prints `server/…` as a debug dump and
 * offers feedback from its own overlay; on the phone the two are one page, because the moment a
 * user needs the process id and the gauges is the moment they are about to describe what went
 * wrong.
 *
 * @param catalog read for [CatalogState.diagnostics]; `null` means the probe has not answered yet,
 *   which the page states rather than drawing a process with a pid of zero.
 * @param onEvent receives [AppEvent.ReloadDiagnostics] and [AppEvent.UploadFeedback].
 * @param onBack closes the page; the caller owns navigation.
 * @param modifier layout modifier for the page surface.
 */
@Composable
fun DiagnosticsScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val diagnostics = catalog.diagnostics
    var reporting by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.diagnostics_screen_title),
            subtitle = diagnosticsSubtitle(diagnostics),
            leading = {
                SurfaceBackButton(stringResource(R.string.diagnostics_screen_back), onBack)
            },
            trailing = {
                IconButton(
                    onClick = { onEvent(AppEvent.ReloadDiagnostics) },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.diagnostics_screen_refresh),
                        modifier = Modifier.size(UiConsts.IconRefresh),
                        tint = colors.primary,
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.ScreenMargin)
                .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            if (diagnostics == null) {
                DiagnosticsEmptyCard(onRead = { onEvent(AppEvent.ReloadDiagnostics) })
            } else {
                DiagnosticsProcessCard(diagnostics.process)
                DiagnosticsGaugesCard(diagnostics.gauges)
            }
            // The feedback card is not conditional. This is the page a user reaches *because*
            // something is wrong, and an unanswered probe is the case where a report is worth most:
            // hiding the form behind a successful read would remove it exactly when it is needed.
            DiagnosticsFeedbackCard(onReport = { reporting = true })
        }
    }

    if (reporting) {
        FeedbackFormSheet(
            onDismiss = { reporting = false },
            onSubmit = { classification, reason, includeLogs ->
                onEvent(AppEvent.UploadFeedback(classification, reason, null, includeLogs))
                reporting = false
            },
        )
    }
}

/**
 * The version line under the header, or the reason there is not one.
 *
 * The version is the first thing a bug report needs, so it earns the header's one line. When the
 * server left the field blank the page names the gap instead of composing a "Server" label with
 * nothing after it.
 *
 * @param diagnostics the last probe answer, or `null` when there has never been one.
 */
@Composable
private fun diagnosticsSubtitle(diagnostics: ServerDiagnosticsResponse?): String = when {
    diagnostics == null -> stringResource(R.string.diagnostics_screen_subtitle_unknown)
    diagnostics.process.id <= 0L -> stringResource(R.string.diagnostics_screen_subtitle_unversioned)

    else -> stringResource(R.string.diagnostics_screen_subtitle, diagnostics.process.id)
}

/**
 * The page before the probe has answered.
 *
 * Not an empty card: "the server reported nothing" and "this client has not asked" are different
 * claims, and only one of them is worth a retry. The button repeats what the header's refresh does,
 * because the empty state is where a user looks for it.
 *
 * @param onRead emits the diagnostics read; nothing is drawn until it answers.
 */
@Composable
private fun DiagnosticsEmptyCard(onRead: () -> Unit) {
    SectionCard(
        title = stringResource(R.string.diagnostics_screen_section_server),
        icon = MiuixIcons.Info,
    ) {
        EmptyState(
            icon = MiuixIcons.Info,
            title = stringResource(R.string.diagnostics_screen_not_asked),
            detail = stringResource(R.string.diagnostics_screen_not_asked_detail),
            action = {
                CodexButton(
                    text = stringResource(R.string.diagnostics_screen_check),
                    onClick = onRead,
                    role = ButtonRole.Secondary,
                )
            },
        )
    }
}

/**
 * Identity of the process that answered: which one it is, what it runs, how long it has been up.
 *
 * The pid and the version are set in the monospace face because both are values a user copies into
 * a report, and a proportional face makes `l` and `1` the same shape.
 *
 * @param process the server's own report. `pid` is zero when it did not send one, and that renders
 *   as the empty-value dash rather than as a process the page invented.
 */
@Composable
private fun DiagnosticsProcessCard(process: ServerDiagnosticsProcess) {
    SectionCard(
        title = stringResource(R.string.diagnostics_process_section),
        icon = MiuixIcons.Info,
    ) {
        ValueRow(
            label = stringResource(R.string.diagnostics_process_pid),
            value = formatGaugeValue(process.id.toDouble()),
            monospace = true,
        )
        CodexDivider()
        ValueRow(
            label = stringResource(R.string.diagnostics_process_resident),
            value = process.residentMemoryBytes?.let(::formatBytes).orEmpty(),
            monospace = true,
        )
        CodexDivider()
        ValueRow(
            label = stringResource(R.string.diagnostics_process_footprint),
            value = process.physicalFootprintBytes?.let(::formatBytes).orEmpty(),
            monospace = true,
        )
    }
}

/**
 * The server's gauges, one row each, scaled against the largest one.
 *
 * `server/…` reports named values with no unit and no ceiling — `threads.loaded` and
 * `rollout.bytes` arrive on the same list — so a bar can only be *relative*: each is that gauge's
 * share of the largest value in the same answer. That makes it a comparison between rows of one
 * reading, never a percentage of a capacity, and the card says so rather than letting the bars
 * imply a limit the protocol never sent.
 *
 * @param gauges the list as the server sent it; empty is a real answer and gets a line of its own.
 */
@Composable
private fun DiagnosticsGaugesCard(gauges: List<ServerDiagnosticsGauge>) {
    val colors = MiuixTheme.colorScheme
    // The scale's unit. A list whose largest value is zero has no scale at all, so every bar is
    // drawn empty: "0 of 0" is not "all of it", and dividing by the peak would be a crash.
    val peak = gauges.maxOfOrNull { it.value }?.takeIf { it > 0L } ?: 0L

    SectionCard(
        title = stringResource(R.string.diagnostics_gauges_section),
        icon = MiuixIcons.Tasks,
        trailing = gauges.size.toString(),
    ) {
        if (gauges.isEmpty()) {
            Text(
                text = stringResource(R.string.diagnostics_gauges_empty),
                modifier = Modifier.padding(
                    horizontal = UiConsts.Space4,
                    vertical = UiConsts.Space6,
                ),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.disabledOnSurface,
            )
        } else {
            gauges.forEachIndexed { index, gauge ->
                if (index > 0) CodexDivider()
                DiagnosticsGaugeRow(gauge = gauge, peak = peak)
            }
            Spacer(Modifier.height(UiConsts.Space8))
            Text(
                text = stringResource(R.string.diagnostics_gauges_note, formatGaugeValue(peak.toDouble())),
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = colors.disabledOnSurface,
            )
        }
    }
}

/**
 * One gauge: its name, its value, and its share of the largest gauge drawn as a bar.
 *
 * The bar is the app's existing usage meter rather than a widget of its own — same track, same
 * height step, same [usageColor] ramp as the context-window meter — so a server whose numbers are
 * climbing reads the same way as a context window that is filling up.
 *
 * @param gauge the name and value exactly as the server reported them.
 * @param peak the largest value in the same answer, or `0.0` when there is no scale; the fraction
 *   is clamped to 0..1 so a bar can never overrun its track.
 */
@Composable
private fun DiagnosticsGaugeRow(gauge: ServerDiagnosticsGauge, peak: Long) {
    val colors = MiuixTheme.colorScheme
    val fraction = if (peak > 0L) {
        (gauge.value.toFloat() / peak.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        ValueRow(
            label = gauge.name,
            value = formatGaugeValue(gauge.value.toDouble()),
            monospace = true,
        )
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiConsts.Space4)
                .padding(bottom = UiConsts.Space8),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = usageColor(fraction),
                backgroundColor = colors.onBackground.copy(alpha = 0.08f),
            ),
            height = UiConsts.ProgressHeightRow,
        )
    }
}

/**
 * A gauge value the way the server reported it.
 *
 * A number is data, not copy, so it is not a string resource: a whole value prints without a
 * decimal point (`turns.active` is `1`, not `1.0`) and a fractional one keeps two places, enough
 * to tell two readings apart without a wall of digits. The locale is pinned the way the token
 * formatter pins it, so the decimal point cannot change under a translated build while the digits
 * stay the server's own.
 *
 * @param value the raw double from the answer.
 */
private fun formatGaugeValue(value: Double): String {
    val whole = value.toLong()
    return if (whole.toDouble() == value) {
        whole.toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

/**
 * A byte count the way the server reported it: whole binary units, two decimals at most.
 *
 * The server sends these two fields as raw bytes with no unit attached; binary units are what a
 * memory reading is conventionally shown in, and a null field renders as the empty-value dash
 * rather than as a zero the server never sent.
 */
private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "$bytes ${units[0]}" else String.format(Locale.US, "%.2f %s", value, units[unit])
}

/**
 * The way out of a page that exists for bad news.
 *
 * Mirrors the feedback path of `bottom_pane/feedback_view.rs`: the user classifies what happened
 * and may add a reason. The classification is required because the server files the report under it
 * and the client cannot invent a category; the reason is optional because a user whose session is
 * broken may not be able to type one.
 *
 * The button only opens the form — the sheet's own confirm is the decision — so it is a secondary
 * action and not the page's primary one.
 *
 * @param onReport opens the form; nothing is uploaded until that sheet is confirmed.
 */
@Composable
private fun DiagnosticsFeedbackCard(onReport: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    SectionCard(
        title = stringResource(R.string.diagnostics_feedback_section),
        icon = MiuixIcons.UploadCloud,
    ) {
        Text(
            text = stringResource(R.string.diagnostics_feedback_body),
            modifier = Modifier.padding(horizontal = UiConsts.Space4),
            fontSize = UiType.Meta,
            lineHeight = UiType.MetaLine,
            color = colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(UiConsts.Space10))
        CodexButton(
            text = stringResource(R.string.diagnostics_feedback_action),
            onClick = onReport,
            modifier = Modifier.fillMaxWidth(),
            role = ButtonRole.Secondary,
        )
    }
}

/**
 * The report form: a fixed classification, an optional reason, and the log disclosure.
 *
 * Mirrors `bottom_pane/feedback_view.rs`: the categories are the server's own wire strings, not
 * free text, and logs are attached only when the user explicitly says so — the rollout log carries
 * prompts and tool output, which is exactly why the disclosure is a choice and not a default.
 *
 * `feedback/…` also takes a thread id and this page passes `null` for it: diagnostics is reachable
 * with no thread open, and a guessed id would attach the report to a conversation the user was not
 * looking at. A caller that knows which thread is at fault can send its own event.
 *
 * @param onDismiss closes the sheet without sending.
 * @param onSubmit reports the chosen category, the reason (`null` when left blank so the wire
 *   carries "no reason" instead of an empty string), and the log disclosure.
 */
@Composable
private fun FeedbackFormSheet(
    onDismiss: () -> Unit,
    onSubmit: (classification: String, reason: String?, includeLogs: Boolean) -> Unit,
) {
    val categories = listOf(
        "bad_result" to stringResource(R.string.diagnostics_feedback_category_bad_result),
        "good_result" to stringResource(R.string.diagnostics_feedback_category_good_result),
        "bug" to stringResource(R.string.diagnostics_feedback_category_bug),
        "safety_check" to stringResource(R.string.diagnostics_feedback_category_safety_check),
        "other" to stringResource(R.string.diagnostics_feedback_category_other),
    )
    FormSheet(
        title = stringResource(R.string.diagnostics_feedback_form_title),
        subtitle = stringResource(R.string.diagnostics_feedback_form_subtitle),
        fields = listOf(
            FormField(
                key = "classification",
                label = stringResource(R.string.diagnostics_feedback_classification),
                initial = "bug",
                choices = categories,
                help = stringResource(R.string.diagnostics_feedback_classification_help),
            ),
            FormField(
                key = "reason",
                label = stringResource(R.string.diagnostics_feedback_reason),
                placeholder = stringResource(R.string.diagnostics_feedback_reason_placeholder),
                required = false,
                help = stringResource(R.string.diagnostics_feedback_reason_help),
            ),
            FormField(
                key = "includeLogs",
                label = stringResource(R.string.diagnostics_feedback_include_logs),
                initial = "false",
                choices = listOf(
                    "false" to stringResource(R.string.diagnostics_feedback_logs_no),
                    "true" to stringResource(R.string.diagnostics_feedback_logs_yes),
                ),
                help = stringResource(R.string.diagnostics_feedback_include_logs_help),
            ),
        ),
        confirmLabel = stringResource(R.string.diagnostics_feedback_send),
        onDismiss = onDismiss,
        onSubmit = { values ->
            onSubmit(
                values["classification"].orEmpty().trim(),
                values["reason"].orEmpty().trim().takeIf { it.isNotEmpty() },
                values["includeLogs"] == "true",
            )
        },
    )
}
