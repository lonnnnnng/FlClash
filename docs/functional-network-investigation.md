# 功能性网络与恢复调查（进行中）

2026-11-11，初始产品基线`99da6121`。这是六项功能收尾中的首请求、Provider恢复与长期稳定性调查，不代表三项已闭环。原45分钟8/10、迁移包6/7以及Provider final6-before超时保留。

最新基线`9612ebcb`十分钟诊断再次抓到真实失败：8笔HTTPS为7成功/1失败，第三次启动在直接RUNNING后7ms开始，握手5003ms失败；指定源端口两笔核心拨号错误均node-dns，错误端点摘要匹配授权原配置第一个VLESS节点server。相同域名A约5024ms失败、AAAA约3711ms成功；DNS事件无socket ID，只能作为同域名/时窗旁证。DNS引导已应用、事件零丢弃/候选无溢出，后续同会话恢复；底层DNS原因仍待查。见[新证据](acceptance/state-key-network-redmi-20261111.json)与[逐键/诊断记录](state-audit-key-join.md)。

同组Provider source3在核心RUNNING后的服务/已应用VPN即时断言失败，尚未请求网络或恢复正文，数据恢复成功。源码存在分开发布的Flow；source4只将验收器改为完整服务状态的有界Flow等待，无固定延迟。最终保存前/后两死亡窗口及6笔HTTPS通过；独立首请求宿主不增加此等待，保留真实DNS失败。此断言问题单列KDIAG-002，不认定旧final6-before超时同因。

后续基线`ce1c423d`新增源端口context关联的结构化lookup，安全错误类别/地址族计数与生效DNS开关；发现并修正独立KCORE-009空地址nil错误，核心`42e93516`已推。907 JVM/Go竞态/构建通过、Redmi六启动+458秒恢复窗口共14笔HTTPS成功、最终语义12/12也通过；但Provider before在恢复后、after在准备阶段两笔TLS握手20秒超时，lookup均成功33 IPv4、核心拨号成功。此为与node-dns不同的失败阶段，原数据恢复/备份清空，整体网络继续未闭环。不可把初始14/14或KCORE-009修正推成全部问题解决。见[本组证据](acceptance/dns-lookup-redmi-20261111.json)与[源码缺陷和边界](dns-lookup-empty-success-defect.md)。

## 本轮诊断改动

- Debug共享`CoreRequestAcceptance`在connect前固定源端口，开启已有Go安全dial采集；采集阶段分别记录begin/end/fail、预算、Android单调时刻、异常类型。请求只执行一次，不改节点/URL，不重试。
- 真实核心就绪定义为`CoreRuntimeStatus.RUNNING`，不是前台服务已出现或旧`readyAt`字段。Stability宿主将首请求移到Provider读取之前，记录runningSince、直接观察与请求入口时间，诊断准备自身耗时单列。
- 当前固定Mihomo在SOCKS成功答复后才实际拨号；节点DNS/拨号失败仍可能表现为应用侧TLS EOF。尚未以本轮绿测证明旧失败属于此原因。
- DNS只有时间窗候选，原事件无socket关联ID；只保存域名/错误/上游哈希与类别，最大64条并明确overflow。端口级dial诊断由核心日志泵在Android队列之前记录；同时保存事件drop/truncate计数。
- Provider宿主新增runtime、列表、正文绑定、密文恢复、提交窗口、关闭和组读取等待阶段；较大日志分片并以SHA/字节数验证，外部driver同步解码。后续compile9已完成保存前/后死亡窗口及受控失败复验，见下节；历史final6-before的原因仍未确定。
- 本诊断初测的Stability快照新增偏好updatedAt和测速四列主键/值/时间摘要，当时尚未实现写入来源审计。后续已接入审计并完成新45分钟窗口，见[状态审计](state-write-audit-investigation.md)；仍不能据此解释旧元数据或偏好变化。

## 首轮真机观察

附件`acceptance-artifacts/functional-network-20261111/`。compile3构建motion/Test通过并回拉一致；产品APK SHA`61aa31e1…983c22b5`，测试包`975b3c09…a6e10f7`。仅Redmi Note 8 Pro `wsvwypiz7xwslvl7`，原1.yaml的独立FILE副本，原文件不改；唯一授权在线地址仅作HTTPS目标，准备明文输入双端核验后删除。

| 场景 | 结果 | 证据边界 |
| --- | --- | --- |
| start-1 | HTTP200、同socket核心归属、正流量、非DIRECT | RUNNING后9ms进入诊断请求；SOCKS动作前还有约110ms诊断准备。DNS候选64条，额外126条受上限丢弃，核心事件流drop为0。 |
| stop-1→start-2 | HTTP200及相同归属要求通过 | RUNNING后5ms进入请求，无DNS候选溢出。 |
| 受控SIGKILL→系统自动恢复→recovered-1 | HTTP200及相同归属要求通过 | 原PID25477消失；在verify之前观察新PID25658及前台服务，再直接确认RUNNING。恢复后3ms进入请求，无DNS候选溢出。 |

三轮dial记录均为成功出站，日志等级/临时日志订阅恢复；无自动重试。当前网络未复现历史TLS EOF，**不能关闭首请求根因**。初轮成功也不能证明健康检查DNS与本socket存在关联。`initial-summary.json`保留精确时间、溢出数和恢复观测。

