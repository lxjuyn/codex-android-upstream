package com.cy.codex

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The incremental parser has to be indistinguishable from a full parse of the same bytes, and it
 * has to keep earlier blocks untouched. Both properties are what makes the streaming renderer
 * linear: anything that re-writes a frozen block would recompose a transcript row.
 */
class MarkdownStreamTest {

    private fun parse(text: String): List<MarkdownBlock> =
        MarkdownStream().apply { append(text) }.allBlocks()

    @Test
    fun `chunked append matches a single parse`() {
        val doc = buildString {
            append("# Title\n\n")
            append("First line\nsecond line\n\n")
            append("- bullet one\n- bullet two\n\n")
            append("1. numbered\n\n")
            append("> quoted\n\n")
            append("```kotlin\nval a = 1\nval b = 2\n```\n\n")
            append("tail paragraph")
        }
        val expected = parse(doc)
        for (step in 1..7) {
            val stream = MarkdownStream()
            var index = 0
            while (index < doc.length) {
                val end = (index + step).coerceAtMost(doc.length)
                stream.append(doc.substring(index, end))
                index = end
            }
            assertEquals(expected, stream.allBlocks(), "chunk size $step")
        }
    }

    @Test
    fun `unterminated fence stays one mutable block`() {
        val stream = MarkdownStream()
        stream.append("```kotlin\nval a = 1\nval b =")
        val tail = stream.tail
        assertTrue(tail is MarkdownBlock.OpenCode)
        assertEquals(listOf("val a = 1"), tail.lines.toList())
        assertEquals("val b =", tail.partial)

        stream.append(" 2\n")
        assertEquals("", tail.partial)
        assertEquals(listOf("val a = 1", "val b = 2"), tail.lines.toList())

        stream.append("```\n")
        assertEquals(
            listOf(MarkdownBlock.Code("val a = 1\nval b = 2", "kotlin")),
            stream.frozen.toList(),
        )
        assertEquals(null, stream.tail)
    }

    @Test
    fun `partial line is a paragraph until it ends`() {
        val stream = MarkdownStream()
        stream.append("hello wor")
        assertEquals(MarkdownBlock.Paragraph("hello wor"), stream.tail)

        stream.append("ld\n")
        assertEquals(MarkdownBlock.Paragraph("hello world"), stream.tail)

        stream.append("\n")
        assertEquals(listOf(MarkdownBlock.Paragraph("hello world")), stream.frozen.toList())
        assertEquals(null, stream.tail)
    }

    @Test
    fun `frozen prefix is append only`() {
        val stream = MarkdownStream()
        stream.append("one\n\n")
        stream.append("two\n\n")
        val firstTwo = stream.frozen.toList()

        stream.append("three\n\n")
        assertEquals(3, stream.frozen.size)
        assertTrue(stream.frozen[0] === firstTwo[0])
        assertTrue(stream.frozen[1] === firstTwo[1])
    }

    @Test
    fun `blank line separates space joined paragraphs`() {
        val stream = MarkdownStream()
        stream.append("alpha\nbeta\n\ngamma")
        assertEquals(listOf(MarkdownBlock.Paragraph("alpha beta")), stream.frozen.toList())
        assertEquals(MarkdownBlock.Paragraph("gamma"), stream.tail)
    }

