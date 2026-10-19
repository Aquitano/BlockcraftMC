package me.blockcraft.duell.menu;

import me.blockcraft.api.game.VoteMenu;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import me.blockcraft.duell.game.DuelVote;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
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
        var votes = duel.votes();
        var options = Arrays.stream(DuelMode.values()).map(mode -> new VoteMenu.Option(
                Optional.of(mode.configKey()),
                messages.get("menu.modes." + mode.configKey()),
                Items.of(icon(mode)).build(),
                List.of(messages.get("menu.modes." + mode.configKey() + "-lore")),
                votes.votes(mode))).toList();
        var own = votes.choiceOf(player.getUniqueId()).map(choice -> Optional.of(choice.mode().configKey()));
        open(player, new VoteMenu(messages.get("menu.modes.title"), options, Optional.empty(), own, Optional.empty(),
                (voter, key) -> key.flatMap(DuelMode::byConfigKey).ifPresent(mode -> openMaps(voter, mode))));
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

    private void open(Player player, VoteMenu menu) {
        menu.open(player, () -> duel.isLobby() && duel.isParticipant(player));
    }

    private static Material icon(DuelMode mode) {
        return switch (mode) {
            case VS -> Material.IRON_SWORD;
            case SG -> Material.CHAINMAIL_CHESTPLATE;
            case SUPERJUMP -> Material.GOLDEN_BOOTS;
        };
    }
}
