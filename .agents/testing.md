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

## Provider 字段化覆盖切片实测记录（2026-10-27）

- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 150 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；24 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 4 项、Compose 20 项。
- 服务层测试确认 Provider 覆盖 JSON 往返、`health-check`/`override` 局部合并和未知字段保留；`CoreRuntimeManager` 测试确认启动暂存配置包含字段化覆盖。
- Compose instrumentation 确认 Provider 列表可打开字段化对话框，填写 interval/timeout 后先保存子表单，再由父 Provider 编辑器回传覆盖 map。
- Debug APK SHA-256：`d7204a8f51af8eddb376d7152a0bfed0dcafb65c2497d4ecea8c4fee4fffb0fd`；设备为 Redmi Note 8 Pro（Android 16/API 36），APK 仅包含 `arm64-v8a` 原生库。
- 本轮未访问当前工作区 `AGENTS.md` 列出的两条真实订阅路径、VPN 或核心启动。

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

## 真实高负载事件流压力验收记录

- 真机 `wsvwypiz7xwslvl7` 使用当前工作区根目录 `AGENTS.md` 第一条授权路径启动核心；压力请求通过 Mihomo 本地 mixed port `127.0.0.1:7890` 发出，确保请求进入核心事件管线。
- 先执行 1 轮、再追加 3 轮，每轮 64 个并发 HTTP 请求，共 256 个压力请求；首页首轮后显示请求记录 74 条、DNS 查询 133 条，累计流量约为上行 198.2 KB、下行 4.2 MB。
- 最新诊断证据包 `[event_flow]` 为 `parsed_batches=79`、`parsed_events=1525`、`emitted_events=1525`、`dropped_events=0`、`truncated_events=0`，高负载下未发生事件丢弃或截断。
- 压力期间进程、两个前台服务、`tun0` 和 `198.18.0.0/30` 路由均保持，未发现 KClash `FATAL EXCEPTION`；外部 STOP 后服务和 TUN 清理，Profile 删除后恢复空配置。

## 诊断卡片事件流健康摘要切片实测记录

- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；工程共 120 项 Kotlin 单元测试失败 0、错误 0、跳过 0。
- 诊断卡片新增事件流健康摘要，正常状态显示解析、投递、丢弃和截断数量；丢弃或截断大于 0 时显示证据可能不完整的错误提示。
- Debug APK 安装到真机 `wsvwypiz7xwslvl7` 后，使用当前工作区根目录 `AGENTS.md` 第一条授权路径启动核心，并通过 Mihomo 本地 mixed port 产生 32 个并发请求；首页显示 `事件流：解析 170 · 投递 170 · 丢弃 0 · 截断 0`，未出现不完整提示。
- STOP 后 `VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由均清理，测试 Profile 删除后恢复空配置。

## 诊断卡片事件流摘要 Compose instrumentation 验收记录

- `DiagnosticEventFlowSummaryInstrumentedTest` 新增 2 项 Compose UI 断言：健康统计显示完整四项计数且不显示不完整警告；丢弃/截断统计显示不完整证据警告。
- AndroidTest 运行时修复了协程 ServiceLoader 边界：Debug 应用合并 Android 与测试协程服务注册，AndroidTest 补充 Android 协程运行时；测试依赖只进入 Debug APK，Release 不携带该测试工件。
- Android 16/API 36 的 Espresso 旧版会反射 `InputManager.getInstance()` 并失败；AndroidTest 使用 `androidx.test.espresso:espresso-core:3.7.0`，Compose 测试迁移到 `androidx.compose.ui.test.junit4.v2.createComposeRule`。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；120 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；5 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 2 项。
- Debug APK `android/app/build/outputs/apk/debug/app-debug.apk` SHA-256 为 `b30f2e8bb91605c8a87b2294c4c98e2f741c2ebab65a28dc12aa62b54a90cb44`，设备为 Redmi Note 8 Pro（Android 16/API 36），APK 仅包含 `arm64-v8a` 原生库。

## 稳定诊断故障归因验收记录

- 新增稳定 `DiagnosticFindingCode`，覆盖 `HTTP_RESPONSE`、`CORE_ERROR`、`NO_RESULT`、`NO_HTTP_STATUS`、`NO_ROUTE` 和 `EVENT_EVIDENCE_LOSS`；核心错误优先于 HTTP 状态，具备代理链时 HTTP 403/200 均保留为可归因的 HTTP 响应。
- 首页诊断卡片新增归因标题、下一步建议和可展开的脱敏原始详情；诊断导出新增开始/结束时间、归因码、标题、建议和脱敏详情。解析事件为 0 但存在丢弃/截断时，仍显示事件证据不完整警告。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；121 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；7 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 4 项。
- 真机设备：Redmi Note 8 Pro，serial `wsvwypiz7xwslvl7`，Android 16/API 36，ABI `arm64-v8a`。本轮仅使用当前工作区根目录 `AGENTS.md` 列出的两条授权路径，不在记录中重复真实 URL 或 Token。
- 第一条授权路径真实返回 HTTP 403；UI 显示 `HTTP_RESPONSE`，并说明 403 是目标服务业务状态，不代表传输链路失败；事件流解析/投递为 `64/64`，丢弃/截断为 `0/0`。
- 第二条授权路径真实返回 HTTP 200；UI 显示 `HTTP_RESPONSE`，当前代理链可以归因；事件流解析/投递为 `50/50`，丢弃/截断为 `0/0`。
- 两个测试 Profile 均通过应用内删除，首页恢复“还没有配置”；`VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由均清理，未发现 `FATAL EXCEPTION`。
- Debug APK SHA-256：`c60e7ac24dd9a34d8275998bb6c95486146f95d7bad50774fe389f5d76704561`；APK 仅包含 `lib/arm64-v8a/libclash.so`。

