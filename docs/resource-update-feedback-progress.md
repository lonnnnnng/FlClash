# 资源更新行内错误与下载进度

KUI-VIS-096，2026-11-24。POCO F2 Pro（`31936bb6`，Android 16/API 36）。公开版仍为 0.1.18；本组未发版。

## Flutter 参考行为

固定参考为 `upstream/main=afe7136002271fd64f098b154e7fff40be721eb8`。目标页是 Tools→资源概览 `lib/views/resources.dart`，不是“自定义资源”里的规则库。

- Flutter 单项同步在 `resources.dart:309-319` 通过 `safeRun` 反馈；Geo 后续下载错误由 `providers/actions/geo_resource.dart:46-93` 显示错误 Banner。
- 批量同步在 `resources.dart:107-112` 收集失败；`common/dialog.dart:240-259,286-323` 显示摘要 Banner 和详情 Sheet。
- `_ResourceItem` 在 `resources.dart:338-376` 只区分更新中 spinner 与已保存的数量/大小，没有行级错误状态。因此 Kotlin 改成行内错误是本产品的交互调整，并非声称复刻 Flutter 原行为。

## Kotlin 实现

- 失败记录带 `ResourceUpdateRow.key`（`kind:id`），列表按 key 放回对应行；同名资源不会串错。只有读取列表或保存更新设置这类没有具体资源行的错误继续显示在页面顶部。
- 单行重试只清除此行错误，批量更新每行完成后立即发布自己的结果，慢资源不会遮住已失败资源。错误行保留到该行下一次重试。
- ICONS、RULE、SCRIPT 通过带 `onProgress` 的分块读取上报已收字节；有可靠 `Content-Length` 时同时显示已下载/总量并绘制确定进度条，未知或压缩长度时显示已下载量和不确定进度条。
- Geo 由核心 HTTP body reader 按读取字节上报，事件携带资源类型、当前尝试、已读字节和总长度；最多每 200ms 发送一次，并在完成/失败时发末值。核心状态事件用资源类型定位，Kotlin 映射到 `GEO:<type>`，避免每次进度回调重查 Room。
- 下载进度是当前进程内的临时状态；页面重开仍可看到正在下载的活动状态，但进程终止后不从持久化任务恢复。

实现位置：[ResourcesPage.kt](../android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/ResourcesPage.kt)、[ResourceUpdatesRepository.kt](../android/app/src/main/kotlin/com/lonnnnnng/flclash/data/ResourceUpdatesRepository.kt)、[ResourceDownloadProgress.kt](../android/app/src/main/kotlin/com/lonnnnnng/flclash/data/ResourceDownloadProgress.kt)、[GeoUpdateStatus](../core/protocol.go) 和 [Geo 下载器](../core/Clash.Meta/component/updater/geo_download.go)。

## 验证与边界

- App JVM：下载进度节流/未知总量用例 1/1；HTTP 分块字节回调与订阅重定向用例 2/2。CoreBridge 事件解析 39/39；Mihomo `component/updater` race 与资源下载重试回归通过，Kotlin core Geo 事件用例通过。
- POCO Motion：资源概览深浅主题/行内错误、菜单关闭后打开编辑框、进度行文案/进度条三项通过；真实 MainActivity 资源输入的错误/保存/取消/重开流程通过。另进度行截图用例 1/1；页面长按、IME 返回及返回工具页的自然触摸录像 PTS 单调，共 468 帧。
- 真实 Core→UI Geo 字节事件未记为真机通过：独立 Motion 库为空，`GeoResourcesHostInstrumentedTest` 在现有 `activeProfile()` 前置读取处因“没有可用配置”退出，尚未发出下载请求。未导入配置、未改正式库和授权 `1.yaml`；Geo 本身由 Go HTTP 回环下载/重试测试、CoreBridge JSON 解析测试和行内 UI 状态用例分段验证。
- Debug、未签名 Release-R8、Motion 与独立 Performance-R8 诊断包构建成功；Debug/Release/Motion Lint 各 0 errors、11 warnings、1 hint；Release APK 的 ML Kit DEX 注册器门禁通过。
- 正式 KClash 0.1.18 的 PID `20700` 始终保留，未对它安装、改设置或触发更新；Flutter 原版包未动。Build 未创建版本标签，不触发发版流水线。
- 最终菜单 trace 在 R8 `PerformanceBenchmark` MainActivity 包上采三次开/关，不录屏、不跑 Instrumentation。Open 的 FrameTimeline 共 50 个 On-time、25 个 Buffer Stuffing、7 个 Dropped、2 个 App Deadline Missed、2 个 App Deadline Missed+Buffer Stuffing 记录（按窗口 layer 统计）。其中有 App Deadline Missed 的 UI 帧在匹配的 `Choreographer#doFrame` 内出现约 21–30ms Running，主要工作可见 traversal、animation、Recomposer、Popup measure/draw；相同 token 的 RenderThread Running 约 0.1–0.2ms。Dropped Frame 没有捕获到对应 shader 或 `waitForBufferRelease`，不能归因。
- Close 的 93 个弹窗 FrameTimeline 帧均标为 Buffer Stuffing/Late Present；其中观测到 77 个 `waitForBufferRelease` slice、合计约 89.8ms，平均每帧约 1ms，不能据此认定为主要原因。FrameTimeline fence 字段为空；trace 中有 `dma_fence_signaled` 和 KGSL 排队/提交/完成事件，但没有 `dma_fence_wait_start/end`，GPU/fence 等待仍未闭环。
- Perfetto 全局 `traced_chunks_discarded=33` 的 `idx=NULL` 没有 producer owner；本地缓冲区丢弃/覆盖/read-gap 为 0，final flush 失败为 0。不能把这 33 个全局计数归因到 KClash，也不能认定 trace 全局完整。
- 前一轮 R8 trace 的首开 shader 编译与 Deadline Missed 同窗，最终源码复测主要看到 UI traversal/动画忙帧，来源未稳定复现；本组没有声称修复菜单慢帧或取得稳定性能收益。完整 Flutter 视觉仍为 PARTIAL，玻璃默认关闭，主题/语言/检测更新仍按用户决定不作 Flutter 对齐。

验收证据见 [resource-updates-poco-20261124.json](acceptance/resource-updates-poco-20261124.json)；真机截图、自然路线录像与最终 R8 trace 保存在工作区外层 `acceptance-artifacts/resource-updates-poco-20261124/` 及 `acceptance-artifacts/rule-resources-poco-20261123/r8-menu-final-20261124/`。
