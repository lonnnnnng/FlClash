# Testing

## Layers

- Kotlin 单元测试覆盖配置解析、领域模型、Repository、UseCase、状态机和错误映射。
- Room 测试使用内存数据库验证约束、关系、查询和未来迁移；不写 Flutter/Drift 兼容测试。
- Core Bridge 测试验证请求/响应/事件编解码、线程边界、重复关闭和 Go 错误映射。
- Compose UI 测试覆盖导航、状态渲染、配置编辑、代理组选择和错误反馈。
- Instrumented tests 验证 Manifest、前台服务、VPN 权限、Deep Link、Quick Settings 和按应用代理入口。

## Device acceptance

构建通过不等于产品完成。Android 手机验收至少覆盖首次启动、Profile 导入/订阅、启动 VPN、停止/重启、代理组切换、连接/请求/DNS/日志页面、后台恢复、通知和 Deep Link。每次设备操作记录 serial、APK 版本、步骤、结果和关键日志。

当前权威设备是已连接的 ARM64 Redmi Note 8 Pro（本轮核验 Android 16/API 36）。serial 不写死在脚本或配置中，每次操作前用 `adb devices -l` 和 `getprop` 重新确认。

## Failure handling

真实设备动作失败时保留失败 Run 和审计事实，先检查 logcat、服务状态、权限和核心事件，再决定修复或重试；不能用历史成功覆盖当前失败。

## 第一阶段实测记录

- `:common:testDebugUnitTest`：30 项通过。
- `:service:testDebugUnitTest`：26 项通过。
- `:app:testDebugUnitTest`：第一阶段骨架时为 `NO-SOURCE`，Profiles 切片后新增 4 项单元测试并通过。
- `:app:assembleDebug`：成功，APK 只包含 `arm64-v8a` 的 `libclash.so`。
- 真机 `wsvwypiz7xwslvl7`：启动 Activity、启动 ProxyService 前台通知、停止 ProxyService 均已验证；启动期和停止期没有 `FATAL EXCEPTION`。

## Profiles 切片实测记录

- `:app:testDebugUnitTest`：4 项 Profile/订阅信息单元测试通过。
- `:app:assembleDebug`：成功，安装包仍只包含 `arm64-v8a` 原生库。
- 真机 `wsvwypiz7xwslvl7`：创建自定义配置后重启应用，配置仍显示且保持选中；核心启动/停止链路仍可用，前台服务 `startForegroundCount=1`、`isForeground=true`，未发现 `FATAL EXCEPTION`。
- 真机 `wsvwypiz7xwslvl7`：使用公开测试订阅地址完成 HTTP 导入，列表显示远程订阅；重复导入收到“该订阅已经添加”，随后删除测试订阅并恢复空状态。

## 代理组与单节点测速切片实测记录

- `./gradlew test :app:assembleDebug --no-daemon`：通过；当前 Debug 单元测试共 71 项，失败 0，错误 0，跳过 0。
- APK 只包含 `arm64-v8a` 的 `libclash.so`；当前 Debug APK 路径为 `android/app/build/outputs/apk/debug/app-debug.apk`。
- 真机 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36）：核心启动后读取真实 `GLOBAL`、`Atom`、`Auto` 代理组；Selector 节点切换 `DIRECT → FR-1-01` 成功。
- 真机单节点测速：`DIRECT` 返回真实延迟 `160 ms`；不可用节点进入“失败”状态；测试后核心正常停止，未发现 `FATAL EXCEPTION`。

## 运行统计切片实测记录

- `./gradlew test :app:assembleDebug --no-daemon`：通过；当前 Debug 单元测试共 75 项，失败 0，错误 0，跳过 0。
- Debug APK 安装到 `wsvwypiz7xwslvl7` 后，启动核心可显示“运行统计”卡片，包含上传、下载、连接和累计流量字段；停止核心后卡片恢复停止态提示。
- 真机停止后 `tun0`、`198.18.0.0/30` 路由以及 KClash `VpnService`/`ProxyService` 均已清理。
- 通过设备 shell/浏览器发起的请求返回 HTTP 301，但尚未在应用内得到非零连接和流量归因证据；Connections/Requests/DNS/Logs 仍未完成。

