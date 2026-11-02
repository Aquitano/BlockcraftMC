package me.blockcraft.api.menu;

import me.blockcraft.api.achievement.Achievement;
import me.blockcraft.api.achievement.AchievementSection;
import me.blockcraft.api.achievement.Achievements;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The catalog, a game's icon before each row of entries: earned entries lit, the rest grey with how far the
 * player has come. The rows of the server's {@link AchievementSection} follow. The last row turns pages and leads
 * back to wherever it was opened from, or closes.
 */
public final class AchievementsMenu extends Menu {

    private final AchievementPages<ItemStack, AchievementSection.Entry> pages;
    private final int footer;
    private final Map<Integer, Consumer<Player>> clicks = new HashMap<>();

    public AchievementsMenu(Achievements achievements, Messages messages, Achievements.Snapshot snapshot,
                            List<AchievementSection.Row> section, Optional<Consumer<Player>> back) {
        this(messages, new AchievementPages<>(groups(achievements, messages, snapshot, section)), back);
    }

    private AchievementsMenu(Messages messages, AchievementPages<ItemStack, AchievementSection.Entry> pages,
                             Optional<Consumer<Player>> back) {
        super(pages.inventoryRows(), messages.get("achievements.menu-title"));
        this.pages = pages;
        footer = (pages.inventoryRows() - 1) * AchievementPages.COLUMNS;
        back.ifPresentOrElse(this::backButton, this::closeButton);
        render();
    }

    private void render() {
        for (int slot = 0; slot < footer; slot++) {
            set(slot, null);
        }
        clicks.clear();
        paginate(pages.count());
        var rows = pages.page(page());
        for (int row = 0; row < rows.size(); row++) {
            int start = row * AchievementPages.COLUMNS;
            info(start, rows.get(row).heading());
            var entries = rows.get(row).entries();
            for (int index = 0; index < entries.size(); index++) {
                int slot = start + index + 1;
                var entry = entries.get(index);
                entry.click().ifPresentOrElse(click -> {
                    set(slot, entry.item());
                    clicks.put(slot, click);
                }, () -> info(slot, entry.item()));
            }
        }
    }

    @Override
    void pageTurned(Player player) {
        render();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        var click = clicks.get(slot);
        if (click != null) {
            click.accept(player);
        }
    }

    private static List<AchievementPages.Row<ItemStack, AchievementSection.Entry>> groups(
            Achievements achievements, Messages messages, Achievements.Snapshot snapshot, List<AchievementSection.Row> section) {
        var groups = new ArrayList<AchievementPages.Row<ItemStack, AchievementSection.Entry>>();
        for (var game : AchievementPages.byGame(achievements.catalog())) {
            long unlocked = game.entries().stream().filter(snapshot::owns).count();
            var heading = gameIcon(game.heading())
                    .name(messages.get("achievements.groups." + game.heading()))
                    .lore(messages.get("achievements.group-lore",
                            Formatter.number("unlocked", unlocked), Formatter.number("total", game.entries().size())))
                    .build();
            groups.add(new AchievementPages.Row<>(heading, game.entries().stream()
                    .map(entry -> AchievementSection.Entry.shown(item(achievements, messages, entry, snapshot))).toList()));
        }
        section.forEach(row -> groups.add(new AchievementPages.Row<>(row.heading(), row.entries())));
        return groups;
    }

    private static ItemStack item(Achievements achievements, Messages messages, Achievement achievement, Achievements.Snapshot snapshot) {
        var name = Placeholder.component("name", achievements.name(achievement));
        var description = Placeholder.component("description", achievements.description(achievement));
        if (snapshot.owns(achievement)) {
            return Items.of(Material.LIME_DYE).glow()
                    .name(messages.get("achievements.unlocked-name", name))
                    .lore(messages.getLines("achievements.unlocked-lore", description))
                    .build();
        }
        if (achievement.isMoment()) {
            return Items.of(Material.GRAY_DYE)
                    .name(messages.get("achievements.locked-name", name))
                    .lore(messages.getLines("achievements.moment-lore", description, Formatter.number("xp", achievement.xp())))
                    .build();
        }
        return Items.of(Material.GRAY_DYE)
                .name(messages.get("achievements.locked-name", name))
                .lore(messages.getLines("achievements.locked-lore", description,
                        Formatter.number("current", Math.min(snapshot.current(achievement), achievement.threshold())),
                        Formatter.number("threshold", achievement.threshold()),
                        Formatter.number("xp", achievement.xp())))
                .build();
    }

    private static Items.Builder gameIcon(String game) {
        return MenuIcon.byKey(game).map(MenuIcon::item).orElseGet(() -> Items.of(Material.NETHER_STAR).hideAttributes());
    }
}
