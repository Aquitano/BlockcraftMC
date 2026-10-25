# /// script
# requires-python = ">=3.13"
# dependencies = ["pyyaml==6.0.3"]
# ///

import argparse
import functools
import hashlib
import http.server
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request

import yaml

ROOT = Path(__file__).resolve().parents[2]
VERSION = "5.29"
JAR_HASH = "e830767fa6364b52b9310fb598508ebde7eec5ebe8360a989e240d991af89a3a"


def write_config(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2), encoding="utf8")


def locations(value, prefix):
    if not isinstance(value, dict):
        return
    if all(axis in value for axis in ("x", "y", "z")):
        yield prefix, {axis: float(value[axis]) for axis in ("x", "y", "z")}
        return
    for key, child in value.items():
        yield from locations(child, f"{prefix}/{key}")


def markers(world):
    result = {}
    for path in sorted((world / "blockcraft").rglob("*.yml")):
        relative = path.relative_to(world / "blockcraft")
        if "legacy" in relative.parts or "maps" in relative.parts:
            continue
        data = yaml.safe_load(path.read_text(encoding="utf8"))
        origin = data.get("origin", {}) if isinstance(data, dict) else {}
        if origin:
            data = {key: value for key, value in data.items() if key != "origin"}
        for label, position in locations(data, relative.with_suffix("").as_posix()):
            position = {axis: coordinate + float(origin.get(axis, 0)) for axis, coordinate in position.items()}
            result[label] = {"type": "poi", "label": label, "position": position}
    return {"setup": {"label": "Configured locations", "toggleable": True, "markers": result}}


def main():
    parser = argparse.ArgumentParser(description="Render saved map templates and serve a read-only local 3D viewer.")
    parser.add_argument("--maps", nargs="+", default=["lobby", "bedwars"])
    parser.add_argument("--worlds", type=Path, default=ROOT / "worlds")
    parser.add_argument("--whole-world", action="store_true", help="Render every saved chunk, ignoring the configured build bounds.")
    parser.add_argument("--render-only", action="store_true")
    parser.add_argument("--serve-only", action="store_true")
    parser.add_argument("--port", type=int, default=8100)
    args = parser.parse_args()
    worlds = args.worlds.resolve()
    for group in args.maps:
        if group not in {path.name for path in worlds.iterdir() if path.is_dir()} or not (worlds / group / "level.dat").is_file():
            parser.error(f"No saved world named {group!r} under {worlds}")
    work = ROOT / "run/map-inspector"
    work.mkdir(parents=True, exist_ok=True)
    if not args.serve_only:
        java_home = os.environ.get("JAVA_HOME")
        java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else shutil.which("java")
        if not java:
            parser.error("Set JAVA_HOME to a Java 25 installation.")
        jar = work / f"bluemap-{VERSION}-cli.jar"
        if not jar.exists():
            url = f"https://github.com/BlueMap-Minecraft/BlueMap/releases/download/v{VERSION}/{jar.name}"
            with urllib.request.urlopen(url, timeout=60) as response:
                jar.write_bytes(response.read())
        if hashlib.sha256(jar.read_bytes()).hexdigest() != JAR_HASH:
            parser.error(f"SHA-256 mismatch for {jar}. Remove that file and retry.")
        config = work / "inspect-config"
        write_config(config / "core.conf", {"accept-download": True, "data": "data", "render-thread-count": 2,
                                            "metrics": False, "scan-for-mod-resources": False})
        write_config(config / "webserver.conf", {"enabled": False})
        write_config(config / "webapp.conf", {"webroot": "web", "client-decompression": True, "use-cookies": False,
            "min-zoom-distance": 1, "resolution-default": 0.5, "hires-slider-default": 32,
            "hires-slider-max": 100, "lowres-slider-default": 100, "lowres-slider-min": 50})
        write_config(config / "storages/file.conf", {"storage-type": "file", "root": "web/maps", "compression": "gzip"})
        presets = json.loads(Path(__file__).with_name("maps.json").read_text(encoding="utf8"))
        for group in args.maps:
            value = {"world": str(worlds / group), "dimension": "minecraft:overworld", "name": group,
                     "remove-caves-below-y": -10000, "ignore-missing-light-data": True, "ambient-light": 0.5,
                     **presets.get(group, {}), "marker-sets": markers(worlds / group)}
            if args.whole_world:
                value.pop("render-mask", None)
            write_config(config / "maps" / f"{group}.conf", value)
        subprocess.run([java, "-Xmx2G", "-jar", str(jar), "-c", str(config), "-v", "26.3", "-r", "-f", "-g", "-s",
                        "--markers", "-m", ",".join(args.maps), "-l", str(work / "data/logs/render.log")], cwd=work, check=True)
    if not args.render_only:
        handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(work / "web"))
        with http.server.ThreadingHTTPServer(("127.0.0.1", args.port), handler) as server:
            print(f"Map inspector: http://localhost:{args.port} (Ctrl+C to stop)", flush=True)
            server.serve_forever()


if __name__ == "__main__":
    main()
