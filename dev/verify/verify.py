#!/usr/bin/env python3
"""Drives a real Minecraft client on an isolated network; see dev/verify/README.md."""
import argparse
import functools
import json
import math
import os
import shutil
import socket
import subprocess
import sys
import time
import zipfile
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "dev/verify/client-mod/run/screenshots"
PORT = int(os.environ.get("VERIFY_PORT", "25599"))
STACK = ("mariadb", "velocity", "sternenfront", "client")
PLAYER = "Verify"
HQ_X = 55
MODEL_ABOVE_FEET = 2
HERO_NAMES = {"jedi": "Obi-Wan", "commander": "Captain Rex", "sith": "Count Dooku", "bounty-hunter": "Jango"}
DEFAULT_WINDOW = (960, 540, 2)
COMMON_SCALES = [(960, 540, 2), (1366, 768, 3), (1920, 1080, 4)]
ALL_SCALES = COMMON_SCALES + [(1366, 768, 2), (1920, 1080, 2), (1920, 1080, 3), (2560, 1440, 3), (2560, 1440, 4), (2560, 1440, 6)]


def compose(*args, capture=False, timeout=None):
    env = {key: value for key, value in os.environ.items() if key != "COMPOSE_FILE"}
    command = ["docker", "compose", "-p", "bcverify", "--project-directory", str(ROOT), "-f", "dev/verify/compose.yml", *args]
    return subprocess.run(command, cwd=ROOT, env=env, check=True, text=True, capture_output=capture, timeout=timeout)


def rcon(command, service="sternenfront"):
    try:
        return compose("exec", "-T", service, "rcon-cli", command, capture=True, timeout=30).stdout.strip()
    except subprocess.CalledProcessError as failure:
        raise RuntimeError(f"rcon on {service} failed for {command!r}: {failure.stderr.strip()}") from failure


class Client:
    def __init__(self, timeout=150):
        self.socket = socket.create_connection(("127.0.0.1", PORT), timeout=timeout)
        self.reader = self.socket.makefile("r", encoding="utf-8")

    def __call__(self, cmd, **fields):
        self.socket.sendall((json.dumps({"cmd": cmd, **fields}) + "\n").encode())
        line = self.reader.readline()
        if not line:
            raise ConnectionError("The client closed the control connection")
        reply = json.loads(line)
        if not reply.pop("ok"):
            raise RuntimeError(f"{cmd}: {reply.get('error')}")
        return reply

    def until(self, what, condition, seconds=60, every=0.5):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            value = condition()
            if value:
                return value
            time.sleep(every)
        raise TimeoutError(f"Waited {seconds}s for {what}")


class Run:
    """One scenario's folder: its report, its frames and a contact sheet, so a look at the result costs one image."""

    def __init__(self, scenario):
        self.scenario = scenario
        self.name = f"{time.strftime('%H%M%S')}-{scenario}"
        self.folder = SHOTS / self.name
        self.frames = 0
        self.report = {"scenario": scenario, "map": Path(running_template()).name, "folder": str(self.folder)}

    def shot(self, client, label=None):
        label = label or f"{self.frames:03d}"
        self.frames += 1
        client("screenshot", name=f"{self.name}/{label}")
        return str(self.folder / f"{label}.png")

    def finish(self, failure=None):
        if failure is not None:
            self.report["failed"] = f"{type(failure).__name__}: {failure}"
        if self.folder.is_dir():
            frames = sorted(self.folder.glob("*.png"))
            if len(frames) > 1:
                columns = min(4, len(frames))
                sheet = self.folder / "contact.jpg"
                # Frames of several window sizes: rebuilding the filter at each new size would throw away the tiles so far.
                subprocess.run(["ffmpeg", "-v", "error", "-y", "-reinit_filter", "0", "-pattern_type", "glob", "-i", str(self.folder / "*.png"), "-vf",
                                f"scale=480:-2,tile={columns}x{(len(frames) + columns - 1) // columns}", "-frames:v", "1", str(sheet)], check=True)
                self.report["contact_sheet"] = str(sheet)
        else:
            self.folder.mkdir(parents=True)
        (self.folder / "report.json").write_text(json.dumps(self.report, indent=1, ensure_ascii=False))


