# AGENTS.md

这是 FlClash Android Kotlin 主线的协作入口。详细约束按任务读取 `.agents/` 下的文档。

## 先读

- [.agents/project.md](.agents/project.md)：项目边界和已确认决策。
- [.agents/architecture.md](.agents/architecture.md)：模块、Mihomo 核心和服务生命周期。
- [.agents/commands.md](.agents/commands.md)：Gradle、Go、测试和设备命令。
- [.agents/rules.md](.agents/rules.md)：Kotlin、Compose、数据库和安全规则。
- [.agents/testing.md](.agents/testing.md)：单元、UI、服务和真机验收边界。
- [.agents/upstream-sync.md](.agents/upstream-sync.md)：Flutter 上游功能同步流程。

## 常驻规则

- 本分支是 Android 手机 Kotlin 主线；不要在这里恢复 Flutter、桌面或 TV 的运行时依赖。
- Go Mihomo 是代理内核；Android 应用层通过 Kotlin Bridge/Repository 使用它，不在 UI 中直接调用 gomobile。
- 核心生命周期只有 `CoreRuntimeManager` 一个事实来源；页面、`VpnService` 和 `ProxyService` 不各自启动核心。
- Room 使用全新数据模型，不读取或迁移 Flutter/Drift 数据；禁止用破坏性迁移清空 Kotlin 用户数据。
- Compose 页面只渲染 ViewModel 的不可变 `StateFlow` 状态，通过事件进入 UseCase/Repository。
- 生成文件由 Gradle、Room、Hilt 或 gomobile 生成，禁止手工编辑。
- 新增代码的关键业务注释使用中文，并在注释中标记作者 `long`；注释解释业务原因、平台约束或生命周期边界，不复述代码。
- 不提交密码、Token、API key、签名私钥或真实订阅链接；日志和报告统一使用 `[REDACTED_SECRET]`。
- Android 设备操作必须显式指定 `adb -s <serial>`，执行前核对设备型号和当前包名。
- Git 提交说明使用中文，不添加 `Co-authored-by`；保留与当前任务无关的改动。
- 不执行 `git reset --hard`、`git clean`、批量删除或其他破坏性命令，除非任务明确要求且目标已核对。

## 分支边界

- `kotlin`：长期主线，推送到 `origin/kotlin`。
- `main`/`upstream/main`：Flutter 参考和上游追踪，不直接合并到 Kotlin 主线。
- 上游新功能必须先确认 Android 手机行为，再在 Kotlin 主线实现并更新同步记录。
