# Android 工具链

交叉编译进 APK、供 `codex-core` spawn 的命令行工具。项目结构与构建入口见根目录
[README.md](../README.md)，当前待办见 [TODO.md](TODO.md)。

## Toolchain（给 codex-core 提供的环境）

`codex-core` 会 spawn `bash` / `git` / `rg` 等外部命令；Android 上 `/system/bin` 只有
toybox 且 app 私有目录不可执行（targetSdk ≥ 29 的 W^X）。因此这些工具用 NDK
交叉编译成 aarch64 可执行文件，并以 `lib<name>.so` 形式放进 APK 的 jniLibs，安装时
被解包到 app 的 native library 目录（唯一可执行的位置）；运行时在 app 私有目录里用
symlink 重建目录树并注入 PATH。

### 构建

构建入口只有 Gradle，一个工具的完整流程是 `prepare<Name>`（取源码）→ `build<Name>`
（configure/make/cmake/cargo/go → `toolchain/build/prefix/<name>/`）→ `packJniLibs`
（合并所有 prefix 到 `toolchain/out/android/<abi>/`）：

```bash
./gradlew :toolchain:buildToolchain :toolchain:packJniLibs   # 全部工具
./gradlew :toolchain:buildGit                                # 单个工具
./gradlew :toolchain:buildGit --rerun-tasks                  # 强制重建
./gradlew :app:assembleDebug -PskipToolchainBuild            # 跳过工具链（要求产物已存在）
```

- 每个工具构建进自己的 prefix，`build<Name>` 用 fingerprint（源码 revision + 补丁 +
  配方 + NDK）判断是否跳过；删 `toolchain/build/prefix/<name>/` 或加 `--rerun-tasks` 强制重建。
- 下载缓存在 `toolchain/build/downloads/`，源码 worktree 在 `toolchain/build/src/<name>/`，
  日志在 `toolchain/build/logs/<name>.log`。
- `-PtoolchainJobs=N` 控制并行度，`-PandroidApi=36` 换 API，`-Pabi=` 目前只支持 `arm64-v8a`。
- `packJniLibs` 对进 jniLibs 的每个 ELF 做 16 KB 页对齐门禁：LOAD 段的 `p_align` 必须是
  `0x4000` 的整数倍（Go 工具链用的 `0x10000` 也通过，非 ELF 的脚本文件跳过），否则直接失败——
  16 KB 页的设备无法 exec / dlopen 未对齐的库（`:native:buildJni` 对自己的 `.so` 同样校验）。

产物在 `toolchain/out/android/<abi>/`：

```
jniLibs/lib*.so          # 拷进 app 的 jniLibs（stageRuntime 生成）
assets/toolchain/...     # 数据文件（python stdlib、git templates、cacert.pem）
native-manifest.txt      # 运行时重建目录树的清单
```

### 源码来源

| 方式 | 工具 | 规则 |
| --- | --- | --- |
| git submodule（`third_party/`，固定 tag） | git、curl、openssl、openssh、sqlite、libffi、zstd、xz、bzip2、vim(xxd)、tree、7zip、procps、file、jq、ripgrep、uv、ruff、ast-grep、fd、yq、shfmt、llvm(clang-format)、binutils、python(cpython) | 在 `toolchain/build/src/<tool>` 的 `git worktree` 上构建，子模块保持干净；补丁在 worktree 上应用。LLVM/Binutils 用 CERNET git 镜像，其余为上游仓库 |
| release tarball（Gradle 下载） | bash、coreutils、grep、sed、make、gawk、diffutils、findutils、patch、tar、gzip、bc、which、unzip、zip | GNU 的 git 树没有生成好的 `configure`（tarball 自带），Info-ZIP 上游没有仓库；默认 CERNET（`gnu/` 镜像 / Debian pool 的 orig 包），失败回退 ftp.gnu.org / SourceForge |
| 上游预编译 | bun | 唯一例外：上游发布 `bun-linux-aarch64-android.zip`（Android target）；`bunx` 由配方写成 `bun x` 包装脚本 |
| 宿主工具 | gofmt | 直接来自宿主 Go 发行版（`GOOS=android` 交叉编译） |

