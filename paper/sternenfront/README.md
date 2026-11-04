# Sternenfront

A team shooter in an original space-opera setting: two factions fight over command posts on a desert world with
blasters, grenades, jump packs, shield walls, heroes with lightsabers and one relic blade that turns its carrier into
the round's champion. It is a game in the genre of large sci-fi objective shooters, not a copy of one: the factions,
the map, the weapons, the heroes and the relic are our own. The heroes borrow the genre's words (Lichtschwert, Jedi,
Sith, die Macht), but no characters or hilt designs; every name lives in `messages.yml`.

Every number named below as a key is in [config.yml](src/main/resources/config.yml), most with a comment.

## The round

**Factions.** Up to `teams.players-per-team` humans each. Players pick a faction and a class in the lobby; whoever
picks no faction is seated on the smaller one.

- **Freie Kolonien** (orange): miners and smugglers, scrappy gear, a landed freighter as their base in the western canyon.
- **Hegemonie** (light blue): battle-droid infantry, clean steel, a landing platform with a dropship in the east.

**Bots.** With `bots.enabled`, one human can start a round, and bots fill each faction to `bots.fill-per-team` fighters
including humans. Without it a round needs two humans. A human joining a running round takes a seat and a bot leaves.
Bots use their class's health and weapon and the round's bolts, heat, reinforcements and respawn delay, but never
become heroes. A bot kill pays `bots.kill-points` battle points and counts on the sidebar; it pays no coins, kill stats
or streak credit. The class menu's **Bot-Stärke** button opens a vote for **Leicht**, **Normal** or **Veteran**. The
most votes choose the difficulty, ties are random, and without votes `bots.difficulty` applies. Voting closes when the
match starts. `bots.profiles` holds each difficulty's tuning, and `bot/Bots` and `bot/Objectives` hold the behaviour.
A bot keeps a visible target until another enemy is substantially closer, so nearby enemies trading places do not
repeatedly reset its reaction delay.

**Command posts.** Three posts (A, B, C) lie between the two headquarters. Standing in a post's zone moves its capture
towards the faction with more players there, up to three times as fast with a bigger lead. An enemy post is first
neutralised, then taken. A post's coloured blocks show its owner from anywhere on the map. Every capture pays coins.

**Reinforcements.** Each faction starts with `tickets.start`. Every death costs one, and every `tickets.bleed-seconds`
the faction holding more posts drains the other by the difference. A faction at zero loses. After `countdown.round`
seconds the faction with more left wins, or it is a draw.

### Acts

A round runs through three acts, and acts only move forward. The next act starts when the lower faction's
reinforcements fall to a share of `tickets.start`, or after a set time. `acts` in the config holds both.

- **Gefecht:** the opening, with the rules above.
- **Landungsangriff:** the round picks a priority post, the **Schwerpunkt** (`PriorityPost`). It counts more when
  bleeding and draws the bots of both sides. The trailing faction's deploy menu offers the **Landungsschiff**, a landing
  beside that post. Bots keep deploying at their headquarters.
- **Letztes Gefecht:** the trailing faction is worked out once more and holds for the rest of the round; the landing
  moves to its side of the same post. Every post now counts more when bleeding. That faction's heroes cost less and it
  may field one more. A sandstorm shortens the bots' sight, and the pressure music starts if it was not playing yet.

### Deploying

A dead player watches their killer, then the map from above. The `respawn.seconds` wait counts from the death, so the
kill cam costs no time. The deploy menu offers the four roles, the faction's heroes and every spawn: the headquarters,
each post the faction owns that no enemy stands in and, for the trailing faction from the assault on, the landing.
Whoever chooses nothing goes to the headquarters after `respawn.auto-seconds`. Enemies inside a headquarters take
damage every second.

### Classes

Both factions fill the same four roles with the same blasters, under their own names. Only the heavy differs in what
it carries. `classes.<faction>.<role>` sets each body's health, speed and scale, for players and bots alike.

| Role | Blaster | Freie Kolonien | Hegemonie |
| --- | --- | --- | --- |
| Soldier | K-7 Karabiner | Milizionär: Schürfgranate, Adrenalin | Linieneinheit L-4: Ionengranate, Überlastung |
| Heavy | R-40 Repetierer | Minenarbeiter: Bauschild, Sprengladung | Brecher-Einheit X-9: Schildwand, Belagerungsmodus |
| Scout | LB-9 Langbüchse | Spurenleser: Sprungmodul | Jäger-Einheit J-3: Schubdüsen |
| Medic | P-2 Pistole | Feldsanitäter: Heilstation | Wartungseinheit W-2: Reparaturfeld |

Right click fires, held down for automatic fire. Blasters have no ammunition: every shot adds heat, and a gun at full
heat vents before it fires again. F uses the first ability and Q the second, with the weapon still in hand. The
Langbüchse is hitscan (`combat.precision-hitscan`); every other player blaster and every bot weapon fires bolts that
can be dodged at range. Grenades and mining charges hurt their thrower as well as enemies; teammates remain safe.

