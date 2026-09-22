package com.cy.codex

import com.cy.codex.diff.TurnDiffAccumulator
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The accumulator stands between a turn's repeated `turn/diff/updated` payloads and the transcript,
 * so its two contracts are pinned here: the result equals a full parse of the newest payload, and
 * unchanged files keep their parsed instance so downstream `remember`s and row skipping survive a
 * notification.
 */
class TurnDiffAccumulatorTest {

    private val fileA = section(
        path = "app/A.kt",
        body = listOf(
            "@@ -1 +1 @@",
            "-old",
            "+new",
        ),
    )

    private val fileB = section(
        path = "app/B.kt",
        body = listOf(
            "@@ -0,0 +1 @@",
            "+fresh",
        ),
    )

    private val fileC = section(
        path = "app/C.kt",
        body = listOf(
            "@@ -0,0 +1 @@",
            "+third",
        ),
    )

    @Test
    fun splitsAMultiFilePayloadIntoOneEntryPerHeader() {
        val files = TurnDiffAccumulator().apply(fileA + fileB + fileC)
        assertEquals(listOf("app/A.kt", "app/B.kt", "app/C.kt"), files.map { it.path })
        assertEquals(listOf(1, 1, 1), files.map { it.additions })
        assertEquals(1, files[0].removals)
    }

    @Test
    fun appendingToTheGrowingFileKeepsTheEarlierFileInstances() {
        // B's hunk already declares the two lines the append brings, so the grown payload is a
        // strict extension of the first one — the shape a real `turn/diff/updated` stream has.
        val openB = section(
            path = "app/B.kt",
            body = listOf(
                "@@ -0,0 +1,2 @@",
                "+fresh",
            ),
        )
        val accumulator = TurnDiffAccumulator()
        val first = accumulator.apply(fileA + openB)

        val grown = accumulator.apply(fileA + openB + "+more\n")
        assertEquals(2, grown.size)
        assertSame(first[0], grown[0])
        assertNotSame(first[1], grown[1])
        assertEquals(2, grown[1].additions)
        assertEquals(
            listOf("fresh", "more"),
            grown[1].lines.filter { it.kind == DiffLineKind.Add }.map { it.text },
        )
    }

    @Test
    fun appendingAWholeNewFileKeepsEveryEarlierFileInstance() {
        val accumulator = TurnDiffAccumulator()
        val first = accumulator.apply(fileA + fileB)

        val all = accumulator.apply(fileA + fileB + fileC)
        assertEquals(3, all.size)
        assertSame(first[0], all[0])
        assertSame(first[1], all[1])
        assertEquals("app/C.kt", all[2].path)
        assertEquals(listOf("app/A.kt", "app/B.kt", "app/C.kt"), all.map { it.path })
    }

    @Test
    fun appendingSeveralFilesInOneNotificationSplitsThemAll() {
        val accumulator = TurnDiffAccumulator()
        val first = accumulator.apply(fileA)
        val all = accumulator.apply(fileA + fileB + fileC)
        assertEquals(3, all.size)
        assertSame(first[0], all[0])
        assertEquals(1, all[1].additions)
        assertEquals(1, all[2].additions)
    }

    @Test
    fun repeatingAPayloadReturnsTheSameResult() {
        val accumulator = TurnDiffAccumulator()
        val first = accumulator.apply(fileA + fileB)
        assertSame(first, accumulator.apply(fileA + fileB))
    }

    @Test
    fun aPayloadThatDoesNotExtendThePreviousOneIsParsedWhole() {
        val accumulator = TurnDiffAccumulator()
        val first = accumulator.apply(fileA + fileB)
        val replaced = accumulator.apply(fileA)
        assertEquals(1, replaced.size)
        assertEquals("app/A.kt", replaced[0].path)
        assertNotSame(first[0], replaced[0])
    }

    @Test
    fun resetForgetsThePreviousPayload() {
        val accumulator = TurnDiffAccumulator()
        accumulator.apply(fileA + fileB)
        accumulator.reset()
        assertTrue(accumulator.files.isEmpty())

        val afterReset = accumulator.apply(fileB)
        assertEquals(1, afterReset.size)
        assertEquals("app/B.kt", afterReset[0].path)
    }

    @Test
    fun emptyPayloadClearsTheResult() {
        val accumulator = TurnDiffAccumulator()
        accumulator.apply(fileA)
        assertTrue(accumulator.apply("").isEmpty())
        assertTrue(accumulator.apply("   \n").isEmpty())
    }

    @Test
    fun extendingAPayloadThatEndsMidLineReparsesItWhole() {
        // The first payload stops inside the second `diff --git` line, so that header is not a
        // section boundary yet. The notification completing it must split the payload exactly like
        // a full parse, or B's content would stay folded into A forever.
        val completed = fileA +
            "diff --git a/app/B.kt b/app/B.kt\n" +
            "--- a/app/B.kt\n" +
            "+++ b/app/B.kt\n" +
            "@@ -0,0 +1 @@\n" +
            "+fresh\n"
        val accumulator = TurnDiffAccumulator()
        accumulator.apply(fileA + "diff --git a/app/B")

        val files = accumulator.apply(completed)
        assertEquals(parseTurnDiff(completed).map { it.path }, files.map { it.path })
        assertEquals(listOf("app/A.kt", "app/B.kt"), files.map { it.path })
    }

    @Test
    fun singleFilePatchWithoutGitHeaderStillParsesAndGrows() {
        // No `diff --git`, so there is no section to extend; the payload takes the full-parse
        // fallback on every notification and must still match a fresh parse.
        val accumulator = TurnDiffAccumulator()
        val first = accumulator.apply("--- a/only.kt\n+++ b/only.kt\n@@ -1 +1 @@\n-x\n+y\n")
        assertEquals("only.kt", first[0].path)

        val grown = accumulator.apply(
            "--- a/only.kt\n+++ b/only.kt\n@@ -1 +1,2 @@\n-x\n+y\n+z\n",
        )
        assertEquals(1, grown.size)
        assertEquals(2, grown[0].additions)
        assertEquals(1, grown[0].removals)
    }

    /** One `diff --git` section, newline-terminated so sections concatenate like a real payload. */
    private fun section(path: String, body: List<String>): String = (
        listOf(
            "diff --git a/$path b/$path",
            "--- a/$path",
            "+++ b/$path",
        ) + body
        ).joinToString("\n") + "\n"
}