`-PgnuMirror=` 覆盖下载镜像（默认 `https://mirrors.cernet.edu.cn`）。
换 submodule tag 用 `git -C third_party/<name> checkout <tag>` 后提交 gitlink；换 tarball
版本改 `toolchain/build.gradle.kts` 顶部的版本常量。

### 多语言扩展

`build-logic/src/main/kotlin/codex/toolchain/` 提供与语言无关的 Step DSL
（`Run`/`Configure`/`Make`/`Cmake`/`Cargo`/`Go`/`Copy`/`Symlink`/`Remove`/`Replace` 等），
Gradle 只把每个仓库自己的构建命令按顺序跑一遍。新增工具 = 在 `toolchain/build.gradle.kts`
里加一个 `tool { source = …; recipe { … } }` 块；确实需要新语言钩子时在 `build-logic` 里加
一个 Step 类型，而不是新开 shell 脚本。

当前工具（arm64-v8a，API 36，`-O3 -flto` 尽可能开启）：

| 类别 | 工具 |
| --- | --- |
| shell | `bash` 5.3（`sh` 是 bash 的 POSIX 模式） |
| VCS | `git` 2.55（https/ssh remote，builtins + libexec 脚本） |
| 文件/文本 | GNU `coreutils` 9.12（`ls` `cp` `mv` `rm` `cat` `sort` `head` `tail` `stat` `md5sum` `base64` `timeout` …，单二进制多命令）、GNU `grep` 3.12、GNU `findutils` 4.11（`find` `xargs`）、`rg` 15.2、GNU `sed` 4.10、`gawk` 5.4、GNU `diffutils` 3.12、GNU `patch` 2.8、`tree` 2.3、`xxd`、`which` 2.25、`bc`/`dc` 1.08 |
| 网络 | `curl` 8.22（HTTPS）、`ssh`/`scp`/`sftp`/`ssh-keygen`（OpenSSH 10.5）、`openssl` 3.5 |
| 语言 | `python` 3.14.7（ssl/zlib/ctypes/hashlib/sqlite3）、`uv` 0.12（venv/纯 Python 包）、`bun` 1.4 + `bunx`（上游官方 Android 预编译） |
| 代码分析/修改 | `clang-format` 23（C/C++/Java/JS/JSON 格式化）、`ruff` 0.16（Python lint/format）、`ast-grep` 0.45（结构化搜索/替换，别名 `sg`）、`fd` 10.5、`shfmt` 3.14、`gofmt`、`yq` 4.53（YAML/JSON） |
| 归档 | GNU `tar` 1.35、`gzip` 1.14、`bzip2` 1.0.8、GNU `xz` 5.8、`zstd` 1.5、Info-ZIP `zip`/`unzip`/`zipinfo` 6.0、`7zz` 26.03 |
| 进程/数据 | `procps-ng` 4.0（`ps` `kill` `pgrep` `pkill` `pidof` `free` `uptime` `pmap` `vmstat`）、`make` 4.4、`sqlite3` 3.53、`jq` 1.8、`file` 5.46 |
| 二进制工具 | binutils 2.47：`readelf` `objdump` `nm` `strings` `objcopy` `strip` `ar` `as` `ld` `addr2line` `size` `c++filt` |

arm64-v8a 的 jniLibs 目前约 363MB（bun 83、ast-grep 46、uv 42、ruff 19、yq 14 占大头）；
不需要的工具把 `toolchain/build.gradle.kts` 里对应的 `tool { }` 块删掉再跑
`:toolchain:packJniLibs` 即可（prefix 里的产物也可手动删）。

### 重复工具的取舍

同样能力只保留一个实现，优先「上游有 Android 成品 / 不需要打 patch / 性能强 / 体积小 /
依赖少」：

