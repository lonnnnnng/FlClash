# 使用 StateFlow 和单向数据流组织界面

状态：accepted

Kotlin UI 采用单 Activity 与 Navigation Compose；每个功能页面由 ViewModel 持有状态，通过 StateFlow 暴露不可变 UI State，用户事件经 UseCase/Repository 进入业务层。页面只负责渲染状态和发送事件，不直接调用 Go 核心、Android Service 或 Room。

这个边界将 Flutter Riverpod 的状态职责迁移到 Kotlin 的生命周期感知组件，同时让核心运行状态、Profiles、代理组、连接、请求、DNS 和日志可以分别测试和演进。
