# KUI-013：Provider 正文冷恢复后父面板停留读取失败

## 来源与影响

这是 Kotlin 冷恢复集成缺陷，未修改 Flutter 或 Mihomo。冷进程自动恢复 MRS 只读正文后，正文内容、会话与滚动正确，返回原 Provider 面板却停在读取失败；配置统计同时失败。返回层级没有丢失，不能用重新打开详情或测试主动重试掩盖问题。

Flutter 参考为 `lib/views/proxies/providers.dart` 的正文路由、`_handleSave` 与 Navigator 返回父列表行为；它不包含本项目新增的独立加密正文冷恢复协议，因此修复归属 Kotlin 宿主。后续参考 Flutter 增量时保留此恢复契约，不合并 Flutter 提交。

## 根因与修复

`ProfileStatisticsViewModel.resumeProviderDocument` 先发布父配置 ID，再调用核心恢复绑定。父面板轮询可先取得核心锁：`CoreRuntimeManager.profileProviders/providerSnapshot` 与 `profileStatistics` 只读已加载配置，尚未加载时失败；正文恢复随后通过 `recoverProfileProviderDocument/ensureConfigLoaded` 成功。

`pollProfile` 为避免持续加载与覆盖错误，首次失败后忽略普通版本通知并停轮询。正文恢复成功原先只清恢复路由，没有刷新父列表，所以父面板保留失败；`closeProvider` 仅关闭正文也不会修复它。

成功绑定且仍为同一父配置时，显式递增 Providers/统计的刷新代次。普通读取失败仍保留错误，恢复失败、目标切换或过期结果不会触发该刷新；不自动重交保存，不把父列表读取改成主动加载核心。

## 复现与验收状态

附件目录：`../acceptance-artifacts/provider-runtime-20261107/`。

- `mrs-final/mrs-preview-again.log`：原20秒返回超时，未含状态字段，保留。
- `mrs-diagnostic2/mrs-preview-again.log`：同样超时，诊断为父配置匹配、detailNodes=1、RESUMED、正文已关闭，Providers count=null/hasError=true、统计error=true。不是父路由丢失或生命周期暂停。
- `parent-recovery-red/`：新增确定性单测先让父列表读取未就绪核心，再放行正文绑定，旧实现恰在父列表非空断言失败。
- `parent-recovery-green/`：11项正文恢复单测通过，含成功补偿、失败不补偿、后续普通错误保持。
- `mrs-fixed-final/` 与随后仅增强Debug诊断的 `mrs-final7/`：各自首次/再次恢复通过，返回及重开均读到91项Providers且无统计错误，正式快照和设备基线保持；不同包的结果不累计。
- Provider运行态及失效引用实核回滚仍以本组最终JSON为准，不能从MRS结果推广。

前两场MRS失败的清理仅允许已核对本轮UUID、临时ID、源SHA与缓存签名的自有对象；正式配置、正文与偏好不回写旧快照。测试驱动问题（手动保存Bundle后继续操作Compose、进程探针累计、RawConfig校验误判）与本缺陷分开保留。

## 后续复查

修改正文恢复、父详情读取或轮询失败策略时，必须保留：成功冷绑定后返回同一就绪面板、恢复失败不重试、后续普通错误不被版本通知抹掉、关闭后不复活密文、保存不重放。VPN/OEM自然回收和全部Provider类型不由此项证明。

## 最终验收

Provider本组已验：MRS只读正文首次/再次冷恢复及原面板返回通过；纯代理保存前后SIGKILL经外部独立观察系统新PID/前台服务，恢复不自动重放、显式重试仅一次；停止态/运行态各源文件或源记录缺失4类真实setup拒绝后回滚旧输入/选择/会话并成功切往有效目标，epoch各增加2。KUI-013父面板冷恢复后错误常驻已红绿复现并修复，KUI-010补齐实核回滚证据。Redmi final7运行态/回滚6/6、12次同socket核心归因HTTP200，MRS 1+1、相邻回归10/10，22张PNG完整校验及8拼图目视。build7 App549实际执行、Service212/Core38/Common31复用，共830项JVM零失败/错误/跳过；389任务28执行/361复用，Debug/Test/未签名Release-R8及双Lint通过，Debug0错误68警告13提示、Release0错误66警告12提示。安装回拉及579份Android源码匹配；正式两配置/正文SHA-size-mtime/偏好/脚本/选择、原1.yaml/Flutter包/设备基线保持，临时对象/密文/标记清除，无服务或tun0。MRS为真实MainActivity STOPPED，首次存活PID受控force-stop、再次恢复前进程已退出，运行态为Debug宿主真实VM/Room/核心的纯代理SIGKILL，非VPN/OEM自然回收或完整点击导航。final6-before的verify超时原因未定，后续通过不抹除；第6项原保护异常、未知偏好变化与网络稳定性仍待查。选项错误截图滚动后提示部分裁切、筛选与保存图含转场中间帧，下一组补键盘错误可见性、稳定终帧及剩余子页/面板，再集中Flutter排版/连续动效；独立全局规则页/资源选择器冷进程另列。效果/性能/兼容/发布按原顺序，蜂窝延期、玻璃默认关闭。证据`docs/acceptance/provider-runtime-redmi-20261107.json`；溯源`docs/provider-cold-parent-refresh-defect.md`；附件`../acceptance-artifacts/provider-runtime-20261107/`。
