package me.blockcraft.sternenfront.game;

import java.util.Optional;

/**
 * The three acts of a round, which only move forward: tickets running low or time passing start the next one, one
 * step per second, so an act passed quickly still gets its start.
 */
public final class RoundActs {

    public enum Act { SKIRMISH, ASSAULT, LAST_STAND }

    public record Rules(double assaultShare, int assaultSeconds, double lastStandShare, int lastStandSeconds,
                        int priorityBleed, int lastStandBleed, double comebackHeroShare, double stormSightShare,
                        double priorityPull, int dropshipDistance) { }

    private final Rules rules;
    private final int startTickets;
    private Act act = Act.SKIRMISH;

    public RoundActs(Rules rules, int startTickets) {
        this.rules = rules;
        this.startTickets = Math.max(1, startTickets);
    }

    public Act act() {
        return act;
    }

    /** One second of the round; the act that starts now, if any. */
    public Optional<Act> second(int lowestTickets, long elapsedSeconds) {
        var next = switch (act) {
            case SKIRMISH -> due(lowestTickets, elapsedSeconds, rules.assaultShare(), rules.assaultSeconds())
                    ? Optional.of(Act.ASSAULT) : Optional.<Act>empty();
            case ASSAULT -> due(lowestTickets, elapsedSeconds, rules.lastStandShare(), rules.lastStandSeconds())
                    ? Optional.of(Act.LAST_STAND) : Optional.<Act>empty();
            case LAST_STAND -> Optional.<Act>empty();
        };
        next.ifPresent(started -> act = started);
        return next;
    }

    private boolean due(int lowestTickets, long elapsedSeconds, double share, int seconds) {
        return (double) lowestTickets / startTickets <= share || elapsedSeconds >= seconds;
    }

    /** What one held post counts for when the bleed comes round. */
    public int bleedWeight(boolean priorityPost) {
        return switch (act) {
            case SKIRMISH -> 1;
            case ASSAULT -> priorityPost ? rules.priorityBleed() : 1;
            case LAST_STAND -> rules.lastStandBleed();
        };
    }
}