def wait_for_client(seconds=600):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            client = Client()
            client("status")
            return client
        except (OSError, ValueError):
            if not compose("ps", "-q", "--status", "running", "--status", "restarting", "client", capture=True).stdout.strip():
                raise RuntimeError("The client stopped, perhaps on a failed build:\n" + compose("logs", "--no-log-prefix", "--tail", "15", "client", capture=True).stdout)
            time.sleep(2)
    raise TimeoutError("The client never opened its control port")


def join(client, host="velocity:25565"):
    def connected():
        status = client("status")
        if status["screen"] in ("TitleScreen", "DisconnectedScreen"):
            client("connect", host=host)
        return status["in_world"]

    client.until("the world", connected, 300, 3)
    client.until("the welcome screen or the game", lambda: client("status")["screen"] in ("", "MultiButtonDialogScreen"), 240)
    if client("status")["screen"]:
        client("press", label="Los geht")
        client.until("the welcome screen to close", lambda: not client("status")["screen"], 30)
    rcon(f"op {PLAYER}")
    rcon("gamerule send_command_feedback false")
    return client("status")


def on_field(status):
    return status["in_world"] and status["gamemode"] == "adventure" and status["y"] < 100 and not status["screen"] \
        and status["hotbar"][0]["item"] != "air" and status["hotbar"][8]["item"] != "ender_eye"


def choose_faction(client, faction):
    """Picks a side from the waiting lobby's hotbar item, which is only there before a round starts."""
    hotbar = client("status")["hotbar"]
    slot = next((index for index, item in enumerate(hotbar) if "Fraktion" in item.get("name", "")), None)
    if slot is None:
        raise RuntimeError(f"No faction item, so no waiting lobby: `restart` first. Hotbar: {[item.get('name') for item in hotbar]}")
    client("hotbar", slot=slot)
    # Aiming at the scenery in front of the lobby's spawn, the item's click never opens the menu.
    client("look", yaw=client("status")["yaw"], pitch=-60)
    settle(client, 3)
    client("tap", key="use")
    client.until("the faction menu", lambda: client("status")["screen"] == "ContainerScreen", 20, 0.3)
    choice = next(item for item in client("screen")["slots"] if faction in item.get("name", ""))
    client("click", slot=choice["slot"])
    settle(client, 4)
    client("close")


def into_round(client, skip_intro=True, faction=None):
    """From wherever the client is onto the field, with the round's own commands and keys."""
    if not client("status")["in_world"]:
        join(client)
    if faction:
        choose_faction(client, faction)
    if on_field(client("status")):
        return client("status")
    if client("status")["screen"]:
        client("close")
    client("command", text="start")
    client.until("the intro or the field", lambda: client("status")["gamemode"] == "spectator" or on_field(client("status")), 60)
    if skip_intro and client("status")["gamemode"] == "spectator":
        client("hold", key="sneak", ticks=10)
    client.until("the field", lambda: on_field(client("status")), 90)
    return client("status")


def settle(client, ticks=30):
    """Lets the client draw and react: its own clock, since a software renderer may run far below real time."""
    start = client("status")["tick"]
    client.until("the client to tick", lambda: client("status")["tick"] >= start + ticks, 120, 0.2)


def set_window(client, width, height, scale):
    client("window", width=width, height=height)
    client("gui_scale", value=scale)
    settle(client, 8)
    return client("status")


@functools.cache
def running_template():
    """The template the game server started with, so that scenarios use the map it is playing."""
    return compose("exec", "-T", "sternenfront", "printenv", "BLOCKCRAFT_WORLD_TEMPLATE", capture=True).stdout.strip()


