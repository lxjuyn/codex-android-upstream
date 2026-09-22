package com.cy.codex.runtime

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.cy.codex.CodexApp
import com.cy.codex.ComposerHistory
import com.cy.codex.NotificationSettings
import com.cy.codex.app.RecapSettings
import com.cy.codex.ensureAgentNotificationChannel
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.JsonRpcAppServerClient
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CodexApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Whether any activity is started.
     *
     * Notifications are gated on it the way the TUI gates on terminal focus
     * (`NotificationCondition::Unfocused`): a turn that completes while the user is looking at the
     * transcript needs no alert. Counting starts rather than using a single activity flag keeps a
     * configuration-change recreation from flickering the value.
     */
    var inForeground = false
        private set

    private val foregroundFlow = MutableStateFlow(false)

    /** Foreground/background transitions; the recap scheduler uses them as terminal focus. */
    val foreground: StateFlow<Boolean> = foregroundFlow.asStateFlow()
    val defaultWorkspace: String get() = File(filesDir, "workspaces/default").absolutePath
    val shellPath: String get() = File(filesDir, "runtime/toolchain/bin/bash").absolutePath
    val configPath: String get() = File(filesDir, "home/.codex/config.toml").absolutePath

    val client: AppServerClient by lazy {
        JsonRpcAppServerClient(
            NativeRpcTransport(this),
            appScope,
            defaultWorkspace,
            watchdogIntervalMs = JsonRpcAppServerClient.WatchdogIntervalMs,
        )
    }

    // Android owns this instance across Activity recreation; transcript and pending approvals
    // stay attached to the same native session while the process remains alive.
    val app: CodexApp by lazy {
        CodexApp(
            appScope,
            getSharedPreferences("codex", Context.MODE_PRIVATE),
            this,
            client,
            defaultWorkspace,
        )
    }

    override fun onCreate() {
        super.onCreate()
        NotificationSettings.load(this)
        RecapSettings.load(this)
        ComposerHistory.load(this)
        ensureAgentNotificationChannel(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0

            override fun onActivityStarted(activity: Activity) {
                started++
                inForeground = true
                foregroundFlow.value = true
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) {
                    inForeground = false
                    foregroundFlow.value = false
                }
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
