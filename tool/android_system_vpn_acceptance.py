#!/usr/bin/env python3
"""指定Redmi真实系统UI开启Always-on，验证无恢复标记冷启动与受控SIGKILL恢复；不需要蜂窝。"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = "com.lonnnnnng.flclash"
ACTIVITY = APP + "/.SystemVpnAcceptanceActivity"
RECEIVER = APP + "/.SystemVpnKillReceiver"
WINDOW = "/sdcard/Download/kclash-always-on-window.xml"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--verification-delay-seconds", type=int, choices=(0, 15), default=0,
                        help="15秒仅用于独立延迟诊断，不替代默认立即请求验收")
    args = parser.parse_args()
    assert args.serial == "wsvwypiz7xwslvl7"
    args.output.mkdir(parents=True, exist_ok=False)
    urls = re.findall(r"https://[^\s`<>]+", (ROOT.parent / "AGENTS.md").read_text())
    assert len(urls) == 1
    source_sha = hashlib.sha256(urls[0].encode()).hexdigest()
    token = uuid.uuid4().hex
    result = {"token": token, "serial": args.serial, "passed": False, "cellularTested": False,
              "verificationDelaySeconds": args.verification_delay_seconds,
              "acceptanceMode": "immediate" if args.verification_delay_seconds == 0 else "delayed-diagnostic",
              "diagnosticPassed": False}
    commands, events, observations = [], [], []
    incomplete_chunks = []
    completed = False

    def safe(value):
        return value.replace(urls[0], "[REDACTED_SECRET]")

    def adb(*parts, check=True, binary=False):
        proc = subprocess.run(["adb", "-s", args.serial, *parts], capture_output=True, text=not binary, timeout=35)
        if check and proc.returncode:
            raise RuntimeError("ADB失败：" + str(parts[:5]) + " exit=" + str(proc.returncode))
        return proc

    def text(*parts):
        return adb(*parts).stdout.strip()

    def pids():
        proc = adb("shell", "pidof", APP, check=False)
        assert proc.returncode in (0, 1) and not proc.stderr.strip()
        return [int(p) for p in proc.stdout.split()]

    def read_events():
        nonlocal events, incomplete_chunks
        raw = text("logcat", "-d", "-v", "raw", "-s", "KClashSystemVpnAcceptance:I", "*:S")
        events, chunks = [], {}
        for line in raw.splitlines():
            if not line.startswith("{"):
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                # 作者 long：历史其他轮次的截断行不属于本轮；本轮损坏证据必须失败，不能悄悄略过。
                assert token not in line, "本轮logcat证据被截断"
                continue
            if row.get("token") != token:
                continue
            if row.get("phase") == "evidence-chunk":
                assert 0 <= row["index"] < row["count"] <= 100
                group = chunks.setdefault(row["id"], {"count": row["count"], "parts": {}})
                assert group["count"] == row["count"]
                parts = group["parts"]
                assert row["index"] not in parts or parts[row["index"]] == row["data"]
                parts[row["index"]] = row["data"]
                if len(parts) != row["count"]:
                    continue
                row = json.loads(base64.b64decode("".join(parts[i] for i in range(row["count"])), validate=True))
                assert row["token"] == token
            events.append(row)
        incomplete_chunks = [{"id": key, "expected": group["count"], "received": sorted(group["parts"])}
                             for key, group in chunks.items() if len(group["parts"]) != group["count"]]
        (args.output / "evidence-integrity.json").write_text(json.dumps({"incompleteChunks": incomplete_chunks}, indent=2) + "\n")
        (args.output / "events.json").write_text(json.dumps(events, ensure_ascii=False, indent=2) + "\n")
        return events

    def phase(name, after=0, seconds=80):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            fresh = read_events()[after:]
            assert not any(e["phase"] == "failed" for e in fresh), "宿主失败，见events.json"
            names = (name, "already-restored") if name == "restored" else (name,)
            found = next((e for e in fresh if e["phase"] in names), None)
            if found:
                assert not incomplete_chunks, "本轮证据分片不完整"
                return found
            time.sleep(1)
        raise RuntimeError("等待验收阶段超时：" + name)

    def activity(command, pid=-1):
        proc = adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--ez", "acceptance", "true",
                   "--es", "token", token, "--es", "sourceSha256", source_sha, "--es", "command", command,
                   "--ei", "expectedPid", str(pid))
        commands.append({"command": command, "epochMs": round(time.time()*1000), "output": safe(proc.stdout + proc.stderr)})
        assert "Error:" not in proc.stdout + proc.stderr

    def window(name):
        adb("shell", "uiautomator", "dump", WINDOW)
        raw = text("exec-out", "cat", WINDOW)
        root = ET.fromstring(raw)
        assert all(n.get("package") == "com.android.settings" for n in root.iter("node"))
        (args.output / (name + ".xml")).write_text(raw)
        return root

    def screenshot(name):
        (args.output / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True).stdout)

    def tap(node):
        bounds = [int(v) for v in re.findall(r"\d+", node.get("bounds", ""))]
        assert len(bounds) == 4 and node.get("enabled") == "true"
        adb("shell", "input", "tap", str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))

    def settings_detail(name):
        adb("shell", "am", "start", "-W", "-a", "android.settings.VPN_SETTINGS")
        root = window(name + "-list")
        # 作者 long：系统可能复用尚未退出的详情Fragment；仅标题准确为KClash时允许沿用，不能猜测当前应用。
        if any(n.get("resource-id") == "com.android.settings:id/collapsing_toolbar" and n.get("content-desc") == "KClash" for n in root.iter("node")):
            assert any(n.get("text") == "始终开启的 VPN" for n in root.iter("node"))
            return root
        parents = {child: parent for parent in root.iter() for child in parent}
        title = [n for n in root.iter("node") if n.get("text") == "KClash"]
        assert len(title) == 1, "只允许准确定位KClash，不操作Flutter那一行"
        row = title[0]
        while not any(n.get("resource-id") == "com.android.settings:id/settings_button" for n in row.iter("node")):
            row = parents[row]
        gear = next(n for n in row.iter("node") if n.get("resource-id") == "com.android.settings:id/settings_button")
        tap(gear)
        return window(name + "-detail")

    def switch(root, label):
        parents = {child: parent for parent in root.iter() for child in parent}
        title = [n for n in root.iter("node") if n.get("text") == label]
        assert len(title) == 1
        row = title[0]
        while not any(n.get("checkable") == "true" for n in row.iter("node")):
            row = parents[row]
        checks = [n for n in row.iter("node") if n.get("checkable") == "true"]
        assert len(checks) == 1
        return checks[0]

    def system_state():
        return {"alwaysOn": text("shell", "settings", "get", "secure", "always_on_vpn_app"),
                "lockdown": text("shell", "settings", "get", "secure", "always_on_vpn_lockdown")}

    def disable_always_on():
        state = system_state()
        assert state["lockdown"] == "0", "不允许本轮自动调整lockdown"
        assert state["alwaysOn"] in ("null", APP), "不改其他应用Always-on"
        root = settings_detail("cleanup")
        toggle = switch(root, "始终开启的 VPN")
        if toggle.get("checked") == "true":
            tap(toggle)
        root = window("always-on-disabled")
        assert switch(root, "始终开启的 VPN").get("checked") == "false"
        assert system_state() == {"alwaysOn": "null", "lockdown": "0"}
        screenshot("always-on-disabled")

    def sample():
        before = pids()
        services = text("shell", "dumpsys", "activity", "services", APP)
        after = pids()
        with (args.output / "services.log").open("a") as f:
            f.write(json.dumps({"epochMs": round(time.time()*1000), "before": before, "after": after}) + "\n" + safe(services) + "\n")
        blocks = re.split(r"(?m)(?=^[ \t]*\* ServiceRecord\{)", services)
        observation = {"pidsBefore": before, "pids": after, "tun0": "tun0" in text("shell", "ls", "/sys/class/net").split()}
        for kind in ("Proxy", "Vpn"):
            block = next((b for b in blocks if re.search(r"ServiceRecord\{[^\n]*[./]" + kind + r"Service(?:[ \t}])", b.splitlines()[0] if b.splitlines() else "")), "")
            match = re.search(r"\bapp=ProcessRecord\{[^}\n]*?\b(\d+):" + re.escape(APP) + r"/", block)
            pid = int(match.group(1)) if match else None
            observation[kind + "Service"] = bool(block)
            observation[kind + "Foreground"] = "isForeground=true" in block and before == after == [pid]
            if kind == "Vpn":
                observation["vpnCreatedByAndroid"] = bool(re.search(r"ServiceRecord\{[^\n]*VpnService c:android[ } ]", block))
                observation["vpnSystemAction"] = "intent={act=android.net.VpnService pkg=" + APP + "}" in block
        return observation

    def observe_new(old_pid, name, started_at):
        while time.monotonic() - started_at < 90:
            observation = sample()
            observation.update({"stage": name, "secondsAfterTrigger": round(time.monotonic()-started_at, 3)})
            observations.append(observation)
            (args.output / "timeline.json").write_text(json.dumps(observations, indent=2) + "\n")
            if old_pid not in observation["pids"] and len(observation["pids"]) == 1 and observation["tun0"] and observation["ProxyForeground"] and observation["VpnForeground"]:
                return observation
            time.sleep(1)
        raise RuntimeError("系统未在90秒内独立恢复，不打开verify补启动")

    prepared = False
    stage = "preflight"
    try:
        assert text("shell", "getprop", "ro.product.model") == "Redmi Note 8 Pro"
        assert text("shell", "getprop", "ro.build.version.sdk") == "36"
        assert text("shell", "getprop", "ro.product.cpu.abi") == "arm64-v8a"
        assert text("shell", "settings", "get", "system", "font_scale") == "1.0"
        display = text("shell", "dumpsys", "window", "displays")
        assert "mRotation=0 " in display and " port " in display
        assert system_state() == {"alwaysOn": "null", "lockdown": "0"}
        apk = ROOT / "android/app/build/outputs/apk/debug/app-debug.apk"
        installed = text("shell", "pm", "path", APP).splitlines()[0].removeprefix("package:")
        result["installedAppSha256"] = text("shell", "sha256sum", installed).split()[0]
        assert result["installedAppSha256"] == hashlib.sha256(apk.read_bytes()).hexdigest()
        result["before"] = sample()
        assert not any(result["before"][k] for k in ("ProxyService", "VpnService", "tun0"))
        stage = "prepare"
        prepared = True
        activity("prepare")
        ready = phase("ready")
        result["ready"] = ready
        assert not ready["resume"] and not ready["vpnPreference"]
        root = settings_detail("before-enable")
        assert switch(root, "始终开启的 VPN").get("checked") == "false"
        assert switch(root, "屏蔽未使用 VPN 的所有连接").get("checked") == "false"
        screenshot("before-enable")
        stage = "kill-stopped-process"
        adb("shell", "am", "kill", APP)
        deadline = time.monotonic() + 15
        while pids() and time.monotonic() < deadline:
            time.sleep(0.5)
        assert not pids(), "开启系统设置前准备进程必须已真正消失"
        result["preparedPidGoneBeforeEnable"] = True
        stage = "enable-always-on"
        enabled_at = time.monotonic()
        tap(switch(root, "始终开启的 VPN"))
        enabled = window("always-on-enabled")
        assert switch(enabled, "始终开启的 VPN").get("checked") == "true"
        assert system_state() == {"alwaysOn": APP, "lockdown": "0"}
        screenshot("always-on-enabled")
        first = observe_new(ready["pid"], "cold-system-start", enabled_at)
        assert any(x["vpnCreatedByAndroid"] and x["vpnSystemAction"] for x in observations)
        result["coldObservedBeforeVerify"] = first
        time.sleep(args.verification_delay_seconds)
        count = len(read_events())
        activity("verify", first["pids"][0])
        stage = "cold-tun-verification"
        result["coldVerified"] = phase("verified", count, seconds=200)
        print(result["acceptanceMode"] + " COLD START / TUN HTTP / CORE ATTRIBUTION PASS", flush=True)
        stage = "sigkill-running-vpn"
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
        assert pids() == first["pids"]
        killed_at = time.monotonic()
        killed = adb("shell", "am", "broadcast", "-n", RECEIVER, "--es", "token", token,
                     "--ei", "expectedPid", str(first["pids"][0]))
        result["killCommand"] = safe(killed.stdout + killed.stderr)
        assert not any(e["phase"] == "kill-rejected" for e in read_events())
        second = observe_new(first["pids"][0], "running-vpn-sigkill", killed_at)
        assert any(e["phase"] == "kill-accepted" and e["pid"] == first["pids"][0] for e in read_events())
        result["recoveryObservedBeforeVerify"] = second
        time.sleep(args.verification_delay_seconds)
        count = len(read_events())
        activity("verify", second["pids"][0])
        stage = "recovery-tun-verification"
        result["recoveryVerified"] = phase("verified", count, seconds=200)
        stage = "disable-and-restore"
        disable_always_on()
        count = len(read_events())
        activity("restore")
        result["restored"] = phase("restored", count)
        result["after"] = sample()
        assert not any(result["after"][k] for k in ("ProxyService", "VpnService", "tun0"))
        completed = True
        result["passed"] = args.verification_delay_seconds == 0
        result["diagnosticPassed"] = args.verification_delay_seconds > 0
        print(result["acceptanceMode"] + " VPN SIGKILL / DATA / SYSTEM RESTORED PASS", flush=True)
    except Exception as failure:
        result["failure"] = {"stage": stage, "type": type(failure).__name__, "message": safe(str(failure))[:1000]}
        raise
    finally:
        try:
            if prepared and not completed:
                disable_always_on()
                count = len(read_events())
                activity("restore")
                result["cleanup"] = phase("restored", count)
            read_events()
            assert not incomplete_chunks, "本轮最终证据分片不完整"
            result["finalSystemState"] = system_state()
            assert result["finalSystemState"] == {"alwaysOn": "null", "lockdown": "0"}
        except Exception as failure:
            result["passed"] = False
            result["diagnosticPassed"] = False
            result["cleanupFailure"] = {"type": type(failure).__name__, "message": safe(str(failure))[:1000], "backupMayRemain": True}
            raise
        finally:
            (args.output / "commands.json").write_text(json.dumps(commands, ensure_ascii=False, indent=2) + "\n")
            (args.output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
