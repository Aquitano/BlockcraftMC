package me.blockcraft.sternenfront.combat;

import me.blockcraft.sternenfront.PackFonts;
import me.blockcraft.sternenfront.game.Ability;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.HudPanel;
import me.blockcraft.sternenfront.hero.HeroAbility;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.map.MinecraftFont;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class CombatHudTest {

    @Test
    public void everyAbilityHasANameAndAHintAndMeterStatesFitTheHud() {
        var messages = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
        var classKeys = Arrays.stream(Faction.values()).flatMap(faction -> Arrays.stream(CombatClass.values())
                .flatMap(combatClass -> combatClass.abilities(faction).stream()).map(ability -> ability.messageKey(faction)));
        var reinforcementKeys = Arrays.stream(Reinforcement.values())
                .flatMap(unit -> unit.abilities().stream().map(ability -> ability.messageKey(unit.faction())));
        var nameKeys = Stream.of(classKeys, reinforcementKeys,
                Arrays.stream(HeroAbility.values()).map(ability -> "hero.ability." + ability.key())).flatMap(keys -> keys);
        Stream.concat(nameKeys, Stream.of("hero.leap")).forEach(key -> {
            var name = messages.getString(key + ".name");
            assertNotNull(key, name);
            assertFalse(name, HudPanel.capitals(name.replaceAll("<[^>]+>", "")).contains("?"));
        });
        Stream.concat(Arrays.stream(Ability.values()).map(Ability::key), Arrays.stream(HeroAbility.values()).map(HeroAbility::key))
                .forEach(key -> assertNotNull(key, messages.getString("combat.hints." + key)));
        for (var key : List.of("guard", "guarding", "broken", "heat", "hot", "venting")) {
            var state = messages.getString("combat." + key).replace("<seconds>", "99").replaceAll("<[^>]+>", "");
            assertTrue(state, MinecraftFont.Font.getWidth(state) + 1 <= CombatHud.STATE_WIDTH);
        }
    }

    /** The ability panel goes in the action bar's middle, which the HUD finds from these widths. */
    @Test
    public void theMetersGlyphsAreAsWideAsTheHudCounts() throws IOException {
        var ui = PackFonts.advances("ui");
        assertEquals(CombatHud.METER_ICON_ADVANCE, (int) ui.get(0xE221));
        assertEquals(CombatHud.METER_ICON_ADVANCE, (int) ui.get(0xE222));
        for (int cells = 0; cells <= 10; cells++) {
            assertEquals(CombatHud.METER_ADVANCE, (int) ui.get(0xE240 + cells));
        }
    }
}
