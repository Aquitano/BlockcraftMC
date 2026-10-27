# Easter eggs and lobby activities

The plugins add persistent discoveries, seasonal rewards, and chat responses. Player-facing text lives in each
plugin's `messages.yml`. Existing configuration files inherit missing keys from the bundled defaults.

## Small surprises in everyday actions

| Plugin | Where to find the detail |
|---|---|
| BlockcraftAPI | `/coins` adds a reference at balances of 42, 64, 404, 1337, and 9001. Returning from AFK rotates the greeting and keeps the minutes away. |
| BlockcraftCore | Hover over a chat name at displayed level 42, 64, or 100. The extra line uses the displayed level even for a nick. `/food` rotates its confirmation. |
| Lobby | The villager has more browsing lines and replies to being hit. Fishing can retrieve a 42-coin receipt, an expired coupon, patch notes, and other junk. Collecting a normal chicken egg gives private feedback. These finds do not add inventory items. |
| Duell | Draw announcements and sent rematch requests rotate their lines. Every rematch confirmation still names the opponent. |
| EmeraldRush | Purchases rotate the receipt. Spending the last Emerald gets an exact-change reply. Every receipt keeps the price and remaining balance. |
| QuickSG | Consuming the full-food item rotates the hunger confirmation. |
| ReportsMenu | An empty report list rotates its staff-only confirmation. Messages about individual reports and punishments keep their existing wording. |
| StatusSigns | A joinable group with zero players shows `Klick: Erster!`. Clicking still joins that group. |
| BlockcraftSystem | `/ping` rotates its reply and always shows the measured ping. Sending a message or party invitation to yourself gets a different refusal. A blocked command with lines under `blocked-replies`, such as `/pl` or `/version`, answers with one of them instead of the plain refusal. |

`Messages.pick` selects one line from a YAML list when an action occurs. Existing server-edited scalar messages
remain valid. The new rotating keys are `afk.back`, `food.done`, `npc.hurt`, `quirks.egg-collected`, `round.draw`,
`rematch.asked`, `shop.bought`, `shop.exact-change`, `consume.food`, `none`, `msg.self`, `ping`, and `party.invite-self`.
`npc.talk`, `npc.window-shopping`, and `fishing.catches` also use this helper. A single line fixes the response to
that wording. An empty list renders empty text.

The numbered keys under `coins.asides` and `chat.level-asides` determine which balances and displayed levels get
an extra line. Set an entry to an empty string to hide it. `sign.first` controls the empty-group prompt.
These details do not grant coins, XP, unlocks, or gameplay advantages.

## Round curiosity

Each round tracks the longest arrow hit, jumps, time at half a heart, rod hits, and Emeralds earned from mining.
The summary shows at most one fact to everyone, selected by the largest ratio to its configured threshold.
Only players still online and not vanished at the end can be named. Spectator jumps and half-heart time do not count.

BlockcraftAPI's `curiosities` thresholds default to 40 blocks, 100 jumps, ten seconds, ten rod hits, and 100 mined
Emeralds. A threshold of zero or less disables that category. Half-heart time uses a once-per-second health check
and stops on death, leaving the round, or the end of the round. The counters stay in memory for that round only.

## Moment achievements

Each completed moment adds a personal recap note and grants its achievement once through `bc_unlocks`. Every
moment a round grants, including Rache, Haarscharf, Nackt, and David gegen Goliath, takes its recap line from
BlockcraftAPI's `moments.<name>`.

| Moment | Condition | Default XP |
|---|---|---|
| Dagobert | Win EmeraldRush with at least 100 Emeralds and no Emeralds spent. EmeraldRush's `moments.dagobert-emeralds` sets the minimum. | 100 |
| Minimalist | Win EmeraldRush without completing a purchase in the round shop, including a free purchase. | 100 |
| Pazifist | Win QuickSG with zero kills in the round. | 100 |
| Makellos | Win a combat duel without taking any non-cancelled damage during the match. | 100 |
| Trittsicher | Reach the SuperJump goal without a water or lava reset. Winning by timeout does not count. | 100 |
| Nachteule | Finish a round between 03:00 inclusive and 04:00 exclusive in the network calendar zone. `moments.night-owl` controls the hours. | 50 |

## Mirror duels

