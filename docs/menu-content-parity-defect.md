# KUI-024：菜单胶囊变形与正文模糊

2026-11-09，继续已发布版本后的 Flutter 视觉对齐。基线 `d088f9ce`；参考固定 Flutter `be847366` / Prism `63450c0` 的 `popup.dart`，不合并 Flutter 提交，不改 Mihomo。

## 来源与差异

- `popup.dart:130` 的 `_beyondRound` 保留源按钮超出圆形的长边；Kotlin 原开场及回源主体只有圆形缩放项，胶囊按钮交接时形状不符。
- `popup.dart:923` 的源图在开场先模糊再隐去；Kotlin 原图像没有模糊且目标框按整数像素取整。
- `popup.dart:940` / `:1180` 的正文先变形，再在屏幕坐标模糊。Kotlin `AppMenuMorphFrame.blur` 虽已计算，实际未使用；`AppMenuStack` 的内层 Column 改用另一条线性曲线，且模糊会随外层缩放变窄，只模糊文字而非完整内容。
- 稳定回源应使用 Flutter `easeOut` / `easeIn` / `easeInOut`，源图从20%恢复、桥接前50ms增长。原来使用 Material 曲线及从0恢复源图，视觉时序不同。

Android16 `RenderEffect.cpp` 通过 `Blur::convertRadiusToSigma` 把radius换成sigma；`Blur.cpp` 为 `0.57735 × radius + 0.5`。Flutter `ImageFilter.blur` 直接接收sigma，不能把同一个数字作为Android radius。官方源码抓取正文保存在本机附件，不能只用参数名称推断单位。

来源：

- [固定 Prism popup.dart](https://github.com/chen08209/prism_ui/blob/63450c0/lib/popup.dart)
- [Flutter curves.dart](https://github.com/flutter/flutter/blob/master/packages/flutter/lib/src/animation/curves.dart)（抓取于2026-11-09，数值与本地参考使用的曲线对应；不是固定 Flutter 运行帧证据）
- [Android16 RenderEffect.cpp](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/libs/hwui/jni/RenderEffect.cpp)
- [Android16 Blur.cpp](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/libs/hwui/utils/Blur.cpp)

## 实现和边界

开场与回源补齐胶囊长边。正文和源图使用可复用 GraphicsLayer，在变形后的坐标中记录，再按屏幕sigma过滤，图层边界外扩3倍sigma；源图使用浮点变换和中等采样。正文移除原独立线性BlurEffect，统一读取几何帧。稳定菜单采用上游三条曲线、20%源图和50ms桥接。

正文另保留独立绘制节点，外层记录变形时引用该节点；子菜单、滚动及内容更新仍由内部正常失效，不把整张菜单冻成一次性位图。第一版没有这层隔离，正式包打开进程掉帧从75/206升至80/183、Popup从35/139变为35/121，原始数据保留。加隔离后同路径为73/206、Popup34/142；关闭从20/178变为19/167，折叠从19/132变为20/131。仅说明本轮恢复到基线附近，不认定显著收益，也不把UI/Render区间称为CPU归因。

快速撤销开场保留已显示帧的blur、源图alpha和桥接，再平滑进入回源；这是既有中断连续性的有意保留，区别于上游直接跳到稳定回源光学初值。胶囊几何和整体回源时间仍采用参考公式。`_glyphIn` 在本项目现有带背景源按钮下等于液滴矩形；没有凭空引入上游裸文字按钮的额外边距。

玻璃默认关闭。API31以下保持既有无RenderEffect降级；本轮只验专用Redmi API36，不以模拟器补低API。上游运行时逐帧像素一致、完整玻璃光学与其余子页仍未完成，性能须单独比较，不能把视觉修正写成性能优化。

## 验证

最终859项JVM结果零失败/错误/跳过（App577本轮执行、其余282复用），Debug、签名Release/R8、独立测试包及双Lint通过：Debug0错误68警告14提示，Release0错误66警告13提示。Redmi最终15/15、28张PNG经4拼图检查；正文原尺寸/半尺寸/源图的过渡宽度同为29px。8对正式终态排除顶部100px状态栏后逐像素一致。深浅视频104/102原PTS帧与输出张数一致，各抽样24张已看；两张玻璃开场截图仍在异步准备，不作展开通过。新增回归用独立贝塞尔数值检查回源、双密度横竖胶囊几何，以及真实绘制的高斯阶跃10%至90%宽度检查：卡片缩为一半后仍约2.56σ、源图也有模糊、清零后无残留。其余真实菜单选择、快速取消、嵌套返回和键盘链路沿用精确回归选择器。

本机附件：`../acceptance-artifacts/menu-content-parity-20261109/`。对照采集与截图/录屏分开，终态截图不能证明连续性能，进程与Popup帧分母不能混用。

最终APK SHA-256 `810a3195cd7dad45c70f0e8477af163394ea2e4fe1b802d22c8d8992739e277d`，正式签名指纹及安装回拉一致。手机保留该本地0.1.0修正版，公开Release不改；motion/test与4个精确采集文件已核对清理，深色/字号1.0、无服务，原1.yaml哈希不变。清理脚本曾把已卸载包的pm path退出1误判为失败，按现状恢复后验证完成；报告生成曾因清理证据未写出而拒绝，未提前记录通过。正式Room与正文未做逐字段保护回读，本轮不宣称其完整一致性。详见`docs/acceptance/menu-content-parity-redmi-20261109.json`。

下一步继续固定Flutter液滴/玻璃材质的剩余差异与真实准备阶段采样、其余子页排版；菜单首开和RenderThread慢帧仍未闭环。
