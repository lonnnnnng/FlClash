# 搜索空态、编辑菜单与材质对照

2026-11-20，KUI-VIS-058，POCO F2 Pro（31936bb6/API36）、竖屏和默认字号。沿057列表编辑继续，玻璃默认关闭；不修改Flutter原版或核心。

## 来源与实现

固定参考为 `../acceptance-artifacts/docked-search-20261110/flutter-full`：`selection_sheet.dart`、`pages/editor.dart`、Prism的`null_status.dart`/`dialog.dart`/`color.dart`和`app_glyphs.dart`。独立宿主启用Rust资产并初始化，复用原始ListEditView和SnifferView。上游随机插图形状/装饰仍不能宣称逐像素相同。

- 嗅探添加面板无结果时显示放大镜复合插图和“没有匹配的结果”，布局位于候选视口偏上方，下沿搜索保持；键盘缩小视口仍可滚动。插图按画布40%绘制中心图标，避免紧凑画布仍用固定64dp。
- 列表添加菜单和快速编辑菜单接已有AppDropdownMenu，使用实际按钮快照作为回源目标。叶子动作等关闭动画结束再执行；后台/页面退出沿组件已有取消机制。
- 字号由平铺项改为原位子菜单，系统返回只退一层、外点关闭全部；使用Flutter的16/18/20sp、1.2倍行高，大小/撤销/重做/换行glyph与尾部勾选，去掉换行Switch和额外分隔。
- 快速编辑独立surface背景，其他ProfileEditorPage保持默认色；共享代码组件新增可选行高，其他入口默认值保持。单项输入窗口显式使用浅色20%、深色48%遮罩，避免平台默认dim压暗浅色页面。
- 菜单回源期间可能收到输入法迟到文本。撤销/重做回调重新检查正文版本及历史存在性，只在正文仍与点击时一致时应用；静态核验提出的条件边界由受控回归覆盖，不称已在用户手机自然复现。

## 开发与测试溯源

首次final3为11/14，三个失败均保留：新菜单测试在菜单节点出现前冻结时钟；旧添加动画测试在输入法抬升Sheet期间点击，尚未受理保存；遮罩参数读取时新Dialog尚未取得焦点。test6分别等待菜单可见、键盘隐藏与窗口焦点，并在空态截图前确认Sheet回到原停靠位置。没有为测试改变产品时序。

第一版空态文案依据应用侧翻译为“无搜索结果”；运行固定Flutter原SelectionSheet后确认其Prism中文为“没有匹配的结果”，build3同步实际文案。插图尺寸与字体参数仍以固定源码为准。

## 空态键盘恢复缺陷

final6（13/14）和final7独立复验发现实际Kotlin缺陷：空态下键盘隐藏后顶部从原1094px停在339px，保持展开。最初怀疑截图/初值时序不准确，稳定初值复验后推翻该假设。不是Flutter或内核问题。

`ConnectionModalSheet`原来在恢复动画开始前清空`detentBeforeIme`，IME收起时的geometry变化会取消重启协程，已丢失恢复目标；同时临时自动展开会更新savedDetent。修复为按自动展开/原档位更新锚点、动画真正到达后才消费恢复意图，并暂停自动IME期间保存临时档位。用户实际拖动或手柄切档会接管并清除旧恢复意图，防止动画被手势取消后阻塞后续选择。修复首轮final5为16/17：半屏无结果恢复已通过，但旧正常搜索用例通过列表语义滚动展开后仍回到半屏（311→1094px）。本机Compose 1.12.1字节码确认语义滚动只发位移、无fling，`currentValue`已改变而`settledValue`未归档；调整为非隐藏档位记录currentValue，HIDDEN关闭仍只认settledValue，避免手拖中途关闭。`restoreDetentAfterIme=false`默认行为保持。此缺陷与修复及原失败在验收JSON单列，不覆盖历史成功。

真实main8在“配置”定位前被系统通知权限窗口遮挡，未进入编辑链；截图和PermissionController前台回读确认。补授独立motion包通知权限后重跑，产品代码未改。该失败及其数据保护通过记录保留。

## 验收与边界

最终build6同套App/Test集中17/17（124.288秒），真实MainActivity自然流程1/1（47.253秒）；618项App motion JVM零失败/错误/跳过，motion/Test、未签名Release/R8与双Lint/DEX通过。motion Lint0错误/73警告/14提示，Release0错误/69警告/13提示。最终两APK覆盖前留存，安装回拉SHA与源码快照一致。

组件70张PNG与真实流程10张PNG留存；Flutter深浅10组限定范围对照，随机装饰/字形和深色字号当前选择差异分别注明。真实录像49.065789秒、1406原PTS帧，仅12个时间点抽样审阅，不称完整逐帧。原版Flutter、原1.yaml和设备设置保持，隔离包/USB副本清理；本轮不发版。

具体批次、安装回拉哈希及图片范围见[验收JSON](acceptance/menu-empty-parity-poco-20261120.json)。原始附件在 `../acceptance-artifacts/menu-empty-parity-poco-20261120/`。

主流程ports_ime图在正文绘制前捕获，只用于键盘/编辑页可见性；保存/重开/重建图和Room回读证明内容。Flutter菜单自动换行未勾选、Kotlin已勾选，根菜单仅作结构/材质比较。

完整Flutter连续录像对照、菜单几何逐帧同态、浅色工具栏圆按钮底色、字形/随机装饰和其余子页仍待；本组菜单使用既有动效实现，不将静态截图或带录屏的结果当作首开/关闭性能改善。无损Perfetto、RenderThread归因与正式R8性能继续单列。