- **JS 运行时：只留 `bun`，不编 node。** bun ≥ 1.3.14 有官方 `bun-linux-aarch64-android`
  预编译（`toolchain/build.gradle.kts` 的 bun 配方只解包，零交叉编译），二进制仅依赖
  `libc/libm/libdl`；`bun x` 自己解释 JS，不经过 shebang，所以在 Android（没有 `/usr/bin/env`）上
  跑 CLI / MCP server 比 npm 更稳。node 需要 V8 + 完整 LTO 的交叉编译、维护 3 个补丁、
  还多出 ~110MB，收益与 bun 重叠，遂移除。
  上游 Android 包只发 `bun` 一个二进制，`bunx` 由配方包装成 `bun x`；不提供 `npx` 别名，
  MCP 配置直接写 `bunx <pkg>` 或 `bun x <pkg>`。
- **下载工具：只留 `curl`。** `wget` 需要 Android patch、与 curl 功能重叠，未加入；
  纯 HTTP 场合用 `curl`，只要 TCP 连通性时用 bash 的 `/dev/tcp`。
- **不用 busybox：** POSIX/GNU 命令全部用上游实现，applet 的裁剪实现与选项/输出差异
  正是要避免的：
  - `coreutils` 以 `--enable-single-binary=symlinks` 编成单个 `coreutils` + 每命令一个
    symlink（`ls`/`cp`/`mv`/`rm`/`cat`/`sort`/…），GNU 语义、只占一份体积；`hostid`
    用 `toolchain/patches/coreutils/` 补上 bionic 缺失的 `gethostid`。
  - `grep`/`find`/`xargs` 用 GNU 版（完整的 BRE/ERE、`--include`/`--exclude`、
    `find -printf`/`-newerXY`、`xargs -0/-P`；PCRE 用 `rg`）。
  - `ps`/`kill`/`pgrep`/`pkill` 用 procps-ng；`toolchain/patches/procps/` 补 bionic 缺失的
    `strverscmp`/`fopencookie` 与 Android 不开放的 `/proc/uptime`、`/proc/loadavg`。
  - `unzip`/`zip` 用 Info-ZIP，并带上 `toolchain/patches/unzip/` 里的 Debian 安全与移植补丁
    （上游 6.0 停更于 2009 年）；解压 `.zip` 也可以用 `7zz x`。
  - busybox 的 `xz` 只能解压（`tar -cJf` 直接失败）这类裁剪实现随之移除。
- **不再提供交互/连接类工具：** `less`/`more`/`vi`/`ed`/`top`/`watch` 这类终端 UI 在无
  PTY 的 `command/exec` 下没有使用场景；`nc`/`ping`/`traceroute`/`netstat` 需要额外权限
  或与 `curl`/`/dev/tcp` 重叠，都未加入。
- 其余无重叠：`rg`、`git`、`python`/`uv`、`openssl`、`openssh`、`7zz`、`make`、`sqlite3`、
  `jq`、`file`、`binutils`、`tree`、`xxd`、`which`、`bc`、`bzip2` 各司其职。
- kit 之外的目标（编译器/JDK/Android 构建工具）见文末「刻意不加」。

注意：设备上装不了带 native addon 的 JS/包（没有 clang/node-gyp），bun/npm 类工具链
只能跑纯 JS；`bun install -g` 的全局 bin 落在 `<root>/bin`，缓存默认 `$HOME/.bun`。

### 分析/修改类工具怎么编的

- `clang-format` 从 `third_party/llvm`（LLVM 23 monorepo）编出（`toolchain/build.gradle.kts` 的 `clang-format`
  配方）：交叉编译 LLVM 需要能在构建机上运行的 tablegen，所以先在宿主上编
  `llvm-tblgen`/`clang-tblgen`/`llvm-min-tblgen`，再用 `-DLLVM_NATIVE_TOOL_DIR` 指向它们做
  交叉编译；产物静态链接 libc++，单文件 3.9MB。CMake 步骤不继承 NDK 的
  `CC/CXX/AR/...`（build-logic 的 `hostEnv` 步骤），Android 工具链通过
  `-DCMAKE_TOOLCHAIN_FILE=$NDK_TOOLCHAIN_FILE` 显式传入。
