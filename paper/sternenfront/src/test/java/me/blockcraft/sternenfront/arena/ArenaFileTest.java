package me.blockcraft.sternenfront.arena;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ArenaFileTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void yardEnvironmentIsOptionalAndPreservesItsCoordinates() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.set("name", "Sturmwerft");
        yaml.createSection("headquarters");
        yaml.set("play-area", List.of(-97.0, 50.0, -70.0, 97.0, 128.0, 71.0));
        for (var position : List.of("relic", "overview", "lobby", "spectator")) yaml.set(position, "0.5 65 0.5");
        var file = temporary.newFile("arena.yml");
        yaml.save(file);
        assertEquals(YardEnvironment.EMPTY, ArenaFile.load(null, file.toPath()).environment());
        assertEquals(196, ArenaFile.load(null, file.toPath()).worldBorderSize(), 0.001);

        yaml.set("environment.steam", List.of("-10.5 67 -51.5"));
        yaml.set("environment.sparks", List.of("13.5 66.5 -49.5"));
        yaml.set("environment.crane.from", "-10.5 84 0.5");
        yaml.set("environment.crane.to", "10.5 84 0.5");
        yaml.save(file);
        var environment = ArenaFile.load(null, file.toPath()).environment();
        assertEquals(-10.5, environment.steam().getFirst().getX(), 0.001);
        assertEquals(67, environment.steam().getFirst().getY(), 0.001);
        assertEquals(-49.5, environment.sparks().getFirst().getZ(), 0.001);
        assertTrue(environment.crane().isPresent());
        var crane = environment.crane().orElseThrow();
        assertEquals(-10.5, crane.from().getX(), 0.001);
        assertEquals(10.5, crane.to().getX(), 0.001);
        assertEquals(84, crane.from().getY(), 0.001);
    }

    @Test
    public void worldBorderCanIncludeSceneryWithoutChangingThePlayArea() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.createSection("headquarters");
        yaml.set("play-area", List.of(-97.0, 50.0, -70.0, 97.0, 128.0, 71.0));
        for (var position : List.of("relic", "overview", "lobby", "spectator")) yaml.set(position, "0.5 65 0.5");
        var file = temporary.newFile("border.yml");
        yaml.set("world-border-size", 384);
        yaml.save(file);
        var map = ArenaFile.load(null, file.toPath());
        assertEquals(384, map.worldBorderSize(), 0.001);
        assertEquals(194, map.playArea().getWidthX(), 0.001);

        yaml.set("world-border-size", 100);
        yaml.save(file);
        assertEquals(196, ArenaFile.load(null, file.toPath()).worldBorderSize(), 0.001);
    }
}
