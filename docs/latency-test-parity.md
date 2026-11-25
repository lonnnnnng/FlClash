# Flutter 与 Kotlin 手动测速链路对照

记录日期：2026-11-25

## 结论

已修正一处会让 Provider 节点采用不同超时窗口的 Kotlin 分支。它只影响“该节点属于 Proxy Provider，且手动请求遇到显式 health-check timeout”的场景；不能据此解释此前普通成功样本的 2733ms 与 3969ms 差值。

Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，API 36）实际安装包报告版本 `0.9.3` / code `2026112401`。只读查看了它的常规设置：测速超时为 8000ms，“核心覆盖”列表未添加“统一延迟”或自定义测速 URL。配置正文、订阅、偏好未修改，核心未启动。该 APK 元数据没有源码 SHA，不能把它精确绑定到本地某个 Git 提交。

## 固定源码与对照结果

- 本地固定的 Flutter v0.9.3 源码提交为 `be84736605527034689c95bf27828ff27d912c5e`（源码版本 `0.9.3+2026110401`，Mihomo gitlink `024245dc72bc92f48237f865119afe0cd53ac9e6`）；v0.9.4 发布提交 `89791d7c11978bf9b580ba71bffcb00506a17659`（`5b453f80dbcee6b3ecfb0415bcace24a48c88707`）中，相关测速分支语义相同。Kotlin 当前打包的 Mihomo 为 `d4877e3e734ecbaa49f6d40ff70f150fb593956b`。
- 这些固定 Mihomo 的 `Proxy.URLTest` 都使用 HTTP `HEAD`；计时从代理拨号前开始，只有启用 `unified-delay` 且第二次请求成功时才改从第二次请求计时。源码对照未发现该测量实现的差异。
- 两边默认 URL 都是 `https://www.gstatic.com/generate_204`；Redmi Flutter 手机上的测速超时是 8000ms，Kotlin 默认也是 8000ms。Flutter 当前没有添加统一延迟或测速 URL 覆盖；Kotlin 保留源 YAML 中显式的 `unified-delay`，未声明时使用 Mihomo 的默认 `false`。
- Flutter 的 Provider 期限选择扫描所有 Provider，筛出包含目标代理且为本次 URL 配置了健康检查期限的 Provider，并取最长的显式期限；命中后即使期限短于通用超时也直接使用它。Kotlin 原先只按 `ProviderName` 查一个 Provider，且只在显式期限大于通用期限时覆盖。对短于 8000ms 的显式 Provider 期限，这会造成 Kotlin 等待更久；同名代理跨 Provider 时也可能漏掉最长匹配项。

## 修正与验证

`core/delay.go` 现按 Provider 实际成员名匹配目标代理，并取所有匹配 Provider 中最长的 URL 专属显式期限；没有匹配时回退到应用超时。新增回归覆盖显式期限短于/长于通用期限、最长匹配 Provider、代理不属于 Provider、URL 不匹配四种情况。

- 定向回归：`go test ./... -run '^TestConfiguredDelayTestTimeoutMatchesFlutterProviderSelection$' -count=1` 通过。
- Go 全量（去掉只因本 Kotlin 仓库不含 Flutter 文件而无法运行的既有 `TestFilterPresetsMatchTheirNamesOnly`）：`go test ./... -skip '^TestFilterPresetsMatchTheirNamesOnly$' -count=1` 通过；同范围 `go test -race` 通过。
- `./gradlew :app:assembleDebug --no-daemon -Ptarget-platform=android-arm64` 通过，gomobile 核心和 Debug APK 已重建；本轮未安装到 Redmi。
- 未跳过的完整 Go 命令会在该既有测试失败：`core/filter_test.go` 尝试读取此 Kotlin 分支不存在的 `../lib/models/config.dart`。该测试失败与测速改动无关，未修改其跨分支假设。

## 尚未归因的真机数值

此前的 2733ms 与 3969ms 是不同时间顺序发出的样本，不是交替同条件测量。当前检查确认了设置和 `URLTest` 计时算法的对齐，也修复了 Provider 超时分支；但尚未证明当时测试节点是否来自 Provider，也没有重现那组数值。因此不把差值归因为本次超时修正、缓存或网络。要给历史数值定因，需要同一设备、同一叶节点、同一目标 URL、核心停止/启动状态一致，按 A/B/A 顺序交替重新请求，并确认实际 Provider 身份。


## 运行态 CoreOptions 对照补验（2026-11-25）

Flutter `UpdateParams` 将 `test-url`、`delay-test-timeout`、`skip-cert-verify` 放在嵌套 `options`，活动核心直接 `updateConfig`；Kotlin 先前修改测速 URL/期限会走完整 Profile 应用。现已把这三个字段通过相同嵌套结构热更新，测速偏好不再重建配置或重跑订阅脚本。

Redmi Note 8 Pro 上用工作区 `1.yaml` 的原字节建立临时本地 Profile，关闭 VPN，仅向设备 loopback HTTP 探测器发 HEAD：核心收到新 `test-url` 后使用新地址；500ms期限下探测超时，运行时改到2000ms后同一探测成功；两次更新都保持相同核心运行会话。最终设置与实核合并 instrumentation 5/5，Service/App JVM 230/630；Go全量/race、Debug/AndroidTest构建和Lint通过。结束时恢复常规/VPN设置与原Profile，临时Profile及设备输入删除，正式Profile行/正文不变，KClash包未留在手机；Flutter 0.9.3包未修改。

该回环测试证明运行态目标和期限生效，不代表公网节点延迟差异已归因。历史2733ms/3969ms仍缺同设备、同节点、同 URL 的A/B/A；冷进程恢复与活动核心故障注入仍需单独验收。完整证据见 [`general-settings-core-overrides-redmi-20261125.json`](acceptance/general-settings-core-overrides-redmi-20261125.json)。
