package me.blockcraft.api.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/** The players still in the round, one head each; a click takes the spectator to them. */
final class SpectatorMenu extends Menu {

    private static final int COLUMNS = 9;
    private static final int MAX_ROWS = 6;

    private final Map<Integer, UUID> targets = new HashMap<>();
    private final BiConsumer<Player, Player> jump;

    SpectatorMenu(Messages messages, List<Player> players, BiConsumer<Player, Player> jump) {
        super(rows(players.size()), messages.get("spectator.menu.title"));
        this.jump = jump;
        int rows = rows(players.size());
        int shown = Math.min(players.size(), (rows - 1) * COLUMNS);
        for (int slot = 0; slot < shown; slot++) {
            var target = players.get(slot);
            var name = Placeholder.unparsed("player", target.getName());
            targets.put(slot, target.getUniqueId());
            set(slot, Items.head(target.getUniqueId(), target.getName())
                    .name(messages.get("spectator.menu.player", name))
                    .lore(messages.get("spectator.menu.player-lore", name))
                    .build());
        }
        closeButton(rows * COLUMNS - 1);
    }

    /** A row of heads per nine players, below them the row with the close button. */
    private static int rows(int players) {
        return Math.min(MAX_ROWS, 1 + Math.max(1, (players + COLUMNS - 1) / COLUMNS));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var target = Optional.ofNullable(targets.get(slot)).map(Bukkit::getPlayer);
        if (target.isEmpty()) {
            return;
        }
        close(player);
        jump.accept(player, target.get());
    }
}
