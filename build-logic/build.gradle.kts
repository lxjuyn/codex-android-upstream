plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("toolchain") {
            id = "codex.toolchain"
            implementationClass = "codex.toolchain.ToolchainPlugin"
        }
        register("native") {
            id = "codex.native"
            implementationClass = "codex.native.NativePlugin"
        }
    }
}
