package me.blockcraft.skywars.map;

import me.blockcraft.api.game.Ballot;
import me.blockcraft.api.game.VoteMenu;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Which map the round is played on: the lobby votes among every theme or leaves it to chance until the round starts,
 * unless staff forced one with /forcemap. The map is built only at the start, so the vote stays open to the end.
 */
public final class MapVote {

    private final Messages messages;
    private final Random random = new Random();
    private Ballot<Theme> ballot = new Ballot<>();
    private Optional<Theme> forced = Optional.empty();

    public MapVote(Messages messages) {
        this.messages = messages;
    }

    public void open(Player player) {
        var options = Arrays.stream(Theme.values()).map(theme -> new VoteMenu.Option(Optional.of(theme.key()),
                messages.get("vote.map", Placeholder.component("map", name(theme))), Items.of(theme.icon()).build(),
                messages.getLines("map." + theme.key() + ".lore"), ballot.votes(Optional.of(theme)))).toList();
        new VoteMenu(messages.get("vote.title"), options, Optional.of(ballot.votes(Optional.empty())),
                ballot.choiceOf(player.getUniqueId()).map(choice -> choice.map(Theme::key)), Optional.empty(), this::vote)
                .open(player);
    }

    public void withdraw(Player player) {
        ballot.withdraw(player.getUniqueId());
    }

    public List<String> keys() {
        return Arrays.stream(Theme.values()).map(Theme::key).toList();
    }

    public Optional<Component> force(String key) {
        var theme = Theme.byKey(key);
        theme.ifPresent(chosen -> forced = Optional.of(chosen));
        return theme.map(this::name);
    }

    /** The map of the round: the forced one, else the vote's winner; the next lobby votes afresh. */
    public Theme decide() {
        var theme = forced.orElseGet(() -> ballot.winner(Arrays.asList(Theme.values()), random).orElse(Theme.WOLKENHAIN));
        ballot = new Ballot<>();
        forced = Optional.empty();
        messages.getLines("vote.result", Placeholder.component("map", name(theme)))
                .forEach(Bukkit::broadcast);
        Sounds.NOTIFY.playToAll();
        return theme;
    }

    public Component name(Theme theme) {
        return messages.get("map." + theme.key() + ".name");
    }

    /** {@code key} empty is a vote to leave the map to chance. */
    private void vote(Player player, Optional<String> key) {
        Menu.close(player);
        var theme = key.flatMap(Theme::byKey);
        if (key.isPresent() && theme.isEmpty()) {
            VoteMenu.closed(player, messages);
            return;
        }
        var option = theme.map(this::name).orElseGet(() -> messages.get("vote.chance"));
        boolean changed = ballot.cast(player.getUniqueId(), theme).isPresent();
        VoteMenu.confirm(player, messages, option, changed);
    }
}
