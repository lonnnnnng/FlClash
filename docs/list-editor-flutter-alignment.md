# 列表输入、工具栏与快速编辑对齐

2026-11-20，KUI-VIS-057。承接056列表草稿，使用用户指定的POCO F2 Pro（31936bb6/API36）、竖屏和默认字号；玻璃默认关闭。

## 实现与上游参考

固定Flutter副本仍为 `../acceptance-artifacts/docked-search-20261110/flutter-full`。参考 `list_edit_view.dart`、`quick_edit.dart`、`widgets/input.dart`、`pages/editor.dart` 和Prism dialog/scaffold；不合并Flutter提交或运行时。

- 单项添加/编辑使用居中圆角表单、填充型输入和满宽“确定”按钮。空值、多行和重复项明确提示；错误出现后随输入重验，已有字段业务校验保留。触摸遮罩或系统返回可取消，确认只修改本页草稿。
- 普通列表将重置/保存合成一个胶囊，添加另为圆按钮；批选时删除/全选合组，标题保留字段名称。添加菜单有独立图标。
- 快速编辑使用带行号的纯文本编辑器，未改动或撤销到原文直接返回、不重新校验；有改动则沿上游返回应用有效内容到列表草稿；无效内容保留并询问继续编辑/放弃。列表页仍显式保存，等待父级持久化回执，不能把快速编辑返回当作配置已提交。
- 提供查找/下一处、撤销/重做、标准/大/超大字号和自动换行。选区/查找不产生撤销记录；撤销、重做各保留最多64项，并在多项时按262144字符预算逐项裁剪；单个超长快照仍保留，状态随既有加密草稿注册表恢复。关闭换行只滚动正文，行号固定，查找显式将横向选区滚入视口。
- `ProfileCodeField` 新增可选字号和换行参数，默认14sp/20sp及换行保持；列表快速编辑使用16sp。`ProfileEditorPage` 新增可选搜索按钮排列，其他入口默认布局保持。

生产实现：`MoreListEditor.kt`、`MoreListEditorChrome.kt`、`ProfileCodeField.kt`、`ProfilePageComponents.kt`。056的列表/目标回执保护保留。

## 开发中问题与溯源

1. 首轮测试编译使用了当前Compose测试库不存在的 `assertTextSelectionEquals`，build1失败；改用已有 `SemanticsProperties.TextSelectionRange` 断言。生产编译通过，不能将此批记作整构建通过。
2. 首轮宽度适配曾在 `height(IntrinsicSize.Min)` 的Row内加入 `BoxWithConstraints`。独立静态核验指出其SubcomposeLayout不支持固有尺寸测量，属于本轮Kotlin候选的崩溃风险，并非已发布或已在手机复现的缺陷。安装前移除内层测量容器，复用外层宽度；共享脚本长文/Provider正文回归纳入最终批次。
3. 集中final3为28/29：横向滚动的第二个测试标签覆盖了正文标签，导致查找断言找不到节点；不是已观测的选区值错误。移除冲突标签，以正文节点读取滚动属性后复验。
4. Release3的Lint内部发生 `AsyncExecutionService.getService must not return null`，未关闭任何检查项；原日志保留，固定源码后重新执行。
5. final5出现旧 `changingSearchDuringExitDoesNotRetainAnEmptyProtocolGroup` 的80ms高度断言间歇失败，同方法final3/4通过。测试原来在保存回调后立即计时，未建立动画首帧起点。test6先确认回执、推进一帧再量80ms，产品动画代码未改；原失败保留。
6. final6另有旧搜索用例在嗅探Sheet关闭动画尚未完成时，直接通过测试宿主切NTP并重开Sheet，迟到关闭导致候选节点缺席。test7等待旧窗口真实消失再切分区，未改变产品导航。该批28/29保留，最终仅定向复验受影响项及三项新编辑用例，不冒称单批全部通过。
7. 056隔离Flutter宿主未带Rust原生资产。057新建独立参考宿主，启用rust_api构建资产并执行 `RustLib.init()`，正常打开上游原始 `ListEditView` 及其快速编辑页面。原固定Flutter源码与原版 `com.follow.clash` 均不修改。这是宿主装配缺口，不是上游产品bug。

## 验收边界

最终产品build5的App motion JVM618项零失败/错误/跳过；motion/Test、未签名Release/R8与双Lint/DEX通过。motion Lint为0错误/73警告/14提示，Release为0错误/69警告/13提示。集中final6为28/29，测试等待修正后final7定向5/5；真实MainActivity main5为1/1（47.374秒）。这29个具名方法分别有通过记录，不写成同一最终批次29/29。产品APK始终与main5相同，main5的test5及组件test6/7均在覆盖前留存并核对安装SHA。

Flutter与Kotlin深浅6组限定范围对照已核对；main5录屏48.987289秒、1404原PTS帧，只抽样审阅12个时间点，不称完整逐帧验收。main5的ports_ime截图在正文绘制前捕获，只证明编辑页与键盘；后续草稿/重开/重建图及Room回读确认两项端口，不作该图稳定正文对照。原版Flutter、原1.yaml与设备设置保持，3个隔离包和USB输入副本已清理，未发布新版本。

本组完整结果和制品哈希记录在 [验收JSON](acceptance/list-editor-parity-poco-20261120.json)。本机原始附件在 `../acceptance-artifacts/list-editor-parity-poco-20261120/`，参考宿主工程为 `../acceptance-artifacts/flutter-list-editor-host-20261120/`。

Flutter宿主直接打开原始列表，未走其完整配置/数据库链路；SDK3.50.0与上游CI3.47.6差别保留。Kotlin仍用系统monospace，而Flutter使用JetBrainsMono；菜单大小选项当前平铺，Flutter为子菜单；精确弹出形变、弹框遮罩浓度、浅色编辑器背景、行距/字形和完整连续运行对照仍不等价。静态截图、录屏开销不作为性能提升证据。

下一组继续搜索无结果插图、菜单/目标选择精确形变和剩余页面间距，再补Flutter连续转场对照；菜单首开/关闭无损Perfetto、RenderThread和正式R8性能仍单列待办。语义预检/系统兼容保留现状，TLS调查按用户决定归档。