## 运行期间 Profile 变更保护验收记录

- `ProfilesViewModel.delete` 增加运行态守卫；核心运行期间拒绝删除配置，错误事件为“核心运行中，请先停止核心后再修改配置”。
- Compose Profile 卡片和删除确认按钮使用同一 `canMutateProfiles` 条件；核心运行期间编辑、同步和删除入口直接禁用，ViewModel 层仍保留最终守卫。
- 新增 `ProfileTest` 运行态边界断言，覆盖停止、启动中、运行中和失败状态。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；122 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；7 项 instrumentation 测试失败 0、错误 0、跳过 0。
- 本轮未执行真实订阅路径；下一条页面级 E2E 将使用无网络自定义 Profile 流程验证创建、编辑、删除确认及运行期间禁用边界。

## Profiles 组件级 Compose 回归验收记录

- `ProfileUiInstrumentedTest` 新增 3 项 Compose 断言：自定义配置模式切换并触发保存回调、编辑对话框显示配置名称并保存初始 YAML、核心运行期间编辑和删除按钮均不可用。
- 为 AndroidTest 暴露 `ProfileCard`、`ProfileEditorDialog` 和 `AddProfileDialog` 的内部 Compose 入口；真实业务状态仍由 `ProfilesViewModel` 和 `ProfileRepository` 提供，测试不访问网络或真实订阅路径。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；122 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；10 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 7 项。
- Debug APK SHA-256：`6c49a2fda426abead03c433e6f3c2d09e3c3bdba5c2bf31de938f497e82ef485`；APK 仅包含 `lib/arm64-v8a/libclash.so`。
- 下一步建立隔离的 Repository/ViewModel 测试宿主，覆盖自定义 Profile 创建、列表刷新、编辑保存和删除确认的真实状态流。

## Profiles Repository/ViewModel 隔离测试验收记录

