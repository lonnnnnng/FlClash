# 网络切换时重置共享出站与保护 VPN 前 socket

状态：实现与自动化验证完成；Hysteria2 Wi-Fi 实网通过，WireGuard/TUIC 实网待补

日期：2026-11-21

## 来源

- Flutter 行为参考：`chen08209/FlClash` 提交 `b13cf52a`，网络改变时关闭共享出站连接。
- Mihomo 实现参考：`chen08209/Clash.Meta:FlClash` 提交 `5b453f80`，为 WireGuard、Hysteria2、TUIC 增加重置行为。
- Kotlin 产品内核仍使用 `lonnnnnng/Clash.Meta:kclash-fixes`，本组只移植上述行为，不改变内核来源或同步整条分支。

## 决策

`NetworkObserver` 已在底层 `Network` 身份变化时调用 `Core.resetNetwork()`。Go 桥原先只关闭统计连接并清 DNS；现先关闭在途连接，再遍历 `tunnel.AllProxies()` 与 Provider 快照，对实现 `ResetNetwork()` 的 adapter 各调用一次，最后清 DNS。Provider 单独遍历是因为 `AllProxies()` 会按名称折叠节点；同一个 adapter 同时出现在两处时按对象去重。

Mihomo 适配只重置依赖共享传输的对象：WireGuard 重新绑定 UDP socket，Hysteria2 关闭旧共享 QUIC 客户端，TUIC 淘汰 TCP/UDP 两个 QUIC 客户端池。已有普通 TCP 连接关闭和 DNS 清理语义保持。

Android socket hook 改为 Go 核心包初始化时安装。VPN 回调尚未就绪时，hook 暂存 `RawConn` 并允许原拨号继续；TUN 栈启动前用同一个 fd 调用 `VpnService.protect`。平台拒绝保护时，活动 gate 仍向新拨号返回错误；暂存 socket 的拒绝结果保留到下一次激活重试，已关闭的 fd 丢弃。待保护队列会在容量增长前清理已关闭 fd。

## 验证边界

- Go 父模块 `go test -race ./...` 通过；包括直接经过 `handleResetNetwork()` 的配置代理及多个 Provider 同名节点重置、共享 adapter 去重回归。
- Mihomo `adapter/outbound` 与 `transport/tuic` 竞态测试通过；`hub/route`、`component/http`、`component/updater` 既有竞态回归通过。
- VPN 前 socket 定向测试使用真实 UDP `RawConn`，确认激活时被保护的是同一 fd；另覆盖保护拒绝后下次激活重试、关闭 fd 丢弃、活动 gate fail-closed 和并发激活。
- POCO F2 Pro / API36 单独实测平台 API：预先创建并绑定的 UDP socket，在系统 TUN 已建立、VPN 服务仍为 `STARTING` 且 `Core.startTun` 调用仍活动时，由同一个 `VpnService` 实例调用 `protect(DatagramSocket)` 返回 true，socket 仍打开。核心启动后独立快照为 `ready`，`callsDuringStart=24`、`rejected=0`。这是直接 Android API 证据，探针 socket 不等同于 Go pending 队列中的任何 `RawConn`，也不声称调用发生于 Go gate 的短暂 `phase=starting` 子窗口；完整回执见 `docs/acceptance/vpn-protect-startup-poco-20261121.json`。
- `./gradlew test :app:assembleDebug --no-daemon` 通过，`core:bindCore` 执行并重新生成 Android 核心；JVM 测试任务由 Gradle 标记为 up-to-date。
- POCO F2 Pro / API36 用工作区 `2.yaml` 的唯一 Hysteria2 节点实测 Wi-Fi 断开 4 秒后重连：切换前后普通独立 UID 的真实 DNS/TLS/HTTPS 均通过，HTTP 200，核心同源端口出现该 Hysteria2 叶节点哈希对应的双向非直连流量；底层 Wi-Fi Network handle 改变而 motion 核心 PID 与 `runningSinceElapsedMs` 不变。`allowBypass` 起始与恢复值均为默认 `false`。WireGuard/TUIC 当前没有验收节点，蜂窝切换也未验（POCO 无 SIM）；测试前后 `2.yaml` SHA、大小和 mtime 保持，motion/测试包已卸载，Flutter 参考包进程未操作且 PID 保持。实测只证明 Hysteria2 在本设备 Wi-Fi 离线重连场景恢复，不推广至其他协议/设备，也不改变历史 TLS 故障归因。
