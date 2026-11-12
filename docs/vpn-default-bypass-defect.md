# KUI-041：默认允许绕过 VPN 时普通应用请求未进入 TUN

2026-11-12，在 Redmi Note 8 Pro / Android16 API36 的 Wi-Fi／蜂窝切换验收中发现。父基线 `4f65f60f`，内核 `d43bbd75`。先记录可复现行为，不将其归因为节点、Mihomo DNS 或所有 Android 的通用缺陷。

## 来源与对照

`NetworkSettings.allowBypass=true` 来自 `eaa6e4b5`，`DefaultVpnOptionsProvider` 无存储时也回退 true，最后调用 Android `VpnService.Builder.allowBypass()`。只读参考 `upstream/main:lib/models/config.dart` 的 `VpnProps`（默认 true）及上游 Android `VpnService`，当前 Kotlin 原默认与 Flutter 一致；不合并 Flutter 提交，也未改动手机 Flutter 参考包。

本轮普通独立应用（非 instrumentation，UID10190、targetSdk36）和 AndroidTest APK 的独立 Activity 均作对照。ROM 对 AndroidTest UID 最初设为 REJECT_ALL，先通过系统设置只解除测试应用限制；该权限拒绝不计产品网络失败。

在 VPN 已连接、默认 Network 显示 VPN、进程未绑定任何 Network 时：

- allowBypass=true：普通未绑定 UDP DNS 查询超时；针对 VPN Network 的系统解析可返回 fakeIP，但未绑定 TCP 连接仍超时，核心没有同源端口连接。
- 同会话显式把 socket 绑定 VPN：DNS 与 HTTPS200、同源端口双向流量及代理链通过，说明该次 TUN 路径可用；这不能替代默认路由验收。
- 只在独立测试包把 allowBypass 改为 false 后，普通未绑定应用 DNS UDP 立即成功；在蜂窝上完成 HTTPS200 和核心归因。
- 同一蜂窝网络恢复 allowBypass=true，再次出现 UDP8秒/TCP12秒超时。错误及反测附件均保留。

该对照支持默认接管行为与此开关相关，尚未定位到系统 fwmark/netd 的具体机制。Wi-Fi 上另有节点握手阶段失败，DNS/TCP已经进入TUN的TLS错误与未进入TUN的TCP超时分别记录；不据此追认旧TLS异常根因。

独立复核曾怀疑测试DNS标签转义；主执行者逐字符核验为两个反斜杠的合法Java源码、按点拆分正确，未修改该行。其余初批runner就绪/缺失回执及权限失败保留，不计为通过。

## 修正

Kotlin 默认关闭“允许绕过 VPN”，无设置存储的 VpnOptions 回退同样关闭。保留用户显式开启的能力和已有保存值，并说明此开关允许应用选择其他网络；不重写已有偏好，不修改订阅正文、节点或 Mihomo。

这项修正只调整默认接管策略，不能保证用户显式开启绕过后仍强制所有 socket 进入 VPN。最终验收使用新安装的独立 motion 包默认配置，以及没有绑定VPN/没有指定HTTP或SOCKS代理的普通客户端，核对真实DNS、HTTP200、同源端口、目标host和不含DIRECT的代理链。网络恢复期间核心不重启、不清DNS、不切节点。

## 最终 Redmi 验收

完整矩阵在独立普通UID客户端（UID10190）中完成，应用进程未绑定物理Network、socket未显式绑定VPN，也未指定SOCKS/HTTP代理。最终10场中9场按预期通过：6/6 Wi-Fi↔蜂窝及完全断网后的恢复请求均完成UDP DNS、HTTPS200和目标端口的非DIRECT核心连接归因；Wi-Fi-only亦HTTP200；两次全断网的外网HTTPS按预期失败。网络切换时核心PID、运行起点和VPN会话保持，没有通过重启核心、清DNS或换节点掩盖恢复问题。

矩阵还保留了一次真实失败：Wi-Fi稳态首请求在约5秒TLS握手报`SSLHandshakeException`，所以8个预期联网请求为7成功/1失败，不能把整项写成10/10。矩阵结束后的Wi-Fi DNS/HTTP200及非DIRECT归因在约806ms通过；它不能覆盖矩阵首失败。系统切换观察等待混有观察器防抖和回执等待，不据此宣称恢复耗时。长连接跨底层网络存活也未测试。

最终修正版由全新motion配置验证`allowBypass=false`，另用网络设置页回归2/2。复验入口为`tool/build_network_probe.py`和`tool/android_network_switch_acceptance.py`。公开正式包仍是未改的0.1.3，SHA与验收前一致；本轮没有安装修正版正式包或发布新版。原`1.yaml`的字节数、mtime与SHA保持，设备恢复Wi-Fi/SIM1开关、核心停止，三个自有验收包与核验后的临时输入已清理。完整边界及源文件哈希见[验收证据](acceptance/network-switch-redmi-20261112.json)。
