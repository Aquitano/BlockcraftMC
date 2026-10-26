package me.blockcraft.zombies.game;

import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Rolls at most one {@link WaveEvent} per wave and runs it until it is over or the wave ends. */
final class WaveEvents {

    private final ZombiesGame game;
    private final ArenaEffects effects;
    private final ZombiesConfig.Events config;
    private WaveEvent.Kind pending;
    private double triggerShare;
    private WaveEvent running;

    WaveEvents(ZombiesGame game, ArenaEffects effects, ZombiesConfig.Events config) {
        this.game = game;
        this.effects = effects;
        this.config = config;
    }

    void begin(WavePlan plan) {
        end();
        var random = ThreadLocalRandom.current();
        int openRooms = (int) game.arena().rooms().stream().filter(room -> game.arena().unlocked(room.id())).count();
        var choices = config.choices(plan, game.powered(), openRooms);
        if (choices.isEmpty() || random.nextDouble() >= config.chance()) {
            return;
        }
        var kind = pick(choices);
        if (kind == WaveEvent.Kind.BLOOD_MOON) {
            start(kind);
        } else {
            pending = kind;
            triggerShare = config.latestShare() > config.earliestShare()
                    ? random.nextDouble(config.earliestShare(), config.latestShare()) : config.earliestShare();
        }
    }

    void tick(int tick) {
        if (pending != null && game.enemies().spawnedShare() >= triggerShare && !game.enemies().living().isEmpty()) {
            var kind = pending;
            pending = null;
            start(kind);
        }
        var current = running;
        if (current != null && !current.tick(tick) && running == current) {
            current.end();
            running = null;
            game.refreshSidebar();
        }
    }

    void killed(Mob mob, Player killer) {
        if (running != null) {
            running.killed(mob, killer);
        }
    }

    boolean holding(Player player) {
        return running != null && running.holding(player);
    }

    boolean bloodMoon() {
        return running instanceof BloodMoon;
    }

    boolean powerOut() {
        return running instanceof PowerOutage;
    }

    int killGold(int gold) {
        return running instanceof BloodMoon moon ? moon.killGold(gold) : gold;
    }

    Optional<GameSidebar.Section> section(Player viewer) {
        if (running == null) {
            return Optional.empty();
        }
        var messages = game.messages();
        return Optional.of(new GameSidebar.Section(messages.get("scoreboard.event", GameSidebar.icons(viewer)),
                List.of(messages.get("event." + running.kind().key() + ".name"), running.goal())));
    }

    void end() {
        pending = null;
        if (running != null) {
            running.end();
            running = null;
        }
    }

    private void start(WaveEvent.Kind kind) {
        running = switch (kind) {
            case BLOOD_MOON -> new BloodMoon(game, effects, config.bloodMoon());
            case POWER_OUTAGE -> new PowerOutage(game, effects, config.powerOutage());
            case BOUNTY -> new Bounty(game, config.bounty());
            case TOXIC_FOG -> new ToxicFog(game, config.toxicFog());
        };
        running.start();
        game.refreshSidebar();
    }

    private static WaveEvent.Kind pick(Map<WaveEvent.Kind, Double> choices) {
        double selection = ThreadLocalRandom.current().nextDouble(choices.values().stream().mapToDouble(Double::doubleValue).sum());
        for (var choice : choices.entrySet()) {
            selection -= choice.getValue();
            if (selection < 0) {
                return choice.getKey();
            }
        }
        return choices.keySet().iterator().next();
    }
}
