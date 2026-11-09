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

`/start` in the waiting lobby starts the mission:

1. An intro cutscene: a flight over the basin, then Kommandantin Vela walks into the headquarters and speaks.
2. Right-click Vela. The conversation opens in a native dialog screen and ends with "Wir sind bereit".
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

## Cutscenes

`cutscenes.scenes` in `config.yml` holds each cutscene as camera shots, Vela's walk, subtitles and effects, timed in
ticks. They play on the shared camera in `paper/api`'s scene package. Each member watches through their own camera and
may skip alone with sneak; the story goes on once every member's scene has ended.
