package me.blockcraft.ttt.task;

import me.blockcraft.ttt.arena.Chore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Who has which chores and which of them are done; the traitors have none. The innocents are finished when every
 * living innocent has done theirs; a dead innocent's open chores drop out, and what anyone did before dying still
 * counts.
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

    private static final int SPREAD_ATTEMPTS = 12;

    private final Map<UUID, List<Duty>> duties = new HashMap<>();

    /**
     * Gives each worker {@code perPlayer} different chores, or all of them if the map has fewer, with as few of them
     * {@code close} to another as a few random tries find. One greedy pass alone can open with a chore close to all
     * the others and miss a spread that exists.
     */
    public void assign(Collection<UUID> workers, List<Chore> chores, int perPlayer, BiPredicate<Chore, Chore> close,
                       RandomGenerator random) {
        for (var worker : workers) {
            List<Chore> best = List.of();
            long bestClose = Long.MAX_VALUE;
            for (int attempt = 0; attempt < SPREAD_ATTEMPTS && bestClose > 0; attempt++) {
                var picked = pick(chores, perPlayer, close, random);
                long closePairs = closePairs(picked, close);
                if (closePairs < bestClose) {
                    best = picked;
                    bestClose = closePairs;
                }
            }
            duties.put(worker, best.stream().map(Duty::new).toList());
        }
    }

    private static List<Chore> pick(List<Chore> chores, int perPlayer, BiPredicate<Chore, Chore> close, RandomGenerator random) {
        var shuffled = new ArrayList<>(chores);
        Collections.shuffle(shuffled, random);
        var picked = new ArrayList<Chore>();
        for (var chore : shuffled) {
            if (picked.size() < perPlayer && picked.stream().noneMatch(other -> close.test(chore, other))) {
                picked.add(chore);
            }
        }
        shuffled.stream().filter(chore -> !picked.contains(chore)).limit(Math.max(0, perPlayer - picked.size()))
                .forEach(picked::add);
        return picked;
    }

    private static long closePairs(List<Chore> picked, BiPredicate<Chore, Chore> close) {
        long pairs = 0;
        for (int one = 0; one < picked.size(); one++) {
            for (int other = one + 1; other < picked.size(); other++) {
                if (close.test(picked.get(one), picked.get(other))) {
                    pairs++;
                }
            }
        }
        return pairs;
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

    public void finish(Duty duty) {
        duty.done = true;
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
    }

    private int done() {
        return (int) duties.values().stream().flatMap(List::stream).filter(duty -> duty.done).count();
    }

    private int open(Collection<UUID> living) {
        return (int) duties.entrySet().stream().filter(entry -> living.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream()).filter(duty -> !duty.done).count();
    }
}
