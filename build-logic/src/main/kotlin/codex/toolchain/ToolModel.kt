package codex.toolchain

/**
 * Where a tool's source comes from. Repositories that need to be compiled are
 * pinned as git submodules; projects that only publish release tarballs are
 * downloaded; prebuilt Android releases and host-provided tools have no source.
 */
data class SourceSpec(
    val kind: Kind,
    /** Submodule path under `third_party/`, or the tarball URL. */
    val value: String = "",
    /** Extract with this many leading path components stripped. */
    val stripComponents: Int = 1,
    /** Also initialise the repo's own submodules (jq vendors oniguruma). */
    val recursive: Boolean = false,
    /** Build inside the submodule checkout instead of a disposable worktree. */
    val inPlace: Boolean = false,
    /** Second URL tried when the (mirror) download fails. */
    val fallback: String = "",
    /**
     * Release tag to clone with `--depth 1` for very large repositories
     * (llvm/binutils/cpython); empty means a full-history clone.
     */
    val tag: String = "",
) : java.io.Serializable {
    enum class Kind { SUBMODULE, TARBALL, HOST }

    val label: String
        get() = when (kind) {
            Kind.SUBMODULE -> "submodule:$value"
            Kind.TARBALL -> "tarball:$value"
            Kind.HOST -> "host"
        }

    companion object {
        fun submodule(
            path: String,
            recursive: Boolean = false,
            inPlace: Boolean = false,
            tag: String = "",
        ) = SourceSpec(Kind.SUBMODULE, path, recursive = recursive, inPlace = inPlace, tag = tag)

        fun tarball(url: String, stripComponents: Int = 1, fallback: String = "") =
            SourceSpec(Kind.TARBALL, url, stripComponents = stripComponents, fallback = fallback)

        fun host() = SourceSpec(Kind.HOST)
    }
}

/** A single build/install action. All file paths support `$VAR` placeholders. */
sealed interface Step : java.io.Serializable {
    data class Run(
        val argv: List<String>,
        val env: Map<String, String> = emptyMap(),
        val workdir: String = "\$SRC",
        /** Skip when this path (relative to the workdir) already exists. */
        val skipIfExists: String? = null,
        /** Run without the NDK cross environment (host tools, cmake, go, cargo). */
        val hostEnv: Boolean = false,
        /** Split expanded arguments on whitespace (compiler flag variables). */
        val splitArgs: Boolean = false,
    ) : Step

    /** Run only when [checkFile] (relative to the workdir) is missing. */
    data class RunFirstTime(
        val argv: List<String>,
        val checkFile: String,
        val env: Map<String, String> = emptyMap(),
        val workdir: String = "\$SRC",
        val hostEnv: Boolean = false,
    ) : Step

    data class Mkdir(val paths: List<String>) : Step
    data class Remove(val paths: List<String>) : Step
    data class Copy(val from: String, val to: String, val executable: Boolean = false) : Step
    data class Symlink(val link: String, val target: String) : Step
    data class ReplaceInFile(
        val file: String,
        val regex: String,
        val replacement: String,
        /** Allow `$1` group references in [replacement]. */
        val groupRefs: Boolean = false,
    ) : Step
    data class WriteFile(val path: String, val content: String, val executable: Boolean = false) : Step
    data class Download(val url: String, val dest: String, val fallback: String = "") : Step
    data class Strip(val dir: String = "\$PREFIX/bin") : Step
}

class ToolSpec(val name: String) {
    var source: SourceSpec = SourceSpec.host()
    /** Directory under `toolchain/patches/`; defaults to the tool name when it exists. */
    var patchDir: String? = null
    /** Include this tool's prefix in the APK toolchain package. */
    var pack: Boolean = true
    /** Tool names whose build tasks must run first. */
    val dependsOn = mutableListOf<String>()
    val steps = mutableListOf<Step>()

    fun recipe(block: RecipeBuilder.() -> Unit) {
        RecipeBuilder(steps).block()
    }
}

/**
 * Declarative helpers for the tool recipes. Everything ends up as [Step] data,
 * so the recipes stay configuration-cache safe: closures only run while the
 * build is configured.
 */
class RecipeBuilder(private val sink: MutableList<Step>) {
    fun run(
        vararg argv: String,
        env: Map<String, String> = emptyMap(),
        workdir: String = "\$SRC",
        skipIfExists: String? = null,
        hostEnv: Boolean = false,
        splitArgs: Boolean = false,
    ) {
        sink += Step.Run(argv.toList(), env, workdir, skipIfExists, hostEnv, splitArgs)
    }

    fun runFirstTime(
        vararg argv: String,
        checkFile: String,
        env: Map<String, String> = emptyMap(),
        workdir: String = "\$SRC",
        hostEnv: Boolean = false,
    ) {
        sink += Step.RunFirstTime(argv.toList(), checkFile, env, workdir, hostEnv)
    }

