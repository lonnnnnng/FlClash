# 添加配置交互与内存优化

2026-11-20，KUI-VIS-063。用户反馈添加配置弹框卡顿、Kotlin内存约为原版两倍。本轮使用POCO F2 Pro（31936bb6/API36）的独立R8包，在空配置、核心停止、竖屏、字号1.0、玻璃关闭条件下对照；没有启动或修改原版`com.follow.clash`。

## 源码问题与修复来源

| 问题 | 来源与修复 | 撤销或继续核查条件 |
| --- | --- | --- |
| 打开菜单时逐帧生成软件阴影路径及纹理，重复操作后Native/Graphics峰值高 | Kotlin `AppMenuPlatterShadow` 原实现以每一帧几何创建`Shape/dropShadow`；改为一次最终卡片阴影纹理，经图层缩放/平移/透明度完成动画，真实液滴联合轮廓仍负责内部扣空。Flutter/Prism只作为材质与时序参考，不合并Flutter代码 | 动画期间阴影柔和宽度、角部及偏移随缩放变化，是明确视觉取舍；稳定终帧保持。若子菜单回源出现裁剪/残留须复核，不把本组视为完整逐像素Flutter一致 |
| 首页无条件扫描全部安装应用与label，加载大量APK资源 | Kotlin `AccessControlViewModel` 初始化曾直接`reload/readApps`；改为先读名单偏好，进入名单页才扫描，并接住偏好尚未返回时的进入请求 | 名单首次进入、权限重试、草稿保持不得退化。进入名单页仍会按需加载这些资源，不宣称永久消除其成本 |
| 批量粘贴在Compose主线程逐行分词、构造Regex及校验URI | Kotlin `AddProfileDialog/ProfileUrlImport` 改为后台可取消解析、120ms输入合并；复用分词Regex并用Sequence。输入/已存在集合/单批模式/来源页变化立即撤销旧预览，只有当前完整结果可提交 | 返回来源页及关闭均取消；不能采用旧批次结果、吞掉取消或重复提交。无真实URL写入测试/证据 |
| 图库更新重复解码与扁平化列表反复分配 | `ResourceUpdatesRepository` 监听无正文解码的失效流，再统一刷新；`MainActivity`按图库集合记忆扁平列表 | 必须保留改名、同步、删除、顺序及新图库触发，不能以缓存掩盖更新 |

没有修改Mihomo、VPN路由或网络恢复策略。观测列表已有有界容量、正文关闭已有清理，本轮未发现证据证明它们是无限增长来源，不做无依据的大规模删功能。

## 测量边界与可重复入口

`performanceBenchmark`继承release/R8/资源压缩及release库，包名`.performance`、debug签名、非debuggable，只增加shell profile能力；不带验收宿主或Instrumentation。正式Release不增加profileable。构建命令见`.agents/commands.md`。

本机附件`../acceptance-artifacts/interaction-memory-poco-20261120/`含原始APK、安装回拉SHA、源码哈希、`driver.py`、`install.py`、`trace.py`、SQL/CSV、逐阶段gfx、meminfo与UI终态。`driver.py measure <新目录>`每次冷进程：首页→配置→5轮来源菜单/链接表单/关闭键盘/关闭表单→静置8秒→工具与四Tab。25阶段不录屏、不截图、不运行Instrumentation；UIAutomator读取终态和shell输入仍有观测开销。

内存是单进程PSS，分别记录Java/Native/Graphics、Views和ViewRootImpl。五次开关后的峰值与静置后的值分开，窗口从10回到1，不把可回收峰值认作泄漏。原版只读快照处于后台、Activities/Views/Graphics为0，与Kotlin前台不同态，不能据此确认或否认“两倍原版”；运行核心、非空大型配置的倍数比较仍未验证。

## 最终结果

最终build-final3：App626、Service220、Core39、Common34共919项JVM结果零失败/错误/跳过（App最终motion变体重新执行，其余293项由本组前批执行后复用）；performance-R8、motion/Test、未签名Release-R8均通过。独立执行双Lint：motion 0错误/75警告/15提示，Release 0错误/71警告/14提示；三个MLKit注册器DEX门禁和2179组中英文资源检查通过。最终安装回拉与源码哈希匹配，正式Release清单无profileable及debug宿主。

POCO同套最终包：`ui-final1` 21/21、`ui-menu-extra` 3/3，共24个不同方法通过。覆盖四来源、单条校验/防双击、批量去重/错误/替换/既有集合变化、age密钥生命周期、QR确认表单、名单与图库、四Tab搜索、阴影扣空、多层回源、长菜单滚动和拖选。44张分片SHA核验截图经4张拼图及子菜单单图检查；另R8真实MainActivity前后4张稳态截图，排除状态栏后两个页面像素差均为0。动画期阴影缩放取舍明确保留，不宣称所有中间帧像素一致。

