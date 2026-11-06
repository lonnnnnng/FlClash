# 代理 List 重建恢复偏移与吸顶标题消失

- 编号：KUI-001
- 日期：2026-11-06
- 归属：Kotlin Compose 页面集成；不是 Flutter/Mihomo 上游源码缺陷。
- 引入来源：修复前基线 `d186b653` 的 `ProxyGroupsPage.kt`，`ProxyGroupsListPage` 直接使用 `rememberLazyListState()` 恢复位置。
- 验收证据：[MainActivity 往返与重建](acceptance/main-navigation-state-redmi-20261106.json)。原始日志、截图和诊断源码留在外层 `acceptance-artifacts/main-navigation-state-redmi-20261106/`，不上传用户配置截图或正文。

## 复现与已确认事实

Redmi Note 8 Pro / API36、arm64、1080×2340 竖屏、默认字号。真实 MainActivity 通过正式 Repository 创建原始 `1.yaml` 的临时副本，切为 List、展开末组、滚到末节点，再重建 Activity。

跨配置页往返正常；Activity 重建后，布局选项、展开集合和分组选择仍在，但末节点 top 从 1792px 变为 1806px。列表视口始终为 `(0,309)-(1080,2081)`，滚动语义从 19594 变为 19580。临时日志进一步确认首个可见索引仍为 39，偏移由 94px 变成 80px。吸顶组头仍出现在布局信息中，实际截图却没有绘出。

首轮六方法 4/6：List 恢复失败与搜索清空断言失败分别保留。搜索失败时 `AnnotatedString` 对象比较未通过，日志却已显示空文本；改为比较目标明确的 `EditableText.text` 后通过，不计产品缺陷。List 经延后截图/采样仍稳定复现，diagnostic2、diagnostic3 的失败不能记作通过。

## 上游参考与修复范围

只读参考本地 Flutter `lib/views/proxies/list.dart`：`ScrollController` 与 `proxiesListStoreKey` 负责滚动记忆，独立分组 sliver 保留标题，已展开组的动画控制器初值为 1。这提供浏览状态应保留的交互语义；没有把 Flutter 代码合入 Kotlin，也没有据此认定 Flutter 有同样缺陷。

Kotlin 原先只依赖懒列表 Saver 的首次恢复。现在进入列表时先冻结已恢复的索引/偏移，等待首个非空实际布局，再以 `scrollToItem` 应用同一位置；每次列表实例只执行一次，不影响后续用户滚动、排序/密度锚点、定位或组折叠操作。未增加时间延迟、固定像素补偿或放宽断言。

diagnostic4 保持原 1px 容差通过，恢复前后均为索引 39 / 偏移 94px，节点坐标一致，截图吸顶标题恢复。最终测试额外比较组头四边坐标，避免仅恢复节点而漏验标题。

已定位到首次布局恢复阶段；AndroidX 内部布局、lookahead 或窗口测量的更细分原因尚未独立证明，不将其登记为库缺陷。

## 后续同步与撤销条件

没有 Mihomo gitlink 或 Flutter 提交需要回合。以后修改 Compose 版本、主页面转场、窗口 inset 或 List 布局时，先运行 `MainNavigationStateInstrumentedTest#listLayoutAndScrollSurviveProfilesRoundTripAndActivityRecreation`，同时复验 List 定位、吸顶与展开/收回。只有移除本次二次应用后，相同设备和断言仍通过，才考虑撤销这段恢复处理。

本组停止态验收不证明冷进程死亡、运行态核心/VPN或完整 Flutter 视觉。正式 FILE/URL 两配置的正文/元数据/偏好/选择均由前后摘要保护；临时副本只按自身 ID 和唯一名称回收。