def keep_running_map():
    """Recreating the game server without this would put it back on the default map."""
    if "VERIFY_MAP" not in os.environ and compose("ps", "-q", "--status", "running", "sternenfront", capture=True).stdout.strip():
        os.environ["VERIFY_MAP"] = Path(running_template()).name


@functools.cache
def arena():
    return yaml.safe_load((ROOT / running_template().lstrip("/") / "blockcraft/Sternenfront/arena.yml").read_text())


def arena_positions():
    """Open-ground spots the map's author set: the named views, each headquarters and the first spawn of each post."""
    spots = {key: arena()[key] for key in ("overview", "spectator") if key in arena()}
    spots.update({f"{faction}_hq": headquarters["spawns"][0] for faction, headquarters in arena()["headquarters"].items()})
    spots.update({f"post_{post['key']}": post["spawns"][0] for post in arena()["posts"]})
    return spots


def post_spots():
    return [spot for key, spot in arena_positions().items() if key.startswith("post_")]


def to_enemy(client):
    """Goes from post to post until an enemy is outlined; False if none is near any of them."""
    for spot in post_spots():
        rcon(f"tp {PLAYER} {spot}")
        settle(client, 8)
        if enemies(client):
            return True
    return False


def as_trooper(client):
    """Back to a class weapon if the last scenario left the player a hero or the champion."""
    if not client("status")["hotbar"][0]["item"].endswith("_hoe"):
        rcon(f"kill {PLAYER}")
        client.until("the next deployment", lambda: on_field(client("status")) and client("status")["hotbar"][0]["item"].endswith("_hoe"), 60, 1)


def enemies(client, radius=64):
    """Feet of outlined bot bodies out on the map, those in sight first: the relic glows too, and anything at a headquarters is inside a zone that hurts a visitor."""
    found = [{**each, "y": each["y"] - MODEL_ABOVE_FEET} for each in client("entities", radius=radius)["entities"]
             if each["glowing"] and each.get("model", "").endswith("_outlined_torso") and abs(each["x"]) < HQ_X]
    return sorted(found, key=lambda each: not each["in_sight"])


def stand_near_enemy(client, reach=4):
    """Goes to open ground beside the nearest outlined enemy, on a side with a clear shot if one has it, where the bots can see and shoot back."""
    if not enemies(client):
        client.until("an enemy near a post", lambda: to_enemy(client), 90, 2)
    target = enemies(client)[0]
    open_ground = None
    for dx, dz in ((reach, 0), (-reach, 0), (0, reach), (0, -reach)):
        spot = f"{target['x'] + dx} {target['y']} {target['z'] + dz}"
        rcon(f"tp {PLAYER} {spot}")
        settle(client, 4)
        if client("status")["in_wall"]:
            continue
        open_ground = open_ground or spot
        seen = enemies(client)
        if seen and seen[0]["in_sight"]:
            return target
    if open_ground is None:
        raise RuntimeError("Every spot beside the enemy is inside a wall")
    rcon(f"tp {PLAYER} {open_ground}")
    settle(client, 4)
    return target


def summarize(events, gui_width):
    """What a timeline amounts to: what a player heard and read, without reading every entry."""
    sounds, health, subtitles, titles = {}, [], set(), set()
    widths, chat, overflows = [], [], set()
    for event in events:
        kind, value = event["kind"], event["value"]
        if event.get("width", 0) > gui_width:
            overflows.add(f"{kind} {event['width']}px of {gui_width}: {value}")
        if kind == "sound":
            sounds[value] = sounds.get(value, 0) + 1
        elif kind == "health":
            health.append(float(value))
        elif kind == "actionbar" and value:
            widths.append(event["width"])
        elif kind == "subtitle" and value:
            subtitles.add(value.encode("unicode_escape").decode())
        elif kind == "title" and value:
            titles.add(value)
        elif kind == "chat":
            chat.append(value)
    return {"sounds": sounds, "lowest_health": min(health, default=None), "actionbar_widths": [min(widths, default=0), max(widths, default=0)],
            "subtitles": sorted(subtitles), "titles": sorted(titles), "chat_lines": chat, "OVERFLOWS": sorted(overflows)}


