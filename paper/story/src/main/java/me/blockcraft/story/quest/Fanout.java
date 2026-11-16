package me.blockcraft.story.quest;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Where the engine runs one trigger for every member, as {@code stage_<stage>} and a held spot are, each party
 * action in it acts once for the party: for the first member it runs for, and never again for that trigger until the
 * party enters another stage, so a member who rejoins does not run it a second time. A lasting trigger, a held spot,
 * keeps what acted until a jump, as a member who was away completes it later. BetonQuest runs a {@code folder} without
 * a delay at once, so its actions count; a delayed one runs outside the trigger and acts every time. Each configured
 * or inline action counts on its own, by the instance BetonQuest made of it.
 */
final class Fanout {

    /** Stands for a run that is the party's own, in which everything acts. */
    private static final String PARTY = "";

    private final Map<String, Set<Object>> ran = new HashMap<>();
    private final Set<String> lasting = new HashSet<>();
    private final Deque<String> running = new ArrayDeque<>();

    void across(String trigger, Runnable work) {
        running.push(trigger);
        try {
            work.run();
        } finally {
            running.pop();
        }
    }

    void acrossLasting(String trigger, Runnable work) {
        lasting.add(trigger);
        across(trigger, work);
    }

    /** Runs {@code work} once for the party, as {@code enter_<stage>} is, also from within a member's trigger. */
    void forParty(Runnable work) {
        across(PARTY, work);
    }

    /** Whether the party action {@code action} may act now: always outside a trigger, the first time within one. */
    boolean first(Object action) {
        var trigger = running.peek();
        return trigger == null || trigger.equals(PARTY) || ran.computeIfAbsent(trigger, ignored -> new HashSet<>()).add(action);
    }

    @Nullable String trigger() {
        return running.peek();
    }

    /** Forgets what acted, except in a trigger still running or a lasting one: a stage entered from its own trigger keeps it once. */
    void reset() {
        ran.keySet().removeIf(trigger -> !running.contains(trigger) && !lasting.contains(trigger));
    }

    /** Forgets everything, lasting triggers too, as a jump puts the party somewhere new. */
    void clear() {
        lasting.clear();
        reset();
    }
}
