package me.blockcraft.sternenfront.bot;

import org.junit.Test;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class BotConfigTest {

    @Test
    public void bundledPresetsIncreaseCombatPressureAndKeepContinuousFireForVeteran() {
        var source = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        var config = BotConfig.from(source, 6);
        var easy = config.profiles().get(BotDifficulty.EASY);
        var normal = config.profiles().get(BotDifficulty.NORMAL);
        var veteran = config.profiles().get(BotDifficulty.VETERAN);
        assertEquals(BotDifficulty.NORMAL, config.difficulty());
        assertTrue(easy.reactionTicks() > normal.reactionTicks());
        assertTrue(normal.reactionTicks() > veteran.reactionTicks());
        assertTrue(easy.spread() > normal.spread());
        assertTrue(normal.spread() > veteran.spread());
        assertTrue(easy.damageScale() < normal.damageScale());
        assertTrue(normal.damageScale() < veteran.damageScale());
        assertEquals(0, veteran.burstShots());
        assertEquals(1, veteran.damageScale(), 0.001);
        assertTrue(veteran.headshots());
        assertFalse(normal.headshots());
        assertEquals(12, config.idleTurnDegrees(), 0.001);
        assertEquals(1, config.stepHeight(), 0.001);
    }

    @Test
    public void profileOverridesDoNotChangeTheOtherDifficulties() {
        var source = new org.bukkit.configuration.MemoryConfiguration();
        source.set("bots.profiles.veteran.damage-scale", 0.8);
        source.set("bots.difficulty", "veteran");
        source.set("bots.step-height", 2);
        var config = BotConfig.from(source, 6);
        assertEquals(BotDifficulty.VETERAN, config.difficulty());
        assertEquals(0.8, config.profiles().get(BotDifficulty.VETERAN).damageScale(), 0.001);
        assertEquals(0.6, config.profiles().get(BotDifficulty.EASY).damageScale(), 0.001);
        assertEquals(1, config.stepHeight(), 0.001);
    }

    @Test
    public void humansReplaceBotSeatsWithoutExceedingTheTarget() {
        var source = new org.bukkit.configuration.MemoryConfiguration();
        var config = BotConfig.from(source, 6);
        assertEquals(4, config.needed(0));
        assertEquals(3, config.needed(1));
        assertEquals(0, config.needed(4));
        assertEquals(0, config.needed(6));
        source.set("bots.enabled", false);
        assertEquals(0, BotConfig.from(source, 6).needed(0));
    }

    @Test
    public void coverAndVoiceOverridesKeepSearchAndChatterBounded() {
        var source = new org.bukkit.configuration.MemoryConfiguration();
        source.set("bots.cover.search-radius", 100);
        source.set("bots.cover.peek-ticks", 0);
        source.set("bots.voice.range", 1000);
        source.set("bots.voice.volume", 4);
        source.set("bots.voice.soldier-cooldown-ticks", 0);
        source.set("bots.voice.global-cooldown-ticks", 0);
        var config = BotConfig.from(source, 6);
        assertEquals(16, config.cover().searchRadius(), 0.001);
        assertEquals(20, config.cover().peekTicks());
        assertEquals(32, config.voice().range(), 0.001);
        assertEquals(1, config.voice().volume(), 0.001);
        assertEquals(100, config.voice().soldierCooldownTicks());
        assertEquals(60, config.voice().globalCooldownTicks());
    }
}
