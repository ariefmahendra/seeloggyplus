import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("publication", Path(__file__).with_name("release_publication.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PublicationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.dist = Path(self.temp.name)
        names = [f"seeloggyplus-Portable-{platform}{suffix}.zip" for platform in ["win", "linux"] for suffix in ["", "-jre"]] + ["update-manifest.json"]
        for name in names:
            (self.dist / name).write_bytes(b"asset")
        self.release = {"id": 1, "tag_name": "0.8.0", "draft": True, "prerelease": False,
                        "assets": [{"name": name, "size": 5, "state": "uploaded"} for name in names]}

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

    def test_published_release_is_not_overwritten(self):
        self.release["draft"] = False
        with self.assertRaises(ValueError):
            module.validate_upload(self.release, "0.8.0", self.dist)

    def test_wrong_tag_is_rejected(self):
        with self.assertRaises(ValueError):
            module.validate_upload(self.release, "0.9.0", self.dist)


if __name__ == "__main__":
    unittest.main()
