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

## 源码缺陷与核心 fork

已确认的上游源码缺陷统一记录在 [`docs/upstream-source-defects.md`](../docs/upstream-source-defects.md)。发现时先分配编号，记录原始仓库/固定 SHA/符号、失败证据、影响、修复提交、回归和撤销条件；尚未确定因果的现场只列调查，不因有限绿测标成已修。

Mihomo 修复保存到 `lonnnnnng/mihomo` 的 `kclash-fixes`，主仓库 gitlink 固定可拉取的修复 SHA；原始来源为 `chen08209/Clash.Meta` 的 `FlClash`。提交父仓库前先确认核心修复已推送且可取回，不能交付仅存在本机的子模块提交。同步时逐项比较本地补丁与新上游的行为，只有等价修复和对应回归通过才撤掉补丁，并在台账保留原失败和替代提交。

核心控制器修复必须运行父模块全量 Go 竞态及 `github.com/metacubex/mihomo/hub/route` 的缺陷回归；前者不会递归执行嵌套 module 的测试。Android 构建和 Redmi 运行证据另记，不能由 Go/macOS 测试推广 Windows Pipe、低 API 或全部 OEM。
