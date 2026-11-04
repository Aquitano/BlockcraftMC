# Sternenfront

A team shooter in an original space-opera setting: two factions fight over command posts on a desert world with
blasters, grenades, jump packs, shield walls, heroes with lightsabers and one relic blade that turns its carrier into
the round's champion. It is a game in the genre of large sci-fi objective shooters, not a copy of one: the factions,
the map, the weapons, the heroes and the relic are our own. The heroes borrow the genre's words (Lichtschwert, Jedi,
Sith, die Macht), but no characters or hilt designs; every name lives in `messages.yml`.

## The round

Two factions, up to `teams.players-per-team` human players each (6 by default):

- **Freie Kolonien** (orange): miners and smugglers, scrappy gear, a landed freighter as their base in the western canyon.
- **Hegemonie** (light blue): battle-droid infantry, clean steel, a landing platform with a dropship in the east.

Players pick a faction and a class in the lobby; whoever picks no faction is seated on the smaller one.

With `bots.enabled`, one human can start a round. Bots fill each faction to `bots.fill-per-team` fighters including
humans (4 by default). They choose capture and defence objectives, spread across posts and stay inside a capture
zone while taking it. They navigate with Paper's mob pathfinder, retreat from close targets outside posts and move
within contested zones. Shooting requires line of sight and a reaction delay; aim includes configurable spread.
Riflemen push towards enemy posts, heavy infantry prefer defending owned posts, and medics follow wounded allies
and heal within four blocks. Each role uses its class's health and weapon. Bots retain a visible target until
another enemy is substantially closer, so nearby enemies trading places do not repeatedly reset the reaction delay.
Their displayed head and gun follow the same stored aim that their shots use, independently of navigation.
The torso and legs turn gradually under `appearance.body-turn-degrees`; the shoulders stay attached while the gun
tracks the firing direction. Non-combat aim turns each tick under `bots.idle-turn-degrees`, with separate speed
thresholds for starting and stopping a walking turn. Combat tracking still uses the selected difficulty.
Unchanged display poses do not restart interpolation. Bots step over rises up to `bots.step-height` (one block by
default) using native collision handling.
New targets must be in `bots.vision-angle`; a target lost behind cover is remembered at its last seen position for
`bots.memory-ticks`. Taking enemy damage alerts a bot without restarting its reaction delay on every hit.
Each post has two authored approach routes per faction, cover, a nearby peek position and a fallback point.
Bots use these routes, retreat while venting or badly wounded, and investigate a remembered position without
firing through cover. A new objective uses the nearest forward waypoint. Holding a defensive or retreat position
does not trigger stuck recovery. Heavy defenders enter their post's capture zone when an enemy threatens it.
These tactics apply on every difficulty. Heavy bots fire the repeater's complete three-bolt
volley, with heat charged once per trigger pull. They also spend their faction's signature ability: the Hegemonie's
enters siege mode while holding a post with a visible enemy in range, unless it is venting or badly wounded, and the
Kolonien's throws its charge at a visible or remembered enemy in a post, if the blast would not reach the thrower.
They share the round's projectile bolts, heat, protection, reinforcements and respawn delay. Friends joining
a running match take human seats and excess bots disappear. Bots have no heroes.
Medics retain their selected wounded patient, hold within three blocks when they have line of sight, and resume
objectives once that patient is healed, gone or out of range. A blocked sightline makes them approach the patient.

The lobby's **Klasse & Bot-Stärke** item opens the class menu: the four roles as portrait cards and, under a divider,
sunk into the panel so it reads as no choice, the selected role's blaster, F and Q abilities and stats. A click selects
a role and the menu stays open. With bots on, the toolbar's **Bot-Stärke** button opens a vote for
**Leicht**, **Normal** or **Veteran**. The most votes choose the round's difficulty; ties are random. Without votes,
`bots.difficulty` applies, defaulting to Normal. Voting closes when the match starts and never changes a running round.

- **Leicht:** the previous gentle tuning: slow tracking, short bursts, long pauses and 60% damage without headshots.
- **Normal:** faster tracking, tighter aim, five-shot bursts with short pauses and 90% damage without headshots.
- **Veteran:** fast acquisition, accurate continuous fire until overheating, full damage and headshot multipliers.

