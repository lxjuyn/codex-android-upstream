package codex.toolchain

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/**
 * Merges every tool prefix into `toolchain/out/<abi>` and packages it for the
 * APK exactly like the old `pack-jnilibs.sh`:
 *
 *  - executables and modules become `jniLibs/lib<slug>.so` (the only place
 *    Android ≥29 lets an app exec/dlopen from),
 *  - data files (python stdlib, git templates, CA bundle) go to assets,
 *  - `native-manifest.txt` records how the runtime rebuilds the tree,
 *  - every ELF that lands in jniLibs is checked for 16 KB page alignment.
 */
abstract class PackJniLibsTask : DefaultTask() {
    @get:Internal abstract val outRootPath: Property<String>
    @get:Internal abstract val abi: Property<String>
    @get:Internal abstract val prefixes: ListProperty<String>
    @get:Internal abstract val ndk: Property<NdkEnv>
    @get:Internal abstract val skip: Property<Boolean>
    @get:Inject abstract val execOps: ExecOperations

    private val stripRoots = listOf("bin", "sbin", "libexec", "share", "lib")

    @TaskAction
    fun pack() {
        if (skip.get()) {
            logger.lifecycle("packJniLibs skipped (-PskipToolchainBuild)")
            return
        }
        val outRoot = File(outRootPath.get())
        val abi = abi.get()
        val stage = File(outRoot, abi)
        val dist = File(outRoot, "android/$abi")
        val jniLibs = File(dist, "jniLibs")
        val assets = File(dist, "assets/toolchain")
        val manifest = File(dist, "native-manifest.txt")

        // The staging tree is derived state: rebuild it from the prefixes so a
        // removed tool (or one rebuilt with fewer files) cannot linger.
        stage.deleteRecursively()
        stage.mkdirs()
        prefixes.get().map(::File).filter { it.isDirectory }.forEach { prefix ->
            merge(prefix, stage)
        }
        if (!File(stage, "bin").isDirectory) {
            throw GradleException("toolchain staging is empty: no tool prefix was built")
        }

        dist.deleteRecursively()
        jniLibs.mkdirs()
        assets.mkdirs()
        var libs = 0
        var links = 0
        var data = 0
        val lines = mutableListOf("abi|$abi")

        val roots = stripRoots.map { File(stage, it) }.filter { it.exists() }
        roots.flatMap { it.walkTopDown().toList() }
            .filter { it != stage }
            .sortedBy { it.path }
            .forEach { path ->
                val rel = path.relativeTo(stage).invariantSeparatorsPath
                if (!stripRoots.any { rel.startsWith("$it/") }) return@forEach
                if (rel.endsWith(".a") || rel.endsWith(".la") || rel.contains("lib/pkgconfig/")) return@forEach
                when {
                    Files.isSymbolicLink(path.toPath()) -> {
                        lines += "link|$rel|${Files.readSymbolicLink(path.toPath())}"
                        links++
                    }

                    path.isFile -> {
                        val executable = rel.startsWith("bin/") || rel.startsWith("sbin/") ||
                            rel.startsWith("libexec/") || rel.endsWith(".so")
                        if (executable) {
                            val lib = "lib${slug(rel)}.so"
                            path.copyTo(File(jniLibs, lib), overwrite = true)
                            File(jniLibs, lib).setExecutable(true, false)
                            lines += "file|$rel|$lib"
                            libs++
                        } else {
                            val dest = File(assets, rel)
                            dest.parentFile.mkdirs()
                            path.copyTo(dest, overwrite = true)
                            lines += "data|$rel|"
                            data++
                        }
                    }
                }
            }
        manifest.writeText(lines.joinToString("\n", postfix = "\n"))

        check16k(jniLibs)
        logger.lifecycle("packed $abi: $libs libs, $links links, $data data files")
        logger.lifecycle("  jniLibs: ${size(jniLibs)}")
        logger.lifecycle("  assets:  ${size(assets)}")
        logger.lifecycle("  manifest: $manifest")
    }

    private fun merge(from: File, into: File) {
        from.listFiles()?.sortedBy { it.name }?.forEach { file ->
            val target = File(into, file.name)
            when {
                Files.isSymbolicLink(file.toPath()) -> {
                    Files.deleteIfExists(target.toPath())
                    Files.createDirectories(target.parentFile.toPath())
                    Files.createSymbolicLink(target.toPath(), Files.readSymbolicLink(file.toPath()))
                }

                file.isDirectory -> {
                    target.mkdirs()
                    merge(file, target)
                }

                file.isFile -> {
                    target.parentFile.mkdirs()
                    file.copyTo(target, overwrite = true)
                    target.setExecutable(file.canExecute(), false)
                }
            }
        }
    }

    private fun slug(rel: String): String {
        val allowed = Regex("[A-Za-z0-9._-]")
        return rel.map { if (allowed.matches(it.toString())) it else '_' }.joinToString("")
    }

    private fun check16k(dir: File) {
        var checked = 0
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        for (file in files) {
            val output = ByteArrayOutputStream()
            val result = execOps.exec {
                commandLine(ndk.get().readelf, "--program-headers", "--wide", file.absolutePath)
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) continue
            for (line in output.toString().lineSequence()) {
                if (!line.trimStart().startsWith("LOAD")) continue
                val align = line.trim().split(Regex("\\s+")).last()
                // readelf prints p_align as 0x-prefixed hex, which toLongOrNull rejects.
                val value = align.removePrefix("0x").toLongOrNull(16) ?: continue
                checked++
                if (value < 16384 || value % 16384 != 0L) {
                    throw GradleException(
                        "$file has a LOAD segment with p_align $align, not a multiple of 0x4000; " +
                            "16 KB page devices cannot load it",
                    )
                }
            }
        }
        if (checked == 0) throw GradleException("$dir has no loadable ELF to check")
    }

    private fun size(file: File): String {
        val bytes = file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return "%.1f MB".format(bytes / 1024.0 / 1024.0)
    }
}
