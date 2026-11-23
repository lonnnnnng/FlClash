# 运行日志重复 key 闪退与 VPN 更新检查诊断

最初记录：2026-11-22；2026-11-23补充VPN下403根因及最终修复。原版 Flutter 参考包 `com.follow.clash` 未修改。

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


## 2026-11-23：运行VPN的403已定位

POCO F2 Pro（31936bb6）公开0.1.17报错页显示 `CORE_HTTP_PROXY/HTTP/403`，VpnService和ProxyService均在运行。经USB转发手机实际7890入站重发同一GitHub API查询：CONNECT 200、TLS证书校验成功、HTTP 403。响应头limit=60、used=60、remaining=0，正文为 `API rate limit exceeded for [REDACTED_EXIT_IP]`；当次重置时间为北京时间18:56:16。该摘要不包含代理出口地址。

GitHub官方说明未认证REST API按来源公网IP统计，基本额度60次/小时。同一出口的其他使用者可能共享计数，不能据此认定这60次全部来自KClash。同一手机核心入口访问 `https://github.com/lonnnnnng/FlClash/releases/latest` 返回302，Location为本仓库0.1.17，安装包HEAD亦302。这说明本次故障不是手机没有联网或代理未接管。

上游参考固定提交 `afe7136002271fd64f098b154e7fff40be721eb8` 的 `lib/common/request.dart:Request.latestRelease`，已有API失败后读取发布页跳转的回退。Kotlin原 `UpdateTransport.releases` 在所有非200响应直接抛异常，没有回退，这是本地功能缺口。初步候选曾对403/429增加网页回退并验证通过；用户随后明确简化，最终删除API分支和安装包HEAD，只对官方 `/releases/latest` 发一次HEAD并读取跳转。校验HTTPS、本仓库、正式Kotlin标签，取消检查会关闭请求。工具页直接弹框，不拉取更新说明，不下载APK。

原始现场和脱敏回执：`../../../acceptance-artifacts/vpn-update-diagnostic-poco-20261123/`；官方正文来源：https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api 。测试覆盖限流回退/相同或新版本/错误标签及站点/安装包缺失/正常API说明/非限流失败；真机结果另行记录，不把USB探针当作已安装应用的成功回执。


最终632项App JVM、正式签名R8、Motion/Test构建、Release Lint零错误与扫码DEX通过；POCO真实核心入口单次网页HEAD和四状态弹框2/2，正式候选实际VPN检查显示最新版。安装回拉SHA为 `b4925490ce5dfbb5f7aef1b1f75056d313371c507128f7ad5a624b2919249ffc`；保持原配置，VPN已恢复。此候选沿用0.1.17版本号，未覆盖GitHub原资产。早期探针一次TLS断连保留，不能声称解决所有网络失败。详见[验收](acceptance/app-update-web-poco-20261123.json)。
