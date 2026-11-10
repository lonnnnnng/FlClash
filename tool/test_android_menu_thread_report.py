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

    def test_thread_missing_before_baseline_remains_visible_as_failure(self):
        report = summarize({"name": "closed", "pid": 1, "actionSucceeded": True,
            "dispatchStartNs": 0, "dispatchEndNs": 1,
            "snapshots": [{"readStartNs": 0, "readEndNs": 1, "threads": [
                {"tid": 9, "name": "RenderThread", "statError": "FileNotFoundException",
                 "schedstatError": "FileNotFoundException"}]}]})
        self.assertEqual([], report["threads"])
        self.assertEqual(9, report["readFailures"][0]["tid"])
        self.assertEqual("FileNotFoundException", report["readFailures"][0]["statError"])
        self.assertTrue(report["actionSucceeded"])

    def test_independent_samples_cover_input_dispatch_and_report_gaps(self):
        sample = {"name": "open", "pid": 1, "dispatchStartNs": 100_000_000,
                  "dispatchEndNs": 235_000_000, "samplingPeriodNs": 50_000_000,
                  "snapshots": [
                      {"readStartNs": 0, "readEndNs": 1, "threads": []},
                      {"readStartNs": 45_000_000, "readEndNs": 45_100_000, "scheduledNs": 50_000_000, "threads": []},
                      {"readStartNs": 100_000_000, "readEndNs": 100_100_000, "scheduledNs": 100_000_000, "threads": []},
                      {"readStartNs": 225_000_000, "readEndNs": 225_100_000, "scheduledNs": 150_000_000, "threads": []},
                      {"readStartNs": 275_000_000, "readEndNs": 275_100_000, "scheduledNs": 275_000_000, "threads": []},
                  ]}
        report = summarize(sample)
        self.assertEqual(45, report["firstObservationMs"])
        self.assertEqual(1, report["observationsBeforeDispatch"])
        self.assertEqual(2, report["observationsDuringDispatch"])
        self.assertEqual(1, report["observationsFirst100ms"])
        self.assertEqual(124.9, report["gaps"][1]["gapMs"])
        self.assertEqual(1, report["gaps"][1]["missedPeriods"])
        self.assertTrue(report["threads"] == [])


if __name__ == "__main__":
    unittest.main()
