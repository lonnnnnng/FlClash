# KUI-VIS-072 播放字形、资源浮框与菜单关闭链路

2026-11-21，POCO F2 Pro（31936bb6，API36），竖屏、默认字号。玻璃默认关闭。本组接续071，范围是首页运行按钮字形、资源周期／Geo地址浮框以及菜单关闭拆窗的线程归因。

## 实现与参考

运行按钮使用固定Flutter `AppGlyphs.playPause` 的三角拆分、双条展开与旋转路径，450ms双向 `easeOutBack`；按需暂停使用原 `wifiOff` 复合路径。字形时钟独立于外轮廓的反向曲线，进度在Painter绘制阶段读取，静止路径复用，避免动画每帧重组整张运行卡。

保留Kotlin现有启停忙态spinner和点击防重；忙态期间字形时钟继续，忙态解除不重播。Flutter参考在此处没有spinner，且暂停时会移除字形Tween；因此不宣称所有服务状态下的动画生命周期完全相同。本组通过状态夹具验证画面，没有启动核心或VPN。

资源周期与Geo地址使用共享浮框。修正按钮默认15sp偏小，改为Prism的17sp、取消500字重及确认强调字重；输入框水平留白包含外层10dp与内层4dp。保留Kotlin“保存／恢复默认”文案、校验、提交回执与关闭合同。

独立Flutter宿主直接调用原 `InputDialog` / `UpdateGeoUrlFormDialog`。双主题各采集周期24、编辑48及Geo初值、编辑值，共8组同值状态；仅用于浮框组件对照，宿主背景与实际整页不同。长URL的断行位置、少量字形／行高仍有差异，完整页面及所有面板未收口。

## 可复现证据

- [`runtime_glyph_oracle_test.dart`](../tool/runtime_glyph_oracle_test.dart) 放入独立Flutter参考宿主 `test/`，以验收JSON列出的固定源码摘要运行 `flutter test`，输出 `glyph-reference.json`。参考运行时不进入Kotlin依赖。
- Android使用8个96×96独立Flutter alpha栅格：进度−0.08、0、0.25、0.5、0.75、1、1.08和wifiOff；仅容许边缘1物理像素及每图少于180个差异像素，内部缺线不能通过。
- POCO最终同套Motion/Test集中11/11，43张PNG完整分片、字节数、SHA及尺寸校验通过并目视。覆盖字形动画与暂停、071轮廓和玻璃、重命名／密钥失败重试、光学相邻回归，以及真实MainActivity资源保存／回读／取消／重置／返回。资源原设置逐值恢复，核心保持STOPPED。
- App Motion JVM628项零失败／错误／跳过；Motion/Test、Release-R8、Performance-R8构建通过。Motion Lint 0错误／75警告／15提示，Release Lint 0错误／71警告／14提示；Release ML Kit DEX门禁3/3。
- Flutter深浅自然录像361／355原PTS帧；Kotlin真实MainActivity深色272原PTS帧，覆盖周期与Geo打开、聚焦、收键盘和返回，末帧已确认Geo浮框关闭。保留原录像、PTS及抽样，不称逐帧等价；Kotlin使用默认Geo长地址，参考使用example.invalid短地址。录制前临时启用独立包自动更新以显示周期入口，结束恢复false；未提交任何输入值。视频与Perfetto分开采集。
- APK回拉、源码摘要、设备收尾及完整记录见[本组验收JSON](acceptance/runtime-glyph-resource-poco-20261121.json)。

首轮动画断言因测试黑底与默认黑色前景重合失败；修正测试前景后定向3/3，最终同批11/11。Flutter URL采集的旧尾部残留、全选快捷键失效样本保留，不进入8组有效对照；最终采集以完整字段值校验。各批次不累加成单批测试数。

## 关闭归因方法与边界

新增[`perfetto_menu_close_chain.sql`](../tool/perfetto_menu_close_chain.sql)，从普通R8应用UI帧内最外层 `Compose:onForgotten` 出发，沿实际Binder flow连接系统回复，再展开窗口布局、焦点和嵌套Binder回复。每行分别报告wall、Running、Runnable、Waiting；父子区间包含关系不能相加。

对上一组普通R8 trace的两次关闭，应用Binder等待对应system_server 5.216／6.522ms回复，服务端Running为4.389／5.372ms；其中布局1.471／1.929ms、焦点1.404／1.932ms。嵌套SurfaceFlinger回复0.104～0.186ms，没有长回复证据。对应UI总帧16.113／17.871ms，不能将onForgotten全部wall算成应用CPU。该trace有23个discarded chunks，仍存在完整性限制。

本组普通R8两轮共4次关闭复采：UI wall 16.972～17.811ms、Running 9.083～10.024ms；onForgotten Running 4.271～5.077ms，其内部应用Binder wall 5.710～6.787ms。实际flow指向system_server回复5.633～6.702ms、Running 4.929～5.589ms；其中布局wall 1.716～2.215ms，焦点1.564～2.071ms。嵌套SurfaceFlinger回复0.123～0.208ms；同token RenderThread为4.514～5.081ms，未观察到waitForBufferRelease切片。每根匹配1条FrameTimeline行，完整原始切片与各阶段值见JSON及CSV。

新两trace discarded chunks为24／25，不是丢帧数，也不能忽略完整性限制；不与旧诊断探针包或前批分母混算。系统窗口释放成本已有实际因果边，GPU／fence完整因果与稳定优化收益尚未证明；不据此引入提前移窗、异步释放或更短动画等可能破坏关闭合同的改动。

## 后续

继续其他未覆盖子页／面板、URL排版和完整页面连续对照；保留忙态反馈与Flutter动画差异记录。玻璃重叠pane分层、全页采样策略仍待补。菜单继续拆分关闭阶段应用本地组合释放与system_server工作，结合有完整性标记的UI／RenderThread／FrameTimeline证据评估优化。

本轮不发布；原版 `com.follow.clash` 未启动或改动，不读取原1.yaml／2.yaml。工作区既有KCORE-011改动独立保留，不计作本组核心验收或提交内容。
