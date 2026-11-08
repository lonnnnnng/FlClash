# KUI-018：菜单阴影缓存失效与动效测量污染

## 2026-11-09：首开/关闭线程归因诊断 — UNRESOLVED_DEVICE_TRACE

作者 long。本轮保留未提交诊断测试 `VisualMotionNaturalInstrumentedTest#lightMenuFirstOpenAndCloseFrameTimeline` / `#darkMenuFirstOpenAndCloseFrameTimeline`，将 `android.os.Trace` 区间覆盖到菜单打开或收回的可见终态；深色 Redmi 批次 `menu-diag-dark3` 的 1/1 用例通过，正式库快照、`1.yaml`、设备设置和服务状态保持。源改动指纹为 `29b28369dcc9901c6bc817255a2efe7fc5c14d424fc01ba91447b1eb9b9f7aa8`。

Perfetto 仍不能提供可用的线程归因：自然结束 trace 的 `traced_flushes_failed=1`、`traced_final_flush_failed=1`，`sched_slice` 为 0，应用线程名与 `kclash_menu_*` slice 均为 0；系统日志记录 `Periodic flush timed out`。去掉 SurfaceFlinger 数据源的短探针也没有得到 sched 样本，说明问题位于本 Redmi/API36 shell trace 会话或 ROM 采集层，不能据此指向 Compose、UI 线程或 RenderThread。该 trace 只作失败证据，不计入性能改善或根因结论；gfxinfo 的首开/关闭慢帧仍是症状数据。

同时完成 `DashboardInstrumentedTest#memoryCardAndDetailsCaptureDarkAndLight` Redmi 1/1：深浅主题内存卡、详情面板和完整窗口截图均通过，固定 fixture 的图形层级无重叠或裁切。截图哈希与设备/安装证据见 `docs/acceptance/menu-rendering-diagnostic-redmi-20261109.json` 及 `../acceptance-artifacts/memory-visual-redmi-20261109/`；该页仍不代表真实 Go 内存数值或 Flutter 同版本逐像素一致。

2026-11-08，作者 long。起点为Kotlin `db195142a895b57072a140a52311a5bc5840b240`。附件保存在`../acceptance-artifacts/motion-performance-20261108/`；最终验收结论以本轮JSON为准，本节先保留定位过程。

最终菜单热点优化已验，整体流畅度仍PARTIAL。权威证据为[验收JSON](acceptance/motion-performance-redmi-20261108.json)：final-build2的849项JVM、Debug/Test/未签名Release-R8/双Lint通过；同套包21/21回归、自然深浅2/2与独立深色补验1/1，593源码/安装回拉匹配。88PNG/31拼图及7组PTS序列已核对；正式两配置和所列设备基线全组保持，独立测量双包及采集文件已收尾。

## 原始问题与测量边界

前组Debug、录屏及PNG采集混合窗口累计592/1872帧jank（31.62%）；这是未通过记录，不倒推为无采集时的产品性能。`VisualMotionNaturalInstrumentedTest.capture`原先先截图和压缩，再由`recordVisualPng`判断是否回传；仅关闭PNG参数无法移除采样开销。gfxinfo只在主题末尾读取，第二主题包含第一主题；120条尾部帧记录也不能代表整个流程或已经关闭的Dialog。

本轮在截图之前检查开关，增加每主题/具名阶段清零和在窗口存活时回读。保留同一自然帧时钟、触点、等待、断言与固定领域对象；只做UI测量，不接核心/网络/Room配置。零帧阶段没有可解释的百分位，不能把gfxinfo返回的4950ms当成耗时。方法采样、系统trace均单独列诊断批次，不混入无采集对照。

首轮`baseline1`无截图/无录屏，深/浅色非启动段分别202/1028（19.65%）及165/1085（15.21%）；二级菜单分别16/22（72.73%）、15/21（71.43%）。这只证明指定Redmi API36的Debug固定流程，非生产Release性能。

## 已定位调用链与参考

`methods1`菜单窗口的采样trace可由Perfetto trace processor读取，采到`DropShadowRenderer.createOuterShadowBitmap`125个调用片段、包容耗时约954.2ms，`AppMenuStack`重组及轮廓计算也在采样中。方法采样不提供精确调用次数；包容耗时存在嵌套，不能跨层相加；trace导入报告28项过深栈丢弃，不将采样当成完整CPU账单。

源码链为`AppDropdownMenu`读取每帧开关值并传给`AppMenuStack`，绘制命中范围又递增`hitRevision`触发宿主重组；每次组合重建`AppMenuMorphShape`，后层未变形的菜单也获得新Shape身份。后层明暗直接进入`Shadow`颜色。当前Compose1.12.1的本地制品反编译确认缓存键含Shape、尺寸、密度及Shadow（除offset）；阴影回调的alpha setter也会使缓存失效，不能只换成lambda就声称位图已复用。

