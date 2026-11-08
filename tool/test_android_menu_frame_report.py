"""作者 long：验证窗口销毁、重置时间与特殊帧不能被误报为性能通过。"""

import unittest

from android_menu_frame_report import parse_gfxinfo


HEADER = "Flags,IntendedVsync,Vsync,SyncQueued,SyncStart,SwapBuffers,\n"


def counters(since: int, frames: int, janky: int) -> str:
    return f"Stats since: {since}ns\nTotal frames rendered: {frames}\nJanky frames: {janky}\n"


def window(name: str, since: int, rows: str) -> str:
    return f"Window: {name}\n" + counters(since, 3, 1) + "---PROFILEDATA---\n" + HEADER + rows + "---PROFILEDATA---\n"


class MenuFrameReportTest(unittest.TestCase):
    def test_window_reset_and_process_counts_stay_separate(self):
        raw = counters(100, 8, 5)
        raw += window("MainActivity", 100, "0,150,150,1150,1200,2200,\n")
        raw += window("Popup", 200, "0,150,150,999999,1200,999999,\n0,250,250,2250,2500,4500,\n")
        report = parse_gfxinfo(raw, "Popup")
        self.assertEqual(8, report["process"]["frames"])
        popup = report["windows"][1]
        self.assertEqual(1, popup["rowsAfterReset"])
        self.assertEqual(0.002, popup["uiElapsed"]["medianMs"])
        self.assertTrue(report["expectedWindowAvailable"])

    def test_destroyed_popup_is_unavailable_not_zero_cost(self):
        raw = counters(100, 20, 4) + window("MainActivity", 100, "0,150,150,250,300,400,\n")
        report = parse_gfxinfo(raw, "Popup")
        self.assertFalse(report["expectedWindowAvailable"])
        self.assertEqual(1, len(report["windows"]))
        self.assertTrue(report["warnings"])

    def test_overlapping_flags_and_invalid_timestamps_are_not_cpu_samples(self):
        raw = counters(100, 3, 1) + window("Popup", 100,
            "2,150,150,100000000,150,100000000,\n"
            "10,160,160,100000000,160,10,\n"
            "0,170,170,10,180,20,\n")
        report = parse_gfxinfo(raw)["windows"][0]
        self.assertEqual(2, report["rtAnimationRows"])
        self.assertEqual(1, report["skippedRows"])
        self.assertEqual(1, report["flagZeroRows"])
        self.assertIsNone(report["uiElapsed"]["medianMs"])
        self.assertEqual(1, report["renderElapsed"]["rejected"])

    def test_truncated_or_malformed_tables_fail_instead_of_passing(self):
        raw = counters(100, 3, 1) + window("Popup", 100, "0,150,150,200,200,300,\n")
        with self.assertRaisesRegex(ValueError, "结束标记"):
            parse_gfxinfo(raw.rsplit("---PROFILEDATA---", 1)[0])
        with self.assertRaisesRegex(ValueError, "非整数"):
            parse_gfxinfo(raw.replace("200,200,300", "bad,200,300"))


if __name__ == "__main__":
    unittest.main()
