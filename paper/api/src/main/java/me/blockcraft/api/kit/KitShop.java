package me.blockcraft.api.kit;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.api.shop.Purchases;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The lobby's kit menu from the game's {@code menu.kits.*} messages: every kit with what {@code lore} says it brings,
 * an owned one picked with a click, a locked one bought after a confirmation. Which kits the player owns is read off
 * the main thread.
 */
public final class KitShop<K extends Enum<K> & UnlockableKit> {

    private final Messages messages;
    private final KitSelection<K> selection;
    private final KitCatalog<K> catalog;
    private final Function<K, List<Component>> lore;

    public KitShop(Messages messages, KitSelection<K> selection, KitCatalog<K> catalog, Function<K, List<Component>> lore) {
        this.messages = messages;
        this.selection = selection;
        this.catalog = catalog;
        this.lore = lore;
    }

    public void open(Player player) {
        if (!selection.isOpen()) {
            return;
        }
        var prefix = selection.kit(player).itemPrefix();
        BlockcraftApi.get().playerTasks().latest(player, () -> BlockcraftApi.get().unlocks().owned(player.getUniqueId(), prefix),
                items -> {
                    if (selection.isOpen()) {
                        new KitMenu(catalog.owned(items), selection.kit(player)).open(player, selection::isOpen);
                    }
                });
    }

    private void choose(Player player, K kit, boolean owned) {
        if (!selection.isOpen()) {
            return;
        }
        if (owned) {
            select(player, kit);
            open(player);
            return;
        }
        Purchases.confirm(player, icon(kit, Stream.empty()), kit.item(), catalog.price(kit),
                buyer -> selection.isOpen(), buyer -> select(buyer, kit), this::open);
    }

    private void select(Player player, K kit) {
        if (!selection.select(player, kit)) {
            return;
        }
        Sounds.EQUIP.play(player);
        BlockcraftApi.get().notifications().feedback(player, messages.get("kit.selected",
                Placeholder.component("kit", name(kit))));
    }

    private Component name(K kit) {
        return messages.get("kit." + kit.key() + ".name");
    }

    private ItemStack icon(K kit, Stream<Component> status) {
        var lines = new ArrayList<>(lore.apply(kit));
        status.forEach(lines::add);
        return Items.of(kit.icon())
                .name(messages.get("menu.kits.entry", Placeholder.component("kit", name(kit))))
                .lore(lines)
                .hideAttributes()
                .build();
    }

    private final class KitMenu extends ListMenu<K> {

        private final Set<K> owned;
        private final K selected;

        KitMenu(Set<K> owned, K selected) {
            super(rowsFor(catalog.kits().size()), messages.get("menu.kits.title"));
            this.owned = owned;
            this.selected = selected;
            closeButton();
            show(catalog.kits());
        }

        @Override
        protected ItemStack icon(K kit) {
            var status = kit == selected
                    ? List.of(Component.empty(), messages.get("menu.kits.selected"))
                    : owned.contains(kit)
                    ? List.of(Component.empty(), messages.get("menu.kits.owned"), messages.get("menu.kits.select"))
                    : List.of(Component.empty(), messages.get("menu.kits.locked", Formatter.number("price", catalog.price(kit))),
                            messages.get("menu.kits.buy"));
            var item = KitShop.this.icon(kit, status.stream());
            return kit == selected ? Items.of(item).glow().build() : item;
        }

        @Override
        protected boolean selectable(K kit) {
            return kit != selected;
        }

        @Override
        protected void onSelect(Player player, K kit, InventoryClickEvent event) {
            choose(player, kit, owned.contains(kit));
        }
    }
}
