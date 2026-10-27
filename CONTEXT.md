# FlClash Android 重写上下文

本上下文记录 FlClash 从 Flutter 多平台客户端向 Android 原生 Kotlin 主线演进时使用的项目语言。它描述产品边界和协作术语，不承载具体实现方案。

## 项目边界

**Kotlin 主线**：面向 Android 手机的 FlClash 原生实现，是本仓库未来持续开发和发布的主产品。

**Flutter 参考实现**：现有 Flutter 版本，作为 Android 功能、交互和视觉一致性的行为参考，并继续承接上游产品变化。

**上游**：`chen08209/FlClash`，提供 Flutter 参考实现及后续产品变化；上游变化需要经过 Android 适配后进入 Kotlin 主线。

**个人 fork**：`lonnnnnng/FlClash`，承载 Kotlin 主线的远程开发和发布分支。

**Android 手机范围**：Kotlin 主线首发面向 Android 手机的竖屏和横屏体验，包含 Flutter Android 端已有的网络代理和配置管理能力；Android TV、平板、车机和桌面平台不属于首发验收范围。

**Android 原生仓库**：Kotlin 主线的仓库根目录直接承载 Android Gradle 工程；Flutter 代码不作为 Kotlin 主线的运行时依赖，只通过上游分支提供行为参考。

**Mihomo 核心**：负责代理协议、路由、DNS、TUN、配置解析和运行时连接的 Go 引擎；Kotlin 主线复用它的 Android 构建产物，应用层通过 Repository/API 使用核心能力。

**新应用数据边界**：Kotlin 应用使用独立的数据目录和全新数据模型，不读取或转换 Flutter/Drift 数据；后续只维护 Kotlin 自身版本之间的升级迁移。

**Kotlin 数据模型**：Room 管理的新应用本地数据模型，按 Android 产品重新设计；业务层通过 Repository 使用它，页面不直接操作数据库。

**UI 状态**：页面可渲染的不可变状态，由 ViewModel 通过 StateFlow 暴露；页面事件经过单向数据流进入业务层，不直接操作核心、服务或数据库。

**核心运行管理器**：Service 模块中唯一负责 Mihomo 核心生命周期和运行状态的组件；VpnService 只负责 TUN，ProxyService 只负责本地代理模式，两者不各自创建核心实例。

**应用依赖注入**：由 Hilt 管理 Application、Activity、Service 和 ViewModel 的依赖生命周期；Repository、UseCase、核心 Bridge 和数据源通过构造函数注入。

**KClash**：Kotlin Android 应用的显示名称和产品身份，使用独立包名 `com.lonnnnnng.flclash`，与 Flutter `FlClash` 并行存在。

**Android 支持下限**：KClash 支持 Android API 24（Android 7.0）及以上；更高的 target/compile 版本需要在迁移时结合实际工具链和发布要求核对。

**ABI 范围**：KClash 的开发、测试和发布都只构建 `arm64-v8a`；不维护 `armeabi-v7a`、`x86_64` 或其他 ABI。

**验收真机**：当前连接的 ARM64 Android 真机作为 KClash 的权威验收设备；本轮核验为 Redmi Note 8 Pro、Android 16/API 36，实际 serial 每次操作前重新读取。

**KClash 签名身份**：发布阶段创建独立的 release keystore；签名文件和秘密不进入仓库，也不复用 FlClash 的签名材料。

**核心协议**：Kotlin Bridge 与 Go Mihomo 之间沿用现有 method/event 语义和 JSON envelope；上层通过类型安全 Kotlin API 使用它，不在页面中传递原始方法字符串。

**配置（Profile）**：用户可以选择并运行的代理配置，来源分为远程订阅、本地文件和自定义配置；配置名称、更新时间、订阅用量和到期信息属于配置的可见信息。

**当前配置**：当前被用户选中的配置。没有可用配置时为空；删除当前配置后，产品选择剩余列表中的第一项作为新的当前配置。

**已应用配置**：已经通过 Mihomo 文件校验和配置应用流程、并作为当前核心运行输入的配置；切换配置不会在核心运行期间偷偷替换它，用户需要先停止核心。

**核心配置副本**：当前 Profile 写入 Mihomo 专属目录后的 `config.yaml` 文件。它是核心的运行输入，不改变 Profile 原始文件的归属和内容。

**订阅信息**：远程订阅响应提供的已上传、已下载、总额度和到期时间，使用 Unix 时间和字节作为产品内部语言。

**代理组**：Mihomo 当前配置运行后返回的可选择或自动选择的出站集合；展示顺序由核心返回的 `all` 列表决定。

**当前代理**：代理组返回的 `now` 节点名称，是核心实际生效的选择；KClash 不以本地点击结果代替核心确认。

**代理组切换**：只对 `Selector` 组开放的运行时操作，通过核心 `changeProxy` 完成；切换后重新读取代理组状态，失败时保留原状态并提示错误。

**单节点测速**：用户在代理组选择对话框中针对一个节点主动发起的延迟探测；使用该代理组的 `testUrl`，未配置时使用 Mihomo 默认测试地址，结果只保留在当前页面内存中。

**测速结果**：Mihomo `asyncTestDelay` 返回的节点结果；正数表示毫秒延迟，`-1` 表示超时，`-2` 表示测试失败，空结果表示本次没有获得并发测试槽位。

**真实请求诊断**：核心运行期间针对当前远程 Profile 发起的一次受控 HTTP(S) 请求，用于确认实际 HTTP 状态、耗时、规则命中和代理链；诊断只展示归因信息，不把订阅响应正文作为产品状态保存。

**诊断会话证据包**：一次真实请求诊断从开始到完成的短时证据窗口，关联该窗口内的 HTTP 状态、代理链、Requests、DNS 和核心日志；会话完成后可通过系统文件选择器导出脱敏文本，不携带订阅凭据或响应正文。

**运行诊断事件**：核心生命周期、VPN 撤销、底层网络变化和 DNS 更新产生的短文本记录；事件历史最多保留 200 条，诊断会话导出时放入 `[runtime]` 分区，用于解释请求成功、失败或网络恢复过程。

**观测详情**：Requests、DNS 和核心日志详情对话框支持大小写不敏感搜索；日志额外支持全部、错误和警告级别筛选，并可通过系统文件选择器导出 UTF‑8 脱敏日志。列表默认跟随最新事件，用户向上阅读历史后保持滚动位置，筛选条件变化时回到最新匹配项。

**进程重建恢复**：应用进程因系统回收或异常退出后，只要用户此前保持核心运行且 VPN 授权仍有效，KClash 会重新建立核心、前台服务和 TUN；用户主动停止或系统撤销 VPN 会清除恢复意图，不会在下一次进程创建时误启动。

**平台运行入口**：通知栏 STOP、桌面启动/停止/切换快捷方式、Quick Settings Tile 和外部 Action 都通过 `CoreRuntimeManager` 改变核心状态；电池优化引导读取系统真实授权状态，Tasker/MacroDroid 实际应用回归仍待补齐。

**远程脚本**：带有 HTTP(S) 地址的脚本资源；KClash 将下载正文保存到应用私有脚本目录，手动同步使用原子替换并保留上一次可读正文，远程资源管理不代表 JavaScript 已执行。

## 示例对话

开发者：上游新增了一个代理组能力，应该直接合并到 Kotlin 主线吗？

领域负责人：先在 Flutter 参考实现中确认 Android 用户能看到的行为，再把同一行为映射到 Kotlin 主线；桌面专属行为不自动进入 Android 产品。
