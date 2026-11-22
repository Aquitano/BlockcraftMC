package me.blockcraft.zombies.game;

import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.Optional;

public enum EnemyKind {
    WALKER(EntityType.ZOMBIE),
    RUNNER(EntityType.ZOMBIE),
    TANK(EntityType.ZOMBIE),
    EXPLODER(EntityType.ZOMBIE),
    TOXIC(EntityType.HUSK),
    HELLHOUND(EntityType.WOLF, new CreatureBody("sw_nexu", 1.8, 2, 2.5, 0.7, 1)),
    INFERNO(EntityType.ZOMBIE),
    CRAWLER(EntityType.ZOMBIE),
    SHIELD_BEARER(EntityType.ZOMBIE),
    FROST(EntityType.ZOMBIE),
    ARSONIST(EntityType.ZOMBIE),
    ELECTRIC(EntityType.ZOMBIE),
    BREEDER(EntityType.ZOMBIE),
    SHADOW(EntityType.ZOMBIE),
    POUNCER(EntityType.ZOMBIE, new CreatureBody("sw_acklay", 3.4, 4.4, 4, 1, 1)),
    RAM(EntityType.ZOMBIE, new CreatureBody("sw_reek", 2.3, 2.8, 4, 1.1, 1)),
    SCAVENGER(EntityType.ZOMBIE, new CreatureBody("kit_womp_rat", 0.56, 0.7, 1.4, 0.45, 0.4));

    private final EntityType entity;
    private final CreatureBody body;

    EnemyKind(EntityType entity) {
        this(entity, null);
    }

    EnemyKind(EntityType entity, CreatureBody body) {
        this.entity = entity;
        this.body = body;
    }

    public EntityType entity() {
        return entity;
    }

    Optional<CreatureBody> body() {
        return Optional.ofNullable(body);
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
