package me.blockcraft.system.network;

import me.blockcraft.common.network.GroupState;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * When the game groups that run on demand start and stop, and who waits for them, without touching Docker or
 * players itself: every call returns the {@link Effect}s for {@link OnDemandServers} to carry out, in order. A group
 * that idles for {@code idleStop} drains and stops; a player who wants into a sleeping group starts it and waits;
 * whoever waited longer than {@code startTimeout} is told it failed. A group that runs but went down, as a game server
 * does to restart after every round, counts as starting, since Docker brings it back by itself.
 */
public final class OnDemand {

    /** {@code maxAwake} caps how many of the groups run or start at once; 0 sets no cap. */
    public record Settings(Set<String> groups, Duration idleStop, Duration startTimeout, int maxAwake) {
    }

    /** What the proxy sees of a group on a poll: whether a server of it runs, and whether all of them sit empty in their lobby. */
    public record Observation(boolean running, boolean idle) {

        static final Observation DOWN = new Observation(false, false);
    }

    /** Something {@link OnDemandServers} has to do. */
    public sealed interface Effect {

        record Start(String group) implements Effect {
        }

        record Stop(String group) implements Effect {
        }

        record Publish(String group, GroupState state) implements Effect {
        }

        /** The group's server is up: send these players, who waited for it, in the order they asked. */
        record Admit(String group, List<UUID> players) implements Effect {
        }

        /** The group's server did not come: tell these players. */
        record Fail(String group, List<UUID> players) implements Effect {
        }
    }

    /** How a request to play a group without room was taken. */
    public enum Reply {
        /** The request woke the group. */
        STARTING,
        /** The group was already on its way up, or on its way down and comes back for this player. */
        WAITING,
        /** Too many groups run already. */
        BUSY
    }

    /** A reply for the player and the effects that come with it. */
    public record Outcome(Reply reply, List<Effect> effects) {
    }

    private static final class Group {
        private GroupState state = GroupState.ASLEEP;
        private Instant since = Instant.EPOCH;
        private Optional<Instant> idleSince = Optional.empty();
        private boolean startIssued;
        private boolean published;
        private final LinkedHashSet<UUID> waiting = new LinkedHashSet<>();
        /** When the first player started waiting for an awake group's server to come back from its restart. */
        private Optional<Instant> rejoinedSince = Optional.empty();
    }

    private final Settings settings;
    private final Map<String, Group> groups = new LinkedHashMap<>();

    public OnDemand(Settings settings) {
        this.settings = settings;
        settings.groups().forEach(group -> groups.put(group, new Group()));
    }

    public boolean manages(String group) {
        return groups.containsKey(group);
    }

    public Set<String> groups() {
        return groups.keySet();
    }

    /**
     * A player wants into {@code group}, which has no room for them. Empty if on-demand cannot help: the group runs,
     * and is only full or playing a round.
     */
    public synchronized Optional<Outcome> request(String group, UUID player, Instant now) {
        select(group, player);
        var entry = groups.get(group);
        if (entry == null) {
            return Optional.empty();
        }
        return switch (entry.state) {
            case AWAKE -> Optional.empty();
            case STARTING -> {
                entry.waiting.add(player);
                var effects = new ArrayList<Effect>();
                if (!entry.startIssued) {
                    entry.startIssued = true;
                    effects.add(new Effect.Start(group));
                }
                yield Optional.of(new Outcome(Reply.WAITING, effects));
            }
            case DRAINING -> {
                entry.waiting.add(player);
                yield Optional.of(new Outcome(Reply.WAITING, List.of()));
            }
            case ASLEEP -> {
                if (settings.maxAwake() > 0 && awake() >= settings.maxAwake()) {
                    yield Optional.of(new Outcome(Reply.BUSY, List.of()));
                }
                entry.waiting.add(player);
                yield Optional.of(new Outcome(Reply.STARTING, wake(group, entry, now)));
            }
        };
    }

    /**
     * As {@link #request}, for a player who just played the group's only server and wants another round there: an awake
     * group, whose server restarts after every round, takes them too and sends them once it is back.
     */
    public synchronized Optional<Outcome> rejoin(String group, UUID player, Instant now) {
        select(group, player);
        var entry = groups.get(group);
        if (entry != null && entry.state == GroupState.AWAKE) {
            entry.waiting.add(player);
            entry.rejoinedSince = Optional.of(entry.rejoinedSince.orElse(now));
            return Optional.of(new Outcome(Reply.WAITING, List.of()));
        }
        return request(group, player, now);
    }

    public synchronized void cancel(UUID player) {
        groups.values().forEach(entry -> remove(entry, player));
    }

    private void select(String group, UUID player) {
        groups.forEach((name, entry) -> {
            if (!name.equals(group)) {
                remove(entry, player);
            }
        });
    }

