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
