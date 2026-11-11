#!/usr/bin/env python3
"""Redmi-only sequential first-request check for the six locally authorized proxy nodes."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import subprocess
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parent
DEFAULT_ARTIFACTS = WORKSPACE / "acceptance-artifacts" / "endpoint-batch-20261111"
SERIAL = "wsvwypiz7xwslvl7"
PACKAGE = "com.lonnnnnng.flclash.motion"
TEST_PACKAGE = f"{PACKAGE}.test"
TEST_RUNNER = f"{TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner"
PREPARATION_TEST = "com.lonnnnnng.flclash.StabilityWindowPreparationTest#loadsUpdatedAuthorizedLocalWithoutReplacingExistingProfiles"


def adb(*parts: str, timeout: int = 30) -> bytes:
    return subprocess.check_output(["adb", "-s", SERIAL, *parts], timeout=timeout)


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def assert_target() -> None:
    model = adb("shell", "getprop", "ro.product.model").decode().strip()
    assert model == "Redmi Note 8 Pro", f"unexpected device model: {model}"


def assert_services_stopped() -> None:
    for package in ("com.lonnnnnng.flclash", PACKAGE):
        services = adb("shell", "dumpsys", "activity", "services", package).decode(errors="replace")
        assert "ProxyService" not in services and "VpnService" not in services, f"active service in {package}"


def install_and_verify(artifacts: Path) -> None:
    assert_target()
    assert_services_stopped()
    apks = (
        (PACKAGE, ROOT / "android/app/build/outputs/apk/motionBenchmark/app-motionBenchmark.apk"),
        (TEST_PACKAGE, ROOT / "android/app/build/outputs/apk/androidTest/motionBenchmark/app-motionBenchmark-androidTest.apk"),
    )
    results = []
    for package, apk in apks:
        raw = apk.read_bytes()
        result = subprocess.run(["adb", "-s", SERIAL, "install", "-r", str(apk)], capture_output=True, text=True, timeout=90)
        assert result.returncode == 0 and "Success" in result.stdout, f"install failed for {package}"
        remote = adb("shell", "pm", "path", package).decode().strip().removeprefix("package:")
        assert sha256(adb("exec-out", "cat", remote)) == sha256(raw), f"APK readback mismatch for {package}"
        results.append({"package": package, "sha256": sha256(raw), "bytes": len(raw), "readbackMatches": True})
    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
    (artifacts / "installation.json").write_text(json.dumps(results, indent=2) + "\n")


def report_items(artifacts: Path, token: str, command_prefix: str) -> list[dict]:
    raw = adb("logcat", "-d", "-v", "raw", "-s", "KClashStability:I", "*:S", timeout=45).decode(errors="replace")
    groups: dict[tuple[str, str], dict] = {}
    for line in raw.splitlines():
        try:
            item = json.loads(line)
        except ValueError:
            continue
        name = item.get("command", "")
        if item.get("token") != token or not (name == command_prefix or name.startswith(command_prefix + "-")):
            continue
        key = (name, item.get("sha256", ""))
        if item.get("transport") == "verified-file-v1":
            groups[key] = {"meta": item, "parts": {}}
            continue
        group = groups.setdefault(key, {"meta": item, "parts": {}})
        assert (group["meta"].get("count"), group["meta"].get("bytes")) == (item.get("count"), item.get("bytes"))
        group["parts"][item["index"]] = item["base64"]

    result = []
    for group in groups.values():
        meta = group["meta"]
        if meta.get("transport") == "verified-file-v1":
            assert re.fullmatch(r"[a-z0-9-]+", meta["command"])
            assert re.fullmatch(r"[a-f0-9]{64}", meta["sha256"])
            expected = f"/sdcard/Android/data/{PACKAGE}/files/stability-reports/{token}-{meta['command']}-{meta['sha256']}.json"
            assert meta["path"] in (expected, expected.replace("/sdcard/", "/storage/emulated/0/", 1))
            remote = meta["path"]
            # 作者 long：外部存储的回执刚写完就可能短暂读到旧长度；只对本轮 token 的精确文件做有限重读，避免把传输竞态误记成业务失败。
            data = b""
            for _ in range(20):
                data = adb("exec-out", "cat", remote, timeout=45)
                if len(data) == meta["bytes"] and sha256(data) == meta["sha256"]:
                    break
                time.sleep(0.5)
            assert len(data) == meta["bytes"] and sha256(data) == meta["sha256"]
            local = artifacts / f"{meta['command']}-{meta['sha256']}-transport.json"
            local.write_bytes(data)
            assert sha256(adb("exec-out", "cat", remote, timeout=45)) == meta["sha256"]
            adb("shell", "rm", "--", remote)
            result.append(json.loads(data))
            continue
        parts = group["parts"]
        if sorted(parts) != list(range(meta["count"])):
            continue
        data = base64.b64decode("".join(parts[index] for index in range(meta["count"])), validate=True)
        assert len(data) == meta["bytes"] and sha256(data) == meta["sha256"]
        result.append(json.loads(data))
    return result


def run_command(artifacts: Path, token: str, name: str) -> dict:
    assert re.fullmatch(r"[a-z0-9-]+", name)
    adb(
        "shell", "am", "start", "-W", "-n",
        f"{PACKAGE}/com.lonnnnnng.flclash.StabilityWindowAcceptanceActivity",
        "--es", "token", token, "--es", "command", name,
        timeout=45,
    )
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        items = report_items(artifacts, token, name)
        (artifacts / f"{name}-events.json").write_text(json.dumps(items, indent=2) + "\n")
        found = [item for item in items if item.get("command") == name]
        if found:
            result = found[-1]
            (artifacts / f"{name}.json").write_text(json.dumps(result, indent=2) + "\n")
            network = result.get("network", {})
            print(json.dumps({
                "command": name,
                "passed": result.get("passed"),
                "failedStep": result.get("failedStep"),
                "networkPassed": network.get("passed"),
                "failureType": network.get("failureType"),
                "endpointLeaf": result.get("endpointLeaf"),
            }, ensure_ascii=False), flush=True)
            return result
        time.sleep(0.5)
    raise TimeoutError(f"no complete acceptance report for {name}")


def prepare(artifacts: Path, previous_run: Path) -> None:
    assert_target()
    # 作者 long：授权正文只从工作区传到专用 Redmi 的临时输入，测试不通过公网发送配置内容。
    current = (WORKSPACE / "1.yaml").read_bytes()
    previous = json.loads(previous_run.read_text())
    root_agents = (WORKSPACE / "AGENTS.md").read_text()
    urls = [value.rstrip(")]") for value in re.findall(r"https://[^\s`<>]+", root_agents)]
    assert len(urls) == 1, "expected exactly one user-authorized live HTTPS target"
    config_hash = sha256(current)
    previous_hash = previous.get("localSha256")
    assert previous_hash and previous_hash != config_hash, "the updated local source must differ from the previous acceptance source"
    token = uuid.uuid4().hex
    source = {
        "token": token,
        "localSha256": config_hash,
        "previousLocalSha256": previous_hash,
        "configBase64": base64.b64encode(current).decode(),
        "authorizedUrl": urls[0],
    }
    remote = f"/data/local/tmp/kclash-stability-{token}.json"
    local = artifacts / f"input-{token}.json"
    local.write_text(json.dumps(source, separators=(",", ":")))
    run = {
        "token": token,
        "localSha256": config_hash,
        "localBytes": len(current),
        "previousLocalSha256": previous_hash,
        "authorizedUrlSha256": sha256(urls[0].encode()),
        "remoteInput": remote,
    }
    (artifacts / "run.json").write_text(json.dumps(run, indent=2) + "\n")
    try:
        push = subprocess.run(["adb", "-s", SERIAL, "push", str(local), remote], capture_output=True, text=True, timeout=60)
        assert push.returncode == 0
        remote_hash = adb("shell", "sha256sum", remote).decode().split()[0]
        assert remote_hash == sha256(local.read_bytes())
        test = subprocess.run([
            "adb", "-s", SERIAL, "shell", "am", "instrument", "-w", "-r",
            "-e", "class", PREPARATION_TEST, "-e", "kclashStabilityInput", remote, TEST_RUNNER,
        ], capture_output=True, text=True, timeout=120)
        (artifacts / "prepare.log").write_text(test.stdout + test.stderr)
        assert test.returncode == 0 and "OK (1 test)" in test.stdout, "authorized local import test failed"
        assert adb("shell", "sha256sum", remote).decode().split()[0] == remote_hash
        print(json.dumps({"prepared": True, "localBytes": len(current), "localSha256": config_hash,
                          "previousSourcePreserved": True}, ensure_ascii=False), flush=True)
    finally:
        if local.exists():
            local.unlink()
        subprocess.run(["adb", "-s", SERIAL, "shell", "rm", "--", remote], capture_output=True, timeout=30)


def node_network_result(result: dict, expected_endpoint: str, expected_node: str, node_index: int, attempt: int) -> dict:
    network = result.get("network", {})
    diagnostics = network.get("dialDiagnostic", [])
    actual = [item.get("dialEndpointHash") for item in diagnostics
              if item.get("kind") == "node-selected" and item.get("dialEndpointHash")]
    lookups = [item for item in diagnostics if item.get("kind") == "lookup"]
    leaf = result.get("endpointLeaf", {})
    stages = [{key: item.get(key) for key in ("protocolStage", "protocolState", "streamBytes", "expectedBytes", "errorCode") if key in item}
              for item in diagnostics if item.get("kind") == "protocol"]
    return {
        "nodeIndex": node_index,
        "attempt": attempt,
        "nodeHash": expected_node,
        "endpointHash": expected_endpoint,
        "endpointPinned": bool(actual) and set(actual) == {expected_endpoint},
        "leafPinned": leaf.get("nodeHash") == expected_node and leaf.get("leafFixed") is True,
        "lookupCount": len(lookups),
        "lastLookupAddressCount": lookups[-1].get("addresses") if lookups else None,
        "passed": network.get("passed") is True,
        "failedStep": result.get("failedStep"),
        "failureType": network.get("failureType"),
        "httpStatus": network.get("http"),
        "protocolStages": stages,
    }


def compare_restoration(before: dict, restored: dict, temporary_profile_sha: str) -> dict:
    left = before.get("snapshot", {})
    right = restored.get("snapshot", {})
    temporary_ids = {str(item.get("id")) for item in left.get("files", []) if item.get("sha256") == temporary_profile_sha}
    if len(temporary_ids) != 1:
        raise AssertionError("could not identify the unique temporary local profile")
    def files(snapshot: dict) -> list[dict]:
        return [{key: item.get(key) for key in ("id", "bytes", "mtime", "sha256")}
                for item in snapshot.get("files", []) if str(item.get("id")) not in temporary_ids]
    original_rows = {key: value for key, value in left.get("rows", {}).items() if key not in temporary_ids}
    return {
        "profileRowsRestored": original_rows == right.get("rows"),
        "profileFilesRestored": files(left) == files(right),
        "roomPreferencesRestored": left.get("roomPreferences") == right.get("roomPreferences"),
        "sharedPreferencesRestored": left.get("sharedPreferences") == right.get("sharedPreferences"),
        "selectionRestored": left.get("selected") == right.get("selected"),
        "runtimeStopped": right.get("runtime") == "STOPPED",
        "temporaryProfileDeleted": restored.get("endpointControl", {}).get("temporaryProfileDeleted") is True,
        "restorePassed": restored.get("passed") is True and restored.get("endpointControl", {}).get("backupCleared") is True,
    }


def run_batch(artifacts: Path) -> None:
    run = json.loads((artifacts / "run.json").read_text())
    token = run["token"]
    before = run_command(artifacts, token, "snapshot-endpoint-nodes-before")
    assert before.get("passed") is True and before.get("snapshot", {}).get("runtime") == "STOPPED"
    results: list[dict] = []
    setup: dict = {}
    prepare_attempted = False
    restore_attempted = False
    try:
        prepare_attempted = True
        prepare_result = run_command(artifacts, token, "endpoint-prepare-0")
        assert prepare_result.get("passed") is True
        setup = prepare_result.get("endpointControl", {})
        assert setup.get("backupPersisted") is True
        assert setup.get("proxyCount") == 6
        assert setup.get("protocolCounts") == {"vless": 5, "vmess": 1}
        (artifacts / "inventory.json").write_text(json.dumps({
            "proxyCount": setup["proxyCount"],
            "protocolCounts": setup["protocolCounts"],
            "sourceSha256": run["localSha256"],
            "targetCount": setup.get("target", {}).get("candidateCount"),
        }, indent=2) + "\n")
        for node_index in range(setup["proxyCount"]):
            if node_index > 0:
                selected = run_command(artifacts, token, f"endpoint-select-{node_index}")
                assert selected.get("passed") is True and selected.get("endpointControl", {}).get("backupPreserved") is True
                target = selected["endpointControl"]["target"]
            else:
                target = setup["target"]
            assert target.get("nodeIndex") == node_index and target.get("candidateCount") == 1
            node_hash = target["nodeHash"]
            applied = run_command(artifacts, token, f"endpoint-apply-node-{node_index}-0")
            assert applied.get("passed") is True
            applied_state = applied.get("endpointControl", {})
            expected_endpoint = target["candidateEndpointHashes"][0]
            assert applied_state.get("nodeIndex") == node_index
            assert applied_state.get("expectedEndpointHash") == expected_endpoint
            # 作者 long：每个节点独立启停核心，首个请求与后续两次请求分别保留，避免跨节点复用连接。
            for attempt in range(1, 4):
                prefix = "start" if attempt == 1 else "checkpoint"
                name = f"{prefix}-endpoint-node{node_index}-attempt{attempt}"
                outcome = run_command(artifacts, token, name)
                row = node_network_result(outcome, expected_endpoint, node_hash, node_index, attempt)
                row["command"] = name
                results.append(row)
                (artifacts / "requests.json").write_text(json.dumps(results, indent=2) + "\n")
            stopped = run_command(artifacts, token, f"stop-endpoint-node{node_index}")
            results.append({"nodeIndex": node_index, "stopPassed": stopped.get("passed") is True})
            (artifacts / "requests.json").write_text(json.dumps(results, indent=2) + "\n")
    finally:
        if prepare_attempted:
            try:
                run_command(artifacts, token, "stop-endpoint-final")
            except Exception as failure:
                (artifacts / "cleanup-error.txt").write_text(type(failure).__name__ + "\n")
            restore_attempted = True
            try:
                restored = run_command(artifacts, token, "endpoint-restore")
                restoration = compare_restoration(before, restored, run["localSha256"])
                (artifacts / "restoration.json").write_text(json.dumps(restoration, indent=2) + "\n")
            except Exception as failure:
                (artifacts / "restore-error.txt").write_text(type(failure).__name__ + "\n")
    summary = {
        "proxyCount": setup["proxyCount"],
        "protocolCounts": setup["protocolCounts"],
        "networkRequests": sum("command" in row for row in results),
        "successfulRequests": sum(row.get("passed") is True for row in results if "command" in row),
        "failedRequests": sum(row.get("passed") is False for row in results if "command" in row),
        "restoreAttempted": restore_attempted,
        "restoration": json.loads((artifacts / "restoration.json").read_text()) if (artifacts / "restoration.json").exists() else None,
    }
    (artifacts / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, ensure_ascii=False), flush=True)
    if restore_attempted:
        restoration = summary.get("restoration") or {}
        required = ("profileRowsRestored", "profileFilesRestored", "roomPreferencesRestored",
                    "sharedPreferencesRestored", "selectionRestored", "runtimeStopped",
                    "temporaryProfileDeleted", "restorePassed")
        if not all(restoration.get(key) is True for key in required):
            raise AssertionError("profile/settings restoration did not pass every snapshot comparison")


def restore_interrupted(artifacts: Path) -> None:
    run = json.loads((artifacts / "run.json").read_text())
    token = run["token"]
    run_command(artifacts, token, "stop-endpoint-recovery")
    restored = run_command(artifacts, token, "endpoint-restore")
    summary = {"passed": restored.get("passed"), "endpointControl": restored.get("endpointControl")}
    (artifacts / "interrupted-restoration.json").write_text(json.dumps(summary, indent=2) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("install", "prepare", "run", "restore"))
    parser.add_argument("--artifacts", type=Path, default=DEFAULT_ARTIFACTS)
    parser.add_argument("--previous-run", type=Path,
                        default=WORKSPACE / "acceptance-artifacts" / "endpoint-control-20261111" / "run.json")
    args = parser.parse_args()
    args.artifacts.mkdir(parents=True, exist_ok=True)
    if args.mode == "install":
        install_and_verify(args.artifacts)
    elif args.mode == "prepare":
        prepare(args.artifacts, args.previous_run)
    elif args.mode == "run":
        run_batch(args.artifacts)
    else:
        restore_interrupted(args.artifacts)


if __name__ == "__main__":
    main()
