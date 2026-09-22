package codex.toolchain

import java.io.File
import java.util.Properties

/**
 * Cross-compilation environment derived from the Android NDK.
 *
 * Mirrors the environment the old `toolchain/env.sh` exported: the NDK clang
 * wrappers, the common optimization flags and the per-ABI triple names.
 */
data class NdkEnv(
    val ndkHome: String,
    val ndkBin: String,
    val sysroot: String,
    val api: Int,
    val abi: String,
) : java.io.Serializable {
    private val clangPrefix: String
        get() = when (abi) {
            "arm64-v8a" -> "aarch64-linux-android"
            "armeabi-v7a" -> "armv7a-linux-androideabi"
            "x86_64" -> "x86_64-linux-android"
            "x86" -> "i686-linux-android"
            else -> error("unsupported abi: $abi")
        }

    val crossHost: String
        get() = when (abi) {
            "arm64-v8a" -> "aarch64-linux-android"
            "armeabi-v7a" -> "armv7-linux-androideabi"
            "x86_64" -> "x86_64-linux-android"
            "x86" -> "i686-linux-android"
            else -> error("unsupported abi: $abi")
        }

    val rustTarget: String get() = crossHost
    val cc: String get() = "$ndkBin/${clangPrefix}${api}-clang"
    val cxx: String get() = "$ndkBin/${clangPrefix}${api}-clang++"
    val ar: String get() = "$ndkBin/llvm-ar"
    val ranlib: String get() = "$ndkBin/llvm-ranlib"
    val nm: String get() = "$ndkBin/llvm-nm"
    val strip: String get() = "$ndkBin/llvm-strip"
    val objcopy: String get() = "$ndkBin/llvm-objcopy"
    val readelf: String get() = "$ndkBin/llvm-readelf"
    val cmakeToolchain: String get() = "$ndkHome/build/cmake/android.toolchain.cmake"

    val cFlags: String =
        "-O3 -fPIC -std=gnu17 -flto -fomit-frame-pointer -ffunction-sections -fdata-sections"
    val cxxFlags: String =
        "-O3 -fPIC -std=gnu++17 -flto -fomit-frame-pointer -ffunction-sections -fdata-sections"
    val ldFlags: String = "-Wl,--build-id=sha1 -flto -Wl,--gc-sections"
}

fun hostTag(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("mac") -> if (arch == "aarch64") "darwin-arm64" else "darwin-x86_64"
        arch == "aarch64" -> "linux-aarch64"
        else -> "linux-x86_64"
    }
}

private fun localProperties(rootDir: File): Properties {
    val properties = Properties()
    val file = File(rootDir, "local.properties")
    if (file.isFile) file.inputStream().use { properties.load(it) }
    return properties
}

private fun versionKey(name: String): List<Int> =
    name.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }

private fun newestNdk(sdk: File): File? {
    val ndkDir = File(sdk, "ndk")
    if (!ndkDir.isDirectory) return null
    return ndkDir.listFiles { file -> file.isDirectory }
        ?.maxByOrNull { versionKey(it.name).joinToString(".") { part -> part.toString().padStart(6, '0') } }
}

/** Locate the NDK the same way the old scripts did (env, local.properties, SDK dirs). */
fun detectNdk(rootDir: File, api: Int, abi: String): NdkEnv {
    val tag = hostTag()
    val candidates = mutableListOf<File>()

    System.getenv("ANDROID_NDK_HOME")?.takeIf { it.isNotBlank() }?.let { candidates += File(it) }
    System.getenv("ANDROID_NDK_ROOT")?.takeIf { it.isNotBlank() }?.let { candidates += File(it) }

    val properties = localProperties(rootDir)
    properties.getProperty("ndk.dir")?.takeIf { it.isNotBlank() }?.let { candidates += File(it) }

    val sdkDirs = mutableListOf<File>()
    properties.getProperty("sdk.dir")?.takeIf { it.isNotBlank() }?.let { sdkDirs += File(it) }
    System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }?.let { sdkDirs += File(it) }
    System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }?.let { sdkDirs += File(it) }
    sdkDirs += File(System.getProperty("user.home"), "Android/Sdk")
    sdkDirs += File("/opt/android-sdk")
    candidates += sdkDirs.mapNotNull { newestNdk(it) }

    val ndk = candidates.firstOrNull { candidate ->
        File(candidate, "toolchains/llvm/prebuilt/$tag/bin").isDirectory
    } ?: error("Android NDK not found: set ANDROID_NDK_HOME or sdk.dir in local.properties")

    val prebuilt = File(ndk, "toolchains/llvm/prebuilt/$tag")
    return NdkEnv(
        ndkHome = ndk.absolutePath,
        ndkBin = File(prebuilt, "bin").absolutePath,
        sysroot = File(prebuilt, "sysroot").absolutePath,
        api = api,
        abi = abi,
    )
}
