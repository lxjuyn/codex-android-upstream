package com.cy.codex.keymap

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The pure keymap, on a plain JVM: every rule the adapter relies on is decided here, so this suite
 * is the contract for both the Compose handler and any future host.
 */
class KeymapTest {

    private fun char(
        c: Char,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ) = KeyChord(CodexKeys.char(c), ctrl = ctrl, alt = alt, shift = shift)

    @Test
    fun `global chords match the TUI defaults`() {
        assertEquals(KeyAction.InterruptTurn, CodexKeymap.resolve(KeyContext.Global, KeyChord(CodexKeys.ESCAPE)))
        assertEquals(KeyAction.InterruptTurn, CodexKeymap.resolve(KeyContext.Global, char('c', ctrl = true)))
        assertEquals(KeyAction.OpenTranscript, CodexKeymap.resolve(KeyContext.Global, char('t', ctrl = true)))
        assertEquals(KeyAction.ShowShortcuts, CodexKeymap.resolve(KeyContext.Global, char('/', ctrl = true)))
        assertEquals(KeyAction.ShowShortcuts, CodexKeymap.resolve(KeyContext.Global, KeyChord(CodexKeys.F1)))
    }

    @Test
    fun `chat context owns escape and falls back to global chords`() {
        assertEquals(KeyAction.InterruptTurn, CodexKeymap.resolve(KeyContext.Chat, KeyChord(CodexKeys.ESCAPE)))
        assertEquals(KeyAction.OpenTranscript, CodexKeymap.resolve(KeyContext.Chat, char('t', ctrl = true)))
        assertEquals(KeyAction.InterruptTurn, CodexKeymap.resolve(KeyContext.Chat, char('c', ctrl = true)))
    }

    @Test
    fun `enter submits while every newline spelling inserts one`() {
        assertEquals(KeyAction.Submit, CodexKeymap.resolve(KeyContext.Composer, KeyChord(CodexKeys.ENTER)))
        assertEquals(KeyAction.InsertNewline, CodexKeymap.resolve(KeyContext.Composer, KeyChord(CodexKeys.ENTER, shift = true)))
        assertEquals(KeyAction.InsertNewline, CodexKeymap.resolve(KeyContext.Composer, KeyChord(CodexKeys.ENTER, alt = true)))
        assertEquals(KeyAction.InsertNewline, CodexKeymap.resolve(KeyContext.Composer, char('j', ctrl = true)))
        assertEquals(KeyAction.InsertNewline, CodexKeymap.resolve(KeyContext.Composer, char('m', ctrl = true)))
    }

    @Test
    fun `question mark toggles shortcuts and escape leaves the composer`() {
        assertEquals(KeyAction.ShowShortcuts, CodexKeymap.resolve(KeyContext.Composer, char('?')))
        assertEquals(KeyAction.ShowShortcuts, CodexKeymap.resolve(KeyContext.Composer, char('?', shift = true)))
        assertEquals(KeyAction.ClearFocus, CodexKeymap.resolve(KeyContext.Composer, KeyChord(CodexKeys.ESCAPE)))
    }

    @Test
    fun `ctrl r and ctrl s drive the reverse history search`() {
        assertEquals(KeyAction.HistoryOlder, CodexKeymap.resolve(KeyContext.Composer, char('r', ctrl = true)))
        assertEquals(KeyAction.HistoryNewer, CodexKeymap.resolve(KeyContext.Composer, char('s', ctrl = true)))
    }

    @Test
    fun `ctrl o and ctrl g reach the host copy and editor hooks`() {
        assertEquals(KeyAction.CopyLastResponse, CodexKeymap.resolve(KeyContext.Composer, char('o', ctrl = true)))
        assertEquals(KeyAction.OpenExternalEditor, CodexKeymap.resolve(KeyContext.Composer, char('g', ctrl = true)))
    }

