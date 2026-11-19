# 协议目标选择与分区 YAML 编辑

2026-11-20，KUI-VIS-059，用户指定POCO F2 Pro（31936bb6/API36），竖屏、默认字号，玻璃默认关闭。承接058，范围仅协议目标菜单和NTP/Sniffer分区YAML；Flutter仅作参考，不修改原版包或合并Flutter提交。

## 实现与来源

固定参考 `../acceptance-artifacts/docked-search-20261110/flutter-full` 的 `views/config/sniffer.dart`、`override.dart`、`ui/list_item.dart`、Prism `popup.dart`/`glyphs.dart`。运行使用前组独立Rust参考宿主，未初始化正式Flutter数据库或内核。

- 协议目标值改为17/22文字、4dp间距和24dp双向picker，源快照仍只接管行尾按钮，保留左侧标题。端口使用现有自绘前进箭头；分隔线覆盖行底，不增加实际高度。
- 三态菜单局部启用44dp行及选中项右8dp内边距，共享菜单默认48dp保持。菜单仍按源右缘/顶部定位，没有新增“选中项贴源居中”的偏移；固定Flutter源码不存在该定位规则。
- “默认／已开启／已关闭”仍对应删除字段/true/false；端口与目标独立保存。保存中返回/外点禁用，失败保留菜单，只有新revision回执关闭；不为视觉修改提前退出。
- 分区YAML复用列表的代码编辑表面：行号、高亮、查找、撤销/重做、16/18/20字号与换行子菜单；标题沿Flutter只保留分区名。有效改动返回后异步校验和保存，无效内容询问继续编辑/放弃，保存失败保留正文。默认状态行不再显示长技术说明和额外确认按钮。
- `MoreCodeEditor`只承载编辑交互，列表返回仍只更新本地列表草稿；YAML返回由分区管理持久化。原文未改直接退出、语义未改不重复提交。保存中重建通过保存的revision与关闭意图等待回执。

## 保存拒绝边界溯源

候选实现曾把 `awaitingReceipt` 同时作为禁用和返回门闩。静态核验发现 `ProfilesViewModel.saveMoreOptions` 在busy时可不受理、无新revision/error；持续拒绝也可能重复相同error，旧Effect不再触发，导致候选YAML永久等待。这是本轮Kotlin候选风险，未发布且未在用户自然操作中复现。

修复后回执标记只负责成功后关闭；禁用只跟随实际checking/saving，编辑会作废旧关闭意图，Effect同时观察标记，重复错误能释放旧意图。受控用例连续验证无状态受理拒绝、两次相同错误拒绝、仍可编辑和原语义退出。忙态防重复由同步checking与父层实际saving共同承担，不等待不存在的回执。

## 本轮失败与修正

- `final2` 为15/16：目标菜单原先只设最小44dp，字体行盒将实际高度撑到114px，超过POCO的44dp约108.9px。产品改为仅三态短菜单固定44dp，其他菜单仍用可增高的48dp最小行；`final3` 同组16/16通过。
- `main3` 在目标保存之后、YAML编辑之前失败。Room已写入false，但测试的一次“已开启”缺席立即结束等待，紧接重读又见退场菜单。原录像40.592秒、测试38.926秒，菜单约38秒才展开，没有等满15秒；已确认点击前默认、点击后进度和false勾选。不能据此称产品菜单卡住15秒。
- 原测试将空活动窗口与文字缺席视为同一结果，未要求稳定父页；窗口切换期间的具体缺席类型没有单独记录。修正仅在测试：等待真实ViewModel新revision、非saving、无error、Room false，并要求有效目标包根窗口内协议父页与“已关闭”持续可见、“已开启”持续消失600ms。超时截图和安全回执计数保留，不修改产品目标保存链。

## 验收边界

本组制品、源码、批次及对照记录见[验收JSON](acceptance/protocol-yaml-parity-poco-20261120.json)，本机附件为 `../acceptance-artifacts/protocol-yaml-parity-poco-20261120/`。

最终`build3`实际执行618项App motion JVM，零失败/错误/跳过；motion/Test与未签名Release-R8通过，motion Lint为0错误/73警告/14提示，Release为0错误/69警告/13提示，ML Kit DEX三注册器通过。`final3`组件及相邻回归16/16（121.312秒）；仅修自然测试等待后`testbuild4`通过，`main4`真实MainActivity流程1/1（54.169秒）。产品APK始终同一份，测试包只变更自然流程等待，均安装回拉核对；不把两套测试APK称为同套，也不把618当全模块新执行；Gradle共227任务，23执行/204 up-to-date。

真实流程验证目标revision 2→3、父页稳定600ms、YAML返回保存、重开/Activity重建、Room的端口/false目标/false状态与源正文保护；正式行/文件/偏好/脚本/选择保持，结束STOPPED。68张Kotlin截图、6组深浅Flutter限定对照经目视；55.834秒自然录像保留1579原PTS帧并抽12点，不作为全逐帧或性能证明。`more_yaml_error_ime_hidden`虽通过同窗口Insets隐藏断言，图上仍见键盘，不能用作视觉稳定收键盘基线；`ports_ime`在注入正文显示前采到，后续草稿/重开及Room已断言两端口。

自有motion/test/独立Flutter宿主三包与USB输入副本已精确回收；原1.yaml的SHA/大小/mtime、原版Flutter身份/安装时间与设备设置保持。当前公开版0.1.10，本轮未发版或安装正式Release。

Kotlin系统monospace与Flutter JetBrainsMono、代码折叠/缩进引导与语法配色、部分按钮材质和逐帧菜单几何仍有差异；参考YAML与Kotlin用例正文不相同，仅比较编辑器结构和行为。默认/非默认端口摘要状态在对照中分别注明。

NTP标量输入仍为Kotlin整页表单，Flutter为输入弹框；该项留下一组。清除本分区是已有Kotlin额外入口，固定Flutter分区没有相同入口，不当作上游缺功能。完整Flutter连续录屏同态、菜单首开/关闭无损Perfetto和RenderThread归因仍待。
