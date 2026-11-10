# KUI-038：按应用代理名单保护

2026-11-11，基线`2153854b`；固定Flutter`be84736605527034689c95bf27828ff27d912c5e`仅作行为参考。本组属于[功能性剩余项收尾](functional-closure.md)第一项，其余五项仍待完成。

## 缺陷与来源

| 当前源码问题 | 用户影响 | 修正 |
| --- | --- | --- |
| 智能选择以完整apps重建两个名单 | 隐藏的系统/无网络应用及未安装项可能丢失 | 与Flutter `lib/views/access.dart:_intelligentSelected`一致，只替换系统/联网过滤范围；搜索不参与，范围外项保留 |
| 剪贴板只按换行拆分，任意非null文本替换 | 空白或无效文本可覆盖原选择 | trim、合法包名、首次去重；零有效项原位报错且不写草稿/Room |
| 计数仅统计visibleApps | 搜索或过滤改变已选总数 | 完整已安装集合计数，不计未安装项；全选仍只针对可见集合 |
| 仅查询Launcher且VM初始化时加载一次 | 无桌面入口包无法选择，返回页面时清单陈旧 | GET_PERMISSIONS全包查询，QUERY_ALL_PACKAGES可见性；进入/恢复页面刷新，不覆盖草稿 |
| 读取设置失败返回DEFAULT，Gson损坏字段可变空名单 | 原名单可能被默认值覆盖 | 仅没有存储记录才用默认；读失败/损坏字段拒绝就绪和保存 |
| 扫描和保存认领/启停保护不完整 | 迟到扫描或重复保存覆盖新会话 | 同步认领busy、草稿revision、核心现有ipRouteRevision；保存沿核心transitionLock复核停止态，取消继续传播 |

这些是Kotlin应用层问题；不修改Mihomo gitlink，不向内核移植补丁。Flutter剪贴板依据`^[a-zA-Z]\w*(\.[a-zA-Z]\w*)+$`，允许未安装合法包，拒绝单段android及非法段。取消/离开后的旧扫描不得重写草稿。来源过滤与搜索范围分开，不能用搜索结果裁剪智能选择。

全包可见性官方来源为`https://developer.android.com/training/package-visibility/declaring`，2026-11-11经smart-search读取；正文说明API30+默认过滤PackageManager结果，QUERY_ALL_PACKAGES使无指定组件的安装包可见。本机证据`acceptance-artifacts/access-package-visibility-20261111.md`。OEM的GET_INSTALLED_APPS仅在设备声明且拒绝时显示授权入口；保留旧列表/名单，设置返回重新查询。无该OEM权限的Redmi不冒充已验所有OEM授权。

首轮Lint因`QueryAllPackagesPermission`审查项失败；VPN名单确需枚举无Launcher应用，已在单个权限声明旁写明用途并加`tools:ignore`。这不是运行时权限绕过，不关闭全局Lint；其他OEM授权仍按平台实际结果处理。真机13项运行后的唯一产品源变化是该Lint元数据，最终构建/静态门禁单独记录。

## 验证记录

首轮定向测试旧剪贴板断言保留空行而失败1/6，编译通过；更新为固定参考规则后12/12通过。随后补标准调度器合并运行窗口、等待核心锁取消两项及真实SQLite损坏字段用例。最终build4共889项JVM（App604/Service216/Core38/Common31，含Gradle复用）零失败/错误/跳过，Debug及未签名Release-R8通过；motion1/Test2回拉SHA与安装包一致。完整结果见[验收JSON](acceptance/access-protection-redmi-20261111.json)。

Redmi final1单批13/13：真实主宿主3项、SQLite3项、组件5项、旧主页面回归2项。无Launcher联网候选实际为Android Auto，通过真实PackageManager差集确定；独立Room回读及Activity重建保持、重复保存回调仅1次。有效/无效名单导入、系统/无网络/未安装项保护、搜索无结果仍智能选择、计数不随过滤变化、真实暂停恢复及各fixture原设置精确恢复通过。Activity重建不称为新VM冷进程；已有TUN分流证据不扩大。

四张PNG已核对：两张稳定的非Launcher选择/保存重建；无效剪贴板图处于系统恢复转场、智能选择图有剪贴板系统浮层，这两张只作过程记录，不计稳定视觉等价。设置及原文件收尾核对另记JSON，正式包本组缺席，不声称正式库保护。

真实设备仅Redmi Note 8 Pro `wsvwypiz7xwslvl7`，竖屏默认字号；使用独立motion空库，原版Flutter和工作区1.yaml不改。无Launcher应用必须由真实PackageManager差集选出，缺少输入应失败，不能空跳过。按应用代理的实际TUN分流沿用既有证据，本组新增全包枚举、真实VM/Room/剪贴板及并发边界，不把组件运行态锁测试称为新实网分流验收。

最终lint2：Debug {'Warning': 73, 'Hint': 14}，Release {'Warning': 69, 'Hint': 13}，均零错误；Release扫码DEX3/3。原lint1失败保留。收尾0配置/核心STOPPED，原1.yaml与设备设置保持；独立双包已核验删除，参考包保留。
