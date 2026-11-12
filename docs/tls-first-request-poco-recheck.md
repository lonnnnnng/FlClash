# 更换手机后的首次联网 TLS 复验

2026-11-12用户更换真机，明确要求重新复现TLS首次联网失败。ADB唯一连接设备为POCO F2 Pro（`31936bb6`），Android 16 / API36；本轮按用户指定覆盖旧Redmi设备限制，不使用模拟器。应用基线为0.1.4对应产品源码，内核仍固定`d43bbd751fe5eb95a599a6211423f645269ba57d`。

## 结果与定位边界

最终5轮冷进程启动全部通过：每轮先停止核心，再force-stop独立motion应用，确认新PID后启动VPN；普通独立UID探针在核心READY后1285～1391ms开始请求，没有人为增加网络预热等待。5次均UDP DNS成功、TLS完成、HTTP200，并在核心观察到同源端口非DIRECT连接和双向正流量，VLESS两字节响应头均完整返回。TLS握手1098～4947ms，成功仍有延迟波动。

唯一授权HTTPS目标摘要、原1.yaml摘要与最新Redmi Wi-Fi失败批次一致；5轮最终`node-selected`端点摘要也与那次失败相同，未手工固定节点或改正文。旧失败已捕获的lookup记录未见失败，`vless-ws-stream`阶段多次出现`read-tcp`错误，独立请求端最终记录`SSLHandshakeException`、5024ms；该旧诊断有9条记录丢弃，不能外推完整无遗漏或排除全部DNS因素。此前旧配置中VLESS响应头等待不结束是另一组保留记录，不能与这次WS阶段报错混为同一个已确认原因。

因此结论是**当前POCO/Wi-Fi环境未复现**，支持问题受当时条件影响、不是本轮稳定必现。手机和物理Wi-Fi同时变化，无法单独归因手机、系统、节点或网络，也没有证据宣称根因已修复。按本轮用户意图完成有界复现，旧失败转为历史观察；有新的可复现失败时带原条件重新打开，下一项回到KUI-042回归收尾。

最终5轮之前另有4次HTTPS200观察，包括同会话3次和仅核心重启后的1次。后者在READY约30秒后请求，不作为立即首请求证据；第一笔原始HTTP200在实时logcat中观察到，但补归档前已轮转，保留SHA核验过的核心回执，不伪造客户端原始JSON。最终5轮的客户端、观察端和时间戳报告完整，作为本组主要验收。

## KDIAG-004：回执重复轮询读取已删除文件

本机驱动`tool/android_endpoint_nodes_acceptance.py::report_items`首次读取`verified-file-v1`回执后会校验SHA/大小并删除设备端精确文件；后续轮询又从logcat得到同一token/command/SHA，仍去读取已删除文件，触发断言。首笔请求实际HTTP200，收集器失败不能计入TLS失败。

已改为先复用本轮目录中按command/SHA命名的本地副本，每次仍复核SHA和大小；新文件继续读取两次核对后精确删除，设备目录与token/command/SHA路径守卫保持。该缺陷来自本项目验收工具，不在Flutter或Mihomo源码，无需向上游内核迁移。4项Python回归覆盖重复轮询、损坏副本、越界路径及非默认设备型号校验。

驱动新增显式`--device-serial`和`--device-model`，非默认serial缺少型号即拒绝；默认仍锁定原Redmi。`--fresh-database`复用已有空motion库准备测试，原授权1.yaml仅通过USB传入，远程目标仅从外层AGENTS读取。独立Java探针补请求开始/TCP连通/TLS开始与完成的单调时间戳和脱敏异常类型链，超时与请求业务逻辑未变。

## 构建、清理与证据

911项JVM测试零失败/错误/跳过（App618、Service220、Core39、Common34），motion应用/测试APK和motion Lint通过（0错误、74警告、14提示）。最终探针Java源码另编译独立普通UID APK并回拉SHA核对；补时间戳后的AndroidTest APK重建/Lint通过，但未把它重新安装用于准备测试。正式Release/R8与Go未重跑，产品网络实现没有修改。

结束STOPPED、无活动VPN，三个本轮自有包已精确卸载；Flutter参考包`com.follow.clash`未操作且保持安装，Wi-Fi仍已验证。原1.yaml SHA-256`7536dd03c962ad47a076feea860c3a573f338024e60c2853685d91b18aeee8d7`、34220字节、mtime`1794392072`保持，不上传正文。

脱敏证据：[tls-first-request-poco-20261112.json](acceptance/tls-first-request-poco-20261112.json)。本机原始报告分别位于`../acceptance-artifacts/tls-first-repro-poco-20261112/`与`../acceptance-artifacts/tls-first-repro-poco-20261112-immediate/`，各有SHA清单，原始附件不提交。
