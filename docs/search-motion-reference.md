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

## 后续切片与已完成范围

1. 代理根页已由KUI-031补齐按钮组到搜索框的连续过渡与单按钮液滴回收，深浅/真实IME/快速反向/Activity重建/自然整页及正式候选已验；见 `proxy-search-motion-defect.md`。其他根页工具栏仍需逐项核对。
2. KUI-032 已在资源绑定、Provider、图标/图标集和Sniffer添加候选中接入48dp下沿搜索，并为正文、脚本、规则、运行配置、观测与按应用代理搜索补连续状态过渡；Redmi共17个具名方法通过。详见 `acceptance/docked-search-redmi-20261110.json`。
3. 完整Flutter安全模式包已在Redmi运行只读观察，但本地FILE没有CUSTOM编辑器，全局规则/Provider/图标集为空；规则类型菜单只记录当前可见行，不据此推断总候选数或搜索阈值。非空资源选择、完整子页和完整页面像素对照仍PARTIAL，不通过生成候选或选择写入来填补前提。
4. 菜单首开/关闭、RenderThread原生慢帧归因、剩余工具栏与子页自然连续过渡仍待验。玻璃默认关闭，性能采样与视觉录屏分开，不作跨运行性能结论。
