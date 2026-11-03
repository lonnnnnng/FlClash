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

## Profile 扩展与应用输入

- URL/FILE 的标准扩展保存在 typed `ProfileProviderOverrides.extension`：配置独有规则、稳定 ID 的全局规则禁用集合和显式模式标记；模式、脚本 ID、默认目标沿用 Profile 自有字段。全局规则定义保存在 `app_preferences.global_extension_rules`，完整备份使用 v2 并接受旧 v1 归档；仅配置恢复不改全局规则。Room schema 不变。
- 标准模式按本配置附加规则 → 启用的全局规则 → 原始规则合并；附加规则中的 `MATCH` 目标取显式默认目标或原配置最后一个 MATCH。脚本模式只执行绑定脚本，不叠加标准规则。源 YAML 不回写，DNS baseline、显式覆盖、LAN 继续作用于暂存输入。旧绑定未明确配置新模式时继续执行；CUSTOM 既有脚本行为保留。
- `ProfileExtensionController` 用串行队列处理元数据、显式应用和退出自动应用，真实应用只能进入 `CoreRuntimeManager.applyCurrentProfile`，并在启停锁内复核当前 ID；应用失败保留已保存编辑并由管理器恢复旧配置/服务。
- `loadedConfigKey` 表示已交给核心的完整输入，普通配置另有 `loadedAutomaticConfigKey` 比较源文件、非扩展覆盖和 LAN。后台节点刷新不能因扩展元数据/脚本变化提前加载；显式应用、选用或启动走完整加载。LAN 布尔热切只更新旧输入的 LAN 指纹，不把待应用扩展标为已应用；待应用时恢复继承 LAN 会提示先应用扩展，避免重新执行未提交脚本。
- `YamlConfigCodec` 通过安全构造器按调用创建解析器；Reader 在普通整缓冲读取时预留一格，供 SnakeYAML 2.3 在末尾遇到 UTF-16 高代理项时补读低代理项，避免包含 emoji 的节点名在扩展/脚本重序列化后触发越界。字符内容不变，源配置不回写。

## 更多分区编辑与暂存合并

- `ProfileMoreFields` 定义 NTP/Sniffer 字段、Flutter 默认值、裸分区 YAML 编解码及校验；`ProfileMoreEditor` 只维护父草稿/字段子页/快捷 YAML，父页保存统一经 ViewModel 合并最新覆盖，源正文不回写。
- 协议移除最后一项代表继承；显式空 sniff 映射代表清空协议。`ProfileMoreOverrideApplier` 先将旧 sniffing/port-whitelist 搬入，再按忽略大小写的协议名替换；明确清空同时屏蔽旧 sniffing，保留未选协议与未知配置字段。
- 当前父页显式保存、空文本/空列表明确覆盖是 Kotlin 既有契约；Flutter 的逐项持久化/部分空值移除行为仍待收敛，不能把本切片作为完整 parity。

## CUSTOM 规则整页

- `CustomRules` 保留原始规则文本与稳定行标识，解析正则/逻辑规则的逗号 payload、src/no-resolve 和裸行/列表 YAML；`CustomRuleCatalog` 从当前源正文提取策略组、本地代理与规则资源，失效引用可见并可修复。类型选择不提供 SUB-RULE；已存在的旧规则可保留、复制、排序和删除，快捷 YAML 确认会按 Flutter 行为移除 SUB-RULE。
- `CustomRulesEditor` 列表、搜索、批选、复制/删除、预设、排序和表单共用逐操作保存；子页独立保留草稿，回读成功才关闭，写入失败恢复列表顺序。搜索与排序互斥，拖动取消恢复已保存顺序；快捷 YAML 在后台解析。
- `ProfilesViewModel` 用规则页 generation 丢弃关闭后的迟到读取，以同步 saving 状态抑制重复提交；成功刷新父概览及源正文快照并留在列表。`ProfileRepository.saveCustomRules` 复用文件 mutex、正文快照、原子文件替换和 Room 事务，取最新元数据后仅更新规则及时间，过期正文或启停/运行状态变化拒绝写入。配置选择仍由核心管理器串行处理，不使用文件写锁阻止热切。
- 普通分区 YAML 保留其余原文与分区边界说明；flow 根对象或整体缩进按语义替换，避免重复 rules。空规则保存为列表 `rules: []`，不把空值变为 null；全局资源选择映射与长列表边缘拖动仍需后续补齐。

