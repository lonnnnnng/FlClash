#!/usr/bin/env python3
"""作者 long：通过正式 ProfileRepository 恢复唯一授权远程配置与工作区原始 1.yaml。"""

import argparse
import base64
import hashlib
import json
import re
import subprocess
import tempfile
import uuid
from pathlib import Path


SERIAL = "wsvwypiz7xwslvl7"
TARGET_PACKAGE = "com.lonnnnnng.flclash"
TEST_PACKAGE = "com.lonnnnnng.flclash.test"
RUNNER = f"{TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner"
METHOD = (
    "com.lonnnnnng.flclash.ProfilePreparationInstrumentedTest#"
    "importsAuthorizedRemoteAndWorkspaceLocalProfilesWithoutStartingRuntime"
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=SERIAL)
    parser.add_argument("--log", type=Path, required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    workspace = repo.parent
    authorized = re.findall(
        r"^\s*-\s+(https?://\S+)\s*$", (workspace / "AGENTS.md").read_text(), re.M
    )
    if len(authorized) != 1:
        raise SystemExit("AGENTS.md 必须恰有一条当前授权远程来源；未操作设备。")
    config = (workspace / "1.yaml").read_bytes()
    config_sha = hashlib.sha256(config).hexdigest()
    if not config:
        raise SystemExit("工作区 1.yaml 为空；未操作设备。")

    adb = ["adb", "-s", args.serial]

    def read(*command):
        return subprocess.check_output(adb + list(command), text=True, stderr=subprocess.STDOUT).strip()

    def apk_hash(package, apk):
        local = repo / apk
        if not local.is_file():
            raise SystemExit(f"缺少当前构建 APK：{local}")
        locations = read("shell", "pm", "path", package).splitlines()
        if len(locations) != 1 or not locations[0].endswith("/base.apk"):
            raise SystemExit(f"设备上没有可核对的单 APK 安装：{package}")
        remote = locations[0].removeprefix("package:")
        installed = read("shell", "sha256sum", remote).split()[0]
        expected = hashlib.sha256(local.read_bytes()).hexdigest()
        if installed != expected:
            raise SystemExit(f"设备 APK 与本轮构建不同：{package}")

    model = read("shell", "getprop", "ro.product.model")
    abi = read("shell", "getprop", "ro.product.cpu.abi")
    api = read("shell", "getprop", "ro.build.version.sdk")
    if model != "Redmi Note 8 Pro" or abi != "arm64-v8a" or api != "36":
        raise SystemExit(f"设备不符合 Redmi 本轮验收基线：{model}, API {api}, {abi}")
    apk_hash(TARGET_PACKAGE, "android/app/build/outputs/apk/debug/app-debug.apk")
    apk_hash(TEST_PACKAGE, "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
    services = read("shell", "dumpsys", "activity", "services", TARGET_PACKAGE)
    if "ProxyService" in services or "VpnService" in services:
        raise SystemExit("检测到 KClash 代理或 VPN 服务；未开始配置导入。")

    remote = f"/data/local/tmp/kclash-profile-prepare-{uuid.uuid4().hex}.json"
    payload = {
        "authorizedUrl": authorized[0],
        "configBase64": base64.b64encode(config).decode("ascii"),
        "configSha256": config_sha,
    }
    command = [
        *adb, "shell", "am", "instrument", "-w", "-r",
        "-e", "kclashProfilePreparation", "true",
        "-e", "kclashPreparationInput", remote,
        "-e", "class", METHOD,
        RUNNER,
    ]
    args.log.parent.mkdir(parents=True, exist_ok=True)
    args.log.touch(mode=0o600, exist_ok=True)
    args.log.chmod(0o600)
    pushed = False
    try:
        with tempfile.TemporaryDirectory(prefix="kclash-profile-prepare-") as directory:
            source = Path(directory) / "input.json"
            source.write_text(json.dumps(payload), encoding="utf-8")
            source.chmod(0o600)
            subprocess.run([*adb, "push", str(source), remote], check=True, capture_output=True)
            pushed = True
            subprocess.run([*adb, "shell", "chmod", "600", remote], check=True)
            result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        safe_output = result.stdout.replace(authorized[0], "[REDACTED_SECRET]")
        args.log.write_text(safe_output + "\n", encoding="utf-8")
        passed = (
            result.returncode == 0
            and "OK (1 test)" in safe_output
            and "kclash_profile_prepare_result=" in safe_output
            and "FAILURES!!!" not in safe_output
        )
        if not passed:
            raise SystemExit(f"配置恢复用例未通过，已保留脱敏日志：{args.log}")
        marker = next(line.strip() for line in safe_output.splitlines() if "kclash_profile_prepare_result=" in line)
        print(f"设备 {args.serial}：{marker}")
        print(f"原始 1.yaml SHA-256：{config_sha}")
        print(f"脱敏日志：{args.log}")
    finally:
        if pushed:
            subprocess.run([*adb, "shell", "rm", "-f", remote], check=True)


if __name__ == "__main__":
    main()
