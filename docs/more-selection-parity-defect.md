# KUI-028：更多设置与资源选择视觉

2026-11-09，接续KUI-026和菜单调查KUI-027。参考固定Flutter `be84736605527034689c95bf27828ff27d912c5e` / Prism `63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185`，只在Kotlin现有状态/持久化链路实现。

## 来源与差异

- Flutter `lib/ui/null_status_illustration.dart` 为NTP/Sniffer指定clock/eye；Prism `null_status.dart` 采用120/160dp复合插图和900ms入场。Kotlin此前仍用72dp系统Schedule/Radar。本组复用既有复合插图，并从固定`app_glyphs.dart`补clock几何；屏高560dp以下用120dp。形状/入场采用现有确定性样式，不宣称等价于Flutter随机抽样的全部变体。
- Flutter `override.dart` 的添加候选来自 `SelectionSheet(removeOnSelect:true)`，分组行无重复加号，候选至少10个或已有查询时可搜索。Kotlin Sniffer的11个候选没有搜索、最后一项添加后仍停空页。本组新增过滤/清空、分组连续圆角行，最后一项必须收到保存revision才关闭；失败留层重试。空态只保留正文添加入口。
- Flutter `sniffer.dart`→`config_item.dart`→`list_item.dart` 使用行尾当前值与锚定Popup选择覆盖目标。Kotlin原为副标题+AlertDialog/RadioButton。本组使用行尾源快照菜单和已选标记，`dismissOnClick=false`等待实际持久化回执；失败原位重试，不丢弃原值或提前关闭。首版整行快照使标题在菜单打开时隐藏，截图检查发现后收窄到行尾picker，标签保持可见。
- Prism `search_field.dart` 为无描边胶囊、20dp glyph、41dp文本起点、框内清空、单行搜索及256字符上限。本组将代理资源、规则资源列表/引用、图标集和策略组图库输入统一到共用`ProfileSearchField`，清空只改变过滤，不保存或关闭选择层。规则引用补分组行、当前选中标记与搜索无结果恢复，保留原别名绑定。

## 保存中菜单退场缺口

首次适配只在`onDismissRequest`检查saving，但`AppDropdownMenu.close`先设置`requestedClose`，约500ms回源完成才调用回调。若此期间保存失败，菜单随后关闭；若仍保存，回调拒绝后菜单重新开场。只禁用菜单条目不能阻止这一链路。

新增默认开启的`dismissEnabled`，在受理Back/Escape/外点与层级返回时检查最新值，协议保存中关闭该门禁；不改变其他入口默认行为，也不阻止外部expanded成功回执收回。新增挂起保存测试比较返回/外点后180ms未点击菜单上半部的像素，另验失败留层重试及最后一项异步回执，不用同步假保存冒充等待窗口。

同一新增测试在门禁前的build2独立APK执行，精确失败于“保存中返回/外点不得先启动回源”，日志位于`legacy-save-dismiss/instrument.log`；不计入最终通过总数。首批focused1为13/14，唯一失败是错误文案同时出现在父页和添加面板而导致单节点匹配歧义；限定面板错误后再跑，不放宽保存/回执断言。

## 范围边界

保留现有Android添加BottomSheet和资源页容器；完整Flutter工具栏搜索展开/收回、SelectionSheet的全部条目进退动效与固定Flutter运行时逐帧对照仍待补。规则资源总列表的额外搜索能力不删除。玻璃默认关闭，菜单性能调查候选没有混入本组。

验收使用原`../1.yaml`上传副本和独立motion包；详细结果见`docs/acceptance/more-selection-parity-redmi-20261109.json`。组件受控保存不代替正式Room、实核或冷进程结论。

最终复核中，第一版全图像素断言在修正版仍失败；`pending-diagnostic`两图差异仅在被点击的第三行（22,286–489,418），是Android独立推进的涟漪。改为比较未点击上半部，仍要求逐像素一致并核对尺寸，不通过颜色容差掩盖回源缩放/模糊。旧全图红测和final1的26/27保留，修正后的同一断言另跑旧包/新包确认。

## 最终交付

最终build4/fixture7/lint4通过：860项JVM零失败/错误/跳过（App578执行，其余282复用），Debug/签名Release/R8/独立测试包通过；Lint Debug0错误68警告14提示、Release0错误66警告13提示。final2同套27个不重复方法全部通过，84PNG分片/长度/SHA校验并经11拼图检查。修正后的同一退场断言旧包未点击上半部最大差163/255、最终包为0；同时验证失败留层与显式重试成功。

正式签名APK `ad5b0604085d51d4a4615829eff8f96e28d0eb9ab5f1315ec1b4deb17cc82357`，证书沿用既有独立发布身份，安装回拉匹配。深浅正式菜单24个阶段终态检查与8PNG通过；进程打开75/198、Popup31/138，关闭20/165，不能认定性能收益或RenderThread根因。正式配置列表仍可见原授权本地配置，首页停止态和网速卡可读；最初误要求首页显示配置名的脚本失败独立保留。正式库未采完整字段快照，不把UI烟测说成元数据不变证明。

本轮只操作Redmi，结束服务停止、深色/字号1.0；motion/test已卸载，新上传的原1.yaml副本与两个自有XML核对SHA后精确删除，工作区原文件和正式配置未清除，Flutter参考包未操作，公开0.1.0不覆盖。详细证据与本机附件见对应JSON。
