package com.cy.codex

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.UiConsts
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.SquircleDefaults
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * Shared surface chrome.
 *
 * Mirrors `codex-rs/tui/src/style.rs`: the squircle silhouette, the glass fill and the expanding
 * bar live in one place so the sidebar, the status card and the composer cannot drift apart.
 */

/**
 * A continuous ("squircle") rounded corner shape built on the miuix squircle SDF path.
 *
 * Extends [CornerBasedShape] so shader based effects (edge highlight, lens) can read the corner
 * radii instead of falling back to a stadium approximation. Immutable: the two fields are `val`s
 * and nothing inside the class is observable, so a shape instance may be shared between
 * composables without making them unstable.
 */
@Immutable
class SquircleShape(
    private val cornerRadius: Dp,
    private val extension: Float = SquircleDefaults.Extension,
) : CornerBasedShape(
    topStart = CornerSize(cornerRadius),
    topEnd = CornerSize(cornerRadius),
    bottomEnd = CornerSize(cornerRadius),
    bottomStart = CornerSize(cornerRadius),
) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val path = Path().apply {
            addSquircleRect(
                width = size.width,
                height = size.height,
                cornerRadius = minOf(topStart, topEnd, bottomEnd, bottomStart),
                extension = extension,
            )
        }
        return Outline.Generic(path)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = SquircleShape(cornerRadius, extension)
}

/**
 * Floating surface with squircle silhouette, soft shadow and hairline outline.
 *
 * When [backdrop] and [blurRadius] are provided the surface samples the content behind it,
 * otherwise it stays an opaque HyperOS style panel.
 */
@Composable
fun Modifier.floatingSurface(
    shape: Shape,
    tint: Color,
    backdrop: Backdrop? = null,
    blurRadius: Dp = 0.dp,
    pressOverlay: Color = Color.Transparent,
    brightness: Float = 0f,
    saturation: Float = 1.2f,
    elevation: Dp = 14.dp,
    useHighlight: Boolean = true,
    borderWidth: Dp = 0.7.dp,
): Modifier {
    val isDark = isSystemInDarkTheme()
    val highlight = if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
    val outline = MiuixTheme.colorScheme.outline.copy(alpha = if (isDark) 0.24f else 0.3f)
    val blurred = backdrop != null && blurRadius > 0.dp
    // `blurred` already proves the backdrop exists; binding it here keeps the draw path free of
    // assertions the compiler would have to trust.
    val sampler = if (blurred) backdrop else null
    val base = this
        .dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = elevation,
                color = Color.Black,
                alpha = if (isDark) 0.42f else 0.11f,
            ),
        )
        .background(tint, shape)
        .background(pressOverlay, shape)
        .border(width = borderWidth, color = outline, shape = shape)

    if (sampler == null) return base
    return base.drawBackdrop(
        backdrop = sampler,
        shape = { shape },
        effects = {
            blur(blurRadius.toPx())
            colorControls(brightness = brightness, contrast = 1f, saturation = saturation)
        },
        onDrawSurface = { drawRect(tint) },
        onDrawFront = { if (pressOverlay.alpha > 0f) drawRect(pressOverlay) },
        highlight = { if (useHighlight) highlight else null },
    )
}

/**
 * The two button heights the app has.
 *
 * [Standard] is a decision — a dialog footer, a sheet's confirm, a form's submit. [Compact] is a
 * control that lives inline in a row, where a 44dp pill would set the row's height by itself. Two
 * steps, because a third is how a screen ends up with three buttons that almost match.
 */
@JvmInline
value class CodexButtonSize private constructor(private val rank: Int) {
    companion object {
        val Standard = CodexButtonSize(0)
        val Compact = CodexButtonSize(1)
    }

    /** Minimum touch target height. */
    val height: Dp get() = if (rank == 0) UiConsts.ButtonHeight else UiConsts.ButtonHeightCompact

    val paddingHorizontal: Dp
        get() = if (rank == 0) UiConsts.ButtonPaddingHorizontal else UiConsts.ButtonPaddingHorizontalCompact
}

