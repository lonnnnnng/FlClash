#!/usr/bin/env python3
"""作者 long：Provider运行态以SIGKILL验证系统自主恢复；仅准备前在无服务/无遗留备份时force-stop隔离Debug探针。"""

import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
COMPONENT_PACKAGE = "com.lonnnnnng.flclash"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", required=True, choices=["before", "after", "stopped-file", "stopped-row", "running-file", "running-row", "diagnostic-close", "diagnostic-timeout"])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--app", choices=[COMPONENT_PACKAGE, COMPONENT_PACKAGE + ".motion"], default=COMPONENT_PACKAGE)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    APP = args.app
    assert not args.case.startswith("diagnostic-") or APP.endswith(".motion"), "受控故障仅限独立诊断包"
    ACTIVITY = APP + "/" + COMPONENT_PACKAGE + ".ProviderRuntimeAcceptanceActivity"
    RECEIVER = APP + "/" + COMPONENT_PACKAGE + ".ProviderRuntimeKillReceiver"
    assert args.serial == "wsvwypiz7xwslvl7"
    args.output.mkdir(parents=True, exist_ok=False)
    urls = re.findall(r"https://[^\s`<>]+", (ROOT.parent / "AGENTS.md").read_text())
    assert len(urls) == 1
    source_hash = hashlib.sha256(urls[0].encode()).hexdigest()
    local_hash = hashlib.sha256((ROOT.parent / "1.yaml").read_bytes()).hexdigest()
    token = uuid.uuid4().hex
    timeline = []
    events = []
    commands = []
    result = {"token": token, "serial": args.serial, "app": APP, "passed": False}

    def safe(value):
        return value.replace(urls[0], "[REDACTED_SECRET]")

    def adb(*parts, check=True):
        proc = subprocess.run(["adb", "-s", args.serial, *parts], capture_output=True, text=True, timeout=30)
        if check and proc.returncode:
            raise RuntimeError("ADB失败：" + safe(" ".join(parts[:5])) + " exit=" + str(proc.returncode))
        return proc

    def text(*parts):
        return adb(*parts).stdout.strip()

    def pids():
        proc = adb("shell", "pidof", APP, check=False)
        assert proc.returncode in (0, 1) and not proc.stderr.strip()
        return [int(x) for x in proc.stdout.split()]

    def read_events():
        nonlocal events
        log = text("logcat", "-d", "-v", "raw", "-s", "KClashProviderRuntime:I", "*:S")
        values = []
        chunks = {}
        for line in log.splitlines():
            if line.startswith("{"):
                row = json.loads(line)
                if row.get("token") != token:
                    continue
                if row.get("phase") == "chunk":
                    key = (row["sourcePhase"], row["sha256"])
                    item = chunks.setdefault(key, {"meta": row, "parts": {}})
                    assert (item["meta"]["bytes"], item["meta"]["count"]) == (row["bytes"], row["count"])
                    item["parts"][row["index"]] = row["base64"]
                else:
                    values.append(row)
        for item in chunks.values():
            meta, parts = item["meta"], item["parts"]
            if sorted(parts) != list(range(meta["count"])):
                continue
            raw = base64.b64decode("".join(parts[i] for i in range(meta["count"])), validate=True)
            assert len(raw) == meta["bytes"] and hashlib.sha256(raw).hexdigest() == meta["sha256"]
            row = json.loads(raw)
            assert row["token"] == token and row["phase"] == meta["sourcePhase"]
            values.append(row)
        values.sort(key=lambda row: (row.get("elapsedMs", 0), row.get("epochMs", 0)))
        events = values
        (args.output / "events.json").write_text(json.dumps(events, ensure_ascii=False, indent=2) + "\n")
        return values

    def wait_phase(phase, seconds=100):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            values = read_events()
            if any(x["phase"] == "failed" for x in values):
                raise RuntimeError("验收宿主报告失败，见脱敏events.json")
            found = next((x for x in values if x["phase"] == phase), None)
            if found:
                return found
            time.sleep(1)
        raise RuntimeError("等待阶段超时：" + phase)

    def activity(command, expected_pid=-1):
        proc = adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--ez", "acceptance", "true",
                   "--es", "token", token, "--es", "command", command, "--es", "sourceSha256", source_hash,
                   "--es", "localSha256", local_hash, "--es", "case", args.case,
                   "--ei", "expectedPid", str(expected_pid), check=False)
        commands.append({"command": command, "epochMs": round(time.time() * 1000),
                         "exitCode": proc.returncode, "output": safe(proc.stdout + proc.stderr)})
        assert proc.returncode == 0 and "Error:" not in proc.stdout + proc.stderr

    def service_snapshot():
        before_pids = pids()
        services = text("shell", "dumpsys", "activity", "services", APP)
        after_pids = pids()
        names = text("shell", "ls", "/sys/class/net").split()
        with (args.output / "service-observations.log").open("a") as log:
            log.write(json.dumps({"epochMs": round(time.time() * 1000), "before": before_pids, "after": after_pids}) + "\n" + safe(services) + "\n")
        records = re.split(r"(?m)(?=^[ \t]*\* ServiceRecord\{)", services)
        proxy = next((block for block in records if re.search(r"ServiceRecord\{[^\n]*[./]ProxyService(?:[ \t}])", block.splitlines()[0] if block.splitlines() else "")), "")
        process = re.search(r"\bapp=ProcessRecord\{[^}\n]*?\b(\d+):" + re.escape(APP) + r"/", proxy)
        service_pid = int(process.group(1)) if process else None
        # 作者 long：同一ServiceRecord的前台标记和ProcessRecord必须绑定本次稳定PID采样，不能拼接旧记录与新进程。
        return {"pids": after_pids, "pidsBefore": before_pids, "stableSample": before_pids == after_pids,
                "servicePid": service_pid, "hasProxyService": bool(proxy),
                "isForeground": "isForeground=true" in proxy and before_pids == after_pids == [service_pid],
                "hasVpnService": bool(re.search(r"ServiceRecord\{[^\n]*[./]VpnService(?:[ \t}])", services)), "hasTun0": "tun0" in names}

    started = False
    stage = "preflight"
    try:
        assert text("shell", "getprop", "ro.product.model") == "Redmi Note 8 Pro"
        assert text("shell", "getprop", "ro.build.version.sdk") == "36"
        assert text("shell", "getprop", "ro.product.cpu.abi") == "arm64-v8a"
        assert text("shell", "settings", "get", "system", "font_scale") == "1.0"
        display = text("shell", "dumpsys", "window", "displays")
        assert "mRotation=0 " in display and " port " in display
        variant = "motionBenchmark" if APP.endswith(".motion") else "debug"
        apk = ROOT / f"android/app/build/outputs/apk/{variant}/app-{variant}.apk"
        expected_sha = hashlib.sha256(apk.read_bytes()).hexdigest()
        installed_path = text("shell", "pm", "path", APP).splitlines()[0].removeprefix("package:")
        installed_sha = text("shell", "sha256sum", installed_path).split()[0]
        assert installed_sha == expected_sha
        result["installedAppSha256"] = installed_sha
        before = service_snapshot()
        assert not before["hasProxyService"] and not before["hasVpnService"] and not before["hasTun0"]
        result["before"] = before
        activity("preflight")
        preflight = wait_phase("preflight", seconds=30)
        assert preflight["noBackup"] and preflight["stopped"]
        result["preflight"] = preflight
        isolation = service_snapshot()
        assert not isolation["hasProxyService"] and not isolation["hasVpnService"] and not isolation["hasTun0"]
        result["beforePreparationIsolation"] = isolation
        # 作者 long：上一场显式重试会留下进程内探针计数；此处仅隔离准备进程，不计入系统自主恢复证据。
        adb("shell", "am", "force-stop", APP)
        assert not pids()
        result["preparationIsolation"] = {"type": "force-stop-before-preparation", "noServices": True, "noBackup": True}
        stage = "prepare"
        started = True
        if args.case in ("before", "after"):
            activity("prepare")
            ready = wait_phase("ready")
            before_kill = service_snapshot()
            result["ready"] = ready
            result["beforeKill"] = before_kill
            assert before_kill["pids"] == [ready["pid"]]
            assert before_kill["hasProxyService"] and before_kill["isForeground"] and not before_kill["hasVpnService"] and not before_kill["hasTun0"], "未确认同一PID的前台ProxyService或发现VPN/TUN，禁止杀进程"
            # 作者 long：准备Activity已结束且旧PID仍存活；HOME只改变前台，不清恢复意图，不调用force-stop。
            adb("shell", "input", "keyevent", "KEYCODE_HOME")
            assert pids() == [ready["pid"]]
            stage = "kill"
            killed_at = time.monotonic()
            proc = adb("shell", "am", "broadcast", "-n", RECEIVER, "--es", "token", token,
                       "--ei", "expectedPid", str(ready["pid"]), check=False)
            result["killCommand"] = {"type": "external-broadcast-triggered-self-SIGKILL", "exitCode": proc.returncode,
                                     "output": safe(proc.stdout + proc.stderr)}
            assert proc.returncode == 0
            assert not any(x["phase"] == "kill-rejected" for x in read_events()), "杀进程门禁拒绝，本次不算进程死亡"
            deadline = killed_at + 90
            stage = "observe-system-restart"
            recovered = None
            while time.monotonic() < deadline:
                observation = service_snapshot()
                observation["secondsAfterKill"] = round(time.monotonic() - killed_at, 3)
                timeline.append(observation)
                (args.output / "timeline.json").write_text(json.dumps(timeline, indent=2) + "\n")
                if observation["secondsAfterKill"] <= 90 and observation["pids"] and ready["pid"] not in observation["pids"] and observation["hasProxyService"] and observation["isForeground"]:
                    assert not observation["hasVpnService"] and not observation["hasTun0"] and len(observation["pids"]) == 1
                    recovered = observation
                    break
                time.sleep(1)
            assert recovered is not None, "90秒内未独立观察到系统恢复，不能用verify启动应用补成通过"
            assert any(x["phase"] == "kill-accepted" and x["pid"] == ready["pid"] for x in read_events())
            result["systemObservedBeforeVerify"] = recovered
            print("SYSTEM RESTART OBSERVED before verify", flush=True)
            stage = "verify-and-restore"
            activity("verify", recovered["pids"][0])
            result["verified"] = wait_phase("verified")
            result["restored"] = wait_phase("restored")
            assert result["verified"]["pid"] == recovered["pids"][0]
            assert result["verified"]["restartAt"] > result["ready"]["readyAt"] == result["verified"]["readyAt"]
        else:
            activity("diagnostic-failure" if args.case.startswith("diagnostic-") else "broken-switch")
            result["verified"] = wait_phase("verified", seconds=180)
            result["restored"] = wait_phase("restored")
        after = service_snapshot()
        assert not after["hasProxyService"] and not after["hasVpnService"] and not after["hasTun0"]
        result["after"] = after
        result["passed"] = True
        print("REAL PID / HTTP / CORE ATTRIBUTION / FORMAL DATA RESTORED PASS", flush=True)
    except Exception as failure:
        result["failure"] = {"stage": stage, "type": type(failure).__name__, "message": safe(str(failure))[:2000]}
        raise
    finally:
        try:
            if started and not result["passed"]:
                # 作者 long：失败保留事件与进程时间线；只恢复本轮token对应快照，不回写旧正式配置。
                prior_events = read_events()
                held = next((x for x in prior_events if x["phase"] == "ready"), None)
                if held and pids() == [held["pid"]]:
                    # 作者 long：原VM仍停在不可取消门闩，恢复入口不能继续提交；先按同一token/PID中断，清理拉起不计系统恢复通过。
                    adb("shell", "am", "broadcast", "-n", RECEIVER, "--es", "token", token,
                        "--ei", "expectedPid", str(held["pid"]))
                    deadline = time.monotonic() + 15
                    while held["pid"] in pids():
                        assert time.monotonic() < deadline, "原准备PID仍存活，保留备份并停止清理"
                        time.sleep(.2)
                    result["cleanupInterruptedHeldProcess"] = True
                previous_count = len(read_events())
                activity("restore")
                deadline = time.monotonic() + 50
                result["cleanup"] = {"status": "timed-out"}
                while time.monotonic() < deadline:
                    fresh = read_events()[previous_count:]
                    restored = next((x for x in fresh if x["phase"] in ("restored", "already-restored")), None)
                    if restored:
                        result["cleanup"] = {"status": "restored", "event": restored}
                        break
                    if any(x["phase"] == "failed" for x in fresh):
                        result["cleanup"] = {"status": "failed", "backupMayRemain": True}
                        break
                    time.sleep(1)
            read_events()
        except Exception as failure:
            result["cleanup"] = {"status": "error", "type": type(failure).__name__, "message": safe(str(failure))[:2000], "backupMayRemain": True}
        finally:
            # 作者 long：恢复入口也失败时仍落盘原始失败结果，不能因收尾异常丢失审计。
            (args.output / "commands.json").write_text(json.dumps(commands, ensure_ascii=False, indent=2) + "\n")
            (args.output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
