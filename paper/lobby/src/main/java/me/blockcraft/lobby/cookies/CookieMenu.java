package me.blockcraft.lobby.cookies;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

final class CookieMenu extends Menu {

    private final CookieClicker cookies;
    private final Player viewer;

    CookieMenu(CookieClicker cookies, Player viewer, Consumer<Player> back) {
        super(3, cookies.messages().get("cookies.title"));
        this.cookies = cookies;
        this.viewer = viewer;
        backButton(back);
        refresh();
    }

    void refresh() {
        var messages = cookies.messages();
        var progress = cookies.progress(viewer);
        var state = progress.snapshot();
        set(11, Items.of(Material.COOKIE).name(messages.get("cookies.balance", Formatter.number("cookies", state.cookies())))
                .lore(messages.getLines("cookies.click-lore", Formatter.number("amount", progress.perClick()),
                        Formatter.number("limit", cookies.limit()))).build());
        progress.next().ifPresentOrElse(upgrade -> set(15, Items.of(Material.FURNACE)
                .name(messages.get("cookies.upgrade", Formatter.number("level", state.level() + 1)))
                .lore(messages.getLines("cookies.upgrade-lore", Formatter.number("cost", upgrade.cost()),
                        Formatter.number("amount", upgrade.perClick()))).build()),
                () -> info(15, Items.of(Material.BLAST_FURNACE).name(messages.get("cookies.complete")).build()));
    }

    @Override
    protected boolean automaticClickSound(int slot, InventoryClickEvent event) {
        return false;
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if ((slot == 11 || slot == 15) && cookies.click(player, slot == 15)) {
            refresh();
        }
    }
}
