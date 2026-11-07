package me.blockcraft.sternenfront.reinforcement;

import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.hero.BattlePoints;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class ReinforcementRoster {

    public enum Deployment { DEPLOYED, UNAVAILABLE, FAILED }

    private final ReinforcementConfig config;
    private final BattlePoints points;
    private final Map<UUID, Reinforcement> active = new HashMap<>();

    public ReinforcementRoster(ReinforcementConfig config, BattlePoints points) {
        this.config = config;
        this.points = points;
    }

    public long count(Reinforcement unit) {
        return active.values().stream().filter(unit::equals).count();
    }

    public boolean open(Faction faction, Reinforcement unit) {
        return unit.faction() == faction && count(unit) < config.stats(unit).limit();
    }

    public boolean affordable(UUID player, Faction faction, Reinforcement unit) {
        return !active.containsKey(player) && open(faction, unit) && points.of(player) >= config.stats(unit).cost();
    }

    // Called on the server thread; reserve before Bukkit callbacks can re-enter deployment.
    public Deployment deploy(UUID player, Faction faction, Reinforcement unit, BooleanSupplier deploy) {
        if (!affordable(player, faction, unit)) {
            return Deployment.UNAVAILABLE;
        }
        int cost = config.stats(unit).cost();
        if (!points.spend(player, cost)) {
            return Deployment.UNAVAILABLE;
        }
        active.put(player, unit);
        boolean deployed = false;
        try {
            deployed = deploy.getAsBoolean();
            return deployed ? Deployment.DEPLOYED : Deployment.FAILED;
        } finally {
            if (!deployed) {
                active.remove(player);
                points.earn(player, cost);
            }
        }
    }

    public void discharge(UUID player) {
        active.remove(player);
    }

    public void clear() {
        active.clear();
    }
}
