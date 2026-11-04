package com.lonnnnnng.flclash.ui

import androidx.compose.ui.graphics.Color

internal enum class AppGlassQuality { STANDARD, ULTRA }

/** 作者 long：材质与捕获、轮廓分离，按钮到菜单的变形才能连续改变光学参数，而不被文字显隐进度提前截断。 */
internal data class AppGlassStyle(
    val tint: Color, val contrast: Float = 0f, val blur: Float = 2.5f,
    val haze: Float = 0f, val hazeBlur: Float = 0f, val refraction: Float = 60f,
    val bevel: Float = 20f, val saturation: Float = 1.5f, val brightness: Float = 1f,
    val rim: Float = .6f, val rimWidth: Float = 1.3f, val rimTail: Float = 0f,
    val rimFocus: Float = 0f, val rimAdded: Float = 0f, val endShade: Float = 0f,
    val dispersion: Float = 0f, val rimBlur: Float = 0f, val hairline: Float = 0f,
    val hairlineSide: Float = hairline, val menuRim: Float = 0f,
) {
    fun interpolate(to: AppGlassStyle, progress: Float): AppGlassStyle {
        val t = progress.coerceIn(0f, 1f)
        if (t == 0f) return this
        if (t == 1f) return to
        fun mix(a: Float, b: Float) = a + (b - a) * t
        return AppGlassStyle(Color(mix(tint.red, to.tint.red), mix(tint.green, to.tint.green), mix(tint.blue, to.tint.blue), mix(tint.alpha, to.tint.alpha)), mix(contrast, to.contrast), mix(blur, to.blur),
            mix(haze, to.haze), mix(hazeBlur, to.hazeBlur), mix(refraction, to.refraction),
            mix(bevel, to.bevel), mix(saturation, to.saturation), mix(brightness, to.brightness),
            mix(rim, to.rim), mix(rimWidth, to.rimWidth), mix(rimTail, to.rimTail),
            mix(rimFocus, to.rimFocus), mix(rimAdded, to.rimAdded), mix(endShade, to.endShade),
            mix(dispersion, to.dispersion), mix(rimBlur, to.rimBlur), mix(hairline, to.hairline),
            mix(hairlineSide, to.hairlineSide), mix(menuRim, to.menuRim))
    }

    // 作者 long：standard只合并雾化采样、去除色散，仍保留折射和边缘；降低质量不能悄悄变成不透明材质。
    fun effective(quality: AppGlassQuality) = if (quality == AppGlassQuality.ULTRA) this
        else copy(blur = if (haze > 0f) hazeBlur else blur, haze = 0f, hazeBlur = 0f, dispersion = 0f)

    // 作者 long：先合并standard雾化再缩放，pill从零展开时才不会在第一帧突然跳到完整15.6dp模糊。
    fun resolve(quality: AppGlassQuality, amount: Float = 1f) = effective(quality).strength(amount)

    // 作者 long：展开强度只减弱光学效果；染色RGB、边缘尺寸和雾化半径保持原材质，避免从透明黑色插值造成变色。
    fun strength(amount: Float): AppGlassStyle {
        val t = amount.coerceIn(0f, 1f)
        return copy(tint = tint.copy(alpha = tint.alpha * t), contrast = contrast * t, blur = blur * t,
            haze = haze * t, refraction = refraction * t, saturation = 1f + (saturation - 1f) * t,
            brightness = 1f + (brightness - 1f) * t, rim = rim * t, endShade = endShade * t,
            dispersion = dispersion * t, rimBlur = rimBlur * t, hairline = hairline * t,
            hairlineSide = hairlineSide * t, menuRim = menuRim * t)
    }

    companion object {
        // 作者 long：固定默认主题的深色来自Flutter实际HCT导出；玻璃仍由独立开关控制，不能顺带开启主题设置。
        private val darkTint = Color(0xFF343537)
        fun regular(dark: Boolean) = AppGlassStyle(if (dark) darkTint.copy(alpha = .62f) else Color(0x8AFDFDFD),
            contrast = if (dark) -.4f else .4f, saturation = if (dark) 2.84f else 2.08f,
            rim = .28f, rimWidth = 1.2f, rimTail = .17f, rimFocus = 1f, rimAdded = .38f,
            hairline = if (dark) 0f else .25f, hairlineSide = .75f)
        fun pill(dark: Boolean) = AppGlassStyle(if (dark) darkTint.copy(alpha = .48f) else Color(0x93F4F4F4),
            contrast = if (dark) -.4f else .4f, blur = 0f, haze = .72f, hazeBlur = 15.6f,
            saturation = if (dark) 1.925f else 2.1f, rim = if (dark) .35f else .6f, hairline = if (dark) 0f else .35f)
        fun menu(dark: Boolean, surfaceBright: Color) = AppGlassStyle(if (dark) darkTint.copy(alpha = .63f) else surfaceBright.copy(alpha = .55f),
            contrast = if (dark) -.53f else 0f, blur = 12f, saturation = if (dark) 1.75f else 2f,
            brightness = if (dark) 1.8f else 1.1f, rim = 0f, menuRim = 1f)
        fun alert(dark: Boolean, surfaceBright: Color) = menu(dark, surfaceBright).copy(blur = 16f)
        fun tinted(dark: Boolean, color: Color) = AppGlassStyle(color.copy(alpha = .82f), saturation = 1.2f,
            rim = if (dark) .14f else .2f, rimWidth = 1.2f, rimTail = .17f, rimFocus = 1f, rimAdded = .38f,
            hairline = if (dark) 0f else .34f, hairlineSide = if (dark) .73f else .5f)
        fun lens(dark: Boolean) = AppGlassStyle(Color.Transparent, blur = 0f, refraction = -4.3f, bevel = 5.5f,
            saturation = 1f, brightness = if (dark) 1.33f else 1f, rim = if (dark) .3f else .6f,
            rimWidth = 1.1f, endShade = .045f, dispersion = 6.5f, rimBlur = 1.5f, hairline = .42f)
    }
}
