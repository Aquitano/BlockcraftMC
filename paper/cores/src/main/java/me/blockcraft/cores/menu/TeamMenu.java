package me.blockcraft.cores.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.cores.game.Side;
import me.blockcraft.cores.game.Teams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiConsumer;

/** Red on the left, blue on the right, each with who is in it; a click joins. */
public final class TeamMenu extends Menu {

    private static final int ROWS = 3;
    private static final Map<Integer, Side> SLOTS = Map.of(11, Side.RED, 15, Side.BLUE);

    private final BiConsumer<Player, Side> choose;

    public TeamMenu(Messages messages, Teams teams, int capacity, BiConsumer<Player, Side> choose) {
        super(ROWS, messages.get("team.title"));
        this.choose = choose;
        SLOTS.forEach((slot, side) -> {
            var lore = new ArrayList<Component>();
            lore.add(messages.get("team.size", Formatter.number("players", teams.size(side)), Formatter.number("max", capacity)));
            teams.members(side).stream().map(Bukkit::getOfflinePlayer).map(OfflinePlayer::getName)
                    .forEach(name -> lore.add(messages.get("team.member", Placeholder.unparsed("player", String.valueOf(name)))));
            lore.add(Component.empty());
            lore.add(messages.get("team.join"));
            set(slot, Items.of(side.wool()).name(messages.get("team.name", Placeholder.component("team", side.displayName(messages))))
                    .lore(lore).build());
        });
        closeButton();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var side = SLOTS.get(slot);
        if (side != null) {
            choose.accept(player, side);
            Menu.close(player);
        }
    }
}
