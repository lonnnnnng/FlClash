# 进程重建时由应用恢复核心并抑制服务抢跑

状态：accepted

KClash 把“用户希望核心保持运行”作为持久化运行意图保存到 Room。`CoreRuntimeManager.startProxy()` 在确认存在当前 Profile 后写入意图；显式停止、系统撤销 VPN 和启动失败时清除意图。应用进程创建时由 `KClashApplication` 读取意图，读取全局 VPN 启用选择，启用时确认 VPN 授权仍有效后复用同一个 `CoreRuntimeManager` 启动入口恢复核心。

`ProxyService` 和 `VpnService` 仍使用 `START_STICKY` 支持系统在进程被回收后重新拉起进程，但收到系统重建的 `null Intent` 时不直接执行服务启动。服务先返回 `START_NOT_STICKY`，让 Application 完成依赖注入、核心初始化和 `VpnOptions` 恢复，再由 Runtime Manager 使用显式 Intent 按选择启动 ProxyService，以及启用 VPN 时的 VpnService。

这样可以避免服务先于 Application 恢复进程内状态，导致 `VpnService` 读取不到 `ServiceConfig.vpnOptions` 而建立失败；同时，用户主动停止不会因为 Sticky Service 的历史重启请求再次启动核心。代价是进程重建恢复依赖 Room 可用、Profile 文件仍存在以及启用 VPN 时授权未失效；纯代理模式不经过 VPN 授权门禁，启用 VPN 且授权失效时保留意图，等待用户回到 Activity 完成授权后再启动。
