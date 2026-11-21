import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;
import 'dart:ui' show Path, Offset;
import 'package:flutter/material.dart' show Matrix4;
import 'package:flutter_test/flutter_test.dart';
import 'shapes/shapes.dart';
void main() { test('fixed upstream runtime morph', () async {
  final morph=Morph(MaterialShapes.circle,MaterialShapes.cookie9Sided);
  final cases=<Object>[];
  for(final p in [0.0,.25,.5,.75,1.0,1.1]) {
    const size=220.0;
    final matrix=Matrix4.translationValues(size/2,size/2,0)..rotateZ(p*2*math.pi/9)..scaleByDouble(size,size,1,1)..translateByDouble(-.5,-.5,0,1);
    final path=morph.toPath(progress:p).transform(matrix.storage);
    final recorder=ui.PictureRecorder();final canvas=ui.Canvas(recorder);
    canvas.drawPath(path,ui.Paint()..color=const ui.Color(0xFFFFFFFF)..isAntiAlias=false);
    final picture=recorder.endRecording();final image=await picture.toImage(220,220);
    final data=(await image.toByteData(format:ui.ImageByteFormat.rawRgba))!;
    final spans=<Object>[];
    for(var y=0;y<220;y++) {var first=-1,last=-1;for(var x=0;x<220;x++) {if(data.getUint8((y*220+x)*4+3)>0){if(first<0)first=x;last=x;}}spans.add([first,last]);}
    cases.add({'progress':p,'size':220,'spans':spans,'profile':liquidGlassProfile(path,const Offset(size/2,size/2))});image.dispose();picture.dispose();
  }
  File('morph.json').writeAsStringSync(jsonEncode({'start':morph.asCubics(0).map((c)=>c.points).toList(),'end':morph.asCubics(1).map((c)=>c.points).toList(),'cases':cases}));
}); }

// 作者 long：以下采样函数原样保留固定Prism实现，独立导出玻璃轮廓参考。
const _profileTurns = 64;
List<double> liquidGlassProfile(Path outline, Offset center) {
  const step = 2 * math.pi / _profileTurns;
  final profile = List<double>.filled(_profileTurns, 0);
  void cross(Offset from, Offset to) {
    final start = from.direction;
    var span = to.direction - start;
    if (span > math.pi) {
      span -= 2 * math.pi;
    } else if (span <= -math.pi) {
      span += 2 * math.pi;
    }
    final edge = to - from;
    final first = (math.min(start, start + span) / step).ceil();
    final last = (math.max(start, start + span) / step).floor();
    for (var turn = first; turn <= last; turn++) {
      final ray = Offset.fromDirection(turn * step);
      final facing = ray.dx * edge.dy - ray.dy * edge.dx;
      profile[turn % _profileTurns] = facing.abs() < 1e-9
          ? math.max(from.distance, to.distance)
          : (from.dx * edge.dy - from.dy * edge.dx) / facing;
    }
  }

  for (final metric in outline.computeMetrics()) {
    final samples = math.max(_profileTurns * 4, metric.length.ceil());
    Offset pointAt(int index) =>
        metric.getTangentForOffset(metric.length * index / samples)!.position -
        center;
    var previous = pointAt(0);
    for (var index = 1; index <= samples; index++) {
      final next = pointAt(index);
      cross(previous, next);
      previous = next;
    }
  }
  return profile;
}
