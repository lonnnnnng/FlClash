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
