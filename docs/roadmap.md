# KClash Kotlin 重写路线图

> **配置子页与输入恢复（2026-11-09，KUI-026）**：修正Provider表单扣除删除触点后的标签/值宽度、开关与IP整行点击、可选提示、最后一项添加自动返回及分组选择排版；配置资源区分搜索无结果/目录为空并可清除恢复，NTP/Sniffer空态按视口定位。字段/YAML确认从子Dialog取IME控制器；旧包同一新增用例在awaitHiddenIme五秒超时，最终包通过。860项JVM（App578执行、其余282复用）、Debug/签名Release/独立测试包及双Lint通过；同套Redmi4+18个不重复方法22/22、68图/10拼图已验。正式签名安装回拉、原配置详情/Provider缓存与底部配置Tab烟测通过；配置未清，测试包及本轮上传原1.yaml副本已精确清理。当前仅本组视觉/交互修正，完整Flutter页面与菜单性能仍PARTIAL，KUI-025首开退化不覆盖。 证据 `docs/acceptance/config-subpages-parity-redmi-20261109.json`，溯源 `docs/config-subpages-parity-defect.md`。

> **源按钮底色与玻璃开场（2026-11-09，KUI-025）**：配置/代理按钮携带真实底色，普通材质按sRGB交接、玻璃只叠源底色。测试改等实际轮廓增长，补齐深浅玻璃展开帧、准备期取消、真实宿主背景冻结与重开重采；860项JVM（App578执行、其余282复用）、Debug/签名Release/独立测试包及双Lint通过。同套Redmi两批3+19个不重复方法22/22、56图/8拼图、8对终态像素一致；正式深浅视频102/103原PTS帧核对、各抽样24张。打开进程74/201→74/190、Popup31/129→37/124，退化观察保留，不认定性能收益；关闭23/168→21/172。手机保留同签名本地修正版，配置未清、测试包/采集文件已清理、服务停止。玻璃默认关闭，完整视觉/慢帧仍PARTIAL。 证据 `docs/acceptance/menu-fill-readiness-redmi-20261109.json`，溯源 `docs/menu-fill-readiness-defect.md`。

> **菜单胶囊与屏幕模糊（2026-11-09，KUI-024）**：补齐源按钮胶囊长边、Flutter稳定回源曲线/20%源图/50ms桥接；正文先变形再按屏幕sigma模糊，源图使用浮点轨迹，正文独立绘制节点复用。859项JVM（App577执行、其余282复用）、Debug/签名Release/独立测试包与双Lint通过；Redmi最终15/15、28图、8对终态像素一致，正文原尺寸/半尺寸/源图模糊宽度均29px。正式包打开进程75/206→73/206、Popup35/139→34/142，关闭20/178→19/167；仅回到基线附近，不宣称性能提升。首版80/183退化保留。深浅视频104/102原PTS帧核对并各抽样24张，玻璃开场两图仍为准备阶段；完整Flutter视觉/慢帧仍PARTIAL。手机保留同签名修正版、配置未清，测试包已清理、服务停止。 证据 `docs/acceptance/menu-content-parity-redmi-20261109.json`，溯源 `docs/menu-content-parity-defect.md`。

> **菜单阴影与顶卡回源（2026-11-09，KUI-023）**：对照固定Flutter/Prism修正29dp半径的sigma换算、仅主体投影并扣内部、普通/玻璃阴影接续，以及顶层卡片矩形/等比回源和后层隐藏。857项JVM结果（App575执行、其余282复用）、Debug/签名Release/独立测试包与双Lint通过；Redmi最终13/13、23图和深浅两段107/103帧原PTS视频已核验，旧联合阴影红测失败保留。GPU替换未过像素门槛已撤回。正式包进程打开69/209→68/209、关闭20/178→20/176；Popup打开24/147→29/144，未证明性能收益。手机保留本地签名修正版，完整Flutter视觉/慢帧仍PARTIAL。证据 `docs/acceptance/menu-shadow-parity-redmi-20261109.json`，溯源 `docs/menu-shadow-parity-defect.md`。

> **菜单准备阶段与分窗口复核（2026-11-09）**：截图准备即停涟漪、清空隐藏图层两个候选均未改善并已撤回，手机恢复KUI-022优化版。新增离线分窗口报告工具，4项边界测试及72份真实帧表解析通过；18次关闭Popup均已销毁，明确标不可观测。当前对照进程打开62/210、Popup自身21/149，两者不能混称；Android16 RTAnimation/Skipped位可重叠。下一步按Popup独立数据核对固定Flutter阴影轮廓、裁剪与回源透明度，完整性能/视觉仍PARTIAL。证据 `docs/acceptance/menu-source-preparation-redmi-20261109.json`，记录 `docs/menu-source-preparation-investigation.md`。

> **隐藏源按钮涟漪优化（2026-11-09，KUI-022）**：菜单接管按钮快照后暂停隐藏按钮的Material3涟漪，回源恢复；代理工具/组选单、配置顶栏与空态新增、Provider过滤、正文历史共6个入口复用。正式签名R8深浅各三轮，原包反测打开130/228帧jank（57.02%）→最终67/205（32.68%），关闭17/179→17/180未见退化；背景Window绘制99→53帧，8对终态排除状态栏后逐像素一致。854项JVM结果（App572执行，其余282复用）、Debug/签名Release/独立测试包和双Lint通过；Redmi最终11/11、20图校验目视，首轮10/11的Espresso焦点失败保留并修正测试。文字模糊诊断无收益已撤回；Simpleperf原生采样被本ROM SELinux上下文拒绝，探针保留、临时权限/组件撤回。手机保留本地签名优化版，公开0.1.0资产不覆盖；菜单剩余慢帧和完整视觉仍PARTIAL。证据 `docs/acceptance/menu-source-ripple-redmi-20261109.json`，溯源 `docs/menu-hidden-source-ripple-defect.md`。

> **菜单线程归因与阴影候选（2026-11-09，KUI-021续）**：6份ART dual-clock trace已完整校验，软件阴影关闭窗口主线程采样CPU约89–138ms，渲染提交长等待主要不在主线程CPU；没有原生RenderThread证据，不能认定GPU/模糊唯一根因。完整裁边与75%分辨率候选固定帧外观通过，半分辨率失败保留；正式签名深浅各三轮对照打开jank58.74%→55.51%/55.60%，收益不足，产品候选全部撤回，仅保留独立方法采样入口。手机恢复23284d4b网速卡紧凑版。完整Flutter视觉/菜单性能仍PARTIAL；下一步分离原生渲染线程等待、文字模糊合成和窗口初建。证据 `docs/acceptance/menu-methods-redmi-20261109.json`，溯源 `docs/menu-content-compositing-defect.md`。

> **网速卡留白再压缩（2026-11-09，KUI-020补充）**：按用户反馈将累计值下方趋势带18dp→8dp，含间距区域22dp→12dp；Redmi正式签名本地修正版深浅停止态、深色运行态6次采样均保持卡片标题间距589px，较公开0.1.0的617px减少28px，累计数值与趋势可见。4项定向JVM、2/2首页组件回归、签名Release与独立测试包构建、Release Lint（0错误/66警告/13提示）通过；不冒充本轮854项全量重跑。手机已覆盖安装并回拉核对，保留配置，测试包清理、深色/字号1.0、服务停止。公开Release资产未改写；菜单首开/关闭线程归因和剩余视觉仍待继续。证据 `docs/acceptance/traffic-card-compact-redmi-20261109.json`。

> **菜单性能调查（2026-11-09，KUI-021）**：完成透明正文图层、硬件阴影、缓存软件阴影、源按钮图层复用四种候选核验。三个签名R8候选深浅各三轮24/24终态均通过，854JVM/双Lint通过，但首开/关闭无充分收益；硬件阴影未过外观门禁，全部撤回，产品源码与公开0.1.0保持一致。缓存软件阴影10对固定帧像素完全一致仍不足以证明性能。新增关闭窗口存活期诊断回收4/9/18条帧，早段UI区间最大75.928ms、渲染最大27.901ms，仅经过时间不作CPU归因。下一步采样首开/关闭前100ms的主线程工作，保留关闭前帧表。公开APK已恢复到Redmi，测试包清理、night=yes、服务停止；原配置/1.yaml保持。证据 `docs/acceptance/menu-performance-investigation-redmi-20261109.json`，溯源 `docs/menu-content-compositing-defect.md`。

> **发布后接续（2026-11-09，正式Release菜单基线）**：公开0.1.0签名R8包在Redmi停止态深色、冷进程三轮真实菜单开/展开/收回/关闭，12/12终态通过；无录屏/截图采样。打开68/116帧jank（58.62%）、展开14/92（15.22%）、收回10/65（15.38%）、关闭10/90（11.11%）。这是真实配置正式包基线，不能与旧.motion夹具直接比较；关闭Popup消失后每轮只剩1条Flags=0记录，RenderThread仍未归因。下一步优先正式菜单首开，关闭需在窗口消失前采帧再归因；透明内容层是源码候选，尚未修改/证实收益。证据 `docs/acceptance/release-menu-redmi-20261109.json`。

> **正式发布完成（2026-11-09，KClash 0.1.0）**：中英文README、独立发布密钥/备份和标签签名CI已落地；`kclash-v0.1.0`固定`961b147d`，versionCode1000，Release为非预发布/Latest。CI[42511426890](https://github.com/lonnnnnng/FlClash/actions/runs/42511426890)全通过，854项JVM、本地双Lint与Go双层race通过；KREL-001正式YAML导入崩溃、KUI-020出口闪烁/网速跳高已修并留溯源。公开APK下载SHA256/资产digest/签名/版本/ABI全部核对，Redmi安装回拉一致；公开包67.7秒16次样本0查询态、IP/国家不变、卡片间距恒定617px（旧694px），VPN/tun0、停止/冷启动通过。本地最终固定/自动两轮各18次也通过。手机保留正式包、一份授权本地配置和原自动选择，服务停止；后续只用Redmi。发布页：https://github.com/lonnnnnng/FlClash/releases/tag/kclash-v0.1.0 。证据 `docs/acceptance/release-0.1.0-20261109.json`。跨版本升级、完整视觉/性能、低API及蜂窝/OEM仍PARTIAL，玻璃默认关闭。

> **最新切片（2026-11-09，CUSTOM 组数芯片与首页统计核对）**：CUSTOM 配置卡新增按本地正文解析的代理组数芯片，未就绪/正文缺失时隐藏，已就绪时显示紧凑数量并保留完整语义描述；Redmi `ProfilesVisualInstrumentedTest#profilesScreenshotMatrixMatchesBaseline` `1/1`，固定 CUSTOM 6 组截图哈希更新为 `cd7cd44392fb23dd888b2ea6a18b9d2e84bbe31eda87311e41f1992c91122e04`，远程配置旧基线保持；长列表选中/长按回归 `1/1`。首页统计核对 `DashboardInstrumentedTest` 运行态卡片与历史重建 `2/2`：上传/下载双流量块、30 点历史，以及连接/请求/DNS 三列摘要均已覆盖；Flutter 手机参考同样是双流量块，不新增“三列流量”实现。证据 `docs/acceptance/custom-profile-chip-dashboard-redmi-20261109.json`。Perfetto 首开/关闭线程归因仍受 Redmi/API36 flush 阻塞，下一步待可用 trace 设备/ROM；继续剩余子页视觉，玻璃默认关闭。

> **最新切片（2026-11-09，菜单线程诊断与内存子页视觉）**：菜单首开/关闭诊断已将 Trace 区间扩展到打开/收回可见终态，Redmi 深色 `menu-diag-dark3` 精确 `1/1` 通过；但 Perfetto 自然结束仍 `traced_flushes_failed=1`、`traced_final_flush_failed=1`，`sched_slice=0`、应用线程和菜单 slice 均为空，RenderThread 根因未能归因，不能把失败 trace 计入性能结论。设备日志记录 `Periodic flush timed out`；保留失败 trace 与探针，不继续在同一 ROM 重复无效长批次。内存详情子页 `DashboardInstrumentedTest#memoryCardAndDetailsCaptureDarkAndLight` Redmi `1/1`，深浅主题卡片/详情/完整窗口截图已回收并目视无重叠或裁切；固定 fixture 仅用于视觉层级，不代表真实 Go 数值。Debug/Test APK 已构建并安装回验，正式配置 0 份、服务停止、Flutter 参考包缺席、`1.yaml` SHA 保持。证据 `docs/acceptance/menu-rendering-diagnostic-redmi-20261109.json`、`docs/motion-performance-defect.md`、`../acceptance-artifacts/memory-visual-redmi-20261109/`。下一步在可用 trace 设备/ROM 上重跑线程归因，再补 CUSTOM 组数芯片、首页无流量统计及其余子页视觉；玻璃默认关闭。

> 最新切片（2026-11-08，菜单动态顶卡与精确复验）：KUI-019继续优化菜单动态轮廓：阴影局部重组、同帧Path/Paint复用、宽度延迟布局及移除重复裁剪。Redmi API36独立非debuggable包、无PNG/录屏，前后各两轮深浅，菜单合计jank331/1429（23.16%）→261/1555（16.78%），顶卡收回38.61%→14.07%；首开26.42%→23.16%改善有限，关闭6.91%→9.94%退化保留，首次打开UI区间中位也未改善。完整自然流程各深浅一轮非启动窗口15.10%→13.39%，不冒充正式R8运行。final-build3共849项JVM零失败/错误/跳过（App568在build1执行、其余281复用，最终四模块UP-TO-DATE）；Debug/Test/未签名Release-R8/双Lint通过，Debug0错误68警告14提示、Release0错误66警告13提示。同套最终包回归19/19、自然整页2/2、三轮菜单2/2；92PNG/32拼图及8组原PTS抽样已核对，593源码与安装回拉匹配。用户确认重置，当前正式0份配置，排除依赖非空库的两条导航回归；Flutter参考包保持缺席、不安装。44份正式快照及最终独立检查一致，原1.yaml/设备设置保持；独立.motion双包与独占文件精确回收，结束无服务/tun0。旧包/候选12图逐字节一致后刷新环境基线，14/19、旧包0/5与run-as拒绝回拉失败均保留。整体性能和完整Flutter视觉仍PARTIAL；下一步优先首开、关闭退化及RenderThread慢帧，再补CUSTOM组数、首页无流量统计、内存图形与其余子页。玻璃默认关闭，既有边界保持。证据`docs/acceptance/menu-rendering-redmi-20261108.json`，溯源`docs/menu-rendering-performance-defect.md`，附件`../acceptance-artifacts/menu-rendering-20261108/`。

> 历史切片（2026-11-08，主页面视觉与自然时钟动效）：主页面/常用面板具名排版与自然时钟交互本组已验（VIS-01～06、MOT-01）：固定Flutter be847366及Prism63450c0，统一首页/配置标题、配置卡/快切布局、根面板与代理选择标题栏、质量加载占位、代理整页搜索空态和短副标题。最终build6同套包Redmi回归14/14、自然时钟深浅场景2/2，62张PNG完整核验/22拼图目视，原始MP4及6组原始PTS抽帧、存活期gfxinfo已留存。849项JVM零失败/错误/跳过，App568执行、其余281复用；Debug/Test/未签名Release-R8/双Lint通过，Debug0错误68警告14提示、Release0错误66警告13提示。593份源码和双包回拉SHA匹配，Release测试宿主隔离。正式两配置、选择/Room偏好/正文SHA-size-mtime、原1.yaml/Flutter包/设备设置保持，独占录屏与gfx缓存已清理，结束无服务/tun0。自然场景不使用ComposeTestRule，验证快速反转导航、搜索/IME、多层菜单和质量六行加载前后Y坐标一致；固定宿主不冒充正式实网，视频PTS不等于渲染耗时。旧哈希失败、节点缓存/半屏定位失败及无进程gfx记录均保留。连续动效性能未通过：最终累计1872帧中592帧jank（31.62%），第二主题包含第一主题不可相加；Debug/录屏/PNG开销尚未隔离，下一步优先独立定位掉帧。浅色固定Debug宿主状态栏图标对比度不计正式主题验收。本机Flutter3.41.9低于固定Prism3.47要求，本轮是源码参数与Kotlin渲染对齐，完整像素一致/性能仍PARTIAL；随后CUSTOM组数、首页无流量统计、内存图形层级与其余子页。原第6项异常/系统网络边界仍待，玻璃默认关闭。证据`docs/acceptance/main-visual-motion-redmi-20261108.json`，溯源`docs/main-visual-motion-alignment.md`，附件`../acceptance-artifacts/visual-motion-20261107/`。

> 历史切片（2026-11-07，真实资源选择器恢复与提交）：真实资源选择器SAF-01～05本组已验：规则YAML/MRS、图库OpenDocument及组图片GetContent接独立加密请求、一次消费和父会话绑定；规则预览正文/选区/错误/MRS字节可冷恢复，目录与回执同事务防重放。Redmi最终同套build13包：交互7/7、冷恢复14/14及再次恢复3/3、相邻回归4/4；38张PNG完整校验、13拼图目视，其中6张系统转场图不作稳定终帧。849项JVM零失败/错误/跳过（App568本批执行，其余281复用），Debug/Test、未签名Release-R8与双Lint通过，Debug0错误68警告14提示、Release0错误66警告13提示。591份源码及安装回拉SHA匹配，Release25个恢复字段和Debug诊断隔离已检查。正式两配置/正文SHA-size-mtime、脚本、偏好与选择保持；原1.yaml、Flutter包、旋转/字号/动画设置保持；自有草稿/回执/临时行和9个独占设备输入已清理，结束无服务/tun0。KUI-017保留ready恢复竞态、选区及全部开发失败；系统选择器只杀普通宿主PID并观察系统新PID，预览/提交用受控force-stop。失效URI为显式协议注入，读取死亡限受理检查点，不推广任意流字节/OEM/VPN自然回收。下一步统一Flutter页面/面板/空态排版与连续动效；整体第9项仍PARTIAL，第6项原保护异常/网络稳定性、蜂窝及兼容/发布边界保持，玻璃默认关闭。证据`docs/acceptance/resource-picker-recovery-redmi-20261107.json`，溯源`docs/resource-picker-recovery-defect.md`，附件`../acceptance-artifacts/resource-picker-20261107/`。

> 历史切片（2026-11-07，独立全局规则恢复与提交）：独立全局规则页GR-01～05本组已验：草稿/错误/选区/放弃确认、可见批选失败重试、原行修改/删除及排序冲突、独立Keystore密文、Room同事务回执防重放和冻结应用目标。Redmi正式交互5/5、cold2冷恢复14/14及提交后再次恢复2/2、相邻回归4/4；16张PNG完整校验与6拼图目视。843项JVM零失败/错误/跳过，App562在build6执行、其余281复用；build8四模块UP-TO-DATE，Debug/Test/未签名Release-R8与双Lint通过（Debug0错误68警告14提示，Release0错误66警告13提示）。最终586份源码匹配，Release14个序列化字段及Debug诊断隔离已检查。真机使用build7同套包；build8仅补未执行的旧GlobalSettingsHost测试收尾，所有产品/实际执行用例源码和Debug/Release APK一致，新的测试APK未安装。正式两配置、正文SHA-size-mtime、脚本、偏好、选择、原1.yaml/Flutter包/设备设置保持，自有规则/草稿/回执清理，结束无服务。KUI-016记录来源及红绿证据；cold1旧after选区失败缺死亡前观测，原因不追认，cold2逐场前后严格比对通过。STOPPED受控force-stop不推广VPN/OEM自然回收，切配置目标冻结由JVM覆盖。下一组真实资源选择器SAF-01～05，随后统一Flutter视觉/连续动效；整体第9项仍PARTIAL，第6项原异常/网络稳定性、蜂窝及兼容/发布边界保持，玻璃默认关闭。证据`docs/acceptance/global-rules-recovery-redmi-20261107.json`，溯源`docs/global-rules-recovery-defect.md`，附件`../acceptance-artifacts/global-rules-20261107/`。

> 历史切片（2026-11-07，Provider校验取消与正文终帧）：Provider校验返回后、保存受理前的Activity重建取消已验：旧任务取消且释放旧门闩不迟到写库，过滤/450秒草稿保持，显式重试后revision仅+1。正文saving_recreated补采位移归零的稳定终帧，固定运行时仅保存一次。Redmi final1精确18/18（8条正式MainActivity/Hilt/Room＋9条组件回归＋1条正文固定运行时），39张PNG完整校验及13拼图目视；8次正式保护与7入口入场Back通过。build1共830项JVM零失败/错误/跳过，App549执行、其余281复用；389任务45执行/344复用，Debug/Test/未签名Release-R8与双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。安装回拉及580份源码匹配，Release等待点接口/标记隔离；正式两配置/正文SHA-size-mtime/脚本行/偏好/选择、原1.yaml/Flutter包/设备设置保持，自有临时行及正文删除，收尾无服务。未新增产品缺陷；取消窗口不推广原生回调执行中，正文终帧不推广正式缓存/冷进程或连续动效性能。下一组独立全局规则页GR-01至GR-05，再真实资源选择器SAF-01至SAF-05，具体清单见docs/remaining-main-page-acceptance.md；随后统一Flutter视觉/连续动效。第6项原保护异常、未知偏好变化、网络稳定性和运行态final6-before超时仍待查，蜂窝延期、玻璃默认关闭。证据`docs/acceptance/provider-validation-boundary-redmi-20261107.json`；附件`../acceptance-artifacts/provider-validation-boundary-20261107/`。

> 历史切片（2026-11-07，Provider子页重建与入场返回）：KUI-015已红绿复现并修复：Provider选项本地错误/放弃确认与快捷YAML格式错误随Activity重建保存；局部校验忙态不持久化。Redmi final4精确16/16（7条正式MainActivity/Hilt/VM/Repository/Room用例＋9条组件回归），36张PNG完整校验及12拼图目视；过滤/IP/绑定搜索恢复、保存等待重建与防重复、7个子页入场中系统Back均通过。build5共830项JVM零失败/错误/跳过，App549在build1执行、其余281复用，最终四模块UP-TO-DATE；389任务11执行/378复用，Debug/Test/未签名Release-R8及双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。双包安装回拉和578份源码匹配；正式两配置/正文SHA-size-mtime/脚本行/偏好/选择、原1.yaml/Flutter包/设备设置保持，7次保护回报确认临时Room行及正文文件均删除，收尾无服务。red1 0/3、final1 14/16、final2与final3各15/16独立保留，不累计。新增正式用例为Activity重建，非冷进程/VPN/OEM自然回收；入场Back已确认目标窗口焦点及64ms时横向位移，非焦点交接前按键或连续动效性能。局部异步校验重建取消、正文保存终帧、独立全局规则页/资源选择器冷恢复仍待补，再统一Flutter排版/连续动效。第6项原保护异常、未知偏好变化、网络稳定性和前组final6超时仍待查；玻璃默认关闭，既有暂缓/取消范围保持。证据`docs/acceptance/provider-options-state-redmi-20261107.json`；溯源`docs/provider-options-restoration-defect.md`；附件`../acceptance-artifacts/provider-options-state-20261107/`。

> 历史切片（2026-11-07，Provider选项交互）：KUI-014已红绿复现并修复：选项页重复同一错误按提交次数回顶，选项/快捷YAML从实际Dialog取得焦点与IME控制器，保存后收键盘。Redmi final2精确9/9（6新增+3相邻回归）、21张PNG完整校验及7拼图目视；覆盖长过滤与YAML、逐层返回/放弃重开、校验/保存防重复、代理/规则各40项绑定失败取消重试及别名保持。build4 App549执行、Service212/Core38/Common31复用，共830项JVM零失败/错误/跳过；389任务22执行/367复用，Debug/Test/未签名Release-R8及双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。安装回拉与577份源码匹配；正式两配置/正文SHA-size-mtime/偏好/选择、原1.yaml/Flutter包/设备设置保持，收尾无服务。red1 0/2、final1 6/9和build3既有Picker单测清理等待失败保留；单测改等实际关闭终态后build4全量通过。此组为正式Compose页面的固定组件宿主/受控回调，非新增MainActivity/Room/核心/实网或冷进程验收；返回先等Window入场，静态终帧不等于连续动效。本组选项/绑定Activity重建与入场中Back、独立全局规则页/资源选择器冷恢复、上一组正文保存终帧继续待补，再统一Flutter排版/连续动效。第6项原保护异常、未知偏好变化、网络稳定性和前组final6超时仍待查；玻璃默认关闭，既有暂缓/取消范围保持。证据`docs/acceptance/provider-options-interactions-redmi-20261107.json`；溯源`docs/provider-options-validation-defect.md`；附件`../acceptance-artifacts/provider-options-interactions-20261107/`。

> 历史切片（2026-11-07，Provider运行态与MRS）：Provider本组已验：MRS只读正文首次/再次冷恢复及原面板返回通过；纯代理保存前后SIGKILL经外部独立观察系统新PID/前台服务，恢复不自动重放、显式重试仅一次；停止态/运行态各源文件或源记录缺失4类真实setup拒绝后回滚旧输入/选择/会话并成功切往有效目标，epoch各增加2。KUI-013父面板冷恢复后错误常驻已红绿复现并修复，KUI-010补齐实核回滚证据。Redmi final7运行态/回滚6/6、12次同socket核心归因HTTP200，MRS 1+1、相邻回归10/10，22张PNG完整校验及8拼图目视。build7 App549实际执行、Service212/Core38/Common31复用，共830项JVM零失败/错误/跳过；389任务28执行/361复用，Debug/Test/未签名Release-R8及双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。安装回拉及579份Android源码匹配；正式两配置/正文SHA-size-mtime/偏好/脚本/选择、原1.yaml/Flutter包/设备基线保持，临时对象/密文/标记清除，无服务或tun0。MRS为真实MainActivity STOPPED，首次存活PID受控force-stop、再次恢复前进程已退出，运行态为Debug宿主真实VM/Room/核心的纯代理SIGKILL，非VPN/OEM自然回收或完整点击导航。final6-before的verify超时原因未定，后续通过不抹除；第6项原保护异常、未知偏好变化与网络稳定性仍待查。选项错误截图滚动后提示部分裁切、筛选与保存图含转场中间帧，下一组补键盘错误可见性、稳定终帧及剩余子页/面板，再集中Flutter排版/连续动效；独立全局规则页/资源选择器冷进程另列。效果/性能/兼容/发布按原顺序，蜂窝延期、玻璃默认关闭。证据`docs/acceptance/provider-runtime-redmi-20261107.json`；溯源`docs/provider-cold-parent-refresh-defect.md`；附件`../acceptance-artifacts/provider-runtime-20261107/`。


> 历史切片（2026-11-07，扩展页冷恢复）：扩展页局部/全局规则独立加密会话、提交前后冷恢复、事务回执防重放、外部规则修改/删除及宿主删除保护已验，KUI-012本组闭环。Redmi final2首次冷恢复18/18、提交后与回执缺口再次恢复4/4，相邻回归11/11，33张PNG完整校验并经11张拼图目视。828项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；执行/复用明细以本组JSON为准。Debug Lint0错误/67警告/13提示，Release0错误/66警告/12提示。安装回拉和578份源码匹配；两份正式配置/正文SHA-size-mtime/脚本/偏好/选择、原1.yaml、Flutter包及设备设置保持，临时对象、密文和提交标记清除，收尾无服务。首轮local-applied恢复过早就绪及后台回执覆盖新UI树已分别红绿复现并修复，原失败保留。本组仅STOPPED/受控force-stop；不推广运行态死亡、OEM自然回收、独立全局规则页冷恢复或完整Flutter视觉。下一组Provider运行态/MRS只读冷恢复及失效引用实核回滚，再统一Flutter视觉；第6项原保护异常、未知偏好变化和网络稳定性仍待查。蜂窝延期，玻璃默认关闭。证据`docs/acceptance/profile-extension-cold-redmi-20261107.json`；溯源`docs/profile-extension-cold-recovery-defect.md`；附件`../acceptance-artifacts/extension-cold-20261107/`。



> 历史切片（2026-11-07，配置管理窗口冷恢复）：配置管理重命名/删除/排序独立加密会话、保存意图与提交结果核对、失败原位重试及外部目标冲突保护已验，KUI-011本组闭环。Redmi final7首次冷恢复17/17、提交后再次恢复3/3，相邻回归11/11，33张PNG完整校验并经11张拼图目视。814项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；App533在本组build4实际执行，Service212/Core38/Common31复用，最终build8四模块UP-TO-DATE、389任务11执行/378复用。Debug Lint0错误/67警告/12提示，Release0错误/66警告/11提示。安装回拉和572份源码匹配；正式两份配置/正文SHA-size-mtime/脚本/偏好值/选择、原1.yaml、Flutter包与设备设置保持，临时对象与草稿清除，收尾无服务。本组仅STOPPED下真实MainActivity/Hilt/Room、受控force-stop；不代表运行态死亡、OEM自然回收或完整Flutter视觉。final5早先17+3通过独立保留，final7增加实际Room名称/顺序和正文结果断言；中间失败与精确恢复见KUI-011，不累计。下一组为扩展页独立冷恢复、保存中断与原规则外部修改/删除保护；Provider运行态/MRS只读冷恢复、统一Flutter视觉及第6项原保护异常/网络稳定性继续待补。蜂窝延期，玻璃默认关闭。证据`docs/acceptance/profile-management-cold-redmi-20261107.json`；溯源`docs/profile-management-cold-recovery-defect.md`；附件`../acceptance-artifacts/management-cold-20261107/`。


> 文档类型：产品迁移路线图、功能矩阵、依赖图和验收门槛
>
> 最后核对：2026-11-07（北京时间，优先完成主页 → 配置 → 代理组；观测页面的后续打磨暂停）
>
> 历史切片（2026-11-07，Provider正文冷恢复）：Provider正文独立加密草稿与正式冷恢复已验：正文/选区/失败/放弃确认跨进程保持；保存前中断等待显式重试、提交后恢复不重放；外部正文冲突、配置删除、引用源记录/文件删除保留草稿并阻止写入；派生缓存缺失由未变源安全重建。KUI-010失效引用配置无法切走已修，保留原ID/节点选择及已应用输入用于失败回滚。Redmi final5九场景首次恢复9/9、提交后及两类源删除再次恢复3/3，相邻回归10/10，22图完整校验并目视。802项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；App521/Service212本次重跑，Core38/Common31复用，build11共389任务60执行/329复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。安装回拉与源码匹配，正式两份配置/正文SHA-size-mtime/脚本/偏好值/选择、原1.yaml、Flutter包与设备设置保持，临时对象和密文清除，收尾无服务。本组为STOPPED下真实MainActivity/Hilt/Room/Bridge、受控force-stop与授权原1.yaml的自有副本；不推广运行态死亡窗口、MRS只读冷恢复、全部Provider类型、OEM自然回收或完整Flutter视觉。KUI-010运行态回滚有JVM证据，尚非实网验证。首轮质量缓存ip_quality_cache_v1变化、提交后准备失败、final3旧提示提前放行、final4源删除后切换缺陷以及对应精确恢复均独立保留；本次键级归因不追认前组未知偏好变化，第6项原保护异常与网络稳定性仍待查。证据`docs/acceptance/provider-document-cold-redmi-20261107.json`；溯源`docs/provider-document-recovery-defect.md`、`docs/broken-current-profile-switch-defect.md`；附件`../acceptance-artifacts/provider-document-cold-20261107/`。下一组继续管理窗口与扩展页冷进程、保存受理中断、目标外部删除，再统一Flutter视觉/连续动效；Provider运行态与MRS只读冷恢复另列补验。蜂窝延期，效果/性能/兼容/发布按原顺序，玻璃默认关闭。

> 历史切片（2026-11-07，正式本地代理异常链路）：正式MainActivity本地代理异常链路已验：真实Room失败经Snackbar反馈、菜单重试只新增一次并跨重建回读；真实配置卡片在受理前切换拒绝写入、受理后保存冻结原节点；40份原正文CUSTOM副本的真实引用查询、首尾滚动、取消保留批选、等待防重复、删除失败原位提示/重建/重试及引用正文保持通过。Redmi final3精确18/18（4新增+14相邻回归）、55图完整校验及16拼图目视；788项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过，安装/源码匹配。两份正式配置/脚本/正文/偏好值/选择、原1.yaml、Flutter和设备设置保持，临时副本/本地节点/SQL触发器清理，收尾无服务。App512在本组build1/build2实际执行，其余276复用；最终build3四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。本组为STOPPED下真实MainActivity/Hilt/Bridge/正式Room；窄Debug门闩仅控制校验/保存前等待，SQL错误类型与固定标记计数证明故障命中，Release无布置接口/测试标记。长引用按名称扫描原文，原文同名inline节点优先，不证明删除后路由失效。未新验运行态、冷进程、全部输入法或完整Flutter视觉。证据`docs/acceptance/formal-local-proxy-redmi-20261107.json`，范围与首轮失败记录`docs/formal-local-proxy-acceptance.md`，附件`../acceptance-artifacts/formal-local-proxy-redmi-20261107/`。下一组推进管理窗口/扩展/Provider正文冷进程、保存受理中断、目标外部删除及Provider独立加密草稿，再统一Flutter视觉/连续动效。第6项原保护异常、前组偏好变化及网络稳定性仍待查；蜂窝延期，效果/性能/兼容/发布按原顺序，玻璃默认关闭。

> 历史切片（2026-11-07，ALPN停止回写修复）：KUI-008已定位并修复：本地代理输入提交后立即重建，旧窗口ON_STOP后由Compose onBlur回传旧文本；正式页面拒绝已停止宿主的业务修改，独立保存树照常捕获。Redmi final1精确14/14、43图完整校验及13拼图目视；语义输入/失焦与平台InputConnection各8轮，含合法清空，VM及页面精确值保持。788项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；安装回拉匹配，两份正式配置/脚本/正文/偏好/选择及原1.yaml/Flutter/设备设置保持。App512本组build3实际执行，其余276复用；最终build4四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。本组为Debug隔离宿主上的正式页面/VM，InputConnection由测试驱动，不代表全部输入法、正式MainActivity全链路、冷进程或完整Flutter视觉。diagnostic1为1/2、diagnostic2为0/1，两次旧值覆盖及onBlur栈保留；前批final1原始空值未留VM轨迹，不能倒推百分百同因。证据`docs/acceptance/alpn-input-boundary-redmi-20261107.json`，溯源`docs/local-proxy-stopped-input-defect.md`，附件`../acceptance-artifacts/alpn-input-boundary-redmi-20261107/`。继续正式MainActivity节点添加失败/重试、保存等待切配置和真实长引用查询；随后管理窗口/扩展/Provider正文冷进程、保存受理中断、目标外部删除及Provider独立加密草稿，再统一Flutter视觉/连续动效。第6项原保护异常、前组偏好变化及网络稳定性继续待查；蜂窝延期，效果/性能/兼容/发布按原顺序，玻璃默认关闭。

> 历史切片（2026-11-07，本地代理确认与节点添加）：KUI-006/007已修：本地代理删除/替换确认可滚动且原位显示失败与等待；添加节点异常按阶段脱敏，重复分支也复核当前配置。Redmi final2精确12/12、43图经完整集合/哈希/解码及13张拼图目视；新增5条覆盖40个长引用、隔离Room真实失败/重试/防重复、实核拒绝、受理前后切配置及单/批链接真实IME/Back/重建。788项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；双包回拉匹配，正式两配置/脚本/正文/偏好/选择、原1.yaml/Flutter/设备保持。App512本组build1/build2实际执行，其余276复用；最终build6四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。新增5方法为Debug隔离宿主、真实Bridge与隔离Room，长引用结果受控注入，不代替正式MainActivity添加失败/切配置、真实引用查询、运行态或冷进程。final1的ALPN立即重建曾丢值，11/12；最终8轮输入重建回归通过仍未定位根因，保留待查。完整Flutter视觉仍PARTIAL。证据`docs/acceptance/local-proxy-interactions-redmi-20261107.json`，溯源`docs/local-proxy-interaction-defects.md`，附件`../acceptance-artifacts/local-proxy-interactions-redmi-20261107/`。优先补正式MainActivity节点添加失败/重试、保存等待切配置及真实长引用查询，并定位ALPN立即重建间歇丢值；再推进管理窗口/扩展/Provider正文冷进程、受理中断、外部删除及独立加密草稿；随后统一视觉，系统/性能/发布按原顺序。第6项原保护异常、前组偏好变化及网络稳定性仍待查，蜂窝延期，玻璃默认关闭。

> 历史切片（2026-11-07，扩展批选与远程新增表单）：KUI-004/005已修：扩展批选与删除确认重建保持，筛选仅删可见已选项、失败原位重试、等待防重复及外部移除收口；远程新增原位校验、真实IME/Next/Done、40条批量首尾滚动/模式/来源往返/重建/取消与临时密钥清理已验。Redmi final4 精确26/26、35图完整校验并经10张拼图目视；782项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8和双Lint通过。双包安装回拉匹配，两份正式配置/脚本/正文/偏好/选择及原1.yaml/Flutter/设备设置保持，临时副本清理、无服务。App506本组build1/build3实际执行，其余276项复用；最终build6四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。证据`docs/acceptance/extension-batch-remote-form-redmi-20261107.json`，溯源`docs/extension-batch-remote-form-defects.md`，本机附件`../acceptance-artifacts/extension-batch-remote-form-redmi-20261107/`。下一组优先代理添加到本地的实核校验/保存失败及等待中切换配置、本地代理删除长引用/取消/失败和分享链接IME返回；随后管理窗口/扩展/Provider正文冷进程、保存受理中断、目标外部删除及正文独立加密草稿，再统一Flutter视觉/连续动效。第6项原保护异常、前组偏好变化及网络稳定性仍待查，蜂窝延期，玻璃默认关闭。

> 历史切片（2026-11-07，扩展页重建与键盘错误恢复）：修复KUI-003：真实MainActivity扩展页重建复用原会话，保留全局子页/搜索、长脚本列表、规则草稿/目标/校验错误及放弃确认；挂起保存不被重连作废，明确重试和退出重开重新读取。真实IME上方操作、两层返回、连续确认只保存一条规则及重新打开回读已验。Redmi final5精确13/13，33张PNG分片回传/解码/哈希校验并经7张拼图目视；778项JVM XML零失败/错误/跳过，Debug/Test、未签名Release-R8和双Lint通过。App/Test回拉匹配，最终安装前/验收前后两份正式配置、正文、偏好/选择、原1.yaml、Flutter基线和设备设置保持，临时配置/脚本清理，收尾无服务。App502于build1重跑，其余276项复用，最终build5四模块UP-TO-DATE。证据`docs/acceptance/main-extension-state-redmi-20261107.json`，缺陷`docs/profile-extension-restoration-defect.md`，本机附件`../acceptance-artifacts/main-extension-state-redmi-20261107/`。原始red1/red2均0/3、挂起保存红灯1/1失败；final1汇总11/13且偏好摘要变化、Binder回传缺两条；final2汇总13/13但仍缺两条明细；final3已13/13完整回传，静态截图退场采样不足，final5采样650ms复验。首轮偏好具体键缺少基线、原因未定，未回写旧偏好，不能消除第6项原保护异常。扩展其余批选/删除确认、远程新增IME/长批量/无效输入/取消、代理添加到本地失败及等待中切换配置、本地代理删除长引用/失败和分享链接IME返回仍待验；管理窗口/扩展/Provider正文冷进程、保存受理中断、目标外部删除及正文加密草稿另行推进。完整Flutter视觉/动效仍PARTIAL，玻璃默认关闭。
>
> 历史切片（2026-11-06，配置管理窗口重建恢复）：修复KUI-002：真实MainActivity配置重命名草稿、删除确认、排序草稿和放弃确认跨Activity重建保持；取消不写入，明确保存/删除仅作用于测试临时副本。Redmi final2精确9/9，25张PNG校验并经3张拼图目视；775项JVM XML零失败/错误/跳过（App499在build3重跑，其余276复用，最终build4四模块均UP-TO-DATE），Debug/Test、未签名Release-R8和双Lint通过。App/Test实际回拉匹配，两份正式FILE/URL配置的正文/元数据/顺序/偏好/选择、原1.yaml、Flutter基线及设备设置保持，临时行清理，收尾无服务。原始red1 0/3及中间final1 8/9分别保留；后者仅为输入框matcher修正，产品不变。证据 `docs/acceptance/main-profile-management-redmi-20261106.json`，缺陷 `docs/profile-management-restoration-defect.md`，附件 `../acceptance-artifacts/main-profile-management-redmi-20261106/`。管理窗口/正文冷进程、运行态与完整Flutter视觉仍PARTIAL，玻璃默认关闭。
>
> 历史切片（2026-11-06，Provider正文真实主宿主）：真实MainActivity首页配置详情→Provider正文链路已验：真实侧载失败保留长草稿/错误/选区并跨后台和Activity重建，放弃确认重建后可继续/放弃；有效缓存保存后退出、重建、重开回读且资源源文件不变；原配置MRS只读预览滚动位置跨重建保持并返回同一面板。Redmi final2精确9/9，21张PNG哈希/解码验证并经4张拼图目视；775项JVM XML零失败/错误/跳过（本次四模块任务均UP-TO-DATE），Debug/Test、未签名Release-R8和双Lint通过。最终双包实际回拉匹配，两份正式FILE/URL配置、正文/元数据/偏好/选择、原1.yaml、Flutter基线和设备设置保持，临时配置及自身投影缓存清理，收尾无服务。首轮7/9和IP质量缓存写入造成的偏好保护失败单独保留，不与最终批次累计。证据 `docs/acceptance/main-provider-document-redmi-20261106.json`，附件 `../acceptance-artifacts/main-provider-document-redmi-20261106/`。正式宿主的Activity重建已补验，冷进程/运行态和完整Flutter视觉仍PARTIAL，玻璃默认关闭。
>
> 历史切片（2026-11-06，真实主页面往返与List重建）：真实MainActivity代理页跨配置/首页往返、List布局/分组/滚动/吸顶标题跨Activity重建、搜索空态保留与清除恢复已验。KUI-001修正List首次恢复14px偏移和吸顶标题消失：首个非空实际布局后重新应用保存的索引/偏移，无固定延迟或像素补偿。Redmi最终同套App/Test final5精确9/9，27张PNG哈希/解码验证并经4张拼图目视；四模块JVM XML共775项零失败/错误/跳过，App499本轮重跑，其余任务UP-TO-DATE。Debug/Test、未签名Release-R8和双Lint通过（Debug0错误/67警告/10提示，Release0错误/66警告/9提示），安装双包实际回拉匹配。两份正式FILE/URL配置仅复用，临时副本回收，正文/元数据/偏好/选择及Flutter基线、原1.yaml、设备设置保持，结束无本应用服务。证据 `docs/acceptance/main-navigation-state-redmi-20261106.json`，缺陷溯源 `docs/proxy-list-restoration-defect.md`，附件 `../acceptance-artifacts/main-navigation-state-redmi-20261106/`。原始4/6及两轮List诊断失败保留，不累计；本组不证明冷进程/运行态恢复、Provider正文正式宿主恢复或完整Flutter逐像素一致，整体仍PARTIAL，玻璃默认关闭。
>
> 历史切片（2026-11-06，代理组排序子菜单键盘）：代理组排序子菜单键盘路径已验：方向键定位排序、Enter进入、连续Escape逐层返回并关闭且不改排序；重新进入后Enter提交名称排序，节点顺序和菜单文案更新，原节点选择与刷新次数保持。Redmi API36精确6/6，3张PNG哈希验证并逐张目视；四模块JVM XML共775项零失败/错误/跳过（最终任务均UP-TO-DATE），Debug/Test、未签名Release/R8、双Lint通过（Debug0错误/67警告/10提示，Release0错误/66警告/9提示）。本组只补测试，生产代码不变；App/Test实际回拉匹配。两份正式FILE/URL配置仅复用，测试前后正文/元数据/偏好/当前选择保持，Flutter基线、原1.yaml和设备设置保持，收尾无本应用服务。证据 `docs/acceptance/proxy-sort-keyboard-redmi-20261106.json`，附件 `../acceptance-artifacts/proxy-sort-keyboard-redmi-20261106/`。键盘事件为instrumentation注入，不等于实体键盘或全应用焦点验收；完整Flutter视觉仍PARTIAL，玻璃默认关闭。
>
> 历史切片（2026-11-06，Provider正文后台与Activity重建）：后台正文/失败/读取、放弃确认和长文选区跨Activity重建、受理保存去重已验，final4精确6/6及10图目视；final3选区恢复失败5/6保留。775项JVM XML、Debug/Test/未签名Release-R8及独立双Lint通过；重装后两份获准配置已恢复，最终复用且保护一致。证据 `docs/acceptance/provider-document-lifecycle-redmi-20261106.json`。固定数据Debug宿主不证明冷进程、正式MainActivity恢复或Provider实网。
>
> 历史切片（2026-11-06，首页策略组面板返回与重建）：新增系统返回及 Activity 重建两项验收，关闭重开清空搜索、保留当前组和节点，重建保留打开状态/查询/组/选中徽标且不重复提交选择。Redmi API36 精确 7/7；14 张 PNG 全部校验，5 张新增截图逐张目视。只修改 Debug 宿主与测试，生产逻辑不变。四模块 JVM XML 共771项零失败/错误/跳过（最终build2测试均UP-TO-DATE），Debug/Test、未签名 Release/R8、双 Lint 通过；Debug0错误/67警告/10提示，Release0错误/66警告/9提示。Debug `a74002163c571eeaa486887f08b150c9b1d09ba79b8108fa09e50859ba44079b`、AndroidTest `5dfdde23cfd3a382699e4c94fa756a4a8032335d17a500794c03f23633ca74db`、未签名 Release `e322caa97d8c452f285e1feb488ad0ecdbfc330f4e3e50f60f99c4362c3ff7fc`；Redmi安装回拉哈希匹配。证据 `docs/acceptance/dashboard-proxy-picker-redmi-20261106.json`，附件 `../acceptance-artifacts/dashboard-proxy-picker-redmi-20261106/`。本组证明固定数据宿主中的 Activity 重建，不等于冷进程/正式仓库恢复或实网；完整 Flutter 视觉仍 PARTIAL，玻璃默认关闭。
>
> 历史切片（2026-11-06，代理组测速触点命中）：Tab/List 两种布局在测速可见语义边界外 8dp 点按均命中测速回调、不切换当前节点；相邻节点点击仍只切换节点。Redmi API36 `ProxyPageParityInstrumentedTest` 整类 11/11 通过，33 张截图哈希均验证、Tab/List 触点截图目视检查；四模块 JVM 771/771，Debug/Test、未签名 Release/R8、双变体 Lint 通过（Debug 0 errors/67 warnings/10 hints；Release 0 errors/66 warnings/9 hints）。Debug APK `f5be349b51797edb4f7fa8721ed3147f7f53d8bd44f35af3eb56dccc6d82f455`、AndroidTest `ff00d705620152b41c62922040300ea8d9555fe7741730d52e40c54be9bce3f1`、未签名 Release `d6c132ce7bfab786bfdcc19024cabf9fddb59aeb2702671890b4a68e9f879461`；Redmi 安装回拉哈希匹配。证据 `docs/acceptance/proxy-delay-touch-target-redmi-20261106.json`，附件 `../acceptance-artifacts/proxy-delay-touch-target-redmi-20261106/final-install/`。该测试不代表真实 Mihomo 测速；完整代理页视觉仍为 PARTIAL，玻璃默认关闭。
>
> 阅读约定：第 3 节“当前快照”和第 7 节功能矩阵记录累计现状；具名、带日期的切片只证明当时范围，旧的“下一组/未提交/当前安装包”不代表今天仍未实现或未提交。不同批次测试数量不相加，代码实现、组件测试、真实系统入口和实网行为分别判定。订阅授权以外层 `AGENTS.md` 当前唯一入口为准。验收 JSON 保留结果、哈希和本机附件索引（当前为外层`acceptance-artifacts/`，历史可能为`/tmp`）；原始日志/截图未随源码上传，远端不能仅凭路径复核附件。
> 历史切片（2026-11-06，代理组溢出菜单键盘联动）：键盘模式打开菜单后自动聚焦首个可用项，DirectionDown 移动焦点、Enter 提交所选代理组、Escape 取消并保持原选择；进入子菜单时沿用首项聚焦。Redmi API36 安装最终 Debug/Test APK 后，`ProxyPageParityInstrumentedTest` 整类 9/9 通过；首次修复后误把“自动首项焦点”再按一次 DirectionDown 的断言失败保留于附件，调整测试顺序后复验通过。最终全量 JVM 771/771，Debug/Test、未签名 Release/R8、Debug/Release Lint 通过（Debug 0 errors/67 warnings/10 hints，Release 0 errors/66 warnings/9 hints）。Debug APK SHA-256 `983d3cbdd78795339b1c66a4f5cc14907bbe436d033785e5afbe7e15eb007558`，AndroidTest `4fcb062ce2fe75d76b79132805fd4b4a1849fc95d3e18b680cfb1df546071feb`，未签名 Release `b213dc0d06e2ca1bddc5e5d2829a2d85b42f2496b38fbc667ce0e0cea6737f8d`；安装回拉哈希匹配。证据 `docs/acceptance/proxy-group-menu-keyboard-redmi-20261106.json`，附件 `../acceptance-artifacts/proxy-group-menu-keyboard-redmi-20261106/`。该切片只覆盖代理组溢出菜单，不代表实体键盘、全应用焦点顺序或完整 Flutter 视觉已完成，整体仍 PARTIAL。
> 历史切片（2026-11-06，代理组批量测速 queued/running/completed）：对照 Flutter `DelayTestPhase` 和任务池语义，Kotlin 批量测速在取得 Semaphore 前发出 QUEUED、获得许可后发出 RUNNING、单项完成或异常立即发出 COMPLETED；代理卡片分别显示灰色时钟、运行指示器和原有结果文案，嵌套代理组仍按展示节点映射。App495/Service207/Core38/Common31，共771项 JVM 测试零失败/错误/跳过；Debug/Test、未签名 Release/R8、Debug/Release Lint 通过（分别0 errors/67 warnings/10 hints、0 errors/66 warnings/9 hints）。Debug APK SHA-256 `4d589eb5a2731082638de967c431009c009bc4ab48086e79f1ad984cf3f518bd`，AndroidTest `9fc7a3522a7bf2957f5d2d04441f35f6219484060c27b1c430009343357a338b`，未签名 Release `de59c88df671905e77d58422a85315ae3d6c387fe6c985d484f9573cbf2d3894`；Redmi API36 定向 `delayStatusUsesFadeTransitionWithoutChangingNodeSelection` 1/1，安装回拉哈希匹配。证据 `docs/acceptance/proxy-delay-queue-redmi-20261106.json`，本机附件 `../acceptance-artifacts/proxy-delay-queue-20261106/`。本组证明 Kotlin 批次调度阶段，不代表 Mihomo 内部全局 URLTest 槽位开始事件；完整 List/子页视觉、完整键盘导航、面板联动、统一 Flutter parity 和全应用动效性能仍为 PARTIAL。
> 历史切片（2026-11-06，代理组自动 `now` 与用户优选徽标）：在 Redmi API36 的 Tab/List 两种布局中验证 URLTest 自动测速 `now` 与用户优选节点同时存在时各自显示“当前使用”和“优选”徽标，优选节点不被误标为当前出口，精确用例 1/1 通过；两张截图已目视核对。该切片只补验证、用例和截图基线，没有修改生产逻辑。工作区 Gradle XML 汇总 App494/Service206/Core38/Common31，共769项 JVM 测试零失败/错误/跳过；Debug/Test、未签名 Release/R8、Debug/Release Lint 通过（分别0 errors/67 warnings/10 hints、0 errors/66 warnings/9 hints）。Debug APK SHA-256 `1cc2204f4e340baed1b135375a73907cfe2c0c939e390347982bc91065a9dd73`，AndroidTest `bee48bc4fcad0ccbee84b05f92ea6494d3a4414e3a064ab80f6dd095c49e6627`，未签名 Release `40fed0e6c33e8b26ab55de49d67babcd56c3d4b5252e94ec60308fc3bfceef21`；证据 `docs/acceptance/proxy-computed-now-preferred-redmi-20261106.json` 与 `docs/visual-baselines/proxy-computed-now-preferred-redmi-api36.sha256`，本机附件 `../acceptance-artifacts/proxy-computed-now-preferred-redmi-20261106/`。完整 List/子页视觉、完整键盘导航、面板联动、统一 Flutter parity 和全应用动效性能仍为 PARTIAL。
>
> 当前基线：本文件所在的 `kotlin` 主线提交，追踪 `origin/kotlin`；DNS/浏览器修复 `cab92464` 和规则资源 `d3ee7fc4` 为已同步历史基线，当前累计能力以第3/7节和最新切片为准，远端差异以 `git status` 为准。Android 默认首页按 Flutter 收敛为状态 → 网络速度 → 出站模式 → 配置 → 策略组，连接统计、内存信息、服务状态、内网 IP 与 VPN 默认隐藏；无配置时隐藏代理 Tab。按应用代理位于工具设置分组，电池授权位于常规/按需连接的前置条件；系统 Always-on 与订阅请求证据仅从显式 Debug 开发者入口进入。
> 首页编辑使用整卡长按、右上角移除与左侧添加；状态卡参与排序且不可移除。标题为“仪表盘 / 编辑仪表盘 / 可添加”，出站模式保留 Rule → Global → Direct 的 52dp connected buttons。配置快切、详情、单项同步以及策略组选择面板沿用已有正式状态与仓库链路。
> 上游健康检查与自动选组同步（2026-11-05，核心当前基线；构建数字为历史批次）
>
> 仅从 `chen08209/Clash.Meta:FlClash` 合入连续的 4 个候选提交 `25fb0699`、`4a8e40cf`、`04f718e6`、`024245dc`；它们与本地控制器修复共同基线分叉，未快进覆盖 `f83e9b10`。核心合并提交为 `852d3ad1`，宿主查询显式 Provider timeout 的适配提交为 `7038231e`，均已推送到 `lonnnnnng/mihomo:kclash-fixes`。Provider 更新后重测、url-test 无效首选缓存和宿主默认健康检查超时已进入产品核心；Flutter v0.9.3 提交没有 merge/cherry-pick 到 Kotlin 主线，只保留功能映射记录。
>
> 外层 Go `go test ./...` 与内核 provider/outboundgroup/singledo/route 定向 race 已通过；Kotlin 11-06代理测速队列历史批次 App 495、Service 207、Core 38、Common 31 项 JVM 单测均通过（最新累计结果见第3节782项），Debug/Test/未签名 Release/R8 及 Debug/Release Lint 独立重跑均通过并已在 Redmi 安装回验。`GeneralSettings.delayTestTimeout` 通过 setup JSON 传入核心，改动会触发配置重载；Provider 显式 timeout 优先于宿主期限。完整 Providers 多类型和全部 URLTest/Fallback 实网覆盖仍按各自验收证据保持 PARTIAL。
> 核心控制器源码修复与溯源（2026-11-05，已合入当前基线；以下为当批验收）
>
> 本组在Mihomo固定基线c5062921上修复KCORE-002重建竞态与KCORE-003坏TLS配置端口泄漏：公开入口同步关闭/绑定，Serve后台运行，显式持有listener覆盖未登记窗口，配置快照及路由构造同锁；TLS客户端CA/ECH准备先于Listen。修复提交f83e9b1032ed02831457d4da5a414e063e932694已推lonnnnnng/mihomo的kclash-fixes，父仓库gitlink固定，原始来源/失败/撤销条件见docs/upstream-source-defects.md与ADR0021。KCORE-001保留前组6ca697d1记录；未认定为旧Always-on TLS EOF根因。
>
> 最终build2 App492/Service206/Core38/Common31共767项JVM零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8和双Lint通过；Debug0错误/67警告/10提示，Release0错误/66警告/9提示。Go父模块全量race修复前265/265但压力38/40，修复后265/265与相同压力40/40；嵌套route为8顶层方法及2子场景共10通过事件。TLS最小复现原始两个泄漏分支（含父项3失败）→相同修复3/3；overlay仅红绿证明，产品直接构建修复源码。CI补父模块和route的race，Windows Pipe只读核验、未运行。
>
> Redmi最终同套App/Test final2精确4/4：控制器6轮，正确密钥12次200、旧/错误12次401、关闭后立即绑定端口6次、嵌入写配置6次405，运行会话/正式配置保持；另模式/组件/旧菜单3/3。Always-on-final1零额外等待，冷启动和SIGKILL恢复首个TUN HTTPS均200、同socket核心归因/正流量；纯代理独立恢复1场景通过。系统恢复观察耗时5.899/6.887及2.927秒仅服务/TUN就绪观察，不是HTTP恢复耗时。初批final1的明文HTTP测试策略/错误类名两失败保留，修正测试后复验，不改产品网络安全策略。
>
> 正式1份配置全窗口逐字段/选择/正文SHA-size-mtime摘要保持，前后保护各1/1；原1.yaml双端、Flutter APK、系统设置保持，安装回拉/构建源码哈希一致，恢复备份清空，结束无服务/tun0，独占XML匹配后定点删除。Always-on开/关两图已目视，仅作系统状态证据。证据docs/acceptance/controller-lifecycle-redmi-20261105.json，本机附件../acceptance-artifacts/controller-race-20261105/。
>
> 本组控制器缺陷已修验证；第8/11项整体仍PARTIAL。旧TLS EOF因果/长期稳定性、第6项原长窗口保护异常保持未闭环；蜂窝延期。第7项已用更新后的唯一授权源在Redmi真实Mihomo停止态/运行态完成非空Fallback的now、固定、清除固定和非法成员拒绝共8次实核采样，并完成固定选择的真实冷进程恢复；自然无效now按当前Mihomo合法解析契约不可自然构造，保留为语义边界，不伪造实核通过。在线age输入仍受限，首次安装/第三方/OEM、低API、远端CI、正式签名运行及完整Flutter视觉仍待补。玻璃默认关闭，原暂缓/取消项保持。
>
> 上一组历史（11-05，TUN保护缺口与首请求复验）：
> 对照Flutter共用核心适配层，修正listener返回前跳过socket保护、平台false被忽略的问题：保护gate先于hook就绪，拒绝/Control错误透传，关闭等待回调并拒绝迟到拨号；Mihomo子模块不改。新增只读DNS实际应用序号、单调时间及按源端口有界诊断，默认停采，不改变DNS调度、节点选择或首请求策略。见ADR0020。
>
> 最终build1 App492/Service206/Core38/Common31共767项JVM结果零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8和双Lint通过；Debug0错误/67警告/10提示，Release0错误/66警告/9提示。Go普通全量265/265、定向race7/7；全量race在控制器httpServer异步重建报1项失败，旧源码overlay复现同一竞态，单列未修，不能写全量race通过。
>
> Redmi final1/2两轮默认零额外等待，冷启动和运行中SIGKILL恢复共四次首个TUN HTTPS均200、同socket归因/正双向流量，直接核心诊断有对应出站事件，无补诊断。3/3模式/组件/旧菜单回归、独立纯代理系统恢复1场景、保护前后各1/1通过，App/Test安装回拉及代码快照匹配。旧包baseline1冷首请求TLS EOF保留；修改版四次启动中保护调用均0，不能认定该保护窗口已在真机触发或已证明是旧失败根因，长期稳定性仍未闭环。
>
> 正式1份配置逐字段摘要/选择/正文SHA-size-mtime、原1.yaml双端、Flutter APK与系统设置保持；备份清空，结束无服务/tun0，独占XML匹配后定点删除。final2系统开/关两张图已目视，仅作系统状态证据，第6项原长窗口保护失败不覆盖。证据`docs/acceptance/system-vpn-protection-redmi-20261105.json`，本机附件`../acceptance-artifacts/tun-protect-20261105/`。第8项仍PARTIAL，蜂窝延期，首次安装/第三方/OEM待验；完整Flutter视觉、低API、正式签名运行和远端CI未补，原暂缓/取消项保持。
>
> 上一组历史（11-05，网络观察生命周期与Always-on首请求复验）：
> 对照本地Flutter将NetworkObserver提前到Application、配置加载前注册，由进程持有；服务停止不再注销共用观察器或清空系统DNS。新增只计数的socket保护结果与安全诊断，不改Go/submodule。
>
> 最终build3 App492/Service206/Core38/Common31共767项JVM结果零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8及双Lint通过；Debug0错误/67警告/10提示，Release0错误/66警告/9提示，安装回拉匹配。Redmi final5/6两轮默认零额外等待，各自冷启动与SIGKILL恢复首个TUN HTTPS均200、同socket核心归因/正流量，无失败后补诊断。精确回归3/3、独立纯代理系统恢复1场景、保护前后各1/1通过。
>
> 旧包baseline及增强包diagnostic2/3在冷首请求同样失败；后两轮物理HTTPS200/两次TUN HTTPS200仍只作诊断。delayed4额外15秒只计延迟诊断，旧passed字段保留并注明语义；最终脚本已分开默认验收与延迟诊断。diagnostic1长JSON截断/清理解析失败与手工token恢复独立保留，最终分片完整性门禁已验证。生命周期缺口已修，但间歇节点DNS/拨号确切根因仍未定，不宣称Always-on稳定性完全闭环。
>
> 正式1份配置逐字段摘要/选择/正文SHA-size-mtime、原1.yaml双端、Flutter APK和设置保持；备份清空，结束无服务/tun0，独占XML已定点删除。final6系统开/关两张截图已目视，仅作系统状态证据；第6项原长窗口保护失败仍独立保留。证据`docs/acceptance/system-vpn-dns-recovery-redmi-20261105.json`，本机附件`../acceptance-artifacts/always-on-dns-20261105/`。第8项整体仍PARTIAL，蜂窝延期；首次安装/第三方真调用/其余OEM待验，完整Flutter视觉仍PARTIAL，原暂缓/取消项保持。
>
> 上一组历史（11-05，todo第8项无蜂窝部分验收）：
> 本组在无蜂窝条件下推进todo第8项，只有Wi-Fi↔蜂窝子项延期。对照Flutter修正系统VPN启动：先前台通知，再等待Application诊断/resume初始化，统一进入CoreRuntimeManager；内部启动action才建TUN。新增7项状态机单测，重复回调、失败释放与按需排除已有覆盖。
>
> 最终build8 App492/Service203/Core38/Common31共764项JVM结果零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8和双Lint通过；Debug0错误/67警告/10提示，Release0错误/66警告/9提示。Redmi同套App/Test精确回归3/3，纯代理SIGKILL恢复独立1场景通过，保护前后各1/1；安装回拉SHA匹配。
>
> Always-on final7仅部分已验：准备PID16294先消失，系统真实开关拉起16486，verify前独立观察双FGS/tun0（约4.706秒）；绑定VPN Network、TLS主机名校验/HTTP200及同socket核心归因通过，平台回读alwaysOn=true/lockdown=false。受控SIGKILL后约6.919秒独立观察系统PID16860及双FGS/tun0，但随后TLS EOF，核心同端口TCP_DIAL_FAILED，脱敏日志含DNS解析失败/context deadline exceeded，根因仍未确定；整组overallPassed=false，不记完整实网恢复通过。上述秒数仅服务/TUN观察耗时。Debug短暂热切日志等级/订阅并在finally恢复，不改正式日志偏好或重载配置。
>
> 各场景正式行/选择/正文SHA-size-mtime恢复并清加密备份；全窗口1份配置摘要保持，原1.yaml双端、Flutter APK和设备设置保持，结束无服务/tun0，单个设备XML临时文件已定点删除。最终系统开/关两张截图已目视，仅作系统状态证据。早期Lint/脚本定位/普通socket超时和各TLS失败批次独立保留，不累计、不覆盖第6项原长窗口保护异常。
>
> 证据`docs/acceptance/system-vpn-always-on-redmi-20261105.json`，附件`../acceptance-artifacts/always-on-20261105/`。下一步优先定位Always-on恢复后的DNS/拨号，再补实网恢复；首次干净安装VPN、蜂窝、Tasker/MacroDroid真调用及其余OEM/开机行为仍待验。生产Always-on状态仍UNKNOWN，本轮Debug平台回读不能推广生产界面；Go、低API、正式签名运行和远端CI未重跑，完整Flutter视觉仍PARTIAL，玻璃默认关闭，原暂缓/取消项保持。

> 上一组（11-05，todo第7/8项，历史切片）：
> todo第7项已重新盘点：原1.yaml为33组（32 select/1 url-test）、0 Fallback；正式缓存与本次唯一授权在线响应同为34815字节、33组、0 Fallback且SHA一致。在线只读下载/解码，不写正式Profile/正文/mtime。合法非空Fallback的now由Mihomo选存活项或首项；无效now协议防御本轮2项回归中已验，但不替代实核与Fallback冷恢复，仍受输入条件限制。
>
> todo第8项纯代理受控SIGKILL系统恢复完成本组验收：准备Activity结束后旧PID8860及其前台ProxyService仍存活；外部带轮次token/PID门禁广播触发自身SIGKILL，约2.874秒在启动verify之前独立观察系统新PID9093及同PID前台服务。verify不调用start/restart/apply，回读RUNNING、纯代理、resume=true及READY之后新CORE_RESTARTED；前后本地SOCKS+TLS主机名校验/HTTP200、对应socket核心连接归因及非DIRECT链均通过。2.874秒仅为前台服务观察耗时，不等于完整核心/HTTP恢复耗时。
>
> 最终build6 App492/Service196/Core38/Common31共757项JVM结果零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8及双Lint通过；Debug0错误/67警告/10提示，Release0错误/66警告/9提示。Debug新增1条仅验收说明文字SetTextI18n；生产运行逻辑未改，新增DUMP权限保护的Debug宿主/receiver及外部驱动，最终Release清单确认无验收组件。Redmi final5系统恢复1场景、精确回归2/2、回归窗口保护前后各1/1；安装回拉SHA匹配。
>
> 各场景内部正式行摘要/选择/正文SHA-size-mtime、VPN/模式/auto/resume已恢复，验收加密备份已清；最终设备快照原1.yaml双端、Flutter包及设备设置保持，结束无服务/tun0。本组保持结论不消除第6项原长窗口元数据/mtime变化原因未定的失败记录。前两次编译失败、主线程网络失败、两次ServiceRecord格式解析门禁失败及中间包通过均独立保留，不与最终批次合计。
>
> 证据`docs/acceptance/proxy-system-recovery-redmi-20261105.json`，持久附件`../acceptance-artifacts/proxy-system-recovery-20261105/`。仅证明本Redmi API36既有配置纯代理的受控SIGKILL后Android重建，不推广OEM内存压力/强行停止/VPN。第8项首次干净安装VPN授权、Wi-Fi↔蜂窝、Tasker/MacroDroid真调用及Always-on OEM仍待验；下一步继续具备条件的系统入口。Go、低API、正式签名运行及远端CI未重跑，无新视觉修改/截图，完整Flutter视觉仍PARTIAL；玻璃默认关闭，原暂缓/取消项保持。

> 上一组（11-05，todo第6项，历史切片）：
> todo第6项成功质量缓存闭环已验：复用Room偏好表，按IP保留3天且不延长原查询时间；失败仅当前路由内存冷却5分钟。force、IO失败降级、取消、路由失效、损坏/未来/过期项、事务并发及迟到失败保护已覆盖；显式JSON字段避免Release混淆改变磁盘格式，不改schema。
>
> 生产build2 App492/Service196/Core38/Common31共757项JVM结果零失败/错误/跳过（含up-to-date复用），Debug、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）。testbuild4 Redmi final3 5/5、cold2双阶段、页面回归10/10；14PNG经3拼图目视。诊断包diagnostic5仅增强只读保护检查，构建/双Lint通过，独立cold5双阶段及短窗口保护前后各1/1；生产APK与功能源码哈希保持。
>
> 8个出口源/6个质量源及13个服务目标分别DIRECT和运行RULE真实尝试；出口解析6/8与7/8、质量3/6与4/6，其他结果保留FAILED/NO_TYPE/IP_MISMATCH，不能写全部源成功。运行服务12项可用、Prime Video失败；DIRECT逐项受限见JSON。真实跨PID回读保持原checkedAt，拒绝联网Runtime调用0且正式DI一致；prepare退出时PID已消失，不称杀死仍存活的准备进程。
>
> 长窗口正式配置保护after为0/1：正文SHA/34815字节/当前ID保持，但currentGroupName、lastUpdateAt及文件mtime变化；系统审计无对应下载成功，原因未确定。未回写旧快照或覆盖失败基线；各fixture内部保护及新cold5短窗口通过，不能替代原长窗口失败。原1.yaml双端、Flutter包和设备设置保持，结束无服务/tun0，安装回拉哈希匹配。
>
> 证据`docs/acceptance/ip-quality-cache-redmi-20261105.json`，持久附件`../acceptance-artifacts/ip-quality-cache-20261105/`；早期并发红测、live1失败和中间批次独立保留、不累计。第6项缓存与全部目标尝试已验，远端受限及长窗口保护异常单列；第7项Fallback仍缺授权非空组，之后推进第8项具备条件的系统边界。完整Flutter视觉仍PARTIAL；Go、低API、VPN、正式签名运行和远端CI未重跑；玻璃默认关闭，原暂缓/取消项保持。

> 上一组（11-05，todo第5项，历史切片）：todo第5项本组范围已验：Provider批量部分失败保留逐项受控原因，单项恢复只移除本项，轮询/前后台保留剩余失败，关闭/换配置拒绝迟到结果，超时释放忙态。Redmi真实Mihomo经受控HTTP服务完成1成功+503/缺失YAML payload两失败、旧缓存保护、单项恢复和只重试剩余项。公开8源更新SHA/条目/时间前进通过；HTTP8种、File8种、Inline3种实际HTTPS均有精确RuleSet/Provider归因，另验原配置geolocation-cn MRS命中及Inline Proxy原两个节点展开。
> 最终build9 App485/Service196/Core38/Common31共750项JVM结果零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）。Redmi同套App/Test final7新增4/4、回归14/14、保护前后各1/1；25张PNG经5张拼图目视，安装回拉哈希匹配。正式1份配置逐字段/选择/正文SHA-size-mtime、原1.yaml双端、Flutter包及设备设置保持，结束无服务/tun0；临时配置/脚本/缓存恢复，18个独占设备输入按SHA核对后精准删除。
> 证据`docs/acceptance/provider-recovery-hits-redmi-20261105.json`，持久附件`../acceptance-artifacts/provider-recovery-hits-20261105/`。本组仅在原1.yaml临时副本的正式脚本投影中隔离规则并关闭嗅探；非整份原配置路由/嗅探交互、全部90个MRS站点或所有代理协议流量覆盖。原MRS首试失败、第二次成功已逐次保留，早期6个失败批次不累计。托管资源混合同步/apply失败新分支仅静态审查，未独立故障注入；Go、低API、VPN、正式签名运行、远端CI未重跑。完整Flutter视觉仍PARTIAL；下一项第6项IP/质量来源、持久缓存与失效恢复，玻璃默认关闭，原暂缓/取消项保持。

> 前组（11-05，todo第4项，历史切片）：todo第4项本组范围已验：规则YAML/MRS与图标集真实OpenDocument，策略组本地图标GetContent经Android16照片选择器“更多选项→浏览…”进入DocumentsUI；覆盖取消、空/错误文件拒绝、重试、预览不写入、正式保存和Activity重建回读。MRS原字节保持，仓库/VM/页面预览摘要一致。公开外部脚本真实下载、改源、同步与绑定配置运行态应用通过，直接Bridge确认新增CustomProxy、暂存规则及Mihomo epoch 4→5→6；正文、DIRECT选择与纯代理会话保持。最终build3 App481/Service195/Core38/Common31共745项JVM结果零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）。Redmi同套App/Test final3新增5/5、相关回归12/12；MRS定向1/1独立保留，前后保护各1/1。15张PNG及2张拼图已目视，安装回拉哈希匹配；正式1份配置逐字段/选择/正文SHA-size-mtime、原1.yaml双端、Flutter包及设备设置保持，结束无服务/tun0。临时资源/配置/脚本与图标历史已收尾，8个独占设备输入按哈希核对后删除，本机附件保留。证据`docs/acceptance/resource-saf-scripts-redmi-20261105.json`，持久附件`../acceptance-artifacts/resource-saf-scripts-20261105/`。仅新增验收代码，生产逻辑和Flutter上游不改。脚本走正式VM方法、无Activity轮询，非完整点击导航E2E；两URL的原始脚本正文相同，只证明改源/文件版本/核心重载，不证明远端正文变化或实际流量命中。组父编辑入口由正式VM打开，图片选择/失败重试/保存为真实UI；资源为Activity重建，不推广选择器冷进程。首批3/5及第二批4/5的定位/系统选择器路径/异步等待失败独立保留，不与最终批次累计；一张选择器截图为切换中间帧，不作完整视觉等价结论。第3项在线age仍受输入条件限制，第5至11项继续待办；玻璃默认关闭，原暂缓/取消项保持。

> 上一组（11-05，todo第3项，历史切片）：todo第3项CUSTOM引用在线源已验：手动ProfilesViewModel同步与生产到期Repository自动更新各完成一次真实下载、提交和当前CUSTOM应用。临时资源初值使用原1.yaml；下载后源字节版本变化，真实Mihomo epoch分别3→4、8→9，按正文SHA生成的文件Provider路径和7个节点回读匹配；当前CUSTOM、DIRECT组选择、正文及纯代理运行会话保持。无Activity/节点轮询参与补加载。唯一授权响应实测为34815字节明文，既有密钥为空，在线age下载/解密/应用仍受输入条件限制，不记通过。最终build2统一检查通过：App481/Service195/Core38/Common31共745项JVM结果零失败/错误/跳过，含up-to-date/缓存复用；Debug/Test、未签名Release/R8和双Lint成功，各0错误/66警告（Debug10/Release9提示）。Redmi同套App/Test在线批次3/3、相关回归8/8，前后保护各1/1；正式1份配置逐字段/选择/正文SHA-size-mtime、原1.yaml双端、Flutter包及设备设置保持，安装回拉哈希匹配，结束无服务/tun0。证据`docs/acceptance/subscription-reference-network-redmi-20261105.json`，原始附件在外层`acceptance-artifacts/subscription-reference-20261105/`。自动场景直接调用生产updateDueProfiles，非另一轮WorkManager自然周期；手动场景走正式VM方法，非完整点击导航E2E。本组证明源字节更新及核心重载，不证明节点定义变化、规则/流量命中或VPN；中断恢复代码已审查，未新注入网络中断/死亡。未重跑Go、低API、正式签名运行、远端CI或完整Flutter视觉；玻璃默认关闭，原暂缓/取消项保持。第3项保留age受限，接下来推进第4项其他资源SAF及外部脚本。
> 前组（11-05，todo第2项）：todo第2项保存/退出受控死亡窗口已验：规则、Proxies、策略组结构化及原始YAML、本地节点的意图落盘/正式提交窗口，实际引用配置的退出应用前后、父草稿清除前后及CAS冲突共15场景。准备测试命中门闩后保持存活，外部核对同PID后force-stop并确认消失；15次首次恢复、5次测试进程正常退出后的再次冷启动检查通过。恢复不重复写入，未提交等待显式重试，源变化保留旧CAS；退出应用调用计数在恢复后为0、显式重试后为1。最终build7 App481/Service195/Core38/Common31共745单测零失败/错误/跳过，Debug/Test、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）；同套App/Test相关回归21/21，安装回拉哈希匹配。正式1份配置/逐字段摘要/选择/正文SHA-size-mtime、本地目录、原1.yaml双端、Flutter包及设备设置保持；临时行/正文与两个草稿Store的主文件/.bak/.new均已清除，结束无服务/tun0。证据`docs/acceptance/editor-commit-window-redmi-20261105.json`，原始附件在外层`acceptance-artifacts/commit-window-20261105/`。这是受控force-stop，未推广系统自动回收/OEM；结构化保存走正式MainActivity的VM入口，本地保存/退出使用Compose点击。实际引用分支调用并返回applyCurrentProfile，核心保持STOPPED；不推广运行中VPN/流量、事务内部崩溃原子性或完整Flutter视觉。首批11场景后退出恢复断言误将输入加载当作退出重放；第二批13场景后父清除检查点未覆盖自动写入器，均保留失败及现场收尾，不与最终批次相加。第1/2项本组范围已验，第3至11项待办；下一组按第3项真实age在线解密与CUSTOM引用源更新应用推进。玻璃默认关闭，原暂缓/取消项不变。
> 前组（11-05，todo第1项）：规则快捷YAML的正文/选区、错误与放弃确认已进入现有加密保存树，仅正文修改清错；异步校验冻结正文，保留原CAS/关闭守卫。App481/Service195/Core38/Common31共745项零失败/错误/跳过；final1统一Debug/Test、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）。Redmi最终同套App/Test六场景各prepare/verify通过，外部严格核对PID消失与恢复PID不同；相关回归18/18，定向绿测独立1/1。31张PNG/6拼图已目视，安装回拉哈希匹配；正式1份配置/原选择/逐字段摘要及正文SHA-size-mtime、原1.yaml双端、Flutter包和设置保持，结束无服务/tun0。证据`docs/acceptance/remaining-editor-cold-redmi-20261105.json`。todo第1项本组范围已验；第2项保存意图落盘后未提交、已提交未清草稿及退出死亡窗口仍待真机。第3至11项保持待办，玻璃默认关闭，原暂缓/取消项不变。
> 前序（11-05，Proxies/本地代理冷进程）：Proxies父草稿v3及本地代理独立加密草稿已接入正式入口，保留原CAS基线、UI树、保存/退出意图，恢复不自动重放；首次读取与元数据迟到恢复竞态已修，集合checkpoint不再回写旧正文。代理YAML改按Flutter语义校验，修复行内map、顶层flow列表及注释包装。App481/Service195/Core38/Common31共745项单测零失败/错误/跳过；最终build8 Debug/Test、未签名Release/R8及独立lint8双变体通过，各0错误/66警告（Debug10/Release9提示）。Redmi最终同套App/Test十场景各prepare/verify通过，外部确认旧PID消失、恢复PID不同；旧入口14/14，Proxies正式MainActivity保存回读独立1/1。30张PNG与5张拼图已目视，App/Test回拉哈希匹配；正式1份配置/选择/正文SHA-size-mtime及逐字段摘要、本地目录、原1.yaml双端、Flutter包与设置保持，结束无服务/tun0。各verify在应用内部确认Store为空且主文件/.bak/.new均不存在；不使用受SELinux拒绝的run-as退出码判断。证据`docs/acceptance/local-proxy-draft-cold-redmi-20261105.json`。规则YAML/保持打开的类型选择器、策略组成员/选择/排序/批选及保存中/落盘后UI收尾前死亡窗口仍待真实冷验。在线age/CUSTOM引用源、其他资源SAF/外部脚本、Provider剩余来源/故障恢复/规则命中、质量来源/持久缓存、Fallback/蜂窝/第三方OEM和第三步视觉/性能保持待办；玻璃默认关闭，原暂缓项保持。
> 前序（11-05，规则/策略组冷进程）：正式入口、原CAS基线、子窗口UI树与提交意图已纳入v2加密父草稿，不向Activity Bundle保存正文。App462/Service195/Core38/Common31共726项零失败/错误/跳过，最终build9 Debug/Test及未签名Release/R8、testbuild10与双Lint通过，各0错误/66警告（Debug10/Release9提示）。Redmi八场景各prepare/verify通过，外部确认旧PID消失、恢复PID不同；最终旧入口15/15，父正文冷进程两阶段另各1/1，35张PNG已目视。正式1份配置/选择/正文SHA-size-mtime及逐字段摘要、原1.yaml双端、Flutter包与设置保持，最终安装哈希匹配，结束无服务/tun0。各verify内部确认草稿Store为空并删除marker；run-as受ROM SELinux拒绝，未独立检查AtomicFile .bak/.new，不将退出码当文件不存在。失败批次独立保留。证据`docs/acceptance/structured-editor-cold-redmi-20261105.json`。配置内Proxies/本地代理仍未接入持久草稿；规则YAML/打开的类型选择器、策略组成员/批选、保存中及落盘后收尾前死亡窗口仍待冷验。第二步其他实网/系统边界和第三步完整视觉/性能保持待办，玻璃默认关闭，原暂缓项保持。
> 前组历史（11-05，后台订阅与节点）：718单测、最终回归7/7及自然到期独立1/1，t0到t1为25分40秒；真实运行态节点落库/Activity重建回读通过。详见`docs/acceptance/subscription-auto-cycle-redmi-20261105.json`及下方历史切片，不重复累计或沿用其中旧的编辑器恢复待办。
> 前序第一步（11-05）：整策略组原始YAML返回先校验并更新父草稿，失败可继续/放弃，长正文错误定位已验；单组图标/添加选项/值子页保存状态与返回层级恢复。本地代理列表/映射由VM保留草稿及会话边界，隔离Activity真实重建后保持，凭据测试标记不入Bundle。服务管理/质量拖回提交、取消、快甩、反向与后台中断已验，质量页退场保留最后正文，修复空白帧。App453/Service193/Core38/Common31共715项单测零失败/错误/跳过；build3 Debug/Test、未签名Release/R8及独立lint3双变体通过，各0错误/64警告（Debug10/Release9提示）。Redmi最终同套App/Test final2 44/44，81张PNG已目视，两包安装哈希匹配。前批6/7、motion2 1/1、fling3 1/1、旧包43/43及质量修复前后各1/1独立保留、不累计；修复前测试也能通过，正文保留另由最终截图确认。正式1份配置/原选择/正文SHA-size-mtime及逐字段摘要保持，原1.yaml双端、Flutter APK和旋转/字号/动画设置保持，结束无服务/tun0。证据`docs/acceptance/stage-one-interactions-redmi-20261105.json`。第一步指定页面交互完成本组范围验收；整组YAML/单组子页为保存状态模拟，本地代理为隔离Activity+正式VM的真实recreate，均不等于冷进程或正式MainActivity全链路。完整Flutter视觉仍PARTIAL；下一步按第二步推进真实加密在线订阅/到期自动更新、其他资源SAF/外部脚本、Provider故障恢复/规则命中、运行态节点落库、编辑器冷进程及质量来源/持久缓存；Fallback输入、蜂窝网络与第三方/OEM条件继续单列。第三步再做空态morph/orbit、全应用精确缓动/视觉、live背景/触点光效及8MiB/动效性能。玻璃默认关闭；备份/系统恢复、观测次要打磨、语言/主题暂缓。
> 前序子页恢复（11-05）：规则编辑的原行身份、批选及保存中状态可重建恢复；源行已变更时阻止误新增。单策略组快捷YAML返回先校验并更新父草稿，错误可继续或放弃；Provider长文保存失败与同文案重试自动回顶。服务详情/管理/质量标题正文连续过渡，独立页内容和滚动状态解决返回后列表回顶；面板退场阻断业务触摸与按键。App451/Service193/Core38/Common31共713项单测零失败/错误/跳过；build4 Debug/Test、未签名Release/R8及独立lint4双变体通过，各0错误/64警告（Debug10/Release9提示）。Redmi最终生产/测试包同批final5 28/28，63张PNG已目视，安装哈希匹配。首批26/28、两次6/7、motion4补验1/1及系统设置覆盖页面的final4 27/28独立保留、不累计。正式1份配置/原选择/正文SHA-size-mtime及逐字段摘要保持；原1.yaml双端、Flutter APK及旋转/字号/动画设置保持，结束无服务/tun0。证据`docs/acceptance/main-subpages-recovery-redmi-20261105.json`。整体Flutter视觉仍PARTIAL；服务子页边缘拖回、精确缓动/全应用视觉、未覆盖子页/空态/异常恢复与性能仍待补。本组规则重建是保存状态模拟恢复，未做冷进程；未重跑实网/核心/VPN、低API、Go、正式签名运行或远端CI。玻璃默认关闭，Dock/按钮实时背景和触点光效后排；备份/系统恢复、观测次要打磨、语言/主题继续暂缓。
> 前序编辑器错误恢复（11-04）：脚本、Provider正文、选项、过滤预设和规则资源错误体验已补齐本组范围；选区移动不清失败，长文查找显式滚动，已提交回读失败只允许重新读取，保存失败自动回到错误且保留草稿。App451/Service193/Core38/Common31共713项单测零失败/错误/跳过；最终build4 Debug/Test、未签名Release/R8及独立lint4双变体通过，各0错误/64警告（Debug10/Release9提示）。Redmi最终App/Test同批final4 43/43，69张PNG已目视，安装哈希匹配；首批40/43独立保留、不累计。正式1份配置/原选择/正文SHA-size-mtime及逐字段摘要保持，原1.yaml、Flutter APK和设备设置保持，结束无服务/tun0。证据`docs/acceptance/editor-errors-recovery-redmi-20261104.json`。完整Flutter视觉仍PARTIAL：未覆盖页面/面板、全应用动效与玻璃采样品质、常驻Dock/按钮/其他面板live背景和触点glow、8MiB正文性能仍待补；低API核心/VPN/实网、Fallback实核/冷进程、Go/正式签名运行/远端CI边界保持。玻璃默认关闭；备份/系统恢复、观测次要打磨、语言/主题继续暂缓。
> 前序共享材质（11-04）：共享regular/pill/menu/alert/tinted/lens材质及STANDARD/ULTRA质量等级已接入，玻璃默认仍关闭；菜单液滴/AGSL、多层可逆子菜单、策略组/更多/链式代理覆盖子页与source→menu材质连续插值完成本组验收。App449/Service191/Core38/Common31共709项单测零失败/错误/跳过；Debug/Test、未签名Release/R8和独立双Lint通过。Redmi API36 final6同批19/19、API27竖屏手机AVD final6同批10/10，最终PNG已目视，App/Test安装哈希匹配。API27仅覆盖离线材质像素、真实Window捕获、菜单与子页fallback；API24/25、31/32及其他ABI未验，亦未覆盖订阅、YAML、VPN或实网。验收记录中的API27旋转/字号/动画设置为`null`，不能据此确认设置恢复；Redmi保护日志的单份正式配置、选择与正文聚合摘要count=1且before/after SHA相同，JSON未内嵌逐字段摘要。早期Room元数据兼容不属于开发阶段要求，历史摘要变化保留、不追加迁移或回溯修补；当前正式配置与正文仍按独立快照保护。证据`docs/acceptance/general-glass-subpages-api27-20261104.json`。完整Flutter视觉仍PARTIAL：未覆盖空态/更多页面、全应用采样品质与逐像素一致性、完整性能profile仍待做；Fallback实核/冷进程、8MiB正文性能、实网、Go、正式签名运行和远端CI保持未验，暂缓项保持。
> 前序液滴菜单（11-04）：源按钮快照液滴/500ms回源、长按原指针滑动携带、基础磨砂材质和配置/代理子页视觉完成本组验收。705项单测、Debug/Test、未签名Release/R8与双Lint通过；最终生产/测试包上Redmi同批24/24，含长正文。正式1份配置/选择/正文SHA-size-mtime保持，最终安装哈希匹配；证据`docs/acceptance/liquid-menu-subpages-redmi-20261104.json`。玻璃完整折射shader、多层卡片物理展开/收回、更多子页精确排版仍未完成，完整Flutter视觉继续PARTIAL；暂缓项保持。
> 前序默认视觉与Provider类型（11-04）：默认Flutter色板/排版、31图标/空态/菜单形变与Provider列表路由重读已补齐；700项单测、Debug/Test、未签名Release/R8及双Lint通过。Redmi主批17/18（14张严格PNG通过，Rule首读等待超时），随后Rule独立1/1通过：6种HTTP规则组合×停止/运行共12次更新、条目/mtime/正文SHA/重建/会话均通过；HTTP/File Proxy已在主批通过。GitHub实网使用用户确认的gh-proxy前缀，镜像哈希一致。安装/失败见`flutter-visual-provider-types-redmi-20261104.json`；完整Flutter视觉与Fallback实核仍PARTIAL，主题/语言设置和备份/系统恢复继续暂缓。
> 前序整页动效（11-04）：整页300ms连续滑动、代理标签居中/渐隐、配置/代理工具栏排版及Dock当前入口回菜单已完成定向验收；697项单测、Debug/Test、未签名Release/R8和双Lint通过。最终UI批次14/15，14项动效/UI/协议通过，Fallback实核因授权配置无非空Fallback未通过。原1.yaml全部90个HTTP Rule/mrs资源经32+55+3三次更新回读完成，最后3项及重建/会话补验1/1；不是单次90/90。安装/保护/失败见`page-continuity-providers-redmi-20261104.json`。完整Flutter视觉、其他Provider类型和Fallback实核仍PARTIAL，主题/语言与备份/系统恢复继续暂缓。
> 前序配置/代理切片（11-04）：配置页/代理组菜单、正文/预览面板和部分动效收尾；App440、Service188、Core38、Common31共697项单测零失败/错误/跳过，Debug/AndroidTest、独立未签名Release/R8和双变体Lint通过。Redmi 24项配置/代理页面回归、2项正文入口、3项菜单/预览交互、停止/运行节点菜单各1项及URLTest冷进程准备/恢复各1项，按独立批次全部通过；设备App/Test哈希匹配。Room在测试卸载后为空，不声称旧Profile/偏好被保留；本地`1.yaml`无Fallback，未覆盖Fallback实核，完整连续动效与Flutter逐像素parity仍PARTIAL。详见`docs/acceptance/profiles-proxy-motion-redmi-20261104.json`。
> 后续菜单补验（11-04）：Providers 行菜单、Provider 资源页操作菜单、代理组编辑器列表菜单在指定 Redmi 上分别以精确方法 1/1 通过；三份 XML 均为失败/错误/跳过0。Kotlin APK `com.lonnnnnng.flclash` 与 AndroidTest APK 已重新安装并回拉核对哈希；Flutter 上游参考包 `com.follow.clash` 仅只读确认仍安装，本轮未对其执行安装、卸载、清数据或启动。完整页面和连续动效仍为 PARTIAL，详见`docs/acceptance/profile-proxy-menus-redmi-20261104.json`。
> 前序主页面板与Providers复查（11-04）：配置切换、内存、IP质量、服务详情/管理共用面板完成自然高度/双停靠、固定标题操作、正文滚动和子页系统返回；同一生产APK上的Redmi面板批次26/26通过。随后独立Providers真实页面补验2/2：90项首次打开/重开、两HTTP资源UI更新、后台恢复、连续180063ms及Activity重建均正常，原现场持续转圈本次未复现。详见`docs/acceptance/dashboard-sheets-redmi-20261104.json`。
> 前序菜单（11-04）：共用菜单与Provider更新超时/取消恢复已验；App437/Service188共625项、最终Redmi同批20/20，同生产APK前批90资源三分钟观察通过。该组失败和截图边界保留于`docs/acceptance/main-menu-redmi-20261104.json`。
> 前序主页面视觉（11-04）：主页面连续圆角/多层阴影、运行按钮与出站模式绘制按压、子树焦点和Dock弹簧拖动/释放/取消已完成本组验收；完整字素修复与Tab/List刷新忙态一致性接通。App430/Service188共618项零失败/错误/跳过，Debug/AndroidTest、独立未签名Release/R8及双变体Lint通过（各0错误/62警告）。Redmi最终同批23/23，包含90个Provider运行态连续180000ms、Activity重建及会话保持；14张严格截图基线和Profiles浅色/菜单、按压/焦点辅助图已目视。正式两份Profile/选择/正文SHA与mtime、脚本及运行态fixture偏好和原1.yaml保持，两包安装哈希匹配，结束无服务/tun0并恢复旋转1/字号1.0。证据见`docs/acceptance/main-visual-redmi-20261104.json`。
> 原始分钟级转圈现场未保存，不认定唯一触发因；三分钟为原1.yaml非空列表的纯代理运行态观察，不推广到全部Provider实网/进程回收。Flutter7种轮廓LTR/RTL每行端点±1px已验，阴影和整页动效不宣称Flutter逐像素一致。独立探子任务通道失败，未执行独立review；Core/Common/Go本组未重跑，正式签名Release运行未验。完整Profiles/UI继续PARTIAL，备份/系统恢复与观测页次要打磨继续暂缓。
> 偏好证据边界：早期视觉批次沿用的旧代理页截图宿主会清展示偏好，未独立持久化组前快照，不能宣称最初代理页展示偏好完整保持；最终测试已在Activity规则之外保存并恢复该选项，回读断言通过。
> 前序首页/代理组闭环（11-04）：Providers/统计读取已改为版本通知合流、独立8秒页面预算和失败保留重试；同配置刷新保留列表，旧不可取消读取不阻塞新配置。首页/代理组按组patch保存、原渲染Profile ID锁内复核及独立失败重试已补齐。App426/Service188共614项单测、Debug/AndroidTest、独立未签名Release/R8及双变体Lint通过；Redmi最终同批9/9，原1.yaml运行中90个Provider连续16秒及Activity重建通过、核心会话保持。正式两份Profile/正文SHA与mtime和原1.yaml保护、最终两包哈希匹配；结束无服务/tun0并恢复旋转。证据见`docs/acceptance/home-proxy-mainflow-redmi-20261104.json`。原始分钟级现场未完整捕获，不能认定唯一根因；首批4/7及两临时配置遗留/定点恢复保留审计。首批未独立保存原选择/VPN偏好，不宣称该失败批次完整偏好保护；后续成功fixture各自保存并恢复状态。下一组主页面视觉，备份/系统恢复暂缓，整体Profiles/UI仍PARTIAL。
> Providers 补充复查（11-04，当时记录）：当次已安装 App/Test 哈希与上述修复组匹配，未重建或重装。正式配置的空 Providers 在停止态观察185.62秒、25次采样均正常；正式空列表及原1.yaml运行态90项/Activity重建两方法同批2/2，前后配置保护各1/1。90项连续窗口仍为16秒，不能推广为分钟级非空列表压力通过；原始卡住现场已不在，唯一触发原因仍未确定。正式两份配置、原选择/偏好、正文及原1.yaml保持，结束无服务/tun0；详见`docs/acceptance/providers-recheck-redmi-20261104.json`。当时视觉改动尚未构建、安装或提交，不属于该次复查证据；本组视觉验收见最新节。
> 前序闭环（11-04）：脚本运行态、全局DNS/规则、Provider覆盖/更多设置/真实侧载与原1.yaml两HTTP资源实更新最终同批13/13，见`advanced-runtime-redmi-20261104.json`；FILE/CUSTOM保存应用、删除回退、整页排序、加密草稿重建和真实下载应用见`profile-closure-redmi-20261104.json`；FILE/remote/脚本/Provider真实选择器等待中进程回收返回与取消见`file-entry-process-redmi-20261104.json`。各批数量不相加，历史未验项按第3/7节累计状态核对。
> 代理页与延迟缓存历史验收：Proxy Groups 已补齐叶子协议、按 Profile/组隔离的滚动恢复、List 组内定位和排序/搜索/密度变化后的语义锚定。URLTest/Fallback 的核心 `now` 优先、手动固定/清除固定及非法成员拒绝已有真实 Mihomo 回归。测速结果按 Profile/组/节点/测速 URL 写入 Room，含45秒TTL与失效清理；正式Profile真机测速、应用`force-stop`后新进程恢复、旧Profile/旧测速URL隔离验收1/1，Room DAO instrumentation 3/3，App/Core JVM测试270/34项通过。后续独立冷进程批次再次验证URLTest正延迟优选恢复与清除固定；本地`1.yaml`不含Fallback，因此不扩展为Fallback跨进程通过。Proxy Groups 页面仍为`PARTIAL`；菜单运行/停止态宿主、页面与预览的本轮证据见`profiles-proxy-motion-redmi-20261104.json`。全部资源实网、质量来源全覆盖/持久缓存和完整UI parity仍待后续。
> 前序延迟缓存验收时 Android 包尚未安装，正式 Room 为空；通过工作区外层 `AGENTS.md` 唯一授权入口导入并保留一份 Profile，未触碰外层 `1.yaml`、未清数据或删除 Profile。延迟缓存用例验证同一 Profile/组/节点/测试 URL 的正延迟跨真实应用进程恢复，并向正式 Room 写入不同 Profile 与旧测试 URL 探针，确认均不进入当前页面；成功后删除的只有测试探针行和应用内标记。核心保持 `STOPPED`，没有 ProxyService、VpnService 或 TUN。
> 完整 ProfileRuntimeParity 长流程仍曾在停止态 `DIRECT` 默认测速阶段失败，不能推广为通过。Dashboard、Profiles 和 Proxy Groups 的完整 UI/UX 仍为 `PARTIAL`；节点菜单停止/运行态均有正式宿主验收，主页面连续圆角/焦点/按压/Dock、配置页菜单/预览交互和URLTest冷进程优选恢复均有本轮切片证据。整页连续动效已有本轮14项UI切片；Fallback固定选择冷进程恢复已在11-06完成；自然无效核心`now`按当前合法Mihomo解析契约不可由合法配置、断网或全不健康成员自然产生，原1.yaml全部90个HTTP Rule/mrs实网已分批覆盖，其他Provider类型、质量来源全覆盖/持久缓存及完整页面parity仍待后续；Provider管理首版与首页route hits见对应记录。验收限定Redmi竖屏、系统默认字号和`arm64-v8a`。

> 2026-11-03 主链路修复补充：真实订阅本身可用，Kotlin 缺少 Flutter 的 DNS baseline，导致 Android 虚拟 DNS 无应答；已补齐脚本后 baseline → 用户覆盖 → LAN 暂存链路，并按已应用配置读取 mixed-port/TUN 参数和发布系统 HTTP 代理。有认证或禁用 mixed-port 时不广播代理，运行态配置改变网络参数时同步重建并支持失败回滚。Redmi 上 UDP/TCP 53 与系统解析均通过；X 浏览器百度与 Cloudflare 页面实测成功。详情及最终构建校验见下方“DNS 基线与浏览器主链路修复”。本段之后较早的“核心 STOPPED、未启动 TUN”等记录只适用于各自历史切片。


### 编辑器错误恢复与交互回归（2026-11-04，历史切片）

脚本、Provider正文、选项、过滤预设和规则资源错误体验已补齐本组范围；选区移动不清失败，长文查找显式滚动，已提交回读失败只允许重新读取，保存失败自动回到错误且保留草稿。

App451/Service193/Core38/Common31共713项单测零失败/错误/跳过；最终build4 Debug/Test、未签名Release/R8及独立lint4双变体通过，各0错误/64警告（Debug10/Release9提示）。Redmi最终App/Test同批final4 43/43，69张PNG已目视，安装哈希匹配；首批40/43独立保留、不累计。正式1份配置/原选择/正文SHA-size-mtime及逐字段摘要保持，原1.yaml、Flutter APK和设备设置保持，结束无服务/tun0。证据`docs/acceptance/editor-errors-recovery-redmi-20261104.json`。

完整Flutter视觉仍PARTIAL：未覆盖页面/面板、全应用动效与玻璃采样品质、常驻Dock/按钮/其他面板live背景和触点glow、8MiB正文性能仍待补；低API核心/VPN/实网、Fallback实核/冷进程、Go/正式签名运行/远端CI边界保持。玻璃默认关闭；备份/系统恢复、观测次要打磨、语言/主题继续暂缓。

### 首页/配置/代理组空态、错误定位（2026-11-04）

- 正文编辑器使用SnakeYAML真实标记显示安全行列并自动滚到错误，支持重复定位、Unicode码点到UTF-16偏移、CR/CRLF及EOF；移动光标/复制只改选区，正文编辑才清错误并写草稿，父草稿和原文件保持。未标记的错误仅提示格式/大小，不输出解析器原文摘录。
- 首页和代理页复用刷新门禁；配置空态添加与顶栏保持同一busy/启停守卫。共用面板/配置子页标题及错误/加载/空态已接通。代理无组/搜索空态沿固定Flutter基线的sunny/flower真实曲线生成；键盘展开时缩小装饰并避让IME，Tab/List清除搜索不改变节点选择。
- 最终build4根Wrapper test与Debug/Test成功：App450/Service193/Core38/Common31共712项，失败/错误/跳过0；release3未签名arm64 Release/R8和独立lint3通过，各0错误/64警告（Debug10/Release9提示）。Flutter离线导出3项通过，生成路径与导出文件哈希相同；不恢复Flutter运行时依赖。
- Redmi API36、arm64、1080×2340竖屏/字号1.0最终App/Test同批final4 15/15；24张PNG及四张联系表已目视，错误定位、真实IME和配置深浅空态另查原图。严格Dashboard/Proxy基线未改。首批14/15中LIST使用TAB标签的断言失败保留，截图另发现并修复IME遮挡和深色标题；ime3 1/1、final3 15/15仅为各自中间包结果，不与final4合计。
- 正式Repository保护before/after4各1/1，非空1份配置/原选择/正文SHA-size-mtime组合及逐字段元数据摘要一致；逐字段摘要未内嵌JSON。最终App/Test安装哈希匹配，原版Flutter APK、本地1.yaml、旋转1/0、字号1.0和动画设置保持，结束无服务/tun0。未访问订阅/Provider或启动核心；API27前组10/10不是本组新包结果。日志、安装、失败、源码和截图索引见`docs/acceptance/main-page-states-redmi-20261104.json`，原始附件仅本机。
- 剩余：其他资源/脚本编辑入口错误体验、未覆盖页面状态/面板、全应用动效/玻璃采样品质和8MiB正文性能；低API核心/VPN/实网、Fallback实核/冷进程、Go/正式签名运行/远端CI仍未验。备份/系统恢复、观测次要打磨、语言/主题继续暂缓，完整Flutter视觉保持PARTIAL。本组改动尚未提交/推送。

### 玻璃折射、多层菜单与子页同行排版（2026-11-04）

菜单背景拆分原图/预模糊图，PixelCopy后后台降采样与三次盒式卷积近似12dp高斯，长循环可取消。API33+ AGSL按固定Flutter连续圆角SDF、60dp折射/20dp bevel、goo法线与高光处理边缘，修正窗口偏移和父卡绘制缩放；深色tint由Flutter独立导出为#343537。低API保留预模糊背景fallback，默认玻璃仍关闭，主题设置未扩展。

多级子菜单保留所有父层、稳定key和各自滚动；新卡由触发行展开，宽度/材质/内容分段，父卡逐层缩小、压暗、减影，标题复用原触发行及旋转箭头。收回完成才移除顶卡，同一弹簧可继承速度反向；外部祖先导航、系统返回、露出父卡点击、后台关闭、后层语义与命中均独立处理。释放按最后位置及实时clip重算；修复maxValue缺少组合订阅导致短菜单仍按长菜单禁用拖选。本地代理基本/认证/可选项、Provider选项及资源引用统一56dp同行、右侧轻量输入、24/8分组标题和资源行边距。

build10根Wrapper test及Debug/Test、未签名arm64 Release/R8成功：App446/Service191/Core38/Common31共706项，失败/错误/跳过0，最终构建复用有效的Gradle增量测试结果。testbuild12仅增强配置保护的逐字段摘要和独立快照会话，生产APK未变；独立串行lint12双变体成功，各0错误/64警告（Debug10/Release9 hints，较前组新增Bitmap.scale的UseKtx风格警告）。此前同批Release生成与Lint并行触发生成文件缺失/FIR阶段异常，失败保留，最终改为先构建再独立Lint。

Redmi final12最终App/Test同批22/22：GPU有/无折射边缘对照且中心保持、三级父卡/收回反向/外部祖先同步、深浅真实背景玻璃子卡、原指针拖选/长菜单/键盘、配置菜单滚动、草稿/失败重试、正式Provider保存重建及旧资源入口全部通过；42张PNG及拼图已主线程目视，未修改严格golden。final10也为22/22但测试包较早，final9及更早批次各自保留，数量不相加；两路只读复核发现的命中/反向/取消/高光与回源首帧问题已修复。

最终glass-final12独立快照before/after各1/1，正式1份配置的Room全行/逐字段摘要、当前选择、正文文件名/SHA/size/mtime保持。**较早原before→final10保护0/1：仅Room行摘要不同，原选择及正文记录相同；只读诊断未确定变化字段/时点。** 原基线与失败保留，未回写未知旧字段；不能把最终窗口稳定推广为整个长窗口元数据保持。只用install-r保留Kotlin数据，App/Test设备SHA匹配，原1.yaml双端字节与Flutter参考包SHA保持；结束无服务/tun0、旋转1/0、字号/动画倍率1.0。详见`docs/acceptance/glass-stack-subpages-redmi-20261104.json`，原始日志/PNG仅本机/tmp。

完整Flutter视觉仍PARTIAL：预模糊是近似，未宣称完整采样品质或全页面逐像素等价；共享六种材质/质量分级、覆盖子页与API27离线降级已验，API24/25、31/32、其他ABI及实网/核心场景未验；其他空态/页面和完整性能profile仍待补。本组未重跑订阅/Provider实网、Go、正式签名运行或远端CI；Fallback实核/冷进程输入限制、8MiB正文性能边界保持。早期Room元数据兼容不在开发阶段范围内，当前正式配置/正文保护仍单独核验。备份/系统恢复、观测次要打磨、语言和主题继续暂缓。


### Flutter默认视觉与六种规则资源实网（2026-11-04）

默认0xFF007AFF/vivid深浅色板及字体语义槽已由固定Flutter上游导出；31个真实glyph接入Dock、配置/代理工具栏和菜单，空配置使用cookie7/cookie4曲线及spin入场，MIT许可随APK保留。菜单采用锚点中心生长、等比内容缩放/高度分离、延迟角部弹簧和跟随阴影。独立导出脚本实际3项通过，复现色板、曲线和23图标双图集字节一致；Native/Flutter图集平均每通道差约0.191/0.178（满量程255）。不是完整页面逐像素等价。

实网发现Provider列表读取期间路由版本变化会拒绝结果，已改为3秒总预算内最多3次重读；仅同一配置/正文/修订号/核心epoch可继续。旧实现新增3项回归中2项红测，修复后Service191项通过。最终App440/Service191/Core38/Common31共700项零失败/错误/跳过，Debug/Test、未签名Release/R8及双变体Lint通过，各0错误/62警告（Debug10/Release9 hints）。

Redmi主批final6为17/18：菜单、图标/动效、14张已目视严格PNG、深浅Tab/List矩阵、整页连续动效和HTTP/File Proxy通过；Rule首次列表等待超时未完整捕获，不认定唯一原因。testbuild13只补安全状态/正式重试入口及规则验收，rule7独立1/1通过且未调用重试：domain/ipcidr/classical各YAML/text共6资源，在STOPPED及纯代理RUNNING各更新一次，共12次；核心数量132/28/46、mtime前进、正文SHA和格式全部核对，Activity重建/16秒轮询及运行会话保持。不能合并宣称一次18/18。

HTTP Proxy首次count0/无cache已保留；后续加载为7条，停止/运行两次正式UI实更新和正文回读通过；File保持原1.yaml字节7条、拒绝在线更新。公开规则原始GitHub地址在Redmi出现TLS连接重置，经用户确认采用gh-proxy.com前缀与DIRECT；6份镜像SHA与原文件一致。使用公开现成规则，未转换原90个MRS；这组证明真实下载/解析/回读，不覆盖每条规则的数据包命中、全部站点或代理协议。

final6与rule7共用生产App；final6测试包和最终testbuild13分开记录。最终App/Test设备哈希匹配，旧入口after1/1，正式1份配置及正文元数据保持，网络fixture另核对Room行/原选择/正文SHA-size-mtime/脚本/VPN，原1.yaml双端哈希保持。结束无服务/tun0、旋转1/0、字号1.0；Flutter com.follow.clash只读参考。失败、源码、安装及本机附件索引见`docs/acceptance/flutter-visual-provider-types-redmi-20261104.json`；原始附件仅本机/tmp。

完整Flutter视觉仍PARTIAL：菜单源快照/液滴返回、blur/滑动携带、完整子菜单/玻璃、更多子页和精确排版未完成；空态只实现spin，其他随机入场未移植。默认主题全局生效，但暂缓观测页旧截图未更新；主题/语言设置、备份/系统恢复与观测次要打磨继续暂缓。Fallback实核/冷进程仍受授权输入无非空Fallback限制，Go未重跑、签名Release运行及远端CI未验；前段子代理通道失败，未计独立review。


### 整页连续动效与全部原配置规则Provider实网（2026-11-04）

主导航采用300ms easeOut整页滑动，标题/正文一起移动、Dock固定；返回保留滚动与草稿视图，跨入口不构建沿途页。代理标签点击立即高亮、选中项居中、末端24dp渐隐，半程拖动取消不写组记忆；配置与代理工具栏72dp、胶囊操作、正文间距和到期标签按Flutter收敛。修复Dock再次轻点当前入口被忽略，工具子页可回根菜单；原地拖动/系统取消不重复导航。

最终生产build3根Wrapper test通过App440/Service188/Core38/Common31共697项，失败/错误/跳过0；Debug/AndroidTest、独立未签名Release/R8及双变体Lint通过，均0错误/62警告（Debug10/Release9 hints）。testbuild4最终UI批次14/15：14项动效、滚动/草稿、深浅Tab/List矩阵、五张严格截图、正文/预览、Dock回归与Fallback协议边界全部通过；第15项因当前唯一授权订阅未提供非空Fallback组失败，保留为未验，不能写成15/15。

原1.yaml的90个HTTP Rule Provider（82 domain/8 ipcidr，均mrs）已逐项实际更新并回读时间前进：首轮32、页面失败项重试55，剩余3由testbuild5正式页面单项更新成功；共90个不同资源，不能称一次90/90全量批次成功。最后3项补验1/1，还验证连续16秒无busy、Activity重建及核心会话保持。首次网络失败原因未确定，不由重试成功推断根因；原配置没有proxy-providers，不能推广全部类型/格式/来源。

Fallback缺失/空/非法now、有效映射/fixed回退和有效now优先，已通过真实Bridge解析→实际VM→Tab/List×深浅主题20种组合；响应由测试替身注入，不是Mihomo自然产出的异常。更新后的唯一授权订阅已提供34组中的1个非空Fallback/7成员，停止态与运行态8/8次真实核心回读有效，固定选择冷进程恢复及清除固定另有11-06证据。Mihomo合法非空组的Now最终返回存活项或首项，故自然无效now仅保留为不可构造的核心语义边界，不生成或替换YAML。

App/Test已用install -r安装且设备哈希匹配；最终Test为testbuild5，仅对应最后3资源补验，UI14项使用testbuild4，两批生产App相同。网络fixture逐次保护正式1份配置/选择/正文SHA及mtime/VPN，只删除自己的临时ID；原1.yaml双端哈希保持。结束无服务/tun0、旋转1/0、字号1.0。Flutter com.follow.clash仅只读参考，未安装覆盖、卸载、清数据或改配置。授权临时JSON核对后定点移除。

深浅页面/菜单、动画中间帧及Fallback边界截图已目视，五张严格原生基线更新后通过；主题配色与图标仍有差异，不宣称Flutter逐像素一致或全部液滴/玻璃菜单完成。失败批次与包/源码/日志/截图摘要见`docs/acceptance/page-continuity-providers-redmi-20261104.json`，原始附件只留本机/tmp。Go测试未重跑，正式签名Release运行未验；子代理通道失败，未计独立review。备份/系统恢复、观测次要打磨、语言/主题继续暂缓，整体Profiles/UI仍PARTIAL。

### 主页面共用面板与 Providers 真实操作复查（2026-11-04）

对照Flutter `showSheet`/`SnapSheetRoute`，配置切换、内存、IP质量、服务详情/管理改用共用面板：短内容自然高度，长内容9/16与0.9双停靠；标题/回收/刷新/返回/关闭固定，正文独立滚动，服务子页系统返回先退一级。内容增长收缩沿弹簧重排，重合停靠点去重；主页面板使用准确连续圆角，配置切换动画期间只认领一次，隐藏IP整行提供单一开关语义。

最终生产build2 App440/Service188共628项，失败/错误/跳过0，Debug/AndroidTest/Lint通过；独立未签名Release/R8/Lint通过，两变体各0错误/62警告。testbuild4为面板最终测试包，26/26通过；后续仅新增Providers真实页面方法，testbuild6测试包/Lint通过，独立2/2补验。两批共享同一生产APK；早期测试包哈希未单独归档，不把最终testbuild6哈希归给此前26项批次。首批24/25的失败为旧隐藏IP测试未滚动到新正文位置，修正后最终通过，原失败保留。

用户要求重新操作复现Providers：正式当前Profile空列表及重建正常；原1.yaml原字节临时FILE的90项列表从正式配置卡进入，停止态首次2.637秒/重开2.015秒，运行态首次2.008秒。仅点击其中两个已有HTTP资源更新，分别3.535/3.495秒，核心更新时间确实前进；第二次退后台再恢复，结束后重开和180063ms连续轮询均无加载条/残留忙态，Activity重建及核心会话保持。未执行全部90资源批量下载；本次未复现不能认定原现场唯一原因。

before/after-panels/after-final各1/1保护通过，两份正式配置非空正文及文件名/size/mtime保持；真实宿主fixture独立核对原行/选择/正文SHA与mtime、脚本和其偏好。原1.yaml双端哈希保持，最终App/Test安装匹配，结束无服务/tun0、旋转1/0和字号1.0。面板长短/深浅/子页、IP/内存和复现图已目视，严格首页基线未修改。证据`docs/acceptance/dashboard-sheets-redmi-20261104.json`；原始附件只留本机。独立探子任务传递失败，未执行独立review；Core/Common/Go本组未重跑，正式签名Release运行未验。完整液滴/玻璃菜单、其他面板和逐屏动效仍PARTIAL，备份/系统恢复与观测页次要打磨继续暂缓。

### 主页面菜单与 Providers 更新恢复（2026-11-04）

配置来源、配置卡片、代理工具/组选择及节点共用菜单已接通浮层样式、真实锚点避让、安全区/IME内滚动、子菜单Back返回、外部点击/后台关闭和键盘入口；卡片涟漪按连续圆角裁剪。单项Provider超时/取消残留busy已用旧实现两项红测确认，改为finally清理，超时显示重试并登记失败项，旧任务不向新配置发布提示。

最终生产build4 App437/Service188共625项零失败/错误/跳过，Debug/AndroidTest/Lint通过；testbuild7只修改测试基线/截图与夹具，独立未签名Release/R8/Lint通过，各0错误/62警告。Redmi最终同批20/20；同一生产APK前批90个Provider连续180000ms、Activity重建及会话保持通过，该前批整体18/20的两个截图失败已保留，最终批次观察为16秒。最终两包设备哈希匹配；正式两份配置非空正文及文件名/size/mtime前后保持，正式宿主fixture独立保护原行/选择/正文SHA与其偏好，原1.yaml双端哈希保持，结束无服务/tun0、旋转1/0及字号1.0。

首批16/18保留：键盘先明确Keyboard模式/焦点，陈旧RUNNING编辑禁用断言纠偏，并补STARTING/STOPPING写入禁用。四个严格截图基线的变动仅为新增裁剪的轮廓抗锯齿（最大色值差4–5），已看旧/新/差异图后更新；空配置与其他基线保持。菜单深浅/子菜单/长菜单/节点两布局及超时/恢复图已目视，Popup截图等待平台绘制。原始分钟级转圈现场仍缺失，不能认定本缺陷就是当次唯一原因。完整液滴/变形/滑动携带/玻璃菜单和全页面UI仍PARTIAL；独立探子任务通道失败，Core/Common/Go未重跑，正式签名Release运行未验。证据`docs/acceptance/main-menu-redmi-20261104.json`，原始附件仅本机。

### 主页面视觉与 Providers 三分钟补验（2026-11-04，最终同批23/23）

- **实现**：沿Flutter engine八分弧参数实现四逻辑角/RTL/零角/超大半径连续圆角，BSD声明随APK保留；主页面卡片/节点/菜单接入，Dashboard多层阴影和Dock阴影按Flutter参数表达。运行按钮/出站模式绘制按压、短按先升后落、原位命中/语义、子树焦点和最后按下项置顶接通；Dock滑块/放大染色/拉伸分别驱动，松手才导航，取消退回当前入口。组图标按完整字素显示并修复非法代理字符；Tab/List刷新保留列表并统一禁用选择/菜单/测速。
- **构建与参考**：最终生产build3 App430/Service188共618项零失败/错误/跳过，Debug/AndroidTest/Lint通过；testbuild6仅补截图外层偏好保存恢复，生产APK未变；此前testbuild5修复触摸模式恢复；独立Release/R8/Lint通过，各0错误/62警告。真实Flutter Path.addRSuperellipse导出7种轮廓，Android LTR/RTL逐行端点允许1px栅格差；几何oracle不等于全页面像素一致。
- **集中验收**：Redmi API36/arm64/竖屏默认字号，retry-final3-long同批23/23，无跳过。七项新交互涵盖真实Dock拖动/取消/语义/外扩命中、长按不误释放及静态bounds、键盘子树焦点、完整emoji显示和两布局忙态。主页/配置/代理组深浅截图、模式/选择持久化/旧ID隔离/失败重试及旧入口一同通过。原1.yaml运行态90项每250ms检查、连续180000ms，Activity重建后数量及会话保持；正式配置空Providers回归通过。
- **视觉与保护**：14张既有主页面严格基线已目视并更新，5张新增Profiles浅色/来源菜单及3张按压/焦点图作人工辅助参考；运行态/失败态图只证明功能。before、after-preflight、after-final保护各1/1，正式count2/missing0，原选择/文件SHA与mtime、脚本及运行态fixture偏好与原1.yaml双端哈希保持；最终App/Test与设备匹配，App仅arm64、测试包无原生库。结束无服务/tun0，旋转恢复1/0、字号1.0。
- **失败保留**：build1 ColorScheme.shadow未定义修为Black；review1为2/12，真实Dock取消误提交已修，RTL像素镜像/Touch焦点/未就绪系统窗口分别修正。interaction2为7/8，List误用Tab标签已修；final-long为18/23，五项整页截图差异仅为继承键盘焦点。本机Compose字节码确认requestInputMode(Touch)只查询，测试改用平台setInTouchMode并等待恢复，保留原14个批准基线后最终通过，未靠更新焦点污染哈希判绿。早期视觉批次沿用的旧代理页截图宿主会清展示偏好，未独立持久化组前快照，不能宣称最初代理页展示偏好完整保持；最终测试已在Activity规则之外保存并恢复该选项，回读断言通过。
- **证据与边界**：`docs/acceptance/main-visual-redmi-20261104.json`保存包/源码摘要、方法结果、失败与本机附件索引；原始日志/PNG/XML未上传。原始转圈现场缺失，无法认定唯一触发因；三分钟非空纯代理观察不证明所有资源更新/外网或完整进程恢复。Flutter默认非玻璃Dock及主页面基础切片通过，全部菜单/面板/玻璃模式和完整页面parity仍PARTIAL。Core/Common/Go未重跑，Release仅未签名构建、未安装运行，独立review因任务通道故障未执行。备份/系统恢复和观测页次要视觉继续暂缓。

### 首页/代理组与 Providers 等待收敛（2026-11-04，最终同批9/9）

- **读取修复**：Providers/统计按前台、配置及运行状态管理窗口；5秒tick、revision和retry合流，不再以版本通知取消当前读取。独立8秒页面预算覆盖只读与managed bindings，不可取消旧读取不阻塞新配置或发布旧结果；同配置刷新保留列表，失败后停止自动读并保留重试。该轮询本身不下载或重载配置。
- **代理主流程**：Room按组patch选择并保留最新其他组/元数据；实际输入变化才作废generation，revision和重复选择不丢保存；首页与代理页回调带原渲染ID，管理器锁内复核读取、切换和单/批测速目标；读取失败有独立重试。
- **构建与回归**：三个新增不可取消读取/版本突发/换配置回归在旧实现确认失败，修复后定向15/15。最终App426/Service188共614项零失败/错误/跳过，Debug/AndroidTest/Lint与独立未签名Release/R8/Lint通过；Debug0/62/9、Release0/62/8，Core/Common/Go本组未重跑。
- **真机**：Redmi API36/竖屏默认字号/arm64，最终batch-final2同批9/9；正式订阅0资源空态与原1.yaml运行中90个Provider非空态均跨16秒轮询和Activity重建通过，核心会话保持。首页/代理页选择持久化、模式、测速隔离、旧ID拒绝、失败重试及旧入口回归通过；九图已目视，只证明功能态。
- **保护与安装**：after-final2保护1/1，两份正式Profile/正文SHA与mtime、原1.yaml双端哈希保持；最终App/Test安装哈希匹配，结束无服务/tun0并恢复旋转。初批4/7遗留两份副本，核对明确ID/名称/类型/原字节后定点回收；首次恢复名称匹配失败发生于修改前。初批原选择/VPN未独立持久化，不能宣称该失败批次偏好全保护；按已声明默认恢复正式订阅，后续成功批次各自恢复状态。
- **证据与边界**：`docs/acceptance/home-proxy-mainflow-redmi-20261104.json`索引最终源码/包/日志/九图哈希，原始附件只在本机`/tmp`。用户原始分钟级现场未完整捕获，旧包重开可读90项，因此不能认定唯一触发原因；16秒不是分钟压力测试。整体Profiles/UI仍PARTIAL，下一组主页面视觉；备份与系统恢复暂缓。

### 高级配置运行态集中闭环（2026-11-04，最终同批13/13）

- **行为与实现**：对照Flutter `scripts.dart` 当前绑定应用行为，稳定RUNNING开放脚本编辑/同步/改源/删除，STARTING/STOPPING拒绝；当前绑定的提交与应用共持管理器锁，无关脚本/排序不重载。脚本库独立应用失败反馈/重试，资源总览区分已保存但应用失败。
- **存储与并发**：编辑/同步复核目录版本、实际正文SHA和mtime；下载锁外、提交固定生命周期锁→目录锁，rename失败不直接覆盖；Room失败恢复字节/mtime，删除文件失败回滚Room绑定。Provider同分区在Room事务内CAS、其他分区取最新值；全局规则提交与受理目标应用共锁，不重选旧目标。
- **构建**：最终 `build4.log` App420/Service187共607项零失败/错误/跳过，Debug/AndroidTest/Lint通过；`release2.log`未签名arm64 Release/R8/Lint通过。Debug Lint0/62/9、Release0/62/8；Core/Common/Go本组未重跑。日志统一前缀`/tmp/kclash-advanced-runtime-20261104-`。
- **真机**：最终`batch-final.log`同批13/13，之前`batch.log`11/12；断言把health-check.interval误读顶层修正后`batch2.log`12/12，随后HTTP单项通过，最终生产包补资源总览反馈后同批13/13。真实核心验证脚本非同值保存/失败旧输入保持/修正/删除、DNS与全局规则非同值应用/CUSTOM隔离、Provider覆盖/更多设置/侧载/旧目标拒绝、原1.yaml两个已有HTTP资源批量更新，会话计时保持。失败日志保留。
- **保护与视觉**：before及after-final首页保护各1/1，正式Profile count2/missing0/expected2；新宿主每项finally核对原行/选择/脚本及正文SHA/mtime，只回收记录ID与精确缓存路径。原1.yaml双端SHA保持，偏好/全局规则恢复；最终App/Test安装哈希匹配、无服务/tun0、旋转1/字号1.0。4图已主线程目视，不代表完整视觉。完整索引见`docs/acceptance/advanced-runtime-redmi-20261104.json`。
- **后续**：本组运行态提交/应用链路通过；整体Profiles/UI继续PARTIAL。接下来首页与代理组主流程，再主页面视觉收敛；备份/系统恢复暂缓。其他资源SAF、外部脚本源完整E2E、全部Provider/网络参数的实网覆盖和正式签名Release运行仍有独立边界。

### 真实文件入口进程恢复与 Providers 持续加载修复（2026-11-04）

- **故障与修复**：用户真机发现 Providers 持续数分钟转圈。`ensureConfigLoaded`在setup完成前发布serviceCacheRevision，代理组观察者取消自身refresh、输入key始终未登记，统计/Providers的collectLatest也被反复取消。对照Flutter setup完成后登记输入再onUpdated，改为NonCancellable完成已受理加载/模式/输入登记后通知；冷恢复详情不再被初始空Profile列表清掉。新增两个异步setup回归先在旧实现确认断言失败，再随全量单测通过。
- **入口闭环**：FILE/远程两个独立launcher在根部稳定注册，Keystore草稿写入成功后才启动系统选择器；回调等待恢复、按原令牌单次消费，孤立pending解锁。脚本持久化导入令牌、保留同名文件；Provider仅持久化恢复摘要，冷进程重建binding并比较原配置/脚本/缓存，已提交而回读失败只允许重新确认，不重复提交。
- **统一构建**：最终App419/Service185共604项，失败/错误/跳过0，Debug/AndroidTest/Lint及单独Release/R8/LintRelease通过；Lint分别0/62/9和0/62/8。仅修正旧只读预览用例后testbuild3与Lint通过，生产APK未变。Core/Common/Go未重跑。证据`docs/acceptance/file-entry-process-redmi-20261104.json`，日志前缀`/tmp/kclash-provider-loading-20261104-`与`/tmp/kclash-file-entry-20261104-`。
- **真机结果**：同一最终生产APK上，FILE/远程本地副本/脚本/Provider导入及四类取消均通过，每次确认DocumentsUI仍前台、宿主PID消失、返回产生新PID。FILE/远程先只暂存、外层保存才提交，脚本同名新增后缀、原脚本不变，Provider缓存mtime变化且真实核心重新读取原1.yaml字节。分阶段覆盖12个不同精确方法；旧首页/代理/预览三项最终同批3/3，不称一次12/12。Provider导入夹具由前一包prepare，实际冷恢复/侧载/回读在最终包；取消夹具重新prepare。
- **失败与保护**：虚拟时间测试等待、错误测试包名、UI XML非法代理字符及旧预览用例重建保留预期均保留日志。只读预览原有规则是在后台/销毁时清除敏感正文，旧用例改为核对清除、重开和原文件/选择保持，未改生产行为。正式两份Profile/原选择/正文SHA与mtime、原1.yaml双端SHA保持，最终两包安装匹配；自己的配置/脚本/缓存和Download临时JS已回收，结束无服务/tun0，字号1.0/旋转1。
- **边界与后续顺序**：当前文件入口组通过，整体Profiles/UI仍PARTIAL。下一组高级配置/脚本运行态逐项应用，再集中首页与代理组主流程、主页面视觉；备份与系统恢复暂缓。其他资源SAF、外部资源实网、正式签名/Release运行及完整视觉仍未验。首页策略组缩略文字`DashboardVisuals.kt`的`name.take(1)`会截断非BMP emoji，已观测系统uiautomator XML序列化失败并按截图点击绕过；作为主页面视觉组的明确待修项，未把该页宣称完整通过。

### 配置管理闭环集中验收（2026-11-04）

- **提交与应用**：稳定RUNNING开放FILE/CUSTOM正文及结构化保存、脚本绑定和删除；写入与核心应用共持`CoreRuntimeManager`启停锁，避免轮询先加载新正文却沿用旧端口/TUN。CUSTOM原文CAS、DNS/链式代理事务内分区合并、失败保留草稿与独立应用重试已接通；DNS重试始终比较打开时基线。删除当前项先成功应用替代项再删除，失败尝试切回；最后一项先停止核心，删除失败提示停止状态。
- **草稿与排序**：父FILE/CUSTOM/远程表单、正文子页、原文指纹、替换字节和picker令牌进入Keystore AES-GCM/AtomicFile加密草稿，不入Bundle或备份。关闭先等旧写入完成再清密文，VM取消不能让旧草稿复活；选择器先落盘令牌再启动。配置排序改整页，复用整行长按/边缘滚动，取消不提交、失败留页、成功返回，期间新增/删除项按ID合并。
- **构建**：最终生产`build5.log`通过App414/Service182共596项，失败/错误/跳过0及Debug/AndroidTest/Lint；最终仅改测试的`testbuild7.log`通过测试APK/Lint。单独`release.log`通过未签名arm64 Release/R8/Lint及包名/版本0.1.0 (1)核实。Debug Lint0errors/62warnings/9hints，Release0/62/8。Core/Common/Go本轮未重跑，不将前次657项与本轮数量混加。日志前缀均为`/tmp/kclash-config-closure-20261104-`。
- **设备**：Redmi竖屏1080×2340/字号1.0，首批9/13，修正测试断言/异步等待后补跑4/4，进程两阶段各1/1，合计15个不同精确方法按最后结果全部通过，无跳过。运行态FILE/CUSTOM Rules、删除fallback、授权两临时目标真实批量下载后应用、远程正文与运行态元数据旧入口、分区事务保护、排序深浅/独立应用重试/共用拖动及真实本地/远程DocumentsUI通过。网络批次收窄目标列表，未点击可能访问其他历史来源的全局“同步全部”。
- **进程和保护**：准备进程5392结束后另启应用5565，退后台`am kill`并确认PID消失；新进程5678恢复父名称/密钥和正文页。放弃后原Room行、选择、正文SHA/mtime一致，临时ID与marker回收。before2/after各1/1且count2/missing0，原1.yaml双端哈希保持；最终Debug/Test安装哈希匹配，结束无服务/tun0、旋转1/字号1.0。4张画面主线程目视，不宣称Flutter像素一致。
- **失败保留与边界**：build1注入参数、build3 Debug/Release并行导致Lint/Hilt生成文件竞争、build4新增JVM未等IO、before类包名、首批四项定位/等待及prepare非Unit初始化失败均保留并已修正。Redmi`run-as`被SELinux拒绝，改用应用内保护，不提权。最后一项删除分支为JVM证据；picker令牌跨VM恢复有单测，真实系统选择器等待中进程回收再返回文件尚未单独E2E。运行态同语义原1.yaml验收不证明所有分区/脚本或改路由后的实网；Provider/脚本/备份SAF、完整视觉、正式签名/Release运行和远端CI继续独立待办。
- **安装证据**：[profile-closure-redmi-20261104.json](acceptance/profile-closure-redmi-20261104.json)保存15方法最后结果、失败日志、两包设备哈希、未签名Release、源码指纹与进程/文件保护记录。原始日志/图片只留本机；整体Profiles/UI仍为PARTIAL。

### Roadmap 累计状态核对与提交前验证（2026-11-03）

- **准确性核对**：将当前快照的旧单测/包哈希替换为最终结果，同步Provider批量/文档首版、节点菜单到本地目录、v6全局设置备份和正文运行态边界；移除已由此前真实age/SAF切片通过的重复待办。历史批次不改写成当时全部完成，旧安装版扫码复验明确标为“当时”。Profiles/UI完整parity仍为PARTIAL，实网、完整进程恢复和各资源独立SAF边界继续保留。
- **集中检查**：根Wrapper `./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug :app:lintRelease --no-daemon -Ptarget-platform=android-arm64` 成功，日志 `/tmp/kclash-roadmap-push-20261103-gradle.log`；Debug单测App409/Service179/Core38/Common31，共**657**项，失败/错误/跳过0。使用Gradle有效的UP-TO-DATE/缓存结果，不把重复变体或历史数量相加。Go核心模块 `go test ./...` 通过（缓存命中），日志同前缀 `go.log`。
- **Lint与制品**：Debug 0errors/62warnings/9hints，Release 0errors/62warnings/8hints；Release R8通过，未签名APK仅arm64-v8a，apkanalyzer核实包名`com.lonnnnnng.flclash`/版本`0.1.0 (1)`，SHA-256 `8b5a823a17ded9dd33b5f83910eef31b485a3b79ba3e879bb95ef3a05e3c151d`。Debug/Test哈希仍与此前12方法安装证据一致；本次只改文档，没有重装或重跑真机。正式签名、Release真机运行及远端CI不能由本地构建通过代替。
- **可复核记录**：[prepush-20261103.json](acceptance/prepush-20261103.json) 保存四模块统计、命令/日志哈希、三包哈希和累计源码指纹；[profile-document-redmi-20261103.json](acceptance/profile-document-redmi-20261103.json) 保存12方法与最近安装保护证据。原始附件只留本机，仓库不上传含正文的设备日志或截图。当前功能/文档一并纳入本次提交，以下“未提交/推送”是各历史批次结束时记录。

### 配置正文草稿、真实文件选择与运行态应用（2026-11-03，DONE-VERIFIED 定向切片）

- **正文与父页提交**：URL/FILE 共用正文快照与严格 UTF-8/8 MiB 校验，正文整页支持查找、替换/全部、撤销/重做和退出保存/放弃/继续编辑。子页确认只回父草稿，外层保存才提交文件与元数据；远程本地副本提供文件替换及自动更新覆盖提示，可保留自动更新或关闭后保存。失败保留正文/父页输入，Activity 重建保留当前 VM 草稿；完整进程回收恢复不在本组证明范围。
- **保存与应用**：仓库以原正文 SHA-256、mtime、表单所属字段与更新时间拒绝过期编辑，保留保存期间最新选节点、组页记忆和其他覆盖；解密/编码/校验失败不写入，Room 或后置守卫失败恢复旧字节/mtime。仅改元数据不重写正文。远程正文/替换保存成功后应用当前配置，不再下载订阅覆盖刚保存的副本；STARTING/STOPPING 仍拒绝写入，FILE/CUSTOM 结构化运行态保存另列待办。
- **最终构建与失败记录**：证据前缀 `/tmp/kclash-profile-document-20261103-`。最终 `build4.log` 的根 Wrapper `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 成功；App **409** 项，失败/错误/跳过均 **0**，Lint **0 errors、62 warnings、9 hints**。build1 的两项 VM 测试失败、build2 的 `SuspiciousIndentation` 失败均保留；明确分支块修正后 build3/build4 成功。Service/Core/Common/Go/Release 本组未重跑，409 只表示 App 模块。
- **集中真机**：Redmi Note 8 Pro `wsvwypiz7xwslvl7`、API36/arm64/1080×2340竖屏/字号1.0，`device1.log` 为 **OK (12 tests)**，逐方法完成码均成功且无 Assume 跳过；完整 `Class#method` 清单见安装证据与同前缀 `methods.txt`。包含正文 UI 两项、隔离仓库三项、正式正文 Host 一项、真实本地/远程 DocumentsUI 两项，以及远程旧编辑、首页预览、配置整页和运行态元数据四项回归。两个系统选择器均验证取消/暂存零写入、外层确认才提交，并只读取原 `1.yaml`。运行态 Host 使用其原字节创建临时远程 Profile，验证重建后父名称草稿保留、子页确认零落盘、外层保存关闭自动更新且 `RUNNING`/运行起点不变、`profileInputsApplied(id)` 为真，最后恢复原选择并回收临时 ID；没有改写 YAML 字段或下载订阅，不能推广为改节点/路由后的实网行为已验。
- **安装、保护与视觉**：`before.log`/`after.log` 各1/1，正式 Profile `count=2 missing=0 expected=2`，原行/选择及文件名/size/mtime保持。Debug `0.1.0 (1)` App SHA-256 `0e0cebabd79d17d02e8c13883a34bb3d3b213aab710c67cf6a80cf49014d1673`，Test `8f72b3802248ac2f108514a4cd5c1d36e429bf653b2e8a1cedb6e0286daf46b3`，均已安装并与设备 `base.apk` 匹配，App 仅 arm64-v8a、测试包无原生库。原 `1.yaml` 本机/设备 SHA-256 均 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`；结束应用服务为空、connectivity未见tun0，自动旋转恢复1、user_rotation0、字号1.0。`images/` 共12图已查看，新正文失败/退出及远程警告/失败四态控件可达；旧列表测试宿主底色/系统栏仍有历史局限，不据此宣称完整深色页面或 Flutter 像素一致。持久安装报告为 [profile-document-redmi-20261103.json](acceptance/profile-document-redmi-20261103.json)，原始日志仍保留在上述 `/tmp` 前缀。
- **收尾与下一组**：本切片 `DONE-VERIFIED`，完整 Profiles/UI 仍 `PARTIAL`。继续 FILE/CUSTOM 结构化运行态保存/应用、删除当前配置切换回退、排序整页/长按、完整进程草稿恢复、真实加密在线订阅、真实订阅批量下载后核心应用及完整视觉。真实 age Android Bridge 解密、新增明文/密文 SAF 和加密 FILE 替换已由“新增文件与真实 age／SAF 导入闭环”验收，不再列为缺项；Provider/脚本/备份各自的 SAF 仍分别待验。未清数据、删除正式 Profile 或访问新订阅；未提交/推送。

### 配置运行态订阅操作、批量同步与提交保护（2026-11-03，首版接通，定向验证通过）

- **上游范围**：只读本地Flutter `main`基线`7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`的`views/profiles/profiles.dart`、`edit.dart`、`preview.dart`、`providers/actions/profiles.dart`及setup应用链路，未fetch/merge。同步/排序/编辑没有运行态禁用；上游“上传”为本机文件替换远程配置本地副本，不是HTTP上传。正文编辑先回父草稿、外层确认落盘，自动更新覆盖提示与完整正文链路留到下一组。
- **页面与状态**：首页单项同步、详情同步、配置顶栏同步全部/排序、远程订阅设置、重命名与自动更新允许稳定RUNNING，STARTING/STOPPING仍暂禁；自定义/FILE正文结构化编辑、脚本绑定和删除本组保留原门禁，不宣称运行态编辑全部完成。正式同步全部由VM一次受理、四并发隔离失败，共享busy在launch前CAS认领；单项与批量共用持久状态，进度/逐项结果、失败项重试、应用失败单独重试已接入首页及配置页。混合失败重试保留前批成功与未解决的应用失败，不重复下载成功项，Activity重建可继续观察VM状态。
- **应用与存储**：下载完成后通过管理器应用此刻选中配置，不切换到下载目标；当前ID再次核对，应用失败保留已下载缓存并单独提示。元数据及运行态选择/组页记忆的整行读改写改Room事务内重读，避免下载、改名和选节点互相覆盖。远程下载在文件锁外进行；受理时冻结原文件字节/mtime，提交重取文件锁+Room事务核验来源/密钥和文件快照，最新目录元数据合并；独占临时文件rename失败不流式覆盖，DB失败恢复原字节/mtime，删除/改源/迟到同源下载拒绝。失败信息使用安全说明且只写回仍属于原版本的行。
- **构建与review**：证据`/tmp/kclash-profile-sync-20261103-`，最终build2通过App406项0失败/错误/跳过、Debug/AndroidTest/Lint，0errors/62warnings/9hints。build1新增测试误用Profile.selectedMap，改为decodeSelectedMap(selectedMapJson)后修复。独立只读review发现混合批次重试丢应用失败，已修并补回归；四新增App测试覆盖四并发/防重/失败重试、下载后当前ID变化、应用重试零下载、稳定运行态元数据与过渡态拒绝。Service/Core/Common/Go/Release本组未重跑。
- **集中真机与视觉**：Redmi `wsvwypiz7xwslvl7` API36/arm64/1080×2340竖屏/字号1.0，device1精确8/8，methods.txt留清单。新结果页深浅/等待/部分失败/应用失败；三个隔离仓库测试仅使用授权原1.yaml原字节，覆盖下载期间并发名称/选节点/覆盖/自动更新/组记忆、DB拒绝后文件恢复、改源和删除后旧请求拒绝、迟到同源请求不能覆盖新文件。正式MainActivity运行真实授权Profile，验证同步/排序入口可用、打开订阅设置并保存临时名称后仍RUNNING且计时不变、正文未重写，finally恢复原行与VPN开关；没有点击正式实网同步。旧首页运行卡/详情同步及自定义正文/删除门禁回归通过。images三图已目视，无明显遮挡；组件结果图不能当作正式实网批次/完整Flutter视觉。
- **保护与安装**：before/after各1/1，正式Profile count2/missing0/expected2，原选择/文件名/size/mtime保持。artifacts.json的App `18a34f2a106ebc7ada703715ce302a7ab82ffba2929d9452abda5ca0f48ca152`、Test `becb139d120460df33780b14ea389cd23f84308a4997337d02ffa66fcbac0fce`与手机匹配，App仅arm64。原1.yaml双端SHA256保持，结束无服务/tun0，旋转1/user_rotation0/字号1.0；无正式订阅下载、清数据或权限更改，未提交/推送。
- **剩余与下一组**：远程正文编辑/“上传”文件替换、自动更新覆盖提示、通用YAML搜索/替换/撤销及预览、FILE/CUSTOM结构化运行态安全保存/应用、删除当前配置切换回退、排序整页/长按、完整进程恢复和实网批量应用仍待补；运行态保存不能只移除按钮门禁。下一组先整组接远程/文件正文草稿与通用编辑器，再集中验证。本组仅验证管理器应用调用和既有管理器锁，未执行真实下载后核心更新E2E，整体Profiles/UI仍PARTIAL。语言/主题暂缓，免责声明/关于不再恢复。

### 按需连接权限、SSID管理与暂停恢复（2026-11-03，首版接通，定向验证通过）

- **上游与页面**：只读本地Flutter `main`基线`7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`的按需页、connectivity_manager、setup及suspend状态，未fetch/merge。补齐前后台精确定位分步授权、系统设置返回回读、定位关闭提示、SSID添加/编辑/排序/多选批删和失败留草稿；精确匹配保留空格/大小写，32字符与重复校验。Activity重建保留原列表快照，保存结果由VM版本状态关闭当前表单，存储CAS与备份写入共锁。
- **运行与网络**：应用级位置敏感网络回调独立于VPN，Android12起显式FLAG_INCLUDE_LOCATION_INFO；仅观察底层当前网络、不扫描。刷新仅保留仍存在的同一Wi-Fi，蜂窝先回调不误恢复，3秒无能力回读按未知处理；失权/关闭定位/全断网立即清SSID。CoreRuntimeManager共用启停锁，RUNNING保留本次意图/计时，独立suspended流标识转发暂停。命中排除关闭两服务/监听但不shutdown核心，离开恢复同会话，手动停止不自启；暂停时切配置不重建服务，出口/服务检测强制DIRECT并使旧缓存失效。恢复或补偿失败清暂停/计时/应用状态，收尾写盘失败不终止网络监听。
- **备份**：v6应用设置增加可选onDemandSsids，旧v6缺项保留、空数组清空、非法类型拒绝；ALL_DATA纳入读写与失败补偿，PROFILES_ONLY保持规则。没有宣称全局所有写者原子快照。
- **构建与核查**：`/tmp/kclash-on-demand-20261103-build1.log`及最终build2均成功，App402/Service179共581项0失败/错误/跳过，Debug/AndroidTest/Lint通过，最终Lint0errors/62warnings/9hints。两只读review指出后台授权引导断路、草稿快照/迟到回调、旧SSID永久保留、失败清理遗漏，均修正；新增运行与缓存、网络回调顺序/超时、SSID校验/旧快照、归档兼容测试。Core/Common/Go/Release本组未重跑。
- **真机与视觉**：Redmi `wsvwypiz7xwslvl7` API36/arm64/1080×2340竖屏/字号1.0。首批7项6过1失败，组件StateRestorationTester被外层Dialog隔离；改为根组件宿主后最终final-device1精确8/8。覆盖深浅整页/错误/排序/批删、恢复中保存一次、正式入口Activity重建与外部恢复过期拒绝、v6旧归档/失败补偿与旧入口回归。真实授权Profile验证VPN及端口监听关闭/重建、计时不变和手动停止不恢复；临时授予定位读取真实当前Wi-Fi，实际名称仅存内存，命中排除后自动暂停、移除规则后自动恢复通过。final-images四图已主线程目视，IME时保存可见，无明显遮挡；不代表Flutter像素一致。
- **安装与保护**：final-before/after各1/1，正式Profile count2/missing0/expected2，原选择及文件名/size/mtime保持，原1.yaml双端SHA256不变。final-artifacts.json的App `8758872d34812bca09eb83a27b6fb73a4631559e62cef4bd278493b9a08b4cf7`、Test `6a35396770ec8d2efa91c0f28c8a12855133fee93a9ea97acfc9ed07a9fea7b2`与手机匹配，仅arm64。临时定位权限及flags前后完全相同，SSID/VPN偏好恢复；结束无服务/tun0，旋转1/user_rotation0/字号1.0。无订阅下载、清数据或网络切换。
- **剩余与下一组**：系统权限弹窗/永久拒绝/设置返回完整E2E、后台Wi-Fi↔蜂窝切换、系统回收/后台FGS恢复、长SSID列表与精确视觉仍待后续。当前测试是前台真实SSID匹配，不等于全后台保活。继续配置运行态编辑/同步、批次结果汇总与通用正文编辑预览；语言/主题暂缓，免责声明/关于不再恢复，整体PARTIAL，未提交/推送。

### 脚本库整页、文件导入与远程改源（2026-11-03，首版接通，定向验证通过）

- **上游与页面**：只读本地Flutter `main`基线`7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`的`lib/views/config/scripts.dart`，未fetch/merge。脚本库由限高弹窗改独立整页，顶栏添加来源菜单、整行编辑、上下文编辑/远程URL/同步/删除、本地远程副标题、最近更新时间和逐项同步进度；排序复用长按/边缘滚动与取消保护。正文整页支持查找、上一/下一处、替换/全部、撤销/重做及保存/放弃/继续编辑退出确认。
- **数据与文件**：新增SAF本地导入，IO线程读取显示名及有界流；本地/远程统一严格UTF-8、BOM、非空/NUL与2MiB上限，正文空白保留，同名文件分配后缀不覆盖原绑定。修改远程地址先下载成功，再在目录锁内复核原源/名称/版本并一起提交地址和正文；失败保留旧地址/字节/mtime。目录创建/编辑/删除/排序共锁，实际Room目录校验不依赖观察流；编辑用原版本CAS，排序要求完整ID排列。
- **草稿与异步**：正文及原版本、远程表单与原Script均由VM持有，Activity重建保留。generation拒绝关闭/改开后的迟到读取；读取失败不能保存模板。同步认领保存/导入，重复点击与picker重复回调不二次提交，失败留输入，取消归还busy；受理后的文件/Room提交NonCancellable完成或补偿。完整进程重建关闭未提交表单，避免把尚未回读的空目标当成新导入。
- **构建与核查**：证据前缀`/tmp/kclash-scripts-page-20261103-`。build1漏新增测试assertArrayEquals导入编译失败，修正后build2通过App398项0失败/错误/跳过、Debug/AndroidTest/Lint（0errors/60warnings/9hints）。两只读核查完成，数据未发现明确P1/P2；UI指出远程地址草稿和空目标缓存两问题，已改VM持有表单并补正式宿主重建断言。review.md保留范围，Service/Core/Common/Go/Release本轮未重跑。
- **集中真机与视觉**：Redmi `wsvwypiz7xwslvl7` API36/arm64/1080×2340竖屏/字号1.0，device1精确9/9，methods.txt包含UI五方法、仓库三方法与正式宿主一方法。覆盖菜单/文件回调/源修改、同步忙态/排序、搜索替换撤销/退出/失败草稿、非法编码/同名导入/下载失败保护/晚到改源/过期编辑，以及旧脚本排序删除绑定；正式入口分别重建远程地址与正文草稿、真实保存回读并返回库页。images/5图和两张contact已主线程目视，键盘时保存可达，无明显重叠；不代表Flutter逐像素一致。
- **安装与保护**：before/after各1/1，正式Profile count2/missing0/expected2，原选择与正文文件名/size/mtime保持；正式宿主仅创建并回收自己的脚本，原脚本元数据保持。artifacts.json的App `c3c3d67d9cbd144fb1598b1d208517fa3a49474c06d169cca2ecfb5340c13997`、Test `e86d511d018345a68b0f3b55cfc37620d805185a6ea4a03629565aeb89636260`与设备匹配，App仅arm64。原1.yaml双端SHA256保持；结束无服务/tun0，旋转1/user_rotation0/字号1.0。未访问订阅/外部脚本服务器或启动VPN。
- **剩余与下一组**：运行中脚本变更仍沿原停止态限制，运行态安全修改/自动应用、系统SAF完整选取E2E、外部下载/完整进程草稿及精确Flutter视觉待后续；不把组件回调/隔离仓库测试当成SAF或实网完成。下一组继续按需连接的定位授权/SSID整页与实际自动启停链路，再补配置运行态编辑/同步、批量同步结果汇总及通用正文编辑/预览。语言/主题暂缓，免责声明/关于不再恢复；整体仍PARTIAL，未提交/推送。

### 备份与恢复整页及全局设置归档 v6（2026-11-03，首版接通，定向验证通过）

- **上游与页面**：只读本地Flutter `main`基线`7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`的`lib/views/backup_and_restore.dart`、`lib/providers/actions/backup.dart`和`lib/common/backup.dart`。工具备份页按远程/本地/选项分区，账号独立整页、文件名独立编辑、连接状态/重试、上传覆盖确认、恢复范围与持久策略接通；凭据实际回读成功后关闭账号页，失败保留草稿。本机WebDAV凭据不写入归档。
- **归档与恢复**：v6全量新增`application_settings.json`，保存常规、网络、全局DNS、VPN、可空LAN覆盖与恢复策略；显式空列表/空监听/false/null往返保持，不生成新认证。仅配置范围不覆盖全局设置；旧v1–v5缺项保留现值，v6全量缺项/错类型拒绝。管理器启停锁仅受理STOPPED/FAILED且无Geo更新，结束或补偿后刷新落盘状态/缓存，不启动服务。失败回滚Room/文件并逐项补偿本轮设置，补偿异常保留原因。
- **文件与忙状态**：同实例备份导出/恢复串行；SAF读写移到IO并共享busy，取消归还忙状态。压缩流64MiB、解压单条16MiB/总128MiB/4096项限制。运行态允许本地导出和手动WebDAV上传，恢复继续停机保护。完整备份包含节点/入站凭据，页面提示妥善保管。
- **集中构建**：证据前缀`/tmp/kclash-backup-settings-20261103-`，最终build6通过App394/Service172共566项单测，0失败/错误/跳过，Debug/AndroidTest/Lint通过；Lint0errors/60warnings/9hints。build1缺NonCancellable导入、build2多余括号与图标参数错误已修，build3–6成功。Core/Common/Go/Release本轮未重跑。
- **真机与视觉**：build5首批11/11，截图发现IME候选回显测试密码，补密码键盘并同步常规密码输入。最终Redmi `wsvwypiz7xwslvl7` API36/arm64/1080×2340竖屏/字号1.0，final-device1精确13/13：备份UI/正式入口与重建、隔离归档全局设置/仅配置范围、写入失败完整补偿、损坏v6/v5/v1兼容、资源偏好/Geo补偿、Android回环WebDAV及常规页回归。final-images/10图和三张contact已主线程目视，密码候选不再回显，保存可达；非Flutter像素一致证明。
- **安装与数据保护**：final-before/after各1/1，正式Profile count2/missing0/expected2，原选择/正文文件名/size/mtime保持；生产偏好恢复使用隔离Room/root，finally恢复原值。final-artifacts.json的App `7b99c7ea5748994b08b9fb1de1cedfa69c251e567d7e58755220b64e2fcf5cda`、Test `27ceff678f6f2e113169066a03b42deaee8bfa43af4b0d96615f0068ff5c2a5a`与设备匹配，App仅arm64。原1.yaml双端SHA256保持，结束无服务/tun0，旋转1/user_rotation0/字号1.0。未访问订阅、外部WebDAV或启动VPN。
- **剩余与范围**：其他界面/按应用代理等偏好尚未纳入v6；Profile/资源/自动更新写者仍有各自锁，不宣称跨全部写者原子快照。SAF等待选择期间Activity/进程重建、系统文件选择器完整E2E、外部WebDAV/自动云同步、完整进程启退、应用更新检查与Release仍待后续。工具语言/主题暂缓且保留占位，免责声明/关于永久移除。子代理通道故障，独立review未完成；整体UI保持PARTIAL，未提交/推送。

### 常规设置整组还原与运行链路（2026-11-03，首版接通，定向验证通过）

- **上游范围**：只读本地Flutter `main` 基线 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 的 `lib/views/config/general.dart`、`general/port_form.dart`、`user_agents.dart`、模型与初始化/退出动作，未fetch/merge。整组实现自动运行、按需连接、退出最小化、最近任务隐藏、通知停止按钮、User-Agent独立管理、证书校验、五端口/LAN/外部控制器/认证、测速超时/查进程/切节点关闭旧连接/仅代理流量统计、Hosts独立管理、追加系统DNS、日志等级/收集/查看。语言/主题暂缓；免责声明/关于永久移除。
- **设置与核心**：独立GeneralSettingsStore，旧快照比较和核心启停共锁；只有runtimeKey变化应用当前Profile，通知/统计/日志等独立更新。应用或日志订阅失败恢复偏好、旧核心和hooks；补偿失败停止服务并回读。预览冻结设置，缓存键含核心参数且LAN仍最后。general在网络之后、LAN/Geo之前结构化合并运行副本；生产使用Flutter常规默认值，不回写订阅正文。控制器固定回环并生成密钥，认证空账号生成8位账号/16位密码，用户名去冒号。测速0占位使用保存超时，显式超时仍优先；切节点可关闭旧连接。
- **应用与下载**：Activity按当前任务回读更新最近任务标志，API24兼容字段；自动运行由retained VM一次认领，复用权限与启动流程；根返回按最小化偏好退后台或先停止再退出。Subscription/Script/规则/图库/WebDAV下载冻结本次UA和证书策略；宽松TLS只作用本连接/克隆client，未改系统默认。Core.setupConfig传入skipCertVerify。自动检查应用更新未接Kotlin发布链路，未摆假开关或使用Flutter发布替代。
- **统一构建**：证据前缀 `/tmp/kclash-general-settings-20261103-`。build3首次完整通过App392/Service171/Core38共601项单测，0失败/错误/跳过及Debug/AndroidTest/Lint；端口整页改动后build4重新通过App392与Debug/AndroidTest/Lint，Service/Core源码未再改，沿用build3结果。Lint0errors/59warnings/9hints。新增测试涵盖端口冲突/认证脱敏/运行key、暂存未知字段/Hosts/DNS、运行hooks/统计/超时/关闭旧连接、核心与日志失败回滚、旧快照拒绝，以及下载重定向UA快照/连接TLS隔离。
- **集中真机**：Redmi `wsvwypiz7xwslvl7` API36/arm64/1080×2340竖屏/字号1.0，初批device1与最终final-device1均精确6/6，清单final-methods.txt。两新页面方法覆盖整组开关/认证/Hosts/深浅主题、端口校验/失败留草稿/重试/UA独立页；两正式宿主方法覆盖端口保存/Activity重建、最近任务实际FLAG、运行中端口/认证/日志/统计更新、实际系统通知actions为空、核心运行时间保持与原Profile保护；另回归按需连接入口、真实VPN UDP53/TCP53/系统DNS。没有重导现有Profile或改订阅，finally恢复本轮偏好与VPN开关。
- **制品与保护**：final-before/final-after首页各1/1，正式Profile count2/missing0/expected2；宿主断言原选择/文件名/大小/mtime不变。原1.yaml双端SHA256仍 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。final-artifacts.json记录App `dcd0bc2f69c8aa1916ca5379a2769c0ebe61606dd94fb0146d1b2abf82eec616`、Test `e538f8258812d3ac1a0b0a7de29abc28bd482b14635e65ae3028f733e33c750d`与设备相符，App仅arm64。结束无应用服务，dumpsys connectivity未见tun0，旋转恢复1/user_rotation0、字号1.0。
- **视觉与修正**：初批7图目视发现端口AlertDialog底部被IME遮挡、UA单选与文字太近；端口按Flutter改整页表单/数字键盘/固定顶栏保存/恢复默认确认，并调整UA间距。最终final-images/7图及final-contact-0/1已主线程查看，键盘开启时保存可见、错误与输入保留。不是Flutter像素一致证明。build1可空taskInfo编译错误、build2的taskId API29 Lint错误已修为旧id兼容字段；build3/4通过。设备ip link show权限拒绝，改系统connectivity回读；子代理空Payload，独立review未执行。
- **剩余与下一阶段**：全局常规/网络/DNS设置备份、Kotlin应用更新检查、完整进程自动启动/退出行为、Release、外部HTTPS策略及控制器真实请求、切节点关闭连接的真实网络行为和测速超时实网逐项验收仍待补。当前DNS回归不等于所有新字段网络E2E，未重验X浏览器。下一组对照Flutter补齐剩余常用设置与备份完整链路，随后集中补运行态/文件选择器等真实路径；整体UI仍PARTIAL。未提交/推送。

### 全局 DNS 与附加规则整组首版（2026-11-03，DONE-VERIFIED 首版）

- **上游与页面范围**：只读 Flutter `main` 基线 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 的 `lib/views/config/dns.dart`、`rules.dart`、`lib/providers/state/profile.dart`、`lib/common/task.dart`、`lib/models/clash_config.dart`，未 fetch/merge。高级配置四入口为网络 / DNS / 附加规则 / 脚本；工具语言/主题继续暂缓，免责声明/关于不再恢复。
- **全局 DNS**：独立 SharedPreferences 持久化 `listen / ipv6 / enhanced-mode / fake-ip-filter / fake-ip-filter-mode / default-nameserver / nameserver / fallback / proxy-server-nameserver / direct-nameserver` 十项通用字段。按选项/Fake-IP/Nameserver 分区，添加、逐项保存与移除覆盖；未添加继承、空列表显式清空、空监听关闭独立监听，失败保留输入并支持重试。模式仅 normal/fake-ip/redir-host，Fake-IP 模式支持 blacklist/whitelist/rule；不把 Profile 专属 enable/respect-rules 等字段搬到全局页。
- **核心输入与隔离**：先脚本/扩展和 DNS baseline，再合并普通配置全局 DNS，保留既有 Profile 显式覆盖在其后；CUSTOM 使用自身 DNS，不叠全局 DNS。预览与暂存共用路径，行内/引号 DNS 先结构化合并，其他分区与源文件保持原文。配置缓存键和预览快照包含全局 DNS，LAN 仍在键末位；在启停锁内复核旧设置、写盘、应用，失败恢复偏好，偏好补偿失败停止服务并回读实际值。原订阅正文不回写。
- **独立附加规则**：全局目录不依赖某个 Profile，支持搜索、添加/编辑、预设、多选/全选/删除确认和独立排序；排序复用边缘滚动组件，搜索时禁用全目录排序。沿用稳定 ID、配置禁用集合、标准模式叠加及 MATCH 占位，不向 CUSTOM/脚本模式注入规则。全局页与旧扩展页的写入均使用 Room 事务旧快照比较，拒绝过期页面覆盖。保存只尝试应用受理时当前配置，切换不串目标；应用失败保留已保存目录并提供重试应用。
- **统一构建**：根 Wrapper `:app:testDebugUnitTest :service:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 最终 `build3.log` 成功，App390/Service165共555项，失败/错误/跳过0，Lint0errors/59warnings/9hints，证据前缀 `/tmp/kclash-global-settings-20261103-`。新增覆盖空值/false往返、DNS未知字段与flow/引号分区、CUSTOM隔离、预览和运行失败回滚、规则无Profile保存/过期拒绝/重复提交/应用失败重试/保存期间切配置。
- **集中真机**：Redmi `wsvwypiz7xwslvl7`、API36/arm64、1080×2340竖屏、字号1.0，`device1.log` 精确5/5：新DNS空列表/失败重试/移除/主题；附加规则搜索/表单/预设/排序按钮/批删/主题；正式工具入口保存、Activity重建及真实Room过期写拒绝；旧网络页宿主回归；真实VPN UDP53/TCP53/系统DNS均true。`before.log`/`after.log`首页保护各1/1，正式Profile count2/missing0/expected2，选择与原文件名/大小/mtime保持；全局DNS/规则与网络偏好在finally恢复。
- **安装与视觉**：`artifacts.json` 记录App `e73b500402f052d00519b0068523f4f797c19959645b67cab1a0b90828a288b9`、Test `495fb192057be45141f69ea222623481df497719dc7e6f4ed300ccfe81205645`，均与设备匹配，App仅arm64。原1.yaml双端SHA256保持。`images/`10图和`contact-0/1.jpg`已主线程目视，含正式页面/深浅主题/排序和IME，无明显遮挡裁切；不宣称Flutter像素一致。结束无服务/tun0，自动旋转恢复1、user_rotation0、字号1.0。
- **失败与后续**：`build1.log`新往返测试发现既有DNS读取把显式空列表转为null，已修正，并同步保留空监听；`build2/3`全通过。子代理再次收到空任务正文，独立review未运行。全局DNS与网络设置尚未纳入备份；新DNS字段的真实运行态热改/失败补偿、附加规则真实运行态与长列表触摸排序/并发后台恢复、完整进程重建/Release与Flutter同屏视觉仍待后续。真实VPN用例只证明恢复原偏好后的DNS回归，不等于新字段运行态逐项验收。常规其他设置继续整组还原，整体UI保持PARTIAL；未提交/推送。

### 工具入口调整与 Android 网络设置整组还原（2026-11-03，首版已接通）

- **用户范围调整**：工具页永久移除“免责声明”和“关于”，普通模式不留空的“其他”分区；语言、主题保留现有入口但暂不实现，不再将这两项作为当前推进阻塞。高级配置先接通“网络”和既有脚本库；全局 DNS / 附加规则和常规其他设置继续排队，不把此页视为完整高级配置 parity。
- **上游与页面**：沿用只读 Flutter 基线 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`，核对 `lib/views/config/{advanced,network,tun}.dart`、`lib/models/{config,clash_config}.dart` 与 `lib/common/task.dart`。Android 网络页整组提供 VPN、系统 HTTP 代理、绕过域名、允许绕过、IPv6、DNS 劫持、四种协议栈、MTU、mips 四种拥塞控制、绕过私有路由及包含/排除 CIDR。分区列表、逐项确认保存、恢复默认、保存失败留草稿和忙状态已接入；桌面专属接口名、Keep Alive、routing mark 不搬入手机页。
- **数据与运行链路**：独立持久化全局网络设置，预览/暂存/核心缓存键及 Android VpnOptions 共用同一快照；旧异步预览拒绝接受网络设置变更后的结果。数字 IP 校验不解析域名，CIDR 差集对齐 Flutter 私有地址表，明确区分未限制路由与全部被排除，防止零路由被误恢复为全量接管。保留 DNS baseline、原端口和认证代理保护。运行态在启停锁内应用并按实际参数重建系统网络，保持当前 VPN 启用模式；失败同时回滚配置、服务和偏好，连偏好回滚也无法写入时回读实际值并停止服务。原订阅正文不回写。VPN 启用开关沿用已有“重启生效”流程。
- **统一构建**：最终 `build6.log` App387 / Service158，共545项单测失败/错误/跳过0，Debug / AndroidTest / Lint通过，0errors/59warnings/9hints；证据前缀 `/tmp/kclash-network-settings-20261103-`。新增7项单测覆盖 CIDR/私有地址/DNS路由保留/零路由、输入边界、DNS与未知字段保护、停止态与旧快照拒绝、运行态重建/回滚及回滚落盘失败后停止。补充发布混淆规则保持 NetworkSettings Gson字段名和默认构造器；本批未执行 Release、Core/Common/Go测试。
- **真机集中验证**：Redmi API36/arm64/1080×2340竖屏/字号1.0，`final-device1.log` 六个精确方法6/6，含新页面深浅/选项保存、无效输入和失败重试、正式工具入口移除/保存/Activity重建、真实运行态系统VPN回读新MTU/关闭HTTP代理/排除路由、旧按需连接入口以及真实VPN UDP53/TCP53/系统DNS回归（全部true）。运行态网络更新未重启核心运行计时。使用当前唯一授权源的既有Profile，不重新导入或替换原文件；浏览器网页与外部节点质量未在本批重验。
- **制品、保护与视觉**：`final-before.log` / `final-after.log`首页各1/1，正式Profile count2/missing0/expected2；宿主测试另断言当前选择与文件名/大小/mtime保持。原1.yaml双端SHA仍 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。App `eaa8d8d0766d269b41ce88a9b4e203a5fd4d26319187aa8475ef12d3a5b93781`、Test `ffc6e9aa5a9489f1d8377139473153f2fcd0ea39665f3574d7ddf36d420e415c`均与设备安装包匹配，App仅arm64；`final-artifacts.json`记录。`final-images/`8图及两张contact已目视，含正式工具/网络页、组件深浅和失败输入，未见重叠；无Flutter同屏基线，不声称像素一致。结束无应用服务/tun0/相机客户端，恢复原旋转与默认字号。测试改动的全局偏好均恢复。
- **失败记录与后续**：build1的两项失败分别来自新增缓存键挤掉LAN热更新依赖的末位及IPv6测试网段写错；修复后build2及最终build6全过。首批device1为5/6，错误提示存在但测试未启用局部文字匹配；修正断言后最终6/6。首批组件截图未带系统栏安全区，改用正式整页容器并补正式宿主图后最终8图通过，保留失败证据。子代理通道仍未收到任务正文，独立review未运行。网络设置尚未纳入备份、完整进程重建与发布版运行未验；常规更多设置/全局DNS/附加规则及整体UI继续保持PARTIAL。未提交/推送。

### 配置详情整页与外部 Provider 管理（2026-11-03，首版已接通，定向验证通过）

- **上游入口**：沿用本地只读Flutter基线`7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`，亲读`lib/views/proxies/providers.dart`与`lib/views/dashboard/widgets/profile_detail.dart`。外部Provider实际位于首页当前配置详情，不新增代理页假入口。详情改完整页面，保留统计、订阅用量、来源/更新时间/扩展模式、运行配置与订阅同步。
- **整组实现**：Provider全部更新并行冻结同一配置的HTTP对象，返回逐项安全错误码；单项/批量共用门禁，界面提供更新状态、失败汇总和仅重试失败项。点击预览、长按或更多菜单可进入编辑/从文件导入/同步/订阅用量；文本可编辑、MRS只读dump、其他二进制给出受控提示。编辑失败保留输入，返回可保存/放弃/继续编辑。文件选择器先冻结目标、配置与文件指纹，返回按原令牌提交，有界8MiB和严格UTF-8/NUL校验，旧选择器不能改写新窗口。
- **核心与归属**：Bridge不把私有路径或原始错误传入界面，只返回正文/类型/指纹、必要的路径哈希和用量。Go持有配置锁核对epoch/version与原文件SHA，侧载通过Mihomo校验/写缓存；失败恢复原字节/mtime，恢复失败保留备份。MRS输出有界并补偿上游转换器吞写入错误的边界。批量下载在配置锁外执行，不阻塞停止。Kotlin仍核验配置输入、缓存修订号和核心会话，更新自身仅允许路由版本变化。应用管理的远程规则依据稳定资源ID和实际文件指纹映射回资源仓库，同步后仅重新应用发起操作的当前配置；此实际同步路径仍待真机验证。
- **统一构建与单测**：`build2.log`的App387/Core38/Service151共576项单测全过，Debug/AndroidTest/Lint通过；`go1.log`为core目录`go test ./...`全过。最终界面修改后`build3.log`再次通过App387、Debug/AndroidTest/Lint，0errors/59warnings/9hints。证据前缀`/tmp/kclash-provider-management-20261103-`。Common/Release、VPN/浏览器本轮未重跑。
- **真机与视觉**：Redmi API36/arm64/1080×2340竖屏/字号1.0，`final-device1.log`六个精确方法6/6：新菜单/用量、编辑草稿保护/MRS只读、旧详情加载/失败重试/单项更新、深浅详情、统计状态与运行配置预览。使用固定UI/VM夹具与正式页面组件，不将其算作真实网络或生产Provider侧载。`final-images/`9图及`final-contact.jpg`已目视，含菜单、用量、失败、退出确认与只读状态；详情截图滚动到列表，统计顶部另有行为回归，不宣称Flutter像素一致。
- **文件与制品保护**：首批与最终before/after首页均1/1，正式Profile count2/missing0/expected2，文件名/大小/mtime保持；本批不写正式Profile或选择。原1.yaml双端SHA-256仍`bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。App`83d9ac105c8006fc0433d644d6e06e43a71c959b528f745acfeb283f2a3a99d1`、Test`c4e0f6d44a8a113c0c483929f2a9f3c295fecea6995b7e151203f1e4d4cdd038`均匹配设备；App仅arm64。见`final-artifacts.json`。结束无应用服务/tun0，相机Active Clients为空，自动旋转恢复1。
- **失败与后续边界**：首轮`build1.log`两项管理器回归失败，修复更新后过度放宽缓存修订号检查，并调整异常链安全断言；`build2/3`通过。首批真机6/6但深色截图是主题过渡帧，截图等待系统绘制后最终6/6、9图通过，同时补详情分区。未执行系统OpenDocument到真实Provider写入完整E2E、应用管理远程规则真机同步、运行态批量/侧载、真实外部资源下载或完整视觉矩阵；这些继续排队，不能将Go临时资源/VM/组件测试推广为端到端完成。子代理通道返回空任务，未完成独立review。未提交/推送，整体目标保持PARTIAL。

### 更多设置逐项保存与空值继承（2026-11-03，DONE-VERIFIED 本轮范围）

- **上游依据**：沿用只读Flutter基线`7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`，对照`lib/views/config/{ntp,sniffer,override}.dart`和`lib/models/clash_config.dart`。添加/移除/开关直接落盘；文本/列表确认后保存；有效YAML返回保存，无效返回确认放弃；快速编辑中初始未列出的空入口保留。本轮未fetch/merge或修改Go。
- **页面**：父页移除总保存；失败留页、原输入保留并支持重试。协议页拆为端口子页和独立目标选项；目标选中即保存，端口确认后返回协议页，留空使用协议默认端口。原CUSTOM更多页和导入菜单回归同步接新revision回读。
- **数据和核心**：空文本/列表仅保留未填写入口，实际暂存过滤空值，false继续覆盖；空sniff恢复继承，不屏蔽源sniffing。`ProfileMorePatch`按单字段及协议内属性差异合并最新JSON，保留另一区、并发目标/端口和未知参数。Room事务内前后检查CUSTOM、会话及运行守卫，仅更新元数据，不写源正文/mtime。共享codec增加可选preserveNulls以区分主动null与缺省，默认脚本行为保持。
- **统一构建**：根Wrapper执行`:app:testDebugUnitTest :service:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`，最终`build3.log`通过，App384/Service149项、失败/错误/跳过0，Lint0errors/59warnings/9hints。仅测试定位修正后的`build4.log`测试包及Lint通过。统一证据前缀`/tmp/kclash-more-inplace-20261103-`；Core/Common/Go、Release与VPN/浏览器本轮未重跑。
- **真机**：Redmi `wsvwypiz7xwslvl7`、API36、arm64、1080×2340竖屏/字号1.0。`device1.log`9/10，`device2.log`受影响2/2；按最后结果10个不同方法全部通过，见`methods.txt`和`results-final.json`。六项更多UI覆盖逐项/校验/失败重试/清空/协议独立保存/深浅主题，另有真实MainActivity保存重开及Activity重建、隔离仓库事务回滚/并发字段保留和旧更多导入/Provider两项回归。正式宿主仅从原1.yaml创建并按ID和名称回收自身临时CUSTOM，隔离仓库仅回收自身独占缓存目录，未访问网络。
- **保护与制品**：before首页1/1、after及after2首页各1/1，正式Profile count2/missing0/expected2，原文件名/大小/mtime及当前选择保持。原1.yaml双端SHA-256仍`bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。App`86db1e6a75c7c73a97f1e361e5a4743c65d3df2af00f33712b6cf9074a9f3cbe`、Test`0a996f6c7b6a064abf918dffa7be19b01b3d44f132aa53bda116e1fcd145f7e2`与设备一致，App仅arm64，见`artifacts.json`。结束无应用服务、connectivity未见tun0、相机Active Clients空，恢复自动旋转1。
- **视觉、失败和后续**：images/23图及contact-0/1/2已主线程目视，含键盘与错误态，未见明显裁切重叠；添加sheet采图出现系统拖动手柄提示，仅作当前布局证据，不宣称Flutter像素一致。build1新增VM测试缺两个JUnit导入；build2新用例暴露codec省略null；build3修复后全过。device1唯一失败是非滚动协议选项弹窗沿用performScrollTo，改测试定位后device2通过，生产逻辑未因该测试失败修改。子代理通道仍返回空任务，未运行独立reviewer。本轮切片完成，Provider批量/侧载、运行态与完整Profiles/UI保持PARTIAL；未提交/推送。

### 本地代理分享链接复杂编辑与长列表排序（2026-11-03，DONE-VERIFIED 本轮范围）

- **上游与差异修复**：沿用只读Flutter基线`7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`的`lib/providers/state/custom.dart`（`ShareLinkEdit/withUnlinkedFields/linkProxyName/freeProxyName`）和`views/profiles/custom/custom_proxies.dart`，未fetch/merge、Go未改。原Kotlin单节点改名会丢失链接外字段且未避开其他本地节点名称；批量对所有行重复解码、冲突名称仍可能接管旧ID。现按上游规划修复。
- **链接编辑**：新增共享`LocalProxyLinkEdit`，完全相同行保留原ID/完整定义，重复链接逐个消费，仅解析改动行并保留源行号；同名且未冲突的替换保留ID，改名按新节点处理。未命名含核心生成的`-01`回退服务器名，避开全局节点/组/保留名并用上游`-01`后缀；不能编码为链接的协议按最近保留锚点保留。旧链接能回读且协议相同时递归保留链接不能承载的字段，链接能承载而已删除的字段不恢复；换协议或无基线不混入旧设置。
- **草稿与确认**：单节点改名也保留链接外字段，填充只返回父表单草稿，明确保存后才写目录；保留父页打开时expected快照，外部目录变化仍拒绝覆盖。批量新增/移除同时展示，继续编辑不丢文本；先校验解码定义、合并后再校验改动节点，失败保留草稿，链接校验错误只显示源行号和修复动作、不回显核心凭据。无法导出链接的单节点仍允许从新链接填写。
- **排序**：本地代理目录复用资源页视口整行长按/边缘持续滚动，取消与后台零提交，只有松手保存完整ID排列；新顺序回读保留视口，搜索退出排序，保存失败恢复已保存排列。没有新建独立节点排序实现。
- **构建**：`./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`通过，App378项，失败/错误/跳过0，Lint0errors/59warnings/9hints。统一证据前缀`/tmp/kclash-local-link-edit-20261103-`，最终build2；首批build1因新增测试DAO误写`preferences()`而非`appPreferences()`编译失败，已修正，失败日志保留。另核对unsupported锚点期望按最近保留行跟随，修正测试期望。Core/Service/Common/Go、Release与VPN/浏览器未重跑。
- **真机**：Redmi `wsvwypiz7xwslvl7` API36/arm64/1080×2340竖屏/字号1.0，device1精确7/7，methods.txt/results.json记录。新三方法验证真实codec+VM+页面、边缘跨帧滚动/取消零提交/松手一次及深浅错误/放弃/替换；旧本地页面两项、原1.yaml native codec一项、正式节点菜单新增/重复反馈/Activity重建一项回归通过。新链接用例只读原1.yaml节点，用隔离Room/cache接正式页面验证填充后零写入、重名避让/扩展字段、并发过期拒绝与重试、无效源行/输入保留、替换取消与确认后保留unsupported；不截图真实节点文本，不作为正式目录链接编辑/进程重建或全协议验收。
- **保护及制品**：before/after首页各1/1，正式Profile count2/missing0/expected2、文件名/大小/mtime保持。原1.yaml双端SHA-256仍`bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。App`ea6e9ecf48649d498b1d348c4e535e183574a869e93795eca4b29ca507021064`、Test`c3a7debf701c3ae9bc06265552de78b55439986b61f87d6bbb1854fd34802439`均与设备匹配，App仅arm64，见artifacts.json。结束无应用服务、connectivity未见tun0、相机Active Clients空，恢复自动旋转1；未访问订阅、清数据或改正式Profile。
- **视觉与后续**：images/18张及contact-0/1已主线程集中目视，含IME/错误/放弃/替换、目录排序和旧协议表单，未见明显重叠裁切；没有Flutter同屏基线，不宣称像素一致。独立reviewer受子代理通道故障未运行，有限复核见review.md。本地代理首版继续补运行态/全协议真实保存及重建边界；下一阶段先核实更多设置逐项保存/空值语义与Provider批量/侧载等整页链路，整体仍PARTIAL。未提交/推送。

### 策略组节点菜单与添加到本地代理（2026-11-03，DONE-VERIFIED 首版）

- **上游与交互**：继续以只读Flutter基线`7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`的`lib/views/proxies/card.dart`、`providers/actions/proxies.dart`、`providers/state/custom_import.dart`和`models/clash_config.dart`为参考，未fetch/merge，Go未改。Selector菜单“选择”、URLTest/Fallback菜单“优选”，标记当前/固定状态；长按本身不切节点。LoadBalance仍可打开叶子节点菜单及独立测速。内建/组节点和CUSTOM自有节点不允许添加，CUSTOM的Provider叶子节点允许添加；Tab/List与深浅主题共用政策，启停过渡和切配置关闭旧菜单。
- **保存与隔离**：添加事件携带所属Profile ID，管理器在transitionLock内确认实际加载输入，读取前后复核Profile/输入指纹；停止态加载配置但不启动服务，拒绝旧菜单读取另一配置同名节点。原生校验后保存前再核对当前ID；成功和重复均经既有全局Snackbar提示最终名称，错误不输出核心原文。
- **共享去重**：新增`planLocalProxyAddition`供菜单和CUSTOM导入共用。深拷贝、移除配置专属dialer-proxy，比较忽略name且递归按字符串比较数值；同名同类型/服务器/端口/凭据账号保留本地可选设置，其余同名冲突避开已有节点、CUSTOM组和保留名。保留未知字段，同一定义不同别名可复用并映射引用。
- **统一构建**：`./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :service:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`通过，App372/Core36/Service148共556项，失败/错误/跳过0，Lint0errors/59warnings/9hints。证据统一前缀`/tmp/kclash-node-menus-20261103-`，最终`build2.log`；首批`build1.log`新增instrumentation缺dp导入编译失败，修正后通过，原JVM均通过。Common/Go、Release和VPN/浏览器未重跑。
- **真机**：Redmi `wsvwypiz7xwslvl7`、API36、arm64、1080×2340竖屏/字号1.0，`device1.log`精确6/6，`methods.txt`与`results.json`记录完整方法。菜单新UI两项、真实宿主一项及旧协议/仓库/停止态核心三项回归通过。真实MainActivity仅读授权原1.yaml原字节创建唯一名临时FILE，通过正式菜单新增/重复反馈/Activity重建后从本地代理目录回读；finally恢复选择，仅按新增ID或冻结定义回收自身节点、按ID及唯一名称回收自身临时配置。
- **保护与制品**：before/after首页各1/1，正式Profile count2/missing0/expected2，原文件名/大小/mtime保持；原1.yaml双端SHA-256仍`bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。App`d182df56cad39389b834b297a1dded7ad1b15e8e18f6a7b2170115369443520b`、Test`bcb7887776a216282d667d0663237cd0be81a008e539bdf13a0e2a1f33ffee66`均匹配设备，App仅arm64，见`artifacts.json`。结束无应用服务、connectivity未见tun0、相机Active Clients空，自动旋转恢复1；未清数据或改正式Profile。
- **视觉及后续**：images/16张及contact-0/1已主线程目视，四张node_provider完整页面菜单未见明显重叠裁切；八张node_menu单卡片宿主缺完整页面背景与系统栏留白，只作控件行为证据，不算整页视觉通过。旧浅色Tab截图仍开工具菜单，不能作关闭态基线。独立reviewer因子代理通道故障未运行，主线程有限复核见`review.md`；没有Flutter同屏基线，不宣称像素一致。整体仍PARTIAL，下一阶段先核实分享链接复杂编辑、父页逐项持久化/草稿、Provider批量/侧载等剩余整页链路；运行态添加和真实外部网络仍待验。未提交/推送。

### 规则资源／图库排序与组图标推荐（2026-11-03，DONE-VERIFIED 首版）

- **上游范围**：沿用本地只读Flutter基线`7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`的`lib/views/config/{providers,icon_sets}.dart`、`lib/views/profiles/custom/icon.dart`和`lib/models/common.dart`。规则资源和图库管理页提供独立排序模式，上游这两页没有批选批删，不将旧待办泛称的“资源批量”扩展成新交互；批量URL导入已在前序实现。
- **排序与保存**：共用视口整行长按、边缘按住持续滚动与上下移动。手势取消、后台或退出不提交；只有松手提交完整ID排列，仓库在目录锁内拒绝重复、缺失、额外或过期排列，只改顺序并保留最新元数据和文件。回读顺序保留视口索引，防止旧首项key锚定将新首项推离顶部。保存失败保留已保存顺序和错误，支持重试；真实页面保存/Activity重建恢复已验。
- **推荐与来源**：推荐来自已导入图库和最近记录，不是在线图库目录。复用上游小写/去非英文数字/去扩展名后的匹配优先级、稳定排序与24项匹配上限，再追加去重最近项；匹配在后台执行。每个图库独立来源，初始定位当前图标所在图库，支持来源内搜索、选中语义、本地图片/URL及移除；删除最近记录不修改图库或当前图标。
- **统一验证**：`./gradlew :app:testDebugUnitTest :service:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`通过，App367/Service147，失败/错误/跳过0，Lint0errors/59warnings/9hints。最终生产及测试包`/tmp/kclash-resource-sort-icons-20261103-build4.log`。Core/Common/Go、Release和VPN/浏览器未重跑。
- **真机与失败记录**：Redmi API36/arm64/1080×2340竖屏/字号1.0，`device2.log`精确13/13：新排序/推荐三项、隔离仓库一项、真实宿主一项、旧资源/图标三项及扫码五项；方法见同前缀`methods2.txt`。首批`device1.log`7/8，测试EntryPoint放在androidTest导致应用Hilt组件不实现，改复用Debug入口后通过。首批截图又发现排序回读视口问题，修复并增加新首项可见断言后通过，失败证据保留。真实宿主仅创建并按ID/唯一名前缀回收本次两对资源；仓库测试使用隔离Room/目录，不访问网络。
- **配置与制品保护**：`before.log`/`after.log`首页各1/1，正式Profile count2/missing0/expected2，文件名/大小/mtime保持。原1.yaml双端SHA-256仍`bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。App`e2eeb9cefdcfa33073afbab0db7fdf4c10a81120253a0ccdb23229268e0ada70`、Test`d844d89620ca3c06c061fdbbec00f139a376a8abda24792a4a1dcc43ee610749`均匹配设备；App仅arm64，见`artifacts.json`。`images2/`27张与`contact-final-0/1/2.jpg`已目视，含键盘/滚动态；图标故障回退来自故意不可用的回环测试URL，不作为真实外部图片加载证明。结束无应用服务、connectivity未见tun0、相机Active Clients空，恢复自动旋转=1。
- **扫码同包复验**：真实相机打开/手电筒/后台释放与恢复、离线QR解析、系统相册选图到确认表单、预填且确认前零提交、拒权/故障动作五项均在最终安装包通过。相册临时图片核对后回收，没有下载订阅或改正式Profile；镜头实码识别、系统拒权弹窗和真实网络导入仍待验。整体目标PARTIAL，下一阶段继续策略组节点菜单/添加本地等剩余链路；未提交/推送。

### Provider 结构化资源／选项／过滤成员整页（2026-11-03，DONE-VERIFIED 首版）

- **上游与范围**：参考本地只读Flutter基线`7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`的`lib/views/profiles/custom/{providers,members}.dart`、`lib/views/config/providers.dart`与过滤表单；未fetch/merge，Go未改。CUSTOM只列组use、规则/扩展RULE-SET及DNS/Sniffer引用，普通配置列全部定义；缺失资源可见、可搜索重绑，稳定ID与配置内别名分开保留。
- **整页交互**：独立代理选项页支持十项设置按需添加/默认值/移除、数字和URL校验、IP版本选择、过滤/排除成员混排和上下移动、预设撤回与自定义追加、显式清空/继承。混排元数据不进入核心；选择按操作顺序重放。快捷YAML与选项均有未保存退出确认；保存失败保留草稿，成功回读revision后返回资源列表，父页不重复显示子页错误。
- **保存与暂存**：ViewModel generation丢弃关闭后的迟到读取/校验，同步saving认领抑制重复提交。CUSTOM元数据only不写源文件/mtime，定义变化用文件原子替换+Room事务，前后运行守卫失败回滚；普通配置选项也在事务内读取最新覆盖并仅替换Provider，保留DNS/脚本元数据。暂存合并支持中文/转义名称、flow/引号分区和内联健康/override map，保留未知字段；健康检查启用时补Flutter默认测速URL。原1.yaml行内资源map与空map编辑兼容，分区解析隔离脚本codec默认map。
- **统一构建**：`./gradlew :app:testDebugUnitTest :service:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`通过，App364、Service147项，失败/错误/跳过0；Lint0errors/59warnings/9hints。最终生产`/tmp/kclash-provider-pages-20261103-build5.log`；仅测试草稿输入修正后`build6.log`测试包与Lint通过。Core/Common/Go、Release、VPN/浏览器未重跑。
- **真机**：Redmi `wsvwypiz7xwslvl7`、API36、arm64、1080×2340竖屏默认字号，最终`device3.log`精确8/8。新页面三方法、隔离仓库一方法、真实宿主一方法及旧页面三方法覆盖默认/校验/失败重试/移除、混排/清空/返回保护、缺失绑定/快捷YAML、元数据only正文/mtime、过期拒绝/事务后置回滚、最新其他覆盖保留、真实MainActivity保存重开/Activity重建。真实宿主只创建并按ID/类型/唯一名称回收自身临时CUSTOM，保留既有Profile和当前选择。
- **保护与制品**：同前缀`before.log`/`after.log`首页和正式Profile保护各1/1，count2/missing0/expected2，原文件名/大小/mtime一致；原1.yaml双端SHA-256仍`bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。App`f9922d3672a24b2746fe9dce31c66b0cf404ab2aea339c8561dfe196813d710d`、Test`6d1d8add6cfcdad04d05c5e82f884498e7557b3713f0e1dc2688ed9a9267e1dc`均匹配设备，见`artifacts.json`；App仅arm64。`images/`11态与`contact.jpg`已集中目视，含键盘/滚动态，不宣称Flutter像素一致。结束应用无服务、connectivity未见tun0、相机Active Clients空，恢复自动旋转=1。
- **失败与边界**：首批`device1.log`4/8，暴露原1.yaml行内规则map拒绝和picker追加顺序问题，以及测试子串/滚动定位问题；`build4.log`新增单测暴露codec默认map混入分区，隔离后`build5.log`全过。`device2.log`7/8，剩余测试按光标插入注释破坏YAML，改完整草稿替换并断言回调次数后`device3.log`8/8。截图提取首轮未启采集参数，未获得图片，最终批次显式开启后11张；所有失败保留。此为整页首版，运行态/外部Provider下载、超长列表拖动、资源批量/排序/推荐及完整Profiles/UI仍PARTIAL；本轮及前序代码未提交/推送。

### 配置页实时二维码扫描导入（2026-11-03，已实现并通过定向验证）

- **Provider 当时安装版扫码复验（2026-11-03）**：用户再次反馈扫码缺项后，核实正式入口与上游 `lib/pages/scan.dart`，现有实现已接通，无需重复修改生产代码。当时 Redmi 安装版精确五项5/5：相机/手电筒/前后台释放、离线识别、真实系统相册到预填表单、确认前零提交、拒权/故障恢复动作；首页前后各1/1，正式两份Profile保留。证据前缀 `/tmp/kclash-qr-current-20261103-`，两张截图已目视。App/Test SHA分别为 `83d9ac105c8006fc0433d644d6e06e43a71c959b528f745acfeb283f2a3a99d1` / `c4e0f6d44a8a113c0c483929f2a9f3c295fecea6995b7e151203f1e4d4cdd038`，均与本地APK一致；本轮未重建或重装。结束相机Active Clients空、无应用服务，旋转恢复1。镜头对准实体二维码、系统拒权弹窗及扫码后的真实订阅下载仍待验。
- **当时安装包复核（2026-11-03）**：确认正式入口为“配置 → ＋ → 扫描二维码导入”，现有安装包已包含相机、相册和手电筒，无需重复实现。Redmi 竖屏默认字号精确五项再次全部通过：真实相机开关/手电筒/前后台释放、离线识别、真实系统相册选图到预填表单、确认前零提交及拒权/故障恢复操作。证据前缀 `/tmp/kclash-qr-recheck-20261103-`，`results.json` 为5/5，`before.log`/`after.log`首页各1/1、正式Profile count2/missing0/expected2；两张截图已目视。安装App/Test哈希分别为 `ea6e9ecf48649d498b1d348c4e535e183574a869e93795eca4b29ca507021064`、`c3a7debf701c3ae9bc06265552de78b55439986b61f87d6bbb1854fd34802439`，对应前序已验证构建；本次未重新构建未完成的更多设置改动。原1.yaml双端哈希保持，结束相机Active Clients空、无应用服务、自动旋转恢复1。镜头对准实码、系统拒权弹窗和真实订阅下载仍待验，不将表单或图片识别推广为这些路径通过。
- **后续补验（该切片）**：系统相册选图→真实离线ML Kit→扫码页一次交付→预填订阅确认表单已闭环，`ProfileQrScannerInstrumentedTest#systemAlbumReturnsQrToConfirmationWithoutImporting`通过；未点击导入、访问测试URL或增加Profile。测试仅插入自己的二维码PNG到下载目录，退出后按URI和名称核对回收。最新日志前缀`/tmp/kclash-qr-final-20261103-`：`build2.log` App355、Service145项全过，Debug/AndroidTest/Lint通过（0 errors/59 warnings/9 hints），`build5.log`为最终测试包。`device2.log`原四项通过，`device5.log`新增相册1/1，合计五个不同精确方法最终通过。
- **本次保护与修正**：设备当前已是2份Profile，前置快照count2/missing0；前置首页和首批`device1.log`受自动旋转横屏影响，竖屏守卫拒绝继续，随后临时锁为竖屏默认字号，`after.log`首页1/1、count2/missing0/expected2，原文件名/大小/mtime与前置一致。相册`device2–4.log`因系统搜索框资源前缀、搜索框与结果文件同名、当前目录限制的测试定位问题失败，最终限定下载目录后通过，失败记录保留。扫码收尾同时恢复Provider整理时误删的`formatBytes/profileSubtitle`，修复Provider默认空map引起的分区嵌套，并增加内联/引号分区与注释保护两项单测；Provider整页本阶段专项仍未完成。
- **扫码补验时制品与边界**：App SHA-256 `8173fb966ea28832e1de4287cfcb2de8931feef194d08807eb77a9a1ee96dd7f`，Test `c0e12330a3ec3fa388d92c6ce4f069b5534cf88785847221980a9f2defe7b0f2`，当时与设备安装包一致；最新同包13/13及安装版见上方资源排序/推荐切片。App仅arm64，原1.yaml双端哈希不变，见`artifacts.json`。`camera.png`/`album-confirmation.png`已目视；`camera-state.log` Active Clients为空、`services.log`无应用服务。镜头实码识别、系统拒权弹窗及真实订阅下载仍待验。下列为首次扫码切片的历史构建与四项验收记录，后续证据以上述为准。未提交/推送，整体目标保持PARTIAL。
- 参考 Flutter `lib/pages/scan.dart` 的相机/相册、重复回调抑制、权限拒绝与前后台释放行为。配置添加来源改为“扫描二维码导入”，全屏提供取景框、关闭、相册与手电筒；拒绝权限或相机故障可重试、进设置或继续选择图片。
- 最终测试代码`lint-final.log`通过，0 errors/59 warnings/9 hints，`git diff --check`通过；测试结束已恢复原自动旋转设置。
- CameraX 1.6.2 + 既有 bundled ML Kit，无在线识别依赖；只识别 QR。连续帧丢弃积压，离页/暂停释放本会话镜头和识别器；迟到结果不重复交付。HTTP(S) 与 Clash 安装链接共用解析，保留名称，识别后预填现有表单，确认前不下载、不创建 Profile，支持继续填写 age 密钥。
- **构建**：根 Wrapper `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 最终生产构建通过，App353项失败/错误/跳过0，Lint0errors/58warnings/9hints（新增1条toUri风格建议）。最终生产日志`/tmp/kclash-qr-20261103-build2.log`；随后只调整测试等待顺序，`build3.log`测试包构建通过。Service/Core/Common/Go、Release与浏览器网络未重跑。
- **Redmi定向验证**：API36、1080×2340竖屏、字号1.0。`device2.log`四个精确方法4/4：真实相机打开/手电筒/退后台释放/恢复重新打开/退出释放，bundled ML Kit识别固定安装链接和拒绝无关QR，预填名称/URL且确认前零提交，拒权/故障恢复动作。首批`device1.log`3/4，恢复前台时直接等待系统状态未推进Compose测试时钟，调整为先等待界面恢复再查系统相机后4/4；生产代码未因该测试失败修改。真实镜头对准实码、系统相册选图完整路径、系统拒权弹窗及真实订阅下载尚未验收，图片识别与表单测试不能代替这些E2E。
- **保护与制品**：`before.log`/`after.log`首页及正式Profile保护各1/1，count1/missing0/expected1，原文件元信息不变；授权原`1.yaml`双端SHA-256仍为`bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。App`a44adfe92d062bbc986a78ecebb4a83281383dfe775ceffc6ccafd770c2f93d8`，Test`a3e1f6487af9c3162da12a8fbf458b7b75c00022c5ee745009e8c5a68a3cfaa9`，均与设备安装包一致；App仅arm64，Test无native。`artifacts.json`、`camera-final-state.log`同上述前缀，结束相机Active Clients为空、应用无运行服务。`camera-final.png`已目视，不能作为Flutter像素一致或镜头实码识别证明。未访问订阅、修改正式Profile或清数据；代码未提交/推送。整体Profiles/UI仍PARTIAL，后续回到Provider结构化整页。

### 过滤预设、批量资源 URL 与资源偏好备份首版（2026-11-03，DONE-VERIFIED 首版）

- **上游行为**：参考 `lib/views/config/filters.dart`、`lib/features/form/filter.dart`、`lib/views/profiles/custom/members.dart`、`lib/views/config/providers.dart` 和 `lib/providers/actions/clash_providers.dart`。先整体接通这一组页面，再集中测试，不将局部页面通过等同完整 parity。
- **过滤预设**：增加全局名称/表达式有序目录和整页搜索、增改、批选删除及排序；名称trim/非空/唯一，保存复用原生regexp2。策略组filter/exclude-filter与Provider过滤覆盖可追加尚未完整应用的预设，保留自由条件，配置保存实际表达式而非名称引用。Provider区分未设置与显式空值，过滤写在Provider层而非节点override层。
- **批量资源**：规则资源与图标集URL入口接共用整页表单，单URL可选名称、多URL按文件名分配重名后缀。整批先验证，最多128项、四并发；已有URL跳过，成功保留，失败/取消项独立重试，摘要不显示原始URL。下载/编译在目录锁外，最终提交再核对URL，规则只回收本次未提交暂存。
- **备份v5**：全量归档增加`resource_preferences.json`，携带图库/过滤/Geo；仅配置恢复不改这些偏好。兼容v1–v4，无新条目的旧归档即使全量覆盖也保留现有新偏好。兼容恢复按图库ID/过滤名称合并，Geo采用归档值。结构/容量与生产核心过滤校验在写入前；Room或Geo持久化失败时恢复旧值。Geo变化使管理器加载缓存失效，事务内不反向调用核心。
- **统一构建**：App352、Service145、Core35，共532项，失败/错误/跳过0；Debug/AndroidTest和Lint通过（0 errors、57 warnings、9 hints）。统一命令为`./gradlew :app:testDebugUnitTest :service:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`。日志统一前缀`/tmp/kclash-filter-resources-20261103-`；最终生产代码构建见`build4.log`，随后仅修正Provider测试选择器，`build5.log`的AndroidTest与Lint通过。
- **精确真机**：Redmi `wsvwypiz7xwslvl7`，API36、1080×2340竖屏、字号1.0；`device1.log` 10/10，扩展`device2.log` 12/13，修正后`device3.log` 2/2，合并最终14个不同精确方法通过，详见`results-final.json`。覆盖原生regexp2/旧快照保护、批次四并发JVM及仓库并发URL去重/未提交暂存回收、全量/仅配置/旧v4/坏归档/核心拒绝与Geo失败补偿、过滤/导入/图库深浅页面、Provider预设，以及真实MainActivity入口/Activity重建。仓库使用隔离Room/受控下载，不能推广为真实外部网络导入E2E。
- **视觉与制品**：`images/`共17张，`contact-final.jpg`与Provider画面已目视；含键盘和滚动态，不声称Flutter/Compose像素等价。App SHA-256 `ae867f91661653013e83c53dc61b546664b45ef515dd8c7bdace8b2bd92181d3`，AndroidTest `922acc6228ef8eefaa9f296fecbd4227136234fa3419d6fd6965deb68e350298`，均与设备base.apk一致；App仅arm64-v8a，测试包无native，见`artifacts.json`。
- **保护与失败记录**：`before.log`的正式Profile元数据快照count1/missing0成功，但首页因继承运行状态未显示“启动核心”而失败；随后从页面停止核心。`device2.log`真实入口后置快照、`device3.log`首页后置快照均count1/missing0，最终首页1/1通过。Provider用例因过滤/排除两区同名预设选择器歧义失败，限定过滤区后通过；生产逻辑未因此修改。原`1.yaml`两端SHA-256仍为`bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`，结束系统服务列表为空，connectivity未见tun接口。`adb ip link`无权限，未用该失败证明无TUN。未清数据、删除正式Profile或新增在线订阅导入；浏览器及运行态资源网络回归未重跑。
- **范围**：真实外部资源网络导入、系统文件选择器、运行态资源更新、过滤成员页完整选择/移除动画、边缘排序与完整主题仍待补；整体资源/Profiles/UI继续PARTIAL。本轮及前序改动未提交/推送。

### 全局本地代理、配置引用与链式代理整页首版（2026-11-03）

- **上游依据**：沿用本地 Flutter `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`，对照 `views/profiles/custom/custom_proxies.dart`、`dialers.dart`、`models/clash_config.dart`、`enum/enum.dart` 与 `providers/state/custom.dart`/`database.dart`；本轮未 fetch/合并 Flutter，Go 未修改。
- **页面和节点**：工具→自定义资源→本地代理，新增搜索、空态、选择/批删、上下拖动排序、协议类型/必填字段/按需选项、密码隐藏、列表/map 子编辑和分享链接快捷编辑。28种类型及85个字段沿用上游；未知字段保留、网络/插件变化清理不再适用的字段。代理页节点长按已有“添加到本地代理”入口，但真实菜单到正式目录保存的 E2E 尚未完成。
- **引用和保存**：目录使用 Room AppPreference 保存 UUID+完整定义，有容量上限，不改 schema。CUSTOM 只保留名称引用，运行时仅投影策略组显式引用的节点；旧配置内联节点优先，不因新增同名全局节点改变原出站。表单改名同步组成员、规则目标、选择及配置独有 Dialers/DNS/NTP 目标，跳过旧同名组/内联节点；多文件/Room 共同提交，失败恢复字节与 mtime，过期目录或正文拒绝覆盖。删除保留失效引用并提示使用配置；退出管理页经唯一管理器尝试应用当前引用配置。
- **配置页面闭环**：概览统计、规则目标候选和组成员候选均读取全局投影，源正文不物化节点。概览“代理”进入链式代理列表；逐项选择本地节点/策略组或“无”，过滤自身、经其他节点/组及无过滤 include-all 形成的循环，失效目标可见。保存合并最新其他分区覆盖并留在列表；快捷映射编辑仍保留。读取有 generation，保存复核节点及运行状态。
- **备份 v4**：增加 `local_proxies.json`，全量携带全部节点，仅配置携带引用依赖，兼容 v1/v2/v3。不同 ID 同名自动改名并只改归档内 CUSTOM 引用；同 ID 在备份后改名时为旧名分配独立 ID，保护归档外配置。旧正文无节点目录时原样恢复/再导出；清单只在确有当前选择时声明该条目，全量覆盖空选择会清除旧 ID。
- **统一构建**：根 Wrapper `:app:testDebugUnitTest :service:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 最终通过。App **345**、Service **143**、Core **35**（共523）项，失败/错误/跳过0；Lint **0 errors、57 warnings、9 hints**。最终 `/tmp/kclash-local-proxies-20261103-build9.log`，此前 build5–8 也通过；Common/Go、Release、VPN/浏览器未重跑。
- **真机证据**：Redmi Note 8 Pro/API36/arm64/竖屏1080×2340/字号1.0，分批按最后一次结果 **15/15**；`results-final.json` 与 `methods2.txt`/`methods5.txt` 同 `/tmp/kclash-local-proxies-20261103-` 前缀。`device4.log` 为11/13，最后仅重跑受影响6项，`device5.log` **6/6**。覆盖隔离仓库导入复用/改名回滚/过期目录/全局投影/备份后改名叠加同名冲突/删除缺失保留、真实 Go validate/分享编解码、深浅协议表单/删除/返回、链式选择过滤/清除、真实 MainActivity 映射保存重开、旧概览及四项备份回归。表单受 IME 影响的添加设置项使用自动化点击，选择页仍使用触摸，不将其推广为全部触摸路径验收。
- **真实核心范围**：`LocalProxiesHostInstrumentedTest#stoppedMihomoProjectsGlobalNodesAndRefreshesRenamedReferences` 由授权 `1.yaml` 原字节规划全局节点，保留原资源定义，真实管理器加载并验证全局节点/改名后的组成员刷新；始终 STOPPED，恢复选择和原目录，仅回收本次临时配置。完整导入的远程规则下载曾超时，该失败保留，不算完整远程导入已验。
- **配置/制品/视觉**：正式 Profile before/final-after 各1/1，count1/missing0/expected1，原正文元数据保持；原1.yaml两端 SHA-256 均 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。最终无服务、无tun0，不清数据、不访问订阅。App SHA-256 `82dc77fc93e0d296c4e0881070cbd0b1f016c899886ff73886348eaf37011021`，Test `ac12ed39afa066e3d657ce0803493e75f08c0dc06e778b904ac5e3ec7a292664`，均与设备一致；App仅arm64，Test无native，记录 `artifacts.json`。`images-final/` **19态**集中目视，无明显重叠裁切；保留键盘可见态，不宣称 Flutter 像素等价。
- **失败与边界**：原 build1 的重复旧进程已 TERM，不能算成功；build3 的测试 matcher 编译失败已修。device1 6/8、device2 10/13、device3 12/13、device4 11/13 的失败日志保留：删除确认层级/转场等待、IME坐标、旧正文解析及空选择清单均已修正或调整对应测试。分享链接的复杂保留/替换流程只有实现和 native codec 分项证据，尚缺完整 VM/E2E；长列表边缘自动滚动、排序/所有协议实际保存、运行态全局编辑/失败应用、菜单正式保存、链式页精确视觉与并发覆盖仍待继续。当前为整页首版，整体目标继续 **PARTIAL**，本轮及前序资源代码未提交/推送；下一阶段继续过滤预设、批量资源和备份等完整页面链路。

### 资源更新总览与图标集首版（2026-11-03）

- **上游基线**：本地 Flutter `upstream/main` `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 的 `views/resources.dart`、`providers/actions/{resources,geo_resource,icon_sets}.dart`、`views/config/icon_sets.dart`、`models/{common,clash_config}.dart`；未 fetch 或合并 Flutter。
- **整页与入口**：工具“资源”进入独立更新总览，含自动更新开关、小时周期、MMDB/ASN/GEOIP/GEOSITE、远程图标集/规则/脚本、大小或图标数量、更新时间、单项/全部更新、长按同步/复制链接、Geo 地址编辑/恢复默认及失败汇总。自定义资源继续承载规则/脚本管理，并新增图标集管理入口。
- **真实核心链路**：Bridge 解析 `geoUpdate` 类型事件；管理器在请求前订阅，受理后保持更新中，以完成/失败/跳过事件结束。下载不持启停锁，停止 VPN 时继续保留正在下载或自动更新所需的事件监听；成功更改 Geo 后作废路由缓存。设置持久化并补入暂存/预览输入，不回写 Profile；热更新失败尝试恢复原核心设置与暂存输入。
- **自动更新与文件保护**：Geo 复用核心 updater；WorkManager 每 20 分钟扫描到期远程图标/脚本，运行时才扫描规则，使用同一全局小时周期及网络/电池约束，不为后台更新启动 VPN。规则更新后尝试应用当前引用配置；脚本对齐上游只同步资源，下一次显式应用时执行。脚本同步新增版本复核、文件 mutex 和 Room/正文回滚，拒绝过期下载覆盖编辑或复活删除项。
- **图标集**：支持 Quantumult X/Loon JSON、URL/文件导入、同源复用、稳定 ID、重命名/改源、同步、上下移动、删除确认、搜索预览及复制图标链接；组图标 picker 接入图库名称与图标。无效/空图库不覆盖旧目录，下载后复核源/版本并保留并发改名；目录与输入有容量上限。
- **集中验证**：App **334**、Service **143**、Core **35** 项 JVM 测试全部通过（失败/错误/跳过 0），Debug/AndroidTest/Lint 成功；Lint 0 errors、57 warnings、9 hints。最终生产构建 `/tmp/kclash-resource-overview-20261103-build3.log`，首次 build1 因依赖注入参数放入相邻 provider 而编译失败，修正后 build2/build3 成功。Common/Go、Release、VPN/浏览器网络本轮未重跑。
- **真机最终记录**：Redmi Note 8 Pro `wsvwypiz7xwslvl7`，API36、arm64、1080×2340 竖屏、字号1.0；首批与最终精确批次均 **10/10**。覆盖新总览、图标集/组图标选择、隔离仓库过期/删除/改源/非法 JSON、脚本并发保护、正式工具入口、旧规则/脚本/成员picker。真实 MMDB 回环下载使用现有数据库原字节验证跳过成功，再返回无效数据库验证失败且旧字节不变；总览 StateFlow 已重读文件 size/mtime，WorkManager 任务注册已核实，恢复原 Geo 设置/mtime，未启 VPN。方法 `/tmp/kclash-resource-overview-20261103-methods.txt`、最终 `/tmp/kclash-resource-overview-20261103-device2.log`；测试增量 testbuild4/testbuild5 成功。首批转场截图未用于最终验收，补动画等待后总览/混合远程/错误/Geo表单/图标集/组picker **14 个深浅态**已集中目视，无明显重叠裁切，最终图目录 `/tmp/kclash-resource-overview-20261103-images2/`；总共23张另含旧页面回归。
- **正式配置与制品**：before/after 各 **1/1**，count1/missing0/expected1，原正文文件名/size/mtime 相同；日志同前缀 `before.log`、`after.log`。原 `1.yaml` 两端 SHA-256 均 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`，未改写正式 Profile、未访问订阅、未清数据，最终服务为空、sysfs 无 tun0。App SHA-256 `4076cb3f9cf9946e14d0faeead78242c7e0605d74e993799070532d570ea7a69`，最终 AndroidTest `3f72464f444a737d467be506625c4bf9521da4cbe13890e9dc0058365df18248`，均与设备安装包一致；App 仅 arm64-v8a、测试包无 native，记录 `/tmp/kclash-resource-overview-20261103-artifacts.json`。
- **边界**：资源页与图标集为整页首版，整体 UI/Profiles 仍 **PARTIAL**。图标集拖动排序/推荐、图标集与 Geo 设置备份、真实系统 JSON 文件选择器 E2E、周期到期的后台真实下载、外部资源站点下载、运行态资源更新、无有效配置时 Geo 手动下载及精确视觉/动效仍待继续；目前 Geo 手动下载复用有效配置加载，不为无配置用户构造替代 YAML。
- 本切片 **DONE-VERIFIED 首版**，不代表完整资源/Profiles/UI完成；下一阶段继续全局本地代理与配置引用等整页还原，做完整版后集中验收。本轮及前序代理资源改动尚未提交/推送。

### 全局代理资源与非 CUSTOM 配置映射首版（2026-11-03）

- 沿用本地 Flutter `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 的 `providers/state/profile.dart`、`actions/setup.dart`、`state/custom_import.dart` 与自定义配置成员/资源页面：非 CUSTOM 配置同时作为代理资源，组通过 `use` 引用；`include-all-providers` 只消费已有显式引用的资源，不注入全部配置。
- Provider 页补配置资源选择、搜索、缺失提示与原别名修复；策略组成员选择接入普通订阅/文件配置，并随策略组原子保存稳定 `kclash-profile:<id>` 绑定。资源改名保留配置内别名；重复显示名使用唯一别名；缺失资源先修复再编辑字段。删除配置提示仍在使用它的自定义配置；概览核对稳定 ID 是否存在。
- 运行输入将引用解析为 Mihomo home 内按内容哈希保存的完整源配置副本，不执行资源配置的脚本/DNS 扩展，不回写两端源文件。节点源更新改变输入指纹，旧副本保留以保护已应用版本；未被组显式引用的绑定不加载。仅配置备份原有全量 Profiles 归档保留稳定 ID 依赖。
- CUSTOM 导入确认后才下载新代理资源；远程按 URL 与显式 age 密钥复用已有非 CUSTOM 配置，本地 inline 资源登记为文件配置，重名同步改写 `use`/`use-position`。保留配置独有健康检查/过滤/节点覆盖；原节点凭据不做全文替换。代理资源文件、Room 记录、规则资源登记和 CUSTOM 正文共享提交/回滚；下载/编译位于文件锁外，提交时复核全局配置快照、正文及运行/会话守卫。没有可读 payload 的外部 file 定义会明确拒绝，请先导入该资源再绑定。
- **统一验证**：根 Wrapper `:app:testDebugUnitTest :service:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 最终通过。App **331**、Service **139**、Core **34**，失败/错误/跳过均 0；Lint **0 errors、56 warnings、9 hints**。最终日志 `/tmp/kclash-proxy-resources-20261103-build5.log`；Common/Go 单测、Release 和 DNS/浏览器网络本轮未重跑。
- **Redmi 验收**：API36、arm64、1080×2340、竖屏字号1.0，精确方法最终 **14/14**，失败/跳过0，方法清单 `/tmp/kclash-proxy-resources-20261103-methods.txt`、最终 `/tmp/kclash-proxy-resources-20261103-device2.log`。覆盖深浅资源缺失/搜索/修复、策略组 picker 及 use+稳定绑定共同保存、旧策略组/Provider页、隔离仓库同源复用/重名/use-position/导入后守卫回滚与新文件回收、profiles-only备份依赖恢复和四项旧备份回归。
- **真实核心**：正式 MainActivity/管理器加载授权 `1.yaml` 的临时副本，真实 file Provider 展开全部原节点；资源改名不改变投影，资源删去一个节点后后台 getProxyGroups 读取新版本，真实组成员减少1。核心始终 STOPPED，未启监听/ProxyService/VPN；最后恢复原选择，只回收核对 ID/名称的临时配置。仓库 downloader 是受控 fake，不能推广为真实远程 HTTP/加密下载验收；真实运行态资源热更新仍待验证。
- **保护与制品**：正式配置 before/after 各1/1，均 count1/missing0/expected1，文件名/size/mtime相同，日志同前缀 `before.log` / `after.log`；原1.yaml本机/设备 SHA-256 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898` 未变。最终无服务、sysfs无tun0，未清数据或删除正式配置。App SHA-256 `db18ef334ffc322a7eeaa0c317cc14cb9924d275ba80e365874856b8e7ef7191`，Test `cb7b059108ee8badc809abcced6c5b8703b055345b56fcb87c6ff8106ffadc98`，均与设备base.apk一致；App仅arm64-v8a，test无native，记录 `/tmp/kclash-proxy-resources-20261103-artifacts.json`。
- **视觉与失败记录**：最终截图 `/tmp/kclash-proxy-resources-20261103-images/`，新缺失/选择/绑定深浅6态和成员选择1态已目视，无遮挡；搜索态键盘正常，未更新旧像素基线。首批14项为13通过/1失败，成员选择按文本定位同时命中搜索框与候选行，新增候选行testTag后最终14/14；build2的旧单行catch触发Lint SuspiciousIndentation，改明确条件块后build3/4/5成功。保留中间日志，不将首批记为全绿。
- 当前切片 **DONE-VERIFIED 首版**，完整资源/Profiles/UI仍 **PARTIAL**。全局本地代理注册表、Geo/图标/脚本更新总览、过滤预设、批量资源 URL、拖动/缓存回收、真实远程代理导入与运行态资源更新继续排队；下一阶段继续整页还原，再集中验收。本轮代码尚未提交/推送。

### 全局规则资源管理、配置引用与导入映射首版（2026-11-03）

- **上游基线**：沿用本地 Flutter `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 的 `views/config/providers.dart`、`views/profiles/custom/providers.dart`、`providers/actions/clash_providers.dart`、`providers/state/custom_import.dart`，本轮未 fetch 或合并 Flutter。全局规则资源是独立注册表，代理资源在 Flutter 中来自非 CUSTOM Profiles，后者本轮尚未迁移。
- **整页与入口**：工具“资源”当前进入规则资源首版；“自定义资源”增加规则资源与既有脚本库入口。规则管理页补新建、单 URL/文件读取入口、搜索、编辑/只读预览、选项、单项/全部远程规则同步、上下移动、引用删除提示和草稿退出确认。MRS 读入保留原始字节，远程资源正文只读；实际文件读取复用 8 MiB reader，HTTP 下载改为同一有界读取。当前导航“资源”仍不是完整 Flutter Geo/图标集/脚本更新总览，自定义资源页也尚未齐备所有上游入口。
- **核心与数据**：`RuleResourceRepository` 使用 Room AppPreference 保存有界全局元数据，私有 UUID 文件保存原文和编译 MRS。通过 `CoreRuntimeManager` 的初始化/串行边界复用既有 Go `compileRuleSet`/`dumpRuleSet`，识别行为与格式；停止态不启动服务或监听。运行输入对 domain/ipcidr 使用编译 MRS，classical 与原生 MRS 使用原格式。原配置保存稳定资源 ID/别名，`toActiveProfile` 生成按内容哈希命名的投影文件，资源改名/同步不回写原配置。资源保存后仅当前引用配置进入统一 apply；旧缓存/投影暂保留以保护已应用版本，缓存回收仍待补。
- **引用与导入**：CUSTOM Provider 页新增全局资源选择和缺失引用修复，保留原 YAML/覆盖编辑。绑定按配置别名选择，可在全局改名后保持引用。`saveCustomProviders` 将正文/代理覆盖原子提交，复核正文快照及启停/运行守卫，保留最新 DNS/脚本元数据。CUSTOM 配置导入确认后才下载/编译/登记规则资源，同 URL 复用，重名分配新标签，并改写普通/嵌套 RULE-SET、DNS/Sniffer rule-set 引用；代理密码和 URL 不做全文替换。资源登记与配置正文共享 Room/文件事务，编译和下载在文件锁外，过期/取消/运行状态变化失败均保留旧文件与目录。
- **备份**：归档 v3 携带资源元数据、原文及所需 MRS，兼容读取 v1/v2；“仅配置”也带引用依赖。恢复按稳定 ID 合并，重名仅调整全局显示名；原生规则、脚本、选择及 v1 恢复回归一起验收。不备份运行投影或旧缓存，不将源文件中的真实路径带到另一台设备。
- **统一构建**：根 Wrapper `:app:testDebugUnitTest :service:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 成功，App **328**、Service **139**、Core **34**，失败/错误/跳过均 0；Lint **0 errors、56 warnings、9 hints**。最终 `/tmp/kclash-resources-20261103-build6.log`。Common/Go 单测、Release、DNS/浏览器网络本轮未重跑。
- **真机**：Redmi API 36、1080×2340、竖屏默认字号 1.0，最终精确 **10/10**，方法清单 `/tmp/kclash-resources-20261103-methods.txt`、日志 `device3.log`（同前缀）。新页面深浅主题搜索/菜单/编辑/取消/删除、缺失引用绑定、隔离 Room 原子保存/导入提交后守卫回滚、过期同步拒绝、资源投影与备份恢复通过；既有 Provider/CUSTOM 导入及四项备份回归通过。真实 MainActivity 打开两处入口，真实 Go 完成 Text→MRS、MRS 识别/预览与非法规则拒绝，核心保持 STOPPED。原授权 `1.yaml` 只用于隔离配置原字节，未访问真实订阅或修改正式 Profile。
- **证据与边界**：正式 Profile before/after 各 1/1，count=1/missing=0/expected=1，文件名/size/mtime 保持；`before2.log`、`after2.log`、`artifacts.json`、`images/` 均为 `/tmp/kclash-resources-20261103-` 前缀。两包设备哈希相同、App 仅 arm64；原 `1.yaml` 两端哈希不变，结束无服务/tun0。10 态截图集中目视，仅证明本地整页布局，不宣称 Flutter 像素等价。URL/同步仓库回归用 fake downloader，事务回归用 fake compiler；真实 Go 转换另有正式宿主证据，尚未做真实运行态资源更新、真实远程规则下载或规则文件系统 picker E2E。
- **失败记录与后续**：首次快照筛选误用了 `.ui.MainActivityInstrumentedTest`，ClassNotFoundException，改实际包名后 1/1；首批 5/6，缺失引用测试宿主未提供布局容器导致点击落在重叠节点上，补实际布局后 device2、最终 device3 均 10/10。build1–6 均成功，保留首批失败日志。规则资源与引用首版 **DONE-VERIFIED**；全局代理/非 CUSTOM Profile 映射、批量 URL/排序手势、Geo/图标集更新总览、全局本地代理/图标集/过滤预设、缓存回收、父页逐项持久化和完整资源/Profiles/UI 继续 **PARTIAL**，下一阶段继续资源与配置整页剩余链路。

### 策略组批量操作、问题提示与图标选择首版（2026-11-03）

- **上游基线**：本地 `upstream/main` `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`，亲读 `profiles/custom/groups.dart`、`icon.dart`、`providers/state/custom.dart:proxyGroupIssues` 与 `features/form/filter.dart`；本轮未 fetch/合并 Flutter。保留既有列表显式保存，单组从概览进入仍直接保存返回；完整逐项持久化 parity 另待收敛。
- **列表与问题**：增加常驻选择框、批选/全选/退出、批量复制和删除；返回先退出批选或排序。副本按源组相邻插入，分配独立 key、清空 originalName 并规避保留/代理/批内重名，原引用不误映射到副本。删除确认提示仍被规则、组成员或链式代理引用的目标，保留失效引用供后续修复。`ProxyGroupIssues` 在列表按钮/详情和单组横幅区分缺失成员/资源警告与名称、无来源、循环引用、非法回退等阻断项；单组确认只检查当前草稿，便于逐个修复旧配置。
- **过滤与保存**：Kotlin Bridge 接入已有 Go `validateFilters`，复用 Mihomo regexp2 与反引号分段语义，不替换成 Java 正则。列表/表单防抖检查，表单确认与 ViewModel 最终保存再次校验；异步结果只提交同一 JSON 快照，草稿变化后提示再次确认，关闭后协程取消。真实核心支持 lookbehind 并拒绝分段中的未闭合表达式。原文件 mutex/正文快照/运行守卫与 Room 原子保存继续沿用。
- **图标**：列表/表单/独立选择页使用 Coil 3.6.3 位图与 SVG 管线，默认首字符回退、加载失败状态、当前图标移除、搜索、HTTP(S) 地址和本地图片入口接通；地址拒绝非 HTTP(S)、内嵌认证及超长输入。本地 reader 经 ViewModel 有界读取，PNG/JPEG/GIF/WebP/SVG 签名识别、128 KiB 上限，嵌入 data URI；最近记录复用 Room 偏好表，最多 24 条/512 KiB，不新增 schema。最近记录删除不改已应用图标。仅使用已配置/最近图标，不预置图标集。官方 Compose/SVG/network 文档经 smart-search 抓取，地址为 `https://coil-kt.github.io/coil/compose/`、`/svgs/`、`/network/`，证据 `/tmp/kclash-coil-*.md`。
- **统一构建**：根 Wrapper `:app:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过，App **324**、Core **34**，失败/错误/跳过 0，Lint **0 errors、56 warnings、9 hints**；生产最终 `/tmp/kclash-group-parity-20261103-build4.log`。之后只补迟到校验测试，`assembleDebugAndroidTest :app:lintDebug` 增量 `/tmp/kclash-group-parity-20261103-build5.log` 成功。Service/Common/Go 单测、Release 与 DNS/浏览器网络未重跑。
- **真机与视觉**：Redmi Note 8 Pro `wsvwypiz7xwslvl7`，API 36、1080×2340 竖屏、字号 1.0，最终精确 **12/12**；方法 `/tmp/kclash-group-parity-20261103-methods.txt`，日志 `device2.log`（同前缀）。包含批选/复制/批删/退出、警告保留、真实核心过滤/迟到保护、图标搜索/移除/地址校验、回环 HTTP PNG/SVG 成功与 404 回退、隔离 Room 最近记录及原有成员/排序/YAML/真实 MainActivity 保存重开。仅真实宿主创建并回收核对 ID/名称的临时空 CUSTOM；没有访问订阅或启动核心监听/VPN。最终 `images/` 共 18 态（同前缀）已集中目视，无明显裁切/重叠，不宣称 Flutter 像素 parity；图标搜索截图保留键盘可见态。
- **保护与制品**：before/after 各 **1/1**，count=1/missing=0/expected=1、正文文件名/size/mtime 不变；日志同前缀 `before.log`/`after.log`。原 `1.yaml` 两端 SHA-256 仍为 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。App `f393e05d3f71b28425ec053f052b91c14c54ca5343f4e3c4c64d4c424aa7fa6b`、AndroidTest `c53dc1e5807ce23ec2dd458d27d6ba5bae75c9798319e02417c70087825276ff` 均与设备匹配；App 仅 arm64-v8a，测试包无原生库；同前缀 `artifacts.json`，结束无服务/tun0，未清数据或修改正式配置。
- **失败记录与边界**：build1 漏 import 编译失败，build2 成功；首批 **8/10**，图标地址错误重复出现在父页与弹窗、图片测试数字回环地址不在既有 Debug 明文策略内，分别修为弹窗内显示和 localhost；build3 的 Java Base64 高于 minSdk 24 被 Lint 阻止，改用现有 Okio 后 build4 成功，最终整批 12/12。失败日志保留。批选/问题/核心过滤与远程图标分项 **DONE-VERIFIED**；本地图片系统选择器与真实宿主图标保存/Activity 重建未做 E2E，不将编码单测视为完整图片导入验收。全局图标集导入/同步/推荐、过滤预设、全局资源映射、长列表边缘拖动、父页逐项持久化和完整 Profiles/UI 仍 **PARTIAL**。下一阶段继续全局资源管理和映射的整页还原，再集中验证。

### 新增文件与真实 age／SAF 导入闭环（2026-11-03）

- **上游基线**：本地 `upstream/main` `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`，参考 `providers/actions/profiles.dart:addProfileFormFile`、`models/profile.dart:saveFile` 和新增来源页。本轮未 fetch 或合并 Flutter；沿用选择、缺钥补输、成功解密/校验后才新增的流程，保留 Kotlin 确认预览。
- **实现**：新增 `ProfileFileImportController`/Host，复用 8 MiB 有界 SAF reader，冻结本次源字节供预检和错钥原位重试；generation/Job 拒绝旧 picker、取消及后台后的迟到结果。UI 状态不保存字节或密钥，密钥只在弹窗内存；系统选择器遮住 Activity 时保留回调令牌。启停/运行守卫在确认及真正写入时复核。Repository 先解密与 YAML 校验，再在文件 mutex 内以 NonCancellable、原子文件替换和 Room 事务共同提交；插入前及文件提交后复核守卫，失败回滚新增行及文件。预检仅证明 YAML 可解析，不代表完整 Mihomo 语义校验。
- **构建**：根 Wrapper `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；App **319**，失败/错误/跳过 0，Lint **0 errors、53 warnings、9 hints**。最终日志 `/tmp/kclash-file-import-20261103-build3.log`。Service/Core/Common/Go 单测、Release 和 DNS/浏览器网络未重跑；Go helper 真实运行成功，不计为 Go 单测。
- **真机**：Redmi Note 8 Pro `wsvwypiz7xwslvl7`、API 36、1080×2340 竖屏、字号 1.0。最终 **8/8**，方法清单 `/tmp/kclash-file-import-20261103-methods.txt`，日志 `/tmp/kclash-file-import-20261103-device2.log`。真实 DocumentsUI 取消/预览取消零写入、授权原 `1.yaml` 明文新增、正确/错误 identity 的真实 age 密文经 Android Bridge 解密新增、Activity 重建后密钥保留及加密 SAF 替换均通过。隔离 Room/cache 的真实密文验证错钥失败不写、提交后守卫回滚、错误接收者更新保留旧文件/mtime，以及持久密钥复用于到期更新；下载器只返回本地密文，不称为真实加密在线订阅验收。
- **数据与制品**：正式 Profile 前后保护各 **1/1**，count=1/missing=0/expected=1，文件名/size/mtime 一致；日志 `/tmp/kclash-file-import-20261103-before.log`、`after.log`。真实宿主只回收本次新增且核对 ID/名称/FILE 类型的临时行，原选择不变。原 `1.yaml` 本机/设备 SHA-256 均为 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。App SHA-256 `02c78758284135c470e59ddabe0872c137122a1ef53de55715db2fd5e37b2d06`，AndroidTest `b7662a4d86143c5bbab64f5f0341f18bda11cc156a562f56f5245898f9a31462`，均与设备一致；App 仅 arm64-v8a，测试包无原生库。记录 `/tmp/kclash-file-import-20261103-artifacts.json`，结束无服务/tun0，未清数据、访问订阅或改写授权 YAML。
- **视觉与边界**：明文预览、缺钥、错钥三态 `/tmp/kclash-file-import-20261103-images/` 已目视，无明显裁切/重叠；本批为深色实际宿主截图，未宣称完整主题或 Flutter 像素 parity。build1 因 FakeStore 缺少接口实现编译失败，补齐后 build2 成功，首批 8/8；扩展加密替换/提交后回滚后 build3 和最终 8/8 再次通过，失败日志保留。真实 age Android Bridge E2E、新增明文/加密 SAF 与加密 FILE 替换分项 **DONE-VERIFIED**；Profiles/整体 UI **PARTIAL**。下一阶段继续策略组批量、图标、问题提示与全局资源映射。

### CUSTOM 规则可视整页与逐操作保存首版（2026-11-03）

- **上游基线**：参考本地 `upstream/main` `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 的 `profiles/custom/rules.dart`、`models/clash_config.dart`、`enum/enum.dart` 与规则预设；本轮未 fetch 或合并 Flutter。
- **整页流程**：搜索类型/内容/目标、批选/当前结果全选、逐条/批量复制删除、上下移动/整行长按排序、预设、36 种可添加规则类型、目标/规则资源搜索选择和快捷 YAML 接通；保存成功留在列表，表单/YAML 等待回读再关闭，失败保留草稿。src 开启时 no-resolve 显示开启且不可切换，与核心语义一致。搜索结束排序，避免过滤行号写入完整列表；排序失败或手势取消恢复已保存顺序。
- **规则与文件保护**：正则/逻辑 payload 保留逗号，NETWORK、范围数字和 DSCP 校验对齐上游；YAML 支持裸行、列表、标题、引号及注释，重复条目按出现顺序复用稳定标识。已有 SUB-RULE/失效旧规则不阻断其他条目修复，类型选择不新增 SUB-RULE，快捷 YAML 确认按 Flutter 移除 SUB-RULE。普通分区只替换 rules 并保留其他原文/边界说明，flow/整体缩进按语义替换；空值输出 `rules: []`。Repository 使用文件 mutex、正文快照、原子替换及 Room 事务，保留最新名称/覆盖/选择；generation 拒绝迟到读取与旧保存，文件写入守卫同时覆盖 STARTING/RUNNING/STOPPING，配置选择继续由核心管理器串行处理。
- **统一验证**：根 Wrapper `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 成功；App **313** 项，失败/错误/跳过均 0；Lint **0 errors、53 warnings、9 hints**。最终构建 `/tmp/kclash-custom-rules-20261103-build8.log`；Service/Core/Common/Go、Release 和 DNS/浏览器网络本轮未重跑，不把历史 Service 139 项计入本轮。
- **真机**：Redmi Note 8 Pro `wsvwypiz7xwslvl7`、API 36、1080×2340 竖屏、字号 1.0；精确 **12/12**，方法清单 `/tmp/kclash-custom-rules-20261103-methods.txt`，日志 `/tmp/kclash-custom-rules-20261103-device1.log`。覆盖搜索/批选/复制/删除、类型/策略/资源选择、src/no-resolve、YAML 校验/旧规则保留、按钮/长按排序及失败恢复、草稿退出、预设、深浅主题、原分区入口。真实 MainActivity 仅从授权原 `1.yaml` 创建临时 CUSTOM，验证自动保存、重开/Activity 重建、其他分区/原 DNS 覆盖/当前选择保留，finally 仅回收核对 ID/名称的临时行。隔离内存 Room/cache 验证过期正文、运行守卫、最新元数据保护；未启动核心或访问订阅。
- **制品与配置保护**：正式 Profile before/after 各 **1/1**，count=1/missing=0/expected=1，文件名/size/mtime 不变；日志 `/tmp/kclash-custom-rules-20261103-before.log`、`/tmp/kclash-custom-rules-20261103-after.log`。原 `1.yaml` 本机/设备 SHA-256 保持 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`，结束无服务/无 tun0。App `16d2c7a7a1227764a13d9c10b2e3e481469eec25f1835426db818f2b1c9fa518`、AndroidTest `e12fa969d7e3032efa872e1e6ab9fbf5c58e76c036e435732c9b0c398d536484` 均与设备安装包一致，App 仅 arm64-v8a、测试包无原生库；记录 `/tmp/kclash-custom-rules-20261103-artifacts.json`。
- **视觉、失败记录与边界**：深浅主题空态/列表/表单/类型选择/YAML/批选 **12 态**已集中目视，目录 `/tmp/kclash-custom-rules-20261103-reviewed-images/`，未见明显裁切/重叠；仅证明原生布局，未宣称 Flutter 像素 parity。首次拖动测试 `size.height` 编译错误、后续 assertNotNull 导入/调度器时序错误及启停写锁旧断言均已修复；build6 的 STARTING 仍可写用例发现实际守卫缺口，build7 更新生产守卫后仅旧契约断言失败，build8 统一通过，中间失败日志保留。整页首版 **DONE-VERIFIED**，长列表边缘拖动、全局资源映射、精确视觉/动效仍待补齐；完整 Profiles/UI **PARTIAL**，下一步真实 age/SAF 导入闭环，再推进其他整页差异。

### 更多 NTP／Sniffer 字段与快捷 YAML 整页首版（2026-11-03）

> 以下为历史首版记录；父页总保存和空值明确覆盖的旧行为，已由上方“更多设置逐项保存与空值继承”切片替代。

- **上游基线**：继续参考本地 `upstream/main` `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 的 `config/override.dart`、`config/ntp.dart`、`config/sniffer.dart`、`models/clash_config.dart` 和 `widgets/config_item.dart`；本轮未 fetch 或合并 Flutter。
- **整页流程**：仅显示已添加字段，底部添加面板仅列剩余项，并使用 Flutter 默认值；选项/嗅探协议分组，开关原位切换，文本/整数/列表和 HTTP/TLS/QUIC 协议进入独立编辑页。移除恢复继承，最后一个协议移除时恢复源协议；协议端口留空使用核心默认端口，独立目标支持默认/启用/禁用。父页、字段页、YAML 返回均有未保存保护，清除分区需确认。
- **快捷 YAML 与校验**：只接受裸分区字段，后台解析且错误留页；拒绝未知键/错误类型，保留 false、空列表和显式空协议映射，null 恢复字段继承。协议名归一大写并拒绝重复；端口/间隔、NTP 主机名/IP、嗅探 IP/CIDR 及 `geoip:`/`rule-set:` 匹配项已接通。数字地址先判定再解析，校验不触发域名查询。
- **暂存合并**：协议覆盖移除源配置中忽略大小写的别名，保留其他协议及原有未知字段；与 Flutter 一样，将旧 `sniffing`/`port-whitelist` 迁入后再覆盖单项。显式清空协议同时屏蔽旧 `sniffing`，避免 Mihomo 在空 `sniff` 下重新启用旧列表。修改只作用于覆盖元数据/暂存输入，不回写原始配置。
- **统一验证**：根 Wrapper `:app:testDebugUnitTest :service:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 成功；App 304、Service 139，失败/错误/跳过 0，Lint 0 errors、53 warnings、9 hints。最终日志 `/tmp/kclash-more-fields-20261103-final-build.log`。Core/Common/Go、Release 与 VPN/浏览器网络本轮未重跑。
- **真机**：Redmi Note 8 Pro `wsvwypiz7xwslvl7`、API 36、1080×2340 竖屏、字号 1.0；最终精确批次 **7/7**，方法清单 `/tmp/kclash-more-fields-20261103-methods.txt`、日志 `/tmp/kclash-more-fields-20261103-final-device.log`。覆盖默认值/非法端口/字段移除、YAML 类型/协议继承、三层取消保护、清除取消、深浅主题、原导入菜单、真实 MainActivity 保存重开/Activity 重建及原子替换回归。真实宿主仅读授权 `1.yaml` 原字节创建临时 CUSTOM，保留原 DNS 覆盖/选择/源文件，仅回收自己创建且核对 ID/名称的临时行；未启动核心或访问订阅。
- **保护与制品**：正式 Profile 前后各 1/1，count=1/missing=0/expected=1，文件名/size/mtime 相同；日志 `/tmp/kclash-more-fields-20261103-before.log`、`/tmp/kclash-more-fields-20261103-final-after.log`。本机/设备 `1.yaml` 哈希不变，结束服务为空、无 tun0。App SHA-256 `927f96cd0c3288fda2ea75b90e4fe7743ca61dd800fd969ec54898c52699adfc`，AndroidTest `344db0f06110c6f35bf12a2f6dae62013cc1e43202133f7f740a329845fbe6fd`，与设备安装包一致，App 仅 arm64-v8a、测试包无原生库，记录 `/tmp/kclash-more-fields-20261103-artifacts.json`。
- **视觉与边界**：最终截图 `/tmp/kclash-more-fields-20261103-final-images/`，新更多页深浅 12 态集中目视，另有旧更多/导入 8 态回归；只证明当前原生布局，不声称 Flutter 像素一致。保留 Kotlin 父页显式保存及明确空文本/空列表语义；Flutter 对应字段逐项落盘、部分空值移除键，这两项仍是 parity 差异。更多页本切片 **DONE-VERIFIED**，Profiles/整体 UI 仍 **PARTIAL**；下一整页为 CUSTOM 规则可视编辑，之后继续真实 age/SAF、策略组批量/图标/问题提示、全局资源映射及运行态扩展 E2E。

### 标准／脚本扩展整页与自动应用首版（2026-11-03）

- 参考本地 Flutter 基线 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 的 `profiles/extend/{extend,standard,script}.dart`、`providers/state/profile.dart`、`providers/actions/setup.dart`、`common/task.dart`、`database/rules.dart` 与七类规则预设，本轮未 fetch 上游。URL/FILE 扩展页新增标准/脚本模式、规则表单/原始规则、预设、上下移动/长按拖动、复选全选批量删除、默认目标搜索/跟随配置、按配置启用/禁用全局规则及全局定义管理、脚本绑定/取消和脚本管理入口；保留 DNS/Provider 覆盖入口和源文件写锁。
- 扩展元数据允许运行时保存，源文件不回写；本配置规则 → 启用的全局规则 → 原规则。附加规则目标 `MATCH` 使用显式默认目标或原最后一条 MATCH 的目标，不改写原规则。脚本模式只执行绑定脚本，标准模式不执行保留的绑定；未显式选择新模式的旧绑定与 CUSTOM 既有脚本行为保持兼容。完整备份输出 v2、读取兼容 v1..2，保留全局规则稳定 ID；仅配置恢复不改全局定义。
- 当前配置支持立即应用与退出自动应用，保存/应用串行，最少显示 600ms 应用状态；非当前配置只保存，应用前在管理器启停锁内复核 ID。失败保留已保存编辑并恢复旧核心输入/服务，页面可重试或暂不应用退出。后台节点刷新不会提前应用待确认的扩展/脚本；源文件、其他覆盖和 LAN 变更仍遵循原加载链。LAN 布尔热切不误标扩展已应用，待应用时恢复继承 LAN 会提示先应用扩展。
- 真实核心验收发现并修复共享编解码边界：SnakeYAML 2.3 的 1024 字符缓冲区末尾若为高代理项，会向已满缓冲区补读并抛出 `IndexOutOfBoundsException`；扩展重序列化改变 emoji 节点名位置后触发。`YamlConfigCodec` 的 Reader 为补读预留一格，不删改字符，覆盖多个首轮/后续缓冲区边界及往返单测。此前标准 apply 失败由此导致，非节点可用性故障。规则行手势取消不再提交旧顺序，修复批量删除后旧规则复现。
- 最终统一根 Wrapper `:app:testDebugUnitTest :service:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 成功；App JVM **299**、Service JVM **137**，失败/错误/跳过均 0；Lint **0 errors、53 warnings、9 hints**。日志 `/tmp/kclash-extension-20261103-final14-build.log`；之后仅将真实配置正文比较改为不打印正文的布尔断言，AndroidTest 增量构建 `/tmp/kclash-extension-20261103-final15-testbuild.log` 和最终 12 项复验通过，生产/UI 源码未变。Core/Common/Go、运行态真实 VPN 与浏览器网络本轮未重跑；运行态应用/回滚由 Service 单测验证，真实核心本轮仅停止态加载，不据此推广为网络验收。
- Redmi Note 8 Pro `wsvwypiz7xwslvl7`，API 36、1080×2340、竖屏字号 1.0，最终精确批次 **12/12**。方法清单 `/tmp/kclash-extension-20261103-methods.txt`，设备日志 `/tmp/kclash-extension-20261103-final15-device.log`；覆盖新页操作/深浅主题、隔离 Repository 和备份 v1/v2、旧配置页、正式 MainActivity 非当前扩展保存重开。真实核心用授权原 `1.yaml` 创建临时 FILE，验证后台刷新保持旧输入、标准/脚本互斥应用、坏脚本回滚及修复重试，最后恢复正式选择并只回收自己创建且核对 ID/名称的 Profile/脚本。
- 正式 Profile 前后保护各 **1/1**，均 count=1、missing=0、expected=1，文件名/size/mtime 一致；日志 `/tmp/kclash-extension-20261103-before.log`、`/tmp/kclash-extension-20261103-final15-after.log`。未清数据、删除正式配置、访问订阅或启用核心监听/VPN，最终服务为空、`/sys/class/net` 无 tun0。原始 `1.yaml` 本机/设备 SHA-256 均为 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。App SHA-256 `07bdb944688b94b5104822f3fb92447c8221f3a3d2856ec9ff607402b2c5d75a`，测试包 `f4e4184a590910d06e5ca64f377d610f3cb4af46e18f7336145f13d3f81445a4`，均与设备安装包一致；App 仅 arm64-v8a，测试包无原生库，记录 `/tmp/kclash-extension-20261103-final15-artifacts.json`。
- 最终截图 `/tmp/kclash-extension-20261103-final14-images/` 共 14 态，新扩展页标准/脚本/已应用/失败重试 × 深浅主题 **8 态已目视**，内容/动作无遮挡、系统栏清晰。固定宿主截图只证明当前原生布局，不作为 Flutter 像素一致性证据。保留早期 UI 删除/定位失败、真实核心 final10/phase11/diagnose12/diagnose13 失败日志；渲染阶段和异常类型/栈位置诊断不输出真实配置正文，最终 final14、final15 均 12/12 通过。
- 本轮**整页首版 DONE-VERIFIED，Profiles/整体 UI 仍 PARTIAL**。超长列表边缘拖动/虚拟化、精确动效/视觉和真实运行态扩展切换 E2E 仍待后续；更多页字段添加/快捷 YAML 已由上方后续切片补齐；下一步按整页继续 CUSTOM 规则可视编辑，再补真实 age/SAF、策略组批量/图标/问题提示和全局资源映射。Requests/DNS/Logs 次要视觉暂停，正式签名与发布独立推进。

### 自定义策略组可视编辑首版（2026-11-03）

- 继续以 Flutter 基线 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 的 `custom/groups.dart`、`custom/members.dart`、`state/custom.dart` 为参考。CUSTOM 概览的 + 直接新增、点组直接编辑，新增管理入口可进入列表。列表支持新增、复制、删除、长按拖动排序及上移/下移；从概览编辑单组确认后直接原子保存并返回，列表批量草稿则统一保存。
- 单组整页分为常规、节点、可选设置：名称、四种类型、图标地址；代理/其他组/Provider 多选、搜索、混合排序与 use-position；全部代理/资源、过滤/排除表达式；按类型显示的测速参数、空组回退、默认选中、负载均衡/哈希、隐藏和 UDP。可选字段有添加面板、逐项编辑和移除，false/0 与未设置分开；保留单组快捷 YAML 和整列表 YAML。
- 草稿保留未知字段，可视表单及单组快捷 YAML 改名同步组成员、规则目标、链式代理、NTP 出站和 Profile 选择/展开/覆盖引用。拒绝空/保留/重复名称、无节点来源、非法空组回退、负数参数及组/链式代理循环。未编辑分区及常规 block YAML 边界注释保留；flow 根对象或整体缩进无法单独定位分区时完整序列化，保持字段语义。修正旧提取器剥掉无缩进列表字段缩进的问题，并防止未来嵌套 name/type 被误作组字段。
- 保存复用文件快照、原子 rename 和 Room 事务，拒绝过期正文/运行状态/旧编辑代际，沿用最新名称、绑定脚本及其他覆盖，保留核心内建 GLOBAL 的选择记录。父概览保存后刷新；取消新建/成员修改不写文件。全屏 Dialog 同步自身深浅主题系统栏，修复浅色编辑页状态栏图标不可读。
- 最终统一执行根 Wrapper `:app:testDebugUnitTest :service:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`，App JVM **294**、Service JVM **128**，失败/错误/跳过均 0；Lint **0 errors、53 warnings、9 hints**。最终日志 `/tmp/kclash-groups-20261103-final6-build.log`；Core/Common/Go、DNS/浏览器网络主链路本轮未重跑。
- Redmi Note 8 Pro `wsvwypiz7xwslvl7`，API 36、1080×2340、竖屏字号 1.0，最终精确批次 **12/12**，方法清单 `/tmp/kclash-groups-20261103-methods.txt`，日志 `/tmp/kclash-groups-20261103-final6-device.log`。覆盖整套深浅表单/成员/设置/YAML、长按拖动、复制删除、直接确认、旧配置页回归；真实 MainActivity 从添加入口创建临时 Profile、可视新增组、文件回读、重新打开及管理/YAML 入口通过，最后只删除自己创建且核对过 ID/名称的临时配置。真实文件仓库用例只读取授权 `1.yaml`（本机/设备 SHA-256 均为 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`），隔离 Room/缓存验证过期/运行守卫及成功保存，不访问 Provider 或订阅。
- 正式 Profile 前后保护各 **1/1**，均 count=1、missing=0、expected=1，文件名/大小/mtime 完全相同；日志 `/tmp/kclash-groups-20261103-before.log`、`/tmp/kclash-groups-20261103-final6-after.log`。未清数据、删除正式配置或启动核心/VPN，最终服务为空且无 tun0。App SHA-256 `9d3db01beb93dcd094f7c383851f6d32ed7512207ccd8e9bd0be71105e17bd48`，测试包 `9f084531e0fd2bfbb48eaa1a3a0d0823f38db18778ced93fed99111a214cc54c`，均与设备安装包匹配；App 仅 arm64-v8a，测试包无原生库。
- 最终截图 `/tmp/kclash-groups-20261103-final-images/` 共 28 个画面（另有拼图），其中策略组列表/成员选择/成员排序/选项/快捷 YAML × 深浅 10 态已目视；画面采于 final5，final6 仅补 GLOBAL 元数据保护，UI 源码相同。首批 9/9 已通过，随后补充管理入口、系统栏、无缩进列表和真实宿主回归后最终 12/12；中间 final2 构建因测试 moveTo 的 durationMillis 参数不存在失败，按编译器签名改为 delayMillis，final3/4/5/6 构建均通过，失败日志保留。
- 当前为**可视编辑首版 DONE-VERIFIED，Profiles/整体 UI 仍 PARTIAL**。批量选择/删除、远程图标选择与预览、过滤预设/核心侧表达式校验、完整问题横幅、长列表边缘拖动滚动、全局资源登记/重名映射仍待对齐；缺失成员目前保留引用供用户修复。当时待补的标准/脚本扩展及 autoApply 已由上方后续切片补齐；更多页字段添加/快捷 YAML 已由上方后续切片补齐；卡片组数、真实 age/SAF 闭环继续作为后续整页工作，不把本批当作完整 Flutter parity。

### 自定义配置导入与更多网络设置（2026-11-03）

- 继续参考 Flutter 基线 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7 的 custom/custom.dart、actions/profiles.dart、state/custom_import.dart、config/ntp.dart、config/sniffer.dart。CUSTOM 顶栏新增 URL/文件导入，读取和摘要确认后才替换当前配置，不额外创建 Profile；保留名称和绑定脚本，清除旧覆盖/节点选择。只导入代理、策略组、资源、规则、DNS、NTP、Sniffer 分区；SUB-RULE 按 Flutter 移除并在确认页报告数量。
- 仓库复用原子文件替换与 Room 事务，确认时校验配置元数据/正文快照及运行守卫，失败恢复旧文件；导入读取可取消，关闭重开后的迟到结果不能进入新会话。URL 读取复用订阅客户端、文件读取复用有界 SAF reader；age 解密复用既有解码器，URL 表单支持可选密钥。导入不自动访问 YAML 内的远程 Provider。
- 更多页补 NTP 六字段及 Sniffer 开关、域名/源目标地址列表、HTTP/TLS/QUIC 端口与独立目标覆盖。字段显式覆盖与继承分开，NTP 端口/正间隔和嗅探端口范围/IP/CIDR 校验已接入；覆盖参与 JSON 往返和核心暂存合并，不改写源正文，保留未知字段及未选协议。分区连续保存合并最新覆盖并刷新父概览，元数据修改保留新草稿。独立 Dialog 的焦点清理修复，避免进入分区或确认导入后恢复旧键盘。
- 最终统一执行 :service:testDebugUnitTest、:app:testDebugUnitTest、:app:assembleDebug、:app:assembleDebugAndroidTest、:app:lintDebug（根 Wrapper、--no-daemon、-Ptarget-platform=android-arm64）通过，App JVM 284、Service JVM 128 项，失败/错误/跳过 0；Lint 0 errors、53 warnings、9 hints。日志 /tmp/kclash-custom-more-20261103-final7-build.log。Core/Common/Go 和 DNS/浏览器网络主链路本轮未重跑。
- Redmi API 36、1080×2340、竖屏默认字号 1.0，精确方法清单 /tmp/kclash-custom-more-20261103-methods.txt。前序最终版本批次 26/26 通过（final5-device.log），含原配置页 24 项与更多/导入 1 项、隔离 Room/文件原子替换 1 项；最终 SUB-RULE 补充后批次日志 /tmp/kclash-custom-more-20261103-final7-device.log，最终 26/26 通过。真实文件用例只读取已授权外层 1.yaml 原字节（本机/设备哈希一致），确认前零写入、运行/过期快照拒绝、成功提交且不增加 Profile 数量均覆盖；未访问真实订阅或启动核心/VPN。
- 正式 Profile 前后保护各 1/1 通过，均 count=1、missing=0、expected=1，文件名/size/mtime 与 before 相同；日志 /tmp/kclash-custom-more-20261103-before.log 和 /tmp/kclash-custom-more-20261103-final7-after.log。未清数据或删除正式配置；正式宿主用例只删除自己创建的空草稿。最终服务为空、sysfs 无 tun0。
- Debug APK SHA-256 5c974fdd17afdde8bf65b279fe1a6fb232f756230ef2763e2d2d6f4cfd258b90，AndroidTest 302ee1fdc1f23637c0c03a36075e2259e01c7299edee561fce9c2e672db7c06d，与设备安装包一致；App 仅 arm64-v8a，测试包无原生库。更多/导入八态截图与前序配置页十态存于 /tmp/kclash-custom-more-20261103-images/，已目视检查；导入输入态键盘可见，确认后键盘退出，固定列表宿主主题问题仍保留。
- 中间失败保留：首轮 service 原统计停止时序测试 1 项失败，独立及后续整批通过；导入读取固定 IO 导致 JVM 测试无法等待和后续 Main dispatcher 清理失败，改为注入调度器后通过；可空文件快照编译错误已修复。设备首批 24/26、后批 25/26，文案子串断言与独立窗口焦点/点击时序修复后 final5 为 26/26；一次 Lint 内部 Unexpected owner function: null 重跑最终成功。不将中间批次记为全绿。
- 整体 UI 仍为 PARTIAL。更多页字段添加面板、逐字段编辑/快捷 YAML、标准/脚本扩展与自动 apply 已由上方后续切片接通；策略组批量/图标/完整问题校验与计数卡仍待补齐；策略组可视首版同见上方后续切片。CUSTOM 导入保留本地 YAML 资源结构，上游全局资源登记/重名映射/远程资源加载尚未对齐；文件密文输入与压缩包导入、真实 URL/age/SAF 全流程 E2E 未验收。后续继续整页还原后批量验证。

### 自定义配置创建与概览整页对齐（2026-11-03）

- 参考 Flutter 基线 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`：CUSTOM 直接创建未命名草稿并进入概览，按规则、资源、DNS、更多组织入口，策略组与本地代理独立展示；标题下拉支持重命名和运行配置预览，顶部返回，展开原始 YAML 后才显示正文保存。卡片移除常驻溢出按钮，统一长按菜单；URL 菜单按编辑、扩展、同步、更多、删除排序。
- 概览后台解析源 YAML 和显式覆盖，统计规则、资源引用与缺失、策略组、代理及 DNS/Dialers 等状态；解析失败显示修复提示，旧代际结果不能覆盖新概览。标题重命名、脚本绑定同步父编辑器的已保存状态，写入期间保护草稿，避免退出误删；Dialers 保存合并最新覆盖。运行配置预览挂载层级修复，避免被概览窗口遮挡。
- 统一执行 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 成功；App JVM 277 项，失败/错误/跳过 0；Lint 0 errors、53 warnings、9 hints。日志 `/tmp/kclash-custom-overview-20261103-final-build.log`。Service/Core/Common/Go 本轮未改、未重跑，DNS/浏览器主链路沿用对应修复节证据，本轮未重新验收。
- Redmi Note 8 Pro `wsvwypiz7xwslvl7`，API 36、1080×2340、竖屏、字号 1.0：精确方法批次 24/24 通过，覆盖创建、概览、重命名、更多、YAML/分区保存、退出命名、草稿保护、长按菜单、只读预览与正式 MainActivity。补充截图方法 2/2 通过。方法清单 `/tmp/kclash-custom-overview-20261103-methods.txt`，设备日志 `/tmp/kclash-custom-overview-20261103-device.log`、视觉日志 `/tmp/kclash-custom-overview-20261103-visual.log`。
- 正式 Profile 前后保护各 1/1 通过，均为 `count=1 missing=0 expected=1`，文件名/size/mtime 一致；日志 `/tmp/kclash-custom-overview-20261103-before.log` 和 `/tmp/kclash-custom-overview-20261103-after.log`。正式配置保留，正式宿主用例仅删除自己新建的空草稿；本轮未清数据、未访问订阅、未读写外层 `1.yaml`，未启动核心/VPN，最终服务为空、无 tun0。
- Debug APK SHA-256 `036015d23a82939ae88a7c2d0966715332bf4902c02518f6b104133cb18fa6ee`，AndroidTest `94a13b184e01e71de70017265f834dccbb4f8ec79a26fcc00fc116e2a7188033`，与设备安装包一致；App 原生库仅 arm64-v8a，测试包无原生库。列表、扩展、远程编辑、自定义概览、更多的深浅主题共 10 张图已目视，目录 `/tmp/kclash-custom-overview-20261103-images/`；概览/更多窗口完整无遮挡，固定列表测试宿主 Activity 背景和系统栏主题仍待统一，不更新旧视觉哈希基线。
- 中间 AndroidTest `Unresolved reference longClick` 已补 import；JVM 首轮 275 项有 3 项因后台解析跨越测试 Main dispatcher 生命周期失败，改为显式注入概览 dispatcher 后最终 277 项全通过。保留失败经过，不将早期失败视为最终结果。
- Profiles/整体 UI 仍为 PARTIAL。当时待补的 CUSTOM URL/FILE 导入和 NTP/Sniffer 已有上方后续切片；策略组可视添加/排序/成员编辑已由上方首版切片补齐；标准/脚本扩展模式及离开后的自动 apply 已由上方后续切片接通；卡片策略组计数和策略组完整 parity 仍待补齐；规则资源引用仅覆盖简单 RULE-SET，不宣称完整问题校验。下一步继续 Profiles/Dashboard 整页差异及真实密文/SAF 闭环，完成整版后批量验证。

### 配置页整页首版对齐（2026-11-03）

- 参考 Flutter 基线 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7 的 profiles/add/edit/custom/extend：列表独立滚动、按最小 300dp 卡宽排为 1–3 列、首次定位选中配置、长按操作菜单、锚定添加来源；远程/FILE/自定义概览及分区编辑改为整页，备份与恢复/脚本库从工具进入。
- FILE 名称、密钥和替换正文原子保存；失败保留原文件和元数据。URL/FILE 的 DNS/Provider 扩展只写覆盖，源缓存只读；连续保存合并最新覆盖，避免丢失另一分区。
- App JVM 272 项通过，失败/错误/跳过 0；Debug/AndroidTest/lintDebug 通过，统一构建日志 /tmp/kclash-profiles-page-20261103-finish-build.log。后续仅调整截图等待和测试断言，AndroidTest 两次增量构建通过。本轮 Service/Core/Common/Go 和网络主链路未重跑。
- Redmi API 36、竖屏默认字号：60 项批次最初 56 通过、2 失败、2 跳过；修复 YAML 切分区前焦点/键盘、父子编辑器按钮定位。原跳过的两项 FILE 仓库测试携授权 1.yaml 参数通过。定向 5 项为 4 通过、1 旧删除按钮数量断言失败；修正后 Repository 整页创建/编辑/删除和正式 MainActivity 最终 2/2 通过。保留中间失败日志，不将 60 项单批记为全绿。
- 正式 Profile 保护 phase=after count=1 missing=0 expected=1，文件名/大小/mtime 与 before 相同。未清数据、删除正式配置或访问订阅，未启动核心/VPN；服务为空，/sys/class/net 无 tun0（ip link 查询被设备权限拒绝，未以失败作为无 TUN 证据）。本地与设备 1.yaml 哈希一致，原文件未改写。
- Debug APK SHA-256 892e1f668dd7405a10d534d77b74064f7ce60b0a91228b3e4a7d2c5847c14358，AndroidTest 8cb9cfe2d97d78c1701453615882c1f9d31136e551d13c5b85a1a52e03796431，与设备一致；App 仅 arm64-v8a，测试包无原生库。最终设备日志 /tmp/kclash-profiles-page-20261103-finish-device.log 和 /tmp/kclash-profiles-page-20261103-confirm-device.log。
- 深浅主题列表/扩展/远程编辑 6 态重新采集并抽查；等待窗口绘制后无叠影。测试宿主 Activity 背景/系统栏主题切换仍待统一，不更新旧 Profiles 哈希基线。当时的常驻溢出按钮、CUSTOM 命名步骤和菜单顺序待办已由上方自定义配置切片补齐；当时待补的 Flutter 标准/脚本扩展模式及规则自动 apply 链已有上方后续切片。Profiles/整体 UI 保持 PARTIAL；下一阶段继续整页差异和真实密文/SAF 闭环。

### 代理页整体首版对齐（2026-11-03）

- 参考本地 Flutter main/upstream/main 基线 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7 的 proxies 页面及 typography/surface/shape/card/button。提取 ProxyGroupsPage.kt；正式页面使用单一工具栏，移除重复边距，整合搜索、分组溢出选择、刷新、定位、批量测速和工具菜单。
- Tab/List × compact/standard/loose × 深浅主题及 List 折叠态共 13 张截图已人工检查。卡片最小高度分别为 66/86/105dp，测速布局不再撑高卡片；不可选择组可独立测速；长中文名 compact 一行、其余两行，修复固定高度裁掉第二行的问题。List 使用固定组头和连续容器底色，定位为组头预留高度；暗色顶栏图标按主题显示。
- Redmi Note 8 Pro wsvwypiz7xwslvl7（Android 16/API 36、1080×2340、竖屏、字号 1.0）最终精确方法批次 21/21 通过：代理组 8 项、ViewModel 面板 4 项、协议/滚动 4 项、页面 parity 2 项、Dashboard picker 2 项、正式 MainActivity 1 项。覆盖选择、独立/批量测速入口、搜索清空、溢出分组、Tab/List、定位、滚动恢复和停止态正式页面。截图矩阵哈希记录于 docs/visual-baselines/proxy-page-layout-matrix-redmi-api36.sha256，主页面深浅基线同步更新。旧截图首次因预期布局改变失败，人工审查新图更新后最终批次通过。
- 最终构建 ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64 通过；App JVM 270 项，失败/错误/跳过 0；Lint 0 errors、51 warnings、9 hints。Service/Core/Common/Go 本轮未重跑，未重新验证 DNS/浏览器网络链路。日志：/tmp/kclash-proxy-page-20261103-submit-build.log 与 /tmp/kclash-proxy-page-20261103-submit-device.log。
- 正式 Profile 前后保护通过：count=1、missing=0、expected=1，ID 对应文件名/大小/mtime 与验收前快照一致；本轮未清数据、删除配置、访问订阅或读写外层 1.yaml。最终服务列表为空，tun0 不存在。Debug APK SHA-256 为 ac176cca36b4025e82e0381e2eb97ae182d53343b4ad09555d92434bca4e675d，AndroidTest 为 bfd106f504a1194cb7af3874a09a8040948eebd08f268d36ca1a70d3822786c8，均与 Redmi 安装包一致；App 原生库只有 arm64-v8a，AndroidTest 无原生库。
- 整体 UI/UX 保持 PARTIAL；连续超椭圆、精确动效/焦点、节点菜单/添加本地、远程组图标和 Providers 仍待后续，不宣称 Flutter 逐像素一致。下一阶段继续 Profiles/Dashboard 整页对齐及真实密文/SAF 闭环，完成整版后批量测试。

### DNS 基线与浏览器主链路修复（2026-11-03）

- **代码**：`ProfileDnsBaselineApplier` 对齐 Flutter `baselineDns`，在原配置未启用 DNS 时只对核心暂存 YAML 补 `enable: true`、`enhanced-mode: fake-ip` 和两个 DoH nameserver；原 Profile 文件不回写，显式 DNS 覆盖仍在其后生效。
- **运行参数**：`DefaultVpnOptionsProvider` 从已校验的 `mihomo/config.yaml` 读取实际 mixed-port、TUN stack/MTU/拥塞控制；Android API 29+ 按 Flutter 语义发布 HTTP proxy，端口认证或无有效端口时关闭广播。运行中配置变更网络参数会重建 Proxy/VPN 服务，失败时恢复旧服务参数。
- **自动化**：App 270 项、Service 125 项 JVM 测试通过；Debug/AndroidTest 构建和 `lintDebug` 通过（0 errors，51 warnings，9 hints）。新增 DNS baseline 单测及 Redmi 真机 DNS 回归。
- **真机**：Redmi Note 8 Pro `wsvwypiz7xwslvl7`，Android 16/API 36，arm64-v8a；UDP 53、绑定 VPN 的 TCP 53、Android 系统域名解析均通过。X 浏览器百度和 Cloudflare `/cdn-cgi/trace` 均成功加载；Kotlin Debug APK SHA-256 为 `1f58b9d1a4129b0de6329e377d07e29c18c0ff9b12bbb3923dd6bbddae41376e`，与设备安装包一致。
- **对照边界**：设备上的 FlClash 0.9.2 使用同一网络和现有配置启动成功，百度与 Cloudflare 均可访问；直接从 shell 探测 `198.18.0.2:53` 的超时在两套实现中都能观察到，不能作为浏览器链路失败证据。Android 16 Private DNS 的 TCP/853 探测由系统额外发起，当前只记录为平台边界，未伪造 853 明文 DNS 劫持。

这份路线图描述 KClash 从 Flutter 参考实现迁移为 Android 原生 Kotlin 应用的完整路径。它同时记录当前实现边界、功能完成度、前置依赖、验收证据和后续工作顺序。

路线图不把“代码存在”“单元测试通过”“真机产品可用”混为一谈。每个功能必须分别经过代码完成、自动化验证和真实设备验收，才能进入已完成状态。

## 1. 固定产品边界

| 项目 | 已确认决策 |
| --- | --- |
| 产品显示名称 | `KClash` |
| Android 包名 | `com.lonnnnnng.flclash` |
| Kotlin 主线 | Git 分支 `kotlin`，长期主分支 |
| Flutter 参考 | `main` / `upstream/main`，只用于行为和视觉参考 |
| 上游仓库 | `chen08209/FlClash` |
| 个人 fork | `lonnnnnng/FlClash` |
| 数据迁移 | 不做 Flutter/Drift 数据迁移；这是全新应用 |
| Android 范围 | Android 手机，验收限定竖屏和系统默认字号 |
| 不纳入首发 | Android TV、平板、车机、桌面托盘和桌面 Helper |
| Android 最低版本 | API 24 |
| 编译与目标版本 | compile/target SDK 37，需随工具链和发布要求复核 |
| ABI | 只构建、测试和发布 `arm64-v8a` |
| 验收设备 | Redmi Note 8 Pro，serial 每次操作前重新读取 |
| 当前验收设备 | `wsvwypiz7xwslvl7`，Android 16 / API 36 |
| 真机验收订阅 | 只使用当前工作区根目录 `AGENTS.md` 保留的唯一授权路径（原第二条）；已导入配置保留供调试 |
| 发布签名 | 发布阶段创建新的独立 keystore，不复用 Flutter 签名 |
| 核心 | Go Mihomo，Android 应用通过 Kotlin Bridge 使用 |

## 2. 状态定义

| 状态 | 含义 |
| --- | --- |
| `DONE-VERIFIED` | 代码完成，自动化检查通过，并有真机或端到端证据 |
| `DONE-UNIT` | 代码完成，单元测试或构建通过，但真实设备链路尚未覆盖 |
| `PARTIAL` | 已有基础代码或局部行为，尚未达到 Flutter Android 功能闭环 |
| `PLANNED` | 已明确范围和验收方式，尚未开始实现 |
| `BLOCKED` | 存在明确外部阻塞条件；当前没有将普通未实现项标为阻塞 |
| `DEFERRED` | 明确不进入当前 Android 手机首发范围 |

## 2026-11-05：todo第1项编辑器剩余冷恢复

规则快捷YAML的正文/选区、错误与放弃确认已进入现有加密保存树，仅正文修改清错；异步校验冻结正文，保留原CAS/关闭守卫。

App481/Service195/Core38/Common31共745项零失败/错误/跳过；final1统一Debug/Test、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）。Redmi最终同套App/Test六场景各prepare/verify通过，外部严格核对PID消失与恢复PID不同；相关回归18/18，定向绿测独立1/1。31张PNG/6拼图已目视，安装回拉哈希匹配；正式1份配置/原选择/逐字段摘要及正文SHA-size-mtime、原1.yaml双端、Flutter包和设置保持，结束无服务/tun0。证据`docs/acceptance/remaining-editor-cold-redmi-20261105.json`。旧包重建后错误节点丢失红测及首次宿主未出现的环境失败分开保存，不跨批累计；当时工具观测到锁屏但未留独立附件，不认定唯一原因。

todo第1项本组范围已验；第2项保存意图落盘后未提交、已提交未清草稿及退出死亡窗口仍待真机。第3至11项保持待办，玻璃默认关闭，原暂缓/取消项不变。

## 2026-11-05：Proxies与本地代理加密草稿及真实冷进程（历史切片）

Proxies父草稿v3及本地代理独立加密草稿已接入正式入口，保留原CAS基线、UI树、保存/退出意图，恢复不自动重放；首次读取与元数据迟到恢复竞态已修，集合checkpoint不再回写旧正文。代理YAML改按Flutter语义校验，修复行内map、顶层flow列表及注释包装。

App481/Service195/Core38/Common31共745项单测零失败/错误/跳过；最终build8 Debug/Test、未签名Release/R8及独立lint8双变体通过，各0错误/66警告（Debug10/Release9提示）。Redmi最终同套App/Test十场景各prepare/verify通过，外部确认旧PID消失、恢复PID不同；旧入口14/14，Proxies正式MainActivity保存回读独立1/1。30张PNG与5张拼图已目视，App/Test回拉哈希匹配；正式1份配置/选择/正文SHA-size-mtime及逐字段摘要、本地目录、原1.yaml双端、Flutter包与设置保持，结束无服务/tun0。各verify在应用内部确认Store为空且主文件/.bak/.new均不存在；不使用受SELinux拒绝的run-as退出码判断。证据`docs/acceptance/local-proxy-draft-cold-redmi-20261105.json`。失败批次和红测独立保留，不跨包/跨批累计成功数。

规则YAML/保持打开的类型选择器、策略组成员/选择/排序/批选及保存中/落盘后UI收尾前死亡窗口仍待真实冷验。在线age/CUSTOM引用源、其他资源SAF/外部脚本、Provider剩余来源/故障恢复/规则命中、质量来源/持久缓存、Fallback/蜂窝/第三方OEM和第三步视觉/性能保持待办；玻璃默认关闭，原暂缓项保持。

## 2026-11-05：规则/策略组加密草稿与真实冷进程（历史切片）

父加密快照v2纳入规则/策略组正式入口、原CAS基线、UI纯值树和待提交正文；Dialog窗口重新提供稳定命名空间，正文不登记到Activity Bundle。真实规则类型选择器暴露DefaultLazyKey白名单缺口，现只往返该精确框架类的整数index，Release保留字段/构造器，不开放任意Parcelable。规则中断操作保留供显式重试，已提交仅回读确认；关闭等密文清子页后退场，旧写入与迟到恢复不能复活草稿。

App462/Service195/Core38/Common31共726项零失败/错误/跳过；生产build9 Debug/Test、未签名Release/R8、最终testbuild10与双Lint通过，各0错误/66警告（Debug10/Release9提示）。Redmi规则表单/批选/冲突、整组/单组YAML、图标/添加选项/值八场景各prepare/verify通过；中间启动正式App、Home、am kill并确认PID消失，恢复PID不同于准备与可见宿主PID。最终同套App/Test旧入口15/15、父正文冷进程两阶段各1/1独立记录，不跨批累计。35张PNG已目视；正式配置/正文/逐字段摘要与安装哈希保护通过，原1.yaml、Flutter包和设置保持，结束无服务/tun0。run-as私有文件检查被SELinux拒绝，原判断作废；草稿Store清空及marker删除由verify内部断言证明，未检查.bak/.new物理文件。registry1保存键测试问题、registry2/3/4生产白名单缺陷、registry5安装重叠0项及cold1错误选区预期全部保留。证据`docs/acceptance/structured-editor-cold-redmi-20261105.json`，原始附件只在本机/tmp。

整体保持PARTIAL：配置内Proxies、本地代理/集合/链接映射仍VM内存；规则YAML/打开的类型选择器、策略组成员/选择/排序/批选及保存中/落盘后UI收尾前死亡窗口尚未完整冷验。其他第二步实网/系统边界与第三步视觉/性能仍待推进，玻璃默认关闭，原暂缓项保持。

## 2026-11-05：后台订阅自然周期与运行态节点闭环（历史切片）

自动订阅下载/解密/校验在核心锁外，原子文件/Room提交与受影响当前配置应用共持生命周期锁；仅实际use的CUSTOM稳定资源ID触发刷新。待应用摘要与正文元数据同事务，应用失败保留新源/旧核心，下次只重试应用，改源使旧pending失效。App454/Service195/Core38/Common31共718项单测零失败/错误/跳过；build4 Debug/Test及未签名Release/R8、testbuild6和独立lint4双变体通过，各0错误/66警告（Debug10/Release9提示）。Redmi最终同套App4/Test6回归7/7；既有生产20分钟调度中的自然到期更新独立1/1，真实同步t0到自动更新t1间隔25分40秒，未调时/伪造lastUpdateAt/直调Worker/强制Job；等待时前台为桌面，核心会话保持。运行态节点经正式MainActivity新增、重复提示、逐字段落库及真实Activity重建回读通过，两张PNG已目视。正式1份配置/选择/正文SHA-size-mtime及逐字段摘要保持，原1.yaml双端、Flutter APK和旋转/字号/动画设置保持，结束无服务/tun0；两包安装哈希匹配。前批6/7、隔离Room观察者关闭连接池崩溃、两次6/7均保留，不累计。证据`docs/acceptance/subscription-auto-cycle-redmi-20261105.json`。只证明一次自然到期流程，不证明严格20分钟间隔或上游正文改变；真实在线age密文及CUSTOM引用源实网全链路仍未验。第二步其余资源SAF/外部脚本、Provider剩余来源/故障恢复/实际规则命中、规则/批选冷进程、质量来源/持久缓存及Fallback/蜂窝/第三方OEM仍待完成。完整视觉/性能属第三步，玻璃默认关闭；备份/系统恢复、观测次要打磨、语言/主题暂缓。

## 2026-11-05：第一步页面交互集中验收（历史切片）

整策略组原始YAML返回先校验并更新父草稿，失败可继续/放弃，长正文错误定位已验；单组图标/添加选项/值子页保存状态与返回层级恢复。本地代理列表/映射由VM保留草稿及会话边界，隔离Activity真实重建后保持，凭据测试标记不入Bundle。服务管理/质量拖回提交、取消、快甩、反向与后台中断已验，质量页退场保留最后正文，修复空白帧。

App453/Service193/Core38/Common31共715项单测零失败/错误/跳过；build3 Debug/Test、未签名Release/R8及独立lint3双变体通过，各0错误/64警告（Debug10/Release9提示）。Redmi最终同套App/Test final2 44/44，81张PNG已目视，两包安装哈希匹配。前批6/7、motion2 1/1、fling3 1/1、旧包43/43及质量修复前后各1/1独立保留、不累计；修复前测试也能通过，正文保留另由最终截图确认。正式1份配置/原选择/正文SHA-size-mtime及逐字段摘要保持，原1.yaml双端、Flutter APK和旋转/字号/动画设置保持，结束无服务/tun0。证据`docs/acceptance/stage-one-interactions-redmi-20261105.json`。

第一步指定页面交互完成本组范围验收；整组YAML/单组子页为保存状态模拟，本地代理为隔离Activity+正式VM的真实recreate，均不等于冷进程或正式MainActivity全链路。完整Flutter视觉仍PARTIAL；下一步按第二步推进真实加密在线订阅/到期自动更新、其他资源SAF/外部脚本、Provider故障恢复/规则命中、运行态节点落库、编辑器冷进程及质量来源/持久缓存；Fallback输入、蜂窝网络与第三方/OEM条件继续单列。第三步再做空态morph/orbit、全应用精确缓动/视觉、live背景/触点光效及8MiB/动效性能。玻璃默认关闭；备份/系统恢复、观测次要打磨、语言/主题暂缓。

## 2026-11-05：主页面子页恢复与面板连续过渡（历史切片）

规则编辑的原行身份、批选及保存中状态可重建恢复；源行已变更时阻止误新增。单策略组快捷YAML返回先校验并更新父草稿，错误可继续或放弃；Provider长文保存失败与同文案重试自动回顶。服务详情/管理/质量标题正文连续过渡，独立页内容和滚动状态解决返回后列表回顶；面板退场阻断业务触摸与按键。

App451/Service193/Core38/Common31共713项单测零失败/错误/跳过；build4 Debug/Test、未签名Release/R8及独立lint4双变体通过，各0错误/64警告（Debug10/Release9提示）。Redmi最终生产/测试包同批final5 28/28，63张PNG已目视，安装哈希匹配。首批26/28、两次6/7、motion4补验1/1及系统设置覆盖页面的final4 27/28独立保留、不累计。正式1份配置/原选择/正文SHA-size-mtime及逐字段摘要保持；原1.yaml双端、Flutter APK及旋转/字号/动画设置保持，结束无服务/tun0。证据`docs/acceptance/main-subpages-recovery-redmi-20261105.json`。

整体Flutter视觉仍PARTIAL；服务子页边缘拖回、精确缓动/全应用视觉、未覆盖子页/空态/异常恢复与性能仍待补。本组规则重建是保存状态模拟恢复，未做冷进程；未重跑实网/核心/VPN、低API、Go、正式签名运行或远端CI。玻璃默认关闭，Dock/按钮实时背景和触点光效后排；备份/系统恢复、观测次要打磨、语言/主题继续暂缓。

## 3. 当前快照

| 维度 | 当前状态 |
| --- | --- |
| 当前分支 | `kotlin`，追踪`origin/kotlin`；本组按验收结果提交推送，远端关系以Git核对为准 |
| 当前版本 | 公开正式 `0.1.0` / code `1000`，标签 `kclash-v0.1.0`不覆盖；手机保留同签名本地修正版（网速卡紧凑/KUI-022至026），本轮不发新Release |
| Gradle 工程 | 根目录Wrapper + `app` / `core` / `service` / `common` |
| UI / 状态 | Jetpack Compose + Material 3；ViewModel + StateFlow + 单向事件流 |
| 数据 / 注入 | Room新schema + 应用独立文件目录；Hilt |
| 核心修复基线 | 原始`chen08209/Clash.Meta`/`c5062921`上的本地控制器修复`f83e9b10`已保留；随后合入上游健康检查/自动选组`852d3ad1`，宿主Provider超时适配后当前gitlink固定`7038231e026b710088b9c369899b3a8b7f281739`；见源码缺陷台账 |
| 核心同步来源 | 仅同步`chen08209/Clash.Meta:FlClash`，不直接同步`MetaCubeX/mihomo`分支/tag/提交/PR；连续4项`25fb0699`、`4a8e40cf`、`04f718e6`、`024245dc`已通过`852d3ad1`合入自己的`kclash-fixes`，当前为`7038231e`，本地修复保留 |
| Flutter增量审查 | 2026-11-05只读核对`7fd417fb`→`be847366`（v0.9.3）9项提交；功能映射见[审查表](upstream-flutter-v0.9.3-review.md)，不等于全部适配。Flutter提交禁止merge/cherry-pick入Kotlin；内核4项增量已集成并进入当前APK，Provider多类型和全部自动选组实网覆盖仍按各自证据保持PARTIAL |
| 核心生命周期 | `CoreRuntimeManager`统一核心、VPN/纯代理、配置应用、代理组/测速、Provider更新及观测失效；UI不直接调用gomobile |
| 单元测试 | 最近产品组860项JVM结果零失败/错误/跳过：App578执行，Service213/Core38/Common31复用。双Lint0错误（Debug68警告14提示、Release66警告13提示）；Go本轮未改未重跑，发布前双层race仅历史证据。 |
| 最新真机 | KUI-026配置子页22/22（4+18不重复方法）、68图/10拼图、旧包IME红测失败与最终通过；签名包安装回拉/原配置详情/配置Tab烟测通过。本轮不测性能，KUI-025打开退化仍待查；完整视觉仍PARTIAL，见config-subpages-parity-redmi-20261109.json。 |
| 当前设备数据 | 保留1份授权本地配置及同签名本地优化版，原1.yaml哈希保持、服务停止；本轮motion/test已卸载。Flutter参考包未操作，后续只用Redmi验收。 |
| Fallback最近实核（历史，本轮未重跑） | Redmi API36、arm64-v8a、竖屏默认字号；更新授权源含34组/1个非空Fallback/7个成员。真实Mihomo停止态与运行态各完成自动选择、固定、非法成员拒绝、清除固定4阶段，8/8样本`valid-now=true`；固定选择跨真实force-stop冷进程恢复并在清除后回到有效自动`now`已通过，见`docs/acceptance/fallback-real-core-redmi-20261105.json`、`docs/acceptance/fallback-cold-process-redmi-20261106.json`。自然无效`now`仍作为合法解析契约下不可自然构造的语义边界 |
| 低API历史证据 | 前组API27手机AVD final6 10/10，仅离线材质/Window捕获/菜单/子页；不是本组新包验证，也不含核心/VPN/实网；当时设置记录null，不能据此确认恢复 |
| Debug / Test | 本轮Debug已构建、未覆盖正式包；最终motion/AppTest安装回拉一致，SHA分别 `bf7b7951…` / `4bd39962…`；正式签名包 `1ab169bd…`。完整哈希和源码快照见本轮验收JSON。 |
| Fallback验收 Debug APK | `android/app/build/outputs/apk/debug/app-debug.apk`；SHA-256 `32b3ad8f2c35b469131ab630540b44f9d778f4c01991de34632972ea2205b6bf`，Redmi回拉匹配，仅arm64-v8a；详见`fallback-real-core-redmi-20261105.json` |
| Fallback验收 AndroidTest APK | SHA-256 `a919dcb176e1f49b1716ac67eae079d0b49d8124a57df23064730be7c858176a`，Redmi回拉匹配，无原生库；冷进程精确日志与清理回验见`fallback-cold-process-redmi-20261106.json` |
| Release | 已正式发布；公开签名arm64/R8 APK SHA `ed063b9ba59d0825308b2d442da38e5e8ecdcb456cc7c55cd385f11b05ad1082`，下载校验/证书/Redmi安装回拉与运行通过。本地包哈希不同，两个构建的证据分别保留；跨版本升级未验。 |
| 最新证据 | 发布：`release-0.1.0-20261109.json`；视觉：`custom-profile-chip-dashboard-redmi-20261109.json`与`menu-rendering-diagnostic-redmi-20261109.json`。Perfetto失败保留，整体视觉/性能仍PARTIAL。 |


## 4. 目标架构图

```mermaid
flowchart TD
    UI[Compose UI\nKClashApp] --> VM[ViewModel\nStateFlow + Events]
    VM --> PROFILE[ProfileRepository]
    VM --> RUNTIME[CoreRuntimeManager]
    PROFILE --> ROOM[(Room\nKotlin schema)]
    PROFILE --> FILES[(App files\nprofiles/*.yaml)]
    RUNTIME --> BRIDGE[CoreRuntimeBridge]
    BRIDGE --> CORE[CoreBridge]
    CORE --> MIHOMO[Go Mihomo\narm64-v8a]
    RUNTIME --> PROXY[ProxyService\nforeground notification]
    RUNTIME --> VPN[VpnService\nTUN / routes / DNS]
    VPN --> MIHOMO
    MIHOMO --> OBS[Traffic / Connections / Requests / DNS / Logs]
    OBS --> BRIDGE
    BRIDGE --> VM
```

架构目标是让 `CoreRuntimeManager` 成为唯一的核心生命周期事实来源。页面、`ProxyService` 和 `VpnService` 不各自创建或销毁 Mihomo 实例。代理组、测速、VPN/TUN、纯代理和配置热切均已进入同一管理器；真实网络/权限/后台/进程恢复已有分项验收，蜂窝网络切换和完整 OEM 行为仍待补齐。

## 5. 里程碑总图

```mermaid
flowchart LR
    M0[M0 决策与骨架\nDONE-VERIFIED] --> M1[M1 配置与 Profiles 基础\nDONE-VERIFIED]
    M1 --> M2[M2 Mihomo 配置应用\nDONE-VERIFIED]
    M2 --> M3[M3 代理组与单节点测速\nDONE-VERIFIED]
    M3 --> M4[M4 真实 VPN/TUN 连接\nPARTIAL]
    M4 --> M5[M5 运行观测\nPARTIAL]
    M4 --> M6[M6 Profiles 高级能力\nPARTIAL]
    M5 --> M7[M7 Android 平台入口\nPARTIAL]
    M6 --> M8[M8 UI/UX 完整对齐\nPARTIAL]
    M7 --> M8
    M5 --> M9[M9 质量、签名与发布\nPARTIAL]
    M6 --> M9
    M8 --> M9
    M9 --> M10[持续上游同步\n持续工作]
```

## 6. 已完成里程碑

### M0：决策、仓库和原生工程骨架 — `DONE-VERIFIED`

已完成内容：

- 建立 `kotlin` 长期主线，保留 `upstream` 作为 Flutter 追踪来源。
- 确定 KClash 产品名、包名、API 下限、ARM64-only、真机验收边界和独立签名边界。
- 根目录建立 Android Gradle 工程和 Wrapper。
- 建立 `app`、`core`、`service`、`common` 模块。
- 接入 Compose、Material 3、Hilt、Room、StateFlow 和基础 ViewModel。
- gomobile 只生成 `android-arm64` / `arm64-v8a` 核心库。
- 建立 `AGENTS.md`、`.agents/`、`CONTEXT.md`、ADR 和上游同步规则。

完成证据：

- 根目录 `./gradlew` 可用。
- Debug APK 已安装到 Redmi 真机。
- APK 内原生库只包含 `lib/arm64-v8a/libclash.so`。

### M1：Profiles 与本地配置基础 — `DONE-VERIFIED`

已完成内容：

- Room `profiles` 表和应用偏好表。
- 当前配置选择和重启后恢复。
- 远程订阅 URL 导入。
- 订阅响应下载和 `subscription-userinfo` 用量解析。
- 重复订阅检测。
- 本地 YAML 文件导入。
- 自定义配置创建。
- 本地配置删除。
- 配置文件原子替换写入。
- 本地配置内容读取和基础 YAML 编辑器。
- 远程订阅手动同步。
- 配置选择由 CoreRuntimeManager 串行校验并热应用，停止态也加载节点；稳定运行态已开放远程本地副本保存/应用，FILE/CUSTOM结构化正文和删除保留各自门禁。

未纳入 M1 完成定义的内容：高级分区编辑；本地备份/恢复已完成首片，WebDAV 手动备份协议、Keystore 凭据、配置 UI、ZIP 仓库接入、认证失败/离线反馈和本地回环真机回归已完成；外部 WebDAV 服务验收、云端同步和完整页面级 E2E 仍待后续。订阅自动更新已完成代码、自动化验证、调度证据和历史两条当时授权路径的真实导入/同步回归（当前仅使用外层AGENTS唯一入口），但周期任务等待到期后的真实自动下载仍未验证。Deep Link 基础导入及本轮冷启动、重复导入和错误反馈回归已完成。二维码图片导入已完成代码、单元测试和历史两条当时授权路径真机验收（不代表现在授权恢复）。

### M2：Mihomo 配置校验与应用 — `DONE-VERIFIED`

已完成内容：

- `initClash` Bridge。
- 配置文件复制到 Mihomo 独立目录。
- 原子 staging，避免核心读取半写入文件。
- `validateConfig` 独立校验。
- 只有校验成功后才调用 `setupConfig`。
- 核心启动、停止、初始化失败状态。
- 运行状态 `STOPPED / STARTING / RUNNING / STOPPING / FAILED`。
- 启停、配置应用和代理操作共用 `CoreRuntimeManager.transitionLock`。
- 失败时不伪造“核心已运行”。

真机已验证：启动核心、前台通知出现、代理组可读取、核心停止后服务退出。

### M3：代理组、Selector 切换与单节点测速 — `DONE-VERIFIED`

已完成内容：

- `getProxies` Bridge 和类型安全的代理组模型。
- 保留核心返回的代理组顺序。
- 读取 `name`、`type`、`now`、`all`、`hidden`、`testUrl`。
- Selector 节点切换。
- 切换后重新读取核心状态，不以本地点击结果冒充核心确认。
- `asyncTestDelay` Bridge。
- 使用代理组 `testUrl`，未配置时使用 Mihomo 默认测试地址。
- 延迟结果按代理组、节点和测试 URL 隔离。
- 正数延迟、超时、失败、空结果和测速中状态。
- Selector 对话框内逐节点测速按钮。
- 嵌套代理组按平面列表展示，摘要沿当前选择递归解析到最终叶子节点，并对自循环/环路做保护。

真机已验证：

- 读取真实 `GLOBAL`、`Atom`、`Auto`。
- `DIRECT` 返回 `160 ms`。
- 不可用节点显示“失败”。
- `DIRECT → FR-1-01` 切换成功。

当前明确未纳入 M3：URLTest/Fallback 全部 Flutter 选择语义及剩余视觉细节；后续切片已补组/测速URL摘要和延迟Room跨进程恢复，不改变M3首片当时的验收范围。

## 7. 进行中和未完成的功能矩阵

### 7.1 真实连接链路

状态：`PARTIAL`。TUN保护和进程级网络观察沿用前组修正；本组控制器重建竞态及TLS端口泄漏已修并有Go红绿/竞态与Redmi6轮证据。最新Always-on零额外等待冷启动/SIGKILL首个TUN HTTPS均200，另纯代理恢复通过。历史TLS EOF因果、长期稳定性与Wi-Fi↔蜂窝仍待验。

当前已有：

- Mihomo控制器同步交接和TLS无效配置释放端口；核心f83e9b10已推并由gitlink固定，KCORE-002/003见`docs/upstream-source-defects.md`。

- Android `VpnService` 基础实现。
- TUN 地址、路由、DNS、IPv6 和访问控制参数模型。
- `Core.startTun` / `Core.stopTun` 调用。
- TUN保护gate在hook发布前就绪，listener未返回也调用平台保护；平台拒绝/Control错误向拨号透传，关闭等待inflight并释放回调，见ADR0020。只读DNS执行序号与有界源端口诊断不作为启动门禁；该保护切片未改Mihomo；本组控制器补丁另见ADR0021。
- 网络观察和 DNS 更新基础设施。
- `NetworkObserver` 由Application在配置加载前启动并由进程持有；ProxyService/VpnService停止不再注销共用观察器或清空系统DNS，真实网络变化仍更新DNS并重置连接。模式切换/停止后注册时间与DNS保持已由本轮真机回归；见ADR0019。
- 前台 `ProxyService` 通知模块。
- `ServiceConfig` 发布 `STOPPED / STARTING / CONNECTED / FAILED` VPN 状态和失败原因。
- `VpnService.onStartCommand()` 已进入真实 `start()`，成功建立 TUN 后才发布 `CONNECTED`。
- `DefaultVpnOptionsProvider` 已提供 Android 手机首阶段默认 TUN 配置，并由 `CoreRuntimeManager` 注入。
- `AndroidProxyServiceController` 先等待 ProxyService 启动确认，启用 VPN 时再启动 VpnService 并等待 VPN 状态或 15 秒超时；关闭 VPN 时仅运行本地代理。
- `MainActivity` 已接入 `VpnService.prepare()` 和 Activity Result 授权回调。
- 启动失败会停止服务、清理监听器，管理器不会把失败报告为 `RUNNING`。
- 停止时向绑定中的 `VpnService` 发送显式 stop action，再停止服务组件，避免系统绑定导致 TUN 残留。
- `RuntimeResumeStore` 将“用户希望核心保持运行”的意图持久化到 Room；启动成功前写入，显式停止、系统撤销和启动失败时清除。
- `KClashApplication` 在进程创建时检查恢复意图和 VPN 选择；纯代理跳过 VPN 授权门禁，启用 VPN 且授权有效时复用 `CoreRuntimeManager.startProxy()` 恢复核心。纯代理受控SIGKILL后系统自主恢复已由11-05本组验收：旧PID存活后外部触发死亡，verify前系统新PID及FGS可见，实际HTTP/核心归因通过；不推广OEM内存压力或强行停止。
- `ProxyService` 与 `VpnService` 对系统重建传入的 `null Intent` 返回 `START_NOT_STICKY`，让 Application 先恢复进程内 `VpnOptions` 后再显式启动，避免 `START_STICKY` 服务抢跑。
- 服务层和管理器新增失败回滚、系统撤销和撤销后重启测试；该早期切片当时98项Kotlin单测通过；当前总数见第3节。

本轮真机验收记录（2026-10-25，设备 `wsvwypiz7xwslvl7`，Android 16 / API 36）：

- 使用当时可用的远程订阅启动成功，UI 显示“核心运行中”；该历史地址已从当前真机验收清单移除，后续仅使用工作区外层 `AGENTS.md` 当前保留的唯一授权路径。
- `dumpsys activity services` 观察到 `VpnService` 和 `ProxyService` 均为前台服务。
- `ip route` 观察到 `198.18.0.0/30 dev tun0`，Mihomo 日志出现从 `198.18.0.1` 发出的 TCP/UDP TUN 流量。
- 设备内 `curl -I http://1.1.1.1` 返回 `HTTP/1.1 301 Moved Permanently`，证明启用 TUN 时设备网络请求仍可完成；尚未将该请求与选定代理节点做可归因的端到端关联。
- 点击停止后 UI 显示“核心已停止”，服务记录、`tun0`、`198.18.0.0/30` 路由均消失；`dumpsys connectivity` 未留下 KClash VPN 网络。
- 临时将 `ACTIVATE_VPN` 设为 deny 后重新启动，系统显示 KClash VPN 授权对话框；点击“取消”后 UI 回到“核心已停止”，显示“VPN 权限未授予”，且未留下前台服务、`tun0` 或 VPN 路由；随后已恢复 `ACTIVATE_VPN: allow`。
- 旧本地测试配置曾被核心拒绝，错误为 `proxy DIRECT is the duplicate name`；该失败状态正确回显，未作为成功证据。
- 使用第二条当前授权路径启动后，通过系统“设置 → VPN → KClash → 断开连接”触发真实系统撤销；`VpnService.onRevoke()` 清理 TUN 并通知应用内 `ServiceBroadcastReceiver`，首页回到“核心启动失败”。
- 系统撤销后 `VpnService`、`ProxyService`、`tun0`、`198.18.0.0/30` 路由和 KClash VPN 网络均消失；同一 Profile 再次点击“启动核心”成功恢复运行，随后停止并删除 Profile，设备回到“还没有配置”空态。
- 使用第一条当前授权路径启动后按 Home 进入后台 10 秒，`VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由持续存在；重新打开 KClash 首页仍显示“核心运行中”，DNS 事件继续到达，随后停止并删除 Profile。
- 使用第一条当前授权路径启动后关闭 Wi‑Fi，等待 7 秒确认底层 Wi‑Fi 网络消失；VPN 网络的 `UnderlyingNetworks` 变为空，但 `VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由仍保持，应用进程未退出。重新开启 Wi‑Fi 后，底层接口恢复并绑定新的网络句柄，VPN、服务和路由保持；通过 TUN 发起的 `ping 1.1.1.1` 成功，Mihomo 日志出现 DNS 更新。
- 本轮设备蜂窝数据未注册且不可用，因此没有把 Wi‑Fi↔移动网络切换写成完成证据；测试 Profile 已通过应用内删除，设备回到“还没有配置”空态。
- 进程重建验收：使用第二条当前授权路径启动后，记录旧 PID `16207`；通过 `cmd activity crash com.lonnnnnng.flclash` 触发进程退出，4 秒后出现新 PID `16637`。20 秒后两个前台服务、`tun0` 和 `198.18.0.0/30` 路由均保持，重新打开首页仍显示“核心运行中”；停止后服务和路由清理完成，Profile 通过应用内删除并恢复“还没有配置”空态。

当前缺失：

- 已应用配置的mixed-port/TUN参数、全局Android网络设置、路由覆盖及运行态服务重建已有后续实现和定向验收，不能沿用“仍是首阶段默认配置”的旧结论；全字段、低API与OEM网络行为仍未全部验收。
- 首次安装或清除数据后的 VPN 授权弹窗尚未在本轮干净状态中重测；临时拒绝授权后的失败回滚路径和运行期间系统撤销 VPN 的路径已验证。
- 已完成“当前代理组/节点 → 受控真实请求 → HTTP 状态、代理链、规则、Requests、DNS、Logs 和累计流量”的首轮真机归因闭环；活动连接在第二条授权路径的 7.4 秒请求中捕获到 1 条。
- Wi‑Fi 断开/恢复和进程重建已完成真机验证；尚未覆盖真实 Wi‑Fi↔移动网络切换，当前设备蜂窝网络未注册。

完成门槛：

1. 首次启动能解释并申请 VPN 权限。
2. 用户授权后能建立 TUN，拒绝时保持失败状态并给出可理解原因。
3. 真实网络请求经过 Mihomo，代理组选择对请求生效。
4. 停止后 TUN、前台服务和核心全部释放。
5. 真机重启应用、切换 Wi-Fi/移动网络、切后台再恢复和进程重建均不会产生悬挂隧道；其中后台恢复、Wi‑Fi 断开恢复和进程重建已完成，Wi‑Fi↔移动网络切换仍待验证。

### 7.2 连接、流量和运行观测

状态：`PARTIAL`，流量、连接数、活动连接、Requests、DNS 和核心日志的基础观测与真实请求归因已验证，详情级交互已支持搜索、筛选、自动跟随和脱敏导出，事件流已具备有界背压和健康统计，运行诊断历史已接入 Room、在进程重建后直接展示恢复事件，并支持完整详情查看和独立脱敏导出；稳定故障归因、建议文案、脱敏原始详情和诊断导出元数据已完成，真实 `MainActivity` 的非空 Requests/DNS/Logs 详情入口、搜索、日志级别筛选和关闭回归已完成，仍需补齐跨设备和完整页面级端到端覆盖。

当前已有：

- `CoreBridge` 已映射 Mihomo `getTraffic`、`getTotalTraffic` 和 `getConnectionCount`，并对负数计数做边界归一化。
- `CoreRuntimeManager` 只在核心运行状态下读取三类统计，避免停止状态访问已释放的核心。
- `MainViewModel` 只在核心运行且首页 Activity 可见时每秒轮询统计；页面进入后台会取消收集，停止或进入错误时清理/回显状态。
- Compose 首页已提供运行统计卡片，展示上传、下载、连接数和累计流量，并覆盖停止态、加载态和错误态。
- `CoreBridge`、`CoreRuntimeManager` 和 `MainViewModel` 已接入 Mihomo `getConnections`，首页显示活动连接数量、目标、进程、代理链、规则和上下行计数。
- Connections 摘要行可打开按分组展示已映射字段的详情；点击快照保留至关闭，在连接结束、核心停止和 Activity 重建后仍可读取；摘要及详情的代理链顺序均与 Flutter 反序展示一致。
- 连接轮询与 request 事件统一解析 GeoIP、ASN 和 DNS 模式；底部详情面板展示本地秒级时间、`process(uid)`、目标 GeoIP/ASN 和 DNS 模式，隐藏空值，文本可复制且有成功/失败反馈。Redmi 固定宿主覆盖非空元数据、返回/下滑关闭及重建恢复；真实 MainActivity 已通过授权路径第 2 条的连接识别、主机复制、停止和重建快照闭环。
- `CoreBridge` 已接入 `updateEventListener()`，解析 Go 核心的 `message.arguments` 批次，并将 `request`、`requestTraffic` 事件转换为 Kotlin 领域事件。
- `CoreBridge` 已将 `dns`、`log` 事件转换为稳定的 Kotlin `CoreDnsQuery` 和 `CoreEvent.Log`，并覆盖 DNS 字段、日志级别和损坏事件丢弃测试。
- `CoreRuntimeManager` 在核心运行期间安装事件监听器和 Mihomo 日志泵，停止或启动失败时成对释放；事件流不经过 Compose 或 gomobile 生成类型。
- `MainViewModel` 已维护最近 2000 条 Requests 历史，并按连接 ID 合并上下行流量更新；首页提供请求空态、最近记录和完整记录对话框。
- `MainViewModel` 已维护最近 1000 条 DNS 查询和核心日志，停止核心时清空运行观测，并提供用户主动清空入口。
- Compose 首页已提供 DNS 查询和核心日志卡片、最近记录和完整记录对话框，覆盖停止态、空态和错误日志颜色提示。
- Requests/DNS 完整记录对话框可进入单条详情；Requests 复用连接快照面板，DNS 按基本信息、错误、答案分组并隐藏缺失字段，支持整行复制及成功/失败反馈。点击代理链或 DNS 标签可叠加精确关键词筛选，自由搜索按空白拆词、忽略大小写且所有词同时匹配；关闭单条详情后保留搜索和标签。固定宿主真机流程已验证，DNS 详情的 Activity 重建恢复仍待补齐。
- 核心日志详情和首页卡片提供系统文件导出；导出前遮蔽 URL 查询参数、JSON 凭据字段和常见 token/key/secret/password/url 值。
- `CoreBridge.probe`、`CoreRuntimeManager.probe` 和首页“真实请求诊断”卡片已接入；结果包含 HTTP 状态、耗时、最终 URL、代理链、规则和核心路由版本，且不保留响应正文。

仍需补齐的 Bridge / Repository / UI：

- 跨设备和完整页面级端到端断言，以及更细的诊断筛选和导出体验；当前真实 `MainActivity` 已补齐观测页停止态空态、Requests/DNS/Logs 三页签导航、非空详情入口、无结果搜索、日志级别筛选和关闭回归。

本轮自动化与真机证据（2026-10-25，设备 `wsvwypiz7xwslvl7`）：

- `./gradlew test :app:assembleDebug --no-daemon` 通过；98 项 Kotlin 单元测试通过，失败 0，错误 0，跳过 0。
- Debug APK 安装成功；启动核心后首页实际收到 DNS 查询（4 条）和核心日志（3 条），卡片出现“查看全部/清空”操作；停止态观测卡片恢复启动核心提示。
- 停止后 `tun0`、`198.18.0.0/30` 路由和 KClash 前台服务均已清理。
- 本轮只使用当前工作区根目录 `AGENTS.md` 列出的两条用户授权路径；第一条诊断返回 HTTP 403 / 1.6 秒，第二条诊断返回 HTTP 200 / 7.4 秒。
- 第一条路径 UI 显示真实代理链和 `Match` 规则，请求记录达到 10 条、累计流量非零；第二条路径 UI 捕获活动连接 1 条、请求记录 17 条、DNS 查询 26 条、核心日志 31 条、累计流量非零。
- 两条路径均在停止后清理 `tun0`、`198.18.0.0/30` 路由和 KClash 前台服务；测试 Profile 均通过应用内删除，设备恢复空配置状态。
- Deep Link 切片真机导入使用当前授权清单中的第一条路径；该次导入成功并显示订阅用量，测试 Profile 已在验收后删除。

本轮观测详情交互切片（2026-10-26，设备 `wsvwypiz7xwslvl7`）：

- Requests 详情对话框支持按目标、进程、代理链和规则大小写不敏感搜索，并显示过滤后数量；DNS 详情对话框支持按域名、上游、答案和错误搜索。
- 核心日志详情对话框支持内容搜索和“全部/错误/警告”级别筛选；筛选无匹配时显示明确空态，不隐藏原始记录总数。
- 三类详情列表共用自动跟随最新事件的滚动行为；用户向上翻阅历史后保持当前位置，改变筛选条件时回到最新匹配项。
- 新增 3 项筛选语义和 1 项日志导出脱敏单元测试；`./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --no-daemon` 通过，工程总计 102 项 Kotlin 单元测试通过。
- 安装 Debug APK 后启动第一条当前授权路径，首页捕获活动连接 1 条、请求 4 条、DNS 查询 13 条和核心日志 8 条；打开三类详情对话框分别看到搜索框，其中日志点击“错误”后显示 `0/8` 和“没有匹配的核心日志”。
- 核心日志详情和首页卡片新增系统文件导出；导出文件使用 UTF‑8 表格文本格式，URL 查询参数、JSON 凭据字段和常见 token/key/secret/password/url 值统一替换为 `[REDACTED_SECRET]`。
- 真机通过 Android `DocumentsUI` 保存 `kclash-logs-*.txt` 到 `Download/kclash-qr`；文件大小 1634 bytes、17 行，前两行为导出标题和列名，设备侧检查到 61 个脱敏标记，未把原始凭据写入导出文件。
- 通过 `adb -s wsvwypiz7xwslvl7 shell am start -a com.lonnnnnng.flclash.action.STOP` 停止核心，确认 `VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由清理；随后应用内删除测试 Profile，首页恢复“还没有配置”，未发现 `FATAL EXCEPTION`。

完成门槛：

- 每个页面都有 loading、空数据、刷新、错误和停止核心状态。
- 观测数据不会阻塞启停和代理切换。
- 真机能看到一条实际请求从连接、请求、DNS 和日志侧的可追踪证据。

### 7.3 Profiles 高级能力

Provider最新（11-05）：todo第5项本组范围已验：Provider批量部分失败保留逐项受控原因，单项恢复只移除本项，轮询/前后台保留剩余失败，关闭/换配置拒绝迟到结果，超时释放忙态。Redmi真实Mihomo经受控HTTP服务完成1成功+503/缺失YAML payload两失败、旧缓存保护、单项恢复和只重试剩余项。公开8源更新SHA/条目/时间前进通过；HTTP8种、File8种、Inline3种实际HTTPS均有精确RuleSet/Provider归因，另验原配置geolocation-cn MRS命中及Inline Proxy原两个节点展开。 证据`docs/acceptance/provider-recovery-hits-redmi-20261105.json`，持久附件`../acceptance-artifacts/provider-recovery-hits-20261105/`。本组仅在原1.yaml临时副本的正式脚本投影中隔离规则并关闭嗅探；非整份原配置路由/嗅探交互、全部90个MRS站点或所有代理协议流量覆盖。原MRS首试失败、第二次成功已逐次保留，早期6个失败批次不累计。托管资源混合同步/apply失败新分支仅静态审查，未独立故障注入；Go、低API、VPN、正式签名运行、远端CI未重跑。完整Flutter视觉仍PARTIAL；第6项缓存与全部目标尝试的最新结果及保护异常见第3/7.7节，玻璃默认关闭，原暂缓/取消项保持。

资源/脚本前组（11-05）：todo第4项本组范围已验：规则YAML/MRS与图标集真实OpenDocument，策略组本地图标GetContent经Android16照片选择器“更多选项→浏览…”进入DocumentsUI；覆盖取消、空/错误文件拒绝、重试、预览不写入、正式保存和Activity重建回读。MRS原字节保持，仓库/VM/页面预览摘要一致。公开外部脚本真实下载、改源、同步与绑定配置运行态应用通过，直接Bridge确认新增CustomProxy、暂存规则及Mihomo epoch 4→5→6；正文、DIRECT选择与纯代理会话保持。证据`docs/acceptance/resource-saf-scripts-redmi-20261105.json`，持久附件`../acceptance-artifacts/resource-saf-scripts-20261105/`。本组之后的Provider进度见本节顶部最新证据。

在线引用前组（11-05）：todo第3项CUSTOM引用在线源已验：手动ProfilesViewModel同步与生产到期Repository自动更新各完成一次真实下载、提交和当前CUSTOM应用。临时资源初值使用原1.yaml；下载后源字节版本变化，真实Mihomo epoch分别3→4、8→9，按正文SHA生成的文件Provider路径和7个节点回读匹配；当前CUSTOM、DIRECT组选择、正文及纯代理运行会话保持。无Activity/节点轮询参与补加载。唯一授权响应实测为34815字节明文，既有密钥为空，在线age下载/解密/应用仍受输入条件限制，不记通过。见`docs/acceptance/subscription-reference-network-redmi-20261105.json`；第3项CUSTOM实网范围已验，age受限；第4项进度见本节顶部最新证据。

保存死亡窗口历史（11-05）：todo第2项保存/退出受控死亡窗口已验：规则、Proxies、策略组结构化及原始YAML、本地节点的意图落盘/正式提交窗口，实际引用配置的退出应用前后、父草稿清除前后及CAS冲突共15场景。准备测试命中门闩后保持存活，外部核对同PID后force-stop并确认消失；15次首次恢复、5次测试进程正常退出后的再次冷启动检查通过。恢复不重复写入，未提交等待显式重试，源变化保留旧CAS；退出应用调用计数在恢复后为0、显式重试后为1。见`editor-commit-window-redmi-20261105.json`；完整实网与视觉仍PARTIAL。

前组冷恢复（11-05）：规则快捷YAML的正文/选区、错误与放弃确认已进入现有加密保存树，仅正文修改清错；异步校验冻结正文，保留原CAS/关闭守卫。App481/Service195/Core38/Common31共745项零失败/错误/跳过；final1统一Debug/Test、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）。Redmi最终同套App/Test六场景各prepare/verify通过，外部严格核对PID消失与恢复PID不同；相关回归18/18，定向绿测独立1/1。31张PNG/6拼图已目视，安装回拉哈希匹配；正式1份配置/原选择/逐字段摘要及正文SHA-size-mtime、原1.yaml双端、Flutter包和设置保持，结束无服务/tun0。见`docs/acceptance/remaining-editor-cold-redmi-20261105.json`。Proxies/本地代理十场景和前序规则/策略组八场景沿用各自历史证据。第1项已验；保存死亡窗口以本节顶部第2项新证据为准，第3至11项待办。

后台订阅历史切片（11-05）：下载/校验在核心锁外，原子提交与受影响当前配置应用共锁；应用失败下次只重试应用，旧pending随来源版本变化失效。718单测、最终回归7/7和自然周期独立1/1见`subscription-auto-cycle-redmi-20261105.json`；真实在线age密文、CUSTOM引用源实网及其他第二步场景仍待验。

状态：`PARTIAL`，基础能力和高级分区首片已完成，完整 Flutter parity 仍在推进。

第一步最新：整策略组原始YAML返回先校验并更新父草稿，失败可继续/放弃，长正文错误定位已验；单组图标/添加选项/值子页保存状态与返回层级恢复。本地代理列表/映射由VM保留草稿及会话边界，隔离Activity真实重建后保持，凭据测试标记不入Bundle。服务管理/质量拖回提交、取消、快甩、反向与后台中断已验，质量页退场保留最后正文，修复空白帧。最终同批44/44及715单测见`stage-one-interactions-redmi-20261105.json`；真实冷进程与实网仍归第二步，完整视觉/性能归第三步。下方旧批次仅证明当时范围。

最新交互补充（11-05）：规则编辑的原行身份、批选及保存中状态可重建恢复；源行已变更时阻止误新增。单策略组快捷YAML返回先校验并更新父草稿，错误可继续或放弃；Provider长文保存失败与同文案重试自动回顶。服务详情/管理/质量标题正文连续过渡，独立页内容和滚动状态解决返回后列表回顶；面板退场阻断业务触摸与按键。本组Redmi 28/28，证据`main-subpages-recovery-redmi-20261105.json`；重建仅证明保存状态恢复，不推广真实冷进程。

最新视觉补充：本地代理基本/认证/可选项、Provider选项与资源列表按56dp同行、右侧轻量输入、24/8分组标题及资源边距补齐；随后共享玻璃材质、策略组/更多/链式代理覆盖子页与API27离线fallback同批验收，见`general-glass-subpages-api27-20261104.json`。正文/运行预览等宽着色、1201行查找复制和72dp排序沿用前组证据。未覆盖的更多页面/空态与8MiB正文极限性能待补，完整Profiles仍PARTIAL；历史Room摘要变化保留，早期元数据兼容不在开发阶段要求内，正式配置与正文仍按验收快照核验。

最新补充：全局本地代理/链式代理、规则/图库/过滤/Geo资源、Provider结构化与外部文档、脚本整页、常规/网络/全局DNS/附加规则、按需SSID、批量同步及正文父草稿均已有首版。备份现为v6，ALL_DATA纳入常规/网络/全局DNS、VPN/LAN、恢复策略及可选SSID；PROFILES_ONLY和旧归档缺项保留现值，失败有补偿。其他界面/按应用代理偏好、跨全部写者一致快照和系统备份SAF完整E2E仍待补。分项设备证据见顶部同名节，完整Profiles仍为PARTIAL。

已完成本轮切片：

- `clash://install-config`、`clashmeta://install-config`、`flclash://install-config` 和 `kclash://install-config` 的 Android 入口声明。
- Deep Link `url` 参数 URL 解码、HTTP/HTTPS 校验和可选名称解析。
- Deep Link 导入复用 `ProfilesViewModel.addRemote`，不在 Activity 中重复下载或写入 Profile。
- 无效 Scheme、Host、目标协议或缺失参数会通过现有 Snackbar 错误流提示，不能创建半成品 Profile。
- 二维码图片选择、QR 格式识别和配置链接解析已接入添加 Profile 对话框；直接 HTTP(S) 链接和四种 KClash Deep Link 统一复用同一解析器及远程订阅写入链路。
- 本地文件选择后先异步校验 YAML 语法并展示文件名、大小与结果；有效文件需用户确认才提交，无效/空文件不可导入且取消不写入。`ProfileRepository.addFileProfile` 在生成 ID、写文件和 Room 插入前再次复核；该预检只证明 YAML 可解析，不等同于 Mihomo 核心语义校验。
- FILE 本地配置编辑提供可选掩码 age 密钥和系统文件替换，原始 YAML 默认折叠；只改密钥保留正文与更新时间。替换按原字节暂存，确认后解密/语法校验，再在同一保存流程更新文件与 Room；异常恢复旧正文/mtime，保存与删除串行，确认前取消零写入。真实 DocumentsUI 已完成授权 `1.yaml` 明文新增与替换，以及同一原字节生成的真实 age 密文新增与替换；错钥原位重试、持久密钥复用、Activity 重建和失败回滚通过，CUSTOM 保持原分区编辑器。
- 稳定RUNNING已开放首页/配置单项和批量同步、排序、重命名、远程/FILE/CUSTOM正文与结构化保存、脚本绑定及删除；STARTING/STOPPING暂禁。提交与应用共持CoreRuntimeManager锁，保存成功但应用失败提供独立重试。删除当前项先切替代项、最后项先停核心，失败保留旧源并尝试恢复；FILE/CUSTOM Rules、授权两目标实下载及本组脚本/全局DNS/规则/Provider覆盖/更多设置/侧载与两HTTP资源实更新已验；外部脚本源生产VM/真实下载/实核运行态集成已由本组补验，完整点击导航及其余字段仍有独立边界。
- 父表单/正文子页和原始并发基线、文件替换及picker令牌保存在noBackup加密草稿；后台am kill后恢复及放弃清除已验。FILE/远程本地副本、脚本和Provider的真实DocumentsUI前台回收进程后返回/取消均已验；FILE/远程外层保存才提交，脚本同名不覆盖，Provider重建绑定并严格复核原输入/缓存摘要后侧载、核心回读。
- 自定义 Profile 新增 Rules 分区快速编辑入口；编辑器逐行校验规则类型和动作，拒绝 `SUB-RULE`，保存时只替换顶层 `rules` 区块，不改写其他 YAML 字段。
- 自定义 Profile 新增 Proxies 分区快速编辑入口；编辑器按 YAML 列表校验代理名称和类型，拒绝重复名称和非列表结构，保存时只替换顶层 `proxies` 区块，不改写其他 YAML 字段。
- 自定义 Profile 的 Proxy Groups 已有可视列表/单组/成员/可选设置首版，保留整列表与单组快捷 YAML；支持复制、删除、排序、引用跟随改名和原子保存，保留未知字段与未编辑分区。批量操作、结构问题提示、核心过滤校验、图标选择、图库/推荐、过滤预设和全局资源映射已有后续首版；真实远程/运行态资源更新和完整视觉仍PARTIAL，证据见顶部相关切片。
- 自定义 Profile 新增 Providers 分区快速编辑入口；分别编辑顶层 `proxy-providers`/`rule-providers` map，校验嵌套 map、非空字段和唯一名称，保存时局部替换并保留边界注释与其他 YAML 字段。
- 自定义 Profile 的代理 Provider 新增字段化覆盖表单；支持健康检查 URL、间隔、超时、lazy、期望状态、名称前缀/后缀、UDP、跳过证书校验和 IP 版本，并将覆盖按 Provider 名称持久化到 Kotlin 自有 `overridesJson`。
- Provider 覆盖只作用于核心启动时生成的暂存 YAML，原始 Profile 文件保持不变；已有 `health-check`/`override` 子字段会局部合并并保留未覆盖字段，避免编辑覆盖后丢失用户配置。
- 自定义 Profile 新增 DNS Overrides 字段化编辑；支持布尔开关、监听地址、增强模式、Fake-IP 地址段、各类 DNS 列表和 Fake-IP 过滤器，DNS 覆盖与 Provider 覆盖共存于同一份 `overridesJson`。
- DNS Overrides 只作用于暂存 YAML；局部合并保留未知字段、原始Profile文件和URL中的冒号。稳定RUNNING的结构化保存/应用已开放，STARTING/STOPPING拒绝；全字段实网仍单列验收。
- 自定义 Profile 新增 Dialers 编辑入口；以“代理名 -> 出站目标”文本维护映射，保存到 Kotlin 自有 `overridesJson`，支持显式空映射清除原有 `dialer-proxy`。
- Dialers 核心应用只修改启动暂存 YAML：清理旧 `dialer-proxy` 后写入当前映射，校验代理和目标存在性，并拒绝经过代理组或代理链形成的循环依赖；原始 Profile 文件保持不变。
- Provider、DNS和Dialers覆盖保存互相保留；稳定RUNNING在管理器共锁下提交并应用，过渡态拒绝。保存与应用失败分别反馈，不能把成功保存视为核心已应用。
- Profile列表整页排序先维护内存草稿，复用整行长按/边缘滚动，取消零提交、失败留页重试、成功返回；列表增删按ID合并，保存后才经ViewModel/Room事务写回orderIndex。稳定RUNNING开放，过渡态拒绝；深浅页和共用拖动已验。
- 自定义 Profile 新增脚本库入口；脚本元数据保存到 Room，正文保存到 `filesDir/scripts/<id>.js`，支持新建、读取、编辑、删除和排序，写入采用原子替换。
- Profile卡片脚本绑定/解绑保存到Kotlin自有`profiles.scriptId`，脚本删除同时清除关联。稳定RUNNING绑定、保存、同步和删除已开放，提交与应用共持管理器锁，只应用受理时当前绑定；过渡态拒绝。
- 脚本在暂存阶段执行，执行失败阻止新输入应用并保留失败反馈；运行态失败修正与独立重试已有分项证据；外部脚本真实下载/改源/同步/实核应用已由本组补验，完整点击导航仍不在本组结论内。
- Profiles 页面视觉首片改为图标顶栏、紧凑配置卡片和菜单式卡片操作；空态展示插画式图形与添加入口，订阅卡显示用量/到期日，存在 URL Profile 时才显示批量同步入口。批量同步仅分发 URL Profile；Repository、Room 和订阅网络行为不因视觉调整改变。
- 自定义 Profile 编辑器首片改为“配置概览 → Rules、Proxies、Proxy Groups、Providers、DNS、Dialers 分区入口 → 原始 YAML 高级编辑”的层级；分区按钮复用已有 ViewModel 编辑器，保存仍由对应 Repository/覆盖模型负责，输入法退出时主动收起以保持手机弹窗标题和操作可见。
- 分区保存成功后同步父级编辑器的 YAML 正文和 Profile 元数据，避免原始 YAML 旧快照覆盖结构化修改；父级仍有未保存 YAML 时，进入分区前要求明确放弃或返回继续编辑。
- 新建配置的未修改空草稿退出时仍删除；Rules、Proxies、Proxy Groups、Providers、DNS 或 Dialers 已持久化修改后退出则保留配置，并提供命名或保留默认名称选项。
- 首页当前配置详情已接通最终输入/核心Provider四项统计、外部明细、HTTP单项/全部更新与失败项重试、逐项安全错误、正文预览/编辑、文件侧载和订阅用量；MRS只读。真实SAF进程回收/侧载、原90个HTTP/mrs分批下载、HTTP/File Proxy已有历史证据；本组新增受控部分失败恢复、HTTP/File各8种与Inline3种规则实际HTTPS命中、原geolocation-cn MRS命中和Inline Proxy原节点展开。正式脚本仅隔离测试运行投影，不推广整份原配置路由/嗅探、全部站点或所有代理协议流量；托管资源混合失败/apply失败尚无新增独立故障注入，完整视觉仍PARTIAL。

### 2026-10-27：Profile DNS Overrides 切片

- `ProfileDnsOverrides` 增加 `enable`、`listen`、`ipv6`、`respect-rules`、`prefer-h3`、hosts、增强模式、Fake-IP 和五类 DNS 列表字段；JSON 往返保留 null 的“未覆盖”语义。
- `ProfileDnsOverrideApplier` 只合并暂存 YAML 的顶层 `dns` 区块；缺少区块时追加，已有区块时局部替换，未知 DNS 字段、Provider 覆盖和其他顶层配置均保留。
- DNS 列表按块序列写回，URL 中的 `:` 不会被误加引号或拆成 YAML map；原始 Profile YAML 不会被覆盖。
- 配置页 Profile 卡片新增 DNS 入口和字段化对话框；核心运行期间入口、保存和 ViewModel 写入均受保护。
- 新增 DNS JSON/YAML 合并、ViewModel 运行态保护、Compose 表单回调和真实 Room/Repository 共存测试。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 168 项 JVM 单元测试失败 0、错误 0、跳过 0，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：首次发现 1 项测试未滚动到 LazyColumn 内字段，修复测试定位后重跑通过；最终 40 项 instrumentation 失败 0、错误 0、跳过 0，其中 Room 6 项、Compose 30 项、WebView JavaScript 4 项。
- Debug APK SHA-256：`2ee8c4bdd81fffd2018f1595fe64b95cd7d3022a4f25f9149b5ae901b5bafd9e`；APK 原生库全部位于 `lib/arm64-v8a/`。本轮未访问真实订阅、VPN 或核心启动。

本轮自动化与真机证据（2026-10-25，设备 `wsvwypiz7xwslvl7`）：

- `:app:testDebugUnitTest` 通过，新增 Deep Link 解析用例覆盖编码 URL、四种协议、非法 Host、非 HTTP(S) 目标和缺失参数。
- `:app:assembleDebug` 通过，APK 只包含 `arm64-v8a` 的原生库。
- 通过 `kclash://install-config` 触发当前授权订阅导入，设备 UI 显示远程订阅及用量信息；随后删除该测试 Profile，恢复空配置状态。
- 强制停止应用后再通过 `kclash://install-config` 冷启动导入成功；再次发送同一链接显示“该订阅已经添加”，没有创建重复 Profile。
- 二维码解析分支已通过 JVM 单元测试和 Debug 构建；两条当前授权路径均已通过二维码图片在真机导入，第一条显示订阅用量，第二条显示 `clash-1220f4ac` 远程订阅。
- 第二条路径首次暴露 HTTP 301 跨协议重定向缺口，已限制放行 `[REDACTED_SECRET]` 的明文入口并由订阅客户端手动跟随 HTTP/HTTPS 重定向；新增本地 301→200 回归测试。
- 第二条二维码重复导入显示“该订阅已经添加”；验收结束后两个测试 Profile 均通过应用内删除，设备恢复“还没有配置”空态。

### 2026-10-27：Profile 排序切片

- 配置页新增“排序”入口和排序弹窗；每行提供“上移/下移”，保存前只修改内存草稿，取消或返回不会持久化中间顺序。
- `ProfilesViewModel.reorder` 复用核心运行态修改保护，并将弹窗顺序与最新 Profile 列表合并，避免排序期间新增配置被遗漏。
- `ProfileRepository.reorder` 在 Room 事务内过滤未知/重复 ID、补齐遗漏 Profile 并重新编号 `orderIndex`，当前选中 Profile 只跟随 ID 不因排序改变。
- 新增 2 项 `ProfilesViewModel` JVM 测试、1 项排序 Compose 测试和 1 项真实 Repository/Room 排序持久化测试。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 152 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；26 项 instrumentation 测试失败 0、错误 0、跳过 0；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK `android/app/build/outputs/apk/debug/app-debug.apk` SHA-256 为 `d1f4098ea916501f595988ca9e398397defb4286d409b37fbd98366be160465e`，APK 原生库仅包含 `lib/arm64-v8a/`；本轮未访问当前 `AGENTS.md` 列出的两条真实订阅路径、VPN 或核心启动。

### 2026-10-27：Scripts 本地资源管理基础切片

- 新增 `ScriptEntity`/`Script`、Room `scripts` 表和 `MIGRATION_3_4`；脚本正文独立保存到应用私有 `filesDir/scripts/<id>.js`，不读取或迁移 Flutter/Drift 数据。
- `ScriptRepository` 支持新建、读取、编辑、删除和排序；名称去重、空内容拒绝、文件原子替换、Room 元数据与正文失败回滚均在 Repository 层处理。
- 配置页新增“脚本”入口、脚本库和编辑器；ViewModel 通过不可变 `StateFlow` 暴露列表和编辑状态，核心运行期间拒绝修改并保留错误反馈。
- UI 文案明确当前只完成本地资源管理，Profile 绑定、远程同步和 Mihomo JavaScript 执行留待后续切片，避免用户误以为脚本已生效。
- 新增 3 项 `ScriptsViewModelTest`、2 项 `ScriptsUiInstrumentedTest`、1 项 `ScriptsRepositoryInstrumentedTest`，并新增 Room 3→4 migration 覆盖；全工程测试计数为 155 项 JVM、30 项 instrumentation。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；JVM 单元测试 155 项失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；30 项 instrumentation 失败 0、错误 0、跳过 0，其中 Room 5 项、Compose 25 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `b3f1e148bbe18e7a23db29cb689104678b8bd8ef73bd15cccc715c0fdfd5ff97`，APK 原生库全部位于 `lib/arm64-v8a/`；本轮未访问当前 `AGENTS.md` 列出的两条真实订阅路径、VPN 或核心启动。

### 2026-10-27：Scripts Profile 绑定切片

- `ProfileRepository.updateProfileScript` 校验目标脚本存在后持久化 `profiles.scriptId`；支持绑定和解绑，不读取或迁移 Flutter/Drift 数据。
- 配置页 Profile 卡片新增脚本绑定弹窗，显示当前绑定、解绑入口和脚本库跳转；核心运行期间由 `ProfilesViewModel` 与 Compose 入口共同拒绝变更。
- 删除脚本时在 Room 事务内清除所有 Profile 的 `scriptId`，再删除脚本元数据和应用私有正文文件，避免后续执行链读取失效引用。
- 新增 2 项 `ProfilesViewModel` JVM 测试、1 项脚本绑定 Compose 测试、1 项真实 Repository/Room 持久化测试，并扩展脚本删除测试验证绑定清理。
- `./gradlew :app:testDebugUnitTest --no-daemon --rerun-tasks`：通过；全工程 157 项 Kotlin 单元测试失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；32 项 instrumentation 失败 0、错误 0、跳过 0，其中 Room 6 项、Compose 26 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `4dfa7e1632017ec276b4a68df2d7ba878d50f3ee2dd8894bbca1c356f5bb5150`，APK 原生库仅包含 `lib/arm64-v8a/`；本轮未访问当前 `AGENTS.md` 列出的两条真实订阅路径、VPN、核心启动或 JavaScript 执行。

待完成：

- 正文查找/替换/撤销重做、退出确认和父草稿已验；本组补真实YAML行列/UTF-16错误定位、重复跳转及选区不改草稿。脚本、Provider正文/选项、规则资源和本组规则/策略组/Provider快捷YAML错误恢复已分批覆盖；剩余未覆盖入口、进一步语法体验和8MiB性能待补。
- 完整页面级 E2E；本地 SAF 备份恢复首片和 WebDAV 手动备份（含凭据、UI、仓库、认证/离线反馈和本地回环真机回归）已完成，外部服务回归和云端同步仍待后续。
- 唯一授权源两临时目标在稳定RUNNING真实下载并应用、全局同步入口分发已有前组证据；既有生产调度自然到期下载/应用已由11-05独立1/1覆盖。CUSTOM引用在线源手动/到期Repository自动更新已由本组3/3补验；在线age受当前授权源明文/无密钥限制，其余未覆盖实网变更仍待验，不要求严格20分钟调度。
- FILE/CUSTOM结构化运行态保存/应用、当前项删除回退、整页长按排序、父/正文加密草稿新进程恢复已验；FILE/remote/脚本/Provider真实SAF等待中进程回收后返回及取消已验。后续补未覆盖编辑入口/面板与异常恢复、其他资源SAF/外部脚本新增集成范围见本组证据，完整导航E2E与完整视觉继续待补；配置正文错误行定位已由本组补齐，备份/系统恢复暂缓。

完成门槛：

- 远程订阅、文件、手工配置、二维码、Deep Link 五种入口的行为一致。
- 编辑内容通过 Mihomo 校验后才能应用。
- 订阅更新失败不会破坏上一份可运行配置。

### 7.4 代理组完整交互

保存窗口最新（11-05）：策略组结构化/原始YAML及本地节点提交前后、实际引用退出应用前后已纳入本组15场景；已提交恢复不重写，退出恢复等待显式重试。见`editor-commit-window-redmi-20261105.json`；Fallback冷进程已在11-06完成，见`fallback-cold-process-redmi-20261106.json`；自然无效`now`按当前Mihomo合法解析契约保留为不可自然构造边界，完整视觉仍待补。

子页冷恢复最新（11-05）：规则YAML/类型选择器、成员选择/排序和组列表批选已完成本组六场景真实PID回收恢复；整组/单组YAML、图标/选项/值保留前组八场景证据。见`docs/acceptance/remaining-editor-cold-redmi-20261105.json`；保存窗口以本节顶部新证据为准，完整视觉仍待补。

运行态节点最新（11-06）：正式MainActivity中添加本地目录、逐字段落库、重复提示和真实Activity重建回读已通过，核心计时与输入保持；见`subscription-auto-cycle-redmi-20261105.json`的最终7/7批次。更新授权源的Fallback真实核心now/fixed/清除固定/非法成员拒绝已完成8阶段实核，见`fallback-real-core-redmi-20261105.json`；固定选择跨真实force-stop冷进程恢复、清除固定回到有效自动now已通过，见`fallback-cold-process-redmi-20261106.json`。自然无效`now`在当前Mihomo合法解析契约下不可自然构造，保留边界；完整视觉仍待补。

状态：`PARTIAL`。

最新卡片语义补验（2026-11-06）：computed URLTest 的自动 `now` 与 `selectedMap` 用户优选并存时，Tab/List 两种布局分别将香港节点标为当前使用、将日本节点标为优选；不会把优选误示为当前出口。Redmi 精确用例 `ProxyPageParityInstrumentedTest#computedGroupSeparatesAutomaticNowFromPreferredNodeMarker` 通过1/1，两张截图均已目视。证据见`docs/acceptance/proxy-computed-now-preferred-redmi-20261106.json`和`docs/visual-baselines/proxy-computed-now-preferred-redmi-api36.sha256`；完整 Flutter 卡片/子页视觉仍未完成。

本组补充：加载/失败/无组和Tab/List搜索空态、真实IME避让、清除搜索保留选择与首页/代理页刷新同一门禁已验；最终同批15/15及截图见`main-page-states-redmi-20261104.json`。

前序菜单补充：保留父卡与独立滚动的多级展开/收回、同弹簧反向、外部祖先同步、触发行标题和父卡缩小/压暗已接通；API33+菜单折射/高光和真实PixelCopy玻璃子卡深浅主题通过。final12同批22/22含三级栈、实时clip释放命中、原指针携带、长菜单/系统Back，见`glass-stack-subpages-redmi-20261104.json`。完整通用玻璃品质与其他页面仍PARTIAL。

已完成首片：

- Tab/List 双布局、横向分组切换、节点网格、搜索、单节点/批量测速、刷新、Profile 当前组记忆和 List 三种图标样式。
- 主导航整页300ms滑动、固定Dock、跨页/快速反向与滚动草稿保持；代理标签即时目标、居中和末端渐隐，以及半程取消/远距离切换均已通过本轮14项UI/协议批次中的相关用例。
- 工具菜单排序、网格密度、卡片尺寸、隐藏超时节点、Tab 当前节点定位及应用级偏好保存；切换失败保留此前的 Profile 映射。
- Dashboard 节点弹层的键盘首排可见性和测速入口已完成切片；有效配置加载后，停止态/运行态均可显示节点、切换和测速。
- Dashboard 代理组摘要与选择弹层：Rule/Direct 过滤 hidden，Global 展示全部；Direct 和停止态仍可管理节点、选择和测速；连续选节点不关闭，复用正式代理页偏好并隔离搜索状态。
- Kotlin Bridge 已保留完整 `proxies` 叶子条目的真实协议类型；Tab/List 节点卡片和嵌套组摘要会按叶子类型显示，不再因只存在于 `proxies` 而显示空白。
- Tab 按 Profile/组隔离滚动状态，List 按 Profile 恢复位置；跨远距离组切换、布局切换、导航返回和 Profile 切换后，当前组滚动位置仍可恢复，List 可定位组内当前节点。

待完成：

- URLTest/Fallback 的有效核心 `now` 优先、手动固定、再次选择清除固定和非法成员拒绝已由真实 Mihomo 真机回归覆盖；URLTest已有正延迟优选及跨进程恢复，更新后的唯一授权源又提供了7成员非空Fallback，在停止态/运行态各完成自动、固定、非法成员拒绝、清除固定4阶段，8/8次核心回读均为组内有效`now`。见`docs/acceptance/fallback-real-core-redmi-20261105.json`。Fallback固定选择跨真实force-stop冷进程恢复及清除固定已通过，见`docs/acceptance/fallback-cold-process-redmi-20261106.json`。自然无效`now`经核对当前Mihomo语义不能由合法配置、断网或全不健康成员自然产生；无有效`now`时映射/`fixed`回退的Bridge→VM→Tab/List×深浅主题协议验收仍是注入边界，不替代核心自然故障实核。
- List/Grid排序、搜索过滤、网格密度变化后的语义锚定已有实现、JVM覆盖和Redmi精确方法`gridSemanticAnchorSurvivesSortDensityAndSearchChanges`真机验收；工具栏、卡片与组标题首版视觉已完成。节点菜单停止/运行态保存、重复提示及Activity重建回读已通过本轮各1项正式宿主用例；配置页菜单/预览交互3项、Providers行菜单、Provider资源页菜单、代理组编辑列表菜单各自1/1，相关页面/正文入口批次也通过。上述旧批只证明静态画面与终态交互；本轮已补主导航与代理Pager中间帧、快速反向、取消及状态恢复，见`docs/acceptance/page-continuity-providers-redmi-20261104.json`。运行态添加到本地目录已由11-05本组正式MainActivity落库/重建回读完成；其余面板及未覆盖页面整页视觉仍待补齐；液滴/玻璃菜单动效与API27 fallback已有独立验收。
- 按组/测速 URL 的成功、超时、失败和进行中摘要已有实现及 JVM 覆盖，不使用缺失模型伪造计数。递归解析仍使用完整组图，保持 Flutter 平面列表，不新增上游没有的子组下钻。
- 延迟结果持久化已有 Room 实现、45 秒 TTL、过期清理、DAO/迁移与 ViewModel 重建 JVM 覆盖；Redmi 真机正式 Profile 通过真实 `force-stop`/新进程验证缓存恢复，并确认旧 Profile 与旧测速 URL 的同名节点不进入当前页面（`DONE-VERIFIED`）。
- 首页/代理组主流程此前同批9/9，选择保存跨revision与Activity重建、旧渲染ID隔离、模式/测速和读失败重试已验。URLTest正延迟手动优选跨冷进程恢复和清除固定此前通过；Fallback协议边界、非空组真实核心8阶段以及固定选择冷进程恢复均已通过，见两份Fallback证据；自然无效`now`按当前Mihomo合法解析契约保留为不可构造边界。真实加密在线订阅与其他资源SAF也保留独立边界；运行态保存、父/正文进程草稿及四类SAF恢复已有前组证据。

完成门槛：

- Selector、URLTest、Fallback、LoadBalance 都能按照 Flutter Android 行为展示。
- 切换、测速、刷新并发时不会覆盖其他操作的状态。
- 所有失败都保留核心错误，不使用历史成功状态掩盖失败。

### 7.5 Android 平台入口

最新系统切片（11-05）：本组核心控制器KCORE-002/003已修，build2/767 JVM、Go父模块全量race265/265和压力40/40、route10通过；Redmi final2控制器6轮及模式/组件/菜单共4/4，Always-on-final1零额外等待冷启动/SIGKILL首个TUN HTTPS均200/同socket归因，纯代理独立恢复及保护通过。生产Always-on识别仍UNKNOWN；旧TLS EOF因果/长期稳定性、首次安装、蜂窝、第三方/OEM待验。见`controller-lifecycle-redmi-20261105.json`。

状态：`PARTIAL`。

已完成：

- 前台通知保留 STOP 操作；通知动作通过受保护的 `QuickActionActivity` 进入 `CoreRuntimeManager.stop()`。
- Android 13+ 首次进入 `MainActivity` 时申请 `POST_NOTIFICATIONS`，避免用户看不到前台服务和停止入口。
- 桌面静态快捷方式提供启动、停止、切换三个入口；三个入口均通过同一个 `QuickActionActivity` 进入 `CoreRuntimeManager`。
- Quick Settings Tile 提供启动/停止切换入口；Tile 状态由 `CoreRuntimeManager.state` 映射，点击通过 `QuickActionActivity` 折叠系统面板并复用 `TOGGLE`。
- 外部 Action 提供 `com.lonnnnnng.flclash.action.START/STOP/TOGGLE`；公开入口只接受固定 action，拒绝 URI 和 extras，再转发到内部 `QuickActionActivity`。
- 电池优化状态和系统引导位于工具→常规→按需连接前置条件；由PowerManager.isIgnoringBatteryOptimizations()回读，不能以点击结果代替系统授权。首页不再默认显示该卡片。
- `QuickActionActivity` 使用 `VpnService.prepare()` 处理启动入口的系统授权边界；自身不导出，快捷入口使用显式组件调用。
- AndroidManifest、快捷方式 Intent 和组件导出边界已完成基础安全核对。
- 按应用代理首片已接入 Room 全局设置、已安装启动器应用列表、应用名/包名搜索、白名单/黑名单选择、批量选择和核心运行中编辑锁定；启动 VPN 时由 `DefaultVpnOptionsProvider` 读取并清洗包名列表，最终由 `VpnService.Builder.addAllowedApplication/addDisallowedApplication` 应用。
- 按应用代理真实核心流量回归已完成：使用工作区根目录 `1.yaml` 启动真实 Mihomo，在 Redmi 真机上用第二条授权路径发起同一受控请求；黑名单选中 `com.mmbox.xbrowser` 时该 UID 只有 `wlan0` 统计、没有 `tun0` 记录，白名单选中同一包名时出现 `tun0` 统计（UID `10785`，约 15 KB 入站、7.8 KB 出站），对照证明 Android 包过滤边界已实际作用于 TUN。

本轮验收证据（2026-10-26，设备 `wsvwypiz7xwslvl7`，Android 16 / API 36）：

- `./gradlew test :app:assembleDebug --no-daemon`：通过；工程 Kotlin 单元测试 98 项，失败 0、错误 0、跳过 0；APK 仅包含 `arm64-v8a` 原生库。
- 安装 Debug APK 后，系统 `cmd shortcut get-shortcuts` 返回 `start`、`stop`、`toggle` 三个静态快捷方式；启动器长按 KClash 图标显示“启动 KClash 核心”“停止 KClash 核心”“切换 KClash 核心运行状态”。
- 从启动器点击“启动 KClash 核心”后，`VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由出现；点击“停止 KClash 核心”后服务和路由均清理。
- 从启动器两次点击“切换 KClash 核心运行状态”，分别验证启动和停止；停止后设备恢复无 KClash 服务、无 `tun0` 的状态。
- 通过 `adb -s wsvwypiz7xwslvl7 shell cmd statusbar add-tile com.lonnnnnng.flclash/.KClashTileService` 将 Tile 加入系统快捷设置；点击后启动核心，系统面板系统描述为“`KClash：核心运行中`”。再次点击后服务、`tun0` 和路由清理，面板描述变为“`KClash：核心已停止`”。
- 通过 ADB 隐式 Action 验证 `START`、`STOP`、`TOGGLE`：启动/停止服务与 `tun0` 状态均正确切换；携带额外参数的 Action 被公开入口拒绝，未启动服务。
- 首页“后台运行”卡片显示真实未忽略状态；点击“打开电池优化设置”进入系统 `RequestIgnoreBatteryOptimizations` 页面，返回后状态仍由系统值刷新，本轮未替用户点击“允许”。
- 验收 Profile 已通过应用内删除，首页恢复“还没有配置”。

待完成：

- Tasker/MacroDroid 实际应用回归（当前已完成 ADB/Intent 契约验证）。
- 完整 Flutter Access 页面 parity（选择器、持久化、服务参数、真实核心流量回归、关闭状态遮罩/一键开启、排序和系统/无网络过滤、智能选择、剪贴板导入/导出首片、Access 卡片顶栏搜索/全选入口、窄屏 FlowRow、横屏重排、1.5/2.0 倍字体可达性、系统字体 1.3/2.0 回归、保存后重建恢复、顶栏脏状态保存/菜单层级、完整搜索态、无结果搜索恢复、当前结果全选/清除与放弃修改真实宿主 E2E、默认卡片截图基线、关闭态/脏状态截图基线、1.3/1.5/1.8/2.0 字体截图矩阵和竖屏/横屏截图矩阵首片已完成；跨设备的竖屏默认字号截图和设备稳定回归仍待补齐；历史横屏/字号矩阵仅存档，不再扩展）。
- Always-on VPN 的完整配置和系统设置兼容。
- 组件导出/固定Intent/权限已有自动化契约审计，继续补第三方Tasker/MacroDroid和OEM系统行为，不能将ADB契约视为第三方应用E2E。

完成门槛：

- 外部入口和页面入口进入同一个 `CoreRuntimeManager`，不产生第二套启停逻辑。
- 被系统拒绝或权限缺失时可见地失败。
- 所有导出的组件都有明确的最小权限边界。

### 7.6 UI/UX 完整对齐

KUI-019继续优化菜单动态轮廓：阴影局部重组、同帧Path/Paint复用、宽度延迟布局及移除重复裁剪。Redmi API36独立非debuggable包、无PNG/录屏，前后各两轮深浅，菜单合计jank331/1429（23.16%）→261/1555（16.78%），顶卡收回38.61%→14.07%；首开26.42%→23.16%改善有限，关闭6.91%→9.94%退化保留，首次打开UI区间中位也未改善。完整自然流程各深浅一轮非启动窗口15.10%→13.39%，不冒充正式R8运行。final-build3共849项JVM零失败/错误/跳过（App568在build1执行、其余281复用，最终四模块UP-TO-DATE）；Debug/Test/未签名Release-R8/双Lint通过，Debug0错误68警告14提示、Release0错误66警告13提示。同套最终包回归19/19、自然整页2/2、三轮菜单2/2；92PNG/32拼图及8组原PTS抽样已核对，593源码与安装回拉匹配。用户确认重置，当前正式0份配置，排除依赖非空库的两条导航回归；Flutter参考包保持缺席、不安装。44份正式快照及最终独立检查一致，原1.yaml/设备设置保持；独立.motion双包与独占文件精确回收，结束无服务/tun0。旧包/候选12图逐字节一致后刷新环境基线，14/19、旧包0/5与run-as拒绝回拉失败均保留。整体性能和完整Flutter视觉仍PARTIAL；下一步优先首开、关闭退化及RenderThread慢帧，再补CUSTOM组数、首页无流量统计、内存图形与其余子页。玻璃默认关闭，既有边界保持。证据`docs/acceptance/menu-rendering-redmi-20261108.json`，溯源`docs/menu-rendering-performance-defect.md`，附件`../acceptance-artifacts/menu-rendering-20261108/`。

本组补充（11-06，代理组状态转场）：代理组 loading/error/empty/content 使用 300ms 入场与 150ms 退场，内容退场保留最后快照并隔离触摸；错误、空态、Dashboard 摘要和恢复路径在 Redmi 上精确5/5通过，证据`proxy-groups-transition-redmi-20261106.json`。本轮继续完成 List 组头 sticky/raised 对齐：`ProxyGroupsPage.kt` 在滚动时以180ms动画提升组头阴影/色调层级，长列表吸顶与远端 Tab 连续转场在 Redmi 精确4/4通过，静止/吸顶截图及哈希见`proxy-list-header-redmi-20261106.json`与`../acceptance-artifacts/proxy-list-header-20261106/`。随后补齐当前节点徽标语义验收：Tab/List 各卡片尺寸与深浅主题均通过，List 从香港节点切换到日本节点后徽标唯一迁移，精确1/1结果和三张截图见`proxy-current-marker-redmi-20261106.json`，独立哈希基线见`docs/visual-baselines/proxy-current-marker-redmi-api36.sha256`。本轮再补 List 组头—节点行连续展开/收回：组头底部圆角240ms连续过渡，节点行按可见顺序错峰淡入/纵向收敛并使用`animateItem`处理插入、退场和布局移动；展开中间帧与稳定帧不同，收起后节点语义节点消失，精确4/4结果、26张截图和源码/包哈希见`proxy-list-animation-redmi-20261106.json`与`../acceptance-artifacts/proxy-list-animation-20261106/`。新增空态三套900ms固定入场编排：Profiles 使用 SPIN、搜索空态使用 MORPH、代理组空态使用 ORBIT，JVM 终帧/中间几何单测通过，Redmi 三组既有空态与代理组回归共16/16；38张截图、原始通过日志和重跑诊断边界见`empty-state-motion-redmi-20261106.json`与`../acceptance-artifacts/empty-state-motion-20261106/`。这些证据只覆盖目标组头、转场、当前徽标、List 展开动效和空态入场切片，不扩大为完整 Flutter 逐像素视觉；剩余子页排版、统一截图基线、全应用动效与性能仍为`PARTIAL`。Fallback真实核心与冷进程证据沿用本节前述记录，自然无效`now`仍按合法解析契约记为不可自然构造边界。

历史边界（11-05，todo第6项）：IP/质量与服务页面10/10回归，14PNG/3拼图已目视；仅验证既有页面和缓存行为，不新增Flutter全应用视觉/缓动或性能结论。最新构建/安装/保护异常见第3/7.7节和`ip-quality-cache-redmi-20261105.json`。

历史边界（11-05）：第2项保存/退出死亡窗口已验，15次首次恢复与5次再次冷恢复及21项回归通过；此批证明持久化恢复和正式入口交互，不新增完整Flutter视觉/缓动或性能结论。

前组边界（11-05）：todo第1项六场景冷恢复及18项回归通过，31张PNG已目视；此证据不扩大到Flutter全应用视觉/缓动或性能。最新快照见第3/7.7节和`docs/acceptance/remaining-editor-cold-redmi-20261105.json`。

第一步指定页面的返回/草稿/拖回与退场正文已由最终44/44集中回归，81张PNG已目视；空态SPIN/MORPH/ORBIT已由11-06切片补齐编排和定向真机验收，全应用逐屏视觉、统一动效、实时背景和性能仍待后续。证据`stage-one-interactions-redmi-20261105.json`。

状态：`PARTIAL`。

历史执行切片（2026-11-07，Provider运行态）：Provider本组已验：MRS只读正文首次/再次冷恢复及原面板返回通过；纯代理保存前后SIGKILL经外部独立观察系统新PID/前台服务，恢复不自动重放、显式重试仅一次；停止态/运行态各源文件或源记录缺失4类真实setup拒绝后回滚旧输入/选择/会话并成功切往有效目标，epoch各增加2。KUI-013父面板冷恢复后错误常驻已红绿复现并修复，KUI-010补齐实核回滚证据。Redmi final7运行态/回滚6/6、12次同socket核心归因HTTP200，MRS 1+1、相邻回归10/10，22张PNG完整校验及8拼图目视。build7 App549实际执行、Service212/Core38/Common31复用，共830项JVM零失败/错误/跳过；389任务28执行/361复用，Debug/Test/未签名Release-R8及双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。安装回拉及579份Android源码匹配；正式两配置/正文SHA-size-mtime/偏好/脚本/选择、原1.yaml/Flutter包/设备基线保持，临时对象/密文/标记清除，无服务或tun0。MRS为真实MainActivity STOPPED，首次存活PID受控force-stop、再次恢复前进程已退出，运行态为Debug宿主真实VM/Room/核心的纯代理SIGKILL，非VPN/OEM自然回收或完整点击导航。final6-before的verify超时原因未定，后续通过不抹除；第6项原保护异常、未知偏好变化与网络稳定性仍待查。选项错误截图滚动后提示部分裁切、筛选与保存图含转场中间帧，下一组补键盘错误可见性、稳定终帧及剩余子页/面板，再集中Flutter排版/连续动效；独立全局规则页/资源选择器冷进程另列。效果/性能/兼容/发布按原顺序，蜂窝延期、玻璃默认关闭。证据`docs/acceptance/provider-runtime-redmi-20261107.json`；溯源`docs/provider-cold-parent-refresh-defect.md`；附件`../acceptance-artifacts/provider-runtime-20261107/`。

前序已验（2026-11-06，Provider正文真实主宿主）：真实MainActivity首页配置详情→Provider正文链路已验：真实侧载失败保留长草稿/错误/选区并跨后台和Activity重建，放弃确认重建后可继续/放弃；有效缓存保存后退出、重建、重开回读且资源源文件不变；原配置MRS只读预览滚动位置跨重建保持并返回同一面板。Redmi final2精确9/9，21张PNG哈希/解码验证并经4张拼图目视；775项JVM XML零失败/错误/跳过（本次四模块任务均UP-TO-DATE），Debug/Test、未签名Release-R8和双Lint通过。最终双包实际回拉匹配，两份正式FILE/URL配置、正文/元数据/偏好/选择、原1.yaml、Flutter基线和设备设置保持，临时配置及自身投影缓存清理，收尾无服务。证据`main-provider-document-redmi-20261106.json`。Provider正文正式宿主的Activity重建本组已补验，冷进程、运行态侧载、其余子页/面板、统一Flutter视觉/动效仍PARTIAL；继续todo第9项，再统一截图基线和连续动效，第10后排、玻璃默认关闭。首轮IP缓存保护变化与小MRS滚动前提失败单独保留。

前序已验（2026-11-06）：真实MainActivity代理页往返/Activity重建及相邻List回归9/9，27图哈希验证并目视。KUI-001已修复List恢复14px偏移与吸顶标题消失；首个实际布局后重放保存位置，原1px断言及新增标题坐标断言通过。775项JVM XML零失败（App499本轮重跑，其余UP-TO-DATE），Debug/Test/Release-R8/双Lint通过，双包实际回拉匹配。两份正式配置/选择/正文/元数据/偏好、Flutter基线、原1.yaml和设备设置保持，收尾无服务。证据`main-navigation-state-redmi-20261106.json`及缺陷记录`proxy-list-restoration-defect.md`。本组只闭环代理浏览状态的Activity重建；Provider正文正式宿主/冷进程、其余子页/面板、异常恢复和统一Flutter视觉仍待补。继续todo第9项，再统一截图基线与连续动效；不新增TalkBack/无障碍。第10项后排、玻璃默认关闭。

前序已验（2026-11-06）：Provider正文生命周期本组已完成：后台只暂停列表轮询，保留草稿/失败/读取；放弃确认、选区跨Activity重建保持，受理保存只提交一次；首读就绪标记防止初始化空快照误关详情。Redmi精确6/6、10张PNG校验并目视，四模块JVM775项零失败，Debug/Test、未签名Release/R8及独立双Lint通过，见`provider-document-lifecycle-redmi-20261106.json`。用户确认手动重装后已按授权恢复本地FILE和远程URL两配置，最终复用且元数据/正文/偏好/选择前后保持；本组固定宿主不证明冷进程、正式MainActivity恢复或Provider实网。此前首页面板7/7、代理页11/11及List动效16/16等保持具名历史。下一步按todo第9项继续配置/代理组剩余子页、面板返回、空态和异常恢复，再统一截图基线与连续动效；不新增TalkBack/无障碍功能。第10项实时背景/触点光效、玻璃品质与8 MiB性能后排，玻璃默认关闭。

此前已验（11-05）：规则编辑的原行身份、批选及保存中状态可重建恢复；源行已变更时阻止误新增。单策略组快捷YAML返回先校验并更新父草稿，错误可继续或放弃；Provider长文保存失败与同文案重试自动回顶。服务详情/管理/质量标题正文连续过渡，独立页内容和滚动状态解决返回后列表回顶；面板退场阻断业务触摸与按键。App451/Service193/Core38/Common31共713项单测零失败/错误/跳过；build4 Debug/Test、未签名Release/R8及独立lint4双变体通过，各0错误/64警告（Debug10/Release9提示）。Redmi最终生产/测试包同批final5 28/28，63张PNG已目视，安装哈希匹配。首批26/28、两次6/7、motion4补验1/1及系统设置覆盖页面的final4 27/28独立保留、不累计。正式1份配置/原选择/正文SHA-size-mtime及逐字段摘要保持；原1.yaml双端、Flutter APK及旋转/字号/动画设置保持，结束无服务/tun0。证据`docs/acceptance/main-subpages-recovery-redmi-20261105.json`。整体Flutter视觉仍PARTIAL；服务子页边缘拖回、精确缓动/全应用视觉、未覆盖子页/空态/异常恢复与性能仍待补。本组规则重建是保存状态模拟恢复，未做冷进程；未重跑实网/核心/VPN、低API、Go、正式签名运行或远端CI。玻璃默认关闭，Dock/按钮实时背景和触点光效后排；备份/系统恢复、观测次要打磨、语言/主题继续暂缓。

前序编辑器已验（11-04）：脚本、Provider正文、选项、过滤预设和规则资源错误体验已补齐本组范围；选区移动不清失败，长文查找显式滚动，已提交回读失败只允许重新读取，保存失败自动回到错误且保留草稿。App451/Service193/Core38/Common31共713项单测零失败/错误/跳过；最终build4 Debug/Test、未签名Release/R8及独立lint4双变体通过，各0错误/64警告（Debug10/Release9提示）。Redmi最终App/Test同批final4 43/43，69张PNG已目视，安装哈希匹配；首批40/43独立保留、不累计。正式1份配置/原选择/正文SHA-size-mtime及逐字段摘要保持，原1.yaml、Flutter APK和设备设置保持，结束无服务/tun0。详见`docs/acceptance/editor-errors-recovery-redmi-20261104.json`。

前序共享材质已验：共享六种玻璃材质及STANDARD/ULTRA等级、默认关闭、菜单折射/高光/液滴回源、多层子菜单可逆展开、策略组/更多/链式代理覆盖子页和API27离线降级；Redmi final6 19/19、API27 final6 10/10，709项单测和独立双Lint通过，证据见`general-glass-subpages-api27-20261104.json`。API24/25、31/32及其他ABI、实网/VPN/核心均未覆盖；API27设置恢复不能单凭该JSON确认。Redmi正式配置/选择/正文聚合保护日志为count=1且before/after SHA一致，逐字段摘要未嵌入JSON。早期Room元数据兼容不属于开发阶段要求。剩余视觉范围为其他空态/页面、完整采样品质、全应用动效/逐像素一致性和性能profile；主题设置仍暂缓，整体状态保持`PARTIAL`。

当前已有：

- Compose + Material 3，默认Flutter蓝色色板/字体语义槽已实际导出对齐，31个glyph和空态曲线已移植；主题设置功能继续暂缓。
- 仪表盘、代理、配置、工具四个手机主 Tab；无配置时隐藏代理 Tab。连接和观测从工具或可选连接统计卡进入，观测页包含 Requests、DNS、Logs 三个页签。
- Edge-to-edge 基础设置。
- Access 卡片首轮响应式收敛：标题/状态/排序/筛选区域可换行，已完成横屏重排、1.5/2.0 倍字体 Compose 可达性回归、系统字体 1.3/2.0 宿主回归、Redmi 默认卡片 PNG 哈希基线、1.3/1.5/1.8/2.0 字体截图矩阵和竖屏/横屏截图矩阵首片；真实宿主保存后重建可恢复 Room 设置；脏状态下提供标题栏保存、Flutter 风格主菜单/设置/操作层级和标题栏完整搜索态，正文操作入口保留为小屏备用。
- Profiles 页面视觉首片：Debug 固定宿主覆盖空态、选中自定义配置和远程订阅用量三态，并包含正式页面顶栏；基线固定为 Redmi Note 8 Pro、Android 16/API 36、竖屏、系统默认字号和深色主题，记录在 `docs/visual-baselines/profiles-states-redmi-api36.sha256`。旧横屏/字号哈希仅保留为历史证据，不再执行相关用例。
- Proxy 节点卡片视觉首片：两行名称、22dp 当前使用/优选徽标、独立测速动作；测速布局按详情行高绘制，由 Compose 扩展触摸命中范围；computed 当前使用优先读取有效核心 `now`，无有效 `now` 时回退保存映射与 `fixed`，优选仍读取保存的 `selectedMap`。代理组主页已支持 Flutter 对应的 Tab/List 布局切换：Tab 保留横向可滚动标签和组内节点网格，List 显示分组标题、组图标样式（填充/简洁/隐藏）、分组批量测速和节点网格；两种模式共用搜索、选择、测速、排序、隐藏超时和 Profile 组记忆。新增代理工具菜单（排序：默认/延迟/名称；网格密度；卡片尺寸；隐藏超时节点；布局；List 图标样式）、应用级偏好持久化和当前节点定位；叶子协议字段、按组滚动恢复和 List 组内定位已完成，竖屏默认字号下新增精确回归和深浅色页面基线已通过。工具栏、卡片与组标题首版已收敛，详见同日代理页整体首版记录；连续超椭圆、子树焦点和绘制按压已由11-04视觉切片覆盖，完整动效仍待补齐，节点菜单/添加本地及远程组图标已有后续首版实现，停止态菜单到正式本地目录的新增/重复提示/Activity重建回读已验，运行态添加已由11-05本组落库/重建回读补齐，完整视觉继续待补。
- List 模式组折叠/展开使用 Profile `unfoldSet` 持久化，空集合初始折叠；标题和展开图标均可切换节点区域，保留批量测速入口。ViewModel、Compose List、Dashboard 代理弹层和真实 Room 持久化均有 Redmi 竖屏默认字号定向回归；完整 List 页面视觉 parity 仍需继续逐项对照 Flutter。
- Connections / Requests / DNS / Logs 记录卡片视觉首片已完成：统一头部时间与类型、24dp 独立记录卡、规则/代理链、DNS 响应和状态标签；记录内容视觉切片已有深浅色基线，Connections/Observation 页面级截图和详情基线复核一致。Redmi 真机 13 项定向交互回归通过。仅使用现有模型真实字段；单连接实时速率、相对时间及完整页面 parity 保留后续。
- Requests/DNS 详情与完整列表首片已完成：记录行进入两档底部面板，DNS 详情分组/空值隐藏/长 IPv6 和错误复制有深浅色八状态基线；详情水平边距已调整为 16dp。全屏列表提供返回、搜索、DNS 清理工具栏和关键词筛选，DNS 详情及列表筛选可在 Activity 重建后恢复；DNS 清理使用 CoreRuntimeManager/CoreBridge，固定宿主覆盖成功/失败反馈。固定宿主证明 KClash 自身渲染稳定，不代表 Flutter 逐像素相同。
- Logs 页面现在复用观测全屏列表壳，提供展开搜索、日志级别关键词、可移除标签、Flutter 风格普通空态/搜索无结果态和顶栏导出；Redmi 竖屏默认字号 9 项固定宿主回归与跨观测页入口单测方法通过，深浅色页基线记录于 `docs/visual-baselines/logs-page-states-redmi-api36.sha256`。日志仍由固定夹具渲染，没有启动核心或访问网络。

验收门槛：

- 首先完成 Dashboard 主页、Profiles 配置和 Proxy Groups 代理组的逐页面、菜单和按钮闭环；三条主流程未收敛前，Requests/DNS/Logs 的进一步视觉打磨暂停。
- 当前主流程切片：主页配置详情和代理组摘要/节点选择 → 自定义配置创建/结构化编辑/草稿退出 → 配置页/代理组菜单、面板和动效差异；有限交互与URLTest冷进程切片此前已验；整页连续动效本轮通过，原1.yaml全部90个HTTP规则资源实网分批完成，最新已补HTTP/File Proxy和6种HTTP Rule（domain/ipcidr/classical×YAML/text）实网更新/回读；全部来源/恢复和完整视觉仍PARTIAL。
- 固定 Debug 宿主覆盖普通/长/缺字段连接、DNS 缓存/NXDOMAIN/超时和日志不同级别，深浅主题各有截图基线。
- 真实 Connections、Requests、DNS、Logs 页面分别验证停止/运行空态、非空态、详情/筛选，以及清空与导出；点击和导航状态不破坏现有记录语义。
- 只呈现 Bridge 已提供的真实字段。单条连接没有速率数据时不显示伪造的 `/s`；日志正文仍为原始文本，不假设未提供的结构化字段。
- Redmi Note 8 Pro 竖屏、系统默认字号真机验证；横屏及大字号用例不运行。
- Dashboard 默认态字号基线文件仅保留既有历史证据；按当前验收范围不再运行横屏或大字号测试。
- Dashboard 页面视觉还原首片：用同一主题壳层覆盖正式 MainActivity 和固定视觉宿主；核心卡使用 34dp 圆角、Flutter 手机内边距和 88dp 开关，流量统计采用上传/下载双块和 22dp 内层形状，当前配置作为首屏入口；四 Tab Dock 改为浮动圆角条和 Material 矢量图标，配置添加 FAB 仅在配置页出现。Dashboard 横向内容宽度上限 560dp，系统导航安全区由 Dock 留白，按应用代理已移入工具设置分组，电池授权已移入常规/按需连接；系统 Always-on 和订阅请求诊断仅从显式 Debug 验收入口进入。
- Dashboard 固定宿主在 Redmi Note 8 Pro（Android 16/API 36、1080×2340，serial `wsvwypiz7xwslvl7`）验证停止/运行和深色/浅色竖屏截图；测试首次采集哈希与旧版不同，已人工检查主卡、流量块、配置卡和 Dock 的布局及按钮裁切，并写入 `docs/visual-baselines/dashboard-states-redmi-api36.sha256`。历史横屏/大字号 PNG 与哈希仅保留为既有记录，不再作为后续回归门槛。截图只证明 KClash 自身回归稳定，不声称 Flutter 引擎逐像素一致；宿主使用固定夹具，不读取 Room、系统设置、已安装应用、网络、VPN、Mihomo 核心或 `1.yaml`。
- Dashboard 视觉边界：Flutter 当前配置入口和浮动 Dock 已还原静态层级；面板动态形变/拖拽动画暂缓；出口 IP、质量与国家代码已有正式 Bridge/Repository/ViewModel 状态；停止态 DIRECT 与运行态实际代理出口已验，质量来源全覆盖仍待验证；运行时间与真实速度历史已有正式链路，曲线滚动动效及精确视觉 parity 继续保留。浅色目前使用项目 Material 3 色板，不等同 Flutter Material You 动态取色。
- Connections 连接详情：以安全布局实际高度实现 `9/16`、`0.9` 两档，宽度超过 600dp 时使用右侧栏，宽为窗口的 40% 并限制在 360–560dp；拖柄支持点击、拖动，返回/下滑/遮罩/顶栏均可关闭。正文中部起手下滑回顶时，位移与剩余惯性都不收起面板。保留本地时间、`process(uid)`、元数据、空字段隐藏、复制、反序代理链和点击快照恢复。深色默认及真实系统字体 2.0x 横竖屏四份内容基线已更新；新增浅色横竖屏与 360×640dp 实际窗口顶部/底部六份基线（`docs/visual-baselines/connections-detail-light-redmi-api36.sha256`），整屏安全区已人工检查。进出场与遮罩联动已使用可测试的临界弹簧；横屏侧栏按 Flutter 的 300ms/200ms 标准减速曲线运行，关闭中旋转不会重新入场或丢失关闭回调。19 项固定宿主回归和另 1 项真实 MainActivity 核心详情 E2E 已通过。
- Connections 运行态摘要字号矩阵：固定单条活动连接在页面注入的 1.3x/2.0x 下验证摘要换行与截断；完整长主机名、长规则及元数据的滚动另由真实系统字体 2.0x 详情测试覆盖。详情已移除 Flutter 参考没有的独立进程路径/UID 行，模型仍保留原始字段。摘要基线记录在 `docs/visual-baselines/connections-font-scale-redmi-api36.sha256`，固定宿主不读取 Room、网络、VPN、Mihomo 核心或 `1.yaml`。
- Observation 运行态大字号视觉首片：固定 Requests、DNS、Logs 各一条记录，在 1.3x/2.0x 下保持页签切换、字段换行和滚动可达，六个基线记录在 `docs/visual-baselines/observation-page-font-scale-redmi-api36.sha256`，不读取 Room、网络、VPN、Mihomo 核心或 `1.yaml`。
- Observation 页面组件交互首片：在固定运行态宿主中验证 DNS 与日志分别清空、日志导出回调以及 Requests 数据保持不受影响；真实 `MainActivity` 的运行数据清空与文件导出已在 Redmi 真机完成，跨设备和完整页面级端到端覆盖仍待补齐。

待完成：

- Flutter 页面信息架构和 Android 手机导航结构对齐。
- 主流程累计：Profiles运行态保存/应用、删除回退、整页排序、父/正文加密草稿新进程恢复，以及FILE/remote/脚本/Provider真实SAF等待中进程回收后返回与取消均已有11-04证据；脚本运行态、全局DNS/规则、Provider覆盖/侧载/HTTP资源实更新由前组覆盖。本组首页/代理组选择、模式/测速隔离、读失败重试和Providers空/90资源非空态跨轮询/Activity重建、配置/代理正文与菜单交互、URLTest冷进程、整页连续动效及共享玻璃/多层子菜单/API27离线子页均有独立批次证据，数量不跨批相加；原1.yaml全部90个HTTP Rule/mrs分批更新已覆盖。Fallback固定选择冷进程恢复已完成；自然无效核心`now`按当前合法解析契约保留为不可自然构造边界，在线age仍缺授权密文和密钥；其他资源SAF/外部脚本完整E2E及Provider批量故障恢复、HTTP/File/Inline代表类型实际HTTPS命中已由第4/5项补验。资源选择器SAF-01～05已由本组补验；剩余为未覆盖来源/协议与托管apply失败故障注入等证据边界；HTTP/File Proxy与domain/ipcidr/classical×YAML/text已有前序分批实网证据。全应用视觉/动效和逐像素一致性仍PARTIAL。备份/系统恢复与观测页次要视觉暂缓。
- Dashboard 出口IP/质量的8/6全部源已在DIRECT及运行RULE尝试，成功质量Room缓存及实际跨PID恢复已验；远端部分失败/无类型/IP不匹配按真实结果保留，更细内容parity仍待补；可选服务状态的route hits逐项核验复用已由后续真实GitHub切片覆盖，不推广为首页所有IP/质量共享缓存；运行时间和最近 30 次真实速度采样已通过正式 MainActivity/核心验收，停止/运行两态和深浅色竖屏截图已有单设备基线。
- 可选服务状态真实检测及出口 IP/质量首片已验收：13 目标、10 种核心状态、单项/全部检测、启用及按钮排序、至少一项、偏好重建；前台可见自动检测前两项，详情按代理链首节点共享出口查询与质量入口，路由稳定等待与旧结果保护。整行长按排序、松手保存、边缘滚动及后台取消已验收；服务 route hits 逐项核验复用已有真实 GitHub 证据，无关选择变化保留检测时间，改变出口或网络通知强制重测。13目标现已分别DIRECT及运行RULE尝试，运行12可用/Prime Video失败；这证明检测链路及状态回显，不能称13服务全部可用，完整parity继续PARTIAL。
- Profiles、Proxy、Connection、Observations页面及其Requests、DNS、Logs页签的完整视觉与交互对齐仍待继续；Dashboard Dock默认非玻璃模式的拖动/弹簧/取消、共用液滴/玻璃菜单、连续动效、主页四入口面板、配置页菜单/预览和节点菜单停止/运行态交互已有定向验收。未覆盖面板、全应用逐屏动效与Flutter逐像素parity仍待继续。
- 暂停项：Requests/DNS 完整页面、搜索筛选视觉校准及 DNS 真实核心清理端到端验收暂不排期；先完成首页、配置和代理组主流程后再恢复。
- Material You 动态颜色；Dashboard 已有浅色/深色静态主题截图，但全应用主题覆盖尚未完成。
- 主页面连续圆角、焦点/按压、默认Dock和共用液滴/玻璃菜单切片已验；配置切换、内存、IP质量、服务详情/管理四入口共用面板的短内容自然高度、长内容双停靠、固定标题操作、正文滚动和子页系统返回已有26项集中回归；配置页菜单、预览和正文入口另有定向用例通过。此证据不覆盖Flutter全页视觉等价；未覆盖面板、其他子页与全应用逐屏动效仍待继续。
- Connections 两档高度、横屏侧栏、浅色和小窗口首片已验证；原连接详情保留四次曲线采样近似轮廓，本组主页四入口使用已有准确连续圆角。共用面板新增自然高度和重合停靠点去重，短/中/长内容及增长收缩已有单测与真机覆盖，原连接详情三项回归通过；不能据此声称所有面板均接入了 `fitsContent`。入场/退场和遮罩联动已完成，横屏侧栏历史上对齐 Flutter 的 300ms/200ms 标准减速曲线；快速拖甩速度延续、越界回弹、连接轮廓逐像素对照及全页面键盘联动继续保留。后续验收固定为竖屏和系统默认字号。
- 小屏滚动策略、触控区域的全页面覆盖；按当前验收范围，后续不执行横屏与大字号测试。
- 截图基线和 Compose screenshot regression；当前已完成 Redmi 单设备观测页 Requests/DNS/Logs 六状态组件基线、Proxy Groups 当前页内节点网格深浅色两状态基线、Dashboard 停止/运行/系统就绪深色基线及浅色默认/运行竖屏基线、Connections 停止态/运行态两状态基线、Profiles 空态/选中自定义配置/远程订阅用量三状态基线；其他页面仍待补齐。历史字号/方向矩阵仅存档，不作为后续执行门槛。Connections 详情独立截图矩阵与真实 MainActivity 观测清空/导出证据保持原有状态。

完成门槛：

- 关键用户流程在 Flutter 参考和 KClash Android 上逐屏比对。
- 页面恢复、进程重建后状态不丢失；后续回归固定为竖屏和系统默认字号。
- UI 不直接调用 Core、gomobile 或数据库。

### 2026-11-01：代理组主页节点直选主流程首片

- 代理目的地改为占满 Scaffold 可用高度的独立页面，横向组标签控制分页；当前组节点在页面内使用虚拟化网格展示，点节点即可切换，不再要求先开选择弹窗。单节点测速仍使用卡片内独立触控区，避免测速点击触发节点切换。
- 搜索、刷新、当前组批量测速和 computed 组当前/优选状态保留在主页面；切组稳定后将组名写回当前 Profile，Room 更新由 Repository 承担，已增加真实 Repository 持久化用例。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏、系统字号 `1.0`：`ProxyGroupsActivityInstrumentedTest` `OK (6 tests)`，覆盖节点直选/自动恢复、横向分组切换、搜索、单节点测速隔离、忙态禁用及深浅色页面截图；`ProfilesRepositoryPanelInstrumentedTest#repositoryPersistsCurrentProxyGroupForPageRestore` `OK (1 test)`。深色页面哈希 `c591ad6f15217ac76146fbe35a458f1fcb43ad85e2954de1804e1c10036b2d5c`，浅色 `6b01f6da5f8e736b853b0862ff1d3d93fb9031d0fa247f9799b6efbbd52b02dc`，记录在 `docs/visual-baselines/proxy-groups-states-redmi-api36.sha256`。
- `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`、`./gradlew test :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 均通过；全工程 JVM 235 项通过，Lint 为 0 error、38 warnings、3 hints。Debug APK SHA-256 `aa2b7f728b617a0656346490b48c27cb68285669e6e9ae6f39053a8d91891676`，AndroidTest APK SHA-256 `a74d2231a439b7a841e872c3ef288574883a358ec0a462026bfb31807cdbfd70`，主 APK 原生库仅含 `arm64-v8a`。
- 本轮只使用固定代理页宿主和内存 Room；未访问在线白名单、未读取或修改 `1.yaml`、未启动 VPN/Mihomo，也未运行横屏/大字号用例。代理组主流程首片已验证，工具菜单、自动定位、递归组和滚动恢复仍待补齐；下一步回到 Profiles 添加、选择、编辑和同步主路径。整体 UI/UX 仍为 `PARTIAL`。

### 7.7 测试、发布和运维

**配置子页与输入恢复（2026-11-09，KUI-026）**：修正Provider表单扣除删除触点后的标签/值宽度、开关与IP整行点击、可选提示、最后一项添加自动返回及分组选择排版；配置资源区分搜索无结果/目录为空并可清除恢复，NTP/Sniffer空态按视口定位。字段/YAML确认从子Dialog取IME控制器；旧包同一新增用例在awaitHiddenIme五秒超时，最终包通过。860项JVM（App578执行、其余282复用）、Debug/签名Release/独立测试包及双Lint通过；同套Redmi4+18个不重复方法22/22、68图/10拼图已验。正式签名安装回拉、原配置详情/Provider缓存与底部配置Tab烟测通过；配置未清，测试包及本轮上传原1.yaml副本已精确清理。当前仅本组视觉/交互修正，完整Flutter页面与菜单性能仍PARTIAL，KUI-025首开退化不覆盖。 详见`docs/acceptance/config-subpages-parity-redmi-20261109.json`。

**源按钮底色与玻璃开场（2026-11-09，KUI-025）**：配置/代理按钮携带真实底色，普通材质按sRGB交接、玻璃只叠源底色。测试改等实际轮廓增长，补齐深浅玻璃展开帧、准备期取消、真实宿主背景冻结与重开重采；860项JVM（App578执行、其余282复用）、Debug/签名Release/独立测试包及双Lint通过。同套Redmi两批3+19个不重复方法22/22、56图/8拼图、8对终态像素一致；正式深浅视频102/103原PTS帧核对、各抽样24张。打开进程74/201→74/190、Popup31/129→37/124，退化观察保留，不认定性能收益；关闭23/168→21/172。手机保留同签名本地修正版，配置未清、测试包/采集文件已清理、服务停止。玻璃默认关闭，完整视觉/慢帧仍PARTIAL。 详见`docs/acceptance/menu-fill-readiness-redmi-20261109.json`。

**菜单胶囊与屏幕模糊（2026-11-09，KUI-024）**：补齐源按钮胶囊长边、Flutter稳定回源曲线/20%源图/50ms桥接；正文先变形再按屏幕sigma模糊，源图使用浮点轨迹，正文独立绘制节点复用。859项JVM（App577执行、其余282复用）、Debug/签名Release/独立测试包与双Lint通过；Redmi最终15/15、28图、8对终态像素一致，正文原尺寸/半尺寸/源图模糊宽度均29px。正式包打开进程75/206→73/206、Popup35/139→34/142，关闭20/178→19/167；仅回到基线附近，不宣称性能提升。首版80/183退化保留。深浅视频104/102原PTS帧核对并各抽样24张，玻璃开场两图仍为准备阶段；完整Flutter视觉/慢帧仍PARTIAL。手机保留同签名修正版、配置未清，测试包已清理、服务停止。 详见`docs/acceptance/menu-content-parity-redmi-20261109.json`。

**最新验收（2026-11-09，KUI-023）**：857项JVM结果、Debug/签名Release/独立测试包与双Lint通过，Redmi最终13/13、23图、两段原PTS视频；旧联合阴影红测/GPU失败保留。当前为视觉/回源行为修正，性能未判改善；完整Flutter运行视觉仍PARTIAL，见`docs/acceptance/menu-shadow-parity-redmi-20261109.json`。

**当前发布后验收（2026-11-09，KUI-022）**：854项JVM结果（App572执行、其余282复用）、Debug/签名Release/独立测试包与双Lint通过，11/11定向回归、20图及8对正式菜单终态一致。首开57.02%→32.68%，剩余慢帧仍待；原生采样受ROM权限阻塞，不计通过。手机保留本地修正版，公开Release不覆盖，见`docs/acceptance/menu-source-ripple-redmi-20261109.json`。

**正式发布完成（2026-11-09，KClash 0.1.0）**：中英文README、独立发布密钥/备份和标签签名CI已落地；`kclash-v0.1.0`固定`961b147d`，versionCode1000，Release为非预发布/Latest。CI[42511426890](https://github.com/lonnnnnng/FlClash/actions/runs/42511426890)全通过，854项JVM、本地双Lint与Go双层race通过；KREL-001正式YAML导入崩溃、KUI-020出口闪烁/网速跳高已修并留溯源。公开APK下载SHA256/资产digest/签名/版本/ABI全部核对，Redmi安装回拉一致；公开包67.7秒16次样本0查询态、IP/国家不变、卡片间距恒定617px（旧694px），VPN/tun0、停止/冷启动通过。本地最终固定/自动两轮各18次也通过。手机保留正式包、一份授权本地配置和原自动选择，服务停止；后续只用Redmi。发布页：https://github.com/lonnnnnng/FlClash/releases/tag/kclash-v0.1.0 。证据 `docs/acceptance/release-0.1.0-20261109.json`。跨版本升级、完整视觉/性能、低API及蜂窝/OEM仍PARTIAL，玻璃默认关闭。

KUI-019继续优化菜单动态轮廓：阴影局部重组、同帧Path/Paint复用、宽度延迟布局及移除重复裁剪。Redmi API36独立非debuggable包、无PNG/录屏，前后各两轮深浅，菜单合计jank331/1429（23.16%）→261/1555（16.78%），顶卡收回38.61%→14.07%；首开26.42%→23.16%改善有限，关闭6.91%→9.94%退化保留，首次打开UI区间中位也未改善。完整自然流程各深浅一轮非启动窗口15.10%→13.39%，不冒充正式R8运行。final-build3共849项JVM零失败/错误/跳过（App568在build1执行、其余281复用，最终四模块UP-TO-DATE）；Debug/Test/未签名Release-R8/双Lint通过，Debug0错误68警告14提示、Release0错误66警告13提示。同套最终包回归19/19、自然整页2/2、三轮菜单2/2；92PNG/32拼图及8组原PTS抽样已核对，593源码与安装回拉匹配。用户确认重置，当前正式0份配置，排除依赖非空库的两条导航回归；Flutter参考包保持缺席、不安装。44份正式快照及最终独立检查一致，原1.yaml/设备设置保持；独立.motion双包与独占文件精确回收，结束无服务/tun0。旧包/候选12图逐字节一致后刷新环境基线，14/19、旧包0/5与run-as拒绝回拉失败均保留。整体性能和完整Flutter视觉仍PARTIAL；下一步优先首开、关闭退化及RenderThread慢帧，再补CUSTOM组数、首页无流量统计、内存图形与其余子页。玻璃默认关闭，既有边界保持。证据`docs/acceptance/menu-rendering-redmi-20261108.json`，溯源`docs/menu-rendering-performance-defect.md`，附件`../acceptance-artifacts/menu-rendering-20261108/`。


历史切片（11-07，Provider运行态与MRS）：Provider本组已验：MRS只读正文首次/再次冷恢复及原面板返回通过；纯代理保存前后SIGKILL经外部独立观察系统新PID/前台服务，恢复不自动重放、显式重试仅一次；停止态/运行态各源文件或源记录缺失4类真实setup拒绝后回滚旧输入/选择/会话并成功切往有效目标，epoch各增加2。KUI-013父面板冷恢复后错误常驻已红绿复现并修复，KUI-010补齐实核回滚证据。Redmi final7运行态/回滚6/6、12次同socket核心归因HTTP200，MRS 1+1、相邻回归10/10，22张PNG完整校验及8拼图目视。build7 App549实际执行、Service212/Core38/Common31复用，共830项JVM零失败/错误/跳过；389任务28执行/361复用，Debug/Test/未签名Release-R8及双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。安装回拉及579份Android源码匹配；正式两配置/正文SHA-size-mtime/偏好/脚本/选择、原1.yaml/Flutter包/设备基线保持，临时对象/密文/标记清除，无服务或tun0。MRS为真实MainActivity STOPPED，首次存活PID受控force-stop、再次恢复前进程已退出，运行态为Debug宿主真实VM/Room/核心的纯代理SIGKILL，非VPN/OEM自然回收或完整点击导航。final6-before的verify超时原因未定，后续通过不抹除；第6项原保护异常、未知偏好变化与网络稳定性仍待查。选项错误截图滚动后提示部分裁切、筛选与保存图含转场中间帧，下一组补键盘错误可见性、稳定终帧及剩余子页/面板，再集中Flutter排版/连续动效；独立全局规则页/资源选择器冷进程另列。效果/性能/兼容/发布按原顺序，蜂窝延期、玻璃默认关闭。证据`docs/acceptance/provider-runtime-redmi-20261107.json`；溯源`docs/provider-cold-parent-refresh-defect.md`；附件`../acceptance-artifacts/provider-runtime-20261107/`。

历史切片（11-07，扩展页冷恢复）：扩展页局部/全局规则独立加密会话、提交前后冷恢复、事务回执防重放、外部规则修改/删除及宿主删除保护已验，KUI-012本组闭环。Redmi final2首次冷恢复18/18、提交后与回执缺口再次恢复4/4，相邻回归11/11，33张PNG完整校验并经11张拼图目视。828项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；执行/复用明细以本组JSON为准。Debug Lint0错误/67警告/13提示，Release0错误/66警告/12提示。安装回拉和578份源码匹配；两份正式配置/正文SHA-size-mtime/脚本/偏好/选择、原1.yaml、Flutter包及设备设置保持，临时对象、密文和提交标记清除，收尾无服务。首轮local-applied恢复过早就绪及后台回执覆盖新UI树已分别红绿复现并修复，原失败保留。本组仅STOPPED/受控force-stop；不推广运行态死亡、OEM自然回收、独立全局规则页冷恢复或完整Flutter视觉。下一组Provider运行态/MRS只读冷恢复及失效引用实核回滚，再统一Flutter视觉；第6项原保护异常、未知偏好变化和网络稳定性仍待查。蜂窝延期，玻璃默认关闭。证据`docs/acceptance/profile-extension-cold-redmi-20261107.json`；溯源`docs/profile-extension-cold-recovery-defect.md`；附件`../acceptance-artifacts/extension-cold-20261107/`。

历史切片（11-07，配置管理窗口冷恢复）：配置管理重命名/删除/排序独立加密会话、保存意图与提交结果核对、失败原位重试及外部目标冲突保护已验，KUI-011本组闭环。Redmi final7首次冷恢复17/17、提交后再次恢复3/3，相邻回归11/11，33张PNG完整校验并经11张拼图目视。814项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；App533在本组build4实际执行，Service212/Core38/Common31复用，最终build8四模块UP-TO-DATE、389任务11执行/378复用。Debug Lint0错误/67警告/12提示，Release0错误/66警告/11提示。安装回拉和572份源码匹配；正式两份配置/正文SHA-size-mtime/脚本/偏好值/选择、原1.yaml、Flutter包与设备设置保持，临时对象与草稿清除，收尾无服务。本组仅STOPPED下真实MainActivity/Hilt/Room、受控force-stop；不代表运行态死亡、OEM自然回收或完整Flutter视觉。final5早先17+3通过独立保留，final7增加实际Room名称/顺序和正文结果断言；中间失败与精确恢复见KUI-011，不累计。下一组为扩展页独立冷恢复、保存中断与原规则外部修改/删除保护；Provider运行态/MRS只读冷恢复、统一Flutter视觉及第6项原保护异常/网络稳定性继续待补。蜂窝延期，玻璃默认关闭。证据`docs/acceptance/profile-management-cold-redmi-20261107.json`；溯源`docs/profile-management-cold-recovery-defect.md`；附件`../acceptance-artifacts/management-cold-20261107/`。

历史切片（11-07，Provider正文冷恢复）：Provider正文独立加密草稿与正式冷恢复已验：正文/选区/失败/放弃确认跨进程保持；保存前中断等待显式重试、提交后恢复不重放；外部正文冲突、配置删除、引用源记录/文件删除保留草稿并阻止写入；派生缓存缺失由未变源安全重建。KUI-010失效引用配置无法切走已修，保留原ID/节点选择及已应用输入用于失败回滚。Redmi final5九场景首次恢复9/9、提交后及两类源删除再次恢复3/3，相邻回归10/10，22图完整校验并目视。802项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；App521/Service212本次重跑，Core38/Common31复用，build11共389任务60执行/329复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。安装回拉与源码匹配，正式两份配置/正文SHA-size-mtime/脚本/偏好值/选择、原1.yaml、Flutter包与设备设置保持，临时对象和密文清除，收尾无服务。本组为STOPPED下真实MainActivity/Hilt/Room/Bridge、受控force-stop与授权原1.yaml的自有副本；不推广运行态死亡窗口、MRS只读冷恢复、全部Provider类型、OEM自然回收或完整Flutter视觉。KUI-010运行态回滚有JVM证据，尚非实网验证。首轮质量缓存ip_quality_cache_v1变化、提交后准备失败、final3旧提示提前放行、final4源删除后切换缺陷以及对应精确恢复均独立保留；本次键级归因不追认前组未知偏好变化，第6项原保护异常与网络稳定性仍待查。证据`docs/acceptance/provider-document-cold-redmi-20261107.json`；溯源`docs/provider-document-recovery-defect.md`、`docs/broken-current-profile-switch-defect.md`；附件`../acceptance-artifacts/provider-document-cold-20261107/`。下一组继续管理窗口与扩展页冷进程、保存受理中断、目标外部删除，再统一Flutter视觉/连续动效；Provider运行态与MRS只读冷恢复另列补验。蜂窝延期，效果/性能/兼容/发布按原顺序，玻璃默认关闭。

历史切片（11-07，正式本地代理异常链路）：正式MainActivity本地代理异常链路已验：真实Room失败经Snackbar反馈、菜单重试只新增一次并跨重建回读；真实配置卡片在受理前切换拒绝写入、受理后保存冻结原节点；40份原正文CUSTOM副本的真实引用查询、首尾滚动、取消保留批选、等待防重复、删除失败原位提示/重建/重试及引用正文保持通过。Redmi final3精确18/18（4新增+14相邻回归）、55图完整校验及16拼图目视；788项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过，安装/源码匹配。两份正式配置/脚本/正文/偏好值/选择、原1.yaml、Flutter和设备设置保持，临时副本/本地节点/SQL触发器清理，收尾无服务。App512在本组build1/build2实际执行，其余276复用；最终build3四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。本组为STOPPED下真实MainActivity/Hilt/Bridge/正式Room；窄Debug门闩仅控制校验/保存前等待，SQL错误类型与固定标记计数证明故障命中，Release无布置接口/测试标记。长引用按名称扫描原文，原文同名inline节点优先，不证明删除后路由失效。未新验运行态、冷进程、全部输入法或完整Flutter视觉。证据`docs/acceptance/formal-local-proxy-redmi-20261107.json`，范围与首轮失败记录`docs/formal-local-proxy-acceptance.md`，附件`../acceptance-artifacts/formal-local-proxy-redmi-20261107/`。下一组推进管理窗口/扩展/Provider正文冷进程、保存受理中断、目标外部删除及Provider独立加密草稿，再统一Flutter视觉/连续动效。第6项原保护异常、前组偏好变化及网络稳定性仍待查；蜂窝延期，效果/性能/兼容/发布按原顺序，玻璃默认关闭。

历史切片（11-07，ALPN停止回写修复）：KUI-008已定位并修复：本地代理输入提交后立即重建，旧窗口ON_STOP后由Compose onBlur回传旧文本；正式页面拒绝已停止宿主的业务修改，独立保存树照常捕获。Redmi final1精确14/14、43图完整校验及13拼图目视；语义输入/失焦与平台InputConnection各8轮，含合法清空，VM及页面精确值保持。788项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；安装回拉匹配，两份正式配置/脚本/正文/偏好/选择及原1.yaml/Flutter/设备设置保持。App512本组build3实际执行，其余276复用；最终build4四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。本组为Debug隔离宿主上的正式页面/VM，InputConnection由测试驱动，不代表全部输入法、正式MainActivity全链路、冷进程或完整Flutter视觉。diagnostic1为1/2、diagnostic2为0/1，两次旧值覆盖及onBlur栈保留；前批final1原始空值未留VM轨迹，不能倒推百分百同因。证据`docs/acceptance/alpn-input-boundary-redmi-20261107.json`，溯源`docs/local-proxy-stopped-input-defect.md`，附件`../acceptance-artifacts/alpn-input-boundary-redmi-20261107/`。继续正式MainActivity节点添加失败/重试、保存等待切配置和真实长引用查询；随后管理窗口/扩展/Provider正文冷进程、保存受理中断、目标外部删除及Provider独立加密草稿，再统一Flutter视觉/连续动效。第6项原保护异常、前组偏好变化及网络稳定性继续待查；蜂窝延期，效果/性能/兼容/发布按原顺序，玻璃默认关闭。

历史切片（11-07，本地代理确认与节点添加）：KUI-006/007已修：本地代理删除/替换确认可滚动且原位显示失败与等待；添加节点异常按阶段脱敏，重复分支也复核当前配置。Redmi final2精确12/12、43图经完整集合/哈希/解码及13张拼图目视；新增5条覆盖40个长引用、隔离Room真实失败/重试/防重复、实核拒绝、受理前后切配置及单/批链接真实IME/Back/重建。788项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；双包回拉匹配，正式两配置/脚本/正文/偏好/选择、原1.yaml/Flutter/设备保持。App512本组build1/build2实际执行，其余276复用；最终build6四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。新增5方法为Debug隔离宿主、真实Bridge与隔离Room，长引用结果受控注入，不代替正式MainActivity添加失败/切配置、真实引用查询、运行态或冷进程。final1的ALPN立即重建曾丢值，11/12；最终8轮输入重建回归通过仍未定位根因，保留待查。完整Flutter视觉仍PARTIAL。证据`docs/acceptance/local-proxy-interactions-redmi-20261107.json`，溯源`docs/local-proxy-interaction-defects.md`，附件`../acceptance-artifacts/local-proxy-interactions-redmi-20261107/`。优先补正式MainActivity节点添加失败/重试、保存等待切配置及真实长引用查询，并定位ALPN立即重建间歇丢值；再推进管理窗口/扩展/Provider正文冷进程、受理中断、外部删除及独立加密草稿；随后统一视觉，系统/性能/发布按原顺序。第6项原保护异常、前组偏好变化及网络稳定性仍待查，蜂窝延期，玻璃默认关闭。

历史切片（11-07，扩展批选与远程新增表单）：KUI-004/005已修：扩展批选与删除确认重建保持，筛选仅删可见已选项、失败原位重试、等待防重复及外部移除收口；远程新增原位校验、真实IME/Next/Done、40条批量首尾滚动/模式/来源往返/重建/取消与临时密钥清理已验。Redmi final4 精确26/26、35图完整校验并经10张拼图目视；782项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8和双Lint通过。双包安装回拉匹配，两份正式配置/脚本/正文/偏好/选择及原1.yaml/Flutter/设备设置保持，临时副本清理、无服务。App506本组build1/build3实际执行，其余276项复用；最终build6四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。修复前red1为0/4；final1为23/26，包含IME自动聚焦问题、重开回读等待和过时后台断言；final2为24/26，余下两项为错误标签匹配与旧测试契约。final3为25/26，旧密钥掩码断言改由隔离回调验证；原始失败保留，未覆盖第6项及前组偏好异常。产品源码自build3后保持，build4/5/6只修测试。证据`docs/acceptance/extension-batch-remote-form-redmi-20261107.json`，溯源`docs/extension-batch-remote-form-defects.md`，本机附件`../acceptance-artifacts/extension-batch-remote-form-redmi-20261107/`。下一组优先代理添加到本地的实核校验/保存失败及等待中切换配置、本地代理删除长引用/取消/失败和分享链接IME返回；随后管理窗口/扩展/Provider正文冷进程、保存受理中断、目标外部删除及正文独立加密草稿，再统一Flutter视觉/连续动效。第6项原保护异常、前组偏好变化及网络稳定性仍待查，蜂窝延期，玻璃默认关闭。

历史切片（11-07，扩展页重建与键盘错误恢复）：修复KUI-003：真实MainActivity扩展页重建复用原会话，保留全局子页/搜索、长脚本列表、规则草稿/目标/校验错误及放弃确认；挂起保存不被重连作废，明确重试和退出重开重新读取。真实IME上方操作、两层返回、连续确认只保存一条规则及重新打开回读已验。Redmi final5精确13/13，33张PNG分片回传/解码/哈希校验并经7张拼图目视；778项JVM XML零失败/错误/跳过，Debug/Test、未签名Release-R8和双Lint通过。App/Test回拉匹配，最终安装前/验收前后两份正式配置、正文、偏好/选择、原1.yaml、Flutter基线和设备设置保持，临时配置/脚本清理，收尾无服务。最终build5为389任务11执行/378复用，App502于build1重跑，其余276复用。Debug0错误/67警告/10提示、Release0错误/66警告/9提示。原始red1/red2均0/3、挂起保存红灯1/1失败；final1汇总11/13且偏好摘要变化、Binder回传缺两条；final2汇总13/13但仍缺两条明细；final3已13/13完整回传，静态截图退场采样不足，final5采样650ms复验。首轮偏好具体键缺少基线、原因未定，未回写旧偏好，不能消除第6项原保护异常。截图通道新增显式48KiB分片及总数/索引/摘要校验，旧驱动行为保留。仅本组交互与状态通过，不称冷进程、运行态或完整视觉通过。证据`main-extension-state-redmi-20261107.json`、缺陷`profile-extension-restoration-defect.md`。

历史切片（11-06，配置管理窗口重建恢复）：修复KUI-002：真实MainActivity配置重命名草稿、删除确认、排序草稿和放弃确认跨Activity重建保持；取消不写入，明确保存/删除仅作用于测试临时副本。Redmi final2精确9/9，25张PNG校验并经3张拼图目视；775项JVM XML零失败/错误/跳过（App499在build3重跑，其余276复用，最终build4四模块均UP-TO-DATE），Debug/Test、未签名Release-R8和双Lint通过。App/Test实际回拉匹配，两份正式FILE/URL配置的正文/元数据/顺序/偏好/选择、原1.yaml、Flutter基线及设备设置保持，临时行清理，收尾无服务。最终build4为389任务11执行/378复用，Debug0错误/67警告/10提示、Release0错误/66警告/9提示。原始red1 0/3为产品缺陷复现，final1 8/9仅重命名输入框matcher错误，改用EditableText.text精确断言后final2通过，产品源码不变。两失败批次及前后保护独立保留；不称冷进程、运行态或完整视觉通过。证据`main-profile-management-redmi-20261106.json`，缺陷`profile-management-restoration-defect.md`。

历史切片（11-06，Provider正文真实主宿主）：真实MainActivity首页配置详情→Provider正文链路已验：真实侧载失败保留长草稿/错误/选区并跨后台和Activity重建，放弃确认重建后可继续/放弃；有效缓存保存后退出、重建、重开回读且资源源文件不变；原配置MRS只读预览滚动位置跨重建保持并返回同一面板。Redmi final2精确9/9，21张PNG哈希/解码验证并经4张拼图目视；775项JVM XML零失败/错误/跳过（本次四模块任务均UP-TO-DATE），Debug/Test、未签名Release-R8和双Lint通过。最终双包实际回拉匹配，两份正式FILE/URL配置、正文/元数据/偏好/选择、原1.yaml、Flutter基线和设备设置保持，临时配置及自身投影缓存清理，收尾无服务。本组仅补验收代码，生产代码和Mihomo不变；真实核心在停止态加载原1.yaml的临时FILE+CUSTOM资源绑定。未手动刷新原90个HTTP Provider或重新下载正式订阅，但配置加载可触发核心资源初始化、首页IP/质量查询，不能声称无网络。final1为7/9：一项只因首次新增ip_quality_cache_v1导致偏好保护失败，正式正文/元数据/选择未变；一项选中可一屏容纳的MRS却要求滚动。未回写旧缓存或放宽滚动断言，改选原配置中200..3000条的真实规则资源后final2整组通过。首轮偏好变化保留，不能以final2覆盖。此组不证明冷进程正文恢复、运行态侧载、Provider网络类型覆盖、全Flutter视觉/精确缓动或性能。证据`main-provider-document-redmi-20261106.json`；最终build2为389任务11执行/378复用，Debug0错误/67警告/10提示、Release0错误/66警告/9提示。

状态：`PARTIAL`。

历史批次（11-06，真实主页面往返与List重建）：真实MainActivity代理页跨配置/首页往返、List布局/分组/滚动/吸顶标题跨Activity重建、搜索空态保留与清除恢复已验。KUI-001修正List首次恢复14px偏移和吸顶标题消失：首个非空实际布局后重新应用保存的索引/偏移，无固定延迟或像素补偿。Redmi最终同套App/Test final5精确9/9，27张PNG哈希/解码验证并经4张拼图目视；四模块JVM XML共775项零失败/错误/跳过，App499本轮重跑，其余任务UP-TO-DATE。Debug/Test、未签名Release-R8和双Lint通过（Debug0错误/67警告/10提示，Release0错误/66警告/9提示），安装双包实际回拉匹配。两份正式FILE/URL配置仅复用，临时副本回收，正文/元数据/偏好/选择及Flutter基线、原1.yaml、设备设置保持，结束无本应用服务。原始首轮4/6、diagnostic2/3的List失败和临时日志编译失败全部保留；修复及最终标题几何回归见`main-navigation-state-redmi-20261106.json`和`proxy-list-restoration-defect.md`。本组未重跑冷进程/实网/核心/VPN/Go/低API/远端CI/正式签名运行，完整视觉仍PARTIAL。

历史切片（11-06，排序子菜单键盘）：代理组排序子菜单键盘路径已验：方向键定位排序、Enter进入、连续Escape逐层返回并关闭且不改排序；重新进入后Enter提交名称排序，节点顺序和菜单文案更新，原节点选择与刷新次数保持。Redmi API36精确6/6，3张PNG哈希验证并逐张目视；四模块JVM XML共775项零失败/错误/跳过（最终任务均UP-TO-DATE），Debug/Test、未签名Release/R8、双Lint通过（Debug0错误/67警告/10提示，Release0错误/66警告/9提示）。本组只补测试，生产代码不变；App/Test实际回拉匹配。两份正式FILE/URL配置仅复用，测试前后正文/元数据/偏好/当前选择保持，Flutter基线、原1.yaml和设备设置保持，收尾无本应用服务。证据`proxy-sort-keyboard-redmi-20261106.json`。键盘事件为instrumentation注入，未验实体键盘/全应用焦点；实网/核心/VPN/Go/低API/远端CI/正式签名运行本组未重跑，完整视觉仍PARTIAL。

历史切片（11-06，Provider正文生命周期）：Redmi精确 **6/6**，10张PNG哈希验证并目视；后台草稿/失败/读取、放弃确认、长文选区、受理保存跨Activity重建和既有菜单/MRS回归通过。四模块JVM XML App499/Service207/Core38/Common31共 **775项** 零失败/错误/跳过，最终检查均UP-TO-DATE；Debug/Test、未签名Release/R8与独立双Lint通过（Debug0错误/67警告/10提示，Release0错误/66警告/9提示），安装双包实际回拉匹配，Release清单无Debug测试入口。用户确认手动重装后，经正式仓库恢复本地1.yaml和唯一远程源两配置，最终复用且前后保护摘要完全一致。final3长文选区恢复失败5/6原样保留，自定义Saver只存选区偏移后final4通过；这是Kotlin本地生命周期缺陷，不记作上游缺陷。证据`provider-document-lifecycle-redmi-20261106.json`；本组固定数据宿主的Activity重建不证明冷进程、MainActivity正式库恢复或Provider实网，完整视觉仍PARTIAL。

历史切片（11-06，首页策略组面板）：Redmi精确 **7/7**，覆盖系统返回、重开搜索清空、Activity重建和相邻配置/服务面板回归；14张PNG清单全部验证，5张新增图逐张目视。四模块JVM XML App495/Service207/Core38/Common31共 **771项** 零失败/错误/跳过，最终build2测试均UP-TO-DATE；Debug/Test、未签名Release/R8及双Lint通过（Debug0错误/67警告/10提示，Release0错误/66警告/9提示）。最终App/Test回拉哈希匹配，包哈希以页首本组切片和`dashboard-proxy-picker-redmi-20261106.json`为准。首轮5/7的两项失败来自空输入框占位Text的测试断言，改为EditableText断言后复验7/7；原失败单独保留，不计为产品修复。固定数据宿主Activity重建不证明冷进程/正式仓库恢复；完整视觉仍PARTIAL。

历史切片（11-06，代理组状态转场增量）：`ProxyGroupsRecoveryInstrumentedTest` 在 Redmi Note 8 Pro（`wsvwypiz7xwslvl7`、API 36、arm64-v8a、竖屏默认字号）精确 **5/5**；覆盖错误/空态重试、内容失败退场快照、旧层按钮失效、Dashboard 独立重试。随后 List 组头/转场回归 `ProxyPageParityInstrumentedTest` 精确 **4/4**，包括长列表组头进入吸顶边界后保持相对列表视口位置、raised 阴影截图和远端 Tab 目标页懒组合等待；本轮新增组头—节点行连续展开/收回动效断言，展开中间帧与稳定帧不同且收起后节点语义树清除。最终 Debug APK SHA-256 `4e690b2f2af354c809ab2c17aad2338496ad310e8a69ca3c0dbc95180deb6ebb`，AndroidTest `083817f8a6682252f3db676882f0603d688d7e237638f53a600d09a427973826`，未签名 Release `461d627884c9bc02090e5db6c31e05e7143635632e852b0fc8ad5a6431e10881`；本组 JVM App492/Core38 项，Debug/Release Lint 均 0 errors（Debug67 warnings/10 hints，Release66 warnings/9 hints）。完整 Flutter 视觉仍`PARTIAL`，详见`docs/acceptance/proxy-groups-transition-redmi-20261106.json`、`docs/acceptance/proxy-list-header-redmi-20261106.json`与`docs/acceptance/proxy-list-animation-redmi-20261106.json`。

历史精确验收（11-06，自动now/优选徽标）：`ProxyPageParityInstrumentedTest#computedGroupSeparatesAutomaticNowFromPreferredNodeMarker` 在指定 Redmi 1/1 通过。页面同时显示 URLTest 自动 `now` 香港节点和用户优选日本节点；Tab/List 两种布局均保持徽标语义分离，两张截图已目视。工作区单测 XML 汇总为 App494/Service206/Core38/Common31，共769项零失败/错误/跳过；构建、未签名 Release/R8 与双 Lint 通过。该批APK与证据哈希见其具名历史切片和JSON，完整视觉仍`PARTIAL`。详情见`docs/acceptance/proxy-computed-now-preferred-redmi-20261106.json`。

当前已有：

- 历史控制器批次build2统一检查通过（11-05）：App492/Service206/Core38/Common31共767项JVM零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8及双Lint通过；Go父模块全量race265/265、同控制器压力40/40、route8方法+2子场景共10通过。TLS原始两个泄漏分支含父项3失败，相同复现修复3/3。核心修复f83e9b10已推并固定gitlink；原失败和同步撤销条件见源码缺陷台账/ADR0021。
- 历史控制器批次Redmi build2同套App/Test（11-05）：final2精确4/4，包含控制器6轮认证/密钥轮换/关闭后立即释放/嵌入写禁用和3/3模式/组件/菜单回归；Always-on-final1默认零延迟冷启动/SIGKILL两次首个TUN HTTPS200及归因，独立纯代理恢复、保护前后各1/1、安装回拉/源码快照匹配。final1测试明文策略/类名两失败保留。旧TLS EOF因果/长期稳定性未闭环，第8/11仍PARTIAL；第7当时输入受限，后续Fallback实核/冷进程见最新快照，第6原保护异常保持。见`controller-lifecycle-redmi-20261105.json`。
- 2026-11-01 Requests/DNS 详情与筛选：Redmi 竖屏默认字号精确筛选 9 项全部通过，DNS 深浅色八状态新增基线，记录卡片、观测页面和连接详情既有基线一致；未运行横屏/大字号用例，未启动网络/VPN/核心。
- 2026-10-31 连接详情面板收敛：Connections 13 项 + Observation 4 项真机回归全部通过；另 1 项显式真实核心详情 E2E 通过。覆盖非空/空元数据、复制、快照恢复、两档几何与拖动、正文中部起手保护、遮罩/返回/下滑关闭、真实系统字体 2.0x 横竖屏、浅色/小窗口和禁用系统动画，共十份详情内容截图；真实请求只识别代理连接，不读取 HTTP 响应状态/正文。
- Debug APK 构建和 arm64 产物检查。
- 历史控制器批次build2双Lint（11-05）：Debug0errors/67warnings/10hints，Release0errors/66warnings/9hints；未签名Release/R8成功，正式签名运行未验。
- 历史87项instrumentation稳定基线与当时Profiles定向29项保留为历史证据，不代表当前整类全量回归；当时覆盖：三状态截图 1 项、ProfilesPanel 10 项、ProfileUi 11 项、MainActivity 创建/编辑/删除及 Rules/Proxies/Proxy Groups/Providers/DNS Overrides/Dialers 7 项。横屏/大字号矩阵是历史测试记录，后续不再运行；真实 MainActivity 观测页清空/导出端到端已由 Redmi 真机操作补齐。随后 MainActivity 类级复验受 `com.duanju.player` 抢占前台和测试收集不完整影响，出现既有宿主失败；停用干扰应用后的复跑 XML 为 `tests=0`，未形成有效类级证据，因此不替换稳定基线。
- Redmi 真机启动、停止、代理组和测速验证。
- Redmi 真机已验证 DNS/日志观测卡片的停止态和空态展示；两条路径的真实请求归因仅为当时授权下的历史证据，当前仅使用外层AGENTS.md唯一授权源，不恢复已撤回来源。

待完成：

- 完整页面级 Compose/Instrumentation 测试仍需按 Android 手机日常使用流程补齐；已完成的覆盖包括 Access 真实宿主选择/保存/恢复、观测页停止态空态和详情交互、Proxy/Dashboard/Connections 页面状态、Profiles 三状态竖屏默认字号截图、Profiles 卡片操作菜单回归及远程批量同步仅分发 URL Profile 的断言。横屏和大字号历史用例不再列入执行计划。历史87项基线不作为当前整类执行门槛；当前按精确方法和每组证据判定，跨设备竖屏默认字号截图仍待补。
- VPN 权限、TUN 停止清理、后台恢复、系统撤销 VPN、Wi‑Fi 断开恢复、进程重建和 `dumpsys connectivity` 真机测试已有证据；Wi‑Fi↔移动网络切换仍待完成。
- 连接、请求、DNS、日志端到端测试的自动化脚本和稳定断言。
- 本次累计变更的未签名Release/R8构建和Lint已通过；正式签名、Release真机安装/运行及发布资产仍未验证。
- 新 release keystore。
- 独立 Kotlin GitHub Actions arm64 workflow 已实现，分支推送触发验证、Tag/手动输入支持未签名预发布；远端运行结果仍需独立观察。
- Release 页面、更新日志、SHA-256 和回滚材料。
- 上游变更后的功能矩阵和回归记录。

完成门槛：

- Debug 和 Release 都能从干净环境构建。
- 发布 APK 只有 `arm64-v8a`。
- 签名、版本、包名、产物哈希和远端 Release 资产都能复核。
- 真机验收记录包含设备 serial、步骤、结果和关键日志。


## 8. 推荐推进顺序

### todo.md 收尾清单（2026-11-08，当前执行入口）

按工作区外层 `todo.md` 的编号顺序推进；本表记录当前进度，优先于下方历史切片的“下一步”。实现、构建和真机验收分别判定，一组通过后再提交推送。

| 编号 | 收尾项 | 当前进度 |
| --- | --- | --- |
| 1 | 编辑器冷恢复：规则 YAML 错误/选区/放弃、打开的类型选择器、策略组成员/排序/批选 | 已验收：745单测、final1构建/双Lint；Redmi六场景双阶段及18/18回归，31PNG；见`remaining-editor-cold-redmi-20261105.json` |
| 2 | 保存意图落盘后、正式提交前；已提交但清草稿前；退出中被杀进程 | 已验收：build7/745单测/双Lint；Redmi15场景受控force-stop后首次恢复、5次正常退出后再次冷启动检查、21/21回归；见`editor-commit-window-redmi-20261105.json` |
| 3 | 在线 age 订阅下载/解密/应用；CUSTOM 引用资源更新并应用 | 部分已验：CUSTOM手动/自动到期处理实网实核通过，在线3/3、回归8/8；age受当前授权响应明文/无密钥限制。见`subscription-reference-network-redmi-20261105.json` |
| 4 | 其他资源真实 SAF；外部脚本下载/改源/同步/应用 | 本组范围已验：YAML/MRS、图标集、图片真实选择/错误重试/重建及公开脚本实网应用；build3/745单测、final3 5/5、回归12/12；见`resource-saf-scripts-redmi-20261105.json`；本组SAF-01～05再补真实系统宿主死亡返回、预览/提交恢复、图库与图片边界，见`resource-picker-recovery-redmi-20261107.json` |
| 5 | Provider 部分失败恢复、剩余来源、实际规则流量命中 | 本组范围已验：build9/750单测、final7 4/4、回归14/14；受控两失败恢复，HTTP8/File8/Inline3实际HTTPS归因，另原MRS命中/Inline Proxy展开；见`provider-recovery-hits-redmi-20261105.json`。不推广全部90个MRS站点、所有协议流量或托管apply失败故障注入 |
| 6 | IP/质量全部来源、持久缓存和失效恢复；服务全部目标 | 缓存闭环与全部目标尝试已验：757单测、final3 5/5、cold2/cold5各双阶段、回归10/10；远端部分受限，原长窗口配置保护after 0/1/原因未定，新短窗口通过不替代失败。见`ip-quality-cache-redmi-20261105.json` |
| 7 | Fallback 无有效 now 实核行为和冷恢复 | 冷进程收尾已验：更新后的唯一授权源为34组、1个非空Fallback/7成员；Redmi真实Mihomo停止态/运行态各4阶段，共8/8有效`now`，固定/清除固定/非法成员拒绝通过；固定选择跨真实force-stop冷进程恢复并在清除后回到有效自动`now`，见`fallback-real-core-redmi-20261105.json`与`fallback-cold-process-redmi-20261106.json`。自然无效`now`经源码核对在合法非空组、断网或全不健康成员下不可自然产生，保留为核心语义边界，不记伪造注入为通过。 |
| 8 | 纯代理冷恢复、首次安装 VPN 授权、蜂窝切换、Tasker/MacroDroid、Always-on OEM | 部分已验：本组build2/767 JVM、Go全量race265/265/压力40/40/route10；控制器KCORE-002/003已修，Redmi6轮及回归共4/4，Always-on-final1零延迟首请求两次200、纯代理恢复/保护通过。旧TLS EOF因果/长期稳定性未闭环；蜂窝延期，其余系统边界待验。见`controller-lifecycle-redmi-20261105.json` |
| 9 | 完整Flutter视觉与常用交互 | KUI-019菜单合计23.16%→16.78%、收回38.61%→14.07%；final-build3同套19/19回归、整页2/2及三轮菜单2/2。首开有限、关闭9.94%退化仍待；当前空库排除两条导航回归。整体PARTIAL。 |
| 10 | 实时背景/触点光效、玻璃采样品质、8MiB正文及连续动效性能 | 后排待做，玻璃默认关闭；已完成六种材质/折射/多层菜单不重复列缺项 |
| 11 | API24/25/31/32及低API核心/VPN/实网、端到端回归、CI/签名/Release/更新资产 | 本组Go控制器race和TLS泄漏已修验证并建源码缺陷台账，CI补父模块/route竞态；未签名Release/R8通过。低API、远端CI、签名及正式发布仍待验 |

本轮原始附件存于工作区外层 `acceptance-artifacts/visual-motion-20261107/`；SAF前组沿用`acceptance-artifacts/resource-picker-20261107/`；全局规则前组沿用`acceptance-artifacts/global-rules-20261107/`；Provider运行态前组沿用 `acceptance-artifacts/provider-runtime-20261107/`；扩展前组沿用`acceptance-artifacts/extension-cold-20261107/`；管理前组沿用`acceptance-artifacts/management-cold-20261107/`；Provider前组沿用`acceptance-artifacts/provider-document-cold-20261107/`；前组正式本地代理沿用 `acceptance-artifacts/formal-local-proxy-redmi-20261107/`；控制器历史附件沿用`acceptance-artifacts/controller-race-20261105/`，前组TUN保护沿用`acceptance-artifacts/tun-protect-20261105/`，网络观察、Always-on及纯代理附件沿用 `acceptance-artifacts/always-on-dns-20261105/`、`acceptance-artifacts/always-on-20261105/` 和 `acceptance-artifacts/proxy-system-recovery-20261105/`，第1至6项附件沿用各自证据索引。旧 `/tmp` 附件曾有不可读取记录，不以历史口述代替当前验收。备份/系统恢复、观测次要打磨、语言/主题暂缓；TalkBack/自定义无障碍、早期Room兼容、横屏/大字号扩展及其他ABI不列待办。

### 当前执行清单（累计状态，2026-11-08）

本清单优先于下方历史N1～N4切片的“下一步”；每步完成后测试验收，并同时更新页首、第3/7节及验收JSON。

当前第9项：KUI-019继续优化菜单动态轮廓：阴影局部重组、同帧Path/Paint复用、宽度延迟布局及移除重复裁剪。Redmi API36独立非debuggable包、无PNG/录屏，前后各两轮深浅，菜单合计jank331/1429（23.16%）→261/1555（16.78%），顶卡收回38.61%→14.07%；首开26.42%→23.16%改善有限，关闭6.91%→9.94%退化保留，首次打开UI区间中位也未改善。完整自然流程各深浅一轮非启动窗口15.10%→13.39%，不冒充正式R8运行。final-build3共849项JVM零失败/错误/跳过（App568在build1执行、其余281复用，最终四模块UP-TO-DATE）；Debug/Test/未签名Release-R8/双Lint通过，Debug0错误68警告14提示、Release0错误66警告13提示。同套最终包回归19/19、自然整页2/2、三轮菜单2/2；92PNG/32拼图及8组原PTS抽样已核对，593源码与安装回拉匹配。用户确认重置，当前正式0份配置，排除依赖非空库的两条导航回归；Flutter参考包保持缺席、不安装。44份正式快照及最终独立检查一致，原1.yaml/设备设置保持；独立.motion双包与独占文件精确回收，结束无服务/tun0。旧包/候选12图逐字节一致后刷新环境基线，14/19、旧包0/5与run-as拒绝回拉失败均保留。整体性能和完整Flutter视觉仍PARTIAL；下一步优先首开、关闭退化及RenderThread慢帧，再补CUSTOM组数、首页无流量统计、内存图形与其余子页。玻璃默认关闭，既有边界保持。证据`docs/acceptance/menu-rendering-redmi-20261108.json`，溯源`docs/menu-rendering-performance-defect.md`，附件`../acceptance-artifacts/menu-rendering-20261108/`。

1. 第一步指定交互已验：整策略组YAML返回草稿/长错误定位、单组图标/选项层级保存状态、本地代理列表/映射返回与隔离Activity重建、服务子页跟手拖回/取消/快甩/后台中断及质量页退场正文。715单测、最终Redmi同套App/Test 44/44与81张PNG见`stage-one-interactions-redmi-20261105.json`；不推广真实冷进程、正式主宿主全路径或全应用视觉等价。
2. 第二步累计：后台订阅自然到期下载/应用和运行态节点落库/重建回读已有证据；todo第1/2项编辑器冷恢复与保存死亡窗口已验，见各组JSON。第3项CUSTOM引用在线源的手动/到期Repository自动更新已由`subscription-reference-network-redmi-20261105.json`补验，在线age因唯一授权响应明文且无密钥保留受限。第4项其他资源真实系统文件选择与外部脚本下载/改源/同步/应用已由`resource-saf-scripts-redmi-20261105.json`补验；第5项部分失败恢复、代表HTTP/File/Inline来源和实际规则命中已由`provider-recovery-hits-redmi-20261105.json`补验（4/4、回归14/14），隔离投影/重试/协议与托管失败边界见JSON；第6项成功质量缓存和全部来源/服务目标尝试已由`ip-quality-cache-redmi-20261105.json`补验，远端受限和长窗口配置保护异常独立保留。第7项已用更新后的授权源完成非空Fallback真实核心8阶段实核，并完成固定选择冷进程恢复与清除固定，见`fallback-real-core-redmi-20261105.json`和`fallback-cold-process-redmi-20261106.json`；自然无效`now`按当前Mihomo合法解析契约保留为不可自然构造边界。第8项纯代理系统恢复已验；进程级网络观察已修，最新`system-vpn-protection-redmi-20261105.json`进一步修正TUN保护缺口，767 JVM、Go普通265/265及定向race7/7、final1/2两轮四次首请求HTTP200，3/3回归、纯代理恢复与保护通过；旧TLS EOF因果/长期稳定性仍待查，本组控制器竞态及TLS泄漏已由核心f83e9b10修复，Go全量race265/265、压力40/40、route10及Redmi6轮/4项回归/Always-on首请求两次200和纯代理恢复通过，见`controller-lifecycle-redmi-20261105.json`；源码来源/原失败/撤销条件见`docs/upstream-source-defects.md`，历史延迟诊断和失败批次保留。蜂窝因不可用单独延期，再继续首次干净安装VPN/第三方真调用/其余OEM中具备条件的场景；不清正式数据构造首次安装，缺条件不记通过。
3. 视觉与性能：KUI-018与KUI-019已改善缓存及动态顶卡，优先继续首开、关闭退化和RenderThread慢帧，不能记成全应用性能已通过；其余效果仍后排。空态morph/orbit已有动效编排和几何单测，继续验收其他页面状态与统一截图基线；完成完整List和其他子页视觉、全应用精确缓动、剩余面板手势与键盘联动；Dock/按钮/其他面板live背景与触点glow；玻璃完整采样品质及8MiB正文/连续动效性能。代理组自动`now`/优选徽标分离已在Tab/List验收，但不能替代整页视觉。六种材质/折射/多层菜单已有实现和定向验收，玻璃仍默认关闭。
4. 后续兼容与发布：API24/25、31/32的arm64关键分支与低API核心/VPN/实网；远端CI、独立正式签名、Release真机运行和发布资产。其他ABI不在当前arm64-only交付范围。

HTTP/File Proxy、六种HTTP YAML/text与原90个HTTP/mrs已有分批下载/解析证据；本组补HTTP/File各8种、Inline3种实际HTTPS命中和原MRS代表命中，以及Inline Proxy原节点展开。第5项本组范围已验，不推广90个MRS全部业务站点、所有代理协议或托管混合/apply失败故障注入。第6项缓存与全目标尝试已验，受限源和长窗口保护异常见最新JSON；第7项非空Fallback真实核心8阶段实核及固定选择冷进程恢复已验，见两份Fallback证据；自然无效`now`按合法Mihomo解析契约保留不可自然构造边界；第8项纯代理恢复及最终两轮Always-on默认冷启动/SIGKILL恢复首请求HTTP200已验，早期间歇节点DNS/拨号根因与稳定性仍待查；15秒延迟诊断不计默认通过，蜂窝单独延期。备份/系统恢复、观测次要打磨、语言/主题暂缓，早期Room兼容、TalkBack/自定义无障碍、横屏/大字号扩展不列待办。

### N1：建立真实连接状态和 VPN 权限链

目标：让“启动核心”升级为“启动可用代理连接”。

当前进度：VPN 授权、TUN 生命周期、后台恢复、系统撤销、Wi‑Fi 断开恢复和进程重建已经完成真机闭环；下一项只剩在具备蜂窝网络条件的设备上验证 Wi‑Fi↔移动网络切换。

收尾范围：

1. 在具备已注册蜂窝数据的设备上验证 Wi‑Fi↔移动网络切换。
2. 记录切换期间 `UnderlyingNetworks`、TUN、路由、前台服务和真实请求的连续性。
3. 若切换失败，保留失败 Run，修复网络绑定或服务收口后再复验。

不在 N1 混入：Connections、Requests、DNS 页面和 UI 大改版。

### N2：真实流量和核心观测

目标：用户能确认代理正在工作，并能解释网络行为。

当前进度：Bridge、运行统计、活动连接、Requests、DNS、基础日志和“真实请求诊断”已完成首轮真机闭环；观测详情入口、非空记录、搜索、日志级别筛选、关闭、自动跟随、DNS/日志清空与日志导出组件边界、真实 MainActivity DNS/日志清空与日志文件导出、诊断会话证据包、有界运行诊断事件历史、事件流背压统计、诊断卡片事件流健康摘要、稳定故障归因、异步事件停止边界、页面退出轮询收敛、真实高负载压力验收、观测目的地页签导航和事件流摘要 Compose instrumentation 断言已完成代码、自动化测试和 Redmi 真机闭环，仍需补齐跨设备和完整页面级交互覆盖。

建议范围：

1. Bridge 流量和连接 API。
2. Dashboard 流量卡片。
3. Connections 页面。
4. Requests 页面。
5. 基础日志流。

当前页面级视觉首片已覆盖 Dashboard、Proxy、Connections、Profiles 和 Observation 的固定状态；Profiles 覆盖竖屏系统默认字号下的空态、选中自定义配置和订阅用量三态，横屏/大字号不再作为后续测试范围。Dashboard、Connections 和 Observation 的大字号矩阵属于历史验证记录，不继续执行。Dashboard 运行态已补齐统计/诊断交互断言；首页三态控件已在 Redmi 真机根目录 `1.yaml` 配置下完成真实核心切换和停止/重启恢复，不作为外网流量分流测试证据。Connections 已补齐详情快照恢复、元数据/格式/复制、Flutter 两档高度、浅色、小窗口、底部弹簧进出场、侧栏减速进出场、关闭中重建和真实核心详情 E2E。当前主流程已覆盖 Profiles 添加/导入首片、文件 YAML 预检和确认弹窗固定宿主、Proxy Groups 递归选择、叶子协议显示、按组滚动恢复、List 组内定位、语义锚定和工具菜单，以及 URLTest/Fallback 真实 Mihomo 选择和延迟缓存真实进程重建回归；Profile 卡片分享三项与指定 ID 只读预览已实现；age表单、真实Android Bridge密文解密及FILE新增/替换SAF已完成分项验收；节点菜单保存到本地目录、代理页卡片/组标题首版及 List 组头 sticky/raised 长列表视觉切片已验，运行态保存/应用、父/正文进程草稿与四类真实SAF冷恢复已由11-04前组完成，前组首页/代理组/Providers同批9/9，主页面基础视觉/交互及三分钟Providers观察同批23/23；本批配置/代理页面24项、正文2项、菜单/预览交互3项、节点菜单停止/运行态各1项和URLTest冷进程prepare/verify各1项分批通过。新批正式Room起始为空，`1.yaml`无Fallback；完整页面parity、连续动效、真实加密在线订阅和其他资源SAF仍待验；Connections/Requests/DNS/Logs的细节暂不推进。当前切片不代表任一完整页面parity。

### N3：DNS、日志和故障诊断

目标：让真机问题可以在应用内定位，而不是只能依赖 logcat。

当前进度：DNS/日志 Bridge、列表、清空、内容筛选、日志级别筛选、脱敏导出、真实 MainActivity DNS/日志清空与日志文件导出、诊断会话证据包、运行诊断历史详情/导出、诊断卡片事件流健康摘要、稳定故障归因和核心/网络/DNS 运行诊断记录已完成代码、单元测试和 Redmi 真机闭环；观测页的 Requests、DNS、Logs 三个页签已接入主导航，非空详情入口、无结果搜索、日志级别筛选和关闭已完成真实 `MainActivity` 验证，Room 持久化、迁移、生命周期锚点、异步网络事件停止边界、页面退出轮询收敛和进程重建后的首页历史展示均已完成自动化与真机验证，仍需补齐跨设备和完整页面级交互端到端覆盖。

建议范围：

1. DNS 查询 Bridge 和列表。
2. 日志导出、过滤、清空和更细的故障诊断边界。
3. 统一错误码和用户可见错误文案。
4. 网络切换、DNS 变化和核心重启的诊断记录。

### 2026-10-26：诊断会话证据包切片

- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过；工程共 105 项 Kotlin 单元测试，失败 0、错误 0、跳过 0。
- Debug APK 安装到 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36）；APK 内原生库仅包含 `arm64-v8a`。
- 使用当前工作区根目录 `AGENTS.md` 中的第一条授权路径导入远程 Profile；启动后首页显示“核心运行中”，`VpnService`/`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由均存在。
- 点击“测试当前路由”后，首页显示“诊断会话：通过 · 请求 11 · DNS 6 · 日志 12”，断言为“HTTP 状态、代理链和核心归因均有效”，本次 HTTP 状态为 403。
- 通过 Android `DocumentsUI` 导出 `kclash-diagnostic-1.txt`；设备侧确认文件约 3 KB、41 行，包含表头和 `[requests]`/`[dns]`/`[logs]` 分区，存在脱敏标记，未命中测试秘密值。
- 通过外部 STOP Action 停止核心后，前台服务、`tun0` 和 `198.18.0.0/30` 路由均清理；应用内删除测试 Profile 后回到“还没有配置”，未发现 `FATAL EXCEPTION`。

### 2026-10-26：运行诊断事件记录切片

- 新增固定上限 200 条的运行诊断历史，记录核心启动、重启、停止、失败、VPN 撤销、底层网络可用/即将丢失/丢失/切换和 DNS 服务器变化；诊断证据包新增 `[runtime]` 分区。
- 新增 4 项单元测试，覆盖运行历史有界清理、清空、核心启动/停止/重启顺序和会话只在运行期间收集运行事件；全工程共 109 项测试通过。
- Redmi 真机使用第二条当前授权路径成功 probe：HTTP 200，UI 显示“诊断会话：通过 · 运行 3 · 请求 2 · DNS 8 · 日志 2”。
- 在核心保持运行期间关闭 Wi‑Fi 8 秒后重新开启，TUN、前台服务和 `198.18.0.0/30` 路由保持；恢复后再次 probe 成功，UI 显示“诊断会话：通过 · 运行 8 · 请求 2 · DNS 8 · 日志 2”。
- 新导出的诊断文件约 2 KB、33 行，包含 `CORE_STARTED`、`DNS_UPDATED`、`NETWORK_LOST`、`NETWORK_AVAILABLE`、`NETWORK_SWITCHED` 和三个观测分区，未命中测试秘密值。
- 第一条路径在 Wi‑Fi 恢复后的两次 probe 返回 EOF，UI 均标记诊断失败并保留运行事件；该失败没有被历史成功记录覆盖，随后切换到第二条授权路径完成成功复验。
- 停止核心并通过应用内删除两个测试 Profile 后，设备恢复“还没有配置”，服务、TUN、路由均清理，未发现 `FATAL EXCEPTION`。

### 2026-10-26：事件流背压与长期运行治理切片

- `CoreBridge` 将核心事件改为固定 256 条容量、`replay=0` 的有界共享流；回调线程只使用非阻塞 `tryEmit`，缓冲满时丢弃最新事件，不阻塞 Mihomo 回调线程。
- 单个 Go `message.arguments` 批次最多解析 512 条事件，`requestTraffic` 单批最多保留 256 条更新；超出部分不会继续在 Kotlin 堆中累积。
- 新增 `CoreEventFlowStats`，按一次核心运行会话统计解析批次、解析事件、成功投递、丢弃、批次截断和最近事件时间；`CoreRuntimeBridge`/`CoreRuntimeManager` 将健康状态暴露给诊断会话。
- 诊断证据包新增 `[event_flow]` 分区，记录上述统计，能够区分“没有事件”“事件已投递”和“事件因背压丢弃”。
- 新增事件批次上限、无订阅者丢弃、活动订阅者成功投递、背压丢弃和诊断导出统计测试；`./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过，工程总计 113 项 Kotlin 单元测试通过。
- 最终 Debug APK 安装到 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36），仅包含 `arm64-v8a`，SHA-256 为 `a5fa9f91db26b3fd5a7e1c1e930f17774f5da19939b1d6b6db297a4f51870ab4`。
- 使用第二条当前授权路径启动后首次 probe 出现一次真实网络失败；停止并重启核心后重试返回 HTTP 200，UI 显示“诊断会话：通过 · 运行 7 · 请求 2 · DNS 6 · 日志 2”。最终导出证据中的 `[event_flow]` 为 `parsed_batches=14`、`parsed_events=17`、`emitted_events=17`、`dropped_events=0`、`truncated_events=0`。
- 核心持续运行 40 秒后，PID `26899`、两个前台服务、`tun0` 和 `198.18.0.0/30` 路由保持，未发现应用 `FATAL EXCEPTION`。停止核心后服务和路由清理，测试 Profile 已删除并恢复“还没有配置”。
- 本切片已完成有界事件流和证据统计；真实高负载压力、页面退出轮询生命周期和进程重建后的完整诊断历史关联仍列为后续工作。

### 2026-10-26：运行诊断 Room 持久化边界切片

- `KClashDatabase` 升至 version 3，新增 `runtime_diagnostic_events` 表和 `MIGRATION_2_3`；`RoomRuntimeDiagnosticStore` 接入 `RuntimeDiagnosticHistory`，应用进程启动时先恢复有界历史，再决定是否把下一次核心启动标记为 `CORE_RESTARTED`。
- DAO trim 改为按自增 ID 保留最新 200 条，修复原实现插入 201 条时会多保留 1 条的边界错误；核心启动、停止和 VPN 撤销的生命周期锚点在方法返回前等待持久化，失败路径也会等待队列清空。
- 新增 JVM 断言，验证诊断历史与持久化存储的顺序一致，以及恢复历史后下一次自动启动记录为重启；`./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过，115 项 Kotlin 单元测试通过，失败 0、错误 0、跳过 0。
- 新增 Android instrumentation DAO 测试，使用当前连接的 Redmi Note 8 Pro（serial `wsvwypiz7xwslvl7`，Android 16/API 36）内存 Room 数据库插入 201 条并验证只保留最新 200 条、恢复顺序和 version 2→3 迁移表/索引；`:app:connectedDebugAndroidTest` 通过，3 项测试通过。
- Debug APK 构建通过，原生库仍仅包含 `arm64-v8a`，SHA-256 为 `dbcede6b6262d353b049309decc6e076f549980f5c7d9346d6bc13249d52590e`。
- 本切片仍未完成：应用被系统杀死后的端到端 UI 诊断历史关联、长时间/高负载下异步网络事件的最终落盘保证，以及页面退出后的轮询收敛。

### 2026-10-26：进程重建诊断历史 UI 关联切片

- `MainViewModel` 新增独立的 `diagnosticHistory` 状态流；核心停止时只清理本次运行的统计、连接、Requests、DNS、日志和 probe session，不再清空已恢复的运行诊断历史。
- 首页“真实请求诊断”卡片在未执行 probe 的 IDLE 状态也展示最近 3 条运行事件；`CORE_RESTARTED` 使用“核心重启（CORE_RESTARTED）”明确标识，用户打开首页即可判断历史是否已经恢复。
- 新增 JVM 断言覆盖恢复历史按最新事件倒序展示和重启标签；`./gradlew test :app:compileDebugKotlin :app:lintDebug --no-daemon` 通过，116 项 Kotlin 单元测试通过，失败 0、错误 0、跳过 0。
- 真机使用当前授权清单第一条路径导入 Profile 并启动核心；旧 PID `29928` 经 `cmd activity crash` 终止后，新 PID `30201` 恢复，两个前台服务、`tun0` 和 `198.18.0.0/30` 路由保持；重新打开首页直接看到“恢复历史：6 条”和“核心重启（CORE_RESTARTED）”，未再次执行 probe。
- 通过外部 STOP Action 后服务、TUN 和路由清理完成；临时 Profile 已在应用内删除，首页回到“还没有配置”，未发现 `FATAL EXCEPTION`。
- Debug APK 构建并安装到 `wsvwypiz7xwslvl7`，原生库仍仅包含 `arm64-v8a`，SHA-256 为 `ab620d45610151ba7a0a635e2a84125fcd11fc3a25652affac71805fafdfc4c9`。
- 本切片当时遗留：诊断历史在长时间/高负载异步网络事件下的最终落盘保证和页面退出后的轮询收敛；历史详情/导出入口已在后续切片完成。

### 2026-10-26：运行诊断历史详情与导出切片

- 首页诊断卡片在有恢复历史时提供“查看全部”和“导出”入口；详情弹窗按最新事件优先展示完整历史，并同时显示中文标签、原始 `RuntimeDiagnosticKind`、详情和相对时间。
- `MainActivity` 新增独立的运行诊断历史 `CreateDocument` 导出流程；导出前冻结事件快照，文件使用 UTF-8 表格文本，保留时间线、事件计数和原始枚举值，并复用 `[REDACTED_SECRET]` 脱敏规则。
- 新增 JVM 断言覆盖历史导出事件顺序、事件计数、原始枚举值和 URL/密码脱敏；`./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug --no-daemon` 通过，全工程 117 项 Kotlin 单元测试通过，失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 和 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 均通过；Room instrumentation 3 项测试通过，失败 0、错误 0、跳过 0。
- Debug APK 构建并安装到 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36），APK 内原生库仍仅包含 `arm64-v8a`。
- 使用当前授权清单第一条路径启动核心，首页显示“恢复历史：3 条”；点击“查看全部”打开“运行诊断历史（3）”弹窗，看到 `DNS_UPDATED`、`CORE_STARTED` 和 `NETWORK_AVAILABLE` 详情；点击“导出”后通过 Android `DocumentsUI` 保存运行诊断历史文件，设备侧文件大小 224 bytes，包含 `event_count=3` 和三类事件记录。
- 通过外部 STOP Action 停止核心后，服务和 TUN 路由清理；应用内删除测试 Profile，首页恢复“还没有配置”，未发现 `FATAL EXCEPTION`。
- 本切片已完成；真实高负载异步网络事件最终落盘和页面退出后的轮询收敛仍列为后续工作。

### 2026-10-26：异步网络诊断事件最终落盘切片

- `NetworkObserver` 的网络回调、延迟 DNS 任务和停止动作现在共享生命周期锁；停止时先关闭回调闸门并取消延迟任务，尾部网络事件不会在核心停止后重新进入诊断历史。
- `RuntimeDiagnosticHistory.flush()` 改为等待持久化尾任务，并在尾任务稳定且没有活动写入后才返回；重复 STOP、失败回滚和正常停止都等待诊断尾巴落盘。
- 新增受控挂起存储单元测试，验证异步 append 尚未完成时 `flush()` 不会提前返回；`./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过，工程总计 118 项 Kotlin 单元测试通过，失败 0、错误 0、跳过 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过，Room instrumentation 3 项测试通过；Debug APK 安装到 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36），原生库仅包含 `arm64-v8a`，APK SHA-256 为 `776ea193dd29817e80b05397c5b3642c271a7f936767c1b0cc2bf8404b5636ef`。
- 使用当前工作区根目录 `AGENTS.md` 第一条授权路径启动核心；执行 Wi‑Fi 关闭 8 秒、恢复后等待 10 秒并立即发送 STOP，期间 TUN、前台服务和 `198.18.0.0/30` 路由保持，首页历史显示 9 条并包含 `NETWORK_LOST`、`NETWORK_AVAILABLE`、`DNS_UPDATED`、`NETWORK_SWITCHED` 和 `CORE_STOPPED`。
- 强制停止并重新打开应用后首页仍显示“恢复历史：9 条”；通过“查看全部”核对事件详情未丢失。测试 Profile 已通过应用内删除，设备恢复“还没有配置”，停止后服务、TUN 和路由均清理，未发现应用 `FATAL EXCEPTION`。
- 本切片已完成；真实高负载压力验收和页面退出后的轮询收敛仍列为后续工作。

### 2026-10-26：页面退出后的轮询收敛切片

- `MainViewModel` 新增页面可见性闸门，统计和活动连接轮询只有在核心为 `RUNNING` 且首页 Activity 处于 `onStart` 到 `onStop` 区间时运行；Activity 进入后台会取消当前 `collectLatest`，回到前台立即恢复轮询。
- `MainActivity` 在 `onStart`/`onStop` 显式通知 ViewModel，避免核心继续后台运行时 UI 轮询持有无意义的 JNI 和生命周期锁。
- 新增状态组合单元测试，覆盖核心运行/停止/失败与页面可见/不可见组合；`./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过，工程总计 119 项 Kotlin 单元测试通过，失败 0、错误 0、跳过 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过，Room instrumentation 3 项测试通过；Debug APK SHA-256 为 `1aacd5dc99d8a580e3bf0ba8c1c23ad27791e789b64046d50b478abb2e2009ce`，native 库全部位于 `arm64-v8a`。
- Redmi 真机使用当前工作区根目录 `AGENTS.md` 第一条授权路径启动核心；按 Home 键进入后台 7 秒时前台应用切换到系统启动器，但 `VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由保持。重新打开 KClash 后仍显示“核心运行中”，运行统计卡片恢复，未发现 `FATAL EXCEPTION`。
- 验收结束后通过外部 STOP Action 停止核心并在应用内删除测试 Profile，首页恢复“还没有配置”，服务、TUN 和路由均清理。
- 本切片已完成；真实高负载压力验收仍列为后续工作。

### 2026-10-26：真实高负载事件流压力验收

- 真机使用当前工作区根目录 `AGENTS.md` 第一条授权路径导入远程 Profile 并启动核心；为确保压力流量进入 Mihomo 事件管线，设备侧请求通过核心本地 mixed port `127.0.0.1:7890` 发出，未使用未授权真实路径。
- 先执行 1 轮、再追加 3 轮，每轮 64 个并发 HTTP 请求，共 256 个压力请求；首页在首轮后显示 `请求记录（74）` 和 `DNS 查询（133）`，运行累计流量约为 `↑ 198.2 KB · ↓ 4.2 MB`。
- 最新诊断证据包的 `[event_flow]` 记录为 `parsed_batches=79`、`parsed_events=1525`、`emitted_events=1525`、`dropped_events=0`、`truncated_events=0`；事件流在高负载下没有丢弃或截断。
- 压力期间应用进程保持存活，首页仍显示“核心运行中”；`VpnService`、`ProxyService` 均保持前台状态，`tun0` 和 `198.18.0.0/30` 路由持续存在，未发现 KClash `FATAL EXCEPTION`。
- 通过外部 STOP Action 停止核心后，服务、TUN 和路由均清理；测试 Profile 已在应用内删除，首页恢复“还没有配置”。
- 本项已完成；下一步转入观测功能的自动化端到端断言和更细故障诊断交互。

### 2026-10-26：诊断卡片事件流健康摘要切片

- `DiagnosticSessionUiState` 的事件流统计新增页面摘要格式化函数，诊断卡片直接显示解析、投递、丢弃和截断数量；只要存在丢弃或截断，页面会显示“诊断证据可能不完整”的错误提示。
- 新增 JVM 测试覆盖健康计数和丢弃/截断边界；`./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过，工程总计 120 项 Kotlin 单元测试通过，失败 0、错误 0、跳过 0。
- Debug APK 安装到 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36）后，使用当前工作区根目录 `AGENTS.md` 第一条授权路径启动核心并通过本地 mixed port 产生压力请求；首页诊断卡片显示 `事件流：解析 170 · 投递 170 · 丢弃 0 · 截断 0`，未出现不完整提示。
- 压力回归期间 `VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由保持；通过 STOP Action 停止后服务和路由清理，Profile 删除后首页恢复“还没有配置”。
- 本切片已完成；下一步转入观测功能的自动化端到端断言和更细故障诊断交互。

### 2026-10-27：诊断卡片事件流摘要 Compose instrumentation 切片

- `DiagnosticEventFlowSummary` 对外开放给 AndroidTest 源集；新增健康计数和丢弃/截断警告的 Compose UI 断言，分别覆盖 `解析 170 · 投递 170 · 丢弃 0 · 截断 0` 与不完整证据提示。
- 修复 AndroidTest 协程 ServiceLoader 边界：Debug 应用合并 Android/测试协程服务条目，AndroidTest 显式使用 `kotlinx-coroutines-android`，避免测试运行时找不到异常处理器；该测试依赖只进入 Debug，不进入 Release。
- Android 16/API 36 移除了 Espresso 旧版反射使用的 `InputManager.getInstance()`；AndroidTest 固定 `espresso-core:3.7.0`，改用 `Context.getSystemService()` 路径，并将 Compose 测试迁移到 `junit4.v2.createComposeRule`。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过：120 项 Kotlin 单元测试失败 0、错误 0、跳过 0；Lint 错误 0，保留 31 条 Warning。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过：5 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 2 项；设备为 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36）。
- Debug APK `android/app/build/outputs/apk/debug/app-debug.apk` SHA-256 为 `b30f2e8bb91605c8a87b2294c4c98e2f741c2ebab65a28dc12aa62b54a90cb44`，仅包含 `arm64-v8a` 原生库。
- 本切片已完成；下一步进入稳定故障归因和更完整的页面级端到端覆盖。

### 2026-10-27：稳定诊断故障归因切片

- `MainViewModel` 新增稳定 `DiagnosticFindingCode`，统一区分 HTTP 响应、核心错误、无结果、无 HTTP 状态、无代理链和事件证据丢失；核心错误优先于 HTTP 状态，HTTP 403/200 在存在代理链时均保留为可归因的 `HTTP_RESPONSE`。
- 首页诊断卡片新增“诊断归因”和“下一步建议”，支持展开脱敏原始详情；诊断导出新增开始/结束时间、归因码、标题、建议和脱敏详情，避免把核心原始错误直接当成用户可执行结论。
- 事件流在“解析为 0 但已有丢弃/截断”时仍显示不完整证据警告，防止空结果掩盖管线丢失。
- 新增 JVM 断言和 2 项 Compose instrumentation 断言；`./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过：121 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过：7 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 4 项。
- Redmi Note 8 Pro（Android 16/API 36）真机只使用当前工作区根目录 `AGENTS.md` 中的两条授权路径：第一条真实返回 HTTP 403，UI 归因为 `HTTP_RESPONSE` 并明确说明这是目标服务业务状态；第二条真实返回 HTTP 200，同样归因为 `HTTP_RESPONSE`。两次事件流分别为解析/投递 64/64 和 50/50，丢弃与截断均为 0。
- 真机诊断卡片、原始详情展开、导出字段和停止清理均完成复验；两个测试 Profile 通过应用内删除，设备恢复“还没有配置”，未发现 `FATAL EXCEPTION`。
- Debug APK `android/app/build/outputs/apk/debug/app-debug.apk` SHA-256 为 `c60e7ac24dd9a34d8275998bb6c95486146f95d7bad50774fe389f5d76704561`，仅包含 `arm64-v8a` 原生库。
- 本切片已完成；下一步转入完整页面级端到端断言、N1 蜂窝网络切换补测和 N4 Profiles 高级能力。

### 2026-10-27：运行期间 Profile 变更保护切片

- `ProfilesViewModel.delete` 现在与切换、编辑、保存使用同一运行态边界；核心运行期间拒绝删除 Profile，并通过统一错误事件提示“核心运行中，请先停止核心后再修改配置”。
- Compose Profile 卡片和删除确认按钮在核心运行期间直接禁用，避免用户进入会改变当前核心文件或选择的操作；ViewModel 仍保留守卫，防止其他入口绕过 UI。
- 新增 `isProfileMutationBlocked` 单元测试，覆盖 `STOPPED`、`STARTING`、`RUNNING` 和 `FAILED` 状态；`./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过：122 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过：7 项 instrumentation 测试失败 0、错误 0、跳过 0；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- 本切片完成了代码和自动化验证；运行中删除 Profile 的真实 UI 流程纳入下一条页面级 Compose/Instrumentation E2E 切片，当前未将其标记为真机完成。
- 下一步进入 Profiles 自定义配置创建→列表展示→编辑保存的页面级 E2E 测试隔离，随后继续 N4 高级配置能力。

### 2026-10-27：Profiles 组件级 Compose 回归切片

- 将 `ProfileCard`、`ProfileEditorDialog` 和 `AddProfileDialog` 暴露为 AndroidTest 可验证的内部 Compose 入口，保持业务操作仍由 ViewModel 回调承载。
- 新增 3 项 Compose instrumentation：自定义配置模式切换并触发保存回调、编辑对话框展示配置名称并保存初始 YAML、核心运行期间编辑/删除按钮禁用。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过：122 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过：10 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 7 项。
- Debug APK SHA-256 为 `6c49a2fda426abead03c433e6f3c2d09e3c3bdba5c2bf31de938f497e82ef485`，仅包含 `lib/arm64-v8a/libclash.so`；本切片未使用真实订阅路径。
- 本切片完成了 Profiles 关键组件的稳定 Compose 回归；下一步仍需建立隔离的 Repository/ViewModel 测试宿主，覆盖自定义 Profile 创建→列表→编辑保存的真实状态流，以及删除确认流程。

### 2026-10-27：Profiles Repository/ViewModel 隔离测试边界切片

- 新增 `ProfileStore` 接口，`ProfileRepository` 继续负责 Room、文件和订阅实现；`ProfilesViewModel` 只依赖该稳定数据源边界。
- 新增 `CoreRuntimeStatusProvider`，`ProfilesViewModel` 只订阅核心运行态，不依赖完整 `CoreRuntimeManager`，便于在不启动 Mihomo 的情况下验证配置变更策略。
- Hilt 通过 `AppModule` 将现有 Repository 和 Runtime Manager 绑定到两个接口，生产运行时依旧使用同一实现，不改变核心生命周期所有权。
- 新增 2 项 `ProfilesViewModel` 单元测试：运行中删除只发出阻止错误且不调用 Repository；停止后删除正常委托到 Repository。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon` 通过：124 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过：10 项 instrumentation 测试失败 0、错误 0、跳过 0。
- Debug APK SHA-256 为 `f643a0893d3c56cab68ac045e167f3def0422ad71c3a8273ab69742090e354b4`，仅包含 `lib/arm64-v8a/libclash.so`。
- 本切片完成了隔离测试边界；下一步把 fake Store 接入 Profiles 页面宿主，覆盖创建→列表刷新→编辑保存→删除确认的组合状态流。

### 2026-10-27：Profiles 页面宿主组合状态流切片

- 新增 `ProfilesPanel` Compose 宿主，将配置标题、空态、添加配置弹窗、Profile 列表、删除确认和编辑器状态统一收口；文件选择器、二维码选择器和真实 `ProfilesViewModel` 回调仍由 `KClashApp` 持有。
- 新增 `ProfilesPanelInstrumentedTest` 4 项 Compose instrumentation，覆盖自定义配置创建后列表刷新、编辑保存关闭并回传 YAML、删除确认取消/确认，以及核心运行期间编辑和删除入口禁用。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过：124 项 Kotlin 单元测试失败 0、错误 0、跳过 0；Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过：14 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 11 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。本轮未访问真实订阅路径。
- Debug APK SHA-256 为 `7a06c6032100a848c2aefe79fecaad909ee6744cd1f94fb843b19be42680854a`，仅包含 `lib/arm64-v8a` 原生库。
- 本切片完成页面宿主的组合状态回归，但仍未声称真实 Repository/ViewModel 页面 E2E 已完成；下一步建立 fake Store 驱动的真实 `ProfilesViewModel` 宿主，随后继续 N4 的高级配置编辑、排序、备份恢复和订阅自动更新。

### 2026-10-27：ProfilesViewModel 页面状态流切片

- 新增 `ProfilesViewModelPanelInstrumentedTest`，通过真实 `ProfilesViewModel`、fake `ProfileStore` 和 fake `CoreRuntimeStatusProvider` 驱动 `ProfilesPanel`，不启动 Room、网络订阅或 VPN 核心。
- 真机 Compose 流程已覆盖：创建自定义 Profile → StateFlow 刷新列表 → 异步读取 YAML → 保存编辑内容 → 删除确认并回到空态；fake Store 断言保存内容为 `mixed-port: 7890`，删除后 Profile 和当前选择均为空。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过：124 项 Kotlin 单元测试失败 0、错误 0、跳过 0；Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过：15 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 12 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。本轮未访问真实订阅路径。
- Debug APK SHA-256 为 `7a06c6032100a848c2aefe79fecaad909ee6744cd1f94fb843b19be42680854a`，仅包含 `lib/arm64-v8a` 原生库。
- 本切片完成真实 ViewModel 状态流的无网络页面回归；仍需后续补真实 `ProfileRepository`/Room 页面 E2E 和 Android 主页面导航覆盖，再进入高级配置分区编辑。

### 2026-10-27：ProfilesRepository/Room 页面状态流切片

- 新增 `ProfilesRepositoryPanelInstrumentedTest`，使用真实 `ProfileRepository`、内存 `KClashDatabase` 和应用文件目录，通过真实 `ProfilesViewModel` 驱动 `ProfilesPanel`；订阅客户端仅提供未调用的失败桩，本轮未访问网络。
- Redmi 真机 Compose 流程已覆盖：创建自定义 Profile 后真实 Room 记录和当前选择出现；编辑器输入 `mixed-port: 7890` 后真实 Profile 文件可读回相同内容；删除确认后 Room 记录、当前选择和 Profile 文件全部清理，页面回到空态。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过：124 项 Kotlin 单元测试失败 0、错误 0、跳过 0；Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过：16 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 13 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。本轮未访问真实订阅路径。
- Debug APK SHA-256 为 `7a06c6032100a848c2aefe79fecaad909ee6744cd1f94fb843b19be42680854a`，仅包含 `lib/arm64-v8a` 原生库。
- Profiles 的真实 Repository/Room 状态流已具备页面回归证据；下一步补 Android 主页面导航/Activity 宿主覆盖，再进入 N4 高级配置分区编辑。

### 2026-10-27：MainActivity 宿主入口 Compose instrumentation 切片

- 新增 `MainActivityInstrumentedTest`，从真实 `MainActivity` 进入首页，验证 Hilt、ViewModel、Material 主题和 Compose 根页面已经连接到可交互宿主。
- 真机测试授予 `POST_NOTIFICATIONS` 权限后，断言首页显示 `KClash`、`核心状态`、`启动核心` 和 `添加配置` 入口；测试不触发订阅网络、VPN 授权或核心启动。
- `androidTestImplementation` 新增 `androidx.test:rules:1.7.0`，仅用于 AndroidTest 的通知权限授予，不进入发布 APK。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过：124 项 Kotlin 单元测试失败 0、错误 0、跳过 0；Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过：17 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 14 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。本轮未访问真实订阅路径。
- Debug APK SHA-256 为 `7a06c6032100a848c2aefe79fecaad909ee6744cd1f94fb843b19be42680854a`，仅包含 `lib/arm64-v8a` 原生库。
- 本切片完成真实 Activity 宿主入口回归；下一步进入完整页面导航覆盖和 N4 高级配置分区编辑。

### 2026-10-27：首页与配置页导航切片

- 在现有单 Activity/Compose 结构上增加轻量目的地状态和 Material 3 `NavigationBar`，先建立“首页”和“配置”两个稳定手机入口。
- 首页承载运行状态、统计和诊断卡片；配置页承载现有 Profiles 宿主；代理、连接和观测分别承载代理组、活动连接以及 Requests/DNS/Logs 页签，核心生命周期和 ViewModel 数据源不变。
- 悬浮“添加配置”入口会先切换到配置页再打开弹窗，离开配置页时关闭添加弹窗，避免跨页面残留 UI 状态。
- 新增真实 `MainActivity` 导航回归：首页 → 配置 → 首页，并使用页面测试标记断言目的地切换；本轮未访问真实订阅路径、VPN 或核心启动。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon` 通过；Kotlin 单元测试 124 项通过，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过：18 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 15 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `b63bb174641fd1cc5f4d9bf034090362f58be66db85cdade15f029832d0895ba`，仅包含 `lib/arm64-v8a` 原生库。
- 本切片完成 Dashboard/Profiles 的第一阶段可达导航；下一步扩展 Requests、DNS、Logs、Connections 和 Proxy 的独立页面入口，再进入 N4 高级配置分区编辑。

### 2026-10-27：代理与连接独立页面导航切片

- 扩展 `MainDestination`，新增“代理”和“连接”两个手机目的地；代理组卡片只在代理页展示，活动连接卡片只在连接页展示。
- 本切片完成时首页仍保留运行状态、统计、诊断、请求、DNS 和日志卡片，未改变 `MainViewModel`、`ProxyGroupsViewModel` 或 `CoreRuntimeManager` 的状态边界；后续观测切片将三类观测内容收口到独立页签。
- `MainActivityInstrumentedTest` 增加真实宿主断言：首页 → 配置 → 首页 → 代理 → 连接 → 首页，并验证代理组与活动连接页面内容可见；本轮未访问真实订阅路径、VPN 或核心启动。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon` 通过；Kotlin 单元测试 124 项通过，Lint 错误 0、Warning 31。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过：18 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 15 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `ea2d5d51753a1ca78b2bed38da70e892491d6703dcbae7b0e7f5e62d51444b6c`，仅包含 `lib/arm64-v8a` 原生库。
- 本切片完成代理与连接两个独立页面入口；下一步拆分 Requests、DNS、Logs 观测页面，再进入 N4 高级配置分区编辑。

### 2026-10-27：观测目的地与 Requests/DNS/Logs 页签导航切片

- 新增 `MainDestination.OBSERVATIONS`“观测”顶层手机目的地，使用 Material 3 `PrimaryTabRow` 提供“请求”“DNS”“日志”三个页签；首页移除重复的 Requests、DNS、Logs 卡片，避免底部导航与首页内容重复。
- 观测页继续复用 `MainViewModel` 已有的 Requests、DNS、Logs `StateFlow`、筛选详情对话框、清空和日志导出回调；本切片不改变 `CoreRuntimeManager`、Bridge 或数据边界。
- `MainActivityInstrumentedTest` 更名为 `mainActivityNavigatesAcrossPrimaryAndObservationDestinations`，真实宿主覆盖首页 → 配置 → 首页 → 代理 → 连接 → 首页 → 观测 → DNS → 日志 → 首页，并断言观测页及三个页签内容可见。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon`：通过；124 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32（另有 2 条 Hint）。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；18 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 15 项；设备为 Redmi Note 8 Pro（Android 16/API 36），报告 `tests=18 failures=0 errors=0 skipped=0`。
- Debug APK SHA-256 为 `831e4a42211ac80d58195a1cc85461ef2147b554d7530a54ae114bda87e27247`，仅包含 `lib/arm64-v8a/libclash.so`。
- 本轮只验证本地导航和现有状态渲染，未访问当前 `AGENTS.md` 授权订阅路径、VPN 或核心启动；下一步进入 N4 Profiles 高级配置分区编辑，同时继续补齐观测页交互 E2E。

### 2026-10-27：自定义 Profile Rules 分区编辑切片

- 新增 `ProfileRulesEditor` 纯 Kotlin 规则分区处理器：从自定义 Profile 原始 YAML 提取顶层 `rules`，保存时只替换该区块；没有 `rules` 时追加区块，其他端口、DNS 和代理字段保持原文。
- 自定义 Profile 卡片新增“高级”入口和规则编辑对话框；每行编辑一条 Clash 规则，校验匹配类型与动作，拒绝 `SUB-RULE`，远程订阅 Profile 不开放该入口。
- `ProfilesViewModel` 新增规则编辑 `StateFlow`、打开/关闭/保存事件；核心运行期间与整份 YAML 编辑使用同一运行态保护，非法规则保存失败时保留编辑内容和对话框。
- 新增 3 项 `ProfileRulesEditor` 单元测试、2 项 `ProfilesViewModel` 单元测试和 1 项 Compose 规则编辑测试；真实 Repository/Room Compose 流程覆盖规则写回文件。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 129 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；19 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 16 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `f7398958c0e01e6a6e3d1ee736cdc2e0928c77e85b6b5303dd2de328a0ae628c`，仅包含 `lib/arm64-v8a/libclash.so`。
- 本轮未访问当前 `AGENTS.md` 授权订阅路径、VPN 或核心启动；下一步继续实现 Proxy Groups/Proxies 分区，随后补 Providers、Scripts、Overrides 和完整页面级 E2E。

### 2026-10-27：自定义 Profile Proxies 分区编辑切片

- 新增 `ProfileProxiesEditor` 纯 Kotlin 代理分区处理器：从自定义 Profile 原始 YAML 提取顶层 `proxies` 列表，保存时只替换该区块；没有区块时追加，空编辑内容写为 `proxies: []`，其他端口、规则和 DNS 字段保持原文。
- 自定义 Profile 卡片新增“代理”入口和代理 YAML 编辑对话框；支持粘贴带 `proxies:` 包装的列表，校验每个条目的非空 `name`、`type`，拒绝重复名称和非列表结构，远程订阅 Profile 不开放该入口。
- `ProfilesViewModel` 新增代理编辑 `StateFlow`、打开/关闭/保存事件；核心运行期间与整份 YAML、Rules 编辑使用同一运行态保护，非法代理保存失败时保留编辑内容和对话框。
- 新增 3 项 `ProfileProxiesEditor` 单元测试、2 项 `ProfilesViewModel` 单元测试和 1 项 Compose 代理编辑测试；真实 Repository/Room Compose 流程覆盖 Rules 与 Proxies 连续写回文件。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 134 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；20 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 17 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `194a18d914f28ba340a3feb3a37dbfcc970ad34a06c63279c406173f0e7a8f74`，仅包含 `lib/arm64-v8a/libclash.so`。
- 本轮未访问当前 `AGENTS.md` 授权订阅路径、VPN 或核心启动；下一步补 Providers、Scripts、Dialers 和完整页面级 E2E。

### 2026-10-27：自定义 Profile Proxy Groups 分区编辑切片

- 新增 `ProfileProxyGroupsEditor` 纯 Kotlin 代理组分区处理器：从自定义 Profile 原始 YAML 提取顶层 `proxy-groups` 列表，保存时只替换该区块；没有区块时追加，空编辑内容写为 `proxy-groups: []`，其他端口、代理、规则和 DNS 字段保持原文。
- 自定义 Profile 卡片新增“代理组”入口和代理组 YAML 编辑对话框；与 Flutter schema 对齐支持 `select`、`url-test`、`fallback`、`load-balance`，拒绝核心不支持的 `relay`，支持带 `proxy-groups:` 包装的粘贴内容。
- 代理组名称和类型校验支持引号与行尾注释；区块替换保留下一个顶层字段前的空白和注释，避免局部编辑吞掉原始 YAML 边界内容。
- `ProfilesViewModel` 新增代理组编辑 `StateFlow`、打开/关闭/保存事件；核心运行期间与整份 YAML、Rules、Proxies 编辑使用同一运行态保护，非法代理组保存失败时保留编辑内容和对话框。
- 新增 4 项 `ProfileProxyGroupsEditor` 单元测试、2 项 `ProfilesViewModel` 单元测试和 1 项 Compose 代理组编辑测试；覆盖提取/替换、追加/包装、空列表、重复名称、非法类型、引号/行尾注释和边界注释保留。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 140 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；21 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 18 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `0117572215f4a96342cd70c0c962eb67897b8f2756409de5378ffa5fcb241e41`，仅包含 `lib/arm64-v8a` 原生库。
- 本轮未访问当前 `AGENTS.md` 列出的两条真实订阅路径、VPN 或核心启动；7.4 的 URLTest/Fallback 运行时语义、批量测速、搜索和缓存仍未完成。下一步进入 Dialers 分区，并继续补 Profile 排序、备份恢复、订阅自动更新和完整页面级 E2E。

### 2026-10-27：自定义 Profile Providers 分区编辑切片

- 新增 `ProfileProvidersEditor` 纯 Kotlin Provider map 处理器：分别提取和替换顶层 `proxy-providers`、`rule-providers`，没有区块时追加，空编辑内容写为 `{}`，其他端口、代理组、规则和 DNS 字段保持原文。
- 自定义 Profile 卡片新增“Provider”入口和双 map YAML 编辑对话框；支持带 `proxy-providers:`/`rule-providers:` 包装的粘贴内容，校验嵌套 map、非空配置、重复名称和错误的 scalar 条目，远程订阅 Profile 不开放该入口。
- `ProfilesViewModel` 新增 Provider 编辑 `StateFlow`、打开/关闭/保存事件；核心运行期间与其他高级分区编辑使用同一运行态保护，非法 Provider 保存失败时保留编辑内容和对话框。
- 新增 4 项 `ProfileProvidersEditor` 单元测试、2 项 `ProfilesViewModel` 单元测试和 1 项 Compose Provider 编辑测试；覆盖双 map 提取/替换、包装粘贴、空 map、边界注释和非法结构。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 146 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；22 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 3 项、Compose 19 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `bb581765f3e47ca991d4187ef5da79501ef8a2c36bf2bdd5d302e6360f32f4a5`，仅包含 `lib/arm64-v8a` 原生库。
- 本轮未访问当前 `AGENTS.md` 列出的两条真实订阅路径、VPN 或核心启动；本切片之后仍需推进 Dialers 和页面级 E2E。

### 2026-10-27：Provider 字段化覆盖与核心应用切片

- 新增 `ProxyProviderOverride` 和 `ProfileProviderOverrides` 类型，覆盖健康检查 URL、interval、timeout、lazy、expected-status、additional-prefix/suffix、UDP、skip-cert-verify 和 ip-version；使用 Gson JSON 保存在 Profile 自有 `overridesJson`，不读取或迁移 Flutter 数据。
- 自定义 Provider 编辑器在 YAML map 下方展示字段化代理 Provider 列表；每个 Provider 使用独立对话框编辑上述字段，布尔项保留“未设置/显式 true 或 false”，IP 版本支持未设置、IPv4、IPv6 和双栈。
- `ProfileRepository.activeProfile()` 将覆盖 JSON 传给 `CoreRuntimeManager`；核心启动前在暂存 `config.yaml` 合并匹配 Provider 的 `health-check` 和 `override` 子区块，原始 Profile 文件不被覆盖。
- 新增 3 项服务层单元测试、1 项 ViewModel 单元测试、1 项 Compose instrumentation 测试和 1 项 Room instrumentation 测试；覆盖 JSON 往返、局部 YAML 合并、未知字段保留、启动暂存配置、字段表单保存和数据库持久化。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 150 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；24 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 4 项、Compose 20 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `d7204a8f51af8eddb376d7152a0bfed0dcafb65c2497d4ecea8c4fee4fffb0fd`，仅包含 `lib/arm64-v8a` 原生库；本轮未访问当前 `AGENTS.md` 列出的两条真实订阅路径、VPN 或核心启动。
- 本切片完成 Providers 字段化 override 的持久化、UI 和核心应用闭环；下一步进入 Dialers，再补备份恢复和订阅自动更新，并继续补完整页面级 E2E。

### 2026-10-27：Scripts 远程导入与手动同步切片

- 脚本库新增远程脚本导入对话框和远程脚本“同步”入口；Repository 只接受 HTTP/HTTPS，限制重定向次数和 2 MiB 正文大小，下载后保存到应用私有脚本目录。
- 远程导入支持从 `Content-Disposition` 或 URL 推导名称，URL 与名称去重；手动同步先下载并原子替换正文，再更新 Room 的 `lastUpdateAt`，下载失败时保留上一份可读正文。
- `ScriptClient` 通过 Hilt 注入，UI 仍只通过 `ScriptsViewModel` 事件进入 Repository；本切片只完成远程资源管理，不执行 JavaScript，也不把任何真实订阅路径写入代码或测试。
- 新增 `ScriptsViewModelTest` 远程导入/同步委托、`ScriptsRepositoryInstrumentedTest` 原子导入/同步和 `ScriptsUiInstrumentedTest` 远程对话框回调覆盖；测试清理等待 Room StateFlow 收敛，避免关闭内存库时 collector 仍访问连接池。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 158 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 32、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：首次运行发现新增 Repository 测试的 Room 清理竞态，修复后完整重跑通过；34 项 instrumentation 测试失败 0、错误 0、跳过 0，其中 Room 6 项、Compose 28 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `5410232c37fd920234021ca3eb98fdcf6ea96cb9c75a0ecd39cb1141a338a385`，APK 仅包含 `lib/arm64-v8a/libclash.so`。
- 本轮未访问当前工作区 `AGENTS.md` 列出的两条真实订阅路径、VPN 或核心启动；下一步推进 Dialers 高级配置，并继续备份恢复、订阅自动更新和完整页面级 E2E。

### 2026-10-27：Scripts JavaScript 执行边界切片

- 服务层新增 `ProfileScriptEvaluator` 契约和 `ProfileScriptApplier`；绑定脚本在核心启动时读取应用私有正文，将 YAML 安全解析为 JSON，执行 `main(config)`，再把返回对象序列化回暂存 YAML。
- Android App 使用系统 WebView/V8 执行现代 JavaScript；每次执行创建独立 WebView，关闭网络、文件和内容访问，支持顶层 `const main`、对象展开、可选链、`Map`/`Set` 和 Promise。
- 执行边界与 Flutter 契约对齐：`undefined`/`null` 回退原配置，缺少 `main`、语法/运行时异常、非对象返回、未收敛 Promise 和超时均失败闭环；脚本正文限制 2 MiB，配置转换限制 8 MiB。
- `ProfileRepository.activeProfile()` 将绑定脚本正文路径传给服务层；脚本文件缺失不会静默跳过，`CoreRuntimeManager` 在 `validateConfig` 和 `setupConfig` 前阻止启动，原始 Profile YAML 不被改写。
- 新增 `ProfileScriptTest`、CoreRuntimeManager 脚本成功/失败测试，以及真机 WebView instrumentation，覆盖现代语法、Promise rejection、缺少入口和 10 秒未收敛边界。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 164 项 Kotlin 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 保持原有非阻断级别。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；38 项 instrumentation 失败 0、错误 0、跳过 0，其中 Room 6 项、Compose 28 项、WebView JavaScript 4 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256：`701e61753fedc0978697adccf28812e11f2496bfb84f3f0476e070ee549e95e6`；APK 原生库仅包含 `arm64-v8a`，未访问真实订阅路径、VPN 或核心启动。

### 2026-10-27：自定义 Profile Dialers 配置切片

- 新增 `ProfileDialerEditor`，支持 `代理名 -> 目标名` 和 `代理名 = 目标名` 两种输入，忽略整行/行尾注释，读取原始 YAML 中已有的 `dialer-proxy` 映射，并拒绝空值、重复代理和格式错误。
- `ProfileProviderOverrides` 新增可区分“未设置”和“明确清空”的 Dialers map；与 Provider/DNS 覆盖共用 Kotlin 自有 `overridesJson`，保存任一高级分区时保留其他分区。
- `ProfileDialerOverrideApplier` 在核心启动暂存阶段移除旧 `dialer-proxy` 并写入当前映射，校验代理、代理组目标和循环依赖；缺少 `proxies` 时只允许空覆盖，原始 Profile YAML 不被改写。
- 配置页 Profile 卡片新增 Dialers 对话框；核心运行中入口和保存均受现有运行态保护，保存失败会保留编辑内容和错误反馈。
- 新增 Dialers 解析/提取/错误测试、服务层 YAML 应用测试、核心暂存配置测试、ProfilesViewModel 保存/错误/覆盖共存测试、Compose 对话框回调测试和真实 Room 持久化测试。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 179 项 JVM 单元测试失败 0、错误 0、跳过 0，Lint 错误 0、Warning 34、Hint 2。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；42 项 instrumentation 失败 0、错误 0、跳过 0，其中 Room 7 项、Compose 31 项、WebView JavaScript 4 项；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- 增补带引号 inline `proxy-groups.proxies` 的循环依赖测试，确认 `ProfileDialerOverrideApplier` 在 `proxies: ["node-b"]` 语法下仍能识别循环并拒绝启动配置。
- Debug APK SHA-256 为 `32d4c5a4c982d7d164998b873f2ea0b533d9f5ebc68621ee41e6b91bf74e910d`，APK 内原生库全部位于 `lib/arm64-v8a/`；本切片只使用自定义 Profile 和本地 Room 流程，未访问当前 `AGENTS.md` 列出的两条真实订阅路径。
- 本切片完成 Dialers 的配置编辑、持久化、核心暂存应用和 ARM64 真机页面/Room 验证；7.4 的 URLTest/Fallback 运行时语义、批量测速、搜索缓存、订阅自动更新和备份恢复仍未完成。

### 2026-10-27：URLTest/Fallback computed 组交互与自动测速事件切片

- `ProxyGroupInfo` 新增 `isComputed`/`isSelectable`，将 `URLTest` 和 `Fallback` 与手动 `Selector` 区分；computed 组可以打开选择对话框，点击当前节点会向 Mihomo 发送空目标以恢复自动选择。
- `CoreBridge` 解析 Mihomo `delay` 事件为 `CoreEvent.Delay`；`ProxyGroupsViewModel` 按代理组、叶子节点和测试 URL 隔离延迟结果，并在代理组刷新后清理已经失效的结果。
- computed 组 UI 使用“选择”文案，显示“点击当前节点可恢复自动选择”，单节点延迟结果继续沿用当前测试入口；未把自动测速结果错误地当成 Selector 的手动切换状态。
- 新增 Core Bridge `delay` 事件解析、computed 选择目标、延迟结果隔离和 Compose computed 组交互测试。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 183 项 JVM 测试失败 0、错误 0、跳过 0，Lint 通过。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；43 项 instrumentation 测试失败 0、错误 0、跳过 0，设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `df54e264fc42c3a1071871fddb4eae20b7ea8acbc2b408a38ec8812768ac2b83`，APK 仅包含 `lib/arm64-v8a/` 原生库。
- 已按真机约束把工作区 `1.yaml` 送入设备文件选择器并确认文件可见，但设备在交互期间被无关前台应用反复抢占，未形成可信的 KClash 核心启动、代理页 computed 组展示、自动测速结果或 VPN/TUN 清理证据；本轮未访问两条授权真实在线路径。
- 当前切片只完成 computed 组基础交互和事件映射；完整递归选择、组级批量测速、搜索过滤、结果缓存失效、订阅自动更新和备份恢复仍未完成。

### 2026-10-27：代理组搜索与延迟结果缓存失效切片

- 代理组选择对话框新增大小写不敏感的节点搜索；搜索只过滤当前组成员，不改变组顺序，空结果显示明确空态。
- 延迟结果增加 45 秒 TTL；读取和代理组刷新时清理过期或已失效的结果，按组名、节点名和测试 URL 继续隔离，避免旧测速结果覆盖新状态。
- 新增 2 项 `ProxyGroupsViewModel` JVM 测试和 1 项 Compose instrumentation 测试，覆盖搜索过滤、空白搜索、TTL 边界和 computed 组搜索展示。
- `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon`：通过；185 项 JVM 单元测试失败 0、错误 0、跳过 0；43 项 instrumentation 测试失败 0、错误 0、跳过 0，设备为 Redmi Note 8 Pro（Android 16/API 36）。
- `./gradlew :app:lintDebug --no-daemon`：通过；Lint 错误 0、Warning 34、Hint 2，均为既有非阻断问题。
- Debug APK SHA-256 为 `e96bbeb58bcd272646e1c82dc2fe9bdd7d4c35192a6335736c12fd46abbe4f4d`，APK 内原生库仅位于 `lib/arm64-v8a/`。
- 本轮未访问当前 `AGENTS.md` 列出的两条真实订阅路径、VPN 或核心启动；单组批量测速、完整递归选择、跨进程缓存持久化、订阅自动更新和备份恢复仍未完成。

### 2026-10-28：代理组批量测速代码切片

- `CoreRuntimeManager.testDelayBatch()` 在核心生命周期锁内执行受控并发测速，默认并发上限为 32；单个节点的成功、异常失败、超时结果和 Mihomo 空结果分别保留，不因单节点失败中断整批请求。
- `ProxyGroupsViewModel` 新增代理组批量测速状态、嵌套代理组递归展开、循环依赖保护、节点与测试 URL 去重，以及批量期间的切换/刷新互斥；代理组卡片新增“批量测速”入口。
- 批量测速的协程取消异常会继续向上传播，停止核心或 ViewModel 生命周期结束时不会把取消误记为节点失败；新增 JVM 回归测试覆盖逐项失败和取消传播。
- `./gradlew test --no-daemon`：通过；App 77 项、Service 64 项、Core 16 项、Common 30 项，共 187 项 JVM 测试失败 0、错误 0、跳过 0。
- `./gradlew :app:lintDebug --no-daemon`：通过，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；43 项 instrumentation 测试失败 0、错误 0、跳过 0，设备为 Redmi Note 8 Pro（Android 16/API 36）。
- Debug APK SHA-256 为 `b45853f58175a89a089e886bca3a51dbc301ee51d8b7c5bd07bdf193c8354d37`，APK 原生库仅包含 `lib/arm64-v8a/`。
- 按当前真机约束导入工作区 `1.yaml` 并选中 Profile；首次启动已通过 VPN 授权，但等待约 1 分钟后 UI 进入“核心启动失败”，运行诊断记录为 `CoreBridgeException`。本轮没有观察到 `VpnService`、`ProxyService`、`tun0` 或 `198.18.0.0/30` 路由，因此没有形成代理页批量测速结果或停止清理证据；两条授权真实在线路径均未访问。
- 当前状态：批量测速代码和自动化验证为 `DONE-UNIT`，真机验收为 `BLOCKED`。下一步先定位 `1.yaml` 在设备上的 Mihomo 配置应用失败原始错误，再复验核心启动、代理组展示、批量测速和停止清理；完整递归选择、跨进程缓存持久化、订阅自动更新和备份恢复继续保持未完成。

### 2026-10-28：Geo 数据离线资源修复与真机批量测速收口

- 根因已确认：`1.yaml` 启用 `geodata-mode`/`geo-auto-update`，首次启动时 Mihomo 尝试下载 GeoIP 数据；在 Android VPN 尚未建立网络前下载超时，导致配置应用失败。运行诊断现在保留脱敏后的 Mihomo 错误码和原文。
- APK 已内置 `ASN.mmdb`、`GEOIP.dat`、`GEOIP.metadb` 和 `GEOSITE.dat`，核心初始化前原子复制到应用私有 `files/mihomo` 目录；后续启动复用完整资源。
- `./gradlew :core:testDebugUnitTest --no-daemon`、`./gradlew :service:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --no-daemon`：通过；Debug APK 已确认仅包含 `arm64-v8a` 原生库。
- 收口验证 `./gradlew test :app:lintDebug :app:assembleDebug --no-daemon`：通过；`ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过，43 项 instrumentation 测试失败 0、错误 0、跳过 0；最终 Debug APK SHA-256 为 `ac8c1c856ac7b0cfcfd5a750ab48152dd3112eeb2f7ed88edfc932a9a01e01dc`。
- 真机 `wsvwypiz7xwslvl7`（Redmi Note 8 Pro，Android 16/API 36）使用工作区 `1.yaml` 启动后，代理页显示 `GLOBAL`、`节点选择`、`URLTest` 等代理组；在 `GLOBAL` 执行“批量测速”后，选择弹窗显示多个成功毫秒值（如 254–359 ms），同时保留未完成节点的“测速”状态。
- 随后点击“停止核心”，UI 显示“核心已停止”；`VpnService`、`ProxyService`、`tun0` 以及 `198.18.0.0/30` 路由均已清理。
- 本轮没有访问两条授权真实在线路径；真机只使用工作区根目录 `1.yaml`。
- 当前批量测速切片状态：`DONE-VERIFIED`。剩余工作转入完整递归选择、订阅自动更新、备份恢复及 N1/N5/N6 平台与发布收尾。

### 2026-10-28：代理组递归选择语义切片

- `ProxyGroupInfo`/Bridge 现在保留 Mihomo URLTest/Fallback 返回的 `fixed` 字段；computed 组 UI 选择状态按“用户固定值 → fixed → 核心自动 now”解析，避免把自动测速结果误当成用户固定选择。
- 新增带 `visited` 环路保护的递归选择解析器，支持三层嵌套代理组、叶子节点解析、自循环和多组环路终止；当前递归只负责选择语义，批量测速仍使用既有独立解析器。
- computed 组点击语义与 Flutter 对齐：没有用户固定值时点击当前自动节点会固定该节点；再次点击已固定节点发送空目标恢复自动选择；Selector 仍按当前选择拒绝无意义的重复切换。
- 代理页卡片和选择对话框改为显示有效选择，补充 `fixed` 解析、三层递归、环路保护、computed pin/auto toggle 和失效选择回退的 JVM 测试。
- `./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --no-daemon --rerun-tasks`：通过；Core/App JVM 测试失败 0、错误 0。
- 当前切片状态：`DONE-UNIT`。递归选择语义已完成；嵌套代理组 UI 导航/展开、Tab/List 布局和跨进程延迟缓存仍未完成，下一步继续补订阅自动更新。

### 2026-10-28：代理组选择持久化闭环切片

- Profile 新增稳定的 `selectedMapJson` 编解码边界：只保留“代理组名 → 节点名”非空映射，损坏 JSON 回退空映射，写入时按键排序保证结果稳定。
- `ProfileStore`/`ProfileRepository` 新增选择映射更新接口；`activeProfile()` 将解析后的 `selectedMap` 传入服务层，Room 重建后仍能恢复当前 Profile 的代理组选择。
- `ActiveProfile`、`CoreRuntimeManager` 和 `CoreRuntimeBridge` 贯通选择映射；核心启动在校验配置后调用 `setupConfig(selectedMap)`，Mihomo 可在启动阶段恢复用户固定选择。
- `ProxyGroupsViewModel` 读取当前 Profile 的持久化映射；代理切换成功后写回 Room，核心切换失败时不修改旧映射；通过 `ProxyGroupsRuntime` 契约隔离 ViewModel，便于验证成功/失败状态收敛。
- 新增选择映射 JSON 单元测试、Runtime 启动参数测试、ViewModel 成功写回/失败保留测试，以及真实 Room/Repository `activeProfile()` 恢复测试。
- `./gradlew test --no-daemon`：通过；App 84 项、Service 68 项、Core 18 项、Common 30 项，共 200 项 JVM 测试失败 0、错误 0、跳过 0。
- `./gradlew :app:assembleDebug :app:lintDebug --no-daemon`：通过；Lint 错误 0，Debug APK SHA-256 为 `cc424209df0cf562d30a7746bde2596a74eb5b4352d940f85ed2ef81f3aa8dce`，原生库仅包含 `lib/arm64-v8a/`。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew connectedDebugAndroidTest --no-daemon`：通过；Redmi Note 8 Pro（Android 16/API 36）44 项 instrumentation 测试失败 0、错误 0、跳过 0，其中包含真实 Room 选择映射和 `activeProfile()` 恢复流程。
- 本轮未访问两条授权真实在线路径，未进行真实核心代理组点击和进程重建后的页面级选择回归，因此切片状态保持 `DONE-UNIT`；下一步推进订阅自动更新，再补代理选择真机 E2E、嵌套代理组 UI 导航和备份恢复。

### 2026-10-28：订阅自动更新闭环切片

- 接入 WorkManager 2.10.1，应用启动时注册唯一周期任务 `kclash-subscription-auto-update`；任务每 20 分钟检查一次，使用网络和非低电量约束，并采用初始 30 分钟的指数退避。
- `RemoteProfileUpdater` 按 Profile 的 `autoUpdate` 和 `autoUpdateIntervalMillis` 筛选到期远程 Profile，默认最小间隔为 15 分钟；单次最多重试 3 次，取消异常继续向上传播，不把任务取消误记为失败。
- 订阅下载完成后先通过 `YamlProfileConfigValidator` 和安全 YAML 编解码校验，校验成功后才原子替换 Profile 文件；失败时保留旧 YAML，并把最多 240 字符的失败摘要写入 `lastUpdateError`。
- Room schema 升级到 v5，新增 `profiles.lastUpdateError` 和 `MIGRATION_4_5`；自动更新成功会清除失败摘要。
- 配置页远程 Profile 新增“自动更新”开关和最近一次自动更新失败原因，`ProfilesViewModel.setAutoUpdate()` 已接入，Worker 通过 Hilt EntryPoint 获取更新器。
- 新增自动更新调度、到期筛选、YAML 校验、旧文件保留、失败摘要和取消传播测试；`./gradlew test --no-daemon` 通过，全工程 204 项 JVM 单元测试失败 0、错误 0、跳过 0。
- `./gradlew :app:assembleDebug :app:lintDebug --no-daemon` 通过，Lint 错误 0；Debug APK SHA-256 为 `89a4f6641ac608406b989bfe9337e597e484beda24260dacd0ad26312c6ca56f`，APK 内原生库仅位于 `lib/arm64-v8a/`。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew connectedDebugAndroidTest --no-daemon` 通过；Redmi Note 8 Pro（Android 16/API 36）48 项 instrumentation 失败 0、错误 0、跳过 0，包含远程 Profile 自动更新开关页面回归。
- 设备 `dumpsys jobscheduler` 已确认唯一 WorkManager 任务存在，包含网络、`BATTERY_NOT_LOW` 和 30 分钟指数退避约束。
- 真机真实在线回归已完成：第二条授权路径完成 Deep Link 导入、HTTP→HTTPS 重定向和手动同步；第一条授权路径完成 Deep Link 导入、订阅用量展示和手动同步；两个远程 Profile 的自动更新开关均完成 `on → off → on` 回归。验收期间未记录 URL、Token 或订阅正文，结束后两个测试 Profile 均已删除，设备恢复空配置状态。
- 由于新导入 Profile 的更新时间尚未达到 24 小时自动更新间隔，本轮验证的是真实下载链路、手动同步、开关持久化和 WorkManager 调度，未等待周期任务自动下载。当前切片状态：`DONE-VERIFIED`；下一步进入本地备份/恢复。

### 2026-10-28：本地备份/恢复首片

- 新增本地 SAF ZIP 导出/导入；归档包含 `manifest.json`、Profile/脚本 Room 元数据、当前 Profile 选择，以及 `profiles/<id>.yaml`、`scripts/<id>.js` 正文文件。
- 恢复支持“仅恢复配置文件 / 恢复全部数据”和“兼容 / 覆盖”两种维度；仅恢复配置文件时清除脚本绑定，避免归档不含脚本却留下悬挂引用；覆盖恢复会清理归档未包含的旧文件。
- Repository 在写入前执行版本、未知条目、重复条目、路径穿越、条目大小、行记录和文件完整性校验，并通过 staging、快照和回滚保护 Room 与应用私有文件的一致性。
- 不备份 `runtime_should_resume`、运行诊断历史、Mihomo 临时目录、缓存和 Geo 数据；本切片只支持 Android 本地 SAF，不接 WebDAV 或云端同步。
- 新增 `BackupRepositoryInstrumentedTest` 3 项，覆盖全部数据往返、兼容恢复、配置文件恢复清除脚本绑定、非法路径和未知版本拒绝；Profiles Compose 测试覆盖备份/恢复入口与选项对话框。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；App 88 项、Service 68 项、Core 18 项、Common 30 项，共 204 项 JVM 单元测试失败 0、错误 0、跳过 0；Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew connectedDebugAndroidTest --no-daemon`：通过；Redmi Note 8 Pro（Android 16/API 36）52 项 instrumentation 失败 0、错误 0、跳过 0。
- 真机已创建自定义 Profile，通过 DocumentsUI 将 ZIP 导出到 Download；随后使用“恢复全部数据 + 兼容”和“恢复全部数据 + 覆盖”分别导入，均显示“备份恢复完成”，Profile 仍可见；最后删除测试 Profile，配置页恢复“还没有配置”。
- Debug APK SHA-256 为 `06fb04e98b7d21fecb4bc2aa2511138b16b11a12944ee07fb93ee9953d17eac4`，APK 内原生库仍仅位于 `lib/arm64-v8a/`；当前切片状态：`DONE-VERIFIED`。

### 2026-10-28：代理选择 ViewModel 页面状态流真机切片

- `ProxyGroupsCard` 为代理组卡片、搜索框、选择节点、单节点测速、批量测速和关闭入口补充稳定 Compose `testTag`；节点 tag 只挂在可点击的 `RadioButton`，避免父 Row 与子控件产生重复匹配。
- 新增 `ProxyGroupsViewModelPanelInstrumentedTest`，使用真实 `ProxyGroupsViewModel`、fake `ProfileStore` 和 fake `ProxyGroupsRuntime`，覆盖 computed 组固定节点、点击当前节点恢复自动选择、成功后持久化选择映射，以及切换失败保留旧映射。
- 定向真机测试通过；全量 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过，54 项 instrumentation 失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过；Debug APK SHA-256 为 `e9650d902f59269a39dfd388056ab6867251f2480871444d436895b7b536197a`，原生库仅包含 `arm64-v8a`。
- 本轮未访问真实订阅路径、未启动真实核心；当前切片状态：`DONE-VERIFIED`（真实设备 Compose 状态流）。完整主 Activity + 真实 Mihomo 代理切换、嵌套代理组的平面 parity 和跨进程选择回归仍待后续切片。

### 2026-10-28：嵌套代理组交互范围校准

- 对照 Flutter `ProxyGroupView` 和 `_ProxyDesc` 行为后确认：嵌套代理组沿用平面分组列表，不进入子组、不建立返回栈；组成员为子组时只展示递归解析后的当前叶子节点。
- Kotlin 已具备三层递归、选择优先级和环路保护；后续只补平面列表中的有效叶子展示与真实 Mihomo 选择回归，不新增与 Flutter 不一致的 drill-down 导航。

### 2026-10-28：真实核心代理选择与重启保持回归

- 使用工作区根目录 `1.yaml` 通过 Android 文件选择器导入本地 Profile；未访问真实在线订阅路径。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36）启动 KClash 后，`VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由均出现，代理页读取到真实 `GLOBAL`、Selector、URLTest 等代理组。
- 在真实“节点选择”组中将当前节点从自动选择切换为 `DIRECT`；UI 显示 `Selector · 当前：DIRECT`，重新读取核心后仍保持 `DIRECT`。
- 停止核心并再次启动后，代理页继续显示 `Selector · 当前：DIRECT`，证明选择映射在核心停止/启动之间保持；本轮未进行应用进程被杀后的页面恢复断言。
- 最终停止核心，确认前台服务、`tun0` 和 `198.18.0.0/30` 路由清理；删除测试 Profile 后配置页恢复“还没有配置”，未发现 KClash `FATAL EXCEPTION`。
- 当前切片状态：`DONE-VERIFIED`；剩余工作为嵌套组平面有效叶子展示和更完整的 Activity 端到端覆盖。

### 2026-10-28：进程重建后的代理选择 UI 恢复

- 使用工作区根目录 `1.yaml` 导入 Profile，启动真实 Mihomo 后将“节点选择”组固定为 `DIRECT`。
- 记录旧应用 PID 后执行 `adb -s wsvwypiz7xwslvl7 shell cmd activity crash com.lonnnnnng.flclash`；设备出现新 PID，`VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30` 路由持续存在。
- 重新打开 KClash 的代理页后，`GLOBAL` 和“节点选择”均显示 `Selector · 当前：DIRECT`；未发现 KClash `FATAL EXCEPTION`。
- 最终停止核心、确认服务和路由清理，并删除测试 Profile，配置页恢复“还没有配置”；当前切片状态：`DONE-VERIFIED`。

### 2026-10-28：嵌套代理组平面叶子展示回归

- `ProxyGroupsCard` 的代理组摘要现在复用 `resolveSelectedProxy()`，沿三层嵌套组递归解析当前选择并展示最终叶子节点；解析失败时回退到当前组可用名称。
- `ProxyGroupsViewModelPanelInstrumentedTest` 新增三层 `Outer → Middle → Leaf → node-c` Compose 断言，确认平面列表显示 `URLTest · 当前：node-c`；测试继续使用真实 `ProxyGroupsViewModel`、fake `ProfileStore` 和 fake `ProxyGroupsRuntime`。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；App 88 项、Service 68 项、Core 18 项、Common 30 项，共 204 项 JVM 单元测试失败 0、错误 0、跳过 0，Lint 错误 0。
- 定向 `ProxyGroupsViewModelPanelInstrumentedTest` 通过；全量 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过，55 项 instrumentation 失败 0、错误 0、跳过 0。
- Debug APK SHA-256 为 `5261eb708a63d992a4fac1680221705a2ae58d49aba637461bfb2eaf23c814e9`，原生库仅包含 `lib/arm64-v8a/`；本轮未访问真实在线路径，也未启动真实 Mihomo 核心。
- 当前切片状态：`DONE-VERIFIED`；嵌套组平面叶子展示已完成，后续转入完整主 Activity 页面级 E2E、WebDAV/云端备份评估和发布收尾。

### 2026-10-28：主 Activity 配置生命周期 E2E 首片

- `MainActivityInstrumentedTest` 新增真实 Activity 流程：从配置页创建自定义 Profile，编辑 YAML 写入 `mixed-port: 7890`，打开删除确认并删除 Profile，最后返回首页。
- 测试使用真实 Hilt、Room、`ProfilesViewModel` 和应用私有文件目录，不访问在线订阅、VPN 或 Mihomo 核心；测试结束后设备恢复“还没有配置”空态。
- 定向 `MainActivityInstrumentedTest` 通过；全量 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过，56 项 instrumentation 失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；App 88 项、Service 68 项、Core 18 项、Common 30 项，共 204 项 JVM 单元测试失败 0、错误 0、跳过 0，Lint 错误 0。
- Debug APK SHA-256 仍为 `5261eb708a63d992a4fac1680221705a2ae58d49aba637461bfb2eaf23c814e9`，原生库仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`；这是主 Activity 配置入口的首个真实状态流闭环，后续继续补代理选择、Requests/DNS/Logs 详情和完整页面级导航交互。

### 2026-10-28：观测详情页签交互回归

- `ObservationDetailsInstrumentedTest` 通过真实 `ObservationPage` 宿主打开 Requests、DNS、Logs 三个详情对话框，分别验证网络字段搜索、DNS 字段搜索、日志级别筛选和关闭回调。
- `ObservationPage`、`RequestsDialog`、`DnsDialog`、`LogsDialog` 收敛为模块内可测试入口，生产 UI 行为和状态来源不变；测试使用固定内存状态，不访问在线路径、VPN 或 Mihomo 核心。
- 定向观测详情测试通过；全量 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过，57 项 instrumentation 失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；App 88 项、Service 68 项、Core 18 项、Common 30 项，共 204 项 JVM 单元测试失败 0、错误 0、跳过 0，Lint 错误 0。
- Debug APK SHA-256 为 `37087d7cd5016bf952439d8ea07f55c7fd6e4e653767c70a9eac2349f7e5bb71`，原生库仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`；Requests/DNS/Logs 详情筛选已具备页面宿主回归，后续继续补代理选择 Activity E2E 和完整页面导航状态保持。

### 2026-10-28：代理选择 Activity E2E 首片

- 新增 Debug-only `ProxyGroupsTestActivity` 宿主，将真实 `ProxyGroupsViewModel`、`ProxyGroupsCard`、`ProfileStore` 和 `ProxyGroupsRuntime` 状态流放入 Activity 生命周期；Release 构建不包含该测试入口。
- 新增 `ProxyGroupsActivityInstrumentedTest`，覆盖代理组页面加载、搜索节点、选择 `node-b` 后的核心切换目标与 Profile 选择映射持久化，以及再次点击当前节点恢复 computed 组自动选择。
- 定向 `ProxyGroupsActivityInstrumentedTest` 真机测试通过 1 项；全量 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过，58 项 instrumentation 失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过；工程共 204 项 JVM 单元测试失败 0、错误 0、跳过 0，Lint 错误 0。
- 设备为 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；本轮使用 Debug fake 核心状态，不访问两条真实订阅路径、不启动真实 Mihomo 或 VPN。
- Debug APK SHA-256 为 `cef37ba37eede00c9c0581a0da9d98ea224900dc3c01d51a1cf93c270dd60871`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`；代理选择已具备 Activity 生命周期状态流证据，下一步补完整页面导航状态保持，再处理 WebDAV/云端备份评估和发布收尾。

### 2026-10-28：主 Activity 页面导航状态保持

- 将观测页当前页签从 `ObservationPage` 内部状态提升到 `KClashApp` 的 `rememberSaveable`，确保从观测页切换到其他顶层页面后返回仍保持 Requests、DNS 或 Logs 页签。
- 新增 `MainActivityInstrumentedTest.mainActivityRetainsObservationTabAcrossPrimaryNavigationAndRecreation`，真实 `MainActivity` 覆盖切换到 DNS、离开到代理页、返回观测以及 Activity 重建后的页签恢复。
- 定向 `MainActivityInstrumentedTest` 通过 4 项；全量 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon` 通过，59 项 instrumentation 失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过；工程共 204 项 JVM 单元测试失败 0、错误 0、跳过 0，Lint 错误 0。
- 设备为 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；本轮未访问两条真实订阅路径、未启动真实 Mihomo 或 VPN。
- Debug APK SHA-256 为 `f4a7d7b22471313b10464c99d2665226967f7c4741544c041505d7df8345e747`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`；主 Activity 的顶层导航与观测页签状态保持已有真实 Activity 证据，下一步进入 WebDAV/云端备份评估或发布收尾。

### 2026-10-28：Release R8 与签名边界收紧

- `release` 构建在没有独立签名身份时不再回退到 debug 签名，改为生成未签名 APK；这样预发布构建可以验证 R8、资源收缩、Manifest、版本和 ABI，同时不会把 debug 信任链误当正式发布身份。
- R8 首次暴露 SnakeYAML 对 Android 不提供的 `java.beans.*` 类型引用；新增仅针对该不可达 JavaBean introspector 路径的 `-dontwarn java.beans.**`，KClash 运行时仍使用 `SafeConstructor`。
- `./gradlew :app:assembleRelease :app:lintRelease --no-daemon` 通过；`apkanalyzer` 核对 applicationId 为 `com.lonnnnnng.flclash`、versionCode `1`、versionName `0.1.0`。
- Release APK 为 `android/app/build/outputs/apk/release/app-release-unsigned.apk`，SHA-256 为 `bf48e0d631f56ff9a8392ff7b00db2636bd14c030ee267b24eb1908911efc57c`，APK 内原生库仅包含 `lib/arm64-v8a/`；`apksigner verify` 按预期拒绝未签名包。
- 本轮未创建或保存 keystore，未安装未签名 Release APK 到真机；正式发布仍需独立签名、CI Kotlin Release 产物和签名后真机验收。
- 当前切片状态：`DONE-UNIT`；R8/Release 预发布构建已验证，签名和远端发布流程仍保持未完成。

### 2026-10-28：Kotlin 主线 Android CI 验证入口

- 新增 `.github/workflows/kotlin-android.yml`，仅对 `kotlin` 分支的 push、Pull Request 和手动触发执行，不改变 Flutter 参考分支的既有发布 workflow。
- CI 使用仓库根目录 Gradle Wrapper、Java 17、Go 1.27.1、Android API 37、Build Tools 36.0.0 和 NDK 28.2.13676358，执行 JVM 测试、Debug/Release 构建、R8、Debug/Release Lint，并固定 `-Ptarget-platform=android-arm64`。
- CI 对未签名 Release APK 检查包存在、原生库 ABI 只有 `arm64-v8a`，并上传 APK 与 SHA-256 文件；没有签名 Secret 时不会伪造正式发布资产。
- 本地 `actionlint .github/workflows/kotlin-android.yml` 通过；本轮未触发远端 GitHub Actions，远端 runner 的 SDK/NDK 下载和构建结果待 push 后观察。
- 当前切片状态：`DONE-UNIT`；Kotlin 主线已有独立可审计的 Android CI 入口，正式签名、Release 页面和远端发布资产仍待发布阶段。

### N4：Profiles 和代理组 parity

目标：补齐 Flutter Android 用户已经拥有的配置和代理组工作流。

建议范围：

1. 二维码导入回归和体验完善。
2. 高级配置分区编辑。
3. URLTest/Fallback 完整语义。
4. 批量测速、搜索、缓存和结果失效。
5. 订阅自动更新和本地 SAF 备份恢复（已完成；WebDAV 手动备份的协议、凭据存储、配置 UI、ZIP 仓库接入、认证/离线反馈和本地回环真机回归已完成，外部授权服务回归仅在具备授权条件时执行，自动云端同步明确延期且不进入首发范围）。

当前已完成的首版包括Profile导入/分享/预览、明文与真实age密文的新增/替换SAF、CUSTOM规则/策略组/Provider/更多分区、标准/脚本扩展、全局本地代理/链式代理及规则/图库/过滤/Geo资源。代理组有效核心now、手动优选/清除、非法成员、语义滚动锚定及延迟Room跨进程恢复已有真机证据，停止态节点菜单新增到本地目录、重复提示和重建回读已验。常规/网络/全局DNS/附加规则、按需SSID前台匹配与VPN暂停恢复、v6全局设置备份、稳定RUNNING订阅设置/同步入口/批次结果及远程正文父草稿保存应用均有定向验收；各批次的实网/系统入口边界保持独立。

累计已验：FILE/CUSTOM结构化运行态保存/应用、Profile删除回退/排序整页、父/正文加密草稿新进程恢复、唯一授权源两目标批量下载后核心应用，以及FILE/remote/脚本/Provider真实系统选择器等待中进程回收后返回与取消。高级配置运行态还覆盖脚本保存/同步/删除、全局DNS/规则、Provider覆盖/侧载及原1.yaml两个HTTP资源实更新；首页/代理组选择、模式/测速隔离与失败重试也已有对应切片。这些不再作为下一组待实现任务，具体实网与进程边界以各组验收JSON为准。

当前收尾顺序：主页四入口共用面板及Providers分钟级加载/真实UI更新复查、配置页/代理组菜单及正文面板交互、URLTest优选冷进程恢复均有分批证据；本次补齐三个剩余菜单入口的真机交互。整页连续动效和原1.yaml全部90个HTTP规则资源分批实网已补齐；Fallback固定选择冷进程恢复已补，当前Mihomo合法解析契约下自然无效`now`保留为不可自然构造边界；接下来按本节当前执行清单收束未覆盖交互、恢复边界及完整视觉。第4/5项已补资源SAF/外部脚本E2E及HTTP/File/Inline代表类型故障恢复和实际命中，第6项已补质量持久缓存和全部来源尝试；在线age缺授权密文/密钥、受限质量源、原保护异常及未覆盖Provider协议/故障注入边界仍分别留账。备份/系统恢复与Requests/DNS/Logs次要视觉暂缓，云端自动同步延期，语言/主题暂缓，免责声明/关于永久移除；完整主流程未收敛前不进入正式发布。

### N5：平台入口和系统集成

目标：让 KClash 在 Android 系统层面可持续运行。

收尾范围：

1. Tasker/MacroDroid 实际应用回归。
2. 完整 Flutter Access 页面 parity（选择器、持久化、服务参数、真实核心流量回归、关闭状态遮罩/一键开启、排序和系统/无网络过滤、智能选择、剪贴板导入/导出首片、安装应用空状态、返回未保存确认、Access 卡片顶栏搜索/全选入口、无结果搜索恢复、当前结果全选/清除和放弃修改真实宿主 E2E 首片已完成；完整视觉交互仍待补齐）。
3. Always-on VPN 系统设置引导首片已完成；由于普通应用无法通过当前公开 Android SDK 稳定回读 OEM 的 Always-on/lockdown 勾选状态，完整状态识别、兼容性矩阵和系统行为回归仍待补齐。
4. Android 组件导出、Intent 和权限的完整自动化审计（已完成；仍需保留 Tasker/MacroDroid 真实第三方应用回归作为独立收尾项）。

### N6：UI/UX 收敛和发布

目标：从“功能骨架”达到可长期使用和发布的 KClash 产品。

建议范围：

1. 完成 Design System 和导航。
2. Flutter 参考截图对齐。
3. UI、Instrumented 和端到端测试。
4. Release 签名、R8、CI、版本和 Release 资产。
5. 以独立 KClash 身份发布第一个 arm64 Android 版本。

## 9. 每个功能切片的统一验收门槛

### 代码层

- Bridge 不向 UI 暴露原始 Mihomo method 字符串。
- ViewModel 只暴露不可变 StateFlow。
- 启停、配置应用、VPN 和代理切换经过 `CoreRuntimeManager`。
- 关键业务逻辑附近有中文注释并标记作者 `long`。
- 不引入 Flutter 数据兼容或迁移逻辑。

### 自动化层

- 相关模块单元测试通过。
- 错误、空数据、取消、超时和重复调用有测试。
- `git diff --check` 通过。
- 受影响模块和 Debug APK 构建通过。
- 需要时增加 Compose UI 或 Instrumented 测试。

### 真机层

- 执行前重新检查 `adb devices -l`、型号、API 和 serial。
- 安装、启动、停止和日志命令显式使用 `adb -s <serial>`。
- 记录成功和失败的关键输出。
- 不能用历史成功记录覆盖本轮失败。
- 停止测试后确认服务、VPN 和核心状态已清理。

### 发布层

- 包名、显示名称、版本号、ABI 和签名一致。
- Release APK 只包含 `arm64-v8a`。
- 私钥、Token、订阅 URL 和真实凭据不进入仓库或日志。
- 发布前重新计算 APK SHA-256，并复核远端资产。

## 10. 上游同步路线

Kotlin 主线不直接合并 Flutter 提交。每次同步按照以下顺序：

1. `git fetch --prune upstream`，记录上游目标 SHA。
2. 阅读上游变更涉及的 Flutter 页面、Provider、Core method 和 Android 行为。
3. 更新本路线图的功能矩阵，标记手机适用、桌面专属、暂缓或需要重新设计。
4. 为 Android 行为建立 Kotlin 领域模型、Bridge、Repository、ViewModel 和 Compose 入口。
5. 先补自动化测试，再用 Redmi 真机完成真实网络/权限/生命周期验收。
6. 更新 `CONTEXT.md`、`.agents/project.md`、`.agents/testing.md` 或新增 ADR。
7. 使用中文提交说明；推送 `origin/kotlin` 前检查分支、差异、产物和凭据边界。

## 11. 当前主要风险

| 风险 | 影响 | 处理顺序 |
| --- | --- | --- |
| Wi‑Fi↔移动网络切换尚未完成 | Wi‑Fi 断开恢复、真实请求归因、后台恢复、进程重建和系统撤销清理已验证，但蜂窝切换仍可能留下悬挂隧道 | N1 真机切片 |
| 平台入口仍有系统级收尾项 | Quick Settings、通知、桌面快捷方式和固定外部 Action 已统一进入 Runtime Manager；Tasker/MacroDroid 真实第三方回归、完整 Flutter Access 页面 parity 的视觉收敛/E2E 和 Always-on VPN 状态回读/兼容性仍未完成 | N5 |
| 主流程已有多批首版，完整页面parity仍未收敛 | 草稿/运行态/系统入口/完整视觉仍有独立缺口，不能用组件回归推广为全产品完成 | 主页/配置/代理组与常用设置优先 |
| 观测治理仍有后续缺口 | 搜索过滤、详情、自动跟随、脱敏导出、有界背压、事件流健康统计、运行诊断历史详情/导出、稳定故障归因、异步事件停止边界、页面退出轮询收敛和真实高负载压力已验证，但完整页面级端到端断言仍不足 | N2/N3 |
| 高级配置仍有完整覆盖边界 | FILE/CUSTOM、脚本/全局DNS/规则/Provider覆盖/更多设置/侧载与两HTTP资源运行态已验；四类DocumentsUI进程回收后返回/取消已验；其他资源SAF、外部脚本源完整E2E、全部字段实网与跨写者快照待补。备份/系统恢复暂缓，外部WebDAV及自动云同步仍按原边界 | M6/N4 |
| UI/Instrumented 覆盖仍有限 | 当前已覆盖主导航、观测页签、事件流摘要和 Room，但完整页面交互、配置编辑、代理组和端到端回归仍不足 | M9/N6 前继续扩展 |
| 没有 release keystore | 已可做未签名 R8/Release 预发布验证，但不能安装或发布正式包 | 发布前创建，不提前提交秘密 |
| Kotlin正式签名发布尚未接入 | Kotlin有独立分支验证与kclash-v* Tag/手动未签名arm64构建；本地Release/R8通过，不能替代远端CI或正式签名资产回验 | 发布前接入签名、Release页面和资产上传 |
| 上游持续变化 | Flutter 行为会继续漂移 | 每次同步更新矩阵和验收记录 |

### 2026-10-28：真机验收路径约束更新

- 真实在线验收范围收敛为工作区根目录 `AGENTS.md` 中维护的两条用户授权路径；旧的第 2、3 条路径已移除，不再用于真机回归。
- 本地配置验收固定使用工作区根目录 `1.yaml`，禁止随机生成或替换测试 YAML。
- 路线图不重复记录真实订阅 URL 或 Token；执行验收前以根目录 `AGENTS.md` 的当前内容为唯一准入清单。

### 2026-10-28：WebDAV 手动备份协议层首片

- 新增 `WebDavEndpoint`、`WebDavClient` 和 `HttpWebDavClient`，覆盖 OPTIONS 探测、MKCOL 目录创建/已存在容错、PUT 上传和 GET 下载。
- 使用 OkHttp 发送真实 WebDAV 方法；客户端关闭自动重定向并手动限制最多 5 次同源重定向，跨 scheme、host 或 port 的重定向直接失败，避免把 Basic 凭据转发到第三方。
- 协议层拒绝 URL 内嵌账号、查询参数和片段；文件名只允许字母、数字、点、下划线和短横线；上传和下载均限制 64 MiB；Basic Auth 通过 OkHttp `Credentials.basic` 生成，兼容 minSdk 24。
- 新增 4 项 `WebDavClientTest`，使用本地 `HttpServer` 验证 Basic Auth 上传/下载、同源重定向、跨站重定向拦截和非法文件名拒绝；未访问真实 WebDAV 服务，也未访问两条真实订阅路径。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon` 通过；全工程 208 项 JVM 单元测试失败 0、错误 0、跳过 0，Lint 错误 0。
- Debug APK SHA-256 为 `71fcfc556f77c82eb12a7287ef13998828fdb8ddc833c3eef9a53fc032bbef85`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-UNIT`。后续必须接入安全凭据存储、WebDAV 配置 UI、现有 ZIP 备份/恢复仓库、认证失败/冲突/离线状态反馈，并用本地或授权 WebDAV 服务完成真机回归；暂不实现自动云端同步。

### 2026-10-28：WebDAV 凭据、配置 UI 与备份仓库接入首片

- 新增 `AndroidWebDavCredentialStore`：使用 Android Keystore 生成 AES-GCM 密钥，凭据密文独立写入偏好文件；`toString()`、日志和备份归档均不包含密码明文。
- 新增 `WebDavBackupRepository`，复用现有 `BackupRepository` 生成和恢复 ZIP；上传通过同名 PUT 明确覆盖远程文件，下载先完整获取归档再按现有“兼容/覆盖”策略恢复，网络或归档失败不会自动改写本地数据。
- 配置页新增 WebDAV 手动入口、地址/账号/密码/文件名表单、连接测试、上传覆盖确认、下载恢复策略和清除凭据操作；核心运行时禁止上传和恢复，避免修改正在使用的配置数据。
- 新增 `WebDavCredentialStoreInstrumentedTest`、`WebDavBackupRepositoryInstrumentedTest` 和 `WebDavUiInstrumentedTest`；真机覆盖 Keystore 密文落盘、Room/文件 ZIP 往返、上传覆盖确认和下载恢复策略。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；`ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过，Redmi Note 8 Pro（Android 16/API 36）63 项 instrumentation 失败 0、错误 0、跳过 0。
- Debug APK SHA-256：`eec8512c7e5a81f2373bb7d76401256738d416285f53ca454aeff5c708d0e3a9`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 本轮未使用真实 WebDAV 账号或外部 WebDAV 服务；已有 JVM `WebDavClientTest` 继续覆盖本地 HTTP 服务的 Basic Auth、同源重定向和跨站拦截。当前切片状态：`DONE-UNIT`；下一步补授权/本地 WebDAV 服务的真实设备回归、认证失败/离线反馈，再评估是否需要云端同步。

### 2026-10-28：WebDAV 认证、离线反馈与真机回环回归

- `HttpWebDavClient` 新增 `WebDavFailureKind`/`WebDavException`，将 401、403、404、服务端错误、连接拒绝/未知主机和 socket 超时转换为可执行的中文提示；提示不包含账号、密码或底层地址细节，原始异常只保留在 cause 中。
- `MainActivity` 的 WebDAV 探测、上传和下载恢复均在网络操作及本地恢复成功后才写入 Keystore；认证失败、远端归档损坏或离线时保留原有可用凭据。
- `WebDavClientTest` 扩展为 6 项，覆盖 401/403、连接拒绝和超时映射；新增 Debug-only `localhost` 明文网络安全配置，Release 仍使用生产网络安全边界。
- 新增 `WebDavNetworkInstrumentedTest`，在 Redmi 真机测试进程内启动临时 WebDAV 服务，真实完成 `OPTIONS → MKCOL → PUT → GET` 往返、错误密码认证失败和关闭服务后的离线反馈；未访问外部 WebDAV 或真实订阅路径。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon`：通过；全工程 210 项 JVM 测试失败 0、错误 0、跳过 0，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon`：通过；Redmi Note 8 Pro（Android 16/API 36）64 项 instrumentation 失败 0、错误 0、跳过 0；定向 WebDAV 回环测试 1 项通过。
- Debug APK SHA-256：`9fb9fd03ab640f0b06b2a65a3aa0a22d25af9add7c90759e401bc826bb097bc0`；Release 未签名 APK SHA-256：`56b8004fdd2ddc9723ba149d95a76a03c30c97598757c38ce238a9322e9bb836`；APK 原生库均仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`。本地 WebDAV 服务、认证失败和离线反馈已具备真机证据；外部授权 WebDAV 服务回归和云端同步仍待后续评估，暂不实现自动云端同步。

### 2026-10-28：Kotlin arm64 Tag/手动预发布构建入口

- `.github/workflows/kotlin-android.yml` 新增 `kclash-v*` Tag 触发和手动 `version_name`/`version_code` 输入；旧 Flutter `v*` Tag 流程不被该入口接管，避免把 Kotlin 未签名 APK 混入 Flutter 发布资产。
- Tag 使用 `kclash-vMAJOR.MINOR.PATCH`；工作流把 Tag 版本名注入 Gradle，并按 `major * 1000000 + minor * 1000 + patch` 生成默认 `versionCode`，手动构建可显式覆盖两个版本字段。
- Release 构建继续保持无签名身份时只产出未签名 APK；工作流核对包名 `com.lonnnnnng.flclash`、版本字段和 `lib/arm64-v8a/` 唯一 ABI，再上传带版本名的 APK/SHA-256 artifact。
- `actionlint .github/workflows/kotlin-android.yml` 通过；本地 `./gradlew test :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease --no-daemon -Ptarget-platform=android-arm64 -PkclashVersionName=0.1.0 -PkclashVersionCode=1000` 通过。
- 本地未签名 Release APK 为 `android/app/build/outputs/apk/release/app-release-unsigned.apk`，`apkanalyzer` 核对版本名 `0.1.0`、版本号 `1000`，SHA-256 为 `a186c2bff429696a562c97ebe17f47867630328a4e7fd2fbe873954d1b389c61`；本轮未创建签名身份、未安装未签名 Release、未访问外部 WebDAV 或真实订阅路径，远端 GitHub Actions 尚未触发观察。
- 当前切片状态：`DONE-UNIT`。下一步是正式发布收尾：创建独立 release keystore、接入加密 CI Secret、签名后真机验收，并复核 GitHub Release 资产；自动云端同步保持 `DEFERRED`。

### 2026-10-28：Profile 名称重命名闭环

- `ProfileRepository.updateProfileLabel` 对名称执行裁剪、非空和 64 字符上限校验，只更新 Room 元数据，不改写 YAML；`ProfilesViewModel.rename` 在核心运行期间拒绝改名，并复用统一错误提示。
- 配置卡片新增“重命名”入口和 Compose 对话框；保存后通过 Repository 的 `StateFlow` 刷新卡片标题，运行中的 Profile 禁用入口。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；当前 JVM 单元测试 212 项，失败 0、错误 0，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.ProfilesPanelInstrumentedTest`：通过，9 项 instrumentation 失败 0；`ProfilesRepositoryPanelInstrumentedTest`：通过，10 项失败 0，覆盖 Compose 页面回写和真实 Room 持久化。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）仅用于本地页面和 Room 验收；本轮未访问外部 WebDAV、真实订阅路径、VPN 或 Mihomo 核心。Debug APK SHA-256：`81c32daebc44d7ef888acbe60b71114e00d2cb481c8da209743b782d513281f4`，原生库仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`；Profile 名称已经形成 Repository、ViewModel、Compose 和真机 Room/页面证据闭环，下一步继续补完整页面级 E2E 或进入正式签名收尾。

### 2026-10-28：外部 Action 契约和组件导出自动化审计

- 新增 `ExternalActionContract` 纯协议校验，只接受 `START`、`STOP`、`TOGGLE` 三个固定 Action，并拒绝 URI、Extras、空 Action 和未知 Action；`ExternalActionActivity` 继续只把合法调用转交给 `CoreRuntimeManager`。
- 新增 `ComponentContractInstrumentedTest`，通过打包后的 `PackageManager` 审计 `ExternalActionActivity`、`QuickActionActivity` 和 `KClashTileService` 的 `exported` 边界、Quick Settings 绑定权限、三个外部 Action filter 以及 MainActivity shortcuts metadata。
- `EnumsTest` 新增合法/非法 Action 协议测试；本轮不访问真实订阅路径、不启动 VPN、不启动 Mihomo 核心，也不执行 Tasker/MacroDroid 实际应用回归。
- `./gradlew :common:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；Debug APK SHA-256 为 `0c1061785b952493cc7919e4cfaa389ce77361aaf284e87cd7f6b6d3ae0a7f10`，原生库仅包含 `lib/arm64-v8a/`。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ComponentContractInstrumentedTest`：通过，1 项 instrumentation 失败 0；设备为 Redmi Note 8 Pro（Android 16/API 36）。
- 真机真实在线验收仍严格只允许工作区根目录 `AGENTS.md` 中维护的两条用户授权路径；本切片未访问任何真实在线路径。当前切片状态：`DONE-VERIFIED`；Tasker/MacroDroid 实际第三方应用回归、按应用代理和 Always-on VPN 仍列为 N5 后续工作。

### 2026-10-28：按应用代理选择器和包名过滤首片

- 新增 `RoomAccessControlSettingsStore`，把按应用代理设置独立保存到 `app_preferences`，不改写 Profile YAML；`DefaultVpnOptionsProvider` 启动时读取并清洗包名列表，`VpnService` 继续使用 Android 原生允许/拒绝包名 API 建立 TUN 过滤边界。
- 首页新增按应用代理卡片：可启用/停用、切换白名单/黑名单、按应用名或包名搜索、全选/清除当前结果、勾选应用并保存；核心运行中禁止修改，避免已建立 TUN 的包过滤边界发生漂移。
- 新增 `AccessControlPanelInstrumentedTest` 2 项 Compose 测试、`AccessControlSettingsStoreInstrumentedTest` 1 项 Room 真机测试和 `DefaultVpnOptionsProviderTest` 1 项 JVM 测试；Manifest 仅声明启动器应用查询，不申请 `QUERY_ALL_PACKAGES`。
- `./gradlew :service:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；Debug APK 原生库仅包含 `lib/arm64-v8a/`。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.AccessControlPanelInstrumentedTest`：通过，2 项 instrumentation 失败 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.data.AccessControlSettingsStoreInstrumentedTest`：通过，1 项 instrumentation 失败 0。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）页面验收实际启用黑名单并选择一个已安装应用，点击保存后强制重启 KClash，首页仍显示“代理除选中应用外的其他应用 · 已选 1 个”；本轮未启动 VPN、未访问真实订阅路径，也未把真实应用流量归因到过滤结果。
- 当前切片状态：`DONE-VERIFIED`（选择器、持久化和服务参数首片）；完整 Flutter Access 页面 parity、Tasker/MacroDroid 和 Always-on VPN 仍保持 N5 后续工作。

### 2026-10-28：按应用代理真实核心流量过滤回归

- 使用工作区根目录 `1.yaml` 导入并启动真实 Mihomo；真机为 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，ABI `arm64-v8a`），本轮只使用第二条用户授权路径发起受控请求。
- 停止核心后启用按应用代理黑名单，选择 `com.mmbox.xbrowser`，保存并启动 VPN；受控请求完成后，Android `dumpsys netstats --uid 10785` 只有 `wlan0` 的该 UID 记录，没有该 UID 的 `tun0` 记录。
- 停止核心后切换为白名单并重新选择 `com.mmbox.xbrowser`，再次启动 VPN 发起同一受控请求；`dumpsys netstats --uid 10785` 出现 `tun0` 记录（约 15 KB 入站、7.8 KB 出站），证明白名单应用真实进入 TUN。
- 两次运行均观察到 `VpnService`、`ProxyService`、`tun0` 和 `198.18.0.0/30`；通过外部 STOP Action 停止后服务、TUN 和路由均清理。验收结束已把按应用代理恢复为停用状态。
- Debug APK SHA-256 为 `6ef4a706396aed8272f69afea2cbf2d28c1497c2c1591c43188e3dbf7531a387`，原生库仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`（真实核心流量过滤首片）；完整 Flutter Access 页面 parity、Tasker/MacroDroid 实际第三方回归和 Always-on VPN 仍保持 N5 后续工作。

### 2026-10-28：Access 页面关闭状态与一键开启 parity 首片

- `AccessControlCard` 对齐 Flutter Access 页面的关闭状态：显示“应用访问控制已关闭”状态栏，并提供“开启”按钮；开启后显示白名单/黑名单语义说明和已选数量。
- 按应用代理关闭时保留已有选择内容，应用列表区域增加视觉遮罩，模式切换、搜索、全选/清除、应用勾选和保存均禁用；核心运行中的既有锁定规则继续优先于一键开启。
- `AccessControlPanelInstrumentedTest` 新增关闭→一键开启回归，断言关闭状态控件禁用、开启按钮可用以及开启后编辑入口恢复；定向测试 4 项通过。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64`：通过；Redmi Note 8 Pro（Android 16/API 36）全量 instrumentation 71 项，失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 测试失败 0、错误 0，Lint 错误 0；本轮未访问真实订阅路径、未启动 VPN 或 Mihomo 核心。
- 当前切片状态：`DONE-VERIFIED`（Access 关闭状态交互首片）；完整 Flutter Access 页面 parity 仍需智能选择、剪贴板导入导出、完整视觉收敛和页面级 E2E。


### 2026-10-28：Access 应用排序与来源过滤 parity 首片

- `AccessControlProps` 新增 `sort`、`isFilterSystemApp` 和 `isFilterNonInternetApp`，通过 Room JSON 持久化；旧版四字段 JSON 在读取时补齐默认排序和过滤值，不影响已有包名选择。
- `InstalledAppInfo` 增加系统应用标记、`INTERNET` 权限标记和 `lastUpdateTime`；应用清单仍限定为 Launcher 可见应用，不申请 `QUERY_ALL_PACKAGES`。
- Access 列表按 Flutter 语义先应用来源过滤，再按“已选置顶 + 默认/名称/更新时间”排序；新增排序按钮和系统/无网络过滤开关，关闭按应用代理或核心运行时继续锁定这些控件。
- 新增 `AccessControlViewModelTest` 2 项列表过滤/排序单元测试；`AccessControlPanelInstrumentedTest` 覆盖新增控件的关闭状态禁用；`AccessControlSettingsStoreInstrumentedTest` 覆盖旧 JSON 默认值兼容。
- `./gradlew :common:testDebugUnitTest :service:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 测试 216 项失败 0、错误 0，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64`：通过；Redmi Note 8 Pro（Android 16/API 36）72 项 instrumentation 失败 0、错误 0、跳过 0。
- Debug APK SHA-256 为 `b97a47829c3e13ba6d956d14d6595e963eb379d0bd9457e3636c705b854c6caa`，原生库仅包含 `lib/arm64-v8a/`；本轮未访问真实在线路径、未使用 `1.yaml`、未启动 VPN 或 Mihomo 核心。
- 当前切片状态：`DONE-VERIFIED`（Access 排序和来源过滤首片）；仍待智能选择、剪贴板导入导出、完整 Flutter 视觉交互和页面级 E2E。

### 2026-10-28：Always-on VPN 系统设置引导首片

- 新增 `AlwaysOnVpn` 平台边界封装和首页“系统 Always-on VPN”卡片；卡片展示当前可确认边界，并通过 `Settings.ACTION_VPN_SETTINGS` 引导用户进入系统 VPN 设置。
- 当前 Android SDK 对普通应用没有稳定公开的 OEM Always-on/lockdown 状态回读能力，`AlwaysOnVpn.state()` 保守返回 `UNKNOWN`；不把 KClash 进程存活、VPN 已连接或 TUN 存在误报成 Always-on 已开启。
- `MainActivityInstrumentedTest` 增加 Always-on 卡片存在断言；定向主 Activity 测试通过，4 项失败 0、错误 0、跳过 0。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试 216 项失败 0、错误 0，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.MainActivityInstrumentedTest`：通过；全量 `connectedDebugAndroidTest` 同样通过，72 项失败 0、错误 0、跳过 0。
- 设备为 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，ABI `arm64-v8a`）；本轮未访问两条真实在线路径、未使用工作区 `1.yaml`、未启动 VPN 或 Mihomo 核心。
- Debug APK SHA-256 为 `3d03f836f80e752c0cb3c13fb5a6f388be4c84e4bf5eb078c27c05d1d37a7fec`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`（系统设置引导首片）；Always-on 状态识别、OEM 兼容性和完整系统行为回归仍列为 N5 后续工作。

### 2026-10-28：Access 智能选择首片

- 新增 `ChinaPackageMatcher`，沿用 Flutter 的国内应用/SDK 包名前缀和排除列表；当前 Kotlin 只对已加载的启动器应用执行分类，不申请 `QUERY_ALL_PACKAGES`，不扫描用户不可见的安装包。
- Access 卡片新增“智能选择”入口；执行期间放到 `Dispatchers.Default`，锁定其他编辑入口并显示进度，完成后同时生成国内应用黑名单和其余应用白名单，保留 Flutter 的双列表语义。
- `AccessControlViewModelTest` 新增国内包识别、排除包和白/黑名单拆分断言；`AccessControlPanelInstrumentedTest` 验证按钮在启用状态可用、关闭状态禁用。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试 216 项失败 0、错误 0，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.AccessControlPanelInstrumentedTest`：通过，3 项失败 0、错误 0；全量 `connectedDebugAndroidTest` 同样通过，72 项失败 0、错误 0、跳过 0。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，ABI `arm64-v8a`）完成页面和控件验收；本轮未访问两条真实在线路径、未使用工作区 `1.yaml`、未启动 VPN 或 Mihomo 核心。
- Debug APK SHA-256 为 `a872797f40816bab48c78d2f49de7c65d88faa0e8dba96f8864a2244f86c3ea0`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`（Access 智能选择首片）；剪贴板导入/导出、完整 Flutter 视觉交互和页面级 E2E 仍待后续。

### 2026-10-28：Access 剪贴板导入/导出首片

- 新增 `ClipboardGateway` 和 Android 系统剪贴板适配器；不申请额外权限，ViewModel 不直接持有 `ClipboardManager`，便于 JVM fake 和页面测试隔离平台依赖。
- 导出严格沿用 Flutter 语义：只导出当前白名单/黑名单列表，以换行连接为纯文本；导入读取 `text/plain`，按换行拆分并按首次出现顺序去重，只替换当前模式，另一模式保持不变，空剪贴板不修改状态。
- 导入结果只写入页面内的 StateFlow，用户仍需点击“保存按应用代理”才会持久化；操作菜单在 Access 关闭时仍可用，核心运行、读取中、保存中或智能选择期间锁定，失败通过现有错误/ Snackbar 通道反馈。
- `AccessControlViewModelTest` 新增当前模式序列化、换行解析、重复项去重和另一模式保留断言；`AccessControlPanelInstrumentedTest` 新增 Compose 菜单导入/导出回调和关闭状态入口断言。
- `./gradlew test --no-daemon -Ptarget-platform=android-arm64`：通过；工程 JVM 测试 219 项失败 0、错误 0、跳过 0。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.AccessControlPanelInstrumentedTest`：通过，定向 4 项失败 0、错误 0；随后全量 instrumentation 73 项失败 0、错误 0、跳过 0。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，ABI `arm64-v8a`）完成剪贴板菜单页面验收；本轮未访问两条真实在线路径、未使用工作区 `1.yaml`、未启动 VPN 或 Mihomo 核心。
- Debug APK SHA-256 为 `a27e7c52a5727b7bea79c4a48b3f8a5502e96eb3d36bbba8e28d79b5f25530f9`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`（Access 剪贴板导入/导出首片）；完整 Flutter 视觉交互和页面级 E2E 仍待后续。

### 2026-10-28：Access 安装应用空状态首片

- `AccessControlCard` 现在区分“设备没有可识别启动器应用”和“当前排序、来源过滤或搜索没有匹配结果”，分别展示可行动的空状态文案或无匹配提示，不再渲染无解释的空 `LazyColumn`。
- 无应用或无匹配结果时，`全选当前结果`、`清除当前结果` 和 `智能选择` 均禁用；剪贴板菜单和保存入口仍遵循原有状态边界，避免空状态阻断用户恢复已有配置。
- 新增 `AccessControlPanelInstrumentedTest.emptyInstalledAppListShowsActionableStatusAndDisablesBatchSelection`，断言空状态文案、批量入口禁用和列表节点不存在。
- `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 测试 219 项失败 0、错误 0、跳过 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64`：通过；Redmi Note 8 Pro（Android 16/API 36）75 项 instrumentation 失败 0、错误 0、跳过 0。
- `./gradlew :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；Debug APK SHA-256 为 `e63b5a3637f53dcef5fb5bf59f01d942e679c06a1de31ade4082db0a28f8f4d8`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`。下一步补 Access 顶栏搜索/全选入口和整体视觉收敛。

### 2026-10-28：Access 返回未保存变更确认首片

- `AccessControlUiState` 新增已保存设置基线和 `hasUnsavedChanges`；搜索文本不计入脏状态，设置修改、剪贴板导入和智能选择均会触发脏状态，保存成功或放弃更改后回到基线。
- `KClashApp` 在 Access 脏状态下拦截系统返回和首页底部导航离开，弹出“取消 / 放弃更改 / 保存并退出”；取消保留当前编辑，放弃恢复已保存设置，保存成功后继续原本的导航或结束 Activity。
- `AccessControlPanelInstrumentedTest` 验证三按钮对话框的取消和放弃状态闭环；`MainActivityInstrumentedTest` 从真实 MainActivity 滚动到 Access 卡片，实际切换设置后验证底部导航离开和系统返回均被拦截，并恢复原状态。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试 220 项失败 0、错误 0、跳过 0，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64`：通过；Redmi Note 8 Pro（Android 16/API 36）77 项 instrumentation 失败 0、错误 0、跳过 0。
- Debug APK SHA-256 为 `738a2bbdb9e717d85a0106397ea6742bd65777da472758fd0d2b7bad39790395`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`。下一步补 Access 顶栏搜索/全选入口和整体视觉收敛。

### 2026-10-29：Access 卡片顶栏搜索与全选入口

- Access 卡片标题栏新增搜索入口和“全选当前结果 / 取消全选当前结果”入口；全选只作用于当前排序、来源过滤和搜索后的可见应用，保持 Flutter 的页面顶栏语义。
- 搜索入口展开后显示应用名/包名输入框；关闭按应用代理时仍允许搜索和查看已保留选择，模式、筛选、应用勾选、批量操作和保存继续由关闭状态遮罩锁定。
- `AccessControlUiState` 新增 `allVisibleAppsSelected`，`AccessControlViewModel.toggleVisibleApps()` 复用当前可见列表执行全选/取消全选；旧的正文批量按钮仍保留，作为小屏备用入口。
- `AccessControlPanelInstrumentedTest` 新增顶栏全选/取消全选闭环，并覆盖关闭状态下搜索入口可用、展开后搜索框可编辑；现有搜索、剪贴板、空态和未保存确认测试同步到入口展开语义。
- `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试 220 项失败 0、错误 0、跳过 0，Lint 错误 0。
- `adb devices -l` 与设备属性确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，设备 ABI 列表包含 `arm64-v8a`）；`ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64`：通过，78 项失败 0、错误 0、跳过 0。
- Debug APK SHA-256 为 `f29442a76b6e4fee0e96a29374bbc6a135ffd1aadc11db3f45100b8554efa125`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`。下一步进入 Access 完整视觉收敛和页面级 E2E，或转入 N1 蜂窝网络条件具备后的切换补测。

### 2026-10-29：Access 顶栏图标视觉收敛

- 搜索、全选/取消全选和关闭搜索改用 Material 3 矢量图标，移除 Unicode 字符作为结构性图标；图标按钮保持 Android 至少 48dp 触控区域。
- 搜索框使用 `AnimatedVisibility` 展开/收起，保持页面空间连续；正文批量按钮继续保留，作为小屏备用入口。
- UI/UX 依据：移动端触控目标至少 48dp，邻近控件保留 8dp 间距；本轮未改变业务状态边界。
- `AccessControlPanelInstrumentedTest` 覆盖搜索关闭、全选/取消全选；定向 Access 测试和全量 instrumentation 均通过。
- `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试 220 项失败 0、错误 0、跳过 0，Lint 错误 0。
- `adb devices -l` 确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；定向 `AccessControlPanelInstrumentedTest` 通过；全量 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64`：通过，78 项失败 0、错误 0、跳过 0。
- Debug APK SHA-256 为 `0b2f79ab4046962228463336411a050a52c14fab8900d9666367faf0b2a62c0c`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`。Access 完整页面视觉交互、动态字体/横竖屏专项验收和页面级 E2E 仍待后续切片。

### 2026-10-29：Access 操作区窄屏响应式收敛

- Access 标题栏将搜索、全选和开关控件放入带 8dp 间距的稳定操作组；正文“智能选择 / 更多操作 / 全选当前结果 / 清除当前结果”改用 `FlowRow`，窄屏或大字号时允许按行换行，不再依赖单行横向空间。
- 该调整只改变布局，不改变按应用代理状态、核心运行锁定、关闭状态遮罩或批量操作语义；小屏仍保留正文文字入口，避免只依赖图标操作。
- `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试 220 项失败 0、错误 0、跳过 0，Lint 错误 0。
- `adb devices -l` 确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；全量 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64`：通过，78 项失败 0、错误 0、跳过 0。Gradle 结束时 logcat reader 报告 stream closed，但 XML 结果仍为成功且无失败/错误。
- Debug APK SHA-256 为 `037ee1ee15296c6de91c8255e40cd1dec0f3ec69af789777cd68f255b3691904`，APK 内原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`。下一步补动态字体/横竖屏专项验收和 Access 页面级 E2E。

### 2026-10-29：Access 动态字体、横屏与真实宿主重建验收

- Access 标题、状态说明、排序和系统/无网络筛选行改为可换行布局；状态说明限制为两行并使用省略号，避免大字号把选中数量或开启入口挤出卡片。
- `AccessControlPanelInstrumentedTest.largeFontScaleKeepsAccessControlActionsReachable` 在 `LocalDensity(fontScale = 1.5f)` 下确认搜索、更多操作、批量入口、保存和应用选择节点仍存在。
- `MainActivityInstrumentedTest.mainActivityPersistsDirectAccessSelectionAcrossRecreation` 从真实 MainActivity 选择设备上的非系统网络应用，保存后重建 Activity，重新读取 Room 设置并断言选择状态保持；测试结束恢复原始剪贴板和按应用代理状态。
- `MainActivityInstrumentedTest.mainActivityReflowsAccessCardAfterRotation` 在 Redmi 真机切换横屏并恢复竖屏，确认 Access 卡片、搜索入口和开关仍可见；Manifest 的 `configChanges` 保持同一 Activity 生命周期。
- 设备系统字体临时设置为 `1.3` 后运行 `MainActivityInstrumentedTest`：8 项失败 0、错误 0、跳过 0；验收结束已恢复系统字体 `1.0`，未留下设备设置副作用。
- `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试 220 项失败 0、错误 0，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64`：通过；Redmi Note 8 Pro（Android 16/API 36）81 项失败 0、错误 0、跳过 0。XML 结果文件为 `android/app/build/outputs/androidTest-results/connected/debug/TEST-Redmi Note 8 Pro - 16.xml`。
- Debug APK SHA-256 为 `fd85cf82d0447e5c92ccd02d59a54441938339c97025a28ccb1c8572741c9142`；APK 原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`。下一步收敛 Flutter 顶栏保存/菜单层级、更多系统字体档位和截图级视觉回归。

### 2026-10-29：Access Flutter 顶栏菜单层级首片

- 顶栏“更多操作”按 Flutter 结构拆为主菜单、设置层和操作层：主菜单提供开启/关闭、设置和操作；设置层提供白名单/黑名单、默认/名称/更新时间排序及系统/无网络应用显示开关；操作层提供智能选择、剪贴板导入和导出。
- 正文“更多操作”保留为直接进入操作层的文字入口，保证小屏路径不依赖顶栏图标；所有菜单项沿用核心运行、读取中、保存中和智能选择中的既有锁定边界。
- `AccessControlPanelInstrumentedTest.topBarMenuMatchesFlutterAccessControlHierarchy` 覆盖主菜单进入设置/操作层、模式切换、排序、来源显示开关、智能选择和关闭入口；定向 Access 类 10 项失败 0、错误 0、跳过 0。
- `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试 220 项失败 0、错误 0。
- `./gradlew :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；Lint 错误 0。
- `adb devices -l` 确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，ABI `arm64-v8a`）；定向 `connectedDebugAndroidTest` 复跑通过，10 项失败 0、错误 0、跳过 0。
- 全量 instrumentation 两次重跑均未通过：83 项中分别出现 2 项和 4 项 `No compose hierarchies found in the app`；logcat 显示设备测试期间前台被外部应用 `com.duanju.player` 抢占，导致 Activity 被暂停/销毁。该结果按设备前台干扰记录，不作为本轮代码失败证据；全量回归需在设备前台稳定后重跑。
- Debug APK SHA-256 为 `acaf5a9dee122262c41e901976e50608cbcfe4fb71a471072f766bc9d2b249a2`；APK 原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`（定向真机证据），全量回归待设备稳定后补验。下一步进入更多系统字体档位和截图级视觉回归。

### 2026-10-29：Access Flutter `searchInBar` 完整搜索态首片

- 搜索框从正文排序区域移到 Access 卡片标题栏，进入搜索后占据标题区域并保留全选、保存、更多操作和开关入口；关闭搜索时清空查询，遵循 Flutter `searchInBar` 的返回语义。
- 搜索框仍使用应用名/包名过滤 `visibleApps`，关闭按应用代理时可搜索和查看已保留选择，模式、筛选、勾选和保存锁定边界不变。
- `AccessControlPanelInstrumentedTest.searchAndSelectPackageUpdatesBlacklist` 增加标题栏搜索框存在、输入、关闭后移除并清空查询的回归断言；定向 Access 类 10 项失败 0、错误 0、跳过 0。
- `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试 220 项失败 0、错误 0，Lint 错误 0。
- `adb devices -l` 确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，ABI `arm64-v8a`）；定向 `connectedDebugAndroidTest` 通过，10 项失败 0、错误 0、跳过 0。
- Debug APK SHA-256 为 `da60abdeff1d1fe4ab5c5911ef85f06e41a7dc44e4c81aeec0bfacd998a4d537`；APK 原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`（定向真机证据）。下一步进入更多系统字体档位和截图级视觉回归。

### 2026-10-29：Access 2.0 字体档位可达性与宿主回归

- 新增 `AccessControlPanelInstrumentedTest.extraLargeFontScaleKeepsAccessControlActionsReachable`，在 `LocalDensity(fontScale = 2.0f)` 下确认标题栏搜索、更多操作、批量入口、保存和应用选择节点仍可达；与既有 1.5 倍 Compose 档位形成双档位回归。
- Redmi Note 8 Pro 真机临时设置系统 `font_scale=2.0`，运行 `MainActivityInstrumentedTest.mainActivityReflowsAccessCardAfterRotation`，确认横屏重排宿主通过；验收结束恢复系统 `font_scale=1.0`。
- 2.0 字体真机测试期间临时停用自动抢占前台的 `com.duanju.player`，测试完成后已恢复启用；该包导致的全量回归干扰不计为 KClash 代码失败。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；App JVM 单元测试 102 项失败 0、错误 0，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.AccessControlPanelInstrumentedTest#extraLargeFontScaleKeepsAccessControlActionsReachable`：通过，1 项失败 0、错误 0；系统字体 2.0 的 MainActivity 定向测试通过，Gradle 仅报告 logcat reader `Stream closed` 警告。
- 本轮两次全量 83 项仍因 `com.duanju.player` 自动抢占前台而未形成稳定全量基线；logcat 明确记录 KClash Activity `PAUSED/DESTROYED` 后出现 `No compose hierarchies found`。该边界保持记录，待设备前台稳定后重跑。
- Debug APK SHA-256 仍为 `da60abdeff1d1fe4ab5c5911ef85f06e41a7dc44e4c81aeec0bfacd998a4d537`，APK 原生库仅包含 `lib/arm64-v8a/`；本轮未访问两条真实在线路径、未使用 `1.yaml`、未启动 VPN 或 Mihomo 核心。当前切片状态：`DONE-VERIFIED`（定向和系统字体真机证据）。下一步进入更多系统字体档位与截图级视觉回归。

### 2026-10-29：Access 默认卡片截图基线首片

- `AccessControlPanelInstrumentedTest.defaultAccessControlCardMatchesScreenshotBaseline` 使用 Compose `captureToImage()` 捕获固定默认状态的 Access 卡片 PNG，并对 PNG 字节计算 SHA-256；布局、主题或默认文案发生像素变化时测试会直接失败。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 `1.0`）基线哈希为 `74d0dc07b35597f6e6f19905ae4047108290ae8bb6c8a5275c7976237e8236e3`，记录在 `docs/visual-baselines/access-control-card-default-redmi-api36.sha256`。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.AccessControlPanelInstrumentedTest#defaultAccessControlCardMatchesScreenshotBaseline`：通过，1 项失败 0、错误 0；为保证前台稳定，测试期间临时停用 `com.duanju.player`，完成后已恢复启用。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过；未访问真实在线路径、未使用 `1.yaml`、未启动 VPN 或 Mihomo 核心。
- 当前切片状态：`DONE-VERIFIED`（单设备截图基线首片）。下一步补齐更多字体档位和横竖屏截图矩阵，再处理跨设备差异。


### 2026-10-29：Access 1.5/2.0 字体截图矩阵首片

- `AccessControlPanelInstrumentedTest.accessControlCardFontScaleScreenshotMatrixMatchesBaseline` 在同一 Compose 内容树内切换 `LocalDensity.fontScale`，覆盖 `1.5` 和 `2.0` 两个档位，避免重复 `setContent` 破坏测试宿主。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 `1.0`）PNG SHA-256：`fontScale=1.5` 为 `8f13a4b9e236f2ea6997f4828486d06d532766f4dfcd751cf791866adf85b0c8`，`fontScale=2.0` 为 `e4ab4b4d3136c0dd017f4e47030a1b8aef8448c71e8df92953373329a5a3b0f5`；基线记录在 `docs/visual-baselines/access-control-card-font-scale-redmi-api36.sha256`。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试失败 0、错误 0，Lint 错误 0。
- `adb devices -l` 确认目标真机；`ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.AccessControlPanelInstrumentedTest#accessControlCardFontScaleScreenshotMatrixMatchesBaseline`：通过，1 项失败 0、错误 0、跳过 0。
- Debug APK SHA-256：`da60abdeff1d1fe4ab5c5911ef85f06e41a7dc44e4c81aeec0bfacd998a4d537`；APK 原生库仅包含 `lib/arm64-v8a/`。验收后系统字体为 `1.0`，`com.duanju.player` 为 `enabled=1`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`（单设备 1.5/2.0 字体截图矩阵首片）。下一步补齐横竖屏截图矩阵、更多系统字体档位和跨设备差异。


### 2026-10-29：Access 竖屏/横屏截图矩阵首片

- 新增 Debug 专用 `AccessControlTestActivity`，固定 Access 初始状态和示例应用清单；横竖屏测试不读取 Room、系统安装包、网络、VPN 或 Mihomo 核心，避免设备状态污染截图基线。
- 新增 `AccessControlOrientationInstrumentedTest.accessControlCardOrientationScreenshotMatrixMatchesBaseline`，在同一 Activity 中切换 `SCREEN_ORIENTATION_PORTRAIT` 与 `SCREEN_ORIENTATION_LANDSCAPE`，等待真实 `Configuration` 方向后捕获 `access_control_card` PNG。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 `1.0`）PNG SHA-256：竖屏 `74d0dc07b35597f6e6f19905ae4047108290ae8bb6c8a5275c7976237e8236e3`，横屏 `72e489740b2ed9e114257b0adbd9732a83acbff0348a801fe7dc8147a52c8318`；基线记录在 `docs/visual-baselines/access-control-card-orientation-redmi-api36.sha256`。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过；占位哈希首次运行按预期失败并输出实际两档哈希，替换基线后定向测试通过。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试失败 0、错误 0，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.AccessControlOrientationInstrumentedTest#accessControlCardOrientationScreenshotMatrixMatchesBaseline`：通过，1 项失败 0、错误 0、跳过 0。
- Debug APK SHA-256：`0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`；验收后系统字体为 `1.0`、设备恢复竖屏、`com.duanju.player` 为 `enabled=1`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`（单设备竖屏/横屏截图矩阵首片）。下一步补齐更多系统字体档位和跨设备截图差异。

### 2026-10-29：Access 1.3/1.8 字体截图档位扩展

- 扩展 `AccessControlPanelInstrumentedTest.accessControlCardFontScaleScreenshotMatrixMatchesBaseline`，在同一 Compose 内容树中覆盖 `1.3`、`1.5`、`1.8`、`2.0` 四个 `LocalDensity.fontScale` 档位；既有 `1.5/2.0` 基线继续保持不变。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 `1.0`）新增 PNG SHA-256：`fontScale=1.3` 为 `7e230a41dc0e03e5220a47c5df55a90d8668fed02fbd0359e55a308849b2fc71`，`fontScale=1.8` 为 `3e18033ee22e687cf7cf39a2f945f4fb7bb97c9fbb54745fe1590a574cfa15d7`；基线已追加到 `docs/visual-baselines/access-control-card-font-scale-redmi-api36.sha256`。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试失败 0、错误 0，Lint 错误 0。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.AccessControlPanelInstrumentedTest#accessControlCardFontScaleScreenshotMatrixMatchesBaseline`：通过，1 项失败 0、错误 0、跳过 0。
- Debug APK SHA-256：`0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`；验收后系统字体为 `1.0`，设备保持竖屏，`com.duanju.player` 为 `enabled=1`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`（单设备四档字体截图矩阵）。下一步补齐跨设备截图差异和完整页面级视觉回归。

### 2026-10-29：全量 instrumentation 稳定复验

- 为排除已知前台抢占干扰，测试前临时停用 `com.duanju.player`；`ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64` 全量通过，87 项失败 0、错误 0、跳过 0。
- XML 结果文件为 `android/app/build/outputs/androidTest-results/connected/debug/TEST-Redmi Note 8 Pro - 16.xml`；设备为 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）。
- Debug APK SHA-256：`0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`；验收后系统字体为 `1.0`、设备保持竖屏，`com.duanju.player` 已恢复 `enabled=1`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`（全量真机 instrumentation 稳定基线）。

### 2026-10-29：Access 真实宿主无结果搜索恢复 E2E

- `MainActivityInstrumentedTest.mainActivityRestoresAccessListAfterSearchNoResults` 从真实 `MainActivity` 进入按应用代理卡片，输入不存在的搜索词 `__flclash_no_match__`，断言显示 `access_control_no_results`、应用列表消失且“全选当前结果/清除当前结果”不可用；关闭标题栏搜索后断言查询清空、空结果提示消失、应用列表恢复。
- 该流程只改变页面内搜索状态，不写入 Room、不启动 VPN、不访问订阅或 Mihomo 核心，结束时没有留下按应用代理设置污染。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityRestoresAccessListAfterSearchNoResults`：通过，1 项失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试 220 项失败 0、错误 0，Lint 错误 0；Debug APK SHA-256 为 `0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`，原生库仅包含 `lib/arm64-v8a/`。
- 随后尝试全量 instrumentation 时，设备报告 45 项中 2 项既有用例失败：`runningCoreLocksAccessControlEdits` 出现 `No compose hierarchies found in the app`，`findingSummaryShowsHttp403GuidanceAndRedactedDetails` 产生空 failure 节点；两项单独定向复跑均通过，未把这次不完整运行计入全量基线。此前稳定全量基线仍为 87 项通过；`com.duanju.player` 已恢复启用。
- 当前切片状态：`DONE-VERIFIED`（真实 MainActivity 无结果搜索恢复）；下一步补真实宿主的顶栏全选/清除边界和保存/放弃组合流程，再处理跨设备截图差异。

### 2026-10-29：Access 真实宿主当前结果批量操作 E2E

- 新增 `MainActivityInstrumentedTest.mainActivityTogglesOnlyVisibleAccessResultsAndDiscardsChanges`；测试从真实 `MainActivity` 搜索设备上的可选网络应用，验证顶栏“全选当前结果”切换为“取消全选当前结果”，正文“清除当前结果”和“全选当前结果”只改变当前搜索结果，并再次验证顶栏取消分支。
- 测试随后关闭搜索，导航到观测页触发真实“按应用代理有未保存的修改”对话框，点击“放弃更改”，返回首页重新搜索同一应用，确认 `enable` 状态和应用勾选状态恢复到进入测试前的持久化基线；流程不写入 Room、不启动 VPN、不访问订阅或 Mihomo 核心。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityTogglesOnlyVisibleAccessResultsAndDiscardsChanges`：通过，1 项失败 0、错误 0、跳过 0。
- 同一设备上完整 `MainActivityInstrumentedTest` 类复跑通过；标准 `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过，JVM 单元测试 220 项失败 0、错误 0，Lint 错误 0；Debug APK SHA-256 仍为 `0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`，原生库仅包含 `lib/arm64-v8a/`。
- 真机为 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；测试期间临时停用 `com.duanju.player`，完成后已恢复启用。未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心。
- 当前切片状态：`DONE-VERIFIED`（真实宿主当前结果批量操作与放弃修改）；下一步补跨设备截图差异和完整页面级视觉回归，再处理剩余 Access 交互缺口。

### 2026-10-29：Access 关闭态与脏状态截图基线

- `AccessControlPanelInstrumentedTest.accessControlCardStateScreenshotMatrixMatchesBaseline` 在同一 Compose 内容树中捕获 Access 关闭态和脏状态的 PNG，并锁定 SHA-256；关闭态覆盖“应用访问控制已关闭”和一键开启入口，脏状态覆盖选中数量、顶栏保存入口和未保存视觉状态。
- 新增基线文件 `docs/visual-baselines/access-control-card-states-redmi-api36.sha256`：关闭态 `697332c4f518d575e67b342a1535b1a74be9ff6fffdbb0b99a072fce859ae90c`，脏状态 `303caf72a6bfb85694a64981e0246f324a14f0637fb063b424a4f29f6aa88193`。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过；首轮按预期输出实际哈希，写入基线后复跑通过。
- `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.AccessControlPanelInstrumentedTest#accessControlCardStateScreenshotMatrixMatchesBaseline`：通过，1 项失败 0、错误 0、跳过 0。
- 同一设备完整 `AccessControlPanelInstrumentedTest` 通过，14 项失败 0、错误 0、跳过 0；标准 `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过，JVM 单元测试 220 项失败 0、错误 0，Lint 错误 0；Debug APK SHA-256 仍为 `0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`，原生库仅包含 `lib/arm64-v8a/`。
- 真机为 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；测试期间临时停用 `com.duanju.player`，完成后已恢复启用。截图测试不读取 Room、系统安装包、网络、VPN 或 Mihomo 核心。
- 当前切片状态：`DONE-VERIFIED`（Access 关键交互状态视觉基线）；下一步继续补跨设备条件可用时的截图差异，并扩展 Dashboard/Profiles/观测页面的真实宿主视觉与交互回归。

### 2026-10-29：MainActivity 观测页停止态空态与页签回归

- 新增 `MainActivityInstrumentedTest.mainActivityRendersObservationEmptyStatesFromEachTab`，从真实 `MainActivity` 进入“观测”，依次切换 Requests、DNS、Logs 三个页签，断言页面标签、停止态空态文案和返回首页导航均可用。
- 该用例只验证本地 Compose 导航和停止态渲染，不启动核心、VPN 或网络，不访问两条授权在线路径，也不读取或替换工作区根目录 `1.yaml`。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过。
- `adb devices -l` 确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；定向执行：
  `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityRendersObservationEmptyStatesFromEachTab`，1 项失败 0、错误 0、跳过 0。
- 本轮 `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试失败 0、错误 0，Lint 错误 0；Debug APK SHA-256 为 `0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`，原生库仅包含 `lib/arm64-v8a/`。
- MainActivity 类级复验期间 `com.duanju.player` 自动抢占前台，造成既有用例失败；临时停用干扰应用后的 Gradle 结果 XML 为 `tests=0`，没有形成有效类级通过证据，稳定的 87 项全量基线保持不变。测试结束已恢复 `com.duanju.player` 为 `enabled=1` 并停止其进程，未改变核心、VPN 或订阅状态。
- 当前切片状态：`DONE-VERIFIED`（观测页停止态和三页签真实宿主定向证据）；下一步补 Requests/DNS/Logs 非空详情在真实 `MainActivity` 中的入口、筛选和关闭回归，再继续 Profiles 高级编辑页面级 E2E。

### 2026-10-29：MainActivity Profiles Rules 高级编辑 E2E

- 新增 `MainActivityInstrumentedTest.mainActivityPersistsCustomProfileRulesThroughRealViewModels`，从真实 `MainActivity` 创建自定义 Profile，先保存基础 YAML，再打开“高级”Rules 编辑器输入 `DOMAIN-SUFFIX,example.com,DIRECT`，保存后重新打开确认规则内容已回读，最后删除测试 Profile 清理 Room 和本地文件。
- 该流程覆盖真实 Hilt、`ProfilesViewModel`、`ProfileRepository`、Room 和 Profile 文件写回；不访问订阅路径，不启动 VPN 或 Mihomo 核心，不使用或替换工作区根目录 `1.yaml`。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过。
- `adb devices -l` 确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；定向执行：
  `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityPersistsCustomProfileRulesThroughRealViewModels`，1 项失败 0、错误 0、跳过 0。
- 测试期间临时停用 `com.duanju.player` 以避免前台抢占，结束后恢复 `enabled=1`；未改变核心、VPN 或订阅状态。
- 本轮 `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；JVM 单元测试失败 0、错误 0，Lint 错误 0；Debug APK SHA-256 为 `0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`，原生库仅包含 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`（Rules 高级编辑真实宿主和持久化回读）；下一步补 Proxies/Proxy Groups/Providers/DNS/Dialers 的真实 `MainActivity` 高级编辑回归，再处理观测非空详情入口和筛选关闭流程。

### 2026-10-29：MainActivity Profiles Proxies 高级编辑 E2E

- 新增 `MainActivityInstrumentedTest.mainActivityPersistsCustomProfileProxiesThroughRealViewModels`，从真实 `MainActivity` 创建自定义 Profile，先保存基础 YAML，再打开“代理”编辑器输入 `DIRECT-TEST` 最小代理条目，保存后重新打开确认代理片段已从真实 Profile 文件回读，最后删除测试 Profile 清理 Room 和本地文件。
- 测试显式选择配置卡片内的“代理”按钮，避免它与底部导航同名造成宿主语义歧义；该流程覆盖真实 Hilt、`ProfilesViewModel`、`ProfileRepository`、Room 和 Profile 文件写回。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过。
- `adb devices -l` 确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；定向执行：
  `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityPersistsCustomProfileProxiesThroughRealViewModels`，1 项失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；四个 Kotlin 模块合计 220 项 JVM 单元测试失败 0、错误 0、跳过 0；Lint 0 errors、36 warnings、2 hints；Debug APK SHA-256 为 `0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`，原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；测试结束后设备没有留下测试 Profile 或本地文件。
- 当前切片状态：`DONE-VERIFIED`（Proxies 高级编辑真实宿主和持久化回读）；下一步补 Proxy Groups/Providers/DNS/Dialers 的真实 `MainActivity` 高级编辑回归，再处理观测非空详情入口和筛选关闭流程。

### 2026-10-29：MainActivity Profiles Proxy Groups 高级编辑 E2E

- 新增 `MainActivityInstrumentedTest.mainActivityPersistsCustomProfileProxyGroupsThroughRealViewModels`，从真实 `MainActivity` 创建自定义 Profile，先保存基础 YAML，再打开“代理组”编辑器输入 `AUTO-TEST` 的 select 组和节点列表，保存后重新打开确认代理组片段已从真实 Profile 文件回读，最后删除测试 Profile 清理 Room 和本地文件。
- 该流程覆盖真实 Hilt、`ProfilesViewModel`、`ProfileRepository`、Room 和 Profile 文件写回；未启动核心，因此不会把代理组 YAML 写回误当成 Mihomo 运行时选择证据。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过。
- `adb devices -l` 确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；定向执行：
  `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityPersistsCustomProfileProxyGroupsThroughRealViewModels`，1 项失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；四个 Kotlin 模块合计 220 项 JVM 单元测试失败 0、错误 0、跳过 0；Lint 0 errors、36 warnings、2 hints；Debug APK SHA-256 为 `0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`，原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；测试结束后设备没有留下测试 Profile 或本地文件。
- 当前切片状态：`DONE-VERIFIED`（Proxy Groups 高级编辑真实宿主和持久化回读）；下一步补 Providers/DNS/Dialers 的真实 `MainActivity` 高级编辑回归，再处理观测非空详情入口和筛选关闭流程。

### 2026-10-29：MainActivity Profiles Providers 高级编辑 E2E

- 新增 `MainActivityInstrumentedTest.mainActivityPersistsCustomProfileProvidersThroughRealViewModels`，从真实 `MainActivity` 创建自定义 Profile，先保存基础 YAML，再打开“Provider”编辑器输入本地 `file` Provider，保存后重新打开确认 `proxy-providers` 片段已从真实 Profile 文件回读，最后删除测试 Profile 清理 Room 和本地文件。
- 测试使用 `test: type: file` 和应用私有相对路径，不访问在线订阅、不引入真实 URL；该流程覆盖真实 Hilt、`ProfilesViewModel`、`ProfileRepository`、Room 和 Profile 文件写回。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过。
- `adb devices -l` 确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；定向执行：
  `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityPersistsCustomProfileProvidersThroughRealViewModels`，1 项失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；四个 Kotlin 模块合计 220 项 JVM 单元测试失败 0、错误 0、跳过 0；Lint 0 errors、36 warnings、2 hints；Debug APK SHA-256 为 `0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`，原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；测试结束后设备没有留下测试 Profile 或本地文件。
- 当前切片状态：`DONE-VERIFIED`（Providers 高级编辑真实宿主和持久化回读）；下一步补 DNS/Dialers 的真实 `MainActivity` 高级编辑回归，再处理观测非空详情入口和筛选关闭流程。

### 2026-10-29：MainActivity Profiles DNS 覆盖高级编辑 E2E

- 新增 `MainActivityInstrumentedTest.mainActivityPersistsCustomProfileDnsOverridesThroughRealViewModels`，从真实 `MainActivity` 创建自定义 Profile，打开“DNS”覆盖表单，滚动到 Nameserver 字段写入保留地址 `198.18.0.2`，保存后重新打开确认覆盖值从真实 Room 元数据回读，最后删除测试 Profile 清理 Room 和本地文件。
- DNS 覆盖属于 Profile 元数据，不改写原始 YAML；该流程验证真实 Hilt、`ProfilesViewModel`、`ProfileRepository`、Room、可滚动 Compose 表单和保存/回读边界，不启动核心或网络。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过。
- `adb devices -l` 确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；定向执行：
  `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityPersistsCustomProfileDnsOverridesThroughRealViewModels`，1 项失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；四个 Kotlin 模块合计 220 项 JVM 单元测试失败 0、错误 0、跳过 0；Lint 0 errors、36 warnings、2 hints；Debug APK SHA-256 为 `0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`，原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；测试结束后设备没有留下测试 Profile 或本地文件。
- 当前切片状态：`DONE-VERIFIED`（DNS 覆盖高级编辑真实宿主和 Room 持久化回读）；下一步补 Dialers 的真实 `MainActivity` 高级编辑回归，再处理观测非空详情入口和筛选关闭流程。

### 2026-10-29：MainActivity Profiles Dialers 高级编辑 E2E

- 新增 `MainActivityInstrumentedTest.mainActivityPersistsCustomProfileDialersThroughRealViewModels`，从真实 `MainActivity` 创建自定义 Profile，打开“Dialers”编辑器输入 `DIRECT-TEST -> AUTO-TEST` 映射，保存后重新打开确认映射从真实 Room 元数据回读，最后删除测试 Profile 清理 Room 和本地文件。
- Dialers 映射属于 Profile 覆盖元数据，核心启动时才注入暂存 YAML；本轮只验证真实 Hilt、`ProfilesViewModel`、`ProfileRepository`、Room 和 Compose 编辑/回读，不启动核心或网络。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过。
- `adb devices -l` 确认 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；定向执行：
  `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityPersistsCustomProfileDialersThroughRealViewModels`，1 项失败 0、错误 0、跳过 0。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过；四个 Kotlin 模块合计 220 项 JVM 单元测试失败 0、错误 0、跳过 0；Lint 0 errors、36 warnings、2 hints；Debug APK SHA-256 为 `0607455c385ab0cd173863786c9fb95f5543e625c5271952e5f8a7f9411b0b39`，原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；测试结束后设备没有留下测试 Profile 或本地文件。
- 当前切片状态：`DONE-VERIFIED`（Dialers 高级编辑真实宿主和 Room 持久化回读）；下一步转入观测非空详情入口、筛选和关闭的真实 `MainActivity` 回归，再补跨设备截图差异。

### 2026-10-29：MainActivity 观测非空详情入口与筛选关闭回归

- 使用工作区根目录 `1.yaml` 在 Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36）启动真实 Mihomo；首页随后累计显示 Requests `269`、DNS `454`、核心日志 `274` 条非空记录。
- 从真实 `MainActivity` 进入“观测”页，Requests、DNS、Logs 三个页签的“查看全部”入口均成功打开详情对话框。
- Requests 详情搜索随机哨兵文本后显示 `0/269` 和“没有匹配的请求记录”；DNS 详情搜索同一哨兵文本后显示 `0/454` 和“没有匹配的 DNS 查询”。
- Logs 详情从“全部”切换到“错误”后显示 `0/274` 和“没有匹配的核心日志”，确认日志级别筛选状态实际生效，即使当前运行批次没有 ERROR 级别记录也不会伪造结果。
- 三个详情对话框均通过真实关闭入口退出；随后停止核心并删除测试 Profile，首页/配置页恢复“还没有配置”，未留下 KClash 前台服务或测试 Profile。
- 本轮只读取已授权的工作区 `1.yaml`，未把配置正文、令牌、密码、UUID 或订阅链接写入路线图；未新增在线地址。
- 当前切片状态：`DONE-VERIFIED`（真实 MainActivity 非空观测详情入口、搜索、日志级别筛选和关闭回归）；下一步转入跨设备/完整页面级覆盖和 UI 截图差异收敛。

### 2026-10-29：观测页 Requests/DNS/Logs 六状态截图基线首片

- `ObservationDetailsInstrumentedTest.observationPageScreenshotMatrixMatchesBaseline` 固定观测页三页签的停止态空卡片和运行态单条记录，捕获包含页签栏与卡片的 `observation_page` PNG，并对六个状态分别计算 SHA-256。
- 为避免相对时间文案随测试运行时钟漂移，运行态日志使用固定历史时间戳 `0L`；该视觉测试不读取 Room、系统安装包、网络、VPN、Mihomo 核心或工作区 `1.yaml`。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 `1.0`、深色模式）六个基线记录在 `docs/visual-baselines/observation-page-states-redmi-api36.sha256`：Requests 停止态 `2c301cde81cb2132e7460d041ed0ec81df0d28812fe0b418ebc99eafc9bbef41`、Requests 运行态 `51acd93dd9bca5e9a3998ba377acbfc017d8a0ccc3b2750c8e083a96bba23540`、DNS 停止态 `f181db667a12f9db0c1cf764816b7c56b139d96792d08d80695ee5b158fdf7e4`、DNS 运行态 `3a544d24ed1a6924f9c902f1cbb85be8fa7415bee5610b990951ffff40abce44`、Logs 停止态 `8ef07b0b6abd266258b74000eafa2b252426a5b999af49a5cf704866ea1624d3`、Logs 运行态 `c5094b6641af25dcf7aac38c1c4d6461321ea7d2a0dbe568daab5fb06f87f495`。
- `ObservationPage` 新增稳定页面级 `testTag("observation_page")`；组件截图定向命令：
  `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.ObservationDetailsInstrumentedTest#observationPageScreenshotMatrixMatchesBaseline`，通过，1 项失败 0、错误 0、跳过 0。
- 本轮只建立单设备组件视觉证据，不能替代完整 `MainActivity` 页面级截图或跨设备差异回归；当前切片状态：`DONE-VERIFIED`（Redmi 单设备观测页六状态截图基线首片）。下一步扩展 Dashboard、Profiles、Proxy、Connections 的页面级截图/交互矩阵，再处理跨设备基线策略。

### 2026-10-29：Proxy 代理组截图基线首片

- `ProxyGroupsActivityInstrumentedTest.proxyGroupsScreenshotMatrixMatchesBaseline` 复用 Debug 专用 `ProxyGroupsTestActivity` 的固定 `Auto` 代理组，捕获默认代理组卡片和初始选择对话框内容两种稳定状态；节点搜索、`node-b` 切换和 computed 组恢复自动选择继续由既有行为测试覆盖。
- `ProxyGroupsCard` 根卡片新增 `proxy_groups_card` 标签，选择器内容新增 `proxy_group_dialog_content_Auto` 标签；截图测试不读取 Room、网络、VPN、Mihomo 核心或工作区 `1.yaml`。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 `1.0`、深色模式）基线记录在 `docs/visual-baselines/proxy-groups-states-redmi-api36.sha256`：默认卡片 `d2dd27e3d2b89b7f2aa41a3e05f27b1a837dd5c042c7e934017c7d35fab7f4ff`，初始选择对话框 `bb9ce7f5ed33e3145023546eb8bc12c3b329cee56d520c5dcfe47e70688dcf0b`。
- 定向截图测试连续运行两次均通过；完整 `ProxyGroupsActivityInstrumentedTest`（代理组加载、节点搜索、选择持久化、恢复自动选择和截图基线）通过，失败 0、错误 0、跳过 0。
- 视觉矩阵主动排除了输入光标和点击涟漪等瞬态像素，避免把动画时序误写成基线；当前切片状态：`DONE-VERIFIED`（Redmi 单设备 Proxy 两状态截图基线首片）。下一步扩展 Dashboard、Profiles、Connections 页面级截图/交互矩阵。

### 2026-10-29：Dashboard 固定状态截图基线首片

- 新增 Debug 专用 `DashboardTestActivity`，复用正式 Dashboard 的 Runtime、运行统计、电池优化、Always-on VPN、按应用代理和真实请求诊断卡片；宿主固定停止核心状态，不读取 Room、系统设置、已安装应用、网络、VPN、Mihomo 核心或工作区 `1.yaml`。
- 新增 `DashboardInstrumentedTest.dashboardScreenshotMatrixMatchesBaseline`，覆盖默认停止态，以及电池优化已忽略、KClash 已启用 Always-on VPN、按应用代理关闭的系统就绪态；根节点使用 `dashboard_page` 标签捕获整组可滚动 Dashboard 内容。
- Runtime、运行统计、电池优化、诊断卡片新增稳定测试标签，正式 MainActivity 行为不变；测试只验证固定数据渲染，真实启动/停止核心和系统设置引导继续由现有宿主 E2E 负责。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 `1.0`、深色模式）基线记录在 `docs/visual-baselines/dashboard-states-redmi-api36.sha256`：默认态 `557961817f608d1eb34033928b354f515f24cc546320acbc4b2771d9640e724e`，系统就绪态 `08dad777abcfe55e86aba952ef355a01a0f757feb45802942f0c3c255fd58607`。
- 定向 Dashboard 截图测试连续运行两次均通过；当前切片状态：`DONE-VERIFIED`（Redmi 单设备 Dashboard 两场景截图基线首片）。Connections 已随后完成固定状态截图首片，下一步转入 Profiles 页面级截图/交互矩阵，再处理跨设备基线策略。

### 2026-10-29：Connections 固定状态截图基线首片

- `ConnectionsCard` 提升为可复用的内部页面组件，增加 `connections_card` 和逐行 `connections_row_<index>` 稳定测试标签；正式 MainActivity 的连接数据源和核心生命周期不变。
- 新增 Debug 专用 `ConnectionsTestActivity`，固定停止态空连接和运行态单条活动连接；宿主不读取 Room、网络、VPN、Mihomo 核心或工作区 `1.yaml`。
- 新增 `ConnectionsInstrumentedTest.connectionsScreenshotMatrixMatchesBaseline`，捕获包含卡片和滚动容器的 `connections_page` PNG，并锁定两种状态的 SHA-256。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 `1.0`、深色模式）基线记录在 `docs/visual-baselines/connections-states-redmi-api36.sha256`：停止态空连接 `07736d6f23751a9f8f9b5caf292562aba4b273eb73e0d78c10beaef487e4087f`，运行态单条连接 `52ec482ca5537a64a2091d563f4fb95ed9fea2d61562cca86d4499e0a88e2920`。
- Connections 定向截图测试连续运行两次均通过；本轮未访问两条真实在线路径、未使用 `1.yaml`、未启动 VPN 或 Mihomo 核心。当前切片状态：`DONE-VERIFIED`（Redmi 单设备 Connections 两状态截图基线首片）。下一步转入 Profiles 固定状态页面级截图/交互矩阵，再处理跨设备基线策略。

### 2026-10-29：Profiles 固定状态截图基线首片

- `ProfilesPanel` 增加稳定的 `profiles_page` 页面标签，`ProfileCard` 增加 `profile_card_<id>` 标签；正式 Profiles ViewModel、Room 和文件写回逻辑不变。
- 新增 Debug 专用 `ProfilesTestActivity`，固定“还没有配置”和“选中自定义配置”两种页面状态；自定义 Profile 的更新时间固定为空，避免相对时间进入截图哈希。
- 新增 `ProfilesVisualInstrumentedTest.profilesScreenshotMatrixMatchesBaseline`，捕获包含配置标题、操作入口和 Profile 卡片的 `profiles_page` PNG，并锁定两种状态的 SHA-256。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 `1.0`、深色模式）基线记录在 `docs/visual-baselines/profiles-states-redmi-api36.sha256`：空态 `7be078c77904b1b361743899e9618d75429d9a9650a205667e77e59b640af0d6`，选中自定义配置态 `793a665b414afa6898783a8298ea631da41a79350a34a707880a8ed3355ba55c`。
- Profiles 定向截图测试连续运行两次均通过；本轮未访问两条真实在线路径、未使用 `1.yaml`、未启动 VPN 或 Mihomo 核心。当前切片状态：`DONE-VERIFIED`（Redmi 单设备 Profiles 两状态截图基线首片）。Profiles 创建、编辑、删除组合流程已有真实 MainActivity 证据，下一步转入跨设备条件可用时的截图差异和完整页面交互收敛。

### 2026-10-29：MainActivity Profiles 生命周期组合流程复验

- 使用当前 `kotlin` 分支重新执行 `MainActivityInstrumentedTest.mainActivityCompletesCustomProfileLifecycleThroughRealViewModels`，真实页面完成创建自定义 Profile、编辑 YAML 写入 `mixed-port: 7890`、打开删除确认、删除 Profile 并返回首页。
- 本次验证覆盖真实 Hilt、Room、`ProfilesViewModel`、应用私有 Profile 文件和 Compose 页面状态；测试结束后设备恢复“还没有配置”空态，未留下测试 Profile 或本地文件。
- 定向命令 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityCompletesCustomProfileLifecycleThroughRealViewModels` 通过，1 项失败 0、错误 0、跳过 0。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态保持 `DONE-VERIFIED`。下一步继续处理跨设备截图差异和 Profiles 其他页面级交互边界。

### 2026-10-29：Profiles 选中配置横竖屏视觉矩阵

- `ProfilesVisualInstrumentedTest.profilesSelectedStateOrientationMatrixMatchesBaseline` 使用 Debug 固定宿主把选中自定义 Profile 分别渲染在竖屏和横屏，覆盖 Activity 方向变化后的页面重建和卡片布局。
- 测试在方向切换后重新注入固定状态，避免 Activity 重建把测试数据误当成持久化证据；最终由 `finally` 将设备恢复为竖屏。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 `1.0`、深色模式）基线记录在 `docs/visual-baselines/profiles-orientation-redmi-api36.sha256`：竖屏 `793a665b414afa6898783a8298ea631da41a79350a34a707880a8ed3355ba55c`，横屏 `df2c3e85c4568f0bbca23a968ed5b4f26986209f63d4bf1bc41213bc31a424f6`。
- Profiles 视觉类完整两项测试通过，横竖屏定向测试连续运行两次通过；标准 `test :app:assembleDebug :app:lintDebug` 通过，JVM 220 项通过，Lint 0 errors。本轮未访问两条真实在线路径、未使用 `1.yaml`、未启动 VPN 或 Mihomo 核心。当前切片状态：`DONE-VERIFIED`；下一步继续补 Profiles 大字号和其他页面级交互边界，再处理跨设备基线策略。

### 2026-10-29：Profiles 选中配置大字号视觉矩阵

- `ProfilesTestActivity` 在固定选中自定义配置宿主内通过 `CompositionLocalProvider(LocalDensity)` 切换 1.3x 和 2.0x 字体缩放；仅改变文字缩放，不改变设备密度，避免把设备分辨率差异混入可达性基线。
- 新增 `ProfilesVisualInstrumentedTest.profilesSelectedStateFontScaleMatrixMatchesBaseline`，覆盖 1.3x/2.0x 两个大字号档位；基线写入 `docs/visual-baselines/profiles-font-scale-redmi-api36.sha256`：1.3x `9bf8f24e721f0535eab0a5f8ed153c37967520659a707541868faae4f94e4e5e`，2.0x `33bd90c4ac325d87f7ddd207bfb210972ab16e00b5fe68af608d1511f6fa4f8e`。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 1.0、深色模式）大字号定向测试连续两次通过；Profiles 视觉类三项测试（空态/选中态、横竖屏、大字号）最终通过，测试结果为 3 项失败 0、错误 0、跳过 0。
- `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过；`./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过，JVM 单元测试 220 项失败 0、错误 0，Lint 0 errors；Debug APK SHA-256 为 `b02f587502716f9fba0b18438381058f3ab99e4ef7d6225e7db2feab01211878`，原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`。下一步补跨设备截图矩阵和 Profiles 其他页面级交互边界，再推进 Dashboard/Connections/Observation 的完整页面级视觉收敛。

### 2026-10-29：Dashboard 默认态大字号视觉矩阵

- `DashboardTestActivity` 在固定停止态 Dashboard 宿主内通过 `CompositionLocalProvider(LocalDensity)` 切换 1.3x 和 2.0x 字体缩放；只改变文字缩放，不改变设备密度，保持 Runtime、运行统计、电池优化、Always-on VPN、Access 和诊断卡片的数据固定。
- 新增 `DashboardInstrumentedTest.dashboardDefaultStateFontScaleMatrixMatchesBaseline`，覆盖 1.3x/2.0x 两个大字号档位；基线写入 `docs/visual-baselines/dashboard-font-scale-redmi-api36.sha256`：1.3x `d5ab1377baf43e6fb32e85712e70e45421fc66451ffaadbf20067f59fd2e0ab5`，2.0x `6c826a5ab6f325b3b089664815746980ef2ba30062f29ca9e3e5f067bf128018`。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 1.0、深色模式）大字号定向测试连续两次通过；Dashboard 视觉类两项测试（默认/系统就绪基线、大字号矩阵）最终通过，测试结果为 2 项失败 0、错误 0、跳过 0。
- `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过；`./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过，JVM 单元测试 220 项失败 0、错误 0，Lint 0 errors；Debug APK SHA-256 为 `14c2936bab4a0c80d6b0959cb17481775e4ddcb7ac84ad50e9545a3a79a04f80`，原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`。下一步补跨设备截图矩阵和 Dashboard/Profiles 其他页面级交互边界，再推进 Connections/Observation 的完整页面级视觉收敛。

### 2026-10-29：Connections 运行态大字号视觉矩阵

- `ConnectionsTestActivity` 在固定单条活动连接宿主内通过 `CompositionLocalProvider(LocalDensity)` 切换 1.3x 和 2.0x 字体缩放；连接主机名、进程路径、地址和规则数据保持固定，只验证可达性档位下的换行与滚动。
- 新增 `ConnectionsInstrumentedTest.connectionsRunningStateFontScaleMatrixMatchesBaseline`，覆盖 1.3x/2.0x 两个大字号档位；基线写入 `docs/visual-baselines/connections-font-scale-redmi-api36.sha256`：1.3x `978584c42dd91dcf17266075137c0f759630207c1faeae1e4776da8f01379c66`，2.0x `74f7977c07aa092c18e1a61db68232bdc0b9934f1d6b9b5486e147da5ac0659e`。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 1.0、深色模式）大字号定向测试连续两次通过；Connections 视觉类两项测试（停止态/运行态基线、大字号矩阵）最终通过，测试结果为 2 项失败 0、错误 0、跳过 0。
- `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64`：通过；`./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`：通过，JVM 单元测试 220 项失败 0、错误 0，Lint 0 errors；Debug APK SHA-256 为 `af040928921d03541b2a92ee4ab4a973feeb1f3d0dac80c45c2386d1245e3233`，原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`。下一步补跨设备截图矩阵和 Dashboard/Profiles/Connections 其他页面级交互边界，再推进 Observation 的完整页面级视觉收敛。

### 2026-10-29：Observation Requests/DNS/Logs 运行态大字号视觉矩阵

- `ObservationDetailsInstrumentedTest` 新增 `observationRunningStatesFontScaleMatrixMatchesBaseline`，固定运行态 Requests、DNS、Logs 各一条记录，在同一 Compose 宿主内切换 1.3x 和 2.0x 字体缩放；只改变字体缩放，不改变设备密度、Room、网络、VPN、Mihomo 核心或工作区 `1.yaml`。
- 六个 PNG 基线写入 `docs/visual-baselines/observation-page-font-scale-redmi-api36.sha256`：1.3x Requests `ded6474b0ca13e5af4d2b81f59434dd5f1425382ebbafd4dfaf2fe8de82ab373`、DNS `cabbe76d6f3400a7b48f11c4d2503b60e87599ffb777a1c6870c9eabf8ae81e0`、Logs `a1c76e593d770e2e98d1ed313fb3e37c0c48c11fe5a8877037d0d82348feba39`；2.0x Requests `d2c796b148a00180ccb35f19b752387e8c2b28096d4de2961ae8204f9eed576b`、DNS `6da0e5f4c9cb5add1227b90f3373f5dde5b57b7417a8f1743589fce58855e25f`、Logs `503bf138c393346bccf59833a1a2905a59f1605f651c83426d0133b9d025d692`。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 1.0、深色模式）定向测试通过；命令为 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.ObservationDetailsInstrumentedTest#observationRunningStatesFontScaleMatrixMatchesBaseline`，结果为 1 项失败 0、错误 0、跳过 0。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64` 通过；标准 `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过，220 项 JVM 单元测试失败 0、错误 0、跳过 0，Lint 0 errors、36 warnings、5 hints；Debug APK SHA-256 为 `af040928921d03541b2a92ee4ab4a973feeb1f3d0dac80c45c2386d1245e3233`，APK 原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`。下一步补跨设备截图矩阵，再扩展 Observation 其他页面级交互边界和完整页面级端到端覆盖。

### 2026-10-29：Observation 页面清空与导出组件交互回归

- `ObservationDetailsInstrumentedTest.observationPageClearsDnsAndLogsIndependentlyAndExportsLogs` 固定运行态 Requests、DNS、Logs 数据，验证 DNS 清空只清除 DNS、日志清空只清除日志、日志导出回调收到原始日志列表，Requests 记录在两次清理后仍保持可见。
- 定向真机命令 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.ObservationDetailsInstrumentedTest#observationPageClearsDnsAndLogsIndependentlyAndExportsLogs` 通过，1 项失败 0、错误 0、跳过 0；完整 `ObservationDetailsInstrumentedTest` 通过，4 项失败 0、错误 0、跳过 0。
- `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon -Ptarget-platform=android-arm64` 和标准 `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 均通过；220 项 JVM 单元测试失败 0、错误 0，Lint 0 errors、36 warnings、5 hints；Debug APK SHA-256 为 `af040928921d03541b2a92ee4ab4a973feeb1f3d0dac80c45c2386d1245e3233`，原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；该切片状态为 `DONE-VERIFIED`（Observation 页面组件交互边界）。真实 `MainActivity` 清空/导出端到端已在后续真机切片补齐，当前转入跨设备截图矩阵和其他页面级交互。

### 2026-10-29：MainActivity 观测页清空与日志导出真机验收

- 设备为 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）；仅使用工作区根目录 `/Users/long/Documents/CodexProjects/clash-kotlin/1.yaml` 作为本地 Profile 导入，设备文件哈希与主机一致（`bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`）。
- 真实 `MainActivity` 启动本地 Profile 后，观测页 Requests/DNS/Logs 分别出现 266、232、271 条记录；本轮受控请求只使用当前 `AGENTS.md` 允许的两条路径，两个请求均在 25 秒超时并返回 HTTP `000`，未把订阅正文写入路线图或导出审计。
- 在真实观测页点击 DNS“清空”后显示 `DNS 查询（0）`；点击日志“导出”后通过 Android `DocumentsUI` 保存 `kclash-logs-1793232270104.txt`，设备文件大小 30891 bytes、273 行；随后点击日志“清空”显示 `核心日志（0）`，切回 Requests 仍显示 `请求记录（266）`。
- 导出文件设备侧审计未命中授权路径 token、`token`/`password`/`secret`/`authorization`/`uuid` 关键字或原始查询参数；导出文件仅保留脱敏后的日志表格内容。
- 通过应用内停止核心后，`ProxyService`、`VpnService`、`tun0` 和 `198.18.0.0/30` 路由均清理；随后删除测试 Profile，首页恢复“还没有配置”，导出文件未包含授权 token。
- 本轮没有改动生产代码或测试代码，只更新路线图证据；当前切片状态：`DONE-VERIFIED`（真实 MainActivity 观测清空/导出端到端）。下一步补跨设备截图矩阵和其他页面级交互覆盖；本地设备验收仍只使用 `1.yaml`，在线验收仍只允许当前 `AGENTS.md` 的两条路径。

### 2026-10-29：Dashboard 运行态视觉与交互首片

- `DashboardTestActivity` 新增确定性的 `RUNNING` 场景，固定上传/下载速率、累计流量、连接数、HTTP 200 探测结果、代理链、规则、延迟以及 Requests/DNS/Logs 各一条诊断记录，并复用正式 Dashboard 卡片组合。
- `DashboardInstrumentedTest` 新增运行态统计与诊断证据断言、运行态整页截图基线和运行态 1.3x/2.0x 大字号矩阵；完整类测试共 5 项，失败 0、错误 0、跳过 0。
- Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`，系统字体 `1.0`、深色模式）运行态整页基线为 `0d27198ca05862b09b06a5d698eccf73b08f4adfb8de85a7907da165d8ada664`；1.3x 为 `4c72f9ae7da4d2743f38bf8b20e4076d339903a8ce17e278534ab3b972ff23e7`，2.0x 为 `c2b7d4d998ea9f79eda98aae598c8ed10f55a98e4de3b4bd37ce9b3cb6933d63`。
- 已通过 `ANDROID_SERIAL=wsvwypiz7xwslvl7 ./gradlew :app:connectedDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64 -Pandroid.testInstrumentationRunnerArguments.class=com.lonnnnnng.flclash.ui.DashboardInstrumentedTest`，以及标准 `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`；JVM 单元测试 220 项通过、Lint 0 errors，Debug APK SHA-256 为 `c2b8e4eb3341f8e0ad6bccaa1aa89d392b3678956f539c0d442d949fdf943355`，原生库仅包含 `lib/arm64-v8a/`。
- 本轮固定宿主不访问两条真实在线路径、工作区根目录 `1.yaml`、Room、系统设置、已安装应用、网络、VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`（Redmi 单设备 Dashboard 运行态统计/诊断交互与视觉首片）。下一步补跨设备截图矩阵和 Dashboard/Profiles/Connections/Observation 其他页面级交互边界。

### 2026-10-29：Connections 连接行详情交互首片

- `ConnectionsCard` 的运行态连接摘要行新增点击语义；点击后打开 Material 3 `ConnectionDetailDialog`，按“基本信息 / 地址 / 代理”展示创建时间、网络类型、进程、规则、上传/下载、主机、来源/目标、远端目标、进程路径、UID、代理链和特殊代理字段。
- 详情只由 `ConnectionsCard` 内部选择状态驱动；关闭对话框后连接列表、`活动连接（1）` 计数和摘要行仍保持可见。`RequestsCard` 继续以只读方式复用摘要渲染，不新增连接操作或核心 API。
- `ConnectionsInstrumentedTest.connectionsRowOpensDetailsAndClosesWithoutChangingList` 覆盖 Redmi 真机点击连接行、部分详情字段可见、关闭和列表恢复；完整类 3 项通过，失败 0、错误 0、跳过 0。原测试对代理链和规则仅检查语义节点存在，不能作为滚动可达性证据，2026-10-31 切片补齐。设备为 Redmi Note 8 Pro（Android 16/API 36，serial `wsvwypiz7xwslvl7`）。
- `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过，JVM 单元测试 220 项、Lint 0 errors；Debug APK SHA-256 为 `39d51b818f5ece83ffce0cb8ee822cf62652b5ef3c980be7bb749dfc576400f3`，原生库仅包含 `lib/arm64-v8a/`。
- 本轮未访问两条真实在线路径、未使用工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心；当前切片状态：`DONE-VERIFIED`（Redmi 单设备 Connections 连接行详情页面级交互）。下一步补连接详情独立截图基线、跨设备截图矩阵及其他页面级交互边界。

### 2026-10-31：Connections 详情快照恢复与可达性收敛

- 连接详情改用显式 `Bundle` Saver 保存单条点击快照；连接从轮询列表消失、核心停止后保留阅读内容，Activity 重建也无需重新向核心查找已结束连接。
- 摘要与详情代理链改为反序展示，与 Flutter 的 `chains.reversed` 一致；共享摘要使 Requests 的三份运行态截图也需要复核。
- 新增长主机、长进程路径、2.0x 字号横竖屏、连接消失/核心停止/Activity 重建和关闭后重建的回归；字段断言限定详情子树，逐项滚动后验证可见。
- 最终在 Redmi Note 8 Pro（serial `wsvwypiz7xwslvl7`，Android 16/API 36）运行完整 `ConnectionsInstrumentedTest` 6 项和 `ObservationDetailsInstrumentedTest` 4 项，输出 `OK (10 tests)`，失败 0、错误 0、跳过 0；真实系统字体 2.0x 横竖屏测试结束后已恢复字号到原值 `1.0`。四份详情截图和六份受影响摘要截图均已人工复核。
- 复现：先构建并用显式 serial 安装 Debug 和 AndroidTest APK，再执行 `adb -s wsvwypiz7xwslvl7 shell am instrument -w -r -e class com.lonnnnnng.flclash.ui.ConnectionsInstrumentedTest,com.lonnnnnng.flclash.ui.ObservationDetailsInstrumentedTest com.lonnnnnng.flclash.test/androidx.test.runner.AndroidJUnitRunner`；需要采集实际 PNG 时在 runner 名称前加入 `-e kclashCapturePng true`，图片通过 `kclash_png_name` / `kclash_png_base64` 状态传回。最终原始日志：`/tmp/kclash-connections-final-20261031.log`，采集记录：`/tmp/kclash-connections-png-channel-20261031.log`、`/tmp/kclash-connections-system-font-20261031.log`（本机临时证据，不进入仓库）。
- 验收修正：最初只注入页面 `LocalDensity` 的测试不能证明独立 Dialog 窗口字号，已改为真实系统字体并在 `finally` 恢复；`run-as` 的 SELinux 拒绝和外部目录 `ENOENT` 也已保留在本机日志，最终使用可选 instrumentation 图片通道，不依赖文件导出权限。首次截图差异经看图确认后仅更新受代理链反序影响的六份摘要基线，DNS/Logs 基线保持一致。
- 标准 `./gradlew test :app:assembleDebug :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过，JVM 220 项通过；最终 AndroidTest 构建和 Lint 再次通过（0 Error、36 Warning、5 Hint）。Debug APK SHA-256：`90116692e1ab704c1f20a04bf7fe1eb62f9892940d0d227c7582173fe2ecb260`，原生库仅 `lib/arm64-v8a/`。
- 当前切片状态：`DONE-VERIFIED`（Redmi 单设备详情交互、点击快照恢复和详情截图矩阵）；未访问两条真实在线路径、未读取 `1.yaml`、未启动 VPN/Mihomo。系统字号是本轮唯一调整后恢复的全局设备设置。
- 边界：仅固定宿主组件验收，不替代真实连接详情 E2E；时间本地化、进程/UID 格式、GeoIP/ASN/DNS 元数据、Flutter 底部面板外形、浅色主题和跨设备验收仍待补齐。

### 2026-10-31：Connections 元数据、格式和底部面板对齐

- 连接轮询与 request 事件共用元数据解析，新增源/目标 GeoIP、源/目标 ASN 和 DNS 模式，并加入单条详情快照保存恢复。
- 详情改为 Material 3 底部面板，支持顶栏关闭、返回/下滑关闭；文本行提供复制回调及面板内成功/失败反馈，代理链以不可复制标签反序展示。
- 创建时间按设备本地时区显示到秒，兼容 Mihomo 的 RFC3339Nano；进程改为 `process(uid)`，UID=0 不额外显示；空元数据隐藏。字段顺序按 Flutter 分组，移除参考详情没有的独立进程路径和 UID 行。
- 标准 `./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过，226 项 JVM 测试失败 0、错误 0、跳过 0；最后的验收代码修正又通过 AndroidTest 构建与 Lint，Lint 0 Error、37 Warning、5 Hint。Debug APK SHA-256 为 `284f33e9ff536a4175fd516ae48bffff976f177ca5fa23676e2725e5123cc173`，原生库仅 `lib/arm64-v8a/`。
- Redmi Note 8 Pro（serial `wsvwypiz7xwslvl7`，Android 16/API 36，深色模式）完整 Connections 8 项 + Observation 4 项结果为 `OK (12 tests)`，失败 0、错误 0、跳过 0。四份展开态详情内容 PNG 人工复核后更新哈希基线，同时采集整屏图检查底部安全区；摘要及 Observation 基线保持一致。系统字体测试结束恢复 `1.0` 和竖屏。最终日志 `/tmp/kclash-sheet-final-device-20261031.log`，截图采集 `/tmp/kclash-sheet-fifth-20261031.log` 和 `/tmp/kclash-sheet-fifth-visual-20261031/`（本机临时证据）。
- 新增显式验收脚本 `tool/android_connection_acceptance.py`，固定读取外层 `AGENTS.md` 两条清单及原始 `1.yaml`，必须提供 serial 并核对设备与已安装双 APK 哈希；测试默认跳过，只有脚本准备临时输入且显式启用时才联网。真实输入不进入命令参数或仓库，日志遮蔽授权链接标识。
- 真实核心命令：先显式 serial 安装本轮双 APK，再运行 `python3 tool/android_connection_acceptance.py --serial wsvwypiz7xwslvl7 --target-index 2 --log /tmp/kclash-live-connection-fourth-20261031.log`。结果 `OK (1 test)` 及 `real-core-detail-copy-recreate-stop-passed`：原始配置 SHA-256 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`，使用其中声明的 `7897` mixed-port，通过主机/源端口/目标端口识别本次真实 SOCKS 连接；真实 MainActivity 打开详情、复制主机、停止核心、重建后主机及 DNS 模式仍可读，关闭后详情消失。
- 真实元数据为 DNS `normal`、GeoIP 数量 `0`、ASN 空；非空字段显示与保存恢复由固定夹具及解析测试验证，不能据此声称真实 GeoIP/ASN 非空或 HTTP 请求成功。本轮不读取响应状态/正文，不跟随重定向；VPN 已授权，未新增 VPN 授权。
- 清理按独立步骤执行，只有全部成功后才发通过标记：删除本次唯一 ID 的 Profile、恢复原选择/剪贴板和原核心暂存配置。结束后服务为空、`tun0` 不存在、VPN 路由消失、Connectivity 无 TUN 记录，固定设备临时输入也已删除。通知权限若由脚本临时授予则在结束后撤销；成功 Run 保持既有通知权限。
- 失败留痕：首轮真实验收在通知权限前置检查失败；第二轮发现验收代码写死 `7890`，原配置及核心实际监听 `7897`；第三轮“连接”文本同时匹配统计项和导航项导致选择器失败。均保留日志，分别修正前置权限恢复、从原配置解析端口和限定可点击导航语义，第四轮通过。`ip link show tun0` 被系统拒绝后，改用 `/sys/class/net/tun0`、路由与 Connectivity 交叉核验，没有将权限拒绝当作清理成功。
- 当前切片状态：`DONE-VERIFIED`（Redmi 单设备元数据、格式、复制、面板基础交互和真实核心详情 E2E）；完整 UI/UX 阶段继续 `PARTIAL`。Material 默认半展开点与 Flutter `9/16`、`0.9` 两档仍有差异，浅色/跨设备/缩减动画及全页面 parity 尚未完成。

### 2026-10-31：Connections 面板进出场与侧栏动效对齐

- `ConnectionModalSheet` 的非拖拽入场和退出保持 Dialog 挂载，底部面板、遮罩和面板位移共用同一临界弹簧进度；退出完成后才移除快照，返回、遮罩和顶栏关闭统一走同一关闭请求。关闭决定用 `rememberSaveable` 保留，退出中旋转或 Activity 重建不会重新入场、丢失回调或恢复已关闭详情。
- 横屏侧栏按 Flutter `side_sheet.dart` 的标准减速曲线实现 300ms 入场、200ms 退场；新增受控 Compose 时钟用例断言底部面板中间帧的真实位移/遮罩变化、侧栏进出方向、重复关闭和退出中旋转。
- `ConnectionModalSheet` 使用四次超椭圆 20 段采样近似 Flutter `ClipRSuperellipse`；形状基线写入 `docs/visual-baselines/connections-sheet-shape-redmi-api36.sha256`，当前仅证明 KClash 自身稳定渲染，不宣称与 Flutter 引擎逐像素相同。
- 标准命令 `./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；工程 231 项 JVM 单元测试失败 0、错误 0、跳过 0，Lint 0 Error。Debug APK SHA-256 `cfd7a2790ea35662ca8e5481b95d8e31b62b6c829158257fedfd1cfe35d6bca2`，AndroidTest APK SHA-256 `7cb13db082156ea64ba0b223120328b37e24cfa49ed1f7d2d1db0f223cd22da6`，APK 内原生库仅 `lib/arm64-v8a/`。
- Redmi Note 8 Pro（serial `wsvwypiz7xwslvl7`，Android 16/API 36）安装最新双 APK 后，`ConnectionsInstrumentedTest` 15 项 + `ObservationDetailsInstrumentedTest` 4 项输出 `OK (19 tests)`；日志 `/tmp/kclash-ui-motion-final-device-sixth-20261031.log`。最终设备设置为竖屏、系统字号 `1.0`、动画倍率 `1.0`、物理尺寸 `1080x2340`；KClash 前台服务、`tun0` 和 `198.18.0.0/30` 路由均无残留。
- 本轮未访问两条真实在线路径、未读取或修改工作区根目录 `1.yaml`，未启动 Mihomo 核心；形状和动效只使用固定 Debug 宿主。当前切片状态：`DONE-VERIFIED`（Redmi 单设备面板进出场、侧栏动效、退出重建安全和截图基线）；完整 UI/UX 阶段继续 `PARTIAL`。快速拖甩的初始速度/轻微过冲、越界回弹、超椭圆精确曲率、短内容 fitsContent、键盘联动、跨设备和全页面 parity 仍待后续。

### 2026-10-31：Connections 双停靠点、横屏侧栏与浅色验收

- `ConnectionModalSheet` 使用实际 Dialog 顶部/左右安全区后的窗口高度计算 `9/16`、`0.9` 两档，底部导航区计入面板高度并由内容避让；宽度超过 600dp 使用右侧栏，宽为 40% 并限制在 360–560dp。上游参考为 `main` 的 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`，对应 `lib/ui/snap_sheet.dart` 和手机/侧栏分支；高度不再依赖 `screenHeightDp`。
- 拖柄提供 48dp 点击区域、拖动，支持遮罩、返回和顶栏关闭；按 99ms 松手速度投影选最近档位，展开态可跨 Activity 重建保留。正文从中部起手下滑回顶时，同次手势的剩余位移和惯性都不会收起面板，下一次从顶部起手仍能收起。原点击快照和 `CoreRuntimeManager` 生命周期保持不变。
- 新增三项几何/窗口变化/速度投影 JVM 测试、五项真机用例（两档/手势/遮罩、正文中部起手、浅色横竖屏、小窗口、禁用动画）。`ConnectionsInstrumentedTest` 13 项 + `ObservationDetailsInstrumentedTest` 4 项输出 `OK (17 tests)`，失败 0、错误 0、跳过 0。完整日志 `/tmp/kclash-detents-final-device-20261031.log`；十份详情内容 PNG 和整屏图已人工核对，深色四份更新至原详情基线，浅色六份新增至 `docs/visual-baselines/connections-detail-light-redmi-api36.sha256`，摘要与 Observation 基线保持一致。
- 小窗口使用同一 Redmi 的实际 `wm size 990x1760`（density 440，即 360×640dp）；这是同设备窗口适配证据，不代表第二台设备。浅色通过 Debug 宿主注入，不修改全局主题；系统字号、窗口尺寸、动画倍率均在 `finally` 恢复。结束核对为字体 `1.0`、动画倍率 `1.0`、原始 `1080x2340`、竖屏；浅色可读性为可视核验，没有宣称量化对比度。
- 标准命令 `./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过，229 项 JVM 失败 0、错误 0、跳过 0，Lint 0 Error、36 Warning、5 Hint；日志 `/tmp/kclash-detents-final-checks-20261031.log`。Debug APK SHA-256 `c5a10b21b7270ae0bdf5ba556123e9ad73ac9df0d34e72700090fec30aeee5de`，原生库仅 `lib/arm64-v8a/`。
- 真实核心复验：安装同一对最新 APK 后执行 `python3 tool/android_connection_acceptance.py --serial wsvwypiz7xwslvl7 --target-index 2 --log /tmp/kclash-detents-live-final-20261031.log`，结果 `OK (1 test)` 和 `real-core-detail-copy-recreate-stop-passed`。仅使用外层原始 `1.yaml` 与白名单第 2 条路径；双 APK 哈希已核对，原始配置 SHA-256 保持 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。真实 DNS `normal`、GeoIP 数量 0、ASN 空，没有新增 VPN 授权，没有验证 HTTP 响应状态/正文。结束后 KClash 服务为空、`tun0` 不存在、VPN 路由和 Connectivity TUN 记录消失、临时输入已删除；测试 Profile、原选择、剪贴板及核心暂存配置由验收用例恢复。
- 失败留痕：首轮 12 项中四项为待复核截图差异，另两项拖动归档未结束；延长到条件等待仍超时，排除了单纯读取过早。临时诊断确认 Foundation 保留初始 fling 对象，旧几何目标 `2119px` 与当前隐藏锚点 `2262px` 不一致；最终松手读取当前锚点，并通过当前 `ScrollScope` 逐帧归档，规避嵌套拖动锁。四项定向交互复验 `OK (4 tests)` 后，完整 17 项和真实核心 1 项均通过。失败/诊断日志保留在 `/tmp/kclash-detents-gestures-second-20261031.log`、`/tmp/kclash-detents-debug-fourth-20261031.log`，临时 Log 代码已移除。截图提取曾因字段顺序错配，改为按完整 instrumentation 状态块配对后，最终十份 PNG 的实际 SHA-256 均与基线一致；证据目录 `/tmp/kclash-detents-final-png-20261031/`。
- 当前切片状态：`DONE-VERIFIED`（Redmi 单设备两档几何、侧栏、浅色/小窗口/禁用动画和真实详情回归）。完整 UI/UX 阶段继续 `PARTIAL`；下一步对齐 superellipse 连续圆角、弹簧/越界回弹和入场退场曲线，再扩展其他页面浅色/响应式与跨设备矩阵。短内容 fitsContent、键盘联动和全页面 parity 尚未完成，当前详情没有输入框。

### 2026-10-31：Profiles 页面视觉首片

- 顶栏操作改为图标入口；没有 URL Profile 时不展示“同步全部”，有订阅时可批量同步 URL Profile 并保留排序/工具入口。脚本、WebDAV、备份和恢复仍可从工具菜单访问。
- 空态改为居中插画式图形与添加入口；卡片移除左侧 RadioButton 和常驻多行文字按钮，改用紧凑标题/副标题、选中标识和溢出菜单。远程订阅卡显示已用/总量与到期日；编辑、单项同步、自动更新、重命名、高级 YAML 分区、脚本绑定和删除均继续可从菜单进入。
- 固定宿主提供空态、选中自定义配置、远程订阅用量三态截图。Redmi Note 8 Pro（serial `wsvwypiz7xwslvl7`，Android 16/API 36，竖屏、系统字号 `1.0`、深色模式）基线见 `docs/visual-baselines/profiles-states-redmi-api36.sha256`：空态 `c648aa2a728eef3d5b3b5ce493e3725b716e8914f653c9366a93f130c56dfff9`、选中态 `ae18793505a09c77a712820d68d4d1f5a5ceedd58337a7cb4cef9120d6cc409b`、订阅用量 `fa4cce6aae89bae203c5123d48aebc2b6bde23afda7f6f0e40000df2a1c09d56`；最终源码复验 `profilesScreenshotMatrixMatchesBaseline` 通过。
- Profiles 定向真机回归共 29 项通过：`ProfilesVisualInstrumentedTest` 1 项、`ProfilesPanelInstrumentedTest` 10 项、`ProfileUiInstrumentedTest` 11 项、真实 `MainActivityInstrumentedTest` 配置生命周期和六类高级编辑 7 项。覆盖菜单操作、远程批量同步只分发 URL Profile、创建/编辑/删除和高级分区回写；没有触发订阅网络、VPN 或 Mihomo 核心。
- 本轮标准 `./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过：231 项 JVM 测试失败 0、错误 0、跳过 0；Lint 0 Error、36 Warning、3 Hint。Debug APK SHA-256 为 `371d11b8b4b0be6fa41887bb429d28af34b61087458c835140325db2a895e147`，AndroidTest APK SHA-256 为 `4ab90d6a5ee4a21bb983455c043fea20f2a0d2b9f29a2cd7302f55ac2fc39bbf`；APK 内原生库仅有 `lib/arm64-v8a/`。
- 验收前后设备均为竖屏、系统字号 `1.0`。未访问两条真实在线路径、未读取或修改工作区根目录 `1.yaml`、未启动 VPN 或 Mihomo 核心。旧横屏/大字号矩阵仅保留为历史，不再执行；Profiles 页面视觉与交互首片为 `DONE-VERIFIED`，完整 UI/UX parity 仍为 `PARTIAL`。下一步继续逐页收敛 Flutter 页面差异，并补充竖屏默认字号范围内的页面回归。

### 2026-10-31：Proxy 节点卡片视觉首片

- 代理组选择弹层中的节点从 `RadioButton + 文本行` 收敛为独立节点卡片：20dp 普通圆角、两行名称、22dp 当前使用/优选徽标，以及 72×48dp 独立测速触控区；保留原有 `onSelect`、搜索、单节点测速和 computed 组自动选择回调。为保留 Android 触控高度，卡片最小 96dp；Flutter 的超椭圆、网格与 86dp standard 几何尚未完整对齐。
- 延迟区域显示闪电入口、测速中进度、无结果/失败/超时和毫秒值；正数 <600ms 使用成功色，>=600ms 使用警告色，<=0 使用错误色（Flutter 对 0 的文案为失败但颜色为 success，本轮统一错误语义）。主题色由 Flutter green/orange HCT 亮色 tone40 / 暗色 tone80 离线计算。叶子协议类型尚未进入 Kotlin Bridge，仅嵌套组显示自身真实类型，不用父组类型替代。
- computed 当前使用标记读取有效核心 `now`，优选读取保存的 `selectedMap`；两者可分别显示。列表改为 `LazyColumn`，搜索和关闭位于滚动区外，内容区域最高 420dp。
- 最终 Redmi Note 8 Pro（serial `wsvwypiz7xwslvl7`，Android 16/API 36）精确筛选 8 项用例，结果 `OK (8 tests)`：Activity 卡片/视觉/触摸/测速中 4 项、真实 ViewModel 面板保存/自动恢复/失败回滚/嵌套摘要 3 项、computed 搜索 1 项。实际触摸测速按钮后选择和 Profile 映射保持不变；测速中禁止重复测速，但卡片选择与关闭仍可用。滚动到列表末项后超时、失败和 Fallback 类型均可见。
- 固定场景 6 份 PNG 经人工复核并更新至 `docs/visual-baselines/proxy-groups-states-redmi-api36.sha256`；默认组卡片哈希保持不变。结果图覆盖深浅色、100ms 成功、600ms 警告、超时/失败、当前出口与未采用优选及列表首尾。截图中的成功/警告文字对比度：浅色均约 4.99:1、深色约 7.19:1/7.21:1；仅验证这两类文字，不代替全页量化审计。基线证明 KClash 自身渲染稳定，尚非与 Flutter 截图逐像素相同。
- 标准 `./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；Debug JVM 报告 231 项，失败/错误/跳过均为 0。最后只变更测试断言后再次通过 AndroidTest 构建和 Lint（0 Error、36 Warning、3 Hint）。Debug APK SHA-256 `aeb3227f2b7bc311a46aca7ba2de813e1901b140b7a0a0b0af1f7360899665a9`，AndroidTest APK SHA-256 `aa7b2ff3d4cc284d3a69edc9fe9d66f0d65fe17e35fc66abec300ebccdd3fe18`；原生库仅 `lib/arm64-v8a/`。
- 复现：显式 serial 安装本轮双 APK，再使用 `am instrument -w -r -e class` 的精确 `Class#method` 列表（4 项 ProxyGroupsActivityInstrumentedTest、3 项 ProxyGroupsViewModelPanelInstrumentedTest 和 ProfileUiInstrumentedTest#computedProxyGroupShowsAutomaticSelectionAction）。用例名称及最终结果保存在 `/tmp/kclash-proxy-final-device-20261031.log`；构建日志 `/tmp/kclash-proxy-final-checks-second-20261031.log`、最后测试构建/Lint 日志 `/tmp/kclash-proxy-final-test-build-20261031.log`，PNG `/tmp/kclash-proxy-final-visual-20261031/`（本机临时证据，不进入仓库）。
- 失败记录：首轮为旧截图哈希失配；复核纠正了父组类型误标在叶子上的问题、空文本语义、缺失触摸 click 导入和旧提示语。加入 LazyColumn 后定向用例必须先滚动到目标项，并在原始语义树中检查嵌套测速控件；两项修正复验 `OK (2 tests)`，最终 8 项均通过。一次 ADB 结果流在第 2 项中断，设备日志实际记录 8 项/3 项失败，未用该不完整输出充当通过证据。
- 当前切片 `DONE-VERIFIED`（Redmi 单设备竖屏、系统默认字号；深浅主题为宿主注入，不改系统主题）。结束时仍为竖屏、字号 1.0，KClash 无运行服务、tun0 不存在。未访问两条在线路径、未读取或修改 `1.yaml`、未启动 VPN/Mihomo，未执行横屏/大字号用例。完整代理页面网格、叶子协议类型、卡片菜单、超椭圆精确曲率及其他页面仍待后续，整体 UI/UX 保持 `PARTIAL`。

### 2026-10-31：Connections / Requests / DNS / Logs 记录卡片视觉首片

- 新增共用 `RecordRows.kt`：连接、DNS 和日志记录采用独立卡片，统一 Flutter 参考的头部/正文/辅助文字层级、间距与状态标签。Connections 显示目标端口、实际目标 IP、规则、反序代理链、进程/UID、来源地址和明确标为累计的上传/下载；DNS 显示时间、类型、真实毫秒耗时、答案、缓存/发起方/上游及失败响应码；Logs 按级别显示中性/警告/错误标签。
- Connections 与 Observation 运行列表移除重复的大外层卡片，停止和空状态保留提示卡；深浅色页面背景及非空列表正文对比已修正。卡片继续保留 Connections 点击详情和触摸反馈。真实数据模型不提供单连接瞬时速率，因此页面不显示虚构速率；日志正文尚未由 Bridge 结构化。
- 新增 1 项 `RecordCardsInstrumentedTest#recordCardsMatchDarkAndLightBaselines`，固定覆盖普通/长/缺字段连接、DNS 缓存/NXDOMAIN/超时、info/warning/error 及空正文；六份记录 PNG 深浅色基线位于 `docs/visual-baselines/record-cards-states-redmi-api36.sha256`。最终截图日志 `/tmp/kclash-record-final-corrected-device-20261031.log`，PNG `/tmp/kclash-record-final-visual-20261031/`；19 张有效 PNG 中，17 张（记录卡片 6 张、Connections/Observation 页面状态 8 张、详情内容/形状 3 张）与仓库基线 SHA-256 一致；另 2 张详情整屏图已人工检查安全区，无哈希基线。截图是固定夹具下 KClash 自身渲染稳定性证据，不代表与 Flutter 引擎逐像素相同。
- Redmi Note 8 Pro（serial `wsvwypiz7xwslvl7`，Android 16/API 36）最终精确执行 13 项：RecordCards 1、Connections 6、ObservationDetails 3、MainActivity 3，结果 `OK (13 tests)`；覆盖深浅卡片矩阵、页面状态、真实详情触摸与恢复、筛选、独立清空/日志导出和跨目的地导航。最终设备为竖屏 `1080x2340`、系统字号 `1.0`，自动旋转已恢复到原值 `1`，用户旋转值 `0`。ADB `ip link` 可读列表无 `tun0`，无 KClash 主进程；Android 拒绝读取 `dumpsys activity services`，故本轮不声称已通过该命令核实服务清空。
- 标准 `./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；JVM 231 项失败/错误/跳过均为 0，Lint 0 Error、36 Warning、3 Hint。Debug APK SHA-256 `3f3477e6cdb17591b6d620d7bc5aaab3ece2f62cca4590e0af31457a36a7dd8e`，AndroidTest APK SHA-256 `e262359d2e71bf0da85a56879dee95892f405df2228211b88a264566dce215b8`；原生库仅 `lib/arm64-v8a/`。构建日志 `/tmp/kclash-record-final-checks-20261031.log`。
- 验收只使用固定测试宿主，没有访问在线白名单路径、读取或修改工作区根目录 `1.yaml`、启动 VPN 或 Mihomo；本轮没有运行横屏或大字号用例。一次自动旋转导致的意外横屏采集已弃用、未进入基线，并新增采集前竖屏/默认字号断言；早期一次 MainActivity 测试类全限定名拼写错误未算通过，改正后 13 项完整重跑通过。当前记录卡片视觉首片 `DONE-VERIFIED`；Requests/DNS 详情差异与关键词交互、结构化日志、时间相对化、Flutter 超椭圆精确曲率、完整页面工具栏/主题及跨设备 parity 仍待后续，整个 UI/UX 阶段保持 `PARTIAL`。

### 2026-11-01：Requests / DNS 单条详情与关键词筛选首片

- 以本地 Flutter `main:lib/views/dns_queries.dart` 的基本信息/错误/答案分组，以及 `main:lib/models/common.dart` 的关键词语义为参考。Requests 行点击复用连接详情面板和点击快照 Saver；DNS 行点击打开共用两档面板，隐藏空字段、保留完整 IPv6，支持字段整行复制及成功/失败反馈。关闭详情后仍保留原列表搜索与关键词。
- Requests 代理链与 DNS 类型/发起方/缓存/非 NOERROR 响应码标签可点击组合筛选；关键词采用区分大小写的精确 AND，自由搜索按空白分词、忽略大小写并采用 AND，两类条件可叠加。Requests 搜索覆盖真实来源/目标地址、进程路径、GeoIP、ASN、特殊规则和代理链；对应边界新增 2 项 JVM 测试。
- 新增 `DnsDetailsInstrumentedTest#dnsDetailsMatchDarkAndLightBaselinesAndCopyValues`，覆盖多答案/错误 × 深浅色 × 半展开/展开八种状态，并检查空字段隐藏、完整 IPv6、复制成功/失败。八份基线写入 `docs/visual-baselines/dns-details-states-redmi-api36.sha256`；浅色由固定宿主注入，不修改系统主题。截图证明 KClash 自身渲染稳定，尚不代表与 Flutter 引擎逐像素相同。
- Redmi Note 8 Pro（serial `wsvwypiz7xwslvl7`，Android 16/API 36）显式指定设备、按精确 `Class#method` 执行 9 项：ObservationDetails 4、DNS 详情 1、RecordCards 1、Connections 3，结果 `OK (9 tests)`；覆盖详情打开/关闭、关键词与搜索、日志级别筛选、独立清空/导出、深浅卡片及既有连接详情。最终日志 `/tmp/kclash-observation-details-stable-final-device-20261031.log`。提取 25 张 PNG，其中有仓库基线的 23 张（DNS 8、记录卡片 6、观测页 6、连接详情 3）全部匹配；另 2 张为无哈希基线的整屏辅助图，本轮不将其计入匹配或人工复核数量。PNG 位于 `/tmp/kclash-observation-details-stable-final-visual-20261031/`，结果报告 `/tmp/kclash-observation-details-stable-final-visual-report-20261031.txt`；DNS 八状态已人工检查。
- 标准 `./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；JVM Debug 报告 233 项，失败/错误/跳过均为 0，Lint 0 Error、36 Warning、3 Hint。最后仅修改测试截图等待与基线后，`:app:assembleDebugAndroidTest` 再次通过并安装复验。Debug APK SHA-256 `4a04cdc2d305c689909ccf0afabf374b13110bb7ca77d2d72199ac23a123125b`，AndroidTest APK SHA-256 `88ce6963ad3c324529918be9fb7eb764804c2d3b3006ac327d199e7a65f019ee`；主 APK 原生库仅 `lib/arm64-v8a/`，测试 APK 无原生库。构建日志 `/tmp/kclash-observation-details-checks-third-20261031.log`、`/tmp/kclash-observation-details-final-test-build-20261031.log`、`/tmp/kclash-observation-details-stable-final-build-20261031.log`。
- 失败留痕：首轮 DIRECT 文本匹配到多个节点，修为限定可点击语义；第二轮点击 DNS 卡片中心误触标签，改为真实触摸卡片右上留白。随后 9 项回归中 DNS 四张展开态哈希漂移，差异仅位于拖柄 48dp 区域；点击拖柄的波纹进入截图，正文保持一致。最终改用 `SemanticsActions.Expand`，等待语义为已展开且面板高度达到窗口的 0.9（误差不超过 2px）；稳定截图人工复核后更新四张展开态基线，最终 9 项完整重跑通过。原始失败日志保留在 `/tmp/kclash-observation-details-device-first-20261031.log`、`/tmp/kclash-observation-details-device-second-20261031.log`、`/tmp/kclash-observation-details-device-final-20261031.log`、`/tmp/kclash-dns-stable-capture-device-20261031.log`。
- 本轮仅使用固定 Compose 夹具，没有访问在线白名单路径、读取或修改工作区根目录 `1.yaml`、启动 VPN 或 Mihomo 核心；没有执行横屏或大字号用例。结束时设备为竖屏 `1080x2340`、系统字号 `1.0`、用户旋转值 `0`、自动旋转恢复原值 `1`；`ip link` 无 `tun0`，KClash 主进程为空，最新 `dumpsys activity services com.lonnnnnng.flclash` 成功返回 `(nothing)`。前轮服务查询被系统拒绝的历史记录仍保留。
- 当前详情与筛选切片为 `DONE-VERIFIED`，整体 UI/UX 继续 `PARTIAL`，本轮未提交推送。下一步优先对齐 Requests/DNS 完整列表工具栏、搜索/关键词区与页面布局，再补 DNS 点击快照的 Activity 重建恢复（当前 `selectedQuery` 仍为普通 `remember`）。DNS 详情水平内边距当前 24dp，Flutter 参考为 16dp；结构化日志、相对时间、全应用主题、连续圆角精确曲率、完整页面 parity 和跨设备验证仍待后续。

### 2026-11-01：Requests / DNS 完整列表工具栏与重建恢复首片

- Requests 与 DNS 记录改为全屏页面壳，统一上游标题“最近请求”“DNS查询”、返回入口、可展开搜索和关键词筛选；DNS 顶栏提供清理操作。详情关闭后保留列表搜索和关键词，DNS 详情快照通过 Activity 重建后恢复。
- 新增固定 Debug 宿主页面截图，Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏默认字号分别采集 Requests/DNS 深浅色 4 张页面图，人工检查标题、顶栏与记录布局无裁切或遮挡；哈希记录在 `docs/visual-baselines/observation-list-pages-redmi-api36.sha256`。测试在采集前断言竖屏和字号 `1.0`。
- AndroidTest 构建已安装到指定真机；截图、DNS 详情快照重建、顶栏操作、详情搜索/日志级别筛选、请求/DNS 详情及关键词组合筛选共 5 项精确 instrumentation 全部通过。完整单元测试、Debug/AndroidTest 构建和 Lint 已按 `android-arm64` 参数通过；主 APK 只包含 `lib/arm64-v8a/libclash.so`。
- 已知行为差异：Kotlin DNS 顶栏动作清除本地查询历史，Flutter `dns_queries.dart` 调用 `clearDnsCache()` 清除 Mihomo 缓存。当前 Core bridge 没有清理缓存方法，不能将此操作记作完全对齐；后续需补 bridge 调用与成功/失败提示。
- 本轮只用固定 Debug 夹具，未访问在线白名单、未读取或修改 `1.yaml`、未启动 VPN 或 Mihomo；未运行横屏/大字号用例。Requests/DNS 工具栏与重建恢复首片 `DONE-VERIFIED`，整体 UI/UX 仍为 `PARTIAL`。后续先做 Flutter 对照下的展开搜索/关键词区和完整页边距视觉校准，再收敛 DNS 清理行为与详情 24dp/16dp 内边距差异。

### 2026-11-01：DNS 缓存清理反馈首片

- `MainViewModel.clearDnsCache()` 返回 `Result<Unit>`，DNS 全屏页根据结果显示成功或失败 Snackbar；提示挂在列表弹窗自身窗口内，避免被下层主页面 Scaffold 遮挡。清理期间禁用重复点击，失败只显示稳定文案，不暴露核心异常文本；本地查询历史不被清空。
- CoreRuntimeManager 的成功确认和负确认单测已随完整 JVM 测试通过。Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36）固定列表宿主 4 项精确 instrumentation 通过：Requests/DNS 深浅截图、DNS 详情与筛选重建、清理成功反馈、清理失败反馈；两种反馈用例均验证 DNS 历史仍显示。截图哈希仍匹配 `docs/visual-baselines/observation-list-pages-redmi-api36.sha256`。
- 标准 `./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；Debug APK SHA-256 `25a9de5850c6030470b59355b3db09471e7c778643ed079275a9ff79e3909139`，AndroidTest APK SHA-256 `ca73f8904b0a4c29080ec14c456ee6ed16988c5e64ffa16fc8246a1750916c78`；APK 只包含 `lib/arm64-v8a/libclash.so`。
- 真机仅安装 Debug/AndroidTest 并运行固定 Compose 夹具；未访问在线白名单、未读取或修改 `1.yaml`、未启动 VPN 或 Mihomo。设备验收为竖屏 `1080×2340`、系统字号 `1.0`；未运行横屏或大字号用例。清理反馈 UI 与 RuntimeManager 返回契约已验证，运行中真实核心的端到端缓存清理仍未验证，不能据此宣称在线核心操作完成。Requests/DNS 整体 UI/UX 仍为 `PARTIAL`；下一步校准 Flutter 对照下的展开搜索、关键词筛选区域和完整列表页视觉。

### 2026-11-01：DNS 详情边距对齐

- Flutter `lib/views/dns_queries.dart` 的 `DnsQueryDetailView` 使用水平 `16dp` 列表边距；Kotlin DNS 详情容器已从 `24dp` 调整为 `16dp`，连接详情容器保持原布局。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36）重新采集并人工检查深浅色 × 成功答案/解析错误 × 半展开/展开 8 种详情图；长域名、DNS 上游 URL、完整 IPv6、错误文本均未裁切。八个 SHA-256 已同步到测试断言和 `docs/visual-baselines/dns-details-states-redmi-api36.sha256`。
- 标准 Gradle 单测、Debug/AndroidTest 构建和 Lint 通过；DNS 详情八态 instrumentation `OK (1 test)`，Requests/DNS 列表回归 `OK (4 tests)`。Debug APK SHA-256 `92430de1fa5bebba6e22f0d8a79eaffd8688f6dc55d7d70d6e9e3a2bac6288f2`，AndroidTest APK SHA-256 `1648caba2645588595e70fcfe9a52bc69965b19866141fcc3ffa947e05be6d33`。
- 本轮只使用固定 Compose 夹具，没有访问在线白名单、读取或修改 `1.yaml`、启动 VPN 或 Mihomo；设备保持竖屏 `1080×2340`、系统字号 `1.0`，未执行横屏/大字号用例。DNS 详情边距切片 `DONE-VERIFIED`，Requests/DNS 和整体 UI/UX 仍为 `PARTIAL`；下一步逐项核对展开搜索、关键词栏及完整 Requests/DNS 列表页面布局。

### 2026-11-01：Requests / DNS 搜索栏和关键词条 Flutter 对齐

- 对照 Flutter `lib/ui/scaffold.dart` 的 `_buildBarSearch`、`_BarSearch`、`_KeywordStrip`，以及 `lib/ui/search_field.dart`、`lib/ui/chip.dart`。展开搜索时 Kotlin 隐藏返回和 DNS 清理动作，保留独立取消键；搜索框为 44dp 胶囊，前置 20dp 搜索图标，文本起点 41dp，查询非空时显示清除按钮，取消或系统返回会清空查询并退出。
- 关键词条固定为 36dp；上下间距分别 4dp / 16dp，横向内边距 16dp，标签间隔 8dp。标签采用上游 15sp 半粗文字、16dp 删除图标及深浅色凸起表面；含标签时顶部总占位仍为 56dp，避免列表起点随筛选状态跳动。
- 搜索输入区、搜索动作和取消动作补齐 Flutter raised-surface 中性胶囊色面。Redmi 最终搜索态截图已人工检查：输入胶囊与取消按钮间隔 12dp，关键词条和记录卡片均无裁切或重叠。
- `ObservationListInstrumentedTest#expandedSearchReplacesToolbarActionsAndClearKeepsSearchOpen`、`ObservationDetailsInstrumentedTest#requestsAndDnsRecordsOpenDetailsAndKeywordChipsFilterRows`、`ObservationListInstrumentedTest#observationRequestAndDnsListsCaptureDarkAndLightScreens` 均在 Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36）精确通过；覆盖搜索态动作切换、字段清空/取消、36dp 标签、请求/DNS 详情和关键词筛选，以及四张普通态深浅色页面截图哈希。四张最终哈希位于 `docs/visual-baselines/observation-list-pages-redmi-api36.sha256`，截图为固定夹具渲染一致性证据，不声称跨 Flutter/Compose 像素等价。
- Flutter `SearchField` 的 41dp 前缀宽度现有真机回归断言：以搜索胶囊为基准检查图标左 inset 12dp、输入起点 41dp、图标到文字 9dp，坐标容差不超过 1px。Redmi 精确执行 `ObservationListInstrumentedTest#expandedSearchReplacesToolbarActionsAndClearKeepsSearchOpen`，结果 `OK (1 test)`；测试报告 `android/app/build/outputs/androidTest-results/connected/debug/TEST-Redmi Note 8 Pro - 16.xml`。本次 Debug APK SHA-256 `a911a6e6abd564bf140b4984d7a3bf3b0ff046e622a0a715200281d79e4dd72c`，AndroidTest APK SHA-256 `48657b5cf2a0c0eb64892e47f846e08d922d21d2c1689e2b9e18ebe377a2d1bb`。
- `./gradlew :app:assembleDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64` 和 `./gradlew :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；Lint `0 errors, 38 warnings, 3 hints`。Debug APK SHA-256 `5d975c6ea9e8e0f31653421561cd3f610521e4ae8eb20f01929012f11d661008`，AndroidTest APK SHA-256 `50baa01fa68b48b8fa026f68e8b28e260e9bd2cf7db10cea9bd3d08653aee477`；主 APK 原生库仅位于 `lib/arm64-v8a/`。
- 验收固定设备竖屏 `1080×2340`、系统字号 `1.0`；没有访问在线白名单、读取或修改 `1.yaml`、启动 VPN 或 Mihomo，也没有运行横屏/大字号用例。全量 JVM 单元测试本轮未重跑，整体 Requests/DNS 页面与 UI/UX 阶段仍为 `PARTIAL`；下一步核对列表页面剩余操作，以及观测页和 Dashboard 的 Flutter 视觉边距。

### 2026-11-01：Requests / DNS 空态与无结果态视觉对齐

- Requests 普通空态改为“暂无请求”与请求图形；DNS 普通空态改为“DNS查询为空”与 DNS 图形；过滤后无记录统一显示“没有匹配的结果”及搜索图形。两类页面的加载态/列表错误态在 Flutter 参考中均未接入，本轮没有新增 Kotlin 专属状态。
- 新增共用 Compose 空态组件，按类型绘制独立的圆瓣矢量底图、上游 primary-container/tertiary 点缀和 Material 图标；正文保持 17sp/22sp，插画依据纵向空间采用 120dp 或 160dp，最大宽 320dp，并按 Flutter 的 -0.2 垂直对齐。日志弹窗仍保留原文本空态。
- 固定夹具新增 Requests/DNS 普通空列表和搜索无结果覆盖。Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）精确执行 `ObservationListInstrumentedTest` 全部 7 项，失败/错误/跳过均为 0；包含 41dp 搜索输入起点、DNS 快捷清理反馈、详情重建，以及空态/无结果深浅色截图。8 个 SHA-256 位于 `docs/visual-baselines/observation-empty-states-redmi-api36.sha256`；4 张全屏空态图和 4 张无结果组件图已人工检查，无裁切、遮挡或主题对比问题。
- 最终设备为竖屏 `1080×2340`、系统字号 `1.0`。仅使用固定 Debug 夹具；没有访问白名单在线路径、读取或修改 `1.yaml`、导入订阅或启动 VPN/Mihomo；没有运行横屏/大字号用例。最终 Debug APK SHA-256 `dae4ea207ce8a8d8b003c55256cd5238e404fa2153478e1312cdee6aff52956e`，AndroidTest APK SHA-256 `f44bbb50ebd1d8aba82fbbee3cc4092198281c29c639c2be37f6638ac4223c2e`。`:app:lintDebug` 通过，0 Error、38 Warning、3 Hint；全量 JVM 单测本轮未重跑。Requests/DNS 其余页面交互及整体 UI/UX 仍为 `PARTIAL`；下一步核对剩余列表操作和观测页/Dashboard 页面边距。

### 2026-11-01：Logs 全屏列表与主流程优先级调整

- Logs 从 AlertDialog 改为与 Requests/DNS 一致的全屏列表页，顶部提供返回、搜索和导出；日志级别标签可加入关键词筛选并从标签条移除。普通空态与搜索无结果态复用观测页插画组件。页面仍使用现有日志状态和导出回调。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏、系统字号 `1.0`：`ObservationListInstrumentedTest` `OK (9 tests)`，覆盖 Logs 深浅色截图哈希、级别筛选、搜索、导出，以及既有 Requests/DNS 回归；`ObservationDetailsInstrumentedTest#requestsDnsAndLogsDetailsSupportSearchAndLevelFilter` 单项通过。`:app:testDebugUnitTest` 113 项通过，`:app:lintDebug` 通过。
- Logs 截图哈希为深色 `018be45df75a8b7f2d121fdf2af1ff2ce062847b6b16488624857c4d9560e5bf`、浅色 `cbe7afba7d17037586701e4dea342629ba92c1caf0b5d2f8bded9f8c204425d5`，存放于 `docs/visual-baselines/logs-page-states-redmi-api36.sha256`。Debug APK SHA-256 `5110f0c01a16e4872a7de0e207fae8991a8b04c7120416fd7d9142cfeff17d88`，AndroidTest APK SHA-256 `cc838f2ea7228babad419af6f9f8f4ad6d40bb10aebe72746ac902b3bd1ee0f0`。
- 验收只使用固定宿主；未访问两条在线白名单、未读取或修改 `1.yaml`、未启动 VPN/Mihomo，也未运行横屏/大字号用例。整体 UI/UX 仍为 `PARTIAL`。后续优先级调整为 Dashboard 当前配置主控 → Profiles 配置管理主路径 → Proxy Groups 组/节点切换；观测日志预览卡的清空快捷操作、自动跟随滚动与更细视觉对齐仍未完成。

### 2026-11-01：首页配置快捷操作与代理模式

- Dashboard 当前配置卡提供多 Profile 快速切换、远程 URL 单项同步和忙碌反馈，并显示配置来源、更新时间、订阅用量进度及到期时间；Profile 操作复用 `ProfilesViewModel`，核心运行或配置操作忙碌时禁用修改。首页新增 Rule/Global/Direct 三态模式控件；`CoreRuntimeManager` 将用户选择写入 SharedPreferences，核心运行时即时调用 Bridge，停止态选择在核心启动后应用。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏、系统字号 `1.0`：`DashboardInstrumentedTest` `OK (7 tests)`，覆盖配置切换、同步忙碌态、模式三态选择及停止/运行深浅色截图；`MainActivityInstrumentedTest#mainActivityRendersHomeAndProfileEntry` `OK (1 test)`。截图哈希：深色默认/系统就绪 `1e1ce2477be1dc3bed4543653ecadfd9197cffa50c35c6a6c1ae7913adb5a0f6`，深色运行 `2ca39f3de36edcafc30ad3ea7c7307b11f2a1f8ca108aa526f90023ad25f46e8`，浅色默认 `6b297450f902ee5ccb9b5da66fc7ba1129e0015aa403c93922ad167fa7d69e14`，浅色运行 `9caed5cf3d74f4842942f527eb9b8532995bd46e75ce8c8a74c350c6b17d6047`。
- `./gradlew test :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；全工程 JVM 242 项，失败 0、错误 0、跳过 0。`:app:lintDebug` 为 0 errors、38 warnings、3 hints。当前 Debug APK SHA-256 `0a44a59cf00ebd73977ee477f29f0312a55b28934dd25d8dea9ba5726379c1d3`；AndroidTest APK SHA-256 `7cb0884b5283556dd6ba2c26fcacd1f1fa1e38dfa1d0d0a8233c76253787afea`。
- 模式控件此前只有固定 Compose 宿主与 CoreRuntimeManager JVM 证据；本轮补上 Redmi 真实核心闭环，详情见下方“首页出站模式真实核心闭环”。本轮未通过界面手动添加或访问在线订阅；核心运行期间设备连接计数变化，目标未核验，因此不把本轮作为在线路径或外网分流验收。未运行横屏或大字号用例。Dashboard 外观和内容 parity 仍为 `PARTIAL`，主流程下一步转到 Profiles 导入，然后推进 Proxy Groups 递归与工具入口。

### 2026-11-01：Profiles 添加来源与远程订阅编辑主路径

- Flutter 远程 Profile 菜单可进入订阅编辑页，编辑名称、URL 和自动更新策略；Kotlin 原先只有同步、自动更新开关和重命名。本轮为 URL Profile 菜单补“编辑”入口与独立编辑表单，提供名称、HTTP/HTTPS URL、自动更新开关和分钟间隔。添加配置现在先选二维码图片、YAML 文件、订阅链接或自定义配置，再进入对应表单，避免来源入口和编辑字段混在同一对话框。
- `ProfilesViewModel` 校验 URL、名称长度和自动更新间隔，核心运行时拒绝保存；`ProfileRepository` 校验订阅地址、重复 URL 和 Profile 类型后持久化设置。URL 实际变化时才调用既有订阅下载/YAML 校验；下载失败保留旧配置文件并通过现有错误流提示。URL 未变化时只保存元数据，不触发网络请求。
- Redmi Note 8 Pro（serial `wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏、系统字号 `1.0`：`ProfileUiInstrumentedTest` 15 项通过，覆盖 QR/文件入口回调、订阅来源独立表单、远程菜单编辑和自动更新间隔；`ProfilesViewModelPanelInstrumentedTest#realViewModelDrivesCreateEditAndDeleteFlow` 1 项通过，覆盖真实 ViewModel 创建、编辑和删除组合路径。全工程 JVM 242 项通过。
- `:app:connectedDebugAndroidTest`、`./gradlew test :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；Lint 0 errors、38 warnings、3 hints。测试使用固定 Compose/ProfileStore 替身，没有访问两条真实在线白名单、未读取或修改根目录 `1.yaml`、未启动 VPN/Mihomo，也未运行横屏/大字号用例。
- 已知差异：Flutter 手机端通过相机扫码；Kotlin 目前从二维码图片 picker 导入，尚未提供相机扫码。Flutter 订阅添加还支持批量链接、重复 URL 提示及 age-secret 解密；Kotlin 目前没有 age-secret 解密 Bridge，配置导入完整路径仍为 `PARTIAL`。Requests/DNS/Logs 后续页面打磨继续暂停。
- 下一步顺序：补齐 Profiles 批量订阅链接、重复项反馈和相机扫码导入；本地 YAML 文件导入已通过真机主路径验收。随后推进 Proxy Groups 工具菜单、递归节点行为和当前节点自动定位。所有验收继续固定为 Redmi 竖屏默认字号，不运行横屏或大字号用例。

### 2026-11-01：首页出站模式真实核心闭环

- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）此前未安装 KClash；本轮安装 Debug APK 并首次授予通知与 Android VPN 权限。只使用工作区根目录 `1.yaml`，未读取其正文；设备 `/sdcard/Download/1.yaml` 与工作区文件 SHA-256 同为 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。
- 从 KClash「配置 → 添加配置 → 从文件导入 YAML」选取 `1.yaml`，首页显示本地 Profile；点击首页启动后进入“核心运行中”。依次切换 Rule、Global、Direct，三个模式均在实际运行态界面得到选中反馈；切换成功后核心状态仍为运行中。
- Direct 模式下停止核心再启动，启动成功且 Direct 仍为当前选中模式；随后再次停止，首页回到“核心已停止”。本轮没有手动访问在线订阅、诊断地址或其他外部 URL；运行核心期间设备连接计数变化，但没有核验这些连接的目标，因此不声称后台没有联网，也不把本轮作为在线路径白名单或公网流量分流验收。
- `./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 成功（Gradle 标记已完成任务为 `UP-TO-DATE`）；APK 原生库仅含 `arm64-v8a`，Debug APK SHA-256 `0a44a59cf00ebd73977ee477f29f0312a55b28934dd25d8dea9ba5726379c1d3`。精确 Redmi instrumentation `DashboardInstrumentedTest#dashboardOutboundModeControlChangesSelectedMode` 和 `#dashboardProfilePickerChangesCurrentProfile` 各 1 项通过；真机保持竖屏、系统字号 `1.0`，未运行横屏或大字号测试。
- 本次闭环验证的是首页操作到 Mihomo `updateConfig` 调用、模式状态反馈及重启恢复，不等同于互联网流量分流端到端验收。整体首页内容 parity 仍为 `PARTIAL`；后续先完成 Profiles 批量链接/重复项反馈和相机扫码，再做 Proxy Groups 工具菜单、递归组和当前节点自动定位。Requests/DNS/Logs 次要视觉工作继续暂停。

### 2026-11-01：Proxy Groups 工具菜单、排序过滤与当前节点定位

- 代理页工具入口加入 Flutter 对应的排序、网格密度、卡片尺寸和隐藏超时节点四类设置；设置保存到应用级 `SharedPreferences`，不写入 Profile YAML，也不会随 Profile 切换丢失。排序实现默认原序、名称升序和延迟优先级（成功、未测、超时、失败）；隐藏超时保留当前选择和嵌套代理组入口，过滤为空时回退原列表。
- 节点卡片支持紧凑/标准/宽松高度；网格密度按 Flutter 的 250dp 基准调整列数。新增定位按钮通过当前组的 `LazyVerticalGrid` 滚动到当前节点；嵌套组仍保持 Flutter 的平面列表行为，没有新增子组下钻页面。
- 自动化验证：App JVM 123 项通过；`:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` 通过；新增代理排序/隐藏过滤/网格密度纯逻辑测试通过。Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏、系统字号 `1.0`：新增 `proxyToolsMenuChangesGridDensityAndCurrentNodeCanBeLocated` 通过；截图方法在更新 `docs/visual-baselines/proxy-groups-states-redmi-api36.sha256` 后重跑通过，整类 7 项已全部通过。
- 本轮未访问两条真实在线白名单、未读取或修改工作区根目录 `1.yaml`、未启动 VPN/Mihomo；没有运行横屏或大字号用例。当前代理主流程工具首片为 `DONE-VERIFIED`，完整 Proxy UI/UX 仍为 `PARTIAL`；Flutter Tab/List 模式切换、列表图标样式、叶子协议字段和更细滚动恢复继续排在后续。

### 2026-11-01：Proxy Groups Tab/List 布局与列表图标

- 代理工具菜单增加 Flutter 对应的布局切换。Tab 模式继续使用横向分组标签和单组节点网格；List 模式按组显示标题、当前节点摘要、组图标和批量测速入口，组内节点仍使用同一选择/测速卡片，不新增子组下钻。
- List 模式提供填充、简洁和隐藏三种组图标样式；布局和图标偏好保存到应用级 `SharedPreferences`，并与排序、密度、卡片尺寸和隐藏超时设置共用状态边界。搜索、节点选择、单节点测速和延迟/超时/失败过滤在两种布局中保持一致。
- 自动化验证：`:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`、App JVM 123 项通过。Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏、系统字号 `1.0`：`proxyListLayoutShowsGroupSectionsAndListIconMenu` 通过；此前代理组工具/定位用例与 7 项整类回归保持通过。
- 本轮未访问两条真实在线白名单、未读取或修改工作区根目录 `1.yaml`、未启动 VPN/Mihomo；没有运行横屏或大字号用例。Tab/List 主流程首片为 `DONE-VERIFIED`，完整 Proxy UI/UX 仍为 `PARTIAL`；叶子协议字段、按组滚动位置恢复和更细 Flutter 卡片几何仍排在后续。

### 2026-11-01：Profiles 批量订阅导入首片

- 添加订阅表单提供“单条/批量”切换。批量输入支持换行、YAML `- ` 列表项和引号；预览已识别链接数、跳过的已有订阅数，重复项不重复导入；输入中存在无效链接时阻止提交并只显示无效数量，不回显链接正文。
- `ProfilesViewModel.addRemoteBatch` 按输入顺序逐条提交，单条下载失败不会中断后续链接；结果摘要只展示新增、重复跳过和失败数量，避免订阅 URL 中的 token 出现在 Snackbar。核心运行时仍拒绝修改 Profile。
- 自动化验证：`ProfileUrlImportTest` 3 项、`ProfilesViewModelTest#batch import is sequential skips existing urls and continues after failures` 通过；App 全量 JVM 测试及 `:app:lintDebug` 通过，Android 主代码和 instrumentation 编译通过。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏、系统字号 `1.0`：`ProfileUiInstrumentedTest#batchProfileImportShowsDuplicateCountAndSubmitsOnlyNewUrls` 与 `#batchProfileImportBlocksInvalidEntriesWithoutShowingTheirContents` 分别 1 项通过。仅使用固定 `.invalid` 表单夹具，没有访问在线白名单、读取或修改根目录 `1.yaml`，也未启动 VPN/Mihomo；没有运行横屏或大字号用例。
- 配置批量导入与重复反馈首片完成，完整 Profiles/UI parity 仍为 `PARTIAL`；相机扫码和 age-secret 解密仍未覆盖。工具菜单、Tab/List 和 Tab 当前节点定位已随后完成；当前下一片为自定义配置创建后的编辑与草稿退出，再补结构化编辑和主流程菜单。完成主流程前继续暂停 Requests/DNS/Logs 视觉工作。

### 2026-11-01：主页配置详情、代理组摘要与 Profiles 结构化编辑入口（DONE-VERIFIED 首片）

- 当前配置整卡可打开配置详情，显示名称、来源、订阅摘要和同步/配置管理入口；核心运行时详情可读、同步禁用。Dashboard 代理组卡显示数量与最多四枚首字徽标及剩余数量，按 Rule/Global/Direct 模式过滤展示；整卡打开节点选择弹层。
- 弹层复用代理组 Tab/List 偏好、搜索、选择与测速回调，选节点后保持打开；关闭后释放查询。使用完整组图解析当前叶子，避免 hidden 分组过滤破坏递归结果。键盘出现时面板自动展开，测速收进摘要行后首排节点和测速控件完整可见。
- Redmi 定向真机证据：`DashboardInstrumentedTest` 14 项、`ProxyGroupsActivityInstrumentedTest` 8 项、Profiles 结构化分区入口 1 项、真实 ViewModel 创建/编辑/放弃组合 1 项、主页空草稿退出 1 项均通过；本轮主流程组合重跑最终 `OK (26 tests)`。首次 Dashboard 搜索用例因键盘压缩半展开面板失败，已修复并复验通过；主页入口旧断言和错误测试类名也已修正。
- 自定义编辑器现在展示“配置概览”以及 Rules、Proxies、Proxy Groups、Providers、DNS、Dialers 六个入口，并保留可滚动原始 YAML；分区按钮调用既有 ViewModel 编辑器。输入法在取消、保存和放弃确认时收起，确保手机弹窗标题和底部操作可见。该切片仍不等同 Flutter 的即时持久化结构化页面，改动后退出命名、导入/预览和完整分区表单继续排队。
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；App JVM `127` 项失败 0、错误 0、跳过 0；Lint `0 errors, 41 warnings, 4 hints`。Debug APK SHA-256 `638425a54b0d174a4ac532014748ea1565fee527ff3ef697f0b0136afc0ee4f9`，AndroidTest APK SHA-256 `2c06880b882c340383b18cffc0f04cc9ab27f4cabf50d43375eb94b83481a267`，主 APK 原生库仅 `lib/arm64-v8a/`。
- 本切片使用固定 Debug/ProfileStore 数据，未访问在线白名单、未读取或修改根目录 `1.yaml`、未启动 VPN/Mihomo；设备为 Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`），仅竖屏系统字号 `1.0`。主流程整体 UI/UX 仍为 `PARTIAL`，下一片优先做自定义配置结构化编辑的即时保存/退出命名，再推进配置管理菜单和代理组剩余视觉交互。

### 2026-11-01：首页运行态配置切换引导

- 首页运行中仍可打开配置选择弹层；弹层明确提示先停止核心，提供直接停止操作，停止完成前禁用其他配置项，停止后在同一弹层继续选择。启动/停止过渡态显示等待提示，避免配置切换与核心生命周期并发。
- `ProfilesViewModel.select` 对 RUNNING、STARTING、STOPPING 状态增加业务层保护；Profile 切换不自动重启核心，当前选择和核心已应用配置仍保持区分。
- `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64` 通过；App JVM 128 项失败 0、错误 0、跳过 0。`:app:lintDebug` 通过。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏 `1080×2340`、系统字号 `1.0`：`DashboardInstrumentedTest` 整类 `15` 项通过，失败/错误/跳过均为 0，覆盖首页运行态停止引导、停止态配置切换、配置详情、代理组摘要与选择器、出站模式和截图基线。停止引导用例验证运行态提示、配置项禁用、停止回调和停止后继续选择；未通过真实运行中的 Mihomo 核心触发停止，因此生产运行时端到端仍未验收。
- 本轮未访问两条真实在线白名单，未读取或修改工作区根目录 `1.yaml`，未启动 VPN/Mihomo；未运行横屏或大字号用例。首页切换交互首片已实现并通过固定宿主验证，完整 Dashboard/Profiles/Proxy Groups 主流程仍为 `PARTIAL`。下一步继续 Profiles 新建自定义配置后的结构化编辑和退出命名，再推进配置管理菜单与 Proxy Groups 叶子协议/视觉细节；Requests/DNS/Logs 后续视觉打磨继续暂停。

### 2026-11-01：Profiles 分区保存与新建草稿退出保护

- Rules、Proxies、Proxy Groups、Providers、DNS 和 Dialers 保存成功后，同步父级编辑器的 YAML 正文或 Profile 覆盖元数据；原始 YAML 有未保存修改时，进入分区前先确认放弃，避免编辑器旧快照覆盖分区结果。
- 新建 Profile 的空草稿仍可在退出时删除；分区内容已经持久化后退出不再删除配置，而是允许保存新名称或保留默认名称。已保存 YAML 的界面状态随文件更新刷新。
- `./gradlew :app:testDebugUnitTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；`ProfilesViewModelTest` 34 项通过。Instrumentation APK 编译通过。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏 `1080×2340`、系统字号 `1.0`：`ProfilesViewModelPanelInstrumentedTest` 2 项通过，覆盖结构化 Rules 保存→父级正文刷新→草稿命名保留，以及未改动草稿退出删除；`ProfileUiInstrumentedTest#savedNewProfileOffersRenameWhenLeavingEditor`、`#savedNewProfileCanKeepDefaultNameWhenLeavingEditor` 和 `#unsavedYamlRequiresConfirmationBeforeOpeningStructuredSection` 通过。
- 仅使用固定 `ProfileStore` 夹具；未访问在线白名单、未读取或修改根目录 `1.yaml`、未启动 VPN/Mihomo；未运行横屏或大字号用例。Profiles 生命周期切片为 `DONE-VERIFIED`，完整 Profiles 和 Dashboard/Proxy Groups 主流程仍为 `PARTIAL`。下一步回到 Dashboard 逐项对齐主页按钮/统计入口，再推进 Profiles 管理菜单与 Proxy Groups 剩余视觉交互；Requests/DNS/Logs 后续视觉打磨继续暂停。

### 2026-11-01：主流程优先级收敛

- 近期实现顺序固定为 Dashboard → Profiles → Proxy Groups，逐页完成主操作、顶栏菜单、卡片/列表入口、弹层和错误/空/忙状态；观测页视觉与菜单打磨排在主流程之后。
- Dashboard 继续保留 Flutter Android 默认卡片顺序，Android 专属核心启停卡作为平台入口；本轮不为统计入口重排主页，也不把 DNS 或最近请求入口作为当前工作目标。
- Dashboard 先完成首页各按钮和跳转的逐项核对；Profiles 随后补齐配置行菜单、添加/同步/编辑/导入等操作；Proxy Groups 再完成运行时代理组菜单及自定义 Proxy Groups 的结构化表单、排序和删除确认。原始 YAML 编辑保留为高级入口，不视作结构化表单完成。
- 本条只调整近期推进顺序，不提升任何功能状态；Dashboard、Profiles、Proxy Groups 整体 UI/UX 仍为 `PARTIAL`。后续验收继续仅限 Redmi Note 8 Pro、竖屏、系统默认字号和 `arm64-v8a`。

### 2026-11-01：Dashboard 首页原位布局编辑

- Dashboard 顶栏提供“编辑首页/完成编辑”切换；首页原位置显示卡片操作行，长按拖动手柄重排、移除按钮隐藏卡片，列表末尾可添加未显示卡片，边缘拖动支持滚动。顺序和显隐实时保存在应用级 SharedPreferences，不写入 Profile 或 YAML；添加卡片时放到可见列表末尾。默认顺序保持 Flutter Android 主卡顺序，核心启停卡固定在顶部。
- 新增布局状态单元测试 6 项，覆盖默认主卡顺序、未知/缺失偏好恢复、升级新增卡片默认可见、显隐与排序独立、可见卡片拖动排序时不带回隐藏卡片，以及重新添加时移至可见列表末尾。
- `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；`DashboardLayoutTest` 6 项通过，Lint 无错误。Debug APK 已安装并在真机打开，竖屏默认字号截图确认编辑操作没有遮挡卡片标题或连接数。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏 `1080×2340`、系统字号 `1.0`：独立 `DashboardLayoutEditorInstrumentedTest` 2 项通过，覆盖卡片长按拖动后顺序持久化、卡片移除/重新添加和 Activity 重建读取。测试只使用应用本地状态；未访问在线白名单、未读取或修改根目录 `1.yaml`、未启动 VPN/Mihomo；未运行横屏或大字号用例。
- 执行边界记录：首次使用 `-Pandroid.testInstrumentationRunnerArguments.method=...` 过滤时参数未生效，实际运行了 `MainActivityInstrumentedTest` 整类 19 项，其中包含观测页和旋转用例。该次有 1 项主页导航断言失败，因为布局偏好虽已恢复，复用的 Activity 仍保留旧组合状态；该执行结果不作为观测页或旋转验收证据。随后将新编辑器测试隔离到单独测试类，定向用例通过，未再次运行该整类。
- 编辑器核心交互已验证；Flutter 进出场/摇摆动效和 Dashboard 主页其余按钮、菜单的逐项核对仍在队列，Dashboard 整体 UI/UX 仍为 `PARTIAL`。下一步进入 Profiles 管理菜单与添加/同步/编辑路径，再推进 Proxy Groups 结构化表单、排序和删除确认；Requests/DNS/Logs 继续延期。

### 2026-11-01：手机主导航四 Tab 与代理入口条件

- Flutter 手机端主导航顺序为“首页 → 代理 → 配置 → 工具”；代理入口不是固定项，无可用配置/代理组时隐藏。初始化前 Flutter 暂以 Profile 是否存在作为入口兜底，初始化完成后按当前可见代理组决定；Kotlin 主页面按 Profile/代理组状态生成导航，空状态只保留“首页、配置、工具”。
- Requests、活动连接、DNS 查询、资源、设置和关于从主导航收回工具页入口；工具页保留 Flutter “导航 / 设置 / 其他”分组。工具入口跳转已通过真实 MainActivity 定向测试；工具页全部二级功能和视觉细节仍需逐项按 Flutter 对齐。
- 自动化验证：`MainPageScaffoldTest` 覆盖无可用配置时隐藏代理、入口可用时四 Tab 顺序；`:app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon -Ptarget-platform=android-arm64` 通过。
- Redmi Note 8 Pro（serial `wsvwypiz7xwslvl7`，Android 16/API 36，ABI 列表含 `arm64-v8a`）竖屏 `1080×2340`、系统字号 `1.0`：安装 Debug APK 后，`MainActivityInstrumentedTest#mainActivityNavigatesAcrossFlutterMobileDestinationsAndTools` 与 `#mainActivityRendersHomeAndProfileEntry` 各 1 项通过。未清空设备应用数据；本轮未访问在线白名单、未读取或修改工作区根目录 `1.yaml`，未启动 VPN/Mihomo，也未运行横屏或大字号用例。
- 当前只完成主导航结构和二级入口首片；四个页面内容/样式整体 parity 仍为 `PARTIAL`。下一步继续按 Dashboard → Profiles → Proxy Groups 顺序逐页、逐菜单和逐按钮对照 Flutter，不扩展 Requests/DNS/Logs 视觉工作。

### 2026-11-01：授权真实配置导入与代理入口条件真机核验

- 使用项目 `AGENTS.md` 明确授权的两条真实订阅路径，通过 KClash Deep Link 导入成功，设备上保留两份远程 Profile；文档、日志和验收输出不记录完整 URL、Token 或配置正文。
- 覆盖当前 APK 的启动空态：在尚未导入有效 Profile 时，Redmi 真机导航只显示“首页、配置、工具”，代理 Tab 不出现。导入真实 Profile 后，代理 Tab 出现；选中真实配置并启动核心后，代理页实际读取到 Mihomo 返回的 Selector、Fallback 和多个节点组。
- 停止核心后，页面回到“核心已停止”，两份真实 Profile 与代理 Tab 保留；设备侧未再发现 KClash 的 VPN/TUN 活动态，确认停止清理完成。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏 `1080×2340`、系统字号 `1.0`；当前 Debug APK 构建成功并覆盖安装，未运行横屏或大字号用例。真实核心闭环已验证，但主流程整体 UI/UX 仍为 `PARTIAL`，后续继续按 Dashboard → Profiles → Proxy Groups 逐页对齐。

### 2026-11-01：Proxy Groups List 组折叠/展开与 Profile 持久化

- Flutter 参考的 List 模式以空 `unfoldSet` 初始折叠所有组；组标题点击切换当前组的展开状态，并使用约 240ms 的展开/收起动画。该行为依据 `lib/views/proxies/list.dart` 的 `_handleChange`、`_GroupSliver` 和 `ListHeader` 核对。
- Kotlin `Profile` 新增安全的 `unfoldSet` JSON 编解码，过滤空组名并对损坏 JSON 回退空集合；`ProfileRepository` 以一次 Room 更新同时保存当前组名和展开集合，避免标题点击的整行写入互相覆盖。
- `ProxyGroupsViewModel` 将展开集合纳入 `ProxyGroupsUiState`，组标题切换先更新 UI，持久化失败时回滚并通过错误流提示；代理组 List 页面保留组标题和批量测速入口，节点区按集合折叠/展开，提供 Flutter 对应的展开/收起图标和动画。
- 新增 JSON、ViewModel、Compose List 和 Repository/Room 回归；`./gradlew :app:testDebugUnitTest --no-daemon` 通过，`compileDebugAndroidTestKotlin` 与 `lintDebug -Ptarget-platform=android-arm64` 通过。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`arm64-v8a`）竖屏 `1080×2340`、系统字号 `1.0`：`ProxyGroupsActivityInstrumentedTest#proxyListLayoutShowsGroupSectionsAndListIconMenu`、`ProxyGroupsViewModelPanelInstrumentedTest#listGroupHeaderTogglesNodesAndPersistsProfileViewState`、`ProfilesRepositoryPanelInstrumentedTest#repositoryPersistsCurrentProxyGroupForPageRestore` 各 1 项通过；未运行横屏或大字号用例。
- 本轮只使用固定代理页/Room 宿主，没有访问两条真实在线路径、读取或修改 `1.yaml`、启动 VPN 或 Mihomo；设备上此前导入的两份真实 Profile 保持不变。Proxy Groups 整体仍为 `PARTIAL`，叶子协议类型、按组滚动位置恢复和更细 Flutter 卡片视觉校准继续排队；下一步回到 Dashboard → Profiles 主流程逐项核对管理菜单和导入/预览。

### 2026-11-01：Dashboard Flutter connectionStats 可选卡片

- 对照 lib/views/dashboard/widgets/connection_stats.dart 和 lib/views/dashboard/widgets/connections_table.dart，新增三等分统计卡片：连接、请求、DNS 各自显示实时计数、按钮语义和独立 testTag；外层保持 Dashboard 34dp 圆角、8dp 内边距、Material 主题色和移动端紧凑布局。
- 对照 lib/models/config.dart 的 Android 默认 Dashboard 列表，CONNECTION_STATS 默认隐藏，只能从“编辑首页 → 添加卡片”加入；旧偏好升级不会强制显示，避免改变已验收的首页顺序。
- 连接入口复用现有 Connections 页面；请求和 DNS 入口进入现有 Observation 目标后打开完整列表对话框，保留搜索、筛选、清空和详情入口，不新增网络或核心数据源；三个入口均经过首页 Access 未保存修改保护。
- :app:testDebugUnitTest、:app:compileDebugAndroidTestKotlin、:app:lintDebug、:app:assembleDebug、:app:assembleDebugAndroidTest 均通过；Impeccable detector 对改动 UI 文件返回空问题列表。Debug APK SHA-256：b229f4e7c1733ac8b54f6872eb58dc73257f0b15c8a07f5987ad6c7f8f09cfc2。
- Redmi Note 8 Pro（wsvwypiz7xwslvl7，Android 16/API 36，arm64-v8a）重新读取为竖屏、系统字号 1.0，覆盖安装未清数据；DashboardLayoutEditorInstrumentedTest 定向执行 3 项全部通过，覆盖添加卡片、三个入口、布局持久化和未保存修改在取消/重建/放弃后的路由恢复。设备服务列表为空，未启动核心；未访问在线白名单、未读取或修改根目录 1.yaml，未运行横屏或大字号用例。连接入口的 Flutter 底部弹层几何和 Dashboard 其余逐按钮视觉 parity 继续排队，整体 UI/UX 仍为 PARTIAL。

### 2026-11-01：Dashboard 顶栏标题与编辑态文案对齐

- Flutter Dashboard 首页标题为“仪表盘”，进入编辑后标题切换为“编辑仪表盘”，编辑列表的候选分区使用“可添加”；Kotlin 复用 MainPageScaffold 的标题参数，Dashboard 编辑态按同一状态切换，原有编辑/完成按钮语义保持不变。
- :app:testDebugUnitTest、:app:compileDebugAndroidTestKotlin、:app:lintDebug、:app:assembleDebug、:app:assembleDebugAndroidTest 通过；Redmi Note 8 Pro（wsvwypiz7xwslvl7，API 36，arm64-v8a）竖屏系统字号 1.0 定向 MainActivityInstrumentedTest#mainActivityRendersHomeAndProfileEntry 与 DashboardLayoutEditorInstrumentedTest#dashboardLayoutEditorReordersAndPersistsSectionVisibility，OK (2 tests)。
- 本轮未访问在线白名单、未读取或修改 1.yaml、未启动 VPN/Mihomo，也未运行横屏或大字号用例；Dashboard 其他卡片和菜单逐项视觉 parity 仍为 PARTIAL。

### 2026-11-01：Dashboard 出站模式 connected button 几何对齐

- Flutter `lib/views/dashboard/widgets/outbound_mode.dart` 与 `card.dart` 的手机分支使用 `DashboardConnectedGroup`：按钮高度 52dp，组间距 3dp，端部外圆角 26dp，内侧静止圆角 12dp，选中后四角展开为 26dp 胶囊；Kotlin 已移除原先的共享底轨和 4dp 内边距，按同一几何与 240ms ease-out 动画实现。
- Rule、Global、Direct 图标以 Flutter `AppGlyphs` 的 24 单位坐标、1.8 单位线宽重建原生矢量路径，标签调整为 footnote 对应的 13sp/18sp、半粗体；当前模式重复点击保持选择，不重复触发变更回调。rules 外框仍使用近似曲线，Flutter superellipse、ElasticPress 按压膨胀/置顶及焦点环尚待继续对齐。
- `:app:testDebugUnitTest`、`:app:compileDebugAndroidTestKotlin`、`:app:lintDebug`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest` 均通过；Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，API 36，arm64-v8a）竖屏、系统字号 1.0 定向执行 Dashboard 出站模式三项切换、主页入口和编辑布局共 3 项，`OK (3 tests)`。
- Dashboard 全屏截图基线已随本轮几何和图标调整更新：dark default/system_ready=`2e5cfd2f...`、running=`2c4fffe5...`，light default=`af78e2ab...`、running=`d309fe04...`；代理组摘要/选择器 dark/light 截图用例仍保持通过。未运行横屏、大字号，未访问在线白名单、未读取或修改 `1.yaml`、未启动 VPN/Mihomo，设备既有两份 Profile 未清理。整体 UI/UX 仍为 `PARTIAL`。
- 截图已通过 instrumentation 通道导出到本机；本轮图片查看工具未成功呈现画面，人工视觉复核仍未完成。哈希基线仅用于锁定本轮原生渲染稳定性，不作为 Flutter 像素一致的验收结论。Debug APK SHA-256：`e6329541f9b26cf7cf6edceebb30f0e2277f21ef5263ada28bbb4fcf2d3c3efe`。
- 最终验证：App JVM 单元测试 142 项，失败/错误/跳过均为 0；三项 Dashboard 固定状态截图用例与代理组摘要/选择器截图用例在安装更新后的测试包后 `OK (4 tests)`，加上三项行为用例共 7 项定向真机回归。基线更新前的旧哈希失败保留为预期变更记录；测试包未重装导致的一次旧断言复跑也已明确纠正。

### 2026-11-01：首页卡片与功能入口归属纠偏

- 逐项对照 `lib/models/config.dart`、`lib/views/dashboard/widget_registry.dart`、`lib/views/tools.dart` 和 `lib/views/config/on_demand.dart`；这些 Flutter 参考文件与本地 `main` 无差异。Android 默认首页严格收敛为：状态 → 网络速度 → 出站模式 → 配置 → 策略组。
- 从首页渲染、可添加清单和 Kotlin 旧布局解析中移除电池优化、系统 Always-on、按应用代理和订阅请求诊断；保留已有卡片的自定义顺序及隐藏偏好，不更改 Profile 数据。网络速度不再附带连接数量，状态卡移除自创提示文案；连接统计仍是原版对应、默认隐藏的可选卡。
- 按应用代理接回“工具 → 设置 → 按应用代理”，沿用现有真实 ViewModel、Room、应用列表、剪贴板和保存；Dock、顶栏返回、系统返回和 Activity 重建后的未保存确认均迁移至该独立子页。重新进入时清空上次搜索。
- 电池优化授权接回“工具 → 常规 → 按需连接 → 前置条件 → 忽略电池优化”；授权文案、已授权状态和按钮颜色对照 Flutter。此轮仅接回既有授权能力，完整常规与按需连接设置（位置权限、SSID 条件等）仍未实现。
- Flutter UI 和现存原生桥接没有找到系统 Always-on 或针对订阅地址的证据包诊断入口。两者按 Kotlin 验收扩展保留，仅 Debug 且显式传入 `kclash_developer_tools=true` 时显示“开发者模式”入口；普通启动与 Release 不显示。没有把它们冒充原版的 VPN 开关或网络服务可用性卡。
- 编辑流程同时收敛：状态卡整卡长按可排序、不可移除；其他卡片右上角叠减号徽标；编辑态屏蔽业务控件，候选行左侧为 28dp 添加按钮、40dp 图标底块和原版文案。
- 当前 Flutter Android 可添加而 Kotlin 尚缺的四项明确排队：VPN（需启用设置与本地代理模式链路）、服务状态（需真实目标探测）、内网 IP（需内网地址及 allowLan/mixedPort 状态）、内存信息（需进程/核心内存模型）。不展示假读数或无行为的占位卡。
- 首页标题同时对照 Flutter 中文资源收敛为“状态 / 配置 / 策略组”。源码核实订阅 UA 的 `clash-verge` 与 `Platform/android` 兼容标记后已补齐 Kotlin 请求格式，保留自身名称与版本；本地 HTTP 重定向回归确认两跳均携带该标记。第一条授权订阅在真机原生请求与本机原版 UA 对照请求中均返回 HTTP 403（nginx、JSON 响应），尚不能判定拒绝原因，未恢复该配置；第二条通过正式 Repository 重新导入成功并保留。失败记录保留，未把单份配置宣称为两份恢复。
- 验证状态：最新 App JVM 146 项、Debug/AndroidTest 构建和 Lint 通过。验收初次通过正式 Room 核实为空，与此前“两份配置保留”的历史记录不一致；此轮没有执行原有 Profile 删除或清数据。按用户最新范围，元数据核验要求唯一授权来源已导入且正文存在，禁止空库通过。截图宿主遵循无配置隐藏代理 Tab 的门控，并取消启动时清空代理页偏好。Tools 导航、电池授权路径、Debug 重建和 Access 退出保护最终通过定向复验，先前失败与修复保留在以下记录；完整 Dashboard UI/UX 仍为 `PARTIAL`。
- 退出确认诊断已经通过正式宿主 ViewModel 区分原因：开关变更后草稿为 `dirty=true`，Dock 仍直接离开应用代理页；批量选中/取消后则回到已保存值，草稿为 `dirty=false`。导航守卫现改为点击时读取当前保存的工具子页与草稿，批量用例明确留下开关草稿后再验证放弃；两项原失败方法修复后 `OK (2 tests)`，日志 `/tmp/kclash-dashboard-ownership-20261101-mainflow_ready-214052.log`。诊断失败日志保留于本机，不把增加等待或空草稿当作修复。
- 主流程定向复验 `OK (9 tests)`（`/tmp/kclash-dashboard-ownership-20261101-mainflow-214715.log`），覆盖工具导航、电池原版路径、Debug 入口恢复、应用代理的 Dock/顶栏/系统返回取消、放弃、保存退出和保存后重建。保存结果从正式 Room 独立回读，Activity 重建只证明页面恢复；保存用例用 `finally` 恢复完整原设置，避免以放弃后续草稿撤销此前保存而留下开关污染。保存中跨重建、保存期间取消/放弃以及进程死亡时的草稿恢复仍需后续独立切片，不作为本轮已完成结论。
- 首页编辑定向复验 `OK (6 tests)`（`/tmp/kclash-dashboard-ownership-20261101-editor-214936.log`），覆盖状态卡整卡排序/不可移除、右上角移除和左侧添加几何、普通卡片拖动、显隐与顺序持久化、连接统计三个入口，以及退出决定在 Activity 重建后的取消/放弃和 Dashboard 目标恢复。所有用例均为 Redmi 竖屏默认字号，未触碰真实 Profile 正文。
- Dashboard 行为定向复验 `OK (5 tests)`（`/tmp/kclash-dashboard-ownership-20261101-dashboard-215155.log`），覆盖运行态默认卡片不混入验收扩展、出站三模式、Rule/Global/Direct 策略组摘要门控、连续节点选择与搜索重置、继承 List 偏好和中部拖动不关闭面板。
- 九张固定宿主截图已同批采集：停止/系统就绪、运行、深浅色首页，以及深浅色策略组摘要和选择面板；其中系统就绪与默认画面相同。旧基线四项失败保留为预期标题/提示/导航变更记录（`/tmp/kclash-dashboard-ownership-20261101-capture-215339.log`）。本轮通过图片工具实际查看了全部不同画面与编辑控件，更新原生基线并安装最终测试包后 `OK (4 tests)`（`/tmp/kclash-dashboard-ownership-20261101-capture-220550.log`）；选择面板的深浅色哈希保持原值。目视核对覆盖按钮/文案可见性、无配置代理入口门控和面板首排，固定数据仍不代表 Flutter 逐像素一致。
- 最终构建日志 `/tmp/kclash-dashboard-ownership-20261101-final-build.log`：App JVM 146 项失败/错误/跳过均为 0，Debug/AndroidTest 构建通过，Lint 0 errors、45 warnings、7 hints。App APK SHA-256 `0338314cfafc8c0ce7c2cdf2ef8d2f2913dc62e4059ba5c4863de57ef2ace58f`，测试 APK `b2a8999e4afb59f951b54dfc3479ffd8c1e046cb1973fe71411c0cbb4f37cdf8`，均已与设备安装包逐一比对；原生库仅 `arm64-v8a`。本轮不启动核心，不运行横屏/大字号；整体 UI/UX 和四项可选卡、持续边缘滚动及完整动效仍为待完成。

### 2026-11-01：在线验收收敛为唯一授权来源

- 用户确认原第一条链接无法导入，明确撤回使用范围；工作区外层 `AGENTS.md` 已删除该条，只保留原第二条并重新编号为 1。后续不再访问被撤回的链接。
- 仓库 `AGENTS.md`、项目上下文、测试入口与当前路线图同步单链接规则；历史双链接证据保留其当时语义。真实订阅地址、Token 和响应正文不进入仓库或验收报告。
- 真机连接验收脚本 `tool/android_connection_acceptance.py` 同步为恰好一条清单，默认索引为 `1`，拒绝旧 `--target-index 2`；当前唯一链接始终从外层 `AGENTS.md` 读取，不写入源码。
- 连接脚本 Python 语法、帮助参数和旧索引拒绝核验通过；该核验在参数阶段结束，没有启动真实核心或设备动作。实际连接流程的过滤器收敛为精确 `Class#method`，后续只运行竖屏默认字号验收。
- 真机已成功导入的授权 Profile 保留供后续 UI/UX 调试验收；准备入口优先复用已有记录与正文，不重复导入或删除。验收前后继续核对正式 Room ID 及文件元数据。
- 单链接准备结果 `added=0 repaired=0 reused=1 failures=0`（`/tmp/kclash-dashboard-ownership-20261101-prepare-210913.log`）。验收前 `/tmp/kclash-dashboard-ownership-20261101-before-210919.log` 与验收后 `/tmp/kclash-dashboard-ownership-20261101-after-220848.log` 均 `OK (1 test)`、`count=1 missing=0 expected=1`，同一份真实 Profile 文件的名称、大小和修改时间保持一致。结束时服务查询为 0；保留配置继续调试，不访问已撤回来源。

### 2026-11-02：Dashboard 会话运行时间与真实速度历史 — `DONE-VERIFIED`

- Flutter 参考基线使用本地 `main=7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`；`current_outbound.dart`、`traffic.dart`、运行状态 Provider 和 `line_chart.dart` 与该基线无差异。运行时间与出口探测相互独立，网络速度卡公开缓冲为 30 点，不引入桌面峰值文案或历史详情入口。
- `CoreRuntimeManager` 在服务与核心监听器成功就绪后记录 `SystemClock.elapsedRealtime()`，重复启动不重置，停止、失败和 VPN 撤销清空；新进程重新启动核心即建立新会话。官方 [SystemClock](https://developer.android.com/reference/android/os/SystemClock) 正文已由 `smart-search fetch` 核实（2026-11-02），明确单调时钟包含深度休眠时间；系统校时不改变时长。
- `MainViewModel` 独立刷新运行时间，不等待统计/连接读取；前台成功读取时追加核心原始速度，最多保留 30 点。后台与读取失败不补点，取消传播到采样协程，新会话清空旧缓冲；最近 30 次采样不等于严格最近 30 秒，历史只保留内存，不持久化。
- 正式首页接入运行时间 `HH:mm:ss`（停止显示 `—`、超过一天继续累计小时、最大 `999:59:59`）与上下行速度曲线。曲线左侧补零仅用于绘制，使用高斯平滑、非负控制点、最低 8 KiB/s 纵轴、2dp 线条和 0.14 透明度填充；停止时速度、累计和曲线一并归零。Flutter 的额外平滑缓冲、连续滚动动效、精确几何与出口 IP/质量均不在本切片完成范围。
- 最终命令 `./gradlew :app:testDebugUnitTest :service:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过（`/tmp/kclash-dashboard-telemetry-20261102-final-build.log`）；App 153 项、Service 75 项，失败/错误/跳过均为 0；Lint 0 errors、45 warnings、7 hints。新增覆盖慢读取期间计时、在途取消后不追加、后台不采样、快速重启状态合并、失败读取保留历史、停止清空、30 点上限、格式和曲线极值。测试替身曾将非 suspend `stop()` 误写为 suspend，编译失败日志保留于 `/tmp/kclash-dashboard-telemetry-20261102-unit-final.log`，修正后复验通过。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，API 36、`arm64-v8a`、1080×2340、字号 1.0）固定宿主 5 项行为通过（`/tmp/kclash-dashboard-telemetry-20261102-behavior-001416.log`）；五张停止/运行/系统就绪与深浅色 PNG 已采集、目视检查并更新原生基线，3 项截图方法复验通过（`/tmp/kclash-dashboard-telemetry-20261102-capture-001314.log`）。首次旧哈希的 3 项失败保留于 `/tmp/kclash-dashboard-telemetry-20261102-capture.log`，不作为 Flutter 像素一致结论。
- 正式 `MainActivity` 真实核心测试只复用唯一授权来源已导入的当前 Profile；使用该来源受控请求并捕获非零真实样本。30 点缓冲、后台暂停、回前台时长追平、Activity 重建保留会话、停止清空全部通过；最终复验峰值 5673 bytes（`/tmp/kclash-dashboard-telemetry-20261102-live-001452.log`），首次复验峰值 1507 bytes 为前一轮证据。没有访问被撤回来源、没有生成设备 YAML 或读取/修改 `1.yaml`，没有运行横屏/大字号。
- 配置保留前后核验均通过：`count=1 missing=0 expected=1`，同一 Profile 文件名称、大小、修改时间一致（`/tmp/kclash-dashboard-telemetry-20261102-before.log`、`/tmp/kclash-dashboard-telemetry-20261102-after-001632.log`）。结束时服务条目为 0；`ip link show` 被系统拒绝 `Permission denied`，改查可读 `/sys/class/net`，确认没有 `tun0`，未提升设备权限或清数据。
- 最终 APK：App SHA-256 `663180de06afbd46e03575431b556f8aa5ec18eda26ae0501fa62c3a4956e3b1`，AndroidTest `38762943393c702d46e2394a39e489fe6219ba4df510e6f4c802fbc43ee94c8e`，均已与真机安装包比对；App 原生库仅 `arm64-v8a`，测试包无原生库。Impeccable 对改动 UI 文件检测结果为空。
- 本切片已验收，Dashboard/Profiles/Proxy Groups 整体仍为 `PARTIAL`。下一步先接出口 IP/质量状态与详情，再补首页剩余菜单/按钮，随后继续 Profiles 管理与导入/预览、完整结构化表单和 Proxy Groups 细节；观测页次要视觉继续暂停。

### 2026-11-02：Dashboard 出口 IP 与质量详情 — 运行态链路 `DONE-UNIT`，固定宿主 `DONE-VERIFIED`

- 沿用本地 Flutter `main=7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 的 `service_probe.dart`、`ip_quality.dart` 和 `current_outbound.dart`。8 个出口来源、6 个质量来源及字段解析已进入独立 Repository；外部接口当前可用性未联网确认。本轮没有同步远端 main，不宣称最新上游 parity。
- `CoreBridge` 复用 Go `outboundIp` 与 `probe`，经空代理名按 Mihomo 规则路由；专用 IP 元数据读取最多 16 KiB 正文，诊断 API 保持 `max-body=0`。响应对象字符串不包含正文或源 URL。`CoreRuntimeManager` 在网络等待前后核对运行态、本地修订和核心 epoch/route-version，外部网络等待释放启停锁，旧会话/旧路由结果拒绝发布；路由快照读取限时 3 秒。Kotlin 取消不等于 Go 请求立即取消，后者仍受自身超时控制。
- 仅核心 RUNNING、Activity 前台且 Dashboard 可见时查询；首页每秒刷新路由快照，模式/节点/会话变化清空旧出口。回到首页先核对路由，质量查询被后台取消后可恢复；手动刷新保留已有 IP，失败提供重试。运行时间和速度历史独立于出口服务，不以探测失败改变核心状态。
- 质量查询沿用首波 3 源、后续每秒推进波次、每源 8 秒超时。明确类型优先，推断住宅只在全部来源结束后作兜底；Tor/滥用优先标记高风险，VPN/代理标记不单独降级。成功缓存 3 天、失败 5 分钟，当前为最多 64 个 IP 的内存缓存，不保存原始响应；持久 Room 缓存留待后续。IPv4 先校验四段数值再调用地址解析，避免无效服务响应进入 DNS；拒绝主机名、私网、环回、链路本地、CGNAT 和组播。
- 状态卡显示国家代码、IP、类型/等级、组织或 ASN；IP 详情支持质量独立刷新、来源失败状态、风险标记、复制 IP/组织/ASN 和隐藏 IP。隐藏仅遮挡画面，复制仍取实际 IP；隐藏状态为当前页面保存状态，未扩为应用全局设置。详情在停止或出口变化时关闭，旧 IP 再次出现不会自动弹回；深浅色详情已实际目视核对。
- 自动化：App 166、Service 77、Core 22 项，失败/错误/跳过均为 0；新增 16 项覆盖八源/六源解析、错误/风险映射、波次与输家取消、缓存/强制刷新、后台恢复、旧路由丢弃、网络等待不阻塞停止及 Bridge 正文不进入日志。`build4.log` 通过全部模块单元测试，最后 UI 调整后 `build5.log` 再次通过 App 单元测试、Debug/AndroidTest 构建与 Lint（0 errors、45 warnings、7 hints）；基线测试包重建 `test-baseline-build.log` 通过，日志均以 `/tmp/kclash-outbound-20261102-` 为前缀。Go `go test . -run 'TestHandle(Probe|OutboundIp)|Test.*Route' -count=1` 通过，`go.log` 为本地回环测试证据。
- Redmi `wsvwypiz7xwslvl7`、API 36、arm64、竖屏 1080×2340、字号 1.0：`ip-015201.log` 3 项、`behavior-015214.log` 5 项、`capture-015230.log` 3 项均通过；五张首页 PNG 与两张质量详情 PNG 已实际查看，首页原生哈希同步更新。`ip-014822.log` 的 ASN 首屏可见断言失败保留，修正为滚动可达性断言并直接展开详情后通过；`capture-014913.log` 的旧基线 3 项失败保留为预期布局变更。早期正则转义/可空访问编译失败和越界地址解析测试失败分别保留于 `build1.log`、`build2.log`，修复后通过。
- 配置核验 `before-014719.log`（旧 telemetry 前缀）和 `after-015258.log` 均 `count=1 missing=0 expected=1`，同一文件名称、大小和修改时间相同；覆盖安装没有清数据或改真实 Profile。App SHA-256 `56ce4869b0072bfc27782530b30bee1cd5f16ce99f81f1d60ae2426e89e429c0`，AndroidTest `c19b27acca702ad6d0d3aade309ef84f7295c0eb5c9a93420886c7c915391b9a`，均与设备安装包一致；原生库只含 arm64-v8a。结束时服务为空、`/sys/class/net` 无 tun0。
- 本轮未访问真实订阅或外部 IP 来源、未启动 VPN/Mihomo、未读取或改写 `1.yaml`、未运行横屏/大字号；实际外部 HTTP/TLS、规则分流后的出口与质量一致性仍未验收。停止态 DIRECT、Flutter route-hits 复用/settle/throttle、国旗与精确几何、质量持久缓存及隐藏偏好全局化未完成，整体 Dashboard 仍 `PARTIAL`。Impeccable 用于原生规范和目视检查；HTML/CSS detector 不适用于 Compose，不以空扫描作为原生验收证据。

### 2026-11-02：Dashboard 编辑持续边缘滚动 — `DONE-VERIFIED`

- 继续按本地 Flutter `main=7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` 的 `lib/views/dashboard/edit.dart` 手机原位编辑交互推进。Kotlin 原实现仅在每次 `onDrag` 时滚动 18 物理像素，手指静止就停止；改为一个受拖动生命周期约束的帧循环。手指进入上下 56dp 边缘区域时按接近程度加速，最大 640dp/s，按帧时长与屏幕密度换算；长帧最多按 32ms 推进，防止恢复画面时跳屏。
- 滚动后持续重新判断排序目标；已写入的新顺序尚未反映到 LazyColumn 坐标时暂停交换，防止旧位置触发反向交换。每次交换保留视口索引和偏移，避免列表跟随首个可见卡片 key 跳到新位置；使用官方 `LazyListState.requestScrollToItem`，正文经 `smart-search fetch` 核实：[Android API 文档](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListState#requestScrollToItem(kotlin.Int,kotlin.Int))。本轮没有同步远端 Flutter 分支。
- 松手、手势取消、完成编辑、离开首页或组件销毁都会结束滚动循环；Activity `ON_STOP` 清除正在拿起的卡片，后台回来不能继续滚动或修改排序。原有整卡长按、状态卡不可移除、移除/添加位置和应用级持久化保持不变；不写入 Profile 或 YAML。
- App JVM 169 项失败/错误/跳过均为 0；新增 3 项纯函数测试覆盖边缘方向与钳制、时间/密度速度换算、长帧保护和旧坐标不反向交换。Debug/AndroidTest 构建及 Lint 通过，Lint 0 errors、45 warnings、7 hints。命令为 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64`，最终日志 `/tmp/kclash-dashboard-edge-20261102-final-format-build.log`；Service/Core 未改动，本轮未重跑其测试。
- Redmi `wsvwypiz7xwslvl7`、API 36、arm64、竖屏 1080×2340、系统字号 1.0：精确选择 `DashboardLayoutEditorInstrumentedTest#dashboardDragContinuesScrollingAtBottomAndStopsOnRelease`、`#dashboardDragContinuesScrollingAtTopAndStopsOnCancel`、`#dashboardEdgeDragDoesNotResumeAfterBackgroundOrEditorExit` 和既有排序/持久化/状态保护/控件 4 项，共 `OK (7 tests)`，日志 `/tmp/kclash-dashboard-edge-20261102-edge-021919.log`。最终格式整理构建覆盖安装后复验仍 `OK (7 tests)`，日志 `/tmp/kclash-dashboard-edge-20261102-edge-022654.log`。新增静止测试在两段各 200ms 帧推进间不发送移动事件，确认第二段仍前进/后退，随后松手/取消并等待，确认滚动位置和保存顺序不再变化；后台返回和完成编辑另有检查。
- 首轮 2 项失败保留于 `device1.log`，诊断 `diagnostic-device.log` 显示排序已改变但两段滚动位置相同；修复列表 key 导致的视口重新定位后，`device3.log` 两项通过，再扩展至上述 7 项通过。前缀均为 `/tmp/kclash-dashboard-edge-20261102-`；没有删除失败记录或把修改测试等待作为修复。
- 编辑控件 PNG `/tmp/dashboard_editor_addable_controls-20261102.png` 已实际查看；截图为滚动后的列表可视区域，顶部卡片因列表位置部分位于视口外，不作为控件裁切或 Flutter 像素一致证据。本轮未改静态首页布局，因此不更新固定首页哈希。
- 覆盖安装前后正式 Room 均 `count=2 missing=0 expected=1`（expected=1 是原验收脚本最低门槛，并非精确数量），两份文件名称、大小、修改时间一致；本轮开始前已经有 2 份，不沿用上一轮 1 份的旧快照。本轮未创建/删除/修改 Profile，测试结束恢复原首页布局，未访问在线来源或读写根目录 `1.yaml`，未启动 VPN/Mihomo，未运行横屏/大字号；结束时服务为空、无 `tun0`。
- 最终 App SHA-256 `336660a72db28aaeecdad7730f68e463a41a1b9cfb58d22f21750197cf4b975a`，AndroidTest `ccf56a850f77bb5b0217ebac3b62a5f173096dbe4ad4f92cc0fbf26b169acc47`；均与 Redmi 已安装包逐一核对一致。最终配置保护复验 `/tmp/kclash-dashboard-edge-20261102-after-022818.log` 为 `OK (1 test)`、`count=2 missing=0 expected=1`；服务为空、无 `tun0`，`git diff --check` 通过。
- Dashboard 整体仍为 `PARTIAL`；本项不代表完整拖动 overlay/动效或完整 Flutter parity。下一步继续首页剩余入口核对，再转 Profiles 管理菜单、导入/预览与结构化表单，最后补 Proxy Groups 细节；Requests/DNS/Logs 次要视觉继续暂停。

### 2026-11-02：停止态节点、配置热切换与真实出口 IP 联动

- 配置加载与流量接管分离：有效配置通过脚本、Provider/DNS 覆盖和 Mihomo 校验后加载节点；停止态不启动监听、ProxyService 或 VPN，但可以查看、切换节点和单项/批量测速。Direct 模式仍显示非隐藏代理组。
- 配置页和首页均允许停止/运行时切换配置；生命周期管理器串行热应用，运行服务保持。失败回滚原选择与暂存输入，恢复失败则停止服务并显示对应错误。配置切换会拒绝旧列表、旧选择保存及旧批量测速回写。
- 首页出口在运行态按 Mihomo 当前路由查询，停止态使用独立 DIRECT；启停、配置、节点、底层网络变化使旧结果失效。首页前台失败约五秒重试、成功约一分钟复查，离开首页或后台暂停。质量仍按实际 IP 缓存，不将旧 IP 质量套到新出口。
- 首页配置详情新增只读“运行配置”预览：运行态读取暂存输入，停止态在内存复用启动转换顺序；有界读取、切换失效，不写原 Profile。SnakeYAML loader/dumper 独立实例避免并发解析互扰。
- 最终构建命令：根目录 Wrapper 执行 App/Service/Core 的 testDebugUnitTest、app assembleDebug、assembleDebugAndroidTest、lintDebug，参数 --no-daemon -Ptarget-platform=android-arm64。App 176、Service 82、Core 22 项通过，失败/错误/跳过 0；Lint 0 errors、45 warnings、7 hints。Go 的 TestDirectOutboundIp、TestHandleOutboundIp、TestHandleSetupConfig 定向测试通过。
- Redmi wsvwypiz7xwslvl7（API 36、arm64、1080×2340 竖屏、字号 1.0）真实验收复用唯一授权订阅，临时第二配置只来自外层 1.yaml 原始字节。停止态节点显示/选择/正延迟 DIRECT 测速、运行中本地与订阅配置热切换、真实代理正延迟测速、GLOBAL 切实际节点、代理链与显示 IP 独立复查、停止后直连出口、停止态切配置均通过。代理 IP 与直连 IP 实际不同，仅记录 changed=true，不记录地址或节点名。
- 原有两份 Profile 的 ID 集合、正文大小/修改时间在用例内前后核对；临时 Profile 只删除本方法刚创建的已知 ID，恢复原选择、节点映射和模式，不清数据。实际最终日志 /tmp/kclash-profile-runtime-20261102-live-034237.log；7 项 UI 日志 /tmp/kclash-profile-runtime-20261102-ui-033907.log。
- 失败记录保留：build6 缺少测试 import；live-033145 在独立复查期间路由变化，拒绝旧结果后增加可见态失败重试；live-033726 停止前后 IPv4/IPv6 来源竞争导致旧等值断言失败，改为当前网络独立 DIRECT 来源集合核验；live-build11 的 async 作用域错误已修正。真实失败不作为通过证据。
- 最终生产构建日志 /tmp/kclash-profile-runtime-20261102-build10.log；最终测试包/Lint 日志 /tmp/kclash-profile-runtime-20261102-live-build13.log；Go 日志 /tmp/kclash-profile-runtime-20261102-go-final.log。Debug APK SHA-256 031f67536cd9eb4c9499cc8b0341b125202988998a7452e9dd3baafee7457244；AndroidTest 4d5f9829647a3ff8a44ba81e013a31e22bb96459b7af1a6f8018297987b3296a，均与设备安装包匹配，应用原生库仅 arm64-v8a。
- 深浅色预览和首页停止态截图已目视核对，首页三处停止态基线随真实 IP/查询空态更新；固定宿主不作为真实网络证据或 Flutter 像素一致证据。完整 Dashboard/Profiles/Proxy Groups UI/UX 仍 PARTIAL；质量来源全覆盖、持久缓存与其他 roadmap 项仍待后续切片。

- 最终首页截图基线复验 3 项通过：/tmp/kclash-dashboard-edge-20261102-capture-034348.log。
- 最终配置保护复验 /tmp/kclash-dashboard-edge-20261102-after-034535.log 通过：count=2 missing=0，文件元数据一致；结束时服务为空、无 tun0，本轮固定临时输入已移除，git diff --check 通过。


### 2026-11-02：可选内存信息、详情与释放内存 — DONE-VERIFIED

- Flutter 参考为本地 main 的 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7，读取 memory_info.dart 与 widget_registry.dart；没有同步远端分支。新增默认隐藏的内存卡，支持首页编辑添加/移除/排序和应用级布局持久化，不写 Profile 或 YAML。
- CoreMemoryStats 严格读取非负整数 RSS、heapInuse、heapIdle、stackInuse、runtimeOther；缺字段、负数、小数和越界拒绝，合计饱和保护。Android 同进程总量直接取 RSS，应用部分为 max(0, RSS-Go分项合计)，不再把核心重复相加；详情说明 OS/Go 口径差异。
- MemoryRuntime 统一由 CoreRuntimeManager 提供，复用现有 getMemoryStats/forceGc 协议；读取和回收串行、有界等待，不加载配置、不启停监听或服务。回收等待回执后重新读取真实统计，提示“已请求回收，内存数据已刷新”，不承诺固定回收量或数值必然下降。
- MemoryViewModel 只在前台、首页非编辑态且卡片实际可见时约每两秒读取，卡片和详情共用一次采样；后台、离页、隐藏卡片取消刷新，恢复立即读取。成功与异常路径均拒绝取消后的旧结果；失败保留上次采样并重试，释放中防重复点击。详情完整展开并支持滚动和重建恢复。
- App 183、Service 84、Core 23 项 JVM 测试通过，共 290 项失败/错误/跳过 0；涵盖后台慢成功/慢失败/慢回收取消、重试、防重复、共享进程口径、协议边界和运行态回收不重载配置/不启停服务。Debug/AndroidTest 构建、Lint 通过（0 errors、45 warnings、7 hints）；Go TestHandleGetMemoryStatsReportsALiveRuntime 定向测试通过。最终构建日志 /tmp/kclash-memory-20261102-final-build.log，增补服务测试 /tmp/kclash-memory-20261102-service-final.log，最终截图测试包/Lint /tmp/kclash-memory-20261102-capture-build.log，Go /tmp/kclash-memory-20261102-go.log。
- Redmi wsvwypiz7xwslvl7、API 36、arm64、1080×2340 竖屏、字号 1.0：DashboardInstrumentedTest 两项内存交互/深浅色截图、MemoryRuntimeLiveTest 一项真实停止态数据/回收/后台/重建/布局、DashboardLayoutEditorInstrumentedTest 三项既有编辑回归共 6 项通过。最终日志 /tmp/kclash-memory-20261102-memory-041518.log、regression-041531.log、memorylive-041548.log（后两项同前缀）。实际 RSS=600170496、Go分项合计=91682969 bytes；不把测试进程占用当普通使用基准。运行中的真机内存回收本轮未启动会话验证，服务层测试已覆盖会话保持。
- 深浅色卡片 PNG 与完整窗口详情 /tmp/memory_detail_screen_dark-20261102.png、/tmp/memory_detail_screen_light-20261102.png 已实际目视；节点截图因独立窗口偏移不作为完整详情证据。固定宿主画面不代表 Flutter 像素一致；默认首页基线未改。
- 失败保留：初次测试 Wrapper 目录错误退出 127；build.log 的可见性控制声明顺序导致 selectedDestination 未解析，移到导航状态后修复；memory-040943.log 的失败提示不可见由面板半展开引起，改为完整展开后两次复验通过。文件前缀均为 /tmp/kclash-memory-20261102-，不以失败记录充当通过。
- 安装前后正式 Room 均 count=2 missing=0 expected=1（expected 是旧脚本最低门槛），两份配置文件名称、大小、修改时间一致；恢复原首页布局，不清数据或创建/删除 Profile。本轮未访问唯一授权订阅、未读取或修改外层 1.yaml、未启动 VPN/ProxyService，正式首页沿用已授权的自动出口 IP 查询；没有运行横屏/大字号。最终保护日志 /tmp/kclash-memory-20261102-after-041603.log。
- 最终 Debug APK SHA-256 52f44c0cffd4f3b46f63f106052e5db86d1b60e30499b6caf9946ac44ba6b1e6，AndroidTest 5498e6cd4d9206783aa6c9954f2cc391a1e1e34219ca5d0127bd780e54a0fc9a；均已核对安装包哈希一致，应用原生库仅 arm64-v8a。结束时服务为空、无 tun0，git diff --check 通过。
- Dashboard 整体仍 PARTIAL；下一步继续首页可选 VPN、服务状态、内网 IP 与剩余菜单，再转 Profiles 管理菜单/导入预览/结构化表单，最后补 Proxy Groups 细节。Requests/DNS/Logs 次要视觉继续暂停。

### 2026-11-02：可选内网 IP 与局域网共享 — DONE-VERIFIED

- Flutter 参考为本地 main 的 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7，核对 intranet_ip.dart、common/network.dart 与网络事件刷新链路；未同步远端分支。新增默认隐藏的内网 IP 卡，支持首页编辑添加/移除/排序及应用级布局持久化。无地址与读取中禁用复制；共享关闭复制 IP，开启复制 IP:port，IPv6 端点使用方括号；显示当前 mixed-port，本轮不增加端口编辑。
- AndroidLocalNetworkSource 使用非 VPN 网络回调和 LinkProperties 地址；Wi-Fi、以太网优先，各网络 IPv4 优先，排除环回、未指定、链路本地和组播。离屏、编辑、离开首页或后台注销回调并撤下旧地址，返回后重建；网络丢失清空对应候选，局域网无 INTERNET 仍可展示。长 IPv6 允许换行，无地址显示“无网络”，注册失败提供重试。
- CoreRuntimeManager 统一管理局域网设置：暂存输入校验 → updateConfig 热更新 → getLanConfig 读取当前核心值 → 偏好落盘。新增 Go getLanConfig 窄接口，只返回当前 allow-lan/mixed-port；原 getConfig(path) 读取文件，不能当作热更新确认。失败恢复核心、暂存输入和偏好，恢复失败则停止服务并回显失败。停止态仅保存/加载配置，不启用监听；运行态更新保持服务和核心会话。
- 全局共享偏好独立保存，启动、配置热切和只读预览共用覆盖逻辑；不写原始 Profile。普通开关只 patch 当前暂存输入，避免重复执行动态脚本。未配置时仍可保存选择；端口 0 或设置未读成功时不复制代理端点。读取、保存和复制错误分开；防重复提交，超时解除忙态允许重试。
- API 依据通过 smart-search fetch 读取 Android 官方 ConnectivityManager 与 LinkProperties 正文（2026-11-02）：https://developer.android.com/reference/android/net/ConnectivityManager 和 https://developer.android.com/reference/android/net/LinkProperties 。证据 /tmp/kclash-lan-connectivitymanager.md、/tmp/kclash-lan-linkproperties.md；使用 registerNetworkCallback/unregisterNetworkCallback/getLinkAddresses，未采用已弃用的轮询 getAllNetworks。
- App 189、Service 89、Core 24 项 JVM 共 302 项通过，失败/错误/跳过 0；覆盖候选网络过滤与优先级、IPv6 复制、前后台/断网清理、保存防重复与失败重试、超时解除、暂存与源文件边界、部分更新失败回滚、偏好落盘失败、脚本不重复执行、全局覆盖跨配置切换/重启。最终根 Wrapper 构建含三模块 testDebugUnitTest、app assembleDebug/assembleDebugAndroidTest/lintDebug，参数 --no-daemon -Ptarget-platform=android-arm64；日志 /tmp/kclash-lan-20261102-final-build.log。Lint 0 errors、45 warnings、7 hints；Go TestGetLanConfigReflectsHotUpdate、TestGetLanConfigRejectsMissingAppliedConfig、TestUpdateConfigAppliesAllowLan 通过，日志 /tmp/kclash-lan-20261102-go2.log。
- Redmi wsvwypiz7xwslvl7、API 36、arm64、1080×2340 竖屏、系统字号 1.0：DashboardInstrumentedTest 的 intranetCardHandlesLoadingOfflineSaveFailureAndRetry、intranetCardCapturesBothThemesAndLongIpv6 共 2 项通过；IntranetIpRuntimeLiveTest 的 realAddressCopySharingHotUpdateAndLifecyclePreserveProfiles 1 项通过；既有首页编辑 3 项和 MemoryRuntimeLiveTest 1 项通过，共 7 项。最终日志 /tmp/kclash-lan-20261102-lanui-044734.log、lanlive-044743.log、regression-044757.log、memorylive-044815.log（后面均同前缀）。
- 真实验收仅复用当前唯一授权来源已导入的 Profile；停止态地址/剪贴板、共享保存/重建/前后台、运行中开启共享可连接手机自身内网 TCP 端口、关闭连接失败、重新开启恢复，核心运行时间标记不变；不向该 TCP 连接发送外部代理目标。首页沿用既有外部出口查询。IPv6-only 真实网络、Wi-Fi↔蜂窝实际切换和另一台局域网客户端访问未验证；对应地址格式与状态切换仅有固定宿主/单元证据。
- 深浅色 IPv4 与长 IPv6 四张卡片 PNG 已目视，路径 /tmp/intranet_card_dark-20261102.png、intranet_card_light-20261102.png、intranet_ipv6_dark-20261102.png、intranet_ipv6_light-20261102.png（后面均位于 /tmp）；完整显示且操作可达，不作为 Flutter 像素一致证据。本轮不改变默认首页固定截图基线。
- 失败保留：初次补测试脚本 cwd 错误未写文件；build1 的三项服务测试使用了字面换行夹具，修正真实换行后通过；lanui-044035 的端口断言位于合并语义之外，改用未合并子树后两次复验通过；service-final 的新增测试未注入 JVM 时钟，报 elapsedRealtime not mocked，注入时钟后 service-final2 和最终构建通过。日志均位于 /tmp/kclash-lan-20261102-*。
- 配置保护 before-043935 与 after-044829 均 count=2 missing=0 expected=1（expected 为旧脚本最低门槛），原两份文件名称/大小/修改时间一致，真实用例内部也核对 ID 集合及正文元数据。恢复原局域网覆盖、原布局与停止态；不清数据、不创建/删除 Profile、不读取或修改外层 1.yaml，未运行横屏/大字号；结束服务为空、无 tun0。
- 最终 App SHA-256 98cc6dcedf84d06330c4b4a52d16de29d7363cd99d35c810514678f6488dec96，AndroidTest 3e4a084bddbc88aada887e02f1fc970fd91008da02e3753d07993744da22754f；均与设备安装包一致，应用原生库仅 arm64-v8a。git diff --check 通过。Dashboard 整体仍 PARTIAL；下一步继续可选 VPN、服务状态及首页剩余菜单，再推进 Profiles、Proxy Groups；观测页次要视觉继续暂停。

### 2026-11-02：可选 VPN 与纯代理模式 — DONE-VERIFIED

- Flutter 基线为本地 main 的 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7，核对 network_takeover.dart 的 VpnCard、providers/state/system.dart 的 vpnOptions 和 vpn_manager.dart 的运行中重启提示；未同步远端。卡片默认隐藏，通过编辑仪表盘添加，整行只有一个 Switch 语义；显示保存的已启用/已停用，运行中修改提供待生效说明与“重启生效”，不把启用选择冒充当前 TUN 状态。
- VpnSettingsStore 独立保存全局选择，默认 true，不写 Profile/暂存 YAML；保存成功才更新页面，失败保持原值并允许重试。CoreRuntimeManager 区分 enabled/appliedEnabled：停止态保存不初始化核心、不启动服务；运行中保存保留会话，改回当前模式取消待生效。显式重启共用 transitionLock，等待旧服务实例退出再启动新模式；失败撤销启动、清除应用态和会话。
- enable=false 时仅启动 ProxyService 与核心监听，不创建 VPN/TUN；ProxyService 也发布真实启动结果，不能只凭 Intent 把核心标为运行。enable=true 继续等待 VPN CONNECTED。ServiceConfig 新增两服务实例生命周期；停止同时撤销排队启动，仅向存活实例发清理 action，避免纯代理凭空创建 VPN。MainActivity、QuickActionActivity、Application 恢复入口按保存设置决定是否 prepare；重启先授权，拒绝时保留原会话，授权回调的重启动作支持 Activity 保存恢复。ADR 0008/0017、CONTEXT、项目约定已同步。
- 官方正文通过 smart-search fetch 获取：VpnService https://developer.android.com/reference/android/net/VpnService （prepare 后再建立 VPN），Services overview https://developer.android.com/guide/components/services （stopSelf/stopService 与 onDestroy 生命周期）。证据 /tmp/kclash-vpnservice-20261102.md、/tmp/kclash-services-20261102.md，访问日期 2026-11-02。
- 最终命令：./gradlew :service:testDebugUnitTest :app:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64。App 189、Service 93、Core 24，共 306 项通过，失败/错误/跳过 0；Lint 0 errors、45 warnings、7 hints；日志 /tmp/kclash-vpn-final2-build.log（51s）。新增服务测试覆盖保存/重建/保存失败、运行中待生效/改回取消、双向重启与启动失败清理；Go 本轮未改动、未重跑。
- Redmi wsvwypiz7xwslvl7、Android 16/API 36、arm64、竖屏默认字号：DashboardInstrumentedTest#vpnCardShowsSavedChoicePendingRestartAndDisabledTransitions、#vpnCardCapturesBothThemesAndPendingModes 共 2 项；VpnSettingsRuntimeLiveTest#vpnChoicePersistsAndExplicitRestartSwitchesRealTunAndProxyModes 1 项；既有首页编辑 3 项、IntranetIpRuntimeLiveTest 真实共享监听 1 项，共 7 项通过。日志 /tmp/kclash-vpn-20261102-vpnui-051534.log、vpnlive-051543.log、regression-051555.log、lanlive-051612.log（后面均同前缀）。
- 正式首页添加 VPN 卡，关闭设置并重建后保持；纯代理启动只有 ProxyService 存活、混合端口 TCP 可连接、shell 接口列表无 tun0。运行中启用不改会话、不创建 TUN；显式重启后 VPN CONNECTED 且 shell 独立确认 tun0。随后停用仍保留旧会话/TUN，显式重启后 TUN 消失且 ProxyService 继续运行；停止后两服务退出。既有共享监听热切和同一会话回归通过。纯代理 kill-process 恢复、快捷入口无授权现场、首次授权拒绝及跨设备代理请求未单独验收，不以源码适配替代现场证据。
- /tmp/vpn_dark-20261102.png、/tmp/vpn_light-20261102.png 已目视，开关、待生效说明与重启按钮完整可读；固定宿主只证明布局，不作为 Flutter 像素一致证据。本轮不改变默认首页截图基线。
- 失败保留：build2 因测试缺少 assertIsOn/assertIsOff 导入和截图助手名称错误而失败，修正后通过；vpnlive-051133 的应用 File.exists 读取 sysfs 误报无 tun0，改为 instrumentation shell 独立读接口后两次复验通过（另有 vpnlive-051324）。初期检索正则错误、候选测试路径不存在及文档脚本反引号解析错误均未修改目标文件。临时脚本生成器的转义警告未影响执行。失败日志保留于 /tmp。
- 配置保护 before-051034 与 after-051626 均 count=2 missing=0 expected=1（expected 为旧脚本最低门槛），正文文件名/大小/mtime 一致；真实用例另核对 ID 集合、当前选择和元数据。仅复用已有唯一授权来源，不下载订阅、不创建/删除 Profile、不清数据、不读写外层 1.yaml；首页继续既有自动出口查询。恢复原 VPN 选择、共享覆盖、布局及停止态；结束无服务、无 tun0，未运行横屏/大字号。
- 最终 App SHA-256 4ebeb83f641234c7f643a634b95ac4e393bae0621d5b30f115e33d97cfecf059；AndroidTest 73112c71fbb4b35ab7a8e78335057ac77dd3223e066647768722a44e86058e50；均与设备 base.apk 哈希一致，应用原生库仅 arm64-v8a，测试包无原生库。git diff --check 通过。Dashboard 整体仍 PARTIAL；下一步服务状态与首页剩余菜单，然后 Profiles 管理菜单/导入预览/结构化表单、Proxy Groups 细节。

### 2026-11-02：可选服务状态真实检测首片 — DONE-VERIFIED（完整 parity 仍 PARTIAL）

- 参考本地 Flutter main 的 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7，核对 service_probe.dart、service_status.dart、routed_probe.dart 与 dashboard/widgets/service_status.dart；未 fetch/merge 上游。新增默认隐藏的服务状态卡，13 个固定目标顺序沿用 Flutter，10 种状态映射中文；正常显示延迟，详情显示地区与代理链，提供单项和全部检测。管理页启用/禁用与上下移动持久化，至少保留一项，保存失败保持原值。
- CoreBridge 接入既有 Go serviceCheck，显式传 names（禁止空列表意外全量）、停止态 DIRECT、运行态空 proxy-name 按规则；严格验证目标完整性、重复、字段类型、非负整数和路由版本，未知状态降为失败。Go 检测器未改写，继续使用固定服务端点、全局 4 槽并发、同名替换取消、超时重试与 36 秒 sweep 预算。网络等待在 CoreRuntimeManager 启停锁外，结果回写前核对本地 revision、运行状态、core epoch 和 route version；路由过期单独抛出 ServiceRouteChangedException，不冒充服务失败。
- 前台且卡片实际可见时自动检测启用列表前两项；详情显示内存缓存，只在用户操作时检测其他目标。后台/离屏取消 Kotlin 请求，Go 已发出的请求由其自身预算结束，不声称远端立即取消。路由变化清除缓存，等待稳定 1.5 秒再自动探测；超时/失败约 5 秒重试，成功不周期全量重测。真实网络/节点/配置/会话变化采用保守清理，尚未解析 hits 做细粒度复用；服务出口 IP/质量、长按拖动排序和更细 Flutter 视觉交互仍待补齐。
- 最终根 Wrapper：./gradlew :app:testDebugUnitTest :service:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64；App 196、Service 96、Core 26，共 318 项，失败/错误/跳过 0。日志 /tmp/kclash-service-status-final2-build.log（前次 final-build 也通过）；Lint 0 errors、46 warnings、7 hints，新增一项为 SharedPreferences.edit 的 UseKtx 建议，显式 commit 用于失败时保持旧页面状态。Go 在 core 模块执行服务检测、服务规则和 route/probe 定向测试通过，日志 /tmp/kclash-service-status-go2.log。
- Redmi wsvwypiz7xwslvl7、Android 16/API 36、arm64、1080×2340 竖屏、字号 1.0：DashboardInstrumentedTest#serviceStatusDetailsSupportSingleBatchChecksAndTargetManagement 与 #serviceStatusCardAndDetailsCaptureBothThemes 共 2 项；ServiceStatusRuntimeLiveTest#realDirectAndRoutedChecksAndCardSettingsPreserveProfiles 1 项；DashboardLayoutEditorInstrumentedTest 的 dashboardLayoutEditorReordersAndPersistsSectionVisibility、dashboardStatusCardCanBeDraggedAndCannotBeRemoved、dashboardEditorUsesOverlayRemovalAndLeadingAddControls 共 3 项；VpnSettingsRuntimeLiveTest#vpnChoicePersistsAndExplicitRestartSwitchesRealTunAndProxyModes 1 项，合计 7 项通过。最终日志 /tmp/kclash-service-status-20261102-serviceui-055045.log、servicelive-054909.log、regression-055101.log、vpnlive-055118.log（后面均同前缀）。
- 正式 MainActivity 添加服务卡，停止态自动显示检测结果；启用/禁用与排序保存后重建保持，至少一项由模型与 UI 共同保护。真实停止态 Google=timeout、GitHub=available，运行态规则检测两者 available，停止后 GitHub DIRECT available；检测不启动停止态服务，运行检测保留 session。只复用已导入的唯一授权来源，不下载订阅。其余 11 个外部服务没有真实验收，不能把固定夹具或已有 Go 单测当作当前服务联通证据。
- 深浅色六张 PNG 已目视：/tmp/services_card_dark-20261102.png、services_card_light-20261102.png、services_details_dark-20261102.png、services_details_light-20261102.png、services_manage_dark-20261102.png、services_manage_light-20261102.png（后面均位于 /tmp）。状态、加载、异常文案、开关与排序按钮可达；仅证明当前 KClash 布局，不作为 Flutter 像素一致证据。默认首页截图基线未修改。
- 失败保留：首轮 servicelive-054408 被“出口已变化，请重新检测”拒绝，增加独立过期异常和前台等待稳定后重试保护；servicelive-054705 的预置偏好在 Activity 重建后被保留的旧 ViewModel 覆盖，修正测试显式 reloadSettings 后最终通过。最终真实 direct 观察到一次路由过期，有限重试成功，原失败未丢弃。Go 初次从仓库根运行报 cannot find main module，改到 core 模块通过；一条含括号的 rg 正则失败，改固定字符串检索；临时脚本转义警告未影响执行。相关日志均保留于 /tmp。
- 配置保护 before-054246 与 after-055130 均 count=2 missing=0 expected=1（expected 是脚本最低门槛）；原文件名/大小/mtime 一致，真实用例另核对 ID 集合、当前选择和元数据。恢复原服务目标设置、首页布局、VPN 选择与停止态；不清数据、不创建/删除 Profile、不读写外层 1.yaml，不运行横屏/大字号。结束 dumpsys services 为空，shell 接口列表无 tun0。
- 最终 App SHA-256 077600611a635b248744e1a83438b012ab0431c75eacf1993b385d51ad03ad20；AndroidTest b574c134c2c86b1c672f287e55a936effb3d20de27ee482323f475dfdfb3fb04；与设备安装包一致，App 原生库仅 arm64-v8a，测试包无原生库。git diff --check 通过；未提交/推送。下一步继续服务出口 IP/质量、排序交互、route hits 复用及首页剩余菜单，然后 Profiles 与 Proxy Groups；Requests/DNS/Logs 次要视觉继续暂停。

### 2026-11-02：服务详情真实出口 IP 与质量入口 — DONE-VERIFIED（完整 parity 仍 PARTIAL）

- 复用既有 Go outboundIp 协议，CoreBridge 显式传 proxy-name，CoreRuntimeManager.queryServiceExit 在锁内核对路由快照、锁外等待网络、回写前再次核对本地 revision/运行态/core epoch/route version。停止态只允许 DIRECT；指定节点不存在时核心返回失败，不回退直连。UI 通过 Repository/Runtime 查询，不直接调用 gomobile；没有新增核心生命周期或改写 Go 检测器。
- 服务详情按检测结果代理链首节点查询真实出口，相同节点共享结果、最多两节点并发；只在详情可见且前台时启动，离开/后台取消 Kotlin 等待，Go 已发请求仍由自身预算结束。出口失败保留节点名并独立重试，不改变服务可用性；用户重测成功会重取 IP，以覆盖同节点轮换出口。路由变化保守清空，细粒度 route hits 复用尚未实现。
- 点击出口 IP 在同一个弹层进入质量页，复用首页质量 Repository、来源缓存、字段、复制和隐藏规则；详情绑定点击时 IP，返回/关闭/路由变化保护迟到结果。来源实际失败才显示逐项失败，整体异常/超时显示通用错误，不捏造来源失败；隐藏偏好仅限各自入口，不声称与首页同步。
- 最终命令：./gradlew :app:testDebugUnitTest :service:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64。App 204、Service 98、Core 26，共 328 项，失败/错误/跳过 0；Lint 0 errors、46 warnings、7 hints。最终构建日志 /tmp/kclash-service-exit-final2-build.log。在 core 目录执行 go test . -run 'Test.*OutboundIp|Test.*NodeHits|TestRouteCheck|TestProbeAnswersCarry' -count=1 通过，日志 /tmp/kclash-service-exit-go.log。
- Redmi wsvwypiz7xwslvl7、Android 16/API 36、arm64、1080×2340 竖屏、字号 1.0：DashboardInstrumentedTest 的 serviceExitHandlesLoadingFailureQualityRetryHideAndReturn、serviceExitAndQualityCaptureThemesAndLongIpv6 共 2 项；ServiceStatusRuntimeLiveTest#serviceExitIpAndQualityFollowDirectAndRealProxyWithoutChangingProfiles 1 项；原首页 IP 3 项、服务管理 2 项、首页编辑 3 项回归通过，合计 11 项。仅使用精确 Class#method，不运行横屏、大字号或整类 instrumentation。
- 最终真机日志 /tmp/kclash-service-exit-20261102-exitui-061913.log、exitlive-061929.log、ip-062329.log、serviceui-062402.log、regression-062514.log（后面均同前缀）。真实 MainActivity 只启用 GitHub 验收：DIRECT 与一个测速成功的真实代理节点出口实际不同；独立 ServiceExitStore 查询复核有效公共 IP。直连质量来源 IP_LOCATE（iplocate.io），代理质量来源 IP_QUERY（ipquery.io）；复制/隐藏/显式返回和停止清除旧出口与质量页通过。相邻查询可能为 IPv4/IPv6 或轮换出口，未断言逐字相等；系统返回键未单独真机验收。
- 深浅色长 IPv6 服务行和完整质量窗口四张 PNG 已目视：/tmp/service_exit_ipv6_dark-20261102.png、service_exit_ipv6_light-20261102.png、service_exit_quality_dark-20261102.png、service_exit_quality_light-20261102.png（后面均位于 /tmp）。质量窗口改用整窗截图，避免弹层 captureToImage 坐标偏移；示例地址只证明布局，不作为真实 IPv6 网络或 Flutter 像素一致证据。默认首页截图基线未修改。
- 失败保留：首轮真实 exitlive-061639.log 在独立复查时遭遇 ServiceRouteChangedException，测试改为最多 3 次重新读取当前快照后复查，生产路由保护不变；最终通过。单测首次在虚拟时钟等待 stop 文件 IO 时推进到查询超时，改为服务停止回调完成受控请求，验证停止不阻塞与旧结果拒绝。初次质量节点截图偏移，最终以完整窗口重新采集并目视。
- 安装前 before-061350 与最终 after-062613 均 count=2 missing=0 expected=1（expected 为脚本最低门槛），文件名/大小/mtime 一致；真实用例另核对 ID 集合、当前选择和元数据，finally 恢复 GLOBAL 映射、出站模式、原布局、服务/VPN 设置和停止态。只复用已导入的唯一授权来源，不下载订阅、不清数据、不创建/删除 Profile、不读写外层 1.yaml。最终 dumpsys activity services 为空，shell 接口列表无 tun0，字号 1.0。
- 最终 App SHA-256 0fcdf3d419e156c007fbba04d9c6df78e48f40ae1bfdb80b7263bda77e2a9e0c；AndroidTest 4c8a2c3c682a215ae3ea5d108c644d0cb2cc381aeda311c48ed1a727a4b558c1；均与设备 base.apk 哈希一致，App 原生库仅 arm64-v8a，测试包无原生库。核对记录 /tmp/kclash-service-exit-artifacts-20261102.json，git diff --check 通过。未提交/推送；服务拖动排序、route hits 复用、质量全来源覆盖/持久缓存、其余服务与节点仍待补齐，下一步继续排序与缓存复用，再补首页剩余菜单，然后 Profiles、Proxy Groups；Requests/DNS/Logs 次要视觉继续暂停。

### 2026-11-02：服务管理整行长按排序 — DONE-VERIFIED（完整 Dashboard 仍 PARTIAL）

- 参考本地 Flutter main 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7 的 lib/views/dashboard/widgets/service_status.dart:716-769、805-834：完整目标排列与禁用集合独立，整行 ReorderableDelayedDragStartListener 长按，至少启用一项；未 fetch 或合并上游。Kotlin 管理页新增 ServiceOrderEditor，沿用现有 Material 色板和上下移动按钮，整行长按预览、拖动提示、下边缘持续滚动与松手停止已实测。
- 视口持有手势与同一个 LazyListState，跨项排序不依赖被回收行；旧布局 key 未对齐前不重复换位，调整顺序时保持视口。复用首页按帧/密度计算的边缘滚动。仅松手后向 ServiceStatusViewModel.reorder 提交一次完整排列；取消、后台或离开丢弃预览，前台不恢复旧拿起。保存期间锁定排序/开关，失败保留旧顺序与错误反馈；缺项、重复或相同排列不写入，禁用集合不变。
- Android 官方 Drag, swipe, and fling 与 gestures API 正文已通过 smart-search fetch 核对，访问日期 2026-11-02：https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling 和 https://developer.android.com/reference/kotlin/androidx/compose/foundation/gestures/package-summary 。detectDragGesturesAfterLongPress 在长按后消费位移，onDragEnd/onDragCancel 分开处理。证据 /tmp/kclash-service-sort-gestures-20261102.md、/tmp/kclash-service-sort-longpress-20261102.md。
- 最终根 Wrapper：./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64，通过；App 207 项 JVM，失败/错误/跳过 0，新增 3 项覆盖完整排列校验、保存失败/重试/重建和保存并发。Lint 0 errors、46 warnings、7 hints；日志 /tmp/kclash-service-sort-final-build.log。Service/Core/Go 本轮未修改、未重跑，上一轮 98/26 项仅作基线，累计 XML 报告为 331 项，不能称本轮全量重跑。
- Redmi wsvwypiz7xwslvl7、Android 16/API 36、arm64、1080×2340 竖屏、字号 1.0：DashboardInstrumentedTest 的 serviceOrderDragPreviewsThenSavesOnceAndCancelKeepsOrder、serviceOrderEdgeScrollContinuesWithStationaryFingerAndStopsOnRelease、serviceOrderBackgroundCancelsPreviewAndDoesNotResumeScrolling 共 3 项；ServiceStatusRuntimeLiveTest#serviceOrderDragPersistsAndKeepsDisabledTargetsWithoutChangingProfiles 1 项；原服务管理及截图 2 项、出口质量 2 项共 8 项精确方法通过。上边缘持续拖动、系统返回时取消、真实进程重建未单独新增验收，Activity 重建和后台取消已有本轮证据。
- 最终日志 /tmp/kclash-service-sort-20261102-sortui-065428.log、sortlive-065513.log、serviceui-065653.log、exitui-065728.log（后面均同前缀）；不运行包含横屏/大字号的整类 instrumentation。正式页面从唯一已启用的 Google 行长按换位，独立回读真实偏好并重建页面，确认 GitHub 位于首项、Google 仍唯一启用且不可关闭。finally 恢复原偏好与布局，检查配置 ID/current/文件名/大小/mtime。固定宿主验证预览不落盘、松手一次保存、取消恢复、下边缘无移动事件仍持续滚动和后台取消。
- 深浅色管理页完整窗口 /tmp/services_manage_dark-20261102.png、/tmp/services_manage_light-20261102.png 已目视；未拿起时沿用弹层底色，拿起时使用 tonal elevation 强调。初次浅色截图抓到上一帧详情，等待列表实际绘制后重新采集；截图只证明当前 KClash 布局，不声称 Flutter 像素一致，默认首页基线未改。
- 失败记录保留：build2 中测试误用 openServiceDetails 辅助方法导致 AndroidTest 编译失败，改用现有 openServices；sortui-064141、064326、064449、064621 未通过，调试确认漏接 LazyColumn.state 导致长按读取空布局，接入同一状态后解决；sortui-064806 和 sortlive-064913 在取消/重建后首项离屏断言失败，保留存储断言并先滚回首项后通过。打包过程中读取 APK 导致 INSTALL_PARSE_FAILED_NO_CERTIFICATES，等待构建完成、ZIP 完整性核对后安装成功；sortui-065010 是旧测试包失败，未计入最终验收。临时 ServiceOrderDebug 日志已移除。
- 最终 App SHA-256 e1a98948d1d7ca2a4cc97b9c25ec46b030471e2a527cc3c957cfa206801bb61e；AndroidTest f7ecda367b427fe0d9695b55de3fcefd2fa5ca579d0506926aeeae4d35d73325；均与设备 base.apk 哈希一致。App 原生库仅 arm64-v8a，测试包无原生库；记录 /tmp/kclash-service-sort-artifacts-20261102.json。不主动启动 VPN/纯代理，不下载订阅、不读写外层 1.yaml、不清数据、不创建/删除 Profile。下一步继续服务 route hits 细粒度缓存复用，再补首页剩余菜单，然后 Profiles 与 Proxy Groups；质量全来源/持久缓存和完整视觉仍待补齐。
- 最终配置保护 /tmp/kclash-service-sort-20261102-before-064013.log 与 after-065840.log 均为 count=2 missing=0 expected=1，文件名/大小/mtime 相同；结束服务为空、shell 接口无 tun0、字号 1.0。git diff --check 通过，未提交或推送；原有工作区改动保留。

### 2026-11-02：服务 route hits 逐项核验缓存复用 — DONE-VERIFIED（完整 Dashboard 仍 PARTIAL）

- 核对本地 Flutter main 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7 的 lib/providers/routed_probe.dart，以及既有 Go route.go/probe.go/service_check.go 协议；未 fetch/merge 上游。Kotlin CoreServiceCheck 保留完整 hits，CoreBridge 接入 checkRoutes 和严格结果解析，CoreRuntimeManager 为唯一核验入口。任一命中损坏就放弃该服务的整项缓存复用，但不丢弃当次可显示的检测结果；held 缺项、强制类型转换、负数/非整数/溢出版本均拒绝。
- 新增独立 serviceCacheRevision，配置、网络、局域网重载和核心生命周期变化强制失效；手动节点/模式变化以及核心自动选择变化进入逐项核验。同一 core epoch、非 timeout/failed、完整命中且核心 held=true 的服务才保留原检测时间，仅更新已核验快照；其余目标按既有可见策略重测。每次变化先隐藏旧结果，等待 1.5 秒稳定，后台不启动检测或核验。稳定等待期间的手动检测保留意图到路由就绪，离开/后台取消；仍只自动检测卡片启用列表前两项。
- 核验在启停锁外等待，前后核对本地 revision、缓存失效计数和核心快照，取消与迟到结果不能写回。核验使用缓存原来的失效计数，避免网络变化在 UI 消费通知前被新计数掩盖。服务结果复用不包括出口 IP/质量，后者清空后按当前快照重新获取，以覆盖同节点轮换出口。仅进程内缓存，首页出口 route hits 和持久缓存仍未实现。
- JVM 共 344 项（App 213、Service 103、Core 28）通过，失败/错误/跳过 0，本轮新增 13 项。覆盖混合 held、检测时间保留、缺失/损坏命中、核验失败/伪造结果、epoch/会话/网络强失效、自动路由变化、后台与迟到、手动等待意图、出口质量清除、停止不阻塞和版本竞争。根 Wrapper 最终完整命令为 ./gradlew :app:testDebugUnitTest :service:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64，日志 /tmp/kclash-service-cache-final3-build.log；后续只修 instrumentation 准备/断言，测试包最终重建日志 /tmp/kclash-service-cache-testfix2-build.log，再跑 Lint /tmp/kclash-service-cache-final-lint.log，0 errors、46 warnings、7 hints。core 目录 go test . -run 'TestRouteCheck|Test.*NodeHits|TestProbeAnswersCarry' -count=1 通过，日志 /tmp/kclash-service-cache-go.log；本轮未修改 Go。
- Redmi wsvwypiz7xwslvl7、Android 16/API 36、arm64、1080×2340 竖屏、字号 1.0，ServiceStatusRuntimeLiveTest#serviceRouteCacheReusesUnchangedExitAndRechecksChangedExitAndNetwork 通过：正式 MainActivity 用真实 GitHub 检测取得完整 hits；DIRECT 模式修改 GLOBAL 选择，独立核心核验 held=1，页面新快照保留原时间及完整结果；手动刷新、调用正式 networkChanged 通知、改 GLOBAL 模式使用测速可用的真实代理、停止均产生新检测时间，运行会话不变。网络用例验证的是正式通知处理，未实际切换 Wi-Fi/蜂窝。日志 /tmp/kclash-service-cache-20261102-cachelive-072212.log。
- 真实出口回归 ServiceStatusRuntimeLiveTest#serviceExitIpAndQualityFollowDirectAndRealProxyWithoutChangingProfiles 1 项通过，DIRECT 与代理公共 IP 实际不同，独立出口查询通过，质量成功来源分别为 IP_LOCATE 和 IP_QUERY，复制/隐藏/返回/停止清除通过。DashboardInstrumentedTest 服务管理与主题 2 项、长按预览/边缘滚动/后台取消 3 项、服务出口质量交互及主题 2 项通过，共 9 项精确功能方法；前后配置保护另各 1 项。不运行横屏、大字号或整类 instrumentation。日志同 /tmp/kclash-service-cache-20261102- 前缀的 exitlive-072301、serviceui-072341、sortui-072420、exitui-072458；本轮未改视觉组件，截图仅为既有回归产物，未新增 Flutter 像素一致性结论。
- 失败保留：unit2 的停止并发夹具初始为已停止状态，不执行服务停止回调，导致受控核验等待超时；改为先启动模拟运行服务，停止回调完成受控请求后验证过期拒绝。cachelive-071752 初始页面未重建，未显示服务卡片，初始等待超时；增加重建及滚动到卡片。cachelive-072011 将结果绑定切换瞬间的精确版本，15 秒等待超时；最终改为等待后续核验快照并严格比对原结果/检测时间，另加独立核心 held 核验。工具层路径遗漏与正则括号错误已改显式工作目录/固定字符串检索，未改文件；失败日志保留，不计入通过项。
- 配置保护 before-071512 和 after-072542 均 count=2 missing=0 expected=1（expected 为脚本最低门槛），文件名/大小/mtime 相同；真实用例另核对 Profile ID 集合、当前选择及元数据。finally 恢复原 GLOBAL 映射、模式、VPN 偏好、服务设置、首页布局与停止态。只复用设备已有授权 Profile，不下载订阅、不读写外层 1.yaml、不清数据、不创建/删除 Profile。结束 dumpsys services 为空、shell 接口无 tun0、字号 1.0。
- 最终 App SHA-256 52a8e6de30910206e74edf534e198740d7b01d3e43f81eedeb338b0fd3a1b23f，AndroidTest 89a67beceb04883020e3c4743b5a7ba83afe184355445beb02dc1505f4036ca3，均与设备 base.apk 一致；App 仅 arm64-v8a，测试包无原生库，记录 /tmp/kclash-service-cache-artifacts-20261102.json。git diff --check 通过，未提交/推送，保留原工作区改动。下一步继续首页剩余菜单和按钮，再推进 Profiles、Proxy Groups；首页出口 route hits、质量全来源/持久缓存、13 项外部服务全覆盖与完整视觉 parity 仍待补齐，Requests/DNS/Logs 次要视觉继续暂停。

### 2026-11-02：首页当前配置详情真实统计 — DONE-VERIFIED（完整 Dashboard 仍 PARTIAL）

- 对照本地 Flutter main 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7 的 lib/common/task.dart:89-98 与 lib/views/dashboard/widgets/profile_detail.dart:40-54、164-208：代理组/内置节点/规则按最终配置 list 数量计数，节点再加已加载 Proxy Provider 数量，Providers 只计核心外部列表；不将 GLOBAL/DIRECT、RULE-SET 展开或 Rule Provider 条目重复算作节点。本轮未 fetch/merge 上游，Go 协议未修改。
- CoreBridge 接入既有 getExternalProviders，仅返回 CoreProviderCounts，不将名称、路径或订阅信息传入统计 UI。严格拒绝缺失字段、未知类型、重复名称、负数/非整数/字符串/溢出数量，规则 Provider 的条目数不累加节点；空数组为真实零。CoreRuntimeManager.profileStatistics 只读已加载暂存 config.yaml（上限 8 MiB），不调用配置加载、启动、脚本执行或订阅下载；配置未加载时返回受控失败，不把源文件或旧核心数量冒充当前统计。
- 核心快照、当前 Profile、loadedConfigKey、源/脚本元数据、本地 revision 均在读取前后核对；Provider 回调和 YAML 解析在启停锁外，停止不等待统计完成，切配置或停止期间的旧结果拒绝。底层不把配置正文放入 data class/toString 或 UI；失败只显示受控文字。预览原有 2 MiB 上限保持，统计有独立的 8 MiB 读取预算。
- 新 ProfileStatisticsViewModel/Panel，首页详情显示代理组、节点、规则、Providers 的两列统计。只在详情可见且应用前台时每 5 秒刷新；关闭、后台、Profile/配置 revision/核心状态变化清除旧数量并取消旧读取，迟到结果不能回写。加载显示省略号、失败显示破折号与重试，真实零显示 0；详情直接展开至内容高度，底部增加导航安全区，运行配置、同步和查看全部配置保持可达。同步运行态门禁未在本轮变更。
- 本轮 JVM 共 355 项，App 217、Service 108、Core 30，失败/错误/跳过 0，新增 11 项覆盖最终脚本输出、Provider 叠加、畸形统计、空值/溢出、未加载/错误 Profile/文件竞争、停止不阻塞、前台/可见轮询、失败重试/超时、状态变化和不可取消迟到。完整构建日志 /tmp/kclash-profile-stats-final-build.log（App/Service/Core 单测、Debug、AndroidTest、Lint）；随后仅改详情展开与导航留白，App 217 项及构建/Lint 再通过，日志 /tmp/kclash-profile-stats-final3-build.log。Lint 0 errors、46 warnings、7 hints。core 目录 go test . -run 'TestExternalProvidersSkipsInlineProviders|TestLookupExternalProviderMatchesTheListedPrecedence' -count=1 通过，日志 /tmp/kclash-profile-stats-go.log。
- Redmi wsvwypiz7xwslvl7、Android 16/API 36、arm64、1080×2340 竖屏、字号 1.0：DashboardInstrumentedTest#profileStatisticsShowsLoadingFailureRetryAndRealZeroCounts、profileStatisticsCapturesBothThemesAndKeepsProfileActionsReachable，及原详情入口、运行配置加载重试主题、运行态快切共 5 项通过；ProfileStatisticsRuntimeLiveTest#appliedProfileStatisticsFollowStoppedRunningAndHotSwitchWithoutChangingFiles 1 项通过。最终日志 /tmp/kclash-profile-stats-20261102-statsui-074920.log、statslive-074953.log；只用精确 Class#method，不运行整类/横屏/大字号。正式 MainActivity 停止态、运行态、后台返回、两份现有配置间热切与恢复原选择、停止后统计均与管理器读取一致，热切核心会话保持。真实 Provider 非零下载/热更新未另外制造现场，不能把单元叠加夹具声称为真实外部 Provider 更新验收。
- 深浅色完整窗口 /tmp/profile_statistics_dark-20261102.png、/tmp/profile_statistics_light-20261102.png 已实际目视，四项数值、元数据、三个底部动作及导航安全区完整。初次节点 captureToImage 发生弹层坐标偏移，改整窗采集；整窗又暴露半展开初始裁切底部动作，改为直接展开和安全区后重建复验。固定夹具只证明布局，不作为真实配置数量或 Flutter 像素一致性证据；默认首页截图基线未更改。Impeccable 用于原生呈现和目视，未以 HTML/CSS 扫描代替 Compose 验收。
- 失败记录保留：unit1 的 semantics 漏传属性块，编译报 No value passed for parameter properties；补空属性块后单测通过。final2 加安全区时遗漏 rememberModalBottomSheetState/navigationBarsPadding 导入，补导入后 final3 构建通过，未安装未完成的 APK。旧 YamlConfigCodec 路径读取失败后定位到 service/ProfileScript.kt；两个只读子代理均因工具未传任务正文而无结果，主线程完成源码核对。
- 配置保护 before-074140 与 after-075044 均 count=2 missing=0 expected=1（expected 是脚本最低门槛），检查两份配置完整及文件名/大小/mtime 一致，真实用例另核对 ID/current 和元数据；finally 恢复原 Profile、VPN 偏好、布局及停止态，不改变模式或节点映射。只复用设备已存在授权配置，不下载订阅、不读写外层 1.yaml、不清数据、不新建/删除 Profile。最终服务为空、shell 接口无 tun0，字号 1.0。
- 最终 App SHA-256 7bac72305bd69ac46731e5b4c452aac6150d6153f4ceba3bb2a8009e7de1fee5，AndroidTest 670c93fa3e975a4b02f2918ed2be312323385ae86c76b7bda5aea149f777ec03，与已安装 base.apk 一致；App 仅 arm64-v8a，测试包无原生库，记录 /tmp/kclash-profile-stats-artifacts-20261102.json。git diff --check 通过，未提交/推送，原有工作区改动保留。下一步补当前配置外部 Providers 明细和单项更新入口，再推进 Profiles、Proxy Groups；首页出口 route hits、质量全来源/持久缓存和完整视觉仍待补齐，观测页次要打磨继续暂停。

### 2026-11-02：首页当前配置外部 Providers 明细与 HTTP 单项更新

- 当前切片 DONE-VERIFIED。参考本地 Flutter main 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7 的 lib/views/proxies/providers.dart，以及现有 Go 外部 Provider 协议；本轮未 fetch/merge 上游。详情新增名称、代理/规则类型、条目数、来源类型和更新时间，HTTP 类型提供单项更新，文件及其他类型只读；加载、失败重试、真实空态、更新中、失败后重试和成功反馈均接入。批量更新、资源编辑/侧载、订阅用量和完整 Providers parity 继续 PARTIAL。
- CoreBridge 的明细模型不包含路径、订阅正文或订阅信息；复用严格数量校验，校验必需元数据，兼容 API 24 的 RFC3339/纳秒及 Go 零时间。单项更新仅以空字符串消息判定成功，不将缺失/错误类型结果当作成功。界面只使用受控错误分类，不透传核心错误正文。
- CoreRuntimeManager 读取前后核对 Profile、loadedConfigKey、源/脚本元数据、revision 和核心路由版本。下载在启停锁外进行，独立更新门禁一直保留到 Go 回调结束，UI 取消不能被当作网络已经停止；停止/切配置无需等待下载。新增 updateExternalProviderScoped 协议，Go 在 configMu 内核对核心/路由版本并捕获 Provider 对象，拒绝过期版本、缺失项和非 HTTP 类型；下载在 configMu 外执行，保留旧 Flutter 字符串更新协议。返回后 Kotlin 再复核配置与会话，迟到结果不能冒充另一配置成功；同一配置完成后使代理组、统计、IP/服务路由检测失效并重读。
- Providers 每 5 秒仅在详情前台刷新；关闭、后台或换配置清除旧列表，下载独立于轮询，重复点击不重复调用。正式首页的详情窗口提升至 KClashApp 页面持有，卡片只发出打开事件，避免 LazyColumn 前一张运行卡片高度变化时回收详情宿主、留下隐藏弹层；固定宿主保留独立入口。
- JVM 全量 363 项通过（App 219、Service 112、Core 32，新增 8 项），失败/错误/跳过均为 0；Debug/AndroidTest 和 Lint 通过，0 errors、46 warnings、7 hints。完整日志 /tmp/kclash-providers-final-build.log；修复详情持有位置后 App 单测及两包/Lint 再通过，/tmp/kclash-providers-sheet-build.log。Go 定向 TestScopedProviderUpdate、TestUpdateExternalProvider、外部列表/查找优先级通过，/tmp/kclash-providers-go.log；覆盖版本过期、文件只读、缺失、重复更新、网络错误分类和下载不持有配置锁。
- Redmi wsvwypiz7xwslvl7、Android 16/API 36、arm64、竖屏默认字号：DashboardInstrumentedTest 精确运行 providerDetailsSupportReadOnlyUpdatesLoadingFailureRetryAndEmpty、providerDetailsCaptureThemesWithActionsReachable、原详情入口和运行态配置快切，共 4 项通过，/tmp/kclash-providers-20261102-providersui-083355.log。深浅色整窗 PNG /tmp/profile_providers_dark-20261102.png、/tmp/profile_providers_light-20261102.png 已实际目视，长名称、HTTP 更新按钮、本地只读提示与底部三项操作完整。
- 正式 MainActivity 的 ProfileStatisticsRuntimeLiveTest#appliedProfileStatisticsFollowStoppedRunningAndHotSwitchWithoutChangingFiles 通过，/tmp/kclash-providers-20261102-statslive-083259.log：两份已有配置分别读取 8/90 项外部 Provider；停止态通过真实按钮成功更新一个已有 HTTP 规则 Provider，保持停止态；后台返回、纯代理运行、两份配置间热切、恢复原选择及停止后列表与当前核心一致，热切保持会话；不存在项被拒绝。真实 Proxy Provider 下载和运行态下载未另外执行，不把规则资源更新推广为所有 Provider 类型在线覆盖。
- 失败记录保留：首次错误分类测试的 cause 断言未考虑协程栈恢复包装，调整为核对异常链不含原始核心错误/敏感正文；首次真机比较不同时间快照失败，随后等待固定旧快照在运行态超时，改为比较当前一致快照；后续首行/按钮在停止后不可见，几何与截图确认弹层确实隐藏，不能仅靠增加等待解决。将正式详情持有位置提升到页面后完整链路通过。相关失败日志保留为 /tmp/kclash-providers-20261102-statslive-081401.log、081628.log、081816.log、082040.log、082308.log、082528.log、082758.log；失败截图仅作本机诊断，不作为视觉基线。
- 验收前后 MainActivity 配置保护各 1 项通过，/tmp/kclash-providers-20261102-before-081143.log、after-083429.log；两份 Profile ID/current、文件名/大小/mtime 保留，finally 恢复原选择、VPN 偏好、首页布局与停止态。最终服务记录 0、无 tun0、字号 1.0。不下载 Profile 订阅正文、不新增/删除 Profile、不清数据、不读写外层 1.yaml；真实资源更新只复用设备现有配置。
- 最终 App SHA-256 bf21d2619526594c323dc47d29ba4bf8c82fa0aabd4bb78a2a4fb247b19f4193；AndroidTest fab7ee293e8ce2a108b5fc702d893994bb2657a8369ae8443497e0f1ef7c7a77。均与设备 base.apk 一致；App 仅 arm64-v8a，测试包无原生库，记录 /tmp/kclash-providers-artifacts-20261102.json。git diff --check 通过，未提交/推送，保留原工作区改动。下一步继续 Profiles 配置管理菜单与 Proxy Groups 剩余行为；Providers 批量/编辑/侧载、首页出口 route hits、质量全来源/持久缓存和完整视觉 parity 继续排队，观测页次要打磨继续暂停。

### 2026-11-02：提交前路线图与累计改动核对

- 本次汇总首页真实运行时间/速度历史、出口 IP/质量、可选内存/内网 IP/VPN/服务状态、拖动排序、服务 route hits 复用、停止态节点管理、运行态配置热切、配置预览/统计和 Providers 明细/HTTP 单项更新的累计改动。未新增产品行为；上游行为参考继续使用本地 main 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7，不合并 Flutter 主线。
- 对照现有实现和分项验收更新页首、当前快照、架构说明、里程碑图、7.3/7.4/7.6/7.7 与 N2/N4：M6/M9 改为 PARTIAL；移除 Direct 空态、运行中禁止选择和 VPN/TUN 未接入的旧结论，标明 Providers 明细/HTTP 单项更新已完成，完整 Providers 与 UI/UX 保持 PARTIAL。当前下一步为 Profiles 管理菜单、导入/预览和结构化表单，再推进 Proxy Groups 叶子协议、按组滚动恢复和视觉；观测页次要打磨继续暂停。
- 本次执行 ./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64，通过；随后对 :app:testDebugUnitTest、:service:testDebugUnitTest、:core:testDebugUnitTest 使用 --rerun 强制执行，再独立强制执行 :common:testDebugUnitTest。四模块合计 394 项（219/112/32/31），失败/错误/跳过均为 0；此前记录的 363 项只包含 App/Service/Core。Lint 为 0 errors、46 warnings、7 hints。当前本机日志 /tmp/kclash-submit-20261102-gradle.log、tests.log、common.log。
- core 目录执行 go test . -run 'TestScopedProviderUpdate|TestUpdateExternalProvider|TestExternalProvidersSkipsInlineProviders|TestLookupExternalProviderMatchesTheListedPrecedence|Test.*Lan|Test.*Memory|Test.*Route|Test.*Outbound' -count=1，通过；日志 /tmp/kclash-submit-20261102-go.log。仅代表这些定向测试，不声称全 Go 测试通过。
- 本地重新核对 Debug/AndroidTest SHA-256 与上个 Providers 真机验收节一致（bf21d261… / fab7ee29…），App 仅 arm64-v8a，测试包无原生库；本次未重新安装、重跑真机或读取设备包，未启动服务或访问真实订阅。旧 /tmp 验收文件在本次核对时已不存在，历史日志路径和设备结果只作为当时记录保留，不能据此声称本次重验设备。
- 确认 origin/kotlin 与提交前 HEAD 一致；本次不创建 Tag 或 Release，推送会按现有 Kotlin workflow 触发验证，远端 CI 结果与本地通过分开报告。暂存前核对累计文件范围并扫描新增内容，未发现真实订阅链接、凭据、生成物或临时日志；提交前继续执行暂存差异检查。

### 2026-11-02：本地 YAML 导入预检与确认

- 本地文件选择改为先异步读取与语法预检，再展示文件名、字节数和校验结果；有效文件由用户确认后导入，无效/空文件禁用导入，取消不触发写入。预览不展示 YAML 正文，避免订阅凭据或代理口令进入界面日志/截图。
- `ProfilesViewModel.validateFileImport` 仅回传受控成功/失败状态；`ProfileRepository.addFileProfile` 在创建 ID、原子写文件和 Room 插入前再次调用同一 validator。校验范围为 YAML 可解析性，不声称 Mihomo 核心已接受配置语义。
- App 219 项 JVM 单测通过，失败/错误/跳过为 0；Debug APK、AndroidTest APK 构建和 Lint 通过（0 errors、46 warnings、7 hints）。Redmi `wsvwypiz7xwslvl7` 上 `ProfileUiInstrumentedTest` 22 项通过，覆盖有效预览确认、无效预览禁用导入和取消；`ProfilesRepositoryPanelInstrumentedTest#invalidFileImportDoesNotCreateProfileOrWriteProfileFile` 1 项通过。该轮没有启动 VPN/Mihomo、访问订阅或读取/导入根目录 `1.yaml`；真实 SAF picker 与正式页面导入流程仍待端到端验收。
- Profiles 页面仍为 `PARTIAL`。下一步先补卡片菜单 parity：非当前 Profile 的配置预览、订阅链接复制、二维码与文件导出；再补 age secret key 等表单差异，之后推进 Proxy Groups 叶子协议与按组滚动恢复。Dashboard 首页出口 route hits、质量来源全覆盖/持久缓存、Providers 批量/编辑/侧载和完整 UI parity 仍排队。

## 12. 路线图维护规则

- 每次功能状态、验收证据、范围或优先级发生变化，都必须同步更新本文件，并与对应代码或文档在同一轮提交。
- 功能完成必须同时更新状态、证据和验收门槛。
- 只实现了 Bridge 或底层模型时标记 `PARTIAL`，不能标记 `DONE-VERIFIED`。
- 只通过构建而没有真机证据时，最多标记 `DONE-UNIT`。
- 新增上游能力先更新矩阵，再决定是否进入 Android 手机范围。
- 任何改变核心生命周期、数据边界、包名、ABI、签名或发布策略的决定，都要同步到 ADR。
- 本文件记录路线和证据，不替代具体代码注释、测试用例或发布清单。


### 2026-11-02：Profile 卡片链接、二维码与 YAML 系统导出

- 本地 Flutter `main` / `upstream/main` 参考均为 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`，核对 `lib/views/profiles/profiles.dart` 的 `_menuItems`：URL 提供原订阅链接复制、二维码及原缓存文件导出，FILE 提供文件导出，CUSTOM 不提供这组三项。Kotlin 按此分层加入“更多”子菜单，不改变当前选择、不下载订阅、不执行脚本；运行中仍可用，原编辑/删除等写操作的守卫保持有效。
- `ProfileSharingViewModel` 管理分享状态，订阅复制标记敏感剪贴板；二维码在 Default 线程用 ZXing 3.5.3 编码原 URL，使用 UTF-8、M 纠错及四模块静区，关闭/后台清除、取消旧编码并拒绝迟到结果。深浅主题均使用整数像素绘制黑码白底，长链接失败可回到复制操作。
- `ProfileExportReader` 按所点 ID 读取 Repository 原缓存字节，限定 8 MiB、拒绝缺失/空缓存，名称移除路径与控制字符；不借用仅支持本地配置的编辑读取入口。打开系统保存窗口前冻结内存快照；`ProfileSharingHost` 只在前台发起 `CreateDocument(application/yaml)`，已打开标记跨 Activity 重建保留，取消零写入，结果只消费一次，写入在 IO 线程执行，异常正文不进入页面。进程被终止时不会恢复配置正文或自动重导出。
- App 231 项 JVM 单测通过（新增 ViewModel/二维码 9 项、缓存导出 3 项），失败/错误/跳过为 0；覆盖原 URL/类型限制/敏感复制、二维码编码解码及迟到保护、非当前 ID 快照、重复点击/保存窗口 claim、取消、一次消费、错误脱敏和原字节/文件名/读取预算。Debug 与 AndroidTest 构建、Lint 通过：0 errors、46 warnings、7 hints。Service/Core/Go 本轮未改、未重跑；394 项仍是历史四模块统计。
- Redmi `wsvwypiz7xwslvl7`（Note 8 Pro、API 36、1080×2340 竖屏、字号 1.0、arm64-v8a）精确方法最终通过 9 项：`ProfileSharingInstrumentedTest` 的 `urlSharingMenuWorksForNoncurrentProfileWhileRunning`、`fileAndCustomMenusExposeOnlySupportedActions`、`qrDialogRendersDecodableCodeInBothThemesAndCloses`、`sensitiveClipboardKeepsOriginalLink`；`ProfileSharingSafInstrumentedTest#realSystemPickerCancelsWithoutWritingThenSavesExactAuthorizedFileSnapshot`；`ProfileUiInstrumentedTest` 的 `localFileImportPreviewShowsMetadataAndConfirmsOnlyAfterReview`、`invalidLocalFilePreviewBlocksImportAndCanBeCancelled`、`profileMutationButtonsDisableWhileCoreIsRunning`；`MainActivityInstrumentedTest#mainActivityRendersHomeAndProfileEntry`（正式库 inspect）。未运行整类、横屏或大字号测试。
- SAF 用例只读外层授权 `1.yaml`，先校验 SHA-256，再将同一字节用于隔离的内存 Room / 私有缓存夹具；没有随机生成或替换真实验收 YAML。通过真实卡片菜单和 Android DocumentsUI 先取消、再保存，用正式 Repository 与文档 Writer 逐字节回读核对；确认非当前配置导出不修改选择、缓存大小/mtime 或 Room 记录数，结束只删除本用例创建的临时导出文件与独占缓存目录。该证据覆盖正式组件的系统保存链路，不作为真实 MainActivity 已有订阅端到端证明，也不替代前一轮仍待验的 SAF 导入链路。
- 深浅二维码两张 PNG 已实际查看，渲染后二维码均能解码回原固定 URL；图片位于 `/tmp/kclash-profile-sharing-visual-20261102/`。最终回归日志 `/tmp/kclash-profile-sharing-regression-final-20261102.log` 为 `OK (8 tests)`，系统导出日志 `/tmp/kclash-profile-sharing-saf-device-recovered-20261102.log` 为 `OK (1 test)`。不将固定夹具或截图视为完整 Flutter 像素 parity。
- 失败保留：首轮新增测试误用了不存在的 `Profile.file()`，改为现有 Profile 加 FILE 类型后构建通过；SAF 首轮阻塞等待没有推进 Compose 异步效果、未收到保存 Intent，改用 Compose 测试时钟后复验；期间 Android `startSeq` 不一致杀死测试进程，终止挂起客户端并重新启动应用后通过，系统日志保留。未清应用数据、重导订阅或启动 VPN/Mihomo。
- 正式库边界：保留前置检查实际返回 `count=0 missing=0`，因至少一份配置的断言失败，不能记为旧配置已保留；最终正式 MainActivity inspect 仍为 0 条。PackageManager 首次安装时间为本轮设备时间 `2026-11-02 12:11:14`，与首次安装窗口一致，但本轮没有安装前的正式库快照，不推断历史两份配置的去向。该失败日志为 `/tmp/kclash-profile-sharing-preserve-before-20261102.log`；本轮没有补建正式 Profile，不能沿用历史的两份保留结论。
- 最终 App SHA-256 `be3857c8cabef6ce7b0533b41de50d5dadd5684a0587268ec4e8ad6d7c08a6a7`；AndroidTest `750736d88b9fec83ab09f55bab9e2a35f8236834e85f035b262c57aa84cc2adc`，均与设备已安装 base.apk 一致。App 原生库仅 arm64-v8a。未访问任何真实在线订阅；外层 `1.yaml` 未修改。
- 三项分享切片为 `DONE-VERIFIED`，完整 Profiles 仍为 `PARTIAL`。非当前 Profile 的运行配置预览尚未实现：当前 `CoreRuntimeManager.previewConfig(id)` 仅允许当前 ID，需要先补独立渲染与并发保护，不能通过临时切换当前配置模拟预览。下一步先补该能力，再补 age secret key 等表单差异，随后推进 Proxy Groups 叶子协议与按组滚动恢复。


### 2026-11-02：Profiles 非当前配置只读预览

- 对照本地 Flutter `main` / `upstream/main` 的 `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`：URL/FILE 在“更多”首项提供运行配置，CUSTOM 主菜单直接提供；读取目标 ID，不切当前配置、没有保存操作。本轮沿用现有 Material 3 只读 Sheet、加载/失败/重试/关闭，三种类型运行中均可打开，编辑/删除等写操作仍受原有守卫保护。
- `ActiveProfileProvider` 增加 `profileById(id)` 与只读 `activeProfileId()`；Repository 精确读取目标 Room 记录、自己的覆盖与脚本，不回退到其他卡片，不写当前偏好。当前 ID 查询不解析当前绑定脚本，因此当前配置脚本缺失也不阻挡另一份有效配置预览。
- `CoreRuntimeManager.previewConfig(id)` 仅在“运行中且目标为当前已加载配置”时返回已应用输入，并核对 loaded key；其余情况冻结目标源文件/脚本正文和 LAN 设置，在启停锁外复用脚本→Provider/DNS 覆盖→LAN 处理链。`ProfileScriptApplier.applyContent` 允许执行冻结脚本，解析器仍按调用创建；预览不启动核心、不加载 Provider、不写暂存或源文件。输入、脚本和结果仍受 2 MiB 预览预算约束。
- 渲染后复核目标元数据、当前 ID、运行状态/路由 revision、LAN 设置及源文件/脚本原字节；等长、相同 mtime 的替换也拒绝。慢脚本期间停止和运行态热切可完成，旧预览返回“配置或核心状态已变化，请重试”。`RuntimeConfigPreviewHost` 将窗口绑定目标卡片与打开页面，目标/脚本元数据、当前选择、运行态或页面变化即清正文；后台和宿主离开也清除，不自动恢复。ViewModel 继续取消旧请求并用 generation 拒绝迟到结果，解析器/脚本异常仅展示受控信息。
- App 231、Service 116 项 JVM 共 347 项通过，失败/错误/跳过为 0；新增 4 项 Service 测试覆盖停止/运行态非当前配置变换和零核心副作用、损坏当前脚本隔离、6 种目标/文件/脚本/LAN/选择变化，以及慢预览不阻塞热切并拒绝旧会话。既有停止态变换、运行态已应用输入不重跑脚本、缺失/超预算、停止不阻塞和 ViewModel 迟到保护回归通过。Debug/AndroidTest 构建、Lint 通过（0 errors、46 warnings、7 hints）。Core/Common/Go 本轮未改、未重跑；历史四模块 394 项不算本轮结果。
- Redmi Note 8 Pro `wsvwypiz7xwslvl7`（API 36、1080×2340 竖屏、字号 1.0、arm64-v8a）精确 10 方法通过：`ProfilePreviewInstrumentedTest` 的 `allProfileTypesOfferReadOnlyPreviewWithoutSelectingInStoppedAndRunningStates`、`noncurrentPreviewStaysOpenInProfilesAndClearsOnTargetSelectionRuntimeOrPageChanges`、`backgroundClearsPreviewAndReturningDoesNotRestoreContent`、`noncurrentPreviewRendersBothThemesAndSupportsFailureRetryAndClose`；`ProfilePreviewRepositoryInstrumentedTest#noncurrentPreviewUsesExactRoomIdWithoutChangingSelectionFilesOrCallingCore`；`DashboardInstrumentedTest#dashboardProfilePreviewLoadsRetriesAndClosesInBothThemes`；`ProfileUiInstrumentedTest#profileMutationButtonsDisableWhileCoreIsRunning`；`ProfileSharingInstrumentedTest` 的 `urlSharingMenuWorksForNoncurrentProfileWhileRunning` 与 `fileAndCustomMenusExposeOnlySupportedActions`；`MainActivityInstrumentedTest#mainActivityRendersHomeAndProfileEntry`（inspect）。没有整类、横屏或大字号测试。
- Repository 真机用例校验外层 `1.yaml` SHA-256 后，只把该原字节写入独占缓存与内存 Room 夹具，分别从 URL/FILE/CUSTOM 非当前卡片打开真实 Repository→CoreRuntimeManager→ViewModel→Sheet；核对预览字节、目标 ID/名称、当前选择、源文件大小/mtime、已暂存文件原字节/mtime、Room 数量及 STOPPED 状态。核心 Bridge 操作一律拒绝，实际操作调用为 0，服务和运行意图写入也拒绝；当前夹具故意绑定缺失脚本，其他目标仍可预览。不随机生成或替换真实验收 YAML，不创建正式 Profile，结束只删除核对过路径的独占缓存。
- 新增五方法日志 `/tmp/kclash-profile-preview-device-20261102.log` 为 `OK (5 tests)`，既有回归 `/tmp/kclash-profile-preview-regression-20261102.log` 为 `OK (5 tests)`。浅色/深色固定正文截图 `/tmp/kclash-profile-preview-visual-20261102/profile_noncurrent_preview_light.png` 与 `profile_noncurrent_preview_dark.png` 均已实际查看，配置名、来源、刷新/关闭、只读正文均正常；不视作 Flutter 像素 parity。构建日志使用 `/tmp/kclash-profile-preview-build-final-20261102.log`，最终测试包构建为 `/tmp/kclash-profile-preview-test-final-build-20261102.log`。
- 实际运行核心下的非当前预览、真实 MainActivity 非空订阅的端到端仍未单独验收；运行态与热切的核心隔离由确定性 Service 单测证明，真机运行状态菜单由固定宿主验证，不能混为真实 VPN/代理验收。本轮未启动核心/VPN、访问真实在线订阅或修改外层 `1.yaml`；正式库 inspect 仍为 0 条，未补建配置、不沿用历史非空库的保留结论。
- 最终 Debug App SHA-256 `26fbf6a9a55b7435bff2740558971dcf8116f56d7d9541326caa8c3c46c8a551`；AndroidTest `4721ccc90d5f673250c8c2d6785e56dab542a0dddc04bfc7c02928cf2d45cdfc`。App 仅 arm64-v8a；安装哈希单独核对。定向方法均通过，本轮无构建或测试失败；起初服务路径检索误用了不存在的 kotlin 目录，实际源码位于 java 目录，没有状态变更。
- 该只读预览切片为 `DONE-VERIFIED`，Profiles 整体仍为 `PARTIAL`。下一步补 age secret key 等结构化表单差异，再推进 Proxy Groups 叶子协议类型、按组滚动恢复与视觉；SAF 导入端到端、Providers 批量/编辑/侧载、首页出口 route hits 和质量全来源/持久缓存继续保留待办。

### 2026-11-02：Profiles age 密钥表单与解密链路首片（分层验收）

- 对照 Flutter `main:lib/core/controller.dart`、`lib/models/profile.dart` 与 Profiles actions：仅第零字节的准确 armor 前缀触发解密；明文即使填写了密钥也不验证该密钥。链接导入和订阅编辑提供可选掩码字段、trim/回填/持久化；URL 或密钥实际变化才重新下载，名称与周期变化不下载。现有 Room `ageSecretKey` 字段复用，无 schema 变更；Profile/Entity 的字符串输出不再展开密钥或订阅 URL。
- 新 `ProfileContentDecoder` 经过 Kotlin `CoreBridge.decryptConfig` 调用现有 Go `decryptConfig`，不初始化或启动核心。加密输入仅写本次独占临时密文文件，finally 清理；解密明文先通过现有 YAML 根对象/语法校验，再由 Repository 原子替换正式缓存。首次 URL 导入也补上写入前校验；解密/校验失败时不新增 Room 或正式缓存，更新失败保留旧文件和更新时间。该校验不代表 Mihomo 语义有效。
- 手动/周期更新统一复用持久化密钥。编辑先保存元数据，再尝试更新，失败时密钥设置保留以便修正，旧缓存保留；下载期间 URL/密钥发生变化会拒绝过期结果。自动更新不弹窗，首次单链接/扫码/本地文件缺钥时提供一次补输，错误密钥直接报错，批量仍逐条失败隔离而不补钥。
- 文件预检识别到 armor 时展示“导入时输入密钥并校验”，不把密文误报为普通 YAML，也不在输入密钥前写入正式文件。密钥不放 SavedState；订阅编辑后台关闭以免清理输入误删已存密钥，导入表单即使切到批量/来源页也会清理，后台前发起的迟到缺钥结果不会重开提示。解密异常转换为不含底层输入的固定提示；协程复制的安全 cause 也经过单测检查。
- Go `handleMethodCall` 对解密结果保持内存回调，避免通用 >1 MiB spill 把明文额外落盘或令 Android Bridge 缺少 result；其他方法保留原 spill 行为。新增真实 age 加密→JSON method 的定向测试，强制低阈值证明解密结果不落盘；已有正确/错误密钥与三项 spill 回归通过。
- JVM：App **243**、Core **32** 共 **275** 项，失败/错误/跳过均 0；新增 App 12 项覆盖 decoder、密钥脱敏/预算/临时文件/取消、编辑只改密钥、缺钥一次重试、取消及迟到保护。Service/Common 本轮未重跑，历史四模块 394 项不算当前结果。Go 命令 `go test . -run '^(TestHandleDecryptConfigReadsAnAgeEncryptedFile|TestDecryptConfigNeverSpillsPlaintext|TestHandleMethodCallSpillsALargeResultToAFile|TestHandleMethodCallAnswersInlineUnderTheThreshold|TestClearSpilledResponsesKeepsTheDirectory)$' -count=1` 通过，日志 `/tmp/kclash-age-go-final-20261102.log`。
- 最终 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过；此前 Core 单测和包含 Go 修改的 arm64 AAR 构建通过。最终 UI 构建日志 `/tmp/kclash-age-final-ui-build-20261102.log`，App/Core 构建日志 `/tmp/kclash-age-validated-build-20261102.log`；Lint **0 errors、46 warnings、7 hints**。App SHA-256 `545e18266953049c793fff720f566a45bbb7e24395dccc6a26d316fd7be26f59`，AndroidTest `01c456a3877242d00c050faf2993e07841ac5cdb8d8b9de78cba18beb9958fd0`，均与 Redmi base.apk 一致；App 仅 arm64-v8a，测试包无原生库。
- Redmi `wsvwypiz7xwslvl7`，API 36、1080×2340 竖屏、字号 1.0：仅精确执行 `ProfileAgeInstrumentedTest` 的 `remoteEditorMasksAndTrimsAgeKeyInBothThemes`、`singleImportForwardsKeyAndBatchModeDoesNotOfferSharedKey`、`missingKeyDialogDisablesEmptySubmitAndClearsWhenBackgrounded`、`backgroundClosesEditorAndClearsHiddenImportKey`、`encryptedFilePreviewRequiresKeyInsteadOfReportingInvalidYaml`、`repositoryUsesPersistedKeyForUpdatesAndKeepsOldCacheAfterFailure`，日志 `/tmp/kclash-age-device-final-20261102.log` 为 `OK (6 tests)`。
- 另精确运行 `ProfileUiInstrumentedTest#remoteProfileMenuOpensEditorAndEditorForwardsSubscriptionSettings`、`#remoteProfileEditorDisablesIntervalWhenAutoUpdateIsOff` 和 `com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityRendersHomeAndProfileEntry`，日志 `/tmp/kclash-age-regression-final-20261102.log` 为 `OK (3 tests)`。正式 Room inspect 仍为 `count=0 missing=0`，不算非空配置保留验收；服务为空，未启动核心/VPN，不为了测试补建正式配置。
- 真机 Repository 只读取外层 `1.yaml` 原字节（34814 bytes、SHA-256 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`），使用隔离内存 Room/独占缓存与 fake 下载器，验证持久密钥经过手动/到期更新、失败不新增/不覆盖以及本地导入；真实 Bridge 只验证该原始明文的透传。UI 加密文件提示仅验证状态，不生成密文 YAML。真实 armor→Android Bridge→Repository 完整 E2E、真实加密订阅、真实 SAF 加密文件导入均**未验收**，不能把这些分层结果表述成已完成真机加密导入。
- `/tmp/kclash-age-visual-final-20261102/profile_age_editor_light.png` 与 `profile_age_editor_dark.png` 已实际查看：名称、订阅、掩码密钥、开关与间隔可读，不算 Flutter 像素 parity。外层 `1.yaml` 哈希保持不变；未访问授权在线订阅或任何新增真实地址。
- 保留失败：首次构建漏同步 Debug fake Store 签名；首轮单测把协程安全异常 cause 错断为 null；首轮真机 5 方法批次有 1 项空文本断言把标签计入、实际 EditableText 已空；首次旧回归的 MainActivity 测试包名错误。分别修正并通过上述最终运行。安装哈希脚本最初漏允许目录字符 `~`、路径检查提前终止，修正后两包匹配；一次独立 `YamlConfigCodec.kt` 只读定位不存在，已按符号定位到 `ProfileScript.kt`。
- **状态**：age 表单/隔离仓库接线为 `DONE-VERIFIED`，完整加密导入链路保持 `DONE-UNIT`，Profiles 整体仍 `PARTIAL`。下一步补本地配置后续密钥编辑/文件替换等结构化表单，再推进 Proxy Groups 叶子协议与按组滚动恢复；真实密文/SAF 端到端继续单列。Providers 批量/编辑/侧载、首页出口 route hits 和质量全来源/持久缓存仍待补齐。本轮未提交或推送。


### 2026-11-02：FILE 本地配置密钥编辑、SAF 文件替换与保存保护

- 对照本地 Flutter `main:lib/views/profiles/profiles.dart:397`、`lib/views/profiles/edit.dart:119-179,257-270` 与 `lib/models/profile.dart:201-215`：FILE/URL 使用带可选 age 密钥的文件编辑路径，CUSTOM 仍为原分区编辑器。FILE 新表单默认折叠原始 YAML，支持掩码密钥、文件替换及未保存退出确认。原版 FILE 只改密钥会进入空 URL 下载路径（静态判断），Kotlin 明确只更新密钥元数据，不下载、不重写正文、不更改正文更新时间。
- `AndroidProfileFileDocumentReader` 从真实 `OpenDocument` URI 读取 DISPLAY_NAME 和原字节，按实际流限制 8 MiB，不持久化 URI 权限；VM 按配置 ID、编辑 generation 和 picker token 隔离回调。选择后冻结字节、显示文件名/大小/语法或 age 提示，替换候选优先于原 YAML 草稿；取消选择、取消替换或确认前放弃均不写入。保存失败保留候选便于改钥重试，不为替换新增缺钥弹窗。
- Repository 替换先解密→大小限制→YAML 校验，再复核完整 Profile、原正文/mtime 和运行态；只有 FILE 可走该路径。文件与 Room 保存异常会恢复旧字节及时间，备份 mtime 无法保留则覆盖前终止；同目录独占临时文件 rename 失败不退化为截断覆盖，回滚失败保留旧文件备份。FILE 保存与删除共用 mutex，锁覆盖事务外恢复，避免失败回滚与另一保存/删除交错。取消在提交前生效；进入 NonCancellable 存储阶段后会完成提交或恢复，不能宣称任意取消均零写入。
- 密钥、正文及候选只在当前会话内存中；普通后台、加载中/保存中切后台会关闭并清除输入，已确认的保存可完成且失败不会重开旧表单。系统 picker 的暂时后台保留编辑；生命周期回调直接读取当前 token，避免在下一帧重组前误读旧 picking 状态。运行中和读写中禁止修改，原 CUSTOM/URL 入口保持原路径。
- JVM：App **258** 项（失败/错误/跳过 0），比上一轮新增 **15** 项：6 项 VM、9 项读取/文件提交故障测试，覆盖取消、字节冻结、旧 picker 在新请求 pending 时迟到、失败重试、运行守卫、8 MiB、rename/DB 前后失败、同大小同 mtime 外部替换及恢复失败保留备份。Core/Service/Common/Go 本轮未重跑，没有将历史统计计入本轮。
- 最终命令 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过，日志 `/tmp/kclash-file-edit-lifecycle-build-20261102.log`；Lint **0 errors、47 warnings、7 hints**（新增 KTX `toUri` 建议，无错误）。App SHA-256 `26b98d53f01780ddf24a5350776bc03204384f41c1d238a141bdb38f5eb1a64f`、AndroidTest `dd0442ded7ecc5345c03613a6c232095fb9d2fb0db18b55d60a50f0251e8ff20`，均与 Redmi base.apk 一致；App 仅 arm64-v8a，测试包无原生库。
- Redmi Note 8 Pro `wsvwypiz7xwslvl7`、API 36、1080×2340 竖屏、字号 1.0，最终精确 **13** 方法通过：`LocalFileProfileInstrumentedTest` 的 `localEditorMasksKeyTrimsMetadataSaveAndRendersBothThemes`、`invalidPreviewAndMissingKeyBlockSaveAndClearingSelectionDoesNotWrite`、`unsavedKeyAndYamlRequireDiscardConfirmation`、`runtimeLocksFileMutationsAndBackgroundClosesEditor`、`loadingAndSavingEditorsCloseOnBackgroundWithoutRestoringSecrets`；`LocalFileProfileRepositoryInstrumentedTest` 的 `keyOnlyEditKeepsExactCacheTimestampSelectionAndProfileCount`、`failedDecodeValidationDatabaseAndLateChangesKeepPreviousBodyAndMetadata`、`realOpenDocumentCancelsAndStagesWithoutWritingThenSavesExactAuthorizedFile`；age 的 `missingKeyDialogDisablesEmptySubmitAndClearsWhenBackgrounded`、`backgroundClosesEditorAndClearsHiddenImportKey`、`singleImportForwardsKeyAndBatchModeDoesNotOfferSharedKey`，以及 `ProfileUiInstrumentedTest#profileMutationButtonsDisableWhileCoreIsRunning`、`com.lonnnnnng.flclash.MainActivityInstrumentedTest#mainActivityRendersHomeAndProfileEntry`（inspect）。真实 SAF 单项日志 `/tmp/kclash-file-edit-saf-final-20261102.log` 为 `OK (1 test)`，其余最终日志 `/tmp/kclash-file-edit-regression-final-20261102.log` 为 `OK (12 tests)`；未运行整类、横屏或大字号。
- 真实 DocumentsUI 从非当前 FILE 卡片进入，先 BACK 取消→选文件暂存→取消替换→再次选文件并确认保存。全程核对选择 ID、Room 数量、旧正文与 mtime，最后检查原字节、密钥与正文更新时间；只用隔离内存 Room/独占缓存和授权 `1.yaml` 原字节，未启动核心或下载订阅。设备 Download 初次读取不存在，重新 push 外层原文件后两端 SHA-256 均为 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`（34814 bytes），外层文件未修改。
- `/tmp/kclash-file-edit-visual-20261102/profile_local_file_editor_light.png` 和 `profile_local_file_editor_dark.png` 为整窗截图并已实际查看，标题/掩码/替换/YAML 入口/保存取消完整可见；不据此宣称 Flutter 像素 parity。正式 Room 安装前及最终 inspect 均 `count=0 missing=0`，不算非空配置保留验收；未清应用数据、补建正式 Profile、启核心/VPN或访问在线订阅，服务为空。
- 失败保留：首轮 `/tmp/kclash-file-edit-device-20261102.log` 为 8 项中 1 项失败，测试错误使用 URL 专有编辑标签，修正为真实 FILE 菜单文本入口；第二轮 `/tmp/kclash-file-edit-device-final-20261102.log` 同为 1 项失败，真实 picker 取消返回后编辑不可见，发现并修复生命周期捕获上一帧 picking 的时序问题，增加恢复后会话 ID/RESUMED 断言后复验通过。失败轮次不计最终验收。
- **状态**：FILE 密钥编辑、真实 SAF 明文替换与保存保护为 `DONE-VERIFIED`；真实 age 密文→Android Bridge→Repository 完整 E2E、加密 SAF、全新 Profile 导入的真实 SAF 链路仍未验收，完整 Profiles 仍 `PARTIAL`。下一步推进 Proxy Groups 叶子协议类型、按组滚动恢复与视觉；CUSTOM 更细结构化资源编辑、Providers 批量/编辑/侧载、首页出口 route hits、质量全来源/持久缓存继续保留。本轮未提交或推送。

### 2026-11-02：Proxy Groups 叶子协议与滚动恢复

- `CoreBridge` 解析完整 `proxies` 中叶子节点的真实 `type`，`ProxyGroupsViewModel` 保存叶子协议映射并为嵌套组提供摘要；List/Grid 两种布局均按实际叶子协议显示，不再因叶子不在 `groups` 列表而留空或误用父组类型。
- Compose 代理页使用 `SaveableStateHolder` 按 Profile/组隔离 Tab 状态，List 使用独立行状态；跨远距离组切换、布局切换、导航返回和 Profile 切换后恢复对应滚动位置。List 当前节点定位按实际组内行索引计算，避免把列索引直接当行索引；显式定位仍支持当前节点和组内目标。
- 新增 `ProxyProtocolScrollInstrumentedTest`，覆盖叶子协议与嵌套组摘要在 Tab/List 和深浅主题中的显示、跨组/布局/导航/Profile 的位置恢复，以及 List 组内定位；同时复用 `ProxyGroupsActivityInstrumentedTest` 4 项、`ProxyGroupsViewModelPanelInstrumentedTest` 2 项和 Dashboard 代理选择器 2 项回归。
- 最终命令 `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过：App JVM **261** 项、Core JVM **34** 项，失败/错误/跳过均为 0；Debug/AndroidTest 构建通过，Lint **0 errors、47 warnings、9 hints**。最终构建日志沿用本轮工作区记录，未把 Service/Common/Go 未重跑结果计入本轮。
- Redmi Note 8 Pro `wsvwypiz7xwslvl7`（Android 16/API 36、1080×2340、竖屏、字号 1.0）精确方法通过：`ProxyProtocolScrollInstrumentedTest#leafProtocolsAndLooseNestedExitRenderInTabAndListBothThemes`、`#groupPositionsSurviveDistantTabsLayoutNavigationAndProfileSwitch`、`#listLocatesInsideGroupAndRestoresScrollAcrossLayoutAndNavigation`，以及上述代理组和 Dashboard 选择器回归。没有运行整类 instrumentation、横屏或大字号。
- Debug APK SHA-256 `b1b3774d63b11b6cac12ed83e8c4ec297cf2d25801dbccd35bb939ed29b2c66b`；AndroidTest APK SHA-256 `287ae0e08fabe9170ad6f6420f5f961d66c6cffcb721f88ce30857e38d6213cd`；App 原生库仅 `arm64-v8a`。本轮未访问订阅、未启动 VPN/Mihomo、未读取或修改外层 `1.yaml`、未清理正式应用数据。
- 本切片状态：`DONE-VERIFIED`。Proxy Groups 整体仍为 `PARTIAL`；URLTest/Fallback 完整计算选择与优选语义、按组/测速 URL 超时摘要、延迟跨进程持久化、排序/搜索/密度变化后的语义锚定和更细卡片/组标题视觉仍待补齐。真实 age 密文 Android E2E、加密 SAF/全新 Profile SAF 导入、Providers 批量/编辑/侧载、首页出口 route hits、质量来源全覆盖/持久缓存及完整页面 parity 继续保留待办。本轮未提交或推送。

### 2026-11-02：URLTest/Fallback 当前选择优先级统一（JVM 验证）

- `selectedProxyName()` 现在按组类型解析当前展示节点：URLTest/Fallback 先使用组快照中仍属于 `all` 的核心 `now`，只有核心没有有效自动结果时才回退 Profile 手动映射，再回退 Mihomo `fixed`；Selector 保持手动映射优先，所有失效节点均返回空。
- List 标题、Tab 摘要、Grid/List 当前使用徽标、组内定位和嵌套组递归统一复用同一解析器，移除各处重复的 `group.now` 优先级分支；computed 的 `preferred` 仍只表示用户手动固定，不把自动结果误标为优选。
- 新增 App JVM 测试覆盖自动 `now` 优先、无 `now` 时的手动/fixed 回退、无效节点清空和 Selector 手动映射优先；`./gradlew :app:testDebugUnitTest :core:testDebugUnitTest --no-daemon` 通过，App **263** 项、Core **34** 项，失败/错误/跳过均为 0。
- 本轮未运行真机、Mihomo/VPN、在线订阅、Debug/AndroidTest 构建或 Lint；因此真实核心 URLTest/Fallback 回归仍未验收。Proxy Groups 仍为 `PARTIAL`，下一步继续处理按组/测速 URL 超时摘要、延迟跨进程缓存、排序/搜索/密度变化后的语义锚定和卡片/组标题视觉校准。本轮未提交或推送。

### 2026-11-02：Proxy Groups 深浅色页面基线复验

- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`，Android 16/API 36，`1080×2340`，竖屏，系统字号 `1.0`）连续采集的代理组页面截图稳定：深色 `104e6e560376d0302b8d55853b3a0313d6b510cf6102be1fefc4a9e7406af782`、浅色 `e34d6aaa8c98143f6b17e748193f49e25b045f26e9283df8f160d29939c5a08d`。两张 PNG 已人工查看，组标签、节点卡片、协议摘要、延迟状态、搜索和定位入口完整，无明显裁切或重叠。
- 更新 `ProxyGroupsActivityInstrumentedTest.proxyGroupsScreenshotMatrixMatchesBaseline` 及 `docs/visual-baselines/proxy-groups-states-redmi-api36.sha256`。基线更新后已重跑代理组整类，行为与截图断言 **8/8** 通过；协议滚动与 ViewModel 面板回归也通过。
- 本轮仍只使用固定 Debug 宿主，不访问在线订阅、不启动 Mihomo/VPN、不读取或修改工作区外层 `1.yaml`；真实 URLTest/Fallback 核心回归、延迟 Room 缓存进程重建、排序/搜索/密度变化后的语义锚定及更细卡片/组标题视觉继续待办。该截图基线切片状态为 `DONE-VERIFIED`，Proxy Groups 整体仍为 `PARTIAL`。

### 2026-11-02：URLTest/Fallback 真实核心选择与滚动锚定 — DONE-VERIFIED（Proxy Groups 整体仍 PARTIAL）

- 新增真实核心回归分支 `kclashComputedGroupsOnly`，复用既有 `ProfileRuntimeParityLiveTest#stoppedNodesHotProfileSwitchAndRealExitFollowRuntime`。授权 Profile 使用工作区外层 `AGENTS.md` 唯一链接，URLTest 配置只从外层 `1.yaml` 原字节导入；没有生成或替换 YAML。
- Redmi Note 8 Pro `wsvwypiz7xwslvl7`（Android 16/API 36、arm64-v8a、1080×2340 竖屏、系统字号 1.0）真实 Go Mihomo 核心停止态加载配置并执行测试：授权 Profile 的 Fallback 与 `1.yaml` 的 URLTest 均找到正延迟候选；通过正式 ViewModel 手动固定后，Room `selectedMap` 与核心 `fixed/now` 一致；提交组外节点被核心拒绝且没有改变有效选择；再次点击清除 `fixed`，核心回到有效自动 `now`，页面解析与核心一致。instrumentation 结果 `healthy=true/true`，1/1 通过。
- `ProxyProtocolScrollInstrumentedTest#gridSemanticAnchorSurvivesSortDensityAndSearchChanges` 精确真机方法通过 1/1：名称排序、网格密度变化后仍定位相同节点，搜索过滤锚点后回到组内首个可见节点。该项连同已有 JVM 锚点测试，语义滚动锚定标为 `DONE-VERIFIED`；更细卡片与组标题视觉仍待完成。
- `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest --rerun-tasks --no-daemon -Ptarget-platform=android-arm64` 强制通过：App 270、Core 34 项，失败/错误/跳过均为 0。Debug/AndroidTest 编译与打包通过；`./gradlew :app:lintDebug --no-daemon -Ptarget-platform=android-arm64` 通过，Lint 0 errors、51 warnings、9 hints。
- Debug APK SHA-256 `3d3f530a95d2763315d1c0d70214c55eebf6643f7ab54a0e1038de101cdef81f`，AndroidTest APK SHA-256 `2645dc44def5fa1ab03071d55523b66c06b699d0b0ec06fcd96d99ed2b8c6f5d`，均与 Redmi 安装包一致；App 原生库仅 `arm64-v8a`，测试 APK 无原生库。
- 配置准备首次出现 `SocketTimeoutException`（`/tmp/kclash-profile-prepare-20261102.log`）；设备重连后同一授权地址准备成功，新增 1 份、失败 0（`/tmp/kclash-profile-prepare-20261102-rerun.log`）。未加参数运行的完整 ProfileRuntimeParity 用例在 `stopped-delay` 阶段因 `DIRECT` 默认测速无正延迟失败，尚未启动核心；保留日志 `/tmp/kclash-profile-runtime-20261102-urltest.log`。目标 computed 组分支随后独立通过，不能把完整长流程记为通过。
- 成功用例清理断言确认临时 Profile 删除、原 Profile ID 集合及正文元数据恢复；外层 `1.yaml` 仍为 34814 bytes、SHA-256 `bd5cb67dc9332cf144f4ccb2f9ef597aa3299c9d207e8cc7cc4e3cda67480898`。核心保持停止态，没有启动 ProxyService/VpnService/TUN；验收后系统服务列表为空。
- 本切片状态 `DONE-VERIFIED`。URLTest/Fallback 无有效 `now` 的核心边界、跨进程优选状态恢复、延迟 Room 缓存真机进程重建和更细代理组视觉仍待补齐；下一步先验收真实 Profile 的 Room 延迟缓存进程重建。Proxy Groups 整体保持 `PARTIAL`。本轮未提交或推送。

### 2026-11-03：正式 Profile 延迟 Room 缓存跨进程恢复 — DONE-VERIFIED

- 新增 `ProxyDelayProcessPersistenceLiveTest` 双阶段用例和 `tool/android_proxy_delay_process_acceptance.py`。脚本只从工作区外层 `AGENTS.md` 读取唯一授权来源，并在日志输出前脱敏；只接受已核验的 Redmi serial，竖屏和字号由 instrumentation 再次断言。若正式 Profile 库为空，第一阶段导入并保留授权 Profile；若库内已有其他 Profile 但没有授权 Profile，则失败退出，不新增记录。
- 第一阶段通过正式 `ProxyGroupsViewModel.testDelay()` 对授权 Profile 真实叶子节点测速，找到正延迟后核实正式 Room `(Profile, 组, 节点, testUrl)` 行已写入；同时写入一个其他 Profile 同 URL 同名节点和一个当前 Profile 旧 URL 探针，并保存只包含验收元数据的应用内标记。
- 脚本对 `com.lonnnnnng.flclash` 执行 `am force-stop` 并确认 PID 消失，第二次精确 instrumentation 在新进程运行，核对 PID 已变化、当前 Profile/组/testUrl 匹配、节点徽标恢复同一延迟值，其他 Profile 与旧 URL 探针均不进入页面。成功时删除仅由本测试写入的两条探针行与标记；真实 Profile 和有效测速缓存保留。
- Redmi Note 8 Pro `wsvwypiz7xwslvl7`（Android 16/API 36、arm64-v8a、1080×2340、竖屏、系统字号 1.0）双阶段通过 `OK (1 test)` / `OK (1 test)`；`ProxyDelayDaoInstrumentedTest` 3/3 通过。全程 Runtime `STOPPED`，没有 ProxyService、VpnService 或 TUN；没有读取/修改外层 `1.yaml`，没有清数据或删除 Profile。
- 强制 `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest --rerun-tasks --no-daemon -Ptarget-platform=android-arm64` 通过：App 270、Core 34 项，失败/错误/跳过 0。Debug/AndroidTest 构建和 `lintDebug` 通过，Lint 0 errors、51 warnings、9 hints。最终重建后 Debug APK SHA-256 `db03165a180429f6d46a336d5c125652dfde20a2fa4110069bcc5fda06dd92ac`，AndroidTest APK SHA-256 `deabbe570d3808404b30800360c9f25d59932c4503458dd0863291c633bd2078`，均与 Redmi 安装包匹配；App 原生库仅 arm64-v8a。
- 本阶段 `DONE-VERIFIED`，Proxy Groups 整体仍为 `PARTIAL`。下一步补代理组卡片和组标题的视觉校准；真实 age 密文 Android E2E、加密 SAF/全新 Profile SAF 导入、URLTest/Fallback 无有效核心 `now` 的边界及跨进程手动优选恢复继续待验。本轮未提交或推送。

### 2026-11-06：Proxy Groups List 组头 sticky/raised 视觉校准 — DONE-VERIFIED（整体仍 PARTIAL）

- `ProxyGroupsPage.kt` 的 List 组头继续使用 Compose `stickyHeader`，在列表滚动时以 180ms 动画将 `shadowElevation` 提升到 `8dp`、`tonalElevation` 提升到 `2dp`，玻璃效果仍默认关闭。实现参考本地 Flutter `PinnedHeaderSliver` 的 floating/lift 与阴影语义，没有合并或修改 Flutter 上游代码。
- 新增 `ProxyPageParityInstrumentedTest#listGroupHeaderKeepsRaisedLayerWhileLongContentScrolls`：先让长列表组头真正到达吸顶边界，再验证后续滚动中相对 `LazyColumn` 视口位置保持稳定，并采集 resting/lifted 两张截图；另修正既有远端 Tab 连续转场用例，等待 `HorizontalPager` 目标页懒组合后再采样。Redmi Note 8 Pro（`wsvwypiz7xwslvl7`、API36、arm64-v8a、1080×2340、竖屏、字号1.0）整类 **4/4** 通过，截图哈希为 resting `069284b6ad50d10a42bffba535af68b482dcdaf0442ec73032ccc79fbd05aa68`、lifted `7404366cd8d45a34da2942b0af0906afe9bab46773b6872c9df7d9e3dde92bc2`。
- 最终 `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease :app:lintRelease --no-daemon -Ptarget-platform=android-arm64` 通过：App JVM **492**、Core **38**，失败/错误/跳过均为0；Debug/AndroidTest、未签名 Release/R8 和双 Lint 均通过，Debug/Release Lint 分别为 **0 errors、67 warnings/10 hints** 与 **0 errors、66 warnings/9 hints**。最终 APK 哈希：Debug `3d48516e64c4b9d1a738aa25811d8d0754fd311f47a39a5368adc0610f79d0e9`、AndroidTest `509a32aa80a983783150b2e3d3e82e54c6861dc26c91346d910c046708da6de2`、未签名 Release `9cc143851852cb0b47b486d6dbd7e4b0559c3badb5e254f2a0615c695410cf49`。
- 证据：`docs/acceptance/proxy-list-header-redmi-20261106.json`；持久附件 `../acceptance-artifacts/proxy-list-header-20261106/`。本轮完成的是 List 组头视觉切片，完整 List/其他子页、空态 morph/orbit、统一截图基线、全应用动效与性能仍为 `PARTIAL`；真实 age、加密 SAF、URLTest/Fallback 无有效 `now` 边界、跨进程优选恢复和其他实网边界按原清单继续排队。代码与 roadmap 已提交并推送至 `origin/kotlin`，提交 `b659ad15`。

### 2026-11-06：代理组标签方向键导航与 Enter 激活 — DONE-VERIFIED（完整键盘联动仍 PARTIAL）

- `ProxyGroupsPage.kt` 原有 `selectable` 标签已支持 Compose 键盘焦点行为，本切片补充明确回归，不改生产页面逻辑。方向键右移只移动焦点；Enter 才切换代理组；末组继续右移不会绕回首组，也不会仅因浏览焦点改动当前组。行为参考本地 Flutter `lib/views/proxies/group_tabs.dart` 的标签选择流程，没有修改或合并 Flutter 源码，也没有新增 TalkBack/无障碍功能。
- 新增 `ProxyPageParityInstrumentedTest#groupTabsCanBeNavigatedWithDirectionalKeysAndActivatedWithEnter`。初次末端断言要求焦点停留在末组，与 Compose 允许焦点离开标签带的规则不符；改为断言首组未获得焦点且当前代理组不变后，定向用例通过，代理页一致性整类最终 **8/8** 通过。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`、Android 16/API36、arm64-v8a、1080×2340、竖屏、字号1.0）。键事件由 Compose instrumentation 注入；未使用实体 USB/Bluetooth 键盘，故系统/OEM 硬件按键路由仍未验证。
- 最终构建命令 `./gradlew :app:testDebugUnitTest :service:testDebugUnitTest :core:testDebugUnitTest :common:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease :app:lintRelease --no-daemon -Ptarget-platform=android-arm64` 成功。四个 JVM 测试任务均 `UP-TO-DATE`（本切片仅改 instrumentation 测试）；最近完整结果为 App 492、Service 206、Core 38、Common 31，失败/错误/跳过均为0。Debug/Test、未签名 Release/R8、双 Lint 通过；Debug Lint 0 errors/67 warnings/10 hints，Release 0 errors/66 warnings/9 hints。
- Debug APK SHA-256 `4d589eb5a2731082638de967c431009c009bc4ab48086e79f1ad984cf3f518bd`；AndroidTest APK `4f884f298597a01c78e4d7be1189b15c5b59b394cc40ddd669198b15aa2ebf2a`；未签名 Release APK `cbbd7f675657348275ed803b98fe912d6b8b745bc32e2e80d11009c5b27dffee`。测试 XML、键盘用例日志及三份构建包保存在 `../acceptance-artifacts/proxy-group-keyboard-redmi-20261106/`；详见 `docs/acceptance/proxy-group-keyboard-redmi-20261106.json`。Gradle 结束后未保留本轮安装包，因此不声称设备回拉哈希匹配。
- 本切片已验，但完整页面键盘焦点顺序、左方向键、菜单/面板键盘联动、实体键盘及其余视觉待办仍为 `PARTIAL`；代理组与完整 Flutter 视觉状态不变，玻璃继续默认关闭。

### 2026-11-06：代理组 List 组头定位与面板联动 — DONE-VERIFIED（整体视觉仍 PARTIAL）

- `ProxyGroupsPage.kt` 在切换组折叠状态前先将不可见组头滚入列表视口，确保状态变化可见；展开/收回的 480ms 过渡锁定组头操作，避免快速重复点击引发滚动与面板状态竞争。定位按钮滚到当前节点，批量测速按钮保持对应组语义。
- 新增 `ProxyPageParityInstrumentedTest#listHeaderLocateBatchAndFoldKeepActionsInSyncWithMotion`，覆盖长组当前节点定位、批量入口目标组、收起/展开期间控件可用状态和最终恢复；代理页整类 10/10 通过。最终原始 instrumentation 日志含 31 张唯一 PNG，重点的定位、收起稳定帧和展开中/稳定帧已目视核对。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`、API36、arm64-v8a、1080×2340、竖屏、字号1.0），安装包回拉哈希与构建产物一致。四模块 JVM App495/Service207/Core38/Common31，共771/771；Debug/Test、未签名 Release/R8、双变体 Lint 成功（0 errors，Debug/Release 67/66 warnings）。Debug `f5be349b51797edb4f7fa8721ed3147f7f53d8bd44f35af3eb56dccc6d82f455`，AndroidTest `868be7605bfc8d61913c7de008eb7cd9280a3a86ff188cc0cf4a7fac0407c04a`，未签名 Release `ba13400267413872a0f4d55b8c9e351e9a53f64226251dda2ded0ec71c3f57be`。
- 证据 `docs/acceptance/proxy-list-panel-linkage-redmi-20261106.json`；本机附件 `../acceptance-artifacts/proxy-list-panel-linkage-redmi-20261106/final-install/`，截图哈希索引为 `screenshots-manifest.sha256`。全程没有启动代理/VPN 服务、改动正式 Profile、读取工作区 `1.yaml` 或触碰 Flutter 基线包。
- 本切片仅证明 List 组头操作与长列表状态联动；完整键盘焦点流、其他子页/面板、统一 Flutter 截图基线和全应用动效仍为 `PARTIAL`。下一步按代理页剩余键盘焦点流与其他子页/面板继续收敛；玻璃默认关闭。

### 2026-11-06：代理组测速触点命中范围 — DONE-VERIFIED（整体视觉仍 PARTIAL）

- 本切片仅补仪器回归，没有改生产触点实现。`ProxyPageParityInstrumentedTest#delayTouchTargetExtendsPastGlyphWithoutStealingAdjacentNodeTap` 在 Tab/List 两种布局中分别点按测速语义边界外 8dp：测速回调各触发一次，当前节点不变；随后相邻节点点击仅切换节点，不额外测速。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`、Android 16/API36、arm64-v8a、1080×2340、竖屏、字号1.0）代理页整类 **11/11** 通过，33 张截图清单的 SHA-256 全部验证，Tab/List 触点截图目视检查。Debug 与 AndroidTest 安装回拉哈希匹配；四模块 JVM App495/Service207/Core38/Common31 共 **771/771**，Debug/Test、未签名 Release/R8 与双变体 Lint 成功（Debug 0 errors/67 warnings/10 hints，Release 0 errors/66 warnings/9 hints）。Debug `f5be349b51797edb4f7fa8721ed3147f7f53d8bd44f35af3eb56dccc6d82f455`，AndroidTest `ff00d705620152b41c62922040300ea8d9555fe7741730d52e40c54be9bce3f1`，未签名 Release `d6c132ce7bfab786bfdcc19024cabf9fddb59aeb2702671890b4a68e9f879461`。
- Redmi 收尾核对未发现本应用活动服务，Connectivity 显示 `VpnNetworkProvider:0` 且无 VPN transport；系统拒绝 `ip link show tun0`，因此不把接口查询写成证据。没有触碰 Profile、授权订阅、工作区 `1.yaml` 或 Flutter 基线包。证据 `docs/acceptance/proxy-delay-touch-target-redmi-20261106.json`；原始日志、构建包、已回拉 APK 和 33 张截图在 `../acceptance-artifacts/proxy-delay-touch-target-redmi-20261106/final-install/`。
- 该切片只证明 instrumentation 注入点按的 UI 命中和回调隔离，不证明手指触屏测量或真实 Mihomo URLTest 执行。其余代理组子页/面板、统一截图基线与全应用连续动效仍 `PARTIAL`；下一步继续其余子页和面板操作/返回状态，玻璃默认关闭。

### 2026-11-06：首页策略组面板返回与 Activity 重建 — DONE-VERIFIED（整体视觉仍 PARTIAL）

- 只增加Debug宿主恢复与两项仪器用例，没有修改生产UI或核心。固定数据宿主在重建后回供当前组、selectedMap、unfoldSet及选择次数，使正式Compose面板的saveable状态可以独立验收；Flutter面板搜索隔离行为仅只读参考。
- 关闭IME后一次系统返回退出面板，Activity保持RESUMED；重新打开时搜索清空，当前组/节点保持。Activity重建时面板仍打开、查询/组/选中徽标保持；随后真正关闭再开才清空查询。两项均核对选择只提交一次，并回归重复选择、List中部拖动、深浅色、配置快切和服务管理/质量返回层级。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`、API36、arm64-v8a、1080×2340、竖屏、字号1.0）精确 **7/7**；14张PNG全部校验，5张新增截图逐张目视。首轮5/7仅因空输入框的合并Text含占位文字，改为EditableText断言后复验7/7，失败原样保留，不认定产品缺陷。
- 统一build2成功（389任务，11执行/378复用）；四模块JVM XML App495/Service207/Core38/Common31共771项零失败/错误/跳过，最终build2测试均UP-TO-DATE，不声称全部强制重跑。Debug/Test、未签名Release/R8、双Lint通过，Debug0错误/67警告/10提示、Release0错误/66警告/9提示。Debug `a74002163c571eeaa486887f08b150c9b1d09ba79b8108fa09e50859ba44079b`、AndroidTest `5dfdde23cfd3a382699e4c94fa756a4a8032335d17a500794c03f23633ca74db`、未签名Release `e322caa97d8c452f285e1feb488ad0ecdbfc330f4e3e50f60f99c4362c3ff7fc`；Redmi安装回拉双包均匹配。
- 当前核心gitlink现场核对为`7038231e`，包含`852d3ad1`上游健康检查/自动选组与本地`f83e9b10`控制器修复；已纠正第3节旧快照，不更改具名历史结果。本组没有触碰正式Profile、订阅、1.yaml、Flutter基线包或设备设置；收尾无本应用服务，实际活动网络仅WIFI、VpnNetworkProvider:0，未查询tun接口。首次收尾脚本误把VPN请求行当活动网络，修正解析后通过，原诊断文件保留。
- 证据 `docs/acceptance/dashboard-proxy-picker-redmi-20261106.json`；原始构建、首轮失败、最终日志、包与截图位于 `../acceptance-artifacts/dashboard-proxy-picker-redmi-20261106/`。本组只证明固定数据宿主的Activity重建，不代表冷进程、正式仓库恢复或实网；其余子页/面板、统一视觉基线和连续动效继续推进，整体Flutter视觉仍PARTIAL，玻璃默认关闭。


### 2026-11-06 Provider正文后台与Activity重建 — DONE-VERIFIED（整体视觉仍 PARTIAL）

- 修复Kotlin自身的生命周期缺陷：切后台只暂停Provider列表轮询，正文、读取任务和失败原因保留于当前ViewModel；显式关闭/切配置继续作废旧读取。放弃确认使用saveable状态，正文选区只保存两个偏移并按当前正文长度收敛；正文不进入自定义Saver。ProfileStore首读就绪标记和ProfilesViewModel订阅顺序避免初始化空快照误关恢复中的详情；真实空库/删除仍正常关闭。
- 先只读参考Flutter `lib/views/proxies/providers.dart`的保存失败留页/脏正文退出确认与`lib/pages/editor.dart`的读取generation/保存去重，再按Android既有架构修复；没有合入Flutter提交、修改Flutter参考包或Mihomo。缺陷触发、修复文件、原失败和最终源码哈希/diff记录于本组验收JSON，便于溯源；这不是上游源码缺陷。
- Redmi Note 8 Pro（`wsvwypiz7xwslvl7`、API36、arm64-v8a、竖屏、字号1.0）最终final4精确 **6/6**；放弃确认、已受理保存去重、预览读取、长文失败/选区跨后台和Activity重建，以及既有编辑失败/MRS只读/菜单/订阅信息回归通过。10张PNG全部哈希校验并经拼图目视。final3 **5/6** 的长文选区恢复失败原样保留，Saver修复后复验通过，不累计成功数。
- 最终构建成功（302任务，12执行/290复用），独立串行双Lint成功（237任务，8执行/229复用）；JVM四模块XML App499/Service207/Core38/Common31共 **775项** 零失败/错误/跳过，最终95任务均UP-TO-DATE，不声称全部强制重跑。Debug0错误/67警告/10提示，Release0错误/66警告/9提示。Debug `4c2ca16e8c11819c505382e574b585138ad18ded044952e9422764abd6491151`、AndroidTest `9af51a7f71477d331f522af27cf474769ec53179d3dba082e22d51fd1169d9ec`、未签名Release `d363da1d61cc85ca36b42ea67ae15157b5af1a2a65ad9f680cb9c85c9fc1620f`；App/Test实际adb pull回拉匹配，Debug/Release原生库仅arm64，Release清单无Debug测试Activity且非debuggable。最终构建/Lint完整输出只保留于工具会话，本机保留制品、Lint XML和JVM日志，不以旧构建日志替代。
- 正式库最初为0份配置，用户已确认当天手动重装并明确授权重新读取外层AGENTS.md唯一远程来源与原始1.yaml。通过正式ProfileRepository恢复FILE和URL各一份，之后准备入口仅复用、不刷新；最终6例前后两配置ID/type/正文SHA-size-mtime、元数据及偏好摘要、当前选择完全一致，runtime均STOPPED。原1.yaml、Flutter APK和旋转/字号/动画设置保持，收尾无本应用服务，实际连接仅WIFI、VpnNetworkProvider:0；未查tun接口。首次网络摘要误计监听请求条件，原文件保留后修正，仅是诊断解析问题。
- 证据`docs/acceptance/provider-document-lifecycle-redmi-20261106.json`，原始附件`../acceptance-artifacts/provider-document-lifecycle-redmi-20261106/`。本组为固定数据Debug宿主后台/Activity重建；不是冷进程或MainActivity正式库恢复。选区由semantics设置，滚动只断言恢复值大于0；Bundle只检查指定marker的三种编码，不推广全部泄漏形式。未重跑Provider实网、核心/VPN、Go、低API、蜂窝/OEM、签名运行或远端CI。继续第9项剩余子页/面板返回与异常恢复，再统一视觉/动效；玻璃默认关闭，第10项光效/性能后排。


### 2026-11-06：代理组排序子菜单键盘返回与名称排序 — DONE-VERIFIED（整体视觉仍 PARTIAL）

- 只在`ProxyPageParityInstrumentedTest`增加独立用例，沿用偏好保存/恢复；生产代码、Flutter参考包和Mihomo均不变。键盘打开后逐项经过定位/刷新/样式到排序，Enter进入后聚焦返回项；Escape先返回根菜单、再次Escape关闭，默认排序与节点顺序保持。重新键盘打开后选择名称，菜单关闭、偏好为NAME、几何顺序为node-a/node-m/node-z，原node-m选择不变且刷新回调0次；再开菜单文案为“排序：名称”。
- Redmi Note 8 Pro（wsvwypiz7xwslvl7、API36、arm64-v8a、1080×2340、竖屏、字号1.0）精确6/6：新增排序键盘、溢出菜单键盘、Tab方向键、测速焦点、工具菜单返回/外部关闭/换配置、排序/密度/搜索滚动锚点；3张PNG哈希验证并逐张目视，不将相邻历史批次数相加。
- 最终build2统一命令成功，389任务中11执行/378复用；App499/Service207/Core38/Common31共775项JVM XML零失败/错误/跳过，四模块任务均UP-TO-DATE。Debug/Test、未签名Release/R8和双Lint通过，Debug0错误/67警告/10提示、Release0错误/66警告/9提示。Debug `4c2ca16e8c11819c505382e574b585138ad18ded044952e9422764abd6491151`、AndroidTest `6cc4fbaaa2943899e8d2bd2953294544ea560894d19d0e77c8d5ac2a20cc993e`、未签名Release `e460c26787690f45c68a2e342f4ef5dc32a0f1d6bd2fcbb2a0c063b59f4e7f16`；App/Test实际回拉匹配，原生库仅arm64。生产Debug与前组字节相同，测试APK包含新方法。
- 配置准备按外层AGENTS.md唯一远程来源和原1.yaml仅复用已恢复的FILE/URL两份配置，不重新下载/覆盖正文；前后正式ID/type/正文SHA-size-mtime、元数据/偏好摘要/当前选择完全一致，runtime均STOPPED。原1.yaml SHA-size-mtime、Flutter APK及旋转/字号/动画设置保持，收尾无本应用服务。
- 证据`docs/acceptance/proxy-sort-keyboard-redmi-20261106.json`；原始构建、定向日志、前后保护摘要、回拉APK及3图在`../acceptance-artifacts/proxy-sort-keyboard-redmi-20261106/`。只证明固定组件数据上的注入键盘路径，未验实体键盘、正式MainActivity恢复、Provider实网、核心/VPN/Go/低API/蜂窝/OEM/签名运行或远端CI。继续第9项其他子页/面板、统一视觉与连续动效，玻璃默认关闭，原暂缓/取消范围保持。

### 2026-11-06：真实主页面往返与代理List重建恢复 — DONE-VERIFIED（整体视觉仍 PARTIAL）

- 新增`MainNavigationStateInstrumentedTest`三方法，真实MainActivity中复用原1.yaml对应的正式FILE正文创建临时副本，验代理→配置/首页→代理、List布局/展开/分组/节点/吸顶标题跨Activity重建，以及搜索空态保持/清除恢复；以当前工作区SHA作为运行参数，不复用过期正文哈希。finally按ID/唯一名称只删除自身临时副本并核对全部正式行、正文与偏好/选择。
- KUI-001：首轮4/6中的List恢复真实失败，重建后同视口末项top1792→1806、firstIndex39/offset94→80，吸顶标题布局仍在但未绘出；延后取样仍失败。对照本地Flutter的ScrollController/PageStorageKey和独立分组语义后，在首个非空实际布局完成后重新应用原保存位置，每实例一次，不引入固定延迟/像素补偿或放宽1px断言；最终新增标题四边坐标断言。源码及同步/撤销条件见`docs/proxy-list-restoration-defect.md`，不认定为Flutter/Mihomo/AndroidX上游缺陷。
- 搜索首轮失败属于AnnotatedString整体比较，改为纯文本EditableText.text后通过，产品搜索逻辑不变；run1、diagnostic2/3和临时日志转义编译失败独立保留。diagnostic4仅证明定向修复，不与最终批次累计。
- 最终build5统一构建成功，389任务38执行/351复用；App499本轮重跑，Service207/Core38/Common31任务复用，775项JVM XML零失败/错误/跳过。Debug/Test、未签名Release/R8和双Lint通过（Debug0错误/67警告/10提示，Release0错误/66警告/9提示），Release清单非debuggable/无测试Activity，原生库仅arm64。
- Debug `7d3c8c0fafc7da16a1e8fe0d451f0e09f3429a066e0ec3b403fdd59aa5cd23da`、AndroidTest `093f3e23b3db28d79576a91bf3cac75e1b85812e46e67cb9856cc3af90e10376`、未签名Release `ed052417f2a2a408c20623b253e02a651d19fca6305914a6eef33b02d3e936d8`。Redmi同套App/Test实际回拉匹配，final5精确9/9；27PNG哈希/解码验证并经4拼图目视，其中9张正式宿主、12张主页面局部转场帧、6张List组件状态。正式两份FILE/URL只复用，前后正文/元数据/偏好/选择摘要完全一致，原1.yaml/Flutter/设置保持，收尾无服务。
- 证据`docs/acceptance/main-navigation-state-redmi-20261106.json`，本机附件`../acceptance-artifacts/main-navigation-state-redmi-20261106/`。仅本组代理浏览状态的Activity重建，不证明冷进程/运行态、Provider正文正式宿主/冷进程或完整Flutter逐像素/动效性能；第9仍PARTIAL。继续剩余配置/代理子页和面板返回、空态/异常恢复，再统一视觉与连续动效；玻璃默认关闭，第10后排，既有暂缓/取消范围保持。

### 2026-11-06：Provider正文真实MainActivity保存与重建返回 — DONE-VERIFIED（整体视觉仍 PARTIAL）

- 新增`MainProviderDocumentInstrumentedTest`三条正式入口链路：首页配置详情→Provider菜单/行→正文→原列表。真实核心停止态侧载失败后长草稿/错误/选区跨后台与Activity重建保持，放弃确认重建后可继续/放弃，缓存SHA-size-mtime不变；有效正文只追加唯一注释，保存回读完成自动退出，重建重开读到新摘要且资源源文件不变；原配置MRS只读长内容滚动/重建位置和面板返回通过。Flutter参考`lib/views/proxies/providers.dart`，没有合入Flutter提交或改产品源码。
- 夹具按当前原1.yaml摘要查找正式FILE原字节，建立自身临时FILE+CUSTOM并经正式绑定投影，真实核心读91项Provider（原90项+临时File Proxy）。只侧载临时缓存；按ID/唯一label删除自身两行及投影。正式配置不清数据、不重导、不刷新订阅，不操作原90项HTTP更新；停止态加载与首页查询可联网，不能称完全离线。
- Redmi final2同套App/Test精确9/9，21张PNG哈希/解码及4拼图目视：3条新增真实宿主、4条固定运行时正文生命周期、1条真实List恢复、1条固定Provider选项回归。正式两份FILE/URL的正文/元数据/偏好/选择最终批次前后完全一致；原1.yaml/Flutter APK/设备设置保持，收尾无服务。
- final1为7/9，完整保留：首次IP质量查询新增`ip_quality_cache_v1`引起偏好保护失败，正式行/正文/mtime/选择未变；另一项选中能一屏显示的MRS无法证明滚动。未回写旧缓存/放宽断言，改选原配置200..3000条的真实规则资源后整组复验通过；这不是产品源码修复或上游缺陷。
- 最终build2统一构建成功（55s，389任务11执行/378复用）；App499/Service207/Core38/Common31共775项JVM XML零失败/错误/跳过，本组四模块任务均UP-TO-DATE。Debug/Test、未签名Release-R8及双Lint通过，Debug0错误/67警告/10提示、Release0错误/66警告/9提示。Debug `7d3c8c0fafc7da16a1e8fe0d451f0e09f3429a066e0ec3b403fdd59aa5cd23da`、AndroidTest `f0496fd2f57a97d02efc8be122390611f741e81a8d8e2a01fedf951f6eb45c6c`、未签名Release `c9437c14d061ec703d837acfb3e31a09e34b74bac5868dd15fe388899e04f6dd`；双包实际回拉匹配，原生库仅arm64，Release非debuggable且无测试Activity。
- 证据`docs/acceptance/main-provider-document-redmi-20261106.json`，原始日志/截图/回拉APK和附件清单见`../acceptance-artifacts/main-provider-document-redmi-20261106/`，附件未上传源码仓库。只证明本组正式宿主Activity重建与停止态真实缓存链路；冷进程正文恢复、运行态侧载、其他Provider实网、统一Flutter逐像素/缓动/性能、低API/蜂窝/OEM、Go/远端CI/正式签名运行未重跑。继续todo第9项剩余子页/面板和异常恢复，玻璃默认关闭，第10后排、既有暂缓/取消边界保持。

### 2026-11-06：配置管理窗口真实MainActivity重建恢复 — DONE-VERIFIED（整体视觉仍 PARTIAL）

- **修复**：KUI-002，重命名/删除只保存目标ID并从最新正式列表取行，初次读库未完成不清目标；排序宿主、打开revision及放弃确认saveable恢复，等profilesLoaded后创建排序页，避免空快照清掉草稿。只修改Kotlin页面集成，Flutter重命名确认/排序局部提交只读参考，Mihomo和数据库不变。
- **验收**：真实MainActivity新增三条验证重命名草稿及明确保存/取消、删除确认恢复及取消/明确删除自身FILE、排序草稿/放弃确认恢复与保存后主页往返回读。加四条固定组件、Provider失败草稿和List两条正式宿主回归，final2共9/9；25张PNG哈希/解码通过，3张拼图已目视，未做Flutter同帧像素/完整动效比较。
- **构建**：最终build4统一命令通过，389任务11执行/378复用；App499/Service207/Core38/Common31共775项JVM XML零失败/错误/跳过，App499在build3实际重跑，其余276项复用，最终四模块UP-TO-DATE。Debug/Test、未签名Release-R8及双Lint通过；Debug0错误/67警告/10提示，Release0错误/66警告/9提示，Release无Debug测试Activity。
- **包与保护**：Debug `9ae1c0c2420e5251e8297d529c2e162f3dee55b46906a63beb599293c68da606`、AndroidTest `e8f1e5ea1f13efca66b2f3fa097e848a90a5deb216d4bfb5a2fb4b26a0ebb269`、未签名Release `3ed748ff8ef2a43ab981a972a0f7aeebfb5c68720bbe649489315efa67ef9a3e`；App/Test实际回拉匹配。两份正式FILE/URL只复用，全部行/正文SHA-size-mtime/顺序/偏好/选择和原1.yaml/Flutter/设备设置三批前后保持，临时CUSTOM/FILE按ID/标签/类型清理，收尾无服务。Provider相邻回归停止态加载核心可联网，不称离线；未手动更新原HTTP Provider/正式订阅，未启动运行态/VPN。
- **原始失败**：未修产品red1三条0/3；修复后final1为8/9，实际草稿首尾空格正确，仅assertTextEquals混入名称/提示。改为精确EditableText.text并等待异步名称后final2通过，产品未再改；失败和保护日志分别保留，不累计通过数。
- **证据与下一步**：`docs/acceptance/main-profile-management-redmi-20261106.json`、`docs/profile-management-restoration-defect.md`；本机附件`../acceptance-artifacts/main-profile-management-redmi-20261106/`。管理窗口/正文冷进程、保存受理中断、目标外部删除、运行态、其他Provider实网、完整视觉/缓动/性能、低API/蜂窝/OEM/Go/远端CI/正式签名运行仍未补。正文冷恢复需独立加密草稿协议；继续第9项其他常用子页/面板与异常恢复，再统一视觉，第10后排、玻璃默认关闭，原暂缓/取消边界保持。

### 2026-11-07：扩展页真实MainActivity重建与键盘错误恢复 — DONE-VERIFIED（整体视觉仍 PARTIAL）

- **修复**：KUI-003，同配置重复open复用ViewModel会话；显式重试才reload，关闭后清会话身份。表单错误/放弃确认saveable，切换ID旧结果仍拒绝；Flutter只读参考，Mihomo不改。
- **验收**：修复KUI-003：真实MainActivity扩展页重建复用原会话，保留全局子页/搜索、长脚本列表、规则草稿/目标/校验错误及放弃确认；挂起保存不被重连作废，明确重试和退出重开重新读取。真实IME上方操作、两层返回、连续确认只保存一条规则及重新打开回读已验。Redmi final5精确13/13，33张PNG分片回传/解码/哈希校验并经7张拼图目视；778项JVM XML零失败/错误/跳过，Debug/Test、未签名Release-R8和双Lint通过。App/Test回拉匹配，最终安装前/验收前后两份正式配置、正文、偏好/选择、原1.yaml、Flutter基线和设备设置保持，临时配置/脚本清理，收尾无服务。
- **构建**：build5统一命令43秒、389任务11执行/378复用；App502于build1重跑，其他276复用，最终四模块UP-TO-DATE。Debug0错误/67警告/10提示、Release0错误/66警告/9提示。
- **证据边界**：原始red1/red2均0/3、挂起保存红灯1/1失败；final1汇总11/13且偏好摘要变化、Binder回传缺两条；final2汇总13/13但仍缺两条明细；final3已13/13完整回传，静态截图退场采样不足，final5采样650ms复验。首轮偏好具体键缺少基线、原因未定，未回写旧偏好，不能消除第6项原保护异常。red1准备曾按既有授权补入本地/远程两配置，不能声称首次准备前库保持；red2及最终批次均复用。Provider相邻回归在停止态加载核心可联网，不称离线或运行态实网验收。
- **下一步**：扩展其余批选/删除确认、远程新增IME/长批量/无效输入/取消、代理添加到本地失败及等待中切换配置、本地代理删除长引用/失败和分享链接IME返回仍待验；管理窗口/扩展/Provider正文冷进程、保存受理中断、目标外部删除及正文加密草稿另行推进。完整Flutter视觉/动效仍PARTIAL，玻璃默认关闭。证据`docs/acceptance/main-extension-state-redmi-20261107.json`、`docs/profile-extension-restoration-defect.md`；本机附件`../acceptance-artifacts/main-extension-state-redmi-20261107/`。
- **提交前复核**：final4同批13/13及33图已通过；进一步将正式脚本元数据比较改为不输出实体原值的布尔断言，驱动精确核对33张预期截图名称，防止整图零分片漏验。仅验收工具变化，最终build5/final5再验13/13及33图，产品源码与build1一致。

### 2026-11-07：扩展批选删除与远程新增表单 — DONE-VERIFIED（整体视觉仍PARTIAL）

KUI-004/005已修：扩展批选与删除确认重建保持，筛选仅删可见已选项、失败原位重试、等待防重复及外部移除收口；远程新增原位校验、真实IME/Next/Done、40条批量首尾滚动/模式/来源往返/重建/取消与临时密钥清理已验。Redmi final4 精确26/26、35图完整校验并经10张拼图目视；782项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8和双Lint通过。双包安装回拉匹配，两份正式配置/脚本/正文/偏好/选择及原1.yaml/Flutter/设备设置保持，临时副本清理、无服务。

App506本组build1/build3实际执行，其余276项复用；最终build6四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。

修复前red1为0/4；final1为23/26，包含IME自动聚焦问题、重开回读等待和过时后台断言；final2为24/26，余下两项为错误标签匹配与旧测试契约。final3为25/26，旧密钥掩码断言改由隔离回调验证；原始失败保留，未覆盖第6项及前组偏好异常。产品源码自build3后保持，build4/5/6只修测试。

证据`docs/acceptance/extension-batch-remote-form-redmi-20261107.json`，溯源`docs/extension-batch-remote-form-defects.md`，本机附件`../acceptance-artifacts/extension-batch-remote-form-redmi-20261107/`。

本组真实主宿主仅编辑/取消测试链接，实际保存删除只操作原1.yaml副本；组件模拟失败/等待/规则外删不等于正式库故障注入或冷进程恢复。首次自动IME修复等待Dialog窗口焦点，未加固定业务延时。旧密钥用例按现有加密编辑契约修正，未回退产品行为。

下一组优先代理添加到本地的实核校验/保存失败及等待中切换配置、本地代理删除长引用/取消/失败和分享链接IME返回；随后管理窗口/扩展/Provider正文冷进程、保存受理中断、目标外部删除及正文独立加密草稿，再统一Flutter视觉/连续动效。第6项原保护异常、前组偏好变化及网络稳定性仍待查，蜂窝延期，玻璃默认关闭。

### 2026-11-07：本地代理确认、节点添加与分享链接 — DONE-VERIFIED（本组范围，整体视觉仍PARTIAL）

KUI-006/007已修：本地代理删除/替换确认可滚动且原位显示失败与等待；添加节点异常按阶段脱敏，重复分支也复核当前配置。Redmi final2精确12/12、43图经完整集合/哈希/解码及13张拼图目视；新增5条覆盖40个长引用、隔离Room真实失败/重试/防重复、实核拒绝、受理前后切配置及单/批链接真实IME/Back/重建。788项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；双包回拉匹配，正式两配置/脚本/正文/偏好/选择、原1.yaml/Flutter/设备保持。

App512本组build1/build2实际执行，其余276复用；最终build6四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。

新增5方法为Debug隔离宿主、真实Bridge与隔离Room，长引用结果受控注入，不代替正式MainActivity添加失败/切配置、真实引用查询、运行态或冷进程。final1的ALPN立即重建曾丢值，11/12；最终8轮输入重建回归通过仍未定位根因，保留待查。完整Flutter视觉仍PARTIAL。

证据`docs/acceptance/local-proxy-interactions-redmi-20261107.json`，溯源`docs/local-proxy-interaction-defects.md`，附件`../acceptance-artifacts/local-proxy-interactions-redmi-20261107/`。

优先补正式MainActivity节点添加失败/重试、保存等待切配置及真实长引用查询，并定位ALPN立即重建间歇丢值；再推进管理窗口/扩展/Provider正文冷进程、受理中断、外部删除及独立加密草稿；随后统一视觉，系统/性能/发布按原顺序。第6项原保护异常、前组偏好变化及网络稳定性仍待查，蜂窝延期，玻璃默认关闭。

### 2026-11-07：ALPN停止回写修复 — DONE-VERIFIED（本组范围）

KUI-008已定位并修复：本地代理输入提交后立即重建，旧窗口ON_STOP后由Compose onBlur回传旧文本；正式页面拒绝已停止宿主的业务修改，独立保存树照常捕获。Redmi final1精确14/14、43图完整校验及13拼图目视；语义输入/失焦与平台InputConnection各8轮，含合法清空，VM及页面精确值保持。788项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；安装回拉匹配，两份正式配置/脚本/正文/偏好/选择及原1.yaml/Flutter/设备设置保持。App512本组build3实际执行，其余276复用；最终build4四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。本组为Debug隔离宿主上的正式页面/VM，InputConnection由测试驱动，不代表全部输入法、正式MainActivity全链路、冷进程或完整Flutter视觉。diagnostic1为1/2、diagnostic2为0/1，两次旧值覆盖及onBlur栈保留；前批final1原始空值未留VM轨迹，不能倒推百分百同因。证据`docs/acceptance/alpn-input-boundary-redmi-20261107.json`，溯源`docs/local-proxy-stopped-input-defect.md`，附件`../acceptance-artifacts/alpn-input-boundary-redmi-20261107/`。继续正式MainActivity节点添加失败/重试、保存等待切配置和真实长引用查询；随后管理窗口/扩展/Provider正文冷进程、保存受理中断、目标外部删除及Provider独立加密草稿，再统一Flutter视觉/连续动效。第6项原保护异常、前组偏好变化及网络稳定性继续待查；蜂窝延期，效果/性能/兼容/发布按原顺序，玻璃默认关闭。

### 2026-11-07：正式本地代理异常链路 — DONE-VERIFIED（本组范围）

正式MainActivity本地代理异常链路已验：真实Room失败经Snackbar反馈、菜单重试只新增一次并跨重建回读；真实配置卡片在受理前切换拒绝写入、受理后保存冻结原节点；40份原正文CUSTOM副本的真实引用查询、首尾滚动、取消保留批选、等待防重复、删除失败原位提示/重建/重试及引用正文保持通过。Redmi final3精确18/18（4新增+14相邻回归）、55图完整校验及16拼图目视；788项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过，安装/源码匹配。两份正式配置/脚本/正文/偏好值/选择、原1.yaml、Flutter和设备设置保持，临时副本/本地节点/SQL触发器清理，收尾无服务。App512在本组build1/build2实际执行，其余276复用；最终build3四模块UP-TO-DATE，389任务11执行/378复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。本组为STOPPED下真实MainActivity/Hilt/Bridge/正式Room；窄Debug门闩仅控制校验/保存前等待，SQL错误类型与固定标记计数证明故障命中，Release无布置接口/测试标记。长引用按名称扫描原文，原文同名inline节点优先，不证明删除后路由失效。未新验运行态、冷进程、全部输入法或完整Flutter视觉。证据`docs/acceptance/formal-local-proxy-redmi-20261107.json`，范围与首轮失败记录`docs/formal-local-proxy-acceptance.md`，附件`../acceptance-artifacts/formal-local-proxy-redmi-20261107/`。下一组推进管理窗口/扩展/Provider正文冷进程、保存受理中断、目标外部删除及Provider独立加密草稿，再统一Flutter视觉/连续动效。第6项原保护异常、前组偏好变化及网络稳定性仍待查；蜂窝延期，效果/性能/兼容/发布按原顺序，玻璃默认关闭。

首轮final1为14/18：直接await等待菜单退场后的动作却未推进Compose测试时钟，超时后迟到动作导致一份测试副本和本地节点残留；其后3条因重复原文拒绝准备。精确恢复1/1只按唯一ID/名称/原文及节点定义回收，恢复后完整快照与原安装前一致。修正帧推进及分步清理后final2为18/18；最终build3前移恢复门禁，final3同清单再次18/18。各轮独立，不累计。

### 2026-11-07：Provider正文独立密文与正式冷恢复 — DONE-VERIFIED（本组范围）

Provider正文独立加密草稿与正式冷恢复已验：正文/选区/失败/放弃确认跨进程保持；保存前中断等待显式重试、提交后恢复不重放；外部正文冲突、配置删除、引用源记录/文件删除保留草稿并阻止写入；派生缓存缺失由未变源安全重建。KUI-010失效引用配置无法切走已修，保留原ID/节点选择及已应用输入用于失败回滚。Redmi final5九场景首次恢复9/9、提交后及两类源删除再次恢复3/3，相邻回归10/10，22图完整校验并目视。802项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；App521/Service212本次重跑，Core38/Common31复用，build11共389任务60执行/329复用。Debug Lint0错误/67警告/10提示，Release0错误/66警告/9提示。安装回拉与源码匹配，正式两份配置/正文SHA-size-mtime/脚本/偏好值/选择、原1.yaml、Flutter包与设备设置保持，临时对象和密文清除，收尾无服务。本组为STOPPED下真实MainActivity/Hilt/Room/Bridge、受控force-stop与授权原1.yaml的自有副本；不推广运行态死亡窗口、MRS只读冷恢复、全部Provider类型、OEM自然回收或完整Flutter视觉。KUI-010运行态回滚有JVM证据，尚非实网验证。首轮质量缓存ip_quality_cache_v1变化、提交后准备失败、final3旧提示提前放行、final4源删除后切换缺陷以及对应精确恢复均独立保留；本次键级归因不追认前组未知偏好变化，第6项原保护异常与网络稳定性仍待查。证据`docs/acceptance/provider-document-cold-redmi-20261107.json`；溯源`docs/provider-document-recovery-defect.md`、`docs/broken-current-profile-switch-defect.md`；附件`../acceptance-artifacts/provider-document-cold-20261107/`。下一组继续管理窗口与扩展页冷进程、保存受理中断、目标外部删除，再统一Flutter视觉/连续动效；Provider运行态与MRS只读冷恢复另列补验。蜂窝延期，效果/性能/兼容/发布按原顺序，玻璃默认关闭。

### 2026-11-07：配置管理窗口独立密文与正式冷恢复 — DONE-VERIFIED（本组范围）

配置管理重命名/删除/排序独立加密会话、保存意图与提交结果核对、失败原位重试及外部目标冲突保护已验，KUI-011本组闭环。Redmi final7首次冷恢复17/17、提交后再次恢复3/3，相邻回归11/11，33张PNG完整校验并经11张拼图目视。814项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；App533在本组build4实际执行，Service212/Core38/Common31复用，最终build8四模块UP-TO-DATE、389任务11执行/378复用。Debug Lint0错误/67警告/12提示，Release0错误/66警告/11提示。安装回拉和572份源码匹配；正式两份配置/正文SHA-size-mtime/脚本/偏好值/选择、原1.yaml、Flutter包与设备设置保持，临时对象与草稿清除，收尾无服务。本组仅STOPPED下真实MainActivity/Hilt/Room、受控force-stop；不代表运行态死亡、OEM自然回收或完整Flutter视觉。final5早先17+3通过独立保留，final7增加实际Room名称/顺序和正文结果断言；中间失败与精确恢复见KUI-011，不累计。下一组为扩展页独立冷恢复、保存中断与原规则外部修改/删除保护；Provider运行态/MRS只读冷恢复、统一Flutter视觉及第6项原保护异常/网络稳定性继续待补。蜂窝延期，玻璃默认关闭。证据`docs/acceptance/profile-management-cold-redmi-20261107.json`；溯源`docs/profile-management-cold-recovery-defect.md`；附件`../acceptance-artifacts/management-cold-20261107/`。

### 2026-11-07：扩展页独立密文、提交回执与正式冷恢复 — DONE-VERIFIED（本组范围）

扩展页局部/全局规则独立加密会话、提交前后冷恢复、事务回执防重放、外部规则修改/删除及宿主删除保护已验，KUI-012本组闭环。Redmi final2首次冷恢复18/18、提交后与回执缺口再次恢复4/4，相邻回归11/11，33张PNG完整校验并经11张拼图目视。828项JVM零失败/错误/跳过，Debug/Test、未签名Release-R8及双Lint通过；执行/复用明细以本组JSON为准。Debug Lint0错误/67警告/13提示，Release0错误/66警告/12提示。安装回拉和578份源码匹配；两份正式配置/正文SHA-size-mtime/脚本/偏好/选择、原1.yaml、Flutter包及设备设置保持，临时对象、密文和提交标记清除，收尾无服务。首轮local-applied恢复过早就绪及后台回执覆盖新UI树已分别红绿复现并修复，原失败保留。本组仅STOPPED/受控force-stop；不推广运行态死亡、OEM自然回收、独立全局规则页冷恢复或完整Flutter视觉。下一组Provider运行态/MRS只读冷恢复及失效引用实核回滚，再统一Flutter视觉；第6项原保护异常、未知偏好变化和网络稳定性仍待查。蜂窝延期，玻璃默认关闭。证据`docs/acceptance/profile-extension-cold-redmi-20261107.json`；溯源`docs/profile-extension-cold-recovery-defect.md`；附件`../acceptance-artifacts/extension-cold-20261107/`。

### 2026-11-07：Provider MRS冷恢复、运行态保存中断与失效引用实核回滚 — DONE-VERIFIED（本组范围）

Provider本组已验：MRS只读正文首次/再次冷恢复及原面板返回通过；纯代理保存前后SIGKILL经外部独立观察系统新PID/前台服务，恢复不自动重放、显式重试仅一次；停止态/运行态各源文件或源记录缺失4类真实setup拒绝后回滚旧输入/选择/会话并成功切往有效目标，epoch各增加2。KUI-013父面板冷恢复后错误常驻已红绿复现并修复，KUI-010补齐实核回滚证据。Redmi final7运行态/回滚6/6、12次同socket核心归因HTTP200，MRS 1+1、相邻回归10/10，22张PNG完整校验及8拼图目视。build7 App549实际执行、Service212/Core38/Common31复用，共830项JVM零失败/错误/跳过；389任务28执行/361复用，Debug/Test/未签名Release-R8及双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。安装回拉及579份Android源码匹配；正式两配置/正文SHA-size-mtime/偏好/脚本/选择、原1.yaml/Flutter包/设备基线保持，临时对象/密文/标记清除，无服务或tun0。MRS为真实MainActivity STOPPED，首次存活PID受控force-stop、再次恢复前进程已退出，运行态为Debug宿主真实VM/Room/核心的纯代理SIGKILL，非VPN/OEM自然回收或完整点击导航。final6-before的verify超时原因未定，后续通过不抹除；第6项原保护异常、未知偏好变化与网络稳定性仍待查。选项错误截图滚动后提示部分裁切、筛选与保存图含转场中间帧，下一组补键盘错误可见性、稳定终帧及剩余子页/面板，再集中Flutter排版/连续动效；独立全局规则页/资源选择器冷进程另列。效果/性能/兼容/发布按原顺序，蜂窝延期、玻璃默认关闭。证据`docs/acceptance/provider-runtime-redmi-20261107.json`；溯源`docs/provider-cold-parent-refresh-defect.md`；附件`../acceptance-artifacts/provider-runtime-20261107/`。

### 2026-11-07：Provider选项错误、真实IME与子面板交互 — DONE-VERIFIED（本组范围）

KUI-014已红绿复现并修复：选项页重复同一错误按提交次数回顶，选项/快捷YAML从实际Dialog取得焦点与IME控制器，保存后收键盘。Redmi final2精确9/9（6新增+3相邻回归）、21张PNG完整校验及7拼图目视；覆盖长过滤与YAML、逐层返回/放弃重开、校验/保存防重复、代理/规则各40项绑定失败取消重试及别名保持。build4 App549执行、Service212/Core38/Common31复用，共830项JVM零失败/错误/跳过；389任务22执行/367复用，Debug/Test/未签名Release-R8及双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。安装回拉与577份源码匹配；正式两配置/正文SHA-size-mtime/偏好/选择、原1.yaml/Flutter包/设备设置保持，收尾无服务。red1 0/2、final1 6/9和build3既有Picker单测清理等待失败保留；单测改等实际关闭终态后build4全量通过。此组为正式Compose页面的固定组件宿主/受控回调，非新增MainActivity/Room/核心/实网或冷进程验收；返回先等Window入场，静态终帧不等于连续动效。本组选项/绑定Activity重建与入场中Back、独立全局规则页/资源选择器冷恢复、上一组正文保存终帧继续待补，再统一Flutter排版/连续动效。第6项原保护异常、未知偏好变化、网络稳定性和前组final6超时仍待查；玻璃默认关闭，既有暂缓/取消范围保持。证据`docs/acceptance/provider-options-interactions-redmi-20261107.json`；溯源`docs/provider-options-validation-defect.md`；附件`../acceptance-artifacts/provider-options-interactions-20261107/`。

### 2026-11-07：Provider子页正式重建与入场中返回 — DONE-VERIFIED（本组范围）

KUI-015已红绿复现并修复：Provider选项本地错误/放弃确认与快捷YAML格式错误随Activity重建保存；局部校验忙态不持久化。Redmi final4精确16/16（7条正式MainActivity/Hilt/VM/Repository/Room用例＋9条组件回归），36张PNG完整校验及12拼图目视；过滤/IP/绑定搜索恢复、保存等待重建与防重复、7个子页入场中系统Back均通过。build5共830项JVM零失败/错误/跳过，App549在build1执行、其余281复用，最终四模块UP-TO-DATE；389任务11执行/378复用，Debug/Test/未签名Release-R8及双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。双包安装回拉和578份源码匹配；正式两配置/正文SHA-size-mtime/脚本行/偏好/选择、原1.yaml/Flutter包/设备设置保持，7次保护回报确认临时Room行及正文文件均删除，收尾无服务。red1 0/3、final1 14/16、final2与final3各15/16独立保留，不累计。新增正式用例为Activity重建，非冷进程/VPN/OEM自然回收；入场Back已确认目标窗口焦点及64ms时横向位移，非焦点交接前按键或连续动效性能。局部异步校验重建取消、正文保存终帧、独立全局规则页/资源选择器冷恢复仍待补，再统一Flutter排版/连续动效。第6项原保护异常、未知偏好变化、网络稳定性和前组final6超时仍待查；玻璃默认关闭，既有暂缓/取消范围保持。证据`docs/acceptance/provider-options-state-redmi-20261107.json`；溯源`docs/provider-options-restoration-defect.md`；附件`../acceptance-artifacts/provider-options-state-20261107/`。

### 2026-11-07：Provider校验取消与正文保存稳定终帧 — DONE-VERIFIED（本组范围）

Provider校验返回后、保存受理前的Activity重建取消已验：旧任务取消且释放旧门闩不迟到写库，过滤/450秒草稿保持，显式重试后revision仅+1。正文saving_recreated补采位移归零的稳定终帧，固定运行时仅保存一次。Redmi final1精确18/18（8条正式MainActivity/Hilt/Room＋9条组件回归＋1条正文固定运行时），39张PNG完整校验及13拼图目视；8次正式保护与7入口入场Back通过。build1共830项JVM零失败/错误/跳过，App549执行、其余281复用；389任务45执行/344复用，Debug/Test/未签名Release-R8与双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。安装回拉及580份源码匹配，Release等待点接口/标记隔离；正式两配置/正文SHA-size-mtime/脚本行/偏好/选择、原1.yaml/Flutter包/设备设置保持，自有临时行及正文删除，收尾无服务。未新增产品缺陷；取消窗口不推广原生回调执行中，正文终帧不推广正式缓存/冷进程或连续动效性能。下一组独立全局规则页GR-01至GR-05，再真实资源选择器SAF-01至SAF-05，具体清单见docs/remaining-main-page-acceptance.md；随后统一Flutter视觉/连续动效。第6项原保护异常、未知偏好变化、网络稳定性和运行态final6-before超时仍待查，蜂窝延期、玻璃默认关闭。证据`docs/acceptance/provider-validation-boundary-redmi-20261107.json`；附件`../acceptance-artifacts/provider-validation-boundary-20261107/`。

### 2026-11-07：独立全局规则草稿、批选、冲突与冷恢复 — DONE-VERIFIED（GR-01～05本组范围）

独立全局规则页GR-01～05本组已验：草稿/错误/选区/放弃确认、可见批选失败重试、原行修改/删除及排序冲突、独立Keystore密文、Room同事务回执防重放和冻结应用目标。Redmi正式交互5/5、cold2冷恢复14/14及提交后再次恢复2/2、相邻回归4/4；16张PNG完整校验与6拼图目视。843项JVM零失败/错误/跳过，App562在build6执行、其余281复用；build8四模块UP-TO-DATE，Debug/Test/未签名Release-R8与双Lint通过（Debug0错误68警告14提示，Release0错误66警告13提示）。最终586份源码匹配，Release14个序列化字段及Debug诊断隔离已检查。真机使用build7同套包；build8仅补未执行的旧GlobalSettingsHost测试收尾，所有产品/实际执行用例源码和Debug/Release APK一致，新的测试APK未安装。正式两配置、正文SHA-size-mtime、脚本、偏好、选择、原1.yaml/Flutter包/设备设置保持，自有规则/草稿/回执清理，结束无服务。KUI-016记录来源及红绿证据；cold1旧after选区失败缺死亡前观测，原因不追认，cold2逐场前后严格比对通过。STOPPED受控force-stop不推广VPN/OEM自然回收，切配置目标冻结由JVM覆盖。下一组真实资源选择器SAF-01～05，随后统一Flutter视觉/连续动效；整体第9项仍PARTIAL，第6项原异常/网络稳定性、蜂窝及兼容/发布边界保持，玻璃默认关闭。证据`docs/acceptance/global-rules-recovery-redmi-20261107.json`，溯源`docs/global-rules-recovery-defect.md`，附件`../acceptance-artifacts/global-rules-20261107/`。

### 历史切片2026-11-08：主页面排版、面板、空态与自然时钟交互 — 具名范围已验，当时性能待定位

本段保留KUI-018之前的混合采集结论，当前菜单性能及后续顺序以下节和页首为准。

固定Flutter `be847366`/Prism `63450c0`，VIS-01～06及MOT-01交互回归已验：最终build6同套包14/14回归、2/2自然深浅场景，62PNG/22拼图及6组原始PTS序列；849项JVM、Debug/Test/未签名Release-R8/双Lint通过。593源码与安装双包匹配，正式两配置、原文件、Flutter包及设备基线保持。连续动效性能未通过：累计592/1872帧jank（31.62%），采集与Debug开销未隔离；下一步优先定位。完整Flutter运行截图逐像素对齐和其余页面仍PARTIAL。来源、失败与边界见`docs/main-visual-motion-alignment.md`，权威证据`docs/acceptance/main-visual-motion-redmi-20261108.json`，截图摘要`docs/visual-baselines/main-visual-motion-redmi-api36.sha256`。

### 历史切片2026-11-08：菜单阴影缓存与独立性能对照 — KUI-018本组范围

KUI-018菜单掉帧热点已定位并优化：稳定后层轮廓、阴影透明度独立合成、普通展开取消每帧命中订阅；补截图前开关与分阶段帧统计。Redmi API36非debuggable独立包、无录屏/PNG，优化前后各两轮深浅：二级菜单jank61/92（66.30%）→20/129（15.50%），主线程阶段中位34.25→9.45ms；非启动总窗口621/4401（14.11%）→465/4599（10.11%）。方法采样阴影位图调用片段125→58、包容耗时954→396ms，非精确调用计数；面板尝试因退化撤回，最终面板62/535（11.59%）。最终build2共849项JVM零失败/错误/跳过，App568执行、其余281复用；Debug/Test/未签名Release-R8/双Lint通过，Debug0错误68警告14提示、Release0错误66警告13提示。同套最终包回归21/21、自然深浅2/2，另深色终帧补验1/1；88PNG/31拼图和7组原始PTS序列已核对，593源码与安装回拉匹配，Release诊断隔离。正式两配置/正文SHA-size-mtime/Room偏好/选择、原1.yaml/Flutter包/设备设置全组保持，独立.motion双包及独占采集文件已收尾，结束无服务/tun0。两张玻璃开场图和一张List图为过渡帧，List原视频及同包补验确认完整节点到达。最终对照位于基线之后，非随机实验；独立包未混淆且库按debug回退，不冒充正式Release。整体性能与完整Flutter视觉仍PARTIAL；下一步优先菜单首帧/动态顶卡与剩余渲染慢帧，再补CUSTOM组数、首页无流量统计、内存图形及其余子页。玻璃默认关闭，既有边界保持。证据`docs/acceptance/motion-performance-redmi-20261108.json`，溯源`docs/motion-performance-defect.md`，附件`../acceptance-artifacts/motion-performance-20261108/`。

### 2026-11-08：动态顶卡与重复菜单精确复验 — IMPROVED-PARTIAL（KUI-019）

KUI-019继续优化菜单动态轮廓：阴影局部重组、同帧Path/Paint复用、宽度延迟布局及移除重复裁剪。Redmi API36独立非debuggable包、无PNG/录屏，前后各两轮深浅，菜单合计jank331/1429（23.16%）→261/1555（16.78%），顶卡收回38.61%→14.07%；首开26.42%→23.16%改善有限，关闭6.91%→9.94%退化保留，首次打开UI区间中位也未改善。完整自然流程各深浅一轮非启动窗口15.10%→13.39%，不冒充正式R8运行。final-build3共849项JVM零失败/错误/跳过（App568在build1执行、其余281复用，最终四模块UP-TO-DATE）；Debug/Test/未签名Release-R8/双Lint通过，Debug0错误68警告14提示、Release0错误66警告13提示。同套最终包回归19/19、自然整页2/2、三轮菜单2/2；92PNG/32拼图及8组原PTS抽样已核对，593源码与安装回拉匹配。用户确认重置，当前正式0份配置，排除依赖非空库的两条导航回归；Flutter参考包保持缺席、不安装。44份正式快照及最终独立检查一致，原1.yaml/设备设置保持；独立.motion双包与独占文件精确回收，结束无服务/tun0。旧包/候选12图逐字节一致后刷新环境基线，14/19、旧包0/5与run-as拒绝回拉失败均保留。整体性能和完整Flutter视觉仍PARTIAL；下一步优先首开、关闭退化及RenderThread慢帧，再补CUSTOM组数、首页无流量统计、内存图形与其余子页。玻璃默认关闭，既有边界保持。证据`docs/acceptance/menu-rendering-redmi-20261108.json`，溯源`docs/menu-rendering-performance-defect.md`，附件`../acceptance-artifacts/menu-rendering-20261108/`。
