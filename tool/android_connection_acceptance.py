#!/usr/bin/env python3
"""作者 long：只用工作区原始 1.yaml 和 AGENTS.md 授权清单准备显式真机验收，不把秘密放进命令或报告。"""

import argparse
import base64
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path
from urllib.parse import parse_qsl, urlsplit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--target-index", type=int, choices=(1, 2), default=2)
    parser.add_argument("--log", type=Path, required=True)
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[1]
    workspace = repository.parent
    # 作者 long：输入来源固定，避免测试脚本接受任意 YAML/地址而绕开项目授权边界。
    targets = re.findall(r"^\d+\.\s+(https?://\S+)\s*$", (workspace / "AGENTS.md").read_text(), re.M)
    if len(targets) != 2:
        raise SystemExit("授权清单必须恰好包含两条路径；未执行设备操作。")
    config = (workspace / "1.yaml").read_bytes()
    if not config:
        raise SystemExit("原始 1.yaml 为空；未执行设备操作。")
    adb = ["adb", "-s", args.serial]

    def read(*command):
        return subprocess.check_output(adb + list(command), text=True).strip()

    model = read("shell", "getprop", "ro.product.model")
    abi = read("shell", "getprop", "ro.product.cpu.abi")
    api = read("shell", "getprop", "ro.build.version.sdk")
    if abi != "arm64-v8a":
        raise SystemExit("目标设备不是 arm64-v8a；未安装或执行测试。")
    print(f"设备 {args.serial}: {model}, API {api}, {abi}")
    # 作者 long：脚本只验收刚构建并安装的同一对 APK，避免旧测试包的通过记录冒充当前实现。
    for package, local in (
        ("com.lonnnnnng.flclash", repository / "android/app/build/outputs/apk/debug/app-debug.apk"),
        ("com.lonnnnnng.flclash.test", repository / "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"),
    ):
        if not local.is_file():
            raise SystemExit(f"请先构建并安装当前 APK：{local}")
        locations = read("shell", "pm", "path", package).splitlines()
        if len(locations) != 1 or not locations[0].startswith("package:/data/app/") or not locations[0].endswith("/base.apk"):
            raise SystemExit(f"未找到可核验的单 APK 安装：{package}")
        installed_hash = read("shell", "sha256sum", locations[0].removeprefix("package:")).split()[0]
        local_hash = hashlib.sha256(local.read_bytes()).hexdigest()
        if installed_hash != local_hash:
            raise SystemExit(f"设备 APK 与当前构建不同，请先安装：{package}")
        print(f"已核对 {package} sha256={local_hash}")
    payload = {
        "target": targets[args.target_index - 1],
        "configBase64": base64.b64encode(config).decode(),
        "configSha256": hashlib.sha256(config).hexdigest(),
    }
    remote = "/data/local/tmp/kclash-live-input.json"
    notification_granted = False
    permission = "android.permission.POST_NOTIFICATIONS"
    package = "com.lonnnnnng.flclash"
    permission_before = re.search(
        r"android\.permission\.POST_NOTIFICATIONS: granted=(true|false)", read("shell", "dumpsys", "package", package)
    )
    if int(api) >= 33 and permission_before is None:
        raise SystemExit("无法确定通知权限原状态；未执行测试。")
    args.log.parent.mkdir(parents=True, exist_ok=True)
    try:
        if int(api) >= 33 and permission_before.group(1) == "false":
            subprocess.run(adb + ["shell", "pm", "grant", package, permission], check=True)
            notification_granted = True
            print("本轮临时授予通知权限，结束后恢复原拒绝状态。")
        with tempfile.TemporaryDirectory(prefix="kclash-live-") as directory:
            source = Path(directory) / "input.json"
            source.write_text(json.dumps(payload))
            source.chmod(0o600)
            subprocess.run(adb + ["push", str(source), remote], check=True, capture_output=True)
            subprocess.run(adb + ["shell", "chmod", "600", remote], check=True)
            args.log.touch(mode=0o600, exist_ok=True)
            args.log.chmod(0o600)
            with args.log.open("w") as output:
                subprocess.run(adb + [
                    "shell", "am", "instrument", "-w", "-r", "-e", "kclashLiveAcceptance", "true",
                    "-e", "class", "com.lonnnnnng.flclash.ConnectionLiveAcceptanceTest",
                    "com.lonnnnnng.flclash.test/androidx.test.runner.AndroidJUnitRunner",
                ], stdout=output, stderr=subprocess.STDOUT, check=True)
        result = args.log.read_text()
        if "OK (1 test)" not in result or "kclash_live_result=real-core-detail-copy-recreate-stop-passed" not in result:
            raise SystemExit(f"真实链路未通过；保留失败证据 {args.log}")
        print(f"授权路径 #{args.target_index} 真实连接详情通过，日志：{args.log}")
    finally:
        if args.log.is_file():
            # 作者 long：即使测试异常也遮蔽授权链接及其中的标识，原始订阅正文从不读入测试日志。
            log_text = args.log.read_text()
            for target in targets:
                log_text = log_text.replace(target, "[REDACTED_SECRET]")
                parsed = urlsplit(target)
                secrets = [value for _, value in parse_qsl(parsed.query)]
                secrets += re.findall(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", parsed.path)
                for secret in secrets:
                    if secret:
                        log_text = log_text.replace(secret, "[REDACTED_SECRET]")
            args.log.write_text(log_text)
        # 作者 long：只删除本脚本固定创建的临时输入，原始 1.yaml 和用户配置不在清理范围。
        try:
            subprocess.run(adb + ["shell", "rm", "-f", remote], check=True)
        finally:
            if notification_granted:
                subprocess.run(adb + ["shell", "pm", "revoke", package, permission], check=True)
                print("已恢复本轮临时通知权限。")


if __name__ == "__main__":
    main()
