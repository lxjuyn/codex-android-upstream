package com.cy.codex.bottom_pane.chat_composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Reverse history search, mirroring `codex-rs/tui/src/bottom_pane/chat_composer/history_search.rs`.
 *
 * The model half is pure: [historySearchMatches] filters [history] (newest first) for the current
 * query and [nextHistoryMatch] walks the matches with Ctrl+R / Ctrl+S. The TUI keeps the query in
 * a footer while the matched entry stays in the textarea; the bar below the field does the same,
 * adding touch controls because a phone is the primary device here.
 */
internal fun historySearchMatches(history: List<String>, query: String): List<Int> =
    history.indices.filter { history[it].contains(query, ignoreCase = true) }

/**
 * The next match in [matches] from [current], walking toward older entries (higher indices) or
 * newer ones. At a boundary the current match is kept, which is the TUI's `AtBoundary` result.
 */
internal fun nextHistoryMatch(matches: List<Int>, current: Int, older: Boolean): Int {
    if (matches.isEmpty()) return -1
    if (current !in matches) return if (older) matches.first() else matches.last()
    return if (older) {
        matches.firstOrNull { it > current } ?: current
    } else {
        matches.lastOrNull { it < current } ?: current
    }
}

/** The search bar that replaces nothing but rides under the field while search is active. */
@Composable
internal fun HistorySearchBar(
    query: String,
    status: String,
    onQueryChange: (String) -> Unit,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = UiConsts.Space6),
        verticalArrangement = Arrangement.spacedBy(UiConsts.Space2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MiuixIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(UiConsts.IconInline),
                tint = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(UiConsts.Space6))
            BasicTextField(
                enabled = enabled,
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(fontSize = UiType.Body, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAccept() }),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.composer_history_search_hint),
                            fontSize = UiType.Body,
                            lineHeight = UiType.BodyLine,
                            color = colors.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(UiConsts.Space6))
            Text(
                text = status,
                modifier = Modifier.padding(end = UiConsts.Space6),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
            IconButton(
                onClick = onOlder,
                enabled = enabled,
                minWidth = UiConsts.IconButtonSize,
                minHeight = UiConsts.IconButtonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.ChevronBackward,
                    contentDescription = stringResource(R.string.composer_history_search_older),
                    modifier = Modifier.size(UiConsts.IconInline),
                    tint = colors.onSurfaceSecondary,
                )
            }
            IconButton(
                onClick = onNewer,
                enabled = enabled,
                minWidth = UiConsts.IconButtonSize,
                minHeight = UiConsts.IconButtonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = stringResource(R.string.composer_history_search_newer),
                    modifier = Modifier.size(UiConsts.IconInline),
                    tint = colors.onSurfaceSecondary,
                )
            }
            IconButton(
                onClick = onAccept,
                enabled = enabled,
                minWidth = UiConsts.IconButtonSize,
                minHeight = UiConsts.IconButtonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = stringResource(R.string.composer_history_search_accept),
                    modifier = Modifier.size(UiConsts.IconInline),
                    tint = colors.primary,
                )
            }
            IconButton(
                onClick = onCancel,
                enabled = enabled,
                minWidth = UiConsts.IconButtonSize,
                minHeight = UiConsts.IconButtonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.composer_history_search_cancel),
                    modifier = Modifier.size(UiConsts.IconInline),
                    tint = colors.onSurfaceSecondary,
                )
            }
        }
        Text(
            text = stringResource(R.string.composer_history_search_keys),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}
