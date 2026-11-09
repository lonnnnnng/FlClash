# 历史稳定性异常：证据边界与后续定位

2026-11-09，按当前 `2f2479b7` 主线只读复核。本文补充可追溯的调查路径，不把当前源码推定为历史失败包的实际行为，也不以短窗口通过撤销旧失败。

## 配置字段和文件时间变化

`docs/acceptance/ip-quality-cache-redmi-20261105.json` 的长窗口 after 仍为失败：同一正式配置的 `currentGroupName`、`lastUpdateAt`、文件 mtime 变化，正文 SHA/字节数与当前 ID 保持。变化位于 `cold1` 完成后至 `testbuild4` 安装前；原始审计只有最近 16 次 Worker 完成记录，没有发现下载成功，确切原因未定。

当前 `IpQualityNetworkInstrumentedTest.fixture` 和冷准备确实在 `withAutomaticUpdateBarrier` 内关闭既有源自动更新，并在收尾恢复；不能笼统说测试未冻结。`MenuVisualHostInstrumentedTest.formalProfilesAndFilesArePreserved` 只生成/比较保护摘要，不承担整个长窗口冻结。fixture 间隙不能沿用其内部保护结论。

`ProfileRepository.updateRemoteProfileInternal` 先原子替换文件和提交 Room，再处理运行态应用；`SubscriptionAutoUpdateWorker` 等整个调用返回才写审计。提交后至审计前进程退出、手动同步，以及超出最近 16 条的历史都不能由“没有完成审计”排除。`LocalProfileFileWrite.write` 即使正文相同也会原子替换，所以 SHA 不变但 mtime/更新时间变化与同内容同步相容，尚不是因果证据。

组名由 `updateProfileCurrentGroupName` 单独写入，不写正文或更新时间；代理页选组及切换 Global 可到达此入口。两个变化不必来自同一个动作，禁止合并为单一已确认原因。

下一轮应在每次 fixture、安装以及间隙前后保存逐字段/偏好键摘要、文件 SHA/size/mtime、当前选择和自动更新开关，并为所有配置写入入口保留受理/提交/完成、来源、单调时钟和进程身份。只保存摘要和安全事件，不记录真实正文、地址、凭据或回写旧快照。

## 未知偏好变化

`docs/acceptance/main-extension-state-redmi-20261107.json` 的 final1 同时包含焦点失败、Provider finally 总偏好摘要变化及 Binder 回传过大。该轮没有逐键 before，旧变化不能倒推到某个键。

另一轮已确认 `ip_quality_cache_v1` 首次写入，但 `provider-document-cold-redmi-20261107.json` 明确保留“不追认旧异常”。当前 `ProfilePreparationInstrumentedTest` 已能输出键摘要→值摘要，`MainProviderDocumentInstrumentedTest` 失败时也保留受控键摘要。后续相邻用例必须先保存这些字段，不能再只存总 SHA。

## Provider 运行态超时

`docs/acceptance/provider-runtime-redmi-20261107.json` 的 final6-before 在系统新 PID 恢复后 verify 超时；缺业务栈，`productDefect=unconfirmed`。final7 通过与 cleanup 恢复均不能消除它。

当前 `ProviderRuntimeAcceptance` 已输出 step、运行态、正文绑定/加载/保存、恢复阻断和提交意图等安全状态；但组读取、TLS/HTTP、连接归属等仍共享粗阶段。下一轮要逐个记录等待开始/结束、预算和耗时，区分运行态等待、组读取、加密路由恢复、正文绑定、显式保存关闭、socket/TLS/HTTP 与核心连接归属。超时日志必须先保留，再恢复现场；不能自动重试后只留下通过结果。

## 网络恢复

控制器重建竞态和坏 TLS 配置端口泄漏已有独立修复与回归；旧 Always-on 首请求 TLS EOF 的完整因果及长期恢复仍未闭环。后续长窗口需同步采集上述写入诊断与网络阶段，保留第一次请求结果。无蜂窝条件下继续 Wi-Fi 可执行范围，Wi-Fi↔蜂窝单列延期；不启动模拟器、不改变 ROM 安全配置。

## 2026-11-09：独立包45分钟观察

同一 Redmi、原1.yaml副本、独立非debuggable motion包，实际窗口2703.218秒。只启纯代理，原配置路由/节点保持；5分钟检查因前序驱动修正实际在400.196秒执行，其余按原始起点调度，不能写严格等间隔全程采样。首个TLS失败后继续观察原会话，没有重启或重试覆盖失败；15分钟token+PID受控SIGKILL，verify前独立观察旧4304→新4941及系统恢复的ProxyService。

10次运行态读取均有90个Provider，读取耗时61–112ms；HTTPS最初两次TLS握手失败，之后8次HTTP200、同socket核心归属、正双向流量及非DIRECT链。长窗实网总结果为8/10，不能标全通过。首次失败只有阶段/异常类，尚缺DNS/节点拨号/服务端关闭的完整关联链；旧Provider verify超时因果仍不能由本次快速读取关闭。

11份快照（含停止）逐字段Profile摘要、当前ID、配置正文SHA/35165字节/mtime一致。运行态偏好变化只观察到：自动更新审计runs由2条增至4条（新增两轮attempted=0、无checks/下载/应用）；30分钟起新增Room质量缓存ip_quality_cache_v1。Worker即使没有到期项也记录结果，见SubscriptionAutoUpdateWorker、SubscriptionAutoUpdateAudit；质量缓存仅成功查询后写入，见IpInformationRepository/RoomIpQualityCache。25分钟打开首页与30分钟新增缓存符合这条路径，但未记录写入时调用栈，具体入口只能推断。显式stop后runtime_should_resume由true变false符合停止语义。

本轮没有不明Profile/mtime变动，不能反向解释历史异常。新快照不等于逐次写入来源审计；仍缺全生产写入的来源/时刻关联。此窗口是独立包、单FILE、纯代理、无自动下载，不推广正式包完整库、URL同步、VPN/OEM、蜂窝或更长运行。

准备/驱动初次失败及修正脚本均保留于`../acceptance-artifacts/stability-window-20261109/`；明文临时输入已精确删除。最终摘要和停止快照见对应验收JSON，禁止后续绿测覆盖首失败。
