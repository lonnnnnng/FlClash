# Architecture

## Target modules

- `app`：唯一 Android application，包含 Compose UI、Navigation、ViewModel、Hilt 入口和 Android Manifest。
- `core`：Go Mihomo 的 gomobile AAR 构建和 Kotlin Bridge；只有 Bridge 可以调用生成的 gomobile 类。
- `service`：`CoreRuntimeManager`、`VpnService`、`ProxyService`、前台通知和服务间状态协作。
- `common`：跨 app/core/service 共享的纯 Kotlin 类型、错误、Intent 常量和协议数据结构。
- `data`/`domain`：先在稳定边界后拆分；Repository、Room DAO、UseCase 不让页面直接依赖底层实现。

## Runtime ownership

`CoreRuntimeManager` 是唯一核心生命周期所有者。它串行化 start/stop/restart/apply-config 请求，维护运行状态、配置版本、错误和恢复策略。UI 只能发送命令并订阅状态；Service 只能执行平台职责并向管理器报告结果。

系统撤销 VPN 时，`ServiceBroadcastReceiver` 只负责接收受签名权限保护的 `VPN_REVOKED` 显式广播并转交 `CoreRuntimeManager`；接收器不直接启停服务，避免形成第二套生命周期。

`VpnService` 创建和销毁 TUN、转发网络变化和前台服务状态；`ProxyService` 管理本地代理模式。两者共享核心管理器，不能各自启动第二个 Mihomo 实例。

## Core boundary

Go 核心继续从 `core/mobile` 生成 Android AAR。Kotlin Bridge 将生成类转换为稳定的领域请求、结果和事件，处理线程切换、错误映射、字节/JSON 编解码和资源释放。Compose、ViewModel、Room 和 Service 不直接依赖 gomobile 生成的类型。

## Data and state

Room 只承载 Kotlin 新应用的数据模型；Repository 负责数据库与领域模型之间的转换。ViewModel 通过 StateFlow 暴露不可变 UI State，核心运行事件、Profiles、代理组、连接、请求、DNS 和日志各自拥有明确状态源。

Profiles 的页面状态通过 `ProfileStore` 和 `CoreRuntimeStatusProvider` 依赖稳定边界，生产环境分别绑定 `ProfileRepository` 和 `CoreRuntimeManager`；测试可以替换这两个实现而不启动 Mihomo 或 Android VPN 服务。

## UI

Compose 使用 Material 3 作为基础，并由独立 Design System 保存 Flutter 参考实现中的颜色、字体、间距、形状、状态色和导航行为。页面不通过平台分支复制逻辑；差异应进入明确的 Android 手机布局策略。