## 新增文件导入

- `ProfilesViewModel.fileImport` 持有独立 controller，Host 只负责 OpenDocument、生命周期和预览/密钥弹窗。8 MiB 有界读取后冻结源字节；界面 StateFlow 只包含文件元数据和操作状态，错钥保留源快照原位重试，取消/后台通过 generation 和 Job 作废迟到结果。系统选择器自身遮挡保留令牌；其他后台清理导入会话和密钥输入。
- `ProfileRepository.importFileProfile` 在解密/YAML 校验之后再进入文件 mutex，NonCancellable 包住原子文件替换与 Room 事务，插入前及文件提交后复核会话/运行守卫，异常回滚新文件与新增行。成功保留已有当前选择；密钥复用既有 Room 字段，不新增 schema。完整 Mihomo 语义预检仍未接入。

## 策略组问题、过滤与图标

- `ProxyGroupIssues` 为列表/表单提供结构错误与可保留的失效引用警告；复制生成新 key 并清除 originalName，避免副本引发旧引用改名。删除提示按名称解析规则目标、成员与链式代理引用，不进行全文替换。
- `NativeProxyGroupFilterValidator` 通过 Bridge 的既有 validateFilters method 使用 Go regexp2。UI 防抖、单组确认与 ViewModel 保存均校验；异步确认冻结 JSON，期间修改后必须重新确认，取消后的协程不继续提交。此操作不启动、加载或改写核心配置。
- `ProfileGroupIcon` 使用 Coil 位图/SVG/网络管线和默认/失败回退；本地图片经 ViewModel 的有界 reader 和 ProfileGroupIconData 签名识别后转为 data URI，128 KiB 上限。最近图标通过 ProfileStore/Repository 的 AppPreference 保存，串行更新并限制 24 条/512 KiB，无 schema 变更。UI 不直接读写数据库或配置文件；图标集管理与推荐尚未接入。

## 全局规则资源与配置引用

- `RuleResourceRepository` 在 `app_preferences.global_rule_resources` 保存有界元数据，原文与 MRS 位于 Mihomo `providers/rules` 私有 UUID 文件。`NativeRuleResourceCompiler` 延迟取得管理器，避免 ProfileRepository→资源→核心管理器→ProfileRepository 构造环；初始化/编译/预览都归 `CoreRuntimeManager`，不启动服务。
- CUSTOM YAML 中 `path: kclash-resource:<id>` 是稳定引用，配置别名独立于全局名称。`toActiveProfile` 解析到当前原文或编译 MRS 的绝对路径，生成哈希命名输入投影；原配置正文不回写。更新资源为新文件，旧文件/投影暂保留供已应用配置读取，回收待补。
- CUSTOM 导入在配置文件锁外下载/编译规则资源，目录锁内复核全局快照；由配置文件替换事务调用资源登记回调，共同提交或回滚。普通/嵌套 RULE-SET 及 DNS/Sniffer 引用按语义改名，源代理凭据与 URL 不作字符串全局替换。`saveCustomProviders` 对正文和覆盖做原子保存，复核旧正文与运行守卫，合并最新元数据。
- 备份v3携带资源元数据、原文、所需MRS，兼容v1/v2；仅配置备份也携带所引用资源，稳定ID使跨设备路径重新解析。全局代理、非CUSTOM Profile资源映射、Geo/图标集/过滤等仍不在此已完成切片内。
