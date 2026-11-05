#!/usr/bin/env python3
"""使用已安装的最终Debug/Test包，分阶段验证IP质量缓存真实跨进程回读。"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
APP = "com.lonnnnnng.flclash"
CLASS = "com.lonnnnnng.flclash.data.IpQualityNetworkInstrumentedTest"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.serial != "wsvwypiz7xwslvl7":
        raise RuntimeError("本组只允许指定Redmi")
    args.output.mkdir(parents=True, exist_ok=False)

    def adb(*parts, check=True):
        return subprocess.run(["adb", "-s", args.serial, *parts], check=check,
                              capture_output=True, text=True)

    assert adb("shell", "getprop", "ro.product.model").stdout.strip() == "Redmi Note 8 Pro"
    assert adb("shell", "getprop", "ro.product.cpu.abi").stdout.strip() == "arm64-v8a"
    urls = re.findall(r"https://[^\s`<>]+", (ROOT.parent / "AGENTS.md").read_text())
    assert len(urls) == 1
    source_hash = hashlib.sha256(urls[0].encode()).hexdigest()
    rows = []

    def phase(name, method):
        started = time.monotonic()
        result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", CLASS + "#" + method,
                     "-e", "kclashIpQualityAcceptance", "true", "-e", "kclashAuthorizedSourceSha256", source_hash,
                     APP + ".test/androidx.test.runner.AndroidJUnitRunner", check=False)
        output = (result.stdout + result.stderr).replace(urls[0], "[REDACTED_SECRET]")
        (args.output / (name + ".log")).write_text(output)
        passed = result.returncode == 0 and "OK (1 test)" in output and "FAILURES!!!" not in output
        rows.append({"phase": name, "method": method, "passed": passed, "seconds": round(time.monotonic() - started, 3)})
        (args.output / "result.json").write_text(json.dumps(rows, indent=2) + "\n")
        print(name, "PASS" if passed else "FAIL", flush=True)
        if not passed:
            raise RuntimeError("分阶段验收失败，先保留日志并按精确restore方法收尾")
        values = re.findall(r"INSTRUMENTATION_STATUS: kclash_ip_quality=(.*)", output)
        return [json.loads(value) for value in values]

    prepared = phase("prepare", "prepareSuccessfulQualityForRealProcessRestart")
    prepared_pid = next(row["pid"] for row in prepared if row.get("phase") == "cold-prepared")
    current = adb("shell", "pidof", APP, check=False)
    assert not current.stderr.strip() and current.returncode in (0, 1)
    assert current.stdout.strip() in ("", str(prepared_pid))
    # 作者 long：外部确认旧进程确实消失，不能用重建Repository或重新打开页面代替冷进程。
    adb("shell", "am", "force-stop", APP)
    absent = adb("shell", "pidof", APP, check=False)
    assert absent.returncode == 1 and not absent.stdout.strip() and not absent.stderr.strip()
    adb("get-state")
    (args.output / "process-boundary.json").write_text(json.dumps({
        "preparedPid": prepared_pid, "pidBeforeForceStop": current.stdout.strip(),
        "forceStopConfirmedAbsent": True, "pidofExitCode": absent.returncode,
    }, indent=2) + "\n")
    restored = phase("verify", "verifyDifferentProcessReadsOriginalQualityWithoutNetwork")
    value = next(row for row in restored if row.get("phase") == "cold-restored")
    assert value["pid"] != prepared_pid and value["metadataCallsOnRefusingRuntime"] == 0
    print("真实不同PID、正式Room、原时间与零联网回读已核对。")


if __name__ == "__main__":
    main()
