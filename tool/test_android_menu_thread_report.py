"""作者 long：防止线程复用/死亡或时钟倒退被误报为完整CPU归因。"""
import unittest
from android_menu_thread_report import summarize


class MenuThreadReportTest(unittest.TestCase):
    def test_tid_reuse_missing_counters_and_late_dispatch_are_explicit(self):
        def snapshot(at, start, cpu, wait):
            thread = {"tid": 9, "name": "RenderThread", "startTicks": start}
            if cpu is not None:
                thread.update(runNs=cpu, waitNs=wait, slices=1)
            return {"readStartNs": at, "readEndNs": at + 100, "threads": [thread]}
        report = summarize({"name": "sample", "pid": 1, "dispatchStartNs": 100,
            "dispatchEndNs": 230_000_000, "snapshots": [
                snapshot(0, 1, 100, 0), snapshot(230_000_000, 1, 1_000_100, 500_000),
                snapshot(300_000_000, 2, 20, 0), snapshot(400_000_000, 2, None, 0)]})
        self.assertEqual(230, report["firstObservationMs"])
        self.assertEqual(2, len(report["threads"]))
        first, second = report["threads"]
        self.assertEqual(1, first["cpuMs"])
        self.assertEqual(.5, first["readyQueueWaitMs"])
        self.assertEqual(2, first["rejectedIntervals"])
        self.assertIsNone(second["cpuMs"])

    def test_backwards_counters_are_rejected(self):
        snapshots = [{"readStartNs": i, "readEndNs": i + 1, "threads": [
            {"tid": 1, "name": "main", "startTicks": 1, "runNs": 20 - i, "waitNs": 0, "slices": 1}]} for i in range(2)]
        report = summarize({"name": "sample", "pid": 1, "dispatchStartNs": 0, "dispatchEndNs": 1, "snapshots": snapshots})
        self.assertIsNone(report["threads"][0]["cpuMs"])
        self.assertEqual(1, report["threads"][0]["rejectedIntervals"])


if __name__ == "__main__":
    unittest.main()
