#!/usr/bin/env python3
"""在指定 Redmi 上验证授权 Fallback 的固定选择跨应用冷进程恢复。"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid


ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parent
APP_ID = "com.lonnnnnng.flclash"
TEST_CLASS = "com.lonnnnnng.flclash.ui.FallbackColdProcessInstrumentedTest"


def run(command: list[str], *, redactions: tuple[str, ...] = ()) -> str:
    result = subprocess.run(command, cwd=ROOT, check=False, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    output = result.stdout
    for value in redactions:
        output = output.replace(value, "[REDACTED]")
    if output:
        print(output, end="" if output.endswith("\n") else "\n")
    if result.returncode != 0:
        raise RuntimeError(f"命令失败，退出码={result.returncode}: {command[0]}")
    return output


def adb(serial: str, *arguments: str) -> str:
    return run(["adb", "-s", serial, *arguments])


def authorized_url() -> str:
    policy = WORKSPACE.joinpath("AGENTS.md").read_text(encoding="utf-8")
    section = re.search(
        r"真机测试配置说明：真实在线验收使用以下链接:\s*(.*?)(?=\n\s*- |\n\s*本地配置验收|\Z)",
        policy,
        flags=re.S,
    )
    matches = re.findall(r"https?://[^\s)]+", section.group(1) if section else "")
    if len(matches) != 1:
        raise RuntimeError("外层 AGENTS.md 的真机在线验收说明必须且只能列出一个授权入口")
    return matches[0]


def verify_device(serial: str) -> None:
    devices = run(["adb", "devices", "-l"])
    if not any(line.startswith(f"{serial} ") and " device " in f" {line} " for line in devices.splitlines()):
        raise RuntimeError("指定 serial 当前未以 device 状态连接")
    values = {
        "model": adb(serial, "shell", "getprop", "ro.product.model").strip(),
        "api": adb(serial, "shell", "getprop", "ro.build.version.sdk").strip(),
        "abi": adb(serial, "shell", "getprop", "ro.product.cpu.abi").strip(),
    }
    if values != {"model": "Redmi Note 8 Pro", "api": "36", "abi": "arm64-v8a"}:
        raise RuntimeError(f"目标设备与项目验收边界不符: {values}")


def runner(serial: str) -> str:
    listing = adb(serial, "shell", "pm", "list", "instrumentation")
    for line in listing.splitlines():
        if line.startswith("instrumentation:") and f"(target={APP_ID})" in line:
            return line.split("instrumentation:", 1)[1].split()[0]
    raise RuntimeError("未找到 KClash Debug instrumentation runner")


def run_phase(serial: str, instrumentation: str, phase: str, url: str) -> str:
    command = [
        "adb", "-s", serial, "shell", "am", "instrument", "-w", "-r",
        "-e", "class", f"{TEST_CLASS}#{'prepareFallbackFixedSelectionForColdProcess' if phase == 'prepare' else 'verifyFallbackFixedSelectionAfterColdProcessAndClear'}",
        "-e", "kclashFallbackColdAcceptance", "true",
        "-e", "kclashFallbackColdPhase", phase,
        instrumentation,
    ]
    return run(command, redactions=(url,))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"), required=not os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--output", type=Path, default=WORKSPACE / "acceptance-artifacts" / "fallback-cold-process-20261106")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    url = authorized_url()
    verify_device(args.serial)
    if not args.skip_build:
        run(["./gradlew", ":app:assembleDebug", ":app:assembleDebugAndroidTest", "--no-daemon", "-Ptarget-platform=android-arm64"])
    app_apk = ROOT / "android/app/build/outputs/apk/debug/app-debug.apk"
    test_apk = ROOT / "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if not app_apk.is_file() or not test_apk.is_file():
        raise RuntimeError("缺少 Debug 或 AndroidTest APK")
    adb(args.serial, "install", "-r", str(app_apk))
    adb(args.serial, "install", "-r", "-t", str(test_apk))
    instrumentation = runner(args.serial)
    device_input = Path(f"/tmp/kclash-fallback-cold-authorized-{uuid.uuid4().hex}.json")
    device_input.write_text(json.dumps({"url": url}, ensure_ascii=False) + "\n", encoding="utf-8")
    try:
        adb(args.serial, "push", str(device_input), "/data/local/tmp/kclash-fallback-cold-authorized.json")
        prepare = run_phase(args.serial, instrumentation, "prepare", url)
        (args.output / "prepare.log").write_text(prepare, encoding="utf-8")
        # 作者 long：只杀应用进程，保留 marker 和 Room，让 verify 在新 PID 中重新加载核心与选择。
        adb(args.serial, "shell", "am", "force-stop", APP_ID)
        after = subprocess.run(["adb", "-s", args.serial, "shell", "pidof", APP_ID], check=False, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if after.stdout.strip():
            raise RuntimeError("force-stop 后 KClash 应用进程仍存在")
        verify = run_phase(args.serial, instrumentation, "verify", url)
        (args.output / "verify.log").write_text(verify, encoding="utf-8")
        if "OK (1 test)" not in verify or "FAILURES!!!" in verify:
            raise RuntimeError("verify instrumentation 未通过")
        final = {
            "serial": args.serial,
            "device": {"model": "Redmi Note 8 Pro", "api": 36, "abi": "arm64-v8a"},
            "test": TEST_CLASS,
            "phases": ["prepare", "force-stop", "verify"],
            "result": "passed",
            "logs": ["prepare.log", "verify.log"],
        }
        (args.output / "result.json").write_text(json.dumps(final, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("Fallback 冷进程恢复验收通过。")
        return 0
    finally:
        run(["adb", "-s", args.serial, "shell", "rm", "-f", "/data/local/tmp/kclash-fallback-cold-authorized.json"], redactions=(url,))
        device_input.unlink(missing_ok=True)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError) as error:
        print(f"验收失败：{error}", file=sys.stderr)
        raise SystemExit(1)
