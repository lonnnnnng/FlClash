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

- `./gradlew test :app:assembleDebug --no-daemon`：通过；当前 Debug 单元测试共 90 项，失败 0，错误 0，跳过 0。
- 二维码导入切片新增直接 HTTP(S) 链接和四种 KClash Deep Link 的统一解析测试；真机二维码图片导入仍待在当前两条授权路径范围内验收。
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
