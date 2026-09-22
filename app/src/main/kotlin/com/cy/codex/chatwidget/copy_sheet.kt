package com.cy.codex.chatwidget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.copyToClipboard
import com.cy.codex.extractCodeBlocks
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * The `/copy` picker.
 *
 * Mirrors `codex-rs/tui/src/chatwidget/interaction.rs::show_copy_picker`: the whole last response,
 * each code block it contains, and — when a status snapshot exists — the status report. The TUI
 * also offers a status-only picker when `/status` was the last thing printed; on the phone the
 * status page owns its own copy button, so this sheet always leads with the response.
 */
@Composable
fun CopySheet(
    response: AgentMessageItem?,
    status: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val codeBlocks =
        remember(response?.id, response?.text) {
            extractCodeBlocks(response?.text.orEmpty())
        }
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.copy_sheet_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = UiConsts.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        ) {
            if (response == null && status == null) {
                Text(
                    text = stringResource(R.string.copy_sheet_nothing),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = com.cy.codex.UiType.SheetBody,
                    lineHeight = com.cy.codex.UiType.SheetBodyLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                return@WindowBottomSheet
            }
            if (response != null && response.text.isNotBlank()) {
                val label = stringResource(R.string.copy_sheet_whole_response)
                Button(
                    onClick = {
                        copyToClipboard(context, response.text, label)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
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
                        text = label,
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            codeBlocks.forEachIndexed { index, (language, code) ->
                val label =
                    if (language.isNullOrBlank()) {
                        stringResource(R.string.copy_sheet_code_block, index + 1)
                    } else {
                        stringResource(R.string.copy_sheet_code_block_language, index + 1, language)
                    }
                Button(
                    onClick = {
                        copyToClipboard(context, code, label)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
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
                        text = label,
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            status?.let {
                val label = stringResource(R.string.copy_sheet_status)
                Button(
                    onClick = {
                        copyToClipboard(context, it, label)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
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
                        text = label,
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
}
