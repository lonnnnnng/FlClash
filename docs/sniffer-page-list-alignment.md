# 域名嗅探页面、列表草稿与真实转场

## 2026-11-23 KUI-VIS-087 真实父子路由复验

当前提交 `0e1d9b5` 的 Motion 与 AndroidTest APK 在 POCO F2 Pro/API36 上重新运行 `MainSnifferNaturalInstrumentedTest#snifferPortsSaveReopenAndRapidBackUseRealNavigation` 两次通过，最终录制批次 1/1。真实 MainActivity 路径覆盖配置列表、临时 CUSTOM、更多、Sniffer、HTTP、端口、IME 搜索、快速返回、独立保存、退出重开、Activity 重建和 Room 回读。原有配置行/正文/脚本/偏好/选择保持，临时 profile/body 清除，runtime 停止。12 张截图分片完整性和 PNG SHA/尺寸核验通过；56.2 秒自然交互录像设备端 SHA 回读一致。

首次尝试的 Downloads 读取遇 scoped-storage `EACCES`，第二次被首次启动通知权限窗挡住 UI 树；均未进入业务流程，第二次 finally 核对并清理了临时对象。只给隔离 Motion 包授予通知权限后重跑通过。两类失败均独立保留，不算入通过次数。

同一 POCO 深色画面上，对照隔离 Flutter `SnifferView` 内存夹具。Sniffer 空态和添加面板静止档位位置接近；展开搜索并显示 IME 时，面板上沿与搜索 Dock 仍可见少量垂直偏差，留作后续视觉收敛。Flutter 空态插图存在随机形态，截图不同不作为 Kotlin 缺陷。此宿主不包含上游完整 MainActivity 导航；原版 `com.follow.clash` 未操作。自然录像不作掉帧/CPU/GPU 证据，本组没有产品代码变化，也没有重跑 JVM、Release 或 Lint。

正式包、普通测试包、原版 Flutter 包、工作区 `1.yaml`（34220 字节、SHA `7536dd03…aeee8d7`、mtime 保持）、Room/偏好与设备方向设置保持。Motion/隔离 Flutter 包及本轮 USB 暂存文件均已清理，服务停止。菜单 discarded 来源、足量 app-surface FrameTimeline、GPU/fence 与 RenderThread 归因仍未闭环；完整 Flutter 视觉和其他页面/面板转场仍 `PARTIAL`。

[验收 JSON](acceptance/sniffer-route-poco-20261123.json)，[本轮截图、录像和日志](../../acceptance-artifacts/sniffer-route-poco-20261123/)。

## 2026-11-23 共用空态按钮范围复验

NTP 页44dp紧凑添加动作先前复用了 Sniffer 也使用的空态代码，导致 Sniffer 按钮从 Material 默认40dp变高。本轮把紧凑内边距和高度条件限制为 NTP；Sniffer 继续使用默认 Material 高度与内边距。POCO F2 Pro/API36 的 `vis086SnifferEmptyActionKeepsDefaultButtonGeometry` 通过，实测40dp；NTP `vis085NtpParentEmptyPageMatchesFlutterReferenceInBothThemes` 仍通过。配置页空态视觉及这两条共享组件边界结果见[KUI-VIS-086验收](acceptance/profile-empty-nullstatus-poco-20261123.json)。本复验不覆盖 Sniffer 整页或完整父子连续转场，仍需继续。

2026-11-20，KUI-VIS-056。用户指定 POCO F2 Pro（31936bb6 / API36），竖屏、默认字号，玻璃默认关闭。本组推进域名嗅探整页与协议子页；完整 Flutter 视觉和菜单性能继续为 PARTIAL。

## 实现与来源

参考本机固定 Flutter 副本 `../acceptance-artifacts/docked-search-20261110/flutter-full` 的 `SnifferView`、`OverrideView`、`SelectionSheet`、`ListEditView` 和 Prism 图标/字号。固定副本与仓库残留 Flutter 源有描述文字差异，不能混用；验收 JSON 保存实际文件 SHA，未合并 Flutter 提交或运行时。

- 标题统一为“域名嗅探”，顶栏使用圆形返回和纸笔快捷编辑；空态使用上游说明、眼睛图形与单层主色材质，位置和按钮底色收敛。
- 添加面板沿用 9/16、90% 两档及 IME 恢复，统一悬浮边距、居中标题、左关闭、56dp 候选、分组/说明字号和可见搜索胶囊。搜索匹配名称、说明及字段键的全部分词。
- 分组称为“选项／协议”；字段名称、两项描述及协议摘要对齐固定参考；目标菜单显示“默认／已开启／已关闭”，语义仍是 null/true/false。
- 端口、域名与地址列表新增独立列表草稿：单项添加/编辑、重复拒绝、批选删除、整行拖动、重置、快速编辑、离开保存/放弃。草稿确认前不写父配置，保存失败留页；清空端口保留协议及独立目标，其他空列表恢复继承。
- 资源排序组件新增可选紧凑行与自定义内容，既有规则资源/图库调用默认行为保持。搜索底色新增显式参数，本组仅添加面板启用。

