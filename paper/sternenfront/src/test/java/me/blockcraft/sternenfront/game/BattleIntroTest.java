package me.blockcraft.sternenfront.game;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BattleIntroTest {
    @Test
    public void configuredPostShotKeepsMovingBeforeTheHeadquartersCut() throws Exception {
        try (var input = new InputStreamReader(BattleIntroTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            var config = YamlConfiguration.loadConfiguration(input);
            int duration = config.getInt("intro.duration-ticks");
            var path = BattleIntro.path(config.getMapList("intro.shots"), duration, ignored -> new Location(null, 0, 0, 0)).camera();
            assertNotEquals(path.sample(duration / 2), path.sample(duration * 2 / 3));
            for (int ticks = 40; ticks <= 600; ticks++) {
                assertEquals(ticks, BattleIntro.path(config.getMapList("intro.shots"), ticks,
                        ignored -> new Location(null, 0, 0, 0)).camera().duration());
            }
        }
    }

    @Test
    public void briefingStartsOnTheHeadquartersShotAndFacesItsCameraForBothFactions() throws Exception {
        try (var input = new InputStreamReader(BattleIntroTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            var config = YamlConfiguration.loadConfiguration(input);
            int duration = config.getInt("intro.duration-ticks");
            for (float yaw : new float[]{90, 270}) {
                var spawn = new Location(null, 82.5, 65, -5.5, yaw, 0);
                var route = BattleIntro.path(config.getMapList("intro.shots"), duration, ignored -> spawn);
                assertEquals(135, route.briefingStart());
                assertEquals(0, route.briefingTick(134));
                assertEquals(0, route.briefingTick(135));
                assertEquals(12, route.briefingTick(147));
                var actor = route.actorAt(spawn);
                var camera = route.camera().sample(route.briefingStart()).in(null);
                var towardCamera = camera.toVector().subtract(spawn.toVector()).setY(0).normalize();
                assertEquals(1, actor.getDirection().dot(towardCamera), 0.00001);
                assertEquals(yaw, spawn.getYaw(), 0);
            }
        }
    }

    @Test
    public void focusIsLimitedToTheConfiguredShotAndDoesNotFollowASkip() {
        var window = new BattleIntro.FocusWindow(true, 0.75, 0.95);
        assertFalse(window.active(134, 180, false));
        assertTrue(window.active(135, 180, false));
        assertTrue(window.active(170, 180, false));
        assertFalse(window.active(171, 180, false));
        assertFalse(window.active(140, 180, true));
        assertFalse(new BattleIntro.FocusWindow(false, 0.75, 0.95).active(140, 180, false));
        assertFalse(new BattleIntro.FocusWindow(true, Double.NaN, 0.95).active(140, 180, false));
        assertFalse(new BattleIntro.FocusWindow(true, 0.95, 0.75).active(140, 180, false));
    }
}