The heavies' signature abilities are a trade rather than a win. The Sprengladung's long fuse makes it strong against
whoever holds a spot and easy to walk away from. Belagerungsmodus roots its unit, which is what a Sprengladung is made
for.

`streaks.orbital-strike` kills in one life grant one use of the Kommandant's Orbitalschlag, thrown with Shift+F. Dying
removes an unused strike.

### Heroes

Kills, captures and neutralised posts earn **battle points**; assists, kills inside a friendly post and healing an ally
pay less (`heroes.points`, `SupportRewards`). Self-damage, environmental damage, overhealing and automatic regeneration
pay no healing points. At `heroes.cost` the deploy menu's hero row unlocks. A faction fields `heroes.per-faction` heroes
at once, each only once.

Each faction has a saber hero and a gun hero: Jedi-Ritter and Kopfgeldjäger for the Kolonien, Sith-Inquisitor and
Kommandant for the Hegemonie. Heroes have their own health and do not heal, and everyone sees a boss bar with each
hero's name and health. A saber hero holds right click to guard, parries by timing it and leaps with a second jump.

Left click sweeps a saber-colored crescent through every enemy ahead, including when aimed at air. The resource-pack
model has a white edge and a tinted trail. Wall-clipped hit rays position its segments, each lasting
`presentation.saber-trail-ticks` under the shared effect cap. Each enemy can take damage or block only once per swing.
`combat.melee` sets the reach, arc angle, cooldown and animation duration. Defaults are 3.5 blocks, 120 degrees,
12 ticks between swings and a four-tick sweep. Successive swings alternate direction. Switching weapons or guarding
cancels the remaining sweep. The relic blade uses the same attack.

### The relic blade

`relic.first-seconds` into the round, the **Ahnenklinge** appears in the temple ruin on the northern cliff, an equal
distance from both headquarters. Whoever walks into it becomes the champion: more health, speed, a glowing outline, the
blade instead of their blaster, the saber heroes' guard and a leap. The champion has a hero's boss bar and no healing,
but takes no slot from the faction, and a hero cannot take the relic. The blade returns `relic.respawn-seconds` after
its carrier falls.

### Speeders

Each faction has `speeders.per-faction` single-seat speeders on pads at its headquarters. Right click boards your
faction's empty speeder and Shift dismounts. W accelerates, S brakes and then reverses, A/D steer, and sprint boosts
while the budget lasts. Right click or attack fires the nose cannon. The cannon shares the rider's blaster heat, so
boarding does not skip a vent. Fast enough, a speeder rams enemies at a cost to its own health. A destroyed speeder
explodes in its rider's name and dismounts the rider unharmed. Abilities, melee, handheld weapons and battlefield
controls are disabled while riding.

### Battlefield controls

Right click the labelled buttons with your weapon in hand. Either faction can use them, and each has its own timing in
`battlefield`.

- The refinery's vent releases damaging steam over the shortcut it denies, which hurts both factions, including the
  player who activated it.
- The wreck's stern bulkhead cuts the corridor to the engine room and the bridge ladder. The hull's flank breaches
  remain open.
- The bazaar's shutters close the centre of its northern gate. The side passages remain open.

## Run and test

```powershell
.\gradlew.bat stagePlugins
docker compose up -d mariadb velocity lobby sternenfront
```

Join through Velocity and run `/play sternenfront`. While the mode is tested, the lobby's navigator shows it only to
players with `blockcraft.lobby.preview` (premium and every team rank); `/play` still works for everyone.
With bots enabled, one player starts the countdown;
`/start` shortens it. The jar supplies defaults. Existing config files should contain only operator overrides;
old full copies can pin obsolete bot damage and timings until those values are removed or updated.

`./gradlew :paper:sternenfront:test` runs the unit tests. These need a client:

- A round through all three acts: the dropship flying in, a landing, the sandstorm.
- A rigged fighter seen from another fighter, from spectator mode and in the owner's own F5 view.
- Hits on the Brecher, whose body looks larger than its hitbox.
- The Langbüchse's zoom with FOV effects on and off, and the Music slider muting the soundtrack and the cantina.

### Capture a bot for debugging

Operators and players with `blockcraft.sternenfront.debug` can use `/sfdebug` to enable NPC inspection. Right click a
bot to save the preceding `debug.history-ticks` and following `debug.capture-ticks` to
`plugins/Sternenfront/debug/npc-*.json`. History begins when inspection is enabled. `/sfdebug capture` records the bot
under the crosshair; `/sfdebug off` restores normal right-click controls while pending captures finish. A disappearing
bot produces a partial report. `BotDebug` defines what a report holds.

## Client behaviour

- **Zoom.** Sneaking with the Langbüchse slows the walk by `scope.slowdown`, which the client draws as a narrower view.
  A player who turned FOV effects off in their accessibility settings sees no zoom, only the slower walk.
- **Music.** The soundtrack and cantina use Minecraft's Music category, so each player's Music slider controls them,
  including muting them at zero, while weapon effects remain in the Players category. There is no second music toggle.
- **Rigged bodies.** Fighters see each other as `appearance/RigBody` models: `appearance.droids` for the Hegemonie,
  `appearance.infantry` for the Kolonien, humans and bots alike. This needs no skin change and no additional plugin. The
  owner keeps their normal first-person and F5 appearance, and spectators see the original bodies so the kill camera
  stays usable. The combat hitbox stays the underlying fighter's size, even on the Brecher.