`bots.profiles.<difficulty>` holds each preset's reaction, fire interval, spread, burst, tracking and damage settings.
`burst-shots: 0` disables burst pauses. The older flat tuning keys under `bots` move into the chosen profile.
Bot count, sight range, rewards and medic settings remain under `bots`.

Bot kills give `bots.kill-points` battle points (25), with no kill coins, persistent kill stats or streak credit.
They do count on the sidebar's kills.
Existing capture and win rewards still apply in bot rounds. Set `bots.enabled: false` for human-only rounds, which
require at least two humans. Bots do not add entries to the network's player list.

Operators and players with `blockcraft.sternenfront.debug` can use `/sfdebug` to enable NPC inspection.
Right click a bot to save the preceding five seconds and following ten seconds to
`plugins/Sternenfront/debug/npc-*.json`. History begins when inspection is enabled. `/sfdebug capture` records the
bot under the crosshair; `/sfdebug off` restores normal right-click controls while pending captures finish.
The report contains native movement and jump flags, aim, navigation decisions, path nodes, terrain, nearby actors,
server timing, and each rendered part's transform and interpolation settings. A disappearing bot
produces a partial report. History and capture lengths are configured under `debug`; sampling stops when neither
an inspector nor a pending capture remains. File encoding and writes run off the game thread.

The map has three **command posts** (A, B, C) between the two headquarters. Standing in a post's zone moves its
capture progress towards the faction with more players there, faster the bigger the lead (up to three times).
An enemy post is first neutralised, then taken. Nobody present lets an unfinished capture drift back. A post's
coloured blocks show its owner from anywhere on the map; standing in the zone shows
the progress as a boss bar. Every capture pays coins.

Each faction starts with `tickets.start` **reinforcements**. Every death costs one. The faction holding more posts
drains the other by the difference every `tickets.bleed-seconds`. A faction at zero reinforcements loses. After
`countdown.round` seconds the faction with more reinforcements left wins, or it is a draw.

### Acts

A round runs through three acts, and acts only move forward. Each second the round checks the next act's start: the
lower faction's reinforcements at or below a share of `tickets.start`, or the time since the start. Collapsing tickets
can pass an act within a second, but every act's start still runs, in order. A title and a chat line announce each one.

- **Gefecht:** the opening, with the rules above.
- **Landungsangriff:** starts at `acts.assault-share` (0.65) or after `acts.assault-seconds` (360). The trailing
  faction is the one with fewer reinforcements, on a tie fewer posts, and if still tied the Kolonien. The round picks
  a priority post, the **Schwerpunkt**: the post held by the trailing faction's enemy nearest post B, else a neutral
  one nearest B, else B. While the act runs it counts `acts.priority-bleed` (2) times when bleeding, carries a star on
  the sidebar and a mark in its boss bar, and pulls the bots of both sides by `acts.priority-pull` blocks of walking.
  Bots already assigned still push the others on to other posts. The trailing faction's deploy menu offers the
  **Landungsschiff**: `acts.dropship-distance` (22) blocks from the Schwerpunkt on that faction's side, on the highest
  solid ground, spread so that a crowd lands apart. Bots keep deploying at their headquarters.
- **Letztes Gefecht:** starts at `acts.last-stand-share` (0.30) or after `acts.last-stand-seconds` (780). The trailing
  faction is worked out once more and holds for the rest of the round; the landing moves to its side of the same post.
  The Schwerpunkt's extra weight ends, and every post counts `acts.last-stand-bleed` (2) times instead. That faction's
  heroes cost `acts.comeback-hero-share` (0.5) of `heroes.cost`, which the deploy cards and sidebar show, and it may
  field one more hero at once. A sandstorm cuts `bots.sight-range` to `acts.storm-sight-share` (0.6), and the pressure
  music starts if it was not playing yet.

### Deploying

A dead player first watches their killer for `killcam.ticks` (2.5 seconds), then the map from above, unseen and
unhurt; the wait of `respawn.seconds` counts from the death, so the kill cam costs no time. The deploy menu shows that
wait in its toolbar and offers the four roles on the class menu's cards, the faction's heroes in the **Held** row and
every spawn in the **Einsatzort** row: the faction's headquarters, each post it owns that no enemy stands in and, for
the trailing faction from the assault on, the landing described under Acts.
Choosing a spawn deploys as soon as the wait is over; whoever chooses nothing goes to the headquarters after
`respawn.auto-seconds`. A deployed player cannot be hurt for `respawn.protection-seconds` or until they fire.
Enemies inside a headquarters take `headquarters.enemy-damage` every second.