def scales(client, run, full=False):
    """Windows and GUI scales that players use: what would not fit, as widths the client's own font measured."""
    rows = []
    for width, height, scale in ALL_SCALES if full else COMMON_SCALES:
        status = set_window(client, width, height, scale)
        hud = client("hud")
        rows.append({"window": status["window"], "scale": status["gui_scale"], "gui": status["gui_size"],
                     "actionbar": hud["actionbar"]["width"], "actionbar_overflows": hud["actionbar"]["overflows"],
                     "subtitle_overflows": hud["subtitle"]["overflows"], "bossbars": len(hud["bossbars"]),
                     "sidebar_share_of_width": hud["sidebar"]["share_of_width"]})
        run.shot(client, f"{width}x{height}_{scale}")
    set_window(client, *DEFAULT_WINDOW)
    run.report["scales"] = rows


def back_on_field(client):
    """Out of a deploy menu after a death: the round deploys anyone who does not choose within its time limit."""
    client.until("the next deployment", lambda: on_field(client("status")), 60, 1)
    stand_near_enemy(client)


def fight(client, run, seconds=20, frames=8):
    """Keeps beside the nearest outlined enemy, since outlines show through walls, and shoots it, recording what the player saw, heard and read."""
    as_trooper(client)
    stand_near_enemy(client)
    client("hotbar", slot=0)
    start = client("status")["tick"]
    gap, next_shot = seconds / max(frames, 1), time.monotonic()
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if not on_field(client("status")):
            back_on_field(client)
        client("hold", key="use", ticks=30)
        seen = enemies(client)
        if seen:
            client("look_at", x=seen[0]["x"], y=seen[0]["y"] + 1.1, z=seen[0]["z"])
        if time.monotonic() >= next_shot:
            run.shot(client)
            next_shot += gap
            stand_near_enemy(client)
        time.sleep(0.3)
    run.report.update(summarize(client("events", since=start)["events"], client("hud")["gui_width"]))
    run.report["hits"] = run.report["sounds"].get("blockcraft:sternenfront.hitmarker", 0)


def fire(client, run):
    """Holds the trigger at a wall and photographs the shooter's view, which nothing should cover."""
    as_trooper(client)
    client("hotbar", slot=0)
    start = client("status")["tick"]
    client("hold", key="use", ticks=30)
    run.report["frames"] = client("capture", name=f"{run.name}/fire", frames=6, every=5)["files"]
    run.report.update(summarize(client("events", since=start)["events"], client("hud")["gui_width"]))


def menus(client, run):
    """Dies to reach the deploy menu, and photographs it at each scale with the cursor out of the way."""
    rcon(f"kill {PLAYER}")
    client.until("the deploy menu", lambda: client("status")["screen"] == "ContainerScreen", 40, 0.3)
    run.report["title"] = client("screen")["title"]
    for width, height, scale in COMMON_SCALES:
        set_window(client, width, height, scale)
        client("mouse", x=2, y=2)
        settle(client, 4)
        run.shot(client, f"{width}x{height}_{scale}")
    set_window(client, *DEFAULT_WINDOW)
    client("close")


def tour(client, run):
    """Flies a spectator over the spots the map's author set."""
    client("option", render_distance=12)
    rcon(f"gamemode spectator {PLAYER}")
    try:
        for key, spot in arena_positions().items():
            rcon(f"tp {PLAYER} {spot}")
            settle(client, 40)
            run.shot(client, key)
    finally:
        rcon(f"gamemode adventure {PLAYER}")
        client("option", render_distance=6)


