package codex.native

import codex.toolchain.Fingerprint
import codex.toolchain.NdkEnv
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/**
 * Creates `native/build-upstream/` from the pinned codex submodule revision and
 * applies `native/patches/android-runtime.patch` there; the submodule itself
 * stays untouched. Mirrors the old `prepare-upstream.sh`, minus the shell.
 */
abstract class PrepareUpstreamTask : DefaultTask() {
    @get:Internal abstract val codexDirPath: Property<String>
    @get:Internal abstract val worktreeDirPath: Property<String>
    @get:Internal abstract val patchFilePath: Property<String>
    @get:Internal abstract val markerPath: Property<String>
    @get:Internal abstract val skip: Property<Boolean>
    @get:Inject abstract val execOps: ExecOperations

    @TaskAction
    fun prepare() {
        if (skip.get()) return
        val codex = File(codexDirPath.get())
        val worktree = File(worktreeDirPath.get())
        val patch = File(patchFilePath.get())
        if (!File(codex, ".git").exists()) {
            throw GradleException("codex submodule is not initialised; run: git submodule update --init codex")
        }
        val revision = capture(codex, "git", "-C", codex.absolutePath, "rev-parse", "HEAD").trim()
        val applied = File(worktree, ".codex-android-applied.patch")
        val marker = File(markerPath.get())
        val fingerprint = Fingerprint.sha256(revision + "|" + Fingerprint.patches(execOps, patch.parentFile))
        if (marker.isFile && marker.readText() == fingerprint && worktree.isDirectory) {
            logger.lifecycle("codex worktree up to date at $revision")
            return
        }
        if (!worktree.isDirectory) {
            exec(ignoreFailure = true, workdir = codex, args = listOf("git", "worktree", "prune", "--expire", "now"))
            exec(workdir = codex, args = listOf("git", "worktree", "add", "--detach", worktree.absolutePath, revision))
        }
        if (capture(worktree, "git", "-C", worktree.absolutePath, "rev-parse", "HEAD").trim() != revision) {
            if (applied.isFile) {
                exec(ignoreFailure = true, workdir = worktree, args = listOf("git", "apply", "--reverse", applied.absolutePath))
                applied.delete()
            }
            exec(workdir = worktree, args = listOf("git", "checkout", "--detach", revision))
        }
        val alreadyApplied = run(
            workdir = worktree,
            args = listOf("git", "apply", "--reverse", "--check", patch.absolutePath),
        ) == 0
        if (!alreadyApplied) {
            if (applied.isFile) {
                exec(ignoreFailure = true, workdir = worktree, args = listOf("git", "apply", "--reverse", applied.absolutePath))
            }
            exec(workdir = worktree, args = listOf("git", "apply", "--check", patch.absolutePath))
            exec(workdir = worktree, args = listOf("git", "apply", patch.absolutePath))
        }
        Files.copy(patch.toPath(), applied.toPath(), StandardCopyOption.REPLACE_EXISTING)
        marker.parentFile?.mkdirs()
        marker.writeText(fingerprint)
    }

    private fun capture(workdir: File, vararg argv: String): String {
        val output = ByteArrayOutputStream()
        val result = execOps.exec {
            commandLine(argv.toList())
            workingDir(workdir)
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) throw GradleException("${argv.joinToString(" ")} failed: $output")
        return output.toString()
    }

    private fun exec(workdir: File, args: List<String>, ignoreFailure: Boolean = false) {
        val result = run(workdir, args)
        if (result != 0 && !ignoreFailure) throw GradleException("${args.joinToString(" ")} failed ($result)")
    }

    private fun run(workdir: File, args: List<String>): Int = execOps.exec {
        commandLine(args)
        workingDir(workdir)
        isIgnoreExitValue = true
    }.exitValue
}

/**
 * Builds the generated Rust sysroot: a symlink shell of the pinned toolchain
 * plus a copy of `rust-src` patched to enable `flock` on Android. Ports the old
 * `prepare-rust-std.sh`.
 */
abstract class PrepareRustStdTask : DefaultTask() {
    @get:Internal abstract val rustToolchain: Property<String>
    @get:Internal abstract val sysrootDirPath: Property<String>
    @get:Internal abstract val patchFilePath: Property<String>
    @get:Internal abstract val skip: Property<Boolean>
    @get:Inject abstract val execOps: ExecOperations

