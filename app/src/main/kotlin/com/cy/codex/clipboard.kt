package com.cy.codex

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * System-clipboard helpers shared by `/copy`, the message cells, the code fences and the status
 * pages.
 *
 * The TUI copies through `codex-rs/tui/src/clipboard_copy.rs`, which has no Android backend (the
 * arboard calls return an error there). The phone uses the platform service instead; the shapes of
 * what gets copied — a whole agent message, one fenced block, the status report — stay aligned with
 * `chatwidget/interaction.rs::show_copy_picker`.
 */

/**
 * Put [text] on the clipboard, tagging the clip with [label] and confirming it in a toast.
 *
 * Blank text is dropped rather than replacing the clip with nothing: every caller derives its text
 * from UI state, and "copy" on an empty cell should not silently destroy the previous clip.
 */
fun copyToClipboard(context: Context, text: String, label: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(
        context,
        context.getString(R.string.clipboard_copied, label),
        Toast.LENGTH_SHORT,
    ).show()
}

/** Fenced code blocks of [markdown] in document order: the info string (may be null) and source. */
fun extractCodeBlocks(markdown: String): List<Pair<String?, String>> {
    if (markdown.isBlank()) return emptyList()
    val stream = MarkdownStream().apply { append(markdown) }
    return stream.allBlocks().mapNotNull { block ->
        when (block) {
            is MarkdownBlock.Code -> block.language to block.code
            is MarkdownBlock.OpenCode -> {
                val source = (block.lines + block.partial).joinToString("\n").trimEnd('\n')
                if (source.isBlank()) null else block.language to source
            }
            else -> null
        }
    }
}
