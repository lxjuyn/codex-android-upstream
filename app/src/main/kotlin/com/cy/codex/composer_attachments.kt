package com.cy.codex

import com.cy.codex.protocol.protocol.v2.ByteRange
import com.cy.codex.protocol.protocol.v2.TextElement

/**
 * A local image staged in the composer.
 *
 * [placeholder] is the `[Image #N]` text inserted into the draft, the way the TUI's
 * `local_image_label_text` inserts an atomic element. The placeholder is what marks the image on
 * the wire: submission keeps only the attachments whose placeholder still appears in the text.
 */
data class ComposerImageAttachment(val path: String, val placeholder: String)

/**
 * Transport ceiling for one image, matching `chatwidget/image_submission.rs`'s
 * `MAX_IMAGE_BYTES`: files above it cannot be base64-encoded inside the 32 MiB frame.
 */
internal const val MaxComposerImageBytes: Long = 32L * 1024 * 1024

/** Extensions `is_image_path` accepts upstream, lowercased. */
private val ImageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp")

/** `[Image #N]` exactly as `protocol/src/models.rs` builds it. */
internal fun imagePlaceholder(index: Int): String = "[Image #$index]"

internal fun isImagePath(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in ImageExtensions

/** The picker's MIME type when it has one, the file name otherwise. */
internal fun isImageAttachment(mimeType: String?, name: String): Boolean =
    mimeType?.startsWith("image/") == true || isImagePath(name)

/**
 * An image path the user pasted, plus the range it occupies in the new draft.
 *
 * [start] is inclusive and [end] exclusive, so the caller can splice the placeholder in with
 * `text.replaceRange(start, end, ...)`.
 */
internal data class PastedImagePath(val path: String, val start: Int, val end: Int)

/**
 * Recognize a paste that was entirely one local image path.
 *
 * The new draft is diffed against the old one to find the inserted run, so typing a character at a
 * time never matches: the run has to be a single path (quoted paths may contain spaces, bare ones
 * may not), must be absolute, and must have an image extension. Existence and size are checked by
 * the caller, which owns the filesystem.
 */
internal fun detectPastedImagePath(previous: String, next: String): PastedImagePath? {
    if (next.length <= previous.length) return null
    var prefix = 0
    val maxPrefix = minOf(previous.length, next.length)
    while (prefix < maxPrefix && previous[prefix] == next[prefix]) prefix++
    var suffix = 0
    val maxSuffix = minOf(previous.length - prefix, next.length - prefix)
    while (suffix < maxSuffix && previous[previous.length - 1 - suffix] == next[next.length - 1 - suffix]) suffix++
    var start = prefix
    var end = next.length - suffix
    while (start < end && next[start].isWhitespace()) start++
    while (end > start && next[end - 1].isWhitespace()) end--
    if (start >= end) return null
    val raw = next.substring(start, end)
    if (raw.contains('\n')) return null
    val path = normalizePastedPath(raw) ?: return null
    if (!path.startsWith('/')) return null
    if (!isImagePath(path)) return null
    return PastedImagePath(path, start, end)
}

/**
 * Strip the decorations a clipboard may wrap a path in.
 *
 * Mirrors the parts of `clipboard_paste.rs:normalize_pasted_path` that apply on Android: quotes
 * and a `file:` scheme. A bare path containing whitespace is rejected, because there is no shell
 * quoting to disambiguate it; a quoted path keeps its spaces.
 */
private fun normalizePastedPath(raw: String): String? {
    val path = when {
        raw.length >= 2 && raw.first() == raw.last() && (raw.first() == '"' || raw.first() == '\'') ->
            raw.substring(1, raw.length - 1)
        raw.any { it.isWhitespace() } -> return null
        else -> raw
    }.removePrefix("file://").ifEmpty { null } ?: return null
    return path
}

/**
 * The byte ranges the [placeholders] occupy in [text], in order.
 *
 * Placeholders are ASCII, but the surrounding text is not: offsets are UTF-8 bytes because that is
 * what `ByteRange` means on the wire. A placeholder the user deleted is skipped, which is how the
 * matching attachment gets pruned at submission.
 */
internal fun placeholderTextElements(text: String, placeholders: List<String>): List<TextElement> {
    val elements = mutableListOf<TextElement>()
    var cursor = 0
    for (placeholder in placeholders) {
        val index = text.indexOf(placeholder, cursor)
        if (index < 0) continue
        val start = text.substring(0, index).toByteArray(Charsets.UTF_8).size
        val end = start + placeholder.toByteArray(Charsets.UTF_8).size
        elements.add(TextElement(ByteRange(start, end), placeholder))
        cursor = index + placeholder.length
    }
    return elements
}
