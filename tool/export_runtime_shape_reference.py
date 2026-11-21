#!/usr/bin/env python3
"""作者 long：固定Prism的Morph在独立Flutter测试中运行，仅导出曲线和栅格证据，不引入Flutter运行依赖。"""
import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--shapes', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    (output / 'test').mkdir()
    shutil.copytree(args.shapes, output / 'test/shapes')
    shutil.copyfile(root / 'tool/runtime_shape_oracle_test.dart', output / 'test/export_test.dart')
    (output / 'pubspec.yaml').write_text('name: runtime_shape_oracle\nenvironment:\n  sdk: ">=3.10.0 <4.0.0"\ndependencies:\n  flutter:\n    sdk: flutter\ndev_dependencies:\n  flutter_test:\n    sdk: flutter\n')
    subprocess.run(['flutter', 'pub', 'get', '--offline'], cwd=output, check=True)
    subprocess.run(['flutter', 'test', '--no-pub', '--reporter', 'expanded'], cwd=output, check=True)
    result = json.loads((output / 'morph.json').read_text())
    result['sourceSha256'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                             for p in sorted(args.shapes.glob('*.dart'))}
    result['scope'] = 'Fixed Prism circle to cookie9 matched cubics and Flutter raster; not full-page runtime parity.'
    fixture = root / 'android/tests/instrumented/resources/runtime-toggle-flutter-reference.json'
    fixture.write_text(json.dumps(result, separators=(',', ':')) + '\n')
    lines = ['// Copyright (c) 2025 Agbama Gifted. MIT license: assets/licenses/material-new-shapes.txt.',
             'package com.lonnnnnng.flclash.ui', '',
             '// 作者 long：由tool/export_runtime_shape_reference.py生成匹配曲线，勿手工修改；端点共享同一分段才能连续插值。',
             'internal object RuntimeToggleCurves {']
    for name in ('start', 'end'):
        lines.append(f'    val {name} = floatArrayOf(')
        lines.extend('        ' + ', '.join(f'{v:.9f}f' for v in cubic) + ',' for cubic in result[name])
        lines.append('    )')
    lines.append('}')
    (root / 'android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/RuntimeToggleCurves.kt').write_text('\n'.join(lines) + '\n')


if __name__ == '__main__':
    main()
