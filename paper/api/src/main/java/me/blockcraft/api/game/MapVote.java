package me.blockcraft.api.game;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * A lobby's vote among a game's maps, open until the game decides it and settled earlier by {@code /forcemap}. The
 * texts are the game's: {@code vote.item}, {@code vote.title}, {@code vote.map}, {@code vote.builders},
 * {@code vote.chance} and {@code vote.result}.
 */
public final class MapVote {

    public record Choice(String key, String name, List<String> builders) {

        public Choice {
            builders = List.copyOf(builders);
        }
    }

    private final Messages messages;
    private final List<Choice> choices;
    private final Material icon;
    private final Random random = new Random();
    private Ballot<String> ballot = new Ballot<>();
    private Optional<Choice> forced = Optional.empty();
    private Optional<Choice> decided = Optional.empty();

    public MapVote(Messages messages, List<Choice> choices, Material icon) {
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("A vote needs a map");
        }
        this.messages = messages;
        this.choices = List.copyOf(choices);
        this.icon = icon;
    }

    /** With a single map there is nothing to vote on. */
    public boolean offered() {
        return choices.size() > 1;
    }

    public boolean open() {
        return offered() && decided.isEmpty();
    }

    /** The lobby hotbar's item for the vote, there while {@code lobby} holds and the vote is open. */
    public LobbyHotbar.Choice hotbarChoice(BooleanSupplier lobby) {
        return new LobbyHotbar.Choice(MenuIcon.VOTE, messages.get("vote.item"), () -> lobby.getAsBoolean() && open(), this::show);
    }

    public void show(Player player) {
        if (!open()) {
            VoteMenu.closed(player, messages);
            return;
        }
        var options = choices.stream().map(choice -> new VoteMenu.Option(Optional.of(choice.key()),
                messages.get("vote.map", Placeholder.unparsed("map", choice.name())), ItemStack.of(icon),
                builders(choice), ballot.votes(Optional.of(choice.key())))).toList();
        new VoteMenu(messages.get("vote.title"), options, Optional.of(ballot.votes(Optional.empty())),
                ballot.choiceOf(player.getUniqueId()), Optional.empty(), this::vote).open(player);
    }

    public void withdraw(Player player) {
        ballot.withdraw(player.getUniqueId());
    }

    public List<String> keys() {
        return choices.stream().map(Choice::key).toList();
    }

    /** Settles the vote on {@code key} while it is open; the map's name, or empty for an unknown key. */
    public Optional<Component> force(String key) {
        var choice = choice(key);
        choice.ifPresent(chosen -> forced = Optional.of(chosen));
        return choice.map(chosen -> Component.text(chosen.name()));
    }

    public Optional<Choice> decided() {
        return decided;
    }

    /** Closes the vote with the forced map, else its winner, and tells everyone; later calls return the same map. */
    public Choice decide() {
        if (decided.isEmpty()) {
            var choice = forced.or(() -> ballot.winner(keys(), random).flatMap(this::choice)).orElseThrow();
            decided = Optional.of(choice);
            if (offered()) {
                messages.getLines("vote.result", Placeholder.unparsed("map", choice.name())).forEach(Bukkit::broadcast);
                Sounds.NOTIFY.playToAll();
            }
        }
        return decided.orElseThrow();
    }

    /** A fresh vote for the next round on the same server. */
    public void reopen() {
        ballot = new Ballot<>();
        forced = Optional.empty();
        decided = Optional.empty();
    }

    /** {@code key} empty is a vote to leave the map to chance. */
    private void vote(Player player, Optional<String> key) {
        Menu.close(player);
        var choice = key.flatMap(this::choice);
        if (!open() || key.isPresent() && choice.isEmpty()) {
            VoteMenu.closed(player, messages);
            return;
        }
        var option = choice.<Component>map(chosen -> Component.text(chosen.name())).orElseGet(() -> messages.get("vote.chance"));
        boolean changed = ballot.cast(player.getUniqueId(), choice.map(Choice::key)).isPresent();
        VoteMenu.confirm(player, messages, option, changed);
    }

    private Optional<Choice> choice(String key) {
        return choices.stream().filter(choice -> choice.key().equalsIgnoreCase(key)).findFirst();
    }

    private List<Component> builders(Choice choice) {
        return choice.builders().isEmpty()
                ? List.of()
                : List.of(messages.get("vote.builders", Placeholder.unparsed("builders", String.join(", ", choice.builders()))));
    }
}
