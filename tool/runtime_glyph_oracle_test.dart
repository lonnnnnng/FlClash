// 作者 long：放入独立 Flutter 参考宿主的 test/ 后运行 flutter test；
// 宿主必须引用验收 JSON 固定摘要的 fl_clash 和 prism_ui，不能把参考运行时接入 Kotlin。
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter_test/flutter_test.dart';
import 'package:fl_clash/ui/icons/app_glyphs.dart';
import 'package:prism_ui/icons/glyph_painter.dart';

void main() {
  test('fixed Flutter glyph pixels', () async {
    final result = <Object>[];
    for (final p in [-.08, 0.0, .25, .5, .75, 1.0, 1.08, 2.0]) {
      final glyph = p == 2 ? AppGlyphs.wifiOff : AppGlyphs.playPause(p);
      final recorder = ui.PictureRecorder();
      GlyphPainter(glyph: glyph, fill: 1, color: const ui.Color(0xffffffff))
          .paint(ui.Canvas(recorder), const ui.Size(96, 96));
      final picture = recorder.endRecording();
      final image = await picture.toImage(96, 96);
      final data = (await image.toByteData(format: ui.ImageByteFormat.rawRgba))!;
      // 作者 long：导出独立光栅的 alpha 掩码，避免复制 Kotlin 算法作为它自己的断言来源。
      result.add({
        'progress': p,
        'mask': List.generate(96, (y) => List.generate(96,
            (x) => data.getUint8((y * 96 + x) * 4 + 3) >= 128 ? '1' : '0').join()),
      });
      image.dispose();
      picture.dispose();
    }
    File('glyph-reference.json').writeAsStringSync(jsonEncode(result));
  });
}
