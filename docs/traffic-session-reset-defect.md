# KUI-034：停止核心后累计流量没有结束当前会话

2026-11-10，作者 long。基线 `2486f0f5fc790b13d5710e8d68491d23f7a3911a`。这是 Kotlin 接入层漏调已有核心接口；未修改 Mihomo、Go 适配层或内核 gitlink。

## 来源与根因

固定 Flutter `be84736605527034689c95bf27828ff27d912c5e` 的 `lib/providers/actions/setup.dart:227` 在完整停止后调用 `resetCoreTraffic()`，同时清速度历史与总量；第327行转发 `_core.resetTraffic()`。按需排除 Wi-Fi 的暂停只收起监听，保留当前会话统计。

本仓库 `core/connection.go` 已有 `handleResetTraffic()`，`core/method.go` 的 `resetTraffic` handler 等清零完成后回 `true`，Mihomo `tunnel/statistic/manager.go:123` 清全部/仅代理两套临时值、速度采样和累计值。Kotlin `CoreBridge` 和 `CoreRuntimeBridge` 未暴露它，`CoreRuntimeManager.stopLocked()` 也未调用。首页非运行态显示零仅遮住旧计数；同一进程重启后再次读出旧总量。

## 修复与同步边界

- `CoreBridge.resetTraffic()` 等待严格的成功回执，`NativeCoreRuntimeBridge` 转发。
- 完整停止先等待平台服务退出、停止监听，再清核心统计，成功后才发布 `STOPPED`。清零失败保留 `FAILED` 与待清标记；下一次启动在打开服务/监听前补清，失败时不能开始带旧计数的新会话。
- 不把清零放进底层 `stopListener()`，避免按需暂停/恢复和运行中配置应用误结束当前统计。重复停止保持幂等。

后续同步只参考 `chen08209/Clash.Meta:FlClash` 及 Flutter 行为；不合并 Flutter 提交。若上游接口回执或生命周期契约变化，需保留“完整停止清零、暂停保持”的回归，再调整桥接。当前修复不宣称覆盖系统撤销 VPN、OEM 回收和所有异常退出路径。

## 红绿与正式包

新增两条状态机单测覆盖清零、暂停/应用配置保留、重复停止、清零失败后阻止启动并重试；旧实现 2/2 失败，修复后 2/2 通过。既有 ViewModel 快速启停用例补总量断言。

Redmi Note 8 Pro `wsvwypiz7xwslvl7`、API36、竖屏默认字号：在独立非 debuggable `.motion` 空库使用原 `1.yaml`，经过真实 mixed/SOCKS 监听向回环端点上传64KiB/下载128KiB，再启动第二会话上传4KiB/下载8KiB。同一个最终测试 APK：旧产品包 `device-red4.log` 在 `STOPPED` 后仍读到上传304598 B、下载134166 B；修复包 `device-green1.log` 1/1通过，10阶段均PID17405，普通停止、暂停中停止、重复停止的全部/代理累计均0，速度采样归零；第二轮新计数独立累计。原正文保持且独立配置已清理。

初次用例的“首次启动绝对为零”“全部连接数为零”“暂停恢复数值完全不变”前提均被原配置后台流量打破，三次失败日志独立保留。最终首次启动记录基线，载荷按增量验证，暂停验证不减少；停止仍严格验零。重启可能立即产生新健康检查流量，不要求运行期间一直为零。全部/代理口径是顺序读取，不是原子快照，不把代理口径上行当作特定远程请求成功。

最终871项JVM（App587/Service215/Core38/Common31）零失败/错误/跳过；Debug/Test、签名Release-R8、motion/Test和双Lint通过，DEX扫码注册器3/3保留。`build-final`执行App/Service单测，Core/Common复用；最终`build-final2`四模块单测复用，390任务中11执行/379复用。Debug Lint0错误/71警告/14提示，Release0错误/68警告/13提示。

正式签名候选0.1.1/code1001 SHA `0ab4cb72359e8c8d0742d2581ffa84b309f4ece4569f0ba23387cab56879974f` 已覆盖安装且回拉一致，公开Release未替换。首页真实启动显示上传107.5KB/下载155.0KB且双服务运行；点击停止后两项累计0B、双服务退出。安装前后UID、schema6、四张业务表和原配置正文SHA/大小/mtime保持。烟测后配置/脚本保持；两偏好中一项value/时间改变，238测速复合键保持、68值和238时间更新，运行投影SHA保持而mtime更新；这些历史异常未因此关闭。

本轮3个独立测试/探针包和7个核对SHA的设备临时文件已清理，原工作区`1.yaml`保持，正式包与隔离Flutter参考包保留，正式服务停止。原始附件在工作区外层 `acceptance-artifacts/traffic-session-reset-20261110/`；结构化结果见[验收证据](acceptance/traffic-session-reset-redmi-20261110.json)。同进程两会话实核回归属于motion，正式R8包为首页启停烟测，不混写范围。
