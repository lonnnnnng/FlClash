# KUI-022：液滴菜单隐藏源按钮后涟漪仍驱动背景窗口

2026-11-09，接续 KUI-021 的线程与阴影调查，产品基线 `5df4a896`，安装对照包为网速卡紧凑版 `23284d4b`。本组保留的修复仅暂停已经隐藏的菜单源按钮涟漪；软件阴影、文字模糊、液滴轮廓和动效时间线不变。

## 来源、复现与归因

Kotlin `AppMenuSource.kt::appMenuSource` 在源按钮隐藏后跳过按钮绘制，但 Material 3 按钮内部的涟漪仍可继续发出绘制请求。`ProxyGroupsPage.kt` 的工具按钮真实点按后，同一进程的主窗口和弹出窗口都继续绘制。仅隐藏画面不等于停止按钮的内部动画。

对照固定 Flutter `be847366` / Prism `63450c0` 的 `popup.dart::_paintSource`、`_paintContent`：源图像与正文各自按透明度及模糊绘制。这里处理的是 Kotlin Material 3 按钮与独立 Popup 的生命周期问题，不认定 Flutter 上游有同一缺陷，也不合入 Flutter 提交。

Android 官方 [Migrate to the new ripple APIs](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/migrate-indication-ripple) 说明 `LocalRippleConfiguration provides null` 用于单组件禁用涟漪，不应作为全局主题配置。2026-11-09 获取正文并保存 `ripple-reference.md`，当前 Material 3 API 经本项目实际编译确认。

正式签名 R8 对照包，Redmi Note 8 Pro/API36/440dpi/竖屏/字号1.0，核心停止、玻璃关闭，原配置及节点选择复用。每个包深浅主题分别冷进程、三轮打开/展开/收回/关闭。性能阶段不截图、录屏、方法采样或启动Instrumentation，帧统计完成后另采终态图。设备电池温度27.6–28.6℃；按原包、无模糊诊断、单入口候选、原包反测、最终包顺序执行，非随机盲测，不声称统计显著性。

| 批次 | 打开jank | 关闭jank |
| --- | --- | --- |
| 原包首轮 | 129/228，56.579% | 20/179，11.173% |
| 临时关闭文字模糊 | 141/235，60.000% | 18/181，9.945% |
| 工具按钮隐藏时暂停涟漪 | 72/209，34.450% | 17/180，9.444% |
| 原包反测 | 130/228，57.018% | 17/179，9.497% |
| 最终共享处理 | 67/205，32.683% | 17/180，9.444% |

每组24/24终态通过。相邻原包反测与最终包相比，打开超时减少63帧，掉帧比例下降约24.34个百分点。原包反测的主窗口83/99、Popup47/123，最终主窗口42/53、Popup25/146；背景窗口帧数下降，两个窗口的超时帧都减少。这里是系统窗口统计，不能直接当作某个方法或GPU耗时。

文字模糊诊断无打开收益，已撤回；其APK、源码和日志保留，不能把取消视觉效果作为性能交付。KUI-021原有阴影候选负结果继续有效。菜单仍有约32.7%打开掉帧，完整性能与Flutter视觉保持PARTIAL。

## 修复范围

`AppMenuSourceContent` 继承当前组件的涟漪配置，仅在 `source.hidden` 时提供空配置；按钮回源后恢复继承值。该包装不添加布局节点，不修改触点、语义、焦点、启用条件、点击回调或源图像捕获。

应用到六个已有源按钮入口：代理工具、代理组溢出选择、配置页顶栏新增、配置页空态新增、Provider过滤、正文历史。真实触点回归覆盖深浅/玻璃开关的开合、中断回源、回源后再次点击；过滤与空态新增额外两次打开/取消，原草稿和导航后续断言继续保留。玻璃默认关闭。

稳定终态8对（深浅各根菜单/子菜单/收回/关闭）在排除顶部100px系统状态栏后逐像素一致，最大及平均通道差均0。中间帧以实际液滴回归截图另验，不能由稳定终态宣称所有帧逐像素等价。

## 原生采样限制

本机读取官方 Simpleperf 应用采样文档，并使用 Redmi 自带 `/system/bin/simpleperf`。shell自身 `cpu-cycles:u` 计数成功；正式包及显式profileable独立包按PID附加均 `Permission denied`。独立包的 `--app` 路径明确失败于：

```text
seapp_context_lookup_internal: No match for app with uid ..., seinfo default:targetSdkVersion=37
simpleperf_app_runner: couldn't set SELinux security context: Permission denied
```

APK已核验包含profileable标记，仍不能取得原生栈；`task-clock:u`又被本ROM拒绝，去掉修饰符则提示必须使用`:u`。未修改SELinux、root、全局调试设置或正式包权限。试验的profileable标记和原生采样测试入口已撤出源码，附件保留。该环境限制不构成RenderThread/GPU根因证据。

## 验证与后续

最终产品构建及854项JVM结果零失败/错误/跳过（App572本轮执行，Service213/Core38/Common31复用）；Debug、签名Release/R8、独立motion包/测试包与双Lint通过，Debug0错误68警告14提示，Release0错误66警告13提示。仅测试用例补强后的末次测试包单独构建并复跑Debug Lint，不把旧测试APK当作最终包。

设备精确回归、安装回拉与收尾清单见 `docs/acceptance/menu-source-ripple-redmi-20261109.json`，所有原始日志、失败探针、APK、帧表与截图在 `../acceptance-artifacts/menu-native-20261109/`。后续继续剩余打开慢帧/关闭早段与原生渲染归因；同步上游或更换按钮/菜单实现时，必须保留隐藏源不驱动额外动画、返回恢复反馈和相同无采集性能对照，才能撤掉该本地补丁。

最终精确回归11/11通过，20张PNG完整分片/哈希验证并经3张拼图目视；首轮10/11的Provider取消失败为Espresso选中失焦Activity，改用既有系统Back helper并确认Popup已移除后复跑，产品源码不变。正式APK及最终motion/AppTest分别安装回拉一致；最终保留签名优化版、原1.yaml哈希保持、深色/字号1.0、服务停止，独立测试包和自有临时文件已收尾。
