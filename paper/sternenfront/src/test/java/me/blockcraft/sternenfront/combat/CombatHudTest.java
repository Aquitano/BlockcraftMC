package me.blockcraft.sternenfront.combat;

import me.blockcraft.sternenfront.game.Ability;
import me.blockcraft.sternenfront.hero.HeroAbility;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.map.MinecraftFont;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class CombatHudTest {

    @Test
    public void distinctAbilityLabelsAndMeterStatesFitThePreviousHudBudget() {
        var messages = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
        var keys = Stream.concat(Arrays.stream(Ability.values()).map(Ability::key),
                Arrays.stream(HeroAbility.values()).map(HeroAbility::key)).toList();
        var labels = new HashSet<String>();
        for (var key : keys) {
            var label = messages.getString("combat.labels." + key);
            assertNotNull(key, label);
            assertTrue(key, labels.add(label));
            assertTrue(key, CombatHud.STATE_WIDTH + 2 * (MinecraftFont.Font.getWidth(label) + 1 + 4) <= 74);
            var hint = messages.getString("combat.hints." + key);
            assertNotNull(key, hint);
            assertTrue(key, hint.startsWith(label + ":"));
        }
        for (var key : List.of("guarding", "broken", "hot", "venting")) {
            var state = messages.getString("combat." + key).replace("<seconds>", "99").replaceAll("<[^>]+>", "");
            assertTrue(state, MinecraftFont.Font.getWidth(state) + 1 <= CombatHud.STATE_WIDTH);
        }
    }
}