### Classes

Every class fires its blaster with right click, held down for automatic fire. Blasters have no ammunition but
heat: every shot heats the gun, the experience bar shows the heat, and a gun at full heat vents for a moment before
it fires again. F uses the first ability and Q the second while the weapon stays in hand. Abilities have no held
items. The combat HUD shows health, heat or guard strength, the ability names and their remaining cooldowns.
It draws the pack's icons and segmented bars.
Deployment also prints the equipped F and Q abilities in chat. Grenades and mining charges hurt their thrower as well
as enemies, with distance falloff and solid-block cover; teammates remain safe.

Both factions fill the same four roles with the same blasters, each under its own names, and only the heavy differs in
what it carries. `classes.<faction>.<role>` sets each body's health, speed and scale, for players and bots alike.

**Freie Kolonien**

| Class | Blaster | Abilities | Health |
| --- | --- | --- | --- |
| Milizionär | K-7 Karabiner: steady medium bolts | Schürfgranate, Adrenalin (speed and regeneration) | 20 |
| Minenarbeiter | R-40 Repetierer: three-bolt bursts with spread | Bauschild: a wall of energy that stops bolts; Sprengladung | 30 |
| Spurenleser | LB-9 Langbüchse: slow, heavy, headshots count more | Sprungmodul: a jump-pack leap that cancels the next fall | 16 |
| Feldsanitäter | P-2 Pistole: quick light bolts | Heilstation: heals allies around it | 20 |

**Hegemonie**

| Class | Blaster | Abilities | Health | Body |
| --- | --- | --- | --- | --- |
| Linieneinheit L-4 | K-7 Karabiner | Ionengranate, Überlastung (speed and regeneration) | 18 | |
| Brecher-Einheit X-9 | R-40 Repetierer | Schildwand; Belagerungsmodus | 36 | speed 0.88, scale 1.1 |
| Jäger-Einheit J-3 | LB-9 Langbüchse | Schubdüsen: a thruster leap that cancels the next fall | 16 | |
| Wartungseinheit W-2 | P-2 Pistole | Reparaturfeld: repairs allies around it | 18 | |

Each heavy has one signature ability on Q, and both are a trade rather than a win:

- **Sprengladung** (`abilities.mining-charge`): a thrown charge on a 60-tick fuse, radius 6, damage 20, under the
  grenade's rules for falloff, cover and self-damage. A long fuse makes it strong against whoever holds a spot and easy
  to walk away from.
- **Belagerungsmodus** (`abilities.siege-mode`): for 6 seconds the unit cannot walk or jump, and its repeater pulls every
  4 ticks with two bolts at half the heat per pull. That is a third more bolts per second, not twice as many: holding
  right click pulls no faster than every 4 ticks, so only the bolts per pull are left to keep the damage in check.
  A rooted unit is what a Sprengladung is made for.

A scale of 1.1 makes a player 1.98 blocks tall, so the two-high corridors in the wreck still fit. A bot's skeleton body
already stands 1.99 blocks and never scales past that, since any taller and the pathfinder plans three-high paths.
Every deploy sets size and speed anew: heroes and champions get normal ones, and the end of a round resets them.

The class menu shows the player's faction. Before they picked one it shows the faction that would seat them right now,
the smaller one, and the banner in its toolbar says so; the sidebar's class line does the same. The deploy menu always shows their own.

The scout's long rifle uses hitscan with a visible tracer when `combat.precision-hitscan` is enabled. Other player
blasters and every bot weapon fire projectiles at their configured `speed` in blocks per tick. Each bolt glows in its faction's colour and
can be dodged at range. They pass through teammates, stop at walls and land a headshot above the shoulders. A hit
shows a marker under the crosshair and plays a hit sound to the shooter, a headshot and a kill their own; the marker
is a subtitle queued through `Notifications` at reward priority, so an important title still wins.

Sneaking with the **Langbüchse** zooms in: the round slows the walk by `scope.slowdown`, which the client draws as a
narrower view. That depends on the client: a player who turned FOV effects off in their accessibility settings sees
no zoom, only the slower walk.

