package me.blockcraft.api.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Each voter's one choice among some options, or their vote to leave it to chance, and the option that wins: the
 * most voted one, where chance breaks a tie, picks for those who left it to chance and decides a vote nobody took
 * part in.
 */
public final class Ballot<K> {

    private final Map<UUID, Optional<K>> votes = new HashMap<>();

    /** The voter's earlier choice, if they change their mind; empty for a vote that leaves it to chance. */
    public Optional<Optional<K>> cast(UUID voter, Optional<K> choice) {
        return Optional.ofNullable(votes.put(voter, choice));
    }

    public void withdraw(UUID voter) {
        votes.remove(voter);
    }

    public Optional<Optional<K>> choiceOf(UUID voter) {
        return Optional.ofNullable(votes.get(voter));
    }

    public long votes(Optional<K> choice) {
        return votes.values().stream().filter(choice::equals).count();
    }

    /** The winner among {@code options}; votes for anything else do not count. Empty only without options. */
    public Optional<K> winner(List<K> options, Random random) {
        if (options.isEmpty()) {
            return Optional.empty();
        }
        var candidates = Stream.concat(options.stream().map(Optional::of), Stream.of(Optional.<K>empty())).toList();
        long most = candidates.stream().mapToLong(this::votes).max().orElse(0);
        var leaders = candidates.stream().filter(choice -> votes(choice) == most).toList();
        return leaders.get(random.nextInt(leaders.size())).or(() -> Optional.of(options.get(random.nextInt(options.size()))));
    }
}
