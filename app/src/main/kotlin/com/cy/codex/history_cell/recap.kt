package com.cy.codex.history_cell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.item.RecapItem
import com.cy.codex.raisedSurface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The conversation recap cell, mirroring `chatwidget/recap.rs`:
 * "Generating conversation recap…" while the hidden turn runs, "Conversation recap" with the
 * summary and an optional `↳ Recap:` next action after it, and a failure line when it could not be
 * generated.
 */
@Composable
internal fun RecapCell(item: RecapItem, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(UiConsts.CornerControl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(raisedSurface(), shape)
            .padding(horizontal = UiConsts.Space16, vertical = UiConsts.Space12),
    ) {
        Text(
            text = stringResource(R.string.recap_cell_title),
            fontSize = UiType.RowTitle,
            lineHeight = UiType.RowTitleLine,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(UiConsts.Space4))
        when {
            item.failed -> Text(
                text = stringResource(R.string.recap_cell_failed),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = colors.error,
            )

            item.text == null -> Text(
                text = stringResource(R.string.recap_cell_loading),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = colors.onSurfaceVariantSummary,
            )

            else -> {
                Text(
                    text = item.text,
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurface,
                )
                if (item.nextAction != null) {
                    Spacer(Modifier.height(UiConsts.Space6))
                    Text(
                        text = stringResource(R.string.recap_cell_next_action, item.nextAction),
                        fontSize = UiType.Footnote,
                        lineHeight = UiType.FootnoteLine,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}
