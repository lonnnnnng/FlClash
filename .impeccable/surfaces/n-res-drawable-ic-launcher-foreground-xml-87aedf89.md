---
version: 1
slug: "n-res-drawable-ic-launcher-foreground-xml-87aedf89"
primary_target: "android/app/src/main/res/drawable/ic_launcher_foreground.xml"
related_targets: ["android/app/src/main/AndroidManifest.xml","assets/branding/kclash-icon.svg"]
---

# Android 启动图标

范围：Android 手机启动图标，Operate。用户在 2026-11-19 明确选择 A「折流 K」，不改变应用内界面主题。

## Direction contract

THESIS: 以 K 主干和折返路径建立独立身份，在桌面小尺寸保持清晰。

OWN-WORLD: 青绿 #087F75、近白 #F4FFF9、薄荷 #9CEAD1；纯色矢量、圆润端点、必要的单次折面。

STORY: 用户在混合应用桌面上迅速识别并打开 KClash；不同遮罩保留同一个标志。

FIRST VIEWPORT: 108×108 dp 两层自适应源，中央约 49×52 dp 标志；方形、圆形、圆角及单色共用几何，背景铺满画布。

FORM: 第六候选「折流 K」，seed:2338a1da128b7846；用户明确选择 A，按该预览几何落地。

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

不扩展到 Flutter、TV、桌面端或应用内主题；旧版 Android 使用各密度的固定方形/圆形位图，现代系统由 Launcher 决定遮罩。单色层必须保留完整轮廓，不能把背景当作标志。
