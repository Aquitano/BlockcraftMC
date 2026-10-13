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
| BlockcraftSystem | `/ping` rotates its reply and always shows the measured ping. Sending a message or party invitation to yourself gets a different refusal. |

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
Emeralds. A threshold of zero or less disables that category. Half-heart time uses a tick-level health check and
stops on death, leaving the round, or the end of the round. The counters stay in memory for that round only.

## Moment achievements

Each completed moment adds a personal recap note and grants its achievement once through `bc_unlocks`.

| Moment | Condition | Default XP |
|---|---|---|
| Dagobert | Win EmeraldRush with at least 100 Emeralds and no Emeralds spent. The threshold is `achievements.emeraldrush.dagobert.threshold`. | 100 |
| Minimalist | Win EmeraldRush without completing a purchase in the round shop, including a free purchase. | 100 |
| Pazifist | Win QuickSG with zero kills in the round. | 100 |
| Makellos | Win a combat duel without taking any non-cancelled damage during the match. | 100 |
| Trittsicher | Reach the SuperJump goal without a water or lava reset. Winning by timeout does not count. | 100 |
| Nachteule | Finish a round between 03:00 inclusive and 04:00 exclusive in the network calendar zone. `moments.night-owl` controls the hours. | 50 |

## Mirror duels

Two consecutive draws against the same opponent unlock `[SPIEGEL]` for both players. An intervening duel with a
different opponent or a decisive result breaks the sequence. The latest result for each account persists in
`bc_duel_history`, while the tag and remembered opponents use `bc_unlocks`. The history update and both tag
unlocks commit together. Later encounters give each duellist a private greeting using the other player's current
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

## Lobby discoveries

The secrets menu shows progress and unlocks riddles after the configured number of discoveries. Each secret pays
once. The first recorded finder gets a network announcement. Finding every secret available without Premium
unlocks `[ENTDECKER]`; the chicken's golden egg is an additional Premium discovery.

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

The course requires all configured checkpoints in order. Falling more than four blocks below the last checkpoint
returns the player there while the timer continues. Times and falls persist in `bc_parkour`. One hundred falls
unlock `Ausdauer`. Flying, gliding, riding, or teleporting cancels a run. Double jumps and gadgets are disabled
during it. An incomplete course reports that it is not ready.

## Calendar

Keep the `calendar` sections in BlockcraftAPI and BlockcraftSystem identical. The default zone is `Europe/Berlin`.
Set `calendar.founded` to the actual opening date as a quoted `"yyyy-MM-dd"` value to enable the network birthday.
It is empty by default because no founding date is assumed.

| Occasion | Behavior |
|---|---|
| Easter | From two days before Easter Sunday through seven days after, configured egg displays appear in the lobby. Discoveries persist per player and year. Finding all selected eggs grants `[OSTERHASE]`. |
| Halloween | October 25 through 31. Writing `Süßes oder Saures` in lobby chat gives either five coins or a pumpkin hat for 300 seconds. Each account can claim once per calendar day. The phrase and rewards are configurable under `activities`. |
| Advent | December 1 through 24. The profile contains a calendar with today's door. Each account can claim 25 coins once per day. Door 24 also unlocks the snow trail. |
| New Year | On December 31, a boss bar counts down the last ten seconds. At midnight the lobby's configured firework positions launch fireworks. Joining during January grants that year's tag. |
| Network birthday | The proxy's MOTD counts the years. Lobby players receive a temporary cake hat for the rest of the day. |

Temporary hats restore the previous helmet when they expire or the player leaves. Choosing another head while
wearing a temporary hat keeps that new choice. Earned tags, the snow trail, and the birthday gadget are accessible
through Extras without Premium. Existing purchased heads keep their original storage keys after their names change.

## Gameplay verification

The automated build checks message keys and covers calendar dates, Konami input, parkour progression, rematch
requests, and head-key compatibility. Database integration tests cover concurrent join numbering, greeting caps,
duplicate reward claims, rollback on failed credit, and the one-time OG snapshot.

Before deploying, verify these interactions with Minecraft clients on a local test network:

- Use two accounts for the welcome window, spectator `F` messages, defeat titles, report suggestions, and rematches.
- Enter the Konami code and select its earned tag with a non-Premium account.
- Complete the configured course, fall at a checkpoint, and retry to confirm the best time and leaderboard.
- Fish with the pond rod and collect a chicken egg. Lower the odds in local test config to exercise rare rewards.
- Check seasonal hats, per-player egg visibility, calendar doors, and midnight behavior using a suitable test date.
- Leave a menu while its database request is pending. Rewards should arrive without reopening the old menu.

The build does not validate client rendering, map geometry, or a live proxy-to-backend connection.
Use the [performance capture workflow](dev/performance/README.md) to compare server tick time, CPU, GC pauses, and client frame times.