**Kill streaks:** `streaks.orbital-strike` kills in one life (5) grant one use of the Kommandant's Orbitalschlag.
Shift+F throws its beacon while the weapon stays in hand; the HUD shows when
the strike is ready. Dying removes an unused strike. The round summary announces the streak itself (`streaks.announce` in
BlockcraftAPI's config).

### Heroes

Kills, captures and neutralised posts earn **battle points** (`heroes.points`: 100, 100 for each player on the post,
50), shown under the kills on the sidebar. At `heroes.cost` (600) the deploy menu's hero row unlocks; picking a hero
spends the points at the next deploy. A faction fields `heroes.per-faction` heroes at once (1), each only once. Heroes
have their own health and do not heal, neither out of combat nor at a med station. Everyone sees a boss bar with
each hero's name and health; the round announces a hero entering and falling, and killing one pays `rewards.hero`.

Recent damage assists grant `heroes.points.assist` points, and kills inside a friendly post grant
`heroes.points.defence` additional points. Both scale down with the bot kill reward. A damaging contribution must
be at least two health and within `assist-window-seconds`; the killer receives no assist reward.
Med stations grant `healing-per-health` points for health actually restored to another ally after enemy damage.
Self-damage, environmental damage, overhealing and automatic regeneration grant none. All healing consumes the
same wound budget. `healing-window-seconds` expires old wounds, and `healing-limit-per-life` caps the reward per
patient life. These are round battle points, with no additional persistent coins or kill stats.

| Hero | Faction | Weapon | F | Q | Health |
| --- | --- | --- | --- | --- | --- |
| Jedi-Ritter | Kolonien | blue Lichtschwert | Machtstoß: a cone push | Lichtschwertwurf | 60 |
| Kopfgeldjäger | Kolonien | Zwillingspistolen, one in each hand | Raketenrucksack: a few seconds of thrust, jump climbs | Fesselkabel: pulls you to the aimed block | 50 |
| Sith-Inquisitor | Hegemonie | red Lichtschwert | Lichtschwertwurf: the saber spins out and back, hitting everyone on its path | Machtgriff: lifts, slows and hurts the aimed enemy for 2 s | 60 |
| Kommandant | Hegemonie | S-12 Sturmgewehr | Orbitalschlag: the thrown beacon marks the spot with a red beam, 3 s later a barrage falls | Sammelruf: speed and resistance for allies within 12 blocks | 50 |

A **lightsaber** strikes enemies in a forward arc, with `combat.melee` setting its reach, angle, swing interval and
maximum targets. The damage is `heroes.saber.damage`, or `relic.damage` for the champion. Solid cover stops a swing.
It arrives as a
hilt and ignites `heroes.saber.ignite-ticks` later, hums while it is held and hisses when swung. Hold right click to
guard. A second jump triggers the forward leap (Machtsprung); the relic champion can also use F. Guarding blocks
frontal bolts and reduces another blade's damage to `blocked-share` of its normal damage. A parry in the first `combat.guard.parry-ticks`
reflects a bolt towards its shooter or stops a melee hit completely. Hits consume guard strength; an exhausted
guard breaks briefly and recovers after release. The HUD distinguishes guarding, recovery and a broken guard.
After damage, a brief red arrow indicates the source relative to the player's current view. Its duration is
`combat.damage-indicator-ticks`. Orbital strikes fix all impact positions when the beacon lands, draw the damage
radius of each planned blast and show the countdown. The warning displays respect walls.
The blade takes its colour from the item (the pack's model is tinted
by the first `custom_model_data` colour): blue for the Jedi, red for the Sith, white for whoever owns the unlock
`sternenfront.saber.white`.

The unlit hilt cannot deal melee damage or block while the blade is in flight. Throws check the whole movement
segment and solid-block cover, hit each enemy once and return at a wall or their maximum range. A returning blade
that meets a wall ends its flight and restores the saber. Blocking requires a drawn blade and facing the attack.
F/Q activate the throw without switching items. Swing sounds follow the configured melee attack interval.

Orbital strikes deal up to 24 damage per impact by default. The first impact lands at the beacon; the remaining
impacts scatter around it. The three-second warning and cover checks remain in place.

The relic's champion is a hero too, with the same boss bar, no healing and no slot taken from the faction; a hero
cannot take the relic.

### Sounds

Every sound of the round plays the pack's `blockcraft:sternenfront.<name>` (`SoundCue`).

Lightsaber sounds use downloaded CC0 recordings for ignition, retraction, hum, swings, impacts, clashes,
deflection and throwing. [Offline regeneration](../../resourcepack/SABER_SOURCES.md) is documented with the pack.

Blasters use MikeE63's CC0 recordings. Scott Buckley's *Legionnaire (Original)* and *Juggernaut* provide opening,
battle, pressure and result cues under CC BY 4.0. The pack includes [the credits](../../CREDITS.md), and the round
links the composer. [Regeneration](../../resourcepack/BATTLE_AUDIO.md) covers the edits and checksums.

The soundtrack and cantina use Minecraft's **Music** category. Each player's normal Music slider controls them,
including muting them at zero, while weapon effects remain in the Players category. There is no second music toggle.
`music.enabled`, `music.volume` and `music.pressure-tickets` control the server mix. Respawning does not restart music;
cues have quiet gaps, pressure replaces the current cue, and shutdown stops the round's music.

### Combat presentation

Firing briefly moves the weapon model and shows a faction-coloured flash model at the barrel for two ticks, larger on
the long rifle and the repeater. The actor's view and shot direction do not change. A gun near full heat vents steam
from its barrel. Bolts are lit, enlarged models with a trail: `presentation.bolt.far-every` decides which dots are
forced, so a far player gets a sparse line and a near one a dense line. The long rifle's hit arrives at once, so it
draws a single stretched tracer that thins away.

A bolt ends in a flash and sparks. On a wall it also throws block fragments, and every `presentation.scorch.every`-th
hit leaves a scorch mark, capped by `presentation.scorch.marks` with the oldest removed first.

A lit blade that reaches a solid block cuts it: a swing that hits no fighter, along its aim ray up to `combat.melee.range`
and across the view, and a thrown saber that strikes a wall on its way out, along its flight. Molten sparks and a quiet
`saber.hit` go with it. A full block's face takes a long glowing cut in the blade's colour (blue, red, white for the
unlock, gold for the relic blade), tilted by up to `presentation.saber-mark.tilt-degrees`. The glow thins away over
`glow-ticks`, the dark scar under it lasts `ticks` and shrinks away over `fade-ticks`, and scars share the scorch marks'
cap. `cooldown-ticks` limits each player. A parried bolt leaves none. All of it is only a picture.

Shockwaves are one scaling ring model: Force Push, Rally,
med-station pulses, shield hits and every explosion. Explosions layer a flash, the ring, thrown ground, forced sparks
and rising smoke. Fighters under `presentation.damaged.below` of their health spark and smoke. Jump packs and jetpacks
show two jets from the back, a thrown saber a trail in its blade's colour, and a force grip a turning ring round the
victim with a line back to the gripper. Grapples draw a short-lived tether. Shield panes retain their collision and
gain a projected grid.

`Flares` owns the short-lived models and holds their number under `presentation.live-effects`: flashes and tracers are
refused at the cap, larger effects push out the oldest. `Ambience` keeps thin smoke and the odd spark on the wrecked
cruiser and steam at the refinery for players within `presentation.ambient.range`.

`Spectacle` holds the round's two big moments. `assaultBegins` flies the trailing faction's dropship in along the
axis between the headquarters, with engine particles and a shadow of dust, and leaves it standing where it landed
until the round ends. `lastStandBegins` starts a sandstorm for the rest of the round: every player gets wind-driven
sand grains (`presentation.storm.density`) and two layers of dust that dim the view, all in one wind direction, with a
gust every `gust-seconds`. There is no storm under a roof and no shader, so it ends with the round. Both are only
pictures and sound.

`presentation.effects` turns off everything but bolt trails, thrusters and plain explosions. Particles and models use
ordinary depth rendering.

Command posts have low projector bases with floating original faction emblems and twelve rotating capture segments.
A contested post pulses. Their floor boundaries and capture rules stay the same. `presentation.holographic-posts`
controls the models.

### The relic blade

`relic.first-seconds` into the round, the **Ahnenklinge** appears in the temple ruin on the northern cliff, an equal
distance from both headquarters. Whoever walks into it becomes the champion: more health, speed, a glowing outline,
the blade instead of their blaster and a leap on a short cooldown. The champion uses the same right-click guard
and timed parry as the other blade heroes. Killing the champion pays extra; the blade returns to the ruin
`relic.respawn-seconds` after its carrier falls.

## The map: Wrack von Tessar

The map is the saved template [`worlds/sternenfront`](../../worlds/sternenfront). Compose and
`./gradlew :paper:runServer -Pgame=sternenfront` restore it before every start like any other map, so a round always
begins on the saved blocks. Its region files go through git-lfs.
A desert basin between canyon walls, about 180 by 130 blocks inside a world border, with a broken plateau and far
mesas behind the walls:

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
its centre. Three speeder pads per faction fit under
each base (at the spawn's x, z 10, 16 and 22), and a craft two wide that steps one block can drive from them to every
landing ground.

Every position the round uses lives in `blockcraft/Sternenfront/arena.yml` inside the template: the play area, the
headquarters, the posts with their coloured blocks and bot routes, the controls and the secrets. `TessarMap` reads it at
start and the plugin refuses to start without it. To change the map, use `/build` and `/map save` as for any template
and edit `arena.yml` by hand where a position moved. Keep a post's coloured blocks as they are: the round recolours
every listed block to the post's owner, and they start white at the start of each round. A control's door panels must
be air in the template, and the round opens any left closed. Replays record only the play area, so the far mesas beyond
it are missing there.

## Battlefield controls

Right click the labelled buttons with your weapon in hand. Either faction can use them. A control click does not
fire the weapon. Controls cannot be used while riding, and each has its own cooldown in `battlefield`.

- The refinery's vent warns for one second, then releases damaging steam for six seconds. Its grate marks the
  shortcut it denies. The steam affects both factions, including the player who activated it.
- The wreck's stern bulkhead closes for eight seconds, cutting the corridor to the engine room and the bridge ladder.
  The hull's flank breaches remain open.
- The bazaar's shutters close the centre of its northern gate for eight seconds. The side passages remain open.

Doors refuse to close when a fighter or vehicle occupies their panels. They reopen automatically and restore
their original blocks when the round ends. A radio message warns the owning team when an enemy enters a post;
`posts.radio-cooldown-seconds` limits repeated alerts. An attack that starts during the cooldown still triggers
an alert when it expires if the enemy remains in the zone.

## Models

Everything a player holds or wears is a `PackModel` (`blockcraft:sternenfront/<name>`): the blasters, the grenade,
the ability items, the relic blade, the lightsabers lit and unlit, a helmet per faction and per hero, and the bright
bolts, built from cuboids with vanilla textures like the Zombies guns, more detailed. Nexo is not needed for the round:
nothing in it is furniture a player sits on or breaks.

Bodies, speeders, dropships and the effect models come from `resourcepack/DroidArt.java`
(`./gradlew :resourcepack:generateDroids`, or `java DroidArt.java src` in `resourcepack/`). Each model family gets one
power-of-two atlas under `textures/block/sternenfront/rig/`. Its faces are packed automatically at a constant texel
density (64 pixels per block on bodies, 32 on speeders, 72 on dropships, 96 on effects) and painted from the box's material: base
ramp, noise, bevel, panel lines, and wear on the scrappy materials. The painter is seeded from the family and face, so
the same source gives byte-identical PNGs. A box may turn by 22.5 or 45 degrees around one axis, and glowing materials
write `light_emission`.

Hiding a player's real body for the rig also takes them out of that viewer's tab list while they are deployed. This is
accepted for now.

Every fighter on the field is rigged for the other fighters: `appearance.droids` for the Hegemonie,
`appearance.infantry` for the Kolonien, humans and bots alike. `appearance/RigBody` holds one body per faction and
class: its model family (`sternenfront/body/<faction>_<role>/<part>`, part `torso`, `head`, `arm` or `leg`), joint
offsets, scale, aiming pose and whether the held weapon shows.

| | Soldier | Heavy | Scout | Medic |
|---|---|---|---|---|
| Hegemonie | Linieneinheit: skeletal, sensor-bar head | Brecher-Einheit: hunched walker frame, 1.25x scale, repeaters built into the forearms, so the held blaster is hidden | Jaeger-Einheit: forward hunch, digitigrade legs, antenna array | Wartungseinheit: tool arm (`tool_arm`, left side only), hover drone on the back |
| Kolonien | Milizionaer: patched uniform, open helmet | Minenarbeiter: exo-rig, chest plate, back tanks, heavy boots | Spurenleser: poncho, goggles, wrapped head | Feldsanitaeter: field pack, orange marking |

Body, limbs, head, held weapon and nameplate follow the underlying fighter. Heroes retain their own appearance. No
skin/profile changes or additional plugin are required. The owner keeps their normal first-person and F5 appearance;
spectators also see the original bodies so the kill camera stays usable. The combat hitbox stays the underlying
fighter's size, even on the Brecher, and melee interactions forward to that fighter after reach and cover checks.

The other generated models, all `Items.of(Material.PAPER).model(Key.key("blockcraft", ...))` without a `PackModel`:

- `sternenfront/speeder/<faction>` and `<faction>_damaged`: one seamless hull, nose at +z, seat top at about y 0.5.
- `sternenfront/dropship/<faction>`: feet at y 0, to be displayed at scale 6 to 8.
- `sternenfront/fx/<name>`: one block across at scale 1, pivot at the model's centre. `ring`, `scorch`, `saber_slash` and
  `saber_slash_cold` lie in the model's XY plane and show on both sides; `ring` and the glow of `saber_slash` are white
  and tinted by the item's first `custom_model_data` colour. `saber_slash` runs along X with a white core and a dark
  melted edge; `saber_slash_cold` is that edge alone.
  `flash_kolonien`, `flash_hegemonie` and `smoke_puff` are crossed sheets.

`:resourcepack:generateCombat` regenerates the HUD icons and bars.

## Speeders

Each faction has `speeders.per-faction` single-seat speeders (two by default, at most three) on marked pads at its
headquarters. Right click boards your faction's empty speeder; Shift dismounts, with a short fall-damage grace period.
An abandoned speeder returns to its pad after the idle timeout. The speeder's model comes from the pack, which the
network requires, in one version per faction and a damaged one for each.

W accelerates. S brakes first and then reverses slowly. A/D steer with a turn rate that grows with speed and is small
at a standstill; in reverse the steering is mirrored, like a car. Holding sprint raises the top speed while the boost
budget lasts. An empty budget recharges to `boost.restart-share` before it can burn again, and the status line shows
it. A speeder climbs steps up to one block high when the vehicle and rider have enough clearance.

Right click or attack fires the forward cannon along the craft's nose, wherever the rider is looking.
Its bolts use `speeders.cannon` and the rider's blaster heat, so the experience bar still shows heat and boarding does
not skip a vent. Hits and kills credit the rider like a blaster's. Abilities, melee and handheld weapons stay disabled
while riding, as do the battlefield controls.

At `ram.minimum-speed` or above, a speeder hits enemy fighters in its path for damage scaled by speed and knocks them
aside, credited to the rider. Each target can be hit once per `ram.cooldown-ticks`, each hit costs the craft
`ram.craft-damage`, and teammates are safe.

Below `damaged-share` of its health a speeder shows its damaged model and trails smoke and sparks. A destroyed speeder
explodes, hurting enemies within `explosion.radius` behind no cover in its rider's name, and respawns after
`respawn-seconds`. The rider is dismounted unharmed. An empty craft explodes without damage.

The invisible vehicle does the collision; the model only shows it. It banks into turns, pitches on acceleration, climbs
and falls, bobs gently and rises smoothly over a step it sees coming, all from `speeders.presentation`. Thrusters and
sand kicked up from the ground block scale with speed.

## Run it

```powershell
.\gradlew.bat stagePlugins
docker compose up -d mariadb velocity lobby sternenfront
```

Join through Velocity and run `/play sternenfront`. While the mode is tested, the lobby's navigator shows it only to
players with `blockcraft.lobby.preview` (premium and every team rank); `/play` still works for everyone.
With bots enabled, one player starts the countdown;
`/start` shortens it. The jar supplies defaults. Existing config files should contain only operator overrides;
old full copies can pin obsolete bot damage and timings until those values are removed or updated.

## Later

Later changes: a second map, coin-unlocked class variants
through `UnlockableKit`, a lobby stand with status signs and a figure.
