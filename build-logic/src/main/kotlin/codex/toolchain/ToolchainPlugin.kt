package codex.toolchain

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.io.File

fun taskSuffix(name: String): String =
    name.split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotEmpty() }
        .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }

/**
 * Registers one `prepare<Name>` / `build<Name>` task pair per tool plus the
 * `packJniLibs` aggregator. Recipes live in the consuming build script; this
 * extension only supplies paths, the NDK environment and task wiring.
 */
open class ToolchainExtension(private val project: Project) {
    private val specs = mutableListOf<ToolSpec>()

    val rootDir: File = project.rootProject.projectDir
    val thirdPartyDir: File = File(rootDir, "third_party")
    val buildRoot: File = File(rootDir, "toolchain/build")
    val outRoot: File = File(rootDir, "toolchain/out")
    val patchesRoot: File = File(rootDir, "toolchain/patches")

    val abi: String = project.providers.gradleProperty("abi").getOrElse("arm64-v8a")
    val api: Int = project.providers.gradleProperty("androidApi").getOrElse("36").toInt()
    val jobs: Int = project.providers.gradleProperty("toolchainJobs").orNull?.toIntOrNull()
        ?: Runtime.getRuntime().availableProcessors()
    val ndk: NdkEnv by lazy { detectNdk(rootDir, api, abi) }
    val skip: Boolean = project.providers.gradleProperty("skipToolchainBuild").isPresent

    fun tool(name: String, block: ToolSpec.() -> Unit = {}) {
        val spec = ToolSpec(name).apply(block)
        specs += spec
        val suffix = taskSuffix(name)
        val patchDir = File(patchesRoot, spec.patchDir ?: name).takeIf { it.isDirectory }
        val inPlace = spec.source.kind == SourceSpec.Kind.SUBMODULE && spec.source.inPlace
        val srcDir = if (inPlace) File(thirdPartyDir, spec.source.value) else File(buildRoot, "src/$name")
        val marker = if (inPlace) File(buildRoot, "$name.source") else File(srcDir, ".toolchain-source")
        val prefixDir = File(buildRoot, "prefix/$name")

        val prepare = project.tasks.register<PrepareSourceTask>("prepare$suffix") {
            group = "toolchain"
            description = "Fetch/extract the source for $name"
            toolName.set(name)
            source.set(spec.source)
            srcDirPath.set(srcDir.absolutePath)
            markerPath.set(marker.absolutePath)
            rootDirPath.set(this@ToolchainExtension.rootDir.absolutePath)
            thirdPartyDirPath.set(this@ToolchainExtension.thirdPartyDir.absolutePath)
            buildRootPath.set(this@ToolchainExtension.buildRoot.absolutePath)
            patchDirPath.set(patchDir?.absolutePath ?: "")
            ndk.set(this@ToolchainExtension.ndk)
            skip.set(this@ToolchainExtension.skip)
        }

        project.tasks.register<ToolBuildTask>("build$suffix") {
            group = "toolchain"
            description = "Build $name for ${this@ToolchainExtension.abi}"
            dependsOn(prepare)
            spec.dependsOn.forEach { dependency -> dependsOn("build${taskSuffix(dependency)}") }
            toolName.set(name)
            source.set(spec.source)
            steps.set(spec.steps.toList())
            srcDirPath.set(srcDir.absolutePath)
            prefixDirPath.set(prefixDir.absolutePath)
            rootDirPath.set(this@ToolchainExtension.rootDir.absolutePath)
            thirdPartyDirPath.set(this@ToolchainExtension.thirdPartyDir.absolutePath)
            buildRootPath.set(this@ToolchainExtension.buildRoot.absolutePath)
            patchDirPath.set(patchDir?.absolutePath ?: "")
            ndk.set(this@ToolchainExtension.ndk)
            jobs.set(this@ToolchainExtension.jobs)
            skip.set(this@ToolchainExtension.skip)
        }
    }

    internal fun finish() {
        val buildTasks = specs.map { "build${taskSuffix(it.name)}" }
        project.tasks.register<Task>("buildToolchain") {
            group = "toolchain"
            description = "Build every toolchain tool for ${this@ToolchainExtension.abi}"
            dependsOn(buildTasks)
        }
        val packed = specs.filter { it.pack }.map { "build${taskSuffix(it.name)}" }
        val packedPrefixes = specs.filter { it.pack }
            .map { File(buildRoot, "prefix/${it.name}").absolutePath }
        project.tasks.register<PackJniLibsTask>("packJniLibs") {
            group = "toolchain"
            description = "Merge tool prefixes into the APK toolchain package"
            dependsOn(packed)
            outRootPath.set(this@ToolchainExtension.outRoot.absolutePath)
            abi.set(this@ToolchainExtension.abi)
            prefixes.set(packedPrefixes)
            ndk.set(this@ToolchainExtension.ndk)
            skip.set(this@ToolchainExtension.skip)
        }
    }
}

class ToolchainPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("toolchain", ToolchainExtension::class.java, project)
        project.afterEvaluate { extension.finish() }
    }
}
