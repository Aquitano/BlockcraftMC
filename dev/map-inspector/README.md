# Local map inspection

The standalone [BlueMap CLI](https://bluemap.bluecolored.de/wiki/getting-started/Installation.html)
renders the saved world templates without installing a Minecraft plugin or restarting a server.
It uses Java 25 and `uv`. From the repository root, run:

```powershell
uv run dev/map-inspector/map_inspector.py
```

Open <http://localhost:8100>. Select a map, then use the person button for free flight.
Move with WASD and Space/Shift. Drag to turn the camera. Click a configured-location marker
to identify a spawn, sign, team selector, or player-display position.

The default renders the lobby and BedWars build areas from `worlds`. Change their bounds in
[maps.json](maps.json). To inspect another saved world, use `--maps emeraldrush`.
To include every generated chunk, use `--whole-world`. Large legacy worlds take longer.

```powershell
uv run dev/map-inspector/map_inspector.py --maps lobby bedwars --render-only
uv run dev/map-inspector/map_inspector.py --maps zombies
uv run dev/map-inspector/map_inspector.py --serve-only
uv run dev/map-inspector/map_inspector.py --worlds run/map-review/preview-worlds
```

`--shot` saves screenshots instead of serving, so a change can be looked at without opening a browser. Each shot
names its map, an optional label and BlueMap's camera: the point looked at, then distance, rotation and angle
(radians). It needs a Chromium on the path (or `CHROME`) and renders with software WebGL, so no GPU is required.
`--no-markers` leaves the configured-location markers out of the render for clean pictures.

```powershell
uv run dev/map-inspector/map_inspector.py --maps lobby --no-markers --shot "lobby-castle@-925,30,1565,40,0.9,1.1"
```

The shot lands in `run/map-inspector/shots/lobby-castle.png`. BlueMap draws blocks only. Mobs, NPC figures, holograms,
displays and the seasonal decoration that Lobby sends to clients are not part of the saved world, so neither the
viewer nor a shot shows them; their configured positions appear as markers.

Rendering again refreshes saved terrain and reads location markers from the active map YAML files.
Save in Minecraft with `/map save` before rendering the preview copies. Rendering reads worlds and
writes only `run/map-inspector`. The web server listens on loopback. It does not expose Minecraft
commands or edit the map. Stop it with Ctrl+C.

BlueMap 5.29 is pinned and its CLI download is checked against SHA-256. On first use it also
downloads Mojang's Minecraft 26.3 rendering assets. Use it with a licensed Java Edition installation.
Downloads, rendered tiles, and caches stay under the ignored `run/map-inspector` directory.

Terrain appears in 3D. NPCs and floating text are represented by setup markers, so their real skins,
collision, click handling, and gameplay still need a Minecraft client check. The viewer renders with
vanilla assets and full lighting where saved lighting is absent. Final lighting and the server resource
pack can look different in the client.

For edits, use `/build` for blocks and `/setup lobby ...` for BedWars positions. Save with `/map save`.
Moving existing BedWars selectors and the CPS tester takes a BedWars restart to apply completely.
The local preview saves into `run/map-review/preview-worlds`, not the tracked `worlds` directory.

Zombies includes the saved Außenposten template. After editing the running arena, run `/map save` before inspecting `worlds/zombies`.
Its setup markers include the arena's configured origin offset.
