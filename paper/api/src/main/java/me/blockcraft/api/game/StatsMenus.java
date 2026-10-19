package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.elo.EloRating;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A game's stats screens, written with its {@code menu.stats} and {@code menu.global-stats} messages: a head per
 * player the viewer can see, with the numbers everyone sees for them, and a global screen whose entries print the
 * top ten of a stat. A ranked game shows each player's rating of the season too.
 */
public final class StatsMenus {

    private static final int ROWS = 2;
    private static final int SLOTS = ROWS * 9;
    private static final int GLOBAL = 13;
    private static final Map<Integer, Stat> CATEGORIES = Map.of(
            0, Stat.WINS,
            2, Stat.DEATHS,
            4, Stat.KILLS,
            6, Stat.LOSSES,
            8, Stat.POINTS);

    private record Loaded(Stats stats, Optional<EloRating> rating) {
    }

    private record Entry(Player player, TagResolver numbers) {
    }

    private final Messages messages;
    private final StatsRepository stats;
    private final TopList topList;
    private final String globalHead;
    private final boolean ranked;

    private StatsMenus(Messages messages, StatsRepository stats, TopList topList, String globalHead, boolean ranked) {
        this.messages = messages;
        this.stats = stats;
        this.topList = topList;
        this.globalHead = globalHead;
        this.ranked = ranked;
    }

    /** {@code globalHead} names the player whose skin the global entry wears. */
    public static StatsMenus of(Messages messages, StatsRepository stats, TopList topList, String globalHead) {
        return new StatsMenus(messages, stats, topList, globalHead, false);
    }

    /** Lore lines may use the rating's tags as well, {@code <rating>} and {@code <group>}. */
    public static StatsMenus ranked(Messages messages, StatsRepository stats, TopList topList, String globalHead) {
        return new StatsMenus(messages, stats, topList, globalHead, true);
    }

    public void open(Player viewer) {
        List<Player> shown = Bukkit.getOnlinePlayers().stream()
                .filter(viewer::canSee)
                .limit(SLOTS - 1)
                .map(Player.class::cast)
                .toList();
        var elo = BlockcraftApi.get().elo();
        BlockcraftApi.get().playerTasks().latest(viewer,
                () -> shown.stream().map(player -> new Loaded(stats.get(player.getUniqueId()),
                        ranked ? Optional.of(elo.rating(player.getUniqueId())) : Optional.empty())).toList(),
                loaded -> {
                    var entries = new ArrayList<Entry>();
                    for (int index = 0; index < shown.size(); index++) {
                        entries.add(new Entry(shown.get(index), numbers(viewer, shown.get(index), loaded.get(index))));
                    }
                    new PlayersMenu(entries).open(viewer);
                });
    }

    /** Another player's numbers as everyone sees them, which for a nicked player are the nick's. */
    private static TagResolver numbers(Player viewer, Player player, Loaded loaded) {
        var api = BlockcraftApi.get();
        boolean own = player.equals(viewer);
        var numbers = TagResolver.builder()
                .resolver(Placeholder.unparsed("player", player.getName()))
                .resolver((own ? loaded.stats() : api.nickStats().stats(player, loaded.stats())).placeholders());
        loaded.rating().ifPresent(rating ->
                numbers.resolver(api.elo().placeholders(own ? rating : api.nickStats().rating(player, rating))));
        return numbers.build();
    }

    private final class PlayersMenu extends Menu {

        PlayersMenu(List<Entry> entries) {
            super(ROWS, messages.get("menu.stats.title"));
            int slot = 0;
            for (var entry : entries) {
                if (slot == GLOBAL) {
                    slot++;
                }
                set(slot++, Items.head(entry.player().getUniqueId(), entry.player().getName())
                        .name(messages.get("menu.stats.player", entry.numbers()))
                        .lore(messages.getLines("menu.stats.player-lore", entry.numbers()))
                        .build());
            }
            set(GLOBAL, Items.head(globalHead).name(messages.get("menu.stats.global")).build());
        }

        @Override
        protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
            if (slot == GLOBAL) {
                new GlobalMenu().open(player);
            }
        }
    }

    private final class GlobalMenu extends Menu {

        GlobalMenu() {
            super(ROWS, messages.get("menu.global-stats.title"));
            CATEGORIES.forEach((slot, stat) -> set(slot, Items.of(Material.PAPER)
                    .name(messages.get("menu.global-stats." + stat.name().toLowerCase(Locale.ROOT)))
                    .build()));
            backButton(StatsMenus.this::open);
        }

        @Override
        protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
            close(player);
            var stat = CATEGORIES.get(slot);
            if (stat != null) {
                topList.send(player, stat);
            }
        }
    }
}
