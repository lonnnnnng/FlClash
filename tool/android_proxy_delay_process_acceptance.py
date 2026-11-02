#!/usr/bin/env python3
"""在指定 Redmi 上验证正式 Profile 的 Room 测速缓存跨应用进程恢复。"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid


ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parent
APP_ID = "com.lonnnnnng.flclash"
TEST_CLASS = "com.lonnnnnng.flclash.ui.ProxyDelayProcessPersistenceLiveTest"


def run(command: list[str], *, redactions: tuple[str, ...] = ()) -> str:
    result = subprocess.run(
        command,
        cwd=ROOT,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    output = result.stdout
    # 作者 long：授权订阅地址只作为运行参数传给设备，错误日志和终端输出均先脱敏。
    for value in redactions:
        output = output.replace(value, "[REDACTED]")
    if output:
        print(output, end="" if output.endswith("\n") else "\n")
    if result.returncode != 0:
        raise RuntimeError(f"命令失败，退出码 {result.returncode}: {command[0]}")
    return output


def adb(serial: str, *arguments: str) -> str:
    return run(["adb", "-s", serial, *arguments])


def authorized_url() -> str:
    policy = WORKSPACE.joinpath("AGENTS.md").read_text(encoding="utf-8")
    matches = re.findall(r"(?m)^\s*1\.\s+(https?://\S+)\s*$", policy)
    if len(matches) != 1:
        raise RuntimeError("外层 AGENTS.md 必须且只能列出一个编号为 1 的在线验收入口")
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


def instrumentation_runner(serial: str) -> str:
    listing = adb(serial, "shell", "pm", "list", "instrumentation")
    for line in listing.splitlines():
        if line.startswith("instrumentation:") and f"(target={APP_ID})" in line:
            return line.split("instrumentation:", 1)[1].split()[0]
    raise RuntimeError("未找到 KClash Debug instrumentation runner")


def run_phase(serial: str, runner: str, phase: str, run_id: str, url: str) -> None:
    method = "seedSuccessfulDelayBeforeProcessRestart" if phase == "seed" else "verifySuccessfulDelayAfterProcessRestart"
    command = [
        "adb", "-s", serial, "shell", "am", "instrument", "-w", "-r",
        "-e", "class", f"{TEST_CLASS}#{method}",
        "-e", "kclashDelayPhase", phase,
        "-e", "kclashDelayRunId", run_id,
    ]
    if phase == "seed":
        command.extend(["-e", "kclashAcceptanceUrl1", url])
    command.append(runner)
    output = run(command, redactions=(url,))
    if "OK (1 test)" not in output or "FAILURES!!!" in output:
        raise RuntimeError(f"instrumentation {phase} 阶段未通过")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"), required=not os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--skip-build", action="store_true")
    args = parser.parse_args()

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
    runner = instrumentation_runner(args.serial)
    run_id = uuid.uuid4().hex

    run_phase(args.serial, runner, "seed", run_id, url)
    adb(args.serial, "shell", "am", "force-stop", APP_ID)
    pid_check = subprocess.run(
        ["adb", "-s", args.serial, "shell", "pidof", APP_ID],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if pid_check.stdout.strip():
        raise RuntimeError("force-stop 后 KClash 进程仍存在，不能声称完成进程重建验收")
    run_phase(args.serial, runner, "verify", run_id, url)
    print("真实 Profile 的 Room 测速缓存跨应用进程恢复验收通过。")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError) as error:
        print(f"验收失败：{error}", file=sys.stderr)
        raise SystemExit(1)
