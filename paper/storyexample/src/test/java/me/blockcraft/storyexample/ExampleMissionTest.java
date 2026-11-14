package me.blockcraft.storyexample;

import me.blockcraft.story.cutscene.Cutscene;
import me.blockcraft.story.game.Chapters;
import me.blockcraft.story.game.Stage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExampleMissionTest {

    private static final YamlConfiguration CONFIG = yaml("src/main/resources/config.yml");
    private static final YamlConfiguration MESSAGES = yaml("src/main/resources/messages.yml");
    private static final YamlConfiguration PACKAGE = yaml("betonquest/QuestPackages/example/package.yml");

    @Test
    public void theCoreReadsTheMissionsStagesAndScenes() {
        var stages = Stage.load(null, CONFIG.getConfigurationSection("stages"));
        var chapters = Chapters.load(CONFIG.getString("mission.name"), stages.stream().map(Stage::name).toList(), CONFIG.getConfigurationSection("chapters"));
        assertEquals(List.of("briefing", "signal", "hold", "report", "ending"), chapters.named("signal").orElseThrow().stages());
        assertEquals(List.of("debrief"), chapters.named("relief").orElseThrow().stages());
        chapters.all().forEach(chapter -> {
            assertText("chapter." + chapter.name());
            assertTrue("no begin_" + chapter.name(), PACKAGE.contains("actions.begin_" + chapter.name()));
        });
        var scenes = CONFIG.getConfigurationSection("cutscenes.scenes");
        for (var name : scenes.getKeys(false)) {
            var scene = Cutscene.load(name, null, scenes.getConfigurationSection(name));
            assertTrue(name + " has no length", scene.duration() > 0);
            scene.lines().forEach(line -> {
                assertText(line.text());
                assertText("cutscene.speaker." + line.speaker());
            });
        }
        CONFIG.getConfigurationSection("mission.npcs").getKeys(false).forEach(npc -> {
            assertText("npc." + npc + ".name");
            assertText("npc." + npc + ".role");
        });
    }

    @Test
    public void thePackageNamesOnlyWhatTheMissionHas() {
        var actions = PACKAGE.getConfigurationSection("actions");
        var stages = CONFIG.getConfigurationSection("stages").getKeys(false);
        named("storystage (\\w+)").forEach(stage -> {
            assertTrue("no stage " + stage, stages.contains(stage));
            assertText("stage." + stage);
        });
        named("storyscene (\\w+)").forEach(scene -> assertTrue("no scene " + scene, CONFIG.contains("cutscenes.scenes." + scene)));
        named("storyvote \\w+ (\\w+)").forEach(choice -> assertText("choice." + choice));
        named("storyend (\\w+)").forEach(ending -> Stream.of("title", "subtitle", "summary")
                .forEach(part -> assertText("ending." + ending + "." + part)));
        named("storyafter \\d+ (\\w+)").forEach(action -> assertTrue("no action " + action, actions.contains(action)));
    }

    /** What every action and condition of the package names after an instruction, as the first group of {@code pattern}. */
    private static List<String> named(String pattern) {
        var instruction = Pattern.compile(pattern);
        return Stream.of("actions", "conditions").flatMap(section -> PACKAGE.getConfigurationSection(section).getValues(false).values().stream())
                .flatMap(text -> instruction.matcher(String.valueOf(text)).results().map(match -> match.group(1))).distinct().toList();
    }

    private static void assertText(String key) {
        assertTrue("messages.yml lacks " + key, MESSAGES.isString(key));
    }

    private static YamlConfiguration yaml(String path) {
        return YamlConfiguration.loadConfiguration(new File(path));
    }
}
