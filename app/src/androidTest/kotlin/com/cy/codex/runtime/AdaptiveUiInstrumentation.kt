package com.cy.codex.runtime

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.*
import com.cy.codex.MainActivity
import com.cy.codex.app.AgentPickerSheet
import com.cy.codex.app.AgentRole
import com.cy.codex.app.AgentRosterEntry
import com.cy.codex.app.FormField
import com.cy.codex.app.FormSheet
import com.cy.codex.bottom_pane.ApprovalDialog
import com.cy.codex.chatwidget.TrustProjectSheet
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalParams
import com.cy.codex.theme.CodexTheme
import top.yukonga.miuix.kmp.basic.Text

/** Device-only fixtures exercise production composables without a native server or credentials. */
internal fun Instrumentation.runAdaptiveUiFixture(options: Bundle) {
    try {
        val scene = options.getString("scene", "form")
        require(scene in setOf("form", "confirmation", "picker", "approval", "page"))
        val activity = startActivitySync(
            Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        runOnMainSync {
            if (scene == "page") {
                (activity.application as CodexApplication).app.openSurface(com.cy.codex.Surface.Settings)
            } else {
                activity.setContent { CodexTheme { Fixture(scene) } }
            }
        }
        waitForIdleSync()
        sendStatus(0, Bundle().apply { putString("stream", "READY $scene\n") })
        // Keep the fixture available for screenshots, keyboard, gestures, and resize checks.
        Thread.sleep(options.getString("holdSeconds", "180").toLong().coerceIn(1, 600) * 1000)
        finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "COMPLETED fixture $scene\n") })
    } catch (error: Throwable) {
        finish(Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", error.stackTraceToString())
            putString("shortMsg", error.message)
        })
    }
}

@Composable
private fun Fixture(scene: String) {
    var open by remember { mutableStateOf(true) }
    var decisions by remember { mutableIntStateOf(0) }
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(if (open) "UI fixture: $scene; decisions=$decisions" else "Dismissed: $scene; decisions=$decisions")
    }
    LaunchedEffect(open, decisions) { android.util.Log.i("AdaptiveUiFixture", "$scene open=$open decisions=$decisions") }
    when (scene) {
        "form" -> if (open) FormSheet(
            title = "Project form fixture",
            fields = (1..8).map { FormField("field$it", "Field $it", initial = if (it == 1) "" else "Value $it") },
            confirmLabel = "Save fixture",
            onDismiss = { open = false },
            onSubmit = { decisions++; open = false },
        )
        "confirmation" -> if (open) TrustProjectSheet(
            path = "/workspace/ui-fixture",
            onTrust = { decisions++; open = false },
            onDismiss = { open = false },
        )
        "picker" -> AgentPickerSheet(
            show = open,
            roster = (1..15).map {
                AgentRosterEntry("thread$it", "Agent $it", AgentRole.Sub, null, null,
                    "Review layout $it", null, null, itemId = null)
            },
            selectedThreadId = "thread1",
            onSelect = { decisions++; open = false },
            onDismiss = { open = false },
            onDismissFinished = {},
        )
        "approval" -> {
            val request = remember(decisions) {
                ApprovalRequest.Exec(
                    RequestId("fixture-$decisions"), "fixture", "turn", "item", 0,
                    CommandExecutionApprovalParams(
                        "fixture", "turn", "item", command = (1..30).joinToString("\n") { "echo review-line-$it" },
                        cwd = "/workspace/ui-fixture", reason = "Offline UI fixture: no command is executed",
                    ),
                )
            }
            ApprovalDialog(
                request = if (open) request else null,
                remainingQueue = if (decisions == 0) 1 else 0,
                onDecision = { _, _ -> decisions++; if (decisions == 2) open = false },
            )
        }
    }
}
