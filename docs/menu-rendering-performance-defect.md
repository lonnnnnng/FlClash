# KUI-019：菜单动态轮廓的重组与重复裁剪

2026-11-08，作者 long。起点为 Kotlin `907a37c618bc07973524eb291635f73668f0677f`，接续 [KUI-018](motion-performance-defect.md)。附件位于 `../acceptance-artifacts/menu-rendering-20261108/`；本轮设备重置后的结果不能与前组配置或帧数合并。

## 问题、来源与实现

KUI-018 已稳定根卡和后层阴影，但顶卡展开/收回仍在组合阶段读取展开进度、宽度、缩放和材质进度。逐帧变化会使正文组合范围一起失效；同一不可变帧的阴影、裁剪、材质还会重复构造轮廓。外层开场阴影即使透明度为零，仍保留路径和阴影计算；顶卡同时经过图层裁剪与绘制路径裁剪。

先参考固定 Flutter `be84736605527034689c95bf27828ff27d912c5e` / Prism `63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185`：`goo.dart` 的 `_outlineOf` 复用相同 pane 几何，阴影/材质位于 paint 阶段；`navigation_dock.dart` 隔离静态 child。参考源码沿用 `../acceptance-artifacts/visual-motion-20261107/reference/`，没有合并 Flutter 提交。Compose 状态延迟读取及阴影缓存资料沿用 KUI-018 的已抓正文，不改依赖或内核。

- `AppMenuMorphFrame` 惰性复用 platter/outline 路径，`AppMenuFrameCache` 只保留上一帧，不建立持续增长的逐帧缓存。返回路径约定只读；调用方不修改共享路径。
- `AppMenuFrameShadow` 将真正变形的阴影限制在小组合范围；不可见时不创建阴影，透明度在独立图层调制。变形仍使用逐帧精确轮廓，半径、颜色、偏移与曲线保持原值。
- `AppMenuStack` 在 layout 阶段读取前 30% 的宽度，在 graphicsLayer/draw 阶段读取缩放、轮廓和材质进度；保留真实宽度重排、精确裁剪和命中框更新，移除第二次相同路径裁剪。
- `AppPopupMenu` 隔离开场/回源阴影，展开后由各层接管；`appMenuMorph` 在关闭表面绘制时提前返回，并复用 saveLayer 的 Paint。

这是 Kotlin 移植中的状态读取和缓存问题，不认定 Flutter 上游存在同一缺陷。没有缩短动画、冻结顶卡或删除阴影来降低分母。

## 性能测量与未完成边界

新增深/浅色具名 `MenuFirstOpenAndRepeatedTopCard` 用例，每主题连续三次打开、展开顶卡、返回、关闭；首次打开与同进程重复打开分别计数。使用非 debuggable、未混淆 `.motion` 独立包，库按 debug 回退，禁止录屏/PNG；它不是正式签名/R8 Release 的运行性能。每段在窗口存活时回读并清零，`Flags=0` 的帧表按 Stats since 过滤；表中首条记录不等同触摸后的真实首帧，UI 区间也不是纯 CPU 时间。

`baseline2/3` 与 `optimized1/2` 各两轮深浅的菜单阶段结果：

| 阶段 | 优化前 jank | 优化后 jank |
| --- | --- | --- |
| 首次打开 | 42/159（26.42%） | 41/177（23.16%） |
| 同进程重复打开 | 105/335（31.34%） | 83/361（22.99%） |
| 顶卡展开 | 80/357（22.41%） | 64/392（16.33%） |
| 顶卡收回 | 78/202（38.61%） | 37/263（14.07%） |
| 菜单关闭 | 26/376（6.91%） | 36/362（9.94%） |
| 以上窗口合计 | 331/1429（23.16%） | 261/1555（16.78%） |

收回的 Vsync→SyncQueued 采样中位从 25.513 降到 9.590ms，菜单合计从 17.500 降到 12.254ms；首次打开该区间中位反而从 17.067 升到 19.653ms。关闭卡顿率退化，不能记为全部阶段改善。关闭的 Flags=0 UI 区间每侧仅 12 条，不能用其下降覆盖 gfxinfo 的退化记录。前后轮次非随机交错，不作统计显著性或全应用性能结论。

