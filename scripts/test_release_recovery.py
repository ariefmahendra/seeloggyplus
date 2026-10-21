import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("recovery", Path(__file__).with_name("verify_release_recovery.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RecoveryTest(unittest.TestCase):
    def setUp(self):
        self.run = {"event": "push", "head_branch": "0.8.0", "head_sha": "abc", "path": ".github/workflows/release.yml", "status": "completed"}
        self.jobs = [{"name": name, "conclusion": "success"} for name in ["test", 'build (linux, ["PIKACU"])', 'build (win, ["windows-latest"])']]
        self.jobs.append({"name": "release", "conclusion": "failure"})

    def test_only_publication_failure_can_be_recovered(self):
        module.validate(self.run, self.jobs, "0.8.0", "abc")

    def test_failed_or_missing_package_gate_is_rejected(self):
        self.jobs[1]["conclusion"] = "failure"
        with self.assertRaises(ValueError):
            module.validate(self.run, self.jobs, "0.8.0", "abc")
        with self.assertRaises(ValueError):
            module.validate(self.run, self.jobs[:1], "0.8.0", "abc")

    def test_wrong_tag_or_commit_is_rejected(self):
        for version, sha in [("0.9.0", "abc"), ("0.8.0", "other")]:
            with self.assertRaises(ValueError):
                module.validate(self.run, self.jobs, version, sha)

    def test_preflight_or_unfinished_run_is_rejected(self):
        for field, value in [("event", "workflow_dispatch"), ("status", "in_progress"), ("path", "other.yml")]:
            run = {**self.run, field: value}
            with self.assertRaises(ValueError):
                module.validate(run, self.jobs, "0.8.0", "abc")

    def test_fetch_tagged_text_decodes_base64(self):
        import base64
        fake_fetch = lambda endpoint: {"content": base64.b64encode(b"line1\r\nline2\n").decode()}
        self.assertEqual(module.fetch_tagged_text("file.txt", "0.8.0", fetch=fake_fetch), "line1\nline2\n")

    def test_release_notes_can_be_recovered_from_the_legacy_tag_location(self):
        import base64
        import subprocess
        from unittest.mock import patch
        def fetch(endpoint):
            if endpoint == "contents/docs/release-notes/0.8.0.md?ref=0.8.0":
                return {"content": base64.b64encode(b"original tested notes\n").decode()}
            raise FileNotFoundError(endpoint)
        with patch.object(module.subprocess, "check_output", side_effect=subprocess.CalledProcessError(128, "git")):
            self.assertEqual(module.fetch_tagged_text("release-notes/0.8.0.md", "0.8.0", fetch=fetch), "original tested notes\n")

    def test_current_tag_notes_take_priority_over_the_legacy_location(self):
        import base64
        endpoints = []
        def fetch(endpoint):
            endpoints.append(endpoint)
            return {"content": base64.b64encode(b"current tested notes\n").decode()}
        self.assertEqual(module.fetch_tagged_text("release-notes/0.9.0.md", "0.9.0", fetch=fetch), "current tested notes\n")
        self.assertEqual(endpoints, ["contents/release-notes/0.9.0.md?ref=0.9.0"])


if __name__ == "__main__":
    unittest.main()