## 活动连接切片实测记录

- `./gradlew test :app:assembleDebug --no-daemon`：通过；当前 Debug 单元测试共 76 项，失败 0，错误 0，跳过 0。
- Debug APK 安装到 `wsvwypiz7xwslvl7` 后，核心运行态显示“活动连接（0）”空态；停止核心后恢复“启动核心后显示当前活动连接”。
- Bridge 已完成 Mihomo `getConnections` 响应模型映射；本轮设备请求未产生可归因的活动连接，连接明细的非空真机证据仍待后续请求闭环。

## DNS 与核心日志观测切片实测记录

- `./gradlew test :app:assembleDebug --no-daemon`：通过；当前 Debug 单元测试共 91 项，失败 0，错误 0，跳过 0。
- 二维码导入切片新增直接 HTTP(S) 链接和四种 KClash Deep Link 的统一解析测试；Redmi 真机已分别导入两条授权路径的二维码，第二条 HTTP 301→HTTPS 重定向已通过本地回归测试和真机复验。
- 真机重复导入第二条二维码显示“该订阅已经添加”；验收后删除两个测试 Profile，设备恢复“还没有配置”空态。
- 新增 Core Bridge DNS 事件字段解析测试，以及 MainViewModel 的 DNS/日志有界历史测试；DNS 和日志历史分别限制为最近 1000 条，并支持用户主动清空。
- `CoreRuntimeManager` 已把 Go Mihomo `startLog/stopLog` 纳入启动、停止和失败回滚；真机启动核心后 DNS 卡片出现 4 条查询、日志卡片出现 3 条日志，并显示“查看全部/清空”。
- Debug APK 安装到 `wsvwypiz7xwslvl7` 后，停止态首页实际显示“DNS 查询”和“核心日志”卡片；两张卡片均显示启动核心提示，应用启动未出现 `FATAL EXCEPTION`。
- 本轮只使用当前工作区根目录 `AGENTS.md` 的两条授权路径；设备请求仍返回 HTTP `000` 超时，未形成非空 Requests 事件或请求归因证据。

## Deep Link 导入切片实测记录

- `:app:testDebugUnitTest`：通过，新增 Deep Link 解析测试覆盖 URL 解码、`clash`/`clashmeta`/`flclash`/`kclash` Scheme、非法 Host、非 HTTP(S) 目标和缺失参数。
- `:app:assembleDebug`：成功，APK 只包含 `arm64-v8a` 的 `libclash.so`。
- 真机 `wsvwypiz7xwslvl7`：通过 `kclash://install-config` 触发第一条当前授权订阅路径导入，UI 显示远程订阅和订阅用量；验收后删除该测试 Profile，设备恢复空配置状态。
- 无效 Deep Link 真机提示“配置 Deep Link 无效”，未创建 Profile，未发现 `FATAL EXCEPTION`。
- 强制停止应用后通过同一 `kclash://install-config` 冷启动导入成功；再次发送同一链接显示“该订阅已经添加”，未产生重复 Profile；验收后删除测试 Profile，恢复空配置状态。

## 真实请求诊断切片实测记录

- `./gradlew test :app:assembleDebug --no-daemon`：通过；当前 Debug 单元测试共 98 项，失败 0，错误 0，跳过 0。
- 新增 `probe` Bridge 和 `CoreRuntimeManager` 诊断入口，返回 HTTP 状态、耗时、最终 URL、代理链、规则和核心路由版本；诊断不读取订阅响应正文，避免配置内容进入 UI 或日志。
- 首页新增“真实请求诊断”卡片，诊断目标只取当前选中的远程 Profile；自定义 Profile 不提供诊断目标。
- 真机 `wsvwypiz7xwslvl7`：使用当前 `AGENTS.md` 授权清单中的第一条路径，启动 TUN 后诊断返回真实 HTTP 403 和 1.6 秒延迟；UI 显示完整代理链和 `Match` 规则，请求记录达到 10 条，累计流量出现非零值，随后停止清理 `tun0`、VPN 路由和前台服务。
- 同一真机使用第二条授权路径冷启动导入并诊断成功，返回 HTTP 200 和 7.4 秒延迟；UI 显示 `proxy_1 → 自动选择 → 主节点`，运行期间捕获活动连接 1 条、请求记录 17 条、DNS 查询 26 条、核心日志 31 条，累计流量达到非零值。
- 第二条路径停止后确认 `198.18.0.0/30` 路由、`tun0`、`VpnService` 和 `ProxyService` 均已清理；两个测试 Profile 均通过应用内删除，设备恢复“还没有配置”空态。

