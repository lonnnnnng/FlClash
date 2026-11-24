# 弹框首次测量开销

记录日期：2026-11-24

## 问题与改动

订阅表单从来源菜单首次打开时，POCO 的 Perfetto trace 记录到一次 AndroidOwner:onMeasure 持续 31.838ms。源码审查发现 ProfileFormRow 在每一行通过 BoxWithConstraints 读取标题宽度；订阅输入页有多行，测量过程中会多次组合标题区域。

本组将逐行 BoxWithConstraints 改为 Modifier.layout，直接按标题收到的剩余约束计算宽度上限。宽度规则保持不变：标题最多使用扣除行内 16dp 间距后的剩余空间一半；有 leading 控件时，Row 先测量并扣除其触点宽度。代码位置为 android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/ProfilePageComponents.kt 的 ProfileFormRow。

这项修改减少了测量期的重复组合，但 trace 只能支持它是一个有贡献的开销点，不能证明它是唯一根因。

## 测量结果

POCO F2 Pro / API 36 同一路线冷开采集的测量窗口中，基线 AndroidOwner:onMeasure 为 31.838ms；候选两次为 25.331ms 和 27.429ms，下降约 14%–20%。测量窗口内 Compose:recompose 切片由 4 段减为 1 段。订阅表单三行标签和占位文字位置与基线一致。

Popup 到 Dialog 的间隙基线为 12.719ms，候选为 12.993ms 和 13.095ms，未改善。gfxinfo 开合 jank 中位数没有形成稳定的全局收益证据。首窗测量仍约 25–27ms，不能据此宣称首帧问题已解决。

## 回归与边界

最终源码在 POCO 的隔离 Motion 包上通过真实 MainActivity 用例 1/1：无效订阅地址校验、真实 IME 导航、隐藏键盘、Activity 重建后保留名称/地址草稿、age 密钥清空、返回关闭，以及正式库/脚本/正文/偏好/选择不变；运行态保持 STOPPED。用例不提交导入、不访问网络。Debug、未签名 Release-R8、Motion 与 Motion AndroidTest 构建通过；Motion JVM 634 项通过；Debug、Release、Motion Lint 均 0 错误、11 警告、1 提示。

本组已随 KClash 0.1.20 正式发布。POCO 正式包仍为 0.1.18，本轮 Release APK 未安装；独立 Motion 与 Performance 包已从 POCO 卸载。Material3 AlertDialog、其它自绘输入 Dialog、完整 Flutter 页面等价、核心运行时性能和菜单 GPU/fence 归因不在本组结论内。

首次自动化连接测试使用 Gradle connectedMotionBenchmarkAndroidTest。尽管传入 device serial 属性，AGP 仍将同一隔离用例分发到所有在线设备，包含 Redmi Note 8 Pro。该轮结果不纳入验收；随后已用 adb -s 31936bb6 单独复跑 POCO 并得到 1/1 通过。为遵守设备边界，没有再查询 Redmi；相关自动化日志与 POCO-only 复跑日志均留在验收材料目录。

详细结果和哈希见 acceptance/profile-form-measurement-poco-20261124.json，以及仓库旁 acceptance-artifacts/dialog-first-measure-poco-20261124/。
