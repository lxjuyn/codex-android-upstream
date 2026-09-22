# AGENTS

本文件只写在本仓库工作时的规范。架构、构建与运行见 [README.md](README.md)，
待办见 [docs/TODO.md](docs/TODO.md)。

## 改动边界

| 路径 | 内容 | 规则 |
| --- | --- | --- |
| `codex/` | 上游 openai/codex submodule，固定提交 | 只读。Android 兼容改动做成 patch 放 `native/patches/` |
| `third_party/` | 各上游源码 submodule，固定发布 tag | 只读。需要的改动做成 patch 放 `toolchain/patches/`，在 worktree 上应用 |
| `build-logic/` | Gradle 约定插件：工具构建、打包、JNI | 多语言支持在这里扩展，不放单个工具的配方 |
| `toolchain/` | 工具链声明（`build.gradle.kts`）、补丁、产物 | 一个工具一个 `tool { }` 块；下载源与版本常量集中在文件头部 |
| `native/` | JNI bridge、codex-helper、Rust 交叉编译 | Rust 构建配方在 `build-logic` 的 `NativePlugin`/`NativeTasks` |
| `app/` | Compose UI、协议客户端、Android 运行时 | 协议形状以 `codex/codex-rs/app-server-protocol` 为准 |

## 约定

- **上游优先**：行为、字段名、枚举 wire 值都对齐 `codex-rs`；新增 wire 字段前先查
  `app-server-protocol` 的 schema，不要发明字段。
- **不重写上游编译方式**：Gradle 只准备源码、环境并按顺序调用 configure/make/cmake/cargo/go。
  补丁放 `toolchain/patches/<tool>/`，在 `toolchain/build/src/<tool>` 的 worktree 上应用，
  submodule 本身保持干净。
- **源码获取**：需要编译且有仓库的上游用 `third_party/` submodule 固定 tag；GNU 工具与 Info-ZIP
  下载 release tarball（GNU 的 git 树没有生成好的 `configure`），默认走 CERNET 的 `gnu/` 镜像，
  失败回退 ftp.gnu.org，`-PgnuMirror` 可换镜像。上游已发 Android 二进制的（bun）直接下载 release。
- **不加 shell 编译脚本**：构建入口只有 Gradle；`scripts/`、`toolchain/device-smoke-test.sh`
  只做打包调用与 adb 编排。
- **不提交构建产物**：`toolchain/out/`、`toolchain/build/`、`native/out/`、`native/target/`、
  `**/build/`、`artifacts/`、`local.properties` 都不入库；APK 内的 jniLibs 与 assets 由
  `packJniLibs`/`stageRuntime` 生成。
- **文档中文、代码注释英文**；注释写「为什么」，引用上游时给 `codex-rs/...` 路径。
- **文档只写当前有效的事实**：完成的条目从待办里删除，不要留成已勾选的历史。
- **Kotlin 块注释会嵌套**：注释正文里不要出现 `/*`（路径写成 `config/…`），
  `SourceCommentTest` 会拦下这类静默吞掉整个文件的写法。
- 改工具链时同步三处：`toolchain/build.gradle.kts` 的 `tool { }`、README 工具表、
  `toolchain/device-smoke-test.sh`；换 submodule tag 或下载源时同时更新 `docs/toolchain.md`。
- 改协议或客户端时同步三处：`app/src/main/kotlin/com/cy/codex/protocol/**` 的 wire 类型、
  `json_rpc_app_server_client.kt` 的绑定、`app/src/test` 的 JVM 测试。
- 优先复用 `miuix` 已有组件，遵循 `miuix` 风格。

## 验证

| 改动范围 | 命令 |
| --- | --- |
| Kotlin | `./gradlew :app:testDebugUnitTest -PskipToolchainBuild -PskipNativeBuild` |
| 全量 APK | `./gradlew :app:assembleDebug` |
| toolchain | `./gradlew :toolchain:buildToolchain :toolchain:packJniLibs`（单工具 `:toolchain:buildGit`） |
| native / Rust | `./gradlew :native:buildJni`，宿主内核验证 `./gradlew :native:hostSmokeTest` |
| 真机集成 | `bash scripts/device-smoke-test.sh -PskipNativeBuild` |

模型生成与登录需要真实账户和网络，离线测试不能替代。