初轮结束执行`stop-2`，快照确认核心STOPPED。后续在同一独立motion库通过正式Repository复用该FILE，并导入唯一授权URL；双端临时明文输入按SHA核对删除。正式包与原版Flutter缺席，固定安全参考包未操作，原始附件保留。

## 最终集中复验（compile9）

同一motion APK SHA`adb529033f89f079ead991619d3ed7d3cfb113d4eef3c8bf26202e7963c1511e`、Test SHA`975b3c092d93707c21e98dfd8a7fc37f2f94bafb250887cdde636388ea6e10f7`，安装回拉一致。driver以`--app com.lonnnnnng.flclash.motion`运行；受控失败禁止在正式包执行。原`run-as`备份检查改为应用内只读preflight：先确认无遗留密文，再等待Application初始化完成，检查STOPPED/恢复意图为关闭/无服务，外部复查后才隔离准备进程；不删除旧备份或跳过保护。

| 场景 | 最终结果 | 证据边界 |
| --- | --- | --- |
| diagnostic-close | 通过：明确关闭底层socket后，TLS握手报SocketException，阶段/源端口/拨号快照字段保留，原日志状态恢复，同会话后续HTTPS200 | 故障注入，不是历史TLS EOF复现；TLS包装对象可能到握手才访问已关闭socket。 |
| diagnostic-timeout | 通过：注入等待超时，TimeoutCancellationException继续传播，完整失败结果仍在NonCancellable收尾发出，日志恢复；同会话后续HTTPS200 | 仅验证取消与诊断收尾，不证明自然网络超时的原因。 |
| before | 通过：提交前SIGKILL，PID29661→29804；密文保留待保存正文，自动提交0次，显式重试提交1次；缓存/核心成员一致、源文件保持 | 外部先观察新PID同PID前台服务，再verify；约1.808秒仅服务观察耗时。 |
| after | 通过：提交后SIGKILL，PID30014→30162；恢复确认已提交、显式保存亦不重放，缓存/核心成员一致、源文件保持 | 外部先观察新PID同PID前台服务，再verify；约1.861秒仅服务观察耗时。 |

最终同套包4/4；两个保存窗口各有准备、恢复、保存后3笔HTTPS200，两个受控失败各1笔后续HTTPS200，共8笔正常网络请求均有新连接ID、匹配源IP/端口/目标及正双向流量/非DIRECT链。两笔注入失败独立保留，不能与8笔请求合并成全成功请求。最终10次诊断结果的DNS候选均无溢出；初轮start-1的126条溢出继续保留。四场各自恢复两份基线行、原正文SHA/size/mtime、偏好值、脚本及选择，清空本场备份，结束无服务/TUN。此保护不涵盖偏好updatedAt/全写者审计，不推广正式R8、VPN或OEM自然回收。

前序compile4保存窗口2/2及compile7关闭诊断通过独立保留，不与final9合计。compile6的首次关闭诊断原结果为失败：宿主只接受tls-create，但设备实际在tls-handshake抛SocketException；数据恢复已通过，修改的是诊断断言，并新增faultApplied确认真实注入。`diagnostic-close-final/result.json`保留原失败。

统一检查的`final-build3`另保留1条既有JVM失败：`ProfilesViewModelTest.persistedFileAndRemotePickerTokensAcceptOnlyMatchingResultAfterViewModelRecovery`在等待真实IO后关闭时，`withTimeout(5000)`使用虚拟时钟提前超时。原XML已保存`jvm-final-build3-profiles-failure.xml`；测试改用Default上下文内同样5秒真实时间，保留关闭终态/密文清理断言，不改生产编辑器。该测试修正不进入motion或正式APK，最终源码差异单独核对。

修正后定向1/1，最终`final-build4`四模块889项JVM零失败/错误/跳过（App604、Service216、Core38、Common31，含既有任务复用），Debug/未签名Release-R8与双Lint通过；Debug73 warning/14 hint、Release69/13，均零error。MLKit最终DEX3/3，Release清单与DEX没有本组诊断入口。源码比对仅JVM测试文件与已安装compile9快照不同，运行源码一致；原1.yaml SHA/35165字节/mtime保持。独立motion包、两份获准基线和加密stability输入暂留用于后续长窗，未宣称已清空测试环境或已发版。结构化证据：[functional-network-diagnostics-redmi-20261111.json](acceptance/functional-network-diagnostics-redmi-20261111.json)。

## 下一步

1. 写入来源审计和新45分3秒窗口已完成阶段验证，10/10正常HTTPS200、零审计丢弃；补齐复合偏好写入映射、逐键来源和快照分析边界，详见[状态审计](state-write-audit-investigation.md)。不能用本轮成功追认旧异常原因。
2. 根据真实首失败归因修复；若环境未复现，保留未定根因，不制造延迟、重试或换加速源的假闭环。Provider同样保留旧final6-before超时，后续阶段日志用于下次现场定位。
3. 独立进程/native加载/只读输入探针已验，核心完整解析预检已接入，扩展边界和同源验收继续，见[预检记录](core-semantic-preflight-investigation.md)。应用内更新真实升级基本流程已验，下载死亡/session中断等尚未收口，按用户最新指示暂缓并排到最后；先推进本页三个历史异常。RawConfig解码不能代替完整解析。
