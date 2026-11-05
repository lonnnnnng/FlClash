# 上游源码缺陷与本地修复台账

本台账记录已确认的源码缺陷、固定来源、修复提交和验证证据。路线图记录进度，本台账用于以后同步 Flutter/Mihomo 时逐项判断补丁是否仍需保留；历史失败不随修复删除。尚未确定因果的现场单列调查，不标成已修源码缺陷。

当前来源：Flutter `chen08209/FlClash` 的 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`；Mihomo `chen08209/Clash.Meta` 的 `c5062921054902b25215a6a933e72ccd714c9bfa`（`FlClash` 分支固定基线）。修复版核心由 `lonnnnnng/mihomo` 的 `kclash-fixes` 分支保存，主仓库以 gitlink 固定实际修复提交，不随远端分支头自动漂移。

| 编号 | 缺陷 | 层级 | 状态 |
| --- | --- | --- | --- |
| KCORE-001 | TUN listener 返回前跳过保护，平台拒绝保护被忽略 | FlClash Go 适配层 | 已修，修复提交 `6ca697d118e124c133f6d7658676cb217c7d9a26` |
| KCORE-002 | 控制器异步重建的全局指针竞态和旧任务覆盖新请求 | Mihomo `hub/route` | 已修，核心提交 `f83e9b1032ed02831457d4da5a414e063e932694`；验证见下文 |
| KCORE-003 | TLS 控制器绑定后校验失败，监听器失去回收路径 | Mihomo `hub/route` | 已修，核心提交 `f83e9b1032ed02831457d4da5a414e063e932694`；验证见下文 |

## KCORE-001：TUN 启动保护缺口

- 原始位置：Flutter 固定基线的 `core/lib.go`，`TunHandler.start`、`handleProtect`、`installHooks`。TUN 栈先启动再返回 listener；原保护逻辑因 listener 为空而直接返回，且不传递 `Protect(fd)==false`。与 Flutter 基线共有，不能归为 Kotlin 独有差异。
- 影响：已进入活动 TUN hook 的出站 socket 可能未调用平台保护，或被拒绝后仍按成功继续拨号。此缺陷不等于已证明导致历史 TLS EOF。
- 修复：提交 `6ca697d118e124c133f6d7658676cb217c7d9a26`；`core/socket_protection.go` 在 hook 发布前持有回调，透传拒绝和 Control 错误，关闭等待已进入回调并拒绝迟到旧 gate。调用仍在 handler 锁外，避免初始化拨号回入死锁。
- 验证：[保护验收 JSON](acceptance/system-vpn-protection-redmi-20261105.json)、[ADR0020](adr/0020-tun-socket-protection-gate.md)。定向竞态验证包含保护相关4项；另3项为诊断边界。Redmi 两轮四次首请求 HTTP200，但 `callsDuringStart=0`，不能称真机观测到原窗口造成失败。
- 同步复查：比较上游是否在 listener 返回前已真实保护、是否透传 false/Control 错误、关闭是否释放回调且不无保护放行迟到 socket。只有语义等价且相关测试通过才撤掉本地 gate，不能只按同名函数或提交标题判断。

## KCORE-002：控制器异步重建竞态

- 原始位置：[固定基线 server.go](https://github.com/chen08209/Clash.Meta/blob/c5062921054902b25215a6a933e72ccd714c9bfa/hub/route/server.go#L92)，`ReCreateServer` 为 HTTP/TLS/Unix/Pipe 各开 goroutine；`start*` 无锁关闭、清空和写入全局服务指针。HTTP race 两端为原文件164/183行，其他 transport 使用相同结构。
- 触发：相邻 `core/config_test.go` 中 secret 更新和开启控制器连续调用 `patchController`。外层 `configMu` 只保护派发过程，不等待 route 后台任务。生产中快速切换控制器设置或重新应用配置也可触发同一交叠。
- 影响：数据竞态；旧请求可能在新请求之后执行，关闭新服务或恢复旧地址/secret。最后一次空配置关闭也可能被迟到启动覆盖。不能通过给测试加 Sleep 或只给异步函数加锁来保证最新请求生效。
- 原始证据：[上一组失败 JSON](acceptance/system-vpn-protection-redmi-20261105.json) 的 `go.fullRace` 和 `go.baselineOverlayRace`；当前与旧源码 overlay 均为同一 `httpServer` race。overlay 保留了新纯 Go helper/tests，不是干净旧基线全量。
- 修复不变量：在公开重建入口串行交接，返回前完成旧监听关闭和新监听准备；Serve 循环在锁外异步运行。显式保存 listener，关闭不能依赖 Serve 已登记它；空配置返回后不得由旧任务重开。保留 TCP/TLS secret 和 Unix/Pipe 空 secret 的既有区别。
- 修复提交：[核心 f83e9b10](https://github.com/lonnnnnng/mihomo/commit/f83e9b1032ed02831457d4da5a414e063e932694)，仅修改 `hub/route/server.go`、新增 `server_test.go`；父仓库 gitlink 固定此完整 SHA。实现决策见 [ADR0021](adr/0021-controller-lifecycle-and-source-defect-tracking.md)。
- Go 验证：本轮修改前一次父模块全量 race 为265/265通过，但相同两项控制器用例各重复20次共38通过/2失败，证明单次绿测会漏掉竞态。修复后父模块全量 race 265/265、相同压力40/40及嵌套 route 10/10通过；route 含同址HTTP40轮/TLS12轮/Unix12轮、64并发启闭、最终关闭、Serve未登记窗口、输入快照和嵌入/非嵌入路由。Windows Pipe 仅源码核验，未运行。
- Android 验证：[本轮验收 JSON](acceptance/controller-lifecycle-redmi-20261105.json)。build2/767项JVM、Debug/Test、未签名Release/R8及双Lint通过；Redmi final2精确4/4，其中控制器在同一运行会话完成6轮认证/密钥轮换、关闭后立即释放和嵌入配置写入拒绝，正式配置保持；另3项为模式/组件/旧菜单回归。独立Always-on默认冷启动/SIGKILL首请求两次HTTP200、纯代理恢复和前后保护通过，不推广历史TLS EOF因果、长期稳定性或其他系统。原始附件仅存本机 `../acceptance-artifacts/controller-race-20261105/`（工作区外层）；提交JSON含哈希索引。
- 同步复查：对比公开入口的完成边界、同址快速重绑、最终关闭、配置输入快照、四种 transport 和 listener 所有权；上游若只改指针锁，仍需验证旧请求覆盖与 Serve 未登记窗口。

## KCORE-003：TLS 校验失败泄漏监听器

- 原始位置：同一 Mihomo 固定基线 `hub/route/server.go` 的 `startTLS`。原205行 Listen 成功后，224–227行加载客户端 CA 或233–236行加载 ECH 失败直接返回，尚未发布 `tlsServer`，以后重建无法回收该 listener。
- 影响：无有效服务却持续占用端口；用户纠正 TLS 配置后仍可能绑定失败。与 KCORE-002 共处控制器生命周期，但证据和失败分支单独记录。
- 修复不变量：证书、客户端 CA 和 ECH 等可能失败的配置准备先完成，再申请监听端口；取得 listener 后由明确的服务实例负责关闭。
- 修复提交：与 KCORE-002 同为核心 `f83e9b1032ed02831457d4da5a414e063e932694`。最小复现对固定原始 `server.go` 使用测试 overlay，坏客户端 CA 和坏 ECH 两分支均出现 `TLS_INVALID_CONFIG_LEFT_PORT_BOUND`（含父项共3项失败）；完全相同的复现应用到修复源码3/3通过。overlay 仅作红绿证明，产品直接构建修复源码；正式 route 测试还验证先关闭旧服务、拒绝坏配置及纠正后立即复用端口。
- 验证索引：同一[本轮验收 JSON](acceptance/controller-lifecycle-redmi-20261105.json) 的 `go.tlsBaseline`、`go.tlsFixed` 与 `go.routeFinal`；Redmi只验嵌入HTTP控制器，TLS两失败分支的运行验证环境为macOS Go，不宣称Android TLS或Windows Pipe已经验过。
- 同步复查：检查每个成功 Listen 后的失败出口，必须有释放或明确的所有权移交。保留坏配置拒绝行为，不通过忽略校验或回退旧配置掩盖失败。

## 未定因果调查：Always-on 首请求 TLS EOF

已保留旧包冷启动失败、随后同会话物理/TUN HTTPS 成功及修改版有限轮次通过证据。KCORE-001 修复静态可证明的保护缺口，KCORE-002/003 修复控制器生命周期；目前不能把这些修复直接认定为此前 TLS EOF 的确定根因。长期稳定性、蜂窝和其他 OEM 场景仍按 roadmap 单独验收。

## 后续同步操作

1. 记录新 Flutter/Mihomo 固定 SHA，先逐项阅读上游对应实现与本台账；核心 fork 保留上游原始提交，不直接改写来源历史。
2. 用 gitlink 基线与核心修复提交比较实际补丁，逐条标记保留、等价已上游化、冲突需重做或不再适用，并记录判断证据。
3. 运行缺陷回归、Go 全量竞态和 Android 构建/真机回归；通过后更新本台账的提交、测试和撤销条件，再更新 gitlink。缺条件的测试明确保留未验，不自动移除补丁。
4. 新发现源码缺陷先分配编号，记录来源/复现/影响/修复提交/验证/同步复查条件；未定因果保持调查状态。未经用户指示不向上游发 Issue、评论或 PR。
