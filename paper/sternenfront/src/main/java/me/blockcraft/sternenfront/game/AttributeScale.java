package me.blockcraft.sternenfront.game;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.Objects;

/** Scales an attribute with a transient modifier: a {@code change} of -1 takes all of it, 0.5 adds half. */
public final class AttributeScale {

    private AttributeScale() {
    }

    public static void apply(LivingEntity body, Attribute attribute, NamespacedKey key, double change) {
        Objects.requireNonNull(body.getAttribute(attribute)).addTransientModifier(new AttributeModifier(key, change,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    public static void remove(LivingEntity body, Attribute attribute, NamespacedKey key) {
        var instance = Objects.requireNonNull(body.getAttribute(attribute));
        if (instance.getModifier(key) != null) {
            instance.removeModifier(key);
        }
    }
}