def bodies(client, run):
    """Follows the nearest enemy from close by, so the rig's walking and firing poses can be looked at."""
    target = stand_near_enemy(client, reach=3)
    client("look_at", x=target["x"], y=target["y"] + 1.1, z=target["z"])
    run.report["frames"] = client("capture", name=f"{run.name}/body", frames=8, every=4)["files"]


def use_ability(client, run, key, label):
    """Presses an ability's key at the nearest enemy and photographs what happened, on the HUD and in the world."""
    seen = enemies(client)
    if seen:
        client("look_at", x=seen[0]["x"], y=seen[0]["y"] + 1.1, z=seen[0]["z"])
    client("tap", key=key)
    settle(client, 4)
    run.shot(client, f"{label}_1")
    settle(client, 12)
    run.shot(client, f"{label}_2")


def hero(client, run, hero_name="jedi"):
    """Deploys as a hero, which the verify config makes free, and uses both abilities beside an enemy."""
    rcon(f"kill {PLAYER}")
    client.until("the deploy menu", lambda: client("status")["screen"] == "ContainerScreen", 40, 0.3)
    wanted = HERO_NAMES[hero_name]
    slots = client("screen")["slots"]
    choice = next((slot for slot in slots if slot.get("name", "").startswith(wanted)), None)
    if choice is None:
        raise RuntimeError(f"{wanted} is not on this side's menu, so pick the side with --faction from the waiting lobby")
    client("click", slot=choice["slot"])
    run.report["hero_card"] = choice.get("lore")
    settle(client, 6)
    home = next(slot for slot in client("screen")["slots"] if slot.get("name", "").startswith("Hauptquartier"))
    client("click", slot=home["slot"])
    client.until("the hero on the field", lambda: client("status")["gamemode"] == "adventure" and not client("status")["screen"]
                 and client("status")["y"] < 100, 60, 0.5)
    settle(client, 10)
    status = client("status")
    run.report["held"] = status["hotbar"][0].get("name")
    run.report["offhand"] = status["offhand"].get("name")
    run.report["max_health"] = status["max_health"]
    client.until("an enemy near a post", lambda: to_enemy(client), 60, 2)
    start = client("status")["tick"]
    run.shot(client, "stands")
    use_ability(client, run, "swap", "first")
    use_ability(client, run, "drop", "second")
    client("hold", key="use", ticks=20)
    settle(client, 20)
    run.shot(client, "weapon")
    run.report["hud"] = client("hud")["actionbar"]["text"].encode("unicode_escape").decode()
    run.report.update(summarize(client("events", since=start)["events"], client("hud")["gui_width"]))


def relic(client, run):
    """Waits for the relic, which the verify config brings early, walks onto it and uses the champion's abilities."""
    spot = arena()["relic"]
    client.until("the relic to appear", lambda: any("Dunkelschwert" in event["value"] for event in client("events", since=0)["events"]), 120, 2)
    rcon(f"tp {PLAYER} {spot}")
    client.until("the blade in hand", lambda: "Dunkelschwert" in client("status")["hotbar"][0].get("name", ""), 20, 0.5)
    start = client("status")["tick"]
    run.report["held"] = client("status")["hotbar"][0].get("name")
    client.until("an enemy near a post", lambda: to_enemy(client), 60, 2)
    use_ability(client, run, "swap", "grip")
    use_ability(client, run, "drop", "grapple")
    run.report["hud"] = client("hud")["actionbar"]["text"].encode("unicode_escape").decode()
    run.report.update(summarize(client("events", since=start)["events"], client("hud")["gui_width"]))


