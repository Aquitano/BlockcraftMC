# Measure Minecraft performance

Keep captures under `run/performance/`, which Git ignores. The summary tool requires Python 3.10 or newer.
Reading JFR files also requires the JDK's `jfr` executable on `PATH`. It processes recordings locally.

Create the output directory from the repository root:

```powershell
New-Item -ItemType Directory -Force run/performance | Out-Null
```

## Capture a server scenario

1. Start the [local network](../../README.md#run-locally) with the same Paper build, heap settings, map, and player count for each comparison.
2. Allow chunks to load and the JVM to warm up for at least one minute. Keep the server ticking when empty if measuring idle time.
3. In the server console, run `jfr start`. Record a fixed route or interaction sequence for 120 seconds, then run `jfr stop`.
4. Save the filename printed by Paper. Run `mspt` and keep its recent average, minimum, and maximum tick durations with the capture.
5. Summarize the recording from the repository root:

```powershell
python dev/performance/summarize.py jfr run/paper/debug/server-<timestamp>.jfr > run/performance/server-summary.json
```

For Docker, copy the printed recording path out of the service before summarizing it:

```powershell
docker compose cp lobby:/data/debug/server-<timestamp>.jfr run/performance/server.jfr
python dev/performance/summarize.py jfr run/performance/server.jfr
```

Minecraft's JFR tick events contain window averages. A p99 of those averages does not describe the slowest individual
ticks. A tick has a 50 ms budget at 20 TPS, but a low average can conceal a pause. Inspect both the `mspt` maximum
and JFR GC pauses. CPU percentages use all logical processors as their denominator, so one saturated server thread
can appear as a small percentage. The sampled Blockcraft frames identify where to investigate; they are not method timings.
Check `gc_collections_by_cause` too. In the [idle baseline](baseline-2026-10-12.md), heap inspection at recording start
and stop caused both GC pauses. Keep this recording overhead separate from gameplay pauses.

For CPU and GC captures of a local Velocity process, use the JDK tool instead. First find its PID with `jcmd -l`:

```powershell
$javaProcessId = 12345 # Replace with the Velocity PID.
$recording = Join-Path (Get-Location) 'run/performance/velocity.jfr'
jcmd $javaProcessId JFR.start name=blockcraft settings=profile duration=120s "filename=$recording"
```

Wait for the recording to finish before passing it to `summarize.py jfr`. Velocity recordings have no Minecraft tick events.

## Capture client frame times on Windows

Use the [PresentMon console application](https://github.com/GameTechDev/PresentMon/blob/main/README-ConsoleApplication.md)
to capture the Minecraft process. Select its PID rather than all `javaw.exe` processes. This example uses the
documented 1.x CSV columns:

```powershell
$clientProcessId = 12345 # Replace with the Minecraft client PID.
.\PresentMon.exe --process_id $clientProcessId --v1_metrics --timed 120 --terminate_after_timed --output_file run/performance/client.csv
python dev/performance/summarize.py frames run/performance/client.csv --column MsBetweenPresents
```

Replace `.\PresentMon.exe` with the path to the downloaded executable.

The input must contain one frame stream from one process. Use a consistent resolution, fullscreen mode, render distance,
simulation distance, FPS cap, VSync setting, resource pack, mods, and shaders. Keep the game focused during capture.
Run the same route after chunks have loaded. Record cold chunk loading separately.

The summary reports frame-time p50/p95/p99, average FPS, and 1% low FPS. Here, 1% low means 1000 divided by the mean
duration of the slowest 1% of frames, rounded up to at least one frame. Different tools can use other definitions.
Presentation intervals measure the application's frame cadence, not how long every frame remained on the display.
Blank, zero, non-finite, or negative intervals fail validation; inspect such rows before using the capture.
Do not substitute sampled FPS readings or CPU work duration for intervals between frames.

## Compare the same workload

Record the commit, Paper/Velocity/client versions, CPU/GPU, Java version, heap flags, player count, map, and settings
beside each capture. Repeat each scenario three times and compare the middle result. Avoid builds and other benchmarks
on the same machine while recording. A local client and server compete for CPU, so keep that arrangement consistent.

Use these scenarios to exercise the features in this repository:

- An empty lobby, followed by two players and then the intended player count.
- The same lobby route with effects enabled and disabled. Include trails, pets, holograms, and AFK labels.
- Repeated parkour starts, checkpoint falls, finishes, and leaderboard refreshes on a configured course.
- Menu opening while greeting, reward, and leaderboard queries run. Check delayed replies and database pool pressure.
- A complete round in each game, including kills, spectator messages, rewards, and rematches.
- Seasonal effects on a separate test setup at the appropriate date. Do not change the operating-system clock of a live server.

Change one variable per comparison. Treat an FPS increase accompanied by missing effects as a behavior change that needs
review. Bot connections can exercise networking and server ticks, but cannot establish rendering performance.

For database work, run `EXPLAIN` and `ANALYZE` against representative disposable data. Compare rows examined, selected
indexes, sort work, and query duration. The greeting and parkour indexes have a measured query-plan improvement;
that does not establish an overall server or client speedup.

## Verify map setup

Run `/lobbyset check` after placing the [lobby activity locations](../../EASTER_EGGS.md#configure-the-lobby-map).
It works from the console too. It checks missing positions, pressure plates, duplicate course stations, course worlds,
the navigator's jump destination, and distinct Easter hiding places. It does not prove that jumps are reachable or
that the pond is fishable. Complete the course and fish with a client, then use `/map save` to retain the setup.

The network birthday still requires the actual opening date in both calendar configs. An empty date leaves it disabled.
