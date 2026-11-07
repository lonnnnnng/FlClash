# KUI-011：配置管理窗口冷恢复与提交结果

状态：本组范围已验收。Redmi final7 冷恢复17/17、再次恢复3/3，相邻回归11/11；814项JVM、Debug/Test、未签名Release-R8及双Lint通过。完整Flutter视觉、运行态死亡和OEM自然回收不在本组结论内。

## 源码问题与参考

2026-11-07，在 `9335edd9` 基线上检查到 Kotlin 配置重命名和删除在发起异步写入前清除窗口 ID；失败只剩 Snackbar，无法在原窗口重试。排序窗口保存初始 `orderRevision`，新进程 ViewModel 的计数从零开始，不能用旧窗口版本可靠判断新提交成功。三类窗口此前仅有 Activity 保存状态，没有独立跨进程草稿和提交意图。

Flutter 参考：`lib/views/profiles/edit.dart` 的 `renameProfile` 先关闭输入框再写入；`lib/views/profiles/profiles.dart` 的 `_ReorderableProfilesSheetState._handleSave` 先退出再补齐最新配置集合。只借鉴名称校验与局部排序交互，不移植 Flutter 提交，不把 Kotlin 的冷进程问题归因于上游。同步上游时应复核交互差异，保留本地确认与失败恢复协议。

## 修复映射

- `ProfileManagementController`：单一窗口会话和同步 busy 认领；原名称、原顺序、操作意图及 UI 树通过独立 `profile-management.v1` Keystore/AtomicFile 密文保存，不进入系统 Bundle 或备份。
- 保存前先持久化 pending，再经过唯一运行管理器；成功写 committed 后才清草稿。恢复只读取正式结果，不自动重放。未完成等待明确重试；已完成只提示关闭。
- `ProfileManagementHost`：重命名/删除失败原位留窗，排序放弃确认和滚动进入独立窗口保存树；名称文字只由业务草稿持有，保存树只留选区，避免两份异步文字不同步。
- `ProfileRepository.compareAndRenameProfile/compareAndReorderProfiles`：在 Room 事务内复核名称和顺序；成员删除与末尾新增可合并，外部排序变化拒绝覆盖。
- 删除先提交 Room、后清理对应正文。冷恢复识别未清理文件，显式重试只处理原 ID；清理未完成时不能取消并抹掉重试入口。清理失败不让核心管理器回滚一条已删除的记录。
- Room 异步读取返回时复核整个内存快照，输入、选区或 busy 已变化则重读，不回写旧状态。旧名称超过 64 字符时仍允许进入重命名或删除。

## 当前验证与边界

首轮 Redmi `final1/rename-draft` 恢复失败：`ParcelableSnapshotMutableState cannot be cast to MutableIntState`，栈定位 `ProfileManagementHost.kt` 选区状态。新增窗口误用了与当前独立保存树不兼容的 primitive state 声明；改用普通 `mutableStateOf(Int)`，保留原失败日志和现场，不清用户数据。原密文恢复与后续新版本集中验收分别记录如下。

修复包回读原失败现场 `recovery1`：名称保留、页面可打开，但选区 `(3,10)` 变为 `(0,0)`，因此仍记失败，不推断旧崩溃过程与源码键变化各自影响。`recovery2` 按原记录精确清理本例副本与草稿，正式行/正文/脚本/偏好/选择保护通过。随后同一修复版本 `final2/rename-draft` 首次与新进程恢复通过，包含精确选区和原始首尾空格；该中间结果不替代整组验收。

后续测试问题单独保留：`final2/rename-before` 在 Bundle 保存回调后继续语义点击，出现 `No compose hierarchies found`；检查改到交互之后，`recovery3` 精确清理通过。`final3/rename-before` 在保存门闩持锁时等待后续 UI 树落盘超时，改为提交前等待完整树、提交后只核对意图，`recovery4` 清理通过。`final4` 已通过前 10 场及 2 次再次恢复，随后 `delete-deleted` 在准备阶段等待屏幕外副本节点超时；改为等待仓库列表并显式滚到操作目标，`recovery5` 清理通过。未用中间成功片段拼出整组通过结论。

独立复核追加排序边界：已观察到的新增成员纳入持久比较基线，两个新尾项随后被外部互换时也拒绝覆盖；新增 JVM 冷恢复回归。产品源码从 `build4` 起保持，`build5/6/7/8` 只修测试准备、验证顺序和增加实际结果断言。

`ProfileManagementControllerTest` 的12项测试覆盖三种操作的意图/提交窗口、再次恢复、失败/重试/防重复、外部删除/改名/排序、成员集合变化、密文读写失败、提交后尚未确认、孤儿正文清理、异步读取与超长旧名称。现有 `ProfilesViewModelTest` 定向回归通过。

`final5` 完整17场景及3次再次恢复通过，独立保留。随后强化正式结果回读断言；`final6` 首场通过，第二场准备立即断言页面显示时遇到转场时序失败，`recovery6` 精确清理和保护通过。`build8` 改为等待真实页面显示后，`final7` 全17场景及3次再次恢复通过，包含提交前后、失败重试和提交后恢复的Room名称/顺序与正文签名核对。没有用中间批次拼接最终结果。

相邻 `regression1` 的11项 instrumentation 全部通过并回传33图；驱动最初只列31图，漏了导航循环动态生成的 `main_navigation_tab_after_profiles` 和 `main_navigation_tab_after_dashboard`，在图片集合断言处退出1。原脚本与原日志保留，按源码补齐期望集合后离线复核原33图集合/字节/哈希、全部方法结果、安装前后及验收前后保护均通过；没有重新运行设备测试，也未丢弃多出的图片。33图经11张拼图目视，目标窗口/草稿/选择/IME和导航状态正常；滚动裁切位置不作为完整静止排版基线。

最终 `build8` 814项JVM零失败/错误/跳过；App533在本组build4实际执行，其余Service212/Core38/Common31复用，build8四模块均UP-TO-DATE（389任务11执行/378复用）。Debug Lint0错误/67警告/12提示，Release0错误/66警告/11提示。首轮Release检查保留检查点枚举字符串的问题已在build2改为生产直接调用后消除；最终R8草稿字段保留、无测试Activity/检查点/SQL故障标记，未签名包仅作构建和静态检查。正式签名运行未验。

验收证据：`docs/acceptance/profile-management-cold-redmi-20261107.json`；原始附件：`../acceptance-artifacts/management-cold-20261107/`。安装回拉与最终包匹配，572份Android源码哈希一致。

正式验收只复制外层原始 `1.yaml` 建立本例配置，保留正式 FILE/URL 两配置的全部行、正文 SHA/size/mtime、脚本、偏好值和当前选择；不用替代 YAML，不修改 Flutter 包。停止态 force-stop 不等于运行态死亡或 OEM 自然回收。完整 Flutter 视觉/连续动效、扩展页冷恢复、运行态 Provider/MRS 补验仍独立列入 todo。
