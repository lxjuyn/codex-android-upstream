package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.protocol.v2.ToolRequestUserInputQuestion
import com.cy.codex.protocol.protocol.v2.UserInputAnswer
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.pressableRow
import com.cy.codex.raisedSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `request_user_input`: the agent blocks on a question it cannot answer itself.
 *
 * Mirrors `codex-rs/tui/src/bottom_pane/request_user_input/`: one block per question, a tappable
 * option list, and an extra free-text row when `isOther` is set. Selection is single-choice; a
 * selected option and the free-text row are both submitted, the note as a second `user_note:`
 * entry, matching `submit_answers` in the upstream bottom pane.
 *
 * The numbered badge is the only decoration: it is what makes "2 of 3 answered" countable at a
 * glance without reading the footer.
 */

/** Sentinel selection for the free-text ("其他") row of a question. */
private const val OtherChoice = -1

/** Prefix marking notes that ride along with a selected option. */
private const val UserNotePrefix = "user_note: "

/** Tappable option row of a question. */
private val OptionShape = RoundedCornerShape(UiConsts.CornerControl)

/** The numbered badge in front of a question header. */
private val BadgeShape = RoundedCornerShape(UiConsts.CornerChip)
private val BadgeSize = 22.dp
private val BadgeTopPadding = 1.dp

@Composable
internal fun RequestUserInputForm(
    request: ApprovalRequest.UserInput,
    onSubmit: (List<UserInputAnswer>) -> Unit,
    onCancel: () -> Unit,
    busy: Boolean = false,
) {
    val questions = request.params.questions
    // questionId -> selected option index, or [OtherChoice] for the free-text row.
    val selections = remember(questions) { mutableStateMapOf<String, Int>() }
    // questionId -> free-text content; appended as a `user_note:` answer when non-blank.
    val notes = remember(questions) { mutableStateMapOf<String, String>() }
    var submitted by remember(questions) { mutableStateOf(false) }

    val answered = questions.count { answersFor(it, selections, notes) != null }
    val complete = questions.isNotEmpty() && answered == questions.size

    fun submit() {
        if (!complete || submitted) return
        submitted = true
        onSubmit(questions.mapNotNull { question ->
            answersFor(question, selections, notes)?.let { UserInputAnswer(question.id, it) }
        })
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ApprovalScrollBody {
            questions.forEachIndexed { index, question ->
                if (index > 0) Spacer(Modifier.height(UiConsts.DialogFieldGap))
                QuestionBlock(
                    index = index,
                    question = question,
                    selected = selections[question.id],
                    note = notes[question.id].orEmpty(),
                    onSelect = { selections[question.id] = it },
                    onNoteChange = { notes[question.id] = it },
                )
            }
        }
        // The scrolling body stops here and the footer starts: without the gap the last question
        // reads as if it ran into the buttons, which are a different thing entirely.
        Spacer(Modifier.height(UiConsts.DialogFooterGap))
        FormButtons(
            confirmLabel = stringResource(R.string.request_user_input_view_submit),
            enabled = complete,
            busy = submitted || busy,
            onConfirm = { submit() },
            onCancel = onCancel,
        )
        if (!complete && questions.isNotEmpty()) {
            Spacer(Modifier.height(UiConsts.Space8))
            Text(
                text = stringResource(
                    R.string.request_user_input_view_unanswered,
                    questions.size - answered,
                ),
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
    }
}

/**
 * The answers a question currently holds, or `null` while it is still unanswered.
 *
 * The selected label comes first and a non-blank note follows as `user_note: <text>`, so choosing
 * an option and adding detail preserves both instead of the note replacing the choice.
 */
private fun answersFor(
    question: ToolRequestUserInputQuestion,
    selections: Map<String, Int>,
    notes: Map<String, String>,
): List<String>? {
    val answers = mutableListOf<String>()
    val selected = selections[question.id]
    if (selected != null && selected != OtherChoice) {
        question.options.orEmpty().getOrNull(selected)?.label?.let(answers::add)
    }
    val note = notes[question.id]?.trim().orEmpty()
    if (note.isNotEmpty()) {
        answers += if (answers.isEmpty()) note else "$UserNotePrefix$note"
    }
    return answers.ifEmpty { null }
}

@Composable
private fun QuestionBlock(
    index: Int,
    question: ToolRequestUserInputQuestion,
    selected: Int?,
    note: String,
    onSelect: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}",
                modifier = Modifier
                    .size(BadgeSize)
                    .background(colors.primary.copy(alpha = 0.14f), BadgeShape)
                    .padding(top = BadgeTopPadding),
                fontSize = UiType.Chip,
                lineHeight = UiType.ChipLine,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
            Spacer(Modifier.width(UiConsts.Space10))
            Text(
                text = question.header,
                modifier = Modifier.weight(1f),
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
        }
        Spacer(Modifier.height(UiConsts.Space5))
        Text(
            text = question.question,
            modifier = Modifier.fillMaxWidth(),
            fontSize = UiType.Body,
            lineHeight = UiType.BodyLine,
            color = colors.onSurfaceSecondary,
        )

        val options = question.options.orEmpty()
        if (options.isNotEmpty()) {
            Spacer(Modifier.height(UiConsts.Space8))
            options.forEachIndexed { optionIndex, option ->
                OptionRow(
                    label = option.label,
                    description = option.description,
                    selected = selected == optionIndex,
                    onClick = { onSelect(optionIndex) },
                )
                if (optionIndex != options.lastIndex) Spacer(Modifier.height(UiConsts.Space6))
            }
        }
        if (question.isOther) {
            Spacer(Modifier.height(UiConsts.Space8))
            TextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = if (options.isEmpty()) {
                    stringResource(R.string.request_user_input_view_your_answer)
                } else {
                    stringResource(R.string.request_user_input_view_other)
                },
                singleLine = !question.isSecret,
                minLines = 1,
                maxLines = if (question.isSecret) 1 else 4,
                visualTransformation = if (question.isSecret) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                textStyle = if (question.isSecret) {
                    MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace)
                } else {
                    MiuixTheme.textStyles.main
                },
            )
        }
    }
}

/** One tappable option row with a trailing check mark when selected. */
@Composable
private fun OptionRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = OptionShape,
                container = if (selected) colors.primary.copy(alpha = 0.1f) else raisedSurface(),
                onClick = onClick,
            )
            .padding(
                horizontal = UiConsts.Space12,
                vertical = UiConsts.Space10,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) colors.primary else colors.onSurface,
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(UiConsts.Space8))
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = stringResource(R.string.request_user_input_view_selected),
                modifier = Modifier.size(UiConsts.IconInline),
                tint = colors.primary,
            )
        }
    }
}
