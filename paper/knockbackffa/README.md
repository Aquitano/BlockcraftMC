# Knockback FFA

`paper/knockbackffa` runs continuous knockback combat in the `knockbackffa` server group. It builds a quartz centre,
four side islands and connecting bridges in an empty world. Players join immediately and respawn after each fall.
The mode has no round timer, winner or round recap.

## Commands

- `/play knockbackffa` joins from another backend. The lobby navigator has the same entry.
- `/ffa spectate` watches in vanilla spectator mode. `/ffa play` rejoins the fight.
- `/hub` returns to the lobby.
- `/stats [player]` shows kills, falls and earned coins. `/top kills` shows the kill leaderboard.

## Combat and rewards

Every player gets a knockback stick. Its appearance follows the shared choice made in the lobby's Extras menu or
BedWars' stick menu. The appearance changes no combat properties.

The last accepted hit within `hit-credit-seconds` earns a kill when the victim falls. Every death resets the victim's
streak. Leaving or switching to spectator while recently hit also counts as a death. A short spawn protection
prevents both receiving and dealing hits.

`rewards.kill-coins` controls the coin reward. The same attacker and victim can earn it only once per
`rewards.same-player-cooldown-seconds`. That cooldown survives a reconnect within the same server process.
Kills and falls still count during the cooldown. Persistent statistics use `knockbackffa_stats`.

Right-click an available emerald block to receive one special item:

- A rescue rod launches its holder upward once.
- A repulsor snowball pushes a player away on an accepted hit.

Pickups return after `pickups.respawn-seconds`. `launch.enabled` optionally launches active players together every
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
- Use both pickups, including the rescue rod while looking into empty air.
- Change the shared stick appearance, then switch between BedWars, MLG Rush and FFA.
- Check spawn protection, `/ffa spectate`, `/staff`, vanish and freeze.
- Repeat a kill before and after the reward cooldown. Enable the launch event and check its countdown.

Continuous FFA sessions do not currently produce match replays.
