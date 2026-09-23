# TODO

与上游 TUI 逐模块对比后的缺口（基线：当前 `codex/` submodule；范围 `tui/src/bottom_pane/`、
`tui/src/chatwidget/` + `history_cell/` + `streaming/`、app 级模块与 slash 命令），
以及对比中发现的协议解析/绑定缺陷。已完成能力见 [README.md](../README.md) 与
[docs/toolchain.md](toolchain.md)；终端专有、平台与工具链约束项见第 6 节。

约定：路径相对仓库根；引用上游一律用 `codex/codex-rs/...`；改协议按 `AGENTS.md` 同步
wire 类型、`json_rpc_app_server_client.kt` 绑定与 JVM 测试三处；每条完成后删除。

## 0. 协议解析与绑定缺陷（改动小、用户直接可见，优先）

`wire_codec.kt` 的 `item()` 变体与字段解析已逐条对齐上游（19 个变体、字段名与 camelCase
拼写、`UserInput.Image` 的 `{url} | {fileId}` 联合）；`app/src/test/.../wire_codec_tests.kt`
覆盖这些解析点。以下为仍未接线的部分。

- [ ] **审批 display params 与持久化选择未解析**：`_meta` 本身已读取并回显（elicitation 路径，
      `json_rpc_app_server_client.kt`、`bottom_pane/mcp_server_elicitation.kt`），缺的是 `_meta`
      之上的四种消费：`codex_approval_kind=mcp_tool_call` 派生的空表单 ApprovalAction
      （Allow / Allow for this session / Always allow，由 `persist` 决定出现哪些）、
      `tool_suggestion` + `tool_type`/`suggest_type`/`install_url` 的 Install/Enable 卡片、
      `tool_params_display` 明细，以及 Exec/ApplyPatch/Permissions/DynamicTool 四族不带 meta
      的审批；`bottom_pane/approval_overlay.kt` 目前只发默认响应。上游
      `tui/src/bottom_pane/approval_overlay.rs`、`mcp_server_elicitation.rs`。
- [ ] **elicitation 只支持 url/form**：`bottom_pane/mcp_server_elicitation.kt` 把
      `openai/userVerification` 变体当普通空表单，无法完成验证签名；上游
      `tui/src/bottom_pane/user_verification.rs`、`app/user_verification_requests.rs`
      （需 native 侧 P-256/SHA-256 凭据签名）。
- [ ] **权限 profile 选择器缺失**：`permissionProfile/list` 已绑定但零调用
      （`protocol/app_server_client.kt`），设置里只有三种 `AskForApproval`。绑定本身也要修：
      `PermissionProfileEntry` 把 `id` 当 `name`（wire 没有 `name`）、未解析 `allowed`，
      而 `ThreadSessionState.activePermissionProfile` 从未被 `WireCodec.session()` 赋值，
      `status/card.kt` 的展示分支是死代码。上游 `tui/src/chatwidget/permissions_menu.rs`、
      `permission_discovery.rs`。
- [ ] **workspace headline/banner 不显示**：`account/workspaceMessages/read` 已绑定但零调用；
      上游 `tui/src/workspace_messages.rs`（取第一条 `Headline`，`featureEnabled=false` 时降级）。
- [ ] **Luna Reserve 的模型侧未接线**：`account/rateLimits/read` 已带 `supportsLunaReserve`
      并对老服务端的 -32600/-32602 回退到无参重试；仍缺 fallback/return 模型与提示，以及响应里
      `ordinaryUsageAllowed`（已解析无消费）与 `rateLimitUpsell`（连类型都没有）。上游
      `tui/src/chatwidget/luna_reserve_model.rs`、`luna_reserve_return.rs`、`backend_banners.rs`。
- [ ] **MCP 调用新字段未渲染**：`appContext/mcpAppUi/pluginId/readOnlyHint/mcpAppResourceUri`
      已解析进 `McpToolCallItem`（含 `appResourceUri` 派生），`history_cell/mcp.kt` 尚未据此
      渲染 app/插件/只读标记；上游 `tui/src/history_cell/mcp.rs` 也没有对应渲染，属超越上游的增强。
- [ ] **web search 结果渲染属增强**：`results` 已解析进 `WebSearchItem`（元素按 `title`/`url`/
      `snippet`/`type` 投影），上游 TUI 只把 results 用于遥测、不渲染，因此这是 Android 侧增强；
      若上游将来返回既无 title 又无 url 的结果类型（如纯图片结果），当前会在解析处丢弃。
