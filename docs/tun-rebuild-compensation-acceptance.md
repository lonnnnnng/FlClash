# TUN 重建异常补偿与验收夹具溯源

2026-11-17，基线 `053bfab3`，POCO F2 Pro（`31936bb6`，API36）。本组先统一 roadmap 当前快照，再用真实 `CoreRuntimeManager`、Android VPN 服务和 Go 核心补验异常分支。最终同套 motion/Test 单批 **5/5、零失败/跳过**；故障前后独立 UID 默认 VPN 的 DNS、TLS、HTTP200 和非 DIRECT 双向核心流量 **10/10**。本轮未发版，公开版本仍为0.1.5。

## 五项实际覆盖

| 场景 | 真实失败及补偿 | 最终结果 |
| --- | --- | --- |
| 系统建接口单次失败 | Builder完整配置后，验收副本MTU改为65536；真实Binder/system_server抛出IllegalStateException，候选失败后自动重建旧参数 | 757ms返回“已恢复原配置”，RUNNING、原会话起点保持，新VPN Network；前后代理联网通过 |
| 系统建接口与回滚连续失败 | 两次真实establish异常 | 763ms返回“核心已停止”，FAILED、恢复意图/起点清空，无服务/TUN；解除故障后显式重试联网通过 |
| 核心拒绝真实TUN FD | 系统先返回真实FD，再用mips栈与非法拥塞参数触发真实Go拒绝；不伪造false | 拒绝后无TUN FD残留，第二次正常接收，779ms自动恢复；前后代理联网通过 |
| 核心连续拒绝候选与回滚FD | 两次真实FD交付均被拒绝 | 两次拒绝后均无TUN FD残留，767ms安全停止；显式重试联网通过 |
| 普通Profile切换、全局网络覆盖 | 原配置的独占内存投影仅改mixed-port/MTU/stack，选择候选再切回；全局设置最终覆盖这些字段 | 864ms、核心epoch增长、候选确实被选用；TUN边界访问0次、Network不变，不误称发生重建 |

耗时是操作至补偿结果返回，不是HTTP恢复耗时。候选网络参数从生产 `updateNetworkSettings(old, candidate)` 进入 `applyProfileLocked`，与配置应用共用补偿路径。普通Profile字段会被全局General/Network设置覆盖，不能仅修改源MTU就声称验证了重建。

每例检查配置整行、当前选择、正文SHA/大小/mtime、未提交草稿密文、网络偏好和已应用配置字节。单次失败保持旧运行起点；双次失败不自动无限重试，显式重试建立新起点。结束逐步恢复本例设置、节点选择，删除独占候选和草稿；恢复错误保留为失败，不能被finally吞掉。

## 实现与上游边界

`VpnService.handleStart` 增加 `TunStartBoundary` 调用边界。debug实现只能在独立motion包、显式参数下布置最多两次故障；没有计划时透传。release同名实现只有透传，不带故障参数或FD诊断。核心接收FD后仍由Go消费原FD/副本；验收只读 `/proc/self/fd`，没有二次adopt/close，避免编号复用误关。

本地Flutter参考 `VpnService`、`core/tun/tun.go` 与当前Kotlin补偿实现已对照：本轮没有确认产品补偿缺陷，未改管理器状态机、Go或Mihomo子模块。仅增加可验证边界及测试；Flutter提交不合入，内核仍固定 `d43bbd751fe5eb95a599a6211423f645269ba57d`。后续同步上游时保留release透传和FD所有权，不将debug故障参数迁入产品。MTU与拥塞拒绝是**受控参数触发真实原生失败**，不代表自然设备故障或任意ioctl/内存耗尽分支均已复现。

## KDIAG-005：只切运行态节点导致重启后DIRECT

第一批原RULE路径五例均在故障注入前TLS失败，未进入补偿验证。随后使用既有endpoint诊断扫描原配置节点：index0、index1的SOCKS HTTPS失败，index2成功。旧驱动名 `start-tun-node-*` 实际进入通用start分支、关闭VPN并发SOCKS请求；只将它作为纯代理节点对照，不作TUN证据。

首次固定index2跑TUN时，五例HTTP200但核心链均为 `[DIRECT, GLOBAL]`，严格非DIRECT门禁正确拒绝。原因是旧endpoint-lock仅调用 `runtime.changeProxy`；正式UI还会调用 `ProfileRepository.updateProfileProxySelection` 持久化。新启动和回滚从Profile的selectedMap加载，因此上一进程临时选中的节点丢失。此问题属于本项目验收夹具，**不是产品节点保存或TUN补偿缺陷**，无需向Flutter/Mihomo迁移修复。

修正仅在新TUN测试内：从加密验收输入读取已核验的原节点index/name，按正式UI合同保存GLOBAL选择；原行先备份，故障窗口内快照不变，结束精确还原selectedMapJson并核对其他字段未变。故障前后均只读确认核心now，恢复后不补切节点。保留同源端口、目标host、独立UID、默认VPN、双向正流量和非DIRECT全部门禁。

四批结果独立保留：`five.log` 0/5（注入前TLS失败）；`native-only.log` 5/5（明确跳过实网）；`live-node2.log` 0/5（DIRECT拒绝）；`live-persisted-node2.log` 最终5/5、10笔实网。不能合计成10项最终用例，也不能用成功覆盖前三批。

## 网络新增观察

11月12日POCO冷启动5/5未复现仍是有效的历史结果；11月17日当前网络下原RULE首请求再次出现TLS握手失败，DNS与核心拨号成功后有WS读错误。两节点失败、第三节点成功只说明当前路径差异，不足以单因果归属节点、网络、系统或软件。当时功能清单记录“TLS有新失败待查”（11月18日按用户决定归档），不继续写成当前全局未复现；本轮固定节点补偿通过不关闭自动选路或所有节点稳定性。

最终通过条件是Wi-Fi、GLOBAL、原index2 VMess/WS节点、直接使用原定义中的IP（节点与地址方式核对见原始 endpoint-select-2 与 endpoint-apply-node-2-0 回执）、唯一授权HTTPS目标。其他协议/来源、在线age、低API/OEM和自然原生故障仍有覆盖边界；本组不扩大到这些环境。

## 构建与收尾

最终 `build4.log`：根Wrapper执行test、motion/Test、未签名Release-R8、motion/release双Lint，7分钟成功，403任务120执行/283复用。911 JVM（618/220/39/34）零失败/错误/跳过；motion Lint0错误/74警告/14提示，release0错误/70警告/13提示。本轮未修改Go源码，未运行Go测试；Gradle重建核心制品不等于Go测试通过。Release全部DEX检查不含故障标识，Release未安装、未发布；motion双APK回拉SHA一致。

完成endpoint设置/选择/暂存配置恢复，备份清空，行和正文指纹回到准备前；探针输入核SHA后删除，准备输入确认不存在；三个自有包精确卸载，结束无自有服务/TUN。清理脚本曾在首包卸载后将pm path缺席退出码1误判异常，后续核实缺席并完成剩余清理，未重复删除未知路径。原1.yaml SHA/34220字节/mtime保持、未上传，Flutter参考包未操作。

脱敏证据：[tun-rebuild-poco-20261117.json](acceptance/tun-rebuild-poco-20261117.json)。原始批次、构建、安装、641份Kotlin源码摘要与清理记录在仓库外 `../acceptance-artifacts/tun-rebuild-poco-20261117/`；不提交原正文、密钥或原始附件。
