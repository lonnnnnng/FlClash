# Worktrees

使用 worktree 前先确认当前分支、工作树和未提交改动。不同任务不得共享同一工作树的未提交文件；结束后只清理自己创建且已确认干净的 worktree，不使用 `git clean` 代替核对。
