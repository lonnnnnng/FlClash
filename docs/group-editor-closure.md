# KUI-037：组编辑持久化与面板交互收口

本组始于2026-11-10、收尾于2026-11-11。固定基线为 Kotlin `3664513bb8903029635d09366874d2edd88042b9`，Flutter `be84736605527034689c95bf27828ff27d912c5e`，Prism `63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185`。Flutter 仅参考，没有合并提交；运行对照沿用KUI-036已留证的固定安全副本，本组未操作参考包。附件位于工作区 `acceptance-artifacts/group-sheet-closure-20261110/`；最终批次、哈希和未验边界见[验收 JSON](acceptance/group-sheet-closure-redmi-20261110.json)。

## 已保存布局的真实冷读

`GroupLayoutColdInstrumentedTest` 使用正式 MainActivity、Hilt 和磁盘 `kclash.db`，强制独立 `.motion` 空库、核心停止、原 `1.yaml` 当轮大小/SHA 门禁。用同一原文件创建 FILE 资源及自有 CUSTOM 副本，通过正式资源引用加入 Provider 与有效过滤条件；真实长按将过滤行移到节点/Provider之前，点击组确认保存。自有副本的后续改名/复制通过正式 ViewModel 保存，明确核对两个目标组名称及完整成员顺序一致，旧名称布局键消失。

准备方法输出 ready 后保持进程，不进入 Activity teardown。外部核对同 PID 存活后 force-stop，确认旧 `/proc/<pid>` 消失及 ADB 在线，再启动另一轮 instrumentation。新进程先读 DAO 的全行和原文件，比较完整顺序、正文 SHA、修改时间及资源原文摘要，再用新读出的 Profile 打开组页面。组布局只在 `overridesJson.groupMemberLayouts`，Mihomo YAML 没有私有布局键。

## 本地源码缺陷：只改布局的保存中断

这是 Kotlin 本地应用层缺陷，非 Flutter 或 Mihomo 上游问题，不需要向内核仓库同步补丁。

旧 `ProfilesViewModel.restoreStructuredDrafts` 只比较 `current == pendingContent` 来认定组保存完成。跨 FILTER/自动包含等类别拖动时，标准 YAML 字段可以完全不变，仅 Room 的布局变化；若停在 `GROUPS_INTENT`，旧判定可能关闭尚未提交的草稿。

现将预期布局作为 `pendingLayouts` 同正文一起保存到已有加密草稿。恢复时同时比较正文和最新 Room 布局；未提交保留草稿并提示重试，已提交只更新父页面，不自动重放保存。布局编码与实际 `saveLayouts` 同源，使用 JSON 结构比较，空布局归一为 `{}`；未放开任意序列化类型，也不改 Room schema。旧格式草稿没有该字段时保留既有行为，开发阶段不增加历史迁移。

回归包括 JVM 的未提交/已提交两分支，以及 Redmi 原正文相同的 `GROUPS_INTENT`、`GROUPS_COMMITTED` 两个精确死亡窗口。每轮恢复后校验正文/全行/mtime不变且保存探针调用数为0。撤销本补丁必须有上游等价提交判定和同一 metadata-only 回归证据，不能仅凭 YAML 相等删除保护。

## 浮动、拖回与短内容

- `ConnectionModalSheet.floatingForm` 默认关闭，只在组编辑启用。参照 Prism `snap_sheet.dart`：半停靠浮动边距8dp，按9/16至0.9档位间的实际高度插值缩放；展开/键盘120dp时浮动归零。使用同一图层变换参与显示和触点映射，补偿缩放后的底边，底角随浮动回到28dp。
- 组表单最低仍为9/16，较短内容的展开档只增长到实际正文高度；与其他短详情面板原本可低于9/16的行为区分。根LazyColumn完整内容可见时记录自然高度，同一JSON滚动回收离屏行不清测量值；成员/选项按实际Column测量。图库需来源与全部Grid条目真正就绪，扣掉Grid视口超出实际行高的空白，长图库继续滚动。短子页两档重合不覆盖用户原展开意图，返回长页恢复原档位。
- 成员、图库、选项支持横向拖回：30dp接管阈值，位置加0.35秒速度投影达到0.4665宽度才完成，300ms归档；普通点返回保留原过渡。子控件先消费，纵向滚动/多指/取消不导航，进入后台清除手势。父页预览不注册返回或测量，也不写业务草稿。
- YAML 参照 Flutter 的 PopGuard 禁用交互式拖回，系统/按钮返回继续走格式校验和放弃提示。图库取消可能异步拒绝返回，因此手势完成后若路由仍未切换会复位，避免页面停在不可交互状态。

