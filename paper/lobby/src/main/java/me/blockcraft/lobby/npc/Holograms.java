package me.blockcraft.lobby.npc;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Stacked floating text lines with a diamond helmet hovering above the first line. */
public final class Holograms {

    private static final double TOP_OFFSET = 1.5;
    private static final double LINE_SPACING = 0.35;
    private static final double HELMET_OFFSET = 1.0;

    private Holograms() {
    }

    public static void spawn(Location base, List<Component> lines) {
        Location top = base.clone().add(0, TOP_OFFSET, 0);
        for (int i = 0; i < lines.size(); i++) {
            ShopNpc.hologramLine(top.clone().subtract(0, LINE_SPACING * i, 0), lines.get(i));
        }
        top.getWorld().spawn(top.clone().add(0, HELMET_OFFSET, 0), ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.getEquipment().setHelmet(ItemStack.of(Material.DIAMOND_HELMET));
        });
    }
}
