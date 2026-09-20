package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.cosmetic.Head;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

final class HeadsMenu extends Menu {

    private static final int BACK_SLOT = 26;
    private static final int REMOVE_SLOT = 25;

    private final Menus menus;
    private final Set<String> owned;

    HeadsMenu(Menus menus, Player viewer, Set<String> owned) {
        super(3, menus.messages().get("heads.title"));
        this.menus = menus;
        this.owned = new HashSet<>(owned);
        refresh(viewer);
        backButton(BACK_SLOT, menus::openShop);
    }

    private void refresh(Player player) {
        var selected = menus.settings().get(player, PlayerSetting.HEAD);
        for (var choice : Head.values()) {
            boolean equipped = selected.filter(choice.key()::equals).isPresent();
            var icon = Items.head(choice.owner()).name(menus.messages().get("heads.name." + choice.key()))
                    .lore(owned.contains(choice.item())
                            ? menus.messages().get(equipped ? "cosmetics.equipped" : "cosmetics.owned")
                            : menus.messages().get("cosmetics.locked", Formatter.number("coins", menus.config().headPrice())),
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
        if (setting == PlayerSetting.HEAD || setting == PlayerSetting.EFFECTS) {
            refresh(player);
        }
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        if (slot == REMOVE_SLOT) {
            return menus.settings().get(player, PlayerSetting.HEAD).isPresent();
        }
        if (slot >= 0 && slot < Head.values().length) {
            if (event.isRightClick()) {
                return menus.settings().enabled(player, PlayerSetting.EFFECTS);
            }
            return menus.settings().get(player, PlayerSetting.HEAD).filter(Head.values()[slot].key()::equals).isEmpty();
        }
        return true;
    }

    @Override
    protected Sounds clickSound(int slot, InventoryClickEvent event) {
        return slot == REMOVE_SLOT || !event.isRightClick() && slot < Head.values().length && owned.contains(Head.values()[slot].item())
                ? Sounds.EQUIP : Sounds.MENU_CLICK;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == REMOVE_SLOT) {
            menus.settings().clear(player, PlayerSetting.HEAD);
            BlockcraftApi.get().notifications().feedback(player, menus.messages().get("cosmetics.removed"));
            return;
        }
        if (slot >= Head.values().length) {
            return;
        }
        var choice = Head.values()[slot];
        if (event.isRightClick()) {
            menus.previews().equipment(player, Items.head(choice.owner()).build(), EquipmentSlot.HEAD, viewer -> {
                refresh(viewer);
                open(viewer);
            });
            return;
        }
        if (owned.contains(choice.item())) {
            equip(player, choice);
        } else {
            menus.purchase(player, clicked, choice.item(), menus.config().headPrice(), buyer -> owned.add(choice.item()), buyer -> {
                if (owned.contains(choice.item())) {
                    equip(buyer, choice);
                }
                refresh(buyer);
                open(buyer);
            });
        }
    }

    private void equip(Player player, Head choice) {
        menus.settings().set(player, PlayerSetting.HEAD, choice.key());
        BlockcraftApi.get().notifications().feedback(player, menus.messages().get("heads.equipped"));
    }
}
