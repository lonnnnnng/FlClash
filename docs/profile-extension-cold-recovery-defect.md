# KUI-012：扩展页冷恢复、提交回执与规则身份

状态：本组已完成构建、Redmi冷恢复/回归及Release静态隔离验收；完整视觉和后续运行态边界保持独立待办。

## 原始缺口

基线 `f9888f1b` 的扩展会话与保存结果计数仅保存在 `ProfileExtensionController` 内存中，界面使用Activity保存树。进程死亡后没有独立密文、提交意图或提交结果恢复。`ProfileExtensionEditor` 用最新规则列表重新查找表单原对象；原ID被外部删除后查找为空，确认可能按新增规则提交。本地 `saveExtension` 更新配置而不插入配置，但缺少旧扩展值比较，整列表可能覆盖外部规则修改。全局规则已有列表CAS，应保留并补齐会话边界。

## Flutter来源与差异

只读参考 `lib/features/form/rule.dart:266` 与 `:317`：表单初始化持有 `widget.rule`，旧规则提交沿用原ID，新规则才生成ID。`lib/views/profiles/extend/standard.dart:23` 先等待表单返回，再通过notifier写入；脚本与模式即时保存，退出扩展可自动应用。上游这些代码没有本次独立密文或跨进程提交协议；`lib/database/rules.dart:106` 为upsert，不把本次Android冷恢复问题归因于上游，不合并Flutter提交。

## 实现映射

- `ProfileExtensionDraft/Commit/Values` 仅持有扩展字段、原/新规则列表、操作UUID和保存树，不保存整份带订阅密钥的Profile。`profile-extension.v1` 使用既有Keystore AES-GCM、AtomicFile和noBackup目录。
- `ProfileExtensionController` 提交前落盘意图，冷启动仅核对结果。保存失败保留原意图并要求明确重试/放弃；同一操作同步认领，其他设置仍按原顺序串行保存。
- `ProfileRepository` 的本地扩展CAS、全局CAS与宿主存在检查处于Room事务。每次写入在同一事务记录无正文的 `extension_commit/<UUID>` 标记，避免数据库已提交但密文回执未落盘，且值又被其他入口改回旧值时误重放。先持久化已完成/已放弃屏障，再删除提交标记，最后清空意图。
- 表单保留原ID/原值并显示外部冲突，内容与选区使用同一TextFieldValue保存。独立保存树在读库结束后挂载；删除确认与表单弹窗使用独立窗口注册表。冷启动自动导航回扩展；宿主删除仍保留草稿和退出入口。

## 验证记录与限制

首批Redmi final1前三场景及local-after再次恢复通过；local-applied在恢复回执未核对完成时提前读取committed，断言失败。源码中restore发布中间状态默认ready=true，load提前结束loading，使页面和调用方可能读取尚未核对的会话。新增receipt读取挂起单测原代码1/1失败；修正为回执核对与密文落盘完成后才ready，期间页面、保存/重试/放弃/退出均不开放。原现场独立诊断恢复1/1通过，未见重复写入；recovery1精确清理1/1及正式数据保护通过。原失败不累计为通过，修复后整组重新验收。

独立复核进一步发现恢复完成后的后台回执复核仍持有旧UI快照，查询挂起期间的新搜索/子页树可能被覆盖。`receipt-ui-red`确定性单测原代码1/1失败；回执返回后改为合并最新状态及最新保存树，提交结果仍基于正式数据与事务回执核对。首次恢复测试另挂起密文写入，确认落盘完成前ready仍为false。

定向首轮 `unit1` 未执行测试：测试Store漏实现 `updateProfileSelectedMap`，编译失败。补齐后继续验证。独立静态复核指出的“失败后下一设置覆盖pending”“已提交后值回退误重放”“宿主删除使批删等待无法结束”已纳入实现修正与回归范围。

2026-11-07构建回读：`../acceptance-artifacts/extension-cold-20261107/build1.log`记录BUILD SUCCESSFUL（3m47s，389任务49执行/340复用）。App545/Service212/Core38/Common31共826项JVM零失败/错误/跳过；App本批实际执行，其他281项复用。新增冷恢复控制器12项通过。Debug/Test、未签名Release-R8及双Lint成功，Debug 0错误/67警告/13提示、Release 0错误/66警告/12提示；`build1/source-hashes.json`的578份文件与当前源码全部匹配。当前尚无本批安装回拉、冷阶段结果、相邻回归或截图证据，真机结论保持待验。

正式验收只使用外层原始 `1.yaml` 的自有FILE副本；局部规则只写该副本，全局规则仅增删本例UUID并精确恢复原偏好行。正式FILE/URL配置、正文SHA/size/mtime、脚本、偏好、选择、Flutter包与设备设置必须回验。本组停止态受控force-stop不代表运行态死亡或OEM自然回收；完整Flutter视觉、Provider运行态/MRS冷恢复、第6项异常与网络稳定性继续独立列入todo。

## 最终验收

扩展页局部/全局规则独立加密会话、提交前后冷恢复、事务回执防重放、外部规则修改/删除及宿主删除保护已验，KUI-012本组闭环。Redmi final2首次冷恢复18/18、提交后与回执缺口再次恢复4/4，相邻回归11/11，33张PNG完整校验并经11张拼图目视。828项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；执行/复用明细以本组JSON为准。Debug Lint0错误/67警告/13提示，Release0错误/66警告/12提示。安装回拉和578份源码匹配；两份正式配置/正文SHA-size-mtime/脚本/偏好/选择、原1.yaml、Flutter包及设备设置保持，临时对象、密文和提交标记清除，收尾无服务。首轮local-applied恢复过早就绪及后台回执覆盖新UI树已分别红绿复现并修复，原失败保留。本组仅STOPPED/受控force-stop；不推广运行态死亡、OEM自然回收、独立全局规则页冷恢复或完整Flutter视觉。下一组Provider运行态/MRS只读冷恢复及失效引用实核回滚，再统一Flutter视觉；第6项原保护异常、未知偏好变化和网络稳定性仍待查。蜂窝延期，玻璃默认关闭。证据`docs/acceptance/profile-extension-cold-redmi-20261107.json`；溯源`docs/profile-extension-cold-recovery-defect.md`；附件`../acceptance-artifacts/extension-cold-20261107/`。
