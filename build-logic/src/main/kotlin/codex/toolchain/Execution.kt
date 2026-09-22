package codex.toolchain

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Shell-free command/file step runner shared by the toolchain and native builds. */
class StepContext(
    val toolName: String,
    val rootDir: File,
    val thirdPartyDir: File,
    val buildRoot: File,
    val patchRoot: File,
    val srcDir: File,
    val prefixDir: File,
    val ndk: NdkEnv,
    val jobs: Int,
    private val execOps: ExecOperations,
    private val logger: Logger,
    private val logFile: File,
) {
    val buildDir: File = File(buildRoot, "tools/$toolName")
    private val basePath: String = System.getenv("PATH").orEmpty()
    private val libclangRt: String = run {
        val clangDir = File(File(ndk.ndkBin, ".."), "lib/clang")
        clangDir.listFiles().orEmpty()
            .sortedByDescending { it.name }
            .map { File(it, "lib/linux/libclang_rt.builtins-aarch64-android.a") }
            .firstOrNull { it.isFile }
            ?.absolutePath
            .orEmpty()
    }

    private val vars: Map<String, String> = buildMap {
        put("SRC", srcDir.absolutePath)
        put("BUILD", buildDir.absolutePath)
        put("PREFIX", prefixDir.absolutePath)
        put("ROOT", rootDir.absolutePath)
        put("THIRD_PARTY", thirdPartyDir.absolutePath)
        put("PATCHES", patchRoot.absolutePath)
        put("LIBCLANG_RT", libclangRt)
        put("NDK_HOME", ndk.ndkHome)
        put("NDK_BIN", ndk.ndkBin)
        put("SYSROOT", ndk.sysroot)
        put("API", ndk.api.toString())
        put("ABI", ndk.abi)
        put("HOST", ndk.crossHost)
        put("RUST_TARGET", ndk.rustTarget)
        put("JOBS", jobs.toString())
        put("CC", ndk.cc)
        put("CXX", ndk.cxx)
        put("AR", ndk.ar)
        put("RANLIB", ndk.ranlib)
        put("NM", ndk.nm)
        put("STRIP", ndk.strip)
        put("OBJCOPY", ndk.objcopy)
        put("READELF", ndk.readelf)
        put("BUILD_TRIPLE", hostTriple())
        put("CFLAGS", ndk.cFlags)
        put("CXXFLAGS", ndk.cxxFlags)
        put("LDFLAGS", ndk.ldFlags)
        put("NDK_TOOLCHAIN_FILE", ndk.cmakeToolchain)
    }

    private val crossEnv: Map<String, String> = buildMap {
        put("ANDROID_NDK_HOME", ndk.ndkHome)
        put("ANDROID_NDK_ROOT", ndk.ndkHome)
        put("ANDROID_API", ndk.api.toString())
        put("NDK_BIN", ndk.ndkBin)
        put("NDK_SYSROOT", ndk.sysroot)
        put("CC", ndk.cc)
        put("CXX", ndk.cxx)
        put("AR", ndk.ar)
        put("RANLIB", ndk.ranlib)
        put("NM", ndk.nm)
        put("STRIP", ndk.strip)
        put("OBJCOPY", ndk.objcopy)
        put("CFLAGS", ndk.cFlags)
        put("CXXFLAGS", ndk.cxxFlags)
        put("LDFLAGS", ndk.ldFlags)
        put("CROSS_HOST", ndk.crossHost)
        put("RUST_TARGET", ndk.rustTarget)
        put("PATH", "${ndk.ndkBin}${File.pathSeparator}$basePath")
    }

    private val prefixOf = Regex("\\\$PREFIX_OF\\(([^)]+)\\)")
    private val variable = Regex("\\\$([A-Za-z_][A-Za-z0-9_]*)")

    fun expand(text: String): String {
        if (!text.contains('$')) return text
        var out = text.replace(prefixOf) { match ->
            File(File(buildRoot, "prefix"), match.groupValues[1]).absolutePath
        }
        repeat(4) {
            val previous = out
            out = variable.replace(out) { match -> vars[match.groupValues[1]] ?: match.value }
            if (out == previous) return out
        }
        return out
    }

    fun run(steps: List<Step>) {
        steps.forEach { execute(it) }
    }

    private fun execute(step: Step) {
        when (step) {
            is Step.Run -> {
                val workdir = File(expand(step.workdir))
                val skip = step.skipIfExists?.let { resolve(workdir, it) }
                if (skip != null && skip.exists()) {
                    logger.lifecycle("$toolName: skip (${skip.name} exists)")
                    return
                }
                exec(step.argv, step.env, workdir, step.hostEnv, step.splitArgs)
            }

            is Step.RunFirstTime -> {
                val workdir = File(expand(step.workdir))
                if (resolve(workdir, step.checkFile).exists()) {
                    logger.lifecycle("$toolName: skip (${step.checkFile} exists)")
                    return
                }
                exec(step.argv, step.env, workdir, step.hostEnv)
            }

            is Step.Mkdir -> step.paths.forEach { File(expand(it)).mkdirs() }

            is Step.Remove -> step.paths.forEach { removePath(File(expand(it))) }

            is Step.Copy -> copyPath(File(expand(step.from)), File(expand(step.to)), step.executable)

            is Step.Symlink -> {
                val link = File(expand(step.link))
                val expandedTarget = expand(step.target)
                if (expandedTarget.isBlank()) {
                    throw GradleException("$toolName: empty symlink target for $link")
                }
                val target = resolveTarget(expandedTarget)
                link.parentFile?.mkdirs()
                Files.deleteIfExists(link.toPath())
                Files.createSymbolicLink(link.toPath(), File(target).toPath())
            }

            is Step.ReplaceInFile -> {
                val file = File(expand(step.file))
                val text = file.readText()
                val regex = Regex(step.regex)
                val replaced = if (step.groupRefs) {
                    regex.replace(text, step.replacement)
                } else {
                    regex.replace(text) { step.replacement }
                }
                file.writeText(replaced)
            }

            is Step.WriteFile -> {
                val file = File(expand(step.path))
                file.parentFile?.mkdirs()
                file.writeText(step.content)
                if (step.executable) file.setExecutable(true, false)
            }

            is Step.Download -> fetch(expand(step.url), expand(step.fallback), File(expand(step.dest)))

            is Step.Strip -> stripTree(File(expand(step.dir)))
        }
    }

    private fun resolve(workdir: File, path: String): File {
        val expanded = expand(path)
        return if (File(expanded).isAbsolute) File(expanded) else File(workdir, expanded)
    }

    private fun exec(
        argv: List<String>,
        env: Map<String, String>,
        workdir: File,
        hostEnv: Boolean,
        splitArgs: Boolean = false,
    ) {
        val command = argv.flatMap { argument ->
            val expanded = expand(argument)
            if (splitArgs) expanded.split(Regex("\\s+")).filter { it.isNotEmpty() } else listOf(expanded)
        }
        val stepEnv = env.mapValues { (_, value) -> expand(value) }
        workdir.mkdirs()
        logger.lifecycle("$toolName: ${command.joinToString(" ")}")
        logFile.appendText("+ ${command.joinToString(" ")}\n")
        val output = logFile.outputStream()
        val result = try {
            execOps.exec {
                commandLine(command)
                workingDir(workdir)
                environment(if (hostEnv) mapOf("PATH" to basePath) + stepEnv else crossEnv + stepEnv)
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            }
        } finally {
            output.close()
        }
        if (result.exitValue != 0) {
            throw GradleException(
                "$toolName: command exited with ${result.exitValue}: ${command.joinToString(" ")}\n" +
                    tailOfLog(),
            )
        }
    }

    private fun tailOfLog(): String =
        logFile.takeIf { it.isFile }?.readLines()?.takeLast(60)?.joinToString("\n").orEmpty()

    private fun copyPath(from: File, to: File, executable: Boolean) {
        if (!from.exists()) throw GradleException("$toolName: missing source $from")
        val dest = if (from.isDirectory && !to.exists()) to else if (to.isDirectory) File(to, from.name) else to
        if (from.isDirectory) {
            from.copyRecursively(dest, overwrite = true)
        } else {
            dest.parentFile?.mkdirs()
            from.copyTo(dest, overwrite = true)
        }
        if (executable) dest.setExecutable(true, false)
    }

    private fun removePath(path: File) {
        val raw = path.absolutePath
        if (!raw.any { it == '*' || it == '?' }) {
            path.deleteRecursively()
            return
        }
        val wildcard = raw.indexOfFirst { it == '*' || it == '?' }
        val slash = raw.lastIndexOf('/', wildcard)
        val base = if (slash <= 0) File("/") else File(raw.substring(0, slash))
        if (!base.isDirectory) return
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$raw")
        Files.walk(base.toPath()).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { candidate ->
                if (matcher.matches(candidate)) candidate.toFile().deleteRecursively()
            }
        }
    }

    private fun resolveTarget(target: String): String {
        if (!target.contains('*')) return target
        val pattern = target.replace("\\", "/")
        val wildcard = pattern.indexOf('*')
        val slash = pattern.lastIndexOf('/', wildcard).takeIf { it >= 0 } ?: return target
        val parent = File(pattern.substring(0, slash))
        val namePattern = Regex(pattern.substring(slash + 1).replace(".", "\\.").replace("*", ".*"))
        return parent.listFiles()?.firstOrNull { namePattern.matches(it.name) }?.absolutePath
            ?: throw GradleException("$toolName: no match for $target")
    }

    private fun fetch(url: String, fallback: String, dest: File) {
        if (dest.isFile && dest.length() > 0) {
            logger.lifecycle("$toolName: cached ${dest.name}")
            return
        }
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, "${dest.name}.part")
        for ((index, candidate) in listOf(url, fallback).filter { it.isNotBlank() }.withIndex()) {
            try {
                exec(
                    listOf(
                        "curl", "-fsSL", "--retry", "3", "--retry-all-errors",
                        "--connect-timeout", "15",
                        "-o", part.absolutePath, candidate,
                    ),
                    emptyMap(),
                    dest.parentFile,
                    hostEnv = true,
                )
                Files.move(part.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                return
            } catch (exception: GradleException) {
                if (index == 0 && fallback.isNotBlank()) {
                    logger.lifecycle("$toolName: $url failed, trying the fallback mirror")
                    continue
                }
                throw exception
            }
        }
    }

    private fun stripTree(dir: File) {
        if (!dir.exists()) return
        dir.walkTopDown().filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }.forEach { file ->
            val header = ByteArray(4)
            file.inputStream().use { it.read(header, 0, 4) }
            val isElf = header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()
            if (isElf) {
                execOps.exec {
                    commandLine(ndk.strip, "--strip-unneeded", file.absolutePath)
                    isIgnoreExitValue = true
                    standardOutput = OutputStream.nullOutputStream()
                    errorOutput = OutputStream.nullOutputStream()
                }
            }
        }
    }
}

