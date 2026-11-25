# 新常规设置运行态补验

2026-11-26，基于 `e99be51b` 后的本组代码，在 Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，API 36）完成六个具名场景。设备开始和结束均未安装 KClash；测试期间只安装自有 Debug/Test 包。POCO 与原版 `com.follow.clash` 未操作。

| 场景 | 已验证结果 |
| --- | --- |
| MainActivity 常规设置保存和重建 | 修改端口/最近任务隐藏，重建 Activity 后回显，最后恢复原值 |
| 真实 VPN 显式重启 | 纯代理→VPN→纯代理；运行中保存 MTU/系统代理参数保留旧 TUN 和会话，点重启后 Android LinkProperties 读到新参数 |
| 运行核心拒绝更新 | Debug 一次性把 `options.delay-test-timeout` 变成字符串，真实 Go 解码返回 `invalid_arguments`；正式管理器恢复偏好/正文并调用真实核心回滚，运行会话保持，CoreBridge 使用旧 URL 成功探测 |
| 停止态冷进程 | 旧 PID 23216→新 PID 23411；五项选择式覆盖保持，页面回显且核心仍停止 |
| 纯代理冷进程 | 23560→23710；Application 自动恢复核心，正文投影和保存的探测地址保持 |
| VPN 冷进程 | 24073→24278；Application 自动恢复核心/VPN，正文投影和保存的探测地址保持 |

这是分批六场景，并非一个六方法测试批次。最终加强 Go 错误码和直接 CoreBridge 检查后，错误回滚与主页面宿主一起复跑 2/2；VPN显式重启方法没有再改；冷恢复随后改用Keystore密文快照并重跑三种模式，VPN首次授权入口另改为startActivityForResult后复跑通过。

## 可复用入口

- `GeneralSettingsHostInstrumentedTest#actualGeneralPersistsPortsAndRecentsFlagAcrossRecreationAndKeepsProfiles`：停止态页面回归。首次安装必须先解锁屏幕；通知权限由测试 Rule 处理。
- `GeneralCoreProbeOptionsRuntimeLiveTest#liveCoreRejectsGeneralOptionsAndRestoresOriginalSettingsAndSession`：传 `kclashGeneralProbeOptionsFailureAcceptance=true` 和 `kclashGeneralProbeOptionsInput`。输入 JSON 仅含原 `1.yaml` 的 Base64 字节与 SHA-256，路径必须匹配 `/data/local/tmp/kclash-general-probe-<32位十六进制token>.json`；不要提交该 JSON。
- `VpnSettingsRuntimeLiveTest#vpnChoicePersistsAndExplicitRestartSwitchesRealTunAndProxyModes`：传 `kclashVpnAcceptance=true`、`kclashVpnAcceptanceLocalYamlPath`、`kclashVpnAcceptanceLocalYamlSha256`。本地路径必须匹配 `/data/local/tmp/kclash-vpn-settings-<token>.yaml`，只可复制工作区原 `1.yaml`。
- `GeneralSettingsMainColdInstrumentedTest#mainActivityRestoresSelectedOverridesAfterRealProcessDeath`：只接受空配置库、空选择、停止态。传 `kclashGeneralMainColdAcceptance=true`、`kclashGeneralMainColdToken`、`kclashGeneralMainColdYaml`、`kclashLocalYamlSha256`、`kclashGeneralMainColdMode=stopped|proxy|vpn`、`kclashGeneralMainColdPhase=prepare|verify|cleanup`。

冷恢复的每个模式必须按以下顺序单独执行：

1. `prepare` 将原设置和临时 Profile ID 保存于Android Keystore加密的私有恢复快照，发出 `ready-for-external-death;mode=...;pid=...` 后等待，最长 180 秒。
2. 主机核对输出 PID 与 `adb -s <serial> shell pidof com.lonnnnnng.flclash` 一致，然后只对该包 `am force-stop`；再次确认旧 PID 消失。
3. 新一轮 Instrumentation 使用同 token/mode 执行 `verify`。测试读 Application 初始化结果，验证前不调用 start/restart/apply；最后恢复设置、删自己的临时 Profile 和快照。
4. VPN授权必须用 `startActivityForResult` 建立系统结果关系；普通 `startActivity` 会超时。中断时保留相同 token，先通过 `cleanup` 恢复，不能直接删除私有快照掩盖现场。每种模式成功清理后才开始下一种。

测试命令使用 `adb -s wsvwypiz7xwslvl7 shell am instrument -w -r -e class <精确方法> ... com.lonnnnnng.flclash.test/androidx.test.runner.AndroidJUnitRunner`。不要整类执行，尤其冷进程 `prepare` 需要主机在同一 PID 活动期间终止。主机检查旧PID消失时，`pidof`退出码1且输出为空表示预期成功，不可将它误判为应用异常。

## 结论边界

冷恢复由 Instrumentation 启动真实 MainActivity，不是无人值守系统服务拉起、桌面点击 E2E、开机或 Always-on 验收。本轮只有回环 HTTP，未验外部节点/独立 UID 流量。手动测速超时由 Kotlin/Bridge 显式传入，未独立观察 Go Provider 默认超时恢复。

故障为 Go 解码阶段拒绝，未覆盖参数已部分生效之后的核心内部异常、磁盘写入故障或回滚再次失败。Release 只保留无状态直通实现，不提供 Debug 的 `arm/reset` 入口；已编译 Core Release 源码，但未重新构建/安装整套正式 R8 APK。

最初两次宿主运行在首个点击前报告 `No compose hierarchies found`：第一轮手机锁屏，解锁后仍因首次通知权限遮挡失败；授予权限后相同页面断言通过。已把权限前置加到本组宿主测试 Rule；没有为通过验收降低断言。

App630/Service230/Core39 JVM报告零失败（最后重复执行为 Gradle 缓存复用）；Debug/AndroidTest构建、Core Release编译、Debug Lint通过，Lint为0错误、12警告、1提示。原始输入只从本地 `1.yaml` 临时复制到测试机，摘要保持；临时输入、Profile、恢复快照和Debug/Test包已清理，无KClash服务/TUN，Flutter包仍安装。

摘要、具体日志与哈希见 [验收JSON](acceptance/general-settings-runtime-redmi-20261126.json)，本机运行材料位于 `../../acceptance-artifacts/general-settings-runtime-redmi-20261126/`。
