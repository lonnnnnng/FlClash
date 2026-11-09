#!/usr/bin/env python3
"""作者 long：检查最终APK的ML Kit反射入口，避免只通过Debug测试却交付无法创建扫码器的Release。"""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
from xml.etree import ElementTree


def check_apk(apk: Path, analyzer: str) -> dict:
    def inspect(*args: str) -> str:
        return subprocess.check_output([analyzer, *args, str(apk)], text=True)

    android_name = "{http://schemas.android.com/apk/res/android}name"
    android_value = "{http://schemas.android.com/apk/res/android}value"
    manifest = ElementTree.fromstring(inspect("manifest", "print"))
    service = next((node for node in manifest.iter("service") if node.get(android_name) ==
                    "com.google.mlkit.common.internal.MlKitComponentDiscoveryService"), None)
    errors = []
    registrars = []
    if service is not None:
        for metadata in service.findall("meta-data"):
            name = metadata.get(android_name, "")
            if name.startswith("com.google.firebase.components:") and metadata.get(android_value) == \
                    "com.google.firebase.components.ComponentRegistrar":
                registrars.append(name.split(":", 1)[1])
    # 作者 long：扫码依赖声明丢失也必须失败，不能让空清单或仅保留通用注册器通过检查。
    required = {
        "com.google.mlkit.common.internal.CommonComponentRegistrar",
        "com.google.mlkit.vision.common.internal.VisionCommonRegistrar",
        "com.google.mlkit.vision.barcode.internal.BarcodeRegistrar",
    }
    for name in sorted(required - set(registrars)):
        errors.append(f"清单缺少扫码注册器：{name}")
    if not any(node.get(android_name) == "com.google.mlkit.common.internal.MlKitInitProvider"
               for node in manifest.iter("provider")):
        errors.append("清单缺少MlKitInitProvider")
    checks = []
    for name in sorted(set(registrars)):
        # 作者 long：直接读取压缩后的DEX，mapping保留类名不代表反射需要的构造器仍存在。
        code = inspect("dex", "code", "--class", name)
        public_constructor = bool(re.search(r"^\.method public constructor <init>\(\)V\s*$", code, re.MULTILINE))
        concrete = not bool(re.search(r"^\.class\b[^\n]*\babstract\b", code, re.MULTILINE))
        checks.append({"class": name, "publicNoArgConstructor": public_constructor, "concrete": concrete})
        if not public_constructor or not concrete:
            errors.append(f"注册器无法通过公共无参构造器实例化：{name}")
    return {"apk": str(apk), "registrars": checks, "errors": errors, "passed": not errors}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--apkanalyzer", default="apkanalyzer")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = check_apk(args.apk, args.apkanalyzer)
    rendered = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(rendered)
    print(rendered, end="")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
