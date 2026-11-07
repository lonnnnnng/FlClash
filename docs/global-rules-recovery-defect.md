# KUI-016：独立全局规则页的会话恢复与提交边界

2026-11-07，作者 long。范围为工具 → 高级配置 → 附加规则；不将 Profile 扩展页已有验收计入此入口。

## 来源与修复前证据

基线 `b5269393`。Flutter 仅作实现参考：固定 `upstream/main` 的 `be847366`，`lib/views/config/rules.dart` 的 `_handleAddOrUpdate`、选择/可见集删除及排序，`lib/features/form/rule.dart` 的保留原 ID 和取消不写；搜索参考 `lib/common/search.dart`。没有合并或移植 Flutter 提交，也没有修改原版安装包。

原 Kotlin `GlobalRulesRoute` 每次组合都 `open()`；Controller 重置内存状态，页面批选/删除确认仅 `remember`，未接独立密文保存树。表单原行从最新目录查找，缺失时可能转新增；删除确认在收到保存结果前关闭。`retryApply()` 重新捕获当前配置而非保存受理时目标。

定向红测 `red1` 为 5 项中 2 项失败：`reenterDuringSaveKeepsAcceptedOperationVisible`、`applyRetryAfterSelectionChangesDoesNotApplyNewProfile`。日志及原始 XML 位于工作区外层 `acceptance-artifacts/global-rules-20261107/red1/`。这些是确定性 JVM 复现，不声称原缺陷均已在真机红测。

## 当前实现

- `GlobalRulesDraft` / `GlobalRulesController`：独立 `global-rules.v1` Keystore 密文会话，保存 UI 树、事务 ID、提交前后目录及冻结应用目标。重复进入同一会话不重置；冷恢复只核对，不自动保存或应用。
- `ProfileRepository.commitGlobalExtensionRules`：目录 CAS 与回执同 Room 事务；全局目录不要求存在宿主配置。即使外部将值改回提交前值，回执仍表明已提交。
- 清理顺序：先将 committed/abandoned 写入密文，再删除回执，最后移除 pending；明确退出成功才清整个会话。迟到 UI 回调不复活关闭会话。
- `GlobalRulesPage`：独立加密保存树、原行快照、搜索/批选/删除确认、排序完整快照；失败保留确认与选择，显式重试或取消。原行改变/删除拒绝覆盖或重新新增。
- `KClashApp`：冷恢复自动进入独立规则入口；关闭完成由 VM 版本信号同步到重建后的导航。
- 共享 `ExtensionRuleForm`：MATCH 等无匹配内容规则的类型或目标变更仍要求确认放弃。

第一轮新增单测 `unit1` 为 12 项中 1 项失败：损坏草稿后 open 仍覆盖文件。已补 recoveryFailed 入口守卫。独立只读审查另发现并修正排序等待结束未重新触发、待应用入口依附错误消息、核对失败 pending 不可处理、关闭跨 Activity 回调失效；上述审查推断与运行红测分别记录，不混同。

## 后续复核发现

- `retarget-red` 单条 JVM 红测复现：A 已保存但应用失败，切到 B 的新保存失败并放弃后，原待应用目标被改为 B。修复仅在新事务确认完成时替换应用目标；未提交操作保持原待应用身份。
- `activity1` 2/4：两个长字段断言错误地把标签也参与全文比较；原语义树已显示正文/选区正确。改为精确 EditableText 比较。
- `activity2` 3/4：测试嵌套 runOnIdle；修正测试线程调用，正式配置保护通过。
- `activity3`、`activity4` 各4/5：新增 MATCH 场景涉及类型菜单与目标子窗退场，分别记录返回确认缺失和窗口令牌等待超时，未将重跑预先认定通过。
- `cold1` 首9场及 applied 再次恢复通过，after 选区预设3..11与恢复286..286不符。原失败保留并经显式门禁精确收尾；后续在外部死亡前记录实际选区，逐值核对恢复，未提交草稿仍要求3..11。
- Release 映射静态发现 GlobalRulesDraft/Commit 被重命名，其字段session/operationId等变为a/b。新增与既有草稿相同的R8保留规则；最终产物须检查字段身份，不以Debug冷恢复替代发布检查。

## 验收状态

本组正式交互5/5、cold2首次14/14及再次2/2、相邻回归4/4通过，16图经6拼图目视；843 JVM零失败，最终build8构建/双Lint通过。证据见`acceptance/global-rules-recovery-redmi-20261107.json`。build8只补旧GlobalSettingsHost测试清理，未单独执行；产品及本组用例源码、Debug/Release APK与真机build7一致。旧cold1 after选区失败没有死亡前实际值，不能追认根因；cold2的after/applied记录死亡前3..11且恢复一致，其他场景同样逐值核对。真实冷进程为 STOPPED 下受控 force-stop，不能推广 VPN/OEM 自然回收、连续动效或完整 Flutter 视觉。

## 后续同步复查

仅当 Flutter 参考修改全局目录编辑/排序/搜索语义时重新对照；Kotlin 的独立密文与事务回执属于平台实现。上游参考更新不能绕过原 ID 冲突保护、CAS、防重放和冻结应用目标。保留原失败证据；若将来更换恢复协议，先证明上述死亡窗口仍成立，再撤销当前机制。
