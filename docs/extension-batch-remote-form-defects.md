# 扩展规则批选与远程导入表单缺陷记录

- 编号：KUI-004（扩展批选/删除）、KUI-005（远程新增表单校验）。
- 归属：Kotlin交互与保存结果集成，不是Flutter或Mihomo源码缺陷。
- 原始基线：`26e45f92b29e8c2920841f6904576c491a358dff`。
- 原始证据：外层`acceptance-artifacts/extension-batch-remote-form-redmi-20261107/red1/`；修复前产品与新增测试安装后精确4条均失败，正式数据保护通过。
- 当前状态：本组修复已通过最终build6构建和Redmi final4集中验收；完整Flutter视觉仍为PARTIAL。

## KUI-004：选择丢失、提前结束确认与筛选误删范围

`ProfileExtensionEditor`的selected/confirmDelete原先只有remember；真实MainActivity重建后丢失批选。确认删除时立即清空selected并关闭窗口，保存失败后无法直接重试。全局规则搜索后仍按所有selected删除，隐藏的已选规则也被包含。

修复前原始用例分别在“已选2项”重建后不可见、失败后“删除规则”确认不可见、筛选后应显示“删除所选1条规则”却不符处失败。失败未进入实际误删隐藏规则步骤，删除范围同时由旧源码`rules.filterNot { it.id in selected }`确认。

Flutter参考为`lib/views/profiles/extend/standard.dart`的取消保持批选、返回先退出批选，以及`lib/views/config/rules.dart`的visible∩selected删除、仅移除deletedIds后保留其余选择。参考的是行为，未合入Flutter提交。

修复将批选、确认及待处理删除按配置/子页保存；保存回读确认目标消失后才结束，失败保留确认与选择。Controller每次保存完成递增saveResultVersion，使同一错误连续出现也能结束等待并允许重试。重复确认由同步pending门禁抑制。外部移除规则时清掉无效选择；可见已选项清空则关闭确认，但保留搜索隐藏的有效选择。

回归覆盖真实宿主的批选/确认重建、取消不写、返回先清选择、明确删除与重开回读；组件覆盖筛选删除、连续相同失败重试、保存等待防重复/防取消、等待和失败后的保存状态恢复、外部移除及配置/子页隔离。组件恢复不替代真实冷进程或挂起期间的正式数据库故障注入。

## KUI-005：无效单条链接离开表单且丢失输入上下文

`AddProfileDialog`原先只检查非空就调用导入回调；主宿主先关闭窗口再交给ViewModel。无效链接因此丢失表单与纠错上下文。修复前组件用例输入file协议后仍调用一次导入回调，断言0次失败为1次。

Flutter参考`lib/features/form/named_url_form.dart`：校验失败留页、trim后提交、首次切到空批量输入时承接单条链接、名称Next→链接→密钥、Done提交。

修复采用单条/批量共用的HTTP/HTTPS及2048字符检查，错误只显示格式说明。保留草稿与错误尝试状态，字段聚焦/键盘动作明确，按钮与IME共用同步提交门禁。密钥仍只在内存中，后台、重建及返回来源时清除，不进入SavedState。协议比较忽略大小写，返回原链接以保持路径和签名参数大小写；此项防止新增UI校验相对旧URL下载路径产生兼容回退。

首次集中final1为23/26：新远程表单的首次自动IME等待超时，来源菜单/底层Activity与Dialog焦点交接时机尚未可靠。将自动聚焦放到Dialog内容的LocalWindowInfo作用域，等待isWindowFocused为true再请求字段焦点和键盘，不添加固定业务延时；键盘一旦手动收起，不因普通重组再次弹出。扩展重开回读尚未就绪的测试补明确等待。旧密钥测试最初怀疑组合时序，但补等待后final2仍失败；核对当前源码与历史提交确认已有订阅编辑器早已保留后台会话，由VM加密暂存。旧测试仍期待后台关闭，故按现行契约改为已有编辑保留、取消后进入新建表单并验证临时密钥清除，未修改RemoteProfileEditorPage产品行为。

final2为24/26：自动IME已成功，单条错误提示标签处于TextField未合并语义树，原匹配器找不到；改用useUnmergedTree，不放宽提示内容或可见性断言。另一失败为上述旧密钥契约。final1/2正式配置与设备保护均相等，中间失败独立保留。build1曾因测试链式调用Unit和缺click导入编译失败，build2成功但新增Modifier参数顺序警告；build3修正参数顺序后恢复既有Lint数量。build4仅匹配器修正构建成功，未安装，后续最终测试包还包含旧用例契约修正。

final3为25/26，35图完整；新功能已过，旧密钥用例将EditableText的密码掩码与明文比较而失败。最终改用隔离保存回调证明后台后仍传递原密钥，然后进入新建表单验证后台/隐藏状态清理；不从密码显示字符推断原值。全部中间失败保留，本轮未修改已有订阅编辑器的后台实现。

API核对来源为Android官方[WindowInfo](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/WindowInfo)和[SoftwareKeyboardController](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/SoftwareKeyboardController)，2026-11-07用smart-search fetch取得正文；show属于best effort，必须存在可接收输入且已聚焦的字段。原文保存于本组本机附件，最终可用性仍由真实IME验收判定。

回归覆盖长单条、40条批量、无效输入、首尾滚动、模式切换、Activity重建、来源往返、取消重开、真实IME上方按钮及两层返回；纯组件证明合法输入trim和一次回调，真实主宿主测试域名仅编辑/取消，绝不提交下载。

## 最终验收

2026-11-07最终build6通过Debug/Test、未签名Release/R8及Debug/Release Lint；四模块JVM结果共782项（App506、Service207、Core38、Common31），零失败/错误/跳过。App506在本组build1/build3实际执行，其余276项复用，最终build6四模块UP-TO-DATE；产品源码自build3后不变，build4/5/6仅修正测试。

Redmi Note 8 Pro/API36的final4精确26/26方法通过，35张PNG完整分片、哈希、解码和名称集合核验，并经10张拼图目视复核。App/Test实际安装回拉匹配build6；安装前及验收前后两份正式FILE/URL配置、脚本、正文SHA/大小/mtime、偏好/选择、原1.yaml、Flutter基线和设备设置保持，临时副本已清理、收尾无服务。本组未准备导入或修改正式订阅，没有将测试链接提交下载。

证据：`docs/acceptance/extension-batch-remote-form-redmi-20261107.json`；原始附件：`../acceptance-artifacts/extension-batch-remote-form-redmi-20261107/`。本组目视仅覆盖指定页面状态、真实IME与恢复，不代表完整Flutter逐页视觉或连续动效完成。

## 同步与撤销条件

后续参考Flutter新版本时复核上述三处文件的选择范围、错误留页和表单键盘语义；这些修复不进入Mihomo同步。替换本地实现前须以同等行为回归确认保存成功/失败/重建边界，不因上游视觉变化撤掉数据保护。

原始失败、构建错误和中间验收均独立保留。本组不解释前组final1偏好摘要变化，也不覆盖todo第6项长窗口元数据/mtime异常；冷进程、运行态网络、完整Flutter视觉和正式Release设备运行另验。
