package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.cosmetic.Trail;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class TrailsMenu extends Menu {

    private static final int BACK_SLOT = 7;
    private static final int REMOVE_SLOT = 8;

    private final Menus menus;

    TrailsMenu(Menus menus, Player viewer) {
        super(1, menus.messages().get("trails.title"));
        this.menus = menus;
        refresh(viewer);
        backButton(BACK_SLOT, menus::openPremium);
    }

    private void refresh(Player player) {
        var selected = menus.settings().get(player, PlayerSetting.TRAIL);
        for (var choice : Trail.values()) {
            boolean equipped = selected.filter(choice.key()::equals).isPresent();
            var icon = Items.of(choice.icon())
                    .name(menus.messages().get("trails.name." + choice.key()))
                    .lore(menus.messages().get("trails.lore." + choice.key()),
                            menus.messages().get(equipped ? "cosmetics.equipped" : "cosmetics.owned"),
                            menus.messages().get(menus.settings().enabled(player, PlayerSetting.EFFECTS)
                                    ? "cosmetics.preview-lore" : "cosmetics.preview-disabled"));
            if (equipped) {
                icon.glow();
            }
            set(choice.ordinal(), icon.build());
        }
        set(REMOVE_SLOT, Items.of(Material.BARRIER).name(menus.messages().get("cosmetics.remove"))
                .lore(menus.messages().get(selected.isPresent() ? "cosmetics.remove-lore" : "cosmetics.empty-remove")).build());
    }

    @Override
    protected void onSettingChanged(Player player, PlayerSetting setting) {
        if (setting == PlayerSetting.TRAIL || setting == PlayerSetting.EFFECTS) {
            refresh(player);
        }
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        if (slot == REMOVE_SLOT) {
            return menus.settings().get(player, PlayerSetting.TRAIL).isPresent();
        }
        if (slot >= 0 && slot < Trail.values().length) {
            if (!player.hasPermission(LobbyPermissions.PREMIUM)) {
                return false;
            }
            if (event.isRightClick()) {
                return menus.settings().enabled(player, PlayerSetting.EFFECTS);
            }
            return menus.settings().get(player, PlayerSetting.TRAIL).filter(Trail.values()[slot].key()::equals).isEmpty();
        }
        return true;
    }

    @Override
    protected Sounds clickSound(int slot, InventoryClickEvent event) {
        return event.isRightClick() && slot != REMOVE_SLOT ? Sounds.MENU_CLICK : Sounds.EQUIP;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == REMOVE_SLOT) {
            menus.settings().clear(player, PlayerSetting.TRAIL);
            BlockcraftApi.get().notifications().feedback(player, menus.messages().get("cosmetics.removed"));
            return;
        }
        if (slot >= Trail.values().length) {
            return;
        }
        var choice = Trail.values()[slot];
        if (event.isRightClick()) {
            menus.previews().trail(player, choice, viewer -> {
                refresh(viewer);
                open(viewer);
            });
            return;
        }
        menus.settings().set(player, PlayerSetting.TRAIL, choice.key());
        BlockcraftApi.get().notifications().feedback(player, menus.messages().get("trails.selected." + choice.key()));
    }
}
