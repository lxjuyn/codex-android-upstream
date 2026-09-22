package com.cy.codex.chatwidget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cy.codex.ButtonRole
import com.cy.codex.CodexButton
import com.cy.codex.ModalSheet
import com.cy.codex.R
import com.cy.codex.copyToClipboard
import com.cy.codex.extractCodeBlocks
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val codeBlocks = remember(response?.id, response?.text) {
        extractCodeBlocks(response?.text.orEmpty())
    }
    ModalSheet(
        show = true,
        onDismiss = onDismiss,
        onDismissFinished = onDismiss,
        title = stringResource(R.string.copy_sheet_title),
    ) {
        if (response == null && status == null) {
            Text(
                text = stringResource(R.string.copy_sheet_nothing),
                modifier = Modifier.fillMaxWidth(),
                fontSize = com.cy.codex.UiType.SheetBody,
                lineHeight = com.cy.codex.UiType.SheetBodyLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            return@ModalSheet
        }
        if (response != null && response.text.isNotBlank()) {
            val label = stringResource(R.string.copy_sheet_whole_response)
            CodexButton(
                text = label,
                onClick = {
                    copyToClipboard(context, response.text, label)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        codeBlocks.forEachIndexed { index, (language, code) ->
            val label = if (language.isNullOrBlank()) {
                stringResource(R.string.copy_sheet_code_block, index + 1)
            } else {
                stringResource(R.string.copy_sheet_code_block_language, index + 1, language)
            }
            CodexButton(
                text = label,
                onClick = {
                    copyToClipboard(context, code, label)
                    onDismiss()
                },
                role = ButtonRole.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        status?.let {
            val label = stringResource(R.string.copy_sheet_status)
            CodexButton(
                text = label,
                onClick = {
                    copyToClipboard(context, it, label)
                    onDismiss()
                },
                role = ButtonRole.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
