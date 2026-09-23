package com.cy.codex.history_cell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.ImageGenerationItem
import com.cy.codex.protocol.protocol.item.ImageViewItem
import com.cy.codex.protocol.protocol.item.SleepItem
import com.cy.codex.protocol.protocol.item.WebSearchAction
import com.cy.codex.protocol.protocol.item.WebSearchItem
import com.cy.codex.protocol.protocol.item.WebSearchResult
import com.cy.codex.ToolCard
import com.cy.codex.ThreadStatusTone
import com.cy.codex.statusDotColor
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Web search, image viewing and generation, and sleeps.
 *
 * Mirrors `codex-rs/tui/src/history_cell/search.rs` and the image helpers in `patches.rs`: a search
 * is a card because its results are the payload, everything else is one compact line because it
 * only narrates what the turn is doing.
 */

@Composable
fun WebSearchCell(
    item: WebSearchItem,
    modifier: Modifier = Modifier,
    resultSpacing: Dp = 9.dp,
    emptyFontSize: TextUnit = UiType.Subtitle,
    emptyLineHeight: TextUnit = UiType.SheetTitle,
) {
    val colors = MiuixTheme.colorScheme
    ToolCard(
        icon = MiuixIcons.Basic.Search,
        title = webSearchTitle(item),
        // A server that does not ship result payloads at all is not a search that found nothing,
        // so the count only appears when there is something to count.
        subtitle = item.results.takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.search_cell_result_count, it.size) },
        modifier = modifier,
    ) {
        if (item.results.isEmpty()) {
            Text(
                text = stringResource(R.string.search_cell_no_results),
                fontSize = emptyFontSize,
                lineHeight = emptyLineHeight,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(resultSpacing)) {
                item.results.forEach { result -> SearchResultRow(result) }
            }
        }
    }
}

/**
 * The web tool's own summary line.
 *
 * Mirrors `web_search_action_detail` and `WebSearchCell::summary` in
 * `codex-rs/tui/src/history_cell/search.rs`: the action names the verb and its own fields the
 * detail, so opening a page reads `Opened <url>` instead of an empty search query.
 */
@Composable
@ReadOnlyComposable
private fun webSearchTitle(item: WebSearchItem): String = when (val action = item.action) {
    is WebSearchAction.OpenPage -> action.url?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.search_cell_opened, it) }
        ?: stringResource(R.string.search_cell_opened_page)

    is WebSearchAction.FindInPage -> {
        val pattern = action.pattern?.takeIf { it.isNotBlank() }
        val url = action.url?.takeIf { it.isNotBlank() }
        when {
            pattern != null && url != null ->
                stringResource(R.string.search_cell_find_in_page, pattern, url)
            pattern != null -> stringResource(R.string.search_cell_searched_for, pattern)
            url != null -> stringResource(R.string.search_cell_searched_page, url)
            else -> stringResource(R.string.search_cell_searched_page_no_url)
        }
    }

    is WebSearchAction.Search -> {
        val detail = action.query?.takeIf { it.isNotBlank() }
            ?: action.queries?.joinToString(", ").orEmpty()
        if (detail.isBlank()) {
            stringResource(R.string.search_cell_searched_web)
        } else {
            stringResource(R.string.search_cell_searched_web_for, detail)
        }
    }

    is WebSearchAction.Other, null -> {
        if (item.query.isBlank()) {
            stringResource(R.string.search_cell_searched_web)
        } else {
            stringResource(R.string.search_cell_searched_web_for, item.query)
        }
    }
}

@Composable
fun ImageViewCell(item: ImageViewItem, modifier: Modifier = Modifier) {
    CompactLine(
        icon = MiuixIcons.Image,
        text = stringResource(R.string.search_cell_view_image),
        detail = item.path,
        modifier = modifier,
    )
}

@Composable
fun ImageGenerationCell(item: ImageGenerationItem, modifier: Modifier = Modifier) {
    val tone = imageGenerationTone(item.status)
    CompactLine(
        icon = MiuixIcons.Photos,
        text =
            stringResource(
                if (item.failed) {
                    R.string.search_cell_image_generation_failed
                } else {
                    R.string.search_cell_image_generation
                }
            ),
        detail = item.revisedPrompt?.takeIf { it.isNotBlank() },
        modifier = modifier,
        tone = tone,
        trailing = { StatusChip(label = imageGenerationLabel(item.status), tone = tone) },
    )
    if (item.savedPath != null) {
        CompactLine(
            icon = MiuixIcons.Photos,
            text = stringResource(R.string.search_cell_image_saved_to),
            detail = item.savedPath,
            modifier = modifier,
        )
    }
}

/**
 * Tone for the server's own image-generation status string.
 *
 * The vocabulary belongs to the server (`ext/items/src/image_generation.rs` only guarantees
 * `completed` and `failed`), so an unknown value reads as still running rather than as a failure.
 */
internal fun imageGenerationTone(status: String): ThreadStatusTone = when (status) {
    ImageGenerationItem.CompletedStatus -> ThreadStatusTone.Done
    ImageGenerationItem.FailedStatus -> ThreadStatusTone.Failed
    else -> ThreadStatusTone.Running
}

/** The chip label for [imageGenerationTone]; the three known statuses reuse the dynamic labels. */
@Composable
@ReadOnlyComposable
internal fun imageGenerationLabel(status: String): String = when (status) {
    ImageGenerationItem.CompletedStatus ->
        stringResource(R.string.mcp_cell_dynamic_status_completed)

    ImageGenerationItem.FailedStatus ->
        stringResource(R.string.mcp_cell_dynamic_status_failed)

    else -> stringResource(R.string.mcp_cell_dynamic_status_calling)
}

@Composable
fun SleepCell(item: SleepItem, modifier: Modifier = Modifier) {
    CompactLine(
        icon = MiuixIcons.Stopwatch,
        text = stringResource(R.string.search_cell_sleep),
        detail = formatToolDuration(item.durationMs),
        modifier = modifier,
    )
}

@Composable
private fun SearchResultRow(
    result: WebSearchResult,
    titleFontSize: TextUnit = UiType.SheetRowTitle,
    titleLineHeight: TextUnit = UiType.SheetRowTitleLine,
    urlFontSize: TextUnit = UiType.Meta,
    urlLineHeight: TextUnit = UiType.Message,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = result.title.ifBlank { result.url },
            fontSize = titleFontSize,
            lineHeight = titleLineHeight,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = result.url,
            fontSize = urlFontSize,
            lineHeight = urlLineHeight,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One-line cell body shared by the narrating items: icon, label, detail and an optional trailing
 * slot (usually a status chip).
 */
@Composable
internal fun CompactLine(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tone: ThreadStatusTone? = null,
    trailing: @Composable (() -> Unit)? = null,
    verticalPadding: Dp = 3.dp,
    iconSize: Dp = 14.dp,
    iconSpacing: Dp = 8.dp,
    fontSize: TextUnit = UiType.RowTitle,
    lineHeight: TextUnit = UiType.RowTitleLine,
    detailSpacing: Dp = 6.dp,
    detailFontSize: TextUnit = UiType.Meta,
    detailLineHeight: TextUnit = UiType.Composer,
    trailingSpacing: Dp = 8.dp,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = tone?.let { statusDotColor(it) } ?: colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(iconSpacing))
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = colors.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(detailSpacing))
        if (detail == null) {
            Spacer(Modifier.weight(1f))
        } else {
            Text(
                text = detail,
                modifier = Modifier.weight(1f),
                fontSize = detailFontSize,
                lineHeight = detailLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(trailingSpacing))
            trailing()
        }
    }
}
