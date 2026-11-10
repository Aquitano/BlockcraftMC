package me.blockcraft.sternenfront.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** What each fighter did this round: the source of the awards and their recap. */
final class RoundTally {

    private static final int FIGHTERS_FOR_AWARDS = 2;
    private static final int HEADSHOTS_FOR_AWARD = 3;
    private static final int CAPTURES_FOR_AWARD = 2;
    private static final int ASSISTS_FOR_AWARD = 3;

    enum Deed { KILL, HEADSHOT, ASSIST, DEFENCE, CAPTURE }

    record Award(String key, UUID winner, int value) { }

    static final class Line {
        private final Map<Deed, Integer> deeds = new EnumMap<>(Deed.class);
        private int points;

        int points() {
            return points;
        }

        int count(Deed deed) {
            return deeds.getOrDefault(deed, 0);
        }
    }

    private final Map<UUID, Line> lines = new HashMap<>();

    private Line line(UUID fighter) {
        return lines.computeIfAbsent(fighter, ignored -> new Line());
    }

    void earned(UUID fighter, int points) {
        line(fighter).points += points;
    }

    void did(UUID fighter, Deed deed) {
        line(fighter).deeds.merge(deed, 1, Integer::sum);
    }

    Map<UUID, Line> lines() {
        return lines;
    }

    /** A bonus in proportion to what a kill pays against a player's kill, and never more than the bonus: a bot's kill pays less. */
    static int bonus(int bonus, int killPoints, int playerKillPoints) {
        return (int) Math.min(bonus, (long) bonus * killPoints / Math.max(1, playerKillPoints));
    }

    /** Nothing to compare with one fighter, whose recap already holds their numbers. */
    List<Award> awards() {
        if (lines.size() < FIGHTERS_FOR_AWARDS) {
            return List.of();
        }
        var awards = new ArrayList<Award>();
        most("mvp", Line::points, 1).ifPresent(awards::add);
        most("sharpshooter", line -> line.count(Deed.HEADSHOT), HEADSHOTS_FOR_AWARD).ifPresent(awards::add);
        most("conqueror", line -> line.count(Deed.CAPTURE), CAPTURES_FOR_AWARD).ifPresent(awards::add);
        most("comrade", line -> line.count(Deed.ASSIST), ASSISTS_FOR_AWARD).ifPresent(awards::add);
        return awards;
    }

    private Optional<Award> most(String key, ToIntFunction<Line> value, int atLeast) {
        return lines.entrySet().stream()
                .filter(entry -> value.applyAsInt(entry.getValue()) >= atLeast)
                .max(Comparator.<Map.Entry<UUID, Line>>comparingInt(entry -> value.applyAsInt(entry.getValue()))
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new Award(key, entry.getKey(), value.applyAsInt(entry.getValue())));
    }
}
