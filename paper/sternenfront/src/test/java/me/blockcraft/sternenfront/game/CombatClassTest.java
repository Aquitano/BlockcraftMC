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
            assertEquals(republic.get(index).shotSound(), separatists.get(index).shotSound());
        }
    }

    @Test
    public void soldierWeaponChoiceSurvivesRoleChangesAndLeavesDroidsAlone() {
        var fighter = new Fighter(new Heat.Rules(10, 5, 40, 0.2, 0.25), ability -> new Charges(1, 20));
        for (var expected : List.of(Blaster.A280, Blaster.E11D, Blaster.CARBINE)) {
            fighter.cycleWeapon(Faction.KOLONIEN);
            assertEquals(expected, fighter.blaster(Faction.KOLONIEN).orElseThrow());
            assertEquals(Blaster.E5, fighter.blaster(Faction.HEGEMONIE).orElseThrow());
            fighter.combatClass(CombatClass.MEDIC);
            assertEquals(Blaster.PISTOL, fighter.blaster(Faction.KOLONIEN).orElseThrow());
            fighter.combatClass(CombatClass.SOLDIER);
            assertEquals(expected, fighter.blaster(Faction.KOLONIEN).orElseThrow());
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
