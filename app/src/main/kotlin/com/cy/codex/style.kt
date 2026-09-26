package com.cy.codex

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One window breakpoint shared by page frames and purpose-specific modal surfaces. */
enum class ShellWidth {
    Compact,
    Expanded,
}

@Composable
fun shellWidth(): ShellWidth = shellWidthFor(LocalWindowInfo.current.containerDpSize.width)

/** The breakpoint itself, without a composition, so the rule can be tested directly. */
fun shellWidthFor(width: Dp): ShellWidth =
    if (width >= UiConsts.WideContentBreakpoint) ShellWidth.Expanded else ShellWidth.Compact

/** Keeps miuix sheets and pushed pages aligned across window sizes. */
@Composable
fun sheetSideMargin(): Dp =
    sheetSideMarginFor(
        width = LocalWindowInfo.current.containerDpSize.width,
        shellWidth = shellWidth(),
    )

/**
 * The gutter a sheet or page leaves at each side of [width].
 *
 * A phone gets none. The proportional gutter clamps to its 40dp minimum long before the window is
 * narrow, so on a 360dp screen a page would be 280dp wide with dead bands either side — the
 * floating-card look this exists to avoid.
 */
fun sheetSideMarginFor(width: Dp, shellWidth: ShellWidth): Dp =
    when (shellWidth) {
        ShellWidth.Compact -> 0.dp
        ShellWidth.Expanded ->
            (width * UiConsts.SheetSideMarginFraction).coerceIn(
                UiConsts.SheetSideMarginMin,
                UiConsts.SheetSideMarginMax,
            )
    }

enum class SurfacePurpose { Approval, Form, Details, Picker, Confirmation, ShortForm }

enum class SurfacePresentation { FullPage, BottomDrawer, CenteredCard }

data class SurfaceGeometry(
    val presentation: SurfacePresentation,
    val sideMargin: Dp,
    val corner: Dp,
    val heightFraction: Float,
)

fun pageTopGapFor(width: ShellWidth): Dp =
    if (width == ShellWidth.Compact) 0.dp else UiConsts.SheetTopGap

fun pageCornerFor(width: ShellWidth): Dp =
    if (width == ShellWidth.Compact) 0.dp else UiConsts.DrawerCorner

/** Full-page approvals can spend more height on evidence while retaining room for decisions. */
fun approvalBodyHeightFractionFor(width: ShellWidth): Float =
    if (width == ShellWidth.Compact) 0.70f else UiConsts.DialogBodyMaxHeightFraction

/** Purpose is explicit so new call sites cannot accidentally turn confirmations into pages. */
fun surfaceGeometryFor(
    width: Dp,
    purpose: SurfacePurpose,
    tall: Boolean = false,
): SurfaceGeometry {
    val shell = shellWidthFor(width)
    if (shell == ShellWidth.Expanded) {
        return SurfaceGeometry(
            SurfacePresentation.BottomDrawer,
            sheetSideMarginFor(width, shell),
            UiConsts.SheetCorner,
            if (tall) UiConsts.SheetHeightFractionTall else UiConsts.SheetHeightFraction,
        )
    }
    return when (purpose) {
        SurfacePurpose.Approval, SurfacePurpose.Form, SurfacePurpose.Details ->
            SurfaceGeometry(SurfacePresentation.FullPage, 0.dp, 0.dp, 1f)
        SurfacePurpose.Picker ->
            SurfaceGeometry(SurfacePresentation.BottomDrawer, 0.dp, UiConsts.SheetCorner, 0.85f)
        SurfacePurpose.Confirmation, SurfacePurpose.ShortForm ->
            SurfaceGeometry(SurfacePresentation.CenteredCard, 20.dp, UiConsts.SheetCorner, 0.72f)
    }
}
