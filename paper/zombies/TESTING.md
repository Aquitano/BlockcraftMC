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
13. Reach wave 7. It sends only black hellhounds under its own title. Get slowed by a Giftzombie from wave 12 on.
14. When three enemies remain in a wave, check that they speed up, and that a wave with nobody alive spawns its next enemy at once.
15. Let an Exploder come within four blocks. It hisses, glows, smokes and rushes for 1.5 seconds. Shoot one before it goes off and nothing explodes; let another go off and the blast hurts less at its edge and throws you back. A wave drops at most two powerups.

## Special enemies

Each kind joins the mix at its `first-wave`. To meet one early, set its `first-wave: 1` and a high `weight` in `enemies`.

1. Kriecher (wave 8): half-size zombies come through one window in threes. They are fast and hard to hit, and each counts once in the wave's enemy count.
2. Schildträger (wave 11): it carries a black shield with a white band in its off hand. Shots from the front deal a quarter of their damage with the shield-block sound. A teammate shooting from its side or back deals full damage.
3. Frost-Zombie (wave 13): standing within three blocks slows you and ices the screen edges. A hit adds more frost and ice shards. Stay in its aura until the overlay is strong. You never take freeze damage, and health only drops from its hits.
4. Brandstifter (wave 15): it holds a bottle and throws nothing from behind the boards. Inside, it backs away when you close in. About every five seconds it lobs a Molotov at you. The ground burns for four seconds and hurts you each second while you stand in the flames. Step out and the damage stops.
5. Elektro-Zombie (wave 18): within ten blocks, a crackling spark line runs from its head to you about every four seconds. Stand with two teammates within four blocks of each other. The zap jumps to both, weaker each time. Spread out and it hits only one of you.
6. Brüter (wave 21): a large, slow zombie. When it dies, four Kriecher burst out where it stood. The wave bar and sidebar count them, and the wave clears only after they die. Kill one outside its window and its brood still has to break in.
7. Schattenläufer (wave 23): a black zombie that stands still and silent while you look at it. Turn away and it rushes you. Losing sight of it behind cover also lets it move. Check that a frozen one still drops when shot off a ledge.
8. During a blood moon the special kinds also run faster. A bounty can mark any of them, including a breeder's crawlers.

## Wave events

To force one event, set `events.from-wave: 1`, `events.chance: 1` and every other event's `weight: 0`.
Each event shows a title with a sound and a chat line, and its name and goal appear in the sidebar under EREIGNIS until it ends.

1. Blutmond: the wave starts with a red title and a deeper horn instead of the normal one. The sky turns to midnight, red dust drifts around the fighters and the enemies, and the enemies move visibly faster. Each kill pays double gold. When the wave ends, the sky returns to dusk.
2. Stromausfall: buy the power first; the event never comes without it. Mid-wave the bulbs go out from the generator outward and everyone sees darkness briefly. Perks, upgrade and team revive refuse with the outage notice. Sneak beside the generator: the action bar counts to 100 %, moving away resets it, and the fixer gets gold when the lights come back. Let a second outage run to the end of the wave and the power must return by itself.
3. Kopfgeld: one enemy glows with a gold name tag. Kill it and the team reads who earned the bonus. Let another bounty run out and it expires with a chat line; a bounty exploder that blows itself up pays nobody.
4. Giftnebel: open a second room first; the event never comes with only the courtyard open. A warning names the room you stand in and thin gas rises. After five seconds the room fills: staying inside poisons, slows and hurts you, and the hits can down you and be revived as usual. Step into another room and the hits stop.
5. Boss and hound waves never get an event, and no wave gets two. Lose the round or stop the server during each event: the sky, the lights after a restart, and the enemy names must be back to normal.

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
| The resource pack is declined | Guns, controls, menus, and navigator entries remain readable. Runners wear orange, tanks iron, exploders a TNT block and the Inferno a carved pumpkin. Shield bearers wear chainmail and their shield, frost zombies light blue with a packed ice block, arsonists soot brown with a honey bottle, electric zombies yellow, breeders a pink chestplate at their large size, shadow walkers black, and crawlers stay bare and small. A Molotov in flight is a honey bottle. |
| The resource pack is applied | Check every weapon in first person, third person, and the hotbar. Runners wear goggles, tanks a riot helmet, exploders dynamite, Giftzombies a gas mask and the Inferno a burning pumpkin. Shield bearers wear a crested great helm, frost zombies an ice crown with icicles, arsonists a beanie and red bandana, electric zombies a copper helmet with a glowing lightning rod, breeders egg sacs, shadow walkers a black hood with glowing eyes, and crawlers red eye clusters with mandibles. A Molotov is a burning bottle in hand and in flight. |

During a local match, run `/map save` after opening a door and breaking a barricade.
The live match must retain those changes. After a restart, the published template must have closed doors and intact boards.
Repeat the save twice in quick succession. The open door must remain usable after both saves.
Use the [local map inspector](../../dev/map-inspector/README.md) to inspect the saved geometry and setup markers.
Check lighting, floating text, weapon models, and combat pacing in a Minecraft client.
