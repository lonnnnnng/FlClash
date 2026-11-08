# 菜单截图准备与窗口计数复核（KUI-021后续）

2026-11-09，基于已交付的KUI-022 `eb9b7b8f`。本组没有保留产品改动；交付离线分窗口帧表工具、候选负结果和当前测量边界。手机恢复到同签名KUI-022优化版，网速卡紧凑修复保持。

## 源图像准备核对

固定Prism `63450c0` 的 `popup.dart::_paintFrame` 在已有sourceImage监听对象但图像未就绪时直接返回，源按钮继续显示；`_captureSource` 在绘制后取得快照才隐藏原按钮。Kotlin `AppMenuSource.capture()`异步读回、随后才设置hidden。真实Redmi开场单次4秒录屏的0.5–1.6秒区间回收31个原始PTS对应帧，未观察到整菜单先闪现再缩回的猜测，不能凭静态分支切换断定现场有闪烁。录屏仅作视觉诊断，不进入性能分母；提图器的非单调DTS警告保留，按原视频PTS和31帧数量核对，未补帧或改写时间轴。

## 两个候选均撤回

1. 截图开始时即暂停源按钮涟漪，在finally恢复准备标记；正式包打开和关闭均退化，撤回。
2. 隐藏按钮时清空原GraphicsLayer绘制记录，菜单仍使用独立截图；未减少背景窗口动画，也未改善Popup，撤回。

同一Redmi/API36/440dpi/竖屏/字号1.0，核心停止、玻璃关闭、原配置复用。每个包深浅主题各冷进程三轮，性能窗口无录屏/PNG/Instrumentation；每批24/24终态检查通过。包依次测量，非随机实验，不声称统计显著性。

| 实现 | 进程打开jank | 进程关闭jank | Popup打开jank |
| --- | --- | --- | --- |
| KUI-022对照 | 62/210（29.52%） | 17/179（9.50%） | 21/149（14.09%） |
| 截图准备即暂停 | 73/205（35.61%） | 21/178（11.80%） | 25/146（17.12%） |
| 清空隐藏图层 | 65/206（31.55%） | 17/180（9.44%） | 26/145（17.93%） |

构建成功只支持候选可以运行，不表示优化通过。两个签名Release构建成功；清空图层的motion/AppTest也构建成功，但没有安装/执行这套夹具。未新增产品逻辑，未重跑854项Android JVM或双Lint，不以旧绿测冒充本轮结果。

## 分窗口和FrameInfo标记

读取Android16固定源码 [`FrameInfo.h`](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r3/libs/hwui/FrameInfo.h)：WindowVisibilityChanged=1、RTAnimation=2、SurfaceCanvas=4、SkippedFrame=8。标记10同时含RTAnimation和SkippedFrame，不能将其计为普通UI方法帧，不能把两种位计数相加。

对照包六次打开的MainActivity统计为41/55，原始重置后帧表61行，其中12行Flags=0、49行含RTAnimation、6行含SkippedFrame；Popup为21/149，重置后143行。原始窗口计数与有限帧表并非同一个分母。过去报告的进程总卡顿率继续作为原始统计保留，不能改称“肉眼可见菜单独自的卡顿率”。KUI-022前后Popup本身也由47/123降至25/146，其改进证据仍成立。

新增 `tool/android_menu_frame_report.py`：

```sh
python3 tool/android_menu_frame_report.py --expected-window 弹出式窗口 --output report.json input.gfx
python3 -m unittest discover -s tool -p test_android_menu_frame_report.py -v
```

工具不接设备，只解析输入文件；分别保留进程与窗口原始计数，各窗口按自身Stats since过滤帧表。Flags=0有效时间戳的UI/渲染区间单列，缺少或倒退时间戳拒绝参与耗时，不用0补齐。区间仍是经过时间，不是CPU/GPU归因。目标Popup不存在时明确输出不可观测警告，不把它生成成零帧窗口。

四项测试覆盖不同窗口重置时刻、销毁Popup、重叠标记/坏时间戳、截断/非数字帧表。本轮72份真实gfx成功解析；18次关闭均已看不到Popup，全部按采集缺口处理。关闭进程累计jank仍保留，但不能用窗口消失后的单行宿主记录给Popup做线程归因。

## 下一步与收尾

下一步按Popup独立数据继续优化，并先核对实际Flutter绘制差异：上游 `_paintShadows` 给platter投影并扣除联合轮廓内部；当前Kotlin `AppMenuFrameShadow` 对联合轮廓使用dropShadow。回源时上游还同时处理正文自身阴影与外层阴影强度，需连同裁剪/透明度一起审查，不能孤立改阴影或把旧Kotlin像素相等当完整Flutter一致。原生RenderThread采样仍有KUI-022记录的ROM限制。

产品源码恢复到HEAD，手机APK回拉SHA-256为 `8e2f579c1f23377b57322ab31fd87b91c4798e6f7f5608f9df4c76490005d110`。原1.yaml哈希保持、深色/字号1.0、服务停止，自有视频和XML核对后删除；Flutter包未操作、正式数据未清除。证据 `docs/acceptance/menu-source-preparation-redmi-20261109.json`，本机附件 `../acceptance-artifacts/menu-preparation-20261109/`。
