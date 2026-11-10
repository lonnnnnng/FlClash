# Mihomo fork 迁移记录

2026-11-10，用户删除旧 `lonnnnnng/mihomo`，重新从 `chen08209/Clash.Meta` 创建 [`lonnnnnng/Clash.Meta`](https://github.com/lonnnnnng/Clash.Meta)。旧仓库的产品修复分支已原样迁入新仓库，App 使用新地址，核心源码及提交历史保持不变。

## 来源与分支

| 项目 | 迁移结果 |
| --- | --- |
| GitHub 仓库 ID | 旧 `1430437521` → 新 `1452078795` |
| 直接 fork parent | `chen08209/Clash.Meta` |
| fork 网络 root source | `MetaCubeX/mihomo`；仅说明网络根来源，不是代码同步目标 |
| 唯一代码同步上游 | `chen08209/Clash.Meta:FlClash` |
| 产品修复分支 | `lonnnnnng/Clash.Meta:kclash-fixes` |
| 产品 gitlink | `464552e44ddf92affe5c41ee5a5d653a0e1e8daf`，迁移前后一致 |
| 当前已包含的 FlClash 上游提交 | `024245dc72bc92f48237f865119afe0cd53ac9e6` |

仅新增 `kclash-fixes`，新 fork 原有 `main`、`FlClash` 和全部标签的引用前后完全一致。没有强推、改写提交或把 Flutter App 提交合入 Kotlin。

保留的本地修复及合并记录：

| 提交 | 内容 |
| --- | --- |
| `f83e9b10` | 控制器重建竞态与 TLS 失败端口泄漏 |
| `852d3ad1` | 合入 FlClash 上游健康检查与自动选组修复 |
| `7038231e` | 暴露 Provider 测速超时给宿主 |
| `20bc0fc4` | Android 资源下载连接生命周期 |
| `464552e4` | Geo 下载瞬断恢复与重试预算 |

这些提交的完整 SHA 和祖先关系均保留，无需 cherry-pick 重做修复。

## 当前主线检出

主仓库 `.gitmodules` 已改为 `https://github.com/lonnnnnng/Clash.Meta.git`，分支仍为 `kclash-fixes`。正常构建继续使用固定 gitlink，不使用 `submodule update --remote` 跟随分支头。

已有检出更新到本次 Kotlin 主线后执行：

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

本机同时更新了父仓库缓存的子模块 URL 和子仓库 `kclash` 远端；子仓库 `origin` 继续指向 `chen08209/Clash.Meta`，`kclash-fixes` 继续跟踪 `kclash/kclash-fixes`。

<a id="historical-checkout"></a>

## 旧版本检出

`kclash-v0.1.0` 和 `kclash-v0.1.1` 的标签内容保持原样，其中 `.gitmodules` 仍为已删除的旧仓库地址。检出旧标签时先覆盖本地子模块 URL，再初始化：

```bash
git clone --branch kclash-v0.1.1 https://github.com/lonnnnnng/FlClash.git
cd FlClash
git config submodule.core/Clash.Meta.url https://github.com/lonnnnnng/Clash.Meta.git
git submodule update --init --recursive
```

检出 `0.1.0` 时替换第一行标签即可。旧标签下不要在覆盖 URL 后执行 `git submodule sync`，否则会重新采用标签内的旧地址。已初始化且仍指向旧地址的子模块，可先执行 `git -C core/Clash.Meta remote set-url origin https://github.com/lonnnnnng/Clash.Meta.git` 再更新；这条命令适用于常规克隆的 `origin`，自定义远端布局应更新实际用于产品拉取的远端。

## 验证与证据

- `gh api` 确认新仓库直接 parent、root source、仓库 ID 和写权限。
- 从新远端独立克隆 `kclash-fixes`，`git fsck --full` 通过，产品提交／源码树与旧本地仓库完全一致，五项修复及上游提交均为其祖先。
- 独立 App 验证目录使用更新后的 `.gitmodules` 完成子模块初始化，得到 `464552e4`。
- 旧标签覆盖 URL 后分别检出 `0.1.0` 的 `7038231e`、`0.1.1` 的 `464552e4`，均通过。
- 本次未修改运行逻辑，未重新执行 APK 构建、Go／JVM 功能测试或 Redmi 验收；不增加既有测试计数，也不改变现有正式版本。

可提交证据见 [`acceptance/mihomo-fork-migration-20261110.json`](acceptance/mihomo-fork-migration-20261110.json)。删除前镜像、两个完整历史 bundle、SHA-256 和命令日志保留于工作区外层 `acceptance-artifacts/mihomo-refork-migration-20261110/`。原仓库两分支均备份，标签／Release／Issue／PR 数均为 0；新 fork 的 GitHub 设置不由 Git 历史自动迁移。

历史验收 JSON 和旧阶段记录保留当时的仓库地址，当前说明及可点击修复链接已更新。GitHub Support 工单未提交；迁移没有使用 Support 改绑。
