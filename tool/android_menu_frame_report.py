#!/usr/bin/env python3
"""作者 long：离线读取gfxinfo，分开窗口与动画标记，不把消失的Popup或无有效帧当成零耗时。"""

import argparse
from collections import Counter
import csv
import json
from pathlib import Path
import re
import statistics


def _counter(block: str, expression: str) -> int:
    match = re.search(expression, block, re.MULTILINE)
    if match is None:
        raise ValueError(f"缺少gfxinfo字段: {expression}")
    return int(match[1])


def _counts(block: str) -> dict:
    frames = _counter(block, r"^Total frames rendered: (\d+)")
    janky = _counter(block, r"^Janky frames: (\d+)")
    if janky > frames:
        raise ValueError("卡顿帧不能大于总帧数")
    return {"statsSinceNs": _counter(block, r"^Stats since: (\d+)ns"),
            "frames": frames, "janky": janky,
            "jankPercent": round(janky / frames * 100, 3) if frames else None}


def _intervals(rows: list[dict], start: str, end: str) -> dict:
    values = []
    rejected = 0
    for row in rows:
        first, last = row.get(start, 0), row.get(end, 0)
        # 作者 long：未完成帧或失效时钟不能产生负耗时，也不能用0补齐后拉低中位数。
        if first <= 0 or last < first:
            rejected += 1
        else:
            values.append((last - first) / 1_000_000)
    return {"samples": len(values), "rejected": rejected,
            "medianMs": round(statistics.median(values), 3) if values else None,
            "maxMs": round(max(values), 3) if values else None}


def parse_gfxinfo(raw: str, expected_window: str | None = None) -> dict:
    sections = re.split(r"^Window: ", raw, flags=re.MULTILINE)
    report = {"process": _counts(sections[0]), "windows": [], "warnings": []}
    for section in sections[1:]:
        name = section.splitlines()[0].strip()
        counts = _counts(section)
        parts = section.split("---PROFILEDATA---")
        if len(parts) % 2 == 0:
            raise ValueError(f"{name}: 帧表缺少结束标记")
        rows = []
        for table in parts[1::2]:
            reader = csv.DictReader(table.strip().splitlines())
            if not reader.fieldnames or not {"Flags", "IntendedVsync"}.issubset(reader.fieldnames):
                raise ValueError(f"{name}: 帧表缺少Flags/IntendedVsync")
            for raw_row in reader:
                if None in raw_row:
                    raise ValueError(f"{name}: 帧表列数超过表头")
                try:
                    row = {key: int(value) for key, value in raw_row.items() if key}
                except (TypeError, ValueError) as error:
                    raise ValueError(f"{name}: 帧表包含缺失或非整数值") from error
                # 作者 long：每个Window都有自己的重置时间，不能套用进程的统计起点混入旧窗口帧。
                if row["IntendedVsync"] >= counts["statsSinceNs"]:
                    rows.append(row)
        histogram = Counter(row["Flags"] for row in rows)
        regular = [row for row in rows if row["Flags"] == 0]
        report["windows"].append({"name": name, **counts,
            "rowsAfterReset": len(rows), "flags": dict(sorted(histogram.items())),
            # 作者 long：Android16 FrameInfo.h定义RTAnimation=2、SkippedFrame=8；位标记可重叠，不能把各类计数相加。
            "rtAnimationRows": sum(bool(row["Flags"] & 2) for row in rows),
            "skippedRows": sum(bool(row["Flags"] & 8) for row in rows),
            "unknownFlagRows": sum(bool(row["Flags"] & ~15) for row in rows),
            "flagZeroRows": len(regular),
            "uiElapsed": _intervals(regular, "Vsync", "SyncQueued"),
            "renderElapsed": _intervals(regular, "SyncStart", "SwapBuffers"),
            "coverage": "available" if regular else "no-regular-frame-rows"})
    if expected_window is not None:
        available = any(expected_window in item["name"] for item in report["windows"])
        report["expectedWindow"] = expected_window
        report["expectedWindowAvailable"] = available
        if not available:
            report["warnings"].append("目标窗口未出现在采集结果中；可能已经销毁，不能据此判断其耗时或卡顿为零。")
    report["interpretation"] = (
        "保留进程与各窗口原始计数，不相加、不从部分帧表重算卡顿分母。"
        "区间仅使用Flags=0且位于该窗口重置后的记录，是经过时间，不是CPU/GPU归因；"
        "RT动画与Skipped标记可能重叠，静态终态或窗口消失不证明动效流畅。"
    )
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", type=Path, nargs="+")
    parser.add_argument("--expected-window", help="例如弹出式窗口；未找到时明确记录采集缺口")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    results = [{"file": str(path), **parse_gfxinfo(path.read_text(), args.expected_window)}
               for path in args.inputs]
    encoded = json.dumps(results, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(encoded)
    else:
        print(encoded, end="")


if __name__ == "__main__":
    main()
