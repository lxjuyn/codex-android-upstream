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
import com.cy.codex.RateLimitNudge
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
    WindowBottomSheet(
        show = true,
        onDismissRequest = onKeep,
        title = stringResource(R.string.rate_limit_nudge_title),
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
                text = stringResource(R.string.rate_limit_nudge_subtitle, nudge.displayName),
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = stringResource(R.string.rate_limit_nudge_body),
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                fontSize = UiType.SheetBody,
                lineHeight = UiType.SheetBodyLine,
                color = colors.onSurface,
            )
            Button(
                onClick = onSwitch,
                modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space12),
                colors = ButtonDefaults.buttonColorsPrimary(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.rate_limit_nudge_switch, nudge.displayName),
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onKeep,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.rate_limit_nudge_keep),
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onNever,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.rate_limit_nudge_never),
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
