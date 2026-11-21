# KUI-VIS-069 资源输入浮框与连续操作

使用 POCO F2 Pro（API 36、竖屏、默认字号）验收“工具→资源”的更新周期和 Geo 地址输入。沿用 068 的 Prism 浮框与 404ms 进退场，替换页面中旧的 Material `AlertDialog` 输入外观；未改资源业务存储、校验范围或 Flutter 代码。

周期输入保留数字键盘和“小时”尾缀，仍由 `GeoResourceSettings.validated()` 拒绝 1～87600 之外的值。Geo 地址保留 URI 键盘、HTTP/HTTPS 与凭据/片段校验；值偏离默认 URL 时，左侧动作改为“恢复默认”，与上游 `InputDialog.resetValue` 一致。恢复默认直接提交并退场；取消、系统返回及点击遮罩只放弃草稿。

成功提交只调用一次原有 `onSettings`。窗口在输入时保持本地 `TextFieldValue` 及选择范围；保存/重置后冻结退场外观，由 `onExitComplete` 收尾页面编辑状态。重新打开时从已保存设置重新取值。重新进入资源页前后的控件树保持同一 ProfileEditorPage 主机，不新建菜单页或绕过 ResourcesViewModel/CoreRuntimeManager。

## 验收

- POCO F2 Pro `31936bb6` / Android API 36：真实 MainActivity 资源页流程 1/1；加入已有双主题资源页回归后同批 2/2。
- 验证周期越界错误、周期有效 IME 提交/重开、取消不保存、无效 Geo scheme 错误、地址 IME 提交/重开、恢复默认提交/重开、系统返回取消，以及返回工具页。
- 最终测试在设置输入前确认真实 IME 可见，并在收起后继续；结束时断言 Geo 设置逐值等于测试前快照、核心保持 STOPPED。
- 回收 16 张 1080×2400 PNG；最终分片索引、字节数、SHA-256 和 PNG 头均核验，并目视检查 8 张目标状态与 8 张双主题相邻回归。
- `app:assembleMotionBenchmark`、`assembleMotionBenchmarkAndroidTest` 和 `testMotionBenchmarkUnitTest` 通过；App 628 项本批执行结果 0 失败/错误/跳过，Service 220、Core 39、Common 34 为既有成功 XML/Gradle up-to-date 复用结果。Debug 与 motion Lint 各 0 错误、75 warnings、15 hints。
- 两个本轮独立包安装回拉 SHA 一致并在本机清理。最终手机仅保留原有 `com.follow.clash`；未启动、修改或替换该上游应用。方向、字号和夜间模式不变；原 `1.yaml` 的 SHA/大小/mtime 与前组记录相同。本组仅读取文件字节计算 SHA-256，未解析正文、用于测试或上传。

上游只进行固定 Flutter 源码阅读，没有启动 Flutter 参考应用进行同态截图；因此此组结论限于控件形态、键盘、错误/重置合同与真实 Kotlin 导航链路，不称完整 Flutter 页面或逐帧像素等价。其他输入面板与页面排版、完整页面连续转场仍 PARTIAL；菜单首开/关闭慢帧和 RenderThread/合成等待也未闭环。玻璃默认关闭，本组未测试玻璃。

证据：[KUI-VIS-069 验收 JSON](acceptance/resource-input-motion-poco-20261121.json)，原始记录位于本机 `../acceptance-artifacts/resource-input-motion-poco-20261121/`。本组未发版；当前公开版本仍为 0.1.11。
