# Story

A co-op story mode for one to four players, built on [BetonQuest](https://betonquest.org) 3.3.0. It is the group
`story` and runs only with `docker compose --profile story`. This module is the engine. A mission brings its quest
package, its `mission`, `stages` and `cutscenes.scenes` in `config.yml`, its lines in `messages.yml` and its voices and
music in the pack. Without a `mission` the plugin stays off.

```sh
./gradlew stagePlugins
VERIFY_GAME=story dev/verify/verify.py up
VERIFY_GAME=story dev/verify/verify.py join
VERIFY_GAME=story dev/verify/verify.py scenario storyjump               # every stage by /story jump, with its state
```

`/start` in the waiting lobby starts the mission, and [`/story`](#debugging) jumps into it anywhere.

## How it is split

BetonQuest runs the conversations and each player's objectives from the mission's quest package in
`betonquest/QuestPackages/<mission>`, and the package drives the mission. Gradle
stages that folder and the release's own `config.yml` (with the version set, the updater off, German and the `blockhud`
style) into `plugins/BetonQuest`, and switches off BetonQuest's FastStats reporting.

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
| objective | `holdconsole <location> <radius> <seconds>` | the console defence, started by whoever stands there and completed for every member at once |
| NPC | `blockcraft <id>` | one of the mission's `npcs`, a Mannequin wearing a Sternenfront body from BetterModel |

The game runs the package's `begin` when the mission starts, `enter_<stage>` once when the party enters a stage,
`stage_<stage>` for every member then and for a member who rejoins, and `decided_<topic>` when a vote is decided. Each
stage in config.yml sets the boss bar, a sound cue and its music and ambience. A member who drops out keeps their place
for `rejoin-seconds` and sees the same stage and facts on return. A vote waits only for the members who are here, and
a tie goes to whoever of them joined first.

`stage_<stage>` and a held console run for every member, but each party action in them acts once for the party: for
the first member, and not again for a member who rejoins in the same stage. So `storyfact count +1` in `stage_<stage>`
counts one. Anywhere else a party action acts each time a member triggers it: a conversation is one member's, and a
`location` objective completes for each member who gets there. A party action by a member who does not play, a
spectator for example, is ignored. `storystage` ignores a stage the party is already in,
`storyscene` is ignored while the party's scene plays, and `storyafter` while a timer for the same actions waits. A
`folder` with a `delay` runs its actions outside the stage's trigger, so there they act for every member. A member who
was away when the party held the console completes it on their return, and its party actions do not act again.

## Campaigns

A party's play of the mission is a campaign in `bc_story_campaigns`, with its members in `bc_story_campaign_members`.
Entering a stage saves the facts there, `stage` among them, and the ending is saved before the round ends, which waits
up to 30 seconds for it. A campaign stays unfinished when its server stops mid-mission, when every member leaves, or
when its ending could not be saved. When the waiting lobby's countdown ends, the game looks up the members' campaigns:
the first member in join order whose latest campaign is unfinished brings the party into it, at the stage it last
entered, and whatever happened after that stage's start plays again, a held console included. A lookup that fails
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

## Debugging

`/story` is for operators and works the same from the console, so RCON scripts and `dev/verify` use it too:

| Command | Does |
| --- | --- |
| `/story` or `/story state [json]` | stage, facts, the playing scene and its tick, upload, timers, soundtrack, votes and the campaign with its revision |
| `/story log [count]` | the last story events: facts, stages, actions, lines and answers, scenes, timers, sounds |
| `/story trace` | streams those events into your chat as they happen |
| `/story hud` | the state in the sidebar, updated twice a second |
| `/story jump <stage> [fact=value ...]` | puts the party at a stage with that stage's `facts`, from the waiting lobby too |
| `/story fact <name> [value\|+n\|clear]` | reads or changes a fact |
| `/story run <action> [player]` | runs a package action for the party, or for one member |
| `/story scene <name> [tick]` | previews a cutscene from any tick, to you alone; it starts no actions, keeps the party's music and does not count as seen |
| `/story scene stop` | ends the previews; `/story skip` ends the party's scene |
| `/story skip` | ends the scene, the upload or the next timer, whichever the party waits on |
| `/story reload` | reads config.yml again: scenes, stages and soundtrack. `/bq reload` reloads the quest package |

Every event also goes to the server log as `[story] <kind> <text>`, so
`docker logs <story container> | grep '\[story\]'` replays a session. `/story jump <stage> decision=<choice>` plays a
stage after a decision straight from the waiting lobby, and `dev/verify/verify.py scenario camera --command "story scene intro"`
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

`voices.properties` holds each recording's length in ticks. A line without answers stays open until its voice ends, and
the server warns at start when a voice outlasts its cutscene line's `ticks`.

Music is `story.music.<track>` and ambience `story.ambience.<name>`, each on its own channel. `soundtrack.properties`
holds each file's length in ticks, after which the plugin plays a loop again, unless the track is listed in
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
