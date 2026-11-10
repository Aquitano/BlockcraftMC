# Duell

`paper/duell` runs 1v1 games in the `duell` group: VS, SG, SuperJump, Sumo and MLG Rush. It resets gameplay between
matches without restarting the server. `/forcemap` decides the mode here, and `/forcemap sumo` forces Sumo for the next
duel.

## Sumo

The circular stone ring is generated in `duell:sumo`, so it needs no `/setup` locations or imported map. Both players
fight with empty hands, and punches knock back without health damage. Falling below the ring's loss height gives the
opponent a point, resets both players and freezes them for a moment. At the round time limit, the player with more
points wins, or equal scores draw.

Each knockout counts as a kill for stats and network XP. The damage-free win achievement excludes Sumo.

## Tours

The vote menu offers `Einzelspiel` and `3-Modi-Tour`. A tour requires both participants to select it before the lobby
countdown finishes. It schedules three distinct playable modes. Each player's mode vote nominates that mode, and the
remaining places are drawn randomly. A forced mode takes one place. Fewer than three playable modes falls back to a single
game with an explanation.

- Every tour plays all three games, including at 2:0. A drawn game consumes its place, and equal game wins after the third
  game draw the tour.
- Leaving or vanishing during a tour forfeits the whole series, including during transitions.
- Kills and deaths accumulate across the tour. The final result grants win coins, XP and the account-level win or loss
  once.
- The same pair keeps its format and mode preferences for a rematch. A new opponent starts with fresh choices.

## Rating

`rating.enabled` defaults to `false`, so single games and tours are unrated while still earning XP, coins and statistics.
Historical Elo is preserved. Enabling the flag exposes a separate rating choice, and a series is rated only when both
players choose it. A decided rated series settles Elo once, including forfeits. Rematches reset rating consent.

## Combat style

1.8 combat has no attack cooldown and deals 1.8's weapon damage. Its rods, snowballs, eggs and pearls knock players back
for no damage, so a melee hit right after a rod hit lands as in 1.8. `pvp.legacy-combat` sets the default, and the other
style applies only when both players choose it. MLG Rush always plays 1.8.

## Ranked queue

`/ranked` in a lobby queues a player for a rated series, and parties cannot queue. The proxy pairs players by Elo for the
current season (`ranked` in its `config.yml`) and sends the pair to an empty server of `ranked.group`, which on-demand
starts if the group sleeps. Once both stand on it, the proxy sends each a `RankedMatch` message. That server then plays
their series rated, whatever `rating.enabled` and the rating vote say. A rematch is unrated unless both vote for it.

`ranked.start-rating` and `ranked.k-factor` in the proxy's config must match BlockcraftAPI's `elo` section.

Seasons last three months (`Season`), so every January, April, July and October everyone starts again at the start
rating. `/elo` keeps the past seasons' tables.

## Direct challenges

`/duel <player>` reserves a private Duell server and moves the challenger there to choose the rules. Both players must
start in lobbies, outside parties and the ranked queue. Ranked selection is available only when the Duell server enables
rated play. Other players cannot join that arena while it is reserved. Accepted rules also apply to rematches.

The proxy's `duels` section sets the setup, invitation and transfer durations and the request cooldown. Failed or cancelled
transfers return players already on the private server to the lobby.

## MLG Rush

See [MLG Rush](../mlgrush/README.md#in-duell) for native settings and its map file.