生产代码位于 `ProfileMoreEditor.kt`、`MoreListEditor.kt`、`ProfileMoreFields.kt`、`CustomProfileEditor.kt`、`ResourceOrderList.kt`、`ProfileSearchField.kt` 与 `AppGlyphs.kt`。

## 保存恢复问题溯源

这是 Kotlin 窗口状态问题，不是 Mihomo 或已确认 Flutter 上游缺陷。

1. 新列表首个实现将初始列表存入 saveable 状态，而关闭标记仅存在父层内存。保存中重建后，已保存列表可能仍被当成脏草稿。独立源码审查发现后，改为读取最新 initial 列表，并保存 `awaitingReceipt`；只有提交结果与当前草稿相同、且保存结束，才退出。失败保留草稿和错误。
2. 协议目标菜单原有 `destination` 可恢复，但 `pending` 不可恢复。保存中重建会丢掉成功后关闭菜单的意图。改为保存 pending 与提交前 revision，恢复时只接受更大的版本回执，不能在重建本身发生时提前关闭。

回归 `vis056ListReceiptAfterStateRestorationClosesSavedDraft`、`vis056DestinationReceiptAfterStateRestorationClosesMenu` 使用保存状态恢复模拟和受控异步回执，断言只提交一次、成功后退出；不冒称系统冷进程或真实磁盘写入中死亡。真实 MainActivity 另验证保存后 Activity 重建和 Room 回读。

后续同步上游时仍应保留 Kotlin 的持久化回执边界：Flutter 参考列表先 pop 草稿结果再更新 model，不能直接照搬成 Android 异步保存前退出。

## 验收与失败记录

最终产品源码为 build9，组件最终测试 APK 来自 testbuild10。真实录像使用 test9，该 APK 未在覆盖前单独保存哈希；testbuild10 日志确认产品编译任务复用、测试重新编译。当前自然用例源码有哈希，但不以此独立证明前一测试制品的身份。App motion JVM 618 项零失败/错误/跳过；motion/Test 构建、motion Lint（0错误/73警告/14提示）、未签名 Release/R8 和 Release Lint（0错误/69警告/13提示）、ML Kit DEX 门禁通过。未安装正式 Release、未发新版。

真实 MainActivity 自然时钟流程经配置卡片→编辑→更多→域名嗅探→HTTP→端口，完成输入/IME、保存、目标切换、退出重开、Activity 重建和 Room 覆盖值回读。源正文 SHA/大小/mtime保持；本轮只临时复制授权 `../1.yaml`，没有在线上传。录像保留原始 PTS，单独抽取进入/返回窗口，截图和录屏开销不用于宣称性能改善。

原始失败保留：首轮组件17/18的容器文字断言；main1初始化IP质量缓存晚写与导航定位、main3误点首页同名入口、main4新窗口接管前Back；main5/7固定900ms后菜单仍未退出，不能据此认定父页返回故障。随后改为等待真实Room目标值及菜单消失，main8与main-final9完整流程通过。

扩展29项批次为27/29：新增域名候选在半屏未被定位，后改用实际搜索入口补验；另一项为未改动图库的旧 `assertIsSelected` 断言，当前控件使用颜色表达选中且未暴露该语义。该失败单独保留，不恢复取消的无障碍语义，也不将该批记为全绿。最终集中批次精确选择本组更多设置、共享排序、下沿搜索与CUSTOM更多页用例，**28/28**通过（185.78秒）。真实主流程最终**1/1**，与组件共92张PNG（88全屏、4局部），12张联系图目视；8组同主题对照与1组跨主题列表结构对照分别记录。最终录像48.970944秒、1364原PTS帧，进入/返回窗口保留抽帧39/42张并抽样核对，对应原PTS窗口实际77/82帧，因此不是完整窗口逐帧全检。

## 仍有差异与下一步

固定 Flutter 独立宿主的快速编辑未初始化 RustLib，显示“编辑器不可用”；只将空态、添加面板、HTTP/目标、端口空态和两项列表作为有效运行对照。Kotlin 快速编辑功能已验，不称与 Flutter Rust 编辑器视觉一致。

单项输入弹框、快速编辑器、搜索无结果插图、目标菜单精确形变、列表工具栏、整页清除覆盖扩展入口、字形/插图随机装饰和部分像素间距仍有差异。原版 `com.follow.clash` 不操作；隔离参考副本 SDK3.50.0 与上游 CI3.47.6 的差别保留。

下一组优先收尾这些剩余子页/面板精确差异，再集中菜单首开/关闭的无损 Perfetto、RenderThread 归因及正式 R8 性能对照。语义预检和系统兼容维持现状，其他暂缓项不恢复。

[验收 JSON](acceptance/sniffer-full-parity-poco-20261120.json)，本机原始附件 `../acceptance-artifacts/sniffer-full-parity-poco-20261120/`。

## 057接续（2026-11-20）

单项输入、列表胶囊和纯文本快速编辑已由[057](list-editor-flutter-alignment.md)接续；新隔离宿主启用Rust资产并初始化，正常运行上游快速编辑。056的未初始化记录仍保留为当时限制，不再代表当前阻塞。精确菜单/遮罩/字形及完整连续对照继续待办。
