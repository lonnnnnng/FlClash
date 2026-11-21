# KUI-VIS-070 代理组健康检查选项行内编辑

## 范围

`url-test` 等代理组的 URL、间隔、超时、最大失败次数、预期状态码和容差现在直接在选项行编辑。数字项使用数字键盘并过滤非数字字符；URL 使用 URI 键盘。清空输入仅从 YAML 草稿中移除该值，选项行仍保留；移除图标才会删除该选项，之后可以从添加设置项重新加入并获得默认值。最终保存仍由代理组父级确认一次性提交。

实现位于 `ProxyGroupEditor.kt` 和共享表单输入 `ProfileFormInputs.kt`。固定 Flutter 源码仅作交互语义参考；本组没有启动 Flutter 页面，也没有声称运行态或逐像素一致。

## 验收

- POCO F2 Pro，serial `31936bb6`，API 36，竖屏，1080×2400。
- `ProxyGroupEditorInstrumentedTest` 最终 11/11 通过，覆盖双主题下的行内输入、真实 URI/数字键盘、数字过滤、清空留行、删除后重加默认值及父级提交回读。
- 24 张截图均为 1080×2400；图片 SHA、字节数和分片完整性在采集阶段校验，本轮再次核对图片哈希和尺寸。截图及清单见 `../acceptance-artifacts/group-options-inline-poco-20261121/final/`。
- motion JVM 单测 628/628 通过；Service 220、Core 39、Common 34 为 Gradle 复用结果。Debug 与 motion 构建、AndroidTest 构建及两种 Lint 均成功，Lint 无错误。
- 构建 APK SHA-256、测试 APK SHA-256、JUnit XML、Instrumentation 日志和截图清单的 SHA-256 见 `docs/acceptance/proxy-group-inline-options-poco-20261121.json`。
- 本轮结束时设备包列表核验未发现 KClash motion 或测试包；原版 `com.follow.clash` 仍安装，本轮没有操作该包。

## 未覆盖

本组仅验代理组选项编辑器及其具名交互。Flutter 页面运行对照、全页逐像素一致、其他子页连续转场、完整玻璃光学和菜单慢帧归因仍未闭环；继续按路线图推进。
