# 语义预检的 VPN 绑定条件与运行保护

2026-11-12，基线 `a99b40ed`，用户指定 POCO F2 Pro（`31936bb6`，Android 16/API36）。本组只修改验收代码，不改产品网络、预检、VPN 服务或 Mihomo 内核。Flutter 仅作只读源码参考，不操作设备参考包。

## 原失败发生在哪里

原 Redmi 两次失败栈保留在仓库外 `../acceptance-artifacts/semantic-commit-20261111/final/05-runningVpnRejectsImportAndPreservesDraftUntilSamePreviewRetriesAfterStop.log` 和 `final2/01-runningVpnRejectsImportAndPreservesDraftUntilSamePreviewRetriesAfterStop.log`。错误为 `NetworkBoundSocketFactory.createSocket → Network.bindSocket → EPERM`，发生在测试创建并显式绑定 socket 时，尚未进行 TLS/HTTP。它不是 `VpnService.Builder.establish()` 的失败栈。

前组后来使用本地 SOCKS 验证运行保护，已如实保留 `tunBoundTrafficPassed=false`。本组补充普通独立 UID 默认路由的真实 TUN 请求，不把旧 SOCKS 成功改写成 TUN 成功，也不改写旧失败。

## 本轮已验证事实

最终六项同批 **6/6 通过、0 失败、0 跳过**。其中新增用例 `ConfigSemanticCommitBoundaryInstrumentedTest#runningVpnProtectsImportAndDraftWithIndependentUidTraffic` 在同一 VPN 会话中完成：

1. 对提供者进程和独立普通应用分别采集 UID/PID、默认网络、进程绑定网络和 VPN Network handle；分别尝试 `socketFactory.createSocket()`、TCP `bindSocket`、UDP `bindSocket`。只保存异常类型链和 errno，不输出原始异常消息。
2. 在拒绝导入前后，由独立 `com.lonnnnnng.flclash.networkprobe` 各执行一次真实 UDP DNS、带主机名验证的 TLS 和 HTTP200。两次均不指定 SOCKS、不绑定物理网络、不显式绑定 VPN socket；默认网络为 VPN，进程无绑定网络。核心观察到同源端口、同目标、新连接、非 DIRECT 链和双向正流量。
3. 运行中确认导入得到 `PROFILE_MUTATION_BLOCKED_MESSAGE`，Repository 二次守卫拒绝；行、选择、原正文 SHA/大小/mtime、未提交草稿密文和回读、epoch、运行起点、VPN 网络/接口摘要保持。停止后同一预览成功新增一份，随后只删除本例副本。

最终绑定采样如下；两次提供者采样之间没有重启核心、改变 UID、权限、VPN 配置或 Network handle：

| 采样 | 实际 UID/PID | 默认网络 | 同一 VPN 的三种显式绑定 |
| --- | --- | --- | --- |
| 提供者初次 | 10232 / 6152 | Wi-Fi，VPN 已能在 allNetworks 中看到 | 全部 `errno=1 / EPERM` |
| 独立普通应用 | 10231 / 6883 | VPN | 全部成功 |
| 提供者复测，初次采样后 830ms | 10232 / 6152 | VPN | 全部成功 |

拒绝导入后的采样三种绑定也均成功。前一批完整六项通过时，同样记录了提供者先失败、约832ms后相同 UID/PID/Network 绑定成功。830/832ms 是采样间隔，不是系统实际就绪耗时或产品启动性能指标。

**结论边界**：POCO 上已复现“VPN Network 可见，但提供者默认网络尚未切至 VPN 时立即显式绑定被系统拒绝”的条件；有限证据与启动期网络发布时序一致，不能断言提供者 UID 永久没有绑定权限。旧 Redmi 没有请求 UID 和相同时间戳，不能据此追认其具体系统内部原因。真实普通应用的默认 TUN 路由在本轮已通过；没有证据把该测试绑定失败认定为产品 TUN 建立失败、内核 TLS 故障或节点问题。没有给产品增加固定等待、重试或改走加速地址。

## 本项目测试源码的修正与溯源

首轮新增场景的两笔真实 TUN 请求均通过，但整例因停机后等待旧状态 `!saving && fileName == null` 超时35秒，按失败保留。KUI-042 的产品 `ProfileFileImportController.confirm()` 成功后保留文件名并发布 `completed=true`，等待用户确认；因此旧用例的完成态断言已经失效。这是本项目验收源码与产品合同不同步，不是 Flutter/Mihomo 缺陷，也不需要迁移内核补丁。

- `android/tests/instrumented/ConfigSemanticCommitBoundaryInstrumentedTest.kt`：等待 `completed`，保留所有运行态/草稿/正文检查；失败收尾也按原行集合、FILE 类型、`authorized` 标签和正文 SHA 识别自有新行，遇未知行中止，不清理未知数据。
- `android/tests/instrumented/SemanticVpnTrafficAcceptance.kt`：新增真实独立 UID 业务与绑定诊断，回执须匹配 token、命令及本次启动后的单调时间，防止重复运行时读取上轮成功。
- `android/app/src/androidTest/java/com/lonnnnnng/flclash/NetworkSwitchProbeActivity.java`：新增只诊断绑定的入口、真实 UID/PID、逐次单调时间与 errno；原 DNS/TLS/HTTP 请求合同保持。普通独立 APK 仍由现有 `tool/build_network_probe.py` 从同一源码构建。

后续同步 UI 合同时保留“完成回执可见后由用户关闭”的行为，测试不得再要求保存自动关表单。任何扩大产品就绪定义的改动，应重新验证默认路由和独立 UID，而不能只等 `allNetworks` 可见。

## 验收、清理和剩余边界

JDK17 根 Wrapper 的 `test`、motion应用/测试APK构建、motion Lint通过；911 JVM结果零失败/错误/跳过，含Gradle复用，242任务中9执行、233复用。Lint 0错误、74警告、14提示。本轮未重跑Release-R8/Go，未发版；无视觉改动或新增截图。最终六项之外的前一批6/6、首轮1项超时分别保存，不累计为最终用例数。

两份motion APK及独立探针安装回拉SHA一致。根目录原始 `1.yaml`（34220字节，SHA `7536dd03c962ad47a076feea860c3a573f338024e60c2853685d91b18aeee8d7`，mtime `1794392072`）仅经USB用于独立验收包，没有上传公网。结束停止服务，精确清理两个探针输入和三个自有验收包；Flutter参考包未操作，原文件保持。

本组覆盖固定Activity/控制器及真实Repository/核心运行链路，不新增正式文件选择器或完整页面点击E2E。原生 `establish()` 拒绝/失败的实机注入、低API、其他OEM、其他授权协议/来源实流、在线age加密订阅仍未验。已有托管脚本apply失败恢复通过，不替代原生建TUN失败回滚；现有JVM服务启动失败补偿检查也不能当作实机原生失败证据。当前六项功能总目标的统计不因此改变。

脱敏证据见 [semantic-vpn-poco-20261112.json](acceptance/semantic-vpn-poco-20261112.json)。原始日志、首轮失败、前一批与最终回执、构建和安装记录保留在仓库外 `../acceptance-artifacts/semantic-vpn-poco-20261112/`，不提交原始附件。