- [ ] **hook cell 仍不显示 hook 名**：wire 只给 opaque `hookRunId`，`notices.kt` 现只在有 id 时
      显示该 id。要显示真正的 hook 名需要与 `hooks/list` 的 `HookMetadata` 建立映射（wire 两侧
      没有共享键）；上游 `tui/src/history_cell/hook_cell.rs` 用 `HookRunSummary` 的
      `status_message`/`entries`，还缺 `HookOutputEntry` 的逐条渲染。
备注：`MemoryCitationEntry` 的全部字段与 `HookPromptFragment.hookRunId` 在上游为必填，Kotlin
侧给了默认值——沿用本仓既有的宽松解析风格，不是 wire 错误，无需改动。

## 1. 交互能力

- [ ] **回退重编辑（backtrack）**：上游 Esc-Esc 打开 transcript、选中用户消息后回退并把它
      重新填进输入框（`tui/src/app_backtrack.rs`）。Kotlin 只有只读 Ctrl+T
      （`app/history_ui.kt`）与 `/revert <itemId>`；用户消息 cell 没有可点击的回退入口。
- [ ] **steer（运行中插话）**：上游运行中提交是 pending steer 并有预览/编辑
      （`tui/src/bottom_pane/pending_input_preview.rs`）；Kotlin 运行中一律走
      `thread/queue`（`chatwidget.kt` 的 `submitInput`），`steerTurn` 协议已绑定但无调用点。
- [ ] **中断时恢复输入**：上游把 pending steer / 队列草稿并回 composer
      （`tui/src/chatwidget/input_restore.rs`）；Kotlin 中断后只重读服务端队列，草稿不回流。
- [ ] **审批决定回执**：上游在 transcript 里记「You approved … / denied / timed out」
      （`tui/src/history_cell/approvals.rs`）；Kotlin 只关弹窗并改状态，没有回执 cell。
- [ ] **Plan 实施提示**：上游问「Implement this plan? / 清空上下文实施」
      （`tui/src/chatwidget/plan_implementation.rs`）；Kotlin 有 plan 模式与时间线，无此一步。
- [ ] **安全缓冲重试**：上游给「换更快的模型重试 / 继续等待 / 了解更多」
      （`tui/src/chatwidget/safety_buffering.rs`）；Kotlin 只报一条诊断。
- [ ] **实时语音（realtime）**：目前是壳，无 WebRTC/录音/字幕，`Realtime*` 通知全部丢弃
      （`chatwidget/realtime.kt`）；上游 `tui/src/chatwidget/realtime.rs`、
      `realtime_split_flap.rs`、`realtime_settings.rs`。含麦克风/扬声器电平条、静音提示、
      连接阶段、voice 选择与设置页。
- [ ] **goal 持久状态指示**：上游 footer 常驻 Active/Paused/Blocked/UsageLimited/
      BudgetLimited/Complete 与用量，恢复会话时提示「Resume paused goal?」
      （`tui/src/chatwidget/goal_status.rs`、`goal_menu.rs`）；Kotlin 只在 GoalSheet 打开时可见。
- [ ] **通知类型与优先级**：上游有 PlanModePrompt、user-input 请求等类型并按优先级聚合
      （`tui/src/chatwidget/notifications.rs`）；Kotlin 只有 TurnComplete + ApprovalRequested。

## 2. Transcript 渲染

- [ ] **Computer/CUA 活动聚合**：相邻 `cua_repl`/node-repl 调用合并为
      「Using/Used computer · N actions」（上游 `tui/src/history_cell/computer_activity.rs`）；
      Kotlin 一次调用一张通用 MCP 卡片，无 `cua` 处理。
- [ ] **Mermaid 图**：完成的 mermaid fence 渲染成图，失败回退代码块
      （上游 `tui/src/markdown_render/mermaid.rs`）；Kotlin 当普通代码块。
- [ ] **inline visualization**：`::codex-inline-vis{…}` 指令（上游
      `tui/src/inline_visualization.rs`）；Kotlin 只处理 `:codex-file-citation{…}`。
- [ ] **数学排版**：上游对受支持的 TeX 子集做有界排版（`tui/src/markdown_render/math/`）；
      Kotlin 显示为 mono 斜体源码。
- [ ] **hook cell**：上游持久显示 Hook completed/failed/Blocked/stopped 及每条
      `HookOutputEntry`，且不依赖 turn 状态行（`tui/src/history_cell/hook_cell.rs`）；
      Kotlin 只有运行中的 hook 名和每条失败一条警告，`entries` 文本丢弃。
