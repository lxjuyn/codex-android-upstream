package com.cy.codex

import java.io.File
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Guards against the one Kotlin mistake that fails *silently*.
 *
 * Kotlin nests block comments. A KDoc line like `The last config/…/write result` — written with the
 * two-character comment opener instead of the ellipsis — therefore opens a second comment level that
 * nothing ever closes, and the rest of the file, including every declaration in it, is swallowed.
 * The compiler then blames the *call sites*: one stray opener in a protocol file produced about
 * fifty "Unresolved reference" errors in a different file, and the real cause was nowhere in the
 * error list.
 *
 * Prose keeps reaching for that opener because it is how the protocol names its families
 * (`account`, `process`, `thread/queue`). This test is cheaper than debugging it again.
 */
class SourceCommentTest {

    @Test
    fun `no block comment is opened inside another block comment`() {
        val root = File("src/main/kotlin")
        assertTrue(root.isDirectory, "expected to run from the module directory; looked for $root")

        val offenders = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file -> offenders += scan(file) }

        assertTrue(
            offenders.isEmpty(),
            buildString {
                appendLine("A `/*` inside a block comment silently swallows the rest of the file.")
                appendLine("Write the path as `family/…` instead, or close the comment first:")
                offenders.forEach { appendLine("  $it") }
            },
        )
    }

    /**
     * Return one message per nested comment opener in [file].
     *
     * The scan has two modes, and conflating them is the bug this whole test exists to catch: inside
     * a block comment nothing but the two delimiters and a newline is meaningful, while outside one
     * the string, character and line-comment forms have to be skipped or an apostrophe in prose
     * ("the item's own") is read as the start of a character literal and swallows the closing
     * delimiter.
     */
    private fun scan(file: File): List<String> {
        val text = file.readText()
        val found = mutableListOf<String>()
        var depth = 0
        var line = 1
        var i = 0

        while (i < text.length) {
            val c = text[i]

            // ---- inside a block comment: only the delimiters and newlines matter ----
            if (depth > 0) {
                when {
                    text.startsWith("/*", i) -> {
                        found += "${file.path}:$line opens a comment inside a comment"
                        depth++
                        i += 2
                    }

                    text.startsWith("*/", i) -> {
                        depth--
                        i += 2
                    }

                    c == '\n' -> {
                        line++
                        i++
                    }

                    else -> i++
                }
                continue
            }

            // ---- outside a comment ----
            when {
                c == '\n' -> {
                    line++
                    i++
                }

                // A raw string ends only at the next triple quote; escapes do not apply inside it.
                text.startsWith("\"\"\"", i) -> {
                    i += 3
                    while (i < text.length && !text.startsWith("\"\"\"", i)) {
                        if (text[i] == '\n') line++
                        i++
                    }
                    i += 3
                }

                c == '"' -> {
                    i++
                    while (i < text.length && text[i] != '"') {
                        if (text[i] == '\\') i++
                        if (i < text.length && text[i] == '\n') line++
                        i++
                    }
                    i++
                }

                // A character literal is at most a few characters; anything further away is an
                // apostrophe in prose that a caller has already mishandled.
                c == '\'' -> {
                    val close = text.indexOf('\'', i + 1)
                    if (close in (i + 1)..(i + 3) && !text.substring(i + 1, close).contains('\n')) {
                        i = close + 1
                    } else {
                        i++
                    }
                }

                text.startsWith("//", i) -> {
                    while (i < text.length && text[i] != '\n') i++
                }

                text.startsWith("/*", i) -> {
                    depth++
                    i += 2
                }

                else -> i++
            }
        }

        // An unbalanced file is the same bug seen from the other end.
        if (depth != 0) found += "${file.path} ends with $depth block comment(s) still open"
        return found
    }
}
