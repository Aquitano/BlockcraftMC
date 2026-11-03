# Sternenfront

A team shooter in an original space-opera setting: two factions fight over command posts on a desert world with
blasters, grenades, jump packs, shield walls, heroes with lightsabers and one relic blade that turns its carrier into
the round's champion. It is a game in the genre of large sci-fi objective shooters, not a copy of one: the factions,
the map, the weapons, the heroes and the relic are our own. The heroes borrow the genre's words (Lichtschwert, Jedi,
Sith, die Macht), but no characters, hilt designs or music; every name lives in `messages.yml`.

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
Their displayed head, body and gun follow the same stored aim that their shots use, independently of navigation.
They share the round's projectile bolts, heat, protection, reinforcements and respawn delay. Friends joining
a running match take human seats and excess bots disappear. Bots have no heroes.

The lobby's **Klasse & Bot-Stärke** item opens the class menu. Its **Bot-Stärke** button opens a vote for
**Leicht**, **Normal** or **Veteran**. The most votes choose the round's difficulty; ties are random. Without votes,
`bots.difficulty` applies, defaulting to Normal. Voting closes when the match starts and never changes a running round.

- **Leicht:** the previous gentle tuning: slow tracking, short bursts, long pauses and 60% damage without headshots.
- **Normal:** faster tracking, tighter aim, five-shot bursts with short pauses and 90% damage without headshots.
- **Veteran:** fast acquisition, accurate continuous fire until overheating, full damage and headshot multipliers.

`bots.profiles.<difficulty>` holds each preset's reaction, fire interval, spread, burst, tracking and damage settings.
`burst-shots: 0` disables burst pauses. The older flat tuning keys under `bots` move into the chosen profile.
Bot count, sight range, rewards and medic settings remain under `bots`.

Bot kills give `bots.kill-points` battle points (25), with no kill coins, persistent kill stats or streak credit.
Existing capture and win rewards still apply in bot rounds. Set `bots.enabled: false` for human-only rounds, which
require at least two humans. Bots do not add entries to the network's player list.

The map has three **command posts** (A, B, C) between the two headquarters. Standing in a post's zone moves its
capture progress towards the faction with more players there, faster the bigger the lead (up to three times).
An enemy post is first neutralised, then taken. Nobody present lets an unfinished capture drift back. A post's
beacon beam and the coloured blocks around it show its owner from anywhere on the map; standing in the zone shows
the progress as a boss bar. Every capture pays coins.

Each faction starts with `tickets.start` **reinforcements**. Every death costs one. The faction holding more posts
drains the other by the difference every `tickets.bleed-seconds`. A faction at zero reinforcements loses. After
`countdown.round` seconds the faction with more reinforcements left wins, or it is a draw.

### Deploying

A dead player first watches their killer for `killcam.ticks` (2.5 seconds), then the map from above, unseen and
unhurt; the wait of `respawn.seconds` counts from the death, so the kill cam costs no time. The deploy menu offers
the four classes, the faction's heroes and every spawn: the faction's headquarters and each post it owns that no
enemy stands in.
Choosing a spawn deploys as soon as the wait is over; whoever chooses nothing goes to the headquarters after
`respawn.auto-seconds`. A deployed player cannot be hurt for `respawn.protection-seconds` or until they fire.
Enemies inside a headquarters take `headquarters.enemy-damage` every second.

### Classes

Every class fires its blaster with right click, held down for automatic fire. Blasters have no ammunition but
heat: every shot heats the gun, the experience bar shows the heat, and a gun at full heat vents for a moment before
it fires again. F uses the first ability and Q the second while the weapon stays in hand. Abilities have no held
items. The combat HUD shows health, heat or guard strength, the ability names and their remaining cooldowns.
With the pack, it draws custom icons and segmented bars; without it, it shows text and percentages.
Deployment also prints the equipped F and Q abilities in chat. Grenades hurt their thrower as well as enemies,
with distance falloff and solid-block cover; teammates remain safe.

| Class | Blaster | Abilities | Health |
| --- | --- | --- | --- |
| Soldat | K-7 Karabiner: steady medium bolts | Ionengranate, Adrenalin (speed and regeneration) | 20 |
| Schwerer | R-40 Repetierer: three-bolt bursts with spread | Schildwand: a wall of energy that stops bolts | 30 |
| Späher | LB-9 Langbüchse: slow, heavy, headshots count more | Sprungmodul: a jump-pack leap that cancels the next fall | 16 |
| Sanitäter | P-2 Pistole: quick light bolts | Heilstation: heals allies around it | 20 |

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
The blade takes its colour from the item (the pack's model is tinted
by the first `custom_model_data` colour): blue for the Jedi, red for the Sith, white for whoever owns the unlock
`sternenfront.saber.white`. Without the pack the saber is a diamond or netherite sword and plays the same.