# Each scenario with the state it needs: "field" means deployed in a running round.
def replay(client, run, round_seconds=75):
    """Fights through a short round, watches its recording on a replay server and photographs the bots' bodies there."""
    keep_running_map()
    os.environ["VERIFY_OVERRIDES"] = "short-round-overrides.yml"
    newest = "SELECT replay_uuid FROM bc_replays ORDER BY started_at DESC LIMIT 1"
    try:
        compose("up", "-d", "--force-recreate", "--no-deps", "sternenfront")
        compose("--profile", "replay", "up", "-d", "--force-recreate", "--no-deps", "replay")
        wait_for_server()
        wait_for_server("replay")
        client = wait_for_client()
        # Read only now: stopping the old server published whatever it was recording.
        known = database(newest)
        into_round(client)
        client("hotbar", slot=0)
        deadline = time.monotonic() + round_seconds + 60
        recording = known
        while recording == known and time.monotonic() < deadline:
            if on_field(client("status")) and enemies(client):
                stand_near_enemy(client)
                client("hold", key="use", ticks=30)
                seen = enemies(client)
                if seen:
                    client("look_at", x=seen[0]["x"], y=seen[0]["y"] + 1.1, z=seen[0]["z"])
            time.sleep(1)
            recording = database(newest)
        if recording == known:
            raise TimeoutError("The round left no recording")
        run.report["recording"] = recording
        run.report["ended"] = database(f"SELECT CONCAT(end_reason, ' after ', duration_ticks, ' ticks') FROM bc_replays WHERE replay_uuid = '{recording}'")
        client.until("the world after the round", lambda: client("status")["in_world"], 120, 2)
        registered = lambda: "Registered replay" in compose("logs", "--since", "3m", "--no-log-prefix", "velocity", capture=True).stdout
        client.until("the proxy to know the replay server", registered, 60, 2)
        client("command", text=f"replay play {recording}")
        arrived = lambda: f"{PLAYER} -> replay has connected" in compose("logs", "--since", "3m", "--no-log-prefix", "velocity", capture=True).stdout
        client.until("the replay server", arrived, 90, 3)
        for attempt in range(40):
            if bodies_shown(client):
                break
            if attempt % 10 == 0:
                run.shot(client)
            time.sleep(3)
        else:
            raise TimeoutError("The playback drew no bodies")
        client("command", text="replay speed 0.5")
        most, deadline = 0, time.monotonic() + 120
        while run.frames < 8 and time.monotonic() < deadline:
            torsos = [each for each in client("entities", radius=250)["entities"] if each.get("model", "").endswith("_torso")]
            most = max(most, len(torsos))
            if not torsos:
                time.sleep(1)
                continue
            target = min(torsos, key=lambda each: each["distance"])
            client("look_at", x=target["x"], y=target["y"] - 1, z=target["z"])
            if target["distance"] <= 14:
                run.shot(client)
                time.sleep(0.7)
                continue
            # Flying forward keeps its height, so the viewer has to sink towards the body as well.
            status = client("status")
            if status["y"] > target["y"] + 1:
                client("hold", key="sneak", ticks=20)
            if math.hypot(status["x"] - target["x"], status["z"] - target["z"]) > 8:
                client("hold", key="forward", ticks=20)
            time.sleep(1)
        run.report["bodies"] = most
        run.report["recap"] = [event["value"] for event in client("events", since=0)["events"] if event["kind"] == "chat"
                               and any(word in event["value"] for word in ("Schlacht", "Rang", "Befördert", "Rekord", "Auszeichnung"))]
        client("command", text="replay stop")
    finally:
        del os.environ["VERIFY_OVERRIDES"]
        compose("--profile", "replay", "stop", "replay")
        # Later scenarios need a full-length round, and this container would otherwise keep the short one.
        compose("up", "-d", "--force-recreate", "--no-deps", "sternenfront")


def bodies_shown(client):
    return sum(each.get("model", "").endswith("_torso") for each in client("entities", radius=250)["entities"])


def database(query):
    return compose("exec", "-T", "mariadb", "mariadb", "-ublockcraft", "-pblockcraft", "blockcraft", "-Nse", query, capture=True).stdout.strip()


SCENARIOS = {"replay": (replay, "joined"), "scales": (scales, "field"), "fight": (fight, "field"), "fire": (fire, "field"), "menus": (menus, "field"),
             "tour": (tour, "field"), "bodies": (bodies, "field"), "hero": (hero, "field"), "relic": (relic, "field")}


