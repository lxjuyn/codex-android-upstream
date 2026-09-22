package com.cy.codex

/**
 * One row of the composer's `@` popup.
 *
 * Mirrors the candidate shape in `codex-rs/tui/src/bottom_pane/mentions_v2/candidate.rs`: a display
 * label, the text the token is replaced with, and the kind that earns its own tag. The client still
 * submits plain text, so [insert] is spliced verbatim after `@`.
 */
data class MentionSuggestion(
    val insert: String,
    val label: String,
    val detail: String? = null,
    val kind: MentionKind = MentionKind.File,
)

/** Candidate kinds the popup distinguishes, in the order upstream orders them. */
enum class MentionKind { Plugin, Task, File, Directory }

/** Case-insensitive contains-or-subsequence match, the fallback the server-side results replace. */
internal fun mentionMatches(query: String, vararg terms: String): Boolean {
    if (query.isEmpty()) return true
    val needle = query.lowercase()
    return terms.any { term ->
        val haystack = term.lowercase()
        haystack.contains(needle) || isSubsequence(needle, haystack)
    }
}

private fun isSubsequence(needle: String, haystack: String): Boolean {
    var at = 0
    for (ch in haystack) {
        if (at < needle.length && ch == needle[at]) at++
    }
    return at == needle.length
}
