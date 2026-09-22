package com.cy.codex.diff

import com.cy.codex.FileDiff
import com.cy.codex.gitFileHeaderOffsets
import com.cy.codex.parseFileSection
import com.cy.codex.parseTurnDiff

/**
 * Owns the growing `turn/diff/updated` payload of one turn.
 *
 * The server re-sends the *whole* accumulated diff on every notification, and a long turn emits
 * many of them; a full parse per notification is quadratic in the size of the payload. The
 * accumulator keeps the last payload and its per-file sections, so an append costs the appended
 * tail plus the one section that grew — every unchanged file keeps the very same [FileDiff]
 * instance, which is what lets the transcript's `remember`s and Compose's row skipping keep
 * working.
 *
 * Confined to the single coroutine that reduces server events, so it is deliberately not
 * synchronized.
 */
class TurnDiffAccumulator {

    /** The exact payload [files] was parsed from. */
    private var input: String = ""

    /** Parsed `diff --git` sections in payload order; a growing payload extends the last one. */
    private var sections: List<Section> = emptyList()

    private var parsed: List<FileDiff> = emptyList()

    /** The whole-turn diff; the same list instance until the payload really changes. */
    val files: List<FileDiff> get() = parsed

    /** Forget everything; the next [apply] parses from scratch. New turn or new thread. */
    fun reset() {
        input = ""
        sections = emptyList()
        parsed = emptyList()
    }

    /**
     * Fold the newest accumulated payload and return the parsed whole-turn diff.
     *
     * Returns the same list instance when [diff] equals the last payload, and reuses the parsed
     * [FileDiff] of every file the append did not touch.
     */
    fun apply(diff: String): List<FileDiff> {
        if (diff == input) return parsed
        if (diff.isEmpty()) {
            reset()
            return parsed
        }
        // A payload that does not extend its predecessor has no reusable prefix, and one without
        // `diff --git` headers is a single-file patch whose only section is the whole text, so it
        // has nothing to extend either. A predecessor that ended mid-line may hide half of a
        // header that only the completed line can recognise, so it is not a safe prefix. All three
        // take the full parse.
        if (input.isEmpty() || sections.isEmpty() || !input.endsWith("\n") || !diff.startsWith(input)) {
            return replace(diff)
        }

        val tail = diff.substring(input.length)
        val headerStarts = gitFileHeaderOffsets(tail)
        val last = sections.last()
        val head = if (headerStarts.isEmpty()) tail else tail.substring(0, headerStarts.first())
        // The tail can cut the last section anywhere. When no new content precedes the first new
        // header, the previous section is byte-identical and keeps its parsed instance.
        val grown = if (head.isEmpty()) last else last.reparse(last.source + head)
        val added = headerStarts.mapIndexed { index, start ->
            val end = headerStarts.getOrNull(index + 1) ?: tail.length
            parseSection(tail.substring(start, end))
        }

        sections = sections.dropLast(1) + grown + added
        input = diff
        parsed = sections.map { it.file }
        return parsed
    }

    /**
     * Full parse: split the payload into `diff --git` sections and keep each section's source so a
     * later append can extend the last one without re-splitting the payload.
     */
    private fun replace(diff: String): List<FileDiff> {
        input = diff
        val starts = gitFileHeaderOffsets(diff)
        if (starts.isEmpty()) {
            // No `diff --git` header: parseTurnDiff still has to guess the path from the `---` and
            // `+++` lines, and there is no section a later append could extend.
            sections = emptyList()
            parsed = parseTurnDiff(diff)
            return parsed
        }
        sections = starts.mapIndexed { index, start ->
            val end = starts.getOrNull(index + 1) ?: diff.length
            parseSection(diff.substring(start, end))
        }
        parsed = sections.map { it.file }
        return parsed
    }

    /** One cached `diff --git` section and the [FileDiff] it parsed into. */
    private class Section(val source: String, val file: FileDiff) {
        fun reparse(source: String): Section = Section(source, parseFileSection(source))
    }

    private companion object {
        fun parseSection(source: String): Section = Section(source, parseFileSection(source))
    }
}
