# Upstream Sync

## Remotes

- `upstream` 指向 `chen08209/FlClash`，提供 Flutter 产品参考和上游变化。
- `origin` 指向 `lonnnnnng/FlClash`，承载 Kotlin 主线。
- `kotlin` 不直接跟踪 `upstream/main`，也不把 Flutter 提交自动合并进来。

## Workflow

1. `git fetch --prune upstream`，记录目标提交和变更范围。
2. 在 `upstream/main` 或 `main` 中确认 Android 用户可见的功能、状态、错误和交互。
3. 更新 Android 功能矩阵，标记手机适用、桌面专属、暂缓或需要重新设计的项目。
4. 在 Kotlin 主线按 Compose、Room、Repository、CoreRuntimeManager 和 Service 边界实现。
5. 运行相关单元、UI、instrumented 和真实设备验收；同步更新上下文或 ADR。
6. 使用中文提交说明推送 `origin/kotlin`，在提交信息中保留上游基线 SHA。

上游同步是功能适配流程，不是把 Flutter 分支直接 merge 到 Kotlin 主线。
