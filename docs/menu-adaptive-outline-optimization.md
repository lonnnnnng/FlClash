# 菜单液滴轮廓自适应采样

KUI-VIS-102，2026-11-26，作者 long。POCO F2 Pro（`31936bb6`，API36）独立 Motion 与 Performance-R8 包验收。基线 `d9dec013`；玻璃默认关闭。

## 来源与实现

固定 Flutter `be84736605527034689c95bf27828ff27d912c5e` / Prism `63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185` 的 `plugins/prism_ui/lib/goo.dart:400-580` 使用自适应桥接：每四条射线取一个种子（128个方向共32个种子），只在深度分类变化或中点偏离弦超过阈值时细分，再简化轮廓内部冗余点。Kotlin 旧实现无条件计算128条射线，也没有弦误差简化。

`AppMenuGoo.kt` 现在按该结构构造桥接；主体连续圆角、距离场、四次二分、Path union与动画时间保持原参数。沿用上游0.05逻辑像素误差，`AppMenuMorphFrame` 将其按密度转换为物理像素。独立诊断包新增 `kclash/menu/goo-outline` 同步切片；普通Debug/Release的 `MENU_TRACE=false`，不加入正式运行时采样。

本组没有合并Flutter提交，没有改主题/语言/更新检查、核心或配置逻辑。不是玻璃材质降级，也没有缩短动画来降低帧数。

## 验证结果

- Motion与Debug App JVM各631项、84 suites，零失败/错误/跳过。菜单动效类16项包含密度误差约束。
- Performance-R8、Motion、AndroidTest、Debug和Release-R8构建通过；Motion/Debug/Release Lint通过，计数见验收JSON。
- POCO五个具名几何/交互用例通过；几何粗回归比较11个开合中间帧的新旧路径填充，返回/中途撤销、深浅主题与普通/玻璃材质、嵌套顶卡回源和外层阴影具名回归通过。Region是隔2px的二值采样，以旧轮廓覆盖面积作为差异分母，阈值0.2%；不是抗锯齿像素等价断言。
- 23张PNG分片/字节/SHA完整；20张几何图已目视。与11月20日同机旧几何图比较，8张全像素相同，其余差异集中在轮廓边缘，最多444/916938像素（0.0484%）。这是历史Kotlin回归对照，不是完整Flutter页面运行对照。
- 独立Motion测试线程局部计时：11帧、每批8遍、3遍预热、9批交错顺序。固定射线中位22.858ms，自适应19.231ms，下降15.9%。包含Path构造/合并/getBounds，不是UI线程、GPU或端到端帧耗时；初期样本仍有JIT/缓存变化，原始九组样本全部保留。

## R8真实菜单对照与保留边界

每轮冷进程、真实MainActivity空配置页来源菜单打开/点外关闭8次，20秒Perfetto。无本轮录屏或Instrumentation并发；手机已有屏幕共享计时与悬浮层保持，没有操作其他应用。不是无后台负载实验，也不是公开签名Release实包。按 `actual_frame_timeline_slice.id` 统计Popup帧，线程根按 `slice.id` 统计；同token不折叠独立根，各类jank可重叠。

| 采样 | 打开帧 / deadline / dropped | 关闭帧 / deadline / dropped | 阴影绘制最大 / 总和（ms） |
| --- | --- | --- | --- |
| 基线1 | 211 / 11 / 6 | 248 / 1 / 0 | 8.538 / 352.324 |
| 基线2 | 221 / 9 / 7 | 248 / 1 / 0 | 7.125 / 348.871 |
| 反向基线 | 218 / 9 / 8 | 248 / 1 / 0 | 7.057 / 345.244 |
| 自适应1 | 217 / 9 / 6 | 248 / 0 / 1 | 3.463 / 323.413 |
| 自适应2 | 219 / 6 / 6 | 248 / 1 / 0 | 4.229 / 326.728 |

阴影切片包含轮廓计算，不与新goo切片相加。当前有局部工作减负证据，但首窗最大UI帧仍约79ms，总体慢帧波动仍在，不能宣称整体菜单卡顿已解决或稳定帧率收益成立。R1关闭仍有1个Dropped Frame，保留而不合并隐藏。

此前还尝试半物理像素步长的128项BlurEffect缓存。两轮缓存候选与反向基线的慢帧/RenderThread时间重叠，反向基线Shader峰值也降低，说明不能把热缓存变化归因给候选。该候选已全部撤回，补丁、APK和两份trace仅留在本机附件。

七份trace的全局 `traced_chunks_discarded` 为55/57/59/61/63/65/67；会话缓冲discard/overwrite和正值error/data_loss均为0。全局计数没有producer owner，不能当菜单丢帧数。FrameTimeline fence字段未填，GPU等待根因仍未收口；本组不宣称新的GPU因果结论。

## 后续

继续首次Popup的组合/测量/系统建窗，以及关闭释放和RenderThread Shader/GPU等待；性能采样和视觉录屏继续分开。完整Flutter有内容页面与连续主路由仍待，独立full.dev参考包仍留在首次声明流程，待用户亲自完成，未代为确认。

本轮Motion/Test/Performance三个自有包和8份设备采集文件已在SHA核对后回收。正式Kotlin0.1.21和原Flutter0.9.4的安装元数据及服务状态前后相同；没有打开正式Room做字节级数据检查。

原始材料位于本机 `../acceptance-artifacts/menu-jank-optimization-poco-20261126/`。验收JSON：[`acceptance/menu-adaptive-outline-poco-20261126.json`](acceptance/menu-adaptive-outline-poco-20261126.json)。本轮不发版；正式Kotlin与原版Flutter安装状态保持，原`1.yaml`未读取或上传。
