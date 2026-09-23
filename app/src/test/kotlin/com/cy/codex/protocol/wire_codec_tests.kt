package com.cy.codex.protocol

import com.cy.codex.protocol.protocol.Json
import com.cy.codex.protocol.protocol.item.AgentMessageDelivery
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.FunctionCallOutputItem
import com.cy.codex.protocol.protocol.item.HookPromptItem
import com.cy.codex.protocol.protocol.item.ImageGenerationFailure
import com.cy.codex.protocol.protocol.item.ImageGenerationItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.WebSearchAction
import com.cy.codex.protocol.protocol.item.WebSearchItem
import com.cy.codex.protocol.protocol.v2.CommandAction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `item` branches of [WireCodec] that only the server can exercise.
 *
 * Every case here is a field the transcript reads, fed the shape the app-server actually sends
 * (`app-server-protocol/src/protocol/v2/item.rs`): the point is to fail when a parser drifts back
 * to a field name the wire does not have, which a type-only schema check cannot catch.
 */
class WireCodecTest {
    private fun item(json: String) = WireCodec.item(Json.parse(json))

    @Test
    fun `hook prompt fragments carry the run id, not a name`() {
        val parsed = item(
            """{"type":"hookPrompt","id":"h","fragments":[
                 {"text":"Retry with tests.","hookRunId":"hook-run-1"}]}"""
        )
        val fragments = assertIs<HookPromptItem>(parsed).fragments
        assertEquals(1, fragments.size)
        assertEquals("hook-run-1", fragments[0].hookRunId)
        assertEquals("Retry with tests.", fragments[0].text)
    }

    @Test
    fun `web search projects results and drops elements with neither title nor url`() {
        val parsed = item(
            """{"type":"webSearch","id":"w","query":"kotlin","action":{"type":"search","query":"kotlin"},
                 "results":[
                   {"type":"text_result","url":"https://example.com/a","title":"A","snippet":"s",
                    "future_field":"ignored"},
                   {"type":"text_result","ref_id":"only-an-id"}]}"""
        )
        val search = assertIs<WebSearchItem>(parsed)
        val results = search.results
        assertEquals(1, results.size, "an element with no title and no url is not a renderable result")
        assertEquals("A", results[0].title)
        assertEquals("https://example.com/a", results[0].url)
        assertEquals("s", results[0].snippet)
        assertEquals("text_result", results[0].type)
        assertEquals(WebSearchAction.Search(query = "kotlin", queries = null), search.action)
    }

    @Test
    fun `command execution parses every command action and the plugin attribution`() {
        val parsed = item(
            """{"type":"commandExecution","id":"c","command":"cat a && rg b && ls",
                 "cwd":"/w","status":"completed","source":"agent","pluginId":"superpowers",
                 "scriptPath":"scripts/x.sh",
                 "commandActions":[
                   {"type":"read","command":"cat a","name":"a","path":"/w/a"},
                   {"type":"listFiles","command":"ls"},
                   {"type":"search","command":"rg b","query":"b","path":"/w"},
                   {"type":"unknown","command":"???"},
                   {"type":"futureAction","command":"x"}]}"""
        )
        val exec = assertIs<CommandExecutionItem>(parsed)
        val actions = exec.commandActions
        assertEquals(4, actions.size, "an unknown future action tag is dropped, not guessed")
        assertEquals(CommandAction.Read("cat a", "a", "/w/a"), actions[0])
        assertEquals(CommandAction.ListFiles("ls", null), actions[1])
        assertEquals(CommandAction.Search("rg b", "b", "/w"), actions[2])
        assertEquals(CommandAction.Unknown("???"), actions[3])
        assertEquals("superpowers", exec.pluginId)
        assertEquals("scripts/x.sh", exec.scriptPath)
    }

