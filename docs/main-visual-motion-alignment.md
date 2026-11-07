# 主页面、面板与自然时钟动效对齐

2026-11-08，作者 long；开发从11-07开始，附件沿用`../acceptance-artifacts/visual-motion-20261107/`。Kotlin起点`31a0ada0`。本组是明确列出的视觉/交互切片，完整Flutter像素一致性继续为PARTIAL。

## 固定参考与取舍

Flutter主仓库固定`be84736605527034689c95bf27828ff27d912c5e`；其共用UI已迁往`plugins/prism_ui`，固定gitlink为`63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185`。参考源码取到外层独立目录，未merge/cherry-pick Flutter提交，未改手机`com.follow.clash`。原始文件摘要见附件`reference/provenance.json`。

| 范围 | 固定Flutter来源 | Kotlin处理与边界 |
| --- | --- | --- |
| 首页/配置标题 | `lib/views/dashboard/dashboard.dart`；Prism `typography.dart`、`metrics.dart` | 首页与配置使用24sp/30sp、72dp标题节奏；代理既有相同参数保留 |
| 首页配置卡 | `lib/views/dashboard/widgets/profiles.dart` | 同步与入口移至卡片标题栏；单行名称旁只留快切，更新时间与来源/到期同一行；右侧入口仍保持Kotlin原“进入配置页”行为，整卡仍开详情 |
| 配置快切 | `profile_detail.dart`→`profiles.dart::_ProfilesGrid` | 24dp卡片、16dp项间距、17sp/22sp标题、13sp/18sp副标题及右侧24dp选中标记；选择回调仍只受理一次 |
| 常用面板标题 | Flutter `lib/ui/scaffold/scaffold.dart`、`scaffold_actions.dart`；Prism `sheet.dart`、`metrics.dart` | 新共用`DashboardSheetHeader`统一快切/代理选择/质量/内存等根面板：48dp栏高、17sp/22sp居中标题、左侧关闭。子面板左返回和原右侧直接关闭保留，未改既有返回契约 |
| IP质量占位 | `lib/features/ip_quality/ip_quality_sheet.dart:143`、`:195` | 质量与网络结果行预留位置，来源行加载时即存在；旧结果刷新保持值，复制槽宽度固定；不以额外spinner行推动正文 |
| 代理搜索/普通空态 | `lib/providers/state/proxies.dart:96`；`views/proxies/tab.dart`、`list.dart`；Prism `null_status.dart` | 搜索先剔除无匹配组；全无匹配显示整页空态，普通空态为“暂无代理”，重心为`(0,-0.2)`。过滤不写用户当前组，清除后恢复；Kotlin保留清除搜索快捷操作 |
| 配置副标题 | `lib/views/profiles/profiles.dart:328`及中文ARB | 配置页/快切共用“本地/远程/自定义”与流量短副标题，不把同一内容按入口展开成长文案 |

本机Flutter SDK为3.41.9，而固定Prism要求3.47.0；未修改全局SDK或伪造相同版本渲染。本组依据固定源码参数和真实Kotlin渲染核对，截图哈希只用于Kotlin回归，不作为Flutter像素相等证据。仍需补CUSTOM组数芯片、首页无流量时三列统计、内存图形层级及其余子页完整视觉；禁止UI自行解析配置凑数据。

## 自然时钟验收

既有`ComposeTestRule`会用测试时钟推进帧，不能把`autoAdvance=true`或冻结截图序列当作自然时间。新增仅Debug的`VisualMotionTestActivity`使用正式`MainNavigationHost`、`ProfilesPanel`、`ProxyGroupsCard`及面板组件和固定领域对象，不生成配置YAML、不接Room/网络/核心。Instrumentation仅用`ActivityScenario`、原生触点/Back/文字设置，未创建ComposeTestRule；输入框实际拉起IME。

