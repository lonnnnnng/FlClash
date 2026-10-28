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

当前已完成的 Android 手机功能切片包括：Profiles 的新 Room 模型、远程订阅下载和订阅用量解析、文件导入、基础 YAML 编辑、自定义 Profile 的 Rules、Proxies、Proxy Groups、Providers map、字段化 proxy provider override 和 DNS Overrides 分区编辑、Profile 列表排序、Profile 名称重命名、脚本本地资源管理（Room 元数据、应用私有正文文件、新建/读取/编辑/删除/排序）、远程脚本导入与手动同步、Profile 脚本绑定、Profile JavaScript 执行边界、本地 SAF 备份/恢复首片、配置文件原子写入、当前配置持久化、Mihomo 配置校验与应用、代理组读取、Selector 节点切换以及 Selector 对话框中的单节点手动延迟测试；Provider 和 DNS 覆盖按 Profile 自有 `overridesJson` 持久化，并在核心启动时只合并到暂存 YAML，不改写原始配置文件。VPN/TUN 启停、运行统计、活动连接、请求、DNS、日志和真实请求诊断也已经统一经过 `CoreRuntimeManager`，并在 Compose 页面通过 StateFlow 渲染；Requests、DNS 和核心日志详情对话框已支持搜索、日志级别筛选、跟随最新事件滚动、核心日志脱敏导出、诊断会话证据包导出以及核心/网络/DNS 运行事件关联；按应用代理选择器首片（Room 设置、应用列表、搜索、白名单/黑名单和运行中锁定）、智能选择首片（国内应用前缀分类、双列表生成和后台执行锁定）、剪贴板导入/导出首片、真实核心流量过滤回归及真实 MainActivity 页面级剪贴板导入/导出 E2E、通知栏 STOP、桌面启动/停止/切换快捷方式、Quick Settings Tile、外部 Action 契约与组件导出/权限自动化审计、电池优化系统引导和 Always-on VPN 系统设置引导首片也已接入并完成 Redmi 真机验收。

仍未完成的产品能力包括：Wi‑Fi↔移动网络切换、Tasker/MacroDroid 实际应用回归、完整 Flutter Access 页面 parity（关闭状态遮罩、一键开启、排序、系统/无网络过滤、智能选择、剪贴板导入/导出首片和页面级 E2E 首片已完成，完整视觉交互和返回未保存确认仍待补齐）、Always‑on VPN 状态回读、OEM 兼容性和完整系统行为回归、完整主题与响应式布局，以及正式发布签名和 Release 流程。订阅自动更新的调度、失败重试、YAML 校验、旧文件保护、失败摘要和通知已完成代码与自动化验证；两条授权路径的真实导入、HTTP 重定向、手动同步和自动更新开关回归也已完成，但周期任务等待到期后的真实自动下载仍未验证。本地 SAF 备份/恢复首片已完成代码、自动化验证和 Redmi 真机导出/导入回归；WebDAV 已完成 Keystore 凭据存储、手动配置 UI、上传/下载恢复接入、认证/权限/离线/超时错误反馈以及 Redmi 真机本地回环 WebDAV 回归，外部授权 WebDAV 服务回归仅在具备授权条件时执行，自动云端同步明确延期且不进入首发范围。Kotlin 主线已新增独立 GitHub Actions arm64 验证 workflow，并支持 `kclash-vMAJOR.MINOR.PATCH` Tag 与手动版本输入来生成可审计的未签名 Release artifact；正式签名和发布资产仍待后续阶段。Wi‑Fi 断开恢复、后台恢复、系统撤销 VPN 后的服务清理、失败态回显、再次启动、进程重建、通知栏 STOP、桌面快捷入口、Quick Settings Tile、外部 Action 的 ADB 契约和电池优化系统引导已完成真机验收；当前设备蜂窝网络未注册，移动网络切换仍待具备条件后验证。二维码图片选择、QR 识别、两条授权路径真机导入和重复导入错误反馈已完成，Deep Link 基础导入、冷启动、重复导入和错误反馈回归已经完成。现有观测卡片已经能展示真实请求诊断的 HTTP 状态、延迟、代理链和规则归因；观测详情的搜索、日志级别筛选、自动跟随和脱敏导出已完成，首页、配置、代理、连接、观测五个顶层目的地已完成首轮切片，观测页包含 Requests、DNS、Logs 三个页签，Rules、Proxies、Proxy Groups、Providers map、字段化 Provider override 和 DNS Overrides 分区快速编辑已完成，脚本 Profile 绑定与删除清理已完成，其他高级配置分区和完整页面级交互 E2E 仍待补齐。

后续按“先建立真实代理连接，再补观测和平台入口，最后做 Flutter 行为与视觉收敛”的顺序推进；Flutter 代码只作为行为参考，不作为 Kotlin 运行时依赖。诊断会话证据包已经覆盖一次 probe 的 HTTP 状态、代理链、Requests、DNS、核心日志和运行诊断事件收集与脱敏导出，后续继续补充跨进程重建后的历史关联、背压治理和长期运行自动化。
