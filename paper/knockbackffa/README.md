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

Everyone fights with the same kit, and it changes for all active players every `kits.interval-seconds` to a different
one (`FfaKit`). The change takes back thrown projectiles and hands out the new kit in place, without a respawn. The
kits are the plain stick, a stronger stick, a bow, snowballs, a fishing rod, a grapple and ender pearls. The grapple
pulls only once its bobber rests on the ground.

Projectiles never deal damage. Arrow, snowball and rod hits credit the shooter like a stick hit and knock back as in
1.8 (BlockcraftAPI's `LegacyProjectiles`). Pearl hits credit nobody.

The last accepted hit within `hit-credit-seconds` earns a kill when the victim falls. Every death resets the victim's
streak. Leaving or switching to spectator while recently hit also counts as a death. For `spawn-protection-seconds`
after a spawn nobody can hit the player, until the player lands a hit of their own (BlockcraftAPI's
`SpawnProtection`).

Leaving vanish or staff mode returns the player to spectator mode. `/ffa play` rejoins combat.

The same attacker and victim can earn `rewards.kill-coins` only once per `rewards.same-player-cooldown-seconds`. That
cooldown survives a reconnect within the same server process. Kills and falls still count during the cooldown.
Persistent statistics use `knockbackffa_stats`.

`launch.enabled` optionally launches active players together every `launch.interval-seconds`.

## Run locally

Build and stage the plugins, then start the local services:

```powershell
./gradlew.bat :paper:knockbackffa:build stagePlugins
docker compose up -d mariadb velocity lobby knockbackffa
```

Connect to `localhost:25565` and use `/play knockbackffa`. The server publishes `OPEN`, so the proxy can seat new
participants during combat. Capacity, party reservations and empty-server draining use the normal server directory.
The Compose service includes BlockcraftCore and GrimAC. `knockbackffa` is one of the default on-demand groups.

## Verification

`./gradlew.bat :paper:knockbackffa:build` runs the scoring tests. Client checks need at least two accounts:

- Join an occupied arena through `/play` and the navigator.
- Knock a player off, fall without a hit, and leave while recently hit. Check attribution and streak reset.
- Lower `kits.interval-seconds` and go through every kit: bow, snowball and rod hits credit the kill, the grapple
  pulls only from a grounded bobber, and a pearl thrown before a fall does not land after the respawn.
- Change the shared stick appearance, then switch between BedWars, MLG Rush and FFA.
- Check spawn protection, `/ffa spectate`, `/staff`, vanish and freeze.
- Repeat a kill before and after the reward cooldown. Enable the launch event and check its countdown.

Continuous FFA sessions do not currently produce match replays: the service does not load BlockcraftReplay.

## Arena rotation

`rotation.enabled` and `rotation.interval-seconds` control the switch between arenas. The timer pauses while nobody
plays. The switch preserves scores, streaks and reward cooldowns. It clears recent hit credit and projectiles, then
respawns active players with fresh spawn protection.
