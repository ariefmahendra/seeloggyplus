import importlib.util
import hashlib
import json
import os
import shlex
import shutil
import subprocess
import tempfile
import textwrap
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("verify_release", Path(__file__).with_name("verify_release.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReleaseValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "release-notes").mkdir(parents=True)
        (self.root / "gradle.properties").write_text("version=0.8.0\n")
        (self.root / "release-notes/0.8.0.md").write_text("# SeeLoggyPlus 0.8.0\n\n## Upgrade notes\nSafe upgrade\n")

    def test_metadata_matches_tag(self):
        module.validate("0.8.0", self.root)

    def test_mismatched_version_is_rejected(self):
        with self.assertRaises(ValueError):
            module.validate("0.8.1", self.root)

    def test_missing_notes_are_rejected(self):
        (self.root / "release-notes/0.8.0.md").unlink()
        with self.assertRaises(ValueError):
            module.validate("0.8.0", self.root)

    def test_incomplete_manifest_is_rejected(self):
        dist = self.root / "dist"
        dist.mkdir()
        (dist / "update-manifest.json").write_text(json.dumps({"latest": "0.8.0", "assets": {}}))
        with self.assertRaises(ValueError):
            module.validate("0.8.0", self.root, dist)

    def complete_manifest(self):
        dist = self.root / "dist"
        dist.mkdir()
        assets = {}
        for platform, asset_platform in [("win", "windows"), ("linux", "linux")]:
            for suffix, variant in [("", "nojre"), ("-jre", "jre")]:
                name = f"seeloggyplus-Portable-{platform}{suffix}.zip"
                with zipfile.ZipFile(dist / name, "w") as archive:
                    for file in ["launcher.sh", "jre/bin/java"]:
                        entry = zipfile.ZipInfo("seeloggyplus/" + file)
                        entry.external_attr = 0o100755 << 16
                        archive.writestr(entry, b"package")
                payload = (dist / name).read_bytes()
                assets[f"{asset_platform}-{variant}"] = {"url": f"https://github.com/ariefmahendra/seeloggyplus/releases/download/0.8.0/{name}", "size": len(payload), "sha256": hashlib.sha256(payload).hexdigest()}
        manifest = {"latest": "0.8.0", "channel": "stable", "assets": assets}
        (dist / "update-manifest.json").write_text(json.dumps(manifest))
        return dist, manifest

    def test_complete_manifest_is_accepted(self):
        dist, _ = self.complete_manifest()
        module.validate("0.8.0", self.root, dist)

    def test_modified_package_is_rejected(self):
        dist, _ = self.complete_manifest()
        (dist / "seeloggyplus-Portable-win.zip").write_bytes(b"changed")
        with self.assertRaises(ValueError):
            module.validate("0.8.0", self.root, dist)

    def test_wrong_download_url_is_rejected(self):
        dist, manifest = self.complete_manifest()
        manifest["assets"]["windows-jre"]["url"] = "https://example.test/wrong.zip"
        (dist / "update-manifest.json").write_text(json.dumps(manifest))
        with self.assertRaises(ValueError):
            module.validate("0.8.0", self.root, dist)

    def test_non_executable_linux_launcher_is_rejected(self):
        dist, manifest = self.complete_manifest()
        path = dist / "seeloggyplus-Portable-linux.zip"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("seeloggyplus/launcher.sh", b"package")
        asset = manifest["assets"]["linux-nojre"]
        asset.update(size=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest())
        (dist / "update-manifest.json").write_text(json.dumps(manifest))
        with self.assertRaises(ValueError):
            module.validate("0.8.0", self.root, dist)


class ReleaseWorkflowTest(unittest.TestCase):
    def test_manual_package_urls_use_the_application_version(self):
        workflow = (Path(__file__).parents[1] / ".github/workflows/release.yml").read_text(encoding="utf-8")
        step = workflow.split("- name: Build platform packages and manifest", 1)[1].split("- name:", 1)[0]
        command = textwrap.dedent(step.split("run: |", 1)[1]).strip()
        substitutions = {"github.repository": "ariefmahendra/seeloggyplus", "github.ref_name || github.run_id": "main", "github.ref_name": "main", "matrix.platform": "win", "vars.MIN_SUPPORTED": ""}
        for expression, value in substitutions.items():
            command = command.replace("${{ " + expression + " }}", value)
        bash = shutil.which("bash")
        git_bash = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe"
        if os.name == "nt" and git_bash.is_file():
            bash = str(git_bash)
        self.assertIsNotNone(bash, "Release workflow validation requires Bash")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gradle.properties").write_text("version=0.8.0\n", encoding="utf-8")
            (root / "capture.py").write_text("import json,sys\nfrom pathlib import Path\nPath('args.json').write_text(json.dumps(sys.argv[1:]))\n", encoding="utf-8")
            command = command.replace("./gradlew", shlex.quote(shutil.which("python").replace("\\", "/")) + " capture.py")
            subprocess.run([bash, "-c", command], cwd=root, check=True, capture_output=True, text=True)
            arguments = json.loads((root / "args.json").read_text())
            self.assertIn("-PupdateBaseUrl=https://github.com/ariefmahendra/seeloggyplus/releases/download/0.8.0", arguments)
            self.assertIn("-PreleaseNotesUrl=https://github.com/ariefmahendra/seeloggyplus/releases/tag/0.8.0", arguments)


if __name__ == "__main__":
    unittest.main()