- 新增 `ProfileStore` 接口，生产 `ProfileRepository` 继续实现 Room、文件和订阅操作；新增 `CoreRuntimeStatusProvider` 后，`ProfilesViewModel` 可以在测试中使用 fake 数据源和 fake 运行态。
- 新增 2 项单元测试：核心运行中删除 Profile 时只发送阻止错误且 Repository 删除次数为 0；核心停止后删除操作委托到 Repository。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon`：通过；124 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；10 项 instrumentation 测试失败 0、错误 0、跳过 0。
- Debug APK SHA-256：`f643a0893d3c56cab68ac045e167f3def0422ad71c3a8273ab69742090e354b4`；APK 仅包含 `lib/arm64-v8a/libclash.so`。
- 当前仍未声称完整页面 E2E 已完成；下一步把 fake Store 接入页面宿主，验证列表、编辑器和删除确认之间的组合状态流。

## Profiles 页面宿主组合状态流验收记录

- 新增 `ProfilesPanel` Compose 宿主，统一承载 Profiles 空态、添加配置弹窗、列表、删除确认和编辑器状态；真实文件/二维码选择器仍由 `KClashApp` 负责注入。
- `ProfilesPanelInstrumentedTest` 新增 4 项 Compose 断言：自定义配置创建后列表出现、编辑保存回传 YAML 并关闭编辑器、删除确认支持取消和确认、核心运行期间编辑/删除入口不可用。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；124 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；14 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 11 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。本轮未访问真实订阅路径。
- Debug APK SHA-256：`7a06c6032100a848c2aefe79fecaad909ee6744cd1f94fb843b19be42680854a`；APK 仅包含 `lib/arm64-v8a` 原生库。
- 该切片只验证可替换状态驱动的页面组合，不等同于真实 Repository/ViewModel 页面 E2E；下一步接入 fake Store 驱动的真实 `ProfilesViewModel` 宿主。

## ProfilesViewModel 页面状态流验收记录

- 新增 `ProfilesViewModelPanelInstrumentedTest`，使用真实 `ProfilesViewModel`，仅替换 `ProfileStore` 和 `CoreRuntimeStatusProvider`，不启动 Room、网络订阅或 VPN 核心。
- 真机 Compose 流程通过：创建自定义 Profile、等待 StateFlow 列表刷新、打开编辑器并异步读取 YAML、保存 `mixed-port: 7890`、确认删除并回到空态；fake Store 同时确认保存内容、Profile 列表和当前 Profile 选择均正确收敛。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；124 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；15 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 12 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。本轮未访问真实订阅路径。
- Debug APK SHA-256：`7a06c6032100a848c2aefe79fecaad909ee6744cd1f94fb843b19be42680854a`；APK 仅包含 `lib/arm64-v8a` 原生库。
- 该切片验证的是真实 ViewModel 加可替换 Store 的页面状态流；真实 `ProfileRepository`/Room 页面 E2E 和主页面导航仍待后续补齐。

## ProfilesRepository/Room 页面状态流验收记录

- 新增 `ProfilesRepositoryPanelInstrumentedTest`，使用真实 `ProfileRepository`、内存 `KClashDatabase` 和应用文件目录；订阅客户端只提供未调用的失败桩，本轮不访问网络或真实订阅路径。
- 真机 Compose 流程通过：创建自定义 Profile 后 Room 记录和当前选择出现；编辑器输入 `mixed-port: 7890` 后真实配置文件读回相同内容；删除确认后 Room 记录、当前选择和 Profile 文件均清理，页面回到“还没有配置”。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；124 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；16 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 13 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。本轮未访问真实订阅路径。
- Debug APK SHA-256：`7a06c6032100a848c2aefe79fecaad909ee6744cd1f94fb843b19be42680854a`；APK 仅包含 `lib/arm64-v8a` 原生库。
- Profiles 真实 Repository/Room 状态流已覆盖；Android 主页面导航和 Activity 宿主仍待后续补齐。

## MainActivity 宿主入口验收记录

- 新增 `MainActivityInstrumentedTest`，从真实 `MainActivity` 进入首页，验证 Hilt、ViewModel、Material 主题和 Compose 根页面已连接到可交互宿主。
- 测试通过 `GrantPermissionRule` 授予 `POST_NOTIFICATIONS`，避免 Android 13+ 首次权限对话框遮挡页面断言；不触发订阅网络、VPN 授权或核心启动。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；124 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；17 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 14 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。本轮未访问真实订阅路径。
- Debug APK SHA-256：`7a06c6032100a848c2aefe79fecaad909ee6744cd1f94fb843b19be42680854a`；APK 仅包含 `lib/arm64-v8a/libclash.so`。
- 主页面 Activity 宿主已具备自动化入口；下一步补完整导航页面级断言，再进入 N4 高级配置分区编辑。

## 首页与配置页导航验收记录

- 新增轻量 `MainDestination` 状态和 Material 3 `NavigationBar`，首页继续承载运行观测卡片，配置页承载 `ProfilesPanel`；不引入第二套核心生命周期或数据源。
- 悬浮“添加配置”会先切换到配置页再打开弹窗，离开配置页时关闭添加弹窗，避免页面切换后保留跨页弹窗。
- `MainActivityInstrumentedTest` 新增真实宿主往返断言：`page_dashboard` → 点击“配置” → `page_profiles` → 点击“首页” → `page_dashboard`，未触发真实订阅、VPN 或核心启动。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon`：通过；124 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；18 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 15 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256：`b63bb174641fd1cc5f4d9bf034090362f58be66db85cdade15f029832d0895ba`；APK 仅包含 `lib/arm64-v8a/libclash.so`。
- 该切片完成首页/配置两目的地；后续切片继续扩展 Connections、Proxy、Requests、DNS 和 Logs 独立页面。

