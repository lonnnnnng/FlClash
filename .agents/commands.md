# Commands

## Repository checks

执行命令前确认当前目录、分支和工作树：

```zsh
pwd
git status --short --branch
git remote -v
```

根目录 Kotlin Android 工程使用仓库根目录 Wrapper；常用检查为：

```zsh
./gradlew :app:assembleDebug
./gradlew test
./gradlew lint
./gradlew connectedDebugAndroidTest
```

Release 预发布检查（没有独立签名身份时输出未签名 APK）：

```zsh
./gradlew :app:assembleRelease :app:lintRelease --no-daemon -Ptarget-platform=android-arm64
```

`.github/workflows/kotlin-android.yml` 使用同一根目录 Wrapper，负责 Kotlin 主线的 arm64 单元测试、Debug/Release 构建、R8、Lint 和未签名 Release 产物检查；Flutter 发布 workflow 不作为 Kotlin 主线的构建入口。

## Go core

```zsh
go version
go test ./core/...
```

gomobile AAR 构建需要服从 `core/go.mod`、Android SDK、NDK 和 Gradle 配置；不要手工复制或修改生成的 AAR/classes。

## Android devices

```zsh
command -v adb
adb devices -l
adb -s <serial> shell getprop ro.build.version.sdk
adb -s <serial> shell getprop ro.product.model
```

安装、启动、截图、日志和清数据都必须显式使用目标 serial。失败时保留命令、serial、退出码和关键错误。

## Git

提交前运行 `git diff --check`、相关 Gradle/Go 测试和 `git status --short --branch`。提交说明使用中文，不把生成物、密钥或临时日志加入版本库。
