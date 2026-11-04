# UI parity

Flutter 参考实现是行为和视觉基线，Compose 是 Kotlin 的实现方式。复刻页面时先记录信息层级、状态、交互反馈、空态、错误态、加载态和竖屏默认字号布局，再落到 Composable。

当前优先级固定为主页 Dashboard → 配置 Profiles → 代理组 Proxy Groups，逐页面、菜单和按钮收敛主流程；Requests/DNS/Logs 的进一步视觉打磨暂停，主流程完成后再恢复。每个已验证切片及时同步 `docs/roadmap.md`，未完成的结构化编辑不能以现有 YAML 弹窗代替完成状态。

Design System 至少集中管理颜色、字体、间距、形状、图标、状态色和动效时长。不要在页面中散落 Material 默认值，也不要为了复刻 Flutter 而把 Flutter 代码或运行时带入 Kotlin 分支。

验收范围固定为竖屏和系统默认字号；用户明确不需要横屏或大字号测试，后续不得运行这些测试，也不要运行包含这些用例的整类 instrumentation。深浅主题可按当前页面需求验证；其他布局差异需说明是 Android 平台约束还是尚未完成的 parity 工作。

## 最新菜单/子页切片（2026-11-04）

共享regular/pill/menu/alert/tinted/lens材质与STANDARD/ULTRA等级、共用液滴回源、可逆多层展开、策略组/更多/链式代理覆盖子页及API27离线fallback已验，玻璃默认关闭；Redmi final6 19/19、API27 final6 10/10，详见`docs/acceptance/general-glass-subpages-api27-20261104.json`。完整视觉仍PARTIAL：预模糊近似，API24/25、31/32及其他ABI/实网未验；其他空态/页面、完整采样品质、全应用逐像素一致性和性能验证待补。早期Room元数据兼容不属于开发阶段要求；Redmi正式配置/选择/正文聚合摘要的count=1且before/after SHA一致，逐字段摘要未嵌入JSON；API27设置记录为null，不能据此确认恢复。主题/语言设置继续暂缓。
