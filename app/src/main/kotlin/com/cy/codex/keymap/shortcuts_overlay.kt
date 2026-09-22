package com.cy.codex.keymap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.SquircleShape
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.floatingSurface
import com.cy.codex.glassTint
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The `?` / Ctrl+/ help overlay: the bindings this client actually implements.
 *
 * Mirrors `composer.toggle_shortcuts` in keymap.rs — the same two chords — but renders a page
 * instead of a terminal footer, because a phone cannot show a hint line under a software keyboard.
 */

/**
 * Visibility of the help overlay.
 *
 * A tiny holder rather than state inside `CodexScreen`: the composer toggles the same overlay with
 * `?`, and the composer is mounted by the read-only chat rendering, so the two ends have to share
 * one instance. [LocalShortcutsHelp] is how it reaches the composer.
 */
class ShortcutsHelpState {
    var visible by mutableStateOf(false)
        private set

    fun toggle() {
        visible = !visible
    }

    fun dismiss() {
        visible = false
    }
}

val LocalShortcutsHelp = staticCompositionLocalOf<ShortcutsHelpState?> { null }

/** Width the key column reserves, so every key hint lines up with the one above it. */
private val KeyColumnWidth = 116.dp

private val CardMaxWidth = 440.dp
private val CardMaxHeight = 560.dp

@Composable
fun ShortcutsOverlay(state: ShortcutsHelpState, modifier: Modifier = Modifier) {
    if (!state.visible) return
    val shape = remember { SquircleShape(UiConsts.DrawerCorner) }
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = UiConsts.ScrimAlpha))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = state::dismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = CardMaxWidth)
                .padding(horizontal = UiConsts.ScreenMargin)
                .floatingSurface(
                    shape = shape,
                    tint = glassTint(alpha = 0.96f),
                    elevation = 18.dp,
                )
                .clip(shape)
                // Swallows the tap that would otherwise reach the scrim behind the card.
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null,
                    onClick = {},
                )
                .heightIn(max = CardMaxHeight)
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.shortcuts_overlay_title),
                fontSize = UiType.SheetTitle,
                lineHeight = UiType.SheetTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            ShortcutGroup(stringResource(R.string.shortcuts_overlay_group_global)) {
                ShortcutRow("Esc", stringResource(R.string.shortcuts_overlay_global_escape))
                ShortcutRow("Ctrl+C", stringResource(R.string.shortcuts_overlay_global_interrupt))
                ShortcutRow("Ctrl+T", stringResource(R.string.shortcuts_overlay_global_transcript))
                ShortcutRow("Ctrl+/  F1", stringResource(R.string.shortcuts_overlay_global_help))
            }
            ShortcutGroup(stringResource(R.string.shortcuts_overlay_group_composer)) {
                ShortcutRow("Enter", stringResource(R.string.shortcuts_overlay_composer_submit))
                ShortcutRow("Shift+Enter", stringResource(R.string.shortcuts_overlay_composer_newline))
                ShortcutRow("Ctrl+J  Alt+Enter", stringResource(R.string.shortcuts_overlay_composer_newline))
                ShortcutRow("Ctrl+R  Ctrl+S", stringResource(R.string.shortcuts_overlay_composer_history))
                ShortcutRow("Ctrl+O", stringResource(R.string.shortcuts_overlay_composer_copy))
                ShortcutRow("Ctrl+G", stringResource(R.string.shortcuts_overlay_composer_editor))
                ShortcutRow("?", stringResource(R.string.shortcuts_overlay_composer_help))
            }
            ShortcutGroup(stringResource(R.string.shortcuts_overlay_group_suggestions)) {
                ShortcutRow("↑  ↓", stringResource(R.string.shortcuts_overlay_popup_move))
                ShortcutRow("Enter  Tab", stringResource(R.string.shortcuts_overlay_popup_accept))
                ShortcutRow("Esc", stringResource(R.string.shortcuts_overlay_popup_dismiss))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.shortcuts_overlay_close_hint),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** One titled section of the overlay; the rows come in as content so the group owns only the title. */
@Composable
private fun ShortcutGroup(title: String, rows: @Composable () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Text(
        text = title,
        fontSize = UiType.Subtitle,
        lineHeight = UiType.SubtitleLine,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    rows()
}

/** One binding: the keys in a fixed column, the meaning beside them. */
@Composable
private fun ShortcutRow(keys: String, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keys,
            modifier = Modifier.width(KeyColumnWidth),
            fontSize = UiType.Meta,
            lineHeight = UiType.MetaLine,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = UiType.Body,
            lineHeight = UiType.BodyLine,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}