真实MainActivity首次进入“按应用代理”正常显示设备应用；资源资产从首页26份按需升至282份，证明没有以删掉名单功能换取启动内存下降。名单保存/重建另由Room与UI定向测试覆盖。

| 场景 | 旧包PSS（KiB） | 组合候选PSS（KiB） | 中位下降 |
| --- | --- | --- | --- |
| 冷启动首页 | 214763 / 226864，第三轮缺测 | 147928 / 147907 | 33.01% |
| 首次链接表单 | 237876 / 248971 / 250003 | 206418 / 206368 | 17.10% |
| 五轮弹框后 | 323658 / 324180 / 323053 | 213475 / 213897 | 33.98% |
| 再静置8秒 | 215694 / 216028 / 215097 | 182467 / 182553 | 15.38% |
| 空配置网络活动页 | 226202 / 226311 / 224724 | 192513 / 192784 | 14.83% |

顺序为baseline2→单独阴影shadow1→baseline3→combined1→baseline4→最终combined2，每组五轮25阶段，共150阶段，分批不合并成一个性能分数。单阴影候选的五轮后217280、静置185084KiB，支持纹理缓存峰值收益；首页尚未懒加载时仍225617KiB。最终组合两轮结果接近，旧包两次回装峰值复现。

**总体卡顿比例尚无稳定改善。** 来源菜单进程jank旧包21/159、13/168、17/171；组合候选14/170、17/172。来源回源→表单交接旧包24/294、23/292、22/292；组合25/288、22/295。键盘关闭这些轮次均0卡顿，但不是整个表单关闭动画。主线程帧计算和内存收益不能替代首帧/整体帧率结论。

测试后核对安装SHA并卸载三个独立自有包，包清单、原Flutter身份/安装时间、系统设置及原`1.yaml` SHA/大小/mtime保持；没有启动核心或导入真实配置。未发版。完整结果与源码/制品/原始证据摘要见[验收JSON](acceptance/interaction-memory-poco-20261120.json)。

## Perfetto线程证据

`baseline-trace1`和`combined-trace1`均包含sched、gfx/view/dalvik及FrameTimeline，stats中非零error/data_loss为0。按Popup帧token匹配同进程UI/RenderThread切片并与sched相交；250ms静默间隔分为首次展开、孤立更新、选择后回源三个片段。线程时间为同一进程帧工作，不是窗口独占CPU；切片包容时间不能相加。

这一对trace中，展开主线程scheduled中位8.366→2.274ms，回源6.088→3.280ms；首帧仍约67ms，候选首次traversal50.744ms、重组/初始化/首次测量仍有开销。回源RenderThread scheduled中位2.446→2.378ms，睡眠等待中位13.968→14.022ms；可见`waitForBufferRelease`等待，不能将其当成同等CPU运算或声称GPU驱动函数根因已确定。这是一对诊断记录，统计收益以多轮gfx结果另判。trace及稳态对照图属于combined1候选；最终combined2仅补批量表单返回来源页的取消边界，仍按不同源码批次留档，不将候选trace称为最终源码重采。

## 原始失败和限制

- `inspect0`的`uiautomator /dev/tty`路径不支持；改独占设备XML路径并核对读取后精确删除。
- `baseline1`空配置页没有顶部添加按钮，驱动改从真实“添加配置”入口定位。失败批次不计通过。
- `baseline4`首页和配置页的两份meminfo只有头部、没有PSS，记缺测，不补零或与其他轮拼装。其余五份内存与全部gfx原始数据保留。
- `build-final-ui`新增测试缺少`performTextReplacement`导入，补导入后重建；生产编译此前已通过。
- 最终审查发现返回来源页未取消批量解析，补充step生命周期键，最终源码另构建/复测。
- `ui-final1`的JUnit原始21/21通过，但收集器误将471×558的菜单节点截图当作全屏；修正白名单后对原始分片离线核验SHA、字节数和尺寸，不重跑、不累计。
- 表单窗口销毁后gfx不再包含该窗口，不能把宿主0～1帧当作关闭动画零耗时；四Tab空列表有的阶段只有一帧，不将100%比例等同持续卡顿。
- 本轮不重跑网络/核心、低API、其他OEM或全应用每个页面，也不发布新版本。完整连续Flutter对照、菜单首帧和GPU/原生函数归因保持待办。
