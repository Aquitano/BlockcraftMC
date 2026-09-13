package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.lobby.gadget.Gadget;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
        var messages = menus.messages();
        var held = menus.gadgets().of(viewer);
        for (Gadget gadget : Gadget.values()) {
            var icon = Items.of(gadget.material())
                    .name(messages.get("gadgets.name." + gadget.key()))
                    .lore(messages.get("gadgets.lore." + gadget.key()));
            if (held.filter(gadget::equals).isPresent()) {
                icon.glow();
            }
            set(gadget.ordinal(), icon.build());
        }
        set(REMOVE_SLOT, Items.of(Material.BARRIER).name(messages.get("gadgets.remove")).build());
        backButton(BACK_SLOT, menus::openExtras);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        close(player);
        if (slot == REMOVE_SLOT) {
            menus.gadgets().clear(player);
            player.sendMessage(menus.messages().get("gadgets.removed"));
            return;
        }
        if (slot >= Gadget.values().length) {
            return;
        }
        Gadget gadget = Gadget.values()[slot];
        menus.gadgets().select(player, gadget);
        player.sendMessage(menus.messages().get("gadgets.chosen",
                Placeholder.component("gadget", menus.messages().get("gadgets.name." + gadget.key()))));
    }
}
