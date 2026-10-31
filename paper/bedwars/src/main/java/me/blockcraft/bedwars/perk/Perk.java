package me.blockcraft.bedwars.perk;

import org.bukkit.inventory.ItemStack;

/** One choice of a {@link PerkGroup}: its key in {@code bc_player_settings} and the picture the perks menu shows. */
public interface Perk {

    String key();

    ItemStack icon();
}
