# KUI-VIS-071 首页启动按钮状态轮廓

2026-11-21，POCO F2 Pro/API36。此项只还原首页核心按钮在停止与运行状态间的轮廓变化，不代表首页其余元素、完整Flutter运行对照或整页转场已经完成。

## 实现

形状端点和控制点由固定Flutter `Morph(MaterialShapes.circle, MaterialShapes.cookie9Sided)` 输出；Kotlin使用同一组匹配三次曲线，在450ms内插值，并按进度旋转 `2π/9`。正向使用上游 `easeOutBack`；反向对 Compose 的递减目标换算上游 `easeInCubic`，快速反向从当前进度继续。动画进度在Shape绘制时读取，运行时间、出口信息等正文不随每个动画帧重新组合。

普通填充、裁剪、焦点轮廓和玻璃采样共享同一个Shape，外层88dp点击区域保持。玻璃开启时，将当前曲线转换成64方向轮廓，并由AGSL使用Catmull-Rom半径及导数计算折射法线和边线；静止且相同尺寸时复用采样。玻璃设置仍默认关闭。

可追溯生成器为[`tool/export_runtime_shape_reference.py`](../tool/export_runtime_shape_reference.py)，独立Flutter曲线/栅格测试为[`tool/runtime_shape_oracle_test.dart`](../tool/runtime_shape_oracle_test.dart)。它们只读取固定Flutter源码，未启动或修改设备上的 `com.follow.clash`。匹配曲线已写入`RuntimeToggleCurves.kt`，参考路径采样进入Android测试资源。Flutter文件摘要与POCO截图、构建、回归记录见[验收JSON](acceptance/runtime-toggle-shape-poco-20261121.json)。

## 验收

固定POCO屏幕/API/旋转/字号，停止核心，只以Compose状态夹具切换状态；不读写用户配置，也未启动VPN。

- 首轮集成回归15/15，包括按钮启停忙态防重、双主题、玻璃开关、运行卡高度、现有实时材质、菜单Dock拖动和焦点环；23张PNG哈希及尺寸回验并目视。
- 最终绘制定向回归3/3，8张按钮PNG：捕获中间帧证明轮廓改变了真实画面；反向后轮廓像素回到圆形；停稳后像素不再变化。
- 对Flutter独立导出的0、0.25、0.5、0.75、1和1.1进度栅格，Kotlin轮廓每行左右边界允许1物理像素误差；64方向轮廓采样与Flutter `PathMetric`结果在0.6像素断言内通过。
- App Motion JVM 628/628；Motion APK/AndroidTest、Release-R8与Performance-R8构建通过。Motion Lint为0错误/75警告/15提示，Release Lint为0错误/71警告/14提示；Release ML Kit DEX注册器门禁3/3。

POCO上安装的Motion、test和performance独立包均按SHA回拉后精确卸载。包清单、原版Flutter安装路径/版本/更新时间、旋转、字号和系统夜间模式保持。本组未发布，公开版仍为0.1.12。

## 未完成

上游在形状内部还会将播放符号变形为暂停符号，Kotlin按钮目前仍用系统电源图标；按需暂停的Wi-Fi符号也未对齐。没有安装/运行上游Flutter应用做全屏比较，其他面板和页面连续转场仍待验。玻璃默认关闭；任意重叠pane的atlas分层、完整光学捕获和全页采样策略仍在KUI-VIS总项中。