深浅两套场景包含配置快切确认/取消、110/95/80ms间隔的导航反转、整页搜索空态/部分匹配/返回、菜单子层打开-Back-重入-切List、质量面板展开及加载完成、配置/代理普通空态。自然时间断言选择次数、最终导航、搜索组过滤与质量六行实际屏幕Y坐标；不是仅比较画面不同。

外部`screenrecord`独立采MP4并回拉SHA核对，仅结束匹配本批文件名的录屏PID，随后删除独占设备副本；不与外部uiautomator抢UiAutomation。原始帧PTS保留，不重采样为60fps制造顺滑。gfxinfo在宿主仍存活时保存，结束后回拉核验并删除独占缓存。录屏/截图/Debug会增加负载，视频平均帧率包含静止画面，不等于应用渲染FPS；数据不能推广Release或其他设备。

## 失败与证据记录

- `natural1`为0/2：画面已显示整页空态，但UiAutomation缓存返回旧Tab节点；增加原生缓存刷新。固定宿主代理页缺正式顶部inset，补齐；不是用正式包状态栏错误冒充通过。
- `natural2`为0/2：质量半屏面板下方字段在视口外，几何断言找不到节点。按真实手柄展开后，`natural3`2/2通过、28PNG；六行加载前后Y坐标严格相等。
- `baseline1/2`各0/4以及`regression1`9/10的失败为旧静态图片哈希不匹配，保留原日志与图片。基线在目视核对后更新；未把所有旧哈希变化都追认为本轮唯一原因。
- 首轮gfxinfo在Instrumentation结束后读取返回“No process found”，因此不作为帧性能证据；后续改在宿主存活时采集。
- 独立只读差异审查未发现确凿新增错组选中、Pager越界、忙态重复提交或返回层级缺陷；静态结论不替代下述最终设备结果。

最终构建、同包验收、录屏/帧统计、截图和正式数据保护以`docs/acceptance/main-visual-motion-redmi-20261108.json`为准，最终build6同套包回归14/14、自然深浅交互2/2、62PNG/22拼图及6组原始PTS序列已核对，593源码/双包匹配，正式数据和设备保护通过；连续动效性能未通过，详见下节。玻璃默认关闭；备份/系统恢复、观测次要打磨、主题/语言继续暂缓，TalkBack及早期Room兼容不进入范围。

## 性能未通过与下一步

`final-natural1`原始录像87.263233秒、1624帧，保留原PTS；6组采样序列未观察到最终返回后残留菜单或错误层级，但抽帧不覆盖每一帧。深色gfxinfo累计916帧/312 jank（34.06%），第二主题结束累计1872帧/592 jank（31.62%），后者包含前者，不能相加。最终累计p50/p90/p95/p99为31/81/125/250ms。

此结果标记`NOT_ACCEPTED_INVESTIGATE`。Debug、screenrecord及PNG采集开销尚未隔离，未认定确切产品根因，也不能声称Release或无采集时流畅度通过。下一步在相同操作窗口取消录屏/截图后取帧统计，再分离Debug开销，定位慢帧后修复并集中复验。本轮原始失败指标持续保留。

2026-11-08后续：已由[KUI-018](motion-performance-defect.md)补无采集、独立非debuggable包对照与方法采样，确认并修复菜单阴影缓存失效热点。二级菜单jank66.30%→15.50%、非启动总窗口14.11%→10.11%；不能与上文混合采集31.62%直接相减。面板尝试退化后撤回，完整性能仍PARTIAL。最终构建、同包回归和剩余边界见[motion-performance-redmi-20261108.json](acceptance/motion-performance-redmi-20261108.json)。

浅色固定Debug宿主的系统状态栏图标继承设备模式为白色，未作为正式主题对比度验收；8张MainPageStates裸组件图缺完整主页面顶部inset，仅证明组件内容/IME边界。正式导航与自然宿主另有inset覆盖。完整Flutter像素一致、CUSTOM组数芯片、首页无流量统计、内存图形层级和其他子页仍列待办。
