# 本地代理旧窗口失焦覆盖输入

- 编号：KUI-008。
- 基线：`89a8f0fa261ebfd933264e1dffbce8a543467cb7`。
- 归属：Kotlin/Compose表单生命周期集成，不是Mihomo缺陷，不改Flutter基线。
- 当前状态：Redmi两次诊断已复现并定位，源码修复及本组范围集中验收完成。

## 现象与定位

上一批`local-proxy-interactions-redmi-20261107/final1`里，ALPN输入`http/1.1`后立即重建，第二项显示为空；该批没有VM轨迹，不能仅凭相似症状确认历史失败的完整因果。

本批在隔离`LocalProxyDraftTestActivity`记录有界输入回调、文本长度和生命周期；不记录节点正文或凭据。通过正式Compose页面的平台`InputConnection`执行组合输入/提交，立即重建Activity：VM先接收`h3`，`onStop`之后旧窗口又回调`http/1.1`，新窗口读取旧值。diagnostic1为1/2，diagnostic2为0/1；正式两配置及设备保护均保持。调用栈明确为`TextFieldDelegate.onBlur → endInputSession → FocusTargetNode.onDetach → ProxyCollectionEditor → updateEditor`。

`LocalProxiesViewModel.updateEditor`的session检查只能拒绝已关闭编辑会话；Activity重建沿用同一VM/session，因此旧窗口回调仍能通过。保存树`updateEditorUiDraft`只合并uiDraft，本次轨迹不经过它覆盖业务正文。修复在正式`LocalProxiesPage`的业务表单更新入口检查宿主仍至少为STARTED，拒绝已停止宿主的输入拆除回调；ON_STOP的独立保存树捕获继续执行。

## Flutter参考与同步边界

先参考`lib/views/profiles/custom/custom_proxies.dart:1586`的`_ProxyValuesView`及`lib/widgets/input.dart`：Flutter的单项输入由TextEditingController保管，确认返回后重新读取当前列表再合并。没有可直接移植的Android Activity/Compose拆除回调处理。Kotlin保留现有内联编辑和加密草稿契约，补足宿主生命周期边界；不得通过等待一帧、忽略所有空值或重建时清空草稿规避。

## 验证边界

新增语义输入/同主线程失焦与平台InputConnection提交两条定向用例，各交替输入8轮，并包含合法空文本。父保存前隔离目录不得变化，随后相邻集合、分享链接和删除/添加交互集中回归。真实InputConnection由测试驱动，不能称人工键盘或所有输入法覆盖；也不替代冷进程、正式MainActivity全链路及完整视觉验收。

原始失败和最终验收附件保留于`../acceptance-artifacts/alpn-input-boundary-redmi-20261107/`，最终build4/final1为14/14、43图及13拼图目视，788项JVM零失败/错误/跳过；App512本组build3实际执行，其余276复用；最终build4四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。Debug/Test/未签名Release-R8及双Lint通过，安装/源码/正式库/设备保护保持。证据`docs/acceptance/alpn-input-boundary-redmi-20261107.json`。上游同步时复查Compose输入会话/拆除行为及Flutter单项确认规则；仅在同样的立即重建红绿用例证明不再需要时移除保护。
