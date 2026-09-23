package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.v2.MemoryStatusResponse
import com.cy.codex.raisedSurface
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import com.cy.codex.successColor
import com.cy.codex.warningColor
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * The memory store: whether it is ready, how much has been consolidated, and the one action that
 * changes it.
 *
 * Mirrors `codex-rs/tui/src/bottom_pane/memories_settings_view.rs`. `memory/…` is a status call and
 * nothing else: it reports a ready flag and a count of consolidated threads, and there is no
 * companion call that returns the memories themselves. So the page shows those two facts and says
 * in as many words that a count is all there is — it must never render an empty list in their
 * place, because an empty list reads as "the store holds nothing", which is a different claim.
 *
 * @param catalog read for [CatalogState.memories]; `null` means this page has not asked yet, which
 *   is deliberately distinct from a store that answered with zero consolidated threads.
 * @param onEvent receives [AppEvent.ReloadMemories] and [AppEvent.ResetMemory].
 * @param onBack closes the page; the caller owns navigation.
 * @param modifier layout modifier for the page surface.
 */
@Composable
fun MemoriesScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val memories = catalog.memories
    var resetting by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.memories_screen_title),
            summary = memoriesSubtitle(memories),
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.memories_screen_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            endActions = {
                IconButton(
                    onClick = { onEvent(AppEvent.ReloadMemories) },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.memories_screen_refresh),
                        modifier = Modifier.size(UiConsts.IconRefresh),
                        tint = colors.primary,
                    )
                }
            },
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
            MemoriesStatusCard(memories = memories, onRead = { onEvent(AppEvent.ReloadMemories) })
            MemoriesResetCard(onReset = { resetting = true })
        }
    }

    if (resetting) {
        ResetMemorySheet(
            onDismiss = { resetting = false },
            onConfirm = {
                onEvent(AppEvent.ResetMemory)
                resetting = false
            },
        )
    }
}

/**
 * The one line under the header: which of the three states this page is in.
 *
 * It sits in the header rather than in the card because the header is the only part of the page
 * that is legible without scrolling, and "not read yet" is exactly what a user has to know before
 * they read anything below it.
 *
 * @param memories the last status answer, or `null` when there has never been one.
 */
@Composable
private fun memoriesSubtitle(memories: MemoryStatusResponse?): String =
    when {
        memories == null -> stringResource(R.string.memories_screen_subtitle_unknown)
        memories.v2Ready ->
            stringResource(
                R.string.memories_screen_subtitle_ready,
                memories.v2ConsolidatedThreads,
            )

        else -> stringResource(R.string.memories_screen_subtitle_not_ready)
    }

/**
 * What the store itself reported.
 *
 * The ready flag and the consolidated-thread count are the whole of `memory/…`. The card keeps the
 * two "nothing here" states apart on purpose: before the first answer it says it has not looked and
 * offers the read, and after an answer of zero it says the store is ready but nothing has been
 * consolidated — a user who cannot tell those apart would read a store that has never been queried
 * as a store that is empty.
 *
 * @param memories the last status answer, or `null` when there has never been one.
 * @param onRead asks for an answer; the header's refresh and this card's button emit the same
 *   request.
 */
