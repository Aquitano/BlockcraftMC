package me.blockcraft.lobby.cookies;

import me.blockcraft.common.config.StrictConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CookieProgress {

    public record Upgrade(long cost, long perClick) { }

    public record Rules(long maxCookies, long clickNanos, long saveTicks, long shutdownSaveSeconds, List<Upgrade> upgrades) {
        public Rules {
            upgrades = List.copyOf(upgrades);
        }

        /** Each of {@code upgrades} costs {@code cost} cookies and pays {@code per-click} from then on. */
        public static Rules from(StrictConfig config) {
            long cap = Math.clamp(config.integer("max-cookies"), 1, 1_000_000_000);
            if (!(config.values().get("upgrades") instanceof List<?> entries)) {
                throw new IllegalStateException(config.file() + ": " + config.path() + ".upgrades must be a list");
            }
            var upgrades = entries.stream().filter(Map.class::isInstance).map(entry -> (Map<?, ?>) entry).limit(16)
                    .map(entry -> new Upgrade(Math.clamp(whole(entry, "cost"), 1, cap), Math.clamp(whole(entry, "per-click"), 1, cap)))
                    .toList();
            return new Rules(cap, 1_000_000_000 / Math.clamp(config.integer("clicks-per-second"), 1, 20),
                    Math.clamp(config.integer("save-seconds"), 5, 300) * 20L,
                    Math.clamp(config.integer("shutdown-save-seconds"), 1, 30), upgrades);
        }

        private static long whole(Map<?, ?> upgrade, String key) {
            if (upgrade.get(key) instanceof Number value) {
                return value.longValue();
            }
            throw new IllegalStateException("Every cookies.upgrades entry needs a whole " + key);
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
