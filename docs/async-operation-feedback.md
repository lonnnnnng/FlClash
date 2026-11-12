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
