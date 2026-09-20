package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.cosmetic.PetType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class PetsMenu extends Menu {

    private static final int BACK_SLOT = 7;
    private static final int REMOVE_SLOT = 8;

    private final Menus menus;

    PetsMenu(Menus menus, Player viewer) {
        super(1, menus.messages().get("pets.title"));
        this.menus = menus;
        refresh(viewer);
        backButton(BACK_SLOT, menus::openPremium);
    }

    private void refresh(Player player) {
        var selected = menus.settings().get(player, PlayerSetting.PET);
        for (var choice : PetType.values()) {
            boolean equipped = selected.filter(choice.key()::equals).isPresent()
                    && menus.pets().of(player).filter(choice::equals).isPresent();
            var icon = Items.of(choice.icon())
                    .name(menus.messages().get("pets.name." + choice.key()))
                    .lore(menus.messages().get("pets.lore." + choice.key()),
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
        if (setting == PlayerSetting.PET || setting == PlayerSetting.EFFECTS) {
            refresh(player);
        }
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        if (slot == REMOVE_SLOT) {
            return menus.settings().get(player, PlayerSetting.PET).isPresent();
        }
        if (slot >= 0 && slot < PetType.values().length) {
            if (!player.hasPermission(LobbyPermissions.PREMIUM)) {
                return false;
            }
            if (event.isRightClick()) {
                return menus.settings().enabled(player, PlayerSetting.EFFECTS);
            }
            return menus.settings().get(player, PlayerSetting.PET).filter(PetType.values()[slot].key()::equals).isEmpty()
                    || menus.pets().of(player).filter(PetType.values()[slot]::equals).isEmpty();
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
            menus.settings().clear(player, PlayerSetting.PET);
            BlockcraftApi.get().notifications().feedback(player, menus.messages().get("cosmetics.removed"));
            return;
        }
        if (slot >= PetType.values().length) {
            return;
        }
        var choice = PetType.values()[slot];
        if (event.isRightClick()) {
            menus.previews().pet(player, choice, viewer -> {
                refresh(viewer);
                open(viewer);
            });
            return;
        }
        if (menus.settings().get(player, PlayerSetting.PET).filter(choice.key()::equals).isPresent()
                && menus.pets().of(player).filter(choice::equals).isEmpty()) {
            menus.pets().release(player.getUniqueId());
            menus.pets().spawn(player, choice);
            refresh(player);
        } else {
            menus.settings().set(player, PlayerSetting.PET, choice.key());
        }
        BlockcraftApi.get().notifications().feedback(player, menus.messages().get("pets.chosen." + choice.key()));
    }
}
