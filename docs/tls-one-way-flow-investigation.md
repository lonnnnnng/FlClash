# 拨号成功后的 TLS 单向流量调查（未闭环）

2026-11-11，父基线 `074db3d4`，内核从 `42e93516` 增加受控诊断到 `9fb797d1a0072e228c7b2dcec69d632ea7e4ad02`，已推自己的 `lonnnnnng/Clash.Meta:kclash-fixes`。没有合入官方 Mihomo 或 Flutter 提交。本组没有认定或修复新的产品根因，不分配新的 KCORE 缺陷编号。应用内更新继续按用户指示暂缓，排在最后。

## 已验证的现场

Redmi Note 8 Pro、API36、`wsvwypiz7xwslvl7`，使用独立 `.motion` 包、原始 `1.yaml` 副本及唯一授权在线目标。原始配置和请求目标未替换，没有加重试、切节点或固定等待。

| 最终包场景 | 观察 | 结果边界 |
| --- | --- | --- |
| Provider after2 | 准备首次 HTTPS：lookup 526ms、33 IPv4；最终拨号回执804ms，IPv4端点摘要 `429cd326…`；TLS 20026ms超时；171次连接采样，上传254B、下载0B | 未到编辑、提交、SIGKILL或恢复；不能计提交后恢复通过 |
| Provider before2 | 准备首次 HTTPS：lookup 512ms、33 IPv4；最终拨号回执845ms，IPv4端点摘要 `2eee191f…`；TLS 20024ms超时；169次连接采样，上传254B、下载0B | 同样未到编辑/死亡窗口；不能计提交前恢复通过 |
| 独立 FILE 控制会话 | start-control3、同会话两次checkpoint共3笔HTTP200，全部有同socket归因、正双向业务流量及非DIRECT链；最终端点为 `ed343fee…`（两笔）和 `387d0d21…`（一笔） | 与失败样本逻辑链哈希相同，但端点不同；3笔成功独立保留，不替代两笔失败，也不证明端点差异是根因 |

最终两次失败连接的读取失败/身份溢出均为0。每笔33个候选拨号只保留前8笔，省略25笔并显式报告；最终选中者另保留，普通记录丢弃0。`node-selected.durationMillis` 是公共拨号总耗时，包含lookup，不能叫纯TCP connect耗时。

这些证据表明异常也会发生在保存和杀进程之前。还没有同一最终端点在相同条件下的成功/失败对照，更没有服务端或抓包证据，不能判定为应用、内核、节点或手机网络中的某一方故障。

## 第一套包的不同失败必须保留

- source1 before：准备HTTP200；真实SIGKILL后系统新PID已在verify前观察，恢复HTTPS在TLS阶段约20秒超时。连接174次采样、254B上传/0B应用层下载，读取失败和溢出均0。
- source1 after：准备HTTP200、列表及文档绑定完成；`prepare-commit-window`等待30003ms超时，`documentError=true`、`documentSaving=false`、intent=1/committed=0。没有执行ready、kill或verify，准备请求结果并未丢失。旧报告没有保存错误具体分类，原因仍未定，不能写成TLS失败。
- source1只有底层候选拨号观察，16条上限曾被并行候选填满，没有最终winner。不可从第一条成功候选推测实际端点。原`source1.json`、事件及失败目录保留；最终包另用`source3.json`，不覆盖旧批次。

source1 after 的保存错误仍待复现。最终两场被准备网络失败挡住，未触及保存，不能声称已覆盖或修复该错误。

## 诊断改动及语义

- Debug `AcceptanceConnectionTrace` 对新连接ID、TCP、源IP/端口、目标主机/443逐项匹配。最多4个身份，保留零流量/单向流量、首末采样时刻、最大收发计数、链哈希及读取失败；不再只在双向成功后留下连接。
- `CoreRequestAcceptance` 将归因成功等待与全请求期间的连续采样分开，在成功、异常或取消的`NonCancellable`收尾统一导出。100ms是诊断采样计划间隔，不是就绪等待，也不是保证实际周期。
- 内核`component/dialer/dialer.go`在底层原拨号返回时发`node-dial`，在公共`DialContext`返回最终连接时发`node-selected`。不包装连接、不增加读写、不改变超时和候选选择；默认未显式启用源端口时不采集。没有`RemoteAddr`的自定义连接仍正常返回。
- 宿主立即把端点变成SHA、错误变成类别，限制候选8笔/总记录16笔；满时保留最后的winner并公开省略计数。Provider失败回执增加固定文案分类及摘要，不输出正文或节点。

Tracker统计位于WS/VLESS等协议适配之后。254B上传只代表核心业务流计数，不证明节点收到ClientHello；0B下载也不证明底层TCP/WS没有收到协议头。源码中的普通WS升级及核心成功日志是协议阶段的辅助线索，不能代替线上收发包或服务端日志。

采集仍按源端口关联，没有跨轮generation；不推广重叠端口复用、所有嵌套拨号或瞬间消失连接。16条缓存只保证满时保留最后winner，DNS事件仍是时窗候选而非socket归属。

## 验证与验收器修正

最终Go父模块全量race以及嵌套dns/dialer/route/http/updater race通过。新增测试覆盖原连接/错误返回、最终连接地址与候选不同、缺失远端地址、候选省略/缓存满后winner及finish/错端口隔离。907项JVM零失败/错误/跳过，Debug、未签名Release-R8、双Lint、motion/Test和MLKit最终DEX3项通过；Debug75 warning/14 hint、Release71/13，均零error。

`android2`保留一条既有JVM红测：`ProfilesViewModelTest.profileSnapshotDistinguishesInitialReadFromLoadedEmptyAndDeletion`期望最终选择为null，却即时读到829。组数流含真实IO，`runCurrent()`不等待其合流完成。测试改用Default上下文的5秒有界`StateFlow.first`等待完整列表/选择终态，保留原空列表与选择清空断言；产品代码不改。定向1/1及最终`android3`四模块907项通过，原XML保留。不将这条测试时序修正认作网络或历史元数据问题根因。

最终1757份源码哈希保持。Redmi回拉App SHA `c11c792e1402c41db76896340fbf7408ef7cf3f02c3d19277f23589e2a1e7a89`、Test SHA `5b47bff850f61e4c6466d6e3d1fbeeec985f93c27cedb1f38e3e73780066a0b2`一致；新增诊断回归2/2。上述编译/回归通过与两场真实网络失败分别报告。

## 收尾与下一步

四场Provider各自确认基线数据恢复、备份清空；最终两场无死亡窗口。正式包本轮未重装，仍为本地签名R8候选`992c48db…8a09f`，不是新发布资产。正式前后四表、两配置正文SHA/size/mtime、UID/schema保持，原`1.yaml`保持35165字节及原SHA/mtime；无Proxy/VPN服务、无tun0。3份本轮只读快照报告回拉校验后精确删除。独立motion/Test及授权加密输入暂留供调查，Flutter参考未操作。

接下来优先补同端点对照及WS/VLESS响应到业务TLS之间的协议阶段证据，再复测Provider保存错误分类和真实死亡窗口。首请求node-dns、历史配置mtime/未知偏好以及旧Provider超时仍不倒推解释；长期稳定性未闭环，应用内更新不推进。

结构化证据：[tls-flow-redmi-20261111.json](acceptance/tls-flow-redmi-20261111.json)。本机原始目录：`../acceptance-artifacts/tls-flow-20261111/`。
