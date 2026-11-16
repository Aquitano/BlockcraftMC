# Story

The core for co-op story games of one to four players, built on [BetonQuest](https://betonquest.org) 3.3.0. It is a
library plugin and plays nothing by itself. A story game is a plugin of its own that depends on this one, brings its
mission and starts it with `Story.start`. [`paper/storyexample`](../storyexample/README.md) is the smallest such game:
the group `story` runs it, only with `docker compose --profile story`.

```sh
./gradlew stagePlugins
VERIFY_GAME=story dev/verify/verify.py up
VERIFY_GAME=story dev/verify/verify.py join
VERIFY_GAME=story dev/verify/verify.py scenario storyjump               # every stage by /story jump, with its state
```

`/start` in the waiting lobby starts the mission, and [`/story`](#debugging) jumps into it anywhere.

## A story game

A game plugin depends on `BlockcraftAPI`, `Story` and `BetonQuest`, calls `Story.start(this, loadout)` in `onEnable`
and `stop()` on what that returns in `onDisable`. Everything else is content:

| Where | What |
| --- | --- |
| `config.yml` | `mission`, `stages` and `cutscenes.scenes`, and any core setting it wants different |
| `messages.yml` | the mission's lines, and any core text it words differently |
| `betonquest/QuestPackages/<mission>` | the quest package, named like `mission.name`, which Gradle stages into `plugins/BetonQuest` |
| `voices.properties`, `soundtrack.properties` | each recording's length in ticks, for the sounds its pack brings |

The game's `config.yml` and `messages.yml` lie on the core's own, which document every key, and an operator's files
in the game's data folder lie on both. So a game states only what is its own.

The core has no weapons, no enemies and no map. `loadout` equips a member at the start of each chapter and after a
rejoin, and whatever else a game needs it registers with BetonQuest itself, as its own actions and objectives. Those
reach the party through `Story.game()`: its members, its facts and its stage. The world is left peaceful, so a game
with enemies sets the difficulty it needs after `Story.start`. A member who would die stands at the rally point again.

## How it is split

BetonQuest runs the conversations and each player's objectives from the game's quest package, and the package drives
the mission. Gradle stages the packages and the release's own `config.yml` (with the version set, the updater off,
German and the `blockhud` style) into `plugins/BetonQuest`, and switches off BetonQuest's FastStats reporting.

The party's story lives in this plugin as named facts, because BetonQuest keeps state per player and its `party`
action only reaches whoever is online and near at that moment. `stage` is where the party stands and `decision` what
it chose; the package may keep any other fact. [`Quests`](src/main/java/me/blockcraft/story/quest/Quests.java) adds
what the package needs. An action marked "party" changes the party's shared state, whichever member runs it:

| Kind | Instruction | Does |
| --- | --- | --- |
| condition | `storyfact <name> <expected>` | compares a fact: `fabrik`, `!=fabrik`, `>=3`, or `*` for "is set" |
| condition | `storystage <stage>`, `storyvoted <topic>` | the current stage, and whether this player has voted |
| party action | `storyfact <name> <value>` | sets a fact; `+1` or `-2` counts, `clear` removes it |
| party action | `storystage <stage>` | moves the party on to a later stage of config.yml's `stages` |
| action | `storyvote <topic> <choice>` | this player's vote; once all have voted, the fact `<topic>` holds the result |
| party action | `storyscene <scene> [then:<actions>]` | plays a cutscene, then runs `then` |
| party action | `storyafter <ticks> <actions>` | runs actions later, unless a jump or the round's end comes first |
| party action | `storymusic`, `storyambience <track\|stop>` | the soundtrack's two channels; a track loops until replaced |
| party action | `storysound <sound> [volume:] [pitch:] [at:]` | one sound for every member |
| party action | `storyend <ending>` | the ending's title, summary and rewards, then the round ends |
| objective | `holdspot <location> <radius> <seconds>` | a spot the party holds together: its time counts while a member stands there, the boss bar shows it, and it completes for every member at once |
| NPC | `blockcraft <id>` | one of the mission's `npcs`, a Mannequin that wears a BetterModel body where its `model` names one |

The game runs the package's `begin` when the mission starts, `enter_<stage>` once when the party enters a stage,
`stage_<stage>` for every member then and for a member who rejoins, and `decided_<topic>` when a vote is decided. Each
stage in config.yml sets the boss bar, a sound cue and its music and ambience. A member who drops out keeps their place
for `rejoin-seconds` and sees the same stage and facts on return. A vote waits only for the members who are here, and
a tie goes to whoever of them joined first.

`stage_<stage>` and a held spot run for every member, but each party action in them acts once for the party: for
the first member, and not again for a member who rejoins in the same stage. So `storyfact count +1` in `stage_<stage>`
counts one. Anywhere else a party action acts each time a member triggers it: a conversation is one member's, and a
`location` objective completes for each member who gets there. A party action by a member who does not play, a
spectator for example, is ignored. `storystage` ignores a stage the party is already in,
`storyscene` is ignored while the party's scene plays, and `storyafter` while a timer for the same actions waits. A
`folder` with a `delay` runs its actions outside the stage's trigger, so there they act for every member. A member who
was away when the party held the spot completes it on their return, and its party actions do not act again.

## Campaigns

A party's play of the mission is a campaign in `bc_story_campaigns`, with its members in `bc_story_campaign_members`.
Entering a stage saves the facts there, `stage` among them, and the ending is saved before the round ends, which waits
up to 30 seconds for it. A campaign stays unfinished when its server stops mid-mission, when every member leaves, or
when its ending could not be saved. When the waiting lobby's countdown ends, the game looks up the members' campaigns:
the first member in join order whose latest campaign is unfinished brings the party into it, at the stage it last
entered, and whatever happened after that stage's start plays again, a held spot included. A lookup that fails
starts the countdown again. The other members belong to that campaign from then on, so one they left unfinished is
over for them once it saves. The waiting lobby tells each player about their open campaign and recalls the `decision`
of their last finished one.

The save holds the facts as the party enters the stage, before `stage_<stage>` and `enter_<stage>` run, since those run
again on a resume. A fact set after `storystage` in the same action, a `folder` for example, is not in it, so set facts
before moving the party on.

Each save expects the revision the last one wrote and runs after it on one thread. A save whose answer was lost and is
tried again is recognised as stored, and once another server has saved the campaign past that revision, this one
stops saving it. A jump from the waiting lobby starts a new campaign, and a jump during the mission saves its stage and
facts into the party's campaign like any other stage; `/story` shows the campaign and its revision.

## Chapters

A game may divide its stages into `chapters` in config.yml, in order, each naming the stage it starts `from`:

```yaml
chapters:
  signal: {from: briefing}
  relief:
    from: debrief
    carries: [decision]
    world: relief
    landing: ["-193.5 47 -317.5 0 0"]
    rally: "-193.5 47 -317.5 0 0"
    npcs:
      mara: {at: "-191.5 47 -313.5 180 0"}
```

The package starts a chapter with `begin_<chapter>`, or `begin` where it has no such action, and `storyend` ends it.
Its campaign is stored under `<mission>.<chapter>`, so each chapter resumes and counts as finished on its own.
`storystage` stays within the chapter being played, and its name is the fact `chapter`.

Ending a chapter opens the next one, and the party goes straight on into it in the same round; the round ends after
the last chapter. A party that leaves on the way and comes back starts from the waiting lobby: it resumes a chapter it
left open, or plays the earliest chapter any of its members has still to finish, so nobody is taken past a chapter
they have not played. A party that has finished every chapter starts over. The waiting lobby tells each player their
own next chapter, named by `chapter.<chapter>` in messages.yml.

`carries` lists the facts a chapter takes over from the ending of the one before. Going on in the same round, those
are the facts the party has just ended with; starting from the waiting lobby, the ones the first member, in join
order, who finished the chapter before ended it with. They are set before `begin_<chapter>` runs, so its conditions
can read them. A `/story jump` clears them like every other fact, so a stage that needs one names it in its `facts`.

A mission without `chapters` is one chapter, stored under the mission's own name.

### A world for each chapter

A chapter without `world` plays in the server's own world, at the `landing`, `rally` and `npcs` of `mission`. A
chapter with `world: <name>` plays in a world of its own and names its own `landing`, `rally` and `npcs`. That world
is a copy of the dimension template `<name>`, a folder with its `region/`, among the game's chapter worlds:
`BLOCKCRAFT_STORY_WORLDS`, or `blockcraft/<game plugin>/worlds/` in the map bundle of the server's own world, where a
map's other files live too. It loads as `story:<name>`, standing still and peaceful like the server's own world, and
every stage's `spawn` and every cutscene of the chapter is a position in it. A game with enemies sets the difficulty
it needs in `WorldLoadEvent`. The server refuses to start a mission whose chapter world has no template.

The next chapter's world is copied and loaded while the party still plays the chapter before, and the last chapter's
world is unloaded once the party has left it, so at most two of them are loaded at once. Between two chapters the
screen fades to black under the next chapter's name and `travel.loading`, stays black for at least
`travel.hold-ticks`, and fades in once every member's client has loaded the new world, or after
`travel.ready-timeout-seconds`. The fade needs the pack; without it the members only see the title.

## Debugging

`/story` is for operators and works the same from the console, so RCON scripts and `dev/verify` use it too:

| Command | Does |
| --- | --- |
| `/story` or `/story state [json]` | chapter and its world, stage, facts, the playing scene and its tick, the hold, timers, soundtrack, votes and the campaign with its revision |
| `/story log [count]` | the last story events: facts, stages, actions, lines and answers, scenes, timers, sounds |
| `/story trace` | streams those events into your chat as they happen |
| `/story hud` | the state in the sidebar, updated twice a second |
| `/story jump <stage> [fact=value ...]` | puts the party at a stage with that stage's `facts`; a stage of another chapter, or any stage from the waiting lobby, starts that chapter first, in its world |
| `/story chapter <chapter>` | in the waiting lobby: the next start plays that chapter afresh, whatever the members finished or left open |
| `/story fact <name> [value\|+n\|clear]` | reads or changes a fact |
| `/story run <action> [player]` | runs a package action for the party, or for one member |
| `/story scene <name> [tick]` | previews a cutscene from any tick, to you alone; it starts no actions, keeps the party's music and does not count as seen |
| `/story scene stop` | ends the previews; `/story skip` ends the party's scene |
| `/story skip` | ends the scene, the hold or the next timer, whichever the party waits on |
| `/story reload` | reads config.yml again: scenes, stages, chapters and soundtrack. `/bq reload` reloads the quest package |

Every event also goes to the server log as `[story] <kind> <text>`, so
`docker logs <story container> | grep '\[story\]'` replays a session. `/story jump <stage> decision=<choice>` plays a
stage after a decision straight from the waiting lobby, and `dev/verify/verify.py scenario camera --command "story scene arrival"`
traces a scene's camera.

## Conversations

[`DialogueHud`](src/main/java/me/blockcraft/story/dialogue/DialogueHud.java) draws a conversation in a panel above the
hotbar: the speaker, up to five lines of text typing out, then up to five numbered answers. A page types out evenly in
`dialogue.typing-ticks` (1.5 seconds, as Typewriter's dialogue does), well ahead of its voice, and only a short page
types slower, at `slowest-characters-per-tick`. Sneak shows the rest of a
line, turns a page, confirms the highlighted answer or closes a line without answers, which also closes by itself
`dialogue.linger-seconds` after it has been spoken, or as soon as the player walks six blocks away. The number keys pick an answer at once and the scroll wheel moves
the highlight. While the panel is open the hotbar rests on slot 7, so a scroll lands on slot 6 or 8 and never on an
answer's number. Movement and the camera stay free; walking away ends the conversation as BetonQuest's `stop` setting says.

Party members within `dialogue.audience-radius` of the member who talks watch the same panel and hear the same voice.
Only that member answers: the others see the highlight move and the hint names who is answering. A member joins as a
line starts and stops watching on walking away, starting a conversation of their own or entering a cutscene. A vote
stays per member: each casts it in their own conversation.

The panel is one action bar line whose glyphs the pack's text shader moves into place (`hud_panel.glsl`, red 234), with
letters from the pack's `blockcraft:text` so their widths hold under a player's own font pack. It knows nothing of
BetonQuest: [`HudConversationIO`](src/main/java/me/blockcraft/story/quest/HudConversationIO.java) is BetonQuest's
conversation IO `blockhud`, the default in the staged `config.yml`, and passes lines and answers through. A replacement
for BetonQuest would call `DialogueHud.say` the same way. Cutscene subtitles use the same panel.

## Voices, music and ambience

Every spoken line has its own sound event, `story.voice.<key>`, played as its text appears:

| Line | Key | Example |
| --- | --- | --- |
| NPC option in the quest package | `<conversation>.<option>` | `vela.greeting` |
| Cutscene line in `messages.yml` | the message key | `cutscene.intro.2` |

The game's `voices.properties` holds each recording's length in ticks. A line without a recording is only read. A
line without answers stays open until its voice ends, and the server warns at start when a voice outlasts its
cutscene line's `ticks`.

Music is `story.music.<track>` and ambience `story.ambience.<name>`, each on its own channel. The game's
`soundtrack.properties` holds each file's length in ticks, after which the plugin plays a loop again, unless the track is listed in
`soundtrack.once`.

## Cutscenes

`cutscenes.scenes` in `config.yml` holds each cutscene, timed in ticks: camera shots that may follow an actor, the cast
with their walks, gestures and idle animations, subtitles with their voices, craft flying past, particle emitters,
sounds and effects, the `music` it starts and the package `actions` it runs at a tick. They play on the shared camera in `paper/api`'s scene package. Each member watches through their own
camera, and the story goes on once every member's scene has ended.

A shot eases in and out once and keeps its pace through the keyframes between, so a scene can be one long move rather
than a row of cuts. A cut further than `far-cut-blocks` dips to black, keeps the chunks
around the new camera loaded from the scene's start and fades in once they have reached the viewer, at most two seconds
later.

Gestures are animations of the BetterModel bodies that `resourcepack/RigModels.py` writes: point, beckon, nod, rest and
look, each blending in over a few ticks. An actor talks for as long as the voice of its line plays. It walks at an even
pace that starts and stops over eight ticks, with its legs at the pace the ground goes by (`walk-paces`), and faces where it goes.
Its body follows its head a few ticks later. An actor named like one of the mission's `npcs` plays in that NPC's own
figure. Between cutscenes an NPC turns towards the nearest member and talks while it speaks.

BetterModel hides the Mannequin under a body by sending it as invisible, which spectators, and so every cutscene viewer,
see as a translucent figure. Every Mannequin that wears a body therefore also wears an empty skin from the pack.

A cutscene every member has already seen may be skipped: holding sneak asks for it, the subtitle's corner counts who
holds, and it ends for everyone once they all hold together for half a second. A cutscene someone has not seen yet plays
to its end. Each member's finished cutscenes are kept in `bc_story_seen_scenes` as `<mission>.<scene>`.

An `EXPLOSION` with a `radius` removes the blocks around it, throws some of them as tumbling block displays that come to
rest on the ground, and leaves fire and smoke. The blocks are set directly, so each blast fires `BlocksChangedEvent` for
replays, and the blocks are put back when the round ends.
