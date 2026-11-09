# KUI-026：配置子页排版与输入恢复

2026-11-09，接续 `d069aadd`，按未完成清单核对 Provider 选项、配置资源选择、NTP/Sniffer。Flutter `be847366`、Prism `63450c0` 仅作参考，不合入其提交。

## 来源与差异

- Flutter `lib/features/form/form_row.dart` 在leading之后计算标题最多占剩余空间的一半；Kotlin `ProfileFormRow` 未扣除删除按钮48dp触点，长标题挤压右侧输入。本组仍保留Android48dp触点，在剩余区域分配标题和值。
- Flutter `lib/views/profiles/custom/providers.dart:420` 的开关、`:552` 的IP版本支持整行点击，文本输入有“可选”提示；Kotlin只有右侧控件响应且空值无提示。选项添加使用 `SelectionSheet.removeOnSelect`，最后一项选择后退出；原Kotlin停留空页。
- Flutter `selection_sheet.dart` 使用分组选择行并分开搜索无结果/目录为空；Kotlin配置资源选择把查询不匹配显示为“暂无可选配置”，整行使用蓝色TextButton，也没有当前选择标记。
- Prism `null_status.dart:206` 的空态沿剩余视口偏上居中（Alignment 0,-0.2）；Kotlin更多设置使用固定80dp顶部留白。分组标题还采用15/20而非headline17/22，卡片采用普通圆角而非项目连续圆角。
- Kotlin `MoreValueEditor` / `MoreYamlEditor` 在子Dialog外取得焦点/键盘控制器，与前期Provider页KUI-014相同：确认时操作的是父窗口IME，校验失败后键盘可能遮挡错误。字段和YAML确认改从子窗口取控制器并在校验前收输入，失败保留草稿。系统返回的草稿确认边界不变。

## 实现范围

修正表单剩余宽度、Provider整行操作/可选提示/最后一项返回，收紧选项添加页双重内边距；IP页采用分组56dp行和已选标记。资源选择采用可滚动分组行、单行省略名称、当前选择标记，单行搜索与可清除的无结果空态。NTP/Sniffer空态按视口定位并居中文字，标题与连续圆角收敛，字段/YAML确认处理各自的IME。

NTP/Sniffer插图仍为现有系统图标，添加面板、协议目标选择、资源搜索栏尚未完整复刻Flutter形态；没有因此宣称整页视觉全部完成。业务持久化/草稿/运行态应用流程沿用原实现。

## 验证

新增真实标签触点、独立删除、最后一项添加返回；长标签/删除触点/右侧编辑几何；同一Dialog的真实IME显示→确认→隐藏且错误/草稿保持。既有Provider错误/长输入/忙态/引用别名、更多设置逐项保存/协议/失败重试及资源选择深浅图一并集中验收。

资源页旧测试锁死早期授权1.yaml的34814字节及旧SHA，现用户原文件为35165字节；读取助手改为验收传入原文件清单，设备字节/哈希必须一致，不使用自行生成配置。仅使用本轮上传的原1.yaml副本，收尾按哈希删除自有设备副本，工作区原文件不改。

本机参考导出、构建和真机证据：`../acceptance-artifacts/config-subpages-parity-20261109/`。只验专用Redmi API36、竖屏默认字号，玻璃默认关闭；整体视觉、菜单性能、实网/跨进程其余边界保持待办。

## 键盘缺陷红绿对照

同一新增用例在前组KUI-025的独立应用APK（SHA `7b30bbe81c2c24b3ad0b9375c63debe8c1a51b3bbf851431e77148c8f5f6dcf9`）执行，字段校验后的`awaitHiddenIme`在5秒门限失败，保留`legacy-ime/instrument.log`。最终源码同测试先行4/4通过，实际检查同一窗口token下IME从显示变为隐藏且错误/正文保持；旧包测试后已恢复最终fixture并回拉核对。本组不将旧包失败计入最终绿色总数，也不依赖修改系统键盘设置。

## 最终交付

最终860项JVM结果无失败/错误/跳过，App578本轮执行，Service213/Core38/Common31复用；Debug、签名Release/R8、独立motion/test及双Lint通过（Debug0错误68警告14提示、Release0错误66警告13提示）。同一fixture分4+18个不重复用例22/22，68张PNG/10拼图目视，覆盖本地代理及策略组相邻编辑。官方源码/本轮Kotlin源码哈希随证据保存。

正式APK SHA `a68d118253282d4e4641ff1d36618322a36bccb78bb54dbc5671ee4bcdc7ce18`、签名与安装回拉一致；原配置详情/Provider缓存、底部配置Tab可读，初次同名点击路径和第二次定位门禁拒绝已更正记录。详细子页验收为独立组件与受控回调，不冒充正式Room/核心运行态；无本轮性能结论。正式配置未清，原1.yaml不改，测试双包和本轮上传副本/XML按SHA精确清理，服务停止、深色/默认字号。公开0.1.0未覆盖，手机保留本地修正版。证据`docs/acceptance/config-subpages-parity-redmi-20261109.json`。
