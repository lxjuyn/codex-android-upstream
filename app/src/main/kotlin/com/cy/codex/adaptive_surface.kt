package com.cy.codex

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cy.codex.theme.HideStatusBarInWindow
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

private val LocalSurfaceHeightFraction = compositionLocalOf { UiConsts.SheetHeightFraction }

@Composable
fun sheetHeightFraction(): Float = LocalSurfaceHeightFraction.current

/** Retains caller-owned decisions and state while adapting only the presentation. */
@Composable
fun AdaptiveSurface(
    purpose: SurfacePurpose,
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    title: String? = null,
    allowDismiss: Boolean = true,
    tall: Boolean = false,
    content: @Composable () -> Unit,
) {
    val geometry = surfaceGeometryFor(LocalWindowInfo.current.containerDpSize.width, purpose, tall)
    CompositionLocalProvider(LocalSurfaceHeightFraction provides geometry.heightFraction) {
        when (geometry.presentation) {
            SurfacePresentation.BottomDrawer -> WindowBottomSheet(
                show = show,
                title = title,
                allowDismiss = allowDismiss,
                onDismissRequest = onDismissRequest,
                onDismissFinished = onDismissFinished,
                backgroundColor = sheetColor(),
                cornerRadius = geometry.corner,
                sheetMaxWidth = UiConsts.SheetMaxWidth,
                outsideMargin = DpSize(geometry.sideMargin, 0.dp),
                insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
                content = content,
            )
            SurfacePresentation.CenteredCard -> WindowDialog(
                show = show,
                title = title,
                onDismissRequest = if (allowDismiss) onDismissRequest else null,
                onDismissFinished = onDismissFinished,
                largeScreen = true,
                backgroundColor = sheetColor(),
                cornerRadius = geometry.corner,
                outsideMargin = DpSize(geometry.sideMargin, 24.dp),
                insideMargin = DpSize(UiConsts.SheetPadding, UiConsts.SheetPadding),
                content = content,
            )
            SurfacePresentation.FullPage -> {
                // Unlike animated sheets, a full page leaves immediately when show becomes false.
                var wasShown by remember { mutableStateOf(false) }
                val finished by rememberUpdatedState(onDismissFinished)
                LaunchedEffect(show) {
                    if (wasShown && !show) finished?.invoke()
                    wasShown = show
                }
                if (show) Dialog(
                    onDismissRequest = { if (allowDismiss) onDismissRequest() },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                        dismissOnBackPress = allowDismiss,
                        dismissOnClickOutside = false,
                    ),
                ) {
                    HideStatusBarInWindow()
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape,
                        color = sheetColor(),
                    ) {
                        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                            if (title != null || allowDismiss) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = UiConsts.SheetPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (allowDismiss) TextButton(
                                        text = stringResource(R.string.adaptive_surface_close),
                                        onClick = onDismissRequest,
                                    )
                                    if (title != null) Text(
                                        text = title,
                                        modifier = Modifier.weight(1f).padding(12.dp),
                                        fontSize = UiType.SheetBody,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            CompositionLocalProvider(
                                LocalDismissState provides if (allowDismiss) onDismissRequest else null,
                            ) {
                                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = UiConsts.SheetPadding)) {
                                    content()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
