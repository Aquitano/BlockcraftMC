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
                            int powerPrice, int teamRevivePrice, double dropChance, int powerupTicks,
                            int noteCoins, int healthRegenTicks, double extraHealth, double headshotMultiplier, double knifeDamage, int damageImmunityTicks,
                            Waves waves, Map<Weapon, Gun> guns, Map<Perk, Integer> perks, Map<EnemyKind, Enemy> enemies) {

    public record Waves(int total, int baseEnemies, int enemiesPerWave, double extraPlayer, double healthPerWave,
                        int spawnTicks, int minimumSpawnTicks, int maxAlive, int bossEvery) { }

    /** {@code burnDamage} per second for {@code burnTicks} after a hit; 0 for a gun that does not set fire. */
    public record Gun(int price, double damage, double range, int pellets, double spread,
                      int clip, int reserve, int cooldownTicks, int reloadTicks, double upgradeMultiplier,
                      double burnDamage, int burnTicks) { }

    public record Enemy(int firstWave, double health, double damage, double speed, int weight) { }

    public static ZombiesConfig from(ConfigurationSection yaml) {
        var waves = new Waves(positive(yaml, "waves.total"), positive(yaml, "waves.base-enemies"),
                positive(yaml, "waves.enemies-per-wave"), Math.max(0, yaml.getDouble("waves.extra-player")),
                Math.max(0, yaml.getDouble("waves.health-per-wave")), positive(yaml, "waves.spawn-ticks"),
                positive(yaml, "waves.minimum-spawn-ticks"), positive(yaml, "waves.max-alive"), positive(yaml, "waves.boss-every"));
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
                    positive(yaml, path + "weight")));
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
                Math.clamp(yaml.getDouble("powerups.drop-chance"), 0, 1), positive(yaml, "powerups.seconds") * 20,
                Math.max(0, yaml.getInt("rewards.note")), positive(yaml, "regeneration.delay-seconds") * 20, Math.max(0, yaml.getDouble("perks.extra-health-bonus")),
                Math.max(1, yaml.getDouble("combat.headshot-multiplier")), Math.max(1, yaml.getDouble("combat.knife-damage")),
                positive(yaml, "combat.damage-immunity-ticks"),
                waves, Map.copyOf(guns), Map.copyOf(perks), Map.copyOf(enemies));
    }

    private static int positive(ConfigurationSection yaml, String path) {
        return Math.max(1, yaml.getInt(path));
    }
}