/**
 * The app's one button.
 *
 * It is a miuix [Button]: the library owns the squircle surface, the tab-stop semantics and the
 * press highlight, so a button and a preference row cannot answer a press two different ways. What
 * stays here is the app's decision about form — the two heights of [CodexButtonSize] and the pill
 * corner that follows from them — and the role hierarchy, which miuix does not have a vocabulary
 * for.
 *
 * The three roles are a hierarchy, not three colours:
 * - [ButtonRole.Primary] — the action the surface was opened for. Filled with `primary`.
 * - [ButtonRole.Secondary] — the alternative ("this session only"). Filled with the library's
 *   `secondaryVariant`, which is exactly the colour the miuix example gives its default button.
 * - [ButtonRole.Destructive] — a refusal. Outlined with error-coloured text and *not* filled: a
 *   solid red button next to a solid accent one makes the refusal look like the primary action,
 *   which is exactly the wrong thing to make easy to hit by accident.
 */
enum class ButtonRole { Primary, Secondary, Destructive }

/**
 * A pill button.
 *
 * @param text the label, always one line.
 * @param onClick invoked on tap; not invoked while [enabled] is false.
 * @param modifier layout modifier, applied outside the pill's own background.
 * @param role which of the three positions in the hierarchy this button takes.
 * @param size [CodexButtonSize.Standard] for a decision, [CodexButtonSize.Compact] inline in a row.
 * @param enabled false renders the disabled fill and drops the tap, keeping the label legible.
 * @param minWidth floor under the pill's width; the dialog footer uses it so a short label does not
 *   produce a stubby button next to a long one.
 */
