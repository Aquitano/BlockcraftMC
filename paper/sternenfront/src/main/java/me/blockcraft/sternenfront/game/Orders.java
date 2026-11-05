package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.game.RoundTally.Deed;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** A small goal for each fighter, one after another, so there is always something specific to go after. */
final class Orders {

    record Order(Deed deed, int count) {

        /** {@code "headshot:3"}, as config.yml writes them. */
        static Order parse(String text) {
            var parts = text.split(":");
            return new Order(Deed.valueOf(parts[0].strip().toUpperCase(Locale.ROOT)), Math.max(1, Integer.parseInt(parts[1].strip())));
        }
    }

    static final class Progress {
        private final List<Order> queue;
        private int index;
        private int done;

        private Progress(List<Order> queue) {
            this.queue = queue;
        }

        Order order() {
            return queue.get(index);
        }

        int done() {
            return done;
        }
    }

    private final List<Order> pool;
    private final int points;
    private final int coins;
    private final RandomGenerator random;
    private final Map<UUID, Progress> progress = new HashMap<>();

    Orders(List<Order> pool, int points, int coins, RandomGenerator random) {
        this.pool = List.copyOf(pool);
        this.points = points;
        this.coins = coins;
        this.random = random;
    }

    static Orders from(ConfigurationSection config, RandomGenerator random) {
        return new Orders(config.getStringList("orders.list").stream().map(Order::parse).toList(),
                Math.max(0, config.getInt("orders.points", 150)), Math.max(0, config.getInt("orders.coins", 3)), random);
    }

    int points() {
        return points;
    }

    int coins() {
        return coins;
    }

    /** Empty when config.yml lists no orders. */
    Optional<Progress> of(UUID fighter) {
        return pool.isEmpty() ? Optional.empty() : Optional.of(progress.computeIfAbsent(fighter, ignored -> {
            var queue = new ArrayList<>(pool);
            Collections.shuffle(queue, Random.from(random));
            return new Progress(queue);
        }));
    }

    /** Counts a deed towards the fighter's order; the order it just completed, if any, and the next one begins. */
    Optional<Order> did(UUID fighter, Deed deed) {
        return of(fighter).filter(current -> current.order().deed() == deed).flatMap(current -> {
            if (++current.done < current.order().count()) {
                return Optional.empty();
            }
            var completed = current.order();
            current.done = 0;
            current.index = (current.index + 1) % current.queue.size();
            return Optional.of(completed);
        });
    }
}
