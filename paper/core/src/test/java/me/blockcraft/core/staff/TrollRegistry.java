package me.blockcraft.core.staff;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Color;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"unchecked", "removal", "deprecation"})
public final class TrollRegistry implements RegistryAccess {

    private final Map<String, PotionEffectType> potions = new HashMap<>();

    @Override
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        return registry();
    }

    @Override
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return registry();
    }

    private <T extends Keyed> Registry<T> registry() {
        return (Registry<T>) Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[]{Registry.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "get", "getOrThrow" -> potions.computeIfAbsent(args[0].toString(), name ->
                            new TestPotion(NamespacedKey.fromString(name)));
                    default -> throw new AssertionError(method.getName());
                });
    }

    private static final class TestPotion extends PotionEffectType {
        private final NamespacedKey key;

        private TestPotion(NamespacedKey key) {
            this.key = key;
        }

        @Override public NamespacedKey getKey() { return key; }
        @Override public String getName() { return key.getKey(); }
        @Override public String getTranslationKey() { return translationKey(); }
        @Override public String translationKey() { return "effect.minecraft." + key.getKey(); }
        @Override public PotionEffect createEffect(int duration, int amplifier) { return new PotionEffect(this, duration, amplifier); }
        @Override public boolean isInstant() { return false; }
        @Override public Color getColor() { return Color.WHITE; }
        @Override public double getDurationModifier() { return 1; }
        @Override public int getId() { return key.hashCode(); }
        @Override public Map<Attribute, AttributeModifier> getEffectAttributes() { return Map.of(); }
        @Override public double getAttributeModifierAmount(Attribute attribute, int amplifier) { return 0; }
        @Override public Category getEffectCategory() { return Category.NEUTRAL; }
        @Override public PotionEffectTypeCategory getCategory() { throw new AssertionError("getCategory"); }
    }
}