@Composable
fun CodexButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: ButtonRole = ButtonRole.Primary,
    size: CodexButtonSize = CodexButtonSize.Standard,
    enabled: Boolean = true,
    minWidth: Dp = 0.dp,
) {
    val colors = MiuixTheme.colorScheme
    // The pill is the button's own height as a squircle radius: miuix blends the corner to a circle
    // once the radius reaches half the shorter side, so this is a capsule at both sizes.
    val cornerRadius = size.height / 2
    // Only the refusal is outlined. The two filled roles use the library's own button colours, so a
    // CodexButton and a miuix TextButton in the same footer resolve to the same two fills.
    val outline = when {
        role != ButtonRole.Destructive -> Color.Transparent
        !enabled -> colors.outline.copy(alpha = 0.18f)
        else -> colors.error.copy(alpha = 0.5f)
    }
    val buttonColors = when (role) {
        ButtonRole.Primary -> ButtonColors(
            color = colors.primary,
            disabledColor = colors.disabledPrimaryButton,
            contentColor = colors.onPrimary,
            disabledContentColor = colors.disabledOnPrimaryButton,
        )

        ButtonRole.Secondary -> ButtonColors(
            color = colors.secondaryVariant,
            disabledColor = colors.disabledSecondaryVariant,
            contentColor = colors.onSecondaryVariant,
            disabledContentColor = colors.disabledOnSecondaryVariant,
        )

        ButtonRole.Destructive -> ButtonColors(
            color = Color.Transparent,
            disabledColor = colors.disabledOnSurface.copy(alpha = 0.1f),
            contentColor = colors.error,
            disabledContentColor = colors.disabledOnSurface,
        )
    }
    Button(
        onClick = onClick,
        // miuix's Button has no border of its own; the two outlined roles take the library's
        // squircle stroke so the outline follows the same silhouette as the fill.
        modifier = if (outline == Color.Transparent) {
            modifier
        } else {
            modifier.squircleBorder(UiConsts.OutlineThickness, outline, cornerRadius)
        },
        enabled = enabled,
        cornerRadius = cornerRadius,
        minWidth = minWidth,
        minHeight = size.height,
        colors = buttonColors,
        insideMargin = PaddingValues(horizontal = size.paddingHorizontal, vertical = 0.dp),
    ) {
        Text(
            text = text,
            fontSize = UiType.Action,
            lineHeight = UiType.ActionLine,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Row background with a selection fill and the theme's press highlight, shared by selection rows in
 * the sidebar, the agent list and the file list.
 *
 * The press feedback is [LocalIndication] — the same miuix style highlight every preference row
 * uses — instead of a hand-drawn overlay. The *fill* stays here because miuix has no selected-row
 * container: every selection row in the app — the agent roster, the model and effort lists, the
 * sidebar's session list, the status card's file rows — changes this fill when it becomes the
 * selected one.
 *
 * [onLongClick] is optional and, when given, is what makes a row mean two things: the agents list
 * uses a tap to open the agent's session and a long press to open its details, the way a file row
 * opens the diff and a session row opens the thread.
 */
@Composable
fun Modifier.pressableRow(
    shape: Shape,
    container: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = container,
        animationSpec = Motion.Tint,
        label = "rowContainer",
    )
    return this
        .clip(shape)
        .background(fill, shape)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * The expanding bar shared by both floating drawers: the navigation panel on the left and the
 * status card on the right.
 *
 * Blur for an expanding bar belongs to miuix, so when the library ships it for the expand option
 * this is the single place that adopts it — until then the bar stays an opaque HyperOS panel and
 * the two sides cannot drift apart.
 *
 * Touches on the bar are always consumed, so a tap can never fall through to the transcript behind.
 *
 * @param expanded whether the bar is currently open.
 * @param onExpandRequest when set and [expanded] is false, tapping the bar itself expands it.
 * @param height fixed height of the bar, or `null` to let the content size it.
 */
@Composable
fun ExpandBar(
    width: Dp,
    height: Dp?,
    shape: Shape,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onExpandRequest: (() -> Unit)? = null,
    elevation: Dp = UiConsts.PanelElevation,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val collapsible = !expanded && onExpandRequest != null
    val pressOverlay by animateColorAsState(
        targetValue = if (collapsible && pressed) {
            colors.onBackground.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        animationSpec = Motion.Tint,
        label = "expandBarPress",
    )
    // The drawer is a panel whose *fill* changes with its state as well as its size, so the tint is
    // animated rather than swapped: it used to repaint on one frame while its own press overlay
    // faded over 160ms.
    val tint by animateColorAsState(
        targetValue = panelColor(),
        animationSpec = Motion.Tint,
        label = "expandBarTint",
    )

    Column(
        modifier = modifier
            .width(width)
            .then(if (height != null) Modifier.height(height) else Modifier)
            .floatingSurface(
                shape = shape,
                tint = tint,
                pressOverlay = pressOverlay,
                elevation = elevation,
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (collapsible) onExpandRequest() },
            ),
        content = content,
    )
}

/**
 * Section card used by the status card and the settings pages.
 *
 * The fill is a miuix [Card]: the squircle silhouette, the corner radius and the content colour
 * come from the library, so a section card and a settings card cannot resolve to two different
 * surfaces. Only the header — an icon, a title, a value and an optional disclosure arrow — and the
 * collapsing body are ours.
 */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    expandable: Boolean = false,
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
    horizontalPadding: Dp = 11.dp,
    verticalPadding: Dp = 8.dp,
    iconSize: Dp = 14.dp,
    iconSpacing: Dp = 8.dp,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 18.sp,
    trailingSpacing: Dp = 4.dp,
    arrowSize: Dp = 14.dp,
    contentSpacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
        colors = CardDefaults.defaultColors(color = raisedSurface(), contentColor = colors.onSurface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expandable && onToggle != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onToggle,
                        )
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = colors.primary,
            )
            Spacer(Modifier.width(iconSpacing))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                )
            }
            if (expandable && onToggle != null) {
                Spacer(Modifier.width(trailingSpacing))
                val arrowRotation by animateFloatAsState(
                    targetValue = if (expanded) 90f else 0f,
                    animationSpec = Motion.Disclosure,
                    label = "sectionArrow",
                )
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = if (expanded) {
                        stringResource(R.string.components_collapse, title)
                    } else {
                        stringResource(R.string.components_expand, title)
                    },
                    modifier = Modifier
                        .size(arrowSize)
                        .graphicsLayer { rotationZ = arrowRotation },
                    tint = colors.onSurfaceVariantSummary,
                )
            }
        }
        // The body grows and shrinks with the card instead of appearing on one frame. `content()`
        // stays inside the scope so a card that is collapsed does not compose its rows at all.
        FoldAway(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(contentSpacing))
                content()
            }
        }
    }
}

/**
 * Folds a card's body away, and reports the height it has *while* it folds.
 *
 * This is `AnimatedVisibility`'s expand and shrink with one correction: it answers the
 * intrinsic-height question with the animated height rather than with the height it is heading for.
 * That question is not academic here. The status card's agents and files cards sit in a row measured
 * by its intrinsic height — `Modifier.height(IntrinsicSize.Min)` is how two cards end on the same
 * line — and a body whose intrinsic answer jumps to the settled height held that row at its old
 * height for the whole fold and dropped it on the last frame, which is what made the panel around
 * them snap instead of fold. Everything else is what `AnimatedVisibility` did: the body is clipped
 * as it moves, it fades as it goes, and a closed card does not compose its rows at all.
 */
