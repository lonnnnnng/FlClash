"""作者 long：回执轮询不能重复读取已回收文件，也不能因本地缓存放宽SHA与路径保护。"""

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import android_endpoint_nodes_acceptance as acceptance


class EndpointReceiptTest(unittest.TestCase):
    def setUp(self):
        self.token = "a" * 32
        self.command = "switch-arm-first"
        self.data = json.dumps({"token": self.token, "command": self.command, "passed": True}).encode()
        self.digest = acceptance.sha256(self.data)
        self.remote = (
            f"/sdcard/Android/data/{acceptance.PACKAGE}/files/stability-reports/"
            f"{self.token}-{self.command}-{self.digest}.json"
        )
        self.meta = {"token": self.token, "command": self.command, "sha256": self.digest,
                     "bytes": len(self.data), "transport": "verified-file-v1", "path": self.remote}

    def test_poll_after_remote_cleanup_uses_verified_local_copy(self):
        remote_present = True
        reads = 0

        def fake_adb(*parts, **kwargs):
            nonlocal remote_present, reads
            if parts[0] == "logcat":
                return json.dumps(self.meta).encode()
            if parts == ("exec-out", "cat", self.remote):
                self.assertTrue(remote_present, "second poll reread the deleted device receipt")
                reads += 1
                return self.data
            if parts == ("shell", "rm", "--", self.remote):
                self.assertTrue(remote_present)
                remote_present = False
                return b""
            self.fail(f"unexpected adb operation: {parts}")

        with tempfile.TemporaryDirectory() as directory, patch.object(acceptance, "adb", side_effect=fake_adb):
            artifacts = Path(directory)
            first = acceptance.report_items(artifacts, self.token, self.command)
            second = acceptance.report_items(artifacts, self.token, self.command)
            self.assertEqual(first, second)
            self.assertTrue(second[0]["passed"])
            self.assertEqual(2, reads)
            self.assertFalse(remote_present)

    def test_corrupted_local_copy_still_fails_digest_check(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(acceptance, "adb") as adb:
            artifacts = Path(directory)
            (artifacts / f"{self.command}-{self.digest}-transport.json").write_bytes(b"corrupted")
            adb.return_value = json.dumps(self.meta).encode()
            with self.assertRaises(AssertionError):
                acceptance.report_items(artifacts, self.token, self.command)
            self.assertEqual(1, adb.call_count)

    def test_foreign_receipt_path_is_rejected_before_file_access(self):
        self.meta["path"] = "/sdcard/Download/foreign.json"
        with tempfile.TemporaryDirectory() as directory, patch.object(acceptance, "adb") as adb:
            adb.return_value = json.dumps(self.meta).encode()
            with self.assertRaises(AssertionError):
                acceptance.report_items(Path(directory), self.token, self.command)
            self.assertEqual(1, adb.call_count)

    def test_non_default_device_requires_explicit_model(self):
        with patch.object(acceptance, "SERIAL"), patch.object(acceptance, "EXPECTED_MODEL"):
            with self.assertRaises(ValueError):
                acceptance.configure_target("31936bb6", None)
            acceptance.configure_target("31936bb6", "POCO F2 Pro")
            with patch.object(acceptance, "adb", side_effect=[b"31936bb6\n", b"unrelated phone\n"]):
                with self.assertRaises(AssertionError):
                    acceptance.assert_target()


if __name__ == "__main__":
    unittest.main()
