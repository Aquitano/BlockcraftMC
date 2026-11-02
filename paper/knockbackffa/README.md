# Knockback FFA

`paper/knockbackffa` runs continuous knockback combat in the `knockbackffa` server group. Players fight on islands,
a ring or crossroads in an empty world. Players join immediately and respawn after each fall.
The mode has no round timer, winner or round recap.

## Commands

- `/play knockbackffa` joins from another backend. The lobby navigator has the same entry.
- `/ffa spectate` watches the way every game's spectators do, unseen with the spectator hotbar. `/ffa play` rejoins the fight.
- `/hub` returns to the lobby.
- `/stats [player]` shows kills, falls and earned coins. `/top kills` shows the kill leaderboard.

## Combat and rewards

Every player gets a knockback stick. Its appearance follows the shared choice made in the lobby's Extras menu or
BedWars' stick menu. The appearance changes no combat properties.

Everyone fights with the same kit, and it changes for all active players every `kits.interval-seconds` (120 by
default) to a different one. The sidebar names the kit and counts down; the last five seconds also show above the
hotbar. The change takes back thrown projectiles and hands out the new kit in place, without a respawn:

- Standard: only the stick. Heavy stick: the stick one knockback level higher, at most 5.
- Archer: a bow and `kits.ammo` arrows. Snowman: `kits.ammo` snowballs. Angler: a fishing rod.
- Grapple: a rod that pulls its holder towards the bobber once the bobber rests on the ground, then waits
  `kits.grapple-cooldown-seconds`.
- Enderman: `kits.pearls` ender pearls.

Projectiles never deal damage. Arrow, snowball and rod hits credit the shooter like a stick hit; Paper gives a hit
without any damage no knockback, so snowballs and rods push by `kits.projectile-push`. Pearl hits credit nobody.
Ammunition refills on every respawn.

The last accepted hit within `hit-credit-seconds` earns a kill when the victim falls. Every death resets the victim's
streak. Leaving or switching to spectator while recently hit also counts as a death. A short spawn protection
prevents both receiving and dealing hits.

Leaving vanish or staff mode returns the player to spectator mode. `/ffa play` rejoins combat.

`rewards.kill-coins` controls the coin reward. The same attacker and victim can earn it only once per
`rewards.same-player-cooldown-seconds`. That cooldown survives a reconnect within the same server process.
Kills and falls still count during the cooldown. Persistent statistics use `knockbackffa_stats`.

`launch.enabled` optionally launches active players together every
`launch.interval-seconds`. It is disabled by default.

## Run locally

Build and stage the plugins, then start the local services:

```powershell
./gradlew.bat :paper:knockbackffa:build stagePlugins
docker compose up -d mariadb velocity lobby knockbackffa
```

Connect to `localhost:25565` and use `/play knockbackffa`. The server publishes `OPEN`, so the proxy can seat new
participants during combat. Capacity, party reservations and empty-server draining use the normal server directory.
The Compose service includes BlockcraftCore and GrimAC. Production keeps it under the `unreleased` profile.
To enable on-demand starting later, add `knockbackffa` to the configured on-demand groups.

## Verification

`./gradlew.bat :paper:knockbackffa:build` runs the scoring tests. Client checks need at least two accounts:

- Join an occupied arena through `/play` and the navigator.
- Knock a player off, fall without a hit, and leave while recently hit. Check attribution and streak reset.
- Lower `kits.interval-seconds` and go through every kit: bow, snowball and rod hits credit the kill, the grapple
  pulls only from a grounded bobber, and a pearl thrown before a fall does not land after the respawn.
- Change the shared stick appearance, then switch between BedWars, MLG Rush and FFA.
- Check spawn protection, `/ffa spectate`, `/staff`, vanish and freeze.
- Repeat a kill before and after the reward cooldown. Enable the launch event and check its countdown.

Continuous FFA sessions do not currently produce match replays.

## Arena rotation

`rotation.enabled` turns rotation on, and `rotation.interval-seconds` sets the interval, 300 seconds by default.
The timer pauses while nobody plays. Warnings appear 30 seconds before the switch and during its final five seconds.
The switch preserves scores, streaks and reward cooldowns. It clears recent hit credit and projectiles,
then respawns active players with fresh spawn protection. The sidebar names the current arena.