def run_scenario(name, faction=None, **options):
    run = Run(name)
    client = wait_for_client()
    scenario, needs = SCENARIOS[name]
    failure = None
    try:
        if needs == "field":
            into_round(client, faction=faction)
        scenario(client, run, **options)
    except Exception as error:
        failure = error
    finally:
        for key in ("use", "sneak", "attack", "forward"):
            try:
                client("release", key=key)
            except (OSError, RuntimeError, ValueError):
                break
    run.finish(failure)
    print(json.dumps({key: value for key, value in run.report.items() if key != "frames"}, indent=1, ensure_ascii=False))
    return 1 if failure else 0


def merge_models():
    """Copies the pack BetterModel built on the game server into resourcepack/src, so the next stage serves it."""
    built = ROOT / "build/verify-bettermodel.zip"
    built.parent.mkdir(exist_ok=True)
    compose("cp", "sternenfront:/data/plugins/BetterModel/build.zip", str(built))
    target = ROOT / "resourcepack/src/assets/bettermodel"
    shutil.rmtree(target, ignore_errors=True)
    with zipfile.ZipFile(built) as models:
        for name in models.namelist():
            if name.startswith("assets/bettermodel/") and not name.endswith("/"):
                path = ROOT / "resourcepack/src" / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(models.read(name))


def build_map(name="sturmwerft"):
    """Runs a generated map's functions on a bare server over a flat sea and saves the result to its world template."""
    subprocess.run([sys.executable, "-I", str(ROOT / f"dev/mapgen/{name}.py")], check=True)
    built = ROOT / "build/mapgen" / name
    (ROOT / "worlds" / name).mkdir(parents=True, exist_ok=True)
    compose("--profile", "mapgen", "rm", "-sf", "mapbuilder")
    subprocess.run(["docker", "volume", "rm", "-f", "bcverify_mapbuilder"], check=True, capture_output=True)
    compose("--profile", "mapgen", "up", "-d", "--no-deps", "mapbuilder")
    deadline = time.monotonic() + 240
    while "Done (" not in compose("logs", "--no-log-prefix", "--tail", "40", "mapbuilder", capture=True).stdout:
        if time.monotonic() > deadline:
            raise TimeoutError("The map builder never finished starting")
        time.sleep(3)
    say = lambda command: print(rcon(command, "mapbuilder")[:140])
    compose("cp", str(built / "datapack"), f"mapbuilder:/data/world/datapacks/{name}")
    compose("exec", "-T", "mapbuilder", "mkdir", "-p", "/data/world/blockcraft/Sternenfront")
    compose("cp", str(built / "arena.yml"), "mapbuilder:/data/world/blockcraft/Sternenfront/arena.yml")
    compose("cp", str(built / "map.yml"), "mapbuilder:/data/world/blockcraft/map.yml")
    say("reload")
    time.sleep(8)
    say(f"datapack enable \"file/{name}\"")
    time.sleep(8)
    for area in (built / "forceload.txt").read_text().split("\n"):
        if area:
            say(f"forceload add {area}")
    time.sleep(30)
    # Twice, because a fill silently skips a chunk that was still loading the first time.
    for _ in range(2):
        say(f"function {name}:build")
        say(f"function {name}:scenery")
        time.sleep(8)
    say("forceload remove all")
    say("save-all flush")
    time.sleep(10)
    compose("--profile", "mapgen", "stop", "mapbuilder")
    template = ROOT / "worlds" / name
    staging, previous = template.with_name(f".{name}.new"), template.with_name(f".{name}.old")
    for leftover in (staging, previous):
        shutil.rmtree(leftover, ignore_errors=True)
    staging.mkdir()
    for part in ("level.dat", "data", "dimensions", "blockcraft"):
        compose("cp", f"mapbuilder:/data/world/{part}", str(staging / part))
    if not (staging / "blockcraft/Sternenfront/arena.yml").is_file():
        raise RuntimeError(f"The export in {staging} is incomplete; {template} is untouched")
    template.rename(previous)
    staging.rename(template)
    shutil.rmtree(previous)


