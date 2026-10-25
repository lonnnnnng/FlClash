# Project Context

FlClash Kotlin 是面向 Android 手机的原生客户端，复用 Go Mihomo 代理核心，使用 Jetpack Compose 重建 Flutter 参考实现的 Android 体验。Kotlin 分支是长期主线，Flutter 代码只在 `main`/`upstream` 分支保留。

## 已确认边界

- 首发设备：Android 手机，覆盖竖屏和横屏；Android TV、平板、车机和桌面平台暂不验收。
- UI：Compose + Material 3，自定义 Design System 对齐 Flutter 的颜色、字体、间距、形状和交互。
- 状态：单 Activity、Navigation Compose、ViewModel、StateFlow 和单向数据流。
- 数据：Room 全新 schema，不兼容 Flutter/Drift 数据；只维护 Kotlin 自身的未来迁移。
- 依赖：Hilt 管理 Application、Activity、Service、ViewModel、Repository 和 UseCase 的生命周期。
- 核心：Go Mihomo 继续负责代理协议、路由、DNS、TUN、配置解析和运行时连接。
- 服务：`CoreRuntimeManager` 统一核心生命周期，`VpnService` 只负责 TUN，`ProxyService` 只负责本地代理模式。

## 功能基线

Android Kotlin 需要覆盖 Flutter Android 端已有的 Profiles/订阅、配置编辑、代理组、延迟测试、连接、请求、DNS、日志、Quick Settings、Deep Link、按应用代理、电池优化和 VPN/TUN 能力。桌面托盘、窗口、全局热键和桌面 Helper 不进入首发范围。

## 当前迁移阶段

第一阶段根目录原生化已落地：根目录使用 Gradle Wrapper，`app`、`core`、`service`、`common` 通过 Android Gradle 工程组装；Compose、Hilt、Room、StateFlow 和 `CoreRuntimeManager` 已接入，Go Mihomo 只生成 `arm64-v8a` AAR。Debug APK 已在 Redmi Note 8 Pro（API 36）安装并完成启动、前台服务启动/停止和界面检查。

当前已完成的 Android 手机功能切片包括：Profiles 的新 Room 模型、远程订阅下载和订阅用量解析、文件导入、基础 YAML 编辑、配置文件原子写入、当前配置持久化、Mihomo 配置校验与应用、代理组读取、Selector 节点切换以及 Selector 对话框中的单节点手动延迟测试。基础切片统一经过 `CoreRuntimeManager`，并在 Compose 页面通过 StateFlow 渲染。

仍未完成的产品能力包括：VPN/TUN 权限与启动链、连接状态和流量仪表盘、连接列表、请求列表、DNS 查询、日志查看、Quick Settings、按应用代理、电池优化引导、配置高级编辑器（规则/代理/代理组/脚本/覆写分区）、二维码导入、完整主题与响应式布局，以及发布签名和 Release 流程。Deep Link 基础导入、冷启动、重复导入和错误反馈回归已经完成。现有 `service` 中的 TUN 和通知代码属于可复用的 Android 基础设施，但尚未全部接入新的 Kotlin 页面和 `CoreRuntimeManager` 业务链路。

后续按“先建立真实代理连接，再补观测和平台入口，最后做 Flutter 行为与视觉收敛”的顺序推进；Flutter 代码只作为行为参考，不作为 Kotlin 运行时依赖。
