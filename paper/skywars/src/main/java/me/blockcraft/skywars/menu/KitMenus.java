package me.blockcraft.skywars.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.shop.Purchases;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.skywars.config.SkyWarsConfig;
import me.blockcraft.skywars.game.SkyWarsGame;
import me.blockcraft.skywars.kit.Kit;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** The lobby's kit choice: which kits the player owns is read off the main thread, a purchase confirmed first. */
public final class KitMenus {

    private final Messages messages;
    private final SkyWarsGame game;
    private final Unlocks unlocks;
    private final SkyWarsConfig config;

    public KitMenus(Messages messages, SkyWarsGame game, Unlocks unlocks, SkyWarsConfig config) {
        this.messages = messages;
        this.game = game;
        this.unlocks = unlocks;
        this.config = config;
    }

    public void open(Player player) {
        if (!game.isLobby()) {
            return;
        }
        BlockcraftApi.get().playerTasks().latest(player, () -> unlocks.owned(player.getUniqueId(), Kit.ITEM_PREFIX), items -> {
            if (game.isLobby()) {
                new KitMenu(messages, config, owned(items), game.kit(player), this::choose).open(player, game::isLobby);
            }
        });
    }

    private Set<Kit> owned(Set<String> items) {
        var owned = EnumSet.noneOf(Kit.class);
        owned.addAll(config.freeKits());
        items.stream().map(Kit::byItem).flatMap(Optional::stream).forEach(owned::add);
        return owned;
    }

    private void choose(Player player, Kit kit, boolean owned) {
        if (!game.isLobby()) {
            return;
        }
        if (owned) {
            select(player, kit);
            open(player);
            return;
        }
        Purchases.confirm(player, KitMenu.icon(messages, kit, Stream.empty()), kit.item(), config.price(kit),
                buyer -> game.isLobby(), buyer -> select(buyer, kit), this::open);
    }

    private void select(Player player, Kit kit) {
        game.selectKit(player, kit);
        Sounds.EQUIP.play(player);
        BlockcraftApi.get().notifications().feedback(player, messages.get("kit.selected",
                Placeholder.component("kit", game.kitName(kit))));
    }
}
