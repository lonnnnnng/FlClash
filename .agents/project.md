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

当前已完成的 Android 手机功能切片包括：Profiles 的新 Room 模型、远程订阅下载和订阅用量解析、文件导入、基础 YAML 编辑、配置文件原子写入、当前配置持久化、Mihomo 配置校验与应用、代理组读取、Selector 节点切换以及 Selector 对话框中的单节点手动延迟测试；VPN/TUN 启停、运行统计、活动连接、请求、DNS、日志和真实请求诊断也已经统一经过 `CoreRuntimeManager`，并在 Compose 页面通过 StateFlow 渲染；Requests、DNS 和核心日志详情对话框已支持搜索、日志级别筛选、跟随最新事件滚动、核心日志脱敏导出、诊断会话证据包导出以及核心/网络/DNS 运行事件关联；通知栏 STOP、桌面启动/停止/切换快捷方式、Quick Settings Tile、外部 Action 和电池优化系统引导也已接入并完成 Redmi 真机验收。

仍未完成的产品能力包括：Wi‑Fi↔移动网络切换、Tasker/MacroDroid 实际应用回归、按应用代理、Always‑on VPN 完整配置、配置高级编辑器（规则/代理/代理组/脚本/覆写分区）、完整主题与响应式布局，以及发布签名和 Release 流程。Wi‑Fi 断开恢复、后台恢复、系统撤销 VPN 后的服务清理、失败态回显、再次启动、进程重建、通知栏 STOP、桌面快捷入口、Quick Settings Tile、外部 Action 的 ADB 契约和电池优化系统引导已完成真机验收；当前设备蜂窝网络未注册，移动网络切换仍待具备条件后验证。二维码图片选择、QR 识别、两条授权路径真机导入和重复导入错误反馈已完成，Deep Link 基础导入、冷启动、重复导入和错误反馈回归已经完成。现有观测卡片已经能展示真实请求诊断的 HTTP 状态、延迟、代理链和规则归因；观测详情的搜索、日志级别筛选、自动跟随和脱敏导出已完成，首页/配置两目的地导航已完成首轮切片，Requests、DNS、Logs、Connections、Proxy 独立页面和完整页面导航仍待补齐。

后续按“先建立真实代理连接，再补观测和平台入口，最后做 Flutter 行为与视觉收敛”的顺序推进；Flutter 代码只作为行为参考，不作为 Kotlin 运行时依赖。诊断会话证据包已经覆盖一次 probe 的 HTTP 状态、代理链、Requests、DNS、核心日志和运行诊断事件收集与脱敏导出，后续继续补充跨进程重建后的历史关联、背压治理和长期运行自动化。
