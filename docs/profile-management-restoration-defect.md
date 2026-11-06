# 配置管理窗口在Activity重建后消失

- 编号：KUI-002
- 日期：2026-11-06
- 归属：Kotlin Compose页面状态集成；不是Flutter或Mihomo上游源码缺陷。
- 修复前基线：`fe6b6b74f6c54964b19f124aaa5693f12da70468`的`KClashApp.kt`，`ProfilesPanel`及`ProfileReorderDialog`。
- 当前状态：已修复并完成Redmi集中验收；最终final2精确9/9，完整Flutter视觉仍PARTIAL。
- 最终证据：[配置管理重建验收](acceptance/main-profile-management-redmi-20261106.json)；本文件与修复代码同次提交，修复提交可由本文件Git历史定位。
- 原始证据：外层`acceptance-artifacts/main-profile-management-redmi-20261106/red1/`，日志/安装回拉哈希/前后保护/截图仅在本机保留。

## 已确认触发与影响

Redmi Note 8 Pro、API36、arm64、1080×2340竖屏、默认字号。真实MainActivity从配置页分别打开重命名、删除确认和排序页，输入或拖动后重建Activity。三条原始用例均在重建后找不到原窗口，结果为0/3。

重命名框本身的`label`和排序子页的`orderedIds`已经使用`rememberSaveable`，但父页中的`profilePendingRename`、`profilePendingDeletion`、`showReorderDialog`只有`remember`。父级目标丢失后子页面无法重新进入，子级保存状态也无法显示；排序的放弃确认和打开时结果revision同样没有保存。

三条测试均通过正式Repository，使用当前原`1.yaml`字节建立自己的CUSTOM/FILE临时副本，当前正式选择保持。失败批次的正式两行、正文SHA/size/mtime、顺序/其他元数据/偏好/选择以及Flutter参考包和设备设置前后保持，副本已清理；未修改正式配置来重现问题。

## Flutter参考与Kotlin修复

只读参考`lib/views/profiles/edit.dart:393`的`renameProfile`：输入框确认返回有效名称后才写Profile；`lib/views/profiles/profiles.dart:606`的排序页在局部列表中暂存顺序，到`_handleSave`才读取最新配置并提交。它们提供待确认操作与正式数据分离的交互语义，不能据此认定Flutter存在相同Android生命周期问题，也没有合入Flutter代码。

Kotlin父窗口只保存目标ID并从最新正式列表解析Profile，不序列化旧对象；首次异步读库尚未完成时保留ID，读完后清掉不存在的目标。排序宿主开关、打开时revision和放弃确认保存恢复，排序子页等`profilesLoaded`后才创建，避免恢复顺序被初始化空集合过滤掉。确认前不写名称/顺序，不触发删除；取消后重新进入从正式数据开始。

## 回归与后续同步

`MainProfileManagementStateInstrumentedTest`三方法保留原恢复断言，并继续验证重命名明确保存及取消、删除取消及仅删除自身副本、排序确认前不落库/放弃重开/明确保存/跨主页返回回读。最终final2三条新增及六条相邻回归共9/9，25张PNG校验并经3张拼图目视；Provider失败草稿与代理List恢复也在真实MainActivity回归，其他四条为固定数据组件。

中间final1为8/9：重命名的实际`EditableText`已正确保留草稿及首尾空格，但`assertTextEquals`把输入框名称和提示一并比较。测试改为精确读取`EditableText.text`并等待异步名称更新；未放宽草稿断言，产品源码保持不变。原始red1的0/3、中间8/9和最终9/9分别保留，不跨批累计。

最终build4统一构建通过：App499/Service207/Core38/Common31共775项JVM XML零失败/错误/跳过；App499在本组build3实际重跑，其余276项复用，最终build4四模块均UP-TO-DATE。Debug/Test、未签名Release-R8和双Lint通过，Debug0错误/67警告/10提示，Release0错误/66警告/9提示。App/Test实际安装回拉SHA与最终产物一致，源码及本机附件摘要见验收JSON。

三个批次前后正式两份FILE/URL配置的全部行摘要、正文SHA/size/mtime、顺序、偏好与选择以及原1.yaml、Flutter基线包和设备设置均保持。仅自身临时CUSTOM/FILE被修改和清理，收尾无应用服务。Provider相邻回归会在停止态加载核心，不能称整批无网络；未手动更新原HTTP Provider或正式订阅，未启动运行态/VPN。

后续修改主页面转场、配置首次加载、Compose保存状态或管理弹窗时，复验这三条及Provider正文/List恢复。只有相同正式宿主场景和断言通过，才能撤销本恢复处理。此项是本地Kotlin修复，Mihomo唯一上游同步规则不变；配置管理/Provider正文冷进程、保存受理中断、目标被外部同时删除、运行态、完整Flutter视觉及低API仍未验。
