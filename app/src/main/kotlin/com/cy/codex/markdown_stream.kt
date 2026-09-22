package com.cy.codex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One block of the markdown-lite document the transcript renders.
 *
 * The shapes match what `parseMarkdown` produced before streaming existed, plus [OpenCode] and
 * [OpenTable] for blocks that are still growing. The model is append-only so the renderer can keep
 * the composition of every finished block and rebuild only the block the latest delta landed in.
 */
sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock

    /** [level] is 1..6, as `codex-rs/tui/src/markdown_render.rs` maps ATX and setext headings. */
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    data class Bullet(val text: String, val depth: Int = 0) : MarkdownBlock
    data class Numbered(val index: Int, val text: String, val depth: Int = 0) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock

    /** A complete fence; [open] is true while the closing marker has not arrived. */
    data class Code(
        val code: String,
        val language: String?,
        val open: Boolean = false,
    ) : MarkdownBlock

    /** A pipe table, already split into cells. [alignments] has one entry per column. */
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val alignments: List<TableAlignment>,
    ) : MarkdownBlock

    /** `---`, `***` or `___` on a line of its own. */
    data object ThematicBreak : MarkdownBlock

    /** A display equation (`$$ … $$`); inline `$…$` stays inside a paragraph's spans. */
    data class Math(val text: String) : MarkdownBlock

    /**
     * A fence whose closing marker has not arrived.
     *
     * This is the one block that is mutated in place rather than replaced: [lines] takes complete
     * lines and [partial] the line still being written, so one delta costs one line instead of a
     * rebuild of the whole code text. The block becomes a [Code] the moment the fence closes.
     */
    class OpenCode(val language: String?) : MarkdownBlock {
        val lines = mutableStateListOf<String>()

        var partial by mutableStateOf("")
            internal set
    }

    /**
     * A table whose terminator has not arrived.
     *
     * Like [OpenCode] this is mutated in place: a new row appends one entry, so a long streaming
     * table does not rebuild its parsed prefix on every delta.
     */
    class OpenTable(
        val header: List<String>,
        val alignments: List<TableAlignment>,
    ) : MarkdownBlock {
        val rows = mutableStateListOf<List<String>>()
    }
}

/** Column alignment of a pipe table, from the `:---:` delimiter row. */
enum class TableAlignment { Start, Center, End }

/**
 * Incremental markdown-lite parser for a streaming message.
 *
 * [append] only scans the new text: a block is frozen into [frozen] as soon as its terminator has
 * been seen, and [tail] holds the single block still being written, so scanning is O(n) over the
 * life of the message instead of the O(n) full re-parse that `remember(markdown)` forced on every
 * delta. [frozen] and [tail] are Compose state, so a delta recomposes the tail view only.
 *
 * The grammar is the tolerant subset the transcript actually sees: paragraphs with hard breaks,
 * ATX and setext headings up to level six, bullet and numbered items nested by indent, block
 * quotes, thematic breaks, indented code, fenced code (backtick or tilde, any fence length, info
 * string) and pipe tables; display math is kept as one block and inline math is left to the span
 * renderer. An unterminated fence renders as code to the end of the buffer.
 */
class MarkdownStream {

    /** Blocks whose terminator has been seen. Append-only; never rewritten. */
    val frozen = mutableStateListOf<MarkdownBlock>()

    /** The block currently being written, or `null` between blocks. */
    var tail by mutableStateOf<MarkdownBlock?>(null)
        private set

    /** True once at least one delta arrived; observable so a cell can pick its body. */
    var hasContent by mutableStateOf(false)
        private set

    val length: Int get() = source.length

    private val source = StringBuilder()
    private var scanPos = 0
    private var fence: Fence? = null
    private var openCode: MarkdownBlock.OpenCode? = null
    private var openTable: MarkdownBlock.OpenTable? = null
    private var indentedCode: MarkdownBlock.OpenCode? = null
    private var mathText: StringBuilder? = null
    private var paragraphStart = -1
    private var paragraphEnd = -1

    /** The whole accumulated source, materialized on demand (completion fallback, copy). */
    val text: String get() = source.toString()

    fun append(delta: String) {
        if (delta.isEmpty()) return
        source.append(delta)
        hasContent = true
        consumeCompleteLines()
        refreshTail()
    }

    /** Every block parsed so far, for callers and tests that want the whole document. */
    fun allBlocks(): List<MarkdownBlock> = if (tail == null) frozen.toList() else frozen + tail!!

