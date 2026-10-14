package me.blockcraft.bedwars.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/** A few maps of the pool drawn for one lobby, each voter's one choice among them, and the map that wins. */
public final class MapVote {

    private final List<String> choices;
    private final Map<UUID, String> votes = new HashMap<>();

    private MapVote(List<String> choices) {
        this.choices = List.copyOf(choices);
    }

    /** Up to {@code size} maps of {@code pool}, drawn at random. */
    public static MapVote among(List<String> pool, int size, Random random) {
        var shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        return new MapVote(shuffled.subList(0, Math.min(size, shuffled.size())));
    }

    public List<String> choices() {
        return choices;
    }

    /** The voter's earlier choice, if they change their mind. */
    public Optional<String> cast(UUID voter, String map) {
        if (!choices.contains(map)) {
            throw new IllegalArgumentException(map + " is not up for the vote");
        }
        return Optional.ofNullable(votes.put(voter, map));
    }

    public void withdraw(UUID voter) {
        votes.remove(voter);
    }

    public long votes(String map) {
        return votes.values().stream().filter(map::equals).count();
    }

    /** The most voted map; a tie, or a vote nobody took part in, is decided by chance. */
    public Optional<String> winner(Random random) {
        long most = choices.stream().mapToLong(this::votes).max().orElse(0);
        var leaders = choices.stream().filter(map -> votes(map) == most).toList();
        return leaders.isEmpty() ? Optional.empty() : Optional.of(leaders.get(random.nextInt(leaders.size())));
    }
}
