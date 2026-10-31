package me.blockcraft.api.menu;

import me.blockcraft.api.achievement.Achievement;
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

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The catalog, a game's icon before each row of entries: earned entries lit, the rest grey with how far the
 * player has come. The last row turns pages and leads back to wherever it was opened from, or closes.
 */
public final class AchievementsMenu extends Menu {

    private final Achievements achievements;
    private final Messages messages;
    private final Achievements.Snapshot snapshot;
    private final AchievementPages pages;
    private final int footer;

    public AchievementsMenu(Achievements achievements, Messages messages, Achievements.Snapshot snapshot, Optional<Consumer<Player>> back) {
        this(achievements, messages, snapshot, back, new AchievementPages(achievements.catalog()));
    }

    private AchievementsMenu(Achievements achievements, Messages messages, Achievements.Snapshot snapshot,
                             Optional<Consumer<Player>> back, AchievementPages pages) {
        super(pages.inventoryRows(), messages.get("achievements.menu-title"));
        this.achievements = achievements;
        this.messages = messages;
        this.snapshot = snapshot;
        this.pages = pages;
        footer = (pages.inventoryRows() - 1) * AchievementPages.COLUMNS;
        back.ifPresentOrElse(this::backButton, this::closeButton);
        render();
    }

    private void render() {
        for (int slot = 0; slot < footer; slot++) {
            set(slot, null);
        }
        paginate(pages.count());
        var rows = pages.page(page());
        for (int row = 0; row < rows.size(); row++) {
            var group = rows.get(row);
            var entries = achievements.of(group.game());
            long unlocked = entries.stream().filter(snapshot::owns).count();
            int start = row * AchievementPages.COLUMNS;
            info(start, gameIcon(group.game())
                    .name(messages.get("achievements.groups." + group.game()))
                    .lore(messages.get("achievements.group-lore",
                            Formatter.number("unlocked", unlocked), Formatter.number("total", entries.size())))
                    .build());
            for (int index = 0; index < group.entries().size(); index++) {
                info(start + index + 1, item(achievements, messages, group.entries().get(index), snapshot));
            }
        }
    }

    @Override
    void pageTurned(Player player) {
        render();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
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
