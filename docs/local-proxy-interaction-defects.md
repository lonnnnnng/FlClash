# 本地代理确认窗口与节点添加反馈缺陷

- 编号：KUI-006（长引用/确认层错误）、KUI-007（添加节点异步反馈）。
- 归属：Kotlin交互集成；不是Mihomo或Flutter上游缺陷。
- 原始基线：`46d9b4d92a9d66252e48e2c9b9ba5f8e7b73c6a0`。
- 当前状态：KUI-006/007源码修复、6条新增控制层用例和Redmi本组范围验收完成；未将静态发现写成旧包实测复现，ALPN间歇异常仍未定位。

## KUI-006：长引用无法滚动、保存失败藏在确认框下

原`LocalProxyConfirmations`用单个Text拼接全部引用名称，没有滚动容器；删除/替换失败由VM保留确认，但错误只显示在底层列表或节点编辑页，当前确认窗口没有错误和等待反馈。原`cancelDelete`也保留了上次失败文字与引用列表。

Flutter对照为`lib/common/dialog.dart`的`SelectableText.rich`及`lib/ui/dialog.dart`的`SingleChildScrollView`；节点删除先查引用再确认，见`lib/views/profiles/custom/custom_proxies.dart`。Kotlin保留现有确认层级，补有界滚动正文、固定在当前层的错误与进度；取消仅移除子确认并清本次错误，保留父节点、链接正文和列表选择。实际删除仍保留配置引用。

本组已证明：40个长引用首尾可达/操作按钮可见，取消不写，真实隔离Room失败后仍留确认和父表单，重复提交及等待取消不触发第二次写入，重建后可明确重试。长引用来自受控查询结果；替换失败由控制层验证保留链接/待替换集合，不扩大为正式引用查询、所有外部删除或冷进程边界。

## KUI-007：重复节点分支遗漏来源复核、异常反馈暴露底层内容

原`addFromRuntime`在新节点校验后检查当前Profile ID，重复节点分支却直接发反馈；查询期间切换配置可能在新页面提示旧请求结果。原生定义/校验或数据库抛异常时，通用perform直接使用异常message，无法保证错误正文不含节点凭据。

修复在规划完成后同时覆盖重复/新节点来源检查；读取定义、目录、引用、规划、实核校验、提交等外部失败按阶段映射可操作文字，不显示底层异常原文。同步busy门禁保留。

Flutter`lib/providers/actions/proxies.dart:addToLocalProxies`同样复制定义到全局目录，没有本组全部异步保护；`lib/providers/state/custom_import.dart`的去重/命名规则仍为参考。受理前发现切换则拒绝；受理后提交冻结的原节点快照，配置切换不改变全局目录写入目标，也不改新配置正文。相同Profile ID内的来源变动、A→B→A及原生调用永久不回调尚未因此闭环。

## 验收与后续同步

新增`LocalProxyInteractionTest`6条与`LocalProxyDraftRecoveryTest`9条初次定向通过；最终build6统一构建、Debug/Test、未签名Release-R8及双Lint通过。四模块XML共788项零失败/错误/跳过，App512本组build1/build2实际执行，其余276复用；最终build6四模块UP-TO-DATE。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。

Redmi最终final2精确12/12，43张原图经集合/哈希/解码校验及13张拼图目视；新增5方法和相邻7方法均通过。真实设备新增用例使用Debug隔离宿主，真实Bridge校验和隔离Room提交；定义从授权原1.yaml内存提取，长引用查询结果受控注入，不能称正式MainActivity全链路或真实引用查询验收。App/Test安装回拉与最终构建匹配；两份正式配置、脚本、正文SHA/大小/mtime、偏好、选择、原1.yaml和Flutter基线保持，隔离副本清理、收尾无服务。完整Flutter视觉仍PARTIAL。

后续参考Flutter更新时核对对话框滚动、删除引用语义、导入去重与错误反馈；不因上游重排布局删除保存失败/受理边界保护。全部失败日志和最终包哈希保留在`../acceptance-artifacts/local-proxy-interactions-redmi-20261107/`；仓库索引为`docs/acceptance/local-proxy-interactions-redmi-20261107.json`。下一组补正式MainActivity添加失败/重试、保存等待切配置及真实引用查询，ALPN间歇恢复继续专项定位。

## 相邻回归间歇现象：ALPN立即重建

本组final1为11/12、43张截图完整，5条新增用例全部通过；既有`StageOneDraftInstrumentedTest#listDraftReturnsToParentCanRemoveLastValueAndRejectsOldSession`在输入`http/1.1`后立即重建，界面第二项为空而失败。正式库/原文件/Flutter/设备前后保护保持。此用例在原有列表/集合实现上触发，本组尚未证实与生产修改有关。

诊断build3加入重建前后VM断言后通过；build4改为主线程只读断言仍通过；build5不在输入和重建之间增加等待，连续8次立即重建仍通过。以上不能定位根因，也不能抹去final1失败。最终build6回归加强为8轮交替输入再立即重建，同时断言VM和页面内容，final2通过；后续需继续观察并在再现时区分IME迟到回调、注册表回放或测试输入同步问题。未因诊断通过改写产品代码，暂不标为已修缺陷。
