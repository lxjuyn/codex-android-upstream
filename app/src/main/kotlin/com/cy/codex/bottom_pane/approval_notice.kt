package com.cy.codex.bottom_pane

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.AutoReviewDenial
import com.cy.codex.CodexButton
import com.cy.codex.CodexButtonSize
import com.cy.codex.ButtonRole
import com.cy.codex.ForeignApproval
import com.cy.codex.PendingReview
import com.cy.codex.R
import com.cy.codex.SquircleShape
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.floatingSurface
import com.cy.codex.glassTint
import com.cy.codex.pressableRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The composer's approval notice bar.
 *
 * Three things the modal cannot say, because the modal is busy with the request in front of it:
 *
 *  - a decision is waiting in another thread ([foreign]), which needs a thread switch rather than
 *    an answer here;
 *  - auto review is still deciding one or more requests ([reviews]), aggregated with the same
 *    `+N more` rule upstream uses for parallel reviews;
 *  - auto review denied something ([denials]), which the user can override once.
 *
 * Nothing renders while all three are empty. The bar sits above the queued-message tray, which is
 * where every "something happened outside the transcript" notice lives.
 */
@Composable
fun ApprovalNoticeBar(
    foreign: List<ForeignApproval>,
    threadName: (String) -> String,
    reviews: List<PendingReview>,
    denials: List<AutoReviewDenial>,
    onOpenThread: (String) -> Unit,
    onApproveDenial: (AutoReviewDenial) -> Unit,
    onDismissDenial: (AutoReviewDenial) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (foreign.isEmpty() && reviews.isEmpty() && denials.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val shape = remember { SquircleShape(UiConsts.PanelCorner) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .floatingSurface(shape = shape, tint = glassTint(0.94f), elevation = UiConsts.PanelElevation)
            .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space10),
        verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
    ) {
        val shapeRow = remember { RoundedCornerShape(UiConsts.CornerControl) }
        foreign.take(UiConsts.ApprovalNoticeThreads).forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableRow(
                        shape = shapeRow,
                        container = Color.Transparent,
                        onClick = { onOpenThread(entry.threadId) },
                        onClickLabel = threadName(entry.threadId),
                    )
                    .padding(vertical = UiConsts.Space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.approval_notice_foreign, threadName(entry.threadId)),
                    modifier = Modifier.weight(1f),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(UiConsts.Space8))
                Text(
                    text = stringResource(R.string.approval_notice_switch),
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary,
                    maxLines = 1,
                )
            }
        }
        if (reviews.isNotEmpty()) {
            Text(
                text = stringResource(R.string.approval_notice_reviewing, reviews.size),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            reviews.take(UiConsts.ApprovalNoticeReviews).forEach { review ->
                Text(
                    text = stringResource(R.string.approval_notice_review_detail, review.detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (reviews.size > UiConsts.ApprovalNoticeReviews) {
                Text(
                    text = stringResource(
                        R.string.approval_notice_review_more,
                        reviews.size - UiConsts.ApprovalNoticeReviews,
                    ),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
        denials.firstOrNull()?.let { denial ->
            Text(
                text = stringResource(R.string.approval_notice_denied_title),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                fontWeight = FontWeight.Medium,
                color = colors.error,
            )
            if (denial.summary.isNotBlank()) {
                Text(
                    text = denial.summary,
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            denial.rationale?.takeIf { it.isNotBlank() }?.let { rationale ->
                Text(
                    text = rationale,
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8)) {
                CodexButton(
                    text = stringResource(R.string.approval_notice_denied_approve),
                    onClick = { onApproveDenial(denial) },
                    size = CodexButtonSize.Compact,
                )
                CodexButton(
                    text = stringResource(R.string.approval_notice_denied_dismiss),
                    onClick = { onDismissDenial(denial) },
                    role = ButtonRole.Secondary,
                    size = CodexButtonSize.Compact,
                )
            }
        }
    }
}
