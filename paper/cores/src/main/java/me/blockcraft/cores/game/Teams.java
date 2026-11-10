package me.blockcraft.cores.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who plays for red and who for blue, in the order they joined. Chat reads it off the main thread, so lookups are
 * safe there; changes happen on the main thread only.
 */
public final class Teams {

    public enum Joined { JOINED, ALREADY, FULL }

    private final int capacity;
    private final Map<UUID, Side> sides = new ConcurrentHashMap<>();
    private final Map<Side, SequencedSet<UUID>> members = new EnumMap<>(Side.class);

    public Teams(int capacity) {
        this.capacity = capacity;
        for (var side : Side.values()) {
            members.put(side, new LinkedHashSet<>());
        }
    }

    public Optional<Side> of(UUID player) {
        return Optional.ofNullable(sides.get(player));
    }

    public List<UUID> members(Side side) {
        return List.copyOf(members.get(side));
    }

    public int size(Side side) {
        return members.get(side).size();
    }

    public boolean sameSide(UUID one, UUID other) {
        var side = sides.get(one);
        return side != null && side == sides.get(other);
    }

    public Joined join(UUID player, Side side) {
        if (sides.get(player) == side) {
            return Joined.ALREADY;
        }
        if (size(side) >= capacity) {
            return Joined.FULL;
        }
        leave(player);
        sides.put(player, side);
        members.get(side).add(player);
        return Joined.JOINED;
    }

    public Optional<Side> leave(UUID player) {
        var side = Optional.ofNullable(sides.remove(player));
        side.ifPresent(left -> members.get(left).remove(player));
        return side;
    }

    /**
     * Seats everyone who chose no team on the smaller side, then, if one side is still empty while the other has
     * two or more, moves the latest half of them over, so that every round has an opponent.
     */
    public Map<UUID, Side> assignRest(List<UUID> players) {
        var assigned = new LinkedHashMap<UUID, Side>();
        for (var player : players) {
            if (sides.containsKey(player)) {
                continue;
            }
            var side = smallest();
            if (size(side) >= capacity) {
                break;
            }
            join(player, side);
            assigned.put(player, side);
        }
        for (var side : Side.values()) {
            var crowd = new ArrayList<>(members.get(side));
            if (size(side.enemy()) == 0 && crowd.size() >= 2) {
                crowd.subList(crowd.size() - crowd.size() / 2, crowd.size()).forEach(player -> {
                    join(player, side.enemy());
                    assigned.put(player, side.enemy());
                });
            }
        }
        return assigned;
    }

    private Side smallest() {
        return Arrays.stream(Side.values()).min(Comparator.comparingInt(this::size)).orElseThrow();
    }
}
