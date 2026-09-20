package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.cosmetic.ChatTag;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

final class ChatTagsMenu extends Menu {

    private static final int BACK_SLOT = 9;
    private static final int REMOVE_SLOT = 17;

    private final Menus menus;

    ChatTagsMenu(Menus menus, Player viewer) {
        super(2, menus.messages().get("tags.title"));
        this.menus = menus;
        refresh(viewer);
        backButton(BACK_SLOT, menus::openExtras);
    }

    private void refresh(Player player) {
        var selected = menus.settings().get(player, PlayerSetting.CHAT_TAG);
        for (ChatTag tag : ChatTag.values()) {
            var icon = Items.of(Material.NAME_TAG)
                    .name(menus.apiMessages().get(tag.messageKey()))
                    .lore(menus.messages().get(selected.filter(tag.key()::equals).isPresent()
                            ? "cosmetics.equipped" : "cosmetics.owned"));
            if (selected.filter(tag.key()::equals).isPresent()) {
                icon.glow();
            }
            set(tag.ordinal(), icon.build());
        }
        set(REMOVE_SLOT, Items.of(Material.BARRIER).name(menus.messages().get("cosmetics.remove"))
                .lore(menus.messages().get(selected.isPresent() ? "cosmetics.remove-lore" : "cosmetics.empty-remove")).build());
    }

    @Override
    protected void onSettingChanged(Player player, PlayerSetting setting) {
        if (setting == PlayerSetting.CHAT_TAG) {
            refresh(player);
        }
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        if (slot == REMOVE_SLOT) {
            return menus.settings().get(player, PlayerSetting.CHAT_TAG).isPresent();
        }
        if (slot >= 0 && slot < ChatTag.values().length) {
            return menus.settings().get(player, PlayerSetting.CHAT_TAG).filter(ChatTag.values()[slot].key()::equals).isEmpty();
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
            menus.settings().clear(player, PlayerSetting.CHAT_TAG);
            BlockcraftApi.get().notifications().feedback(player, menus.messages().get("tags.removed"));
            return;
        }
        if (slot >= ChatTag.values().length) {
            return;
        }
        ChatTag tag = ChatTag.values()[slot];
        menus.settings().set(player, PlayerSetting.CHAT_TAG, tag.key());
        BlockcraftApi.get().notifications().feedback(player, menus.messages().get("tags.chosen", Placeholder.component("tag", menus.apiMessages().get(tag.messageKey()))));
    }
}
