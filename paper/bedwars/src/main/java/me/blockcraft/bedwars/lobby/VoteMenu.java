package me.blockcraft.bedwars.lobby;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.bedwars.map.ArenaMap;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** The maps up for the vote in one row, each with its builders and the votes it has; the last slot closes. */
public final class VoteMenu extends Menu {

    private final Map<Integer, ArenaMap> maps = new HashMap<>();
    private final BiConsumer<Player, ArenaMap> onVote;

    public VoteMenu(Messages messages, MapVote vote, List<ArenaMap> choices, List<Integer> slots,
                    BiConsumer<Player, ArenaMap> onVote) {
        super(1, messages.get("vote.title"));
        this.onVote = onVote;
        for (int index = 0; index < Math.min(choices.size(), slots.size()); index++) {
            var map = choices.get(index);
            var lore = new ArrayList<Component>();
            if (!map.builders().isEmpty()) {
                lore.add(messages.get("vote.builders", Placeholder.unparsed("builders", String.join(", ", map.builders()))));
            }
            lore.add(Component.empty());
            lore.add(messages.get("vote.votes", Formatter.number("votes", vote.votes(map.key()))));
            maps.put(slots.get(index), map);
            set(slots.get(index), Items.of(map.icon())
                    .name(messages.get("vote.map", Placeholder.unparsed("map", map.name())))
                    .lore(lore)
                    .hideAttributes()
                    .build());
        }
        closeButton();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var map = maps.get(slot);
        if (map != null) {
            onVote.accept(player, map);
            close(player);
        }
    }
}
