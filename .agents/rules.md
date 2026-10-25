# Rules

## Kotlin and Compose

- 遵循 Kotlin 官方格式和项目配置；不要在同一文件混用 Java 风格和 Kotlin 习惯。
- Compose Composable 只负责渲染和发出事件；业务计算、网络、数据库和核心调用放在 ViewModel、UseCase 或 Repository。
- UI 使用 `dp`、`sp` 和 Material 3 Design System；不要把物理像素写进布局规则。
- 页面状态使用不可变 data class；StateFlow 对外只暴露只读流，副作用通过明确的事件和效果流处理。
- 不在 Composable、Activity 或 Service 中手工创建单例；共享对象通过 Hilt 注入。

## Business comments

关键业务注释使用中文并标记作者 `long`。注释说明为什么要这样处理 VPN、核心生命周期、配置应用、权限或数据边界；不要逐行翻译代码，也不要保留过期设计。

## Core and service safety

- 任何启动、停止、重启或配置应用都经过 `CoreRuntimeManager` 的串行状态机。
- 前台服务必须先满足 Android 版本对应的启动和通知约束，再执行核心操作；失败时保持失败状态，不伪造“已连接”。
- gomobile 事件转换必须处理线程、生命周期、空值、错误和重复关闭；不得在 UI 线程执行会阻塞的核心调用。
- VPN 权限、通知权限、电池优化和按应用代理权限都要保留用户可见的失败原因。

## Data and secrets

- Room 数据库只使用新应用 schema；未来迁移必须保留用户数据，禁止 destructive fallback。
- 订阅 Token、配置密钥、日志中的 URL 参数和签名材料不得写入源码、测试输出或提交记录。
- 测试使用脱敏 fixture，真实订阅链接只在用户明确授权的设备验收中临时使用。

## Generated files and scope

- Gradle、Room、Hilt、gomobile 和资源生成文件不能手工编辑。
- 当前任务范围外的 Flutter、桌面和上游文件只作为参考，不顺手修复。
- 不使用破坏性 Git 命令覆盖用户改动；删除前核对路径和 Git 状态。