- `ruff`/`ast-grep`/`fd`/`ripgrep`/`uv` 用 cargo 交叉编译（build-logic 的 `cargo` 助手：
  NDK linker + `libgcc` shim + 完整 LTO，uv/ruff 用 1.97.1 工具链）；`yq`/`shfmt`/`gofmt`
  用宿主 Go 交叉编译（`GOOS=android`）。
- 这些工具都只做**读代码/改代码/格式化**，不包含编译器。

### 刻意不加

- **编译器/构建工具**（clang、rustc、cmake、ninja、pkg-config、perl）：设备不做编译，
  加了只增体积（数百 MB）。
- **JDK + Android SDK 构建工具**（aapt2/d8/apksigner/zipalign、Gradle）：这些是 glibc
  程序，bionic 上跑不起来，需要 proot/glibc 层或远端构建，不属于本工具链。

### APK 侧接入

App 的 Gradle 项目就在仓库根。`stageRuntime` 会从工具链产物目录生成
`jniLibs` 和 assets，无需手动复制到源码目录；安装配置（`useLegacyPackaging` +
`extractNativeLibs="true"`）见根目录 README。首次启动时（或版本变化时）在 `filesDir` 里重建环境：

1. 解包 assets/toolchain 到 `files/runtime/toolchain/` 的临时 staging 目录，完成后替换旧工具链；
2. 按 `native-manifest.txt` 建 symlink：
   - `file|lib/python3.14/lib-dynload/_ssl.cpython-314-aarch64-linux-android.so|liblib_python3.14_lib-dynload__ssl.cpython-314-aarch64-linux-android.so.so`
     → `ln -s "$nativeLibraryDir/<第三列>" "<toolchainRoot>/<第二列>"`
   - `link|bin/python3|python3.14` → `ln -s python3.14 <toolchainRoot>/bin/python3`
   - `data|lib/python3.14/ssl.py|` 已由第 1 步放置，忽略
3. 给 codex 的子进程注入环境变量：

```
PATH=<toolchainRoot>/bin:<toolchainRoot>/sbin
SHELL=CODEX_SHELL=<toolchainRoot>/bin/bash
HOME=<filesDir>/home
CODEX_HOME=CODEX_SQLITE_HOME=<filesDir>/home/.codex
GIT_EXEC_PATH=<toolchainRoot>/libexec/git-core
GIT_TEMPLATE_DIR=<toolchainRoot>/share/git-core/templates
PYTHONHOME=<toolchainRoot>
TMPDIR=<filesDir>/tmp
CURL_CA_BUNDLE=SSL_CERT_FILE=<toolchainRoot>/share/cacert.pem
GIT_SSL_CAINFO=<toolchainRoot>/share/cacert.pem
```

注意 `git` 的 https 走静态 libcurl（`git-remote-https`），libcurl 认 `CURL_CA_BUNDLE`，
但 git 自己只认 `GIT_SSL_CAINFO` / `http.sslCAInfo`，两者都要给。

注意：`files/runtime/toolchain` 里的 symlink 指向 native lib 目录中的只读文件，因此 codex 的
`git stash`/`git rebase` 等 libexec shell 脚本、python 的扩展模块都能正常 exec /
dlopen（Android 只拦截**可写**文件的动态加载）。`TMPDIR` 必须指向可写目录，bash
heredoc / git 临时文件依赖它。

`HOME` 与工具链目录分离，升级工具链不会删除用户配置和工作区。运行时通过
`BUN_INSTALL_CACHE_DIR` 等变量将可清理缓存放到 `cacheDir/toolchain`。
`bun`/`bunx` 通过 jniLibs 的 symlink 运行。
