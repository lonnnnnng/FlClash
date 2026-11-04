package com.lonnnnnng.flclash.ui

// 作者 long：坐标直接移植 main:lib/ui/icons/app_glyphs.dart，保持上游图形语义与24单位网格；渲染规则见AppGlyph。
internal object AppGlyphs {
    val dashboard by lazy { AppGlyph(listOf(
    GlyphBox(3.5f, 3.5f, 10.5f, 11.5f, 2f),
    GlyphBox(13.5f, 3.5f, 20.5f, 9.5f, 2f),
    GlyphBox(3.5f, 14.5f, 10.5f, 20.5f, 2f),
    GlyphBox(13.5f, 12.5f, 20.5f, 20.5f, 2f),
  )) }

    val proxies by lazy { AppGlyph(listOf(
    GlyphCircle(12f, 12f, 8.8f),
    GlyphOval(8.2f, 3.2f, 15.8f, 20.8f, role = GlyphRole.detail),
    GlyphPolyline(listOf(
      GlyphVertex(3.2f, 12f),
      GlyphVertex(20.8f, 12f),
    ), role = GlyphRole.detail),
  )) }

    val profiles by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(5f, 3f, 2.5f),
      GlyphVertex(13.5f, 3f, 1f),
      GlyphVertex(19f, 8.5f, 1f),
      GlyphVertex(19f, 21f, 2.5f),
      GlyphVertex(5f, 21f, 2.5f),
    ), closed = true),
    GlyphPolyline(listOf(
      GlyphVertex(13.5f, 3.2f),
      GlyphVertex(13.5f, 8.5f, 1.5f),
      GlyphVertex(18.8f, 8.5f),
    ), role = GlyphRole.detail),
    GlyphPolyline(listOf(
      GlyphVertex(8.5f, 12.5f),
      GlyphVertex(15.5f, 12.5f),
    ), role = GlyphRole.detail),
    GlyphPolyline(listOf(
      GlyphVertex(8.5f, 16f),
      GlyphVertex(13f, 16f),
    ), role = GlyphRole.detail),
  )) }

    val tools by lazy { AppGlyph(listOf(
    GlyphBox(3f, 7f, 21f, 20f, 2.8f),
    GlyphPolyline(listOf(
      GlyphVertex(9f, 7f),
      GlyphVertex(9f, 4.6f, 1.4f),
      GlyphVertex(15f, 4.6f, 1.4f),
      GlyphVertex(15f, 7f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(3.2f, 12.6f),
      GlyphVertex(9.6f, 12.6f),
    ), role = GlyphRole.detail),
    GlyphPolyline(listOf(
      GlyphVertex(14.4f, 12.6f),
      GlyphVertex(20.8f, 12.6f),
    ), role = GlyphRole.detail),
    GlyphBox(10.4f, 11f, 13.6f, 14.4f, 1f, role = GlyphRole.detail),
  )) }

    val sync by lazy { AppGlyph(listOf(
    GlyphArc(
      12f,
      12f,
      7.78f,
      kotlin.math.PI.toFloat() * 1.144f,
      kotlin.math.PI.toFloat() * 0.889f,
      role = GlyphRole.line,
    ),
    GlyphPolyline(listOf(
      GlyphVertex(5f, 5f),
      GlyphVertex(5f, 8.6f, 0.4f),
      GlyphVertex(8.6f, 8.6f),
    ), role = GlyphRole.line),
    GlyphArc(
      12f,
      12f,
      7.78f,
      kotlin.math.PI.toFloat() * 0.144f,
      kotlin.math.PI.toFloat() * 0.889f,
      role = GlyphRole.line,
    ),
    GlyphPolyline(listOf(
      GlyphVertex(19f, 19f),
      GlyphVertex(19f, 15.4f, 0.4f),
      GlyphVertex(15.4f, 15.4f),
    ), role = GlyphRole.line),
  )) }

    val sort by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(4f, 7f),
      GlyphVertex(20f, 7f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(4f, 12f),
      GlyphVertex(15f, 12f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(4f, 17f),
      GlyphVertex(10f, 17f),
    ), role = GlyphRole.line),
  ), matchTextDirection = true) }

    val addCircle by lazy { AppGlyph(listOf(
    GlyphCircle(12f, 12f, 8.8f, role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(12f, 7.8f),
      GlyphVertex(12f, 16.2f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(7.8f, 12f),
      GlyphVertex(16.2f, 12f),
    ), role = GlyphRole.line),
  )) }

    val search by lazy { AppGlyph(listOf(
    GlyphCircle(10.3f, 10.3f, 6.9f, role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(15.18f, 15.18f),
      GlyphVertex(20.6f, 20.6f),
    ), role = GlyphRole.line),
  )) }

    val bolt by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(14.5f, 2.8f, 1f),
      GlyphVertex(5f, 13.7f, 1f),
      GlyphVertex(11.2f, 13.7f, 0.6f),
      GlyphVertex(9.5f, 21.2f, 1f),
      GlyphVertex(19f, 10.3f, 1f),
      GlyphVertex(12.8f, 10.3f, 0.6f),
    ), closed = true),
  )) }

    val more by lazy { AppGlyph(listOf(
    GlyphCircle(12f, 4.2f, 1.65f, role = GlyphRole.line, solid = true),
    GlyphCircle(12f, 12f, 1.65f, role = GlyphRole.line, solid = true),
    GlyphCircle(12f, 19.8f, 1.65f, role = GlyphRole.line, solid = true),
  )) }

    val check by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(4.8f, 12.5f),
      GlyphVertex(9.6f, 17.3f),
      GlyphVertex(19.4f, 6.7f),
    ), role = GlyphRole.line),
  )) }

    val checkCircle by lazy { AppGlyph(listOf(
    GlyphCircle(12f, 12f, 8.8f),
    GlyphPolyline(listOf(
      GlyphVertex(7.59f, 12.3f),
      GlyphVertex(10.49f, 15.2f),
      GlyphVertex(16.41f, 8.79f),
    ), role = GlyphRole.detail),
  )) }

    val close by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(6f, 6f),
      GlyphVertex(18f, 18f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(18f, 6f),
      GlyphVertex(6f, 18f),
    ), role = GlyphRole.line),
  )) }

    val clearField by lazy { AppGlyph(listOf(
    GlyphCircle(12f, 12f, 8.8f),
    GlyphPolyline(listOf(
      GlyphVertex(9.3f, 9.3f),
      GlyphVertex(14.7f, 14.7f),
    ), role = GlyphRole.detail),
    GlyphPolyline(listOf(
      GlyphVertex(14.7f, 9.3f),
      GlyphVertex(9.3f, 14.7f),
    ), role = GlyphRole.detail),
  )) }

    val arrowBack by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(5f, 12f),
      GlyphVertex(19f, 12f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(11.5f, 5.5f),
      GlyphVertex(5f, 12f, 0.4f),
      GlyphVertex(11.5f, 18.5f),
    ), role = GlyphRole.line),
  ), matchTextDirection = true) }

    val chevronBack by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(15.25f, 5.5f),
      GlyphVertex(8.75f, 12f, 0.4f),
      GlyphVertex(15.25f, 18.5f),
    ), role = GlyphRole.line),
  ), matchTextDirection = true) }

    val chevronForward by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(8.75f, 5.5f),
      GlyphVertex(15.25f, 12f, 0.4f),
      GlyphVertex(8.75f, 18.5f),
    ), role = GlyphRole.line),
  ), matchTextDirection = true) }

    val chevronDown by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(5.5f, 8.75f),
      GlyphVertex(12f, 15.25f, 0.4f),
      GlyphVertex(18.5f, 8.75f),
    ), role = GlyphRole.line),
  )) }

    val chevronUp by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(5.5f, 15.25f),
      GlyphVertex(12f, 8.75f, 0.4f),
      GlyphVertex(18.5f, 15.25f),
    ), role = GlyphRole.line),
  )) }

    val refresh by lazy { AppGlyph(listOf(
    GlyphArc(
      12f,
      12f,
      7.78f,
      kotlin.math.PI.toFloat() * 0.19f,
      kotlin.math.PI.toFloat() * 1.666f,
      role = GlyphRole.line,
    ),
    GlyphPolyline(listOf(
      GlyphVertex(19f, 5f),
      GlyphVertex(19f, 8.6f, 0.4f),
      GlyphVertex(15.4f, 8.6f),
    ), role = GlyphRole.line),
  )) }

    val locate by lazy { AppGlyph(listOf(
    GlyphCircle(12f, 12f, 6.2f),
    GlyphCircle(12f, 12f, 1.8f, role = GlyphRole.detail, solid = true),
    GlyphPolyline(listOf(
      GlyphVertex(12f, 2.9f),
      GlyphVertex(12f, 5.8f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(12f, 18.2f),
      GlyphVertex(12f, 21.1f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(2.9f, 12f),
      GlyphVertex(5.8f, 12f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(18.2f, 12f),
      GlyphVertex(21.1f, 12f),
    ), role = GlyphRole.line),
  )) }

    val edit by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(4f, 20f, 1f),
      GlyphVertex(8.75f, 18.75f, 1f),
      GlyphVertex(20.25f, 7.25f, 1.5f),
      GlyphVertex(16.75f, 3.75f, 1.5f),
      GlyphVertex(5.25f, 15.25f, 1f),
    ), closed = true),
    GlyphPolyline(listOf(
      GlyphVertex(13.25f, 7.25f),
      GlyphVertex(16.75f, 10.75f),
    ), role = GlyphRole.detail),
  )) }

    val delete by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(5.8f, 6.8f),
      GlyphVertex(18.2f, 6.8f),
      GlyphVertex(17.2f, 21f, 2.5f),
      GlyphVertex(6.8f, 21f, 2.5f),
    ), closed = true),
    GlyphPolyline(listOf(
      GlyphVertex(10f, 10.5f),
      GlyphVertex(10f, 17.5f),
    ), role = GlyphRole.detail),
    GlyphPolyline(listOf(
      GlyphVertex(14f, 10.5f),
      GlyphVertex(14f, 17.5f),
    ), role = GlyphRole.detail),
    GlyphPolyline(listOf(
      GlyphVertex(3.8f, 6.8f),
      GlyphVertex(20.2f, 6.8f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(9f, 6.8f),
      GlyphVertex(9f, 3.2f, 1.4f),
      GlyphVertex(15f, 3.2f, 1.4f),
      GlyphVertex(15f, 6.8f),
    ), role = GlyphRole.line),
  )) }

    val textShort by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(4f, 9f),
      GlyphVertex(20f, 9f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(4f, 15f),
      GlyphVertex(13f, 15f),
    ), role = GlyphRole.line),
  ), matchTextDirection = true) }

    val eye by lazy { AppGlyph(listOf(
    GlyphPath(
      "M3.01 11.32A10.08 10.08 0 0 1 20.99 11.32A1.5 1.5 0 0 1 20.99 12.68A10.08 10.08 0 0 1 3.01 12.68A1.5 1.5 0 0 1 3.01 11.32Z",
    ),
    GlyphCircle(12f, 12f, 2.8f, role = GlyphRole.detail),
  )) }

    val puzzle by lazy { AppGlyph(listOf(
    GlyphPath(
      "M3 10A2.2 2.2 0 0 1 5.2 7.8H7.94A2.6 2.6 0 1 1 11.26 7.8H14A2.2 2.2 0 0 1 16.2 10V12.74A2.6 2.6 0 1 1 16.2 16.06V18.8A2.2 2.2 0 0 1 14 21H5.2A2.2 2.2 0 0 1 3 18.8V17.58A0.8 0.8 0 0 1 4.08 16.83A2.6 2.6 0 1 0 4.08 11.97A0.8 0.8 0 0 1 3 11.22Z",
    ),
  )) }

    val moreCircle by lazy { AppGlyph(listOf(
    GlyphCircle(12f, 12f, 8.8f),
    GlyphCircle(7.4f, 12f, 1.4f, role = GlyphRole.detail, solid = true),
    GlyphCircle(12f, 12f, 1.4f, role = GlyphRole.detail, solid = true),
    GlyphCircle(16.6f, 12f, 1.4f, role = GlyphRole.detail, solid = true),
  )) }

    val copy by lazy { AppGlyph(listOf(
    GlyphBox(8.5f, 7.5f, 20.5f, 21f, 2.5f),
    GlyphPolyline(listOf(
      GlyphVertex(4f, 16.5f),
      GlyphVertex(4f, 3f, 2.5f),
      GlyphVertex(16f, 3f),
    ), role = GlyphRole.line),
  )) }

    val qrCode by lazy { AppGlyph(listOf(
    GlyphBox(3f, 3f, 10.2f, 10.2f, 2.2f),
    GlyphBox(13.8f, 3f, 21f, 10.2f, 2.2f),
    GlyphBox(3f, 13.8f, 10.2f, 21f, 2.2f),
    GlyphPath(
      "M6.1 5.5H7.1A0.6 0.6 0 0 1 7.7 6.1V7.1A0.6 0.6 0 0 1 7.1 7.7H6.1A0.6 0.6 0 0 1 5.5 7.1V6.1A0.6 0.6 0 0 1 6.1 5.5Z",
      role = GlyphRole.detail,
      solid = true,
    ),
    GlyphPath(
      "M16.9 5.5H17.9A0.6 0.6 0 0 1 18.5 6.1V7.1A0.6 0.6 0 0 1 17.9 7.7H16.9A0.6 0.6 0 0 1 16.3 7.1V6.1A0.6 0.6 0 0 1 16.9 5.5Z",
      role = GlyphRole.detail,
      solid = true,
    ),
    GlyphPath(
      "M6.1 16.3H7.1A0.6 0.6 0 0 1 7.7 16.9V17.9A0.6 0.6 0 0 1 7.1 18.5H6.1A0.6 0.6 0 0 1 5.5 17.9V16.9A0.6 0.6 0 0 1 6.1 16.3Z",
      role = GlyphRole.detail,
      solid = true,
    ),
    GlyphPath(
      "M14.5 13.8H15.5A0.7 0.7 0 0 1 16.2 14.5V15.5A0.7 0.7 0 0 1 15.5 16.2H14.5A0.7 0.7 0 0 1 13.8 15.5V14.5A0.7 0.7 0 0 1 14.5 13.8Z",
      role = GlyphRole.line,
      solid = true,
    ),
    GlyphPath(
      "M19.3 13.8H20.3A0.7 0.7 0 0 1 21 14.5V15.5A0.7 0.7 0 0 1 20.3 16.2H19.3A0.7 0.7 0 0 1 18.6 15.5V14.5A0.7 0.7 0 0 1 19.3 13.8Z",
      role = GlyphRole.line,
      solid = true,
    ),
    GlyphPath(
      "M19.3 18.6H20.3A0.7 0.7 0 0 1 21 19.3V20.3A0.7 0.7 0 0 1 20.3 21H19.3A0.7 0.7 0 0 1 18.6 20.3V19.3A0.7 0.7 0 0 1 19.3 18.6Z",
      role = GlyphRole.line,
      solid = true,
    ),
  )) }

    val export by lazy { AppGlyph(listOf(
    GlyphPolyline(listOf(
      GlyphVertex(8.4f, 10.5f),
      GlyphVertex(5f, 10.5f, 1.5f),
      GlyphVertex(5f, 21f, 2.8f),
      GlyphVertex(19f, 21f, 2.8f),
      GlyphVertex(19f, 10.5f, 1.5f),
      GlyphVertex(15.6f, 10.5f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(12f, 15.6f),
      GlyphVertex(12f, 3f),
    ), role = GlyphRole.line),
    GlyphPolyline(listOf(
      GlyphVertex(8.2f, 6.8f),
      GlyphVertex(12f, 3f, 0.4f),
      GlyphVertex(15.8f, 6.8f),
    ), role = GlyphRole.line),
  )) }

    val link by lazy { AppGlyph(listOf(
    GlyphPath(
      "M11.01 8.46L14.55 4.93A3.2 3.2 0 0 1 19.07 9.45L15.54 12.99",
      role = GlyphRole.line,
    ),
    GlyphPath(
      "M12.99 15.54L9.45 19.07A3.2 3.2 0 0 1 4.93 14.55L8.46 11.01",
      role = GlyphRole.line,
    ),
    GlyphPolyline(listOf(
      GlyphVertex(9.17f, 14.83f),
      GlyphVertex(14.83f, 9.17f),
    ), role = GlyphRole.line),
  )) }
}