## 系统撤销 VPN 生命周期切片实测记录

- 新增 `CoreRuntimeManager.handleVpnRevoked()`、应用内签名广播接收器和撤销/重复撤销/撤销后重启单元测试；服务状态收口仍由 Runtime Manager 统一负责。
- `./gradlew test :app:assembleDebug --no-daemon`：通过；98 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Debug APK 只包含 `arm64-v8a`。
- 真机 `wsvwypiz7xwslvl7` 使用当前第二条授权路径启动成功；系统设置页显示 `KClash / 已连接`，`VpnService`、`ProxyService` 均为前台服务，存在 `tun0` 和 `198.18.0.0/30` 路由。
- 在系统设置页点击 `KClash / 断开连接` 后，首页显示“核心启动失败”；`VpnService`、`ProxyService`、`tun0`、`198.18.0.0/30` 路由和 KClash VPN 网络均已消失。
- 在失败态再次点击“启动核心”成功恢复运行；随后停止并删除测试 Profile，设备恢复“还没有配置”空态。

## 后台恢复生命周期切片实测记录

- 真机 `wsvwypiz7xwslvl7` 使用当前第一条授权路径启动成功后，通过 Home 键进入后台并等待 10 秒；期间 `VpnService`、`ProxyService` 均保持前台服务，`tun0` 和 `198.18.0.0/30` 路由持续存在。
- 重新打开 KClash 后首页仍显示“核心运行中”，运行统计和 DNS 卡片恢复正常，DNS 事件继续到达；随后停止并删除测试 Profile，设备恢复空配置状态。

## Wi‑Fi 中断恢复切片实测记录

- 真机 `wsvwypiz7xwslvl7`（Android 16/API 36）使用当前第一条授权路径启动核心后执行 `svc wifi disable`，等待 7 秒确认底层 Wi‑Fi 网络消失；VPN `UnderlyingNetworks` 变为空，但 `VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由仍保持，应用进程未退出。
- 执行 `svc wifi enable` 后，Wi‑Fi 接口恢复并绑定新的网络句柄；VPN、两个前台服务和 TUN 路由保持，Mihomo 日志出现 DNS 更新，设备内通过 TUN 的 `ping 1.1.1.1` 成功。
- 本轮蜂窝数据未注册且不可用，因此只记录 Wi‑Fi 断开恢复证据，不将 Wi‑Fi↔移动网络切换标记为完成；测试 Profile 已通过应用内删除，设备回到“还没有配置”空态。

## 进程重建生命周期切片实测记录

- `./gradlew test :app:assembleDebug --no-daemon`：通过；工程 Kotlin 单元测试共 98 项，失败 0、错误 0、跳过 0；APK 内只包含 `arm64-v8a` 的 `libclash.so`。
- 真机 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36）使用第二条当前授权路径启动核心，记录旧进程 PID `16207`；`VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由均已存在，首页显示“核心运行中”。
- 通过 `adb shell cmd activity crash com.lonnnnnng.flclash` 触发应用进程退出；4 秒后出现新 PID `16637`。20 秒后两个前台服务、`tun0` 和 `198.18.0.0/30` 路由仍存在，重新打开 KClash 首页仍显示“核心运行中”。
- 代码通过 Room `RuntimeResumeStore` 保存运行意图；`KClashApplication` 在授权有效时复用 `CoreRuntimeManager.startProxy()` 恢复核心；服务收到系统重建的 `null Intent` 时不抢跑，避免进程内 `VpnOptions` 尚未恢复的竞态。
- 停止核心后服务、TUN 和路由均清理；测试 Profile 通过应用内删除，设备恢复“还没有配置”空态。`cmd activity crash` 产生的强制崩溃仅用于验收触发，不作为应用自身异常证据。

## Android 平台入口切片实测记录

