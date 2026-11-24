package me.blockcraft.api.game;

import org.bukkit.Material;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class LegacyDamageTest {

    @Test
    public void weaponsAddTheirOneEightBonusSoSwordsBeatAxesAndOtherItemsKeepTheirs() {
        assertEquals(Optional.of(7), LegacyDamage.bonus(Material.DIAMOND_SWORD));
        assertEquals(Optional.of(5), LegacyDamage.bonus(Material.STONE_SWORD));
        assertEquals(Optional.of(4), LegacyDamage.bonus(Material.STONE_AXE));
        assertEquals(Optional.of(3), LegacyDamage.bonus(Material.WOODEN_AXE));
        assertEquals(Optional.of(1), LegacyDamage.bonus(Material.WOODEN_SHOVEL));
        assertEquals(Optional.empty(), LegacyDamage.bonus(Material.STICK));
        assertEquals(Optional.empty(), LegacyDamage.bonus(Material.TRIDENT));
    }
}
