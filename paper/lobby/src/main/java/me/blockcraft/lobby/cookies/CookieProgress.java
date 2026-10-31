package me.blockcraft.lobby.cookies;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CookieProgress {

    public record Upgrade(long cost, long perClick) { }

    public record Rules(long maxCookies, long clickNanos, long saveTicks, long shutdownSaveSeconds, List<Upgrade> upgrades) {
        public Rules {
            upgrades = List.copyOf(upgrades);
        }

        public static Rules fromYaml(Map<String, Object> config) {
            long cap = number(config, "max-cookies", 1_000_000, 1, 1_000_000_000);
            var upgrades = config.get("upgrades") instanceof List<?> entries ? entries.stream()
                    .filter(Map.class::isInstance).map(entry -> (Map<?, ?>) entry).limit(16)
                    .map(entry -> new Upgrade(number(entry, "cost", 50, 1, cap),
                            number(entry, "per-click", 2, 1, cap))).toList()
                    : List.of(new Upgrade(50, 2), new Upgrade(200, 5), new Upgrade(1000, 10));
            return new Rules(cap, 1_000_000_000 / number(config, "clicks-per-second", 5, 1, 20),
                    number(config, "save-seconds", 30, 5, 300) * 20,
                    number(config, "shutdown-save-seconds", 5, 1, 30), upgrades);
        }

        private static long number(Map<?, ?> config, String key, long fallback, long min, long max) {
            return Math.clamp(config.get(key) instanceof Number value ? value.longValue() : fallback, min, max);
        }
    }

    public record Snapshot(long cookies, int level) { }

    private final Rules rules;
    private long cookies;
    private int level;
    private long lastClick;
    private boolean clicked;

    public CookieProgress(Rules rules, Snapshot saved) {
        this.rules = rules;
        cookies = Math.clamp(saved.cookies(), 0, rules.maxCookies());
        level = Math.clamp(saved.level(), 0, rules.upgrades().size());
    }

    public Snapshot snapshot() {
        return new Snapshot(cookies, level);
    }

    public long perClick() {
        return level == 0 ? 1 : rules.upgrades().get(level - 1).perClick();
    }

    public Optional<Upgrade> next() {
        return level < rules.upgrades().size() ? Optional.of(rules.upgrades().get(level)) : Optional.empty();
    }

    public boolean click(long now) {
        if (cookies >= rules.maxCookies() || clicked && now - lastClick < rules.clickNanos()) {
            return false;
        }
        clicked = true;
        lastClick = now;
        cookies += Math.min(perClick(), rules.maxCookies() - cookies);
        return true;
    }

    public boolean upgrade() {
        var next = next();
        if (next.isEmpty() || cookies < next.get().cost()) {
            return false;
        }
        cookies -= next.get().cost();
        level++;
        return true;
    }
}