@Composable
private fun FoldAway(
    visible: Boolean,
    modifier: Modifier = Modifier,
    animationSpec: FiniteAnimationSpec<Float> = Motion.Disclosure,
    content: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = animationSpec,
        label = "foldAway",
    )
    // The policy reads the progress, so it is rebuilt while the fold runs — a new policy instance is
    // what tells the layout node to measure again with the new fraction.
    val policy = remember(progress) {
        object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints,
            ): MeasureResult {
                val placeable = measurables.firstOrNull()
                    ?.measure(constraints.copy(minHeight = 0))
                    ?: return layout(0, 0) {}
                return layout(placeable.width, (placeable.height * progress).roundToInt()) {
                    placeable.place(0, 0)
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int,
            ): Int = measurables.firstOrNull()
                ?.let { (it.minIntrinsicHeight(width) * progress).roundToInt() }
                ?: 0
        }
    }
    // `clipToBounds` is what makes the fold a fold: without it the body would be measured shorter
    // but still drawn at its full height, spilling out of the card it belongs to.
    Layout(
        content = {
            // A closed card composes no rows of its own: the body comes back for the expand and stays
            // until the unfold is over, the way it did when this was an `AnimatedVisibility`.
            if (visible || progress > 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (progress < 1f) Modifier.alpha(progress) else Modifier),
                ) {
                    content()
                }
            }
        },
        modifier = modifier.clipToBounds(),
        measurePolicy = policy,
    )
}

/**
 * The silhouette of a pushed page: a squircle along the top edge, square along the bottom.
 *
 * A page stops short of the top of the screen but reaches the bottom of it, so only the top corners
 * are ever seen against the transcript behind. Rounding all four would notch the two bottom corners
 * against the very edge they are supposed to sit on.
 *
 * The path is built [cornerRadius] taller than the box it is applied to, which pushes the bottom
 * arcs outside the layer: clipped to the box, the silhouette has square bottom corners and the same
 * continuous top corners as [SquircleShape]. Immutable, for the same reason as [SquircleShape].
 */
@Immutable
class SheetShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val radius = with(density) { cornerRadius.toPx() }.coerceAtLeast(0f)
        val path = Path().apply {
            addSquircleRect(
                width = size.width,
                height = size.height + radius,
                cornerRadius = radius,
            )
        }
        return Outline.Generic(path)
    }
}

/**
 * How far a sheet stands off the sides of the window.
 *
 * Proportional, with a floor and a ceiling — see [UiConsts.SheetSideMarginFraction] for why a fixed
 * number of dp could not work. Every surface that slides up over the content reads this one
 * function, so a page and a panel cannot end up framed differently.
 */
@Composable
fun sheetSideMargin(): Dp {
    val width = LocalWindowInfo.current.containerDpSize.width
    return (width * UiConsts.SheetSideMarginFraction)
        .coerceIn(UiConsts.SheetSideMarginMin, UiConsts.SheetSideMarginMax)
}

/**
 * The frame every surface that slides up over the content is laid out in — a modal sheet and a
 * pushed page alike.
 *
 * Both used to carry their own copy of this geometry, and the copies had already drifted: the same
 * card stood 14dp off the screen edge as a sheet and 20dp as a page, stopped 58dp short of the top
 * in one case and not at all in the other, and carried a different shadow. Applying the metrics from
 * one place is what makes that impossible rather than merely fixed.
 *
 * It is two layers on purpose. The outer one carries the margin and the width ceiling; the inner one
 * is the panel itself, and it is the inner one a caller animates. Animating the outer layer would
 * move the margin too, so a sheet on a 1200dp window would appear to fly in from off to the side.
 *
 * @param shape silhouette of the panel.
 * @param tint fill of the panel.
 * @param elevation depth of its shadow.
 * @param maxWidth ceiling on the panel's width, before the side margin is taken off.
 * @param fillHeight whether the panel takes the whole height it is offered. A pushed page does — it
 *   is a page and reaches the bottom edge. A modal sheet does not: it is only as tall as what it has
 *   to show, up to the share of the window the caller allows it.
 * @param panel modifiers for the panel itself, which is where per-surface motion goes.
 */
