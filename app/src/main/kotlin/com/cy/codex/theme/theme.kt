package com.cy.codex.theme

import android.content.Context
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cy.codex.Motion
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import androidx.core.content.edit

/**
 * Client-side appearance preferences, independent of the app-server.
 *
 * The TUI reads its theme from `[tui] theme` and its motion from the terminal's own settings;
 * Android's equivalent is a SharedPreferences file the activity and the settings page both watch,
 * so a change applies without recreating the app. The values are global mutable state because the
 * theme wraps the whole composition, above the `CodexApp` that owns every other client setting.
 */
object Appearance {
    private const val FileName = "codex_ui"
    private const val KeyThemeMode = "theme_mode"
    private const val KeyReduceMotion = "reduce_motion"
    private const val KeyShowTooltips = "show_tooltips"

    var themeMode by mutableStateOf(ColorSchemeMode.System)
        private set
    var reduceMotion by mutableStateOf(false)
        private set

    /** `tui.show_tooltips` upstream: whether a startup tip is shown on a fresh conversation. */
    var showTooltips by mutableStateOf(true)
        private set

    /** Read once, before the first composition; a missing key keeps the system default. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(FileName, Context.MODE_PRIVATE)
        themeMode = when (prefs.getString(KeyThemeMode, null)) {
            "light" -> ColorSchemeMode.Light
            "dark" -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
        reduceMotion = prefs.getBoolean(KeyReduceMotion, false)
        showTooltips = prefs.getBoolean(KeyShowTooltips, true)
        syncSystemAnimators()
    }

    /**
     * Follow the system animator scale alongside the in-app switch.
     *
     * Android's "Remove animations" accessibility setting zeroes `ANIMATOR_DURATION_SCALE`, which
     * `ValueAnimator.areAnimatorsEnabled` reports; the TUI gets the same behavior from the terminal
     * (or not at all), and the settings summary already promises it. Called on load and on every
     * activity resume, because the developer option can flip while the app is alive.
     */
    fun syncSystemAnimators() {
        Motion.reduced = reduceMotion || !android.animation.ValueAnimator.areAnimatorsEnabled()
    }

    fun setThemeMode(context: Context, mode: ColorSchemeMode) {
        themeMode = mode
        preferences(context).edit { putString(KeyThemeMode, mode.wire) }
    }

    fun setReduceMotion(context: Context, enabled: Boolean) {
        reduceMotion = enabled
        syncSystemAnimators()
        preferences(context).edit { putBoolean(KeyReduceMotion, enabled) }
    }

    fun setShowTooltips(context: Context, enabled: Boolean) {
        showTooltips = enabled
        preferences(context).edit { putBoolean(KeyShowTooltips, enabled) }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(FileName, Context.MODE_PRIVATE)

    private val ColorSchemeMode.wire: String
        get() = when (this) {
            ColorSchemeMode.Light -> "light"
            ColorSchemeMode.Dark -> "dark"
            else -> "system"
        }
}

@Composable
fun CodexTheme(
    colorMode: ColorSchemeMode = Appearance.themeMode,
    content: @Composable () -> Unit,
) {
    val controller = remember(colorMode) { ThemeController(colorMode) }
    // Reading the reduce-motion flag here makes the theme the one subscriber: a flip recomposes the
    // tree and every `Motion` spec built in that pass is a snap. The flag itself is pushed into
    // [Motion] by [Appearance]; this read is what schedules the pass.
    Appearance.reduceMotion
    MiuixTheme(controller = controller) {
        val colors = MiuixTheme.colorScheme
        val indication = remember(colors.onBackground) {
            RoundedIndication(color = colors.onBackground)
        }
        CompositionLocalProvider(
            LocalIndication provides indication,
            content = content,
        )
    }
}
