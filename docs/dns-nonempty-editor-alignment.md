# DNS 非空字段与编辑面板对齐

KUI-VIS-095，2026-11-23。POCO F2 Pro（`31936bb6`，API 36）集中验收。正式版本保持0.1.18，本组未发版。

## 实现

- 全局 DNS 15 项现在与当前 Flutter 上游 `upstream/main=afe71360` 的 `normalProfileKeys` 一致。补齐 Kotlin 缺少的 `prefer-h3` 以及 `use-hosts`、`use-system-hosts`、`respect-rules`、`cache-algorithm`；布尔 `false` 和缓存算法也经过 JSON、备份、Profile YAML 序列化/回读。缓存算法仅接受 `lru` / `arc`。
- 保留字段默认值与显式空/false 语义。字段选择成功后立即写默认值并留在选择器；保存失败不移除候选、错误和忙态可见。字段删除恢复继承，空列表仍表示显式覆盖为空。
- 非空页按 Flutter 的“选项 / Fake-IP / Nameserver”分组、标题副值、删除触点、枚举菜单和页尾说明整理。`listen` 与列表长度显示在标题下；Nameserver列表有字段化说明。
- Nameserver列表独立成页：逐项新增/编辑与重复校验、长按排序、批选删除、快速文本编辑、修改后确认/重置、脏草稿返回确认。保存等待真实回执；Activity重建不重放提交。外部把字段删除时，nullable基线能识别与用户保存空数组的冲突。
- 监听字段使用共享浮动输入框、IME与失败重试。主题、语言、检测更新按用户明确边界保持Kotlin当前实现，不加入本组Flutter对齐。

## 参考版本边界

固定完整Flutter隔离包为0.9.3/code2026110401、源码`be84736605527034689c95bf27828ff27d912c5e`。旧包全局页仅11项，不能运行验证后来新增的四项；新增四项依据当前`upstream/main`源码的字段列表、默认值与模型校验核对。当前Kotlin15项也只是Flutter完整自定义DNS模型31项中的全局页子集。原版`com.follow.clash`未操作；用于布置非空参考画面的独立包五个临时DNS字段在收尾移除，DNS恢复空态、主题恢复浅色并强制停止。

旧参考运行与当前源码不是同版本；不声称最新Flutter完整运行、逐帧或全像素一致。实测截图能确认分组、字段值、模式菜单和列表表单结构。列表按钮的图标组合、个别行高/文字位置仍有小差异，后续完整视觉任务继续处理。

## 验收

POCO集中回归8/8：浮动监听保存等待/失败重试；显式空数组、字段移除与两主题；列表拖拽、重复项、快速编辑、保存/放弃；默认项逐个重试、开关、枚举、最后候选关面板；字段被外部移除的冲突；列表保存中重建及防重放；DNS空页/选择器；真实MainActivity DNS/规则保存重建及数据恢复。另真实MainActivity自然浅色/深色路线各1/1，包含系统IME、列表草稿Activity重建、提交、Room空库/null选择/STOPPED门禁和最终DNS偏好回读。自然测试JUnit计时分别58.2秒、54.8秒，录像时长另见JSON，设备视频SHA回读一致。

相关JVM130项（ApplicationSettingsBackup3、GlobalDnsSettings5、CoreRuntimeManager122）全通过；Motion APK / AndroidTest APK组装和Motion Lint通过，0错误、11警告、1提示；双语资源2196对校验通过。未运行全仓单测、Release R8或蜂窝/代理实网；性能归因不是本组目标。

原始仪器失败与修正过程留在外部附件，不能把测试驱动定位错误说成产品回归。正式APK保持PID 20700，版本和安装时间未变。原`1.yaml`未读取。Motion/AndroidTest双包和本组设备视频/XML已经精确回收，完整参考包仅保留安装并强制停止。

- [POCO 验收 JSON](acceptance/dns-nonempty-editor-poco-20261123.json)
- 原始截图、XML、录屏和日志：`../acceptance-artifacts/dns-fields-poco-20261123/`
