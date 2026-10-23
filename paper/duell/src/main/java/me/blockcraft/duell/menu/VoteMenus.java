package me.blockcraft.duell.menu;

import me.blockcraft.api.game.VoteMenu;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import me.blockcraft.duell.game.DuelVote;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Duell's vote in two steps: the mode, then one of its playable maps or chance. Only a participant votes, in the lobby. */
public final class VoteMenus {

    private final Duel duel;
    private final Messages messages;

    public VoteMenus(Duel duel, Messages messages) {
        this.duel = duel;
        this.messages = messages;
    }

    public void openModes(Player player) {
        open(player, new ModeMenu(player));
    }

    /** Chance is offered only where a map is playable, so that nobody votes for a mode that cannot start. */
    private void openMaps(Player player, DuelMode mode) {
        var votes = duel.votes();
        var playable = duel.playableMaps(mode);
        var options = playable.stream().map(map -> new VoteMenu.Option(
                Optional.of(String.valueOf(map)),
                messages.get("menu.maps.map", Placeholder.unparsed("map", duel.mapName(mode, map))),
                Items.of(Material.FILLED_MAP).build(),
                List.of(),
                votes.votes(new DuelVote.Choice(mode, OptionalInt.of(map))))).toList();
        var chance = playable.isEmpty()
                ? Optional.<Long>empty()
                : Optional.of(votes.votes(new DuelVote.Choice(mode, OptionalInt.empty())));
        var own = votes.choiceOf(player.getUniqueId())
                .filter(choice -> choice.mode() == mode)
                .map(choice -> choice.map().stream().mapToObj(String::valueOf).findFirst());
        var title = messages.get("menu.maps.title", Placeholder.component("mode", messages.get("mode." + mode.configKey())));
        open(player, new VoteMenu(title, options, chance, own, Optional.of(this::openModes), (voter, key) -> {
            Menu.close(voter);
            duel.vote(voter, new DuelVote.Choice(mode, key.stream().mapToInt(Integer::parseInt).findFirst()));
        }));
    }

    private void open(Player player, Menu menu) {
        menu.open(player, () -> duel.isLobby() && duel.isParticipant(player));
    }

    private final class ModeMenu extends Menu {

        private static final int MLG_RUSH_SLOT = 22;
        private final List<DuelMode> modes = List.of(DuelMode.values());

        private ModeMenu(Player player) {
            super(4, messages.get("menu.modes.title"));
            closeButton();
            var votes = duel.votes();
            var own = votes.choiceOf(player.getUniqueId()).map(DuelVote.Choice::mode);
            var voteMessages = BlockcraftApi.get().messages();
            for (int index = 0; index < modes.size(); index++) {
                var mode = modes.get(index);
                boolean mine = own.filter(choice -> choice == mode).isPresent();
                var lore = new ArrayList<>(List.of(messages.get("menu.modes." + mode.configKey() + "-lore"),
                        voteMessages.get("vote.votes", Formatter.number("votes", votes.votes(mode)))));
                if (mine) {
                    lore.add(voteMessages.get("vote.own"));
                }
                lore.add(voteMessages.get("vote.choose"));
                var item = Items.of(icon(mode)).name(messages.get("menu.modes." + mode.configKey()))
                        .lore(lore).hideAttributes();
                if (mine) {
                    item.glow();
                }
                set(10 + index * 2, item.build());
            }
            set(MLG_RUSH_SLOT, MenuIcon.MLGRUSH.item().name(messages.get("menu.modes.mlgrush"))
                    .lore(messages.getLines("menu.modes.mlgrush-lore")).build());
        }

        @Override
        protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
            if (!duel.isLobby() || !duel.isParticipant(player)) {
                VoteMenu.closed(player, messages);
                return;
            }
            if (slot == MLG_RUSH_SLOT) {
                var plugin = JavaPlugin.getProvidingPlugin(VoteMenus.class);
                String group = plugin.getConfig().getString("navigation.mlgrush-group", "mlgrush");
                Menu.close(player, closed -> ProxyMessaging.play(plugin, closed, group));
            } else if (slot >= 10 && (slot - 10) % 2 == 0 && (slot - 10) / 2 < modes.size()) {
                openMaps(player, modes.get((slot - 10) / 2));
            }
        }
    }

    private static Material icon(DuelMode mode) {
        return switch (mode) {
            case VS -> Material.IRON_SWORD;
            case SG -> Material.CHAINMAIL_CHESTPLATE;
            case SUPERJUMP -> Material.GOLDEN_BOOTS;
            case SUMO -> Material.SLIME_BLOCK;
        };
    }
}
