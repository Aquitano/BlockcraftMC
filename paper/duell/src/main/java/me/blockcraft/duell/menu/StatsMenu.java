package me.blockcraft.duell.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.GameItems;
import me.blockcraft.duell.game.TopList;
import me.blockcraft.duell.storage.Stats;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class StatsMenu extends Menu {

    private static final int SLOT_SELF = 0;
    private static final int SLOT_GLOBAL_WITH_OPPONENT = 4;
    private static final int SLOT_OPPONENT = 8;
    private static final int SLOT_GLOBAL_ALONE = 8;

    private final TopList topList;
    private final int globalSlot;

    public StatsMenu(Messages messages, GameItems items, TopList topList, String globalHead,
                     Player viewer, Stats viewerStats, Optional<Player> opponent, Stats opponentStats) {
        super(1, messages.get("menu.stats.title"));
        this.topList = topList;
        set(SLOT_SELF, head(messages, viewer, viewerStats));
        opponent.ifPresent(other -> set(SLOT_OPPONENT, head(messages, other, opponentStats)));
        globalSlot = opponent.isPresent() ? SLOT_GLOBAL_WITH_OPPONENT : SLOT_GLOBAL_ALONE;
        set(globalSlot, items.head(globalHead, "menu.stats.global", "menu.stats.global-lore"));
    }

    private static ItemStack head(Messages messages, Player player, Stats stats) {
        double kdr = Math.round((double) stats.kills() / stats.deaths() * 100.0) / 100.0;
        return Items.head(player.getUniqueId(), player.getName())
                .name(messages.get("menu.stats.player", Placeholder.unparsed("player", player.getName())))
                .lore(messages.getLines("menu.stats.player-lore",
                        Formatter.number("kills", stats.kills()),
                        Formatter.number("wins", stats.wins()),
                        Formatter.number("deaths", stats.deaths()),
                        Placeholder.unparsed("kdr", stats.deaths() > 0 ? String.valueOf(kdr) : "-")))
                .build();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        close(player);
        if (slot == globalSlot) {
            topList.sendWins(player);
        }
    }
}
