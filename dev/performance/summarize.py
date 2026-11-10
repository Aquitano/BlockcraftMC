import argparse
import collections
import csv
import json
import math
import re
import statistics
import subprocess
import sys
from pathlib import Path


def distribution(values: list[float]) -> dict[str, float | int]:
    ordered = sorted(values)
    if not ordered:
        return {"samples": 0}
    return {
        "samples": len(ordered),
        "mean": statistics.fmean(ordered),
        "p50": ordered[math.ceil(len(ordered) * 0.50) - 1],
        "p95": ordered[math.ceil(len(ordered) * 0.95) - 1],
        "p99": ordered[math.ceil(len(ordered) * 0.99) - 1],
        "max": ordered[-1],
    }


def milliseconds(value: str) -> float:
    match = re.fullmatch(r"PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?", value)
    if not match or not any(match.groups()):
        raise ValueError(f"Unsupported JFR duration: {value}")
    hours, minutes, seconds = (float(part or 0) for part in match.groups())
    return (hours * 3600 + minutes * 60 + seconds) * 1000


def recording(path: Path) -> dict:
    events = "minecraft.ServerTickTime,jdk.CPULoad,jdk.GCPhasePause,jdk.GarbageCollection,jdk.ExecutionSample,jdk.NativeMethodSample"
    result = subprocess.run(
        ["jfr", "print", "--json", "--events", events, str(path)],
        check=True, capture_output=True, encoding="utf-8",
    )
    ticks: list[float] = []
    cpu: list[float] = []
    pauses: list[float] = []
    methods: collections.Counter[str] = collections.Counter()
    gc_causes: collections.Counter[str] = collections.Counter()
    execution_samples = 0
    for event in json.loads(result.stdout)["recording"]["events"]:
        values = event["values"]
        match event["type"]:
            case "minecraft.ServerTickTime":
                ticks.append(milliseconds(values["averageTickDuration"]))
            case "jdk.CPULoad":
                cpu.append(100 * (values["jvmUser"] + values["jvmSystem"]))
            case "jdk.GCPhasePause":
                pauses.append(milliseconds(values["duration"]))
            case "jdk.GarbageCollection":
                gc_causes[values["cause"]] += 1
            case "jdk.ExecutionSample" | "jdk.NativeMethodSample":
                execution_samples += 1
                stack = values.get("stackTrace") or {}
                for frame in stack.get("frames", []):
                    method = frame["method"]
                    name = method["type"]["name"].replace("/", ".")
                    if name.startswith("me.blockcraft."):
                        methods[f"{name}.{method['name']}"] += 1
                        break
    return {
        "recording": str(path),
        "server_tick_window_average_ms": distribution(ticks),
        "tick_windows_over_50ms": sum(value > 50 for value in ticks),
        "jvm_cpu_percent_of_machine": distribution(cpu),
        "gc_pause_ms": distribution(pauses),
        "gc_pause_total_ms": sum(pauses),
        "gc_collections_by_cause": dict(gc_causes),
        "execution_samples": execution_samples,
        "nearest_blockcraft_frames": dict(methods.most_common(10)),
        "limits": [
            "Minecraft tick events contain window averages, not individual ticks. Their percentiles can hide stalls.",
            "CPU is normalized across the machine's processors. A saturated server thread may use a small percentage.",
            "Stack counts are sampled attribution, not method durations. No samples does not mean no work.",
            "This recording does not measure client frame times or GPU utilization.",
        ],
    }


def frames(path: Path, column: str, unit: str) -> dict:
    scale = {"ns": 0.000001, "us": 0.001, "ms": 1}[unit]
    times: list[float] = []
    with path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        if not reader.fieldnames or column not in reader.fieldnames:
            raise ValueError(f"CSV has no {column!r} column; found {reader.fieldnames}")
        for row in reader:
            cell = row[column]
            if cell is None or not cell.strip():
                raise ValueError(f"Missing frame time on CSV line {reader.line_num}")
            value = float(cell) * scale
            if not math.isfinite(value) or value <= 0:
                raise ValueError(f"Frame time on CSV line {reader.line_num} must be finite and positive")
            times.append(value)
    if not times:
        raise ValueError("The CSV contains no frames")
    slowest = sorted(times, reverse=True)[:max(1, math.ceil(len(times) * 0.01))]
    return {
        "capture": str(path),
        "frame_time_ms": distribution(times),
        "average_fps": 1000 / statistics.fmean(times),
        "one_percent_low_fps": 1000 / statistics.fmean(slowest),
        "frames_over_16_67ms": sum(value > 1000 / 60 for value in times),
        "frames_over_50ms": sum(value > 50 for value in times),
        "limits": [
            "Input must contain one interval per presented frame from one Minecraft process, not FPS samples or CPU frame work time.",
            "1% low is 1000 divided by the mean duration of the slowest ceil(1% of frames).",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Summarize local Minecraft performance captures without uploading them.")
    commands = parser.add_subparsers(dest="command", required=True)
    jfr = commands.add_parser("jfr", help="Read a Java Flight Recorder capture with the JDK's jfr tool")
    jfr.add_argument("path", type=Path)
    client = commands.add_parser("frames", help="Read a CSV containing individual presented-frame intervals")
    client.add_argument("path", type=Path)
    client.add_argument("--column", required=True)
    client.add_argument("--unit", choices=("ns", "us", "ms"), default="ms")
    args = parser.parse_args()
    try:
        summary = recording(args.path) if args.command == "jfr" else frames(args.path, args.column, args.unit)
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(f"Could not summarize capture: {error}", file=sys.stderr)
        return 1
    print(json.dumps(summary, indent=2, allow_nan=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
