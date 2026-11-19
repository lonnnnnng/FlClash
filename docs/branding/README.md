# KClash「折流 K」应用图标

用户于 2026-11-19 选择方案 A。青绿底色、近白主干与薄荷色下折面组成独立 K 标志。本次仅替换 Kotlin Android 手机图标。

- 可编辑源稿：`assets/branding/kclash-icon.svg`（108×108 坐标，背景铺满）。
- 高清导出：`assets/branding/kclash-icon-1024.png`、`android/app/src/main/ic_launcher-playstore.png`（512×512）。
- 五档旧版位图：mdpi 48、hdpi 72、xhdpi 96、xxhdpi 144、xxxhdpi 192 px，每档含方形与圆形。
- API 26+：前景/背景分层，由 Launcher 裁成圆形、方形、圆角或其他系统形状。Manifest 同时声明 `icon` 和 `roundIcon`。
- 主题图标：独立 `ic_launcher_monochrome.xml` 只含主干和完整折线，系统负责着色。
- 设计尺寸：标志约 48.61×52.43 dp，完整位于中央 66×66 dp 安全方区，不能称为直径 66 dp 的安全圆。
- 生成来源：位图内嵌 XMP 源稿哈希，完整来源记录见 `assets/branding/provenance.json`。本图为原创矢量绘制，无 AI 位图生成。

## 重新生成

需要 Node.js 与 sharp（本轮使用 sharp 0.35.5）。在仓库根执行：

```sh
node tool/generate_kclash_launcher.cjs
```

若 sharp 安装在独立工具目录，显式传 `--sharp-module /absolute/path/to/sharp`。脚本会更新手机资源、商店/高清导出及遮罩预览。请修改 SVG 后重新生成，不手改生成的 XML/位图。

旧背景色 `ic_launcher_background` 仍供 TV 使用；手机使用独立的 `kclash_launcher_background`。Flutter、TV、Windows 和 macOS 图标不属于本次替换范围。遮罩预览是源稿的静态栅格化，不等同于其他 OEM 桌面实测。

平台尺寸来源：[Android 官方自适应图标规范](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)，读取于 2026-11-19。