@Composable
fun SheetFrame(
    shape: Shape,
    tint: Color,
    modifier: Modifier = Modifier,
    elevation: Dp = UiConsts.SheetElevation,
    maxWidth: Dp = UiConsts.SheetMaxWidth,
    fillHeight: Boolean = false,
    panel: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            // The ceiling comes *before* `fillMaxWidth` on purpose. Written the other way round the
            // `widthIn` had nothing left to constrain — `fillMaxWidth` had already claimed the whole
            // parent — and the cap silently did nothing, which is how a page ended up 1123dp wide on
            // a window whose sheets were capped at 1040.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(top = UiConsts.SheetTopGap)
                .padding(horizontal = sheetSideMargin()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                    .then(panel)
                    .floatingSurface(shape = shape, tint = tint, elevation = elevation)
                    .clip(shape),
            ) {
                content()
            }
        }
    }
}

/**
 * A modal sheet: the app's thin layer over miuix's [WindowBottomSheet].
 *
 * miuix owns the part that is hard to get right — a velocity-aware drag, a grabber that answers a
 * press, nested scroll so the list scrolls before the sheet starts to move, IME padding, and a
 * predictive-back gesture that drives the same exit as the drag. This used to be hand-rolled here,
 * and the hand-rolled version could only be dismissed by tapping the scrim: on a device whose only
 * navigation control is a gesture there was no way out of it that a thumb would find first.
 *
 * What is left for us is the sheet's *identity*: the geometric frame the pushed pages share
 * ([SheetFrame]'s numbers, applied through miuix's own [outsideMargin] / [insideMargin] /
 * [cornerRadius] / [sheetMaxWidth]), the header block, and the scrolling body.
 *
 * The caller owns `show` and must clear it in [onDismissFinished], not in [onDismissRequest]:
 * the first is called once the sheet has actually left, the second the moment the user asks for it
 * to leave. Clearing it in the wrong one cuts the exit animation off mid-slide.
 */
@Composable
fun ModalSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    maxHeightFraction: Float = UiConsts.SheetHeightFraction,
    allowDismiss: Boolean = true,
    onDismissFinished: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        title = null,
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
        dragHandleColor = colors.onSurfaceVariantSummary.copy(alpha = 0.4f),
        allowDismiss = allowDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LocalWindowInfo.current.containerDpSize.height * maxHeightFraction),
        ) {
            if (title != null || subtitle != null) {
                SheetHeader(title = title, subtitle = subtitle)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = UiConsts.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                content = content,
            )
        }
    }
}

/**
 * Title block of a sheet: what the sheet is, and the one line of state under it.
 *
 * No close button. The sheet is dismissed by the grabber, a drag, the scrim, the back gesture or
 * [LocalDismissState] from a row — a crossing-out in the corner would be a sixth way to say the
 * same thing, in the corner furthest from the thumb.
 */
@Composable
private fun SheetHeader(title: String?, subtitle: String?) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = UiConsts.Space2, bottom = UiConsts.Space10),
    ) {
        if (title != null) {
            Text(
                text = title,
                fontSize = UiType.SheetTitle,
                lineHeight = UiType.SheetTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Header row shared by the sidebar, the status card and the full-screen surfaces. */
@Composable
fun SurfaceHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 10.dp,
    leadingSpacing: Dp = 10.dp,
    trailingSpacing: Dp = 8.dp,
    titleFontSize: TextUnit = 18.sp,
    titleLineHeight: TextUnit = 24.sp,
    subtitleFontSize: TextUnit = 12.sp,
    subtitleLineHeight: TextUnit = 16.sp,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(leadingSpacing))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = titleFontSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = subtitleFontSize,
                    lineHeight = subtitleLineHeight,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(trailingSpacing))
            trailing()
        }
    }
}

/**
 * A labelled text input.
 *
 * The app had three hand-rolled ones — the rename field, the composer and the elicitation form —
 * with three different paddings and two different placeholder treatments. This is the one the
 * pages that are *forms* use: a settings value, a filter, a pairing code. The composer keeps its own
 * because it is not a field, it is the main input.
 *
 * @param label the field's name, above the box; `null` for a bare search box.
 * @param placeholder shown while [value] is empty.
 * @param onImeAction invoked by the keyboard's action key, for a field that submits.
 */