Two draws in a row between the same two players unlock `[SPIEGEL]` for both. A decisive result between them
starts the count again; duels against other players in between do not. Duell keeps every pair that ever met and
its current run of draws in its own `bc_duel_pairs` table; the draw that completes the run unlocks the tag for both
in the same transaction. Later encounters give each duellist a private greeting using the other player's current
displayed name. The tag is available in Extras without Premium.

## Death messages and kill credit

QuickSG, EmeraldRush, and combat duels use the shared `death-lines` message pools. The lines distinguish falls,
void, lava, fire, drowning, explosions, arrow distance, and melee kills with selected weapons. Environmental
deaths name the recent attacker when one exists. `death-lines.credit-seconds` in BlockcraftAPI defaults to ten.
A newer hit replaces the earlier attacker, and a death consumes that credit. Reeling a player in with a fishing
rod records an attack even when the hook deals no damage. Cancelled hits do not count.

## Network and games

| Feature | Behavior and default settings |
|---|---|
| Welcome committee | A first join opens a 60-second greeting window in the proxy's `welcome.groups`, initially `lobby`. Each greeter earns 10 coins for up to five newcomers per database day. Ten greetings unlock the achievement. Repeat greetings for the same newcomer never pay twice. |
| Erzfeind | An opponent with at least three more kills against you is eligible to be your nemesis. A private notice identifies one in your round. Defeating them adds a revenge title, achievement, and recap entry. Rivalry notices are suppressed for nicked players. |
| F pays respects | A chat message containing only `F` during the ten seconds after a death contributes to one summary. Each viewer sees only contributions they could have read in chat, including spectator and ignore restrictions. |
| Report suggestion | A recently killed player who writes a configured accusation receives a private button opening `/report` for the killer. It does not submit a report. |
| Close-call wins | Winning with half a heart or less unlocks `Haarscharf`. QuickSG also rewards an unarmored win and a Starter-kit kill against a Diamond-kit player. |
| Defeat feedback | The victim sees the killer and remaining hearts. The title distinguishes elimination, a lost life, and a duel point. |
| Duel rematch | Both duelists receive a rematch button. Requests accepted within the configured window reserve two seats in an available duel. The restart delay leaves time for the default 15-second window. |
| Personal anniversary | A verified first-join anniversary adds a welcome message, a friend notification, and the birthday cake gadget. |
| OG tag | The shared migration snapshots accounts already present in `VCOINS`, `qsg_stats`, `em_stats`, or `duell_stats`. Those accounts keep the earned tag. New accounts do not become OG merely by playing later. |
| Personal server list | Players can enable a greeting using their last login address. This defaults to off. Friend counts also require the proxy's `server-list.show-friends: true`. |

The proxy owns `welcome.enabled`, `welcome.groups`, and the greeting window. BlockcraftCore owns greeting words,
coin rewards, the daily cap, and the achievement threshold. BlockcraftAPI owns `nemesis`, `respect`, `hacker`, and
the achievement catalog. Duell owns `rematch.window-seconds`.

Existing imported `bc_players.first_seen` values are not treated as verified historical join dates. New joins
record a verified date. Join numbers remain stable, but the ordering of imported accounts follows their stored
first-seen timestamps and UUIDs.

## Shooting stars

Between 22:00 inclusive and 02:00 exclusive in the network calendar zone, a rare particle trail crosses the lobby sky.
A wish during the next 15 seconds finds `Sternschnuppe`. It is a bonus secret and does not gate `[ENTDECKER]`.
The event needs at least one active, non-vanished player. Cosmetic-effect settings control the particles.

Lobby's `shooting-stars` section controls the hours, check interval, odds, cooldown, animation, and wish
window. Defaults check once per minute with a one-in-30 chance and a 30-minute cooldown after an event.
Setting `enabled` to false stops the sky events.

While a star is up, messages beginning with a configured `wish-prefixes` phrase and a nonempty wish are
intercepted in lobby chat before Blockcraft's word and advertisement filters. They are cancelled and removed from
every audience, including the console. Only the first wish per account and event triggers the discovery. At any
other time such a message is ordinary chat. The event stores a deadline and account UUIDs, while the secret uses
the existing unlock system. The wish text is never stored or forwarded by the Blockcraft plugins.

## Herobrine

