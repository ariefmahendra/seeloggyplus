import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path


def validate(version, root, distributions=None):
    def require(condition, message):
        if not condition:
            raise ValueError(message)

    require(re.fullmatch(r"\d+\.\d+\.\d+", version), "Release must use a stable semver tag")
    properties = (root / "gradle.properties").read_text(encoding="utf-8")
    require(re.search(rf"^version={re.escape(version)}$", properties, re.M), "Tag and application version differ")
    notes = root / f"docs/release-notes/{version}.md"
    require(notes.is_file(), "Release notes are missing")
    body = notes.read_text(encoding="utf-8")
    require(body.startswith(f"# SeeLoggyPlus {version}\n") and "## Upgrade notes" in body, "Release notes are incomplete")
    if distributions is None:
        return
    manifest = json.loads((distributions / "update-manifest.json").read_text(encoding="utf-8"))
    require(manifest.get("latest") == version, "Manifest version differs from tag")
    require(manifest.get("channel") == "stable", "Manifest channel must be stable")
    assets = manifest.get("assets", {})
    require(set(assets) == {"windows-nojre", "windows-jre", "linux-nojre", "linux-jre"}, "Manifest must contain all four packages")
    for key, asset in assets.items():
        platform = "win" if key.startswith("windows") else "linux"
        suffix = "-jre" if key.endswith("-jre") else ""
        name = f"seeloggyplus-Portable-{platform}{suffix}.zip"
        archive = distributions / name
        require(archive.is_file(), f"Missing package: {name}")
        require(asset.get("url") == f"https://github.com/ariefmahendra/seeloggyplus/releases/download/{version}/{name}", f"Wrong download URL: {name}")
        require(asset.get("size") == archive.stat().st_size, f"Wrong package size: {name}")
        with archive.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        require(asset.get("sha256") == digest, f"Wrong checksum: {name}")
        if platform == "linux":
            with zipfile.ZipFile(archive) as package:
                entries = {entry.filename: entry for entry in package.infolist()}
                executables = ["seeloggyplus/launcher.sh"]
                if suffix:
                    executables.append("seeloggyplus/jre/bin/java")
                for executable in executables:
                    require(executable in entries, f"Missing executable: {executable}")
                    require((entries[executable].external_attr >> 16) & 0o111, f"Missing execute permission: {executable}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument("--distributions", type=Path)
    args = parser.parse_args()
    validate(args.version, Path.cwd(), args.distributions)
    print(f"RELEASE VALIDATION PASS: {args.version}")
