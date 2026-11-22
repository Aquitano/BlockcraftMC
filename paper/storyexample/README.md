# Story example

The smallest story game on the [story core](../story/README.md): one NPC, one cutscene, a spot to reach, a spot to
hold, a vote with two endings, and a second chapter in a world of its own that knows how the first one ended. The first
chapter plays on the flat world of the `story` service, and the second on a copy of the shared waiting island, which
the service mounts as the chapter world `relief`, so the example needs no map of its own. It exists to show how a story game is put
together and to give the core something to run in `dev/verify`. It is not a mission anyone is meant to play.

```sh
./gradlew stagePlugins
docker compose --profile story up -d mariadb velocity lobby story
```

Join, `/play story`, and `/start` in the waiting lobby. `/story jump <stage>` enters any stage.

## What is where

| File | Shows |
| --- | --- |
| [`StoryExamplePlugin`](src/main/java/me/blockcraft/storyexample/StoryExamplePlugin.java) | the whole Java side of a story game: `Story.start`, a loadout, `stop` |
| [`config.yml`](src/main/resources/config.yml) | a mission: landing and rally points, an NPC, the stages in order, the two chapters they divide into with the second's own world and positions, a cutscene, and a key of the game's own |
| [`messages.yml`](src/main/resources/messages.yml) | the texts a mission brings: its NPC, stages, choices, endings and cutscene lines |
| [`package.yml`](betonquest/QuestPackages/example/package.yml) | the quest package that drives the mission, with every action the core calls by name |
| [`mara.yml`](betonquest/QuestPackages/example/mara.yml) | a conversation that reads the party's stage and casts each member's vote |
| [`ExampleMissionTest`](src/test/java/me/blockcraft/storyexample/ExampleMissionTest.java) | a check worth copying: the core's loaders read the mission, and the package names only stages, scenes and texts that exist |

The first chapter starts with `begin_signal`, which plays the cutscene and enters `briefing`. Mara's conversation moves
the party on to `signal`, BetonQuest's `location` objective to `hold`, the core's `holdspot` to `report`, and the vote
to `ending`, where `storyend` picks the ending the party chose. The screen then goes black under the second chapter's
name, the party lands on the island, and `begin_relief` enters `debrief`, where Mara speaks of the decision the first
chapter carried over. Her `dismiss` answer ends the second chapter and with it the round.

## Start a game of your own

1. Copy this module, rename the plugin in `plugin.yml`, and add it to `settings.gradle.kts`.
2. Name the mission in `config.yml` and the quest package folder alike, and add the folder to `stagePlugins` in the
   root `build.gradle.kts`.
3. Give the game a server group: a service in `docker-compose.yml` like `story`, with the game's plugin directory in
   `COPY_PLUGINS_SRC` and its map as the world template. Chapter worlds go into that map, under
   `blockcraft/<plugin>/worlds/<world>/`.
4. Put weapons, enemies and anything else the mission needs into the game's own code, registered with BetonQuest as
   actions and objectives.
