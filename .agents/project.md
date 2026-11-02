# Project Context

FlClash Kotlin 是面向 Android 手机的原生客户端，复用 Go Mihomo 代理核心，使用 Jetpack Compose 重建 Flutter 参考实现的 Android 体验。Kotlin 分支是长期主线，Flutter 代码只在 `main`/`upstream` 分支保留。

## 已确认边界

- 首发设备：Android 手机；Android TV、平板、车机和桌面平台暂不验收。页面验收固定使用竖屏和系统默认字号，不运行横屏或大字号测试。
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

当前已完成的 Android 手机功能切片包括：Profiles 的新 Room 模型、远程订阅下载和订阅用量解析、文件导入、基础 YAML 编辑、自定义 Profile 的 Rules、Proxies、Proxy Groups、Providers map、字段化 proxy provider override 和 DNS Overrides 分区编辑、Profile 列表排序、Profile 名称重命名、脚本本地资源管理（Room 元数据、应用私有正文文件、新建/读取/编辑/删除/排序）、远程脚本导入与手动同步、Profile 脚本绑定、Profile JavaScript 执行边界、本地 SAF 备份/恢复首片、配置文件原子写入、当前配置持久化、Mihomo 配置校验与应用、代理组读取、Selector 节点切换以及 Selector 对话框中的单节点手动延迟测试；Provider 和 DNS 覆盖按 Profile 自有 `overridesJson` 持久化，并在核心启动时只合并到暂存 YAML，不改写原始配置文件。VPN/TUN 启停、运行统计、活动连接、请求、DNS、日志和真实请求诊断也已经统一经过 `CoreRuntimeManager`，并在 Compose 页面通过 StateFlow 渲染；Requests、DNS 和核心日志详情对话框已支持搜索、日志级别筛选、跟随最新事件滚动、核心日志脱敏导出、诊断会话证据包导出以及核心/网络/DNS 运行事件关联；按应用代理选择器首片（Room 设置、应用列表、搜索、白名单/黑名单和运行中锁定）、智能选择首片（国内包名前缀分类、双列表生成和后台执行锁定）、剪贴板导入/导出首片、真实核心流量过滤回归及真实 MainActivity 页面级剪贴板导入/导出 E2E、Access 返回未保存确认、Access 卡片顶栏搜索/全选入口、Material 矢量图标/无障碍语义收敛和窄屏 `FlowRow` 响应式操作区、Access 直接选择保存后 Room 重建恢复、横屏重排、1.5/2.0 倍字体可达性、系统 1.3/2.0 字体回归、Flutter 顶栏保存/菜单层级、完整搜索态、默认卡片截图基线、1.3/1.5/1.8/2.0 倍字体截图矩阵和竖屏/横屏截图矩阵首片、通知栏 STOP、桌面启动/停止/切换快捷方式、Quick Settings Tile、外部 Action 契约与组件导出/权限自动化审计、电池优化系统引导和 Always-on VPN 系统设置引导首片也已接入并完成 Redmi 真机验收。

仍未完成的产品能力包括：Wi‑Fi↔移动网络切换、Tasker/MacroDroid 实际应用回归、完整 Flutter Access 页面 parity（关闭状态遮罩、一键开启、排序、系统/无网络过滤、智能选择、剪贴板导入/导出首片、Access 卡片顶栏搜索/全选、Material 矢量图标与无障碍语义首片、窄屏 FlowRow、横屏重排、1.5/2.0 倍字体可达性、系统 1.3/2.0 字体回归、真实宿主保存后 Room 重建恢复、Flutter 顶栏保存/菜单层级、完整搜索态、默认卡片截图基线和 1.3/1.5/1.8/2.0 倍字体截图矩阵首片已完成，更多系统字体档位、跨设备截图差异和完整视觉交互仍待补齐，页面级 E2E 仍需继续扩展）、Always‑on VPN 状态回读、OEM 兼容性和完整系统行为回归、完整主题和响应式布局，以及正式发布签名和 Release 流程。订阅自动更新的调度、失败重试、YAML 校验、旧文件保护、失败摘要和通知已完成代码与自动化验证；两条授权路径的真实导入、HTTP 重定向、手动同步和自动更新开关回归也已完成，但周期任务等待到期后的真实自动下载仍未验证。本地 SAF 备份/恢复首片已完成代码、自动化验证和 Redmi 真机导出/导入回归；WebDAV 已完成 Keystore 凭据存储、手动配置 UI、上传/下载恢复接入、认证/权限/离线/超时错误反馈以及 Redmi 真机本地回环 WebDAV 回归，外部授权 WebDAV 服务回归仅在具备授权条件时执行，自动云端同步明确延期且不进入首发范围。Kotlin 主线已新增独立 GitHub Actions arm64 验证 workflow，并支持 `kclash-vMAJOR.MINOR.PATCH` Tag 与手动版本输入来生成可审计的未签名 Release artifact；正式签名和发布资产仍待后续阶段。Wi‑Fi 断开恢复、后台恢复、系统撤销 VPN 后的服务清理、失败态回显、再次启动、进程重建、通知栏 STOP、桌面快捷入口、Quick Settings Tile、外部 Action 的 ADB 契约和电池优化系统引导已完成真机验收；当前设备蜂窝网络未注册，移动网络切换仍待具备条件后验证。二维码图片选择、QR 识别、两条授权路径真机导入和重复导入错误反馈已完成，Deep Link 基础导入、冷启动、重复导入和错误反馈回归已经完成。现有观测卡片已经能展示真实请求诊断的 HTTP 状态、延迟、代理链和规则归因；观测详情的搜索、日志级别筛选、自动跟随和脱敏导出已完成，仪表盘、代理、配置、工具四个手机主 Tab 已接入，无配置时隐藏代理，连接和观测从工具或可选连接统计卡进入，观测页包含 Requests、DNS、Logs 三个页签，Rules、Proxies、Proxy Groups、Providers map、字段化 Provider override 和 DNS Overrides 分区快速编辑已完成，脚本 Profile 绑定与删除清理已完成，其他高级配置分区和完整页面级交互 E2E 仍待补齐。

