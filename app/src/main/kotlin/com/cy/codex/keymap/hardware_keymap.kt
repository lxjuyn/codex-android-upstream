package com.cy.codex.keymap

/**
 * The keyboard layer: one place that says what a hardware key means.
 *
 * A hardware keyboard is a first-class input device on Android — USB, Bluetooth, I2C and built-in
 * HID keyboards all arrive as ordinary key events, there is no transport-specific API — and the TUI
 * this app mirrors already drives everything from one. The layer is split in two on purpose:
 *
 * - this file is the pure part, free of Compose and `android.view` types, so every rule (context
 *   precedence, AltGr protection, case normalisation) is testable on a plain JVM;
 * - `key_event_adapter.kt` is the only file that knows how a platform event is shaped.
 *
 * The bindings mirror `codex-rs/tui/src/keymap.rs` (`built_in_defaults`) and the matching rules
 * mirror `codex-rs/tui/src/key_hint.rs` (`normalize_key_parts`), adapted to the features this client
 * has: no pager, no vim editing, no queued-message key, and no process exit on a chord because an
 * Android app has no terminal to quit.
 */

/**
 * One key press, with its modifiers.
 *
 * [key] is a Unicode code point for character keys — so case survives until [CodexKeymap.normalize]
 * folds it — and one of the negative sentinels in [CodexKeys] for keys that produce no character of
 * their own.
 */
data class KeyChord(
    val key: Int,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
)

/**
 * The named keys, plus the factory for character keys.
 *
 * The sentinels are negative so they can never collide with a code point. Android key codes stay in
 * `key_event_adapter.kt`; nothing outside it needs to know a physical key code.
 */
object CodexKeys {
    const val UNKNOWN = 0
    const val ENTER = -1
    const val ESCAPE = -2
    const val TAB = -3
    const val UP = -4
    const val DOWN = -5
    const val LEFT = -6
    const val RIGHT = -7
    const val F1 = -8

    /** A character key: its identity is the code point the layout produced. */
    fun char(c: Char): Int = c.code
}

/**
 * Where a chord is being interpreted.
 *
 * A context's own bindings win over [Global], the way `resolve_with_global` lets a composer action
 * reuse a global binding; a context that binds a chord also shadows the global action for it. The
 * composer consults [Composer] normally and [Popup] while a suggestion list is open, so arrow keys
 * move the popup cursor instead of the text caret.
 */
enum class KeyContext { Global, Chat, Composer, Popup }

/**
 * What a chord means, wire-agnostic: the host decides whether it can act on it.
 *
 * [HistoryOlder] and [HistoryNewer] are the reverse-search pair (`history_search_previous` /
 * `history_search_next` in keymap.rs): Ctrl+R begins or walks toward older drafts, Ctrl+S walks
 * back. Up and Down stay unbound because a focused multi-line field owns them for caret movement.
 * [CopyLastResponse] and [OpenExternalEditor] are host hooks: the composer cannot reach the
 * clipboard or start an activity, so the host passes a callback and the chord is inert without one.
 */
enum class KeyAction {
    Submit,
    InsertNewline,
    InterruptTurn,
    OpenTranscript,
    ShowShortcuts,
    PopupNext,
    PopupPrev,
    PopupAccept,
    PopupDismiss,
    ClearFocus,
    HistoryOlder,
    HistoryNewer,
    CopyLastResponse,
    OpenExternalEditor,
}

object CodexKeymap {

    /**
     * The action a chord means in [context], or null when nothing is bound to it.
     *
     * Lookup order is context first, then [KeyContext.Global]. Returning null is not a failure: it
     * is how a host knows to leave the event alone (text input, caret movement, the clipboard).
     */
    fun resolve(context: KeyContext, chord: KeyChord): KeyAction? {
        val normalized = normalize(chord)
        // AltGr arrives as Ctrl+Alt from a physical keyboard. A layout that needs it uses the pair
        // to type an ordinary character, never to mean a shortcut, so a modified character key is
        // dropped rather than matched. Mirrors `is_altgr`/`has_ctrl_or_alt` in key_hint.rs, which
        // is a no-op on platforms that report AltGr as a real modifier pair.
        if (normalized.ctrl && normalized.alt && normalized.key > 0) return null
        return bindings(context)[normalized] ?: bindings(KeyContext.Global)[normalized]
    }

