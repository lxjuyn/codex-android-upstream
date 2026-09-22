package com.cy.codex.keymap

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * The only file that knows how a platform key event is shaped.
 *
 * Android delivers every hardware keyboard — USB, Bluetooth, I2C, built-in HID — as a normal
 * [KeyEvent] (a wrapper over `android.view.KeyEvent`), so all of them go through the same conversion
 * and are then matched by the pure [CodexKeymap].
 */

/**
 * The chat root's focus target.
 *
 * The composer hands focus back here when it gives it up (Esc clears the field), because a Compose
 * hierarchy with nothing focused receives no key events at all: without one persistent target the
 * global chords would stop arriving the moment the user left the prompt.
 */
val LocalChatKeyFocus = staticCompositionLocalOf<FocusRequester?> { null }

/**
 * Convert one Compose key event to a chord, or null when the event must be ignored.
 *
 * KeyUp is ignored and KeyDown/KeyRepeat are not, mirroring `KeyBinding::is_press` in key_hint.rs: a
 * shortcut fires once per physical press, and a held key repeats it the way a terminal would.
 */
fun KeyEvent.toKeyChord(): KeyChord? {
    if (type == KeyEventType.KeyUp) return null
    val native = nativeKeyEvent
    val ctrl = native.isCtrlPressed
    val alt = native.isAltPressed
    val shift = native.isShiftPressed
    return KeyChord(
        key = codexKey(native.keyCode, native.unicodeChar, modified = ctrl || alt),
        ctrl = ctrl,
        alt = alt,
        shift = shift,
    )
}

/**
 * Route hardware key presses to [onAction] while [context] applies.
 *
 * `onPreviewKeyEvent` rather than `onKeyEvent` on purpose: the preview pass reaches this node before
 * the `BasicTextField` inside the composer does, so a bound chord (Enter, arrows) cannot be consumed
 * as text editing first.
 */
fun Modifier.codexHardwareKeys(
    context: KeyContext,
    onAction: (KeyAction) -> Boolean,
): Modifier = onPreviewKeyEvent { event ->
    val chord = event.toKeyChord() ?: return@onPreviewKeyEvent false
    val action = CodexKeymap.resolve(context, chord) ?: return@onPreviewKeyEvent false
    onAction(action)
}

/**
 * Map an Android key code and the character the layout produced onto [CodexKeys] or a code point.
 *
 * Named keys are matched by key code because they have no character of their own. Everything else is
 * a character, and which one depends on the modifiers: without Ctrl/Alt the layout decides (so
 * Shift+/ becomes `?`), while a modified chord takes the base character of the key so Ctrl+J stays
 * `j` even when the IME would report the control character the chord produces.
 */
internal fun codexKey(keyCode: Int, unicodeChar: Int, modified: Boolean): Int {
    namedKey(keyCode)?.let { return it }
    val typed = unicodeChar.takeIf { it != 0 && !Character.isISOControl(it) }?.toChar()
    val character = if (modified) baseCharacter(keyCode) else typed ?: baseCharacter(keyCode)
    return character?.code ?: CodexKeys.UNKNOWN
}

private fun namedKey(keyCode: Int): Int? = when (keyCode) {
    android.view.KeyEvent.KEYCODE_ENTER,
    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> CodexKeys.ENTER

    android.view.KeyEvent.KEYCODE_ESCAPE -> CodexKeys.ESCAPE
    android.view.KeyEvent.KEYCODE_TAB -> CodexKeys.TAB
    android.view.KeyEvent.KEYCODE_DPAD_UP -> CodexKeys.UP
    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> CodexKeys.DOWN
    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> CodexKeys.LEFT
    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> CodexKeys.RIGHT
    android.view.KeyEvent.KEYCODE_F1 -> CodexKeys.F1
    else -> null
}

private fun baseCharacter(keyCode: Int): Char? = when (keyCode) {
    in android.view.KeyEvent.KEYCODE_A..android.view.KeyEvent.KEYCODE_Z ->
        'a' + (keyCode - android.view.KeyEvent.KEYCODE_A)

    in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 ->
        '0' + (keyCode - android.view.KeyEvent.KEYCODE_0)

    android.view.KeyEvent.KEYCODE_SPACE -> ' '
    android.view.KeyEvent.KEYCODE_SLASH -> '/'
    else -> null
}
