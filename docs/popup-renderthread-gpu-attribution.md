# 菜单动画后续帧与 GPU 等待归因

## 2026-11-25 POCO F2 Pro / API36 R8 双次采样

本轮在 `com.lonnnnnng.flclash.performance` 独立 Performance-R8 `MainActivity` 连续打开/点外关闭来源菜单各 8 次，两次独立 Perfetto 采集时没有屏幕录像和 Instrumentation。Trace 保存在 `../acceptance-artifacts/sniffer-continuation-poco-20261125/r8-menu-joint-r1/` 和 `r8-menu-joint-r2/`；输入配置只用作未触碰正式库的本地验收副本。

### FrameTimeline 与 RenderThread 计数

RenderThread 有效 `DrawFrames` 根切片在第一轮为517条/501个 token，第二轮520条/504个 token。每轮16个 token 各关联两条不同根切片；它们都在同一 RenderThread 轨道、depth=0、彼此不重叠，也没有完全相同的切片行。因此 `surface_frame_token` 是关联键，不能作为根切片主键。保留 `slice.id` 才能避免把实际不同的绘制工作错误折叠。

FrameTimeline 必须用 `actual_frame_timeline_slice.id` 统计，不能把同 token 的多个 RenderThread 根或主窗/Popup layer join 行相加。修正后的 Popup layer frame 行为493/496，每个 `frame_id` 均唯一。标记的打开/关闭区间结果如下，App Deadline Missed 与 Buffer Stuffing 复合类型同时计入两个类别，因此分类数不是互斥总数：

| 采样 | 阶段 | Popup 帧行 | App Deadline Missed | Dropped Frame | Buffer Stuffing |
| --- | --- | ---: | ---: | ---: | ---: |
| R1 | 打开 | 220 | 8 | 7 | 57 |
| R1 | 关闭 | 248 | 0 | 0 | 216 |
| R2 | 打开 | 223 | 4 | 8 | 24 |
| R2 | 关闭 | 248 | 0 | 0 | 217 |

修正 SQL：`tool/perfetto_popup_rt_frame_detail.sql` 保留所有根切片并把timeline frame ID展开；`tool/perfetto_popup_unique_frame_summary.sql` 按 Popup frame ID汇总；`tool/perfetto_popup_kgsl_waits.sql` 配对应用 RenderThread 上的 KGSL waittimestamp，并核对动画阶段、RenderThread根及Popup frame窗口的时间交集。校正结果另存于两份 trace 目录中的 `rt-frame-detail-corrected.csv`、`popup-frame-summary-corrected.csv` 与 `kgsl-waits-corrected.csv`。

### Shader 编译与 KGSL 等待

两轮均捕获到 RenderThread Shader 编译根：

- R1 打开阶段最大一根 `DrawFrames` 为39.899ms Running，其中 Shader 编译切片共4个、合计34.313ms；cache miss 33.404ms、driver compile 11.322ms、driver link 19.865ms。这些子切片有包含关系，不能相加成另一份总耗时。
- R1 另有打开帧 Shader 编译11.035ms，关闭帧7.162ms；R2 的打开帧为22.231ms Running，包含一次15.732ms Shader 编译。跨两次独立采样仍能看到编译工作，不能把它归为只发生一次的冷首帧。
- 驱动的 `kgsl_waittimestamp` 直接出现在应用 RenderThread，但较稀疏：R1 关闭阶段3次，共3.661ms，单次0.236–2.159ms；R1 打开阶段另有3.828ms一次；R2 只记录到打开阶段1.251ms一次。R1关闭阶段的3次等待各与一个 RenderThread 根及横跨阶段边界的 frame interval 有时间交集，其中 frame 记录带有 App Deadline Missed/Buffer Stuffing 标记。相邻窗口重叠只证明时间相关，不能证明等待造成了该帧超时；R2关闭阶段没有 KGSL wait。

两份 trace 的 `gpu_slice` 都是0行，FrameTimeline 的 `latched_fence_state` 和 unsignaled fence 计数为空；所以当前能确认 RenderThread Shader 编译和少量 KGSL 等待事件，但没有 GPU 执行时长切片，也没有稳定证据证明 GPU/fence 等待是关闭慢帧的主因。关闭区间按 frame 起点分类未见 App Deadline Missed / Dropped Frame，但 R1 的阶段边界 wait 与前后 frame overlap 仍保留在逐事件 CSV，不把这个窗口统计解释成“完全没有 GPU 相关等待”。

两轮全局 `traced_chunks_discarded` 为51/53，session ring buffer overwrite 和 data-loss counters 均为0；Perfetto 汇总没有提供 discarded 的 producer owner，来源仍未知。

### 结论范围

证据来自独立 Performance-R8 包的真实 MainActivity来源菜单，不是普通正式签名 Release。它显示打开/关闭两段都能出现 RenderThread 编译活动，少量 KGSL等待只在部分轮次与阶段边界重叠；没有建立稳定的 GPU 根因、没有验证菜单优化收益。完整 Flutter 连续页面对照也未完成。本条为采集与分析，不修改产品 UI。

[结构化验收 JSON](acceptance/sniffer-continuation-poco-20261125.json)，[原始 trace 与派生 CSV](../acceptance-artifacts/sniffer-continuation-poco-20261125/)。