    @TaskAction
    fun prepare() {
        if (skip.get()) return
        val toolchain = rustToolchain.get()
        val sysroot = File(sysrootDirPath.get())
        val source = File(sysroot, "lib/rustlib/src/rust")
        val patch = File(patchFilePath.get())
        val stamp = File(sysroot, ".std-patch")
        val fingerprint = Fingerprint.sha256(patch.readText())

        exec(File(System.getProperty("user.home")), listOf("rustup", "component", "add", "--toolchain", toolchain, "rust-src"))
        if (stamp.isFile && stamp.readText().trim() == fingerprint && source.isDirectory) {
            logger.lifecycle("rust sysroot up to date")
            return
        }

        val original = File(capture(File("."), "rustup", "run", toolchain, "rustc", "--print", "sysroot").trim())
        sysroot.mkdirs()
        original.listFiles().orEmpty().filter { it.name != "lib" }.forEach { entry ->
            link(File(sysroot, entry.name), entry)
        }
        File(original, "lib").listFiles().orEmpty().filter { it.name != "rustlib" }.forEach { entry ->
            link(File(File(sysroot, "lib"), entry.name), entry)
        }
        File(original, "lib/rustlib").listFiles().orEmpty().filter { it.name != "src" }.forEach { entry ->
            link(File(File(sysroot, "lib/rustlib"), entry.name), entry)
        }
        source.deleteRecursively()
        File(original, "lib/rustlib/src/rust").copyRecursively(source, overwrite = true)
        exec(source, listOf("git", "apply", patch.absolutePath))
        stamp.writeText(fingerprint)
    }

    private fun link(link: File, target: File) {
        link.parentFile?.mkdirs()
        Files.deleteIfExists(link.toPath())
        Files.createSymbolicLink(link.toPath(), target.toPath())
    }

    private fun capture(workdir: File, vararg argv: String): String {
        val output = ByteArrayOutputStream()
        val result = execOps.exec {
            commandLine(argv.toList())
            workingDir(workdir)
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) throw GradleException("${argv.joinToString(" ")} failed: $output")
        return output.toString()
    }

    private fun exec(workdir: File, args: List<String>) {
        val result = execOps.exec {
            commandLine(args)
            workingDir(workdir)
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) throw GradleException("${args.joinToString(" ")} failed (${result.exitValue})")
    }
}

/**
 * Compiles the codex JNI library and helper with cargo (`-Z build-std`), then
 * installs the stripped `.so` files and enforces 16 KB ELF alignment.
 */
abstract class NativeBuildTask : DefaultTask() {
    @get:Internal abstract val nativeDirPath: Property<String>
    @get:Internal abstract val codexDirPath: Property<String>
    @get:Internal abstract val worktreeDirPath: Property<String>
    @get:Internal abstract val outDirPath: Property<String>
    @get:Internal abstract val targetDirPath: Property<String>
    @get:Internal abstract val sysrootDirPath: Property<String>
    @get:Internal abstract val patchDirPath: Property<String>
    @get:Internal abstract val rustToolchain: Property<String>
    @get:Internal abstract val opensslPrefixPath: Property<String>
    @get:Internal abstract val sqlitePrefixPath: Property<String>
    @get:Internal abstract val libffiPrefixPath: Property<String>
    @get:Internal abstract val ndk: Property<NdkEnv>
    @get:Internal abstract val smoke: Property<Boolean>
    @get:Internal abstract val jobs: Property<Int>
    @get:Internal abstract val skip: Property<Boolean>
    @get:Inject abstract val execOps: ExecOperations