- `./gradlew test :app:assembleDebug --no-daemon`：通过；工程 Kotlin 单元测试 98 项，失败 0、错误 0、跳过 0；APK 仅包含 `arm64-v8a` 的 `libclash.so`。
- `./gradlew :app:lintDebug --no-daemon`：通过；错误 0，保留 24 条非阻断警告供后续 UI/发布治理处理。
- Android 13+ 通知权限已在 `MainActivity` 进入时申请；前台通知显示 STOP 操作，通知动作和桌面快捷入口均通过受保护的 `QuickActionActivity` 复用 `CoreRuntimeManager`。
- 安装 Debug APK 后，`adb -s wsvwypiz7xwslvl7 shell cmd shortcut get-shortcuts --user 0 --flags 15 com.lonnnnnng.flclash` 返回 `start`、`stop`、`toggle` 三个静态快捷方式；启动器长按 KClash 图标显示三个对应中文入口。
- 真机 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36）从启动器点击“启动 KClash 核心”后，`VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由出现；点击“停止 KClash 核心”后全部清理。
- 从启动器两次点击“切换 KClash 核心运行状态”，分别验证启动和停止；停止后 `dumpsys activity services` 无 KClash 服务，`ip route` 无 `tun0` 路由。
- 通过 `adb -s wsvwypiz7xwslvl7 shell cmd statusbar add-tile com.lonnnnnng.flclash/.KClashTileService` 将 Tile 加入系统快捷设置；点击 Tile 启动核心后，系统面板无障碍描述为“`KClash：核心运行中`”。再次点击 Tile 停止核心后，面板描述为“`KClash：核心已停止`”。
- Tile 组件由 `android.permission.BIND_QUICK_SETTINGS_TILE` 保护；`dumpsys package` 显示 `android.service.quicksettings.action.QS_TILE` resolver 指向 `.KClashTileService`，没有出现 `SecurityException` 或 `FATAL EXCEPTION`。
- 通过 `adb -s wsvwypiz7xwslvl7 shell am start -a` 分别发送 `com.lonnnnnng.flclash.action.START`、`STOP`、`TOGGLE` 验证外部 Action：`START` 建立两个前台服务、`tun0` 和 `198.18.0.0/30` 路由，`STOP` 清理，`TOGGLE` 可往返切换。
- 外部入口 `ExternalActionActivity` 只接受固定 action；使用 `--es unexpected value` 发送额外参数时直接结束，服务和路由保持停止；验收后删除测试 Profile，首页恢复“还没有配置”。
- 首页“后台运行”卡片显示“建议关闭电池优化”；点击“打开电池优化设置”后，真机进入 `com.android.settings/.fuelgauge.RequestIgnoreBatteryOptimizations`，系统对话框显示 KClash 和“允许”按钮。按返回取消后回到首页，状态仍为未忽略。
- 验收 Profile 已通过应用内删除，首页显示“还没有配置”；未发现 `FATAL EXCEPTION`。

## 诊断会话证据包切片实测记录

- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；工程共 105 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Debug APK 只包含 `arm64-v8a`。
- 真机 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36）使用当前工作区根目录 `AGENTS.md` 第一条授权路径冷启动导入远程 Profile，启动后 `VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由均出现。
- 点击“测试当前路由”后首页显示“诊断会话：通过 · 请求 11 · DNS 6 · 日志 12”，诊断结果为 HTTP 403，断言显示“HTTP 状态、代理链和核心归因均有效”。
- 通过 Android `DocumentsUI` 保存 `kclash-diagnostic-1.txt`；设备侧检查到约 3 KB、41 行，包含诊断表头及 `[requests]`、`[dns]`、`[logs]` 三个分区，存在脱敏标记且未命中测试秘密值。
- 使用 STOP Action 停止后确认前台服务、`tun0` 和 `198.18.0.0/30` 路由清理；应用内删除测试 Profile 后首页恢复“还没有配置”，未发现 `FATAL EXCEPTION`。

## 观测详情交互切片实测记录

