# 失效引用配置无法切换离开

- 编号：KUI-010。
- 基线：`4ede6b850b9d019b8f98ccc8ae2884ccbb2f7c29`；本轮 Provider 冷恢复验收中发现。
- 归属：Kotlin `CoreRuntimeManager` 配置切换前置条件，不是 Mihomo 缺陷。
- 状态：已修；五项新增JVM回归及整组802项通过，Redmi源Profile/源文件删除两类均两次恢复后成功切回正式配置并保持保护快照。

## 真实触发与原因

临时 CUSTOM 配置引用的源 Profile 被外部删除后，Provider 正文连续两次冷启动仍能恢复草稿并拒绝写入。清理阶段通过正式管理器切回有效配置，却在 `applyProfileLocked` 开始处调用旧 `activeProfile()`；旧引用投影解析抛出“代理资源缺失”，尚未选择新目标就退出。原 `final4/deleted-source-profile-again.log` 保留此异常，不能把整场记为通过。

同类原因包括当前 CUSTOM 正文文件或绑定脚本缺失。旧选择 ID 仍存在，不应要求旧正文可重新构建才能离开。直接把旧配置解析异常转换为 null 也不正确：新目标再失败时会丢失原 ID，而且无法拿到回滚用的节点选择。

## 上游参考与本地决策

Flutter `lib/views/profiles/profiles.dart` 的 `ProfileItem.onChanged` 先更新 `currentProfileIdProvider`；`lib/manager/core_manager.dart` 的选择监听只调度新配置 `fullSetup`，不解析旧配置。`lib/providers/actions/setup.dart` 构建新目标。只读参考证明旧输入不应成为切换前置条件。

Flutter 对拒绝的目标保持新选择；Kotlin 现有契约是新目标失败后恢复原选择和已应用核心输入。本次保留 Kotlin 已验证的事务保护，不合并 Flutter 提交、不改变 Mihomo。

## 修复与回滚保护

`ActiveProfileProvider.profileSelectedMap(id)` 增加独立元数据读取。正式 `ProfileRepository` 只查 Room 的节点选择，不解析正文、脚本或引用源。`applyProfileLocked` 先保存原 ID、节点选择、已应用字节、配置 key 与 VPN 参数；不同目标直接进入新选择及正式加载链路。同一 ID 的无变化预检在任何写入前执行；预检失败直接反馈，不触发补偿加载。

新目标失败时恢复原 ID，按已应用字节及原节点选择回滚核心。运行态缺少可靠回滚输入时，在任何选择或加载前拒绝操作，提示停止后切换；回滚再次失败仍停止服务/监听并清恢复标记和运行状态。取消在补偿后继续传播，不转换为普通成功或普通错误。

## 验证记录

新增四项 JVM 回归涵盖停止态/运行态从坏旧源切往有效目标、目标校验失败回滚原 ID 与已应用输入、目标和回滚 setup 双失败收尾，以及取消传播。旧实现四项全部失败，证据 `../acceptance-artifacts/provider-document-cold-20261107/switch-red/`。

修复后首次整类为 116/117；唯一失败是新增取消测试断言异常对象引用相同。协程可复制取消异常以恢复堆栈，测试改为验证 CancellationException 类型、原消息及实际回滚结果，不再要求引用同一对象；原结果保留 `switch-green/`。最终结果随后以本组验收 JSON 为准。

独立审查发现同 ID 预检若放在补偿事务内，读取坏源失败也会重载核心，补偿再次失败时反而停止原会话。新增第五项回归先得到 1/1 失败，证据 `same-id-red/`；随后前移同 ID 只读预检，要求选择次数、核心调用、服务事件、已应用字节和运行时长全部保持。

## 后续同步

上游参考更新时继续核对配置选择前置条件和拒绝目标的产品语义。只有替代方案同时证明坏旧源可离开、运行态失败可回滚、双失败可靠停止以及取消不丢失，才可撤销本地保护。真机 STOPPED 证据不推广为运行态实网验收。

最终证据：`docs/acceptance/provider-document-cold-redmi-20261107.json`。build11 App521/Service212重跑、Core38/Common31复用，802项零失败/错误/跳过；final5九场景首次恢复9/9、再次恢复3/3，regression1 10/10和22图目视。运行态实网、MRS只读冷恢复和完整视觉不由本组推导。

## 2026-11-07 实核补验

`provider-runtime-redmi-20261107.json`的final7停止态/纯代理运行态各源文件/源记录缺失共4场通过。目标RawConfig解码通过，真实setup拒绝后默认回退与旧输入补偿使epoch各增加2，旧ID/已应用字节/组成员/节点选择/运行会话保持；恢复目标后成功切换且旧源不重建。运行态每场前、补偿后、切换后各HTTPS200/同socket归因。此为Debug宿主真实管理器/Room/核心，未覆盖VPN、完整MainActivity点击导航或补偿再次失败实核分支。原JVM与停止态历史证据保留。
