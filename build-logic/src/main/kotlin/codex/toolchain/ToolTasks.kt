package codex.toolchain

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/**
 * Materialises a tool's source into a disposable directory:
 *  - submodules become a `git worktree` at the pinned commit (submodule stays clean),
 *  - tarballs are downloaded and extracted,
 *  - host-provided tools only get an (empty) source directory.
 *
 * Patches under `toolchain/patches/<tool>/` are applied on top. The work is
 * redone whenever the pinned commit, the patches or the recipe change; a
 * `.toolchain-source` marker records the fingerprint that produced the tree.
 */
abstract class PrepareSourceTask : DefaultTask() {
    @get:Internal abstract val toolName: Property<String>
    @get:Internal abstract val source: Property<SourceSpec>
    @get:Internal abstract val srcDirPath: Property<String>
    @get:Internal abstract val markerPath: Property<String>
    @get:Internal abstract val rootDirPath: Property<String>
    @get:Internal abstract val thirdPartyDirPath: Property<String>
    @get:Internal abstract val buildRootPath: Property<String>
    @get:Internal abstract val ndk: Property<NdkEnv>
    @get:Internal abstract val patchDirPath: Property<String>
    @get:Internal abstract val skip: Property<Boolean>
    @get:Inject abstract val execOps: ExecOperations

    @TaskAction
    fun prepare() {
        if (skip.get()) {
            logger.lifecycle("${toolName.get()}: source preparation skipped (-PskipToolchainBuild)")
            return
        }
        val name = toolName.get()
        val spec = source.get()
        val rootDir = File(rootDirPath.get())
        val thirdPartyDir = File(thirdPartyDirPath.get())
        val buildRoot = File(buildRootPath.get())
        val srcDir = File(srcDirPath.get())
        val marker = File(markerPath.get())
        val patchDir = patchDirPath.get().takeIf { it.isNotBlank() }?.let { File(it) }
        if (patchDir != null && !patchDir.isDirectory) {
            throw GradleException("$name: patch directory $patchDir does not exist")
        }

        // Initialise missing submodules before fingerprinting: the git HEAD is
        // part of the fingerprint and "missing" must not become a stable state.
        if (spec.kind == SourceSpec.Kind.SUBMODULE) {
            ensureSubmodule(name, spec, rootDir, thirdPartyDir)
        }

        val fingerprint = Fingerprint.tool(execOps, spec, patchDir, emptyList(), ndk.get(), thirdPartyDir)
        if (marker.isFile && marker.readText() == fingerprint) {
            logger.lifecycle("$name: source up to date")
            return
        }

        when (spec.kind) {
            SourceSpec.Kind.SUBMODULE -> prepareSubmodule(name, spec, rootDir, thirdPartyDir, srcDir)
            SourceSpec.Kind.TARBALL -> prepareTarball(name, spec, buildRoot, srcDir)
            SourceSpec.Kind.HOST -> srcDir.mkdirs()
        }

        applyPatches(name, patchDir, srcDir)
        marker.parentFile?.mkdirs()
        marker.writeText(fingerprint)
    }

    private fun prepareSubmodule(
        name: String,
        spec: SourceSpec,
        rootDir: File,
        thirdPartyDir: File,
        srcDir: File,
    ) {
        val repo = File(thirdPartyDir, spec.value)
        ensureSubmodule(name, spec, rootDir, thirdPartyDir)
        if (spec.inPlace) {
            if (spec.recursive) {
                exec(repo, "git", "submodule", "update", "--init", "--recursive")
            }
            return
        }
        if (srcDir.exists()) {
            exec(repo, "git", "worktree", "remove", "--force", srcDir.absolutePath, ignoreFailure = true)
            srcDir.deleteRecursively()
        }
        exec(repo, "git", "worktree", "prune", ignoreFailure = true)
        exec(repo, "git", "worktree", "add", "--quiet", "--detach", srcDir.absolutePath, "HEAD")
        if (spec.recursive) {
            exec(srcDir, "git", "submodule", "update", "--init", "--recursive")
        }
    }

