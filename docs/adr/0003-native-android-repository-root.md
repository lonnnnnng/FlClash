# Kotlin 主线采用原生 Android 仓库根

状态：accepted

`kotlin` 分支的仓库根目录直接承载原生 Android Gradle 工程。现有 Flutter Android 壳会被 Kotlin Activity、ViewModel 和 Android UI 替换；Flutter 源码只保留在 `main`/`upstream` 分支作为行为参考。现有 Go mihomo 核心以及 Android VPN/Proxy 服务库继续作为 Kotlin 主线的底层能力，不能因为移除 Flutter 壳而一并删除。

这个结构避免两套应用入口、构建系统和智能体约束长期共存，也让 Android Studio、Gradle、设备验收和发布流程以 Kotlin 主线为唯一工作面；代价是必须同步重写 Flutter 壳依赖的插件注册、状态管理、导航和 CI 路径。