- **Tab list.** Hiding a player's real body for the rig also takes them out of that viewer's tab list while they are
  deployed. This is accepted for now.
- **Hit marker.** The marker under the crosshair is a subtitle queued through `Notifications` at reward priority, so an
  important title still wins.
- **Effects.** `presentation.effects` turns off everything but bolt trails, thrusters and plain explosions. Scorch marks
  and blade cuts are display models and change no blocks. `Spectacle` runs the dropship and the sandstorm. The storm is
  particles sent to each player, with none under a roof and no shader, so nothing outlasts the round.

## Sounds

Every sound of the round plays the pack's `blockcraft:sternenfront.<name>` (`SoundCue`).

Lightsaber sounds use downloaded CC0 recordings for ignition, retraction, hum, swings, impacts, clashes,
deflection and throwing. [Offline regeneration](../../resourcepack/SABER_SOURCES.md) is documented with the pack.

Blasters use MikeE63's CC0 recordings. Scott Buckley's *Legionnaire (Original)* and *Juggernaut* provide opening,
battle, pressure and result cues under CC BY 4.0. The pack includes [the credits](../../CREDITS.md), and the round
links the composer. [Regeneration](../../resourcepack/BATTLE_AUDIO.md) covers the edits and checksums.

## The map: Wrack von Tessar

The map is the saved template [`worlds/sternenfront`](../../worlds/sternenfront). Compose and
`./gradlew :paper:runServer -Pgame=sternenfront` restore it before every start like any other map, so a round always
begins on the saved blocks. Its region files go through git-lfs. Its secrets are in
[EASTER_EGGS.md](../../EASTER_EGGS.md#sternenfront).

A desert basin between canyon walls, about 190 by 140 blocks, with a broken plateau and far mesas behind the walls. The
cliffs are the real edge; the world border stands behind them.

- **West:** the Kolonien camp under a long patched hauler on four legs: containers slung on its flanks, a plank ramp
  into its hold, a railed lookout deck on its back and a leaning crane. Spawn spots and speeder pads lie under its belly.
- **East:** the Hegemonie's raised octagonal deck on pylons, with the spawn and pads in the hall underneath, a broad ramp
  up to the deck, a dropship on top and chevron blast walls in front.
- **A, Raffinerie (north):** a paved yard between copper tanks and two pressure tanks, a catwalk over its north edge,
  and works along the cliff behind each landing ground: a pipe rack, a sooted stack and a control house.
- **B, Wrack (centre):** one long cruiser from the buried nose in the north to the engine bells in the south, broken
  in the middle. Ribs stand round the post, staggered openings cross the gap, breaches cross each half, a corridor runs
  along each keel behind bulkheads, a stair climbs to the upper deck and a ladder to the bridge.
- **C, Basar (south):** a paved square behind a gate, market stalls under faded cloth with one red and one teal awning,
  small houses on the corners, flat-roofed houses along the cliff, the cantina in the west and a warehouse in the east.
- **Temple ruin (north edge):** where the relic appears, carved into the cliff.
- **The lanes:** dry washes, rock fins, mesas, boulders, torn hull plates, lost containers, crashed fighters, bones and a
  pipeline on trestles, one after another, with worn paths on the ground and ripples only where nobody walks.

The posts lie on the north-south centre line, so every post is as far from one base as from the other. Each post has a
landing ground open to the sky at x -22 and x 22 at its z, flat for at least 7 by 7 blocks and clear for 6.5 blocks round
its centre. Three speeder pads per faction fit under each base (at the spawn's x, z 10, 16 and 22), and a craft two wide
that steps one block can drive from them to every landing ground.

Every position the round uses lives in `blockcraft/Sternenfront/arena.yml` inside the template: the play area, the
headquarters, the posts with their coloured blocks and bot routes, the controls and the secrets. `TessarMap` reads it at
start and the plugin refuses to start without it. To change the map, use `/build` and `/map save` as for any template
and edit `arena.yml` by hand where a position moved. Keep a post's coloured blocks as they are: the round recolours
every listed block to the post's owner, and they start white at the start of each round. A control's door panels must
be air in the template, and the round opens any left closed. Replays record only the play area, so the far mesas beyond
it are missing there.

## Models

Everything a player holds or wears is a `PackModel`, built from cuboids with vanilla textures like the Zombies guns,
more detailed. Nexo is not needed for the round: nothing in it is furniture a player sits on or breaks.

Bodies, speeders, dropships and the effect models come from `resourcepack/SternenfrontArt.java`
(`./gradlew :resourcepack:generateSternenfrontArt`). The code refers to them by key
(`Items.of(Material.PAPER).model(Key.key("blockcraft", ...))`) without a `PackModel`, so a model renamed there needs its
call site changed too. `:resourcepack:generateHud` regenerates the HUD icons and bars.

## Later

Later changes: a second map, coin-unlocked class variants
through `UnlockableKit`, a lobby stand with status signs and a figure.