    /** `./configure` with the usual `config.status` guard. */
    fun configure(
        vararg args: String,
        env: Map<String, String> = emptyMap(),
        skipIfExists: String = "config.status",
        hostEnv: Boolean = false,
    ) {
        run("./configure", *args, env = env, skipIfExists = skipIfExists, hostEnv = hostEnv)
    }

    fun make(
        vararg args: String,
        targets: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
        workdir: String = "\$SRC",
        skipIfExists: String? = null,
        hostEnv: Boolean = false,
    ) {
        run(
            "make", "-j\$JOBS", *targets.toTypedArray(), *args,
            env = env, workdir = workdir, skipIfExists = skipIfExists, hostEnv = hostEnv,
        )
    }

    /** `make DESTDIR=<destDir> install` (autotools install staged into a prefix). */
    fun makeInstall(
        destDir: String,
        vararg args: String,
        env: Map<String, String> = emptyMap(),
        targets: List<String> = listOf("install"),
        workdir: String = "\$SRC",
        hostEnv: Boolean = false,
    ) {
        run(
            "make", "-j\$JOBS", *targets.toTypedArray(), "DESTDIR=$destDir", *args,
            env = env, workdir = workdir, hostEnv = hostEnv,
        )
    }

    fun cmakeConfigure(
        source: String,
        build: String,
        vararg args: String,
        env: Map<String, String> = emptyMap(),
        skipIfExists: String? = "build.ninja",
    ) {
        run(
            "cmake", "-S", source, "-B", build, "-G", "Ninja", *args,
            env = env, workdir = "\$SRC", skipIfExists = skipIfExists?.let { "$build/$it" }, hostEnv = true,
        )
    }

    fun cmakeBuild(build: String, vararg targets: String) {
        val targetArgs = targets.flatMap { listOf("--target", it) }
        run("cmake", "--build", build, "--parallel", "\$JOBS", *targetArgs.toTypedArray(), hostEnv = true)
    }

    /**
     * `cargo build --release` with the NDK linker, the `libgcc` shim some
     * crates need and an explicit toolchain (uv/ruff need a newer rustc than
     * the JNI core's 1.95).
     */
    fun cargo(
        vararg args: String,
        manifest: String = "\$SRC/Cargo.toml",
        toolchain: String? = null,
        env: Map<String, String> = emptyMap(),
    ) {
        sink += Step.Mkdir(listOf("\$BUILD/libgcc-shim"))
        sink += Step.Symlink("\$BUILD/libgcc-shim/libgcc.a", "\$LIBCLANG_RT")
        val cargoEnv = mutableMapOf(
            "CARGO_TARGET_DIR" to "\$BUILD/cargo-target",
            "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER" to "\$CC",
            "CC_aarch64_linux_android" to "\$CC",
            "AR_aarch64_linux_android" to "\$AR",
            "RANLIB_aarch64_linux_android" to "\$RANLIB",
            "CARGO_PROFILE_RELEASE_LTO" to "fat",
            "CARGO_PROFILE_RELEASE_CODEGEN_UNITS" to "1",
            "CARGO_PROFILE_RELEASE_OPT_LEVEL" to "3",
            "RUSTFLAGS" to "-Lnative=\$BUILD/libgcc-shim",
        )
        toolchain?.let { cargoEnv["RUSTUP_TOOLCHAIN"] = it }
        cargoEnv.putAll(env)
        run(
            "cargo", "build", "--release", "--target", "\$RUST_TARGET",
            "--manifest-path", manifest, *args,
            env = cargoEnv,
        )
    }

    /** `go build` with `GOOS=android` (pure-Go tools need no NDK). */
    fun go(args: List<String>, env: Map<String, String> = emptyMap()) {
        run(
            "go", *args.toTypedArray(),
            env = mapOf("GOOS" to "android", "GOARCH" to "arm64", "CGO_ENABLED" to "0", "GOCACHE" to "\$BUILD/go-cache") + env,
            hostEnv = true,
        )
    }

    fun mkdir(vararg paths: String) {
        sink += Step.Mkdir(paths.toList())
    }

    fun remove(vararg paths: String) {
        sink += Step.Remove(paths.toList())
    }

    fun copy(from: String, to: String, executable: Boolean = false) {
        sink += Step.Copy(from, to, executable)
    }

    fun symlink(link: String, target: String) {
        sink += Step.Symlink(link, target)
    }

    fun writeFile(path: String, content: String, executable: Boolean = false) {
        sink += Step.WriteFile(path, content, executable)
    }

    fun replaceInFile(file: String, regex: String, replacement: String, groupRefs: Boolean = false) {
        sink += Step.ReplaceInFile(file, regex, replacement, groupRefs)
    }

    fun download(url: String, dest: String, fallback: String = "") {
        sink += Step.Download(url, dest, fallback)
    }

    fun strip(dir: String = "\$PREFIX/bin") {
        sink += Step.Strip(dir)
    }
}
