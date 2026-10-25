# 使用单一核心运行管理器协调 Android 服务

状态：accepted

在 `service` 模块建立唯一的核心运行管理器，统一负责 Go Mihomo 的 start/stop/restart、配置切换、崩溃恢复、前台通知和运行状态；`VpnService` 只负责 TUN，`ProxyService` 只负责本地代理模式，二者通过管理器协作且不各自启动核心。UI 通过命令接口请求状态变化，不直接操作 Service 或 gomobile。

这个边界保持核心生命周期只有一个事实来源，避免页面、VPN 服务和代理服务之间互相覆盖状态；代价是所有启动和停止请求都必须经过管理器的状态机和序列化队列。
