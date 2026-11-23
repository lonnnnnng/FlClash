# DNS整页、长规则列表与浅色自然转场

2026-11-23，KUI-VIS-094，续KUI-VIS-093。仅使用POCO F2 Pro（31936bb6/API36），玻璃关闭。本组不发版，公开版仍为0.1.18。

## 实现与缺陷溯源

- **DNS空态定位**：旧`GlobalDnsSettingsPanel`作为工具LazyColumn中的520dp项目，受外层Dock和尾部Spacer影响；改成独立`GlobalDnsSettingsPage`，保持原工具返回路径及保存回调。空态按Flutter整窗`Alignment(0,-.2)`定位，标题栏浮在上方，非空设置继续避让标题并自行滚动；160dp/短视口120dp插图改用DNS双服务器符号，按钮改为44dp中性表面，并补齐中英文“覆写项”文案。Dock仅在底层工具页存在，DNS前景整页不可见。
- **规则排序入口丢失**：基线`c3a5d5de`保留了排序状态、快照、保存和回执，却没有任何`sorting=true`入口。恢复具名排序按钮；仅无查询且可写时可用，进入时冻结目录和版本，拖动仍通过已有`ResourceOrderList`松手提交与控制器事务保存。排序态沿用紧凑双行规则展示，不改变规则字符串。
- **搜索浏览位置**：完整目录和搜索结果各自持有LazyListState，清除搜索恢复目录锚点。审查发现新增归零Effect会在Activity恢复同一查询时覆盖搜索位置，现只在查询实际变化时归零；80条规则测试同时核对原目录锚点和多屏搜索结果重建位置，误差≤1px。
- **浅色Sheet系统栏**：完整浅色对照暴露独立Sheet Window仍保留白色系统图标。`ConnectionModalSheet`现在对自身Window设置当前主题的状态/导航栏图标，并在关闭时归还原值；自然验收从实际焦点Window读取标志核对两主题。

源码缺口与本组失败过程均保留在验收JSON，不把测试驱动错误冒充产品故障。

## 验收

最终源码POCO集中7/7：长列表真MainActivity/Room、DNS两主题几何与搜索、规则排序/批删/预设、DNS失败重试/显式空值/保存等待、DNS规则保存重建、深色自然路线。另浅色自然表单/DNS与80条长列表自然拖动各1/1，分别保留连续录像。26项相关JVM单测（GlobalRulesController16、ConnectionSheetGeometry10）通过，2184对双语资源检查通过，Motion/AppTest构建和Lint通过（0错误、11既有警告、1提示）。未重跑全应用单测或正式Release，不把Motion证据当R8性能结果。

自然用例不创建ComposeTestRule，使用真实触摸、系统Back、IME；正文采用ACTION_SET_TEXT。两主题设置只写独立motion包，并在最外层finally恢复原值及原键集合。规则仅写隔离空库、保持null选择与STOPPED；保存回读、完整偏好行、草稿/回执清理均验证。长列表精确定位/重建用例另使用Compose框架，两类证据分开。

完整Flutter参考使用现成独立包`com.lonnnnnng.kclash.reference.full.dev`，固定`be84736605527034689c95bf27828ff27d912c5e`、Prism`63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185`、0.9.3、SAFE_MODE=true。用户在本轮自行完成首次声明；通过实际工具→进阶配置→DNS/附加规则、主题页切浅色、真实表单保存和内置预设建立非空规则，再真实长按换序、滑动和搜索。前后XML明确记录两个AND规则交换；不是内存组件宿主。原版`com.follow.clash`未启动、改数据或替换APK。

四段最终录像覆盖Flutter完整应用浅色规则/DNS和非空规则，以及Kotlin真实MainActivity对应浅色路线/80条长列表。保留原PTS与抽帧索引。两端等待时间、规则数量和驱动不同，不声称同步逐帧同相位、全像素相同或性能收益；Kotlin保留放弃草稿确认/原始规则按钮/显式排序入口。

## 剩余范围

本组具名DNS空态/整页、规则长列表和浅色连续路线已验，093“只有Kotlin录像”和“只有Flutter组件宿主”的缺口得到补充。整体Flutter视觉仍PARTIAL：DNS非空字段卡片与编辑面板、选择即加默认值/保留选择器的上游交互、缺少的五个DNS字段映射，以及字体/图形/少量间距差异继续待办。参考包是固定旧版，当前`upstream/main=afe71360`及其Prism`6c47901`的完整运行尚未覆盖；不可将旧版完整运行写成最新版完整运行。

规则原位长按排序与Kotlin显式排序模式仍不同；当前已具备可达操作及真实顺序持久化，不因本次布局任务重写已验证的事务边界。菜单discarded owner、GPU/fence和稳定性能收益未在本组采样。下一步优先DNS非空字段/编辑面板，再继续剩余子页与菜单诊断。

[验收JSON](acceptance/dns-long-rules-poco-20261123.json)；原始资料位于工作区`acceptance-artifacts/dns-long-rules-poco-20261123/`。