- [ ] **compaction 进度**：上游有实时标题与「Context compacted · 3s」
      （`tui/src/chatwidget/compaction.rs`）；Kotlin 只有静态通知。
- [ ] **unified exec 等待/交互 cell**：上游区分「Waited for background terminal」与
      「Interacted with background terminal」（`tui/src/history_cell/exec.rs`）；Kotlin 把
      stdin 混进命令输出，等待不可见。
- [ ] **嵌套 review turn**：上游在 transcript/backtrack 里隐藏 review 内部 turn
      （`tui/src/app_backtrack.rs`）；Kotlin 每个 finished turn 都插分隔。
- [ ] **启动警告 cell**：上游在 transcript 顶部提示「N startup issues」
      （`tui/src/history_cell/startup_warnings.rs`）；Kotlin 只在 `/mcp` 页可见。
- [ ] **turn 分隔符的 runtime metrics**（工具/推理调用数、TTFT/TBT，
      `tui/src/history_cell/separators.rs`）：wire 没有该数据，需要本地统计。

## 3. 会话与工作区

- [ ] **resume picker**：补排序键（Created/Updated/Recency/Section）与 All/Cwd/来源过滤；
      展开预览改为完整 transcript（上游 `tui/src/resume_picker/`）；删除加二次确认；
      打开归档会话时给「解档并恢复」（上游 `tui/src/unarchive_prompt.rs`）。
- [ ] **worktree**：补 owner/thread 绑定、remove/copy、以及新会话/fork 的「在哪运行」选择
      （上游 `tui/src/worktree_browser.rs`、`chatwidget/worktree_picker.rs`）；Kotlin
      `app/worktrees.kt` 只有 `git worktree list/add`。
- [ ] **`/cd` 语义**：上游在当前会话内换目录（`tui/src/app/working_directory.rs`）；
      Kotlin 会新开空会话（`app.kt`），`/pwd` 也会打开目录选择器。
- [ ] **resume/fork 的 cwd 提示**：上游问「用会话 cwd 还是当前 cwd」并记住选择
      （`tui/src/cwd_prompt.rs`、`session_resume.rs`）；Kotlin 固定用服务端记录值。
- [ ] **additional dirs**：上游可增删可写根（`tui/src/additional_dirs.rs`）；
      Kotlin 设置页只读展示。

## 4. 设置、引导与更新

- [ ] **statusline 配置**：`/statusline` 选择/排序/实时预览页脚条目
      （上游 `tui/src/bottom_pane/status_line_setup.rs`、`status_surface_preview.rs`）；
      Kotlin 只有固定状态卡。
- [ ] **startup hooks 信任审查**：启动时对新增/变更的 hooks 做阻塞式信任确认
      （上游 `tui/src/startup_hooks_review.rs`）；Kotlin 只在用户打开 `/hooks` 时逐条信任，
      没有「全部信任」。
- [ ] **主题**：语法高亮主题列表、实时预览与自定义主题（上游 `tui/src/theme_picker.rs`）；
      Kotlin 只能选 System/Light/Dark。
- [ ] **experimental 开关**：失败后保留意图可重试、按 stage 门控、发现失败提示
      （上游 `tui/src/bottom_pane/experimental_features_view.rs`）；Kotlin 写入是
      fire-and-forget。
- [ ] **skills 展示与搜索**：用 `interface.displayName/shortDescription` 并支持模糊过滤
      （上游 `tui/src/skills_helpers.rs`）；Kotlin 用 raw name，无搜索框。
- [ ] **`@` 提及**：补 skills 与已授权 connector（`app://`）、搜索模式切换、footer 提示与
      高亮（上游 `tui/src/task_mentions.rs`、`bottom_pane/mentions_v2/`）；Kotlin 只有
      plugins/tasks/files/directories。
- [ ] **模型/effort 默认值**：会话打开时也能「设为默认」，Plan 模式单独覆盖
      （上游 `tui/src/chatwidget/model_popups.rs`）；补 auto-model 分组与 Ultra 并发警告。
- [ ] **review 分支/commit 选择器**：上游列出真实分支与 commit（`chatwidget/review_popups.rs`）；
      Kotlin 要求手输。
- [ ] **feedback**：区分内外部受众的披露、上传后给 issue 链接、附件选择
      （上游 `tui/src/bottom_pane/feedback_view.rs`）；Kotlin 只有分类/理由/日志同意。
