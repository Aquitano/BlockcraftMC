package me.blockcraft.api.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;

/** The players still in the round, one head each; a click takes the spectator to them. */
final class SpectatorMenu extends ListMenu<Player> {

    private static final int COLUMNS = 9;
    private static final int MAX_ROWS = 6;

    private final Messages messages;
    private final BiConsumer<Player, Player> jump;

    SpectatorMenu(Messages messages, List<Player> players, BiConsumer<Player, Player> jump) {
        super(rows(players.size()), messages.get("spectator.menu.title"));
        this.messages = messages;
        this.jump = jump;
        closeButton();
        show(players);
    }

    /** A row of heads per nine players, below them the row with the close button. */
    private static int rows(int players) {
        return Math.min(MAX_ROWS, 1 + Math.max(1, (players + COLUMNS - 1) / COLUMNS));
    }

    @Override
    protected ItemStack icon(Player target) {
        var name = Placeholder.unparsed("player", target.getName());
        return Items.head(target.getUniqueId(), target.getName())
                .name(messages.get("spectator.menu.player", name))
                .lore(messages.get("spectator.menu.player-lore", name))
                .build();
    }

    @Override
    protected void onSelect(Player player, Player target, InventoryClickEvent event) {
        if (!target.isOnline()) {
            return;
        }
        close(player);
        jump.accept(player, target);
    }
}