The unlit hilt cannot deal melee damage or block while the blade is in flight. Throws check the whole movement
segment and solid-block cover, hit each enemy once and return at a wall or their maximum range. A returning blade
that meets a wall ends its flight and restores the saber. Blocking requires a drawn blade and facing the attack.
F/Q activate the throw without switching items. Swing sounds follow the configured melee attack interval.

Orbital strikes deal up to 24 damage per impact by default. The first impact lands at the beacon; the remaining
impacts scatter around it. The three-second warning and cover checks remain in place.

The relic's champion is a hero too, with the same boss bar, no healing and no slot taken from the faction; a hero
cannot take the relic.

### Sounds

Every sound of the round plays the pack's `blockcraft:sternenfront.<name>` to players wearing the pack and a vanilla
stand-in to everyone else (`SoundCue`).

### The relic blade

`relic.first-seconds` into the round, the **Ahnenklinge** appears in the temple ruin on the northern cliff, an equal
distance from both headquarters. Whoever walks into it becomes the champion: more health, speed, a glowing outline,
the blade instead of their blaster and a leap on a short cooldown. The champion uses the same right-click guard
and timed parry as the other blade heroes. Killing the champion pays extra; the blade returns to the ruin
`relic.respawn-seconds` after its carrier falls.

## The map: Wrack von Tessar

Built into an empty world at every start, like Cores' Himmelsfeste, so the service needs no world template.
A desert basin between canyon walls, about 180 by 130 blocks inside a world border:

- **West:** the Kolonien camp in a canyon, a landed freighter, containers, tents, scaffolding.
- **East:** the Hegemonie landing platform, white steel, a dropship, light strips.
- **A, Raffinerie (north):** tanks, pipes, catwalks and a smokestack.
- **B, Wrack (centre):** a crashed cruiser lying across the basin: hull ribs, torn plates, corridors through its
  body and its bridge tower.
- **C, Basar (south):** dome houses, market stalls under awnings, narrow alleys and a cantina.
- **Temple ruin (north edge):** where the relic appears, carved into the cliff.

The posts lie on the north-south centre line, so every post is as far from one base as from the other.

## Models

Everything a player holds or wears is a `PackModel` (`blockcraft:sternenfront/<name>`): the blasters, the grenade,
the ability items, the relic blade, the lightsabers lit and unlit, a helmet per faction and per hero, and the bright
bolts, built from cuboids with vanilla textures like the Zombies guns, more detailed. Without the pack, every item is its vanilla fallback, the
factions read from their dyed armour and the bolts from their coloured particle trail. Nexo is not needed for the round:
nothing in it is furniture a player sits on or breaks.

With `appearance.droids`, deployed Hegemonie infantry and bots appear as animated battle droids to other players
using the pack. Their body, limbs, head, held weapon and nameplate follow the underlying fighter. Heroes retain
their own appearance. No skin/profile changes or additional plugin are required. The owner keeps their normal
first-person and F5 appearance; spectators also see the original bodies so the kill camera stays usable.
Players without the pack see the original armour or bot skeleton. The droid's combat hitbox stays the underlying
fighter's size. Melee interactions forward to that fighter after reach and cover checks.
`./gradlew :resourcepack:generateDroids` regenerates the body models and item definitions.

With `appearance.infantry`, Kolonien bots appear as human infantry with helmets, goggles, uniforms and an aiming
pose. Both rigs show the weapon facing along the shooter's aim. Clients without the pack still see the underlying
armoured skeleton. `:resourcepack:generateDroids` also regenerates these models and the speeder;
`:resourcepack:generateCombat` regenerates the HUD icons and bars.

## Speeders

Each faction has one unarmed, single-seat speeder on a marked pad at its headquarters. Right click boards your
faction's empty speeder. W/S accelerate, brake and reverse; A/D steer; Shift dismounts. Weapons and abilities are
disabled while riding. The vehicle takes enemy damage and respawns after destruction; an abandoned vehicle returns
to its pad after the idle timeout. Dismounting grants a short fall-damage grace period. `speeders` holds its health,
speed, acceleration, turn rate and timers. A client without the pack sees a minecart item with its nameplate.

## Run it

```powershell
.\gradlew.bat stagePlugins
docker compose up -d mariadb velocity lobby sternenfront
```

Join through Velocity and run `/play sternenfront`. With bots enabled, one player starts the countdown;
`/start` shortens it. The jar supplies defaults. Existing config files should contain only operator overrides;
old full copies can pin obsolete bot damage and timings until those values are removed or updated.

## Later

Later changes: a second map, coin-unlocked class variants
through `UnlockableKit`, a lobby stand with status signs and a figure.
