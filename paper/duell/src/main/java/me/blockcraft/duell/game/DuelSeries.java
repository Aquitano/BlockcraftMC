package me.blockcraft.duell.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public final class DuelSeries {

    public enum Format { SINGLE, TOUR }

    private final List<UUID> participants;
    private final List<DuelVote.Outcome> schedule;
    private final int[] wins = new int[2];
    private int gameIndex;

    public DuelSeries(List<UUID> participants, List<DuelVote.Outcome> schedule) {
        this.participants = List.copyOf(participants);
        this.schedule = List.copyOf(schedule);
        if (this.participants.size() != 2 || this.participants.getFirst().equals(this.participants.getLast())) {
            throw new IllegalArgumentException("A duel series needs two distinct participants");
        }
        if ((this.schedule.size() != 1 && this.schedule.size() != 3)
                || this.schedule.stream().map(DuelVote.Outcome::mode).distinct().count() != this.schedule.size()) {
            throw new IllegalArgumentException("A duel series needs one game or three distinct modes");
        }
    }

    public static List<DuelVote.Outcome> plan(Format format, DuelVote votes,
                                            Map<DuelMode, List<Integer>> playable, Random random) {
        var available = Arrays.stream(DuelMode.values())
                .filter(mode -> !playable.getOrDefault(mode, List.of()).isEmpty())
                .toList();
        if (format == Format.SINGLE || available.size() < 3) {
            return List.of(votes.tally(playable, random));
        }
        var modes = new ArrayList<DuelMode>();
        votes.forced().filter(available::contains).ifPresent(modes::add);
        votes.nominations().stream().filter(available::contains).limit(2)
                .filter(mode -> !modes.contains(mode)).forEach(modes::add);
        var remaining = new ArrayList<>(available);
        remaining.removeAll(modes);
        while (modes.size() < 3) {
            modes.add(remaining.remove(random.nextInt(remaining.size())));
        }
        Collections.shuffle(modes, random);
        return modes.stream().map(mode -> votes.tally(mode, playable.get(mode), random)).toList();
    }

    public List<UUID> participants() {
        return participants;
    }

    public List<DuelVote.Outcome> schedule() {
        return schedule;
    }

    public int gameIndex() {
        return gameIndex;
    }

    public Optional<DuelVote.Outcome> current() {
        return finished() ? Optional.empty() : Optional.of(schedule.get(gameIndex));
    }

    public boolean complete(DuelSeries expectedSeries, int expectedGameIndex, Optional<UUID> winner) {
        return this == expectedSeries && complete(expectedGameIndex, winner);
    }

    public boolean complete(int expectedGameIndex, Optional<UUID> winner) {
        if (finished() || expectedGameIndex != gameIndex
                || winner.filter(player -> !participants.contains(player)).isPresent()) {
            return false;
        }
        winner.ifPresent(player -> wins[participants.indexOf(player)]++);
        gameIndex++;
        return true;
    }

    public boolean finished() {
        return gameIndex == schedule.size();
    }

    public int wins(UUID participant) {
        int slot = participants.indexOf(participant);
        return slot < 0 ? 0 : wins[slot];
    }

    public Optional<UUID> winner() {
        if (!finished() || wins[0] == wins[1]) {
            return Optional.empty();
        }
        return Optional.of(participants.get(wins[0] > wins[1] ? 0 : 1));
    }
}
