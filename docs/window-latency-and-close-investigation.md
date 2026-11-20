# 首次建窗、表单整体延迟与关闭阶段

2026-11-20，KUI-VIS-066。延续065，基线`87327f57`，仅POCO F2 Pro（31936bb6/API36）竖屏、默认字号、空配置STOPPED的独立R8和motion包。玻璃默认关闭；本组不运行原Flutter、不使用原1.yaml、不发版。

## 本组实现与缺陷溯源

### 外部撤销后重开会重放旧选择

065的`AppDropdownMenu`以`active = expanded && !requestedClose`作为交接协程唯一key。选择菜单项后requestedClose为true，外部expanded先false再true时active始终false；旧回源协程未取消，最后只读expanded=true，可能把旧动作当作新一轮展开的选择执行。

新增真实Popup定向用例：选择→回源80ms→外部false并提交一帧→true→等待回源，旧源码出现`expected:<0> but was:<1>`。修正为同时观察active与expanded；撤销时丢弃pendingAction、清关闭请求，重开取消旧协程。源按钮恢复、后台撤销、拖选与子菜单原有边界保留。

这是Kotlin交接状态缺陷，不是Mihomo或Flutter内核问题。固定Prism的`popup.dart:_select`使用Navigator route pop与延迟选择回调，没有Kotlin这个expanded/requestedClose双布尔生命周期；未来参考上游菜单时需保留“已撤销选择不能借用新展开”的回归，不可直接合入Flutter提交。实现位于`ui/AppPopupMenu.kt`，回归`ProfileInputWindowInstrumentedTest#reopeningAfterExternalRevocationDoesNotReplayPreviousSelection`。

### 表单返回来源菜单分开移窗与建窗

原`AddProfileDialog`返回按钮同批次清焦/收IME、将step设为SOURCE，导致旧Dialog销毁和新Popup创建集中于一帧。本组等待Dialog View实际detach后再进入SOURCE，父表单继续持有草稿，父会话退出取消等待，返回按钮同步防重。共用移窗等待提取到`ui/AppWindowHandoff.kt`，Popup→Dialog沿用065行为。

没有缩短原450/500ms菜单动效、禁用系统窗口动画或预建隐藏窗口。Flutter手机链接表单仍是SnapSheetRoute，本组保留Kotlin的Material AlertDialog，不能称完整页面等价。

## 已排除和撤回的候选

- 源截图到达后AnimatedVisibility→Box可能重建正文：真实挂载/释放计数用例1/1通过，正常首开只挂载一次。未据源码形似问题改包装结构；保留该防回归用例。
- `ScrollState.maxValue`改派生Boolean订阅：中间候选首帧数值变化不稳定，最终集中回归23/24，长菜单滑动未接管；独立同用例再次失败。已恢复065直接订阅，不保留这个候选或称有收益。确切内部取消顺序未单独归因，失败作为候选否决证据。

## 线程与窗口证据

065最终trace进一步分解：Popup首帧UI75.327ms，其中真实调度运行59.914ms；Dialog首帧69.291ms、运行57.089ms。Popup traversal中组合11.744、测量9.060、relayout7.070、布局4.944、绘制10.467ms；Dialog traversal中组合21.882、测量9.281+1.185、relayout5.829、布局3.125、绘制2.869ms。父子切片包容，不能相加当总成本；这些值也不代表某个Kotlin函数独占CPU。

同份trace的回源32个RT根切片墙钟中位16.070ms、调度运行中位2.251ms、`waitForBufferRelease`中位14.308ms；主线程中位6.077ms。RT长切片主要包含缓冲释放等待，不支持“RenderThread一直计算”的解释，具体GPU/合成消费者归因仍未完成。

新采集加入binder_driver。反向返回中一次应用Binder请求21.724ms经flow关联到system_server reply21.644ms，其中`wmUpdateFocus`19.146ms；内部Binder15.300ms进一步关联SurfaceFlinger reply14.732ms。这是该次窗口移除/焦点更新路径，不外推为所有卡顿原因或手机故障。

关闭时可能只有主线程拆窗、不提交新图像。065的Dialog关闭token6343743有20.759ms主线程工作却没有actual FrameTimeline行，旧查询从FrameTimeline起表会漏掉。新增`tool/perfetto_window_work.sql`以实际UI/RT线程帧起表，同token多窗口不重复累计同一线程根；同类嵌套onForgotten只计最外层区间，保留无Timeline关闭帧。

`tool/perfetto_form_handoff.sql`区分点击分发结束、Dialog首帧开始/完成与系统IME事件。`IC.showRequestFromApiToImeReady`本轮dur=-1，只有开始标记，**不能称IME已经ready**；键盘动画结束也不等于已注入字符成功。真实输入功能由自然焦点/IME/输入回归补足。查询限当前单次表单流程，不推广任意多次复用窗口。

本轮前七份trace没有正值error/data_loss severity，但有9～15个`traced_chunks_discarded`，不能声称全链路无缺失。现存完整切片可用于具名窗口对照，采集完整性与细函数归因仍有限；065原“零error/data_loss”也仅指该severity查询，不等于零discard。

