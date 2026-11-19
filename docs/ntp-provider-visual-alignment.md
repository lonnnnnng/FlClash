# NTP 添加面板与 Provider 选择器对照

2026-11-20，KUI-VIS-055，本组已在用户指定的 POCO F2 Pro（31936bb6 / API36）完成。玻璃保持默认关闭，限定竖屏和默认字号。完整 Flutter 视觉仍为 PARTIAL。

## 本组变化

- NTP 添加面板使用悬浮半屏形态、居中标题和左侧关闭，保留 9/16 与 90% 两档。拖动条按上游 6dp 顶距与 4dp 厚度排布，普通候选最小 56dp，说明使用 15/20 字号槽；分隔线覆盖绘制，避免逐行增加高度。
- NTP 文案与上游一致：状态、服务器、端口、同步间隔、拨号代理、写入系统。Android 不生效的说明保留。未修改默认值、逐项保存或核心应用语义。
- Provider 绑定选择器改为分组单选列表，当前项有勾选，不靠导航箭头表达选择；名称和说明支持空白分词、忽略大小写的全词匹配。保存失败、忙态、取消搜索及原有选择恢复机制继续保留。

对应实现为 `ProfileMoreEditor.kt`、`ProfileMoreFields.kt`、`ProfileProvidersPage.kt`；共享 `ConnectionModalSheet.kt` 仅新增默认零偏移的拖动条位置参数，NTP 显式启用。

## 上游依据及结论边界

固定 Flutter 副本位于本机 `../acceptance-artifacts/docked-search-20261110/flutter-full`，无 Git 元数据，因此在 JSON 中保存实际源文件 SHA。隔离宿主直接复用 `NtpView`、`SelectionSheet` 及 Prism 组件；SDK 为 3.50.0，与上游 CI 的 3.47.6 不同。

上游没有 Kotlin 这张独立“重绑定资源”页。Provider 对照是共用 SelectionSheet 的 12 候选、当前项、搜索、清除和重开状态；真实绑定保存由 Kotlin MainActivity 路径单独验证。少于 10 项时 Kotlin 仍保留常驻搜索，上游共用组件在 10 项起提供搜索。

初始候选曾依据通用面板测试限制短内容自然高度；真实 Flutter NtpView 在短列表时仍能展开至 90%，因此撤回限制，未认定为上游缺陷。后续并排截图发现标题下移和副标题过小，按 SheetDragHandle / ListHeader / subheadline 参数修正。早期候选和错误命名截图保留在本机，但不纳入最终对照集合。

16 组状态对照已目视：NTP 浅色 6 项/展开/剩 3 项、深色 6 项/展开/剩 3 项/最后一项/全部添加；Provider 代理深色和规则浅色各选择/过滤/清除/重开。字形度量、分隔线色度、深色搜索底色和父页工具栏/空态插图仍有差异，不记逐像素或全页一致。NTP 全部添加截图用于确认终态，父页控件仍属后续整页范围。

## 验收结果

最终 build4 单批 **11/11**，70.577 秒，零失败/跳过。覆盖 NTP 深浅色添加/展开/短列表/关闭重开/最后提交后退出，Provider 名称与说明多词筛选、无结果恢复、真实 IME 清除/取消、选择重开，及 Sniffer、失败重试、异步回执回归。

真实 MainActivity 从 CUSTOM 资源页打开两候选选择器，筛选/清空/选择备用候选后，Repository 重新读取已保存 YAML 的引用 ID，再次打开确认勾选已切换。绑定 ID 位于配置正文，不能将此描述为“Room 绑定字段回读”；fixture 另行核对 Room 行、正文、脚本、偏好、当前选择和 STOPPED 状态。仅使用授权 `../1.yaml` 原字节副本，未上传网络。

App motion JVM 报告 **618 项**零失败/错误/跳过；motion 应用/测试构建和 Lint 通过（0 错误、73 警告、14 提示）。最终未签名 Release/R8 和 Release Lint 通过（0 错误、69 警告、13 提示），ML Kit 注册器 DEX 门禁通过。未安装 Release、未发新版，未将缓存任务当成全工程新增测试。

42 张 PNG 经分片索引、字节数、SHA 和尺寸核验，并通过 7 张联系图、8 张对照图目视。41 张全屏 1080×2400；1 张退出动画为局部节点截图，尺寸随动画采样变化，不以固定尺寸冒充全屏。motion 双 APK 安装回拉与构建产物一致。

结束已回收本轮 motion/test/隔离 Flutter 宿主和哈希核对后的设备正文副本。原版 `com.follow.clash` 版本/安装时间、手机旋转/字号/深色设置、根目录 `1.yaml` SHA/大小/mtime 全部保持，无 KClash 服务。

[脱敏验收 JSON](acceptance/ntp-provider-flutter-parity-poco-20261120.json)；本机附件 `../acceptance-artifacts/ntp-provider-parity-poco-20261120/`。本组不包含自然时钟整页视频、网络/VPN、低 API/OEM 或菜单性能。

## 接续顺序

1. 收尾 Sniffer 整页、协议子页及工具栏/空态差异；沿用本组已核实的标题与列表尺寸。
2. 验真实 MainActivity 连续进入、返回、键盘收放和快速反向，区分静态截图与自然时钟逐帧证据。
3. 再做菜单首开/关闭无损 Perfetto 重采、RenderThread 归因和正式 R8 对照。保留旧 trace 丢块和无 fence/GPU 轨道限制，不推断根因已确定。
