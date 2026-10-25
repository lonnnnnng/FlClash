# 使用 Hilt 管理应用依赖

状态：accepted

Kotlin 应用使用 Hilt 管理 Room、Mihomo Bridge、网络客户端、Repository、UseCase、ViewModel 和 Service 管理器的依赖生命周期；页面、Service 和全局状态不自行创建共享对象，测试通过替换注入绑定隔离核心、数据库和网络数据源。

这个选择让 Compose、前台服务和核心运行管理器共享明确的生命周期边界，并减少手工 Service Locator 的隐式全局状态；代价是构建需要额外的代码生成和模块配置。
