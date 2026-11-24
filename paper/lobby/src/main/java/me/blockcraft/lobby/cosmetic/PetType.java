package me.blockcraft.lobby.cosmetic;

import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Wolf;

import java.util.Arrays;
import java.util.Optional;

public enum PetType {
    WOLF("wolf", Material.BONE, Wolf.class, true),
    CHICKEN("chicken", Material.EGG, Chicken.class, false),
    PIG("pig", Material.PORKCHOP, Pig.class, true),
    RABBIT("rabbit", Material.CARROT, Rabbit.class, true);

    private final String key;
    private final Material icon;
    private final Class<? extends Animals> entityClass;
    private final boolean baby;

    PetType(String key, Material icon, Class<? extends Animals> entityClass, boolean baby) {
        this.key = key;
        this.icon = icon;
        this.entityClass = entityClass;
        this.baby = baby;
    }

    public static Optional<PetType> of(Entity entity) {
        return Arrays.stream(values()).filter(type -> type.entityClass.isInstance(entity)).findFirst();
    }

    public static Optional<PetType> byKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }

    public String key() {
        return key;
    }

    public Material icon() {
        return icon;
    }

    public Class<? extends Animals> entityClass() {
        return entityClass;
    }

    public boolean baby() {
        return baby;
    }
}