Between 22:00 inclusive and 04:00 exclusive in the network calendar zone, each one-minute check has a one-in-120
chance that Herobrine stands about 32 blocks ahead of one active, non-vanished player, on solid ground roughly at their
height. Only that player sees him, and he keeps staring at them. He vanishes after 30 seconds, when the player
leaves, or when they come within 16 blocks; the last finds `Herobrine`, a bonus secret that does not gate
`[ENTDECKER]`. After an appearance he stays away for an hour. Lobby's `herobrine` section sets all of this.

## The castle dungeon

The ladder next to the `Burgkerker!` sign leads into the dungeon, where a prisoner in striped clothes stands at the
map's `prisoner` spot (`/lobbyset prisoner`). Right-clicking him rattles his chains and gets one of his complaints;
hitting him gets another kind. Without the spot he is not there.

## Sleeping players

After ten AFK minutes, the floating label appears. It adds `Bitte nicht wecken` after 20 minutes and a duration
line in whole hours after one hour. Lobby's `activities.sleep-after-seconds`, `sleep-warning-seconds`, and
`sleep-duration-hours` control those stages. Cosmetic-effect and player-visibility settings still apply.

Attacking a visible sleeping player records a poke without damage or knockback. Returning from AFK shows one
private chat line naming the most persistent poker and the number of attempts. The name follows the current nick
when that player is online, or the displayed name of their last poke otherwise. Poke counts are cleared on wake-up
and disconnect. They are not stored in the database.

## Lobby discoveries

The secrets menu shows progress and unlocks riddles after the configured number of discoveries. Each secret pays
once. The first recorded finder gets a network announcement. Finding all sign, Konami-code, and pond discoveries
unlocks `[ENTDECKER]`; the chicken's golden egg, the shooting star and Herobrine are bonus discoveries.

Places the map's builders left without a sign count as well. Walking within `activities.place-radius` (four
blocks) of the princess tower, the castle dungeon, the spring, the hanging-out spot or the old beta server finds
them; passing them again stays silent. Clicking the cake next to "Gute Nacht!" finds `Der Kuchen` and reminds you
that it is a lie. Three end portal frames around the map hold an eye each; clicking one takes it (stored per
account), and with all three the empty frame in the hut lights up for that player, finds `Das vierte Auge` and
carries them to the beta-server platform two seconds later. The spots are `secret-<name>`, `cake`, `eye-1` to
`eye-3`, `eye-frame` and `eye-arrival` in the map's `activities.yml`; `/lobbyset` sets each, the block spots by
looking at the block.

Press forward twice, backward twice, left, right, left, right, sneak, then jump to enter the Konami code. Release
each key between presses. The default bindings are `W W S S A D A D Shift Space`. A three-second gap resets the
sequence. Progress appears after the third correct input. Completion plays a note-block tune, finds `Retro`,
and unlocks `[1UP]`.

Near the configured pond, an empty fourth hotbar slot receives a fishing rod. Catches produce localized junk
descriptions; the default golden-carp chance is one in 500 catches. The carp is a permanent secret. The rod is
separate from the grappling-hook gadget and disappears when the player leaves the pond area.

The chicken pet lays an owner-only collectible every 180 seconds. One in 200 is golden and unlocks a secret.
Uncollected eggs disappear after 20 seconds. Players who have been AFK for ten minutes gain a floating sleep
label. Returning clears it and reports the time away. These timings and odds live under Lobby's `activities`.

The shop villager has separate lines for browsing, low balances, and being hit. Firework plates can be used again
after a 30-second per-player cooldown and explain when they are still waiting.

## Configure the lobby map

`/lobbyset` requires `blockcraft.lobby.setspawn`. It saves your current position to the active map's
`blockcraft/Lobby/activities.yml`. It does not place blocks or build a pond or course.
Map-specific overrides for the plugin's `activities` settings live in `blockcraft/Lobby/activity-settings.yml`.
The imported lobby sets `parkour-checkpoints: 0` for its existing course.

1. Build a fishable pond and run `/lobbyset pond` at its center. The default radius is 20 blocks.
2. Build a parkour course with start, checkpoint, and finish pressure plates. Stand on each plate and run
   `/lobbyset parkour-start`, `/lobbyset checkpoint-1` through `/lobbyset checkpoint-7`, and
   `/lobbyset parkour-finish`. Set `activities.parkour-checkpoints` before startup if the course has a different
   number of checkpoints. Zero checkpoints is supported.
3. Run `/setspawn jump` at the navigator's arrival point. Run `/lobbyset parkour-top` where the top-ten display
   belongs.