    /**
     * Clone the pinned submodule checkout. `git submodule update` cannot match
     * gitlinks that only exist in the index (nothing is committed yet), so the
     * URL and revision are resolved from `.gitmodules`/the index explicitly.
     */
    private fun ensureSubmodule(name: String, spec: SourceSpec, rootDir: File, thirdPartyDir: File) {
        val repo = File(thirdPartyDir, spec.value)
        if (File(repo, ".git").exists()) return
        val rel = "third_party/${spec.value}"
        val url = capture(rootDir, "git", "-C", rootDir.absolutePath, "config", "-f", ".gitmodules", "--get", "submodule.$rel.url").trim()
        val sha = capture(rootDir, "git", "-C", rootDir.absolutePath, "ls-files", "-s", "--", rel)
            .trim().split(Regex("\\s+")).getOrNull(1)
        if (url.isBlank() || sha.isNullOrBlank()) {
            throw GradleException("$name: cannot resolve submodule $rel (url='$url', sha='$sha')")
        }
        logger.lifecycle(
            "$name: cloning submodule $rel at ${sha.take(12)}" +
                if (spec.tag.isNotBlank()) " (tag ${spec.tag}, shallow)" else "",
        )
        repo.parentFile?.mkdirs()
        repo.deleteRecursively()
        if (spec.tag.isNotBlank()) {
            exec(rootDir, "git", "clone", "--depth", "1", "--branch", spec.tag, url, repo.absolutePath)
        } else {
            exec(rootDir, "git", "clone", url, repo.absolutePath)
        }
        val head = capture(repo, "git", "-C", repo.absolutePath, "rev-parse", "HEAD").trim()
        if (head != sha) {
            // Tag moved or the server checked out a different revision: pin hard.
            exec(repo, "git", "fetch", "--depth", "1", "origin", sha)
            exec(repo, "git", "checkout", "--quiet", "--detach", sha)
        }
        if (spec.recursive) {
            exec(repo, "git", "submodule", "update", "--init", "--recursive")
        }
    }

    private fun capture(workdir: File, vararg argv: String): String {
        val output = java.io.ByteArrayOutputStream()
        val result = execOps.exec {
            commandLine(argv.toList())
            workingDir(workdir)
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException("${argv.joinToString(" ")} failed (${result.exitValue}): $output")
        }
        return output.toString()
    }

    private fun prepareTarball(name: String, spec: SourceSpec, buildRoot: File, srcDir: File) {
        val fileName = spec.value.substringAfterLast('/')
        val archive = File(File(buildRoot, "downloads"), fileName)
        if (!archive.isFile || archive.length() == 0L) {
            archive.parentFile.mkdirs()
            val part = File(archive.parentFile, "$fileName.part")
            val candidates = listOf(spec.value, spec.fallback).filter { it.isNotBlank() }
            var lastError: GradleException? = null
            for ((index, candidate) in candidates.withIndex()) {
                try {
                    exec(
                        archive.parentFile,
                        "curl", "-fsSL", "--retry", "3", "--retry-all-errors", "--connect-timeout", "15",
                        "-o", part.absolutePath, candidate,
                    )
                    Files.move(part.toPath(), archive.toPath())
                    lastError = null
                    break
                } catch (exception: GradleException) {
                    lastError = exception
                    if (index + 1 < candidates.size) {
                        logger.lifecycle("$name: $candidate failed, trying the fallback source")
                    }
                }
            }
            if (lastError != null) throw lastError
        }
        srcDir.deleteRecursively()
        srcDir.mkdirs()
        logger.lifecycle("$name: extracting ${archive.name}")
        when {
            fileName.endsWith(".zip") ->
                exec(srcDir, "python3", "-m", "zipfile", "-e", archive.absolutePath, srcDir.absolutePath)

            fileName.endsWith(".tar.xz") || fileName.endsWith(".tar.gz") ||
                fileName.endsWith(".tgz") || fileName.endsWith(".tar.bz2") ->
                exec(
                    srcDir,
                    "tar", "-xf", archive.absolutePath, "-C", srcDir.absolutePath,
                    "--strip-components=${spec.stripComponents}",
                )

            else -> throw GradleException("$name: unknown archive type ${archive.name}")
        }
    }

