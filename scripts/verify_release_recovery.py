import argparse
import re
import subprocess
from pathlib import Path
from release_publication import request


def validate(run, jobs, version, sha):
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("A stable release tag is required")
    if run.get("event") != "push" or run.get("head_branch") != version or run.get("head_sha") != sha:
        raise ValueError("Source run does not match the pushed release tag and commit")
    if run.get("path") != ".github/workflows/release.yml" or run.get("status") != "completed":
        raise ValueError("Source must be a completed release workflow")
    for prefix in ["test", "build (linux,", "build (win,"]:
        matches = [job for job in jobs if job.get("name") == prefix or job.get("name", "").startswith(prefix)]
        if len(matches) != 1 or matches[0].get("conclusion") != "success":
            raise ValueError("Source run did not pass its required gate: " + prefix)


def fetch_tagged_text(name, version, fetch=request):
    try:
        import base64
        content = fetch("contents/" + name + "?ref=" + version)
        return base64.b64decode(content["content"]).decode("utf-8").replace("\r\n", "\n")
    except Exception:
        try:
            return subprocess.check_output(["git", "show", version + ":" + name]).decode("utf-8").replace("\r\n", "\n")
        except subprocess.CalledProcessError:
            if name.startswith("release-notes/"):
                return fetch_tagged_text("docs/" + name, version, fetch=fetch)
            raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument("run_id", type=int)
    args = parser.parse_args()
    if not re.fullmatch(r"\d+\.\d+\.\d+", args.version):
        raise ValueError("A stable release tag is required")
    commit = request("commits/" + args.version)
    run = request("actions/runs/" + str(args.run_id))
    jobs = request("actions/runs/" + str(args.run_id) + "/jobs?per_page=100")["jobs"]
    validate(run, jobs, args.version, commit["sha"])
    for name in ["gradle.properties", "release-notes/" + args.version + ".md"]:
        tagged = fetch_tagged_text(name, args.version)
        if Path(name).read_text(encoding="utf-8").replace("\r\n", "\n") != tagged:
            raise ValueError("Recovery metadata differs from the tested tag: " + name)
    artifacts = request("actions/runs/" + str(args.run_id) + "/artifacts?per_page=100")["artifacts"]
    packages = [artifact for artifact in artifacts if artifact["name"].startswith("update-")]
    if {artifact["name"] for artifact in packages} != {"update-linux", "update-win"} or len(packages) != 2 or any(artifact["expired"] for artifact in packages):
        raise ValueError("Verified platform artifacts are missing or expired")
    print("RELEASE RECOVERY SOURCE PASS: " + args.version + " from run " + str(args.run_id))
