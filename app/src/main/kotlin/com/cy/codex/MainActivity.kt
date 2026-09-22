package com.cy.codex

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cy.codex.theme.Appearance
import com.cy.codex.theme.CodexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before setContent: the first frame already needs the chosen theme, not the system's.
        Appearance.load(this)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        window.isNavigationBarContrastEnforced = false

        // The app is its own window chrome: the transcript starts at the top edge and the floating
        // controls sit under the camera cutout, so a system status bar on top of them is just a
        // second, less useful header. There is no permission for this — hiding a system bar is a
        // window-level request (`WindowInsetsController`), granted to any app that asks; the bars
        // come back on a swipe and stay away afterward.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            CodexTheme {
                CodexRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The developer option behind "Remove animations" can change while the app is backgrounded,
        // and the motion layer caches the decision.
        Appearance.syncSystemAnimators()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // A transient swipe or a system dialog can bring the bar back; re-assert the window's own
        // choice every time it regains focus.
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}
