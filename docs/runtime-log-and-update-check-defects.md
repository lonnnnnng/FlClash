# 运行日志重复 key 闪退与 VPN 更新检查诊断

记录日期：2026-11-22。此批源码尚未发布；原版 Flutter 参考包 `com.follow.clash` 未修改。

## 运行日志闪退

### 观测与根因

用户提供的崩溃日志为 Compose `LazyColumn` 重复 key 异常。冲突 key 的形式是 `timestampMillis|level|payload`，正文示例以 `1795279154653|info|[UDP] mihomo ...` 开头。

`CoreBridge` 以 `System.currentTimeMillis()` 为日志事件赋时，同一毫秒可能收到多条等级和正文都相同的日志。`LogsUiState` 将事件追加到最多 1000 条的列表，而 `LogsDialog` 原先把时间、等级和正文拼成 LazyColumn key，因此两个不同事件会被误认为同一个条目并让 Compose 抛出 `IllegalArgumentException`。

### 修正

`LogsUiState` 在接纳每个事件时分配单调递增的进程内 ID；列表过滤按原 ID 保留记录身份，清空和容量裁剪不回收已经使用的 ID。ID 只属于运行期 UI 状态，不写入 Room，也不影响导出日志格式。

### 验证

- App JVM 用例覆盖同一事件对象重复接纳、字段相同的新事件、追加后的稳定 ID、清空后不复用 ID，以及 1000 条容量裁剪。
- POCO F2 Pro/API36 的 Compose instrumentation 将两条同时间戳、同等级、同正文日志渲染为两行，1/1 通过。
- `./gradlew test`、Debug 与 Release-R8 构建、Debug/Release Lint 通过。

## VPN 开启时更新检查失败

### 已确认事实

`AppUpdateRepository` 从 `CoreRuntimeManager.downloadRoute()` 取得当前路由；核心运行时 `UpdateTransport` 将请求交给本机 HTTP 入站，核心停止时使用直连。POCO F2 Pro/API36 上真实 GitHub 更新页在核心停止状态 1/1 成功，证明该设备此时的直连分支可用。

本轮 POCO 和 Redmi 都没有安装 Kotlin 包；POCO 当时只有原版参考包 `com.follow.clash`。因此没有在运行中的 Kotlin VPN 会话下复现用户报告，也没有证据把故障归到 KClash 代理、节点、Android VPN 或手机网络。用户提供的崩溃附件只包含重复日志 key 异常，不含更新检查异常。

### 诊断改进与下一步

更新失败状态现在只暴露脱敏的路由/阶段码与可选 HTTP 状态，例如 `CORE_HTTP_PROXY/DNS`、`CORE_HTTP_PROXY/TLS` 或 `CORE_HTTP_PROXY/HTTP/403`；不会展示异常文本、凭据或响应正文。路由策略没有改动。

仍需在用户报告的 VPN 实际运行状态下重新检查。读取页面上的诊断码后，按其指出的阶段继续定位；如果核心停止时仍能成功而活动核心时出现 `CORE_HTTP_PROXY/TIMEOUT`、`/DNS` 或 `/HTTP/<status>`，应分别检查本地代理入站、核心节点出站或 GitHub 响应，不能先绕过代理。
