package com.cy.codex.history_cell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.AsyncUserInputQuestion
import com.cy.codex.protocol.protocol.v2.UserInput
import com.cy.codex.MarkdownStream
import com.cy.codex.MarkdownStreamText
import com.cy.codex.MarkdownText
import com.cy.codex.SquircleShape
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.copyToClipboard
import com.cy.codex.pressableRow
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * User turns and agent answers.
 *
 * Mirrors `codex-rs/tui/src/history_cell/messages.rs`: the user's prompt is a right-aligned block
 * that carries every non-text attachment as a labelled chip, and the agent's answer is a labelled
 * markdown body. The streaming variant appends the TUI's block caret while deltas are still
 * arriving.
 */

@Composable
fun UserMessageCell(
    item: UserMessageItem,
    modifier: Modifier = Modifier,
    corner: Dp = UiConsts.CornerCard,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 10.dp,
    attachmentSpacing: Dp = 4.dp,
    textSpacing: Dp = 7.dp,
    fontSize: TextUnit = UiType.Message,
    lineHeight: TextUnit = UiType.MessageLine,
) {
    val colors = MiuixTheme.colorScheme
    val text = item.content.filterIsInstance<UserInput.Text>()
        .joinToString("\n\n") { it.text }
        .trim()
    val attachments = item.content.filterNot { it is UserInput.Text }
    if (text.isEmpty() && attachments.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = UiConsts.UserBubbleMaxWidth)
                .clip(SquircleShape(corner))
                .background(colors.surfaceContainerHighest)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.End,
        ) {
            if (attachments.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(attachmentSpacing),
                    horizontalAlignment = Alignment.End,
                ) {
                    attachments.forEach { attachment -> AttachmentChip(attachment) }
                }
                if (text.isNotEmpty()) Spacer(Modifier.height(textSpacing))
            }
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = colors.onSurface,
                )
            }
        }
    }
}

@Composable
fun AgentMessageCell(
    item: AgentMessageItem,
    modifier: Modifier = Modifier,
    stream: MarkdownStream? = null,
    streaming: Boolean = false,
    label: String = stringResource(R.string.messages_cell_assistant_name),
    cwd: String? = null,
    labelFontSize: TextUnit = UiType.Subtitle,
    labelLineHeight: TextUnit = UiType.SheetTitle,
    labelSpacing: Dp = 5.dp,
    questionSpacing: Dp = 10.dp,
    onAnswerQuestion: (String) -> Unit = {},
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val questions = item.questions.orEmpty()
    val copyLabel = stringResource(R.string.clipboard_copy_message)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = labelFontSize,
                lineHeight = labelLineHeight,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
                maxLines = 1,
            )
            // The rendered markdown is a lossy view of the source; `/copy` and this button both
            // hand over `item.text` so pasting keeps the original markdown.
            if (item.text.isNotBlank()) {
                IconButton(
                    onClick = { copyToClipboard(context, item.text, copyLabel) },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Copy,
                        contentDescription = copyLabel,
                        modifier = Modifier.size(UiConsts.IconInline),
                        tint = colors.primary.copy(alpha = 0.7f),
                    )
                }
            }
        }
        // A question message's text is the server's own rendering of the same questions — each
        // title followed by its options as a markdown list — so drawing it here would show every
        // question twice: once as inert markdown and once as the answerable list below. The list
        // carries the titles and options, so nothing is lost.
        if (questions.isEmpty()) {
            Spacer(Modifier.height(labelSpacing))
            // While deltas are being buffered the parsed blocks are the body; once the item
            // completes the stream is dropped and the authoritative text renders instead.
            if (stream != null && stream.hasContent) {
                MarkdownStreamText(stream = stream, streaming = streaming, cwd = cwd)
            } else {
                MarkdownText(markdown = item.text, cwd = cwd)
            }
        }
        if (questions.isNotEmpty()) {
            Spacer(Modifier.height(questionSpacing))
            QuestionList(questions, onAnswer = onAnswerQuestion)
        }
    }
}

