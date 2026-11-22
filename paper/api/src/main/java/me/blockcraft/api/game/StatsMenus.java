package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.common.elo.EloRating;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A game's stats screens, written with its {@code menu.stats} and {@code menu.global-stats} messages. They open on
 * the viewer's own numbers, each line of the player lore written out in a half row with its number on the right;
 * from there a head per player the viewer can see shows the numbers everyone sees for them, and a global screen's
 * entries print the top ten of a stat. A ranked game shows each player's rating of the season too.
 */
public final class StatsMenus {

    private static final int ROWS = 2;
    private static final int COLUMNS = 9;
    private static final int HALF_COLUMNS = 4;
    private static final int MOST_LINES = 10;
    private static final int LABEL_INSET = 3;
    private static final int HALF_WIDTH = HALF_COLUMNS * 18 - 2;
    private static final Pattern LEAD = Pattern.compile("^[^\\p{L}\\p{N}]+");
    private static final TextColor NAME = TextColor.color(0x9BAEBD);
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
    private final String playerLore;

    private StatsMenus(Messages messages, StatsRepository stats, TopList topList, String globalHead, boolean ranked,
                       String playerLore) {
        this.messages = messages;
        this.stats = stats;
        this.topList = topList;
        this.globalHead = globalHead;
        this.ranked = ranked;
        this.playerLore = playerLore;
    }

    /** {@code globalHead} names the player whose skin the global entry wears. */
    public static StatsMenus of(Messages messages, StatsRepository stats, TopList topList, String globalHead) {
        return of(messages, stats, topList, globalHead, "menu.stats.player-lore");
    }

    public static StatsMenus of(Messages messages, StatsRepository stats, TopList topList, String globalHead,
                                String playerLore) {
        return new StatsMenus(messages, stats, topList, globalHead, false, playerLore);
    }

    /** Lore lines may use the rating's tags as well, {@code <rating>} and {@code <group>}. */
    public static StatsMenus ranked(Messages messages, StatsRepository stats, TopList topList, String globalHead) {
        return new StatsMenus(messages, stats, topList, globalHead, true, "menu.stats.player-lore");
    }

    public void open(Player viewer) {
        List<Player> shown = Stream.concat(Stream.of(viewer), Bukkit.getOnlinePlayers().stream()
                        .filter(player -> !player.equals(viewer) && viewer.canSee(player)))
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
                    new OwnMenu(entries).open(viewer);
                });
    }

    /** Lines fill the rows under the toolbar two at a time, the left half before the right. */
    static int lineSlot(int index) {
        return (1 + index / 2) * COLUMNS + index % 2 * (COLUMNS - HALF_COLUMNS);
    }

    /** A lore line such as "» Siege: 12" as its name and its value in the label letters, the name cut to leave the value room. */
    static List<String> nameAndValue(String line) {
        var text = LEAD.matcher(line).replaceFirst("").strip();
        int colon = text.lastIndexOf(':');
        var name = UiFont.capitals(colon < 0 ? text : text.substring(0, colon).strip(), UiFont.LABEL_LETTERS);
        var value = colon < 0 ? "" : UiFont.capitals(text.substring(colon + 1).strip(), UiFont.LABEL_LETTERS);
        int room = (HALF_WIDTH - 2 * LABEL_INSET) / UiFont.LABEL_ADVANCE - value.length() - (value.isEmpty() ? 0 : 1);
        if (name.length() > room) {
            name = name.substring(0, Math.max(0, room - 1)) + "…";
        }
        return List.of(name, value);
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

    /** The first entry is the viewer's own; their head in the middle of the toolbar leads to the others when there are any. */
    private final class OwnMenu extends Menu {

        private final Component title;
        private final List<Component> lines;

        OwnMenu(List<Entry> entries) {
            this(entries, messages.getLines(playerLore, entries.getFirst().numbers()).stream().limit(MOST_LINES).toList());
        }

        private OwnMenu(List<Entry> entries, List<Component> lines) {
            super(1 + (lines.size() + 1) / 2, messages.get("menu.stats.title"));
            this.title = messages.get("menu.stats.title");
            this.lines = lines;
            var own = entries.getFirst();
            closeButton(ToolbarButton.LEFT);
            var head = Items.head(own.player().getUniqueId(), own.player().getName())
                    .name(messages.get("menu.stats.player", own.numbers()));
            if (entries.size() > 1) {
                head.lore(BlockcraftApi.get().messages().get("stats-menu.players-lore"));
            }
            toolbar(ToolbarButton.MIDDLE, head.build(), player -> {
                if (entries.size() > 1) {
                    new PlayersMenu(entries).open(player);
                }
            });
            toolbar(ToolbarButton.RIGHT, MenuIcon.GLOBAL_STATS.mark(Items.head(globalHead))
                    .name(messages.get("menu.stats.global")).build(), player -> new GlobalMenu().open(player));
            for (int index = 0; index < lines.size(); index++) {
                area(lineSlot(index), HALF_COLUMNS, 1);
                set(lineSlot(index), MenuIcon.PAGE.item().name(lines.get(index)).build());
            }
        }

        @Override
        protected Optional<Component> screen() {
            var screen = UiFont.menuTitle(1 + (lines.size() + 1) / 2, true, true, Component.empty());
            for (int index = 0; index < lines.size(); index++) {
                var parts = nameAndValue(PlainTextComponentSerializer.plainText().serialize(lines.get(index)));
                int slot = lineSlot(index);
                screen = screen.append(UiFont.rowLabel(slot, LABEL_INSET, parts.getFirst(), NAME))
                        .append(UiFont.rowLabel(slot, HALF_WIDTH - LABEL_INSET - parts.getLast().length() * UiFont.LABEL_ADVANCE,
                                parts.getLast(), NamedTextColor.WHITE));
            }
            return Optional.of(screen.append(title));
        }

        @Override
        protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        }
    }

    private final class PlayersMenu extends ListMenu<Entry> {

        PlayersMenu(List<Entry> entries) {
            super(rowsFor(entries.size()), messages.get("menu.stats.title"));
            backButton(StatsMenus.this::open);
            show(entries);
        }

        @Override
        protected ItemStack icon(Entry entry) {
            return Items.head(entry.player().getUniqueId(), entry.player().getName())
                    .name(messages.get("menu.stats.player", entry.numbers()))
                    .lore(messages.getLines(playerLore, entry.numbers()))
                    .build();
        }

        @Override
        protected boolean selectable(Entry entry) {
            return false;
        }

        @Override
        protected void onSelect(Player player, Entry entry, InventoryClickEvent event) {
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
