package me.blockcraft.ttt.shop;

import me.blockcraft.api.task.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holding a player fast, as a bear trap or the taser does: slowness stops the walking, and a jump strength of zero
 * stops the sprint-jumps slowness alone lets through. Overlapping holds end with the longest.
 */
final class Holds {

    private static final int STILL_AMPLIFIER = 6;
    private static final NamespacedKey KEY = new NamespacedKey("blockcraft", "ttt_held");
    private static final AttributeModifier NO_JUMP = new AttributeModifier(KEY, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);

    private final Plugin plugin;
    private final Map<UUID, Integer> until = new HashMap<>();

    Holds(Plugin plugin) {
        this.plugin = plugin;
    }

    void hold(Player player, int ticks) {
        var id = player.getUniqueId();
        until.merge(id, Bukkit.getCurrentTick() + ticks, Math::max);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, STILL_AMPLIFIER, false, false, true));
        var jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null && jump.getModifier(KEY) == null) {
            jump.addTransientModifier(NO_JUMP);
        }
        Tasks.later(plugin, ticks, () -> {
            var held = Bukkit.getPlayer(id);
            if (held != null && until.getOrDefault(id, 0) <= Bukkit.getCurrentTick()) {
                release(held);
            }
        });
    }

    void release(Player player) {
        until.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        var jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(KEY);
        }
    }

    void clear() {
        Map.copyOf(until).keySet().stream().map(Bukkit::getPlayer).filter(player -> player != null).forEach(this::release);
        until.clear();
    }
}
