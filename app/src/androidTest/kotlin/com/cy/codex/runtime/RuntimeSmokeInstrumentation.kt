package com.cy.codex.runtime

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.cy.codex.protocol.JsonRpcAppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.v2.ClientInfo
import com.cy.codex.protocol.protocol.v2.ThreadReadParams
import com.cy.codex.protocol.protocol.v2.ThreadResumeParams
import com.cy.codex.protocol.protocol.v2.ThreadStartParams
import com.cy.codex.protocol.protocol.v2.TurnStatus
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Exercises the production client and JNI inside the real Android application sandbox. */
class RuntimeSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val report = StringBuilder()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runBlocking {
                withTimeout(180_000) {
                    val installation = ToolchainInstaller(targetContext).install()
                    val configFile = File(installation.codexHome, "config.toml")
                    check(configFile.isFile) { "Persistent configuration was not initialized" }
                    val originalConfig = configFile.readBytes()
                    val customizedConfig = originalConfig + "\n# runtime-smoke-preserve-user-config\n".toByteArray()
                    try {
                        configFile.writeBytes(customizedConfig)
                        ToolchainInstaller(targetContext).install()
                        check(configFile.readBytes().contentEquals(customizedConfig)) {
                            "Runtime preparation overwrote an existing user configuration"
                        }
                    } finally {
                        configFile.writeBytes(originalConfig)
                    }
                    report.appendLine("PASS persistent private config and idempotent installation")
                    val client = JsonRpcAppServerClient(
                        NativeRpcTransport(targetContext), scope, installation.workspace.path,
                    )
                    try {
                        client.initialize(ClientInfo("codex_android_smoke", "Codex Android Test", "1.0"))
                            .getOrThrow()
                        report.appendLine("PASS embedded app-server initialization")
                        client.readAccount().getOrThrow()
                        client.readConfig(installation.workspace.path).getOrThrow()
                        client.listThreads().getOrThrow()
                        check(client.listModels().getOrThrow().isNotEmpty()) { "Empty model catalog" }
                        report.appendLine("PASS account/config/thread/model RPC decoding")

                        val testDirectory = File(installation.workspace, ".runtime-smoke-${System.nanoTime()}")
                        client.createDirectory(testDirectory.path).getOrThrow()
                        try {
                            val file = File(testDirectory, "roundtrip.txt")
                            val bytes = "Codex Android JNI roundtrip\n".toByteArray()
                            client.writeFile(file.path, bytes).getOrThrow()
                            check(client.readFile(file.path).getOrThrow().contentEquals(bytes))
                            check(client.readDirectory(testDirectory.path).getOrThrow().isNotEmpty())
                            report.appendLine("PASS fs create/write/read/list")

                            val command = client.execCommand(
                                listOf("${installation.root.path}/bin/bash", "-c",
                                    "set -e; git --version; rg --version; " +
                                        "python3 -c 'import ssl,sqlite3; print(\"python-ok\")'; " +
                                        // `test -r` is not usable for app-private files on Android:
                                        // access(2) returns EACCES even for the owning UID (SELinux),
                                        // while an actual open succeeds. Open the bundle instead.
                                        "bun --version; head -c 1 \"\$GIT_SSL_CAINFO\" >/dev/null; " +
                                        "printf 'toolchain-ok\\n'"),
                                cwd = testDirectory.path,
                                timeoutMs = 30_000,
                            ).getOrThrow()
                            check(command.exitCode == 0) { "Command failed: ${command.stderr}" }
                            check("python-ok" in command.stdout && "toolchain-ok" in command.stdout)
                            report.appendLine("PASS command/exec with bash/git/rg/python/bun and CA bundle")

                            val patch = "*** Begin Patch\n*** Add File: helper.txt\n+android helper ok\n*** End Patch"
                            val patched = client.execCommand(
                                listOf("${installation.root.path}/bin/apply_patch", patch),
                                cwd = testDirectory.path,
                                timeoutMs = 10_000,
                            ).getOrThrow()
                            check(patched.exitCode == 0) { "apply_patch helper failed: ${patched.stderr}" }
                            check(client.readFile(File(testDirectory, "helper.txt").path).getOrThrow()
                                .decodeToString() == "android helper ok\n")
                            report.appendLine("PASS executable apply_patch helper")
                        } finally {
                            client.removePath(testDirectory.path, recursive = true).getOrThrow()
                        }

                        val thread = client.startThread(
                            ThreadStartParams(cwd = installation.workspace.path),
                        ).getOrThrow()
                        check(thread.threadId.isNotBlank())
                        check(thread.cwd == installation.workspace.path)
                        report.appendLine("PASS thread/start")
                        val completed = async(start = CoroutineStart.UNDISPATCHED) {
                            client.events.first {
                                it is AppServerEvent.TurnCompleted && it.threadId == thread.threadId
                            } as AppServerEvent.TurnCompleted
                        }
                        client.runShellCommand(thread.threadId,
                            "test -n \"\$BASH_VERSION\" && command -v git && printf 'thread-shell-ok\\n'",
                        ).getOrThrow()
                        check(completed.await().status == TurnStatus.Completed) { "Shell turn failed" }
                        val history = client.readThread(
                            ThreadReadParams(thread.threadId),
                        ).getOrThrow()
                        check(history.items.filterIsInstance<CommandExecutionItem>().any {
                            it.exitCode == 0 && it.aggregatedOutput.orEmpty().contains("thread-shell-ok")
                        }) { "Shell output was not present in the real transcript" }
                        report.appendLine("PASS streamed shell turn and transcript")
                        client.unsubscribeThread(thread.threadId).getOrThrow()
                        client.close()
                        client.initialize(ClientInfo("codex_android_smoke", "Codex Android Test", "1.0"))
                            .getOrThrow()
                        // Upstream thread/list excludes shell-only threads with no chat preview.
                        // Read and resume by ID to verify their persisted transcript directly.
                        val restored = client.readThread(
                            ThreadReadParams(thread.threadId),
                        ).getOrThrow()
                        check(restored.thread.id == thread.threadId)
                        check(restored.items.filterIsInstance<CommandExecutionItem>().any {
                            it.exitCode == 0 && it.aggregatedOutput.orEmpty().contains("thread-shell-ok")
                        }) { "Persisted shell transcript disappeared after restarting the native server" }
                        check(client.resumeThread(ThreadResumeParams(thread.threadId)).getOrThrow().threadId == thread.threadId)
                        report.appendLine("PASS native restart and thread resume")
                        client.unsubscribeThread(thread.threadId).getOrThrow()
                        client.deleteThread(thread.threadId).getOrThrow()
                    } finally {
                        client.close()
                    }
                }
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("stream", report.appendLine("FAIL ${error.stackTraceToString()}").toString())
                putString("shortMsg", error.message)
            })
        } finally {
            scope.cancel()
        }
    }
}