- [ ] **backend/workspace banner 通用化**：上游 `actionable_banner.rs` 支持标题/描述/CTA/关闭
      （account mismatch、用量恢复、workspace owner 提示等）；Kotlin 的
      `app/session_status.kt` 明确不解析 banner，只有硬编码横幅与连接中断横幅。
- [ ] **onboarding/启动**：首运行欢迎页、trust 提示的「Open restricted/Open existing task」
      变体、model migration 一次性提示（上游 `tui/src/onboarding/welcome.rs`、
      `trust_directory.rs`、`model_migration.rs`、`startup_orchestration.rs`）；Kotlin
      启动编排只有 bootstrap/restore。

## 5. 暂缓与待定

- [ ] **账户分析仪表盘**：上游 `tui/src/analytics/` 直连 ChatGPT 私有 HTTP 接口
      （`backend-client` 的 analytics 路由），app-server 无对应方法；补齐按模型/功能/
      日期范围的报表需要上游先加协议。已有 `account/usage/read` 的每日用量与 summary。
- [ ] **APK 更新提示**：上游 `tui/src/updates.rs` 面向自更新安装；Android 走应用分发，
      是否在应用内做检查/提示待定。
- [ ] **本地模型 provider（Ollama/LM Studio）**：上游 `tui/src/oss_selection.rs`；手机上
      是否有可用的本地服务端场景待定。

## 6. 终端专有、平台与工具链

以下机制在终端形态下才有直接对应物，或受 Android 平台约束尚未提供；均作为待办跟踪，
多数需要先确定 Android 上的等价交互或前置能力。

- [ ] **vim 模态与键位重绑**：上游 `tui/src/bottom_pane/vim_*.rs`、`keymap/`；在 Compose
      输入层实现，硬件键盘场景可用。
- [ ] **终端按键语义**：crossterm 原始键事件、Kitty 键盘协议、bracketed paste；Compose
      没有对应事件层，需先定义映射。
- [ ] **光标与 scrollback 重排**：Compose 列表没有终端 scrollback，需确定等价交互
      （保持阅读位置、跳转等）。
- [ ] **ANSI/OSC 与终端元信息**：ANSI/OSC 标记、终端标题与调色板、BEL/OSC 9、OSC-52；
      需要通知、剪贴板与标题的等价物。
- [ ] **sixel 内联图片**：改为 Compose 图片渲染路径。
- [ ] **pager overlay**：在 Ctrl+T 只读历史页（`app/history_ui.kt`）基础上扩展为通用
      全屏分页视图。
- [ ] **PTY 终端网格渲染与 daemon 菜单**：依赖下一条的 PTY 支持。
- [ ] **IDE context IPC**：上游 `tui/src/ide_context.rs`；需要 IDE 侧协议配合。
- [ ] **`$EDITOR` → PTY**：可用系统编辑器 Intent 近似，语义不同。
- [ ] **`/raw`、`/title`、pets**：上游 `tui/src/chatwidget/pets.rs` 等；需定 Android
      表现形式。
- [ ] **PTY / 交互式命令**：`process/spawn|write|resize|kill`；当前
      `json_rpc_app_server_client.kt` 保留 `require(!tty)`，命令只走 `command/exec`
      的非交互流。
- [ ] **LocalDaemon / Remote 后端**：探测与回退、ws 鉴权、remote-workspace 语义、
      按后端隔离的配置（上游 `tui/src/lib.rs` 的 `AppServerTarget`）；当前固定同进程
      Embedded。
- [ ] **CLI 专有机制**：named session lookup（`named_session_lookup.rs`）、跨会话排队
      （`session_queue_commands.rs`）、`CODEX_TUI_RECORD_SESSION` 式 JSONL 录制
      （`session_log.rs`）、`/app`（上游仅 macOS/Windows，需 Android 等价物）、`/ide`、
      `/daemon`、`/keymap`、`/vim`、`/elevate-sandbox`。
- [ ] **设备端编译工具链**：clang/rustc/cmake/ninja/perl 与 JDK、Android 构建工具
      （aapt2/d8/apksigner/Gradle）受 bionic/glibc 限制，需先评估可行路径；
      见 [toolchain.md](toolchain.md)。
- [ ] **缺失工具**：node/npm（现由 bun 取代）、wget（现由 curl 取代）、
      vi/less/top/watch、nc/ping/traceroute（依赖 PTY 或额外权限）。
- [ ] **登录与凭据**：系统浏览器回跳的自定义 scheme（现由 app-server localhost 回调
      替代）、`auth.json` 的 Keystore 保护（内嵌 Rust 直接读写该文件，需要上游支持
      外部密钥回调）。
