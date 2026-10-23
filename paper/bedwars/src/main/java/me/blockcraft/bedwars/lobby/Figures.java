package me.blockcraft.bedwars.lobby;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.ItemStack;

/** The players-shaped figures the game stands around: shop dealers, team members in the lobby, the CPS tester. */
public final class Figures {

    private Figures() {
    }

    /** Stands where it is put, cannot be hurt, and is gone with the server instead of being saved into the map. */
    public static Mannequin spawn(Location location, ResolvableProfile skin, Component name, ItemStack... armor) {
        return location.getWorld().spawn(location, Mannequin.class, figure -> {
            figure.setProfile(skin);
            figure.setDescription(null);
            figure.customName(name);
            figure.setCustomNameVisible(true);
            figure.setImmovable(true);
            figure.setGravity(false);
            figure.setInvulnerable(true);
            figure.setPersistent(false);
            figure.setRemoveWhenFarAway(false);
            if (armor.length > 0) {
                figure.getEquipment().setArmorContents(armor);
            }
        });
    }
}
