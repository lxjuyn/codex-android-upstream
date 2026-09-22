package com.cy.codex.theme

import androidx.compose.animation.core.Animatable
import com.cy.codex.Motion
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Miuix style press highlight, but drawn as an inset rounded rect instead of a full-bleed
 * rectangle so it follows the rounded silhouette of floating surfaces and popup rows.
 */
class RoundedIndication(
    private val color: Color,
    private val radius: Dp = 16.dp,
    private val inset: Dp = 4.dp,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource)

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + radius.hashCode()
        result = 31 * result + inset.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoundedIndication) return false
        return color == other.color && radius == other.radius && inset == other.inset
    }

    private inner class Node(
        private val interactionSource: InteractionSource,
    ) : Modifier.Node(), DrawModifierNode {

        private val alpha = Animatable(0f)

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    val target = when (interaction) {
                        is PressInteraction.Press -> 0.1f
                        is HoverInteraction.Enter, is FocusInteraction.Focus -> 0.06f
                        is PressInteraction.Release, is PressInteraction.Cancel,
                        is HoverInteraction.Exit, is FocusInteraction.Unfocus,
                        -> 0f

                        else -> return@collect
                    }
                    alpha.animateTo(target, Motion.Panel)
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val alphaValue = alpha.value
            if (alphaValue <= 0.001f) return
            val insetPx = inset.toPx()
            val width = size.width - insetPx * 2f
            val height = size.height - insetPx * 2f
            if (width <= 0f || height <= 0f) return
            drawRoundRect(
                color = color,
                topLeft = Offset(insetPx, insetPx),
                size = Size(width, height),
                cornerRadius = CornerRadius(radius.toPx().coerceAtMost(minOf(width, height) / 2f)),
                alpha = alphaValue,
            )
        }
    }
}
