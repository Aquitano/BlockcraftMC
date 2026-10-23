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

/** What the participants chose in the lobby, counted once when the round starts. Staff may force the mode. */
public final class DuelVote {

    /** A vote for {@code mode}, and for one of its maps unless the voter left that to chance. */
    public record Choice(DuelMode mode, OptionalInt map) {
    }

    public record Outcome(DuelMode mode, int map) {
    }

    /** Without a playable map the first one is used, and the duel warns that its spawns are missing. */
    private static final int FALLBACK_MAP = 1;

    private final Map<UUID, Choice> choices = new HashMap<>();
    private final Map<UUID, DuelSeries.Format> formats = new HashMap<>();
    private final Map<UUID, DuelRating> ratings = new HashMap<>();
    private Optional<DuelMode> forced = Optional.empty();

    /** The voter's earlier choice, which this one replaces. */
    Optional<Choice> cast(UUID voter, Choice choice) {
        return Optional.ofNullable(choices.put(voter, choice));
    }

    void withdraw(UUID voter) {
        choices.remove(voter);
        formats.remove(voter);
        ratings.remove(voter);
    }

    void clearChoice(UUID voter) {
        choices.remove(voter);
    }

    void chooseFormat(UUID voter, DuelSeries.Format format) {
        formats.put(voter, format);
    }

    public DuelSeries.Format formatChoice(UUID voter) {
        return formats.getOrDefault(voter, DuelSeries.Format.SINGLE);
    }

    public DuelSeries.Format selectedFormat(List<UUID> participants) {
        return participants.size() == 2 && !participants.getFirst().equals(participants.getLast())
                && participants.stream().allMatch(player -> formatChoice(player) == DuelSeries.Format.TOUR)
                ? DuelSeries.Format.TOUR : DuelSeries.Format.SINGLE;
    }

    void chooseRating(UUID voter, DuelRating rating) {
        ratings.put(voter, rating);
    }

    public DuelRating ratingChoice(UUID voter) {
        return ratings.getOrDefault(voter, DuelRating.UNRATED);
    }

    public DuelRating selectedRating(List<UUID> participants, boolean ratedEnabled) {
        return ratedEnabled && participants.size() == 2 && !participants.getFirst().equals(participants.getLast())
                && participants.stream().allMatch(player -> ratingChoice(player) == DuelRating.RATED)
                ? DuelRating.RATED : DuelRating.UNRATED;
    }

    List<DuelMode> nominations() {
        return choices.values().stream().map(Choice::mode).distinct().sorted().toList();
    }

    /** The mode is settled whatever the votes say; the votes for its maps still count. */
    void force(DuelMode mode) {
        forced = Optional.of(mode);
    }

    public Optional<DuelMode> forced() {
        return forced;
    }

    void clearForced() {
        forced = Optional.empty();
    }

    void clearRatings() {
        ratings.clear();
    }

    public Optional<Choice> choiceOf(UUID voter) {
        return Optional.ofNullable(choices.get(voter));
    }

    public long votes(DuelMode mode) {
        return choices.values().stream().filter(choice -> choice.mode() == mode).count();
    }

    public long votes(Choice choice) {
        return choices.values().stream().filter(choice::equals).count();
    }

    /**
     * Where the vote stands: the forced mode or the one ahead of all others, with the map ahead among the votes for
     * it if one is; none while nobody voted or the modes are tied.
     */
    public Optional<Choice> leader() {
        var modes = forced.map(List::of).orElseGet(() -> mostVoted(choices.values().stream().map(Choice::mode)));
        if (modes.size() != 1) {
            return Optional.empty();
        }
        var mode = modes.getFirst();
        var maps = mostVoted(mapVotes(mode));
        return Optional.of(new Choice(mode, maps.size() == 1 ? OptionalInt.of(maps.getFirst()) : OptionalInt.empty()));
    }

    /**
     * The forced or most-voted mode and, among the votes for it, the most-voted of its {@code playable} maps; ties
     * fall to {@code random}. Without votes any mode with a playable map can come up, and without map votes any
     * playable map. Votes for unavailable modes are ignored while another mode has a playable map.
     */
    public Outcome tally(Map<DuelMode, List<Integer>> playable, Random random) {
        var available = playable.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        var modes = forced.map(List::of).orElseGet(() -> mostVoted(choices.values().stream().map(Choice::mode)
                .filter(mode -> available.isEmpty() || available.contains(mode))));
        if (modes.isEmpty()) {
            modes = available;
        }
        var mode = pick(modes.isEmpty() ? List.of(DuelMode.values()) : modes, random);
        return tally(mode, playable.getOrDefault(mode, List.of()), random);
    }

    Outcome tally(DuelMode mode, List<Integer> maps, Random random) {
        var votedMaps = mostVoted(mapVotes(mode).filter(maps::contains));
        var candidates = votedMaps.isEmpty() ? maps : votedMaps;
        return new Outcome(mode, candidates.isEmpty() ? FALLBACK_MAP : pick(candidates, random));
    }

    private Stream<Integer> mapVotes(DuelMode mode) {
        return choices.values().stream()
                .filter(choice -> choice.mode() == mode)
                .flatMapToInt(choice -> choice.map().stream())
                .boxed();
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