后续按“先建立真实代理连接，再补观测和平台入口，最后做 Flutter 行为与视觉收敛”的顺序推进；Flutter 代码只作为行为参考，不作为 Kotlin 运行时依赖。诊断会话证据包已经覆盖一次 probe 的 HTTP 状态、代理链、Requests、DNS、核心日志和运行诊断事件收集与脱敏导出，后续继续补充跨进程重建后的历史关联、背压治理和长期运行自动化。

## 当前首页归属（2026-11-02）

- 当前在线真机验收只使用工作区根目录 `AGENTS.md` 保留的唯一授权链接（原第二条）；保留已导入的真实配置供调试，旧双链接记录只作为历史证据。
- Android 默认首页只显示状态、网络速度、出站模式、配置、策略组，按 Flutter 顺序排列；连接统计、内存信息、服务状态、内网 IP 和 VPN 默认隐藏。
- 按应用代理从工具设置分组进入；电池优化从常规/按需连接的前置条件进入。系统授权不再作为首页卡片。
- 系统 Always-on 与订阅请求证据包是 Kotlin 验收扩展，仅显式 Debug 开发者入口可见，普通启动与 Release 不展示。
- 内存信息默认隐藏，已支持真实 RSS/Go 分项、详情、释放后重读与前台可见态轮询；内网 IP 已支持非 VPN 地址、复制、局域网共享覆盖和运行态监听热切换；可选 VPN 已接入独立设置、纯代理模式及显式重启生效；服务状态已接入 13 个固定目标、停止态 DIRECT/运行态规则检测、目标管理与路由失效保护，详情按检测代理链首节点查询真实出口 IP，并支持共享质量详情、复制、隐藏和独立重试，整行长按排序、边缘持续滚动和松手保存已验收，服务结果已接入 route hits 逐项核验复用，保留原检测时间；网络/配置/会话强失效，出口 IP 与质量不随服务结果复用，验收证据见 roadmap；运行时间与 30 点真实速度历史已完成独立切片，出口 IP 已支持停止态 DIRECT 与运行态路由查询，有效配置加载后即可管理节点，运行中可热切配置；质量来源全覆盖、持久缓存和完整页面视觉继续按 roadmap 推进。当前配置详情已增加已应用输入与核心 Provider 的四项真实数量统计，详情前台刷新、失败重试和配置/运行状态变化清除已接入，外部 Provider 明细与 HTTP 单项更新已接入，文件类型只读；更新在启停锁外执行，Go 版本门禁和 Kotlin 配置/会话复核防止同名资源串配置，管理器驱动节点、统计与路由刷新。批量更新、资源编辑/侧载、订阅用量与完整视觉 parity 仍待补齐。

## 当前推进顺序（2026-11-02）

首页真实状态、停止态节点管理、运行态配置热切、预览/统计与 Providers 明细/HTTP 单项更新已有分项验收。Profiles 卡片分享三项、按目标 ID 只读配置预览、age 链接/订阅表单和 FILE 密钥编辑/系统文件替换已完成切片；FILE 只改密钥不改正文或时间，真实 SAF 取消/暂存/确认及保存失败保护已用外层 1.yaml 原字节、隔离 Room/缓存验收，CUSTOM 保持原分区编辑器。Proxy Groups 叶子协议显示、按 Profile/组隔离的 Tab/List 滚动恢复和 List 组内当前节点定位已完成 Redmi 精确回归。真实 age 密文经 Android Bridge 的完整 E2E、加密 SAF 与全新 Profile 导入的真实 SAF 仍待验，整体 Profiles/Proxy Groups UI/UX 保持 PARTIAL。下一步推进 URLTest/Fallback 完整语义、更细代理组视觉和排序/搜索/密度变化后的语义锚定；Providers 批量/编辑/侧载、首页出口 route hits、质量来源全覆盖/持久缓存及完整页面 parity 继续待补。Requests/DNS/Logs 次要视觉暂停，正式签名和发布单独推进。本轮 App 261、Core 34 项单测、Debug/AndroidTest 与 Lint 通过（0 errors、47 warnings、9 hints），Redmi 代理组精确方法通过；Service/Common/Go 未重跑。正式 Room 前后 inspect 仍为 0，未补建正式 Profile、清数据、启核心或访问订阅；详细证据和失败记录见 roadmap 同日 Proxy Groups 节。