    private static void remove(Group entry, UUID player) {
        entry.waiting.remove(player);
        if (entry.waiting.isEmpty()) {
            entry.rejoinedSince = Optional.empty();
        }
    }

    /** One poll of the servers; a group missing from {@code seen} has no server running. */
    public synchronized List<Effect> observe(Map<String, Observation> seen, Instant now) {
        var effects = new ArrayList<Effect>();
        groups.forEach((group, entry) -> {
            var observation = seen.getOrDefault(group, Observation.DOWN);
            switch (entry.state) {
                case ASLEEP -> {
                    if (observation.running()) {
                        awaken(group, entry, now, effects);
                    }
                }
                case STARTING -> {
                    if (observation.running()) {
                        awaken(group, entry, now, effects);
                    } else if (!now.isBefore(entry.since.plus(settings.startTimeout()))) {
                        move(group, entry, GroupState.ASLEEP, now, effects);
                        fail(group, entry, effects);
                    }
                }
                case AWAKE -> {
                    if (!observation.running()) {
                        move(group, entry, GroupState.STARTING, now, effects);
                        entry.startIssued = false;
                        entry.rejoinedSince = Optional.empty();
                        break;
                    }
                    if (!entry.waiting.isEmpty() && observation.idle()) {
                        // Back from a restart the poll did not see, or another server of the group has room.
                        entry.rejoinedSince = Optional.empty();
                        effects.add(new Effect.Admit(group, drain(entry.waiting)));
                    } else if (entry.rejoinedSince.filter(since -> !now.isBefore(since.plus(settings.startTimeout()))).isPresent()) {
                        entry.rejoinedSince = Optional.empty();
                        fail(group, entry, effects);
                    }
                    if (!observation.idle()) {
                        entry.idleSince = Optional.empty();
                    } else {
                        var idleSince = entry.idleSince.orElse(now);
                        entry.idleSince = Optional.of(idleSince);
                        if (!now.isBefore(idleSince.plus(settings.idleStop()))) {
                            move(group, entry, GroupState.DRAINING, now, effects);
                            effects.add(new Effect.Stop(group));
                        }
                    }
                }
                case DRAINING -> {
                    // Only the stop's answer ends a drain: a server leaves the directory before its container exits,
                    // and a start sent in between would find it still running.
                }
            }
            if (!entry.published) {
                entry.published = true;
                effects.add(new Effect.Publish(group, entry.state));
            }
        });
        return effects;
    }

    /** The stop of a draining group came back; a failed one leaves the group awake, to try again after another idle spell. */
    public synchronized List<Effect> stopped(String group, boolean stopped, Instant now) {
        var entry = groups.get(group);
        if (entry == null || entry.state != GroupState.DRAINING) {
            return List.of();
        }
        var effects = new ArrayList<Effect>();
        if (!stopped) {
            move(group, entry, GroupState.AWAKE, now, effects);
            entry.idleSince = Optional.of(now);
            if (!entry.waiting.isEmpty()) {
                effects.add(new Effect.Admit(group, drain(entry.waiting)));
            }
            return effects;
        }
        move(group, entry, GroupState.ASLEEP, now, effects);
        if (!entry.waiting.isEmpty()) {
            effects.addAll(wake(group, entry, now));
        }
        return effects;
    }

    /** The waker could not start the group's server. */
    public synchronized List<Effect> startFailed(String group, Instant now) {
        var entry = groups.get(group);
        if (entry == null || entry.state != GroupState.STARTING) {
            return List.of();
        }
        var effects = new ArrayList<Effect>();
        move(group, entry, GroupState.ASLEEP, now, effects);
        fail(group, entry, effects);
        return effects;
    }

    private static void fail(String group, Group entry, List<Effect> effects) {
        if (!entry.waiting.isEmpty()) {
            effects.add(new Effect.Fail(group, drain(entry.waiting)));
        }
    }

    private List<Effect> wake(String group, Group entry, Instant now) {
        var effects = new ArrayList<Effect>();
        move(group, entry, GroupState.STARTING, now, effects);
        entry.startIssued = true;
        effects.add(new Effect.Start(group));
        return effects;
    }

    private void awaken(String group, Group entry, Instant now, List<Effect> effects) {
        move(group, entry, GroupState.AWAKE, now, effects);
        entry.idleSince = Optional.empty();
        if (!entry.waiting.isEmpty()) {
            effects.add(new Effect.Admit(group, drain(entry.waiting)));
        }
    }

    private void move(String group, Group entry, GroupState state, Instant now, List<Effect> effects) {
        entry.state = state;
        entry.since = now;
        entry.published = true;
        effects.add(new Effect.Publish(group, state));
    }

    private int awake() {
        return (int) groups.values().stream().filter(entry -> entry.state != GroupState.ASLEEP).count();
    }

    private static List<UUID> drain(LinkedHashSet<UUID> waiting) {
        var players = List.copyOf(waiting);
        waiting.clear();
        return players;
    }
}
