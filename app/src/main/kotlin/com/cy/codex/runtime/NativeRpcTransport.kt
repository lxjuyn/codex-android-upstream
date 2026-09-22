package com.cy.codex.runtime

import android.content.Context
import android.system.Os
import com.cy.codex.protocol.JsonRpcMessageKind
import com.cy.codex.protocol.JsonRpcTransport
import com.cy.codex.protocol.protocol.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NativeRpcTransport(context: Context) : JsonRpcTransport {
    private val context = context.applicationContext
    private val lifecycle = Mutex()
    @Volatile private var handle = 0L

    override suspend fun start() = withContext(Dispatchers.IO) {
        lifecycle.withLock {
            if (handle != 0L) return@withLock
            val installation = ToolchainInstaller(context).install()
            NativeEnvironment.install(installation.environment)
            NativeBridge.load()
            val config = buildJsonObject {
                put("env", JsonObject(installation.environment.mapValues { JsonPrimitive(it.value) }))
                put("cwd", installation.workspace.absolutePath)
                put("codexHome", installation.codexHome.absolutePath)
                put("nativeLibraryDir", context.applicationInfo.nativeLibraryDir)
                put("codexSelfExe", "${context.applicationInfo.nativeLibraryDir}/libcodex_helper.so")
                put("toolchainRoot", installation.root.absolutePath)
                put("shellPath", "${installation.root.absolutePath}/bin/bash")
            }
            handle = NativeBridge.nativeStart(Json.write(config).encodeToByteArray()).also {
                check(it != 0L) { "Codex returned an invalid native session." }
            }
        }
    }

    override suspend fun send(kind: JsonRpcMessageKind, message: String) = withContext(Dispatchers.IO) {
        val active = handle
        check(active != 0L) { "Codex is not connected." }
        NativeBridge.nativeSend(active, kind.code, message.encodeToByteArray())
    }

    override suspend fun receive(): String? = withContext(Dispatchers.IO) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val active = handle
            if (active == 0L) return@withContext null
            NativeBridge.nativeReceive(active, 250)?.let { return@withContext it.decodeToString() }
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        lifecycle.withLock {
            val active = handle
            handle = 0L
            if (active != 0L) NativeBridge.nativeStop(active)
        }
    }
}

private object NativeEnvironment {
    private var installed: Map<String, String>? = null

    @Synchronized
    fun install(environment: Map<String, String>) {
        val previous = installed
        if (previous != null) {
            check(previous == environment) { "Restart the app to change the native environment." }
            return
        }
        // Use Android's platform API before loading Rust or starting native workers.
        environment.forEach { (key, value) -> Os.setenv(key, value, true) }
        installed = environment.toMap()
    }
}
