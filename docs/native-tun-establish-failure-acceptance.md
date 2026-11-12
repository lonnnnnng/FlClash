# 系统拒绝建立 TUN 后的补偿验收

2026-11-12，源码基线 `44278c36`，POCO F2 Pro（`31936bb6`，Android16/API36，竖屏、默认字号）。只使用独立 `.motion` 验收包、原始 `1.yaml` 和既有授权 HTTPS 目标。本组仅新增验收代码，产品和 Mihomo 内核未修改，未发版。

## 真实失败条件与结果

新安装的验收包尚未取得系统 VPN 授权，先断言 `VpnService.prepare(context) != null`，再有意绕过 Activity 的授权前置检查，通过真实 `CoreRuntimeManager` 启动。没有使用假服务、生产故障开关、坏 YAML、appops 或其他应用撤权来制造失败。

真实 `VpnService.handleStart()` 调用 `Builder.establish()` 返回 null，应用抛出固定错误 `VPN establishment was rejected by the system`。三次均在本进程真实服务日志看到该拒绝；最终单批 **1 项场景通过、0 失败、0 跳过**，内部包含三次拒绝和授权后一次恢复，不计成四个测试：

| 操作 | 失败返回耗时 | 结果 |
| --- | --- | --- |
| 直接启动 | 1224ms | 原始拒绝原因传回，FAILED，无会话起点 |
| 收到失败后立即再次启动 | 437ms | 同一拒绝原因传回，FAILED，无会话起点 |
| 生产 `MainViewModel.startRuntime()` | 435ms | `errors` 流收到同一明确错误，未等满15秒服务超时 |

失败阶段验证原配置行、当前选择、正文 SHA/大小/mtime、未提交草稿密文和回读保持；`runtime_should_resume` 不存在，`appliedEnabled` 和运行起点为空，VPN/Proxy 服务已退出，无 VPN Network。状态时间线在该阶段没有 RUNNING。

随后通过系统正常授权界面确认，再启动同一配置：RUNNING、CONNECTED、运行起点与恢复意图建立；独立普通应用 UID 通过系统默认 VPN 发真实 UDP DNS、带主机名校验的 TLS 和 HTTP200，核心观察到同源端口、同目标连接记录的双向正流量和非 DIRECT 链。未指定 SOCKS、未绑定物理网络或显式 VPN socket。原配置和草稿继续保持，最终停止并仅删除本例草稿。

## 失败来源和补偿链路

- `android/service/src/main/java/com/follow/clash/service/VpnService.kt`：`handleStart` 将 null 转为固定拒绝错误；`start` 发布 FAILED 并清理，`onStartCommand` 停止服务。该 null 处理在本地 Flutter 参考源码中相同；只读对照，不合入 Flutter 提交。
- `android/service/src/main/java/com/lonnnnnng/flclash/service/CoreRuntimeContracts.kt`：真实 `AndroidProxyServiceController.start` 等待服务结果，失败时停止服务并抛给管理器。
- `android/service/src/main/java/com/lonnnnnng/flclash/service/CoreRuntimeManager.kt`：启动失败停止监听/服务，清恢复意图、起点和应用中的 VPN 状态，发布 FAILED。
- `android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/MainViewModel.kt`：错误沿生产 `errors` 流交付。本组验证到该流，没有新增正式页面 Snackbar 的逐帧或完整点击链路证据；正常 Activity 的授权前置检查仍保持。

官方 [VpnService.Builder.establish 文档](https://developer.android.com/reference/android/net/VpnService.Builder#establish())（2026-11-12 经 smart-search 抓取正文）说明，未 prepared 或已被撤销时返回 null，其他无法建立接口的条件可抛异常。本轮实测的是**系统未授权拒绝分支**，不是内核 ioctl 异常、Core 拒绝已建立 FD，或运行中候选重建一次失败后的自动回滚。恢复是用户正常授权后的显式重试，不是同一个旧 TUN FD 自动复活。

本轮三次拒绝均及时传递，未复现服务 FAILED 随销毁变为 STOPPED 导致等待超时；有限调度样本不证明所有服务并发窗口均已覆盖，因此未据静态推测修改产品状态机。

## 验收源码问题与修正溯源

新增 `android/tests/instrumented/NativeTunFailureInstrumentedTest.kt` 复用真实 Repository/Runtime/Service、生产 ViewModel 与前组独立 UID 流量工具。

首轮三次拒绝和清理均通过，但授权后立即读取 Network 得到0，整例在 `SemanticVpnTrafficAcceptance.kt:23` 的网络数量断言失败。服务 CONNECTED 代表 FD 已交付，系统 Network 发布仍异步；已改为10秒有界条件等待，未给产品加固定延迟或放宽 DNS/TLS/HTTP/核心归因检查。原失败保留在 `baseline/native-failure.log`，不计入最终通过数。

独立静态审查另指出两项新验收器隐患：异常收尾可能因 stop/偏好恢复失败而跳过后续释放，以及 synchronizedList 的迭代缺少外部同步。已改为逐步清理、将收尾错误附到原始失败，且加锁获取时间线快照。两项属于审查发现，不宣称均曾在真机复现。本例草稿只在内容仍匹配时删除；协程和 ViewModel 有独立释放路径，不删除原配置。

以上均为本项目新增验收代码的问题，不是 Flutter/Mihomo 缺陷，无需同步内核补丁。

## 构建、清理与保留边界

JDK17 下执行根 Wrapper `test`、motion应用/测试APK构建和motion Lint通过。911 JVM结果零失败/错误/跳过（App618、Service220、Core39、Common34，含Gradle复用）；242任务中9执行、233复用，Lint0错误/74警告/14提示。本轮未重跑Release-R8/Go，无视觉改动或新增截图。motion双包与独立探针APK安装回拉SHA匹配。

验收后外部服务记录为空，`ls /sys/class/net` 未发现 tun 接口；ROM拒绝 `ip -o link show`（Permission denied），该诊断限制单列，不作为建TUN失败。两个自有探针输入按SHA核对后删除，三个验收包卸载，同时移除本例授予的motion VPN资格。Flutter参考包未操作；根 `1.yaml` SHA、34220字节和mtime保持，未上传公网。

语义预检的系统拒绝补偿分支现已补验；内核/ioctl异常、热切候选建TUN失败自动恢复旧参数、其他协议/来源、在线age、低API/其他OEM仍按各自条件保留。整个语义预检目标继续PARTIAL，不由本例扩展为所有原生失败均已覆盖。

脱敏证据：[native-tun-failure-poco-20261112.json](acceptance/native-tun-failure-poco-20261112.json)。原始失败、最终回执、构建/安装与清理附件保留在仓库外 `../acceptance-artifacts/native-tun-failure-poco-20261112/`，不提交原始附件或配置正文。
