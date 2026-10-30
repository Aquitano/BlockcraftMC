package me.blockcraft.api.game;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 1.8's weapon damage for players in {@link CombatStyle#LEGACY}. Modern values are balanced by the attack cooldown that
 * LEGACY removes, so an axe would hit as often as a sword and harder. A weapon adds its kind's and its tier's bonus to the
 * fist, as in 1.8; a weapon a game gave its own attack damage keeps it, and so does everything else.
 */
public final class LegacyDamage implements Listener {

    private enum Kind {
        SWORD(4), AXE(3), PICKAXE(2), SHOVEL(1);

        private final int bonus;

        Kind(int bonus) {
            this.bonus = bonus;
        }
    }

    private enum Tier {
        WOODEN(0), GOLDEN(0), STONE(1), COPPER(1), IRON(2), DIAMOND(3), NETHERITE(4);

        private final int bonus;

        Tier(int bonus) {
            this.bonus = bonus;
        }
    }

    private static final Map<Material, Integer> BONUS = bonuses();

    private static Map<Material, Integer> bonuses() {
        var bonuses = new EnumMap<Material, Integer>(Material.class);
        for (var kind : Kind.values()) {
            for (var tier : Tier.values()) {
                var material = Material.getMaterial(tier.name() + "_" + kind.name());
                if (material != null) {
                    bonuses.put(material, kind.bonus + tier.bonus);
                }
            }
        }
        return bonuses;
    }

    /** What the weapon adds to the fist in 1.8, or nothing for an item 1.8 had no weapon value for. */
    static Optional<Integer> bonus(Material weapon) {
        return Optional.ofNullable(BONUS.get(weapon));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player attacker)
                || CombatStyle.of(attacker) != CombatStyle.LEGACY) {
            return;
        }
        var weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.isDataOverridden(DataComponentTypes.ATTRIBUTE_MODIFIERS)) {
            return;
        }
        bonus(weapon.getType()).ifPresent(legacy -> {
            double difference = legacy - modernBonus(weapon);
            event.setDamage(event.getDamage() + difference * (event.isCritical() ? 1.5 : 1));
        });
    }

    private static double modernBonus(ItemStack weapon) {
        var modifiers = weapon.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        return modifiers == null ? 0 : modifiers.modifiers().stream()
                .filter(entry -> entry.attribute() == Attribute.ATTACK_DAMAGE)
                .map(ItemAttributeModifiers.Entry::modifier)
                .filter(modifier -> modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER
                        && modifier.getSlotGroup().test(EquipmentSlot.HAND))
                .mapToDouble(AttributeModifier::getAmount)
                .sum();
    }
}
