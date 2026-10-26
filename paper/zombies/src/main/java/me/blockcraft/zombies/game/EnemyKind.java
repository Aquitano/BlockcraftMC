package me.blockcraft.zombies.game;

import org.bukkit.entity.EntityType;

import java.util.Locale;

public enum EnemyKind {
    WALKER(EntityType.ZOMBIE),
    RUNNER(EntityType.ZOMBIE),
    TANK(EntityType.ZOMBIE),
    EXPLODER(EntityType.ZOMBIE),
    TOXIC(EntityType.HUSK),
    HELLHOUND(EntityType.WOLF),
    INFERNO(EntityType.ZOMBIE);

    private final EntityType entity;

    EnemyKind(EntityType entity) {
        this.entity = entity;
    }

    public EntityType entity() {
        return entity;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
