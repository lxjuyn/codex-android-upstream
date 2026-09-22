package com.cy.codex

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The diff parser is the only thing standing between the server's raw unified diff and the two
 * gutters the transcript draws, so its behaviour is pinned here — including the tolerant cases a
 * streaming turn actually produces.
 */
class DiffModelTest {

    @Test
    fun parsesHunkHeaderAndBothGutters() {
        val lines = parseUnifiedDiff(
            """
            @@ -12,4 +12,6 @@ fun main() {
             context
            -removed
            +added
            +added too
            """.trimIndent(),
        )
        assertEquals(5, lines.size)
        assertEquals(DiffLineKind.Hunk, lines[0].kind)
        assertEquals(DiffLineKind.Context, lines[1].kind)
        assertEquals(12, lines[1].oldLine)
        assertEquals(12, lines[1].newLine)
        assertEquals(DiffLineKind.Remove, lines[2].kind)
        assertEquals(13, lines[2].oldLine)
        assertEquals(null, lines[2].newLine)
        assertEquals(DiffLineKind.Add, lines[3].kind)
        assertEquals(13, lines[3].newLine)
        assertEquals("added", lines[3].text)
        assertEquals(14, lines[4].newLine)
    }

    @Test
    fun treatsFileHeadersAsHunks() {
        val lines = parseUnifiedDiff(
            """
            --- a/foo.kt
            +++ b/foo.kt
            @@ -1 +1 @@
            -a
            +b
            """.trimIndent(),
        )
        assertEquals(DiffLineKind.Hunk, lines[0].kind)
        assertEquals(DiffLineKind.Hunk, lines[1].kind)
        assertEquals(DiffLineKind.Hunk, lines[2].kind)
    }

    @Test
    fun contentLinesThatLookLikeFileHeadersStayContentInsideAHunk() {
        val lines = parseUnifiedDiff(
            listOf(
                "@@ -1,3 +1,3 @@",
                "--- keep",
                "+++ going",
                " context",
            ).joinToString("\n"),
        )
        assertEquals(
            listOf(
                DiffLineKind.Hunk,
                DiffLineKind.Remove,
                DiffLineKind.Add,
                DiffLineKind.Context,
            ),
            lines.map { it.kind },
        )
        assertEquals("-- keep", lines[1].text)
        assertEquals("++ going", lines[2].text)
    }

    @Test
    fun gitHeadersSplitEvenWhenAHunkWasTruncated() {
        // A truncated payload can cut a hunk short. `diff --git` is unambiguous — hunk content
        // always carries a prefix — so it must still open a new section.
        val lines = parseUnifiedDiff(
            listOf(
                "@@ -1,5 +1,5 @@",
                "-a",
                "+b",
                "diff --git a/next.kt b/next.kt",
                "--- a/next.kt",
                "+++ b/next.kt",
            ).joinToString("\n"),
        )
        assertEquals(DiffLineKind.Hunk, lines[3].kind)
        assertEquals(DiffLineKind.Hunk, lines[4].kind)
        assertEquals(DiffLineKind.Hunk, lines[5].kind)
    }

    @Test
    fun fileHeadersAfterAnExhaustedHunkAreStillHeaders() {
        val lines = parseUnifiedDiff(
            listOf(
                "@@ -1 +1 @@",
                "-a",
                "+b",
                "--- a/next.kt",
                "+++ b/next.kt",
            ).joinToString("\n"),
        )
        assertEquals(DiffLineKind.Hunk, lines[3].kind)
        assertEquals(DiffLineKind.Hunk, lines[4].kind)
    }

    @Test
    fun infersFileKindFromTheBody() {
        val added = parseUnifiedDiff("@@ -0,0 +1,2 @@\n+a\n+b")
        assertEquals(DiffFileKind.Added, diffFileKind(added))
        val deleted = parseUnifiedDiff("@@ -1,2 +0,0 @@\n-a\n-b")
        assertEquals(DiffFileKind.Deleted, diffFileKind(deleted))
        val updated = parseUnifiedDiff("@@ -1 +1 @@\n-a\n+b")
        assertEquals(DiffFileKind.Modified, diffFileKind(updated))
    }

