# 页面与弹层的自适应规则

`ShellWidth` 沿用 720dp 断点，以当前窗口宽度判断；分屏与窗口缩放也按同一规则处理。
`style.kt` 的纯函数集中决定侧边距、页顶留白、圆角、弹层高度比例。

## 页面导航

- Compact（小于 720dp）：`SheetPage` 全屏，无侧边距、页顶留白和圆角；
  `NavDisplay` 使用 `MiuixDefault` 推入转场，横向返回手势。
- Expanded（至少 720dp）：保留画框、58dp 页顶留白、原圆角、Modal 转场及向下返回手势。
- 聊天根页面仍禁用滑动关闭。

## 弹层分类

当前共 **14 个文件、16 处调用**，统一经过 `AdaptiveSurface`。
用途由调用点显式指定，不按文本长度或标题临时猜测。

| 调用点 | 用途 | Compact 呈现 |
| --- | --- | --- |
| `ApprovalDialog` | 阻塞式审批、补丁审阅、问题表单 | 全屏，必须通过原有决策按钮回应，不允许返回或外部点击撤销 |
| `FormSheet` | 项目、路径、环境等表单 | 全屏，保留提交校验，增加关闭入口 |
| `GoalSheet` | 目标编辑及状态操作 | 全屏 |
| `PluginInstallAuthSheet` | 多步安装授权流程 | 全屏 |
| `PluginDetailSheet` | 插件详情及组件列表 | 全屏 |
| `AgentsOverview` | 代理用量及状态详情 | 全屏 |
| `MisalignmentReviewSheet` | 审阅提示详情 | 全屏 |
| `AgentPickerSheet` | 临时切换代理 | 底部抽屉 |
| `CopySheet` | 选择响应、状态或代码块复制 | 底部抽屉 |
| `AgentsScreen` 重命名 | 单字段短表单 | 居中卡片 |
| `AgentsScreen` 归档 | 操作确认 | 居中卡片 |
| `FileBrowserScreen` 删除 | 破坏性操作确认 | 居中卡片 |
| `ResetMemorySheet` | 清空记忆确认 | 居中卡片 |
| `ResetCreditSheet` | 消耗重置信用确认 | 居中卡片 |
| `TrustProjectSheet` | 目录信任确认 | 居中卡片 |
| `RateLimitNudgeSheet` | 切换模型提示 | 居中卡片 |

Compact 全屏容器占满窗口，内容避开系统安全区与键盘；没有抽屉拖动手柄。
临时选择器无侧边距、保留顶角，内容最高占窗口 85%；短确认与短表单
左右留 20dp、保留圆角，内容高度上限为 72%。内容仍使用各调用点已有的滚动容器。

Expanded 所有弹层维持原底部抽屉：侧边距 `clamp(宽度 × 0.055, 40dp, 72dp)`，
圆角 28dp，内容高度上限 72%，代理概览为 90%。
全屏弹层仍是独立模态窗口，不新增业务导航路由；二级页面的推入转场由 `NavDisplay` 负责。

## 验证

`ShellWidthTest` 覆盖断点两侧、紧凑布局、用途分类、宽屏回归、侧边距上下限和概览高度。
2026-09-23 验证：46 个测试类、318 个用例，失败/错误/跳过均为 0。
2026-09-26 修复后重新构建应用与测试 APK：46 个测试类、319 个用例，失败和错误均为 0。

```powershell
./gradlew.bat :app:testDebugUnitTest -PskipToolchainBuild -PskipNativeBuild --no-parallel --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8' '-Dkotlin.daemon.jvmargs=-Xmx1024m' --console=plain
```

## 设备验证入口

`RuntimeSmokeInstrumentation` 不带 `scene` 参数时仍执行原有 native 冒烟测试。
带 `scene` 时进入仅打包在测试 APK 中的交互样例，可选 `form`、`confirmation`、
`picker`、`approval`、`page`。这些样例调用真实生产组件，审批不会执行命令，
表单不会写服务器数据。日志中的 `READY`/`COMPLETED` 只表示样例生命周期，
不表示用户操作或 native 测试自动通过。

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e scene approval -e holdSeconds 600 com.cy.codex.test/com.cy.codex.runtime.RuntimeSmokeInstrumentation
```

`AdaptiveUiFixture` 日志记录关闭状态与决策计数。切换样例前先执行
`adb shell am force-stop com.cy.codex`，避免两个 instrumentation 同时控制应用。

本机沿用未提交的构建绕行（跳过 native staging、缓存 Gradle 版本和 Maven 镜像）。
离线 APK 可以打开真实导航页面，但服务端因缺失 `toolchain/native-manifest.txt` 启动失败。
因此这次设备检查覆盖 UI 呈现与交互，不覆盖 JNI、真实账户、工具执行和服务器审批往返。

设备为 API 36 x86_64 模拟器，1536MB RAM。手机使用 720×1600 @320dpi（360×800dp），
宽屏通过 `wm size 1280x800` / `wm density 160` 测试；断点使用 719/720×800 @160dpi。
结束后恢复 `wm size reset` 与 `wm density reset`。构建与模拟器分开运行。

2026-09-26 设备检查记录（人工操作与截图，非自动断言）：

| 场景 | 结果与证据 |
| --- | --- |
| 断点与宽屏 | [719dp 全屏](images/adaptive/width719-settings.png)、[720dp 画框](images/adaptive/width720-settings.png)、[1280dp 画框](images/adaptive/tablet-settings.png)；窄屏右滑返回聊天 |
| 表单 | 必填项为空时禁止提交；输入后按钮启用，键盘打开时可滚动至末尾与提交按钮，见[截图](images/adaptive/phone-form-keyboard.png) |
| 临时选择器 | 15 项列表显示为底部抽屉；输入 Agent 15 后过滤为单项，键盘不遮挡结果 |
| 短确认 | 目录信任显示居中卡片，返回键关闭 |
| 阻塞审批 | [手机审批](images/adaptive/phone-approval.png)返回键不关闭；滚动后 Allow 进入下一条且滚动归零，再 Deny 关闭；日志依次为 decisions=0/1/2，最终 open=false |

设备测试发现并修复：窄屏审批正文仍沿用宽屏 36% 高度，现独立使用 70%；
连续请求曾继承上一条的滚动位置，现按 requestId 重建审批内容状态。宽屏保持原高度。

参考：[Miuix 导航](https://compose-miuix-ui.github.io/miuix/guide/miuix-nav)、
[WindowDialog](https://compose-miuix-ui.github.io/miuix/components/windowdialog)。使用仓库现有 0.9.4 依赖。
