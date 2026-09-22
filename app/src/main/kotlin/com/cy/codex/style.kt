package com.cy.codex

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/** Keeps miuix sheets and pushed pages aligned across window sizes. */
@Composable
fun sheetSideMargin(): Dp {
    val width = LocalWindowInfo.current.containerDpSize.width
    return (width * UiConsts.SheetSideMarginFraction).coerceIn(
        UiConsts.SheetSideMarginMin,
        UiConsts.SheetSideMarginMax,
    )
}