固定Flutter `be84736605527034689c95bf27828ff27d912c5e`的Prism `63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185`为参考：`goo.dart`复用相同pane几何的outline，`navigation_dock.dart`保留绘制层和静态子树；`snap_sheet.dart`明确将presentation位移放在paint阶段，只有高度影响layout。该缺陷是Kotlin移植中缓存/状态读取方式的问题，不认定Flutter上游有同一缺陷，不向Flutter主线合代码。

优化保留原曲线、时长、阴影半径/偏移、逐层返回与展开宽度；普通菜单不再订阅拖选命中revision，动画时钟通过State传入绘制层。根卡/后层使用值相等的固定24dp轮廓；阴影单独绘制，其透明度由合成层调制。真正变形的顶卡仍重建几何，不冻结内容、缩短动画或删除效果来制造绿测。

## 已保留的失败与限制

- Perfetto `--background-wait`两次超时，后台会话实际存在；精确匹配PID/文件名停止后保留零字节启动样本，不计有效trace。后续后台trace包含FrameTimeline，但缺应用线程/调度数据，且`traced_flushes_failed=1`，只用于辅助帧类型诊断。
- `cmd package compile -f -m speed`返回Success，但系统回读仍为`status=verify`。该轮不是有效AOT对照，不能声称JIT已排除。
- 首次菜单优化构建失败于DropShadowScope要求Float/Offset而传入Dp/DpOffset；未安装。随后检查缓存实现，改为独立阴影层。日志分别保留，禁止覆盖失败或将不同源码包混算。
- 非Debug对照使用显式开启的`.motion`独立包名和同一Debug固定宿主。该路径不压缩混淆，库使用debug匹配回退且保留验收依赖；仅隔离应用debuggable标记，不等同正式签名/R8 Release；默认构建和正式Release不增加宿主或运行依赖。
- 最终自然录像中的深色List PNG抓到节点行入场前一帧，原视频PTS35.497仅组头、35.763完整节点，同包`dark-recheck1`1/1且终帧完整；保留原图，不称原图为稳定终帧。两张玻璃开场图也是源按钮过渡帧，稳定玻璃子菜单另有回归证据。

## 中间优化与回退记录

`menu-fix1`只含菜单修复，Debug二级菜单卡顿帧深/浅色分别22.58%/23.33%，原无采集基线为72.73%/71.43%。随后曾尝试将面板遮罩及presentation延迟读取；非Debug两轮对照中菜单改善保持，但质量面板窗口由74/531（13.94%）升至106/557（19.03%）。该面板改动已撤回，原日志/包/源码快照保留；不能把中间包性能数字写成最终源码结论。最终菜单单独两轮（各深浅主题）对照：二级菜单61/92（66.30%）→20/129（15.50%），主线程阶段中位34.25→9.45ms；非启动总窗口621/4401（14.11%）→465/4599（10.11%）。面板回到62/535（11.59%），保留原布局实现。最终两轮位于基线之后，不是随机/交错实验；此前组合补丁的交错样本独立保留，不混算。

`methods2`沿用同一菜单采样窗口，采到阴影位图创建58段、包容耗时396.2ms；窗口约8.111秒（原8.028秒），两轮都含相同等待和操作。新trace导入有66项过深栈丢弃，继续保留不完整采样限制。菜单修复保持，取样结束于质量面板打开前，不把该方法样本冒充撤回后的完整包验收。

## 撤销与同步条件

后续同步Flutter/Compose时保留固定参考与本地补丁来源。若新的缓存实现能够保证相同轮廓仅栅格化一次，并且同流程前后测量、深浅主题终帧、多层返回/反转及拖选回归通过，才可撤销本补丁；不能仅凭上游版本升级或一次总帧率改善删除。完整Flutter像素对齐和全部页面性能仍按各自清单验收。

## 文档与制品依据

2026-11-08通过smart-search读取[Compose性能最佳实践](https://developer.android.com/develop/ui/compose/performance/bestpractices)、[Compose阴影](https://developer.android.com/develop/ui/compose/graphics/draw/shadows)和[Perfetto FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline)，正文保存在附件`references/`。依赖实际为Compose1.12.1，阴影缓存键与alpha失效行为由本机对应AAR的`AndroidShadowContext`/`BlockDropShadowNode`字节码交叉核对，未改依赖源码。FrameTimeline的Buffer Stuffing、App Deadline Missed与gfxinfo指标不同，不能混用分母或把它们全称为应用CPU慢帧。
