import argparse
import json
import os
import urllib.error
import urllib.request
from pathlib import Path


def validate_upload(release, version, distributions):
    if release.get("tag_name") != version or not release.get("draft") or release.get("prerelease"):
        raise ValueError("Only the matching stable draft may be published; published releases are immutable")
    expected = {f"seeloggyplus-Portable-{platform}{suffix}.zip" for platform in ["win", "linux"] for suffix in ["", "-jre"]}
    expected.add("update-manifest.json")
    assets = release.get("assets", [])
    if {asset["name"] for asset in assets} != expected or len(assets) != len(expected):
        raise ValueError("Release upload is incomplete or contains duplicate assets")
    for asset in assets:
        path = distributions / asset["name"]
        if asset.get("state") != "uploaded" or not path.is_file() or asset.get("size") != path.stat().st_size:
            raise ValueError("Release asset was not completely uploaded: " + asset["name"])


def request(endpoint, method="GET", body=None):
    payload = json.dumps(body).encode() if body is not None else None
    headers = {"Authorization": "Bearer " + os.environ["GITHUB_TOKEN"], "Accept": "application/vnd.github+json", "Content-Type": "application/json"}
    url = "https://api.github.com/repos/" + os.environ["GITHUB_REPOSITORY"] + "/" + endpoint
    with urllib.request.urlopen(urllib.request.Request(url, data=payload, headers=headers, method=method), timeout=60) as response:
        return json.load(response)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["prepare", "publish"])
    parser.add_argument("version")
    args = parser.parse_args()
    try:
        release = request("releases/tags/" + args.version)
    except urllib.error.HTTPError as error:
        if error.code != 404 or args.action != "prepare":
            raise
        release = None
    if args.action == "prepare":
        if release is not None and not release.get("draft"):
            raise ValueError("This release is already published; do not overwrite its assets")
        print("Release draft preparation verified")
    else:
        validate_upload(release, args.version, Path("build/distributions"))
        request("releases/" + str(release["id"]), "PATCH", {"draft": False, "prerelease": False, "make_latest": "true"})
        print("Complete stable release published: " + args.version)