- 新增 Requests、DNS 和核心日志详情筛选函数测试 3 项，以及日志导出脱敏测试 1 项；`./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --no-daemon` 通过，工程总计 102 项 Kotlin 单元测试通过。
- Requests 详情支持按目标、进程、代理链和规则搜索；DNS 详情支持按域名、上游、答案和错误搜索；核心日志详情支持内容搜索及“全部/错误/警告”级别筛选。
- 三类详情列表默认跟随最新事件；用户滚动查看历史后保持当前位置，筛选条件变化时回到最新匹配项。
- 核心日志详情和首页卡片提供系统文件导出；导出使用 UTF‑8 表格文本，URL 查询参数、JSON 凭据字段和常见 token/key/secret/password/url 值统一替换为 `[REDACTED_SECRET]`。
- 真机 `wsvwypiz7xwslvl7` 安装 Debug APK 后，使用当前授权清单第一条路径启动核心，首页收到活动连接 1 条、请求 4 条、DNS 查询 13 条和核心日志 8 条；三类详情对话框均显示搜索框，日志切换“错误”后显示 `0/8` 和“没有匹配的核心日志”。
- 真机通过 Android `DocumentsUI` 保存 `kclash-logs-*.txt` 到 `Download/kclash-qr`；文件大小 1634 bytes、17 行，前两行为导出标题和列名，设备侧检查到 61 个脱敏标记。
- 使用 `adb -s wsvwypiz7xwslvl7 shell am start -a com.lonnnnnng.flclash.action.STOP` 停止后，`VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由均清理；测试 Profile 已删除，首页恢复“还没有配置”，未发现 `FATAL EXCEPTION`。

## 运行诊断事件记录切片实测记录

- 新增固定上限 200 条的运行诊断历史，覆盖核心启动/重启/停止/失败、VPN 撤销、网络可用/丢失/切换和 DNS 变化；诊断证据包新增 `[runtime]` 分区。
- 新增 4 项单元测试；`./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过，工程总计 109 项 Kotlin 单元测试失败 0、错误 0、跳过 0。
- 真机 `wsvwypiz7xwslvl7` 使用第二条当前授权路径启动后 probe 成功，HTTP 200，UI 显示运行事件 3 条、Requests 2 条、DNS 8 条、Logs 2 条。
- 核心运行期间执行 Wi‑Fi 关闭 8 秒、重新开启并等待网络恢复；`VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由保持，恢复后 probe 仍成功，UI 显示运行事件 8 条。
- 通过 `DocumentsUI` 保存的最新诊断文件约 2 KB、33 行，包含 `CORE_STARTED`、`DNS_UPDATED`、`NETWORK_LOST`、`NETWORK_AVAILABLE`、`NETWORK_SWITCHED` 及 `[requests]`/`[dns]`/`[logs]` 分区，未命中测试秘密值。
- 第一条路径在 Wi‑Fi 恢复后的两次 probe 返回 EOF，应用正确显示失败态并保留失败证据；随后使用第二条授权路径完成成功复验。两个测试 Profile 已删除，设备恢复空配置状态。

## 事件流背压与长期运行治理切片实测记录

- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；工程共 113 项 Kotlin 单元测试失败 0、错误 0、跳过 0。
- `CoreBridge` 事件流使用固定 256 条缓冲、非阻塞 `tryEmit`，单批解析上限为 512 条；单元测试覆盖批次截断、无订阅者丢弃、活动订阅者成功投递和诊断导出统计。
- 最终 Debug APK 安装到 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36），只包含 `arm64-v8a`，SHA-256 为 `a5fa9f91db26b3fd5a7e1c1e930f17774f5da19939b1d6b6db297a4f51870ab4`。
- 使用第二条当前授权路径启动后首次 probe 出现一次真实网络失败；停止并重启核心后重试返回 HTTP 200，UI 显示“诊断会话：通过 · 运行 7 · 请求 2 · DNS 6 · 日志 2”。最终导出文件 `[event_flow]` 记录 `parsed_batches=14`、`parsed_events=17`、`emitted_events=17`、`dropped_events=0`、`truncated_events=0`。
- 核心持续运行 40 秒后 PID `26899`、两个前台服务、`tun0` 和 `198.18.0.0/30` 路由保持，未发现应用 `FATAL EXCEPTION`。停止后服务和路由清理，测试 Profile 已删除并恢复“还没有配置”。
- 本轮尚未覆盖真实高负载事件压力、页面退出后的轮询生命周期和跨进程诊断历史关联。
