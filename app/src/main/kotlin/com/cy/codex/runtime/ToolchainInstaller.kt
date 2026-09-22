package com.cy.codex.runtime

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest

data class RuntimeInstallation(
    val root: File,
    val workspace: File,
    val codexHome: File,
    val environment: Map<String, String>,
)

class ToolchainInstaller(private val context: Context) {
    fun install(): RuntimeInstallation {
        val manifest = context.assets.open("toolchain/native-manifest.txt").bufferedReader().use { it.readText() }
        val entries = parseNativeManifest(manifest)
        val nativeDirectory = File(context.applicationInfo.nativeLibraryDir)
        val root = File(context.filesDir, "runtime/toolchain")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val stamp = MessageDigest.getInstance("SHA-256")
            .digest("$manifest\n${packageInfo.lastUpdateTime}\n$nativeDirectory".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val stampFile = File(root, ".installation")

        if (!stampFile.isFile || stampFile.readText() != stamp) {
            val staging = File(root.parentFile, "toolchain.staging")
            staging.deleteRecursively()
            check(staging.mkdirs()) { "Cannot create the toolchain staging directory." }
            for ((kind, relative, target) in entries) {
                val destination = File(staging, relative)
                check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs()) {
                    "Cannot create the runtime directory for $relative"
                }
                when (kind) {
                    "data" -> context.assets.open("toolchain/$relative").use { source ->
                        destination.outputStream().use { source.copyTo(it) }
                    }
                    "file" -> {
                        val library = File(nativeDirectory, target)
                        check(library.isFile) { "Native tool is missing from the APK: $target" }
                        Os.symlink(library.absolutePath, destination.absolutePath)
                    }
                    "link" -> Os.symlink(target, destination.absolutePath)
                }
            }
            val helper = File(nativeDirectory, "libcodex_helper.so")
            check(helper.isFile) { "Codex executable helper is missing from the APK." }
            Os.symlink(helper.absolutePath, File(staging, "bin/apply_patch").absolutePath)
            File(staging, ".installation").writeText(stamp)
            val previous = File(root.parentFile, "toolchain.previous")
            previous.deleteRecursively()
            if (root.exists()) check(root.renameTo(previous)) { "Cannot replace the installed toolchain." }
            if (!staging.renameTo(root)) {
                previous.renameTo(root)
                error("Cannot activate the installed toolchain.")
            }
            previous.deleteRecursively()
        }

        val home = directory("home")
        val codexHome = directory("home/.codex")
        Os.chmod(home.path, 0x1C0)
        Os.chmod(codexHome.path, 0x1C0)
        initializeConfig(codexHome)
        val temporary = directory("tmp")
        val workspace = directory("workspaces/default")
        val configHome = directory("home/.config")
        val dataHome = directory("home/.local/share")
        val stateHome = directory("home/.local/state")
        val cache = File(context.cacheDir, "toolchain").apply {
            check(isDirectory || mkdirs()) { "Cannot create the toolchain cache directory." }
        }
        directory("home/.codex/log")
        val certificates = File(root, "share/cacert.pem")
        check(certificates.isFile) { "Toolchain CA certificate bundle is missing." }
        check(File(root, "bin/bash").canExecute()) { "Packaged bash is not executable." }
        return RuntimeInstallation(root, workspace, codexHome, mapOf(
            "PATH" to "${root.path}/bin:${root.path}/sbin",
            "HOME" to home.path,
            "CODEX_HOME" to codexHome.path,
            "CODEX_SQLITE_HOME" to codexHome.path,
            "SHELL" to "${root.path}/bin/bash",
            "CODEX_SHELL" to "${root.path}/bin/bash",
            "TMPDIR" to temporary.path,
            "TMP" to temporary.path,
            "TEMP" to temporary.path,
            "XDG_CONFIG_HOME" to configHome.path,
            "XDG_DATA_HOME" to dataHome.path,
            "XDG_STATE_HOME" to stateHome.path,
            "XDG_CACHE_HOME" to cache.path,
            "UV_CACHE_DIR" to File(cache, "uv").path,
            "PIP_CACHE_DIR" to File(cache, "pip").path,
            "BUN_INSTALL_CACHE_DIR" to File(cache, "bun").path,
            "GIT_EXEC_PATH" to "${root.path}/libexec/git-core",
            "GIT_TEMPLATE_DIR" to "${root.path}/share/git-core/templates",
            "GIT_SSL_CAINFO" to certificates.path,
            "CURL_CA_BUNDLE" to certificates.path,
            "SSL_CERT_FILE" to certificates.path,
            "PYTHONHOME" to root.path,
            "PYTHONDONTWRITEBYTECODE" to "1",
            "MAGIC" to "${root.path}/share/misc/magic",
            "LANG" to "C.UTF-8",
            "TERM" to "xterm-256color",
        ))
    }

    private fun initializeConfig(codexHome: File) {
        val config = File(codexHome, "config.toml")
        if (config.exists()) {
            check(config.isFile) { "CODEX_HOME/config.toml is not a regular file." }
            return
        }
        val atomic = AtomicFile(config)
        val output = atomic.startWrite()
        try {
            context.assets.open("runtime/default-config.toml").use { it.copyTo(output) }
            atomic.finishWrite(output)
            Os.chmod(config.path, 0x180)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun directory(relative: String): File = File(context.filesDir, relative).also {
        check(it.isDirectory || it.mkdirs()) { "Cannot create private directory: $relative" }
    }
}
