package com.cy.codex

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Link destinations keep their meaning: a web URL stays a URL, a local path keeps its optional
 * `:line:col` suffix and is displayed relative to the session's working directory.
 */
class LinkTargetTest {

    @Test
    fun `http destinations stay web links`() {
        assertEquals(LinkTarget.Web("https://example.com/a"), parseLinkTarget("https://example.com/a"))
    }

    @Test
    fun `absolute and dot-relative paths are local`() {
        val absolute = parseLinkTarget("/workspace/src/A.kt", "/workspace")
        assertTrue(absolute is LinkTarget.Local)
        assertEquals("/workspace/src/A.kt", absolute.path)
        assertEquals("src/A.kt", absolute.display)

        val relative = parseLinkTarget("./src/B.kt", "/workspace")
        assertTrue(relative is LinkTarget.Local)
        assertEquals("./src/B.kt", relative.path)
    }

    @Test
    fun `colon and hash location suffixes are split off`() {
        val colon = parseLinkTarget("/workspace/src/A.kt:12:4", "/workspace") as LinkTarget.Local
        assertEquals("/workspace/src/A.kt", colon.path)
        assertEquals(":12:4", colon.location)
        assertEquals("src/A.kt:12:4", colon.display)

        val hash = parseLinkTarget("/workspace/src/A.kt#L12C4", "/workspace") as LinkTarget.Local
        assertEquals("L12C4", hash.location?.removePrefix("#"))
        assertEquals("src/A.kt#L12C4", hash.display)
    }

    @Test
    fun `citations become local links`() {
        val text = "see :codex-file-citation{path=\"src/A.kt\"} for details"
        val citation = citationAt(text, text.indexOf(":"))
        assertEquals("src/A.kt", citation?.second)
    }

    @Test
    fun `display path prefers cwd and home`() {
        assertEquals("src/A.kt", displayLocalPath("/workspace/src/A.kt", "/workspace"))
        assertEquals("/other/A.kt", displayLocalPath("/other/A.kt", "/workspace"))
    }

    @Test
    fun `diff paths relativize against cwd and home`() {
        assertEquals("src/A.kt", displayDiffPath("/workspace/src/A.kt", "/workspace", "/home/u"))
        assertEquals("../docs/A.md", displayDiffPath("/workspace/docs/A.md", "/workspace/app", "/home/u"))
        assertEquals("~/notes/A.md", displayDiffPath("/home/u/notes/A.md", "/workspace", "/home/u"))
        assertEquals("/tmp/A.md", displayDiffPath("/tmp/A.md", "/workspace", "/home/u"))
        assertEquals("src/A.kt", displayDiffPath("src/A.kt", "/workspace", "/home/u"))
    }
}
