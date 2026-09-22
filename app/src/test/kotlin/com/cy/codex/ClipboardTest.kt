package com.cy.codex

import org.junit.Test
import kotlin.test.assertEquals

class ClipboardTest {
    @Test
    fun `fenced code blocks are extracted in document order`() {
        val markdown = """
            Intro
            ```kotlin
            val x = 1
            ```
            text
            ```
            plain
            ```
        """.trimIndent() + "\n"
        assertEquals(
            listOf("kotlin" to "val x = 1", null to "plain"),
            extractCodeBlocks(markdown),
        )
    }

    @Test
    fun `an unterminated fence still yields a copyable block`() {
        assertEquals(listOf("sh" to "echo hi"), extractCodeBlocks("```sh\necho hi"))
    }

    @Test
    fun `text without fences yields no choices`() {
        assertEquals(emptyList(), extractCodeBlocks("just prose"))
    }
}
