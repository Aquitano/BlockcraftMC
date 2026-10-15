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
 * The player's own card: rank, level, coins and what every game remembers about them. The toolbar closes, opens
 * the settings and, where the server has one, the cosmetics; elsewhere its third button leads to the lobby.
 */
final class ProfileMenu extends Menu {

    private static final int RECAP_SLOT = 11;
    private static final int HEAD_SLOT = 13;
    private static final int ACHIEVEMENTS_SLOT = 15;
    private static final int GAMES_ROW = 18;

    private final Consumer<Player> achievements;
    private final Optional<Consumer<Player>> recentRound;
    private final Optional<Consumer<Player>> advent;

    ProfileMenu(Profiles profiles, Player viewer, Profiles.Snapshot snapshot, Optional<Consumer<Player>> cosmetics) {
        super(3, profiles.messages().get("profile.title"));
        recentRound = profiles.recentRound();
        advent = profiles.advent();
        var messages = profiles.messages();
        advent.ifPresent(open -> set(17, Items.of(Material.CHEST).name(messages.get("profile.advent")).build()));
        achievements = player -> profiles.achievements().open(player, Optional.of(back -> profiles.open(back, cosmetics)));
        closeButton(ToolbarButton.LEFT);
        toolbar(ToolbarButton.MIDDLE, button(messages, MenuIcon.TOOL_SETTINGS, "profile.settings"),
                player -> new SettingsDialog(player, Optional.of(back -> profiles.open(back, cosmetics))).open(player));
        cosmetics.ifPresentOrElse(
                open -> toolbar(ToolbarButton.RIGHT, button(messages, MenuIcon.TOOL_COSMETICS, "profile.cosmetics"), open),
                () -> toolbar(ToolbarButton.RIGHT, button(messages, MenuIcon.TOOL_HOME, "profile.hub"),
                        player -> ProxyMessaging.hub(profiles.plugin(), player)));
        var xp = profiles.xp();
        var progress = xp.levels().progress(xp.total(viewer));
        recentRound.ifPresent(open -> set(RECAP_SLOT, Items.of(Material.WRITABLE_BOOK)
                .name(messages.get("profile.recap")).lore(messages.get("profile.recap-lore")).build()));
        info(HEAD_SLOT, Items.head(viewer.getUniqueId(), profiles.realNames().of(viewer))
                .name(messages.get("profile.head", Placeholder.unparsed("player", viewer.getName())))
                .lore(messages.getLines("profile.head-lore",
                        Placeholder.component("rank", Ranks.prefix(viewer).orElse(Component.empty())),
                        Formatter.number("level", progress.level()),
                        Formatter.number("next", progress.level() + 1),
                        Formatter.number("missing", progress.needed() - progress.into()),
                        Formatter.number("coins", snapshot.coins())))
                .build());
        set(ACHIEVEMENTS_SLOT, Items.of(Material.NETHER_STAR).hideAttributes()
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
        if (slot == 17) {
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

    private static int gameSlot(int index, int games) {
        return GAMES_ROW + (9 - games) / 2 + index;
    }
}
