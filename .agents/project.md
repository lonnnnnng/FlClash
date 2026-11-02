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

## 当前推进顺序（2026-11-03）

配置页整页首版已接入独立滚动列表、选中定位、长按菜单、锚定添加来源、远程/FILE/自定义概览整页编辑及工具备份/脚本入口；FILE 名称/密钥/正文原子保存、URL/FILE DNS/Provider 覆盖已接通。自定义配置已补直接创建草稿、四分区概览、标题重命名/预览、整卡长按与 URL 菜单顺序，并保护元数据保存期间的新草稿；CUSTOM 导入与更多 NTP/Sniffer 字段覆盖/暂存合并已接入，App 284、Service 128 项通过，Redmi 最终 26/26，正式 Profile 文件保持完整。策略组可视列表/单组/成员/可选设置和快捷 YAML 首版已接通，引用跟随改名、原子保存与无缩进列表提取已补齐；App 294、Service 128 项和 Redmi 最终 12/12 通过，真实 MainActivity 仅创建并回收自己的临时配置，正式 Profile 前后保持完整。全屏编辑 Dialog 系统栏已跟随主题。标准/脚本扩展整页及 autoApply 首版已接通，含全局规则、预设/排序/批删、默认目标和脚本互斥；App 299、Service 137 项通过，Redmi 最终 12/12，真实停止态核心完成应用/回滚/重试，正式 Profile 前后保护通过。共享 YAML codec 已修复 emoji 高代理项满缓冲补读越界；证据见 roadmap 同日扩展页节。策略组批量/图标/问题横幅、更多页字段添加/快捷 YAML、全局资源导入映射、卡片组数、完整主题和真实运行态扩展 E2E 仍为 PARTIAL；下一步优先更多页字段添加/快捷 YAML、CUSTOM 规则可视整页，再补真实密文/SAF 闭环。

首页真实状态、停止态节点管理、运行态配置热切、预览/统计与 Providers 明细/HTTP 单项更新已有分项验收。Profiles 分享三项、按目标 ID 只读预览、age 表单和 FILE 密钥/系统文件替换已完成切片；真实 age 密文 Android E2E、加密 SAF 与全新 Profile 导入 SAF 仍待验。URLTest/Fallback 有效核心 now、固定/恢复自动、非法节点保护，延迟 Room 缓存真实进程重建，以及排序/搜索/密度变化后的语义锚定已完成对应验收。DNS baseline 和浏览器主链路修复已提交并推送，详见 roadmap。

代理页整体首版对齐已完成：单一工具栏、Tab/List、分组溢出选择、搜索清空、定位、折叠、三档卡片、独立测速和深浅主题，Redmi 精确批次 21/21 通过。整体 UI/UX 仍为 PARTIAL，下一步继续 Profiles/Dashboard 整页差异，并补真实密文/新增导入 SAF、URLTest/Fallback 无有效 now 的真实边界和跨进程手动优选状态。代理页连续超椭圆、精确动效/焦点、节点菜单/添加本地、远程组图标，Providers 批量/编辑/侧载与质量来源全覆盖/持久缓存仍待后续；首页服务 route hits 逐项核验复用已有分项证据。

Requests/DNS/Logs 次要视觉暂停，正式签名和发布单独推进。当前保留一份正式授权 Profile，不沿用旧文档 Room 为空的历史状态；不清数据、不删除配置、不访问撤回源。最新构建、定向真机与文件保护证据见 roadmap 同日配置页节。
