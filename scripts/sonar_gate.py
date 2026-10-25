import argparse
import json
import os
import re
import time
import urllib.parse
import urllib.request


def verify(repository, revision, fetch_runs, clock, sleep, timeout):
    deadline = clock() + timeout
    while True:
        runs = [run for run in fetch_runs() if run.get("head_sha") == revision
                and run.get("head_branch") == "dev"
                and run.get("event") in {"push", "workflow_dispatch"}
                and run.get("path") == ".github/workflows/sonarqube.yml"
                and (run.get("head_repository") or {}).get("full_name") == repository]
        latest = max(runs, key=lambda run: run["id"], default=None)
        if latest and latest.get("status") == "completed":
            if latest.get("conclusion") != "success":
                raise ValueError("Latest SonarQube dev scan ended with " + str(latest.get("conclusion")) + ": " + latest.get("html_url", ""))
            return latest
        if clock() >= deadline:
            raise TimeoutError("No successful SonarQube scan completed for dev commit " + revision)
        sleep(15)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--timeout", type=int, default=900)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
        raise ValueError("Invalid GitHub repository")
    if not re.fullmatch(r"[a-fA-F0-9]{40,64}", args.revision):
        raise ValueError("Expected a complete commit SHA")
    token = os.environ["GH_TOKEN"]
    query = urllib.parse.urlencode({"head_sha": args.revision, "branch": "dev", "per_page": 100})
    url = "https://api.github.com/repos/" + args.repository + "/actions/workflows/sonarqube.yml/runs?" + query

    def fetch_runs():
        request = urllib.request.Request(url, headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json", "User-Agent": "SeeLoggyPlus-SonarGate", "X-GitHub-Api-Version": "2022-11-28"})
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)["workflow_runs"]

    result = verify(args.repository, args.revision, fetch_runs, time.monotonic, time.sleep, args.timeout)
    print("SONARQUBE DEV GATE PASS: " + args.revision + " " + result["html_url"])


if __name__ == "__main__":
    main()
