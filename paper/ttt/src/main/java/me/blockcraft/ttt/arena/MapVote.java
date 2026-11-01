package me.blockcraft.ttt.arena;

import me.blockcraft.api.game.Ballot;
import me.blockcraft.api.game.VoteMenu;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Which map the round is played on: the lobby votes among the maps or leaves it to chance until the round starts,
 * unless staff forced one with /forcemap. A map is loaded or built only once it is chosen, and the server restarts
 * after its one round, so the vote is never held twice.
 */
public final class MapVote {

    /** A map the lobby can vote for; {@code town} loads or builds it for the round. */
    public record Choice(String key, String name, List<String> builders, Material icon, Supplier<Town> town) {

        public Choice {
            builders = List.copyOf(builders);
        }
    }

    private final Messages messages;
    private final List<Choice> choices;
    private final Logger logger;
    private final Random random = new Random();
    private final Ballot<String> ballot = new Ballot<>();
    private Optional<Choice> forced = Optional.empty();

    public MapVote(Messages messages, List<Choice> choices, Logger logger) {
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("A vote needs a map");
        }
        this.messages = messages;
        this.choices = List.copyOf(choices);
        this.logger = logger;
    }

    public void open(Player player) {
        var options = choices.stream().map(choice -> new VoteMenu.Option(Optional.of(choice.key()),
                messages.get("vote.map", Placeholder.unparsed("map", choice.name())),
                ItemStack.of(choice.icon().isItem() ? choice.icon() : Material.PAPER),
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

    public Optional<Component> force(String key) {
        var choice = choice(key);
        choice.ifPresent(chosen -> forced = Optional.of(chosen));
        return choice.map(chosen -> Component.text(chosen.name()));
    }

    /** Loads the chosen map, falling back to the first, already-built map if loading fails. */
    public Town decide() {
        var choice = forced.or(() -> ballot.winner(keys(), random).flatMap(this::choice)).orElseThrow();
        Town town;
        try {
            town = choice.town().get();
        } catch (RuntimeException failure) {
            logger.warn("Could not load TTT map {}; using {}", choice.key(), choices.getFirst().key(), failure);
            choice = choices.getFirst();
            town = choice.town().get();
        }
        messages.getLines("vote.result", Placeholder.unparsed("map", choice.name())).forEach(Bukkit::broadcast);
        Sounds.NOTIFY.playToAll();
        return town;
    }

    /** {@code key} empty is a vote to leave the map to chance. */
    private void vote(Player player, Optional<String> key) {
        Menu.close(player);
        var choice = key.flatMap(this::choice);
        if (key.isPresent() && choice.isEmpty()) {
            VoteMenu.closed(player, messages);
            return;
        }
        var option = choice.<Component>map(chosen -> Component.text(chosen.name())).orElseGet(() -> messages.get("vote.chance"));
        boolean changed = ballot.cast(player.getUniqueId(), choice.map(Choice::key)).isPresent();
        VoteMenu.confirm(player, messages, option, changed);
    }

    private Optional<Choice> choice(String key) {
        return choices.stream().filter(choice -> choice.key().equals(key)).findFirst();
    }

    private List<Component> builders(Choice choice) {
        return choice.builders().isEmpty()
                ? List.of()
                : List.of(messages.get("vote.builders", Placeholder.unparsed("builders", String.join(", ", choice.builders()))));
    }
}
