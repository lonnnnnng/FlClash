# 全局规则自然转场与 DNS 参考对照

2026-11-23，KUI-VIS-093。验收设备仅 POCO F2 Pro（31936bb6/API36），玻璃关闭。公开版仍为0.1.18，本组不发版。

## 本次发现与修正

规则表单在键盘出现时几乎拉满屏幕。`ExtensionRuleForm` 虽然启用了 `fitFormOverKeyboard`，却没有传入 `naturalContentHeight`；`ConnectionSheetGeometry.formHeight()` 因此把正文按整页计算，无法先消耗短表单的底部留白。已有 `ResourceNamedUrlSheet` 同类实现会上报正文实测高度。现在规则表单同样测量不受滚动视口压缩的正文，再加68dp标题/拖柄区，复用共享几何与弹簧；不修改共享窗口算法。POCO修改前截图与最终截图保留，最终自然测试同时确认真实IME可见、标题保留在屏幕下部以及收键盘后可继续操作。

非空全局规则原先是72dp起的原始逗号串与箭头。参考 `AddedRulesView` / `RuleItem` 后，改为紧凑分组卡片：左侧类型/匹配内容，右侧语义目标色与圆形勾选。已有括号感知的 `ExtensionRules.fields()` 仅用于展示，保存字符串不变，`no-resolve`不误作目标。补上勾选进入/退出批选的真实触点验证。字体沿用Android系统字体，未引入Flutter字体运行依赖。

## 固定参考与结论范围

Flutter使用本地固定 `be84736605527034689c95bf27828ff27d912c5e` 的原始 `AddedRulesView`、`RuleForm`、`DnsView`，Prism固定 `63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185`。独立包 `com.lonnnnnng.kclash.reference.flutter_rules_dns_reference` 使用内存规则提供者；页面、表单与路由仍来自参考源码。它是组件参考宿主，不是完整Flutter MainActivity/Drift持久化验收，也不能代替当前上游 `afe71360` 新增的自定义出口/MITM说明等行为。原版 `com.follow.clash` 未启动或改数据。

Kotlin使用独立 `.motion` 包的真实 MainActivity、ViewModel、Room和加密草稿链路。新自然用例没有ComposeTestRule：实际触摸、系统Back和系统IME运行；正文通过Android语义的ACTION_SET_TEXT注入，并非逐键打字。只允许空配置/无当前选择/STOPPED的隔离包，不会把测试规则应用到正式VPN。完整偏好行、脚本、配置数/选择、草稿与回执在finally严格回读和还原。

两端连续录像包含规则列表→添加→IME→Back→取消→重新添加→保存返回→重开编辑→关闭。Kotlin保留已实现的放弃草稿确认和原始规则编辑入口，未为了截图一致删去保护。DNS两端补空态→添加选择器→滚动→搜索/键盘→返回。录像按原PTS抽帧并记录帧号；不同驱动的等待、编码静帧间隔与Flutter/Kotlin入口不同，不能用视频总长或抽帧间隔代表响应时间、jank或逐帧同相位等价。

## 仍未完成

- 093已补齐上述固定参考路线的自然过程证据；完整最新版Flutter MainActivity同态对照、规则长列表拖拽/滚动、任意规则类型及浅色过程仍待。原子项保留PARTIAL，不把一次深色路线算完整视觉完成。
- DNS空态纵向位置、插图细节、按钮材质/文案仍有差异；Kotlin工具子页保留Dock，上游参考整页没有Dock。选择器字段集合亦不一致，例如参考有Prefer H3；这一项属于功能映射核对，不能只补一个可见空选项。
- 主标题、搜索表面与部分字形/间距仍非逐像素一致。参考宿主字体/资源加载与完整原版不同，不能用此处像素替换整应用基线。
- 菜单discarded owner、GPU/fence与正式R8稳定收益未在本组采样，不新增性能结论。

原始资料与汇总见 [验收JSON](acceptance/global-rules-natural-route-poco-20261123.json)。下一组优先DNS整页/字段添加面板和规则长列表，随后继续其他子页；既有冻结/暂缓范围不变。
