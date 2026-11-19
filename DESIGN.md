---
name: "KClash 折流 K"
description: "Android 手机启动图标规范，仅适用于图标。"
colors:
  background: "#087F75"
  foreground: "#F4FFF9"
  fold: "#9CEAD1"
---

# Design System: KClash 折流 K

## Overview

用户于 2026-11-19 选择方案 A「折流 K」。圆润主干和折返路径组成独立 K 标志，薄荷色下折面提供一次色彩分层。本规范只记录 Android 手机启动图标，不定义应用内主题，也不扩展 Flutter、TV、Windows 或 macOS 图标。

## Colors

三色精确值以 frontmatter 为准：`background` 是铺满画布的青绿背景，`foreground` 是主干与完整折返路径，`fold` 是覆盖在折线上的薄荷色下折面。

## Layout

源稿及自适应层均为 108×108；Android 层使用 dp。标志约 48.61×52.43，完整位于中央 66×66 **方形**安全区（坐标 21–87）；最大采样半径约 33.95，不能称为直径 66 的安全圆。

旧版位图以中央 72×72 视窗导出，同密度方形与圆形保持相同标志比例。

| 密度 | 尺寸 |
| --- | --- |
| mdpi | 48×48 px |
| hdpi | 72×72 px |
| xhdpi | 96×96 px |
| xxhdpi | 144×144 px |
| xxxhdpi | 192×192 px |

商店导出 512×512 px，高清品牌导出 1024×1024 px。

## Elevation & Depth

源稿为不透明纯色填充，无渐变、阴影或描边；折流层次由下折面的颜色形成。

## Shapes

主干与折返路径保持圆润端点。自适应层不预置外轮廓，由系统与 Launcher 决定圆形、圆角或其他遮罩。旧版方形/圆形位图是独立固定输出。

## Components

Manifest 同时声明 `icon` 与 `roundIcon`；两份自适应 XML 共用独立手机背景、三路径彩色前景及两路径单色前景。单色层保留主干与完整折线，由系统着色。TV 继续使用旧背景。

唯一可编辑源稿为 [kclash-icon.svg](assets/branding/kclash-icon.svg)，原创矢量绘制，非 AI 位图生成。[生成器](tool/generate_kclash_launcher.cjs)统一派生 XML、各密度位图、高清导出及预览。位图内嵌 XMP 来源/源稿哈希；见[来源记录](assets/branding/provenance.json)与[再生成说明](docs/branding/README.md)。

Redmi API36 独立 motion 包已在系统应用信息页展示圆形图标；静态遮罩/主题预览不代替其他 OEM、低 API 设备或 Launcher 主题开关实测。

## Do's and Don'ts

- Do 修改 SVG 后统一重新生成，保留完整 K 轮廓。
- Do 让系统决定自适应图标遮罩。
- Don't 将图标配色推广到应用内主题或其他平台。
- Don't 手改生成 XML/位图，或把安全方区写成安全圆。
