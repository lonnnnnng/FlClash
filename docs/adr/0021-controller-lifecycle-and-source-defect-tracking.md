# 控制器同步交接与上游源码缺陷溯源

状态：accepted

日期：2026-11-05

Mihomo 固定基线 `c5062921054902b25215a6a933e72ccd714c9bfa` 的 `ReCreateServer` 为四种传输各派发后台任务，调用返回不代表旧监听已关闭或新监听已绑定。FlClash 外层配置锁无法约束这些迟到任务；连续配置更新可以触发全局服务指针竞态、旧请求覆盖新密钥，最终关闭也可能被迟到启动覆盖。另一个独立缺陷是 TLS 已绑定后客户端 CA 或 ECH 校验失败，未发布的 listener 失去回收路径。

核心公开重建入口现在串行完成监听交接，顺序以锁内执行为准；仅 Serve 循环在后台运行。每个已发布实例同时持有 HTTP Server 和 listener，关闭时显式释放 listener，覆盖 Serve 尚未登记它的窗口。配置按值及 Origins 切片快照，UI 路径和嵌入模式与路由构造使用同一把锁。HTTP/TLS 的 secret 与 Unix/Pipe 的本地认证语义保持。TLS 证书、客户端 CA 和 ECH 的准备均先于 Listen，坏配置明确拒绝，不回退旧服务。

修复实际落在 Mihomo 源码，不以 Sleep、fake、构建 overlay 或手改 AAR 替代。核心修复提交 `f83e9b1032ed02831457d4da5a414e063e932694` 保存到 `lonnnnnng/mihomo` 的独立 `kclash-fixes` 分支；原始来源仍为 `chen08209/Clash.Meta` 的 `FlClash`。父仓库 `.gitmodules` 使用公开 HTTPS fork，gitlink 固定修复 SHA；提交父仓库前先确认核心远端可取回，不修改 fork 默认分支。

新增 `docs/upstream-source-defects.md`，逐项保存编号、固定基线/符号、失败证据、修复提交、验证和撤销条件。KCORE-002 记录重建竞态，KCORE-003 记录 TLS 泄漏，上一组 TUN 保护缺口记为 KCORE-001。后续同步必须比较行为和对应回归，不能仅凭同名提交撤掉补丁；历史失败继续保留。

Go 父模块 `./...` 不遍历 Mihomo 嵌套 module，因此 CI 另跑 `go test -race github.com/metacubex/mihomo/hub/route`。Android 另经正式常规设置验证运行态控制器密钥轮换、认证、嵌入写入口禁用和立即释放端口。验证结果与环境边界见 `controller-lifecycle-redmi-20261105.json`；macOS/Redmi 不推广 Windows Pipe、低 API、全部 OEM 或长期稳定性，也不能由本次控制器修复倒推历史 Always-on TLS EOF 的原因。
