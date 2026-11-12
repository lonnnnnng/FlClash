# KUI-040：大正文加载越界及撤销历史丢失

2026-11-12，基线 `1ed65de9`。属于 Kotlin 正文编辑器缺陷，不是 Mihomo、订阅节点或手机网络问题。

## 来源与修复前证据

- `ProfileDocumentEditor.change` 的整串历史实现来自 `eaa6e4b5`：加入历史后按 200 万字符预算删除；正文超过该预算时，新加入的唯一历史立即删除，首次编辑无法撤销。此项首先由源码确认。
- `ProfileCodeField` 将全文交给一个 `BasicTextField`，外层滚动并使用 `IntrinsicSize.Min` 测量完整高度。Redmi 独立非 Debug 包，以授权 `1.yaml` 为前缀、仅在内存追加注释到精确 8,388,608 UTF-8 字节，加载即失败：`Can't represent a width of 0 and height of 14420429 in Constraints`。未走到撤销步骤，不能称旧包已实测撤销。
- 原始失败：工作区外 `acceptance-artifacts/editor-external-20261112/baseline-redmi.log`；首编译漏 import 单独保留，不计产品运行失败。

## Flutter 参考与修正方向

只读参考 `upstream/main` 的 `lib/pages/editor.dart` 与 `plugins/code_forge/lib/code_forge/controller/document.dart`：上游使用 Rope，并将长正文读取/编码移出 UI isolate。禁止合并 Flutter 提交；Kotlin 保留现有 ViewModel/Repository/密文草稿合约。

Kotlin 大正文限制单次排版范围，明确显示当前正文区间、提供相邻区间导航，全文查找仍定位到对应区间；修改只拼接当前区间，不能截断其余正文。撤销改为有界差异记录，普通按键不再保留整份大正文。匹配计数离开主线程。独立只读复核还发现中间候选的跨段选区被显示层截短：直接输入替换可能残留命中尾部。最终修正普通命中的窗口定位，并在超长选区输入时映射完整全文范围；JVM覆盖反向选区和IME组合区，Redmi增加普通/超长选区及首末/相邻段导航回归。最终真机、保存/恢复及边界见后续验收记录，不以源码修正代替运行结论。

后续同步编辑器时保留 8 MiB 上限、Unicode/CRLF、区间拼接、全文查找与差异撤销回归。分段编辑不能冒充 Flutter Rope 的连续滚动或逐帧一致性。

## 验收口径

最终正文 UI 共6项通过，含原有4项、8MiB加载/查找/编辑/撤销/重做/退出/段导航，以及普通跨段和超长反向选区输入。`editor-final3.log` 5/6的失败来自Compose测试语义动作使用反向start/end调用`replaceRange`，改走输入法提交命令后最终6/6；失败保留，不修改产品逻辑适配测试动作。

真实Room、ViewModel、Keystore草稿在两轮instrumentation的不同PID间回读，子页核心语义预检和外层正式保存均检查完整8MiB字节及尾部修改。前一runner结束后PID已退出，外部force-stop仅作清理门禁，不称为保存窗口内杀死活进程；也不覆盖自然内存回收、断电或其他OEM。

8MiB上限编辑和保存功能可用，不等于整体性能收口：最终UI检查点加载约269ms、查找约1216ms、编辑约856ms、撤销约708ms，含Compose同步/动作和菜单等待；不是帧率指标。草稿准备/恢复提交整阶段仍耗时数十秒，留作下一轮性能优化；Java检查点用量也不当作进程峰值内存。

构建、安装摘要、原始失败和最终记录见 [本组证据](acceptance/editor-external-redmi-20261112.json)。本地原1.yaml和USB原文副本SHA保持，任何机密正文及网络原始日志均不提交。
