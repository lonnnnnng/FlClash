# 工具页、网络活动与双语外观

2026-11-20，KUI-VIS-062。用户将工具页合并入口、全应用中文/英文与三种主题设为当前任务，覆盖此前语言和主题暂缓的安排。玻璃仍默认关闭，语义预检和系统兼容不扩展。

## 行为

工具页导航收为一个“网络活动”入口，直接显示最近请求、活动连接、DNS 查询和运行日志四个 Tab。各自使用完整列表与独立搜索、关键词、滚动和详情快照；活动连接不再限制前五条。首页相关快捷入口进入同页指定 Tab。DNS 清缓存与清查询历史分开，日志保留清空和导出；点击搜索后展开输入，切 Tab 保留条件且不主动拉起键盘。

语言只提供跟随系统、中文、English；系统中文使用中文，其他系统语言使用英文。主题只提供跟随系统、浅色、深色。偏好先提交成功再发布，不写配置、不触发核心重新应用。主页面和独立更新页共享外观，状态栏和导航栏图标随实际主题改变。

中英资源位于 common 的 `values/app_text.xml` 和 `values-zh/app_text.xml`；`AppTextCatalog` 将原文与稳定资源键对应。只有应用自有文案进入翻译；配置/节点名称、正文、原始核心日志和外部发布说明保持原值。参数只替换一次，参数中的中文、美元符号、反斜杠和花括号保持。领域错误和内部字符串判断保留原文，在显示边界翻译。Android 13以上通过 LocaleManager 同步应用资源和快捷方式，旧系统使用局部配置上下文；未宣称旧API实机覆盖。

## 本轮发现与失败记录

- `compile1`：产品编译通过，旧观测摘要测试仍传已删除回调；替换为四Tab完整列表测试。
- `build1`：测试缺少 `assertTextEquals` 导入；补导入。
- `build2`：Lint 分析期间找不到 Hilt Release 生成文件，改成先构建再独立执行Lint。
- `final1`：Android ICU正则拒绝参数表达式未转义的右花括号，AppText静态初始化崩溃；左右花括号均显式转义。未执行任何用例，不计通过。
- `lint3`：补译成员选择标题时误用 `choice.entry`，实际字段为 `choice.member`，已修正。
- `build4`：新增恢复测试使用了不匹配的 Compose StateRestorationTester 包名，改用项目已有 junit4 版本。
- `final2`：Application.attachBaseContext阶段的applicationContext为空，语言初始化空指针；直接使用已附着的Application上下文，并在读取主偏好前排除隔离语义预检进程。未执行任何用例，不计通过。

最终构建和POCO结果以配套验收JSON为准；本机原始记录保存在 `../acceptance-artifacts/tools-language-theme-poco-20261120/`。本组不发版，不把motion包运行等同正式R8运行，也不把定向页面截图当全部子页像素验收。

## 最终验证

最终同套build7 APK/Test在POCO的final4为10/10（44.256秒），regression2为5/5（14.822秒）；冷进程准备与回读各1/1，PID24772→25008。准备Instrumentation结束后进程已退出，外部确认缺席再启动核验；原驱动pidof退出1保留，未执行force-stop、不宣称杀死存活进程。语言与主题跨Activity重建、冷进程回读及未提交User-Agent草稿保持已验。

App624、Service220、Core39、Common34共917项结果零失败/错误/跳过；App最终重新执行，其余293来自本任务前批执行后复用。motion/Test、未签名Release/R8、MLKit三注册器DEX通过；motion Lint0错误/75警告/15提示，Release0错误/71警告/14提示。当前完整APK携带双语资源，AppBundleLocaleChanges警告不等于AAB拆语言已验；未安装Release、未发版。

最终16张图已目视，含一张原用例有意记录的NTP淡出中间帧；final3的11图中部分是过渡帧，后续final4增加等待及精确页面断言。空配置下常规LAN读取失败提示原样保留，不将其当本组网络验收。2178组资源通过键集合/占位符/英文无中文残留检查，Android实际读取亦通过。

本轮只操作POCO独立motion双包，最终全部清理；设备设置/包清单、原版Flutter身份和原1.yaml哈希/大小/mtime保持。未联网验收、未操作正式配置或启动核心；系统跟随逻辑有JVM覆盖，系统语种/夜间模式没有为测试而更改，旧API及通知/磁贴/Launcher实机显示未单独覆盖。完整逐页视觉与菜单性能仍待。
