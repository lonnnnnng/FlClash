# KUI-015：Provider 子页重建后丢失错误和放弃确认

## 来源与影响

基线 `1a5ef84c` 的 Kotlin Provider 选项已保存输入与子页位置，但本地错误、放弃确认以及快捷 YAML 格式错误使用普通 `remember`。Activity 重建后输入仍在，错误与确认框消失，用户无法继续原来的错误处理或退出确认。

缺陷属于 Kotlin 页面状态生命周期，不是 Mihomo 或 Flutter 源码缺陷。Flutter 仅作交互参考，不合入 Flutter 提交：只读 `upstream/main` 的 `be847366`，`lib/views/profiles/custom/providers.dart:94-125` 以独立草稿提交选项；`lib/views/profiles/custom/members.dart:637-674` 即时更新父草稿；`lib/features/form/nested_form_sheet.dart:57-100` 处理父层保存/放弃/继续。上游 `test/features/nested_form_sheet_test.dart:161-177` 表达系统返回先退出子页且不保存父表单，未在本轮运行 Flutter 测试。

上游 Provider 选项没有完全对应的快捷 YAML 页面，通用 quick_edit 的返回即应用行为也不等同于 Kotlin 已确立的显式保存协议；本修复保留当前 Kotlin 保存边界。最新导航实现位于本地未初始化的 prism_ui 子模块，不能用旧导航源码声称最新上游的入场返回已核实。

## 修复

`ProviderOptionsPage` 的 `discard/localError` 改为以资源名称为输入的 `rememberSaveable`；`ProviderQuickYaml.failure` 与其正文和放弃状态一起保存。提示均是既有校验信息，不增加原始正文或资源地址输出。

`validating` 继续使用普通状态：旧 Composition 的校验协程会取消，不能恢复一个没有任务负责结束的忙态。已经进入 `ProfilesViewModel` 的保存由保留的 ViewModel 继续处理，子页仅在成功回读新 revision 后关闭。

## 修复前证据

附件 `../acceptance-artifacts/provider-options-state-20261107/`：`red-build` 以旧产品源码和新增测试成功构建；`red1` 三条正式 MainActivity 用例均先看到提示，再在实际 `ActivityScenario.recreate()` 后失败：

- `optionErrorAndInputSurviveActualActivityRecreation`：`provider_options_error` 不再显示。
- `optionDiscardConfirmationSurvivesActualActivityRecreation`：“放弃选项修改？”不再显示。
- `quickYamlErrorAndDiscardSurviveActualActivityRecreation`：`providers_yaml_error` 不再显示。

原始失败结果为 0/3，独立保留，不与后续成功累计。三条均确认正式两配置/正文 SHA-size-mtime/脚本/偏好/选择保持，自己的临时 CUSTOM 已删除；外部门禁确认原 `1.yaml`、Flutter APK 和设备设置保持，收尾无服务。

## 首轮集中验收保留

`build1` 统一构建通过，`final1` 为 14/16、33 图，前后保护通过。两条失败发生在测试准备阶段：放弃确认用例等待新建配置卡片可见超时，未进入被测子页；入场返回用例在已经退出的 Dialog 上调用 Espresso 收键盘，触发 `RootViewWithoutFocusException`。原始日志保留。

测试改为等待正式 ViewModel 收到临时配置，再使用 LazyColumn 的滚动动作定位目标；入场用例本身没有文字输入，改成等待父层稳定后直接检查焦点 Window 的 IME 隐藏，随后冻结 Compose 时钟并按原断言发送 Back。未增加子页入场等待，也未放宽目标 Window 或横向位移断言。产品修复自 `build1` 起保持不变。

## 验收边界

新增测试使用真实 MainActivity/Hilt/ProfilesViewModel/Repository/Room，以授权原 `1.yaml` 的自有 CUSTOM 副本通过正式导航打开资源页。Activity 重建检查同一个 ViewModel、子页草稿及显式保存结果；这不证明冷进程或 OEM 自然回收。

保存等待使用不写数据的真实 Room 事务持锁制造窗口，不替换保存回调；入场返回冻结 Compose 时钟，在目标 Dialog 获得焦点且仍有横向位移时投递真实系统 Back。此范围不包含新窗口尚未取得焦点前的按键，也不等于连续动效性能验收。最终结果见下节及同名验收 JSON；局部异步校验的重建取消、冷进程与连续动效性能仍未纳入本组通过范围。

## 最终验收

KUI-015已红绿复现并修复：Provider选项本地错误/放弃确认与快捷YAML格式错误随Activity重建保存；局部校验忙态不持久化。Redmi final4精确16/16（7条正式MainActivity/Hilt/VM/Repository/Room用例＋9条组件回归），36张PNG完整校验及12拼图目视；过滤/IP/绑定搜索恢复、保存等待重建与防重复、7个子页入场中系统Back均通过。build5共830项JVM零失败/错误/跳过，App549在build1执行、其余281复用，最终四模块UP-TO-DATE；389任务11执行/378复用，Debug/Test/未签名Release-R8及双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。双包安装回拉和578份源码匹配；正式两配置/正文SHA-size-mtime/脚本行/偏好/选择、原1.yaml/Flutter包/设备设置保持，7次保护回报确认临时Room行及正文文件均删除，收尾无服务。red1 0/3、final1 14/16、final2与final3各15/16独立保留，不累计。新增正式用例为Activity重建，非冷进程/VPN/OEM自然回收；入场Back已确认目标窗口焦点及64ms时横向位移，非焦点交接前按键或连续动效性能。局部异步校验重建取消、正文保存终帧、独立全局规则页/资源选择器冷恢复仍待补，再统一Flutter排版/连续动效。第6项原保护异常、未知偏好变化、网络稳定性和前组final6超时仍待查；玻璃默认关闭，既有暂缓/取消范围保持。证据`docs/acceptance/provider-options-state-redmi-20261107.json`；溯源`docs/provider-options-restoration-defect.md`；附件`../acceptance-artifacts/provider-options-state-20261107/`。

## 后续边界补验（2026-11-07）

`provider-validation-boundary-redmi-20261107.json`追加校验返回后/onSave前重建取消，旧任务不迟到提交，显式重试revision只增加1；连同本组原16项及正文终帧共18/18。原生回调执行中未挂起注入，不能据此宣称该窗口已实测。KUI-015产品修复保持，新增仅Debug等待点与测试证据。
