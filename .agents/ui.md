# UI parity

## 动态顶卡与菜单复验（2026-11-08，当前）

KUI-019继续优化菜单动态轮廓：阴影局部重组、同帧Path/Paint复用、宽度延迟布局及移除重复裁剪。Redmi API36独立非debuggable包、无PNG/录屏，前后各两轮深浅，菜单合计jank331/1429（23.16%）→261/1555（16.78%），顶卡收回38.61%→14.07%；首开26.42%→23.16%改善有限，关闭6.91%→9.94%退化保留，首次打开UI区间中位也未改善。完整自然流程各深浅一轮非启动窗口15.10%→13.39%，不冒充正式R8运行。final-build3共849项JVM零失败/错误/跳过（App568在build1执行、其余281复用，最终四模块UP-TO-DATE）；Debug/Test/未签名Release-R8/双Lint通过，Debug0错误68警告14提示、Release0错误66警告13提示。同套最终包回归19/19、自然整页2/2、三轮菜单2/2；92PNG/32拼图及8组原PTS抽样已核对，593源码与安装回拉匹配。用户确认重置，当前正式0份配置，排除依赖非空库的两条导航回归；Flutter参考包保持缺席、不安装。44份正式快照及最终独立检查一致，原1.yaml/设备设置保持；独立.motion双包与独占文件精确回收，结束无服务/tun0。旧包/候选12图逐字节一致后刷新环境基线，14/19、旧包0/5与run-as拒绝回拉失败均保留。整体性能和完整Flutter视觉仍PARTIAL；下一步优先首开、关闭退化及RenderThread慢帧，再补CUSTOM组数、首页无流量统计、内存图形与其余子页。玻璃默认关闭，既有边界保持。证据`docs/acceptance/menu-rendering-redmi-20261108.json`，溯源`docs/menu-rendering-performance-defect.md`，附件`../acceptance-artifacts/menu-rendering-20261108/`。

## 菜单动效性能（2026-11-08，KUI-018历史）

KUI-018菜单掉帧热点已定位并优化：稳定后层轮廓、阴影透明度独立合成、普通展开取消每帧命中订阅；补截图前开关与分阶段帧统计。Redmi API36非debuggable独立包、无录屏/PNG，优化前后各两轮深浅：二级菜单jank61/92（66.30%）→20/129（15.50%），主线程阶段中位34.25→9.45ms；非启动总窗口621/4401（14.11%）→465/4599（10.11%）。方法采样阴影位图调用片段125→58、包容耗时954→396ms，非精确调用计数；面板尝试因退化撤回，最终面板62/535（11.59%）。最终build2共849项JVM零失败/错误/跳过，App568执行、其余281复用；Debug/Test/未签名Release-R8/双Lint通过，Debug0错误68警告14提示、Release0错误66警告13提示。同套最终包回归21/21、自然深浅2/2，另深色终帧补验1/1；88PNG/31拼图和7组原始PTS序列已核对，593源码与安装回拉匹配，Release诊断隔离。正式两配置/正文SHA-size-mtime/Room偏好/选择、原1.yaml/Flutter包/设备设置全组保持，独立.motion双包及独占采集文件已收尾，结束无服务/tun0。两张玻璃开场图和一张List图为过渡帧，List原视频及同包补验确认完整节点到达。最终对照位于基线之后，非随机实验；独立包未混淆且库按debug回退，不冒充正式Release。整体性能与完整Flutter视觉仍PARTIAL；下一步优先菜单首帧/动态顶卡与剩余渲染慢帧，再补CUSTOM组数、首页无流量统计、内存图形及其余子页。玻璃默认关闭，既有边界保持。证据`docs/acceptance/motion-performance-redmi-20261108.json`，溯源`docs/motion-performance-defect.md`，附件`../acceptance-artifacts/motion-performance-20261108/`。

Flutter 参考实现是行为和视觉基线，Compose 是 Kotlin 的实现方式。复刻页面时先记录信息层级、状态、交互反馈、空态、错误态、加载态和竖屏默认字号布局，再落到 Composable。

当前优先级固定为主页 Dashboard → 配置 Profiles → 代理组 Proxy Groups，逐页面、菜单和按钮收敛主流程；Requests/DNS/Logs 的进一步视觉打磨暂停，主流程完成后再恢复。每个已验证切片及时同步 `docs/roadmap.md`，未完成的结构化编辑不能以现有 YAML 弹窗代替完成状态。

Design System 至少集中管理颜色、字体、间距、形状、图标、状态色和动效时长。不要在页面中散落 Material 默认值，也不要为了复刻 Flutter 而把 Flutter 代码或运行时带入 Kotlin 分支。

验收范围固定为竖屏和系统默认字号；用户明确不需要横屏或大字号测试，后续不得运行这些测试，也不要运行包含这些用例的整类 instrumentation。深浅主题可按当前页面需求验证；其他布局差异需说明是 Android 平台约束还是尚未完成的 parity 工作。

