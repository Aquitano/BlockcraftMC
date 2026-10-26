package me.blockcraft.api.menu;

import me.blockcraft.api.dialog.SettingsDialog;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.rank.Ranks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The player's own card, with separate destinations for permanent levels and spendable coins.
 */
final class ProfileMenu extends Menu {

    private static final int LEVEL_SLOT = 10;
    private static final int HEAD_SLOT = 13;
    private static final int COINS_SLOT = 16;
    private static final int RECAP_SLOT = 19;
    private static final int ACHIEVEMENTS_SLOT = 22;
    private static final int ADVENT_SLOT = 25;
    private static final int GAMES_ROW = 27;
    private static final int ROWS_ABOVE_GAMES = 3;
    private static final int COLUMNS = 9;

    private final Consumer<Player> achievements;
    private final Consumer<Player> progression;
    private final Consumer<Player> shop;
    private final Optional<Consumer<Player>> recentRound;
    private final Optional<Consumer<Player>> advent;

    ProfileMenu(Profiles profiles, Player viewer, Profiles.Snapshot snapshot, Optional<Consumer<Player>> cosmetics) {
        super(ROWS_ABOVE_GAMES + gameRows(snapshot.games().size()), profiles.messages().get("profile.title"));
        recentRound = profiles.recentRound();
        advent = profiles.advent();
        var messages = profiles.messages();
        advent.ifPresent(open -> set(ADVENT_SLOT, MenuIcon.ADVENT.item().name(messages.get("profile.advent")).build()));
        achievements = player -> profiles.achievements().open(player, Optional.of(back -> profiles.open(back, cosmetics)));
        progression = player -> profiles.progression(player, Optional.of(back -> profiles.open(back, cosmetics)));
        shop = profiles.shop().orElse(player -> ProxyMessaging.hub(profiles.plugin(), player));
        closeButton(ToolbarButton.LEFT);
        toolbar(ToolbarButton.MIDDLE, button(messages, MenuIcon.TOOL_SETTINGS, "profile.settings"),
                player -> new SettingsDialog(player, Optional.of(back -> profiles.open(back, cosmetics))).open(player));
        cosmetics.ifPresentOrElse(
                open -> toolbar(ToolbarButton.RIGHT, button(messages, MenuIcon.TOOL_COSMETICS, "profile.cosmetics"), open),
                () -> toolbar(ToolbarButton.RIGHT, button(messages, MenuIcon.TOOL_HOME, "profile.hub"),
                        player -> ProxyMessaging.hub(profiles.plugin(), player)));
        var xp = profiles.xp();
        var progress = xp.levels().progress(xp.total(viewer));
        recentRound.ifPresent(open -> set(RECAP_SLOT, MenuIcon.RECAP.item()
                .name(messages.get("profile.recap")).lore(messages.get("profile.recap-lore")).build()));
        info(HEAD_SLOT, Items.head(viewer.getUniqueId(), profiles.realNames().of(viewer))
                .name(messages.get("profile.head", Placeholder.unparsed("player", viewer.getName())))
                .lore(messages.getLines("profile.head-lore",
                        Formatter.number("level", progress.level()),
                        Formatter.number("missing", progress.needed() - progress.into()),
                        Formatter.number("next", progress.level() + 1),
                        Formatter.number("coins", snapshot.coins()),
                        Placeholder.component("rank", Ranks.prefix(viewer).orElse(Component.empty()))))
                .build());
        set(LEVEL_SLOT, MenuIcon.ACHIEVEMENTS.item()
                .name(messages.get("profile.level", Formatter.number("level", progress.level())))
                .lore(messages.getLines("profile.level-lore",
                        Formatter.number("xp", progress.into()), Formatter.number("needed", progress.needed()),
                        Placeholder.component("reward", profiles.achievements().nextCosmetic(progress.level()))))
                .build());
        set(COINS_SLOT, MenuIcon.TOOL_SHOP.item()
                .name(messages.get("profile.coins", Formatter.number("coins", snapshot.coins())))
                .lore(messages.getLines(profiles.shop().isPresent() ? "profile.coins-lore" : "profile.coins-hub-lore"))
                .build());
        set(ACHIEVEMENTS_SLOT, MenuIcon.ACHIEVEMENTS.item()
                .name(messages.get("profile.achievements"))
                .lore(messages.get("profile.achievements-lore",
                        Formatter.number("unlocked", snapshot.achievements()),
                        Formatter.number("total", profiles.achievements().catalog().size())))
                .build());
        var games = snapshot.games();
        for (int index = 0; index < games.size(); index++) {
            var game = games.get(index);
            info(gameSlot(index, games.size()), gameIcon(game.key())
                    .name(messages.get("profile.games." + game.key()))
                    .lore(messages.getLines("profile.stats-lore", game.stats().placeholders()))
                    .build());
        }
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == LEVEL_SLOT) {
            progression.accept(player);
        } else if (slot == COINS_SLOT) {
            shop.accept(player);
        } else if (slot == ADVENT_SLOT) {
            advent.ifPresent(open -> open.accept(player));
        } else if (slot == RECAP_SLOT) {
            recentRound.ifPresent(open -> open.accept(player));
        } else if (slot == ACHIEVEMENTS_SLOT) {
            achievements.accept(player);
        }
    }

    private static ItemStack button(Messages messages, MenuIcon icon, String key) {
        return icon.item().name(messages.get(key)).lore(messages.get(key + "-lore")).build();
    }

    /** A game the pack has no icon for, such as one added in config.yml alone, shows as a sword. */
    private static Items.Builder gameIcon(String key) {
        return MenuIcon.byKey(key).map(MenuIcon::item).orElseGet(() -> Items.of(Material.IRON_SWORD).hideAttributes());
    }

    /** One row per nine games, at least one. */
    static int gameRows(int games) {
        return Math.max(1, (games + COLUMNS - 1) / COLUMNS);
    }

    /** The games share their rows evenly, each row centred: ten games are two rows of five, not nine and one. */
    static int gameSlot(int index, int games) {
        int rows = gameRows(games);
        int perRow = (games + rows - 1) / rows;
        int row = index / perRow;
        int inRow = Math.min(perRow, games - row * perRow);
        return GAMES_ROW + row * COLUMNS + (COLUMNS - inRow) / 2 + index % perRow;
    }
}
