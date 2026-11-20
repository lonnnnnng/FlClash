# KUI-VIS-068 输入浮框、实时光学与菜单定域分析

本组延续067，使用POCO F2 Pro、竖屏和默认字号。玻璃默认关闭；原Flutter应用不启动、不替换。固定Flutter副本的原始InputDialog与LiquidGlass在独立参考宿主运行，Kotlin主线没有合并Flutter提交。

## 输入面板

本地FILE配置的名称与age私钥复用56dp同行输入、分隔线和显隐按钮。重命名与补age密钥保持上游手机居中浮框：最大348dp、横向40dp避让、34dp连续圆角、24dp输入胶囊、48dp双按钮、404ms临界阻尼入场。退出保持当时缩放，只淡出，不反向放大。Kotlin保留已有“保存／解密并导入”、忙态和失败反馈。

业务会话由原控制器处理。名称只读退场快照不重新保存；取消先等待加密草稿清理回执，清理失败显示错误并允许再取消，防止留下透明禁用窗口。密钥显隐不进SavedState；后台清理保持。IME读取最新提交资格，密钥在交给外层前同步消费，按钮与IME同帧不能重放。

本轮真实MainActivity只在独立空库导入授权1.yaml的USB副本，分别验证FILE元数据编辑和CUSTOM独立重命名，重建/保存/取消/重开，结束回收临时记录。FILE没有独立重命名菜单，首轮测试错用该入口的失败保留，不为测试扩展产品入口。

## 实时光学

原067的矩形pow8位移与统一白描边替换为固定Prism局部方程：圆角法线正负折射、连续超椭圆边线、镜像轴红蓝分色、饱和度与亮度、五点背景对比度、上下定向高光和暗线。pill使用28%清晰与72%雾化输入，雾化15.6dp；tinted保持色散0。镜片单独使用lens，图标与文字使用lensContent，不给普通玻璃人为添加彩边。

采样由raw/frost/rim/haze四槽GPU局部图集提供。每槽显式裁剪，防止零模糊RenderNode越界覆盖邻槽；四周保留模糊/负折射/色散所需余量。雾化材质的五点亮度读取haze。动态RenderEffect缓存最多四档，默认关闭在分配图层前退出。

Dock主体先绘制，抬起镜片采样已绘制主体，文字层独立折射。触光采用高斯衰减，普通按钮120ms入/360ms出，Dock原100/500ms弹簧，先画光再画前景。菜单仍冻结背景，未改为实时捕获。

本组不是完整Flutter捕获引擎：任意重叠pane的atlas分层/group/veil、启动按钮运行态circle→cookie9轮廓、全页采样降采样策略仍有差异。旧API仅保留既有模糊/染色降级，未扩大系统兼容验收。

## 菜单性能与溯源

仅将没有backdrop时的材质插值/对象构造移入玻璃分支，不改固有测宽、长列表滚动或窗口生命周期；这些历史候选已测无稳定收益或失败。

`kclashMenuTrace=true`仅对独立performanceBenchmark构建启用。标记Popup壳、根测量、stack卡片/正文实际测量和固有尺寸查询；普通R8/正式Release恒为false。单个标记是包容墙钟，需与thread_state求Running交集，各层不能相加。诊断包不参与无探针性能分母。

普通R8同设备五轮对照如下。计数是进程gfxinfo，Popup窗口另在JSON保留；关闭时已销毁的窗口不补零。没有同时录屏、截图或Instrumentation。

| 阶段 | 067基线 jank/帧 | 068最终 jank/帧 |
| --- | --- | --- |
| 来源菜单打开 | 18/162（11.11%） | 12/171（7.02%） |
| 链接Sheet打开 | 24/351（6.84%） | 21/356（5.90%） |
| 键盘关闭 | 1/116（0.86%） | 1/114（0.88%） |
| Sheet关闭 | 6/150（4.00%） | 2/155（1.29%） |

这是五轮非随机观察，不能宣称稳定性能提升。普通R8 Perfetto首开UI从75.732ms/Running58.375ms变为74.761/59.212ms，基本相当；复开63.441/49.346ms→58.547/46.065ms。最终两次窗口释放帧17.126–18.266ms，其中Compose:onForgotten为11.960–12.990ms；RenderThread本轮最大14.325ms，旧包21.560ms，不能由单窗口最大值推断原生瓶颈已解决。首页PSS154882→151180KiB、表单272699→271499KiB，静置246442→250332KiB，无显著整体内存改善。

诊断R8首开主线程74.511ms/Running59.366ms；Popup根实际测量8.000ms，内部首次固有宽度查询4.032ms。该帧还包含重组18.574ms、绘制14.305ms、Binder12.054ms。类别/层级可能嵌套，不相加；测量不足以解释整帧，下一步查首次组合、绘制、窗口交接和释放。查询已落地`tool/perfetto_menu_scopes.sql`，与原本机查询输出逐字节一致；`tool/perfetto_window_work.sql`继续保留拆窗线程工作。普通基线/最终/诊断trace的`traced_chunks_discarded`为18/19/20，是否跨会话累计未定，不称无丢失或完整原生函数归因。诊断后已恢复final12普通R8并回验SHA。

## 验收记录

最终串行final-build12：App628、Service220、Core39、Common34共921项JVM零失败/错误/跳过，包含Gradle复用；motion/Test、performance R8、Release R8构建成功，双Lint零错误（motion75警告/15提示，Release71警告/14提示），最终MLKit DEX门禁通过。

同套final12安装回拉匹配，POCO集中37/37、84张PNG全部目视，22张固定菜单几何/阴影图与066逐像素一致；660份Kotlin源码与构建快照一致。MainActivity真实FILE/CUSTOM记录只在独立库，原授权正文SHA-size-mtime保持，结束临时记录为空且核心STOPPED。Activity重建不等于真实进程死亡。

普通R8玻璃开关在force-stop后保持，静止4秒gfx无新增帧，仅为有限静止样本。自然录像28.801秒、610原PTS帧保留，12点拼图目视覆盖来源菜单、链接Sheet/IME、工具与主题、首页滚动；不是610帧逐帧对照，晚段Dock拖动不计入录像覆盖。真实上游Dock拖动另拍，和最终Kotlin held图确认都存在镜片边缘分色；背景/位置/图标不完全一致，不称像素等价。

开发中先淡出再清草稿导致清理失败后留下透明窗口，现由`FloatingInputDialog.requestClose`等待业务回执，失败重试用例通过；age显隐后旧IME回调由`rememberUpdatedState`修复。它们是本组开发阶段发现的本地问题，不归因为Flutter上游缺陷。编译/错误入口/错误断言/采集脚本失败保留在JSON，不与最终37/37混算。

四份自有验收包卸载前逐个核对安装SHA；授权USB副本仅在created=true且哈希匹配后删除。清理脚本首次将包缺席的`pm path`退出1当异常，确认已删首包后改用包清单继续。最终包清单、原Flutter版本/安装时间/代码路径、旋转/字号/night与原1.yaml SHA-size-mtime均保持。

结果见[本组验收JSON](acceptance/panels-live-optics-poco-20261120.json)，原始附件仅留本机`../acceptance-artifacts/panels-optics-poco-20261120/`。未跑实网核心/VPN、低API/其他OEM/横屏/大字号，未发版；完整页面视觉、连续转场、完整光学和稳定性能收益仍PARTIAL。
