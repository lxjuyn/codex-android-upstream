package codex.native

import codex.toolchain.NdkEnv
import codex.toolchain.detectNdk
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Gradle entry points for the embedded Codex app-server: patch the upstream
 * worktree, build the pinned Rust sysroot and compile/strip the JNI library and
 * helper. The recipes no longer live in shell scripts.
 */
open class NativeExtension(private val project: Project) {
    val rootDir: File = project.rootProject.projectDir
    val nativeDir: File = File(rootDir, "native")
    val codexDir: File = File(rootDir, "codex")
    val worktreeDir: File = File(nativeDir, "build-upstream")
    val abi: String = project.providers.gradleProperty("abi").getOrElse("arm64-v8a")
    val api: Int = project.providers.gradleProperty("androidApi").getOrElse("36").toInt()
    val ndk: NdkEnv by lazy { detectNdk(rootDir, api, abi) }
    val rustToolchain: String = project.providers.gradleProperty("rustToolchain").getOrElse("1.95.0")
    val jobs: Int = project.providers.gradleProperty("toolchainJobs").orNull?.toIntOrNull()
        ?: Runtime.getRuntime().availableProcessors()
    val skip: Boolean = project.providers.gradleProperty("skipNativeBuild").isPresent
    val smoke: Boolean = project.providers.gradleProperty("nativeSmoke").isPresent

    fun prefix(name: String): File = File(rootDir, "toolchain/build/prefix/$name")
}

class NativePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val native = project.extensions.create("native", NativeExtension::class.java, project)
        val outDir = File(native.nativeDir, "out/${native.abi}")
        val rustSysroot = File(native.nativeDir, "rust-sysroot")
        val androidPatch = File(native.nativeDir, "patches/android-runtime.patch")
        val rustStdPatch = File(native.nativeDir, "patches/rust-std-android-flock.patch")

        project.tasks.register<PrepareUpstreamTask>("prepareUpstream") {
            group = "native"
            description = "Create the patched codex worktree at the pinned revision"
            codexDirPath.set(native.codexDir.absolutePath)
            worktreeDirPath.set(native.worktreeDir.absolutePath)
            patchFilePath.set(androidPatch.absolutePath)
            markerPath.set(File(native.nativeDir, "build/upstream.fingerprint").absolutePath)
            skip.set(native.skip)
        }

        project.tasks.register<PrepareRustStdTask>("prepareRustStd") {
            group = "native"
            description = "Build the patched Rust sysroot for Android"
            rustToolchain.set(native.rustToolchain)
            sysrootDirPath.set(rustSysroot.absolutePath)
            patchFilePath.set(rustStdPatch.absolutePath)
            skip.set(native.skip)
        }

        project.tasks.register<NativeBuildTask>("buildJni") {
            group = "native"
            description = "Build libcodex_android_jni.so and the helper"
            dependsOn("prepareUpstream", "prepareRustStd")
            dependsOn(":toolchain:buildOpenssl", ":toolchain:buildSqlite", ":toolchain:buildLibffi")
            nativeDirPath.set(native.nativeDir.absolutePath)
            codexDirPath.set(native.codexDir.absolutePath)
            worktreeDirPath.set(native.worktreeDir.absolutePath)
            outDirPath.set(outDir.absolutePath)
            targetDirPath.set(File(native.nativeDir, "target").absolutePath)
            sysrootDirPath.set(rustSysroot.absolutePath)
            patchDirPath.set(File(native.nativeDir, "patches").absolutePath)
            rustToolchain.set(native.rustToolchain)
            opensslPrefixPath.set(native.prefix("openssl").absolutePath)
            sqlitePrefixPath.set(native.prefix("sqlite").absolutePath)
            libffiPrefixPath.set(native.prefix("libffi").absolutePath)
            ndk.set(native.ndk)
            smoke.set(native.smoke)
            jobs.set(native.jobs)
            skip.set(native.skip)
        }

        project.tasks.register<NativeProbeTask>("buildFileLockProbe") {
            group = "native"
            description = "Build the file-lock probe for the device smoke test"
            dependsOn("prepareUpstream", "prepareRustStd")
            dependsOn(":toolchain:buildOpenssl", ":toolchain:buildSqlite", ":toolchain:buildLibffi")
            nativeDirPath.set(native.nativeDir.absolutePath)
            patchDirPath.set(File(native.nativeDir, "patches").absolutePath)
            targetDirPath.set(File(native.nativeDir, "target").absolutePath)
            sysrootDirPath.set(rustSysroot.absolutePath)
            rustToolchain.set(native.rustToolchain)
            opensslPrefixPath.set(native.prefix("openssl").absolutePath)
            sqlitePrefixPath.set(native.prefix("sqlite").absolutePath)
            libffiPrefixPath.set(native.prefix("libffi").absolutePath)
            ndk.set(native.ndk)
            jobs.set(native.jobs)
            skip.set(native.skip)
        }

        project.tasks.register<HostSmokeTask>("hostSmokeTest") {
            group = "verification"
            description = "Run the host app-server smoke test"
            dependsOn("prepareUpstream")
            nativeDirPath.set(native.nativeDir.absolutePath)
            targetDirPath.set(File(native.nativeDir, "host-target").absolutePath)
            rustToolchain.set(native.rustToolchain)
            jobs.set(native.jobs)
            skip.set(native.skip)
        }
    }
}
