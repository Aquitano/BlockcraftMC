package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.cosmetic.Shirt;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

final class ShirtsMenu extends Menu {

    private static final int BACK_SLOT = 8;
    private static final int REMOVE_SLOT = 7;

    private final Menus menus;
    private final Set<String> owned;

    ShirtsMenu(Menus menus, Player viewer, Set<String> owned) {
        super(1, menus.messages().get("shirts.title"));
        this.menus = menus;
        this.owned = new HashSet<>(owned);
        refresh(viewer);
        backButton(BACK_SLOT, menus::openShop);
    }

    private void refresh(Player player) {
        var selected = menus.settings().get(player, PlayerSetting.SHIRT);
        for (var choice : Shirt.values()) {
            boolean equipped = selected.filter(choice.key()::equals).isPresent();
            var icon = Items.of(Material.LEATHER_CHESTPLATE).color(choice.color()).name(menus.messages().get("shirts.name." + choice.key()))
                    .lore(owned.contains(choice.item())
                            ? menus.messages().get(equipped ? "cosmetics.equipped" : "cosmetics.owned")
                            : menus.messages().get("cosmetics.locked", Formatter.number("coins", menus.config().shirtPrice())),
                            menus.messages().get(menus.settings().enabled(player, PlayerSetting.EFFECTS)
                                    ? "cosmetics.fitting-lore" : "cosmetics.preview-disabled"));
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
        if (setting == PlayerSetting.SHIRT || setting == PlayerSetting.EFFECTS) {
            refresh(player);
        }
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        if (slot == REMOVE_SLOT) {
            return menus.settings().get(player, PlayerSetting.SHIRT).isPresent();
        }
        if (slot >= 0 && slot < Shirt.values().length) {
            if (event.isRightClick()) {
                return menus.settings().enabled(player, PlayerSetting.EFFECTS);
            }
            return menus.settings().get(player, PlayerSetting.SHIRT).filter(Shirt.values()[slot].key()::equals).isEmpty();
        }
        return true;
    }

    @Override
    protected Sounds clickSound(int slot, InventoryClickEvent event) {
        return slot == REMOVE_SLOT || !event.isRightClick() && slot < Shirt.values().length && owned.contains(Shirt.values()[slot].item())
                ? Sounds.EQUIP : Sounds.MENU_CLICK;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == REMOVE_SLOT) {
            menus.settings().clear(player, PlayerSetting.SHIRT);
            BlockcraftApi.get().notifications().feedback(player, menus.messages().get("cosmetics.removed"));
            return;
        }
        if (slot >= Shirt.values().length) {
            return;
        }
        var choice = Shirt.values()[slot];
        if (event.isRightClick()) {
            menus.previews().equipment(player, Items.of(Material.LEATHER_CHESTPLATE).color(choice.color()).build(), EquipmentSlot.CHEST, viewer -> {
                refresh(viewer);
                open(viewer);
            });
            return;
        }
        if (owned.contains(choice.item())) {
            equip(player, choice);
        } else {
            menus.purchase(player, clicked, choice.item(), menus.config().shirtPrice(), buyer -> owned.add(choice.item()), buyer -> {
                if (owned.contains(choice.item())) {
                    equip(buyer, choice);
                }
                refresh(buyer);
                open(buyer);
            });
        }
    }

    private void equip(Player player, Shirt choice) {
        menus.settings().set(player, PlayerSetting.SHIRT, choice.key());
        BlockcraftApi.get().notifications().feedback(player, menus.messages().get("shirts.equipped"));
    }
}
