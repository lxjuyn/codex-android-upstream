package com.cy.codex.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.cy.codex.ModalSheet
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.v2.MisalignmentErrorDetails
import com.cy.codex.raisedSurface
import com.cy.codex.warningColor
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The longest steer `continuation_message` accepts upstream. */
internal const val MaxMisalignmentSteerChars = 1024

/**
 * The safety-stop bar above the composer.
 *
 * Mirrors `chatwidget/misalignment_policy.rs`: the turn stopped as a precaution, the composer is
 * blocked, and the only ways forward are reviewing the findings or continuing with the steer.
 * Continuing is offered only when the server supplied a usable steer message.
 */
@Composable
internal fun MisalignmentBar(
    details: MisalignmentErrorDetails,
    onReview: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val accent = warningColor()
    val shape = RoundedCornerShape(UiConsts.CornerControl)
    val steer = details.steer?.message?.takeIf { it.isNotBlank() && it.length <= MaxMisalignmentSteerChars }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.12f), shape)
            .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space10),
    ) {
        Text(
            text = stringResource(R.string.misalignment_title),
            fontSize = UiType.RowTitle,
            lineHeight = UiType.RowTitleLine,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(UiConsts.Space4))
        Text(
            text = stringResource(R.string.misalignment_description),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.onSurfaceSecondary,
        )
        Spacer(Modifier.height(UiConsts.Space8))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(UiConsts.Space8)) {
            Text(
                text = stringResource(R.string.misalignment_review),
                modifier = Modifier
                    .clip(RoundedCornerShape(UiConsts.CornerChip))
                    .clickable(onClick = onReview)
                    .padding(horizontal = UiConsts.Space10, vertical = UiConsts.Space5),
                fontSize = UiType.Action,
                lineHeight = UiType.ActionLine,
                color = colors.primary,
            )
            if (steer != null) {
                Text(
                    text = stringResource(R.string.misalignment_continue),
                    modifier = Modifier
                        .clip(RoundedCornerShape(UiConsts.CornerChip))
                        .clickable(onClick = onContinue)
                        .padding(horizontal = UiConsts.Space10, vertical = UiConsts.Space5),
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
            }
        }
    }
}

/** "What we detected": the findings the safety system returned, plus the quoted continuation. */
@Composable
internal fun MisalignmentReviewSheet(
    details: MisalignmentErrorDetails,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val steer = details.steer?.message?.takeIf { it.isNotBlank() && it.length <= MaxMisalignmentSteerChars }
    ModalSheet(
        show = true,
        onDismiss = onDismiss,
        onDismissFinished = onDismiss,
        title = stringResource(R.string.misalignment_review_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (steer != null) {
                Text(
                    text = stringResource(R.string.misalignment_continuation_label),
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = steer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(raisedSurface(), RoundedCornerShape(UiConsts.CornerControl))
                        .padding(UiConsts.Space10),
                    fontSize = UiType.Code,
                    lineHeight = UiType.CodeLine,
                    fontFamily = FontFamily.Monospace,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(UiConsts.Space12))
            }
            Text(
                text = details.detailedExplanation?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.misalignment_description),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = colors.onSurfaceSecondary,
            )
        }
    }
}
