import argparse
import csv
import html
import json
import math
import re
import statistics
from collections import defaultdict
from pathlib import Path


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)] if ordered else 0


def distribution(values):
    return {
        "minimum": min(values), "median": statistics.median(values), "maximum": max(values),
        "p95": percentile(values, .95), "p99": percentile(values, .99)
    } if values else {}


def histogram(path):
    classes = {}
    total = 0
    if not path.exists():
        return {"totalBytes": 0, "classes": classes}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        match = re.match(r"\s*\d+:\s+(\d+)\s+(\d+)\s+(\S+)", line)
        if match:
            classes[match[3]] = {"instances": int(match[1]), "bytes": int(match[2])}
        match = re.match(r"Total\s+\d+\s+(\d+)", line)
        if match:
            total = int(match[1])
    return {"totalBytes": total, "classes": classes}


def operations(directory, result):
    values = result.get("operations", [])
    if not values:
        pattern = r"\{operation=(.*?), mib=(\d+), run=(\d+), milliseconds=([\d.]+), count=(\d+)\}"
        for match in re.finditer(pattern, (directory / "console.log").read_text(encoding="utf-8", errors="replace")):
            values.append({"operation": match[1], "mib": int(match[2]), "run": int(match[3]),
                           "milliseconds": float(match[4]), "count": int(match[5])})
    grouped = defaultdict(list)
    for value in values:
        if value["run"] > 0:
            grouped[f"{value['operation']}:{value['mib']}"].append(value)
    return {key: {"milliseconds": distribution([item["milliseconds"] for item in items]),
                  "counts": sorted(set(item["count"] for item in items)), "measuredRepetitions": len(items)}
            for key, items in grouped.items()}


def resource_stats(rows, processors):
    result = {key: distribution([row[key] for row in rows])
              for key in ["heapUsed", "heapCommitted", "workingSet", "privateBytes", "threads"]}
    result["fxLatencyMs"] = distribution([row["fxLatencyNanos"] / 1e6 for row in rows if row["fxLatencyNanos"] > 0])
    if len(rows) > 1:
        seconds = rows[-1]["elapsedSeconds"] - rows[0]["elapsedSeconds"]
        core_seconds = (rows[-1]["cpuNanos"] - rows[0]["cpuNanos"]) / 1e9
        result["cpuAverageCorePercent"] = core_seconds / seconds * 100
        result["cpuAverageMachinePercent"] = core_seconds / seconds / processors * 100
    result["samples"] = len(rows)
    return result


def phase(root, name, partial):
    directory = root / name
    complete = (directory / "result.json").exists()
    if not complete and not partial:
        raise SystemExit(f"{directory}: profile has not completed")
    result = json.loads((directory / "result.json").read_text(encoding="utf-8")) if complete else {}
    rows = []
    with (directory / "samples.csv").open(encoding="utf-8-sig", newline="") as source:
        for row in csv.DictReader(source):
            if any(value is None for value in row.values()):
                continue
            rows.append({key: value if key == "scenario" else float(value) for key, value in row.items()})
    tail = [row for row in rows if row["scenario"] == "tail-soak"]
    if not tail:
        raise SystemExit(f"{directory}: tail phase has not started")
    cutoff = tail[0]["elapsedSeconds"] + result.get("soakSeconds", 1800) - 2
    normal = [row for row in rows if row["scenario"] != "close-sessions" and row["elapsedSeconds"] < cutoff]
    tail = [row for row in tail if row["elapsedSeconds"] < cutoff]
    steady = [row for row in tail if row["elapsedSeconds"] >= tail[-1]["elapsedSeconds"] - 600]
    processors = result.get("processors", 12)
    native = directory / "peak-VM.native_memory.txt"
    native_match = re.search(r"Total: reserved=(\d+)KB, committed=(\d+)KB", native.read_text() if native.exists() else "")
    summary = {
        "complete": complete, "java": result.get("java"), "soakSeconds": result.get("soakSeconds"),
        "soakTicks": result.get("soakTicks"), "tailRowsPerSession": result.get("tailRowsPerSession"),
        "normal": resource_stats(normal, processors), "tail": resource_stats(tail, processors),
        "steadyLast600Seconds": resource_stats(steady, processors), "operations": operations(directory, result),
        "peakHistogram": histogram(directory / "peak-GC.class_histogram.txt"),
        "closedHistogram": histogram(directory / "closed-GC.class_histogram.txt"),
        "nmtPeakCommittedBytes": int(native_match[2]) * 1024 if native_match else None,
        "samplerError": result.get("samplerError")
    }
    if result.get("soakTicks"):
        summary["observedSyntheticTailRowsPerSecond"] = result["soakTicks"] * 500 / result["soakSeconds"]
    return summary, normal


