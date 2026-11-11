# KCORE-008：MKCP/Mekya 非法参数延迟到会话阶段触发 panic

2026-11-11，扩展只读语义预检时发现。固定来源为核心 `5fd3564fc10e3dea681b5928ac4b7b690d4d8a45`，此前已存在；未认定为历史 TLS EOF、Provider 恢复超时或配置元数据变化的原因。

## 两个确定的危险输入

- `transport/mkcp/config.go` 的发送/接收飞行窗口均计算 `… / (1000 / c.tti())`。`tti=0` 使用默认50；大于1000时内层整数除法为0，连接创建调用窗口计算会 panic。原监听和拨号入口均未限制该值。
- `transport/mekya/server.go:getSession` 使用配置 `PacketWritingBuffer` 分配 channel；负值会在首次会话创建时 panic。原监听和客户端构造未拒绝负值。

上面是原代码与 Go 运算/分配路径确认的缺陷，不声称专用 Redmi 曾被这两个输入触发崩溃。原入口 overlay 以不联网的 stub/空底层对象运行新回归：MKCP监听误接受、拨号继续访问底层对象，Mekya监听继续访问底层对象、客户端误接受，四分支红灯均保留；它证明入口未拒绝，不等于复现两种实际网络 panic 栈。

## 修正

MKCP 增加共享 `Config.Validate`，在 Listen、Dial 和 VMess 出站构造时拒绝 `tti>1000`；保留默认50及边界1000。Mekya 增加共享校验，在 Listen/NewClient 之前拒绝负写队列，并递归校验内层 MKCP。入站预检同样调用这些入口的共享规则，无需申请端口。

修复提交为 `a2136a6d34ac7c6144ffb75f0e13c0b8735dfca2`，已推送自有 `lonnnnnng/Clash.Meta:kclash-fixes`。

原始证据在外层 `acceptance-artifacts/semantic-preflight-20261111/extended-transport-red1.log`；overlay 仅替换四个原入口，保留新测试/辅助校验代码，不能称干净旧版本全量。修复后父模块与transport包race、端到端VMess MKCP/Mekya定向race和最终Redmi12/12、正式R8原配置导入结果见[扩展语义验收证据](acceptance/core-semantic-extended-redmi-20261111.json)。真机原配置不含两种坏参数，不能把原配置导入当作Redmi上两种panic的红绿复现。

## 同步复查

仅比较 `chen08209/Clash.Meta:FlClash` 后续源码；同步时同时检查监听、客户端/拨号及VMess构造三个入口。上游等价拒绝危险配置并通过正反例后再撤销本地补丁。无新增上游消息、Issue 或 PR。
