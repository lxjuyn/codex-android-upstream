# Codex Android

把 [openai/codex](https://github.com/openai/codex) 的 Rust 内核以 JNI 库编进 Android Compose 应用：
UI 通过上游 app-server 协议访问同进程内的 `codex-core`，命令交给 App 内置的 Android 工具链执行。

整个仓库是一个 Gradle 工程：Kotlin UI、codex JNI（cargo）与 40 多个交叉编译的命令行工具
（autotools / make / cmake / cargo / go）都由 Gradle 统一编排，一条命令直接产出 APK。
各上游仓库保留自己的构建方式，Gradle 只负责取源码、配好 NDK 环境并按顺序调用。

## 目录

```text
settings.gradle.kts      Gradle 根：:app / :toolchain / :native
build-logic/             约定插件：多语言工具构建、打包与 JNI 构建（Kotlin，无 shell 编译脚本）
app/                     Compose UI、协议客户端、Android 运行时
native/                  JNI bridge、codex-helper、Rust 交叉编译与补丁
toolchain/               工具链声明（build.gradle.kts）、补丁、构建产物
third_party/             上游源码仓库（git submodule，固定发布 tag）
codex/                   上游 openai/codex Git submodule，固定提交
docs/                    工具链说明与待办
scripts/                 真机冒烟脚本
```

界面直接使用 [miuix](https://github.com/compose-miuix-ui/miuix) 的按钮、输入框、卡片、
偏好行和弹层；复杂布局在业务页面组合这些组件。`app/src/main/kotlin/com/cy/codex/style.kt`
只保留弹层与页面共用的边距计算，尺寸与颜色分别由 `UiConsts` 和主题提供。
Compose UI 中有上游对应的移植文件按 `codex/codex-rs/tui/src/` 的模块路径和文件名放置，目录
模块沿用 `mod.kt`；测试使用 snake_case 的 `<subject>_tests.kt` 并跟随同一模块。Android 专属
页面、协议和运行时代码继续放在各自的现有目录。

## 构建

需要 JDK 21、Android SDK 37、NDK r30、Rust 1.95.0 与 1.97.1（`rustup`）及两者的
`aarch64-linux-android` target、1.95.0 的 `rust-src` 组件，宿主还需要 `cmake`、`ninja`、
Go 1.27（yq/shfmt/gofmt），以及 binutils 从 git 构建用的 `autoconf`/`bison`/`flex`。
首次克隆后执行 `git submodule update --init --recursive`（`third_party/` 下 25 个上游仓库，
其中 `llvm`/`binutils` 历史较大）。

```bash
# Kotlin + 工具链 + codex JNI + 打包，直接产出 APK
./gradlew :app:assembleDebug

# 只迭代 Kotlin：跳过工具链与 JNI（要求产物已存在）
./gradlew :app:testDebugUnitTest -PskipToolchainBuild -PskipNativeBuild

# 只做工具链 / 只做 JNI
./gradlew :toolchain:buildToolchain :toolchain:packJniLibs
./gradlew :native:buildJni
```

源码获取方式（Gradle 任务，详见 [docs/toolchain.md](docs/toolchain.md)）：

- 需要编译、上游有仓库：`third_party/` 里的 git submodule 固定发布 tag，在
  `toolchain/build/src/<tool>` 的 `git worktree` 里构建，子模块本身保持干净；补丁打在 worktree 上。
  LLVM/Binutils 用 CERNET 的 git 镜像，其余为上游官方仓库。
- GNU 工具与 Info-ZIP 下载 release tarball（git 树没有生成好的 `configure`）：默认走 CERNET 的
  `gnu/` 镜像与 Debian pool 的 orig 包，失败回退 ftp.gnu.org / SourceForge；`-PgnuMirror=` 可换镜像。
- 上游已提供 Android 二进制（bun）：直接下载解包；其余一概源码编译。
- 每个工具构建进 `toolchain/build/prefix/<tool>/`，`:toolchain:packJniLibs` 再合并成
  `toolchain/out/android/<abi>/`；已完成的工具按 fingerprint 跳过，删掉对应 prefix 即强制重建。

Android 必要兼容补丁位于 `native/patches/`，`:native:prepareUpstream` 在
`native/build-upstream/` 独立工作树应用，`codex/` 保持上游原样。标准库补丁、helper 与运行时契约见
[native/README.md](native/README.md)。

APK：`app/build/outputs/apk/debug/app-debug.apk`。应用 ID 与包名均为 `com.cy.codex`。
`assemble*` 结束后会执行 `verify*ToolchainAssets`，逐条核对 `native-manifest.txt` 里每个 `data`
条目确实进了 APK——工具链必须原样打包，资产合并静默丢文件会直接失败在构建期。

## 运行架构

```text
Compose UI / CodexApp
  -> JsonRpcAppServerClient
  -> NativeRpcTransport / NativeBridge
  -> codex-app-server::in_process
  -> codex-core / exec-server
  -> App 内置 bash、git、rg、python、bun 等工具
```

- `CodexApplication` 持有会话与协程，Activity 重建不会重启 native 会话。
- 后端固定 Embedded(in-process) 一种：没有 LocalDaemon/Remote 的选择、endpoint 发现与
  websocket 通信（上游 `tui/src/lib.rs` 的 `AppServerTarget` 另外两种不采用）。
- JNI 启动在 IO 线程完成，使用上游 `initialize` / `initialized` 握手。
- 通信在 JNI 边界只编解码一次 JSON：消息以 UTF-8 字节（`ByteArray`）传递，并带
  `JsonRpcMessageKind`（request/notification/response/error）标记。Rust 按标记用 `serde_json`
  直接反序列化成 typed `ClientRequest`/`ClientNotification`（不再解析 JSON-RPC 信封再转一次
  `Value`），事件用 `serde_json::to_vec` 从 typed `ServerNotification` 一次写出；Kotlin 侧用
  kotlinx.serialization 的 `JsonElement` 解析/编码一次。字节传输同时避免了 Java 字符串的
  Modified UTF-8 转换，非 BMP 字符（emoji）不再经过变更编码。
- 无本地演示账户、示例项目或脚本回放；配置、模型、会话和消息来自真实服务端。
- 未登录也能启动，模型请求需要有效账户：账户页面提供 ChatGPT 设备码与 API key 两种登录。
  宿主机 Codex 凭据不会复制到设备。
- Android 进程被系统终止后，下次启动重新连接并读取磁盘上的会话；没有后台常驻保证。
- 命令运行在 Android App UID 的权限边界内。Android 不支持上游 Linux namespace
  沙箱，因此嵌入运行时使用 `danger-full-access`；这不代表拥有 root 或其他 App 的私有数据权限。
- 协议方法、通知与类型以 `codex/codex-rs/app-server-protocol` 为准；上游客户端请求都已绑定
  （测试用的 `mock/experimentalMethod` 除外），`AppServerClientBindingTest` 离线校验没有
  方法落到 `unsupported()` 默认值；尚未覆盖的能力与全部待办见 [docs/TODO.md](docs/TODO.md)。

## 私有数据

```text
files/runtime/toolchain/  APK 数据与指向 nativeLibraryDir 的工具 symlink
files/home/              HOME
files/home/.codex/       CODEX_HOME，配置、认证与会话记录
files/home/.codex/config.toml 首次生成、之后保留的用户配置
files/home/.codex/log/   Codex 日志目录
files/home/.config/     XDG_CONFIG_HOME
files/home/.local/      XDG 数据和状态
files/workspaces/default/ 默认工作目录
files/tmp/               临时文件
cache/toolchain/         可清理的工具缓存
```

工具链更新采用独立 staging 目录，保留 HOME、配置和工作区。APK 使用
`extractNativeLibs=true` 和 `useLegacyPackaging=true`，使 Android 可以从安装目录执行
内置工具。`PATH`、`SHELL`、`PYTHONHOME`、`GIT_EXEC_PATH`、`GIT_SSL_CAINFO`、
`SSL_CERT_FILE` 等由运行时注入，完整清单与 symlink 规则见 [docs/toolchain.md](docs/toolchain.md#apk-侧接入)。
账户及工作区数据不参与 Android 自动备份。

## 验证

```bash
./gradlew :app:testDebugUnitTest -PskipToolchainBuild -PskipNativeBuild
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  com.cy.codex.test/com.cy.codex.runtime.RuntimeSmokeInstrumentation
```

真机测试在真实 App UID 下验证工具链安装、JNI 启动、账户/配置/模型/会话读取、文件读写、
`command/exec`、apply_patch、新建会话、shell 消息流和重启后恢复，不发送模型请求。模型生成和
登录需要有效账户及网络连接，不能由离线测试替代。

`scripts/device-smoke-test.sh -PskipNativeBuild` 可执行同一套打包、安装和验证，报告写到
`artifacts/device-smoke.txt`。`./gradlew :native:hostSmokeTest` 使用临时 CODEX_HOME 验证宿主机上的
真实内核与 helper；`native/file-lock-smoke-test.sh` 在设备上验证 Android 标准库的互斥锁、共享锁、
竞争和释放。工具链自身的真机冒烟见 `toolchain/device-smoke-test.sh`。

仅修改 JVM 层时，可执行
`./gradlew :app:testDebugUnitTest -x :app:stageRuntime -PskipToolchainBuild -PskipNativeBuild`，
该命令不产生可安装 APK，也不验证 JNI。

## 文档

- [docs/TODO.md](docs/TODO.md)：当前待办与已知功能缺口
- [docs/toolchain.md](docs/toolchain.md)：工具链构建、工具清单与取舍
- [native/README.md](native/README.md)：JNI/helper 构建、补丁与运行时契约
- [AGENTS.md](AGENTS.md)：在本仓库工作时的规范
