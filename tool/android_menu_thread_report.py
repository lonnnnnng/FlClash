#!/usr/bin/env python3
"""作者 long：按TID与启动时钟核对自身线程CPU计数，不将同名线程、缺失记录或注入延迟当作完整原生归因。"""

import argparse
import json
from pathlib import Path


def summarize(sample: dict) -> dict:
    snapshots = sample["snapshots"]
    dispatch_start = sample["dispatchStartNs"]
    dispatch_end = sample["dispatchEndNs"]
    dispatch_ms = {"start": round(dispatch_start / 1e6, 3), "end": round(dispatch_end / 1e6, 3),
                   "duration": round((dispatch_end - dispatch_start) / 1e6, 3)}
    identities = {(t["tid"], t.get("startTicks"), t["name"])
                  for s in snapshots for t in s["threads"] if "startTicks" in t}
    # 作者 long：采样前已退出的线程没有 startTicks，不能从身份集合中静默消失；错误单列，不补零或并入同名线程。
    read_failures = [{"snapshot": index, "tid": t["tid"], "name": t["name"],
                      **{key: t[key] for key in ("statError", "schedstatError") if key in t}}
                     for index, snapshot in enumerate(snapshots) for t in snapshot["threads"]
                     if "statError" in t or "schedstatError" in t]
    threads = []
    for tid, start_ticks, name in sorted(identities):
        intervals = []
        rejected = 0
        for first, last in zip(snapshots, snapshots[1:]):
            def match(snapshot):
                return next((t for t in snapshot["threads"] if
                             t["tid"] == tid and t.get("startTicks") == start_ticks), {})
            a, b = match(first), match(last)
            # 作者 long：schedstat提供纳秒级累计CPU与就绪队列等待；stat的粗tick只保留在原始证据，不能偷偷替代或拼接。
            keys = ("runNs", "waitNs", "slices")
            chronological = first["readStartNs"] <= first["readEndNs"] <= last["readStartNs"] <= last["readEndNs"]
            if not chronological or not all(k in a and k in b and b[k] >= a[k] for k in keys):
                rejected += 1
                continue
            interval = {"fromReadStartMs": round(first["readStartNs"] / 1e6, 3),
                "toReadEndMs": round(last["readEndNs"] / 1e6, 3),
                "cpuMs": round((b["runNs"] - a["runNs"]) / 1e6, 6),
                "readyQueueWaitMs": round((b["waitNs"] - a["waitNs"]) / 1e6, 6),
                "slices": b["slices"] - a["slices"],
                "overlapsInputDispatch": last["readEndNs"] > dispatch_start and first["readStartNs"] < dispatch_end}
            if "scheduledNs" in last:
                interval["scheduledReadMs"] = round(last["scheduledNs"] / 1e6, 3)
                interval["scheduleLagMs"] = round((last["readStartNs"] - last["scheduledNs"]) / 1e6, 3)
            intervals.append(interval)
        threads.append({"tid": tid, "startTicks": start_ticks, "name": name,
            "acceptedIntervals": len(intervals), "rejectedIntervals": rejected,
            "cpuMs": round(sum(i["cpuMs"] for i in intervals), 3) if intervals else None,
            "readyQueueWaitMs": round(sum(i["readyQueueWaitMs"] for i in intervals), 3) if intervals else None,
            "intervals": intervals})
    timed_snapshots = snapshots[1:]
    gaps = [{"fromReadEndMs": round(first["readEndNs"] / 1e6, 3),
             "toReadStartMs": round(last["readStartNs"] / 1e6, 3),
             "gapMs": round((last["readStartNs"] - first["readEndNs"]) / 1e6, 3),
             "missedPeriods": max(0, round((last["readStartNs"] - first["readEndNs"]) / sample.get("samplingPeriodNs", 100_000_000)) - 1)}
            for first, last in zip(timed_snapshots, timed_snapshots[1:])]
    return {"name": sample["name"], "pid": sample["pid"],
        "actionSucceeded": sample.get("actionSucceeded"), "readFailures": read_failures,
        "dispatchStartMs": dispatch_ms["start"], "dispatchEndMs": dispatch_ms["end"],
        "dispatchDurationMs": dispatch_ms["duration"],
        "readOverheadMs": [round((s["readEndNs"] - s["readStartNs"]) / 1e6, 3) for s in snapshots],
        "firstObservationMs": round(timed_snapshots[0]["readStartNs"] / 1e6, 3) if timed_snapshots else None,
        "observationsBeforeDispatch": sum(s["readEndNs"] <= dispatch_start for s in timed_snapshots),
        "observationsDuringDispatch": sum(s["readStartNs"] < dispatch_end and s["readEndNs"] > dispatch_start for s in timed_snapshots),
        "observationsFirst100ms": sum(dispatch_start <= s["readStartNs"] < dispatch_start + 100_000_000 for s in timed_snapshots),
        "lastObservationAfterDispatchMs": round((timed_snapshots[-1]["readEndNs"] - dispatch_end) / 1e6, 3) if timed_snapshots else None,
        "gaps": gaps,
        "threads": threads,
        "interpretation": "独立未混淆诊断包、预先发现线程的累计CPU差值；同名RenderThread按TID分开。"
            "采样线程与触点并发，能观察注入阻塞期间的线程计数变化，但不证明画面已呈现。"
            "不覆盖后来创建的线程，不代表正式R8包帧率、Popup独占耗时、原生调用栈或GPU耗时。"
            "实际读取边界与采样缺口显式记录；就绪队列等待不包含所有睡眠或驱动等待。"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    lines = args.input.read_text().splitlines()
    samples = [json.loads(line.split("kclash_thread_cpu=", 1)[1])
               for line in lines if line.startswith("INSTRUMENTATION_STATUS: kclash_thread_cpu=")]
    if not samples:
        raise ValueError("未找到线程采样证据")
    args.output.write_text(json.dumps([summarize(s) for s in samples], ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
