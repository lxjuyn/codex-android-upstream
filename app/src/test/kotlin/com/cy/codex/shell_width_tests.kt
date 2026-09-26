package com.cy.codex

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Locks the phone/tablet rule the whole shell reads through `shellWidth()` / `sheetSideMargin()`. */
class ShellWidthTest {
    @Test
    fun `full-page approvals use more evidence space without changing tablet approvals`() {
        assertEquals(0.70f, approvalBodyHeightFractionFor(ShellWidth.Compact))
        assertEquals(UiConsts.DialogBodyMaxHeightFraction, approvalBodyHeightFractionFor(ShellWidth.Expanded))
    }
    @Test
    fun `compact pages remove the frame and expanded pages retain it`() {
        assertEquals(0.dp, pageTopGapFor(ShellWidth.Compact))
        assertEquals(0.dp, pageCornerFor(ShellWidth.Compact))
        assertEquals(UiConsts.SheetTopGap, pageTopGapFor(ShellWidth.Expanded))
        assertEquals(UiConsts.DrawerCorner, pageCornerFor(ShellWidth.Expanded))
    }

    @Test
    fun `reading and editing use the entire compact window`() {
        for (purpose in listOf(SurfacePurpose.Approval, SurfacePurpose.Form, SurfacePurpose.Details)) {
            for (width in listOf(320.dp, 360.dp, 600.dp, 719.dp)) {
                val geometry = surfaceGeometryFor(width, purpose)
                assertEquals(SurfacePresentation.FullPage, geometry.presentation)
                assertEquals(0.dp, geometry.sideMargin)
                assertEquals(0.dp, geometry.corner)
                assertEquals(1f, geometry.heightFraction)
            }
        }
    }

    @Test
    fun `temporary choices keep context and short decisions stay centered`() {
        val picker = surfaceGeometryFor(360.dp, SurfacePurpose.Picker)
        assertEquals(SurfacePresentation.BottomDrawer, picker.presentation)
        assertEquals(0.dp, picker.sideMargin)
        assertEquals(0.85f, picker.heightFraction)
        assertEquals(UiConsts.SheetCorner, picker.corner)
        for (purpose in listOf(SurfacePurpose.Confirmation, SurfacePurpose.ShortForm)) {
            val card = surfaceGeometryFor(360.dp, purpose)
            assertEquals(SurfacePresentation.CenteredCard, card.presentation)
            assertEquals(20.dp, card.sideMargin)
            assertEquals(UiConsts.SheetCorner, card.corner)
            assertEquals(0.72f, card.heightFraction)
        }
    }

    @Test
    fun `all purposes preserve expanded geometry including the tall overview`() {
        for (purpose in SurfacePurpose.entries) {
            for (width in listOf(720.dp, 1280.dp, 2000.dp)) {
                val geometry = surfaceGeometryFor(width, purpose)
                assertEquals(SurfacePresentation.BottomDrawer, geometry.presentation)
                assertEquals(sheetSideMarginFor(width, ShellWidth.Expanded), geometry.sideMargin)
                assertEquals(UiConsts.SheetCorner, geometry.corner)
                assertEquals(UiConsts.SheetHeightFraction, geometry.heightFraction)
                assertEquals(UiConsts.SheetHeightFractionTall, surfaceGeometryFor(width, purpose, tall = true).heightFraction)
            }
        }
    }

    @Test
    fun `the breakpoint is the same 720dp the transcript column already uses`() {
        assertEquals(ShellWidth.Compact, shellWidthFor(360.dp))
        assertEquals(ShellWidth.Compact, shellWidthFor(719.dp))
        assertEquals(ShellWidth.Expanded, shellWidthFor(720.dp))
        assertEquals(ShellWidth.Expanded, shellWidthFor(1280.dp))
    }

    @Test
    fun `a phone keeps no gutter`() {
        // The proportional rule would clamp to its 40dp floor here, which on a 360dp screen is a
        // page 280dp wide with dead bands either side — the floating-card look this rule avoids.
        assertEquals(0.dp, sheetSideMarginFor(width = 360.dp, shellWidth = ShellWidth.Compact))
        assertEquals(0.dp, sheetSideMarginFor(width = 719.dp, shellWidth = ShellWidth.Compact))
    }

    @Test
    fun `a tablet gets the proportional gutter, clamped at both ends`() {
        // 1280 * 0.055 = 70.4, inside the clamp.
        val proportional =
            sheetSideMarginFor(width = 1280.dp, shellWidth = ShellWidth.Expanded)
        assertTrue(
            proportional.value > 70f && proportional.value < 71f,
            "expected ~70.4dp, got $proportional",
        )
        // 720 * 0.055 = 39.6, under the 40dp floor.
        assertEquals(40.dp, sheetSideMarginFor(width = 720.dp, shellWidth = ShellWidth.Expanded))
        // 4000 * 0.055 = 220, over the 72dp ceiling.
        assertEquals(72.dp, sheetSideMarginFor(width = 4000.dp, shellWidth = ShellWidth.Expanded))
    }
}
