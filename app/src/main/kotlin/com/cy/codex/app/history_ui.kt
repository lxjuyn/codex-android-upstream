package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.AppEvent
import com.cy.codex.CodexApp
import com.cy.codex.R
import com.cy.codex.Surface
import com.cy.codex.UiConsts
import com.cy.codex.chatwidget.Transcript
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The Ctrl+T transcript overlay, mirroring `app_backtrack.rs` `open_transcript_overlay`.
 *
 * Upstream the overlay is the committed transcript cells plus a live tail, paging older history
 * with `thread/items/list`; there is no separate "history browser" in the TUI — `Ctrl+T` and the
 * transcript are the same view, one of them read-only. This page is that view: the open thread's
 * own items, rendered with the same cells, minus the composer and status chrome. It never reads a
 * different thread and has nothing to search, so the old search/timeline browser is gone.
 */
@Composable
fun ThreadHistoryScreen(app: CodexApp, onBack: () -> Unit) {
    val session = app.widget.state
    Column(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        BasicComponent(
            title = stringResource(R.string.history_ui_title),
            summary = session.config.displayName,
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.history_ui_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
        )
        Transcript(
            items = session.items,
            diagnostics = session.diagnostics,
            // Nothing streams into an overlay: the live transcript behind it owns the stream, and
            // this copy is a snapshot of the same items, so no row is ever the streaming one.
            isStreaming = { false },
            streamFor = { null },
            plan = session.plan,
            loading = false,
            empty = session.items.isEmpty() && session.diagnostics.isEmpty(),
            cwd = session.config.cwd,
            onOpenAgent = { threadId -> app.openSurface(Surface.SubAgentThread(threadId)) },
            onOpenAgentInfo = { threadId -> app.openSurface(Surface.SubAgent(threadId)) },
            onAnswerQuestion = { text -> app.onAppEvent(AppEvent.AnswerAsyncQuestion(text)) },
            canLoadEarlier = app.widget.canLoadEarlier,
            loadingEarlier = app.widget.loadingEarlier,
            onLoadEarlier = app.widget::loadEarlier,
            contentPadding =
                PaddingValues(
                    start = UiConsts.TranscriptGutter,
                    end = UiConsts.TranscriptGutter,
                    top = UiConsts.Space8,
                    bottom = UiConsts.PageBottomInset,
                ),
        )
    }
}
