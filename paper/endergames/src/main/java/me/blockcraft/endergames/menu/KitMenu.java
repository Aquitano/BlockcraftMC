package me.blockcraft.endergames.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.endergames.config.EnderGamesConfig;
import me.blockcraft.endergames.kit.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Every kit with what it brings and does; a click picks an owned kit for the round or offers to buy a locked one. */
final class KitMenu extends ListMenu<Kit> {

    interface Choice {
        void choose(Player player, Kit kit, boolean owned);
    }

    private final Messages messages;
    private final EnderGamesConfig config;
    private final Set<Kit> owned;
    private final Kit selected;
    private final Choice choice;

    KitMenu(Messages messages, EnderGamesConfig config, Set<Kit> owned, Kit selected, Choice choice) {
        super(rowsFor(Kit.values().length), messages.get("menu.kits.title"));
        this.messages = messages;
        this.config = config;
        this.owned = owned;
        this.selected = selected;
        this.choice = choice;
        closeButton();
        show(Arrays.asList(Kit.values()));
    }

    /** The kit as an item: its name, what it starts with and what it can, followed by {@code status}. */
    static ItemStack icon(Messages messages, Kit kit, Stream<Component> status) {
        var lore = new ArrayList<Component>();
        lore.addAll(messages.getLines("kit." + kit.key() + ".contents"));
        lore.addAll(messages.getLines("kit." + kit.key() + ".ability"));
        status.forEach(lore::add);
        return Items.of(kit.icon())
                .name(messages.get("menu.kits.entry", Placeholder.component("kit", messages.get("kit." + kit.key() + ".name"))))
                .lore(lore)
                .hideAttributes()
                .build();
    }

    @Override
    protected ItemStack icon(Kit kit) {
        var status = kit == selected
                ? List.of(Component.empty(), messages.get("menu.kits.selected"))
                : owned.contains(kit)
                ? List.of(Component.empty(), messages.get("menu.kits.owned"), messages.get("menu.kits.select"))
                : List.of(Component.empty(), messages.get("menu.kits.locked", Formatter.number("price", config.price(kit))),
                        messages.get("menu.kits.buy"));
        var item = icon(messages, kit, status.stream());
        return kit == selected ? Items.of(item).glow().build() : item;
    }

    @Override
    protected boolean selectable(Kit kit) {
        return kit != selected;
    }

    @Override
    protected void onSelect(Player player, Kit kit, InventoryClickEvent event) {
        choice.choose(player, kit, owned.contains(kit));
    }
}