## 代理与连接独立页面验收记录

- `MainDestination` 新增“代理”和“连接”两个目的地；代理组卡片迁移到代理页，活动连接卡片迁移到连接页，首页运行观测和其他卡片保持不变。
- `MainActivityInstrumentedTest` 覆盖真实宿主：首页 → 配置 → 首页 → 代理 → 连接 → 首页，并断言代理组、活动连接和首页核心状态可见；本轮未访问真实订阅、VPN 或核心启动。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon`：通过；124 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；18 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 15 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256：`ea2d5d51753a1ca78b2bed38da70e892491d6703dcbae7b0e7f5e62d51444b6c`；APK 仅包含 `lib/arm64-v8a/libclash.so`。
- 当前已完成首页、配置、代理、连接、观测五个目的地；观测页包含 Requests、DNS、Logs 三个页签，完整页面级交互仍待扩展。

## 主页面导航与观测页签验收记录

- `MainActivityInstrumentedTest` 从真实 Activity 覆盖首页 → 配置 → 首页 → 代理 → 连接 → 首页 → 观测 → DNS → 日志 → 首页，并断言五个顶层目的地和观测页三个页签的页面标记与标题可见。
- 观测页复用现有 `MainViewModel` 的 Requests、DNS、Logs `StateFlow`、筛选详情对话框、清空和日志导出回调；首页不再重复渲染三张观测卡片。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon`：通过；124 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32（另有 2 条 Hint）。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；18 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 15 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `831e4a42211ac80d58195a1cc85461ef2147b554d7530a54ae114bda87e27247`，APK 仅包含 `lib/arm64-v8a/libclash.so`。
- 本轮未访问真实订阅路径、VPN 或核心启动；真机验收仍只允许当前工作区根目录 `AGENTS.md` 中的两条授权路径。

## 自定义 Profile Rules 分区编辑验收记录

- 自定义 Profile 卡片新增“高级”入口；规则编辑器按每行一条 Clash 规则编辑，只回写顶层 `rules` 区块，保留端口、DNS 和其他未编辑 YAML 字段；远程订阅 Profile 不开放该入口。
- `ProfileRulesEditor` 覆盖规则区块提取、无区块追加、原始 YAML 其余字段保留、规则类型/动作校验和 `SUB-RULE` 拒绝；ViewModel 在核心运行期间继续禁止打开和保存规则。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 129 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；19 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 16 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `f7398958c0e01e6a6e3d1ee736cdc2e0928c77e85b6b5303dd2de328a0ae628c`，APK 仅包含 `lib/arm64-v8a/libclash.so`。
- 真机流程使用真实 Room、应用文件目录和 `ProfilesViewModel`，创建自定义 Profile 后编辑 `DOMAIN,example.com,DIRECT`，确认文件写回 `rules:` 区块；本轮未访问真实订阅路径、VPN 或核心启动。

