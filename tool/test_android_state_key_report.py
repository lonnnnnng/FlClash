"""作者 long：反例保证报告器不会把回滚、旧值或apply受理错误升级为持久化因果。"""
import unittest
from android_state_key_report import report_pair


def state(rows=None, prefs=None, start=10, end=11):
    return {"auditDelaySnapshot": {"enabled": True, "startedElapsedMs": start, "finishedElapsedMs": end,
            "profiles": {"1": {"startedElapsedMs": start, "finishedElapsedMs": end, "rows": rows or {}}}},
            "auditSharedPreferenceSnapshot": {"enabled": True, "startedElapsedMs": start, "finishedElapsedMs": end,
            "stores": {"test": {"startedElapsedMs": start, "finishedElapsedMs": end, "values": prefs or {}}}}}


def writes(fields, verb="INSERT", commit="db_committed", time=20):
    base = {"pid": 1, "operation": "op", "source": "test", "table": "proxy_delay_results", "elapsedMs": time}
    return [{**base, "kind": "entity_write", "fields": fields},
            {**base, "kind": "db_write_accepted", "write": "w", "verb": verb},
            {**base, "kind": "db_write_executed", "write": "w", "rootTransaction": "t"},
            {**base, "kind": commit, "transaction": "t"}]


class KeyReportTest(unittest.TestCase):
    row = {"profileId": {"digest": "p"}, "groupName": {"digest": "g"}, "proxyName": {"digest": "n"},
           "testUrl": {"digest": "u"}, "value": {"digest": "v"}, "updatedAtMillis": {"digest": "t"}}

    def test_committed_whole_row_matches_but_never_claims_unique_writer(self):
        r = report_pair(state(), state({"k": self.row}, start=30, end=31), writes(self.row))
        self.assertEqual([], r["unmatchedChanges"])
        self.assertFalse(r["changes"][0]["uniqueWriterProven"])

    def test_rollback_and_stale_commit_do_not_explain_new_value(self):
        for events in (writes(self.row, commit="db_rolled_back"), writes(self.row, time=9)):
            r = report_pair(state(), state({"k": self.row}, start=30, end=31), events)
            self.assertEqual(1, len(r["unmatchedChanges"]))

    def test_same_node_different_url_or_timestamp_does_not_match(self):
        for name in ("testUrl", "updatedAtMillis"):
            row = {**self.row, name: {"digest": "different"}}
            r = report_pair(state(), state({"k": row}, start=30, end=31), writes(self.row))
            self.assertEqual(1, len(r["unmatchedChanges"]))

    def test_exact_delete_matches_and_expiry_predicate_remains_unproven(self):
        key = {k: v for k, v in self.row.items() if k not in ("value", "updatedAtMillis")}
        exact = report_pair(state({"k": self.row}), state(start=30, end=31), writes(key, verb="DELETE"))
        self.assertEqual([], exact["unmatchedChanges"])
        expiry = report_pair(state({"k": self.row}), state(start=30, end=31),
                             writes({"profileId": self.row["profileId"], "cutoff": {"digest": "cutoff"}}, verb="DELETE"))
        self.assertEqual(1, len(expiry["unmatchedChanges"]))

    def test_apply_and_failed_commit_are_not_disk_commit(self):
        for kind in ("preference_apply_accepted", "preference_commit_failed", "preference_committed"):
            value = {"kind": "String", "digest": "value", "bytes": 5}
            events = [{"pid": 1, "kind": kind, "store": "test", "elapsedMs": 20, "after": {"k": value}}]
            r = report_pair(state(), state(prefs={"k": value}, start=30, end=31), events)
            self.assertEqual([], r["unmatchedChanges"])
            self.assertEqual(kind == "preference_committed", r["changes"][0]["hasDiskCommitAcknowledgement"])

    def test_shared_delete_requires_observed_null_for_same_key(self):
        events = [{"pid": 1, "kind": "preference_committed", "store": "test", "elapsedMs": 20,
                   "after": {"k": {"kind": "null", "bytes": 0, "digest": "empty"}}}]
        r = report_pair(state(prefs={"k": {"digest": "v"}}), state(start=30, end=31), events)
        self.assertEqual([], r["unmatchedChanges"])

    def test_multiple_entities_cannot_be_guessed_into_one_sql(self):
        events = writes(self.row)
        events.insert(1, dict(events[0]))
        r = report_pair(state(), state({"k": self.row}, start=30, end=31), events)
        self.assertEqual(1, len(r["unmatchedChanges"]))
        self.assertEqual(1, len(r["ambiguousEntityOperations"]))


if __name__ == "__main__":
    unittest.main()