    @Test
    fun `image generation reads the fields the union actually has`() {
        val parsed = item(
            """{"type":"imageGeneration","id":"i","status":"failed","result":"",
                 "revisedPrompt":"a red cube","transparentBackground":true,
                 "savedPath":"/tmp/image.png",
                 "failure":{"type":"usageLimitExceeded","limitId":"imagegen","resetsAt":1760000000}}"""
        )
        val image = assertIs<ImageGenerationItem>(parsed)
        assertTrue(image.failed)
        assertEquals("a red cube", image.revisedPrompt)
        assertTrue(image.transparentBackground == true)
        assertEquals("/tmp/image.png", image.savedPath)
        val failure = assertIs<ImageGenerationFailure.UsageLimitExceeded>(image.failure)
        assertEquals("imagegen", failure.limitId)
        assertEquals(1760000000L, failure.resetsAt)
    }

    @Test
    fun `image generation without a revised prompt falls back to its id`() {
        val image = assertIs<ImageGenerationItem>(item("""{"type":"imageGeneration","id":"i","status":"completed","result":"data:image/png;base64,AA"}"""))
        assertFalse(image.failed)
        assertNull(image.failure)
        assertEquals("i", image.detail)
    }

    @Test
    fun `mcp tool call keeps app context, app ui and the read only hint`() {
        val parsed = item(
            """{"type":"mcpToolCall","id":"m","server":"s","tool":"t","status":"completed",
                 "arguments":{},"readOnlyHint":true,"pluginId":"p","mcpAppResourceUri":"ui://legacy",
                 "appContext":{"connectorId":"c","appName":"App","actionName":"Do"},
                 "mcpAppUi":{"resourceUri":"ui://new","preferredModelDisplayMode":"fullscreen"}}"""
        )
        val call = assertIs<McpToolCallItem>(parsed)
        assertEquals("c", call.appContext?.connectorId)
        assertEquals("App", call.appContext?.appName)
        assertEquals("Do", call.appContext?.actionName)
        assertNull(call.appContext?.linkId)
        assertEquals("fullscreen", call.mcpAppUi?.preferredModelDisplayMode)
        assertEquals("ui://new", call.appResourceUri, "the descriptor-captured uri wins over the legacy field")
        assertTrue(call.readOnlyHint == true)
        assertEquals("p", call.pluginId)
    }

    @Test
    fun `mcp tool call falls back to the legacy resource uri and tolerates no app context`() {
        val call = assertIs<McpToolCallItem>(item("""{"type":"mcpToolCall","id":"m","server":"s","tool":"t","status":"failed","arguments":{},"mcpAppResourceUri":"ui://legacy"}"""))
        assertNull(call.appContext)
        assertNull(call.mcpAppUi)
        assertEquals("ui://legacy", call.appResourceUri)
        assertNull(call.readOnlyHint)
    }

    @Test
    fun `agent message keeps the memory citation and delivery`() {
        val parsed = item(
            """{"type":"agentMessage","id":"a","text":"done","delivery":"async",
                 "memoryCitation":{"entries":[{"path":"/w/m.md","lineStart":3,"lineEnd":9,"note":"why"},
                                              {"note":"no path"}],
                                   "threadIds":["t1"]}}"""
        )
        val message = assertIs<AgentMessageItem>(parsed)
        assertEquals(AgentMessageDelivery.Async, message.delivery)
        val citation = message.memoryCitation
        assertEquals(1, citation?.entries?.size, "a citation entry without a path is dropped")
        assertEquals("/w/m.md", citation?.entries?.first()?.path)
        assertEquals(3, citation?.entries?.first()?.lineStart)
        assertEquals(9, citation?.entries?.first()?.lineEnd)
        assertEquals("why", citation?.entries?.first()?.note)
        assertEquals(listOf("t1"), citation?.threadIds)
    }

    @Test
    fun `agent message without a citation or delivery leaves both null`() {
        val message = assertIs<AgentMessageItem>(item("""{"type":"agentMessage","id":"a","text":"hi"}"""))
        assertNull(message.memoryCitation)
        assertNull(message.delivery)
    }

    @Test
    fun `unknown item types stay inspectable instead of dropping the turn`() {
        val parsed = item("""{"type":"brandNewThing","id":"x","payload":1}""")
        val fallback = assertIs<FunctionCallOutputItem>(parsed)
        assertEquals("x", fallback.id)
        assertEquals("brandNewThing", fallback.name)
        assertTrue(fallback.output.contains("\"payload\""), "the raw item is kept so an unknown type is still visible")
    }
}
