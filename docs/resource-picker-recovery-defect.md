# KUI-017：资源文件选择器的请求身份与冷恢复

2026-11-07，作者 long。基线 dbdf7a3f。范围为 SAF-01～05：规则 YAML/MRS、图库 OpenDocument 和组图片 GetContent。本组具名范围已验；完整视觉、任意流字节中断与OEM自然回收不作通过结论。

## 来源和原链路

Flutter固定参考 be847366：`lib/views/config/providers.dart` 文件导入直接使用原字节，已登记后文本可编辑、MRS核心只读预览；`lib/views/config/icon_sets.dart` 选中即导入；`lib/views/profiles/custom/icon.dart` 图标只返回局部组草稿，组确认才写配置。该参考没有本地组图片入口，不能冒充上游已有相同实现。Kotlin既有规则先预览再保存的交互保留，不合并Flutter提交。

原 Kotlin `KClashApp` 两个 OpenDocument 回调直接交VM，没有请求身份；`RuleResourcesViewModel`预览和MRS原字节仅内存；图库重复返回可新增两行。`ProxyGroupVisuals`读取协程只属于子页面，不绑定持久父会话。以上为源码核验，尚未记录原版真机红测。

## 本组实现

- `PersistentFilePicker` 独立密文请求，WAITING→READING→RESULT→DONE。等待记录落盘后才启动系统页，回调一次认领；旧token、重复或取消后迟到读取拒绝交付。读取中死亡恢复为可重选错误，已读取结果保留原字节。
- 规则预览接独立加密保存树，包含正文、选区、错误、放弃确认和MRS字节；交付尚未确认时禁止保存。保存意图与目录同事务回执，提交后恢复不重新登记。
- 图库保留选中即导入，同事务保存目录和回执；请求DONE先于清回执，防清理窗口重放。
- 组图片请求owner存在父组加密树，交付先更新父组草稿并落盘，再清请求。父表单确认才写配置；已关闭旧owner结果不交新组。实际读取预算为128 KiB。
- 恢复失败保留原密文并给显式重试入口，规则/图库/组图片分别处理。

## 开发检查与历史失败

unit1编译失败：新 CompositionLocalProvider 缺少导入，未进入测试。unit2选择器协议5/5通过。build1全量测试与APK生成完成，但Release Lint的SuspiciousIndentation失败；保留日志，修正缩进后继续统一检查。

独立只读复核发现并修正：规则预览交付未确认时可保存导致复活；规则恢复失败后RESULT触发不重试；恢复错误无按钮；远程资源选项切本地误传空正文；组图标父子落盘间旧owner阻塞新请求。结论分别标明静态推导，不当作真机通过。

补充定位：activity1为0/5、activity2为1/5。diagnostic1明确报告规则ready=false/busy=true、pickerReady=true/request=null。原因是`mutable.value.copy(rows = repository.all())`先求值旧接收者，挂起读取后把恢复完成状态覆盖回false；修复为先读rows，再合并最新mutable.value。前述“仅测试时钟”不作为该规则页失败根因。

图库等待中ActivityScenario.recreate强等RESUMED，而真实选择器在前台时宿主为STOPPED；改为直接请求Activity重建，结果返回后再核验。测试清理产生单个`global_icon_sets=[]`新偏好：正式原键值/正文保持，键哈希和空值已核对，显式门禁精确恢复1/1；新清理恢复原偏好行。原保护失败独立保留，不与后续成功合并。

## 验收与后续同步

activity5的选区回读为0,0；selection1记录重建前后树均为3,11，不能倒推旧失败原因。正文选区变更增加同步checkpoint并拒绝STOPPED旧窗口回调后，activity6完整规则/图库5/5通过（16PNG）。

group1/3/4/5/6停在DocumentsUI文件定位，未进入应用读取；group2被APK哈希门禁拦住。group6终帧与完整节点诊断确认：系统图片网格隐藏文件名，坏/好图片实际均存在。测试先切系统列表再匹配精确文件名后group7为1/1（3PNG）；不是产品图片读取故障，也不以缩略图位置猜选。

cold1在Instrumentation宿主死亡后没有正常系统任务返回，改为准备结束、普通启动App、真实打开DocumentsUI后只杀宿主PID。cold2临时UI树不可读增加有界重试。cold3记录旧PID消失、系统选取拉起新PID，但驱动3秒后接管时读取未完成；FAILED记录保留。cold4改为观察真实业务结果页后再核验，规则返回/取消、MRS、图库返回/取消和YAML预览两次恢复通过。上述受控失败的夹具均通过精确恢复方法核对并清理，未清正式应用数据。

cold4的preview-error准备失败：`payload: [`被既有规则识别器按Text回退接受，不能用它断言YAML语法错误。改用域名与CIDR混合产生明确格式冲突；此调整只纠正错误用例，不改格式识别器，不把被接受的旧输入宣称为校验失败。

cold5修正错误输入后，preview-error、preview-mrs、rule-before、rule-after及icons-after均通过，后两项再恢复也通过。image-return普通启动时UiAutomation多次返回null root，截图显示图标页已恢复，稍后独立读取UI树成功；此失败保留为驱动暂不可读，扩大有界重试后继续，不能宣称图片冷恢复已经通过。

最终build13为849项JVM、Debug/Test/Release-R8和双Lint通过；同套包final-activity1为7/7、final-cold1为14/14及3次再恢复、final-regression1为4/4，38PNG/13拼图（6张转场图单列）。591源码及双包回拉匹配，25序列化字段与Release隔离通过。详见`docs/acceptance/resource-picker-recovery-redmi-20261107.json`。只复用已验证资源输入和原1.yaml，正式配置/目录/文件/偏好逐场保护；不修改Flutter参考包。真实选择器等待时只终止已核对宿主PID并保留DocumentsUI；预览/提交死亡另用受控停止。两者不得混报。

后续上游同步只参考交互与格式，保留请求一次消费、回执、父会话绑定与128KiB读取预算。早期Room兼容、TalkBack、备份/系统恢复不在本组范围。
