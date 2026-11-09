#!/usr/bin/env python3
"""作者 long：按TID与启动时钟核对自身线程CPU计数，不将同名线程、缺失记录或注入延迟当作完整原生归因。"""

import argparse
import json
from pathlib import Path


def summarize(sample: dict) -> dict:
    snapshots = sample["snapshots"]
    identities = {(t["tid"], t.get("startTicks"), t["name"])
                  for s in snapshots for t in s["threads"] if "startTicks" in t}
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
            if not all(k in a and k in b and b[k] >= a[k] for k in keys):
                rejected += 1
                continue
            intervals.append({"fromReadStartMs": round(first["readStartNs"] / 1e6, 3),
                "toReadEndMs": round(last["readEndNs"] / 1e6, 3),
                "cpuMs": round((b["runNs"] - a["runNs"]) / 1e6, 6),
                "readyQueueWaitMs": round((b["waitNs"] - a["waitNs"]) / 1e6, 6),
                "slices": b["slices"] - a["slices"]})
        threads.append({"tid": tid, "startTicks": start_ticks, "name": name,
            "acceptedIntervals": len(intervals), "rejectedIntervals": rejected,
            "cpuMs": round(sum(i["cpuMs"] for i in intervals), 3) if intervals else None,
            "readyQueueWaitMs": round(sum(i["readyQueueWaitMs"] for i in intervals), 3) if intervals else None,
            "intervals": intervals})
    return {"name": sample["name"], "pid": sample["pid"],
        "dispatchStartMs": round(sample["dispatchStartNs"] / 1e6, 3),
        "dispatchEndMs": round(sample["dispatchEndNs"] / 1e6, 3),
        "readOverheadMs": [round((s["readEndNs"] - s["readStartNs"]) / 1e6, 3) for s in snapshots],
        "firstObservationMs": round(snapshots[1]["readStartNs"] / 1e6, 3) if len(snapshots) > 1 else None,
        "threads": threads,
        "interpretation": "独立未混淆诊断包、预先发现线程的累计CPU差值；同名RenderThread按TID分开。"
            "不覆盖后来创建的线程，不代表正式R8包帧率、Popup独占耗时、原生调用栈或GPU耗时。"
            "采样注入可能超过100ms；实际读取边界不等于动效边界。就绪队列等待不包含所有睡眠或驱动等待。"}


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
