# Flutter 搜索容器与连续动效参考

2026-11-10，接续 KUI-030。固定 Flutter `be84736605527034689c95bf27828ff27d912c5e`、Prism `63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185`、Flutter SDK 3.47.2。只作 Kotlin 实现参考，不合并 Flutter 提交。

## 已核实的入口差异

| 入口 | 固定源码 | Redmi 独立宿主观察 |
| --- | --- | --- |
| 手机根页，有搜索按钮组 | Scaffold 的 `_buildBarSearch`、`_SearchMove` | 点击后输入框占据顶栏，自动显示 IME；过滤、收键盘、取消搜索、再打开均可完成；取消清空查询并恢复标题/按钮组 |
| 普通推入子页，无底栏 | `_SheetForm.of` 的 `docksSearch` | 搜索常驻下沿，焦点/非空时从右侧展开取消；IME 显示时位于键盘上方；取消清空但不退页 |
| 紧凑 Sheet | `surface.isCompact` 选择 dockedSearch | 本组只读源码，未用实际 Sheet 宿主验收 |
| 显式 searchInBar 的推入页 | 保留顶栏路径 | 宿主入口已备，未执行独立场景 |

真实代理根页 `lib/views/proxies/proxies.dart` 提供 `searchState`，搜索中保留 primaryAction。更多设置添加候选使用 `showSheet → SelectionSheet`，不能仅凭“可搜索”就一律改成顶栏展开。

## 运行参考及限制

仓库外独立包 `com.lonnnnnng.kclash.reference.search_reference` 使用原样的 `lib/ui/scaffold/{scaffold,scaffold_actions,scaffold_search}.dart`，三文件与固定 Git 内容逐字节一致。页面数据为24条普通文字，不接核心、配置、Room或网络；没有安装/修改 `com.follow.clash`。

Redmi API36、竖屏、默认字号：根页搜索打开→输入23→系统Back收IME→取消→再打开空查询→再关闭；推入页下沿搜索输入Resource 23→只余一项→取消清空→返回根页。保存了UI树、截图、原始录屏。录屏777帧、PTS严格递增，32个采样位置核验到标题/按钮淡出、搜索展开及关闭时按钮组恢复；主机动作时间与视频PTS没有严格时钟同步，不能据此声称精确动画毫秒或渲染性能。本轮仅浅色原组件状态，未跑完整Flutter应用或深浅全矩阵。

首版宿主缺ViewScope导致ErrorWidget；第二版正文忽略浮动顶栏inset而重叠。均只补宿主上下文/`sectionsTopPadding`，原Scaffold未改，失败日志保留。抽帧旧`-vsync`参数被本机FFmpeg拒绝后改`-fps_mode passthrough`，按原始帧索引取帧，未重新采样伪造帧率。

安装回拉APK哈希一致；录屏按SHA核对后从手机定点删除，独立参考包已卸载，正式Kotlin包和原配置未操作。摘要见 `acceptance/search-motion-reference-redmi-20261110.json`；本机附件 `../../acceptance-artifacts/flutter-search-host-20261110/`。

## 下一实现切片

1. `ProxyGroupsPage.kt` 的 `ProxyPageToolbar` 仍直接分支替换标题与输入框；先补代理根页按钮组到搜索框的连续过渡，保持搜索中批量测速、取消清空、返回层级和现有选择。
2. 以固定 `_SearchMove` 的开/关时序、当前位置/速度反转作为依据，覆盖快速开关、真实IME、清除与取消区别、切页/重建。先验证普通材质；玻璃默认关闭，不能把背景采样优化混入常用交互。
3. 资源选择和添加Sheet随后按真实容器补下沿搜索；KUI-030的首次显现、末项/键盘、别名与一次提交回归必须保留。紧凑Sheet和完整页面尚未有本轮运行对照，继续记PARTIAL。