private fun hostTriple(): String {
    val arch = System.getProperty("os.arch").lowercase()
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") && arch == "aarch64" -> "arm64-apple-darwin"
        os.contains("mac") -> "x86_64-apple-darwin"
        arch == "aarch64" -> "aarch64-unknown-linux-gnu"
        else -> "x86_64-pc-linux-gnu"
    }
}

object Fingerprint {
    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun patches(execOps: ExecOperations, dir: File?): String {
        if (dir == null || !dir.isDirectory) return "none"
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".patch") }
            ?.sortedBy { it.name }
            .orEmpty()
        val digest = MessageDigest.getInstance("SHA-256")
        files.forEach { file ->
            digest.update(file.name.toByteArray())
            digest.update(file.readBytes())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun gitHead(execOps: ExecOperations, repo: File): String {
        if (!repo.isDirectory) return "missing"
        val output = ByteArrayOutputStream()
        val result = execOps.exec {
            commandLine("git", "-C", repo.absolutePath, "rev-parse", "HEAD")
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        return if (result.exitValue == 0) output.toString().trim() else "missing"
    }

    fun tool(
        execOps: ExecOperations,
        source: SourceSpec,
        patches: File?,
        steps: List<Step>,
        ndk: NdkEnv,
        thirdPartyDir: File,
    ): String {
        val text = buildString {
            append(source.label).append('\n')
            if (source.kind == SourceSpec.Kind.SUBMODULE) {
                append(gitHead(execOps, File(thirdPartyDir, source.value))).append('\n')
            }
            append(patches(execOps, patches)).append('\n')
            steps.forEach { append(it).append('\n') }
            append(ndk.ndkBin).append('|').append(ndk.api).append('|').append(ndk.abi)
        }
        return sha256(text)
    }
}
