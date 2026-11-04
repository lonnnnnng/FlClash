#!/usr/bin/env python3
"""作者 long：用固定Flutter上游源码生成独立色板/图标/曲线证据，避免以Kotlin自身截图证明Flutter对齐。"""
import argparse
import json
import re
import subprocess
from pathlib import Path

REFERENCE = "7fd417fb9a9a277c6a9339a8ac87f907df7c25f7"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--flutter", default="flutter")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    tests = output / "test"
    tests.mkdir()
    def upstream(path):
        return subprocess.check_output(["git", "show", f"{REFERENCE}:{path}"], cwd=root, text=True)
    color = upstream("lib/ui/color.dart").replace("package:material_ui/material_ui.dart", "package:flutter/material.dart")
    (tests / "color.dart").write_text(color)
    for name in ["glyph", "glyph_painter", "app_glyphs"]:
        source = upstream(f"lib/ui/icons/{name}.dart").replace("package:material_ui/material_ui.dart", "package:flutter/material.dart").replace("package:fl_clash/ui/icons/glyph.dart", "glyph.dart")
        (tests / f"{name}.dart").write_text(source)
    (output / "pubspec.yaml").write_text('name: kclash_visual_oracle\nenvironment:\n  sdk: ">=3.8.0 <4.0.0"\ndependencies:\n  flutter:\n    sdk: flutter\n  material_new_shapes: 1.0.0\n  path_parsing: 1.1.0\n  material_color_utilities: 0.13.0\ndev_dependencies:\n  flutter_test:\n    sdk: flutter\n')
    (tests / "export_test.dart").write_text("import 'dart:convert';\nimport 'dart:io';\nimport 'package:flutter/material.dart';\nimport 'package:flutter_test/flutter_test.dart';\nimport 'color.dart';\nvoid main() { test('default palette from upstream', () {\nfinal result = <String, dynamic>{};\nfor(final brightness in Brightness.values) {\nfinal c=seededColorScheme(seedColor: const Color(0xFF007AFF), brightness: brightness);\nresult[brightness.name] = {'primary': c.primary.toARGB32().toRadixString(16),'onPrimary': c.onPrimary.toARGB32().toRadixString(16),'surfaceTint': c.surfaceTint.toARGB32().toRadixString(16),'primaryContainer': c.primaryContainer.toARGB32().toRadixString(16),'onPrimaryContainer': c.onPrimaryContainer.toARGB32().toRadixString(16),'primaryFixed': c.primaryFixed.toARGB32().toRadixString(16),'primaryFixedDim': c.primaryFixedDim.toARGB32().toRadixString(16),'onPrimaryFixed': c.onPrimaryFixed.toARGB32().toRadixString(16),'onPrimaryFixedVariant': c.onPrimaryFixedVariant.toARGB32().toRadixString(16),'secondary': c.secondary.toARGB32().toRadixString(16),'onSecondary': c.onSecondary.toARGB32().toRadixString(16),'secondaryContainer': c.secondaryContainer.toARGB32().toRadixString(16),'onSecondaryContainer': c.onSecondaryContainer.toARGB32().toRadixString(16),'secondaryFixed': c.secondaryFixed.toARGB32().toRadixString(16),'secondaryFixedDim': c.secondaryFixedDim.toARGB32().toRadixString(16),'onSecondaryFixed': c.onSecondaryFixed.toARGB32().toRadixString(16),'onSecondaryFixedVariant': c.onSecondaryFixedVariant.toARGB32().toRadixString(16),'tertiary': c.tertiary.toARGB32().toRadixString(16),'onTertiary': c.onTertiary.toARGB32().toRadixString(16),'tertiaryContainer': c.tertiaryContainer.toARGB32().toRadixString(16),'onTertiaryContainer': c.onTertiaryContainer.toARGB32().toRadixString(16),'tertiaryFixed': c.tertiaryFixed.toARGB32().toRadixString(16),'tertiaryFixedDim': c.tertiaryFixedDim.toARGB32().toRadixString(16),'onTertiaryFixed': c.onTertiaryFixed.toARGB32().toRadixString(16),'onTertiaryFixedVariant': c.onTertiaryFixedVariant.toARGB32().toRadixString(16),'error': c.error.toARGB32().toRadixString(16),'onError': c.onError.toARGB32().toRadixString(16),'errorContainer': c.errorContainer.toARGB32().toRadixString(16),'onErrorContainer': c.onErrorContainer.toARGB32().toRadixString(16),'outline': c.outline.toARGB32().toRadixString(16),'onSurface': c.onSurface.toARGB32().toRadixString(16),'onSurfaceVariant': c.onSurfaceVariant.toARGB32().toRadixString(16),'inverseSurface': c.inverseSurface.toARGB32().toRadixString(16),'onInverseSurface': c.onInverseSurface.toARGB32().toRadixString(16),'inversePrimary': c.inversePrimary.toARGB32().toRadixString(16),'shadow': c.shadow.toARGB32().toRadixString(16),'scrim': c.scrim.toARGB32().toRadixString(16),'surface': c.surface.toARGB32().toRadixString(16),'surfaceBright': c.surfaceBright.toARGB32().toRadixString(16),'surfaceDim': c.surfaceDim.toARGB32().toRadixString(16),'surfaceContainerLowest': c.surfaceContainerLowest.toARGB32().toRadixString(16),'surfaceContainerLow': c.surfaceContainerLow.toARGB32().toRadixString(16),'surfaceContainer': c.surfaceContainer.toARGB32().toRadixString(16),'surfaceContainerHigh': c.surfaceContainerHigh.toARGB32().toRadixString(16),'surfaceContainerHighest': c.surfaceContainerHighest.toARGB32().toRadixString(16),'outlineVariant': c.outlineVariant.toARGB32().toRadixString(16)};\n}\nFile('colors.json').writeAsStringSync(jsonEncode(result));\n}); }\n")
    (tests / "glyph_export_test.dart").write_text("import 'dart:ui' as ui;\nimport 'dart:io';\nimport 'package:flutter/material.dart';\nimport 'package:flutter_test/flutter_test.dart';\nimport 'app_glyphs.dart';\nimport 'glyph_painter.dart';\nvoid main() { test('glyph atlas', () async {\nfinal glyphs = [AppGlyphs.dashboard,AppGlyphs.proxies,AppGlyphs.profiles,AppGlyphs.tools,AppGlyphs.sync,AppGlyphs.sort,AppGlyphs.addCircle,AppGlyphs.search,AppGlyphs.bolt,AppGlyphs.more,AppGlyphs.check,AppGlyphs.checkCircle,AppGlyphs.close,AppGlyphs.clearField,AppGlyphs.arrowBack,AppGlyphs.chevronBack,AppGlyphs.chevronForward,AppGlyphs.chevronDown,AppGlyphs.chevronUp,AppGlyphs.refresh,AppGlyphs.locate,AppGlyphs.edit,AppGlyphs.delete];\nfor(final filled in [false,true]) {\n final recorder=ui.PictureRecorder();final canvas=Canvas(recorder);\n canvas.drawColor(const Color(0xFFEEEEEE),BlendMode.src);\n for(var i=0;i<glyphs.length;i++) {\n canvas.save();canvas.translate((i%6)*120.0+24,(i~/6)*120.0+24);\n GlyphPainter(glyph:glyphs[i],fill:filled?1:0,color:const Color(0xFF007AFF)).paint(canvas,const Size(72,72));canvas.restore();\n }\n final pic=recorder.endRecording();final image=await pic.toImage(720,480);\n final bytes=await image.toByteData(format:ui.ImageByteFormat.png);\n File('glyphs-$filled.png').writeAsBytesSync(bytes!.buffer.asUint8List());\n image.dispose();pic.dispose();\n}\n}); }\n")
    (tests / "shapes_test.dart").write_text("import 'dart:convert';\nimport 'dart:io';\nimport 'package:flutter_test/flutter_test.dart';\nimport 'package:material_new_shapes/material_new_shapes.dart';\nvoid main() {test('shape coordinates', () {\nFile('shapes.json').writeAsStringSync(jsonEncode({\n'cookie7':MaterialShapes.cookie7Sided.cubics.map((c)=>c.points).toList(),\n'cookie4':MaterialShapes.cookie4Sided.cubics.map((c)=>c.points).toList()}));\n});}\n")
    shape_test = tests / "shapes_test.dart"
    shape_test.write_text(shape_test.read_text().replace("'cookie7':MaterialShapes.cookie7Sided", "'sunny':MaterialShapes.sunny.cubics.map((c)=>c.points).toList(),\n'flower':MaterialShapes.flower.cubics.map((c)=>c.points).toList(),\n'cookie7':MaterialShapes.cookie7Sided"))
    subprocess.run([args.flutter, "pub", "get", "--offline"], cwd=output, check=True)
    subprocess.run([args.flutter, "test", "--no-pub", "--reporter", "expanded"], cwd=output, check=True)
    # 作者 long：路径代码直接由同批Flutter曲线生成，避免手工抄坐标后空态外形逐渐偏离参考。
    shapes = json.loads((output / "shapes.json").read_text())
    lines = ["// Copyright (c) 2025 Agbama Gifted. MIT license: assets/licenses/material-new-shapes.txt.",
             "package com.lonnnnnng.flclash.ui", "", "import androidx.compose.ui.graphics.Path", "",
             "// 作者 long：由tool/export_flutter_visual_reference.py按锁定上游实际Bézier曲线生成，不手工修改。",
             "internal object ProfileIllustrationPaths {"]
    for name in ["cookie7", "cookie4", "sunny", "flower"]:
        cubics = shapes[name]
        lines.append(f"    val {name} by lazy {{ Path().apply {{")
        lines.append("        moveTo(" + ",".join(f"{v:.9f}f" for v in cubics[0][:2]) + ")")
        for cubic in cubics:
            lines.append("        cubicTo(" + ",".join(f"{v:.9f}f" for v in cubic[2:]) + ")")
        lines.extend(["        close()", "    } }"])
    lines.append("}")
    (output / "ProfileIllustrationPaths.kt").write_text("\n".join(lines) + "\n")
    (output / "provenance.json").write_text(json.dumps({"reference": REFERENCE, "seed": "FF007AFF", "style": "vivid", "material_color_utilities": "0.13.0", "material_new_shapes": "1.0.0", "glyphGrid": 24, "rasterScale": 3}, indent=2))
    print(output)

if __name__ == "__main__":
    main()
