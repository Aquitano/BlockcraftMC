package me.blockcraft.api.cosmetic;

import me.blockcraft.api.settings.PlayerSettingChangedEvent;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.settings.PlayerSettingsLoadedEvent;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class WeaponStyles implements Listener {

    private static final NamespacedKey TAG = new NamespacedKey("blockcraft", "weapon_stick");
    private final PlayerSettings settings;

    public WeaponStyles(PlayerSettings settings) {
        this.settings = settings;
    }

    public WeaponStyle selected(Player player) {
        return settings.get(player, PlayerSetting.WEAPON_STYLE).flatMap(WeaponStyle::byKey).orElse(WeaponStyle.STICK);
    }

    public ItemStack stick(Player player, ItemStack stick) {
        if (stick.getType() != Material.STICK) {
            return stick;
        }
        return selected(player).skin(tag(stick));
    }

    public static ItemStack tag(ItemStack stick) {
        stick.editMeta(meta -> meta.getPersistentDataContainer().set(TAG, PersistentDataType.BOOLEAN, true));
        return stick;
    }

    public static boolean isStick(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(TAG);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoaded(PlayerSettingsLoadedEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChanged(PlayerSettingChangedEvent event) {
        if (event.setting() == PlayerSetting.WEAPON_STYLE) {
            refresh(event.getPlayer());
        }
    }

    private void refresh(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var item = inventory.getItem(slot);
            if (isStick(item)) {
                inventory.setItem(slot, stick(player, item));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isStick(event.getItem().getItemStack())) {
            event.getItem().setItemStack(stick(player, event.getItem().getItemStack()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        var item = event.getItemDrop();
        if (isStick(item.getItemStack())) {
            item.setItemStack(WeaponStyle.STICK.skin(item.getItemStack()));
        }
    }

}
