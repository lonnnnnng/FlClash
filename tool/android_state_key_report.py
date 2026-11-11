#!/usr/bin/env python3
"""作者 long：将独立长窗逐键快照与已提交写入联接；值来源匹配不等于唯一写者或历史根因。"""

import argparse
from collections import defaultdict
import gzip
import json
from pathlib import Path


def committed_entities(records):
    commits = {(r["pid"], r["transaction"]): r for r in records if r["kind"] == "db_committed"}
    groups = defaultdict(list)
    for row in records:
        if row.get("operation") and row.get("table"):
            groups[(row["pid"], row["operation"], row["table"])].append(row)
    found, ambiguous = [], []
    for key, rows in groups.items():
        entities = [r for r in rows if r["kind"] == "entity_write"]
        accepted = [r for r in rows if r["kind"] == "db_write_accepted"]
        executed = [r for r in rows if r["kind"] in ("db_write_executed", "db_autocommit_returned")]
        if not entities:
            continue
        # 作者 long：同一业务操作可能写多次同表；缺少唯一实体与SQL配对时保留歧义，不按列表顺序猜测。
        if len(entities) != 1 or len(accepted) != 1 or len(executed) != 1:
            ambiguous.append({"pid": key[0], "operation": key[1], "table": key[2]})
            continue
        a, e = accepted[0], executed[0]
        if a.get("write") != e.get("write") or e.get("affectedRows") == 0 or e.get("rowId") == -1:
            continue
        commit = commits.get((e["pid"], e.get("rootTransaction")))
        if commit is None and e["kind"] != "db_autocommit_returned":
            continue
        found.append({**entities[0], "commitElapsedMs": (commit or e)["elapsedMs"], "verb": a.get("verb")})
    return found, ambiguous


def evidence(matches, clock):
    return [{"pid": r["pid"], "source": r.get("source"), "operation": r.get("operation"),
             "write": r.get("write"), "elapsedMs": r[clock], "kind": r["kind"]} for r in matches]


