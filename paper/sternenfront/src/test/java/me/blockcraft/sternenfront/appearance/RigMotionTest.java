package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.game.CombatClass;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;

import static org.junit.Assert.*;

public class RigMotionTest {

    @Test
    public void bundledClassesHaveDistinctGaitsThatRestAtIdleAndCompleteOneCycle() throws Exception {
        var profiles = new EnumMap<CombatClass, RigMotion>(CombatClass.class);
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            var config = YamlConfiguration.loadConfiguration(reader);
            for (var role : CombatClass.values()) {
                var profile = RigMotion.from(config, role);
                profiles.put(role, profile);
                assertEquals(new RigMotion.Pose(0, 0, 0, 0), profile.pose(0, 0));
                double moved = profile.strideBlocks() / profile.cycleTicks();
                assertEquals(Math.TAU, profile.advance(moved) * profile.cycleTicks(), 0.000001);
                assertEquals(profile.pose(0, moved).step(), -profile.pose(Math.PI, moved).step(), 0.000001);
                assertEquals(profile.bobHeight(), profile.pose(Math.PI / 2, moved).bob(), 0.000001);
                assertEquals(profile.advance(moved), profile.advance(100), 0.000001);
            }
        }
        assertEquals(4, new HashSet<>(profiles.values()).size());
        assertTrue(profiles.get(CombatClass.HEAVY).strideBlocks() < profiles.get(CombatClass.SOLDIER).strideBlocks());
        assertTrue(profiles.get(CombatClass.SCOUT).cycleTicks() < profiles.get(CombatClass.SOLDIER).cycleTicks());
        assertTrue(profiles.get(CombatClass.MEDIC).recoilDegrees() < profiles.get(CombatClass.SOLDIER).recoilDegrees());
    }
}