    @Test
    fun splitsATurnDiffIntoOneFilePerHeader() {
        val diff = listOf(
            "diff --git a/app/A.kt b/app/A.kt",
            "--- a/app/A.kt",
            "+++ b/app/A.kt",
            "@@ -1 +1 @@",
            "-old",
            "+new",
            "diff --git a/app/B.kt b/app/B.kt",
            "--- a/app/B.kt",
            "+++ b/app/B.kt",
            "@@ -0,0 +1,1 @@",
            "+fresh",
        ).joinToString("\n")

        val files = parseTurnDiff(diff)
        assertEquals(2, files.size)
        assertEquals("app/A.kt", files[0].path)
        assertEquals(DiffFileKind.Modified, files[0].kind)
        assertEquals(1, files[0].additions)
        assertEquals(1, files[0].removals)
        assertEquals("app/B.kt", files[1].path)
        assertEquals(DiffFileKind.Added, files[1].kind)
        assertEquals(1, files[1].additions)
        assertEquals(0, files[1].removals)
    }

    @Test
    fun singleFilePatchWithoutGitHeaderStillYieldsOneEntry() {
        val files = parseTurnDiff("--- a/only.kt\n+++ b/only.kt\n@@ -1 +1 @@\n-x\n+y")
        assertEquals(1, files.size)
        assertEquals("only.kt", files[0].path)
    }

    @Test
    fun emptyDiffYieldsNothing() {
        assertTrue(parseTurnDiff("").isEmpty())
        assertTrue(parseTurnDiff("   \n").isEmpty())
    }

    @Test
    fun fileDiffCarriesDisplayHelpers() {
        val file = fileDiffOf(
            path = "app/src/main/kotlin/com/cy/codex/state/diff_model.kt",
            lines = parseUnifiedDiff("@@ -1 +1 @@\n-a\n+b"),
        )
        assertEquals("diff_model.kt", file.fileName)
        // parentPath shortens a deep directory from the left, keeping the last three
        // segments so the folder the file actually lives in stays readable.
        assertEquals("…/cy/codex/state/", file.parentPath)
        assertEquals("M", file.letter)
    }

    @Test
    fun shortPathsKeepTheirDirectory() {
        val file = fileDiffOf("A.kt", emptyList())
        assertEquals("A.kt", file.fileName)
        assertEquals("", file.parentPath)
    }

    @Test
    fun renameSectionsCarryBothPathsAndTheRenamedKind() {
        val diff = listOf(
            "diff --git a/old/name.kt b/new/name.kt",
            "similarity index 90%",
            "rename from old/name.kt",
            "rename to new/name.kt",
            "--- a/old/name.kt",
            "+++ b/new/name.kt",
            "@@ -1 +1 @@",
            "-old",
            "+new",
        ).joinToString("\n")

        val files = parseTurnDiff(diff)
        assertEquals(1, files.size)
        assertEquals("new/name.kt", files[0].path)
        assertEquals("old/name.kt", files[0].oldPath)
        assertEquals(DiffFileKind.Renamed, files[0].kind)
        assertEquals("R", files[0].letter)
        assertEquals("name.kt → name.kt", files[0].displayName)
    }

    @Test
    fun singleFileRenameWithoutGitHeaderIsStillDetected() {
        val files = parseTurnDiff("--- a/old.kt\n+++ b/new.kt\n@@ -1 +1 @@\n-a\n+b")
        assertEquals("new.kt", files[0].path)
        assertEquals("old.kt", files[0].oldPath)
        assertEquals(DiffFileKind.Renamed, files[0].kind)
    }

    @Test
    fun noNewlineMarkerIsMetadataNotContent() {
        val lines = parseUnifiedDiff(
            "@@ -1 +1 @@\n-old\n+new\n\\ No newline at end of file",
        )
        assertEquals(DiffLineKind.Hunk, lines.last().kind)
    }

    @Test
    fun unparseableLinesBecomeContextRatherThanFailing() {
        val lines = parseUnifiedDiff("this is not a diff at all")
        assertEquals(1, lines.size)
        assertEquals(DiffLineKind.Context, lines[0].kind)
    }
}
