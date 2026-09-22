package com.cy.codex

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Default paragraph metrics, shared so transcript cells measure the same. */
private val TranscriptFontSize = UiType.Message
private val TranscriptLineHeight = UiType.MessageLine

/**
 * Markdown-lite renderer for agent output.
 *
 * The TUI renders through `codex-rs/tui/src/markdown_render.rs` with a streaming variant that
 * tolerates half-finished fences (`markdown_render/streaming.rs`). The phone keeps the same
 * contract but a smaller surface: paragraphs with hard breaks, headings up to level six (ATX and
 * setext), nested lists, block quotes, thematic breaks, indented and fenced code with syntax
 * highlighting, pipe tables, display math, and inline `code` / **bold** / *italic* / ~~strike~~ /
 * `$math$` / links are styled.
 *
 * A partially streamed document is legal input — an unterminated fence simply renders as code to
 * the end of the buffer. Streaming callers hand over a [MarkdownStream] so a delta rebuilds only
 * the tail block, links [MarkdownStreamText]; non-streaming callers pass the whole buffer.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MiuixTheme.colorScheme.onSurface,
    fontSize: TextUnit = TranscriptFontSize,
    lineHeight: TextUnit = TranscriptLineHeight,
    streaming: Boolean = false,
    blockSpacing: Dp = 10.dp,
    headingSizeStep: TextUnit = UiType.HeadingSizeStep,
    headingLineHeightStep: TextUnit = UiType.HeadingLeadingStep,
    quoteBarWidth: Dp = 3.dp,
    quoteBarHeight: Dp = 20.dp,
    quoteBarCorner: Dp = UiConsts.CornerBar,
    quoteSpacing: Dp = 10.dp,
    cwd: String? = null,
) {
    val style = markdownStyle(
        textColor, fontSize, lineHeight, blockSpacing, headingSizeStep, headingLineHeightStep,
        quoteBarWidth, quoteBarHeight, quoteBarCorner, quoteSpacing, cwd,
    )
    // The stream is immutable once filled: the buffer arrives whole here, and a caller that has a
    // growing buffer uses [MarkdownStreamText] instead so the parse stays incremental.
    val stream = remember(markdown) { MarkdownStream().apply { append(markdown) } }
    MarkdownStreamText(stream, modifier, style, streaming)
}

/**
 * Render a streaming buffer.
 *
 * [stream.tail] is the only state a delta rewrites, so the blocks in [MarkdownStream.frozen] keep
 * their composition and Skia keeps its text layouts; the caret, when [streaming], is drawn by the
 * tail block's own composable so its blink invalidates that one leaf.
 */
@Composable
fun MarkdownStreamText(
    stream: MarkdownStream,
    modifier: Modifier = Modifier,
    textColor: Color = MiuixTheme.colorScheme.onSurface,
    fontSize: TextUnit = TranscriptFontSize,
    lineHeight: TextUnit = TranscriptLineHeight,
    streaming: Boolean = true,
    blockSpacing: Dp = 10.dp,
    headingSizeStep: TextUnit = UiType.HeadingSizeStep,
    headingLineHeightStep: TextUnit = UiType.HeadingLeadingStep,
    quoteBarWidth: Dp = 3.dp,
    quoteBarHeight: Dp = 20.dp,
    quoteBarCorner: Dp = UiConsts.CornerBar,
    quoteSpacing: Dp = 10.dp,
    cwd: String? = null,
) {
    val style = markdownStyle(
        textColor, fontSize, lineHeight, blockSpacing, headingSizeStep, headingLineHeightStep,
        quoteBarWidth, quoteBarHeight, quoteBarCorner, quoteSpacing, cwd,
    )
    MarkdownStreamText(stream, modifier, style, streaming)
}

@Composable
private fun MarkdownStreamText(
    stream: MarkdownStream,
    modifier: Modifier,
    style: MarkdownStyle,
    streaming: Boolean,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(style.blockSpacing)) {
        // Each list read belongs to its own composable: appending a frozen block recomposes this
        // loop only, and a tail rewrite does not touch it at all.
        FrozenBlocks(stream.frozen, style)
        val tail = stream.tail
        if (tail != null) MarkdownBlockView(tail, style, caret = streaming)
    }
}

