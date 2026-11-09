# 菜单关闭留窗观测与原生采样限制

2026-11-09，基线00c353c9，本轮没有菜单性能优化补丁。

## 补齐关闭帧可见性

原测量在Popup销毁后读取gfxinfo，窗口消失导致无法报告关闭帧。Debug/独立motion入口新增MenuCloseCaptureProbe：只在原500ms回源动画完成后暂留窗口，完成后100ms再读取帧表；读取后释放。正式Release同名实现为空，不提供arm接口。动画期间没有截图、录屏、节点查询或dumpsys。它改变诊断窗口寿命，不能当正式Release性能数据。

closing1三轮Popup普通帧覆盖100%，分别16/27/27帧，jank3/2/2；宿主均0帧。renderElapsed中位2.683/3.858/3.799ms，最大166.134/32.109/22.787ms。首轮第三样本UI区间55.169ms、DrawStart→SyncQueued47.868ms、IssueDraw→Swap162.854ms、完成275.862ms；后两轮第三样本DrawStart→SyncQueued49.380/48.090ms，完成100.168/102.467ms，仍超截止。关闭慢帧不是只存在于首轮。

Vsync→SyncQueued、SyncStart→SwapBuffers、IntendedVsync→FrameCompleted均为经过时间，包含等待，不能称CPU/GPU时间。后段SwapBuffersCompleted→FrameCompleted约10–12ms并未超截止，不能单独判jank；DisplayPresentTime含失效值，不作实际呈现延迟。完整原始表、解析报告、dispatch/animationCompleted纳秒见本机附件。

## 原生采样未成功

系统/system/bin/simpleperf具有原始可执行标签，直接由目标UID启动，不复制二进制、不改安全属性。两次record均退出1、recorded=false，错误no process with pid。第二次从Activity回读PID并验证targetPid=collectorPid=3959及目标cmdline为motion，仍失败；因此不能归因“选错PID”。当前ROM的进程可见性/采样路径仍限制原生录制，未获调用栈。两次Instrumentation通过只代表失败报告与现场清理完成。

perf属性前后完全一致；没有root、setprop、SELinux或全局采样配置变更。旧Perfetto flush失败继续保留，缺原生栈时不再重复长采样或猜测某个RenderEffect唯一负责。下轮优先可用native采样环境与相同关闭窗口时间轴，现有经过时间只帮助缩小采集范围。

## 2026-11-10：重启后隔离数据源

现场`traced_probes`主线程处于D（不可中断等待），内核stack/syscall读取被系统拒绝。重启后恢复S；第一轮包含ftrace/process_stats的3秒探针再次使其进入D，839字节trace、sched_slice=0、两项flush失败。该轮系统在开机时拉起了KClash后台进程，不能当作无应用对照；误选`/data/local/tmp`输出路径的Permission denied也单独保留。

第二轮再次重启，系统初始化结束后明确force-stop KClash。两个探针前后完整进程表均无KClash进程：

| 数据源 | 结果 | traced_probes状态 |
| --- | --- | --- |
| linux.process_stats，3秒 | 41,335字节；777条process记录；final flush成功 | S→S |
| linux.ftrace，仅sched_switch/sched_waking，3秒 | 758字节；sched_slice=0；traced_flushes_failed=1，traced_final_flush_failed=1 | S→D |

因此当前阻塞可以在KClash完全未运行时由系统ftrace采集复现，问题范围收窄到本ROM/内核的ftrace生产者路径；没有内核栈，不能进一步指认具体驱动或内核函数。进程统计成功不等于能归因RenderThread。两次重启不改变ROM/SELinux/perf配置；原生慢帧归因继续未完成，后续需要可用ftrace的真机环境，不继续重复相同长采样。

原始配置、trace、前后进程状态、解析结果和失败日志：`../../acceptance-artifacts/post-release-20261110/`，摘要纳入本轮验收JSON。