/** One `@file` / image / skill chip above the bubble text. */
@Composable
private fun AttachmentChip(
    input: UserInput,
    corner: Dp = UiConsts.CornerChip,
    horizontalPadding: Dp = 7.dp,
    verticalPadding: Dp = 2.dp,
    fontSize: TextUnit = UiType.Footnote,
    lineHeight: TextUnit = UiType.CardTitle,
) {
    val colors = MiuixTheme.colorScheme
    val label = when (input) {
        is UserInput.Image -> stringResource(R.string.messages_cell_attachment_image)
        is UserInput.LocalImage -> stringResource(R.string.messages_cell_attachment_local_image)
        is UserInput.Audio -> stringResource(R.string.messages_cell_attachment_audio)
        is UserInput.LocalAudio -> stringResource(R.string.messages_cell_attachment_local_audio)
        is UserInput.Skill ->
            stringResource(R.string.messages_cell_attachment_skill, input.name)
        is UserInput.Mention -> stringResource(
            R.string.messages_cell_attachment_mention,
            input.path.ifEmpty { input.name },
        )
        is UserInput.Text -> input.text
    }
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(colors.primary.copy(alpha = 0.12f))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = colors.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Questions the agent asked inside its message, shown as a bordered list so a request that is
 * waiting on the user never reads as plain prose.
 *
 * Mirrors `codex-rs/tui/src/bottom_pane/async_questions/`: an option is one tap, a free-text answer
 * is always reachable — the "None of the above" row on a question that has options, the only row
 * on one that does not — and the submitted answer goes back as an ordinary user message framed with
 * the question it answers ([AsyncQuestions.answeredText]), which is what `go_next_or_submit` does
 * upstream. Answering locks the question locally; the list itself is a snapshot of the message, so
 * only a replayed item can show it again.
 */
@Composable
private fun QuestionList(
    questions: List<AsyncUserInputQuestion>,
    onAnswer: (String) -> Unit,
    corner: Dp = UiConsts.CornerControl,
    borderWidth: Dp = 1.dp,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 10.dp,
    questionSpacing: Dp = 9.dp,
    optionSpacing: Dp = 3.dp,
    titleFontSize: TextUnit = UiType.RowTitle,
    titleLineHeight: TextUnit = UiType.RowTitleLine,
    bulletWidth: Dp = 12.dp,
    optionFontSize: TextUnit = UiType.Subtitle,
    optionLineHeight: TextUnit = UiType.BodyLine,
    optionHorizontalPadding: Dp = 6.dp,
    optionVerticalPadding: Dp = 4.dp,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember(corner) { RoundedCornerShape(corner) }
    val optionShape = remember(corner) { RoundedCornerShape(corner) }
    // The editor applies the TUI's option bounds before anything is shown.
    val bounded = remember(questions) { AsyncQuestions.normalize(questions) }
    // question index -> the answer already submitted; an entry locks its question.
    val answered = remember(bounded) { mutableStateMapOf<Int, String>() }
    // question index -> the in-progress free-text draft.
    val drafts = remember(bounded) { mutableStateMapOf<Int, String>() }
    // question index -> the free-text row of an option question was tapped open.
    val otherOpen = remember(bounded) { mutableStateMapOf<Int, Boolean>() }

    fun submit(questionIndex: Int, title: String, answer: String) {
        if (answered.containsKey(questionIndex) || answer.isBlank()) return
        answered[questionIndex] = answer.trim()
        onAnswer(AsyncQuestions.answeredText(title, answer))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(borderWidth, colors.outline.copy(alpha = 0.55f), shape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(questionSpacing),
    ) {
        bounded.forEachIndexed { questionIndex, question ->
            val options = question.options.orEmpty()
            val chosen = answered[questionIndex]
            val freeTextOpen = options.isEmpty() || otherOpen[questionIndex] == true
            Column(verticalArrangement = Arrangement.spacedBy(optionSpacing)) {
                Text(
                    text = question.title,
                    fontSize = titleFontSize,
                    lineHeight = titleLineHeight,
                    color = colors.onSurface,
                )
                options.forEach { option ->
                    QuestionOptionRow(
                        text = option,
                        selected = chosen == option,
                        optionShape = optionShape,
                        bulletWidth = bulletWidth,
                        fontSize = optionFontSize,
                        lineHeight = optionLineHeight,
                        horizontalPadding = optionHorizontalPadding,
                        verticalPadding = optionVerticalPadding,
                        onClick = { submit(questionIndex, question.title, option) },
                    )
                }
                if (chosen != null && options.none { it == chosen }) {
                    // A free-text answer has no option row to mark, so it gets one of its own.
                    QuestionOptionRow(
                        text = chosen,
                        selected = true,
                        optionShape = optionShape,
                        bulletWidth = bulletWidth,
                        fontSize = optionFontSize,
                        lineHeight = optionLineHeight,
                        horizontalPadding = optionHorizontalPadding,
                        verticalPadding = optionVerticalPadding,
                        onClick = {},
                    )
                } else if (chosen == null && !freeTextOpen) {
                    QuestionOptionRow(
                        text = stringResource(R.string.request_user_input_view_other),
                        selected = false,
                        optionShape = optionShape,
                        bulletWidth = bulletWidth,
                        fontSize = optionFontSize,
                        lineHeight = optionLineHeight,
                        horizontalPadding = optionHorizontalPadding,
                        verticalPadding = optionVerticalPadding,
                        onClick = { otherOpen[questionIndex] = true },
                    )
                } else if (chosen == null) {
                    QuestionAnswerField(
                        value = drafts[questionIndex].orEmpty(),
                        onValueChange = { drafts[questionIndex] = it },
                        onSubmit = { submit(questionIndex, question.title, drafts[questionIndex].orEmpty()) },
                    )
                }
            }
        }
    }
}

/** One tappable row of a question: a model-authored option, the free-text row, or an answer. */
@Composable
private fun QuestionOptionRow(
    text: String,
    selected: Boolean,
    optionShape: RoundedCornerShape,
    bulletWidth: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = optionShape,
                container = if (selected) colors.primary.copy(alpha = 0.1f) else Color.Transparent,
                onClick = onClick,
            )
            .padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stringResource(R.string.messages_cell_option_bullet),
            modifier = Modifier.width(bulletWidth),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = if (selected) colors.primary else colors.onSurfaceVariantSummary,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = if (selected) colors.primary else colors.onSurfaceVariantSummary,
        )
        if (selected) {
            Spacer(Modifier.width(UiConsts.Space6))
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = stringResource(R.string.request_user_input_view_selected),
                modifier = Modifier.size(UiConsts.IconInline),
                tint = colors.primary,
            )
        }
    }
}

/** The free-text row: a field and the one button that sends what it holds. */
@Composable
private fun QuestionAnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val enabled = value.isNotBlank()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.Space2),
        verticalAlignment = Alignment.Bottom,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.request_user_input_view_your_answer),
            singleLine = false,
            minLines = 1,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (enabled) onSubmit() }),
            textStyle = MiuixTheme.textStyles.main,
        )
        Spacer(Modifier.width(UiConsts.Space6))
        IconButton(
            onClick = { if (enabled) onSubmit() },
            minWidth = UiConsts.IconButtonSize,
            minHeight = UiConsts.IconButtonSize,
        ) {
            Icon(
                imageVector = MiuixIcons.Send,
                contentDescription = stringResource(R.string.composer_send),
                modifier = Modifier.size(UiConsts.IconInline),
                tint = if (enabled) colors.primary else colors.disabledOnSurface,
            )
        }
    }
}

// The streaming caret lives with the markdown renderer: it is drawn by the tail block's own
// composable there, so a blink no longer changes the message text and no longer forces a re-parse.
