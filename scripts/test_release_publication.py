import importlib.util
import hashlib
import tempfile
import unittest
import urllib.error
from unittest.mock import Mock
from pathlib import Path

spec = importlib.util.spec_from_file_location("publication", Path(__file__).with_name("release_publication.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PublicationTest(unittest.TestCase):
    def test_draft_is_found_when_tag_endpoint_returns_404(self):
        fetch = Mock(side_effect=[urllib.error.HTTPError("url", 404, "Not Found", {}, None), [self.release]])
        self.assertEqual(self.release, module.find_release("0.8.0", fetch))

    def test_published_release_uses_tag_endpoint(self):
        fetch = Mock(return_value=self.release)
        self.assertEqual(self.release, module.find_release("0.8.0", fetch))
        fetch.assert_called_once_with("releases/tags/0.8.0")

    def test_missing_release_is_distinct_from_authentication_failure(self):
        fetch = Mock(side_effect=[urllib.error.HTTPError("url", 404, "Not Found", {}, None), []])
        self.assertIsNone(module.find_release("0.8.0", fetch))
        with self.assertRaises(urllib.error.HTTPError):
            module.find_release("0.8.0", Mock(side_effect=urllib.error.HTTPError("url", 403, "Forbidden", {}, None)))

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.dist = Path(self.temp.name)
        names = [f"seeloggyplus-Portable-{platform}{suffix}.zip" for platform in ["win", "linux"] for suffix in ["", "-jre"]] + ["update-manifest.json"]
        for name in names:
            (self.dist / name).write_bytes(b"asset")
        self.release = {"id": 1, "tag_name": "0.8.0", "draft": True, "prerelease": False,
                        "assets": [{"name": name, "size": 5, "state": "uploaded", "digest": "sha256:" + hashlib.sha256(b"asset").hexdigest()} for name in names]}

    def test_complete_draft_can_be_published(self):
        module.validate_upload(self.release, "0.8.0", self.dist)

    def test_partial_upload_is_rejected(self):
        self.release["assets"].pop()
        with self.assertRaises(ValueError):
            module.validate_upload(self.release, "0.8.0", self.dist)

    def test_wrong_asset_size_is_rejected(self):
        self.release["assets"][0]["size"] = 1
        with self.assertRaises(ValueError):
            module.validate_upload(self.release, "0.8.0", self.dist)

    def test_wrong_uploaded_package_digest_is_rejected(self):
        self.release["assets"][0]["digest"] = "sha256:" + "0" * 64
        with self.assertRaises(ValueError):
            module.validate_upload(self.release, "0.8.0", self.dist)

    def test_published_release_is_not_overwritten(self):
        self.release["draft"] = False
        with self.assertRaises(ValueError):
            module.validate_upload(self.release, "0.8.0", self.dist)

    def test_wrong_tag_is_rejected(self):
        with self.assertRaises(ValueError):
            module.validate_upload(self.release, "0.9.0", self.dist)


if __name__ == "__main__":
    unittest.main()