@Composable
private fun FrozenBlocks(blocks: List<MarkdownBlock>, style: MarkdownStyle) {
    for (index in blocks.indices) {
        // Frozen blocks are append-only, so positional identity is stable. Unchanged blocks
        // compare equal by content and skip.
        MarkdownBlockView(blocks[index], style, caret = false)
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, style: MarkdownStyle, caret: Boolean) {
    when (block) {
        is MarkdownBlock.Paragraph -> StyledText(
            text = style.inline(block.text, style.textColor),
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            color = style.textColor,
            caret = caret,
        )

        is MarkdownBlock.Heading -> StyledText(
            text = style.inline(block.text, style.textColor),
            fontSize = (style.fontSize.value + headingStep(block.level) * style.headingSizeStep.value).sp,
            lineHeight = (style.lineHeight.value + style.headingLineHeightStep.value).sp,
            color = style.textColor,
            caret = caret,
            fontWeight = FontWeight.SemiBold,
        )

        is MarkdownBlock.Bullet -> BulletRow(
            marker = stringResource(R.string.markdown_render_bullet),
            text = style.inline(block.text, style.textColor),
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            color = style.textColor,
            caret = caret,
            depth = block.depth,
        )

        is MarkdownBlock.Numbered -> BulletRow(
            marker = stringResource(R.string.markdown_render_numbered, block.index),
            text = style.inline(block.text, style.textColor),
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            color = style.textColor,
            caret = caret,
            depth = block.depth,
        )

        is MarkdownBlock.Quote -> QuoteRow(
            text = style.inline(block.text, style.textColor),
            style = style,
            caret = caret,
        )

        is MarkdownBlock.Code -> CodeBlock(
            code = block.code,
            language = block.language,
            streaming = block.open,
            caret = caret,
        )

        is MarkdownBlock.OpenCode -> StreamingCodeBlock(block, caret = caret)

        is MarkdownBlock.Table -> TableView(
            header = block.header,
            rows = block.rows,
            alignments = block.alignments,
            style = style,
            caret = caret,
        )

        is MarkdownBlock.OpenTable -> OpenTableView(block, style, caret = caret)

        MarkdownBlock.ThematicBreak -> ThematicBreakView()

        is MarkdownBlock.Math -> MathBlock(block.text)
    }
}

/** Extra size steps an H1..H6 gets over the body text; H4-H6 step down below H3. */
private fun headingStep(level: Int): Int = (3 - level).coerceIn(-2, 3)

/** Styling for every block, bundled so a block view compares one stable parameter. */
@Immutable
private data class MarkdownStyle(
    val textColor: Color,
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val blockSpacing: Dp,
    val headingSizeStep: TextUnit,
    val headingLineHeightStep: TextUnit,
    val quoteBarWidth: Dp,
    val quoteBarHeight: Dp,
    val quoteBarCorner: Dp,
    val quoteSpacing: Dp,
    val cwd: String?,
) {
    /** Resolve inline spans; kept here so every block shares one link colour and cwd. */
    @Composable
    fun inline(text: String, color: Color): AnnotatedString {
        val uriHandler = LocalUriHandler.current
        val context = LocalContext.current
        val linkColor = MiuixTheme.colorScheme.primary
        return remember(text, color, linkColor, cwd, uriHandler, context) {
            inline(text, color, cwd, linkColor) { target -> openLink(context, uriHandler, target) }
        }
    }
}

@Composable
private fun markdownStyle(
    textColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    blockSpacing: Dp,
    headingSizeStep: TextUnit,
    headingLineHeightStep: TextUnit,
    quoteBarWidth: Dp,
    quoteBarHeight: Dp,
    quoteBarCorner: Dp,
    quoteSpacing: Dp,
    cwd: String?,
): MarkdownStyle = MarkdownStyle(
    textColor, fontSize, lineHeight, blockSpacing, headingSizeStep, headingLineHeightStep,
    quoteBarWidth, quoteBarHeight, quoteBarCorner, quoteSpacing, cwd,
)

/**
 * A body of text plus the streaming caret.
 *
 * The caret is appended here, in the leaf, so a blink recomposes this Text and nothing above it.
 */
@Composable
private fun StyledText(
    text: AnnotatedString,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    caret: Boolean = false,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    modifier: Modifier = Modifier,
) {
    val suffix = if (caret) rememberBlinkingCaret() else ""
    val shown = remember(text, suffix) {
        if (suffix.isEmpty()) text else AnnotatedString.Builder(text).apply { append(suffix) }.toAnnotatedString()
    }
    Text(
        text = shown,
        modifier = modifier,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        textAlign = textAlign,
        color = color,
    )
}

@Composable
private fun BulletRow(
    marker: String,
    text: AnnotatedString,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    caret: Boolean,
    depth: Int = 0,
    markerWidth: Dp = UiConsts.IconLeading,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (depth > 0) Spacer(Modifier.width(markerWidth * depth))
        Text(
            text = marker,
            modifier = Modifier.width(markerWidth),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        StyledText(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
            caret = caret,
        )
    }
}

@Composable
private fun QuoteRow(text: AnnotatedString, style: MarkdownStyle, caret: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(style.quoteBarWidth)
                .height(style.quoteBarHeight)
                .clip(RoundedCornerShape(style.quoteBarCorner))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(style.quoteSpacing))
        StyledText(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            caret = caret,
        )
    }
}

