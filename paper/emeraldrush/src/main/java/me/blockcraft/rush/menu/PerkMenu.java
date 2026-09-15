package me.blockcraft.rush.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.kit.Perk;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;

final class PerkMenu extends Menu {

    private static final Map<Integer, Perk> PERKS = Map.of(11, Perk.ANGEL, 13, Perk.LUCKY_POTION, 15, Perk.STRENGTH);

    private final RushMenus menus;

    PerkMenu(RushMenus menus, Messages messages, Set<Perk> owned) {
        super(3, messages.get("perk.title"));
        this.menus = menus;
        PERKS.forEach((slot, perk) -> {
            var icon = Items.of(perk.icon()).name(messages.get("perk.item", RushMenus.perkName(messages, perk)))
                    .lore(messages.get("perk.description." + perk.key()),
                            messages.get(owned.contains(perk) ? "perk.unlocked" : "perk.price-lore", Formatter.number("price", perk.price())),
                            messages.get("perk.inspect"));
            if (owned.contains(perk)) {
                icon.glow();
            }
            set(slot, icon.hideAttributes().build());
        });
        set(22, Items.of(Material.EMERALD).name(messages.get("perk.explain")).lore(messages.getLines("perk.explain-lore")).build());
        closeButton(26);
    }

    @Override
    protected boolean fillEmptySlots() {
        return false;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var perk = PERKS.get(slot);
        if (perk != null) {
            menus.openPerk(player, perk);
        }
    }
}
