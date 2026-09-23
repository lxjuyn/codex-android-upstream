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

```powershell
./gradlew.bat :app:testDebugUnitTest -PskipToolchainBuild -PskipNativeBuild --no-parallel --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8' '-Dkotlin.daemon.jvmargs=-Xmx1024m' --console=plain
```

本机沿用未提交的构建绕行（跳过 native staging、缓存 Gradle 版本和 Maven 镜像）。
编译与 JVM 测试不代表视觉验收；仍需在可运行 native 的设备检查：
360dp 手机、720dp 断点、宽屏、横屏键盘、长补丁/长表单、返回手势、审批队列连续切换。

参考：[Miuix 导航](https://compose-miuix-ui.github.io/miuix/guide/miuix-nav)、
[WindowDialog](https://compose-miuix-ui.github.io/miuix/components/windowdialog)。使用仓库现有 0.9.4 依赖。
