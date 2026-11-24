# Cores

`paper/cores` plays GommeHD's Cores in the `cores` group: red against blue, two beacons a team. The first team to break
both enemy cores wins, and a round that reaches `countdown.round` is a draw. `config.yml` holds the team size, timings,
radii, kit and loot.

- A core sits in a 3x3x3 cube that nobody can change, so it is only reached from above.
- An enemy who can reach a core mines with Mining Fatigue, which is what makes a beacon take 15 seconds.
- Respawns are unlimited, and combat is 1.8-style. There is no shop.
- Base blocks other than the iron do not break. The rest of the map, and everything players place, does.
- Team chat is the default during a round. A message that starts with `@` goes to everyone.

The map, Himmelsfeste, is built by `SkyMap` into an empty world at every start, so the service needs no world template.
The server keeps that world in `cores-built-maps/` as a template. Every round plays a fresh copy of it, which is thrown
away with the round; two copies take turns, so one is copied anew in the background while the other is played. The next
round follows on the same server, `rounds-in-place` times before it restarts.
