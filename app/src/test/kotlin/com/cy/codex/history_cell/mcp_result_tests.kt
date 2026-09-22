package com.cy.codex.history_cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MCP result projection mirrors `codex-rs/tui/src/history_cell/mcp_result.rs`: text stays
 * verbatim, media bodies never reach the transcript, malformed blocks keep their exact JSON.
 */
class McpResultProjectionTest {
    @Test
    fun `text blocks are kept and media blocks become summaries`() {
        val result = """
            {"content":[
              {"type":"text","text":"hello"},
              {"type":"image","data":"aGVsbG8=","mimeType":"image/png"},
              {"type":"audio","data":"YXVkaW8=","mimeType":"audio/wav"}
            ],"isError":false}
        """.trimIndent()

        assertEquals(
            listOf(
                ToolContentBlock.Text("hello"),
                ToolContentBlock.Summary("Returned image"),
                ToolContentBlock.Summary("<audio content>"),
            ),
            projectMcpResult(result),
        )
    }

    @Test
    fun `embedded resources and links keep their uri`() {
        val result = """
            {"content":[
              {"type":"resource","resource":{"uri":"file:///tmp/a.txt","text":"body"}},
              {"type":"resource_link","uri":"plugin://demo","name":"demo"}
            ]}
        """.trimIndent()

        assertEquals(
            listOf(
                ToolContentBlock.Summary("embedded resource: file:///tmp/a.txt"),
                ToolContentBlock.Summary("link: plugin://demo"),
            ),
            projectMcpResult(result),
        )
    }

    @Test
    fun `unknown blocks and non-object results keep their json`() {
        val unknown = projectMcpResult("""{"content":[{"type":"weird","value":1}]}""").single()
        assertEquals(ToolContentBlock.RawJson("""{"type":"weird","value":1}"""), unknown)

        val legacy = projectMcpResult("plain old result").single()
        assertEquals(ToolContentBlock.RawJson("plain old result"), legacy)
    }

    @Test
    fun `missing result projects to nothing`() {
        assertTrue(projectMcpResult(null).isEmpty())
        assertTrue(projectMcpResult("   ").isEmpty())
        assertTrue(projectMcpResult("""{"content":[]}""").isEmpty())
    }
}
