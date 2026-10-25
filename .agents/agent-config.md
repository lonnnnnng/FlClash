# Agent Configuration

`AGENTS.md` 只放所有任务都必须知道的边界；详细项目事实放在 `.agents/*.md`。可重复且稳定的工作流才建立仓库 Skill，机械检查优先落到 Gradle、Kotlin lint、测试或 Git hooks。

不要把用户级偏好、秘密、临时设备 serial 或个人路径写进仓库文档。涉及当前版本、Android API、Gradle/AGP 行为或上游事实时，先读取项目文件和官方资料再下结论。
