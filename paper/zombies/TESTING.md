# Playtest Außenposten

Use a local Zombies server with the bundled arena and a disposable database.
Join through Velocity with `/play zombies`. Run `/setup check` before starting.
Keep the normal 30-wave settings for a balance playtest.

For a short mechanics check, stop the local server and change its Zombies `config.yml`:

```yaml
countdown:
  intermission: 5
waves:
  total: 3
  boss-every: 3
```

Change only these keys in the existing file. Keep the other countdown and wave settings.
Restore the defaults before a balance playtest.
A short round checks transitions and the boss. It does not establish the balance of a full match.

## Arena route

The courtyard starts open. Copper doors connect it to four wings.
Coordinates below are relative to `origin`, which defaults to world `(0, 96, 0)`.

| Area | Door price | Station position | What to check |
| --- | ---: | --- | --- |
| Courtyard | Open | Armory `(-4, 0, 12)` | Buy every gun, replace a held gun, buy ammo, and refuse duplicate purchases. |
| Courtyard | Open | Armor `(4, 0, 12)` | Buy iron armor, then diamond armor. Refuse a third upgrade. |
| East depot | 500 | Mystery box `(36, 0, -10)` | Draw a weapon that the player does not own. Check the two-weapon limit. |
| North workshop | 750 | Upgrade `(10, 0, -36)` | Refuse upgrades before power. After power, improve damage and refill the selected gun once. |
| South generator room | 1,000 | Power `(-10, 0, 36)` | Turn on power for the whole team. Refuse a second payment. |
| West clinic | 750 | Perks `(-36, 0, 10)` | Refuse purchases before power. Check extra hearts, faster reload, faster revive, and slowing bullets. |
| West clinic | 750 | Team revive `(-36, 0, -10)` | After power, recover both downed and bled-out teammates. Refuse payment when nobody needs help. |

Open each wing and walk to its stations. Check that the floating labels identify the door price and station.
Each bought door rolls up row by row. Turning on the power lights the bulbs outward from the generator, including the jeep's headlights.
Read the five notes: one in the radio shelter and one in each wing.
The first read of each note pays coins and counts toward five. Reading a note again only opens it.
Try a purchase with too little gold, then move away with a shop open and try again.
Neither attempt should spend gold or grant an item.

## Barricades and combat

1. Defend the four courtyard windows. Enemies must spawn inside the enclosed rooms, approach the boards, and walk through after breaking them. They must not teleport into the courtyard.
2. Open all wings. Approach each broken barricade. Players must remain outside the spawn rooms.
3. Defend each wing's outer window. Check all eight breach locations across the map.
4. Sneak at a broken window without nearby enemies. Check that all four boards return and repair gold stops at the per-wave limit.
5. Approach the window with an enemy nearby. Repair must stop. Leave one repaired board and confirm that an arriving enemy breaks it.
6. Fire every gun at body and head height. Check wall obstruction, spread, range, cooldown, empty magazines, and finite reserves.
7. Start a reload, then buy ammo or an upgrade. Check that the pending reload does not change the refill later.
8. Use the knife and collect each powerup. Check team-wide ammo, double gold, and instant kills.
9. Take damage at full food. Health must stay unchanged for five seconds, then recover through the mode's regeneration.
10. Let an Inferno blast or Exploder explosion defeat the last fighter on the final wave. The result must remain a loss without clear rewards or another wave.
11. Use the courtyard features and each wing's cover (racks, jeep, generator, operating room) to break line of sight. Check that enemies can still follow both routes around the cover.
12. Listen at the start of each wave for the horn, and during intermissions for groans behind the boards. A wave clear shows a title.

## Player states and integration

| Setup | Expected behavior |
| --- | --- |
| One player | The countdown starts. Quick Revive saves the player once and consumes the perk. |
| Two to four players | Enemy counts scale with the connected squad at each wave's start. A temporary death does not reduce that count. |
| A downed teammate | Movement stops and health stays at one. Holding sneak nearby with line of sight fills revive progress. Moving away resets it. |
| A teammate bleeds out | The player spectates until the next intermission, loses perks, and retains weapons, armor, and gold. |
| A player joins during combat | The player watches and cannot shoot, buy, collect powerups, or affect the team. |
| A fighter leaves | The player records a loss. The next wave uses the smaller squad. Losing the last fighter ends the round. |
| Staff freezes a fighter | Door purchases and shooting stop. Unfreezing restores those interactions. |
| A clear or team wipe | Rewards, stats, sidebar, recap, and the restart agree on the outcome. |
| The resource pack is declined | Guns, controls, menus, and navigator entries remain readable. |
| The resource pack is applied | Check every weapon in first person, third person, and the hotbar. |

During a local match, run `/map save` after opening a door and breaking a barricade.
The live match must retain those changes. After a restart, the published template must have closed doors and intact boards.
Repeat the save twice in quick succession. The open door must remain usable after both saves.
Use the [local map inspector](../../dev/map-inspector/README.md) to inspect the saved geometry and setup markers.
Check lighting, floating text, weapon models, and combat pacing in a Minecraft client.
