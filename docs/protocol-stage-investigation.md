# VLESS 协议阶段与 Provider 提交等待调查

2026-11-11，父基线`65f5ba96`、核心基线`9fb797d1`。继续首请求与Provider恢复功能收尾；应用内更新暂缓，名单保护已完成，其他项按各自边界保持，不以本组有限绿测宣称总目标完成。

## 协议阶段

在现有普通VLESS连接内部观察首请求写入和响应头读取，不增加net.Conn包装，不修改`Read/ReadBuffer`、`Write/WriteBuffer`及可替换接口的合法路径。只保留固定阶段、begin/end、耗时、业务载荷数、向下一协议层交付/取得的字节数、期望头长度和错误类别；不保存UUID、节点、主机、路径或字节内容。

`vless-ws-stream`只说明原Stream构造调用开始/返回。普通无early-data的WS会在返回前校验升级；early-data或fast-open可延期，不能把该事件泛称HTTP101。`vless-request`载荷为0时，只能证明写了VLESS请求头，不能说ClientHello已发出。`vless-response`包括两字节头及声明的附加数据；没有end不等于成功，更不能推断已收到0个底层TCP字节。

拨号ctx可能在连接交付后取消，协议回调仅借其源端口身份关联，不将该取消状态记录成IO失败。停采/更换observer后旧回调失效；仍无跨轮generation，不推广端口复用或所有协议。只覆盖普通`*vless.Conn`的TCP路径，Vision等其他包装不假装已覆盖。

宿主仍仅保存前8个候选dial，诊断总上限由16扩为32以容纳协议阶段，省略/丢弃计数继续公开；满时保留最后winner。数据是协议适配层的计数，不是TCP线上或服务端确认字节。

读取中发现并红绿确认[KCORE-010](vless-response-addon-defect.md)：原VLESS附加头CopyN错误被忽略，已返回原错误并保留未完成握手状态。它与真实TLS停滞的因果尚未建立。

## KDIAG-003：保存已经报错却继续等提交门闩

前组source1 after准备HTTP200后，`prepare-commit-window`等待30003ms，终态`documentError=true`、saving=false、intent=1/committed=0；没有ready/kill/verify。`ProfileStatisticsViewModel`会把保存异常转成页面状态而不向宿主抛出，原宿主却只等committed Gate，因此把提前失败显示为超时。

Debug宿主现同时等待Gate和文档错误，错误出现且saving=false时立即以固定诊断错误结束，继续输出已有脱敏分类/摘要；不重试、不改保存业务，也不释放已命中的死亡门闩。真实保存错误根因仍未知，文案分类不能替代底层阶段/绑定证据。

新增两项真机回归分别检查提前拒绝不等2秒超时，以及提交已到达时文档仍开着也可结束等待。前两项连接轨迹回归另保留；实际运行结果见本组验收JSON。

## 改动前的独立对照

沿用前组已安装包`c11c792e…`，`provider-after-baseline`完成准备、真实SIGKILL后系统新PID、正文回读/无重放、保存后核对及3笔HTTPS200。独立服务观察约1.864秒，不是HTTP恢复耗时。该场数据恢复/备份清空，未复现保存错误；不能用它关闭前组两笔TLS失败或source1保存错误。

最终包的网络结果、构建、源码身份和清理证据在[protocol-stage-redmi-20261111.json](acceptance/protocol-stage-redmi-20261111.json)中分别记录；原始目录`../acceptance-artifacts/protocol-stage-20261111/`，不覆盖前组失败。

## 最终集中验收及用户收口决定

最终内核`d43bbd751fe5eb95a599a6211423f645269ba57d`已推自有fork，Go父模块及dns/dialer/vless/route/http/updater race通过。907项JVM零失败/错误/跳过，Debug/未签名Release-R8/双Lint/motion-Test和MLKit DEX3项通过；Lint Debug75 warning/14 hint、Release71/13，均零error。1760份源码哈希保持。Redmi App SHA`313e94a1664634b533176280acef0071d88adb4f84c81af513acff56ab394a81`、Test SHA`336964e0d3347acbc15be8444a4c9ac511f2d564c6581de62397487c8ae17919`安装回拉匹配，连接轨迹/保存等待回归4/4。

| 最终场景 | 结果 | 边界 |
| --- | --- | --- |
| Provider after1 | 通过，准备/真实SIGKILL系统恢复/保存后三笔HTTPS200，缓存与成员/防重放及数据恢复通过 | 同一最终包，仅此死亡场景完成 |
| Provider before1 | 准备HTTPS TLS约20秒超时，254B上传/0B业务下载，174次采样、0读取失败 | 未到保存和死亡，不计恢复失败或通过；计网络失败 |
| 独立FILE控制同会话 | start失败、checkpoint2成功、checkpoint3失败、checkpoint4成功，2/4 | 失败发生于同会话再次请求，不限启动/恢复；失败独立保留 |

最终包共8笔网络样本5成功/3失败，三次失败均WS Stream已返回、VLESS首写计数完整，response begin后在采集结束前没有end。请求首写289或552字节，载荷分别254或517字节；因此零载荷首包不适用于这三笔。失败的协议记录丢弃0、连接读取失败0，但阻塞ReadFull可能已取得0或1字节，不能把无end写成“绝对没有收到任何响应”。成功样本响应头均2/2、未出现非零附加头，KCORE-010不能认定为这些超时原因。

正式四表、两正文SHA/size/mtime、UID/schema、原1.yaml保持，正式包未重装；两份只读报告按SHA核对后精确删除，结束无服务/tun0。独立motion/Test和授权加密输入暂留，Flutter未操作、未新发版。App updater继续暂缓。

本轮用户明确接受“近期验收正常、历史异常目前不能复现，不再死磕”。配置/偏好长期稳定性、Provider运行态进程恢复因此收口为`closed-accepted-historical-risk`；不再追加专门复现旧异常/保存错误的任务，保留历史记录、审计和诊断，有新可复现问题再打开。上文“保存错误原因仍未知”是留档边界，不是继续阻塞目标的任务。仍可复现的TLS属于独立网络任务，继续做同端点/协议与链路对照。
