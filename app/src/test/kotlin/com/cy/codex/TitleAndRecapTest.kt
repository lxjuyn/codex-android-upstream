package com.cy.codex

import com.cy.codex.app.RecapPromptPrefix
import com.cy.codex.app.parseRecap
import com.cy.codex.app.parseThreadTitle
import com.cy.codex.app.recapHistory
import com.cy.codex.app.takeUtf8Bytes
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.MessagePhase
import com.cy.codex.protocol.protocol.v2.UserInput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TitleAndRecapTest {

    @Test
    fun `generated titles are normalized like the TUI`() {
        assertEquals("Fix parser crash", parseThreadTitle("""{"title":"  Fix   parser crash. "}"""))
        assertEquals("Ship it", parseThreadTitle("""{"title":"\"Ship it\""}"""))
        assertNull(parseThreadTitle("""{"title":"   "}"""))
        assertNull(parseThreadTitle("not json"))
        assertNull(parseThreadTitle(null))
        val long = parseThreadTitle("""{"title":"${"x".repeat(80)}"}""")
        assertEquals(36, long?.length)
        assertEquals("😀", com.cy.codex.app.takeCodePoints("😀😀", 1))
    }

    @Test
    fun `utf8 truncation never splits a character`() {
        val text = "图a图"
        assertEquals("图", takeUtf8Bytes(text, 3))
        assertEquals("图a", takeUtf8Bytes(text, 4))
        assertEquals("图a", takeUtf8Bytes(text, 6))
        assertEquals("图a图", takeUtf8Bytes(text, 7))
    }

    @Test
    fun `recaps parse their two fields`() {
        val recap = parseRecap("""{"summary":"Did the thing.","next_action":"Run the tests"}""")
        assertEquals("Did the thing.", recap?.summary)
        assertEquals("Run the tests", recap?.nextAction)
        assertNull(parseRecap("""{"summary":"Done.","next_action":null}""")?.nextAction)
        assertNull(parseRecap("""{"summary":"","next_action":null}"""))
        assertNull(parseRecap("nope"))
    }

    @Test
    fun `recap history keeps exchanges and skips commentary`() {
        val items = listOf(
            UserMessageItem("u1", content = listOf(UserInput.Text("first"))),
            AgentMessageItem("a1", "working", phase = MessagePhase.Commentary),
            AgentMessageItem("a2", "answer one"),
            UserMessageItem("u2", content = listOf(UserInput.Text("second"))),
            AgentMessageItem("a3", "answer two"),
        )
        val history = recapHistory(items)!!
        assertTrue(history.startsWith("User: first"))
        assertTrue(history.contains("Assistant: answer one"))
        assertTrue(history.contains("User: second"))
        assertTrue(history.endsWith("Assistant: answer two"))
        assertTrue(!history.contains("working"))
        assertTrue(RecapPromptPrefix.endsWith("Conversation:\n"))
    }
}
