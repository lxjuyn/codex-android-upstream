package com.cy.codex.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Ask the window this composable is in to hide its status bar.
 *
 * Dialogs, bottom sheets and popups are *separate windows* on Android, and the system bar
 * visibilities of the topmost window are the ones that count: an app that hides its status bar in
 * the activity still gets it back the moment one of its own dialogs comes up. So the request has to
 * be repeated by every window the app opens, which is what this does.
 *
 * There is no permission involved — hiding a system bar is a window-level request, and the bar comes
 * back on a swipe from the top and leaves again afterwards.
 */
@Composable
fun HideStatusBarInWindow() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, view).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
