# KUI-014：Provider 选项重复校验失败不回到提示，保存后键盘遮挡

## 来源与影响

这是 Kotlin 选项页的交互缺陷。无效数字首次提交会显示错误，用户滚到下方后再次提交同一个值，错误提示仍在屏外；编辑时从顶栏保存也没有结束输入焦点或收起键盘。看起来像保存按钮没有反应，但输入没有丢失，也没有产生持久化写入。

原始基线为 `50ef131f`。Flutter 只作行为参考：`lib/views/profiles/custom/providers.dart` 使用独立选项表单，`lib/views/profiles/custom/members.dart` 的选择立即更新父表单，`lib/features/form/nested_form_sheet.dart` 负责父级保存/放弃/继续编辑。本修复不改变这些边界，不合入 Flutter 提交；Flutter 该页没有相同的 Kotlin 数字内联校验协议，不能把缺陷归为上游内核问题。

## 根因与修复

`ProviderOptionsPage.save` 的数字/URL 同步验证失败直接写 `localError` 并返回，不进入 `validating`。原来的回顶效果只以错误和忙状态为键；重复相同错误没有状态变化，滚动不会重新执行。

每次受理保存递增尝试次数，失败效果增加尝试次数键。尝试计数参考同目录 `ProviderQuickYaml` 已有模式，但不能照搬其原来的焦点控制器作用域。

首轮集中验证发现，`ProviderQuickYaml` 原有的 `LocalFocusManager`/`LocalSoftwareKeyboardController` 从 `ProfileEditorPage` 外读取；首版选项修复沿用这个位置也没有收起键盘。`ProfileEditorPage` 实际创建独立 Dialog，父组合的焦点管理器不持有当前输入框。两页改为在 Dialog 内执行的 `confirmButton` 内容中读取控制器，再结束输入、执行校验。忙状态仍拒绝提交，原始输入和过滤条件保持，只有验证成功才调用保存回调。

## 复现证据

本机附件：`../acceptance-artifacts/provider-options-interactions-20261107/`。

- `red-build/`：未修复产品源码加新测试的 AndroidTest 构建通过。
- `red1/`：Redmi 两条精确方法均按预期失败。`repeatedIdenticalOptionErrorReturnsToTopWithoutEditing` 第一次提示通过、滚离后第二次提示不可见；`optionValidationHidesRealImeAndKeepsInputForCorrection` 在保存后等待 IME 隐藏 5 秒超时。
- 两条失败前后正式配置行、正文 SHA/大小/mtime、偏好、选择及原 `1.yaml`、Flutter APK、设备设置相同，收尾无应用服务。该组件批次不操作脚本表，不宣称脚本表摘要已检查。
- `build1`/`build2` 均统一构建通过；`final1` 为 6/9。重复相同错误回顶已通过，但选项和快捷 YAML 保存后 IME 等待超时；原 IP 子页返回用例在新 Window 完成入场前注入 Back，后续先确认子页可见并等待系统入场。该首次失败保留，最终结果不与它累计。最终 IME 断言只接受曾显示键盘的同一窗口明确回读隐藏，缺失 Window/Insets 不能判通过。

## 同轮既有单测等待修正

`build3` 全量构建在 `ProfilesViewModelTest.persistedFileAndRemotePickerTokensAcceptOnlyMatchingResultAfterViewModelRecovery` 的关闭后 `assertNull(saved)` 失败，549 项 App 中 1 项失败；原 XML 保存于 `build3/jvm/`。文件预检使用 `Dispatchers.IO`，`advanceUntilIdle()` 不保证真实 IO 触发的写锁清理完成。测试改为等待产品在成功清密文后发布的 `profile == null` 终态，再保留原密文为空断言。未修改 ViewModel 或草稿协议，`unit-fix/` 精确方法通过；全量复验结果另列。

## 复查边界

后续修改校验、焦点、滚动或子页提交时，保留相同错误重复提交、真实 IME、校验/保存等待防重复、过滤子页返回保留父草稿、父页放弃重开不写入的断言。组件宿主和注入回调不能代替正式 Room/核心全链路、冷进程恢复、全部输入法或 Flutter 全局视觉/连续动效验收。

最终build4共830项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；Redmi final2 9/9、21图/7拼图通过。两份正式配置/正文/偏好/选择与设备基线保持，双包回拉及源码匹配。明细见 `acceptance/provider-options-interactions-redmi-20261107.json`。