    private fun consumeCompleteLines() {
        while (true) {
            val newline = source.indexOf('\n', scanPos)
            if (newline < 0) return
            val lineEnd = newline + 1
            val raw = source.substring(scanPos, newline)
            val line = raw.trimEnd()

            val open = fence
            if (open != null) {
                if (isClosingFence(line, open)) closeFence() else openCode?.lines?.add(raw)
                scanPos = lineEnd
                continue
            }

            val code = indentedCode
            if (code != null) {
                when {
                    line.isBlank() -> code.lines.add("")
                    raw.startsWith("    ") -> code.lines.add(raw.removePrefix("    "))
                    raw.startsWith("\t") -> code.lines.add(raw.removePrefix("\t"))
                    else -> {
                        // The block ends where the indentation does; the line still has to be
                        // classified, so the scanner stays put and re-reads it below.
                        closeIndentedCode()
                        continue
                    }
                }
                scanPos = lineEnd
                continue
            }

            val math = mathText
            if (math != null) {
                if (line.trim() == MathFence) closeMath() else math.append(raw).append('\n')
                scanPos = lineEnd
                continue
            }

            val table = openTable
            if (table != null) {
                val cells = tableCells(line)
                if (cells == null) {
                    closeTable()
                    continue
                }
                table.rows += cells
                scanPos = lineEnd
                continue
            }

            when {
                isFenceOpener(line) != null -> {
                    freezeParagraph()
                    val opener = isFenceOpener(line)!!
                    fence = opener
                    openCode = MarkdownBlock.OpenCode(opener.language)
                    scanPos = lineEnd
                }

                paragraphStart >= 0 && isSetextUnderline(line) -> {
                    val level = if (line.trim()[0] == '=') 1 else 2
                    freezeParagraphAsHeading(level)
                    scanPos = lineEnd
                }

                line.isBlank() -> {
                    freezeParagraph()
                    scanPos = lineEnd
                }

                isThematicBreak(line) -> {
                    freezeParagraph()
                    frozen += MarkdownBlock.ThematicBreak
                    scanPos = lineEnd
                }

                HeadingMarker.matches(line) -> {
                    freezeParagraph()
                    val match = HeadingMarker.find(line)!!
                    frozen += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2])
                    scanPos = lineEnd
                }

                paragraphLineCount() == 1 && isTableDelimiter(line) && headerCells() != null -> {
                    val header = headerCells()!!
                    openTable = MarkdownBlock.OpenTable(header, tableAlignments(line))
                    resetParagraph()
                    scanPos = lineEnd
                }

                line.trim() == MathFence -> {
                    freezeParagraph()
                    mathText = StringBuilder()
                    scanPos = lineEnd
                }

                paragraphStart < 0 && isIndentedCodeStart(raw) -> {
                    freezeParagraph()
                    val block = MarkdownBlock.OpenCode(null)
                    block.lines.add(stripCodeIndent(raw))
                    indentedCode = block
                    scanPos = lineEnd
                }

                line.trimStart().startsWith(">") -> {
                    freezeParagraph()
                    val quoted = line.trimStart().removePrefix(">").removePrefix(" ")
                    frozen += MarkdownBlock.Quote(quoted)
                    scanPos = lineEnd
                }

                BulletMarker.matches(line.trim()) -> {
                    freezeParagraph()
                    frozen += MarkdownBlock.Bullet(
                        text = BulletMarker.find(line.trim())!!.groupValues[1],
                        depth = listDepth(raw),
                    )
                    scanPos = lineEnd
                }

                NumberedMarker.matches(line.trim()) -> {
                    freezeParagraph()
                    val match = NumberedMarker.find(line.trim())!!
                    frozen += MarkdownBlock.Numbered(
                        index = match.groupValues[1].toIntOrNull() ?: 1,
                        text = match.groupValues[2],
                        depth = listDepth(raw),
                    )
                    scanPos = lineEnd
                }

                else -> {
                    if (paragraphStart < 0) paragraphStart = scanPos
                    paragraphEnd = lineEnd
                    scanPos = lineEnd
                }
            }
        }
    }

    /** Lines in the open paragraph, counted from its start to its last complete line. */
    private fun paragraphLineCount(): Int {
        if (paragraphStart < 0) return 0
        var count = 0
        var cursor = paragraphStart
        while (cursor < paragraphEnd) {
            count++
            val next = source.indexOf('\n', cursor)
            if (next < 0 || next + 1 >= paragraphEnd) break
            cursor = next + 1
        }
        return count
    }

    /** The paragraph's first line, used as the header row of a table that follows it. */
    private fun headerCells(): List<String>? {
        if (paragraphStart < 0 || paragraphLineCount() != 1) return null
        val end = source.indexOf('\n', paragraphStart).let { if (it < 0) source.length else it }
        return tableCells(source.substring(paragraphStart, end))
    }

    private fun freezeParagraph() {
        if (paragraphStart < 0) return
        val text = joinParagraph(paragraphStart, paragraphEnd)
        if (text.isNotEmpty()) frozen += MarkdownBlock.Paragraph(text)
        resetParagraph()
    }

    /** Fold an open paragraph into a setext heading instead of a paragraph. */
    private fun freezeParagraphAsHeading(level: Int) {
        if (paragraphStart < 0) return
        val text = joinParagraph(paragraphStart, paragraphEnd)
        if (text.isNotEmpty()) frozen += MarkdownBlock.Heading(level, text)
        resetParagraph()
    }

    private fun resetParagraph() {
        paragraphStart = -1
        paragraphEnd = -1
    }

    private fun closeFence() {
        val code = openCode
        if (code != null) {
            frozen += MarkdownBlock.Code(
                code = trimCodeLines(code.lines),
                language = fence?.language,
            )
        }
        openCode = null
        fence = null
    }

    private fun closeIndentedCode() {
        val code = indentedCode ?: return
        frozen += MarkdownBlock.Code(code = trimCodeLines(code.lines), language = null)
        indentedCode = null
    }

    private fun trimCodeLines(lines: List<String>): String {
        var last = lines.size
        while (last > 0 && lines[last - 1].isEmpty()) last--
        return if (last == 0) "" else lines.take(last).joinToString("\n")
    }

    private fun closeMath() {
        val text = mathText?.toString()?.trimEnd('\n').orEmpty()
        frozen += MarkdownBlock.Math(text)
        mathText = null
    }

    private fun closeTable() {
        val table = openTable ?: return
        frozen += MarkdownBlock.Table(table.header, table.rows.toList(), table.alignments)
        openTable = null
    }

    /**
     * The block still being written.
     *
     * A paragraph is rebuilt from its own lines (a paragraph is bounded by a blank line, so the
     * tail itself is small); an open fence, table or code block is handed over as the same mutable
     * instance each time, which is what keeps a long streaming block linear.
     */
    private fun refreshTail() {
        val open = fence
        if (open != null) {
            val code = openCode
            if (code == null) {
                tail = null
                return
            }
            code.partial = source.substring(scanPos)
            tail = code
            return
        }
        val code = indentedCode
        if (code != null) {
            val partial = source.substring(scanPos)
            code.partial = if (hasCodeIndent(partial)) stripCodeIndent(partial) else ""
            tail = code
            return
        }
        val math = mathText
        if (math != null) {
            tail = MarkdownBlock.Math((math.toString() + source.substring(scanPos)).trimEnd('\n'))
            return
        }
        val table = openTable
        if (table != null) {
            tail = table
            return
        }
        if (paragraphStart >= 0) {
            val text = joinParagraph(paragraphStart, source.length)
            tail = if (text.isEmpty()) null else MarkdownBlock.Paragraph(text)
            return
        }
        if (scanPos >= source.length) {
            tail = null
            return
        }
        // A still-growing line is shown as a paragraph; the scanner re-reads it when it ends and
        // turns it into whatever its first character decides.
        val text = source.substring(scanPos).trim()
        tail = if (text.isEmpty()) null else MarkdownBlock.Paragraph(text)
    }

    /** Join the lines of `[from, to)` with single spaces, honoring hard line breaks. */
    private fun joinParagraph(from: Int, to: Int): String {
        val builder = StringBuilder()
        var cursor = from
        var hardPrevious = false
        while (cursor < to) {
            var end = source.indexOf('\n', cursor)
            if (end !in 0..to) end = to
            val raw = source.substring(cursor, end)
            // Two trailing spaces or a trailing backslash are CommonMark's line-break markers; the
            // marker itself is not part of the text.
            val hard = raw.endsWith("  ") || raw.endsWith("\\")
            var line = raw.trim()
            if (hard && line.endsWith("\\")) line = line.dropLast(1).trimEnd()
            if (line.isNotEmpty()) {
                if (builder.isNotEmpty()) builder.append(if (hardPrevious) '\n' else ' ')
                builder.append(line)
            }
            hardPrevious = hard
            cursor = end + 1
        }
        return builder.toString()
    }
}

