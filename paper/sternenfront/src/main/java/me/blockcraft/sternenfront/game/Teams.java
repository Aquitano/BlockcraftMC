package me.blockcraft.sternenfront.game;

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
 * Who fights for which faction, in the order they joined. Chat reads it off the main thread, so lookups are
 * safe there; changes happen on the main thread only.
 */
public final class Teams {

    public enum Joined { JOINED, ALREADY, FULL }

    private final int capacity;
    private final Map<UUID, Faction> factions = new ConcurrentHashMap<>();
    private final Map<Faction, SequencedSet<UUID>> members = new EnumMap<>(Faction.class);

    public Teams(int capacity) {
        this.capacity = capacity;
        for (var faction : Faction.values()) {
            members.put(faction, new LinkedHashSet<>());
        }
    }

    public Optional<Faction> of(UUID player) {
        return Optional.ofNullable(factions.get(player));
    }

    public List<UUID> members(Faction faction) {
        return List.copyOf(members.get(faction));
    }

    public int size(Faction faction) {
        return members.get(faction).size();
    }

    public boolean allies(UUID one, UUID other) {
        var faction = factions.get(one);
        return faction != null && faction == factions.get(other);
    }

    public Joined join(UUID player, Faction faction) {
        if (factions.get(player) == faction) {
            return Joined.ALREADY;
        }
        if (size(faction) >= capacity) {
            return Joined.FULL;
        }
        leave(player);
        factions.put(player, faction);
        members.get(faction).add(player);
        return Joined.JOINED;
    }

    public Optional<Faction> leave(UUID player) {
        var faction = Optional.ofNullable(factions.remove(player));
        faction.ifPresent(left -> members.get(left).remove(player));
        return faction;
    }

    /**
     * Seats everyone who chose no team on the smaller faction, then, if one faction is still empty while the other has
     * two or more, moves the latest half of them over, so that every round has an opponent.
     */
    public Map<UUID, Faction> assignRest(List<UUID> players) {
        var assigned = new LinkedHashMap<UUID, Faction>();
        for (var player : players) {
            if (factions.containsKey(player)) {
                continue;
            }
            var faction = smallest();
            if (size(faction) >= capacity) {
                break;
            }
            join(player, faction);
            assigned.put(player, faction);
        }
        for (var faction : Faction.values()) {
            var crowd = new ArrayList<>(members.get(faction));
            if (size(faction.enemy()) == 0 && crowd.size() >= 2) {
                crowd.subList(crowd.size() - crowd.size() / 2, crowd.size()).forEach(player -> {
                    join(player, faction.enemy());
                    assigned.put(player, faction.enemy());
                });
            }
        }
        return assigned;
    }

    private Faction smallest() {
        return Arrays.stream(Faction.values()).min(Comparator.comparingInt(this::size)).orElseThrow();
    }
}
