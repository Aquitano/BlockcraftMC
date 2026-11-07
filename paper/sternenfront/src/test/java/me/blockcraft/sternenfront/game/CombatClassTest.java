package me.blockcraft.sternenfront.game;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CombatClassTest {

    @Test
    public void eachFactionsHeavyCarriesItsOwnSignatureOnQ() {
        assertEquals(List.of(Ability.SHIELD_WALL, Ability.MINING_CHARGE), CombatClass.HEAVY.abilities(Faction.KOLONIEN));
        assertEquals(List.of(Ability.SHIELD_WALL, Ability.SIEGE_MODE), CombatClass.HEAVY.abilities(Faction.HEGEMONIE));
    }

    @Test
    public void everyClassAndWhatItCarriesHasItsFactionsNameAndLore() {
        var messages = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
        for (var faction : Faction.values()) {
            for (var combatClass : CombatClass.values()) {
                assertHasNameAndLore(messages, combatClass.messageKey(faction));
                assertHasNameAndLore(messages, "blaster." + combatClass.blaster(faction).key());
                combatClass.abilities(faction).forEach(ability -> assertHasNameAndLore(messages, ability.messageKey(faction)));
            }
        }
    }

    @Test
    public void eachFactionCarriesItsOwnWeaponsWithTheSameCombatProfiles() {
        var republic = List.of(Blaster.CARBINE, Blaster.REPEATER, Blaster.LONG_RIFLE, Blaster.PISTOL);
        var separatists = List.of(Blaster.E5, Blaster.WRIST_BLASTER, Blaster.E5S, Blaster.SE14);
        var roles = CombatClass.values();
        for (int index = 0; index < roles.length; index++) {
            assertEquals(republic.get(index), roles[index].blaster(Faction.KOLONIEN));
            assertEquals(separatists.get(index), roles[index].blaster(Faction.HEGEMONIE));
            assertEquals(republic.get(index), separatists.get(index).profile());
            assertEquals(SoundCue.shot(republic.get(index)), SoundCue.shot(separatists.get(index)));
        }
    }

    private static void assertHasNameAndLore(YamlConfiguration messages, String key) {
        assertFalse(key, messages.getString(key + ".name", "").isEmpty());
        assertFalse(key, messages.getStringList(key + ".lore").isEmpty());
    }

    @Test
    public void noTwoAbilitiesShareTheMaterialTheirCooldownShowsOn() {
        var materials = Arrays.stream(Ability.values()).map(Ability::material).collect(Collectors.toSet());
        assertEquals(Ability.values().length, materials.size());
        assertFalse(materials.contains(Material.AIR));
    }
}