private class Fence(val marker: Char, val length: Int, val language: String?)

private const val MathFence = "$$"
private const val BacktickFence = '`'

private val FenceOpener = Regex("^(`{3,}|~{3,})\\s*(.*?)\\s*$")
private val BulletMarker = Regex("^[-*+]\\s+(.*)$")
private val NumberedMarker = Regex("^(\\d+)[.)]\\s+(.*)$")
private val HeadingMarker = Regex("^(#{1,6})\\s+(.*)$")
private val SetextUnderline = Regex("^ {0,3}(=+|-+)\\s*$")
private val ThematicBreak = Regex("^ {0,3}((\\*[ \\t]*){3,}|(-[ \\t]*){3,}|(_[ \\t]*){3,})$")

/** `| --- | :---: |`; the leading/trailing pipes are optional, as in CommonMark. */
private val TableDelimiter =
    Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$")

/** The long fence form (` ``` ` inside a ` ```` ` fence) is legal content, not a closer. */
private fun isClosingFence(line: String, fence: Fence): Boolean {
    val trimmed = line.trim()
    if (trimmed.length < fence.length) return false
    return trimmed.all { it == fence.marker }
}

/**
 * Parse a fence opener, or return `null` when the line is not one.
 *
 * Any run of three or more backticks or tildes opens a fence; a backtick fence's info string may
 * not contain a backtick, which is what keeps the closing marker unambiguous.
 */
