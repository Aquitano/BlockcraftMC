package me.blockcraft.ttt.task;

import me.blockcraft.ttt.arena.Chore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Who has which chores and which of them are done. The traitors' chores look the same and count for nothing. The
 * innocents are finished when every living innocent has done theirs; a dead innocent's open chores drop out, and
 * what anyone did before dying still counts.
 */
public final class TaskBoard {

    public static final class Duty {
        private final Chore chore;
        private boolean done;

        private Duty(Chore chore) {
            this.chore = chore;
        }

        public Chore chore() {
            return chore;
        }

        public boolean done() {
            return done;
        }
    }

    private final Map<UUID, List<Duty>> duties = new HashMap<>();
    private final Set<UUID> pretending = new HashSet<>();

    /** Gives each worker {@code perPlayer} different chores, or all of them if the map has fewer. */
    public void assign(Collection<UUID> workers, Set<UUID> traitors, List<Chore> chores, int perPlayer, RandomGenerator random) {
        for (var worker : workers) {
            var shuffled = new ArrayList<>(chores);
            Collections.shuffle(shuffled, random);
            duties.put(worker, shuffled.stream().limit(perPlayer).map(Duty::new).toList());
            if (traitors.contains(worker)) {
                pretending.add(worker);
            }
        }
    }

    public boolean assigned() {
        return !duties.isEmpty();
    }

    public List<Duty> of(UUID player) {
        return duties.getOrDefault(player, List.of());
    }

    public Optional<Duty> open(UUID player, Predicate<Chore> which) {
        return of(player).stream().filter(duty -> !duty.done && which.test(duty.chore)).findFirst();
    }

    /** Marks the duty done; true if it was a real chore rather than a traitor's show. */
    public boolean finish(UUID player, Duty duty) {
        duty.done = true;
        return !pretending.contains(player);
    }

    public boolean complete(Collection<UUID> living) {
        return done() > 0 && open(living) == 0;
    }

    /** How far the innocents are, from 0 to 1. */
    public float progress(Collection<UUID> living) {
        int done = done();
        int total = done + open(living);
        return total == 0 ? 0 : (float) done / total;
    }

    public void clear() {
        duties.clear();
        pretending.clear();
    }

    private int done() {
        return (int) duties.entrySet().stream().filter(entry -> !pretending.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream()).filter(duty -> duty.done).count();
    }

    private int open(Collection<UUID> living) {
        return (int) duties.entrySet().stream()
                .filter(entry -> !pretending.contains(entry.getKey()) && living.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream()).filter(duty -> !duty.done).count();
    }
}
