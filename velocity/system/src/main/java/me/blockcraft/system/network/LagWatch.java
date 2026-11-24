package me.blockcraft.system.network;

import me.blockcraft.common.network.ServerRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A server lags once its milliseconds per tick stayed above the limit for a number of polls in a row, and stops
 * lagging with the first poll below it. Changes stay pending until they are marked as announced.
 */
final class LagWatch {

    record Change(ServerRecord server, boolean lagging) {
    }

    private final double msptLimit;
    private final int polls;
    private final Map<String, Integer> slowPolls = new HashMap<>();
    private final Set<String> announcedLagging = new HashSet<>();

    LagWatch(double msptLimit, int polls) {
        this.msptLimit = msptLimit;
        this.polls = polls;
    }

    /** Servers that went away are forgotten without a recovery, their heartbeat is gone and not their lag. */
    List<Change> poll(List<ServerRecord> alive) {
        var names = alive.stream().map(ServerRecord::name).collect(Collectors.toSet());
        slowPolls.keySet().retainAll(names);
        announcedLagging.retainAll(names);
        var changes = new ArrayList<Change>();
        for (var server : alive) {
            boolean slow = server.status().mspt() > msptLimit;
            if (slow) {
                slowPolls.merge(server.name(), 1, Integer::sum);
            } else {
                slowPolls.remove(server.name());
            }
            boolean lagging = slowPolls.getOrDefault(server.name(), 0) >= polls;
            if (lagging != announcedLagging.contains(server.name())) {
                changes.add(new Change(server, lagging));
            }
        }
        return changes;
    }

    void announced(List<Change> changes) {
        for (var change : changes) {
            if (change.lagging()) {
                announcedLagging.add(change.server().name());
            } else {
                announcedLagging.remove(change.server().name());
            }
        }
    }
}
