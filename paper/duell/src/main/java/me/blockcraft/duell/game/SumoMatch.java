package me.blockcraft.duell.game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SumoMatch {

    public enum State { WAITING, ACTIVE, RESETTING, FINISHED }

    private final int target;
    private final int[] scores = new int[2];
    private List<UUID> players = List.of();
    private State state = State.WAITING;
    private UUID winner;

    public SumoMatch(int target) {
        if (target < 1) {
            throw new IllegalArgumentException("points-to-win must be positive");
        }
        this.target = target;
    }

    public boolean start(List<UUID> players) {
        if (state != State.WAITING || players == null || players.size() != 2
                || players.getFirst() == null || players.getLast() == null
                || players.getFirst().equals(players.getLast())) {
            return false;
        }
        this.players = List.copyOf(players);
        state = State.ACTIVE;
        return true;
    }

    public boolean fall(UUID player) {
        if (state != State.ACTIVE || player == null) {
            return false;
        }
        int slot = players.indexOf(player);
        if (slot < 0) {
            return false;
        }
        int opponent = 1 - slot;
        scores[opponent]++;
        if (scores[opponent] >= target) {
            winner = players.get(opponent);
            state = State.FINISHED;
        } else {
            state = State.RESETTING;
        }
        return true;
    }

    public void ready() {
        if (state == State.RESETTING) {
            state = State.ACTIVE;
        }
    }

    public int score(UUID player) {
        int slot = player == null ? -1 : players.indexOf(player);
        return slot < 0 ? 0 : scores[slot];
    }

    public Optional<UUID> winner() {
        return Optional.ofNullable(winner);
    }

    public State state() {
        return state;
    }
}