    @Test
    fun `line blocks freeze as soon as the line ends`() {
        val stream = MarkdownStream()
        stream.append("# h\n- b\n2. n\n> q\n")
        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, "h"),
                MarkdownBlock.Bullet("b"),
                MarkdownBlock.Numbered(2, "n"),
                MarkdownBlock.Quote("q"),
            ),
            stream.frozen.toList(),
        )
        assertEquals(null, stream.tail)
    }

    @Test
    fun `a line is not classified until its newline arrives`() {
        val stream = MarkdownStream()
        stream.append("# heading")
        assertEquals(MarkdownBlock.Paragraph("# heading"), stream.tail)

        stream.append("\n")
        assertEquals(listOf(MarkdownBlock.Heading(1, "heading")), stream.frozen.toList())
    }

    @Test
    fun `trailing blank lines in a fence are trimmed`() {
        val stream = MarkdownStream()
        stream.append("```\ncode\n\n\n```\n")
        assertEquals(listOf(MarkdownBlock.Code("code", null)), stream.frozen.toList())
    }

    @Test
    fun `hasContent and text track the appended deltas`() {
        val stream = MarkdownStream()
        assertEquals(false, stream.hasContent)
        assertEquals(0, stream.length)

        stream.append("x")
        stream.append("y")
        assertEquals(true, stream.hasContent)
        assertEquals("xy", stream.text)
    }

    @Test
    fun `blank input produces no blocks`() {
        assertEquals(emptyList<MarkdownBlock>(), parse(""))
        assertEquals(emptyList<MarkdownBlock>(), parse("\n\n   \n"))
    }

    @Test
    fun `headings go up to level six and setext underlines become headings`() {
        assertEquals(
            listOf(MarkdownBlock.Heading(4, "h4"), MarkdownBlock.Heading(6, "h6")),
            parse("#### h4\n\n###### h6\n"),
        )
        assertEquals(
            listOf(MarkdownBlock.Heading(1, "Title")),
            parse("Title\n=====\n"),
        )
        assertEquals(
            listOf(MarkdownBlock.Heading(2, "Subtitle")),
            parse("Subtitle\n---\n"),
        )
    }

    @Test
    fun `horizontal rules are their own block`() {
        assertEquals(
            listOf(MarkdownBlock.ThematicBreak, MarkdownBlock.Paragraph("after")),
            parse("***\n\nafter"),
        )
        assertEquals(listOf(MarkdownBlock.ThematicBreak), parse("_ _ _\n"))
    }

    @Test
    fun `hard breaks survive as newlines in a paragraph`() {
        val blocks = parse("first line  \nsecond line\n\nbackslash\\\nbreak")
        assertEquals(MarkdownBlock.Paragraph("first line\nsecond line"), blocks[0])
        assertEquals(MarkdownBlock.Paragraph("backslash\nbreak"), blocks[1])
    }

    @Test
    fun `fences accept tildes long markers and info strings`() {
        assertEquals(
            listOf(MarkdownBlock.Code("code", "rust")),
            parse("~~~rust\ncode\n~~~\n"),
        )
        assertEquals(
            listOf(MarkdownBlock.Code("code", "rust")),
            parse("````rust title=main.rs\ncode\n````\n"),
        )
        // A fence body may contain a shorter fence of the same character.
        assertEquals(
            listOf(MarkdownBlock.Code("```\ninner", null)),
            parse("````\n```\ninner\n````\n"),
        )
    }

    @Test
    fun `indented code is a code block until the indentation ends`() {
        // A blank line inside indented code is part of the block, as in CommonMark.
        val blocks = parse("    val a = 1\n    val b = 2\n\n    val c = 3\ntext\n")
        assertEquals(
            listOf(
                MarkdownBlock.Code("val a = 1\nval b = 2\n\nval c = 3", null),
                MarkdownBlock.Paragraph("text"),
            ),
            blocks,
        )
    }

    @Test
    fun `list items nest by indent`() {
        assertEquals(
            listOf(
                MarkdownBlock.Bullet("outer"),
                MarkdownBlock.Bullet("inner", depth = 1),
                MarkdownBlock.Bullet("deeper", depth = 2),
                MarkdownBlock.Numbered(3, "counted", depth = 1),
            ),
            parse("- outer\n  - inner\n    - deeper\n  3. counted\n"),
        )
    }

    @Test
    fun `pipe tables freeze when the table ends`() {
        val blocks = parse(
            "| Name | Value |\n| :--- | ---: |\n| a | 1 |\n| b | 2 |\n\nafter\n",
        )
        assertEquals(2, blocks.size)
        val table = blocks[0] as MarkdownBlock.Table
        assertEquals(listOf("Name", "Value"), table.header)
        assertEquals(listOf(listOf("a", "1"), listOf("b", "2")), table.rows)
        assertEquals(listOf(TableAlignment.Start, TableAlignment.End), table.alignments)
        assertEquals(MarkdownBlock.Paragraph("after"), blocks[1])
    }

    @Test
    fun `streaming table rows append without rewriting the table`() {
        val stream = MarkdownStream()
        stream.append("| h |\n| --- |\n| one |")
        assertTrue(stream.tail is MarkdownBlock.OpenTable)
        // The unfinished row is not a row yet: a row is only parsed once its newline arrives.
        assertEquals(emptyList(), (stream.tail as MarkdownBlock.OpenTable).rows.toList())

        stream.append("\n| two |\n")
        assertEquals(
            listOf(listOf("one"), listOf("two")),
            (stream.tail as MarkdownBlock.OpenTable).rows.toList(),
        )

        stream.append("\n")
        assertEquals(
            listOf(MarkdownBlock.Table(listOf("h"), listOf(listOf("one"), listOf("two")), listOf(TableAlignment.Start))),
            stream.frozen.toList(),
        )
    }

    @Test
    fun `display math is one block`() {
        assertEquals(
            listOf(MarkdownBlock.Math("x^2 + y^2 = z^2")),
            parse("$$\nx^2 + y^2 = z^2\n$$\n"),
        )
    }

    @Test
    fun `chunked append still matches a single parse with the new blocks`() {
        val doc = buildString {
            append("### Title\n\n")
            append("para  \nwith break\n\n")
            append("- a\n  - b\n\n")
            append("| h | h2 |\n| --- | --- |\n| 1 | 2 |\n\n")
            append("~~~json\n{\"a\": 1}\n~~~\n\n")
            append("    indented\n\ntext \$x\$\n")
        }
        val expected = parse(doc)
        for (step in 1..6) {
            val stream = MarkdownStream()
            var index = 0
            while (index < doc.length) {
                val end = (index + step).coerceAtMost(doc.length)
                stream.append(doc.substring(index, end))
                index = end
            }
            assertEquals(expected, stream.allBlocks(), "chunk size $step")
        }
    }
}
