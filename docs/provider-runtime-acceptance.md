# Provider 冷恢复与运行态边界验收

本组承接 KUI-010 和 Provider 正文独立密文恢复；生产实现是否需要修改由真实测试结果决定。构建、设备结果和通过范围以 `docs/acceptance/provider-runtime-redmi-20261107.json` 的最终记录为准，本文的用例设计不能代替通过证据。

## 输入与保护

仅使用工作区原 `1.yaml` 的正式 FILE 字节副本，以及外层 AGENTS 唯一授权源对应的正式 URL 行。输入以 SHA-256 绑定，不提交地址、节点凭据或正文。创建独占 FILE 源、CUSTOM 宿主和 FILE 目标；只有自己的缓存、源文件、行和目标正文允许变化。

运行态验收将原配置行、正文 SHA/size/mtime、偏好值、脚本摘要、选择及 VPN/模式值保存在 Debug 独立 AES-GCM 密文。跨进程窗口暂时关闭已启用的正式自动更新；清理先核对正式行除了该开关没有外部变化，再恢复原开关。禁止回写整库覆盖外部编辑，失败保留本轮 token 的证据与恢复备份。

## 用例

1. **MRS 只读预览**：正式 MainActivity 进入原配置中的真实 MRS，滚动后等待密文 UI 树落盘，外部核对同一存活 PID 再 force-stop。新进程必须自动打开预览，核对名称、会话、正文摘要、资源 digest、只读状态、无保存按钮和滚动位置；再次恢复后逐层返回同一 Provider 面板，关闭并重开详情不复活预览。此项限定停止态。
2. **运行态保存前中断**：正式 ViewModel 到 `PROVIDER_INTENT` 时挂起，密文意图已落盘而缓存及核心成员未变；外部 SIGKILL 后先独立观察系统新 PID 和同 PID 前台 ProxyService，再启动 verify。禁止 verify 调用 start/restart/apply；恢复不自动重交，显式重试仅提交一次。
3. **运行态提交后中断**：到 `PROVIDER_COMMITTED` 时缓存及核心已变化、密文尚未确认回执。系统恢复后从正式存储和核心重新绑定，识别已提交并阻止重复提交。两个窗口都将原副本中的一个节点改名，同时核对真实成员变化，避免用注释变化冒充核心应用。
4. **KUI-010 实核切换/回滚**：停止态、纯代理运行态分别删除自己的引用源文件或源记录。目标仍取原配置副本，仅将首个组类型改成未知值。调用正式 `selectProfile` 触发实际核心 setup 失败，核对旧选择 ID、节点选择、已应用字节、真实组成员/当前节点和运行时长恢复；随后恢复目标副本的原字节，验证可成功切走且坏旧源没有被悄悄补回。

运行态请求只经过本地 SOCKS 到唯一授权 HTTPS 地址，开启 TLS 主机名校验、要求 HTTP 200，并通过同 socket 源端口和正双向流量与 Mihomo 连接关联。验证用例不通过页面读组或 `profileInputsApplied` 触发重解析坏旧引用。

## 上游契约与源码定位

- Flutter `lib/views/proxies/providers.dart` 的 `_sideLoadProvider`、`_handleSave`：核心侧载成功才退出，保存失败留页。只读参考，不合并 Flutter 提交。
- Go 适配层 `core/config.go`：`handleValidateConfig → handleGetConfig → unmarshalRawConfig` 只解码原始结构；`applyConfig → loadSetupConfig → loadConfig` 才完整解析。未知组类型由 `core/Clash.Meta/adapter/outboundgroup/parser.go` 拒绝。
- `applyConfig` 解析失败仍临时应用默认配置并返回错误，因此 KUI-010 的实核断言要求失败 setup 和补偿 setup 至少使 epoch 前进两次，再确认旧核心内容。这比只注入 FakeBridge 校验错误覆盖更深，但不证明“补偿 setup 也失败”的真实内核分支。
- Kotlin `CoreRuntimeManager.applyProfileLocked` 的旧输入补偿逻辑沿用 KUI-010 修复；新 Debug 宿主只组织场景，不实现产品回滚。

## 失败与边界

原始附件固定在 `../acceptance-artifacts/provider-runtime-20261107/`，失败单列 `failures-retained.json`，不累计早先成功子项冒充最终整轮。

首次 MRS 再次恢复的返回失败来自测试手动调用 `onSaveInstanceState` 后继续操作 Compose；该调用使 ComponentActivity 生命周期进入 CREATED。移动 Bundle 泄漏检查到最后一次 UI 操作之后，保留原失败与精确恢复。

运行态初次提交后准备沿用上场新进程，Debug 探针计数累计；准备前无服务且无本轮遗留备份时 force-stop 隔离探针。此准备隔离不算系统恢复证据，正式窗口死亡仍是 token/PID 限定 SIGKILL。ready 后杀进程前若失败，清理也先通过该受保护接收器中断挂起保存，无法确认死亡时保留备份并报告未清理。

首场失效引用测试错误地断言 RawConfig 校验应拒绝未知组类型，尚未进入真实切换即失败；修正测试契约，保持实际 setup 拒绝和补偿回滚作为通过条件。构建任务名一次拼写错误也独立保留。上述均为新增验收代码问题，不据此登记生产缺陷。

随后MRS再次恢复返回仍超时，增加状态诊断后确认父详情与生命周期正确，但列表和统计停留读取失败。已按KUI-013记录并修复冷绑定后的父面板刷新，见 `docs/provider-cold-parent-refresh-defect.md`，单测红绿及两套包的MRS复验独立保留。

`final6-before` 已观察系统新PID和前台服务，但verify发生无业务栈的TimeoutCancellationException；正式数据与备份精确恢复。仅增强Debug阶段诊断后，`final7-before`恢复前后真实HTTP200/同socket核心归因、显式一次提交和清理通过。原超时具体等待无法从旧日志还原，仍保留未定原因，后续网络/恢复稳定性检查不得据重跑成功抹除。

本组不覆盖 VPN/TUN 保存死亡、OEM 自然回收、所有 Provider 类型/代理协议、运行态 MainActivity 完整点击导航、独立全局规则页冷恢复、补偿再次失败的实核注入或完整 Flutter 视觉。玻璃默认关闭，原暂缓与取消范围保持。