/** The horizontal rule `---` / `***` / `___` renders. */
@Composable
private fun ThematicBreakView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
    )
}

/** A display equation: mono-italic so `\alpha` and friends stay distinguishable. */
@Composable
private fun MathBlock(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiConsts.CornerChip))
            .background(codeSurface())
            .padding(horizontal = 10.dp, vertical = 7.dp),
        fontSize = UiType.Body,
        lineHeight = UiType.Message,
        fontFamily = FontFamily.Monospace,
        fontStyle = FontStyle.Italic,
        color = MiuixTheme.colorScheme.onSurface,
    )
}

// ---------------------------------------------------------------------------------------------
// Tables
// ---------------------------------------------------------------------------------------------

@Composable
private fun TableView(
    header: List<String>,
    rows: List<List<String>>,
    alignments: List<TableAlignment>,
    style: MarkdownStyle,
    caret: Boolean,
    modifier: Modifier = Modifier,
) {
    TableFrame(modifier) {
        TableRowView(header, alignments, style, header = true, caret = false)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(1.dp)
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
        )
        for (index in rows.indices) {
            TableRowView(rows[index], alignments, style, header = false, caret = caret && index == rows.lastIndex)
        }
    }
}

@Composable
private fun OpenTableView(
    table: MarkdownBlock.OpenTable,
    style: MarkdownStyle,
    caret: Boolean,
    modifier: Modifier = Modifier,
    rowSpacing: Dp = 3.dp,
) {
    TableFrame(modifier) {
        TableRowView(table.header, table.alignments, style, header = true, caret = false)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(1.dp)
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
        )
        val rows = table.rows
        for (index in rows.indices) {
            TableRowView(
                cells = rows[index],
                alignments = table.alignments,
                style = style,
                header = false,
                caret = caret && index == rows.lastIndex,
                topPadding = rowSpacing,
            )
        }
    }
}

@Composable
private fun TableFrame(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiConsts.CornerRow))
            .background(codeSurface())
            .padding(vertical = 7.dp),
    ) {
        content()
    }
}

@Composable
private fun TableRowView(
    cells: List<String>,
    alignments: List<TableAlignment>,
    style: MarkdownStyle,
    header: Boolean,
    caret: Boolean,
    topPadding: Dp = 0.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = if (header) 0.dp else topPadding),
    ) {
        for (index in cells.indices) {
            val text = style.inline(cells[index], style.textColor)
            StyledText(
                text = text,
                modifier = Modifier.weight(1f),
                fontSize = UiType.Meta,
                lineHeight = UiType.Message,
                color = style.textColor,
                caret = caret && index == cells.lastIndex,
                fontWeight = if (header) FontWeight.Medium else null,
                textAlign = when (alignments.getOrNull(index) ?: TableAlignment.Start) {
                    TableAlignment.Start -> TextAlign.Start
                    TableAlignment.Center -> TextAlign.Center
                    TableAlignment.End -> TextAlign.End
                },
            )
        }
    }
}

/**
 * A fenced code block. Wraps the whole block in a horizontally scrollable surface with a language
 * chip, which is what the TUI's `code_fence.rs` does with its own fence detection.
 *
 * The body is one Text per line rather than one Text for the block: a streaming fence then lays out
 * only the line that changed, and every earlier line keeps its cached layout.
 */
