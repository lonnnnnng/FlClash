# KCORE-010：VLESS 截断附加头的读取错误被忽略

2026-11-11，自己的核心基线`9fb797d1a0072e228c7b2dcec69d632ea7e4ad02`。源码为`transport/vless/conn.go`的`Conn.recvResponse`，与本地跟踪的唯一上游`chen08209/Clash.Meta:FlClash`固定`024245dc72bc92f48237f865119afe0cd53ac9e6`字节相同，原SHA256为`b0bba1f18b8936136eebdd8096f00d92d6a003203ee0e5fdcde74e1ea62f27d6`。没有直接同步官方 Mihomo 提交。

## 原问题及影响

响应前两字节声明有附加头时，原代码调用`io.CopyN(io.Discard, vc.ExtendedConn, length)`却忽略返回错误。远端声明4字节、实际只返回0或2字节即结束时，`recvResponse`错误地返回nil。

普通`Read`和`ReadBuffer`都会据此将`received`设为true，允许`ReaderReplaceable`绕过握手层；截断响应未被认定为握手失败。下一次业务读取仍可能报EOF，所以不能把本缺陷夸大为所有截断都无错误、或已经证明实际正文损坏。非EOF读取错误也可能丢失原出处。

## 修正与红绿

保存`io.CopyN`返回值和原错误，在附加头未收齐时将错误传回原调用者，不释放握手读取状态。不改合法响应、业务正文、超时、重试或选路。

固定修复提交：`d43bbd751fe5eb95a599a6211423f645269ba57d`，已推`lonnnnnng/Clash.Meta:kclash-fixes`。本组Go双层race、907项JVM、Debug/Release-R8/双Lint及Redmi最终包验证见[证据](acceptance/protocol-stage-redmi-20261111.json)；实网没有非零附加头，修复本身由确定的内存协议红绿测试证明。

`TestResponseRejectsTruncatedAddonAndPreservesReadFailure`原源码红测：`truncated addon accepted: <nil>`。修正后同名测试通过，覆盖无附加字节、部分附加字节、公共Read保留未完成状态，以及完整附加头后不吞业务载荷。`TestHandshakeDiagnosticsPreserveOrdinaryAndBufferIO`另覆盖普通/缓冲首写、零/非零载荷及响应头后正文。完整Go竞态及Android/Redmi证据随[本组调查](protocol-stage-investigation.md)记录。

本机原始证据位于`../acceptance-artifacts/protocol-stage-20261111/`的`vless-addon-source.json`、`vless-addon-red.log`、`vless-addon-green.log`。核心提交由父仓库gitlink与本组验收JSON固定。

## 与真实 TLS 失败的边界

目前真实异常为lookup/connect成功后254B业务上传、0B业务下载。尚无证据证明这些失败收到过带非零附加长度的VLESS响应，本缺陷不能认定为历史TLS超时根因。

后续只从`chen08209/Clash.Meta:FlClash`同步时，检查此函数是否等价返回附加头读取错误，并运行同一红绿回归。只有上游等价修正及回归通过后才移除此本地补丁，保留本记录和原始失败。
