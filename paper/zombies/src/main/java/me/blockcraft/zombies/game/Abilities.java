package me.blockcraft.zombies.game;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import java.util.EnumMap;
import java.util.Map;

/** Each special kind's {@link Ability}, and where the game's events reach them. */
public final class Abilities {

    private final Enemies enemies;
    private final Arsonist arsonist;
    private final Map<EnemyKind, Ability> byKind = new EnumMap<>(EnemyKind.class);

    Abilities(ZombiesGame game, Enemies enemies) {
        this.enemies = enemies;
        var config = game.config().abilities();
        arsonist = new Arsonist(game, config.arsonist());
        byKind.put(EnemyKind.CRAWLER, new Crawler(config.crawler()));
        byKind.put(EnemyKind.SHIELD_BEARER, new ShieldBearer(config.shieldBearer()));
        byKind.put(EnemyKind.FROST, new Frost(game, config.frost()));
        byKind.put(EnemyKind.ARSONIST, arsonist);
        byKind.put(EnemyKind.ELECTRIC, new Electric(game, config.electric()));
        byKind.put(EnemyKind.BREEDER, new Breeder(enemies, config.breeder()));
        byKind.put(EnemyKind.SHADOW, new ShadowWalker(game, config.shadow()));
    }

    Ability of(EnemyKind kind) {
        return byKind.getOrDefault(kind, Ability.NONE);
    }

    void tick(int tick) {
        byKind.values().forEach(ability -> ability.tick(tick));
    }

    void clear() {
        byKind.values().forEach(Ability::clear);
    }

    public void landed(Projectile projectile) {
        arsonist.landed(projectile);
    }

    /** An enemy's attack reached a fighter. */
    public void struck(Entity damager, Player player) {
        var enemy = enemies.enemy(damager);
        if (enemy != null) {
            of(enemy.kind).struck(enemy, player);
        }
    }
}