4. Register Easter hiding places with `/lobbyset egg-1` through `/lobbyset egg-20`. Configure at least as many
   spots as `activities.easter-eggs`, which defaults to ten. Each year selects a stable shuffled subset.
5. Run `/map save` when the server uses a world template. Otherwise, the next restart restores the old map and
   discards these positions. See the [map workflow](README.md#maps).

Run `/lobbyset check` before the final save. The command also works from the console and reports missing locations,
invalid or duplicate pressure plates, stations in another world, a missing jump warp, and insufficient or duplicate
Easter hiding places. It checks the setup, not whether the jumps are reachable or the pond is fishable.
Look at the finish sign and run `/lobbyset parkour-finish-sign` to register it. Its text updates on both sides.
The finish sign is optional. When configured, the check verifies that its block is a sign.

Set pieces stand where staff run `/setprop <name>` (permission `blockcraft.lobby.setholo`): the trophy at spawn, and
one piece per game named after its area, as in `npc-<area>`: `skywars`, `ttt`, `bedwars`, `endergames`, `duell`
(crossed swords over a red and blue arena), `rush` (EmeraldRush: an emerald over a mine cart of ore), `mlgrush` (a
bed, a sandstone tower and a glinting stick), `zombies` (a grave with a tombstone), `cores` (a beacon with its beam
over a red and blue plate) and `qsg` (a chest and a sword stuck in the ground). Stand next to or behind the game's
figure, so the piece does not hide it. Each is a group of block and item displays from `props` in the lobby's
`config.yml` that turns and bobs on the spot; the spot is kept in the map's `props.yml`, so `/map save` keeps it, and
the displays themselves are spawned at every start, never saved. The blocks carry each shape without the pack; the
pack's models only refine the items: without it, the trophy's star is a nether star, the TTT knife and the swords of
BedWars, Duell and QuickSG are iron swords, the EmeraldRush gem is an emerald, and the zombie hand reaching out of the
grave is a zombie head peeking out of it.

The course requires all configured checkpoints in order. Falling more than four blocks below the last checkpoint
returns the player there while the timer continues. Times and falls persist in `bc_parkour`. One hundred falls
unlock `Ausdauer`. Flying, gliding, riding, or teleporting cancels a run. Double jumps and gadgets are disabled
during it. An incomplete course reports that it is not ready.
The first successfully saved completion grants "Im Ziel" and 100 XP through the achievement catalog.
Later completions update best times without paying that achievement again. `/erfolge` opens the achievements menu.
Signs can only be edited while `/build` is enabled, including for owners and players in creative mode.

## Lobby figures, notes and quests

These spots are optional: `/lobbyset check` does not report them missing, and a feature without its spot simply
does not appear. Run `/lobbyset` where you stand and look for a figure, and while looking at a block for an item
lying on it; the item turns the way you face.

- **Game figures:** `/lobbyset npc-<area>` for `qsg`, `rush`, `duell`, `bedwars`, `mlgrush`, `zombies`, `cores`,
  `endergames`, `ttt` and `skywars`, in front of each area. The figure holds the game's item, a card above it names
  the game, its state and how many play, and a click of either hand joins it like the navigator. It turns to the
  nearest player within `game-npcs.look-radius` and now and then swings its arm or crouches; `game-npcs.skins` names
  whose skin each wears. Light particles of the game (`ambience.particles`) rise round it for players with effects
  on. The area's `/setspawn` warp stays where players arrive, so nobody lands inside the figure.
- **Podiums:** `/lobbyset podium-<board>` for any board of `leaderboards.holograms`. The board's first place stands
  there in their own skin, named with their value, and changes with the board. Every board line starts with the
  player's face, and within `leaderboards.own-radius` a line only you see shows your own place.
- **Lore notes:** `/lobbyset note-<key>` for each of `lore-notes.notes`, looking at the block it lies on. A click
  opens the note as a book; the first read pays `lore-notes.reward` coins and adds it to the secrets menu, where it
  can be read again.
- **Boards:** `/lobbyset board-changelog` and `/lobbyset board-rules` at spawn, looking at the block. The changelog
  opens as a dialog, and once for every returning player after `notice-boards.changelog-version` changes; the rules
  board runs the proxy's `notice-boards.rules-command`.
- **Quest givers:** `/lobbyset quest-<key>` for each of `quests`. Talking opens the quest; a finished one pays its
  reward once. A boss bar of news (`news`) rotates over the whole lobby.

## Friendship anniversaries

Accepted friendships retain their existing `bc_friends.created_at` timestamp. New acceptances already reset it
to the acceptance time. The lobby menu shows elapsed calendar days for online and offline friends.

Each recipient gets one private notice on the yearly date, using the network calendar zone and the current nick
when the friend is visible online. February 29 falls on February 28 in non-leap years. Join checks and a
once-per-minute calendar check cover both reconnects and friends who stay online through midnight. The proxy
remembers the day's notices in memory only, so a proxy restart on the day can repeat a notice once. Ignored and
invisible online friends produce no notice.

## Calendar

Keep the `calendar` sections in BlockcraftAPI and BlockcraftSystem identical. The default zone is `Europe/Berlin`.
Set `calendar.founded` to the actual opening date as a quoted `"yyyy-MM-dd"` value to enable the network birthday.
It is empty by default because no founding date is assumed.

| Occasion | Behavior |
|---|---|
| Easter | From two days before Easter Sunday through seven days after, configured egg displays appear in the lobby. Discoveries persist per player and year. Finding all selected eggs grants `[OSTERHASE]`. The imported lobby has twenty hiding places, `egg-1` to `egg-20`, near its landmarks. |
| Halloween | October 17 through 31. Glowing pumpkins sit at a yearly draw of the same hiding places as the Easter eggs; finding all of them grants `[KÜRBIS]`. Joining the lobby shows a hint with the phrase. Writing `Süßes oder Saures` in lobby chat gives either five coins or one of three tricks: a pumpkin hat for 300 seconds, a swarm of bats, or a ghost that darkens the screen. Each account can claim once per calendar day. The phrase and rewards are configurable under `activities`. |
| Winter | December 1 through 31. Only the lobby's look and sound change (see below). |
| Advent | December 1 through 24. The profile contains a calendar with today's door, and joining the lobby names it. Each account can claim 25 coins once per day. Door 24 also unlocks the snow trail. |
| Christmas | December 24 through 26. The Weihnachtsmann stands at `/lobbyset santa` and gives each account `activities.christmas-coins` (100) once per year, with a jingle. Without that spot he does not appear. Every ten minutes his sleigh, pulled by four reindeer and Rudolph, flies over a lobby player ringing Jingle Bells and drops four presents; each player catches one per flight for 15 coins. Lobby's `sleigh` section sets the timing, and staff send it on any day with `/schlitten`. |
| New Year | On December 31, a boss bar counts down the last ten seconds. At midnight the lobby's configured firework positions launch fireworks. Joining during January grants that year's tag. |
| Network birthday | The proxy's MOTD counts the years. Lobby players receive a temporary cake hat for the rest of the day. |
| OG | No date; staff switch it on with `/event og`. The BedWars map vote offers the old server's maps (`era: og`) instead of the current ones. |

The calendar's `halloween` and `winter` windows can be moved in both `calendar` sections; a window whose end comes
before its start runs across New Year.

Staff with `blockcraft.system.event` switch the occasions network-wide on the proxy. `/event` shows what is active
and whether it is forced, `/event christmas winter` makes exactly those occasions active whatever the date,
`/event none` turns every occasion off, and `/event auto` hands back to the date. The choice is the `occasions` row
in `bc_settings`, so it survives restarts. Every server reads it back every `calendar.poll-seconds` (5), and the
lobby redresses itself on its next tick. The staff log records each switch under the issuer. Date-bound details stay
on the date: the Advent door, the birthday's age and the January tag. While an occasion is active, the second line
of the server list shows its seasonal line from `motd.season`; the order is the birthday, then New Year's Eve,
Christmas, Advent, winter, Halloween, Easter and OG.

Lobby's `seasons` section dresses the lobby for each active occasion. Every block swap and the cover on top happen
only in the packets players receive, so the map and `/map save` never contain them. The defaults turn leaves pale,
torches and lanterns into soul flames, glowstone into jack-o'-lanterns and flowers into wither roses for Halloween, with
leaf litter on some grass; in winter every full block and leaf open to the sky gets a snow layer and the grass under
it turns snowy. Each occasion can also let a particle fall around players and play an occasional ambient sound near
them; both respect the effect and sound settings. A player who clicks a swapped block may see the real one again
until the chunk reloads.

## Wurfschlacht

During winter, Halloween and Easter (`throw-fight.during` in Lobby's and Duell's config), the season has its own
throwable: snowballs, pumpkins at Halloween, eggs at Easter. They are eggs in the season's look that never run out,
never hatch and only push. In the lobby everyone holds one in the fourth slot, which the pond rod takes over at the
pond; a hit tells the thrower their count and the target who it was. Hidden, vanished and racing players are left
alone. Duell offers the same fight as the mode Wurfschlacht while the season lasts: Sumo's ring and points, with
throws instead of fists. The shared mechanics are `ThrowFight` in BlockcraftAPI.

Winter also lets real snow fall: every chunk within the season's `snowfall-radius` of the spawn becomes a snowy
biome as it loads, and players with effects on get downfall, which the client draws as snowflakes. Players who set
their own weather keep it. The original biomes return when the season ends, for an unloaded chunk when it next
loads, and around every `/map save`. Snow and leaf litter only lie on the highest block of a column when that block
is solid, so tall plants keep both halves.

Temporary hats restore the previous helmet when they expire or the player leaves. Choosing another head while
wearing a temporary hat keeps that new choice. Earned tags, the snow trail, and the birthday gadget are accessible
through Extras without Premium. Existing purchased heads keep their original storage keys after their names change.

## Gameplay verification

The automated build checks message keys and covers calendar dates, Konami input, parkour progression, rematch
requests, hidden-command replies and bypasses, attacker-credit expiry, hour windows, curiosity selection,
wake-up counts, friendship age, every achievement's texts, and shooting-star wish cancellation. Database
integration tests cover concurrent join numbering, greeting caps, duplicate reward claims, rollback on failed
credit, the one-time OG snapshot, and duel pairs counting their draws. They need a disposable test database;
a build without one skips those tests.

Before deploying, verify these interactions with Minecraft clients on a local test network:

- Use two accounts for the welcome window, spectator `F` messages, defeat titles, report suggestions, and rematches.
- Draw twice against the same opponent, choose `[SPIEGEL]` without Premium, and rematch with a nick. Check that
  private greetings show the current nick. A decisive result between the two must restart the draw count.
- Try environmental deaths, a long arrow hit, a wooden-sword kill, and a rod pull into the void. Credit must expire
  after ten seconds and be consumed by the first death. Check one shared curiosity line at the end.
- Exercise all six moment conditions. A failed shop purchase must preserve Minimalist; a completed free purchase
  must disqualify it. Water and lava resets must disqualify Trittsicher. Cancelled damage must preserve Makellos.
- Run `/pl` a few times and `/version` as a regular player, then check the real responses with the staff bypass.
  The blocked commands must remain absent from regular-player tab completion.
- Enter the Konami code and select its earned tag with a non-Premium account.
- Complete the configured course, fall at a checkpoint, and retry to confirm the best time and leaderboard.
- Fish with the pond rod and collect a chicken egg. Lower the odds in local test config to exercise rare rewards.
- Check seasonal hats, per-player egg visibility, calendar doors, and midnight behavior using a suitable test date.
- During Halloween and winter, walk the lobby and look for swapped blocks that differ in shape from the real ones,
  rejoin, teleport, and cross midnight with the client open. Trick-or-treat until each trick has appeared once.
- At Christmas, set `/lobbyset santa`, check his skin and name, take the present, click again, and hit him.
- Run `/schlitten` with two accounts: the reindeer must lead, Santa must sit in the sleigh, Rudolph's nose must glow,
  the bells must follow the sleigh, and each account may catch only one present per flight.
- Shorten sleep thresholds in local config, poke a sleeping account, and wake it. Check the growing label,
  absence of damage and knockback, and the private count using the poker’s current nick.
- Set shooting-star odds to one in local config and choose a window containing the current hour in the network
  calendar zone. Test a wish within 15 seconds and a repeat. Neither may appear on another client, in console chat,
  or in the moderation advertisement log. A wish after the window must reach chat normally. Restore the normal odds
  after checking the sky trail.
- Use local friendship fixtures to check the yearly notice, elapsed days, reconnect deduplication, both
  recipients, nicknames, vanished friends, and midnight.
- Leave a menu while its database request is pending. Rewards should arrive without reopening the old menu.

The build does not validate client rendering, map geometry, or a live proxy-to-backend connection.
Use the [performance capture workflow](dev/performance/README.md) to compare server tick time, CPU, GC pauses, and client frame times.
