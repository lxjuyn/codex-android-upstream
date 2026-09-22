import com.android.build.api.artifact.SingleArtifact
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

android {
    namespace = "com.cy.codex"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cy.codex"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "com.cy.codex.runtime.RuntimeSmokeInstrumentation"
    }

    buildTypes {
        release {
            // R8 and the resource shrinker are release-only; debug stays uninstrumented so a
            // stack trace or a layout inspector session reads like the source.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        // 21, not 17: `miuix-nav`'s `entry<T> { }` DSL is an inline function compiled to JVM 21
        // bytecode, and Kotlin refuses to inline a higher-target body into a lower-target module.
        // minSdk is 36, so there is nothing below to desugar this for anyway.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        aidl = false
        // The settings page and the initialize handshake report the packaged version, so the app
        // has to know it at runtime.
        buildConfig = true
        shaders = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        // The toolchain is packaged verbatim: native-manifest.txt lists every file the runtime
        // installer copies out of the APK, so anything the asset merger drops turns into a
        // FileNotFoundException on the device. AGP's default pattern ends with `.*:<dir>_*`, which
        // discards dotfiles and every entry below a directory whose name starts with `_` — Python
        // 3.14 ships `_pyrepl`, `__phello__`, `compression/_common` and `zipfile/_path`, so 32
        // listed files never reached the APK. Keep only junk rules that match nothing in the
        // toolchain tree; the per-variant verify…ToolchainAssets task fails the build otherwise.
        ignoreAssetsPattern =
            "!.svn:!.git:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!*~"
    }

}

abstract class StageRuntime : Sync() {
    @get:Internal abstract val jniDirectory: DirectoryProperty
    @get:Internal abstract val assetsDirectory: DirectoryProperty
}

/**
 * Reads the manifest the installer itself reads and checks that every `data` entry it names is
 * present in the packaged APK. Asset merging silently drops files (see ignoreAssetsPattern), and
 * the loss is otherwise only visible on a device as "Codex 启动失败" plus one asset path.
 *
 * The APK is an internal input on purpose: hashing the ~285 MB artifact on every build to make this
 * cheap check skippable would cost more than running it.
 */
abstract class VerifyToolchainAssets : DefaultTask() {
    @get:Internal abstract val apkDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val directory = apkDirectory.get().asFile
        val apks = directory.listFiles { file -> file.isFile && file.name.endsWith(".apk") }.orEmpty()
        val file = apks.singleOrNull() ?: error("Expected one APK in $directory, found ${apks.size}")
        val prefix = "assets/toolchain/"
        val missing = mutableListOf<String>()
        ZipFile(file).use { zip ->
            val manifest = zip.getEntry("${prefix}native-manifest.txt")
                ?: error("${file.name} has no ${prefix}native-manifest.txt")
            val listed = zip.getInputStream(manifest).bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    line.split('|').takeIf { it.size >= 2 && it[0] == "data" }?.get(1)
                }.toList()
            }
            listed.filterTo(missing) { zip.getEntry("$prefix$it") == null }
        }
        check(missing.isEmpty()) {
            "${file.name} is missing ${missing.size} of the ${prefix}native-manifest.txt data " +
                "entries, starting with ${missing.first()}. The runtime installer would fail on the " +
                "device; update androidResources.ignoreAssetsPattern so the toolchain ships verbatim."
        }
    }
}

val repositoryRoot = rootProject.projectDir

val stageRuntime = tasks.register<StageRuntime>("stageRuntime") {
    // The toolchain package and the JNI library are ordinary Gradle tasks now;
    // `-PskipToolchainBuild` / `-PskipNativeBuild` make the individual tasks
    // no-ops and the checks below then fail with the same actionable messages.
    dependsOn(":toolchain:packJniLibs", ":native:buildJni")
    into(layout.buildDirectory.dir("runtime"))
    jniDirectory.set(layout.buildDirectory.dir("runtime/jniLibs"))
    assetsDirectory.set(layout.buildDirectory.dir("runtime/assets"))
    from(repositoryRoot.resolve("toolchain/out/android/arm64-v8a/jniLibs")) {
        into("jniLibs/arm64-v8a")
    }
    from(repositoryRoot.resolve("native/out/arm64-v8a")) {
        include("*.so")
        into("jniLibs/arm64-v8a")
    }
    from(repositoryRoot.resolve("toolchain/out/android/arm64-v8a/assets")) {
        into("assets")
    }
    from(repositoryRoot.resolve("toolchain/out/android/arm64-v8a/native-manifest.txt")) {
        into("assets/toolchain")
    }
    val nativeLibrary = repositoryRoot.resolve("native/out/arm64-v8a/libcodex_android_jni.so")
    val nativeHelper = repositoryRoot.resolve("native/out/arm64-v8a/libcodex_helper.so")
    val manifest = repositoryRoot.resolve("toolchain/out/android/arm64-v8a/native-manifest.txt")
    doFirst {
        check(nativeLibrary.isFile && nativeHelper.isFile) {
            "Missing Codex JNI library. Run ./gradlew :native:buildJni (or drop -PskipNativeBuild)."
        }
        check(manifest.isFile) {
            "Missing toolchain package. Run ./gradlew :toolchain:packJniLibs (or drop -PskipToolchainBuild)."
        }
    }
}
tasks.named("preBuild") { dependsOn(stageRuntime) }
androidComponents.onVariants { variant ->
    variant.sources.jniLibs?.addGeneratedSourceDirectory(stageRuntime) { it.jniDirectory }
    variant.sources.assets?.addGeneratedSourceDirectory(stageRuntime) { it.assetsDirectory }
    val suffix = variant.name.replaceFirstChar { it.uppercaseChar() }
    val verifyToolchainAssets = tasks.register<VerifyToolchainAssets>("verify${suffix}ToolchainAssets") {
        group = "verification"
        description = "Fails when asset packaging dropped anything native-manifest.txt lists."
        apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
    }
    tasks.matching { it.name == "assemble$suffix" }.configureEach { finalizedBy(verifyToolchainAssets) }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.components.resources)

    implementation(libs.androidx.activity.compose)

    // The transport and session reducer use coroutines.
    implementation(libs.kotlinx.coroutines.android)

    // Wire JSON: kotlinx.serialization's JsonElement tree, parser and printer.
    implementation(libs.kotlinx.serialization.json)

    // Protocol-level tests are plain JVM code: the JSON-RPC codec, the unified-diff parser, the
    // agent-roster fold must hold without a device.
    testImplementation(kotlin("test"))
    // Constructor parameters of the protocol data classes, read for the schema-drift check.
    testImplementation(kotlin("reflect"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Reads the precomputed experimental schema export (zstd-compressed JSON).
    testImplementation(libs.zstd.jni)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.squircle)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.shader)
}