## 自定义 Profile Proxies 分区编辑验收记录

- 自定义 Profile 卡片新增“代理”入口；代理编辑器按 YAML 列表编辑 `proxies`，只回写顶层 `proxies` 区块，保留端口、规则、DNS 和其他未编辑字段；远程订阅 Profile 不开放该入口。
- `ProfileProxiesEditor` 覆盖现有区块提取、无区块追加、空列表、`proxies:` 包装粘贴、`name`/`type` 必填、重复名称和非列表结构校验；ViewModel 在核心运行期间继续禁止打开和保存代理。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 134 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；20 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 17 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `194a18d914f28ba340a3feb3a37dbfcc970ad34a06c63279c406173f0e7a8f74`，APK 仅包含 `lib/arm64-v8a/libclash.so`。
- 真机流程使用真实 Room、应用文件目录和 `ProfilesViewModel`，创建自定义 Profile 后依次写回 `rules:` 和 `proxies:` 区块；本轮未访问真实订阅路径、VPN 或核心启动。

## 自定义 Profile Proxy Groups 分区编辑验收记录

- 自定义 Profile 卡片新增“代理组”入口；代理组编辑器只回写顶层 `proxy-groups` 区块，保留端口、代理、规则、DNS 和其他未编辑字段；远程订阅 Profile 不开放该入口。
- `ProfileProxyGroupsEditor` 覆盖现有区块提取、无区块追加、空列表、`proxy-groups:` 包装粘贴、Flutter 支持的四种代理组类型、引号/行尾注释、重复名称、`relay` 和非列表结构校验；ViewModel 在核心运行期间继续禁止打开和保存代理组。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 140 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；21 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 18 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256：`0117572215f4a96342cd70c0c962eb67897b8f2756409de5378ffa5fcb241e41`；APK 仅包含 `lib/arm64-v8a/libclash.so`、`libandroidx.graphics.path.so` 和 `libbarhopper_v3.so`，未访问当前 `AGENTS.md` 列出的真实订阅路径。

## 自定义 Profile Providers 分区编辑验收记录

- 自定义 Profile 卡片新增“Provider”入口；编辑器分别维护 `proxy-providers` 与 `rule-providers` 顶层 map，只回写对应区块，保留其他 YAML 字段；远程订阅 Profile 不开放该入口。
- `ProfileProvidersEditor` 覆盖双 map 提取/替换、包装粘贴、空 map、嵌套 map、重复名称、scalar 条目和边界注释保留；ViewModel 在核心运行期间继续禁止打开和保存 Provider。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 146 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；22 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 19 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256：`bb581765f3e47ca991d4187ef5da79501ef8a2c36bf2bdd5d302e6360f32f4a5`；APK 原生库全部位于 `lib/arm64-v8a/`，本轮未访问当前 `AGENTS.md` 列出的真实订阅路径。字段化 proxy provider override 另见下方 2026-10-27 验收记录。

## Profile 排序验收记录

- 配置页新增“排序”入口和“排序配置”弹窗；上下移动只调整内存草稿，点击“保存排序”后才写回 Profile 顺序，取消不会产生持久化变更。
- `ProfilesViewModel` 测试覆盖排序委托、遗漏 Profile 补齐和核心运行期间拒绝排序；Repository/Room 测试覆盖事务内 `orderIndex` 重排及当前 Profile 选择保持不变。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 152 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；26 项 instrumentation 测试失败 0、错误 0、跳过 0，新增排序 Compose 与 Room 流程均通过；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256：`d1f4098ea916501f595988ca9e398397defb4286d409b37fbd98366be160465e`；APK 原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问当前 `AGENTS.md` 列出的两条真实订阅路径、VPN 或核心启动。
