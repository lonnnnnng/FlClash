# 异步操作等待反馈审计

## KUI-042：提交后静默等待

### 问题

配置订阅表单提交后曾立即由宿主关闭，网络下载、age 解密和 Room 写入在后台继续；用户只能等配置列表突然变化。批量导入也只在结束时发一次事件，无法看到当前处理到第几条。文件导入确认后的解密/保存没有完成回执。常规设置和 DNS 覆盖虽然在操作期间禁用了控件，但缺少可见忙态；配置选择/删除等操作同样只靠禁用交互表达等待。

### 修复

- 订阅新增使用带请求编号的状态流，单条显示下载/解密/校验，缺密钥时说明等待输入，失败保留表单并允许重试，成功后由用户确认关闭。批量显示当前序号和最终新增、重复、失败数量。
- SAF 配置导入显示保存/解密状态；成功后显示文件名与完成确认，失败保留原预览与输入。
- DNS 和常规设置、User-Agent、Hosts、局域网设置保存时在所在页面或编辑器显示忙态；配置页通用异步动作显示页面级反馈。
- 保持网络资源、规则资源、Provider、订阅同步、核心状态、代理组测速、编辑器保存、应用版本检查等原有页面/行级进度与错误回执。
- 反馈按订阅请求编号更新，迟到的旧请求不会覆盖新开的添加表单。进度采用不确定进度，不伪造下载百分比。

### 实现位置

- `android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/ProfilesViewModel.kt`
- `android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/KClashApp.kt`
- `android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/ProfileFileImportController.kt`
- `android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/ProfileFileImportHost.kt`
- `android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/GlobalDnsSettingsPage.kt`
- `android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/GeneralSettingsPage.kt`

### 验收与边界

全模块 911 项 JVM 测试通过，Debug、AndroidTest、未签名 Release-R8 和双 Lint 构建通过。Redmi Note 8 Pro / API 36 新增忙态用例 5/5 通过。最终竖屏相关 UI 类回归共 38 项，其中 6 项失败、1 项按 `1.yaml` 授权参数跳过；失败分别是两项文件预览旧文案、两项来源菜单测试回调、ProfileCard 预览回调，以及 GlobalRules 排序标签。涉及的旧组件/断言均未由本组修改；失败已保留，完整类回归不记全绿。

证据见 [`acceptance/async-feedback-redmi-20261112.json`](acceptance/async-feedback-redmi-20261112.json)。本轮未覆盖 TalkBack、备份/系统恢复、语言主题、历史TLS根因或菜单视觉性能；这些范围仍按既有路线图处理。

### 回归余项收口（2026-11-12，POCO）

在用户更换的 POCO F2 Pro（`31936bb6`，Android 16/API 36，竖屏、默认字号）上，先使用未修正的测试包补齐本地配置授权参数，同四类完整基线为 **38 项：31 通过、7 失败、0 跳过**。原 Redmi 的六个失败全部复现；原跳过的 age 仓库用例执行后，另暴露一处旧错误文案断言。原 Redmi 证据保持原样，不用新结果覆盖旧失败。

本组仅修正三个测试文件，产品源码和内核未改。各项原因与修正如下，后续同步产品行为时据此复核测试合同：

| 测试位置/用例 | 失败原因 | 修正与保留的检查 |
| --- | --- | --- |
| `ProfileUiInstrumentedTest` 两项文件预览 | 仍断言旧的 YAML 格式文案 | 对齐现行完整核心语义预检文案；保留确认导入和无效文件禁止导入检查 |
| 同类的 CUSTOM/QR/FILE 来源菜单 | 点击后立即断言回调，且在同一个关闭中的菜单连续选择 QR 和 FILE | 宿主消费关闭状态，等待并计数一次回调；选 FILE 前真实重新打开菜单 |
| 同类的运行态 ProfileCard | 点击只读预览后立即断言 | 等待一次预览交付；编辑、重命名、删除禁用检查保持 |
| `GlobalSettingsInstrumentedTest` 全局规则排序 | 仍操作已移除的上下移按钮，固定宿主未返回保存成功版本 | 使用真实长按拖拽往返，模拟成功回执并检查两次版本推进；取消选择使用现行标签 |
| `ProfileAgeInstrumentedTest` 仓库失败保护 | 仍期望原始解密错误，实际已持久化统一脱敏文案 | 对齐脱敏错误，增加成功重试清除错误检查；保留失败时正文、mtime、lastUpdateAt 不变及密钥接线检查；结束自有观察协程后再关闭内存 Room |

最终重新构建后，同一批运行以下四类，**38/38 通过、0 失败、0 跳过**：`ProfileUiInstrumentedTest` 26 项、`ProfileAgeInstrumentedTest` 6 项、`GlobalSettingsInstrumentedTest` 3 项、`GeneralSettingsInstrumentedTest` 3 项。KUI-042 五个忙态用例包含在这 38 项中，不另行累计。

统一检查命令为 JDK 17 下的 `./gradlew test :app:assembleMotionBenchmark :app:assembleMotionBenchmarkAndroidTest :app:lintMotionBenchmark --no-daemon -Ptarget-platform=android-arm64 -PkclashMotionBenchmark=true`。构建成功，911 项 JVM 结果零失败/错误/跳过，含 Gradle 复用；Motion Lint 0 错误、74 警告、14 提示。本轮没有重跑 Release-R8 或 Go。两 APK 安装回拉摘要一致，13 张 PNG 的分片、SHA、图片完整性与目视检查已完成。

本轮是固定 Compose 宿主的组件回归，不新增正式 MainActivity/SAF 全链路覆盖。age 仓库用例使用内存 Room、隔离缓存、注入下载/解码器和真实核心明文透传，**不代表在线 age 加密订阅下载、解密、应用 E2E 已验**。菜单用例验证最终一次交付，没有逐帧证明交付恰在动画结束后发生。

USB 临时配置副本按 SHA 核对后删除，两个自有验收包已卸载，结束无 KClash 服务；Flutter 参考包未操作，原始 `1.yaml` 的 SHA/大小/mtime 保持且未上传。本组不发版。证据见 [`acceptance/kui042-regression-poco-20261112.json`](acceptance/kui042-regression-poco-20261112.json)，原始日志与图片保留在仓库外 `../acceptance-artifacts/kui042-regression-poco-20261112/`。
