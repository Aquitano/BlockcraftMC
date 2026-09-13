package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;

final class EffectsMenu<E extends Enum<E>> extends Menu {

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
        this.owned = owned;
        Optional<String> selected = menus.settings().get(viewer, kind.setting());
        var messages = menus.messages();
        for (E effect : kind.values()) {
            String key = kind.key().apply(effect);
            boolean isOwned = owned.contains(kind.item().apply(effect));
            var icon = Items.of(kind.icon().apply(effect))
                    .name(messages.get(kind.messagePrefix() + ".name." + key))
                    .lore(isOwned
                            ? messages.get(selected.filter(key::equals).isPresent() ? "effects.selected-lore" : "effects.owned-lore")
                            : messages.get("effects.price-lore", Formatter.number("coins", price)));
            if (selected.filter(key::equals).isPresent()) {
                icon.glow();
            }
            set(effect.ordinal(), icon.build());
        }
        set(RESET_SLOT, Items.of(Material.BARRIER).name(messages.get("effects.reset")).lore(messages.get("effects.reset-lore")).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        close(player);
        if (slot == RESET_SLOT) {
            menus.settings().clear(player, kind.setting());
            player.sendMessage(menus.messages().get("effects.reset-done"));
            return;
        }
        if (slot >= kind.values().size()) {
            return;
        }
        E effect = kind.values().get(slot);
        String item = kind.item().apply(effect);
        if (owned.contains(item)) {
            select(player, effect);
        } else {
            menus.purchase(player, item, price, () -> select(player, effect));
        }
    }

    private void select(Player player, E effect) {
        String key = kind.key().apply(effect);
        menus.settings().set(player, kind.setting(), key);
        player.sendMessage(menus.messages().get("effects.chosen",
                Placeholder.component("effect", menus.messages().get(kind.messagePrefix() + ".name." + key))));
    }
}