    @TaskAction
    fun build() {
        if (skip.get()) {
            logger.lifecycle("buildJni skipped (-PskipNativeBuild)")
            return
        }
        val nativeDir = File(nativeDirPath.get())
        val worktree = File(worktreeDirPath.get())
        val outDir = File(outDirPath.get())
        val targetDir = File(targetDirPath.get())
        val env = NativeEnv.computeEnv(
            nativeDir, worktree, targetDir, File(sysrootDirPath.get()), rustToolchain.get(),
            File(opensslPrefixPath.get()), File(sqlitePrefixPath.get()), File(libffiPrefixPath.get()),
            ndk.get(),
        )
        val fingerprint = NativeEnv.fingerprint(execOps, File(codexDirPath.get()), File(patchDirPath.get()))
        val marker = File(outDir, ".fingerprint")
        if (marker.isFile && marker.readText() == fingerprint &&
            File(outDir, "libcodex_android_jni.so").isFile && File(outDir, "libcodex_helper.so").isFile
        ) {
            logger.lifecycle("buildJni up to date")
            return
        }
        outDir.mkdirs()

        exec(env, nativeDir, listOf("rustup", "target", "add", "--toolchain", rustToolchain.get(), ndk.get().rustTarget))
        val buildArgs = mutableListOf(
            "cargo", "build", "-Z", "build-std=std,panic_unwind", "--locked",
            "--manifest-path", File(nativeDir, "Cargo.toml").absolutePath,
            "--target", ndk.get().rustTarget, "--release", "--lib", "--bin", "codex-helper",
        )
        if (smoke.get()) buildArgs += listOf("--bin", "codex-smoke")
        buildArgs += listOf("--jobs", jobs.get().toString())
        exec(env, nativeDir, buildArgs)

        val target = File(File(targetDir, ndk.get().rustTarget), "release")
        Files.copy(
            File(target, "libcodex_android_jni.so").toPath(),
            File(outDir, "libcodex_android_jni.so").toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
        Files.copy(
            File(target, "codex-helper").toPath(),
            File(outDir, "libcodex_helper.so").toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
        File(outDir, "libcodex_android_jni.so").setExecutable(true, false)
        File(outDir, "libcodex_helper.so").setExecutable(true, false)
        exec(env, nativeDir, listOf(ndk.get().strip, "--strip-unneeded",
            File(outDir, "libcodex_android_jni.so").absolutePath,
            File(outDir, "libcodex_helper.so").absolutePath))
        NativeEnv.check16k(execOps, ndk.get().readelf, outDir, logger)
        marker.writeText(fingerprint)
        logger.lifecycle("buildJni: ${File(outDir, "libcodex_android_jni.so")}")
    }

    private fun exec(env: Map<String, String>, workdir: File, args: List<String>) {
        logger.lifecycle("native: ${args.joinToString(" ")}")
        val result = execOps.exec {
            commandLine(args)
            workingDir(workdir)
            environment(env)
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) throw GradleException("${args.joinToString(" ")} failed (${result.exitValue})")
    }
}

/** Runs a list of [codex.toolchain.Step]s with no fingerprint caching. */
abstract class SimpleStepsTask : DefaultTask() {
    @get:Internal abstract val toolName: Property<String>
    @get:Internal abstract val steps: ListProperty<codex.toolchain.Step>
    @get:Internal abstract val srcDirPath: Property<String>
    @get:Internal abstract val prefixDirPath: Property<String>
    @get:Internal abstract val rootDirPath: Property<String>
    @get:Internal abstract val thirdPartyDirPath: Property<String>
    @get:Internal abstract val buildRootPath: Property<String>
    @get:Internal abstract val ndk: Property<NdkEnv>
    @get:Internal abstract val jobs: Property<Int>
    @get:Internal abstract val skip: Property<Boolean>
    @get:Inject abstract val execOps: ExecOperations

    @TaskAction
    fun run() {
        if (skip.get()) return
        val buildRoot = File(buildRootPath.get())
        val logFile = File(File(buildRoot, "logs"), "${toolName.get()}.log")
        logFile.parentFile.mkdirs()
        logFile.writeText("")
        codex.toolchain.StepContext(
            toolName = toolName.get(),
            rootDir = File(rootDirPath.get()),
            thirdPartyDir = File(thirdPartyDirPath.get()),
            buildRoot = buildRoot,
            patchRoot = File(rootDirPath.get(), "native/patches"),
            srcDir = File(srcDirPath.get()),
            prefixDir = File(prefixDirPath.get()),
            ndk = ndk.get(),
            jobs = jobs.get(),
            execOps = execOps,
            logger = logger,
            logFile = logFile,
        ).run(steps.get())
    }
}

object NativeEnv {
    fun computeEnv(
        nativeDir: File,
        worktree: File,
        targetDir: File,
        sysroot: File,
        rustToolchain: String,
        openssl: File,
        sqlite: File,
        libffi: File,
        ndk: NdkEnv,
    ): Map<String, String> {
        val shim = File(nativeDir, "build/libgcc-shim")
        shim.mkdirs()
        val builtins = builtins(ndk)
        val shimLink = File(shim, "libgcc.a")
        Files.deleteIfExists(shimLink.toPath())
        Files.createSymbolicLink(shimLink.toPath(), builtins.toPath())
        val wrapper = File(nativeDir, "build/rustc-android.sh")
        wrapper.parentFile.mkdirs()
        wrapper.writeText(
            "#!/bin/sh\nexec ${rustCompiler(ndk, rustToolchain)} --sysroot ${sysroot.absolutePath} \"\$@\"\n",
        )
        wrapper.setExecutable(true, false)
        val pkgConfig = listOf(openssl, sqlite, libffi).joinToString(":") { "${it.absolutePath}/lib/pkgconfig" }
        return mapOf(
            "RUSTUP_TOOLCHAIN" to rustToolchain,
            "CARGO_TARGET_DIR" to targetDir.absolutePath,
            "CARGO_PROFILE_RELEASE_LTO" to "thin",
            "CARGO_PROFILE_RELEASE_CODEGEN_UNITS" to "8",
            "CARGO_PROFILE_RELEASE_OPT_LEVEL" to "2",
            "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER" to ndk.cc,
            "CC_aarch64_linux_android" to ndk.cc,
            "AR_aarch64_linux_android" to ndk.ar,
            "RANLIB_aarch64_linux_android" to ndk.ranlib,
            "CXX_aarch64_linux_android" to ndk.cxx,
            "CFLAGS_aarch64_linux_android" to "-O2 -fPIC",
            "CXXFLAGS_aarch64_linux_android" to "-O2 -fPIC -std=c++17",
            "CODEX_ANDROID_BUILTINS" to builtins.absolutePath,
            "RUSTFLAGS" to "-Lnative=${shim.absolutePath} -C link-arg=-Wl,-z,max-page-size=16384",
            "RUSTC" to wrapper.absolutePath,
            "RUSTC_BOOTSTRAP" to "1",
            "OPENSSL_DIR" to openssl.absolutePath,
            "OPENSSL_STATIC" to "1",
            "PKG_CONFIG_ALLOW_CROSS" to "1",
            "PKG_CONFIG_LIBDIR_aarch64_linux_android" to pkgConfig,
            "LZMA_API_STATIC" to "1",
            "ANDROID_NDK_HOME" to ndk.ndkHome,
            "PATH" to "${ndk.ndkBin}${File.pathSeparator}${System.getenv("PATH").orEmpty()}",
        )
    }

    fun rustCompiler(ndk: NdkEnv, toolchain: String): String {
        val output = ByteArrayOutputStream()
        ProcessBuilder("rustup", "which", "--toolchain", toolchain, "rustc")
            .redirectErrorStream(true)
            .start()
            .also { process -> output.write(process.inputStream.readBytes()); process.waitFor() }
        return output.toString().trim().ifEmpty { "rustup which --toolchain $toolchain rustc" }
    }

    fun builtins(ndk: NdkEnv): File {
        val libDir = File(File(ndk.ndkBin, ".."), "lib/clang")
        val candidates = libDir.listFiles().orEmpty()
            .sortedByDescending { it.name }
            .map { File(it, "lib/linux/libclang_rt.builtins-aarch64-android.a") }
            .filter { it.isFile }
        return candidates.firstOrNull()
            ?: throw GradleException("compiler-rt builtins not found under ${libDir.absolutePath}")
    }

    /** Bump when the JNI build recipe in this file changes. */
    const val RECIPE_VERSION = "1"

    fun fingerprint(execOps: ExecOperations, codex: File, patchesDir: File): String =
        Fingerprint.sha256(
            Fingerprint.gitHead(execOps, codex) + "|" + Fingerprint.patches(execOps, patchesDir) +
                "|" + RECIPE_VERSION,
        )

    fun check16k(execOps: ExecOperations, readelf: String, dir: File, logger: Logger) {
        var checked = 0
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(".so") }.orEmpty().forEach { file ->
            val output = ByteArrayOutputStream()
            execOps.exec {
                commandLine(readelf, "--program-headers", "--wide", file.absolutePath)
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            }
            for (line in output.toString().lineSequence()) {
                if (!line.trimStart().startsWith("LOAD")) continue
                val align = line.trim().split(Regex("\\s+")).last()
                // readelf prints p_align as 0x-prefixed hex, which toLongOrNull rejects.
                val value = align.removePrefix("0x").toLongOrNull(16) ?: continue
                checked++
                if (value < 16384 || value % 16384 != 0L) {
                    throw GradleException("$file is not 16 KB page aligned (p_align $align)")
                }
            }
        }
        if (checked == 0) throw GradleException("$dir has no loadable .so to check")
    }
}