def kernel(directory):
    runs = json.loads((directory / "kernel.json").read_text(encoding="utf-8"))
    result = {}
    for workers in [2, 4, 6]:
        by_query = {}
        for query in ["ABSENT_MARKER", "ERROR", "2026"]:
            measured = [row for row in runs if row["workers"] == workers and row["query"] == query and row["run"] > 0]
            by_query[query] = {"milliseconds": distribution([row["milliseconds"] for row in measured]),
                               "allocatedBytes": distribution([row["allocatedBytes"] for row in measured]),
                               "matches": sorted(set(row["matches"] for row in measured))}
        result[str(workers)] = {"queries": by_query, "sumMedianMilliseconds": sum(row["milliseconds"]["median"] for row in by_query.values())}
    result["burst"] = json.loads((directory / "burst.json").read_text(encoding="utf-8"))
    return result


def chart(summary, destination):
    width, height = 1000, 390
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
             '<rect width="100%" height="100%" fill="#ffffff"/>',
             '<g font-family="Arial, sans-serif" fill="#172b4d">',
             '<text x="30" y="35" font-size="22">Normal-workload memory peaks (MiB)</text>',
             '<text x="30" y="59" font-size="13">Diagnostic GC and close phase excluded; cache budget is not the whole process budget.</text>']
    keys = [("privateBytes", "Private memory"), ("heapUsed", "Java heap used"), ("workingSet", "Working set")]
    maximum = max(summary[phase]["normal"][key]["maximum"] / 2**20 for phase in ["before", "after"] for key, _ in keys)
    for i, (key, label) in enumerate(keys):
        y = 95 + 87 * i
        parts.append(f'<text x="30" y="{y + 23}" font-size="15">{html.escape(label)}</text>')
        for offset, phase_name, color in [(0, "before", "#64748b"), (30, "after", "#167a56")]:
            value = summary[phase_name]["normal"][key]["maximum"] / 2**20
            length = value / maximum * 620
            parts.append(f'<rect x="210" y="{y + offset}" width="{length:.1f}" height="23" rx="3" fill="{color}"/>')
            parts.append(f'<text x="{220 + length:.1f}" y="{y + offset + 17}" font-size="13">{value:.1f}</text>')
    parts += ['<rect x="210" y="363" width="14" height="14" fill="#64748b"/><text x="232" y="375" font-size="13">Before</text>',
              '<rect x="330" y="363" width="14" height="14" fill="#167a56"/><text x="352" y="375" font-size="13">After</text>', '</g></svg>']
    destination.write_text("\n".join(parts), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("build/performance"))
    parser.add_argument("--output", type=Path, default=Path("docs/reports/assets/performance-audit"))
    parser.add_argument("--partial", action="store_true")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    summary = {}
    samples = {}
    for name in ["before", "after"]:
        summary[name], samples[name] = phase(args.root, name, args.partial)
        summary[name]["kernel"] = kernel(args.root / f"{name}-kernel")
    selected = args.root / "selected-tests.json"
    if selected.exists():
        summary["selectedTests"] = json.loads(selected.read_text(encoding="utf-8"))
    smoke = args.root / "smoke.json"
    if smoke.exists():
        summary["smoke"] = json.loads(smoke.read_text(encoding="utf-8"))
    summary["privatePeakReductionPercent"] = (1 - summary["after"]["normal"]["privateBytes"]["maximum"] / summary["before"]["normal"]["privateBytes"]["maximum"]) * 100
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    chart(summary, args.output / "memory-peaks.svg")
    print(json.dumps({"complete": all(summary[name]["complete"] for name in ["before", "after"]),
                      "privatePeakReductionPercent": summary["privatePeakReductionPercent"],
                      "output": str(args.output)}, indent=2))


if __name__ == "__main__":
    main()