private fun isFenceOpener(line: String): Fence? {
    if (!line.startsWith("```") && !line.startsWith("~~~")) return null
    val match = FenceOpener.find(line) ?: return null
    val run = match.groupValues[1]
    val info = match.groupValues[2]
    if (run[0] == BacktickFence && info.contains('`')) return null
    val language = info.split(Regex("\\s+")).firstOrNull()?.ifBlank { null }
    return Fence(run[0], run.length, language)
}

private fun isSetextUnderline(line: String): Boolean =
    SetextUnderline.matches(line) && !line.trim().startsWith("#")

private fun isThematicBreak(line: String): Boolean = ThematicBreak.matches(line)

private fun listDepth(raw: String): Int {
    val indent = raw.length - raw.trimStart().length
    return (indent / 2).coerceIn(0, 6)
}

private fun hasCodeIndent(raw: String): Boolean = raw.startsWith("    ") || raw.startsWith("\t")

private fun stripCodeIndent(raw: String): String =
    if (raw.startsWith("\t")) raw.removePrefix("\t") else raw.removePrefix("    ")

private fun isIndentedCodeStart(raw: String): Boolean {
    if (!hasCodeIndent(raw)) return false
    val stripped = stripCodeIndent(raw).trim()
    // An indented list marker is a nested list, not code.
    return !BulletMarker.matches(stripped) && !NumberedMarker.matches(stripped)
}

/** Split one pipe-table row into cells, or return `null` when the line is not a row. */
private fun tableCells(line: String): List<String>? {
    if (!line.contains('|')) return null
    var text = line.trim()
    if (text.startsWith("|")) text = text.substring(1)
    if (text.endsWith("|") && !text.endsWith("\\|")) text = text.dropLast(1)
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var index = 0
    while (index < text.length) {
        when (val char = text[index]) {
            '\\' if index + 1 < text.length && text[index + 1] == '|' -> {
                cell.append('|')
                index += 2
            }
            '|' -> {
                cells.add(cell.toString().trim())
                cell.clear()
                index++
            }
            else -> {
                cell.append(char)
                index++
            }
        }
    }
    cells.add(cell.toString().trim())
    return cells.ifEmpty { null }
}

private fun isTableDelimiter(line: String): Boolean =
    line.contains('|') && TableDelimiter.matches(line)

private fun tableAlignments(line: String): List<TableAlignment> =
    tableCells(line).orEmpty().map { cell ->
        val start = cell.startsWith(":")
        val end = cell.endsWith(":")
        when {
            start && end -> TableAlignment.Center
            end -> TableAlignment.End
            else -> TableAlignment.Start
        }
    }
