package me.blockcraft.duell.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** What the participants chose in the lobby, counted once when the round starts. */
public final class DuelVote {

    /** A vote for {@code mode}, and for one of its maps unless the voter left that to chance. */
    public record Choice(DuelMode mode, OptionalInt map) {
    }

    public record Outcome(DuelMode mode, int map) {
    }

    /** Without a playable map the first one is used, and the duel warns that its spawns are missing. */
    private static final int FALLBACK_MAP = 1;

    private final Map<UUID, Choice> choices = new HashMap<>();

    /** False if the voter had already cast this very choice. */
    boolean cast(UUID voter, Choice choice) {
        return !choice.equals(choices.put(voter, choice));
    }

    void withdraw(UUID voter) {
        choices.remove(voter);
    }

    public Optional<Choice> choiceOf(UUID voter) {
        return Optional.ofNullable(choices.get(voter));
    }

    public long votes(DuelMode mode) {
        return choices.values().stream().filter(choice -> choice.mode() == mode).count();
    }

    public long votes(DuelMode mode, int map) {
        return choices.values().stream()
                .filter(choice -> choice.mode() == mode && choice.map().equals(OptionalInt.of(map)))
                .count();
    }

    /** The mode ahead of all others; none while nobody voted or the votes are tied. */
    public Optional<DuelMode> leader() {
        var ahead = mostVoted(choices.values().stream().map(Choice::mode));
        return ahead.size() == 1 ? Optional.of(ahead.getFirst()) : Optional.empty();
    }

    /**
     * The most-voted mode and, among the votes for it, the most-voted of its {@code playable} maps; ties fall to
     * {@code random}. Without votes any mode with a playable map can come up, and without map votes any playable map.
     */
    public Outcome tally(Map<DuelMode, List<Integer>> playable, Random random) {
        var modes = mostVoted(choices.values().stream().map(Choice::mode));
        if (modes.isEmpty()) {
            modes = playable.entrySet().stream()
                    .filter(entry -> !entry.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
        }
        var mode = pick(modes.isEmpty() ? List.of(DuelMode.values()) : modes, random);
        var maps = playable.getOrDefault(mode, List.of());
        var votedMaps = mostVoted(choices.values().stream()
                .filter(choice -> choice.mode() == mode)
                .flatMapToInt(choice -> choice.map().stream())
                .boxed()
                .filter(maps::contains));
        var candidates = votedMaps.isEmpty() ? maps : votedMaps;
        return new Outcome(mode, candidates.isEmpty() ? FALLBACK_MAP : pick(candidates, random));
    }

    /** Every option with the highest count, in natural order so that an injected {@link Random} decides predictably. */
    private static <T extends Comparable<T>> List<T> mostVoted(Stream<T> votes) {
        var counts = votes.collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
        long most = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() == most)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static <T> T pick(List<T> options, Random random) {
        return options.get(random.nextInt(options.size()));
    }
}
