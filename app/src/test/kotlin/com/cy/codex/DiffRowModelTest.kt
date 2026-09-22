package com.cy.codex

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Pins the row precompute the diff body relies on.
 *
 * The renderer folds colours, gutters and signs into [DiffRowModel] once per body so a
 * recomposition only lays a row out. Equality is the other half of that contract: Compose can skip
 * a row only when two folds of unchanged lines compare equal.
 */
class DiffRowModelTest {

    private val palette = DiffPalette(
        addText = Color(0xFF00FF00),
        addSurface = Color(0x1100FF00),
        removeText = Color(0xFFFF0000),
        removeSurface = Color(0x11FF0000),
        hunkSurface = Color(0x110000FF),
        gutter = Color(0x66000000),
        context = Color(0xFF222222),
    )

    private val hunkTextColor = Color(0xFF123456)

    private fun rowsFor(diff: String) = buildDiffRows(
        lines = parseUnifiedDiff(diff),
        palette = palette,
        hunkTextColor = hunkTextColor,
        signAdded = "+",
        signRemoved = "−",
    )

    @Test
    fun foldsGuttersSignsAndColorsPerKind() {
        val rows = rowsFor("@@ -1,2 +1,2 @@\n-old\n+new\n context")
        // The first hunk header is metadata; only content becomes rows.
        assertEquals(3, rows.size)

        assertEquals("1", rows[0].oldLine)
        assertEquals("", rows[0].newLine)
        assertEquals("−", rows[0].sign)
        assertEquals("old", rows[0].text)
        assertEquals(palette.removeSurface, rows[0].background)
        assertEquals(palette.removeText, rows[0].textColor)

        assertEquals("", rows[1].oldLine)
        assertEquals("1", rows[1].newLine)
        assertEquals("+", rows[1].sign)
        assertEquals("new", rows[1].text)
        assertEquals(palette.addSurface, rows[1].background)
        assertEquals(palette.addText, rows[1].textColor)

        assertEquals("2", rows[2].oldLine)
        assertEquals("2", rows[2].newLine)
        assertEquals("", rows[2].sign)
        assertEquals(Color.Transparent, rows[2].background)
        assertEquals(palette.context, rows[2].textColor)
    }

    @Test
    fun fileMetadataIsDroppedAndHunksAreSeparated() {
        val rows = rowsFor(
            listOf(
                "diff --git a/a.kt b/a.kt",
                "--- a/a.kt",
                "+++ b/a.kt",
                "@@ -1 +1 @@",
                "-old",
                "+new",
                "@@ -20 +20 @@",
                " context",
                "\\ No newline at end of file",
            ).joinToString("\n"),
        )
        // Metadata lines produce nothing; the second hunk header becomes the separator.
        assertEquals(4, rows.size)
        assertEquals("old", rows[0].text)
        assertEquals("new", rows[1].text)
        assertEquals(DiffHunkSeparator, rows[2].text)
        assertEquals("", rows[2].sign)
        assertEquals("context", rows[3].text)
    }

    @Test
    fun tabsExpandToFourSpaces() {
        val rows = rowsFor("@@ -1 +1 @@\n-\tindented\n+\tindented more")
        assertEquals("    indented", rows[0].text)
        assertEquals("    indented more", rows[1].text)
    }

    @Test
    fun foldingTheSameLinesTwiceProducesEqualRows() {
        assertEquals(rowsFor("@@ -1 +1 @@\n-old\n+new"), rowsFor("@@ -1 +1 @@\n-old\n+new"))
    }
}
