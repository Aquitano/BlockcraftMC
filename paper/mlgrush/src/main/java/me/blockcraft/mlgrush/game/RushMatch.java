package me.blockcraft.mlgrush.game;

import java.util.List;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RushMatch {

    public enum State { WAITING, ACTIVE, RESETTING, FINISHED, STOPPED }

    private final int target;
    private final int[] scores = new int[2];
    private List<UUID> players = List.of();
    private State state = State.WAITING;
    private UUID winner;
    private final Set<UUID> rematchRequests = new HashSet<>();

    public RushMatch(int target) {
        if (target < 1) {
            throw new IllegalArgumentException("score-target must be positive");
        }
        this.target = target;
    }

    public boolean start(List<UUID> players) {
        if (state != State.WAITING || players.size() != 2 || players.getFirst().equals(players.getLast())) {
            return false;
        }
        this.players = List.copyOf(players);
        state = State.ACTIVE;
        return true;
    }

    public boolean score(UUID player, int bedOwner) {
        int slot = slot(player);
        if (state != State.ACTIVE || slot < 0 || bedOwner < 0 || bedOwner > 1 || slot == bedOwner) {
            return false;
        }
        scores[slot]++;
        if (scores[slot] >= target) {
            winner = player;
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

    public boolean leave(UUID player) {
        int slot = slot(player);
        if (slot < 0 || (state != State.ACTIVE && state != State.RESETTING)) {
            return false;
        }
        winner = players.get(1 - slot);
        state = State.FINISHED;
        return true;
    }

    public int slot(UUID player) {
        return players.indexOf(player);
    }

    public boolean requestRematch(UUID player) {
        return state == State.FINISHED && slot(player) >= 0 && rematchRequests.add(player)
                && rematchRequests.size() == players.size();
    }

    public int score(int slot) {
        return scores[slot];
    }

    public State state() {
        return state;
    }

    public Optional<UUID> winner() {
        return Optional.ofNullable(winner);
    }

    public void stop() {
        state = State.STOPPED;
    }
}
