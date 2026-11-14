package me.blockcraft.story.state;

import me.blockcraft.story.debug.Trace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What the party has done and decided so far, as named facts every member shares: {@code stage} is where the party
 * stands, a vote's topic holds what the party decided, and the quest package keeps whatever else it needs. Values are
 * text; a comparison treats two numbers as numbers.
 */
public final class Facts {

    public static final String STAGE = "stage";
    public static final String CHAPTER = "chapter";
    /** What a condition expects: a value, or a value after a comparison such as {@code >=3}, or {@code *} for "is set". */
    private static final Pattern EXPECTED = Pattern.compile("(<=|>=|!=|<|>|=)?(.*)");

    public enum Comparison {
        EQUAL("="), NOT_EQUAL("!="), LESS("<"), AT_MOST("<="), MORE(">"), AT_LEAST(">=");

        private final String symbol;

        Comparison(String symbol) {
            this.symbol = symbol;
        }

        public static Optional<Comparison> of(String symbol) {
            for (var comparison : values()) {
                if (comparison.symbol.equals(symbol)) return Optional.of(comparison);
            }
            return Optional.empty();
        }

        boolean holds(int order) {
            return switch (this) {
                case EQUAL -> order == 0;
                case NOT_EQUAL -> order != 0;
                case LESS -> order < 0;
                case AT_MOST -> order <= 0;
                case MORE -> order > 0;
                case AT_LEAST -> order >= 0;
            };
        }
    }

    private final Map<String, String> values = new LinkedHashMap<>();
    private final Trace trace;

    public Facts(Trace trace) {
        this.trace = trace;
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    public void set(String name, String value, String cause) {
        var before = values.put(name, value);
        if (!value.equals(before)) trace.event("fact", name + " = " + value + (before == null ? "" : " (was " + before + ")") + " by " + cause);
    }

    /** Adds {@code amount} to a number, counting a fact that is not set yet as zero. */
    public void add(String name, long amount, String cause) {
        set(name, String.valueOf(number(values.get(name)).orElse(0L) + amount), cause);
    }

    public void clear(String name, String cause) {
        if (values.remove(name) != null) trace.event("fact", name + " cleared by " + cause);
    }

    public void clearAll(String cause) {
        if (values.isEmpty()) return;
        values.clear();
        trace.event("fact", "all cleared by " + cause);
    }

    /** Whether the fact is as {@code expected} says: {@code fabrik}, {@code !=fabrik}, {@code >=3} or {@code *}. */
    public boolean test(String name, String expected) {
        if (expected.equals("*")) return values.containsKey(name);
        var parts = EXPECTED.matcher(expected);
        if (!parts.matches()) return false;
        var comparison = parts.group(1) == null ? Comparison.EQUAL : Comparison.of(parts.group(1)).orElseThrow();
        return test(name, comparison, parts.group(2));
    }

    /** Whether the fact compares to {@code value} as asked; a fact that is not set only differs from everything. */
    public boolean test(String name, Comparison comparison, String value) {
        var actual = values.get(name);
        if (actual == null) return comparison == Comparison.NOT_EQUAL;
        var left = number(actual);
        var right = number(value);
        int order = left.isPresent() && right.isPresent() ? Long.compare(left.get(), right.get()) : actual.compareTo(value);
        return comparison.holds(order);
    }

    public Map<String, String> all() {
        return Map.copyOf(values);
    }

    /** In the order the facts were first set, for a readable overview. */
    public Map<String, String> ordered() {
        return new LinkedHashMap<>(values);
    }

    private static Optional<Long> number(String text) {
        if (text == null) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(text.trim()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
