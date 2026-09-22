package com.cy.codex.chatwidget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.ButtonRole
import com.cy.codex.CodexButton
import com.cy.codex.ModalSheet
import com.cy.codex.R
import com.cy.codex.RateLimitNudge
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The "approaching rate limits" model-switch prompt.
 *
 * Mirrors `chatwidget/rate_limits.rs::open_rate_limit_switch_prompt`: switch, keep, or keep and
 * never be asked again (which writes `notices.hide_rate_limit_model_nudge`). The read happens once
 * per process; the persistent opt-out is what survives.
 */
@Composable
fun RateLimitNudgeSheet(
    nudge: RateLimitNudge,
    onSwitch: () -> Unit,
    onKeep: () -> Unit,
    onNever: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    ModalSheet(
        show = true,
        onDismiss = onKeep,
        onDismissFinished = onKeep,
        title = stringResource(R.string.rate_limit_nudge_title),
        subtitle = stringResource(R.string.rate_limit_nudge_subtitle, nudge.displayName),
    ) {
        Text(
            text = stringResource(R.string.rate_limit_nudge_body),
            modifier = Modifier.padding(horizontal = UiConsts.Space4),
            fontSize = UiType.SheetBody,
            lineHeight = UiType.SheetBodyLine,
            color = colors.onSurface,
        )
        CodexButton(
            text = stringResource(R.string.rate_limit_nudge_switch, nudge.displayName),
            onClick = onSwitch,
            modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space12),
        )
        CodexButton(
            text = stringResource(R.string.rate_limit_nudge_keep),
            onClick = onKeep,
            modifier = Modifier.fillMaxWidth(),
            role = ButtonRole.Secondary,
        )
        CodexButton(
            text = stringResource(R.string.rate_limit_nudge_never),
            onClick = onNever,
            modifier = Modifier.fillMaxWidth(),
            role = ButtonRole.Secondary,
        )
    }
}
