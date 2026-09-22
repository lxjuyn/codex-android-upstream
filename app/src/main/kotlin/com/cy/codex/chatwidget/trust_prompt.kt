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
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The folder-trust prompt.
 *
 * Mirrors `codex-rs/tui/src/onboarding/trust_directory.rs`: starting or resuming a thread in a
 * folder that is not under a trusted project asks first, and trusting writes
 * `projects."<path>".trust_level`. The TUI can quit as the alternative; on Android the honest
 * alternative is to cancel, which abandons the action and leaves the config untouched.
 */
@Composable
fun TrustProjectSheet(
    path: String,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    ModalSheet(
        show = true,
        onDismiss = onDismiss,
        onDismissFinished = onDismiss,
        title = stringResource(R.string.trust_project_title),
        subtitle = path,
    ) {
        Text(
            text = stringResource(R.string.trust_project_body),
            modifier = Modifier.padding(horizontal = UiConsts.Space4),
            fontSize = UiType.SheetBody,
            lineHeight = UiType.SheetBodyLine,
            color = colors.onSurface,
        )
        CodexButton(
            text = stringResource(R.string.trust_project_confirm),
            onClick = onTrust,
            modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space12),
        )
        CodexButton(
            text = stringResource(R.string.trust_project_cancel),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            role = ButtonRole.Secondary,
        )
    }
}