## 原始失败与最终结果

- red1在菜单出现前冻结测试时钟，找不到`reopen_action`；补自然就绪等待后red2复现产品旧动作执行1次。两批分开留档。
- trace_processor首次错误使用`--csv`，改为实际帮助中的`query --format csv`；原trace未修改。
- DEX脚本首次误用`--apk`，实际要求位置参数，修正命令后检查通过。
- 中间scroll派生候选的23/24与独立0/1保持失败，不能累计为最终通过。
- 初版关闭SQL把嵌套onForgotten重复相加，已改最外层同类区间并重新导出CSV；同名根/子例子与原查询证据保留。

最终构建、性能、视觉及设备保护结果以配套验收JSON和下方收尾记录为准。

## 最终构建与验收

最终build-final6：App627本批执行，Service220/Core39/Common34任务内复用，共920项JVM结果零失败/错误/跳过；performance完整R8、未签名Release-R8、motion及测试APK构建通过。motion Lint为0错误/75警告/15提示，Release为0错误/71警告/14提示，MLKit三个注册器DEX门禁通过。652份Kotlin源码摘要与最终包快照一致，三个独立APK安装回拉一致。

POCO最终`final-ui2`同套包24/24，包含7项输入窗口、5项液滴菜单（含长列表）、5项配置表单、2项age、2项主菜单和几何/阴影/配置视觉各1项。中间批次不累计。38张PNG经过4张拼图完整目视，与065同名图比较27张像素一致（几何20/阴影2/配置5）；其余11张含液滴/系统栏采样差异，只记目视正常。最终R8自然录像8.696秒/180原PTS帧，8fps抽样核对回源→表单与键盘→返回来源→关闭；未见异常空白或卡住。系统退出动画与新来源菜单可短暂同屏，View detach不等于旧像素已经完全消失；录像不混入性能数据，也不冒充完整逐帧Flutter等价。

### 返回来源菜单的局部收益

| R8批次 | 返回时Popup首帧UI墙钟/ms | 点击分发结束→Popup首帧完成/ms |
| --- | ---: | ---: |
| 原同步返回候选candidate-return1 | 81.081 | 89.251 |
| 原同步返回反测candidate-return2 | 86.646 | 91.310 |
| 拆窗候选return-trace1 | 47.287 | 93.564 |
| 拆窗候选return-trace2 | 46.849 | 97.947 |
| 最终撤回滚动派生后的final-return1 | 47.953 | 97.539 |

旧路径将拆Dialog和建Popup集中在一帧，拆分后最终建窗峰值约48ms，仍超过16.7ms帧预算；总完成时间比旧路径晚约6～8ms。本组确认的是集中卡顿下降，不能称返回响应更快。比较中的旧路径/中间候选含后来撤回的滚动派生订阅；最终包恢复直接订阅仍得到47.953ms，保留该区分，不拼接不同包的通过数。

### 首开、正向表单与系统Back

最终5轮来源菜单进程卡顿17/173，回源→表单22/296；基线20/164和21/290。首开段下降、表单段未降，而且冷首开存在波动，整体仍未闭环。中间候选与最终gfx各目录25阶段单独留存，不合并成整体改善百分比。

最终两份trace的首次Popup UI为75.585/64.463ms，Dialog首次完整画面为点击分发后645.614/658.764ms，`IMS.showSoftInput`结束为727.968/758.685ms，IME动画结束1086.203/1129.536ms。与基线650.616ms首画面、746.765ms showSoftInput结束、1122.011ms动画结束相近，未稳定缩短。原500ms回源与系统窗口/IME动画均保留。

系统Back关闭Dialog的最终线程根token6797341没有FrameTimeline行，但UI工作17.995ms、Running11.063ms、等待6.622ms；最外层onForgotten14.913ms，Binder6.141ms。这补足了关闭“无新图像仍有工作”的观测，未消除所有关闭慢帧。最终trace的discarded统计读数为16/17，尚未确定该统计是否跨会话累计，不把它等同丢帧数，也不声称trace完全无缺失。

### 保护与下一步

安装SHA和无服务核对后精确卸载本轮三个独立包，包清单恢复；原Flutter版本/安装时间/代码路径、系统旋转/字号/夜间模式及原1.yaml SHA/大小/mtime保持。本组不操作Redmi、不启动核心、不发版。

后续优先首次Popup组合/测量CPU成本、表单端到端交接延迟与BufferQueue/SurfaceFlinger等待，再继续高频观测状态重组隔离及剩余Flutter面板/连续转场。语义预检边界与系统兼容按用户决定保持，玻璃默认关闭。

证据：[window-latency-poco-20261120.json](acceptance/window-latency-poco-20261120.json)；本机原始附件`../acceptance-artifacts/window-latency-poco-20261120/`。原始失败、候选制品、最终制品、线程切片/SQL/CSV、录像和图片均保留SHA清单。
