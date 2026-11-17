#!/usr/bin/env python3
"""作者 long：只把授权 age 验收输入短暂送到指定 POCO，并核验后清除。"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import tempfile
import uuid


REPO = Path(__file__).resolve().parents[1]
DEVICE = "31936bb6"
MODEL = "POCO F2 Pro"
PACKAGE = "com.lonnnnnng.flclash.motion"
TEST_PACKAGE = PACKAGE + ".test"
TEST_RUNNER = TEST_PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def adb(*parts: str, timeout: int = 45) -> bytes:
    return subprocess.check_output(["adb", "-s", DEVICE, *parts], timeout=timeout)


def write_private(path: Path, data: bytes) -> None:
    # 作者 long：输入和回执可能带授权信息，创建即限0600并原子替换，避免先宽后收权限。
    temporary = path.with_name(f".{path.name}-{uuid.uuid4().hex}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
        os.replace(temporary, path)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--identity", type=Path, required=True)
    parser.add_argument("--wrong-identity", type=Path, required=True)
    parser.add_argument("--ciphertext", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    args = parser.parse_args()
    artifacts = args.artifacts.resolve()
    artifacts.mkdir(parents=True, exist_ok=True)

    model = adb("shell", "getprop", "ro.product.model").decode().strip()
    assert model == MODEL, f"unexpected device model: {model}"
    packages = adb("shell", "pm", "list", "packages", "-3").decode()
    assert f"package:{PACKAGE}\n" in packages and f"package:{TEST_PACKAGE}\n" in packages

    root_agents = (REPO.parent / "AGENTS.md").read_text()
    urls = [value.rstrip(")]") for value in re.findall(r"https://[^\s`<>]+", root_agents)]
    assert len(urls) == 1, "expected exactly one user-authorized live URL"
    manifest = json.loads(args.manifest.read_text())
    identity = args.identity.read_text().strip()
    wrong_identity = args.wrong_identity.read_text().strip()
    ciphertext = args.ciphertext.read_bytes()
    assert (args.identity.stat().st_mode & 0o777) == 0o600
    assert (args.wrong_identity.stat().st_mode & 0o777) == 0o600
    assert identity.startswith("AGE-SECRET-KEY-1") and wrong_identity.startswith("AGE-SECRET-KEY-1")
    assert identity != wrong_identity
    assert sha(ciphertext) == manifest["cipherSha256"] and ciphertext.startswith(b"-----BEGIN AGE ENCRYPTED FILE-----")

    token = uuid.uuid4().hex
    remote_input = f"/data/local/tmp/kclash-online-age-{token}.json"
    private_dir = None
    local_input = None
    body = None
    result = {"device": {"serial": DEVICE, "model": model}, "cipherSha256": manifest["cipherSha256"],
              "plainSha256": manifest["plainSha256"], "urlSha256": sha(urls[0].encode()),
              "remoteInputRemoved": False, "passed": False}
    failure_type = None
    try:
        # 作者 long：原始 instrumentation 输出可能含失败控件文本，只放进独占临时目录并在回执筛选后删除。
        private_dir = tempfile.TemporaryDirectory(prefix=".private-age-", dir=artifacts)
        private_root = Path(private_dir.name)
        body = json.dumps({
            "token": token,
            "authorizedUrl": urls[0],
            "identity": identity,
            "wrongIdentity": wrong_identity,
            "cipherSha256": manifest["cipherSha256"],
            "plainSha256": manifest["plainSha256"],
        }, separators=(",", ":")).encode()
        local_input = private_root / "input.json"
        write_private(local_input, body)
        raw_log = private_root / "instrumentation.log"

        adb("push", str(local_input), remote_input)
        adb("shell", "chmod", "600", remote_input)
        assert sha(adb("exec-out", "cat", remote_input)) == sha(body)
        args_list = ["adb", "-s", DEVICE, "shell", "am", "instrument", "-w", "-r",
                     "-e", "class", "com.lonnnnnng.flclash.OnlineAgeSubscriptionInstrumentedTest",
                     "-e", "kclashOnlineAgeAcceptance", "true",
                     "-e", "kclashOnlineAgeInput", remote_input,
                     "-e", "kclashOnlineAgeInputSha256", sha(body),
                     "-e", "kclashOnlineAgeUrlSha256", sha(urls[0].encode()),
                     "-e", "kclashOnlineAgeCipherSha256", manifest["cipherSha256"],
                     "-e", "kclashOnlineAgePlainSha256", manifest["plainSha256"], TEST_RUNNER]
        with raw_log.open("wb") as stream:
            completed = subprocess.run(args_list, stdout=stream, stderr=subprocess.STDOUT, timeout=480)
        text = raw_log.read_text(errors="replace")
        phases = []
        for line in text.splitlines():
            if "kclash_online_age=" in line:
                try:
                    phase = json.loads(line.split("kclash_online_age=", 1)[1])
                    safe_fields = {
                        "phase", "ageArmor", "cipherBytes", "cipherSha256", "passed", "profilesUnchanged",
                        "profileId", "plaintextBytes", "plaintextSha256", "storedKeyMatches",
                        "currentProfileMatches", "running", "groupCount", "runningSinceElapsedMs",
                        "vpnEnabled", "configEpoch", "http", "positiveTraffic", "hasNonDirectChain",
                    }
                    phases.append({key: value for key, value in phase.items() if key in safe_fields})
                except ValueError:
                    pass
        result.update({"exitCode": completed.returncode, "passed": completed.returncode == 0 and "OK (1 test)" in text,
                       "phases": phases, "inputSha256": sha(body)})
        if not result["passed"]:
            failure_type = "InstrumentationFailed"
    except Exception as failure:
        failure_type = failure.__class__.__name__
        result["passed"] = False
    finally:
        cleanup_failure = None
        if local_input is not None and local_input.exists():
            try:
                local_input.unlink()
            except Exception as failure:
                cleanup_failure = failure.__class__.__name__
        try:
            exists = subprocess.run(["adb", "-s", DEVICE, "shell", "test", "-e", remote_input], capture_output=True)
            if exists.returncode == 0:
                current = adb("exec-out", "cat", remote_input)
                if body is None or sha(current) != sha(body):
                    raise RuntimeError("remote input digest mismatch")
                adb("shell", "rm", "--", remote_input)
                absent = subprocess.run(["adb", "-s", DEVICE, "shell", "test", "!", "-e", remote_input], capture_output=True)
                if absent.returncode != 0:
                    raise RuntimeError("remote input remains")
                result["remoteInputRemoved"] = True
            else:
                if exists.returncode != 1:
                    raise RuntimeError("remote input state unknown")
                result["remoteInputRemoved"] = True
        except Exception as failure:
            result["remoteInputRemoved"] = False
            cleanup_failure = cleanup_failure or failure.__class__.__name__
        if private_dir is not None:
            try:
                private_dir.cleanup()
            except Exception as failure:
                cleanup_failure = cleanup_failure or failure.__class__.__name__
        if cleanup_failure is not None:
            # 作者 long：远端可能仍留有订阅地址和 age identity，清理不确定时不能报告验收通过。
            result["localInputRemoved"] = local_input is None or not local_input.exists()
            result["passed"] = False
            result["cleanupFailureType"] = cleanup_failure
            failure_type = failure_type or "SensitiveInputCleanupFailed"
        else:
            result["localInputRemoved"] = True
    safe_log = artifacts / "instrumentation-sanitized.log"
    safe_lines = [f"passed={str(result['passed']).lower()}"]
    if "exitCode" in result:
        safe_lines.append(f"exitCode={result['exitCode']}")
    safe_lines.extend(f"kclash_online_age={json.dumps(phase, separators=(',', ':'))}" for phase in result.get("phases", []))
    write_private(safe_log, ("\n".join(safe_lines) + "\n").encode())
    result["sanitizedLog"] = str(safe_log)
    write_private(artifacts / "result.json", (json.dumps(result, ensure_ascii=False, indent=2) + "\n").encode())
    summary = {k: v for k, v in result.items() if k != "phases"}
    print(json.dumps(summary, ensure_ascii=False))
    if failure_type:
        raise SystemExit(failure_type)


if __name__ == "__main__":
    main()
