# Popup 首帧与文件选择器连续转场

2026-11-25，作者 long。POCO F2 Pro（31936bb6/API36）验收；工作区原始 `1.yaml`、Flutter 源码与数据未修改。

## Popup 首帧分解

独立 R8 `.performance` 变体中打开 `MENU_TRACE`，只为首帧测量组合/行组合、测量、绘制、shadow 与 morph，并捕获真实 MainActivity 的文件选择流程。正式 Debug/Release 的 `MENU_TRACE` 为 false；测试 APK 是独立包名，不进入正式包性能比较。

两份独立 `popup-draw-detail` trace 的首个 UI `Choreographer#doFrame` 标记如下，单位毫秒：

| 切片 | draw-detail 1 | draw-detail 2 |
| --- | ---: | ---: |
| `popup-content-compose` | 8.924 | 10.930 |
| `stack-compose`（包含于 Popup 组合） | 6.294 | 7.383 |
| QR 行组合 | 2.035 | 2.510 |
| 文件行组合 | 0.576 | 0.772 |
| 订阅行组合 | 0.858 | 0.838 |
| `morph-draw` | 0.385 | 0.373 |
| `platter-shadow-draw` | 0.048 | 0.067 |

`popup-content-compose`、`stack-compose` 和行组合是父子切片，不可相加。Popup 各轮首帧总 UI 墙钟为66.373–76.702ms、线程Running为53.007–60.142ms。测量约6.901–8.020ms，relayout约4.936–8.726ms，layout约4.250–5.369ms，draw约13.581–18.957ms，Binder约9.169–11.101ms；这些分类也有重叠，不能求和成总量。后续菜单动画帧 `morph-draw` 和外层阴影绘制分别可达约3.6–4.3ms，说明首帧组合与持续动画绘制是不同阶段。

曾尝试去掉 Popup 外壳的 `IntrinsicSize.Max`。候选两轮首帧 UI 墙钟72.612/76.702ms，测量7.067/8.020ms；此前两轮为73.425/69.663ms、测量7.680/7.403ms。首帧总体与测量均未呈稳定改善，因此产品布局源码恢复原 intrinsic 约束，未保留该候选。

12秒文件选择器路线 Perfetto 包含79个 Kotlin UI 帧，其中5个 UI 帧超过16.6ms。首次 Popup UI 帧75.257ms（Running61.368ms、waiting12.624ms）；后续慢帧约19.032–25.810ms，其中既有Running，也有等待/Binder区间。该采集覆盖应用菜单打开、DocumentsUI 展示及系统返回后的窗口交接，只能描述此流程观察到的UI工作，未提供单独的GPU fence或RenderThread根因。

## 文件选择器与连续转场

Kotlin真实MainActivity流程为：空配置页 → 添加来源菜单 → “从文件导入 YAML” → 系统 DocumentsUI → 系统Back → 配置空态 → 再开/收起来源菜单。Flutter参考包同样走配置列表 → 添加来源 → 文件 → DocumentsUI → Back → 原列表 → 再开/收起菜单。两边都没有选择文件、读取正文或提交配置。

固定 Flutter 参考 `be84736605527034689c95bf27828ff27d912c5e` 的 `lib/common/picker.dart:10-11` 调用 `FilePicker.pickFile(initialDirectory: await appPath.downloadDirPath)`；`lib/common/path.dart:29` 默认使用 `getDownloadsDirectory`，`downloadDirPath` 在目录为空时回退应用目录。Kotlin `ProfileFileImportHost.kt:20-22,45-49` 使用 `ActivityResultContracts.OpenDocument()` 并传 `*/*`，未显式设置 initial URI。POCO上两个入口都解析到 Android DocumentsUI；Flutter初始页面为“下载”，Kotlin为“最近”。

这说明 Flutter 调用方明确提供了初始目录，而 Kotlin 当前未提供。但固定 Flutter源码未带该版本 picker 插件的 Android native 实现，本轮没有确认它最终使用的 Intent action、MIME 与URI映射；一次 `EXTRA_INITIAL_URI` 探针也仍显示 DocumentsUI“最近”，不能据此断言所有厂商实现均忽略该值。为避免猜测路径或更换SAF契约，本轮没有修改 Kotlin picker。后续若继续对齐，先核对锁定版 `file_picker 7.0.1` / `path_provider_android 2.1.6` 的Android实现，再用 POCO 对照相同目录和返回动画。

## 构建、测试与范围

- `:app:testMotionBenchmarkUnitTest :app:assemblePerformanceBenchmark` 在显式性能与Motion变体下成功；Motion XML共84套、630项，失败/错误/跳过均0。
- `:app:assembleDebug` 成功，普通Debug中追踪关闭。
- POCO另存录屏：Kotlin 13.391秒/203帧、Flutter 13.123秒/225帧。连续过程目视检查过关键帧。正式KClash `0.1.21` 的安装/更新时间为2026-11-24 23:03:27；Flutter `0.9.4` 的安装时间2026-11-07 02:44:08、更新时间2026-11-22 14:21:51；验收结束值一致。诊断 `.performance` 包已卸载，代理/VPN服务均未运行。
- 没有测量真实Profile内容页，也没有做全量子页转场、其他设备/OEM、低API或GPU fence闭环。两个安装包当时的配置状态不一致（Kotlin空库、Flutter有一份配置），因此本轮只比较系统文件选择器和打开/返回动作，不声称整页视觉等价。

验收JSON：[`acceptance/popup-first-frame-picker-transitions-poco-20261125.json`](acceptance/popup-first-frame-picker-transitions-poco-20261125.json)。原始Perfetto、CSV、录屏、截图及SHA位于本机 `../acceptance-artifacts/dialog-first-measure-poco-20261125/`。