@Composable
fun CodexTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
            Spacer(Modifier.height(UiConsts.Space4))
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            label = placeholder.orEmpty(),
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            keyboardActions = KeyboardActions(
                onDone = { onImeAction?.invoke() },
                onGo = { onImeAction?.invoke() },
                onSend = { onImeAction?.invoke() },
            ),
        )
    }
}

/**
 * A switch with its own name and explanation, as a miuix [SwitchPreference].
 *
 * The library owns the row: the whole row is the hit target rather than the switch alone (a 40dp
 * control at the far edge of a phone is the worst place to have to aim), the explanation sits under
 * the title, and the press feedback is the same highlight every other miuix row uses. This used to
 * be a hand-rolled row that re-implemented those three things slightly differently from the
 * settings page, which was the one screen already using the real [SwitchPreference].
 */
@Composable
fun CodexSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        modifier = modifier,
        summary = subtitle,
        // The same inside margin the rows of a section card use, so the title aligns with the
        // value rows above and below it instead of inheriting the library's wider preference inset.
        insideMargin = PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        enabled = enabled,
    )
}

/**
 * A read-only fact, as a row inside a [SectionCard].
 *
 * [monospace] is for anything the user may have to copy out — a path, an id, a code — because a
 * proportional face makes `l` and `1` the same shape and a path with either in it stops being
 * checkable by eye.
 */
@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    tint: Color? = null,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = UiType.Meta,
            lineHeight = UiType.MetaLine,
            color = colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(UiConsts.Space10))
        Text(
            text = value.ifEmpty { "—" },
            modifier = Modifier.weight(1.4f),
            fontSize = UiType.Detail,
            lineHeight = UiType.DetailLine,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            color = tint ?: colors.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A row that does something: a page entry, a list item that opens a detail, a destructive action.
 *
 * It is a miuix [ArrowPreference]: the library owns the row height, the title/summary type ramp,
 * the end-slot alignment and the disclosure arrow. [trailing] is the row's *state* ("3 servers",
 * "已连接") and [subtitle] is its explanation — a row that only names a thing makes the user open
 * it to find out whether it is the thing they wanted. [icon] is the leading glyph, tinted like a
 * miuix preference row's start action.
 */
@Composable
fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = MiuixTheme.colorScheme
    ArrowPreference(
        title = title,
        modifier = modifier,
        summary = subtitle,
        startAction = if (icon == null) {
            null
        } else {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint = if (enabled) colors.primary else colors.disabledOnSurface,
                )
            }
        },
        endActions = {
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MiuixTheme.textStyles.body2,
                    // The end-slot role the miuix example gives preference end actions, so a
                    // trailing state reads the same here and in the settings page.
                    color = colors.onSurfaceVariantActions,
                    maxLines = 1,
                )
            }
        },
        // The same inside margin the rows of a section card use, so the title aligns with the
        // value rows above and below it instead of inheriting the library's wider preference inset.
        insideMargin = PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        onClick = onClick,
        enabled = enabled,
    )
}

/** Hairline between two rows of a card; the miuix divider with the row separation this app uses. */
@Composable
fun CodexDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(vertical = UiConsts.Space1))
}

/**
 * What a page shows when it has nothing to show.
 *
 * Says *why* it is empty, not just that it is: "还没有项目" and "无法连接服务器" look identical on a
 * blank page, and only one of them is worth retrying.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = UiConsts.Space24, horizontal = UiConsts.Space16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(UiConsts.IconBoxLarge)
                .squircleBackground(color = raisedSurface(), cornerRadius = UiConsts.CornerCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(UiConsts.IconHeader),
                tint = colors.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(UiConsts.Space12))
        Text(
            text = title,
            fontSize = UiType.RowTitle,
            lineHeight = UiType.RowTitleLine,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Spacer(Modifier.height(UiConsts.Space4))
            Text(
                text = detail,
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(UiConsts.Space16))
            action()
        }
    }
}

/** Back chevron used by every pushed page's header. */
@Composable
fun SurfaceBackButton(contentDescription: String, onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        minWidth = UiConsts.IconButtonSize,
        minHeight = UiConsts.IconButtonSize,
    ) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = contentDescription,
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}
