# KCORE-006：Geo 下载瞬断恢复缺口

2026-11-10。本组关闭 Geo 短暂下载失败恢复、有界失败、旧资源保护和手动恢复的实现与验收；当前 Redmi 新网络下自动及固定出口的原址更新已通过。旧网络中的 TLS 超时、504 具体发生在哪一层仍未确认，不能由本组成功样本倒推历史因果或保证任意网络稳定。

## 来源与问题

固定父仓库基线为 `27d6177bcd99b1c4f6bcfe32b683638b7cdb696e`，核心为 `20bc0fc467cac942bf55009017f87474114fe90c`。`component/updater/update_geo.go` 的 MMDB/ASN/GEOIP/GEOSITE 均只调用一次 `vehicle.Read(context.Background(), oldHash)`；瞬时 TLS 超时、网关错误或正文截断直接成为一次操作的最终失败。该文件与 Flutter be847366 使用的核心024245dc的 Git blob 均为 `229833859f0806c427fb877dc0ea9542b7a92dfb`，因此是共享核心恢复能力缺口，不是 Kotlin 绕过代理。

KCORE-005 已修正 Android 强制 Connection:close 与本现场代理链正文结束处理的不兼容，并回收每次请求的连接池。本项补充传输瞬断恢复，不把新的 TLS/504 一律归因到旧缺陷。旧网络与自动叶节点差异保留在 [原复核](acceptance/geo-network-recurrence-redmi-20261109.json)。

## 修正与不变量

核心修复 [464552e4](https://github.com/lonnnnnng/mihomo/commit/464552e44ddf92affe5c41ee5a5d653a0e1e8daf) 已推自己的 `kclash-fixes`；只参考 `chen08209/Clash.Meta:FlClash`，未合官方 Mihomo 或 Flutter 提交。

- 四类 Geo 统一经 `readGeoResource` 下载，最多3次，基础退避1秒/2秒，尊重 Retry-After。单次仍90秒，下载及等待共享165秒预算，低于 Kotlin 180秒等待；不为每次尝试另开完整总预算。
- 仅重试网络/TLS超时、EOF/截断、连接重置/拒绝/断开和 HTTP408/429/500/502/503/504；403/404、证书错误、无效数据库不重复下载。取消停止后续尝试。
- 每次重新请求原始 URL 并重新走重定向，丢弃失败正文；不拼接、不自动改镜像、不替用户固定节点。格式校验与写入保留在重试外，开始和最终完成事件各一次，整个操作保持忙态。
- 失败逐次记录主机、HTTP状态、字节数、白名单错误与后续重试标志，默认 error 日志等级也可保留现场。最终错误只保留安全类别及尝试次数；Kotlin 剥离次数后缀后仍显示原截断/超时提示，不透传签名URL。
- 下载或校验失败保留旧文件及mtime；成功同内容沿用既有跳过重写语义。没有改变其他 Provider 的下载策略。

## 验收

完整数据、源码/附件哈希见 [验收JSON](acceptance/geo-download-recovery-redmi-20261110.json)，本机原始批次为 `../../acceptance-artifacts/geo-stability-closure-20261109/`。

1. Go父模块全量race及route/http/updater通过；最终updater为8个顶层测试（7个Geo、1个既有核心名称测试），含子项26个通过事件。旧源码overlay在同一公开更新入口仅尝试1次即504失败，红测保留。真实TLS服务端首连接延迟11秒触发生产10秒握手超时，第二次TLS及正文成功；另覆盖真实HTTP504→截断→完整正文、总预算/Retry-After、取消、单终态与旧文件保护。
2. 865项JVM结果零失败/错误/跳过：App583本轮执行，Service213/Core38/Common31复用；Debug、签名Release/R8、motion及测试包构建通过。双Lint零错误（Debug69警告14提示、Release66警告13提示），最终测试源码再补Debug Lint通过。
3. Redmi独立空motion包故障注入5场景通过：504→截断→第三次成功；持续504三次失败；随后手动恢复一次成功；403一次失败；无效数据库一次失败。逐场检查忙态、唯一终态和旧文件，失败mtime保持。使用motion-build2测试包；后续新增实网方法及分片回传，故障方法逻辑未变，不声称全组使用同一测试APK。
4. 同机独立核心按原自动路径→固定80→固定8880→恢复自动路径四轮，四个原GitHub Geo完整正文16/16；每项HTTP200且接收字节=Content-Length=文件大小，SHA跨轮一致，全部请求命中github规则。自动实际叶节点先80、恢复后2086。仅独立暂存输入关闭ETag，原1.yaml未改；后三轮虽同hash跳过重写，确实重新收齐正文。实网均首次成功，未自然触发重试，恢复机制由上述真实TLS回归和手机故障注入证明。
5. 正式签名R8包同签名覆盖安装后，运行VPN/ProxyService，真实“工具→资源→全部更新”连续3轮12项成功，约6.871/9.275/9.340秒；属于缓存校验路径，不冒充正式VPN包16次完整正文。最终资源页已目视，四项原始URL回读一致。

## 安装与清理

正式APK SHA-256为 `033d6c108c732ed4718af05c714a43ab3d80776f5fd69e35e97b364f96a83fb8`，证书仍为 `3d6cf8aab47ed321b7ca4b3c85960c63168d57ca9f35bd03e739c815444c0f49`。手机安装包回读一致，正式数据未清、正式选择未改；原1.yaml SHA保持。最终卸载本轮motion/test，核对哈希后删除新上传的手机副本 `/sdcard/Download/1.yaml`，工作区原件保留。正式VPN/ProxyService保持运行，公开0.1.0资产不覆盖。

首批故障测试因正式VPN仍运行而遇独立核心DNS5335占用和回环超时，停止正式服务后通过；该环境冲突不记产品根因。首批实网准备误把changeProxy返回false当作失败，尚未发Geo请求，改为回读实际选择后通过。所有失败批次独立保留，未用最终通过抹掉失败。

## 后续同步复查

只从 `chen08209/Clash.Meta:FlClash` 比较后续实现。撤销补丁前必须核对重试白名单、总预算、Retry-After/取消、每次重新走原URL、单终态/旧文件保护及安全日志，并重跑公开Geo入口、真实TLS/截断和Android原址下载。长期切网、蜂窝/OEM、历史元数据/偏好/Provider超时仍按原任务单列，不能因本项闭环而改为通过。
