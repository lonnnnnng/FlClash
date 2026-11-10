# KUI-036：组编辑 Sheet、原位成员与图库

基线为 Kotlin `4ec1ab72448cb9b85b079f11c1716818bc715740`；Flutter 固定 `be84736605527034689c95bf27828ff27d912c5e`，Prism UI 固定 `63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185`。Flutter 仅作实现与运行对照，没有合并其提交。原始附件位于工作区 `acceptance-artifacts/group-sheet-20261110/`。

## 实现与来源

- Flutter `lib/views/profiles/custom/groups.dart`、`members.dart`、`icon.dart`：常规字段、原位成员、添加行、选项顺序；成员左删除、右常驻拖动柄，协议/类别徽标；添加节点返回保留草稿；图标选择返回同一父表单。
- Flutter `lib/features/form/nested_form_sheet.dart` 与 Prism `paged_sheet.dart`、`snap_sheet.dart`：同一窗口的子页栈和退出保护、9/16 与 0.9 停靠档位。Kotlin 新增 `ProfileEditorSheet`，在同一 Dialog 内切换成员、图库、选项和快捷 YAML，子页滑移/淡入淡出，离场内容停止接收输入。
- Kotlin `ProxyGroupInlineMembers` 使用单个 LazyColumn；长按拖拽支持边缘自动滚动，抬起才更新草稿，取消/进入后台丢弃预览。成员布局元数据仍不进入 Mihomo YAML。
- `ProxyGroupVisuals` 对齐图库来源按钮及 56dp 图标底板、40dp 图片、6dp 图文间距、13sp/18sp 普通字重标签；保留本地图片入口、已选图标说明和搜索。
- `ConnectionModalSheet` 的新增退出门禁、柄尺寸与程序关闭参数均有默认值，其他既有面板继续沿用原行为。玻璃默认关闭。

## 本轮源码问题与修复溯源

以下是本轮 Kotlin 候选实现的本地问题，**不是 Flutter/Mihomo 上游缺陷**。后续同步时应保留对应语义和测试，不向内核仓库搬移这些补丁。

| 问题 | 触发与原因 | 修复与验证入口 |
|---|---|---|
| 加密注册表不接受枚举 | 新 Sheet 将 `ConnectionSheetDetent` 直接放入 `rememberSaveable`；正式宿主的 `EditorSavedValue` 只接受明确值类型，Compose 注册阶段即可拒绝。静态发现，未在正式包触发崩溃。 | 停靠值通过 Saver 保存名称；`encryptedRegistryRestoresSheetAndMemberDraftAfterSubtreeRecreation` 和真实宿主验证。没有放开任意 Serializable/Parcelable。 |
| 放弃后迟到校验可能转为保存 | 异步过滤校验在退场动画完成前返回，旧实现可能填充 `acceptedJson`。静态发现。 | 退出使确认序号失效，关闭/放弃时拒绝迟到结果；`discardDuringValidationRejectsLateSuccessBeforeExitAnimationEnds`。 |
| 重复成员身份冲突 | 快捷 YAML/导入允许同名成员；仅用类型和名称作 LazyColumn key 会冲突，删除也可能删掉所有同名项。静态发现。 | key 包含出现序号，删除只移除一个出现位置；`repeatedMembersHaveIndependentRowsAndDeleteOnlyOneOccurrence`。 |
| 子页滚动位置断开 | 图库 Grid 和 YAML 正文原来未提供实际位置给 Sheet，中部起手可能被当成顶部下拉。 | 提升 Grid/正文滚动状态，按当前路由判断顶部；`galleryAndYamlMiddleScrollDoNotCollapseExpandedSheet`。 |
| 子页重建/重开会话 | 早批 YAML 重建丢输入；保存所有历史子页又可能在重开成员页时复活旧 origin。 | Dialog 外的 SaveableStateHolder 接入加密树，仅当前子页保留重建状态，返回时注销已退出页；重建及再次打开用例。 |

失败的构建、测试与定位批次独立保留，最终结果不与中间批次累计。旧成员测试还依赖独立成员页/排序按钮；本轮改为真实原位拖动与同 Sheet 返回。真实宿主测试分别识别空态正文/非空顶栏的添加入口并等待文件保存完成；长列表先由 LazyColumn 定位尚未组合的添加行。长正文滚动只用可见视口内的真实触点，超大语义 ScrollBy 会把剩余位移交给父 Sheet，不能当成用户从中部起手。

## 验收与边界

最终结果见 [Redmi 验收 JSON](acceptance/group-sheet-redmi-20261110.json)。879项JVM（App594/Service216/Core38/Common31，含Gradle复用）零失败/错误/跳过；Debug、未签名Release-R8、motion/Test构建及双Lint零错误，Debug72警告/14提示、Release68警告/13提示，扫码DEX门禁3/3。

Redmi最终组件及编辑回归16/16、真实MainActivity保存/重开1/1、组成员与原位拖拽两条加密冷恢复均通过；16项来自tests9，其同批MainActivity失败保留，修正测试等待后单独main-final通过，不称单批17/17。冷恢复使用原1.yaml的独立临时CUSTOM副本，退出放弃后正文SHA/mtime保持，结束fixture库0配置；属于加密UI草稿恢复，仍不覆盖混排元数据提交后的Room冷读全矩阵。force-stop结束旧PID6876/7471，ROM随即出现替换进程；已验证旧/proc消失与不同验证PID，不把零进程门禁失败改写为成功，不推断替换进程的拉起来源。

深浅自然触摸流程2/2，12张终态图及503/510帧录像，原PTS单调；各抽32帧目视。固定Flutter图库往返录像57帧也按原PTS抽32帧核对。录屏包含宿主启动/主题设置和系统退出帧，这些不计子页视觉等价，也不用于菜单性能比较。最终组件22PNG、两条冷恢复4PNG经拼图目视。本轮仅 Redmi Note 8 Pro `wsvwypiz7xwslvl7`、API36、竖屏、系统字号1.0；隔离 motion 包，不安装或修改原版 `com.follow.clash`。

固定 Flutter 安全副本成功通过 URL 导入三张 PNG，进入图库详情及 CUSTOM 组图标子页，真实选图后回父草稿，退出放弃，随后精确删除 `KUI-036 gallery` 并回读空态。图片来自电脑 127.0.0.1 HTTP 服务，经 USB reverse；这是非空 UI/图片加载对照，不是公网图标服务验收。Kotlin 使用隔离 PNG 数据 URI 并断言解码像素，不能把它记成 HTTP 图库导入通过。

以上为KUI-036历史验收；提交后混排Room冷读、浮动边距/缩放、横向拖回和短内容适配的后续进展见[KUI-037](group-editor-closure.md)。仍待逐帧等价及全产品其他子页；本轮具名组编辑流程不能代替完整 Flutter 视觉验收。菜单首开、关闭和 RenderThread 原生函数归因未在本组解决，KUI-035 的 A-B-A 负结果继续有效。正式签名运行、真实网络/核心负载、历史异常长窗口、低 API/蜂窝/OEM 仍按既有清单推进；备份恢复、语言主题、观测次要打磨继续暂缓，不恢复 TalkBack 工作。