def report_pair(before, after, records):
    entities, ambiguous = committed_entities(records)
    changes = []
    left = before["auditDelaySnapshot"]; right = after["auditDelaySnapshot"]
    assert left["enabled"] and right["enabled"]
    primary = ("profileId", "groupName", "proxyName", "testUrl")
    for profile_id in left["profiles"].keys() | right["profiles"].keys():
        old = left["profiles"].get(profile_id, {"rows": {}, "startedElapsedMs": left["startedElapsedMs"]})
        new = right["profiles"].get(profile_id, {"rows": {}, "finishedElapsedMs": right["finishedElapsedMs"]})
        candidates = [r for r in entities if r["table"] == "proxy_delay_results"
                      and old["startedElapsedMs"] <= r["commitElapsedMs"] <= new["finishedElapsedMs"]]
        for key in old["rows"].keys() | new["rows"].keys():
            a, b = old["rows"].get(key), new["rows"].get(key)
            if a == b:
                continue
            if b is None:
                matches = [r for r in candidates if r["verb"] == "DELETE"
                           and all(r.get("fields", {}).get(k) == a[k] for k in primary)]
            else:
                matches = [r for r in candidates if r["verb"] in ("INSERT", "UPDATE", "REPLACE")
                           and r.get("fields") == b]
            changes.append({"store": "proxy_delay_results", "profileId": profile_id, "keyDigest": key,
                            "change": "deleted" if b is None else "inserted" if a is None else "updated",
                            "matchedCommittedValueOrExactDelete": bool(matches),
                            "matches": evidence(matches, "commitElapsedMs"), "uniqueWriterProven": False})
    left = before["auditSharedPreferenceSnapshot"]; right = after["auditSharedPreferenceSnapshot"]
    assert left["enabled"] and right["enabled"]
    for store in left["stores"].keys() | right["stores"].keys():
        old = left["stores"].get(store, {"values": {}, "startedElapsedMs": left["startedElapsedMs"]})
        new = right["stores"].get(store, {"values": {}, "finishedElapsedMs": right["finishedElapsedMs"]})
        candidates = [r for r in records if r.get("store") == store and r["kind"] in
                      ("preference_committed", "preference_apply_accepted", "preference_commit_failed")
                      and old["startedElapsedMs"] <= r["elapsedMs"] <= new["finishedElapsedMs"]]
        for key in old["values"].keys() | new["values"].keys():
            a, b = old["values"].get(key), new["values"].get(key)
            if a == b:
                continue
            matches = [r for r in candidates if key in r.get("after", {}) and
                       (r["after"][key] == b if b is not None else r["after"][key].get("kind") == "null")]
            changes.append({"store": store, "keyDigest": key,
                            "change": "deleted" if b is None else "inserted" if a is None else "updated",
                            "matchedObservedValue": bool(matches),
                            "hasDiskCommitAcknowledgement": any(r["kind"] == "preference_committed" for r in matches),
                            "matches": evidence(matches, "elapsedMs"), "uniqueWriterProven": False})
    return {"changes": changes, "ambiguousEntityOperations": ambiguous,
            "unmatchedChanges": [r for r in changes if not r.get("matchedObservedValue", r.get("matchedCommittedValueOrExactDelete"))],
            "historicalFailuresExplained": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    snapshot_index = {}
    for path in args.directory.glob("*.json"):
        data = json.loads(path.read_text())
        if isinstance(data, dict) and data.get("snapshot", {}).get("auditDelaySnapshot", {}).get("enabled"):
            key = (data["pid"], data["command"], data["elapsedMs"])
            # 作者 long：收集器保留完整传输副本和解码结果；同一快照只能计一次，两个副本不一致则拒绝分析。
            assert key not in snapshot_index or snapshot_index[key] == data, "同一快照副本不一致"
            snapshot_index[key] = data
    snapshots = list(snapshot_index.values())
    snapshots.sort(key=lambda r: r["snapshot"]["snapshotStartedElapsedMs"])
    assert len(snapshots) >= 2, "需要同一审计会话至少两份新格式快照"
    records, integrity = [], []
    for path in sorted((args.directory / "audit").glob("*.jsonl*")):
        with gzip.open(path, "rt") if path.suffix == ".gz" else path.open() as reader:
            rows = [json.loads(line) for line in reader]
        assert [r["sequence"] for r in rows] == list(range(1, len(rows) + 1)), path.name
        assert all(r.get("dropped", 0) == 0 for r in rows), path.name
        records.extend(rows)
        integrity.append({"file": path.name, "records": len(rows), "zeroDrops": True, "sequenceContiguous": True})
    assert records
    sessions = {r["session"] for r in records if r["kind"] == "process_started"}
    assert len(sessions) == 1, "不能跨HMAC会话合并摘要"
    digests = {s["snapshot"][name]["sessionDigest"] for s in snapshots
               for name in ("auditDelaySnapshot", "auditSharedPreferenceSnapshot")}
    assert len(digests) == 1 and None not in digests, "相同PID也不能混合不同HMAC会话的快照"
    session_pids = {r["pid"] for r in records if r["kind"] == "process_started"}
    assert all(s["snapshot"]["auditStatus"]["pid"] in session_pids and
               s["snapshot"]["auditStatus"]["dropped"] == 0 for s in snapshots), "快照须来自完整审计会话的进程"
    result = {"integrity": integrity, "snapshots": len(snapshots), "pairs": [], "historicalFailuresExplained": False,
              "limitations": ["逐存储读取非全局原子；时间窗相交时不证明唯一写者",
                              "apply与commit=false只支持已观察的内存值，不证明磁盘提交",
                              "批量过期删除只有条件摘要，未逐键证明删除，匹配不到的必须保留",
                              "缺少实体与SQL唯一配对时保留歧义；不靠后来成功追认旧失败"]}
    for before, after in zip(snapshots, snapshots[1:]):
        result["pairs"].append({"before": before["command"], "after": after["command"],
                                **report_pair(before["snapshot"], after["snapshot"], records)})
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"snapshots": len(snapshots), "records": len(records),
                      "changes": sum(len(p["changes"]) for p in result["pairs"]),
                      "unmatched": sum(len(p["unmatchedChanges"]) for p in result["pairs"])}))


if __name__ == "__main__":
    main()
