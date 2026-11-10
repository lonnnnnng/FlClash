# 下沿搜索与编辑器连续过渡（KUI-032）

2026-11-10，基于 Kotlin `0bf636d0`。本组是 Kotlin 与固定 Flutter 容器行为的对齐；不合并 Flutter 提交，不修改 Mihomo。

## 来源与实现

固定 Flutter `be84736605527034689c95bf27828ff27d912c5e`、Prism `63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185`。`lib/ui/scaffold/scaffold_actions.dart` 的 `_SheetForm.of` 按页面容器选择 `dockedSearch`；`scaffold_search.dart` 提供下沿搜索的焦点／非空展开及取消行为。`lib/features/form/selection_sheet.dart` 在目录达到阈值或已有查询时提供搜索，并首次显现当前选择。前组原 Scaffold 独立运行证据见 [参考记录](search-motion-reference.md)。

原 Kotlin 在资源选择、图标预览和添加面板顶端放置搜索，生产 `ProviderBindingPicker` 还没有与旧绑定组件相同的已选项定位。此次由 `ProfileSearchField.kt` 的 `DockedProfileSearch` 统一 48dp 下沿搜索、450ms 归一化弹簧与取消按钮；清空保留焦点，取消清空并收 IME，均不退出父页或提交选择。`ProfileEditorPage` 的底栏统一消费安全区和 IME 差值，列表占剩余空间。代理／规则绑定、生产 Provider、图标选择、图库预览和 Sniffer 添加面板使用该组件；生产 Provider 补已选项首次显现。

`SearchOptionsTransition` 给正文、脚本、规则和运行配置查找选项增加高度与透明度连续进退，退出阶段拒绝指针触点。观测工具栏把焦点请求放到已组合的输入框中，Back 在自身 Dialog 内收回搜索；按应用代理标题／输入框增加连续切换。它们仍是当前页面结构，不能宣称全部工具栏已与 Flutter 完全一致。

## 构建与 Redmi 验收

- 869 项 JVM（App587／Service213／Core38／Common31）零失败、错误、跳过；本组首次构建四模块已执行，最终 Debug 检查复用未变化结果。Debug、签名 Release/R8、motion 与测试包通过。Debug Lint 0错误／71警告／14提示，Release 0错误／68警告／13提示；两条新增 Modifier 参数顺序警告保留。扫码注册器最终 DEX 3/3 保留。
- Redmi Note 8 Pro API36、竖屏、字号1.0。组件8/8＋共享过渡7/7＋自然时钟深浅2/2，共17个不同方法通过。39张PNG完整校验；自然录像275／265帧，原PTS递增，各取24个原始帧位置。自然用例验证真实IME、搜索取消留层、一次候选回调与稳定父页；固定宿主不接Room或核心。
- 产品 motion APK 始终相同；首8项使用final3测试包，其余9项使用final5，变化仅为新增自然用例的定位与终帧等待，不改已执行8项源码。中间成功批次不累计到17项。
- 签名候选 SHA-256 `475e5a740395eb1befb73f628bcd531459c367f90540ccc0b47093ba3d7edcf2` 已安装回拉一致。正式首页、配置列表和Geo资源只读烟测通过；没有启动核心或点击更新。

## 完整 Flutter 运行对照的实际边界

完整固定源码在独立 `com.lonnnnnng.kclash.reference.full.dev` 安全模式包运行，用户自行确认首次声明；导入的临时文件与原 `1.yaml` 字节相同。已查看深色仪表盘、配置空态／列表／添加来源、真实 DocumentsUI、配置编辑／上下文及二级菜单、工具／Geo资源、进阶配置、规则／规则集／图库空态、扩展、MATCH-TARGET选择Sheet和添加规则类型菜单。

本地 FILE 配置没有 CUSTOM 的 NTP/Sniffer 编辑入口；全局规则集和图库为空，没有完整的非空 Provider／图标集对照。规则类型菜单只记录可见行，不把视口行数当成候选总数。完整 Flutter 本组只有深色观察，尚未完成所有子页、同数据截图或逐像素比对；不把 Kotlin 固定宿主录像算为 Flutter 全页连续动效证据。原版 `com.follow.clash` 始终未操作。

## 保留失败与数据变化

初次联合 Debug/Release 构建在 R8 报 Hilt 生成类缺失；完整强制重编 Release 并禁构建缓存后成功，未修改生成文件。首轮录屏驱动缺文件，仪器测试未启动；首自然测试错误地等待屏外HTTP，改为搜索过滤后定位。final4父页截图落在Sheet退场中，final5增加终态等待，深浅均通过。这些失败均保留在附件。

同签名探针确认安装边界 UID、四张业务表及配置文件保持。启动烟测后配置21字段／2条偏好／脚本／原正文SHA-size-mtime保持；测速表原始逐行比较失败。按 `(profileId, groupName, proxyName)` 完整键复核，238键与testUrl保持，238项value及updatedAtMillis更新，具体触发事件尚未归因。运行投影`mihomo/config.yaml`内容SHA保持、mtime更新；数据库WAL/SHM与ProfileInstaller文件变化单列。不回写旧快照，不认定历史异常已闭环。

4个独立测试／参考／探针包、68个已校验设备文件精确回收；正式候选保留、服务停止、系统设置与原1.yaml保持。证据：[验收JSON](acceptance/docked-search-redmi-20261110.json)，附件 `../../acceptance-artifacts/docked-search-20261110/`。

下一步补 CUSTOM 与非空资源的完整 Flutter 页面矩阵、其余工具栏／子页自然连续过渡，再继续菜单首开／关闭／RenderThread归因和历史异常长窗口。玻璃默认关闭；既有暂缓与取消范围不变。
