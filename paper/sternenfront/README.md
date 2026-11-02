# Sternenfront

A team shooter in an original space-opera setting: two factions fight over command posts on a desert world with
blasters, grenades, jump packs, shield walls and one relic blade that turns its carrier into the round's champion.
It is a game in the genre of large sci-fi objective shooters, not a copy of one: the factions, the map, the weapons
and the relic are our own, and every name lives in `messages.yml`.

## The round

Two factions, `teams.players-per-team` each (6 by default, so 2v2 up to 6v6 plays):

- **Freie Kolonien** (orange): miners and smugglers, scrappy gear, a landed freighter as their base in the western canyon.
- **Hegemonie** (light blue): white armour, clean steel, a landing platform with a dropship in the east.

Players pick a faction and a class in the lobby; whoever picks no faction is seated on the smaller one.

The map has three **command posts** (A, B, C) between the two headquarters. Standing in a post's zone moves its
capture progress towards the faction with more players there, faster the bigger the lead (up to three times).
An enemy post is first neutralised, then taken. Nobody present lets an unfinished capture drift back. A post's
beacon beam and the coloured blocks around it show its owner from anywhere on the map; standing in the zone shows
the progress as a boss bar. Every capture pays coins.

Each faction starts with `tickets.start` **reinforcements**. Every death costs one. The faction holding more posts
drains the other by the difference every `tickets.bleed-seconds`. A faction at zero reinforcements loses. After
`countdown.round` seconds the faction with more reinforcements left wins, or it is a draw.

### Deploying

A dead player watches the map from above, unseen and unhurt, for `respawn.seconds`, while the deploy menu offers
the four classes and every spawn: the faction's headquarters and each post it owns that no enemy stands in.
Choosing a spawn deploys as soon as the wait is over; whoever chooses nothing goes to the headquarters after
`respawn.auto-seconds`. A deployed player cannot be hurt for `respawn.protection-seconds` or until they fire.
Enemies inside a headquarters take `headquarters.enemy-damage` every second.

### Classes

Every class fires its blaster with right click, held down for automatic fire. Blasters have no ammunition but
heat: every shot heats the gun, the experience bar shows the heat, and a gun at full heat vents for a moment before
it fires again. Abilities sit next to the blaster in the hotbar; F uses the first and Q the second without
switching, and each item shows its cooldown.

| Class | Blaster | Abilities | Health |
| --- | --- | --- | --- |
| Soldat | K-7 Karabiner: steady medium bolts | Ionengranate, Adrenalin (speed and regeneration) | 20 |
| Schwerer | R-40 Repetierer: three-bolt bursts with spread | Schildwand: a wall of energy that stops bolts | 30 |
| Späher | LB-9 Langbüchse: slow, heavy, headshots count more | Sprungmodul: a jump-pack leap that cancels the next fall | 16 |
| Sanitäter | P-2 Pistole: quick light bolts | Heilstation: heals allies around it | 20 |

Bolts are not instant: each one flies at `weapons.bolt-speed` blocks per tick, glows in its faction's colour and can
be dodged at range. They pass through teammates, stop at walls and land a headshot above the shoulders.

### The relic blade

`relic.first-seconds` into the round, the **Ahnenklinge** appears in the temple ruin on the northern cliff, an equal
distance from both headquarters. Whoever walks into it becomes the champion: more health, speed, a glowing outline,
the blade instead of their blaster and a leap on a short cooldown. A champion who sneaks deflects bolts that hit
them from the front back at the shooter. Killing the champion pays extra; the blade returns to the ruin
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

Everything a player holds or wears is a `PackModel` (`blockcraft:sternenfront/<name>`): the four blasters, the
grenade, the ability items, the relic blade, a helmet per faction and the glowing bolts, built from cuboids with
vanilla textures like the Zombies guns, more detailed. Without the pack, every item is its vanilla fallback, the
factions read from their dyed armour and the bolts from their coloured outline. Nexo is not needed for the round:
nothing in it is furniture a player sits on or breaks.

## Run it

```powershell
.\gradlew.bat stagePlugins
docker compose up -d mariadb velocity lobby sternenfront
```

Join through Velocity and run `/play sternenfront`. Two players start the countdown; `/start` shortens it.

## Later

Not in the first version, each a change of its own: speeder bikes, a second map, coin-unlocked class variants
through `UnlockableKit`, a scope for the long rifle, a lobby stand with status signs and a figure.