第一版 `optimize1/2` 尚未移除重复裁剪，第二版 `optimized1/2` 才对应最终候选；两版日志、包和摘要分别保存，不混入最终分母。首轮仅 `optimize1` 的比较文件保留，不能当两轮结果。首次打开、关闭和剩余 RenderThread 慢帧继续待查，整体流畅度与完整 Flutter 视觉仍 PARTIAL。

## 环境变化与失败记录

Redmi `wsvwypiz7xwslvl7`，Note 8 Pro / API36 / arm64 / 竖屏默认字号。用户确认近期卸载或重置过；本轮开始 KClash、测试包与 `com.follow.clash` 均缺席。仅安装 KClash Debug/测试包用于只读正式库检查，当前正式配置 0 份，不导入配置、不访问授权订阅；Flutter 参考包保持缺席、不安装。原 `1.yaml` 的 SHA、大小、mtime 与设备设置逐轮保护。

`baseline1` 因参考包 `pm path` 返回 1 被原脚本拒绝，发生在候选安装前；修正门禁允许参考包缺席后另起 `baseline2`，不追认失败。两次探索子代理因模型工具调用配置失败，没有提供有效独立核验，不能记为代码审查通过。

正式空库无法执行两条依赖已导入配置的 `MainNavigationStateInstrumentedTest`，本轮明确排除其 6 张截图；历史通过记录保留，不把当前 0 份写成两份配置保持。

`final-regression1` 为 14/19，40 张图完整回传；5 项失败均为静态截图哈希矩阵。随后用同机旧产品源码的独立 `.motion` 包跑 `baseline-static1`，原矩阵同样 0/5；旧包与候选包的 12 张对应 PNG **逐字节 SHA 全部一致**，详见 `static-environment-comparison.json`。这排除了本补丁造成这些截图差异，不能进一步推断具体系统字体、字体缓存或渲染设置的原因。刷新 12 个基线，旧哈希与新哈希均保存在 `screenshot-baseline-update.json`；不通过放宽断言或忽略截图测试消除失败。

完整自然流程另以 `baseline-natural` / `optimized-natural` 各深浅 2/2，无录屏/PNG，全部非启动窗口为 327/2166（15.10%）→296/2211（13.39%）。其中质量相关回读窗口合计 58/363→55/374，包含邻接返回阶段，不认定质量面板自身优化。本补丁未改面板实现；单轮完整流程只作相邻边界观察，不替代上述两轮专门菜单对照。

制品归档首次脚本因未解析的 `Path('.')` 访问 `parents[1]` 抛出 `IndexError`，尚未复制 APK；改为 `Path.cwd()` 后归档并逐个 SHA 核对通过，不属于源码构建失败。两处外围检索曾使用不存在的 `.agents/README.md` 和本轮 `reference/` 路径，已改为实际协作入口与上一组固定参考目录，不据空检索作结论。

`final-visual1` 两条自然用例完成后，私有 gfx 回拉的 SHA 门禁失败；未写最终结果 JSON，不计通过。回读得到 SELinux `No match for app ... targetSdkVersion=37:fromRunAs` / `Permission denied`，ADB exec-out 的输出实际为拒绝消息，不是帧表。原始交互日志与回拉输出保留；录屏停止后核对设备 SHA 并回收，两个私有帧文件由具名 `removeExactOwnInterruptedFrameArtifacts` 在应用 UID 下先全部核对文件名/大小/原 SHA 再删除，结果 removed=2/remaining=0。没有修改 SELinux、设备安全策略或扩大应用权限。

后续自然视觉批次启用现有 `kclashMotionInlineFrames=true`，不依赖 run-as。整页和三轮菜单分批使用唯一 run，避免非分段主题 gfx 同名覆盖。新的回收方法只存在 Instrumentation；正常完整流程必须按具名方法执行，不能整类运行。结束检查使用空清单回读自有采集缓存为空，不删除其他缓存。

## 同步与撤销条件

后续同步 Flutter 或 Compose 时，对照上述固定提交和调用点复查路径只读约定、精确轮廓裁剪、拖选命中、子菜单反转、源按钮回归和阴影交接。只有同流程性能、深浅主题和相邻交互均有独立证据时才撤销；不能因升级依赖或总卡顿率下降就认定首次打开、关闭及全应用流畅度已完成。
