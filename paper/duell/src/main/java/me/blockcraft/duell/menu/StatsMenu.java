package me.blockcraft.duell.menu;

import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.TopList;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.GameItems;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
                     Player viewer, TagResolver viewerStats, Optional<Player> opponent, TagResolver opponentStats) {
        super(1, messages.get("menu.stats.title"));
        this.topList = topList;
        set(SLOT_SELF, head(messages, viewer, viewerStats));
        opponent.ifPresent(other -> set(SLOT_OPPONENT, head(messages, other, opponentStats)));
        globalSlot = opponent.isPresent() ? SLOT_GLOBAL_WITH_OPPONENT : SLOT_GLOBAL_ALONE;
        set(globalSlot, items.head(globalHead, "menu.stats.global", "menu.stats.global-lore"));
    }

    /** {@code stats} carries the game stats and the rating of the season, as the lore names them. */
    private static ItemStack head(Messages messages, Player player, TagResolver stats) {
        return Items.head(player.getUniqueId(), player.getName())
                .name(messages.get("menu.stats.player", Placeholder.unparsed("player", player.getName())))
                .lore(messages.getLines("menu.stats.player-lore", stats))
                .build();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        close(player);
        if (slot == globalSlot) {
            topList.send(player, Stat.WINS);
        }
    }
}