@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    caret: Boolean = false,
    corner: Dp = UiConsts.CornerRow,
    headerStartPadding: Dp = 14.dp,
    headerEndPadding: Dp = 12.dp,
    headerTopPadding: Dp = 9.dp,
    labelFontSize: TextUnit = UiType.Caption,
    labelLineHeight: TextUnit = UiType.CardTitle,
    contentHorizontalPadding: Dp = 14.dp,
    contentVerticalPadding: Dp = 10.dp,
    codeFontSize: TextUnit = UiType.Body,
    codeLineHeight: TextUnit = UiType.BodyLine,
) {
    val palette = syntaxPalette()
    val trimmed = remember(code) { code.trimEnd('\n') }
    val lines = remember(trimmed) { if (trimmed.isEmpty()) emptyList() else trimmed.lines() }
    val styled = remember(trimmed, language, palette) { highlightCodeLines(trimmed, language, palette) }
    CodeSurface(
        language = language,
        streaming = streaming,
        copyText = trimmed.ifBlank { null },
        modifier = modifier,
        corner = corner,
        headerStartPadding = headerStartPadding,
        headerEndPadding = headerEndPadding,
        headerTopPadding = headerTopPadding,
        labelFontSize = labelFontSize,
        labelLineHeight = labelLineHeight,
        contentHorizontalPadding = contentHorizontalPadding,
        contentVerticalPadding = contentVerticalPadding,
    ) {
        for (index in lines.indices) {
            CodeLine(
                text = styled.getOrElse(index) { AnnotatedString(lines[index]) },
                caret = caret && index == lines.lastIndex,
                fontSize = codeFontSize,
                lineHeight = codeLineHeight,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StreamingCodeBlock(block: MarkdownBlock.OpenCode, caret: Boolean) {
    val palette = syntaxPalette()
    val spec = remember(block.language) { languageSpec(block.language) }
    val lexer = remember(spec, palette) { spec?.let { SyntaxLexer(it, palette) } }
    // Highlighted lines are cached per completed line: a streaming fence then pays for the line it
    // just finished instead of re-lexing every line on each delta.
    val styled = remember(lexer, palette) { mutableListOf<AnnotatedString>() }
    CodeSurface(
        language = block.language,
        streaming = true,
        copyText = (block.lines + block.partial).joinToString("\n").trimEnd('\n').ifBlank { null },
        modifier = Modifier,
        corner = UiConsts.CornerRow,
        headerStartPadding = 14.dp,
        headerEndPadding = 12.dp,
        headerTopPadding = 9.dp,
        labelFontSize = UiType.Caption,
        labelLineHeight = UiType.CardTitle,
        contentHorizontalPadding = 14.dp,
        contentVerticalPadding = 10.dp,
    ) {
        val lines = block.lines
        if (lexer == null) {
            for (index in lines.indices) {
                CodeLine(
                    text = AnnotatedString(lines[index]),
                    caret = false,
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        } else {
            while (styled.size < lines.size) {
                styled.add(lexer.highlight(lines[styled.size]))
            }
            for (index in lines.indices) {
                CodeLine(
                    text = styled[index],
                    caret = false,
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
        // The partial line is where the stream is writing; only this scope re-reads when it grows.
        // Its highlight starts from the state the previous complete line left behind, which is what
        // keeps a block comment or raw string open across the boundary.
        val partial = block.partial
        if (partial.isNotEmpty() || caret) {
            val partialStyled = if (lexer == null || partial.isEmpty()) {
                AnnotatedString(partial)
            } else {
                val state = lexer.snapshot()
                val highlighted = lexer.highlight(partial)
                lexer.restore(state)
                highlighted
            }
            CodeLine(
                text = partialStyled,
                caret = caret,
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CodeSurface(
    language: String?,
    streaming: Boolean,
    copyText: String?,
    modifier: Modifier,
    corner: Dp,
    headerStartPadding: Dp,
    headerEndPadding: Dp,
    headerTopPadding: Dp,
    labelFontSize: TextUnit,
    labelLineHeight: TextUnit,
    contentHorizontalPadding: Dp,
    contentVerticalPadding: Dp,
    content: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val shape = remember(corner) { RoundedCornerShape(corner) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(codeSurface()),
    ) {
        if (!language.isNullOrBlank() || streaming || copyText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = headerStartPadding,
                        end = headerEndPadding,
                        top = headerTopPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language?.ifBlank { null } ?: stringResource(R.string.markdown_render_code),
                    modifier = Modifier.weight(1f),
                    fontSize = labelFontSize,
                    lineHeight = labelLineHeight,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (streaming) {
                    Text(
                        text = stringResource(R.string.markdown_render_generating),
                        fontSize = labelFontSize,
                        lineHeight = labelLineHeight,
                        color = colors.primary,
                    )
                }
                // The copy target is the fence source, not the highlighted spans, so pasting it
                // anywhere lands as plain code.
                if (copyText != null) {
                    val copyLabel = stringResource(R.string.clipboard_copy_code)
                    IconButton(
                        onClick = { copyToClipboard(context, copyText, copyLabel) },
                        minWidth = UiConsts.IconButtonSize,
                        minHeight = UiConsts.IconButtonSize,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Copy,
                            contentDescription = copyLabel,
                            modifier = Modifier.size(UiConsts.IconInline),
                            tint = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = contentHorizontalPadding, vertical = contentVerticalPadding),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun CodeLine(
    text: AnnotatedString,
    caret: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
) {
    val suffix = if (caret) rememberBlinkingCaret() else ""
    val shown = remember(text, suffix) {
        if (suffix.isEmpty()) text else AnnotatedString.Builder(text).apply { append(suffix) }.toAnnotatedString()
    }
    Text(
        text = shown,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = FontFamily.Monospace,
        color = color,
        softWrap = false,
    )
}

/** One-line shell command with the same monospace treatment the TUI's exec cells use. */
@Composable
fun InlineCode(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.onSurface,
    corner: Dp = UiConsts.CornerChip,
    horizontalPadding: Dp = 5.dp,
    verticalPadding: Dp = 1.dp,
    fontSize: TextUnit = UiType.Body,
    lineHeight: TextUnit = UiType.BodyLine,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(codeSurface())
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = FontFamily.Monospace,
        color = color,
        softWrap = false,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------------------------------------
// Inline spans
// ---------------------------------------------------------------------------------------------

/**
 * Style inline spans.
 *
 * Recognised spans are the ones agent output actually uses: `code`, **bold**, *italic*,
 * ~~strikethrough~~, `$inline math$`, `[label](url)` links and `:codex-file-citation{…}` directives.
 * A link keeps its destination: web links are annotated so a tap opens them, and local file links
 * render their cwd-relative target so the path is readable without a tap.
 */
internal fun inline(
    text: String,
    color: Color,
    cwd: String? = null,
    linkColor: Color = color,
    onLink: ((LinkTarget) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < text.length) {
        val next = nextSpan(text, cursor, cwd) ?: run {
            append(text.substring(cursor))
            break
        }
        append(text.substring(cursor, next.start))
        if (next.target == null) {
            withStyle(next.style(color)) { append(next.render) }
        } else {
            val annotation = LinkAnnotation.Clickable(
                tag = next.render,
                styles = TextLinkStyles(
                    style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                ),
                linkInteractionListener = { onLink?.invoke(next.target) },
            )
            withLink(annotation) {
                withStyle(next.style(linkColor)) { append(next.render) }
                if (next.suffix.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = linkColor.copy(alpha = 0.75f),
                        ),
                    ) {
                        append(next.suffix)
                    }
                }
            }
        }
        cursor = next.end
    }
}

private class Span(
    val start: Int,
    val end: Int,
    val render: String,
    val style: (Color) -> SpanStyle,
    val target: LinkTarget? = null,
    val suffix: String = "",
)

private fun nextSpan(text: String, from: Int, cwd: String?): Span? {
    val candidates = listOfNotNull(
        spanOf(text, from, '`', '`') { c ->
            SpanStyle(fontFamily = FontFamily.Monospace, color = c, background = c.copy(alpha = 0.07f))
        },
        spanOf(text, from, "**", "**") { _ -> SpanStyle(fontWeight = FontWeight.SemiBold) },
        spanOf(text, from, "~~", "~~") { _ -> SpanStyle(textDecoration = TextDecoration.LineThrough) },
        spanOf(text, from, '*', '*') { _ -> SpanStyle(fontStyle = FontStyle.Italic) },
        mathSpan(text, from),
        linkSpan(text, from, cwd),
        citationSpan(text, from, cwd),
    )
    return candidates.minByOrNull { it.start }
}

private fun spanOf(
    text: String,
    from: Int,
    open: Char,
    close: Char,
    style: (Color) -> SpanStyle,
): Span? {
    val start = text.indexOf(open, from)
    if (start < 0) return null
    val end = text.indexOf(close, start + 1)
    if (end < 0 || end == start + 1) return null
    return Span(start, end + 1, text.substring(start + 1, end), style)
}

private fun spanOf(
    text: String,
    from: Int,
    open: String,
    close: String,
    style: (Color) -> SpanStyle,
): Span? {
    val start = text.indexOf(open, from)
    if (start < 0) return null
    val end = text.indexOf(close, start + open.length)
    if (end < 0) return null
    return Span(start, end + close.length, text.substring(start + open.length, end), style)
}

/** `$…$` with no whitespace next to the delimiters, so shell variables and prices stay text. */
private fun mathSpan(text: String, from: Int): Span? {
    var start = text.indexOf('$', from)
    while (start >= 0) {
        if (start > 0 && text[start - 1] == '\\') {
            start = text.indexOf('$', start + 1)
            continue
        }
        val end = text.indexOf('$', start + 1)
        if (end > start + 1) {
            val body = text.substring(start + 1, end)
            val bounded = body.firstOrNull()?.isWhitespace() == false &&
                body.lastOrNull()?.isWhitespace() == false &&
                !body.contains('\n')
            if (bounded) {
                return Span(
                    start,
                    end + 1,
                    body,
                    { c -> SpanStyle(fontFamily = FontFamily.Monospace, fontStyle = FontStyle.Italic, color = c) },
                )
            }
            start = text.indexOf('$', end + 1)
        } else {
            start = text.indexOf('$', start + 1)
        }
    }
    return null
}

/** `[label](destination)`; the destination is kept as the link target, not dropped. */
private fun linkSpan(text: String, from: Int, cwd: String?): Span? {
    var open = text.indexOf('[', from)
    while (open >= 0) {
        val close = text.indexOf(']', open + 1)
        if (close > open + 1 && text.getOrNull(close + 1) == '(') {
            val end = text.indexOf(')', close + 2)
            if (end > close + 2) {
                val label = text.substring(open + 1, close)
                // A title (`"…"`) may follow the destination; it is not part of the URL.
                val destination = text.substring(close + 2, end).trim()
                    .substringBefore(" ")
                    .removeSurrounding("<", ">")
                if (destination.isNotEmpty()) {
                    val target = parseLinkTarget(destination, cwd)
                    val suffix = if (target is LinkTarget.Local) " (${target.display})" else ""
                    val render = label.ifEmpty {
                        if (target is LinkTarget.Local) target.display else destination
                    }
                    return Span(
                        open,
                        end + 1,
                        render,
                        { c -> SpanStyle(color = c) },
                        target,
                        suffix,
                    )
                }
            }
        }
        open = text.indexOf('[', open + 1)
    }
    return null
}

/**
 * A `:codex-file-citation{path="…"}` directive.
 *
 * The directive is control data, not prose: it is replaced by the path it points at, exactly as
 * `markdown_render/file_citations.rs` turns it into a local link.
 */
private fun citationSpan(text: String, from: Int, cwd: String?): Span? {
    var start = text.indexOf(":codex-file-citation{", from)
    while (start >= 0) {
        val citation = citationAt(text, start)
        if (citation != null) {
            val (end, path) = citation
            val target = parseLinkTarget(
                if (isLocalPathLike(path)) path else "./$path",
                cwd,
            )
            val display = (target as? LinkTarget.Local)?.display ?: path
            return Span(
                start,
                end,
                display,
                { c -> SpanStyle(fontFamily = FontFamily.Monospace, color = c) },
                target,
            )
        }
        start = text.indexOf(":codex-file-citation{", start + 1)
    }
    return null
}

// ---------------------------------------------------------------------------------------------
// Caret
// ---------------------------------------------------------------------------------------------

/**
 * The TUI's block caret: `▍` at the end of a streaming block, blinking on a 600ms period.
 *
 * The animation is created by the composable that draws the caret, so its 60fps invalidation never
 * reaches a parent: a page of frozen blocks does not repaint because the caret blinked.
 */
@Composable
private fun rememberBlinkingCaret(periodMs: Int = StreamingCaretPeriodMs): String {
    val transition = rememberInfiniteTransition(label = "streamingCaret")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "streamingCaretPhase",
    )
    return if (phase > 0.5f) BlockCaret else ""
}

private const val StreamingCaretPeriodMs = 600
private const val BlockCaret = "▍"

/** Default paragraph style, shared so transcript cells measure the same. */
val TranscriptTextStyle: TextStyle
    @Composable get() = TextStyle(
        fontSize = TranscriptFontSize,
        lineHeight = TranscriptLineHeight,
        color = MiuixTheme.colorScheme.onSurface,
    )
