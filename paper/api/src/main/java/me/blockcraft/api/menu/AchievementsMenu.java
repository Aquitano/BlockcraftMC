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

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The catalog, one row per game with the game's icon in front: earned entries lit, the rest grey with how far
 * the player has come. The corner leads back to wherever it was opened from, or closes.
 */
public final class AchievementsMenu extends Menu {

    private static final int COLUMNS = 9;
    private static final int MAX_ROWS = 6;

    public AchievementsMenu(Achievements achievements, Messages messages, Achievements.Snapshot snapshot, Optional<Consumer<Player>> back) {
        super(rows(achievements), messages.get("achievements.menu-title"));
        var games = List.copyOf(achievements.games());
        for (int row = 0; row < games.size() && row < MAX_ROWS - 1; row++) {
            var game = games.get(row);
            var entries = achievements.of(game);
            long unlocked = entries.stream().filter(snapshot::owns).count();
            set(row * COLUMNS, gameIcon(game)
                    .name(messages.get("achievements.groups." + game))
                    .lore(messages.get("achievements.group-lore",
                            Formatter.number("unlocked", unlocked), Formatter.number("total", entries.size())))
                    .build());
            for (int column = 1; column <= entries.size() && column < COLUMNS; column++) {
                set(row * COLUMNS + column, item(achievements, messages, entries.get(column - 1), snapshot));
            }
        }
        int corner = rows(achievements) * COLUMNS - 1;
        back.ifPresentOrElse(previous -> backButton(corner, previous), () -> closeButton(corner));
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
    }

    private static int rows(Achievements achievements) {
        return Math.min(MAX_ROWS, achievements.games().size() + 1);
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
