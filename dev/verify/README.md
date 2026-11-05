# Verify

Plays the game with a real Minecraft 26.3 client, so a change can be seen and measured without a person at the keyboard.
It starts its own network in the Docker project `bcverify`: MariaDB, Velocity and a Sternenfront server from
`build/docker`, and the client on a virtual display with Mesa's software renderer. Nothing touches a production service
and nothing is published beyond the loopback control port. Every container has a hard CPU and memory ceiling
(`VERIFY_CLIENT_CPUS`, `VERIFY_SERVER_CPUS`). On a host that also runs the live servers those ceilings reduce contention
but do not remove it, so stop the rig with `down` when it is not in use, and run it elsewhere for long sessions.

```sh
./gradlew stagePlugins
dev/verify/verify.py up          # first start downloads Minecraft
dev/verify/verify.py join        # into the world, resource pack applied, operator rights given
dev/verify/verify.py round       # /start, skip the intro, on the field; --watch-intro keeps it
dev/verify/verify.py scenario fight
dev/verify/verify.py down        # -v also deletes the database and the download cache
```

After `stagePlugins`, `restart` restarts the game server and `join` reconnects; `restart client` or `restart velocity`
picks up a changed mod or a new resource pack.

## Scenarios

Each scenario reaches the state it needs itself, writes a folder under `dev/verify/client-mod/run/screenshots` and prints
a short summary with that folder's path. The folder holds `report.json`, its frames and `contact.jpg`: read that image
first and open a full frame only where it shows something.

- `fight` stands beside an enemy and shoots: sounds, lowest health, every title and marker and chat line, HUD width range.
- `fire` holds the trigger and photographs the shooter's own view.
- `scales` covers common windows and GUI scales and reports what overflows; `--full` covers every combination.
- `menus` photographs the deploy menu at each common scale.
- `bodies` watches an enemy's rig from close by.
- `tour` flies a spectator over the spots the map's author set.
- `hero --hero jedi` deploys as a hero and uses both abilities beside an enemy. Separatist heroes need
  `--faction Separatisten` from the waiting lobby, so `restart` and `join` first.
- `relic` takes the relic and uses the champion's abilities.

`sternenfront-overrides.yml` makes heroes free and brings the relic early on this network only.

## Maps

`dev/verify/verify.py map` runs `dev/mapgen/sturmwerft.py`: it writes the map's blocks as Minecraft functions and its
`arena.yml`, builds them from an endless flat sea on a bare server and saves the result to `worlds/sturmwerft`. The generator
stops if the arena would place a fighter inside a block. To play a map on this network, start it with `VERIFY_MAP=sturmwerft`,
then use the scenarios, `tour` above all. Scenarios and `restart` follow the map the server is running, and each report names it.

## Replays

`dev/verify/verify.py scenario replay` restarts the server with a 75-second round, plays it, starts the replay server, watches the
recording there and reports how many animated bodies it drew.

## Single commands

`dev/verify/verify.py c <command> key=value ...` sends one command. They live in `client-mod/.../Commands.java`.

| Command | Fields | Does |
|---|---|---|
| `status` | | window, GUI scale, position, health, hotbar, FPS |
| `hud` | | action bar, title, subtitle, boss bars, sidebar and chat as text, each with its width |
| `screen` | | the open screen's title, buttons and container slots |
| `press`, `click`, `close` | `label`; `slot`, `button` | use a dialog button or a menu slot |
| `mouse` | `x y` | moves the cursor in window pixels, for a menu screenshot without a tooltip |
| `hold`, `release`, `tap` | `key`, `ticks` | `forward back left right jump sneak sprint attack use swap drop inventory` |
| `hotbar` | `slot` | selects a slot |
| `look`, `look_at`, `aim` | `yaw pitch`; `x y z`; `name` | turns the camera, `aim` to the nearest match |
| `command`, `chat` | `text` | sends either as the player |
| `gui_scale`, `window` | `value`; `width height` | changes the UI scale or the window |
| `option` | `fov render_distance particles perspective hide_hud` | client options |
| `screenshot`, `capture` | `name`; `frames every` | one image, or one every few ticks |
| `entities` | `radius totals_only` | what the client sees around the player |
| `events` | `since` | every change to the HUD, screen, health and chat and every sound played, by client tick, recorded each tick so nothing short-lived is missed |

`verify.py rcon "<command>"` runs a server command, such as `tp` or `damage`.

## Limits

The software renderer runs at a few frames per second, so a capture samples an animation rather than showing every frame.
The default 960x540 at GUI scale 2 has the same layout as 1080p at scale 4 and draws faster. Audio has no output device, so
`events` says which sounds played, not how they sound. A round's state varies, so compare what a report says rather than
pixels between runs. The software renderer's memory grows until the container's ceiling stops the client, after roughly 15 to
25 minutes. Docker restarts it and `join` reconnects.
