# 先校验再应用核心配置

状态：accepted

KClash 启动核心时先把当前 Profile 原子复制为 Mihomo 专属的 `config.yaml`，再依次执行 `validateConfig` 和 `setupConfig`，校验失败时保持失败状态，不启动监听或前台代理服务。Mihomo 的 `setupConfig` 在解析失败时会回退到内置空配置并返回错误；如果直接把调用完成当作启动成功，界面会显示核心运行中但实际上没有可用代理，因此运行管理器必须把校验和应用作为启动前置条件。
