package com.cy.codex

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MentionsTest {
    @Test
    fun `an empty query matches every candidate`() {
        assertTrue(mentionMatches("", "anything"))
    }

    @Test
    fun `matching is case-insensitive contains or subsequence`() {
        assertTrue(mentionMatches("PLAN", "planning"))
        assertTrue(mentionMatches("pln", "planning"))
        assertTrue(mentionMatches("md", "AGENTS.md"))
        assertFalse(mentionMatches("xyz", "planning"))
    }
}
