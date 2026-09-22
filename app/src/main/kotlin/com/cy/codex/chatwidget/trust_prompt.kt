package com.cy.codex.chatwidget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

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
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.trust_project_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = UiConsts.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        ) {
            Text(
                text = path,
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = stringResource(R.string.trust_project_body),
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                fontSize = UiType.SheetBody,
                lineHeight = UiType.SheetBodyLine,
                color = colors.onSurface,
            )
            Button(
                onClick = onTrust,
                modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space12),
                colors = ButtonDefaults.buttonColorsPrimary(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.trust_project_confirm),
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.trust_project_cancel),
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
