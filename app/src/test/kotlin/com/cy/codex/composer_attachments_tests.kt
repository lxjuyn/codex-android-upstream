package com.cy.codex

import com.cy.codex.protocol.protocol.v2.UserInput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposerAttachmentsTest {

    @Test
    fun `a pasted absolute image path is recognized where it was inserted`() {
        val previous = "look at this: "
        val next = "look at this: /tmp/photo.png"
        val pasted = detectPastedImagePath(previous, next)
        assertEquals("/tmp/photo.png", pasted?.path)
        assertEquals(next.indexOf("/tmp"), pasted?.start)
        assertEquals(next.length, pasted?.end)
    }

    @Test
    fun `a quoted path keeps its spaces and a bare sentence is rejected`() {
        val quoted = detectPastedImagePath("", "\"/tmp/my photo.png\"")
        assertEquals("/tmp/my photo.png", quoted?.path)
        assertNull(detectPastedImagePath("", "/tmp/hello world.png"))
        assertNull(detectPastedImagePath("", "see /tmp/photo.png for details"))
    }

    @Test
    fun `only absolute paths with image extensions qualify`() {
        assertNull(detectPastedImagePath("", "photo.png"))
        assertNull(detectPastedImagePath("", "/tmp/notes.txt"))
        assertEquals("/tmp/photo.webp", detectPastedImagePath("", "file:///tmp/photo.webp")?.path)
    }

    @Test
    fun `byte ranges count utf-8 bytes, not characters`() {
        val text = "图 see [Image #1]"
        val elements = placeholderTextElements(text, listOf("[Image #1]"))
        val element = elements.single()
        assertEquals(8, element.byteRange.start)
        assertEquals(18, element.byteRange.end)
        assertEquals("[Image #1]", element.placeholder)
    }

    @Test
    fun `pending inputs stage every live image and prune deleted placeholders`() {
        val state = SessionState()
        val first = state.addComposerImage("/tmp/a.png")
        val second = state.addComposerImage("/tmp/b.png")
        state.applyDraft("look $first and $second")
        val inputs = state.pendingTurnInputs()
        assertEquals(
            listOf(UserInput.LocalImage("/tmp/a.png"), UserInput.LocalImage("/tmp/b.png")),
            inputs.dropLast(1),
        )
        assertEquals(2, (inputs.last() as UserInput.Text).textElements.size)

        state.applyDraft("only $second")
        assertEquals(listOf("/tmp/b.png"), state.composerImages.map { it.path })
        assertEquals(
            listOf(UserInput.LocalImage("/tmp/b.png")),
            state.pendingTurnInputs().dropLast(1),
        )
    }

    @Test
    fun `removing an image renumbers the placeholders that remain`() {
        val state = SessionState()
        val first = state.addComposerImage("/tmp/a.png")
        val second = state.addComposerImage("/tmp/b.png")
        state.applyDraft("$first $second")
        state.removeComposerImage("/tmp/a.png")
        assertEquals(" [Image #1]", state.composerDraft)
        assertEquals("[Image #1]", state.composerImages.single().placeholder)
        assertTrue(state.pendingTurnInputs().last() is UserInput.Text)
    }

    @Test
    fun `an image-only draft submits without a text input`() {
        val state = SessionState()
        val placeholder = state.addComposerImage("/tmp/a.png")
        state.applyDraft(placeholder)
        val inputs = state.pendingTurnInputs()
        assertEquals(2, inputs.size)
        assertTrue(inputs[0] is UserInput.LocalImage)
        assertEquals("$placeholder", (inputs[1] as UserInput.Text).text)
    }
}
