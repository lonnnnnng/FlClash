# Upstream Sync

## Remotes

- `upstream` 指向 `chen08209/FlClash`，提供 Flutter 产品参考和上游变化。
- `origin` 指向 `lonnnnnng/FlClash`，承载 Kotlin 主线。
- `kotlin` 不跟踪 `upstream/main`；Flutter 仓库提交禁止通过 merge 或 cherry-pick 合入 Kotlin 主线，只参考 UI/UX、功能行为和实现思路。涉及共用 Go/Android 适配层时也须逐项审查并在 Kotlin 当前协议与生命周期中实现，不能借同步内核名义合入 Flutter 提交。

## Workflow

1. `git fetch --prune upstream`，记录目标提交和变更范围。
2. 在 `upstream/main` 或 `main` 中确认 Android 用户可见的功能、状态、错误和交互。
3. 更新 Android 功能矩阵，标记手机适用、桌面专属、暂缓或需要重新设计的项目。
4. 在 Kotlin 主线按 Compose、Room、Repository、CoreRuntimeManager 和 Service 边界实现。
5. 运行相关单元、UI、instrumented 和真实设备验收；同步更新上下文或 ADR。
6. 使用中文提交说明推送 `origin/kotlin`，在提交信息中保留上游基线 SHA。

上游同步是功能适配流程，不是把 Flutter 分支直接 merge 到 Kotlin 主线。

## Flutter v0.9.3 参考增量审查（2026-11-05）

已只读核对旧参考 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 到 `be84736605527034689c95bf27828ff27d912c5e` 的9项提交，功能映射与边界见 [`docs/upstream-flutter-v0.9.3-review.md`](../docs/upstream-flutter-v0.9.3-review.md)。更新远端跟踪记录不表示这些功能已适配，也不改变当前产品内核gitlink。按应用代理、超时与时区等差异需在 Kotlin 现有实现中完成；MITM 为上游默认关闭且公开随包核心未实现的预埋，不能算公开能力已可用。

## 源码缺陷与核心 fork

### 唯一代码同步来源（2026-11-05 用户确认）

- Mihomo 的唯一代码同步上游为 `chen08209/Clash.Meta` 的 `FlClash` 分支，自己的 `lonnnnnng/mihomo:kclash-fixes` 继续保存产品内核与必要的本地修复。
- 不直接同步 `MetaCubeX/mihomo` 的任何分支、tag、提交或 PR；不通过 merge、cherry-pick、手工复制补丁或直接跟随官方版本绕过此约束。官方 PR 盘点仅为历史研究，不是待直接合入清单。
- FlClash 作者已吸收的官方改动，可以随 `chen08209/Clash.Meta:FlClash` 一起同步；同步时仍以该分支的固定 SHA 为来源，记录实际差异和验证结果。
- 自行确认的本地缺陷仍可修复，先参考 FlClash 对应实现并登记缺陷台账；本地补丁不因同步而自动丢弃。需要改变唯一同步来源时，须由用户明确修改本条约定。

已确认的上游源码缺陷统一记录在 [`docs/upstream-source-defects.md`](../docs/upstream-source-defects.md)。发现时先分配编号，记录原始仓库/固定 SHA/符号、失败证据、影响、修复提交、回归和撤销条件；尚未确定因果的现场只列调查，不因有限绿测标成已修。

Mihomo 修复保存到 `lonnnnnng/mihomo` 的 `kclash-fixes`，主仓库 gitlink 固定可拉取的修复 SHA；原始来源为 `chen08209/Clash.Meta` 的 `FlClash`。提交父仓库前先确认核心修复已推送且可取回，不能交付仅存在本机的子模块提交。同步时逐项比较本地补丁与新上游的行为，只有等价修复和对应回归通过才撤掉补丁，并在台账保留原失败和替代提交。

核心控制器修复必须运行父模块全量 Go 竞态及 `github.com/metacubex/mihomo/hub/route` 的缺陷回归；前者不会递归执行嵌套 module 的测试。Android 构建和 Redmi 运行证据另记，不能由 Go/macOS 测试推广 Windows Pipe、低 API 或全部 OEM。