def wait_for_server(service="sternenfront", seconds=240):
    deadline = time.monotonic() + seconds
    while "Done (" not in compose("logs", "--no-log-prefix", "--since", "5m", "--tail", "60", service, capture=True).stdout:
        if time.monotonic() > deadline:
            raise TimeoutError(f"{service} never finished starting")
        time.sleep(3)


def stage():
    subprocess.run(["./gradlew", "stagePlugins", "--console=plain", "-q"], cwd=ROOT, check=True)


def refresh_models():
    """After changing a body: the game server builds the new pack, which goes into resourcepack/src and out to the client."""
    subprocess.run([sys.executable, "-I", str(ROOT / "resourcepack/RigModels.py")], check=True)
    stage()
    compose("up", "-d", "--force-recreate", "--no-deps", "sternenfront")
    time.sleep(20)
    wait_for_server()
    merge_models()
    stage()
    compose("up", "-d", "--force-recreate", "--no-deps", "velocity")


def field(text):
    key, _, value = text.partition("=")
    try:
        return key, json.loads(value)
    except json.JSONDecodeError:
        return key, value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    commands.add_parser("up")
    restart = commands.add_parser("restart", help="restart services, by default the game server, picking up staged jars")
    restart.add_argument("services", nargs="*", default=["sternenfront"])
    down = commands.add_parser("down")
    down.add_argument("-v", action="store_true", help="also delete the database and the client's download cache")
    logs = commands.add_parser("logs")
    logs.add_argument("service")
    logs.add_argument("--tail", default="60")
    command = commands.add_parser("rcon")
    command.add_argument("text")
    commands.add_parser("join")
    commands.add_parser("map", help="generate Sturmwerft and save it to worlds/sturmwerft")
    commands.add_parser("models", help="after changing a body: regenerate its models and pack, and serve them")
    round_ = commands.add_parser("round", help="start a round and deploy")
    round_.add_argument("--watch-intro", action="store_true")
    scenario = commands.add_parser("scenario")
    scenario.add_argument("name", choices=sorted(SCENARIOS))
    scenario.add_argument("--full", action="store_true", help="scales: every combination instead of the common ones")
    scenario.add_argument("--hero", choices=sorted(HERO_NAMES), help="hero: which one, of the side being played")
    scenario.add_argument("--faction", help="pick this side first, from the waiting lobby: Republik or Separatisten")
    call = commands.add_parser("c", help="one client command, as: c hold key=use ticks=20")
    call.add_argument("cmd")
    call.add_argument("fields", nargs="*")
    args = parser.parse_args()

    if args.action == "up":
        keep_running_map()
        compose("up", "-d", "--build", *STACK)
    elif args.action == "restart":
        keep_running_map()
        compose("up", "-d", "--force-recreate", "--no-deps", *args.services)
    elif args.action == "down":
        compose("--profile", "replay", "--profile", "mapgen", "down", *(["-v"] if args.v else []))
    elif args.action == "logs":
        compose("logs", "--no-log-prefix", "--tail", args.tail, args.service)
    elif args.action == "rcon":
        print(rcon(args.text))
    elif args.action == "map":
        build_map()
    elif args.action == "models":
        refresh_models()
    elif args.action == "join":
        print(json.dumps(join(wait_for_client()), indent=1, ensure_ascii=False))
    elif args.action == "round":
        print(json.dumps(into_round(wait_for_client(), not args.watch_intro), indent=1, ensure_ascii=False))
    elif args.action == "scenario":
        return run_scenario(args.name, **({"full": True} if args.full else {}), **({"hero_name": args.hero} if args.hero else {}), faction=args.faction)
    elif args.action == "c":
        print(json.dumps(Client()(args.cmd, **dict(map(field, args.fields))), indent=1, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
