package me.blockcraft.zombies.config;

import me.blockcraft.zombies.game.Perk;
import me.blockcraft.zombies.game.EnemyKind;
import me.blockcraft.zombies.game.Weapon;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

public record ZombiesConfig(int maxPlayers, int startingGold, int intermissionSeconds, int waveSeconds,
                            int bleedTicks, int reviveTicks, double reviveRadius, int protectionTicks,
                            int hitGold, int killGold, int repairGold, int repairLimit, int clearGold,
                            int repairTicks, int breakTicks, int killCoins, int waveCoins, int winCoins,
                            int ammoPrice, int armorPrice, int upgradePrice, int mysteryPrice,
                            int powerPrice, int teamRevivePrice, double dropChance, int powerupTicks, int powerupsPerWave,
                            int noteCoins, int healthRegenTicks, double extraHealth, double headshotMultiplier, double knifeDamage, int damageImmunityTicks,
                            Waves waves, Map<Weapon, Gun> guns, Map<Perk, Integer> perks, Map<EnemyKind, Enemy> enemies, Exploder exploder) {

    /** The last {@code stragglers} enemies of a wave move at least at {@code stragglerSpeed}. */
    public record Waves(int total, int baseEnemies, int enemiesPerWave, double extraPlayer, double healthPerWave,
                        int spawnTicks, double spawnTicksPerWave, int minimumSpawnTicks, int maxAlive, int bossEvery,
                        Late late, Hounds hounds, int stragglers, double stragglerSpeed) { }

    /** Every wave after {@code from} adds these on top of the per-wave growth. */
    public record Late(int from, double healthPerWave, double healthPerExtraPlayer, double spawnTicksPerWave) { }

    /** From wave {@code first}, every {@code every}th wave that is no boss wave sends only hellhounds, {@code share} as many. */
    public record Hounds(int first, int every, double share) { }

    /** {@code burnDamage} per second for {@code burnTicks} after a hit; 0 for a gun that does not set fire. */
    public record Gun(int price, double damage, double range, int pellets, double spread,
                      int clip, int reserve, int cooldownTicks, int reloadTicks, double upgradeMultiplier,
                      double burnDamage, int burnTicks) { }

    /**
     * In mixed waves the kind's {@code weight} at its {@code firstWave} moves evenly to {@code lateWeight} at the final
     * wave. {@code slowTicks} is how long its touch slows a player; 0 for a kind that does not slow.
     */
    public record Enemy(int firstWave, double health, double damage, double speed, double weight, double lateWeight,
                        int slowTicks) {

        public double weightAt(int wave, int finalWave) {
            if (wave < firstWave) {
                return 0;
            }
            double progress = finalWave <= firstWave ? 1 : Math.min(1, (wave - firstWave) / (double) (finalWave - firstWave));
            return weight + (lateWeight - weight) * progress;
        }
    }

    /** Each kind's weight in a mixed wave; the boss comes only on boss waves. */
    public Map<EnemyKind, Double> mix(int wave) {
        var weights = new EnumMap<EnemyKind, Double>(EnemyKind.class);
        enemies.forEach((kind, enemy) -> {
            if (kind != EnemyKind.INFERNO) {
                weights.put(kind, enemy.weightAt(wave, waves.total()));
            }
        });
        return weights;
    }

    /**
     * An exploder within {@code triggerRange} of a player lights its fuse and rushes at {@code rushSpeed}; after
     * {@code fuseTicks} it blasts everyone in sight within {@code radius}, harder the closer they stand.
     */
    public record Exploder(double triggerRange, int fuseTicks, double rushSpeed, double radius, double knockback) { }

    public static ZombiesConfig from(ConfigurationSection yaml) {
        var waves = new Waves(positive(yaml, "waves.total"), positive(yaml, "waves.base-enemies"),
                positive(yaml, "waves.enemies-per-wave"), Math.max(0, yaml.getDouble("waves.extra-player")),
                Math.max(0, yaml.getDouble("waves.health-per-wave")), positive(yaml, "waves.spawn-ticks"),
                Math.max(0, yaml.getDouble("waves.spawn-ticks-per-wave")), positive(yaml, "waves.minimum-spawn-ticks"),
                positive(yaml, "waves.max-alive"), positive(yaml, "waves.boss-every"),
                new Late(positive(yaml, "waves.late.from"), Math.max(0, yaml.getDouble("waves.late.health-per-wave")),
                        Math.max(0, yaml.getDouble("waves.late.health-per-extra-player")),
                        Math.max(0, yaml.getDouble("waves.late.spawn-ticks-per-wave"))),
                new Hounds(positive(yaml, "waves.hounds.first"), positive(yaml, "waves.hounds.every"),
                        Math.clamp(yaml.getDouble("waves.hounds.share"), 0.1, 1)),
                Math.max(0, yaml.getInt("waves.stragglers")), Math.clamp(yaml.getDouble("waves.straggler-speed"), 0.05, 0.6));
        var guns = new EnumMap<Weapon, Gun>(Weapon.class);
        for (var weapon : Weapon.values()) {
            String path = "weapons." + weapon.key() + ".";
            guns.put(weapon, new Gun(Math.max(0, yaml.getInt(path + "price")), Math.max(0.1, yaml.getDouble(path + "damage")),
                    Math.max(1, yaml.getDouble(path + "range")), Math.clamp(yaml.getInt(path + "pellets"), 1, 16),
                    Math.clamp(yaml.getDouble(path + "spread"), 0, 0.5), positive(yaml, path + "clip"),
                    positive(yaml, path + "reserve"), positive(yaml, path + "cooldown-ticks"), positive(yaml, path + "reload-ticks"),
                    Math.max(1, yaml.getDouble(path + "upgrade-multiplier")), Math.max(0, yaml.getDouble(path + "burn-damage")),
                    Math.max(0, yaml.getInt(path + "burn-seconds")) * 20));
        }
        var perks = new EnumMap<Perk, Integer>(Perk.class);
        for (var perk : Perk.values()) {
            perks.put(perk, positive(yaml, "perks." + perk.key()));
        }
        var enemies = new EnumMap<EnemyKind, Enemy>(EnemyKind.class);
        for (var kind : EnemyKind.values()) {
            String path = "enemies." + kind.key() + ".";
            enemies.put(kind, new Enemy(positive(yaml, path + "first-wave"), Math.max(1, yaml.getDouble(path + "health")),
                    Math.max(1, yaml.getDouble(path + "damage")), Math.clamp(yaml.getDouble(path + "speed"), 0.05, 0.6),
                    Math.max(0, yaml.getDouble(path + "weight")), Math.max(0, yaml.getDouble(path + "late-weight", yaml.getDouble(path + "weight"))),
                    Math.max(0, yaml.getInt(path + "slow-seconds")) * 20));
        }
        return new ZombiesConfig(Math.clamp(yaml.getInt("players.max", 4), 1, 4), positive(yaml, "gold.start"),
                positive(yaml, "countdown.intermission"), positive(yaml, "countdown.wave-limit"),
                positive(yaml, "revive.bleed-seconds") * 20, positive(yaml, "revive.hold-seconds") * 20,
                Math.clamp(yaml.getDouble("revive.radius", 2.5), 1, 5), positive(yaml, "revive.protection-seconds") * 20,
                positive(yaml, "gold.hit"), positive(yaml, "gold.kill"), positive(yaml, "gold.repair"),
                positive(yaml, "gold.repair-limit-per-wave"), positive(yaml, "gold.wave-clear"),
                positive(yaml, "barricades.repair-ticks"), positive(yaml, "barricades.break-ticks"),
                Math.max(0, yaml.getInt("rewards.kill")), Math.max(0, yaml.getInt("rewards.wave")), Math.max(0, yaml.getInt("rewards.win")),
                positive(yaml, "prices.ammo"), positive(yaml, "prices.armor"), positive(yaml, "prices.upgrade"),
                positive(yaml, "prices.mystery"), positive(yaml, "prices.power"), positive(yaml, "prices.team-revive"),
                Math.clamp(yaml.getDouble("powerups.drop-chance"), 0, 1), positive(yaml, "powerups.seconds") * 20, positive(yaml, "powerups.max-per-wave"),
                Math.max(0, yaml.getInt("rewards.note")), positive(yaml, "regeneration.delay-seconds") * 20, Math.max(0, yaml.getDouble("perks.extra-health-bonus")),
                Math.max(1, yaml.getDouble("combat.headshot-multiplier")), Math.max(1, yaml.getDouble("combat.knife-damage")),
                positive(yaml, "combat.damage-immunity-ticks"),
                waves, Map.copyOf(guns), Map.copyOf(perks), Map.copyOf(enemies),
                new Exploder(Math.clamp(yaml.getDouble("exploder.trigger-range"), 1, 8), positive(yaml, "exploder.fuse-ticks"),
                        Math.clamp(yaml.getDouble("exploder.rush-speed"), 0.05, 0.6), Math.clamp(yaml.getDouble("exploder.radius"), 1, 8),
                        Math.max(0, yaml.getDouble("exploder.knockback"))));
    }

    private static int positive(ConfigurationSection yaml, String path) {
        return Math.max(1, yaml.getInt(path));
    }
}