    private fun applyPatches(name: String, patchDir: File?, srcDir: File) {
        val patches = patchDir?.listFiles { file -> file.isFile && file.name.endsWith(".patch") }
            ?.sortedBy { it.name }
            .orEmpty()
        patches.forEach { patch ->
            logger.lifecycle("$name: applying ${patch.name}")
            exec(
                srcDir,
                "patch", "-p1", "-d", srcDir.absolutePath, "--forward", "--no-backup-if-mismatch",
                "-i", patch.absolutePath,
            )
        }
    }

    private fun exec(workdir: File, vararg argv: String, ignoreFailure: Boolean = false) {
        val result = execOps.exec {
            commandLine(argv.toList())
            workingDir(workdir)
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0 && !ignoreFailure) {
            throw GradleException("${toolName.get()}: ${argv.joinToString(" ")} failed (${result.exitValue})")
        }
    }
}

/**
 * Runs a tool's recipe into its own prefix (`toolchain/build/prefix/<name>`).
 * The prefix doubles as the build cache: a `.toolchain-built` marker stores the
 * fingerprint (source revision + patches + recipe + NDK), and a prefix that
 * still matches is left untouched. Deleting the prefix forces a rebuild.
 */
abstract class ToolBuildTask : DefaultTask() {
    @get:Internal abstract val toolName: Property<String>
    @get:Internal abstract val source: Property<SourceSpec>
    @get:Internal abstract val steps: ListProperty<Step>
    @get:Internal abstract val srcDirPath: Property<String>
    @get:Internal abstract val prefixDirPath: Property<String>
    @get:Internal abstract val rootDirPath: Property<String>
    @get:Internal abstract val thirdPartyDirPath: Property<String>
    @get:Internal abstract val buildRootPath: Property<String>
    @get:Internal abstract val patchDirPath: Property<String>
    @get:Internal abstract val ndk: Property<NdkEnv>
    @get:Internal abstract val jobs: Property<Int>
    @get:Internal abstract val skip: Property<Boolean>
    @get:Inject abstract val execOps: ExecOperations

    @TaskAction
    fun build() {
        val name = toolName.get()
        if (skip.get()) {
            logger.lifecycle("$name: build skipped (-PskipToolchainBuild)")
            return
        }
        val prefixDir = File(prefixDirPath.get())
        val marker = File(prefixDir, ".toolchain-built")
        val patchDir = patchDirPath.get().takeIf { it.isNotBlank() }?.let { File(it) }
        val thirdPartyDir = File(thirdPartyDirPath.get())
        val fingerprint = Fingerprint.tool(execOps, source.get(), patchDir, steps.get(), ndk.get(), thirdPartyDir)
        if (prefixDir.isDirectory && marker.isFile && marker.readText() == fingerprint) {
            logger.lifecycle("$name: up to date")
            return
        }

        prefixDir.deleteRecursively()
        prefixDir.mkdirs()
        val buildRoot = File(buildRootPath.get())
        val logFile = File(File(buildRoot, "logs"), "$name.log")
        logFile.parentFile.mkdirs()
        logFile.writeText("")
        StepContext(
            toolName = name,
            rootDir = File(rootDirPath.get()),
            thirdPartyDir = thirdPartyDir,
            buildRoot = buildRoot,
            patchRoot = File(File(rootDirPath.get(), "toolchain/patches"), name),
            srcDir = File(srcDirPath.get()),
            prefixDir = prefixDir,
            ndk = ndk.get(),
            jobs = jobs.get(),
            execOps = execOps,
            logger = logger,
            logFile = logFile,
        ).run(steps.get())
        marker.writeText(fingerprint)
        logger.lifecycle("$name: installed to $prefixDir")
    }
}
