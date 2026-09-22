package codex.native

import codex.toolchain.NdkEnv
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/** Cross-compiles `native/probes/file-lock` for the device lock smoke test. */
abstract class NativeProbeTask : DefaultTask() {
    @get:Internal abstract val nativeDirPath: Property<String>
    @get:Internal abstract val patchDirPath: Property<String>
    @get:Internal abstract val targetDirPath: Property<String>
    @get:Internal abstract val sysrootDirPath: Property<String>
    @get:Internal abstract val rustToolchain: Property<String>
    @get:Internal abstract val opensslPrefixPath: Property<String>
    @get:Internal abstract val sqlitePrefixPath: Property<String>
    @get:Internal abstract val libffiPrefixPath: Property<String>
    @get:Internal abstract val ndk: Property<NdkEnv>
    @get:Internal abstract val jobs: Property<Int>
    @get:Internal abstract val skip: Property<Boolean>
    @get:Inject abstract val execOps: ExecOperations

    @TaskAction
    fun build() {
        if (skip.get()) return
        val nativeDir = File(nativeDirPath.get())
        val targetDir = File(targetDirPath.get())
        val env = NativeEnv.computeEnv(
            nativeDir, File(nativeDir, "build-upstream"), targetDir, File(sysrootDirPath.get()),
            rustToolchain.get(), File(opensslPrefixPath.get()), File(sqlitePrefixPath.get()),
            File(libffiPrefixPath.get()), ndk.get(),
        )
        exec(env, nativeDir, listOf("rustup", "target", "add", "--toolchain", rustToolchain.get(), ndk.get().rustTarget))
        exec(
            env, nativeDir,
            listOf(
                "cargo", "build", "-Z", "build-std=std,panic_unwind", "--release", "--locked",
                "--manifest-path", File(nativeDir, "probes/file-lock/Cargo.toml").absolutePath,
                "--target", ndk.get().rustTarget, "--jobs", jobs.get().toString(),
            ),
        )
        logger.lifecycle("file-lock probe: ${File(File(targetDir, ndk.get().rustTarget), "release/codex-android-file-lock-probe")}")
    }

    private fun exec(env: Map<String, String>, workdir: File, args: List<String>) {
        val result = execOps.exec {
            commandLine(args)
            workingDir(workdir)
            environment(env)
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) throw GradleException("${args.joinToString(" ")} failed (${result.exitValue})")
    }
}

/** Builds the host helper/smoke binaries and runs the real app-server smoke test. */
abstract class HostSmokeTask : DefaultTask() {
    @get:Internal abstract val nativeDirPath: Property<String>
    @get:Internal abstract val targetDirPath: Property<String>
    @get:Internal abstract val rustToolchain: Property<String>
    @get:Internal abstract val jobs: Property<Int>
    @get:Internal abstract val skip: Property<Boolean>
    @get:Inject abstract val execOps: ExecOperations

    @TaskAction
    fun run() {
        if (skip.get()) return
        val nativeDir = File(nativeDirPath.get())
        val targetDir = File(targetDirPath.get())
        val env = mapOf(
            "RUSTUP_TOOLCHAIN" to rustToolchain.get(),
            "CARGO_TARGET_DIR" to targetDir.absolutePath,
        )
        exec(env, nativeDir, listOf(
            "cargo", "build", "--locked", "--manifest-path", File(nativeDir, "Cargo.toml").absolutePath,
            "--bin", "codex-helper", "--bin", "codex-smoke", "--jobs", jobs.get().toString(),
        ))
        val scratch = Files.createTempDirectory("codex-host-smoke").toFile()
        try {
            exec(
                env + mapOf("CODEX_HOME" to File(scratch, "runtime/codex").absolutePath),
                nativeDir,
                listOf(
                    File(targetDir, "debug/codex-smoke").absolutePath,
                    File(scratch, "runtime").absolutePath,
                    File(targetDir, "debug/codex-helper").absolutePath,
                ),
            )
        } finally {
            scratch.deleteRecursively()
        }
    }

    private fun exec(env: Map<String, String>, workdir: File, args: List<String>) {
        val result = execOps.exec {
            commandLine(args)
            workingDir(workdir)
            environment(env)
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) throw GradleException("${args.joinToString(" ")} failed (${result.exitValue})")
    }
}
