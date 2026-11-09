# KUI-029：添加候选连续退场与分组标题

2026-11-09，基线 `00c353c9`。本组只适配 Kotlin 更多设置添加面板，不合入 Flutter 提交。

## 固定来源与缺口

Flutter `be84736605527034689c95bf27828ff27d912c5e` 的 `lib/features/form/selection_sheet.dart` 使用每条独立的 `EntryTransitions`；最后候选关闭面板，分组标题随该组最后一条同步退场。Prism `63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185` 的 `lib/animated_entries.dart:132` 使用 300ms 线性时钟驱动 `easeInOutCubic` 高度和 `Interval(0.5,1)` 透明度。退场禁点击，文字裁剪而非纵向压缩。

Kotlin 基线在成功回执改变 `remaining` 后直接移除行，后续候选和分组标题跳动；没有对应的连续退场。此为 Kotlin 适配缺口，不是上游源码缺陷。早期未提交实现将 divider/row 同置 Box、搜索取消动画后无法清理候选，两者在本轮静态复核时已修，未作为交付版本。

## 实现与边界

`SelectionEntryTransition` 在 layout/graphicsLayer 读取时钟；条目仅裁剪高度、淡出内容，300ms结束后由父层移除身份。`ProfileMoreEditor` 保留成功提交的离场项，保存中/离场中不能再次提交；保存失败仍原位保留。每项独立时钟，连选不会重启先前动画。搜索移出已保存候选时父层立即释放身份，防止动画协程取消后残留空分组。分组标题随最后条目开始退场；最后一个可用候选仍等待保存revision成功后关闭整层。

固定 Flutter 组件宿主在仓库外生成，包名 `com.lonnnnnng.kclash.reference.motion_reference`；复用原 Prism 组件，不加载配置、核心或网络。没有安装或改动 `com.follow.clash`。Flutter SDK 固定 3.47.2，宿主和参考源码 SHA 见验收清单。它验证组件动态行为，不代表完整 Flutter 应用页面、工具栏或所有布局逐像素等价。

## 验收与后续

最终结果见 `acceptance/selection-motion-stability-redmi-20261109.json`。无截图的菜单关闭帧诊断与录屏视觉窗口独立；后者不能作为性能成绩。完整工具栏搜索、长资源目录与IME、完整固定Flutter页面运行对照继续待补。

后续同步固定上游时，核对 EntryTransition 曲线/时长、最后候选退出以及标题时序；只有对应交互与中间帧通过才调整本地实现。

## 最终结果

最终build4/fixture6+8/lint5通过：860项JVM（App578执行、其余282复用），Debug/签名Release/R8/motion及双Lint0错误，Debug69警告14提示、Release66警告13提示。Redmi final3同套13/13、自然1/1；32PNG按分片/长度/SHA核验并检查4张拼图。Flutter74帧/Kotlin238帧原PTS视频与条目退场抽样已检查；Kotlin BottomSheet底部锚定会随内容缩小移动容器，不能与独立Prism列表逐像素等同。

保存失败/连选/搜索切换通过；focused1的1/2为文字匹配父页歧义，final1的12/13为菜单回源回调过早断言，natural-first为Dialog未继承宿主测试标签，均保留并修正测试时序/定位，不放宽产品断言。正式签名包a72a955a安装回拉、原配置列表/首页烟测通过；诊断双包和独立Prism参考包已卸载，公开0.1.0不覆盖。
