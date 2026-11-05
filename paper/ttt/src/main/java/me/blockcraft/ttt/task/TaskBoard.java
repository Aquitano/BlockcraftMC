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
    private final Map<UUID, Integer> contributions = new HashMap<>();

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

    public List<Chore> available(UUID player) {
        if (!duties.containsKey(player)) {
            return List.of();
        }
        return duties.values().stream().flatMap(List::stream).filter(duty -> !duty.done)
                .map(Duty::chore).distinct().toList();
    }

    public Optional<Duty> open(UUID player, Predicate<Chore> which) {
        if (!duties.containsKey(player)) {
            return Optional.empty();
        }
        Predicate<Duty> matches = duty -> !duty.done && which.test(duty.chore);
        return of(player).stream().filter(matches).findFirst()
                .or(() -> duties.values().stream().flatMap(List::stream).filter(matches).findFirst());
    }

    public boolean finish(UUID player, Chore chore) {
        var duty = open(player, chore::equals);
        if (duty.isEmpty()) {
            return false;
        }
        duty.get().done = true;
        contributions.merge(player, 1, Integer::sum);
        return true;
    }

    public int contribution(UUID player) {
        return contributions.getOrDefault(player, 0);
    }

    public int remaining(Chore chore) {
        return (int) duties.values().stream().flatMap(List::stream)
                .filter(duty -> !duty.done && duty.chore.equals(chore)).count();
    }

    public boolean complete() {
        return assigned() && total() > 0 && done() == total();
    }

    public float progress() {
        int total = total();
        return total == 0 ? 0 : (float) done() / total;
    }

    private int done() {
        return (int) duties.values().stream().flatMap(List::stream).filter(duty -> duty.done).count();
    }

    private int total() {
        return duties.values().stream().mapToInt(List::size).sum();
    }

    public void clear() {
        duties.clear();
        contributions.clear();
    }
}
