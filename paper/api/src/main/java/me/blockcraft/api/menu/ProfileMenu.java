package me.blockcraft.api.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.rank.Ranks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.function.Consumer;

/** The player's own card: rank, level, coins and what every game remembers about them. */
final class ProfileMenu extends Menu {

    private static final int HEAD_SLOT = 4;
    private static final int GAMES_ROW = 9;
    private static final int SETTINGS_SLOT = 21;
    private static final int COSMETICS_SLOT = 23;
    private static final int CLOSE_SLOT = 26;

    private final Profiles profiles;
    private final Optional<Consumer<Player>> cosmetics;

    ProfileMenu(Profiles profiles, Player viewer, Profiles.Snapshot snapshot, Optional<Consumer<Player>> cosmetics) {
        super(3, BlockcraftApi.get().messages().get("profile.title"));
        this.profiles = profiles;
        this.cosmetics = cosmetics;
        var api = BlockcraftApi.get();
        var messages = api.messages();
        var progress = api.xp().levels().progress(api.xp().total(viewer));
        set(HEAD_SLOT, Items.head(viewer.getUniqueId(), api.realName(viewer))
                .name(messages.get("profile.head", Placeholder.unparsed("player", viewer.getName())))
                .lore(messages.getLines("profile.head-lore",
                        Placeholder.component("rank", Ranks.prefix(viewer).orElse(Component.empty())),
                        Formatter.number("level", progress.level()),
                        Formatter.number("next", progress.level() + 1),
                        Formatter.number("missing", progress.needed() - progress.into()),
                        Formatter.number("coins", snapshot.coins())))
                .build());
        var games = snapshot.games();
        for (int index = 0; index < games.size(); index++) {
            var game = games.get(index);
            set(gameSlot(index, games.size()), Items.of(Material.IRON_SWORD)
                    .name(messages.get("profile.games." + game.key()))
                    .lore(messages.getLines("profile.stats-lore", game.stats().placeholders()))
                    .hideAttributes()
                    .build());
        }
        set(SETTINGS_SLOT, Items.of(Material.COMPARATOR).name(messages.get("profile.settings"))
                .lore(messages.get("profile.settings-lore")).build());
        cosmetics.ifPresent(ignored -> set(COSMETICS_SLOT, Items.of(Material.ENDER_CHEST)
                .name(messages.get("profile.cosmetics")).lore(messages.get("profile.cosmetics-lore")).build()));
        closeButton(CLOSE_SLOT);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == SETTINGS_SLOT) {
            new SettingsMenu(player, Optional.of(back -> profiles.open(back, cosmetics))).open(player);
        } else if (slot == COSMETICS_SLOT) {
            cosmetics.ifPresent(open -> open.accept(player));
        }
    }

    private static int gameSlot(int index, int games) {
        return GAMES_ROW + (9 - games) / 2 + index;
    }
}
