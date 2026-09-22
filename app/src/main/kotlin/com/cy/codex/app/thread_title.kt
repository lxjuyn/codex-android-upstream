package com.cy.codex.app

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Automatic thread titles, ported from `tui/src/app/thread_title.rs`.
 *
 * A hidden structured turn writes a one-field JSON object; the client normalizes it and calls
 * `thread/name/set`. The server never generates the name itself, so the constraints (one line,
 * 36 characters, no quotes or trailing punctuation) live here.
 */
internal const val ThreadTitleMaxChars = 36
internal const val ThreadTitlePromptMaxBytes = 960

internal fun threadTitleOutputSchema(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put(
        "properties",
        buildJsonObject {
            put(
                "title",
                buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("minLength", JsonPrimitive(1))
                    put("maxLength", JsonPrimitive(ThreadTitleMaxChars))
                },
            )
        },
    )
    put("required", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("title"))))
    put("additionalProperties", JsonPrimitive(false))
}

internal fun threadTitleInstructions(): String =
    "Generate a concise, single-line task title of at most $ThreadTitleMaxChars characters and " +
        "under five words where possible. Start with an imperative verb. Capitalize only the first " +
        "word unless the user's language, proper nouns, acronyms, or code terms require otherwise. " +
        "Preserve ticket references exactly. Write in the user's language. Do not use quotes, " +
        "markdown, or trailing punctuation. Do not answer the request."

/** The bounded title prompt: instructions plus as much of the user message as the byte budget allows. */
internal fun threadTitlePrompt(userMessage: String): String {
    val prefix = threadTitleInstructions() + "\n\nUser prompt:\n"
    val remaining = (ThreadTitlePromptMaxBytes - prefix.toByteArray(Charsets.UTF_8).size).coerceAtLeast(0)
    return prefix + takeUtf8Bytes(userMessage.trim(), remaining)
}

/**
 * Normalize the generated JSON into a title, or null when it is unusable.
 *
 * Mirrors `parse_thread_title`: the response must be a JSON object with a `title` string, which is
 * then trimmed, stripped of wrapping quotes, collapsed to single spaces, stripped of trailing
 * sentence punctuation, and cut to the display limit without splitting a character.
 */
internal fun parseThreadTitle(response: String?): String? {
    val text = response?.trimStart().orEmpty()
    if (!text.startsWith("{")) return null
    val title = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject["title"]?.jsonPrimitive?.contentOrNull
    }.getOrNull() ?: return null
    val normalized = title
        .trim()
        .trim(*WrapCharacters)
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .trimEnd('.', '?', '!')
        .trim()
    if (normalized.isEmpty()) return null
    return takeCodePoints(normalized, ThreadTitleMaxChars)
}

private val WrapCharacters = charArrayOf('"', '\'', '`', '\u201c', '\u201d', '\u2018', '\u2019')

/** At most [max] Unicode scalar values of [text], never splitting a surrogate pair. */
internal fun takeCodePoints(text: String, max: Int): String {
    if (max <= 0) return ""
    if (text.codePointCount(0, text.length) <= max) return text
    return text.substring(0, text.offsetByCodePoints(0, max))
}

/** [text] cut to at most [maxBytes] UTF-8 bytes, never splitting a character. */
internal fun takeUtf8Bytes(text: String, maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
    var bytes = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val width = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
        if (bytes + width > maxBytes) break
        bytes += width
        index += Character.charCount(codePoint)
    }
    return text.substring(0, index)
}
