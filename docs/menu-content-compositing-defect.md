# KUI-021：菜单首开与关闭性能候选核验

2026-11-09，接续正式 0.1.0 菜单基线。本组完成四种候选核验和关闭窗口存活期采帧，**没有保留产品改动**；首开/关闭优化仍未完成。权威证据为 `docs/acceptance/menu-performance-investigation-redmi-20261109.json`，本机完整附件 `../acceptance-artifacts/menu-transparent-20261109/`。

## 对照范围

Redmi Note 8 Pro `wsvwypiz7xwslvl7` / API36 / 440dpi / 字号1.0，核心停止、复用原授权本地配置。深浅主题分别冷进程，三轮打开/展开/收回/关闭；每个包24个终态检查通过。性能阶段不截图/录屏，静态图在每批测量完成后另采。系统原night=yes已恢复，未操作其他设备或Flutter参考包。

基线使用与发布源码一致的本机签名R8包 `72527d65…`，所有候选同一工具链/版本/签名；不混用Linux公开包与macOS候选的构建差异。首轮 `baseline-dark` 与一次安装包回拉重叠，明确排除，另跑 `baseline2-dark/light` 作为对照。电池温度各批约27.4–28.4℃；非随机盲测，不声称统计显著性。

## 候选与结论

| 候选 | 打开jank | 关闭jank | 结论 |
| --- | --- | --- | --- |
| 原实现对照 | 134/238，56.303% | 17/187，9.091% | 对照 |
| 透明/全不透明正文绕过saveLayer | 133/224，59.375% | 19/177，10.734% | 无目标收益，撤回 |
| 复用软件阴影Alpha8缓冲区 | 136/226，60.177% | 18/176，10.227% | 外观一致但无目标收益，撤回 |
| 源按钮直接复用GraphicsLayer | 119/221，53.846% | 20/178，11.236% | 打开仅小幅变化、关闭更差，撤回 |
| 硬件RenderEffect阴影 | 未进入正式性能对照 | 未进入正式性能对照 | 未通过固定帧外观门禁，撤回 |

展开/收回数字详见JSON；局部数字下降不能代替首开/关闭结论。所有候选的代码、APK、日志和失败图留在本机附件，不把无收益实现交付到主线。

### 透明正文

固定Flutter `be847366` / Prism `63450c0` 的 `prism_ui/lib/popup.dart::_paintContent` 在alpha=255时直接绘制，`_paintSource`在alpha=0时跳过。Kotlin `AppMenuMorph.kt`无条件saveLayer；关闭100ms后正文透明但材质/液滴持续至500ms。尝试跳过透明正文及全不透明图层，8张深浅终态应用区域像素一致，性能无收益。几何和时长未改，已撤回。

### 阴影后端与缓冲区

Compose1.12.1实际AAR的`DropShadowRenderer`字节码显示变形轮廓创建Alpha8位图和BlurMaskFilter；KUI-018旧采样也出现该链路。本组先尝试硬件GraphicsLayer/RenderEffect。首轮类型错误为两个CompositingStrategy命名空间混用；修正后固定帧门禁失败max8/mean0.359。核对AOSP `libs/hwui/jni/RenderEffect.cpp`发现原生层也做radius→sigma，撤掉重复换算后仍max5/mean0.231，未放宽原max3/mean0.2门禁，因此撤回。

随后复用原软件算法的Alpha8位图、Canvas及Paint/BlurMaskFilter，仅当前尺寸持有缓存，尺寸变化才替换，不提前recycle渲染队列可能引用的图像。10个深浅开场/回源固定帧共20张PNG均完整回收，10对**逐像素完全一致**（max0/mean0），但签名R8性能仍无改善，代码与对照测试均撤出主线。原候选保存在`cached-AppMenuMorph.kt`、`MenuShadowInstrumentedTest.kt`，结果在`cached-decision.json`、`cached-shadow-visual.json`。

### 源按钮图层

`AppMenuSource.capture()`每次用`GraphicsLayer.toImageBitmap()`把已记录的按钮读回，再交动画上传。尝试直接持有原层、隐藏期间不重录、回源后恢复alpha、释放后跳过绘制；玻璃背景PixelCopy不变。独立.motion的LiquidMenuInstrumentedTest 4/4通过，覆盖回源后动作、中断、拖选和后台取消；8张PNG已回收并目视，其中两张玻璃开场仅显示源按钮过渡，不能写完整展开一致。签名包打开/关闭对照仍不足以支持保留，已撤回，候选文件为`layer-AppMenuSource.kt`等。

## 关闭采集缺口

原脚本等窗口消失后读取gfxinfo，每轮只剩1条Flags=0记录。本组另起独立诊断，按主机分发Back后约132/263/392ms开始读取，结束约264/343/485ms，分别回收4/9/18条有效帧；三个窗口最终均正确关闭。

| 采集窗口 | 有效帧 | UI区间中位/最大 | 渲染区间中位/最大 |
| --- | --- | --- | --- |
| 前段 | 4 | 34.036 / 75.928ms | 13.358 / 27.901ms |
| 中段 | 9 | 19.744 / 71.891ms | 5.763 / 25.172ms |
| 后段 | 18 | 9.617 / 62.191ms | 6.104 / 22.771ms |

这是窗口寿命诊断，dumpsys本身有扰动，主机分发时刻不等于真实触摸时刻；UI/渲染区间是经过时间，不是线程CPU账单。不能与无采集干扰的性能分母合并，也不能据此确定RenderThread根因。数据支持下一步优先采样首开和关闭前100ms的主线程工作，同时保留窗口消失前帧表。Perfetto此前ROM flush失败保持，本组未重复启动无效长trace。

## 检查与收尾

各进入完整构建的候选均通过854项JVM（含同源码UP-TO-DATE结果）及Debug/签名Release/R8和双Lint；Debug0错误68警告14提示，Release0错误66警告13提示。这些结果不等于性能通过。所有试验失败独立保留。

最终三个产品文件已恢复到提交源码，逐文件哈希与0.1.0已验证源码一致；Redmi重新安装公开APK，回拉SHA-256为`ed063b9ba59d0825308b2d442da38e5e8ecdcb456cc7c55cd385f11b05ad1082`。本轮.motion/测试包已卸载，两个自有设备XML回收后精确删除；原授权配置、自动选择、原1.yaml保持，服务停止，系统night=yes恢复。

后续同步应保留这些负结果，不重复把“减少一层/少分配Bitmap/取消回读”直接认定为首开根因。没有合入Flutter应用提交，也未改Mihomo。
