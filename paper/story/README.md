# Story (prototype)

A ten-minute co-op mission for one to four players on Wrack von Tessar, built to find out how far
[BetonQuest](https://betonquest.org) 3.3.0 carries a story mode. It is the group `story` and runs only with
`docker compose --profile story`.

## Playing it

```sh
./gradlew stagePlugins
VERIFY_GAME=story dev/verify/verify.py up
VERIFY_GAME=story dev/verify/verify.py join
VERIFY_GAME=story dev/verify/verify.py scenario story --choice fabrik   # or gefangene, and --rejoin
```

`/start` in the waiting lobby starts the mission, and `/storyscene <name>` plays one cutscene to an operator alone:

1. An intro cutscene: a dropship over the basin, then Kommandantin Vela walks into the headquarters and speaks.
2. Right-click Vela. The conversation opens in the panel above the hotbar and ends with "Wir sind bereit".
3. Walk to the relay in the refinery (BetonQuest's `location` objective).
4. Hold the console against waves of B1 droids (`holdconsole`, this plugin's objective). The upload only runs while
   a member stands at the console. The carbine fires with the use key.
5. Report back to Vela. Every member votes in her conversation; a tie goes to the member who joined first.
6. One of two ending cutscenes follows the decision, then the round ends.

## How it is split

BetonQuest runs the conversations and each player's objectives from the quest package in
[`betonquest/QuestPackages/tessar`](betonquest/QuestPackages/tessar). Gradle stages that folder and the release's own
`config.yml` (with the version set, the updater off, German and the `dialog` style) into `plugins/BetonQuest`, and
switches off BetonQuest's FastStats reporting.

The party's state lives in this plugin, because BetonQuest keeps state per player and its `party` action only reaches
whoever is online and near at that moment. [`Quests`](src/main/java/me/blockcraft/story/quest/Quests.java) adds what
the package needs to read and change it:

| Kind | Instruction | Does |
| --- | --- | --- |
| condition | `storystage <stage>`, `storychoice <choice>`, `storyvoted` | reads the party's stage, decision and this player's vote |
| action | `storystage <stage>`, `storyvote <choice>` | moves the whole party forward, casts this player's vote |
| objective | `holdconsole <location> <radius> <seconds>` | the console defence, completed for every member at once |
| NPC | `blockcraft <key>` | a Mannequin this plugin spawns, wearing a Sternenfront body from BetterModel |

When the party enters a stage, every member runs the package action `stage_<stage>`, which hands out that stage's
objectives. A member who drops out keeps their place for `rejoin-seconds` and runs it again on return, so they see
the same stage and the same decision as the others. The decision is also stored per member in `bc_story_choices`,
for a later mission to read.

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
line starts and stops watching on walking away, starting a conversation of their own or entering a cutscene. The
report's decision stays a vote, which each member casts in their own conversation with Vela.

The panel is one action bar line whose glyphs the pack's text shader moves into place (`hud_panel.glsl`, red 234), with
letters from the pack's `blockcraft:text` so their widths hold under a player's own font pack. It knows nothing of
BetonQuest: [`HudConversationIO`](src/main/java/me/blockcraft/story/quest/HudConversationIO.java) is BetonQuest's
conversation IO `blockhud`, the default in the staged `config.yml`, and passes lines and answers through. A replacement
for BetonQuest would call `DialogueHud.say` the same way. Cutscene subtitles use the same panel.

## Voices

Every spoken line has its own sound event, `story.voice.<key>`, played as its text appears:

| Line | Key | Example |
| --- | --- | --- |
| NPC option in the quest package | `<conversation>.<option>` | `vela.greeting` |
| Cutscene line in `messages.yml` | the message key | `cutscene.intro.2` |

To use a recording, put it at `resourcepack/audio/story/recorded/<key>.wav` (`.ogg`, `.flac` and `.mp3` work too), as
`vela.greeting.wav`, and run `uv run resourcepack/StoryVoices.py`. A recording wins over the Piper placeholder in
`audio/story/placeholder/`. The script normalizes it, puts the radio filter on `funk` lines, writes it into the pack and
writes its length to `voices.properties`. A line without answers stays open until its voice ends, and the server warns
at start when a voice outlasts its cutscene line's `ticks`. Without arguments the script also lists every line that has
no audio. `--model <path to de_DE-thorsten-high.onnx>` synthesizes placeholders for new or changed lines.

## Cutscenes

`cutscenes.scenes` in `config.yml` holds each cutscene, timed in ticks: camera shots that may follow an actor, the cast
with their walks, gestures and idle animations, subtitles with their voices, craft flying past, particle emitters,
sounds and effects. Gestures are animations of the BetterModel bodies that `resourcepack/RigModels.py` writes: talk,
point, beckon, nod, rest and look. Between cutscenes Vela turns towards the nearest member and gestures while she speaks. They play on the shared camera in `paper/api`'s scene package. Each member watches through their own camera,
and the story goes on once every member's scene has ended.

A cutscene every member has already seen may be skipped: holding sneak asks for it, the subtitle's corner counts who
holds, and it ends for everyone once they all hold together for half a second. A cutscene someone has not seen yet plays
to its end. Each member's finished cutscenes are kept in `bc_story_seen_scenes` as `<mission>.<scene>`.
