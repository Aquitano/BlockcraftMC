package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.settings.PlayerSetting;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

final class EffectsMenu<E extends Enum<E>> extends Menu {

    private static final int BACK_SLOT = 7;
    private static final int RESET_SLOT = 8;

    private final Menus menus;
    private final EffectKind<E> kind;
    private final int price;
    private final Set<String> owned;

    EffectsMenu(Menus menus, EffectKind<E> kind, int price, Player viewer, Set<String> owned) {
        super(1, menus.messages().get(kind.messagePrefix() + ".title"));
        this.menus = menus;
        this.kind = kind;
        this.price = price;
        this.owned = new HashSet<>(owned);
        refresh(viewer);
        backButton(BACK_SLOT, menus::openExtras);
    }

    private void refresh(Player player) {
        Optional<String> selected = menus.settings().get(player, kind.setting());
        var messages = menus.messages();
        for (E effect : kind.values()) {
            String key = kind.key().apply(effect);
            boolean isOwned = owned.contains(kind.item().apply(effect));
            var icon = Items.of(kind.icon().apply(effect))
                    .name(messages.get(kind.messagePrefix() + ".name." + key))
                    .lore(isOwned
                            ? messages.get(selected.filter(key::equals).isPresent() ? "cosmetics.equipped" : "cosmetics.owned")
                            : messages.get("cosmetics.locked", Formatter.number("coins", price)),
                            messages.get(menus.settings().enabled(player, PlayerSetting.EFFECTS)
                                    ? "effects.preview-lore" : "cosmetics.preview-disabled"));
            if (selected.filter(key::equals).isPresent()) {
                icon.glow();
            }
            set(effect.ordinal(), icon.build());
        }
        set(RESET_SLOT, Items.of(Material.BARRIER).name(menus.messages().get("cosmetics.remove"))
                .lore(menus.messages().get(selected.isPresent() ? "cosmetics.remove-lore" : "cosmetics.empty-remove")).build());
    }

    @Override
    protected void onSettingChanged(Player player, PlayerSetting setting) {
        if (setting == kind.setting() || setting == PlayerSetting.EFFECTS) {
            refresh(player);
        }
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        if (slot == RESET_SLOT) {
            return menus.settings().get(player, kind.setting()).isPresent();
        }
        if (slot >= 0 && slot < kind.values().size()) {
            if (event.isRightClick()) {
                return menus.settings().enabled(player, PlayerSetting.EFFECTS);
            }
            return menus.settings().get(player, kind.setting()).filter(kind.key().apply(kind.values().get(slot))::equals).isEmpty();
        }
        return true;
    }

    @Override
    protected Sounds clickSound(int slot, InventoryClickEvent event) {
        return slot == RESET_SLOT || !event.isRightClick() && slot < kind.values().size()
                && owned.contains(kind.item().apply(kind.values().get(slot)))
                ? Sounds.EQUIP : Sounds.MENU_CLICK;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == RESET_SLOT) {
            menus.settings().clear(player, kind.setting());
            BlockcraftApi.get().notifications().feedback(player, menus.messages().get("effects.reset-done"));
            return;
        }
        if (slot >= kind.values().size()) {
            return;
        }
        E effect = kind.values().get(slot);
        if (event.isRightClick()) {
            preview(player, effect);
            return;
        }
        String item = kind.item().apply(effect);
        if (owned.contains(item)) {
            select(player, effect);
        } else {
            menus.purchase(player, clicked, item, price, buyer -> owned.add(item), buyer -> {
                if (owned.contains(item)) {
                    select(buyer, effect);
                }
                refresh(buyer);
                open(buyer);
            });
        }
    }

    private void preview(Player player, E effect) {
        menus.previews().effect(player, viewer -> kind.preview().accept(viewer, effect), viewer -> {
            refresh(viewer);
            open(viewer);
        });
    }

    private void select(Player player, E effect) {
        String key = kind.key().apply(effect);
        menus.settings().set(player, kind.setting(), key);
        BlockcraftApi.get().notifications().feedback(player, menus.messages().get("effects.chosen",
                Placeholder.component("effect", menus.messages().get(kind.messagePrefix() + ".name." + key))));
    }
}