@Composable
private fun MemoriesStatusCard(
    memories: MemoryStatusResponse?,
    onRead: () -> Unit,
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
            title = stringResource(R.string.memories_screen_section_status),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Notes,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                (memories?.v2ConsolidatedThreads?.toString())?.let {
                    Text(
                        text = it,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            },
            insideMargin = PaddingValues(0.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (memories == null) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(vertical = UiConsts.Space24, horizontal = UiConsts.Space16),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier.size(UiConsts.IconBoxLarge)
                            .squircleBackground(
                                color = raisedSurface(),
                                cornerRadius = UiConsts.CornerCard,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Notes,
                        contentDescription = null,
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(UiConsts.Space12))
                Text(
                    text = stringResource(R.string.memories_screen_not_asked),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = stringResource(R.string.memories_screen_not_asked_detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space16))
                Button(
                    onClick = onRead,
                    colors = ButtonDefaults.buttonColors(),
                    cornerRadius = UiConsts.ButtonHeight / 2,
                    minWidth = 0.dp,
                    minHeight = UiConsts.ButtonHeight,
                    insideMargin =
                        PaddingValues(
                            horizontal = UiConsts.ButtonPaddingHorizontal,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.memories_screen_check),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            BasicComponent(
                title = stringResource(R.string.memories_screen_store),
                endActions = {
                    Text(
                        text =
                            (if (memories.v2Ready) {
                                    stringResource(R.string.memories_screen_store_ready)
                                } else {
                                    stringResource(R.string.memories_screen_store_not_ready)
                                })
                                .ifEmpty { "—" },
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = UiType.Detail,
                        lineHeight = UiType.DetailLine,
                        fontFamily = null,
                        color = if (memories.v2Ready) successColor() else warningColor(),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                },
                insideMargin =
                    PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            BasicComponent(
                title = stringResource(R.string.memories_screen_consolidated),
                endActions = {
                    Text(
                        text = (memories.v2ConsolidatedThreads.toString()).ifEmpty { "—" },
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = UiType.Detail,
                        lineHeight = UiType.DetailLine,
                        fontFamily = null,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                },
                insideMargin =
                    PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
            )
            if (memories.v2ConsolidatedThreads == 0) {
                Spacer(Modifier.height(UiConsts.Space8))
                MemoriesNote(stringResource(R.string.memories_screen_store_empty))
            }
            Spacer(Modifier.height(UiConsts.Space8))
            MemoriesNote(stringResource(R.string.memories_screen_count_only))
        }
    }
}

/**
 * The destructive action, and the reason it is not the button that acts.
 *
 * A reset has no undo — nothing on the server and nothing in this client can put the store back —
 * so the page's own button only opens the confirmation and [ResetMemorySheet] is the single path
 * that emits [AppEvent.ResetMemory].
 *
 * The button is deliberately not gated on a successful read. Resetting a store whose status could
 * not be read is still a request the server can answer, and hiding the action behind a read that
 * just failed would remove the only way out of a store that is wedged.
 *
 * @param onReset opens the confirmation; it does not emit anything itself.
 */
@Composable
private fun MemoriesResetCard(onReset: () -> Unit) {
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
            title = stringResource(R.string.memories_screen_reset_section),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            insideMargin = PaddingValues(0.dp),
        )
        Spacer(Modifier.height(8.dp))
        MemoriesNote(stringResource(R.string.memories_screen_reset_note))
        Spacer(Modifier.height(UiConsts.Space10))
        Button(
            onClick = onReset,
            modifier =
                Modifier.fillMaxWidth()
                    .squircleBorder(
                        UiConsts.OutlineThickness,
                        MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                        UiConsts.ButtonHeight / 2,
                    ),
            colors =
                ButtonColors(
                    color = Color.Transparent,
                    disabledColor = MiuixTheme.colorScheme.disabledOnSurface.copy(alpha = 0.1f),
                    contentColor = MiuixTheme.colorScheme.error,
                    disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
                ),
            cornerRadius = UiConsts.ButtonHeight / 2,
            minWidth = 0.dp,
            minHeight = UiConsts.ButtonHeight,
            insideMargin =
                PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.memories_screen_reset_action),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A footnote under a card's rows.
 *
 * Always the page's own wording, never a value that came off the wire: `memory/…` has no free-text
 * field, so every sentence rendered through this helper is a string resource.
 *
 * @param text the copy to render, already resolved against the page's own resources.
 */
@Composable
private fun MemoriesNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = UiConsts.Space4),
        fontSize = UiType.Footnote,
        lineHeight = UiType.FootnoteLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * The confirmation that stands in front of [AppEvent.ResetMemory].
 *
 * Dismissal is the caller's, like every other sheet here: the sheet is composed only while it
 * should be visible, so [onDismiss] serves as both the refusal and the end of the exit animation.
 *
 * @param onDismiss a refusal, or the sheet being dragged or tapped away.
 * @param onConfirm the one path that emits the reset.
 */
@Composable
private fun ResetMemorySheet(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
        title = stringResource(R.string.memories_screen_reset_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
        dragHandleColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
        ) {
            Text(
                text = stringResource(R.string.memories_screen_reset_subtitle),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = UiConsts.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                Text(
                    text = stringResource(R.string.memories_screen_reset_body),
                    modifier = Modifier.padding(horizontal = UiConsts.Space4),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceVariantSummary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space4),
                    horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minWidth = 0.dp,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.memories_screen_reset_cancel),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        modifier =
                            Modifier.weight(1f)
                                .squircleBorder(
                                    UiConsts.OutlineThickness,
                                    MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                                    UiConsts.ButtonHeight / 2,
                                ),
                        colors =
                            ButtonColors(
                                color = Color.Transparent,
                                disabledColor =
                                    MiuixTheme.colorScheme.disabledOnSurface.copy(alpha = 0.1f),
                                contentColor = MiuixTheme.colorScheme.error,
                                disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
                            ),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minWidth = 0.dp,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.memories_screen_reset_confirm),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