## 历史主页面视觉与自然时钟交互（2026-11-08）

固定Flutter `be847366`与Prism `63450c0`，统一首页/配置标题、配置卡/快切、共用根面板标题、质量占位、代理整页搜索空态与短副标题。build6同套包Redmi回归14/14、自然深浅交互2/2，62PNG/22拼图及6组原始PTS序列已核对；849项JVM、Debug/Test/未签名Release-R8和双Lint通过，593源码/安装回拉匹配，正式两配置及所列设备基线保持。详见`docs/main-visual-motion-alignment.md`和`docs/acceptance/main-visual-motion-redmi-20261108.json`。

连续动效性能未通过：最终累计jank31.62%（592/1872），第二主题含第一主题不可相加；Debug/录屏/PNG开销未隔离。下一步优先定位掉帧，再补CUSTOM组数、首页无流量统计、内存图形及其余子页。固定Debug宿主浅色状态栏对比度不计正式主题已验；本机Flutter3.41.9低于固定Prism3.47.0要求，源码参数和Kotlin截图不证明Flutter逐像素相同。整体仍PARTIAL，玻璃默认关闭。具体清单见`docs/remaining-main-page-acceptance.md`。

## 历史主页面交互切片（2026-11-05）

规则编辑的原行身份、批选及保存中状态可重建恢复；源行已变更时阻止误新增。单策略组快捷YAML返回先校验并更新父草稿，错误可继续或放弃；Provider长文保存失败与同文案重试自动回顶。服务详情/管理/质量标题正文连续过渡，独立页内容和滚动状态解决返回后列表回顶；面板退场阻断业务触摸与按键。App451/Service193/Core38/Common31共713项单测零失败/错误/跳过；build4 Debug/Test、未签名Release/R8及独立lint4双变体通过，各0错误/64警告（Debug10/Release9提示）。Redmi最终生产/测试包同批final5 28/28，63张PNG已目视，安装哈希匹配。首批26/28、两次6/7、motion4补验1/1及系统设置覆盖页面的final4 27/28独立保留、不累计。正式1份配置/原选择/正文SHA-size-mtime及逐字段摘要保持；原1.yaml双端、Flutter APK及旋转/字号/动画设置保持，结束无服务/tun0。证据`docs/acceptance/main-subpages-recovery-redmi-20261105.json`。整体Flutter视觉仍PARTIAL；服务子页边缘拖回、精确缓动/全应用视觉、未覆盖子页/空态/异常恢复与性能仍待补。本组规则重建是保存状态模拟恢复，未做冷进程；未重跑实网/核心/VPN、低API、Go、正式签名运行或远端CI。玻璃默认关闭，Dock/按钮实时背景和触点光效后排；备份/系统恢复、观测次要打磨、语言/主题继续暂缓。

## 最新菜单/子页切片（2026-11-04）

共享regular/pill/menu/alert/tinted/lens材质与STANDARD/ULTRA等级、共用液滴回源、可逆多层展开、策略组/更多/链式代理覆盖子页及API27离线fallback已验，玻璃默认关闭；Redmi final6 19/19、API27 final6 10/10，详见`docs/acceptance/general-glass-subpages-api27-20261104.json`。完整视觉仍PARTIAL：预模糊近似，API24/25、31/32及其他ABI/实网未验；其他空态/页面、完整采样品质、全应用逐像素一致性和性能验证待补。早期Room元数据兼容不属于开发阶段要求；Redmi正式配置/选择/正文聚合摘要的count=1且before/after SHA一致，逐字段摘要未嵌入JSON；API27设置记录为null，不能据此确认恢复。主题/语言设置继续暂缓。

## 前序编辑器与主页面回归（2026-11-04）

脚本、Provider正文、选项、过滤预设和规则资源错误体验已补齐本组范围；选区移动不清失败，长文查找显式滚动，已提交回读失败只允许重新读取，保存失败自动回到错误且保留草稿。App451/Service193/Core38/Common31共713项单测零失败/错误/跳过；最终build4 Debug/Test、未签名Release/R8及独立lint4双变体通过，各0错误/64警告（Debug10/Release9提示）。Redmi最终App/Test同批final4 43/43，69张PNG已目视，安装哈希匹配；首批40/43独立保留、不累计。正式1份配置/原选择/正文SHA-size-mtime及逐字段摘要保持，原1.yaml、Flutter APK和设备设置保持，结束无服务/tun0。证据`docs/acceptance/editor-errors-recovery-redmi-20261104.json`。完整Flutter视觉仍PARTIAL：未覆盖页面/面板、全应用动效与玻璃采样品质、常驻Dock/按钮/其他面板live背景和触点glow、8MiB正文性能仍待补；低API核心/VPN/实网、Fallback实核/冷进程、Go/正式签名运行/远端CI边界保持。玻璃默认关闭；备份/系统恢复、观测次要打磨、语言/主题继续暂缓。