本轮按固定参考的关键几何与行为实现；跟手弹簧、设备圆角、IME连续几何及短内容换页尺寸插值仍可能有像素差异，不声称 Flutter 引擎逐帧完全等价。玻璃默认关闭，其他面板保留默认参数；菜单原生慢帧归因未在本组解决。

## 失败记录与交付边界

最终build5共881项JVM（App596/Service216/Core38/Common31，含Gradle复用）零失败/错误/跳过，Debug与未签名Release/R8构建通过；独立lint5零错误，Debug72警告/14提示、Release68警告/13提示，最终Release扫码注册器DEX门禁3/3。产品motion6回拉SHA为`e41b62e5…36e068a`，最终test7为`187c741c…79f156`，均核对一致。

最终定向`regression-final7`为11/11，含全部7项组Sheet、3项浮动/手势以及添加全部选项回归，8张PNG已核对。前序`regression-final2`为21/22，其中失败方法已在最终包补验；两批去重覆盖22项，不能称单批22/22。三条磁盘Room冷恢复全部在产品motion6执行：saved PID19313→19500、intent19653→19834、committed19961→20137，均先确认原进程存活后终止、旧/proc消失，再由不同PID验证；三轮保存重放探针均为0。冷恢复使用motion6配套测试APK，冷恢复源码与最终test7相同，test7只增强视觉驱动。

最终深浅自然流程2/2通过，16张PNG已核对，录像648/660帧且原PTS单调，各抽32帧目视；包含宿主启动、主题设置和系统退出帧，不计子页视觉等价。组件截图/录屏仅用于具名视觉和触点验收，不当作菜单性能样本。最终隔离库0配置、空偏好、核心STOPPED；本轮双包、2份XML及授权文件设备副本按身份核验后精确清理，原1.yaml字节/mtime保持，参考包保留，结束无服务/tun0。

原失败批次保留：缺少协程导入的编译失败、首次安装通知权限遮挡、过滤行合并语义定位、测试等待保存门闩但未推进退场帧钟、图库加载空档的档位回退、短子页抹掉展开意图、单图Grid误报整个视口高度，以及根Lazy列表滚动回收离屏行时反复清空自然高度的反馈循环。编译、权限、定位及等待问题与产品缺陷分开记录；最后三项视觉问题分别由保留展开意图、扣除网格空白和缓存同内容测量值修复。

构建失败包括 Lint 读取暂时缺失的 Hilt 生成文件；通过先完成 APK 构建、再串行 Lint 重验，没有手工编辑生成文件。`motion-final` 在测试APK覆盖安装未结束时过早启动，被包替换中断并报告 `Process crashed`；安装完成、回拉双APK核对后 `motion-final2` 4/4通过。`regression-final2` 为21/22，失败是IME移动期间物理返回点击未命中；改用语义动作的定向补验通过，不称原批次全绿。`natural-dark-final`已拖回父页，但系统树未提供无文案确认按钮而使驱动报空；最终驱动按标题/工具栏尺寸注入真实触点，并继续断言保存恰好1次，组件另验拖回后父页按钮可操作。历史中间通过不覆盖最终源码。

收尾只读检查首试写错测试类包名而报`ClassNotFoundException`，正确类为`com.lonnnnnng.flclash.ProfilePreparationInstrumentedTest`，`fixture-final-inspection2.log`通过。清理脚本还曾将卸载后的`pm path`退出码1当成执行错误；修正为断言包缺席后继续，只删除已核对身份的两个隔离包。

所有真机动作只在 Redmi Note 8 Pro `wsvwypiz7xwslvl7`、API36、竖屏默认字号执行。原版 `com.follow.clash` 不安装、不修改；固定 Flutter 安全副本仅用于对照。隔离测试通过不能替代正式签名/R8运行、真实网络/核心负载、低API/蜂窝/OEM、历史长窗口异常验收。备份/系统恢复、语言/主题及观测次要打磨继续暂缓，TalkBack不恢复。