    @Test
    fun `popup chords move accept and dismiss`() {
        assertEquals(KeyAction.PopupPrev, CodexKeymap.resolve(KeyContext.Popup, KeyChord(CodexKeys.UP)))
        assertEquals(KeyAction.PopupNext, CodexKeymap.resolve(KeyContext.Popup, KeyChord(CodexKeys.DOWN)))
        assertEquals(KeyAction.PopupAccept, CodexKeymap.resolve(KeyContext.Popup, KeyChord(CodexKeys.ENTER)))
        assertEquals(KeyAction.PopupAccept, CodexKeymap.resolve(KeyContext.Popup, KeyChord(CodexKeys.TAB)))
        assertEquals(KeyAction.PopupDismiss, CodexKeymap.resolve(KeyContext.Popup, KeyChord(CodexKeys.ESCAPE)))
    }

    @Test
    fun `popup shadows escape but still reaches global chords`() {
        assertEquals(KeyAction.PopupDismiss, CodexKeymap.resolve(KeyContext.Popup, KeyChord(CodexKeys.ESCAPE)))
        assertEquals(KeyAction.OpenTranscript, CodexKeymap.resolve(KeyContext.Popup, char('t', ctrl = true)))
        assertEquals(KeyAction.ShowShortcuts, CodexKeymap.resolve(KeyContext.Popup, KeyChord(CodexKeys.F1)))
    }

    @Test
    fun `altgr with an ordinary character is not a shortcut`() {
        assertNull(CodexKeymap.resolve(KeyContext.Global, char('q', ctrl = true, alt = true)))
        assertNull(CodexKeymap.resolve(KeyContext.Composer, char('c', ctrl = true, alt = true)))
        assertNull(CodexKeymap.resolve(KeyContext.Composer, char('/', ctrl = true, alt = true)))
    }

    @Test
    fun `uppercase folds to shift plus lowercase`() {
        assertEquals(KeyChord(CodexKeys.char('a'), shift = true), CodexKeymap.normalize(KeyChord(CodexKeys.char('A'))))
        assertEquals(
            KeyChord(CodexKeys.char('a'), shift = true),
            CodexKeymap.normalize(KeyChord(CodexKeys.char('A'), shift = true)),
        )
        assertEquals(
            KeyChord(CodexKeys.char('a'), ctrl = true, shift = true),
            CodexKeymap.normalize(KeyChord(CodexKeys.char('A'), ctrl = true)),
        )
        assertEquals(KeyChord(CodexKeys.ENTER), CodexKeymap.normalize(KeyChord(CodexKeys.ENTER)))
    }

    @Test
    fun `an uppercase letter never matches its lowercase shortcut`() {
        assertNull(CodexKeymap.resolve(KeyContext.Global, KeyChord(CodexKeys.char('T'), ctrl = true)))
        assertNull(CodexKeymap.resolve(KeyContext.Global, char('t', ctrl = true, shift = true)))
        assertNull(CodexKeymap.resolve(KeyContext.Composer, KeyChord(CodexKeys.char('J'), ctrl = true)))
    }

    @Test
    fun `unhandled keys return null`() {
        assertNull(CodexKeymap.resolve(KeyContext.Global, char('x')))
        assertNull(CodexKeymap.resolve(KeyContext.Chat, char('x')))
        assertNull(CodexKeymap.resolve(KeyContext.Composer, char('x')))
        assertNull(CodexKeymap.resolve(KeyContext.Popup, char('x')))
        assertNull(CodexKeymap.resolve(KeyContext.Chat, KeyChord(CodexKeys.TAB)))
        assertNull(CodexKeymap.resolve(KeyContext.Composer, KeyChord(CodexKeys.ENTER, ctrl = true)))
        // Up and Down belong to the caret while no popup is open: draft history is not wired.
        assertNull(CodexKeymap.resolve(KeyContext.Composer, KeyChord(CodexKeys.UP)))
        assertNull(CodexKeymap.resolve(KeyContext.Composer, KeyChord(CodexKeys.DOWN)))
        assertNull(CodexKeymap.resolve(KeyContext.Global, KeyChord(CodexKeys.UNKNOWN)))
    }
}
