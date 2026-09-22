package com.cy.codex

import org.junit.Test
import kotlin.test.assertEquals

class SlashInputTest {
    private val known = setOf("diff", "new", "shell")

    @Test
    fun `known commands keep their argument text`() {
        assertEquals(SlashInput.Command("diff", ""), classifySlashInput("/diff", known))
        assertEquals(SlashInput.Command("shell", "ls -la"), classifySlashInput("/shell ls -la", known))
        assertEquals(SlashInput.Command("shell", "ls -la"), classifySlashInput("  /shell ls -la  ", known))
    }

    @Test
    fun `unknown command names are reported rather than treated as text`() {
        assertEquals(SlashInput.Unknown("dff"), classifySlashInput("/dff", known))
        assertEquals(SlashInput.Unknown("dff"), classifySlashInput("/dff some words", known))
    }

    @Test
    fun `paths and plain text are not commands`() {
        assertEquals(SlashInput.NotCommand, classifySlashInput("/home/cy/notes.txt", known))
        assertEquals(SlashInput.NotCommand, classifySlashInput("/", known))
        assertEquals(SlashInput.NotCommand, classifySlashInput("", known))
        assertEquals(SlashInput.NotCommand, classifySlashInput("hello", known))
        assertEquals(SlashInput.NotCommand, classifySlashInput("what about /diff", known))
    }
}
