# KCORE-009：双栈查询等待结束后误报空地址成功

2026-11-11，固定来源`a2136a6d34ac7c6144ffb75f0e13c0b8735dfca2`，本地参考`origin/FlClash=024245dc72bc92f48237f865119afe0cd53ac9e6`的`dns/resolver.go`同源。调查首请求失败时发现；不认定此前node-dns、TLS EOF或Provider旧超时由此引起。

## 触发与复现

`dns.Resolver.LookupIP`并发发起AAAA、同步等待A。A失败后再等待IPv6短窗口，窗口结束直接`return ips, nil`；若AAAA尚未返回且A没有地址，就出现空地址却没有错误。下游拨号才改为no-IP，原DNS失败链丢失。

`TestLookupIPPreservesFailureWhenIPv6WindowExpires`用独占内存dnsClient：AAAA经通道保持未完成，A收到合法空答复，lookup转为ErrIPNotFound。原实现失败`empty lookup must preserve failure: count=0 errorPresent=false`，不使用真实DNS、端口或授权配置替代文件。正例在AAAA已完成时保留有效IPv6成功结果。

## 修正与边界

仅在等待结束且地址为空、A已有错误时返回原错误，保留超时/取消与原有错误身份，不额外包装成新的不可重试类别。A成功和已完成AAAA回退保持原契约；不延长拨号预算、不添加请求、重试或睡眠。

这不是“AAAA已成功却因A残留err被丢弃”的证据：正常同次LookupIP获取到AAAA后明确返回nil error。父拨号可能先到期、实际单栈以及DNS事件只有共享查询级别都会造成不同观察，需用结构化lookup诊断进一步判断。

## 结构化取证

新增显式源端口观察器，tunnel只在当前采集端口传递context；parseAddr记录实际network、resolver角色、DisableIPv6、地址数/族计数、调用耗时、原错误及父context结果。宿主立即只保留白名单类别与host摘要；未启用时不记录，不从日志中任意域名猜节点。默认DNS事件增加有效地址记录数和安全错误类别，Debug候选仍不声称与socket逐次关联。

修复提交`42e9351626ff02b3161e95c96ed820a8e0227cf7`已推自有`lonnnnnng/Clash.Meta:kclash-fixes`。原始红灯、修复正反例和Go父模块及dns/dialer/route/http/updater竞态测试通过。907 JVM与Debug/Release-R8/双Lint、motion/Test构建通过；六轮Redmi首请求200且结构化lookup已回读，更多恢复窗口结果见本组最终证据。新增字段和有界诊断不保证外部DNS可靠，不以绿测替代旧失败定因。

## Redmi集中结果与未定网络失败

六轮启动与458.150秒恢复窗口8笔共14笔HTTPS200，同socket/正流量/非DIRECT；实际lookup为tcp/proxy、全局IPv6未禁用、返回23或33个IPv4。部分其他DNS查询失败分类dns-all-failed，不能把旁路候选误认本次lookup。语义回归最终同源12/12包含运行前后2笔HTTPS；首批11/12系driver漏传必填授权URL、在核心启动前失败，独立保留。

Provider保存前死亡复验准备HTTPS成功，但新PID恢复后lookup181ms返回33 IPv4、核心拨号成功，TLS握手20012ms超时；保存后场景在准备阶段lookup772ms返回33 IPv4、拨号成功，TLS握手再次超时，尚未到进程死亡。两场均明确服务CONNECTED/核心RUNNING、DNS引导已应用，日志状态恢复/原数据恢复/备份清空。它们不是node-dns分类失败，也没有证明KCORE-009的因果；整体网络和Provider本批不能标通过。

旧输入token包含前轮文件回执，新的恢复目录收集器曾被已删除的旧回执/同命令副本干扰而SHA拒绝，尚未网络测试；两份本轮报告按SHA补取留存并精确删除。collector改为只读当前命令及子阶段、传输副本名称带SHA后新目录完成窗口，原失败不改写。

正式本地签名0.1.2未重装，四表/两正文SHA-size-mtime/UID/schema保持；原1.yaml保持、无服务，独立motion保留供后续调查。结构化证据：[dns-lookup-redmi-20261111.json](acceptance/dns-lookup-redmi-20261111.json)，原始附件`../acceptance-artifacts/dns-startup-20261111/`。下一步补成功拨号后TLS停滞的流量/远端证据；旧三个历史异常不关闭，应用内更新暂缓。

仅跟随`chen08209/Clash.Meta:FlClash`。未来同步需复查空地址错误、A成功/AAAA回退正例、父请求取消及诊断停采/端口隔离；上游等价修复后再撤销同义补丁。未经用户指示不发上游Issue或PR。
