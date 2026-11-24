# 配置来源菜单到订阅表单的交接

记录日期：2026-11-25

## 发现与修改

POCO F2 Pro 上，点击“添加订阅链接”后，到链接表单首次绘制的基线时间为 586.763ms。Perfetto 显示 Popup 窗口完成回源动画后，Dialog 首帧约再等 12.973ms；源码中真正占主要时间的是菜单项触发后固定播放的 500ms 回源形变，再等待 Popup detach。

`AppDropdownMenu` 现提供 `actionCloseDurationMillis` 参数，默认仍为 500ms。`ProfileSourceMenu` 为选择来源后的页面交接设置 250ms，并将回源形变进度完整映射到原有终点；点外关闭仍保留 500ms。旧 Popup 仍必须 detach 后才执行下一步，避免两个窗口争抢焦点或输入法。

## 实测与回归

POCO F2 Pro / API 36 的独立 R8 `MainActivity` 性能包上，点击到表单首帧两轮为 337.567ms、336.829ms，较基线分别缩短 249.196ms、249.934ms（约 42.5%）。Popup 到 Dialog 的窗口交接间隔仍为 13.381ms、13.752ms，没有明显变化。

`ProfileInputWindowInstrumentedTest#menuActionRunsAfterPopupViewHasDetached` 在 POCO 上通过 1/1；Motion JVM 630 项、84 个 suite 通过，失败、错误和跳过均为 0。Performance-R8、Motion 与 AndroidTest APK 构建成功。独立测试包采样后全部卸载；正式包和 Flutter 参考包的版本与安装时间保持。

## 范围限制与后续

这是“来源菜单选择项到订阅表单”的交接优化，不代表其它菜单或整个应用的掉帧已有稳定改善。首个 Popup 帧仍观察到约 67–74ms 的慢帧；GPU/fence 等待和 RenderThread 慢帧根因仍需单独归因。尚未完成其它子页的完整 Flutter 运行对照与连续转场验收，玻璃继续默认关闭。

原始 Perfetto、trace SQL/CSV、构建日志和截图位于工作区外的 `../acceptance-artifacts/dialog-first-measure-poco-20261125/`。详细数值、哈希、设备保护状态见 [验收记录](acceptance/visual-source-menu-handoff-poco-20261125.json)。
