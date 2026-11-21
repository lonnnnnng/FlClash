# KUI-VIS-073 资源选项、长URL与菜单销毁定域

2026-11-21，POCO F2 Pro（31936bb6，API36），竖屏、默认字号，玻璃默认关闭。本组补资源选项面板与脚本 URL 输入，并拆出菜单 Popup 关闭时的本地组合释放和系统窗口移除成本。

## 面板与输入

本地图标集名称编辑使用共享输入浮框；远程图标集选项使用同行名称／URL Sheet，URL自动聚焦并支持IME提交；已有远程脚本“修改地址”使用URL浮框。新远程脚本导入现也改为单URL浮框，与Flutter的`showUrlInput`路径相符；名称由脚本仓库按下载建议名/URL文件名去重生成，不再单独输入。图标集保存失败保留草稿并解锁重试，成功回执后才退场；脚本下载期间锁定，失败保留草稿，成功后暂留UI状态直至关闭动画完成。

固定Flutter实现参考 `views/config/icon_sets.dart`、`features/form/named_url_form.dart` 和 `views/config/scripts.dart`。隔离Flutter宿主调用原始表单组件，双主题8状态已采集；不是完整上游应用运行或全页像素等价。Kotlin仍保留本地提交／远程下载合同；窗口外框、键盘位置及少量标签文案仍有差异。

URI字段原先在 `country.mmdb` 中间换行。字段可用宽度和字体声明与Flutter相同；Compose Simple／Paragraph策略均在 `country` 后断行，均衡策略会拆开域名。最终实现只在显示层的点号两侧加word-joiner并映射UTF-16偏移，底层文本、复制和提交仍使用原URL。POCO验证完整扩展名换行、长URL/转义查询、插入、复制、IME提交和光标在每个点号边界左右移动；Backspace仍只删除原URL字符。任意连字符URL的逐字符串断点等价尚未覆盖。

2026-11-21续验使用Motion instrumentation APK，在新建脚本和修改地址两个入口重复检查错误保留、Activity重建、取消及原始URL恢复；真实MainActivity新脚本导入入口验证无效URL反馈、重建保留长URL、取消后重开清空。没有发起真实网络下载，也没有把下载成功写入Room记为通过。

## 验收范围

POCO前组组件5/5、13张截图校验哈希与尺寸；续验新增6个组件用例和2个MainActivity宿主用例，最终合批8/8，15张PNG校验哈希、尺寸，并目视拼图。Motion App JVM 629项零失败/错误/跳过；Motion/Test、Release-R8及诊断Performance-R8构建通过。Motion Lint 0错误／75警告／15提示，Release Lint 0错误／71警告／14提示；Release ML Kit DEX门禁3/3。正式Release不含诊断scope，独立诊断R8包含标记。

真实MainActivity宿主方法原始JUnit日志为 `OK (1 test)`，覆盖本地改名保存／Activity重建回读、脚本地址错误草稿、重建后取消与脚本正文／元数据保护。它在“没有截图”的JSON中被通用采集器因只识别复数 `OK (n tests)`误判为未通过；本组按JUnit原始输出记录行为通过，不计采集器全绿。前置使用隔离motion库，不读取或导入配置。

自然时钟真机录制已完成：POCO真实Android Window自然推进两个主题下Local选项、远程图标集、已有脚本改址和新脚本导入共8轮打开→编辑→Back取消→重开→提交→关闭，JUnit 1/1、8次提交回执。最终源码对应的Motion/Test APK录得66.131秒录像并核对关键帧。本测试的下载/保存回执由600ms隔离宿主模拟，故它证明这些面板的自然窗口转场，不证明真实下载或全应用连续导航等价。早期状态栏触点遮挡与录制器等待超时失败保留，未计入最终通过。

## 菜单销毁与窗口归因

AGP诊断只在显式 `-PkclashPopupDisposeTrace=true` 且独立 `performanceBenchmark` 变体中插桩Compose 1.12.1：`disposeComposition`、输入连接解绑、`PopupLayout.dismiss`/`removeViewImmediate`、`onDetachedFromWindow` 各有独立同步区间；原调用顺序不变。普通R8和正式Release不携带这些探针。

两轮普通R8关闭因果trace已有system_server链路；本轮诊断R8先采4次，将组合销毁、输入解绑、窗口移除和detach分开。另在同一POCO/API36的诊断R8普通关闭路径采2轮6秒Perfetto关闭trace：

| 阶段 | wall | Running |
| --- | --- | --- |
| 主 `disposeComposition` | 2.569–3.138 ms | 2.569–2.931 ms |
| `PopupLayout.dismiss` 与窗口移除 | 7.223–11.423 ms | 1.468–2.697 ms |
| 输入连接解绑 | 0.004–0.006 ms | 0.004–0.006 ms |
| Popup detach | 0.039–0.122 ms | 0.039–0.122 ms |

每个已注册菜单项注销为0.009–0.126 ms；捕获图引用释放约0.056–0.061 ms；指针/hover释放各约0.005–0.011 ms。主要应用本地组合销毁约2.6–3.1 ms；窗口移除内同步等待wall 4.381–7.004 ms，并沿flow对应system_server回复。SurfacePlacement与焦点布局仍是可见系统工作。同token RenderThread约3.615–4.812 ms，没有 `waitForBufferRelease` slice。短业务清理不足以解释全部关闭时间，没有找到可安全删掉的毫秒级重复清理，也没有把窗口移除异步化。

续采两轮的同帧 `disposeComposition` 为3.100/2.864 ms Running；`PopupLayout.dismiss/removeViewImmediate` 外层为8.917/9.058 ms，其中UI线程Running 1.861/1.919 ms、等待6.179/6.442 ms。对应system_server binder reply wall 6.220/6.498 ms、Running 5.313/5.136 ms；内部`performSurfacePlacement`、`wmUpdateFocus`和`removeInputChannel`均为嵌套子工作，不能与reply相加。条目注销≤0.125 ms，关闭后引用/指针/hover/lifecycle清理≤0.077 ms。结果支持窗口系统等待占有显著部分关闭墙钟时间；不证明所有等待都由某个单一系统函数造成，也没有找到安全删减的毫秒级业务清理点。

诊断帧为14.687–23.255 ms，含插桩开销；与普通R8帧不可直接比较收益。前组trace有26／27、新增短trace有28／29个全局`traced_chunks_discarded`；环形缓冲写入覆盖、per-buffer discarded与各数据丢失计数均为0，故不是buffer写满或Android丢帧，但producer级全局discard原因未定位，采样仍标为部分完整。父子wall/Running切片重叠，不能相加。GPU/fence完整因果和稳定优化收益仍未证明。

设备结束时本轮三个自有motion/test/performance包均按安装APK SHA核对后卸载；包清单、字体、旋转、夜间模式和参考Flutter的稳定安装字段与起始快照一致，KClash/Flutter服务记录0。原Flutter未启动或修改，未读原1.yaml／2.yaml，未启动核心/VPN。

源码、组件图、原始trace、失败日志、构建产物和设备回读见[本组验收记录](acceptance/resource-options-url-disposal-poco-20261121.json)。本组未发版。工作区中的其它既有变更与本组范围分开保留。