    /**
     * Fold a chord the way `normalize_key_parts` does.
     *
     * A keyboard may report an uppercase letter without the SHIFT modifier (a terminal has the same
     * inconsistency), so uppercase implies Shift and the key folds to lowercase; a binding written
     * as shift+j then matches either spelling, while ctrl+j deliberately does not match ctrl+J.
     * Non-character keys pass through untouched.
     */
    internal fun normalize(chord: KeyChord): KeyChord {
        if (chord.key <= 0 || chord.key > Char.MAX_VALUE.code) return chord
        val ch = chord.key.toChar()
        if (ch in 'A'..'Z') return chord.copy(key = ch.lowercaseChar().code, shift = true)
        return chord
    }

    private fun bindings(context: KeyContext): Map<KeyChord, KeyAction> = when (context) {
        KeyContext.Global -> global
        KeyContext.Chat -> chat
        KeyContext.Composer -> composer
        KeyContext.Popup -> popup
    }

    /**
     * Application-level chords: available everywhere, and the fallback for every other context.
     */
    private val global: Map<KeyChord, KeyAction> = mapOf(
        // Esc is the TUI's `chat.interrupt_turn`. The host only interrupts while a turn is running;
        // otherwise the chord is left to the composer, where it closes a popup or leaves the field.
        KeyChord(CodexKeys.ESCAPE) to KeyAction.InterruptTurn,
        // Ctrl+C interrupted (and quit) the TUI. Android never quits from a key, and the clipboard
        // owns Ctrl+C while idle, so the host also gates this on a running turn.
        KeyChord(CodexKeys.char('c'), ctrl = true) to KeyAction.InterruptTurn,
        // `app.open_transcript`: the transcript overlay, which this client opens as ThreadHistory.
        KeyChord(CodexKeys.char('t'), ctrl = true) to KeyAction.OpenTranscript,
        KeyChord(CodexKeys.char('/'), ctrl = true) to KeyAction.ShowShortcuts,
        KeyChord(CodexKeys.F1) to KeyAction.ShowShortcuts,
    )

    /** Chords that belong to the chat surface itself, evaluated before global ones. */
    private val chat: Map<KeyChord, KeyAction> = mapOf(
        KeyChord(CodexKeys.ESCAPE) to KeyAction.InterruptTurn,
    )

    /**
     * Chords the composer owns. The host only acts on them while the field is focused, and [Submit]
     * additionally requires an enabled, non-blank field.
     */
    private val composer: Map<KeyChord, KeyAction> = mapOf(
        KeyChord(CodexKeys.ENTER) to KeyAction.Submit,
        // `editor.insert_newline`: shift-enter, alt-enter, ctrl-j and ctrl-m.
        KeyChord(CodexKeys.ENTER, shift = true) to KeyAction.InsertNewline,
        KeyChord(CodexKeys.ENTER, alt = true) to KeyAction.InsertNewline,
        KeyChord(CodexKeys.char('j'), ctrl = true) to KeyAction.InsertNewline,
        KeyChord(CodexKeys.char('m'), ctrl = true) to KeyAction.InsertNewline,
        // `composer.toggle_shortcuts` binds both spellings because a layout may report `?` with or
        // without SHIFT. The host gates it on an empty field so it can never eat typed text.
        KeyChord(CodexKeys.char('?')) to KeyAction.ShowShortcuts,
        KeyChord(CodexKeys.char('?'), shift = true) to KeyAction.ShowShortcuts,
        KeyChord(CodexKeys.ESCAPE) to KeyAction.ClearFocus,
        // `history_search_previous` / `history_search_next`.
        KeyChord(CodexKeys.char('r'), ctrl = true) to KeyAction.HistoryOlder,
        KeyChord(CodexKeys.char('s'), ctrl = true) to KeyAction.HistoryNewer,
        // `composer.copy_last_response` and `composer.open_external_editor`; the clipboard and the
        // editor activity belong to the host, so these actions are inert without a callback.
        KeyChord(CodexKeys.char('o'), ctrl = true) to KeyAction.CopyLastResponse,
        KeyChord(CodexKeys.char('g'), ctrl = true) to KeyAction.OpenExternalEditor,
    )

    /**
     * Chords the open suggestion list owns. Enter and Tab accept, as the list's own bindings do in
     * keymap.rs (`list.accept` plus the composer's Tab), and Esc dismisses.
     */
    private val popup: Map<KeyChord, KeyAction> = mapOf(
        KeyChord(CodexKeys.UP) to KeyAction.PopupPrev,
        KeyChord(CodexKeys.DOWN) to KeyAction.PopupNext,
        KeyChord(CodexKeys.ENTER) to KeyAction.PopupAccept,
        KeyChord(CodexKeys.TAB) to KeyAction.PopupAccept,
        KeyChord(CodexKeys.ESCAPE) to KeyAction.PopupDismiss,
    )
}
