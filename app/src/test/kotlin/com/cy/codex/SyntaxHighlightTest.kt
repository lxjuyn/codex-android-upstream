package com.cy.codex

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The highlighter is line-oriented and tolerant: unknown languages stay plain, strings and
 * comments win over keywords, and multi-line state (a block comment, a raw string) carries across
 * lines through a [SyntaxLexer].
 */
class SyntaxHighlightTest {

    private val palette = SyntaxPalette(
        plain = Color(0xFF000000),
        keyword = Color(0xFF111111),
        type = Color(0xFF222222),
        string = Color(0xFF333333),
        comment = Color(0xFF444444),
        number = Color(0xFF555555),
        function = Color(0xFF666666),
    )

    private fun styleOf(text: String, language: String, token: String): Color? {
        val styled = highlightCodeLine(text, language, palette)
        val start = styled.text.indexOf(token)
        return styled.spanStyles.firstOrNull { start >= it.start && start < it.end }?.item?.color
    }

    @Test
    fun `unknown languages render plain`() {
        val styled = highlightCodeLine("fun main() {}", "brainfuck", palette)
        assertEquals("fun main() {}", styled.text)
        assertTrue(styled.spanStyles.isEmpty())
    }

    @Test
    fun `keywords strings numbers and comments get their own colors`() {
        assertEquals(palette.keyword, styleOf("fun main() {}", "kotlin", "fun"))
        assertEquals(palette.string, styleOf("val a = \"hi\"", "kotlin", "\"hi\""))
        assertEquals(palette.number, styleOf("val a = 42", "kotlin", "42"))
        assertEquals(palette.comment, styleOf("val a = 1 // note", "kotlin", "// note"))
    }

    @Test
    fun `a line comment hides the code after it`() {
        val styled = highlightCodeLine("val a = 1 // fun", "kotlin", palette)
        val comment = styled.spanStyles.first { it.item.color == palette.comment }
        assertEquals("// fun", styled.text.substring(comment.start, comment.end))
    }

    @Test
    fun `multi-line block comments carry across lines`() {
        val lexer = SyntaxLexer(languageSpec("kotlin")!!, palette)
        val first = lexer.highlight("/* open")
        assertTrue(first.spanStyles.any { it.item.color == palette.comment })
        val second = lexer.highlight("still comment */ val a = 1")
        val comment = second.spanStyles.first { it.item.color == palette.comment }
        assertEquals("still comment */", second.text.substring(comment.start, comment.end))
    }

    @Test
    fun `python triple quoted strings carry across lines`() {
        val lexer = SyntaxLexer(languageSpec("python")!!, palette)
        lexer.highlight("doc = \"\"\"")
        val second = lexer.highlight("text")
        assertTrue(second.spanStyles.any { it.item.color == palette.string })
    }

    @Test
    fun `shell commands highlight keywords and strings`() {
        assertEquals(palette.keyword, styleOf("if [ -f x ]; then", "bash", "if"))
        assertEquals(palette.string, styleOf("echo \"hello\"", "bash", "\"hello\""))
    }

    @Test
    fun `json booleans and numbers are highlighted`() {
        assertEquals(palette.keyword, styleOf("{\"a\": true}", "json", "true"))
        assertEquals(palette.number, styleOf("{\"a\": 12}", "json", "12"))
        val stringStyle = styleOf("{\"a\": 12}", "json", "\"a\"")
        assertEquals(palette.string, stringStyle)
    }

    @Test
    fun `file extensions select a grammar`() {
        assertEquals("rs", languageFromPath("src/main.rs"))
        assertEquals("kt", languageFromPath("app/src/state.kt"))
        assertEquals("dockerfile", languageFromPath("Dockerfile"))
        // An unknown extension resolves to a name the grammar table does not know, which renders
        // plain; it is not an error.
        assertEquals(null, languageSpec(languageFromPath("Vagrantfile")))
    }

    @Test
    fun `oversized blocks fall back to plain lines`() {
        val long = "a".repeat(SyntaxHighlightMaxLineBytes + 1)
        val lines = highlightCodeLines(long, "kotlin", palette)
        assertEquals(1, lines.size)
        assertTrue(lines[0].spanStyles.isEmpty())
    }
}
