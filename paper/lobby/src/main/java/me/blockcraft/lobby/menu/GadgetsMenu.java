package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.gadget.Gadget;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class GadgetsMenu extends Menu {

    private static final int BACK_SLOT = 7;
    private static final int REMOVE_SLOT = 8;

    private final Menus menus;

    GadgetsMenu(Menus menus, Player viewer) {
        super(1, menus.messages().get("gadgets.title"));
        this.menus = menus;
        refresh(viewer);
        backButton(BACK_SLOT, menus::openExtras);
    }

    private void refresh(Player player) {
        var selected = menus.settings().get(player, PlayerSetting.GADGET);
        for (var choice : Gadget.values()) {
            boolean equipped = selected.filter(choice.key()::equals).isPresent();
            var icon = Items.of(choice.material())
                    .name(menus.messages().get("gadgets.name." + choice.key()))
                    .lore(menus.messages().get("gadgets.lore." + choice.key()),
                            menus.messages().get(equipped ? "cosmetics.equipped" : "cosmetics.owned"));
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
        if (setting == PlayerSetting.GADGET) {
            refresh(player);
        }
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        if (slot == REMOVE_SLOT) {
            return menus.settings().get(player, PlayerSetting.GADGET).isPresent();
        }
        if (slot >= 0 && slot < Gadget.values().length) {
            return menus.settings().get(player, PlayerSetting.GADGET).filter(Gadget.values()[slot].key()::equals).isEmpty();
        }
        return true;
    }

    @Override
    protected Sounds clickSound(int slot, InventoryClickEvent event) {
        return Sounds.EQUIP;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == REMOVE_SLOT) {
            menus.settings().clear(player, PlayerSetting.GADGET);
            BlockcraftApi.get().notifications().feedback(player, menus.messages().get("cosmetics.removed"));
            return;
        }
        if (slot >= Gadget.values().length) {
            return;
        }
        var choice = Gadget.values()[slot];
        menus.settings().set(player, PlayerSetting.GADGET, choice.key());
        BlockcraftApi.get().notifications().feedback(player, menus.messages().get("cosmetics.selected"));
    }
}
