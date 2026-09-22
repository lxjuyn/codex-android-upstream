package com.cy.codex

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Colour helpers shared by every surface.
 *
 * Mirrors `codex-rs/tui/src/color.rs` and `style.rs`: one place decides what a status, a diff line
 * or an approval outcome looks like, so the transcript, the status card and the approval cards
 * cannot drift apart. Diff colours themselves live with the diff model in
 * `com.cy.codex.diffPalette`.
 */

/** Dark/light pair resolved at composition, because most of these have no miuix tonal equivalent. */
private class Accent(val dark: Color, val light: Color) {
    @Composable
    operator fun invoke(): Color = if (isSystemInDarkTheme()) dark else light
}

private val Success = Accent(Color(0xFF6EDC8C), Color(0xFF1A9E4B))
private val Warning = Accent(Color(0xFFFFC24B), Color(0xFFE08600))

@Composable
fun successColor(): Color = Success()

@Composable
fun warningColor(): Color = Warning()

/** How a status should read, independent of which domain produced it. */
enum class ThreadStatusTone { Idle, Running, Waiting, Done, Failed }

/** Colour of an agent/environment status dot. */
@Composable
fun statusDotColor(tone: ThreadStatusTone): Color {
    val colors = MiuixTheme.colorScheme
    return when (tone) {
        ThreadStatusTone.Idle -> colors.onSurfaceVariantSummary
        ThreadStatusTone.Running -> colors.primary
        ThreadStatusTone.Waiting -> Warning()
        ThreadStatusTone.Done -> Success()
        ThreadStatusTone.Failed -> colors.error
    }
}

/** A thread is "active" both while working and while blocked on the human; the UI must tell them
 * apart, because only the second one needs an action-required badge. */
val com.cy.codex.protocol.protocol.v2.ThreadStatus.isWaitingOnUser: Boolean
    get() = this is com.cy.codex.protocol.protocol.v2.ThreadStatus.Active && activeFlags.any {
        it == com.cy.codex.protocol.protocol.v2.ThreadActiveFlag.WaitingOnApproval ||
            it == com.cy.codex.protocol.protocol.v2.ThreadActiveFlag.WaitingOnUserInput
    }

/** Fold the protocol status into the tone the theme knows how to colour. */
fun com.cy.codex.protocol.protocol.v2.ThreadStatus.tone(): ThreadStatusTone = when (this) {
    is com.cy.codex.protocol.protocol.v2.ThreadStatus.Idle -> ThreadStatusTone.Idle
    is com.cy.codex.protocol.protocol.v2.ThreadStatus.NotLoaded -> ThreadStatusTone.Idle
    is com.cy.codex.protocol.protocol.v2.ThreadStatus.Active ->
        if (isWaitingOnUser) ThreadStatusTone.Waiting else ThreadStatusTone.Running

    is com.cy.codex.protocol.protocol.v2.ThreadStatus.SystemError -> ThreadStatusTone.Failed
}

/** Pill fill behind a status label. */
@Composable
fun statusPillSurface(tone: ThreadStatusTone): Color = statusDotColor(tone).copy(alpha = 0.14f)

/** Highlighter tint behind inline code and tool output. */
@Composable
fun codeSurface(): Color {
    val colors = MiuixTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        colors.onSurface.copy(alpha = 0.06f)
    } else {
        colors.surfaceContainerHighest.copy(alpha = 0.72f)
    }
}

/**
 * Fill of a surface sitting on top of a floating panel: a section card, or the selected row of the
 * navigation panel.
 *
 * The dark palette has no container lighter than the panel's own surfaceContainerHighest, so a
 * translucent coat of that colour over the panel resolves to the panel itself and the surface
 * silently disappears — cards lose their edges. Dark mode therefore lifts the surface with a wash
 * of the content colour; the light palette keeps its tonal step.
 */
@Composable
fun raisedSurface(): Color {
    val colors = MiuixTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        colors.onSurface.copy(alpha = 0.055f)
    } else {
        colors.surfaceContainerHighest.copy(alpha = 0.7f)
    }
}

/**
 * Fill of a bottom sheet.
 *
 * A sheet is a window-level panel, so it cannot sample what is behind it the way the in-window glass
 * surfaces do; it takes the same step the drawers use instead of a translucent tint that would only
 * ever resolve against the window background.
 */
@Composable
fun sheetColor(): Color {
    val colors = MiuixTheme.colorScheme
    return if (isSystemInDarkTheme()) colors.surfaceContainerHighest else colors.surfaceContainer
}

/** Panel fill used by the floating drawers when no blur backdrop is attached. */
@Composable
fun panelColor(): Color {
    val colors = MiuixTheme.colorScheme
    return if (isSystemInDarkTheme()) colors.surfaceContainerHighest else colors.surfaceContainer
}

/** Translucent fill for the floating glass surfaces. */
@Composable
fun glassTint(alpha: Float = 0.88f): Color {
    val colors = MiuixTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        colors.surfaceContainerHigh.copy(alpha = alpha)
    } else {
        colors.surface.copy(alpha = if (alpha > 0.8f) 0.94f else alpha)
    }
}

/** Context-window meter colour: primary until the window gets crowded, then amber, then error. */
@Composable
fun usageColor(fraction: Float): Color = when {
    fraction >= 0.9f -> MiuixTheme.colorScheme.error
    fraction >= 0.7f -> Warning()
    else -> MiuixTheme.colorScheme.primary
}

/** Last path segment, used as the headline of a file row. */
fun fileName(path: String): String = path.substringAfterLast('/')

/** Directory of a file, shortened from the left so the last folders stay readable. */
fun parentPath(path: String): String {
    val dir = path.substringBeforeLast('/', "")
    if (dir.isEmpty()) return ""
    val parts = dir.split('/')
    return if (parts.size <= 3) "$dir/" else "…/" + parts.takeLast(3).joinToString("/") + "/"
}
