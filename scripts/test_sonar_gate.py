import importlib.util
import unittest
from pathlib import Path
from unittest.mock import Mock


class SonarGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("sonar_gate", Path(__file__).with_name("sonar_gate.py"))
        cls.gate = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.gate)

    def run_record(self, **changes):
        result = {"id": 5, "head_sha": "abc123", "head_branch": "dev", "event": "push", "status": "completed", "conclusion": "success", "path": ".github/workflows/sonarqube.yml", "head_repository": {"full_name": "owner/repo"}, "html_url": "https://github.com/owner/repo/actions/runs/5"}
        result.update(changes)
        return result

    def test_accepts_success_for_exact_dev_commit(self):
        record = self.run_record()
        self.assertEqual(record, self.gate.verify("owner/repo", "abc123", Mock(return_value=[record]), lambda: 0, Mock(), 30))

    def test_rejects_old_commits_other_branches_forks_and_workflows(self):
        for changes in [{"head_sha": "old"}, {"head_branch": "main"}, {"head_repository": {"full_name": "fork/repo"}}, {"path": ".github/workflows/other.yml"}, {"event": "pull_request"}]:
            with self.subTest(changes=changes), self.assertRaises(TimeoutError):
                self.gate.verify("owner/repo", "abc123", Mock(return_value=[self.run_record(**changes)]), Mock(side_effect=[0, 31]), Mock(), 30)

    def test_newer_failure_cannot_be_hidden_by_an_older_success(self):
        records = [self.run_record(id=6, conclusion="failure"), self.run_record()]
        with self.assertRaisesRegex(ValueError, "failure"):
            self.gate.verify("owner/repo", "abc123", Mock(return_value=records), lambda: 0, Mock(), 30)

    def test_waits_for_running_scan_then_accepts_success(self):
        fetch = Mock(side_effect=[[self.run_record(status="in_progress", conclusion=None)], [self.run_record()]])
        sleep = Mock()
        result = self.gate.verify("owner/repo", "abc123", fetch, lambda: 0, sleep, 30)
        self.assertEqual("success", result["conclusion"])
        sleep.assert_called_once()

    def test_manual_dev_scan_is_accepted_but_skipped_and_cancelled_are_not(self):
        record = self.run_record(event="workflow_dispatch")
        self.assertEqual(record, self.gate.verify("owner/repo", "abc123", Mock(return_value=[record]), lambda: 0, Mock(), 30))
        for conclusion in ["skipped", "cancelled", "timed_out", "neutral"]:
            with self.subTest(conclusion=conclusion), self.assertRaises(ValueError):
                self.gate.verify("owner/repo", "abc123", Mock(return_value=[self.run_record(conclusion=conclusion)]), lambda: 0, Mock(), 30)

    def test_api_failure_fails_closed(self):
        with self.assertRaises(OSError):
            self.gate.verify("owner/repo", "abc123", Mock(side_effect=OSError("API unavailable")), lambda: 0, Mock(), 30)


if __name__ == "__main__":
    unittest.main()
