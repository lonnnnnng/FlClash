# UI parity

Flutter 参考实现是行为和视觉基线，Compose 是 Kotlin 的实现方式。复刻页面时先记录信息层级、状态、交互反馈、空态、错误态、加载态和横竖屏布局，再落到 Composable。

Design System 至少集中管理颜色、字体、间距、形状、图标、状态色和动效时长。不要在页面中散落 Material 默认值，也不要为了复刻 Flutter 而把 Flutter 代码或运行时带入 Kotlin 分支。

验收需要在浅色/深色、竖屏/横屏和不同字体缩放下检查；差异必须说明是 Android 平台约束、可访问性要求还是尚未完成的 parity 工作。
