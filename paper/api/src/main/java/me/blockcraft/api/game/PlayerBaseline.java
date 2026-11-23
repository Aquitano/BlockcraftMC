package me.blockcraft.api.game;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.GameMode;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How each player was when they first joined this server, so that a player who stays for the next round of a
 * {@link RoundHost} starts it as one who just joined. A restart used to give that for free. A player's own
 * attribute bases differ from the attributes' defaults, a player's movement speed among them, so they are read off
 * the player rather than assumed.
 */
final class PlayerBaseline {

    private static final float WALK_SPEED = 0.2f;
    private static final float FLY_SPEED = 0.1f;

    private final Map<UUID, Map<Attribute, Double>> bases = new HashMap<>();

    void remember(Player player) {
        bases.computeIfAbsent(player.getUniqueId(), id -> {
            var values = new HashMap<Attribute, Double>();
            Registry.ATTRIBUTE.forEach(attribute -> {
                var instance = player.getAttribute(attribute);
                if (instance != null) {
                    values.put(attribute, instance.getBaseValue());
                }
            });
            return Map.copyOf(values);
        });
    }

    void restore(Player player, GameMode gameMode) {
        player.closeInventory();
        var values = bases.getOrDefault(player.getUniqueId(), Map.of());
        Registry.ATTRIBUTE.forEach(attribute -> {
            var instance = player.getAttribute(attribute);
            if (instance == null) {
                return;
            }
            instance.getModifiers().forEach(instance::removeModifier);
            var base = values.get(attribute);
            if (base != null) {
                instance.setBaseValue(base);
            }
        });
        PlayerReset.reset(player, gameMode);
        player.setWalkSpeed(WALK_SPEED);
        player.setFlySpeed(FLY_SPEED);
        player.setGlowing(false);
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setSilent(false);
        player.setGravity(true);
        player.setAbsorptionAmount(0.0);
        player.setFreezeTicks(0);
        player.setArrowsInBody(0);
        player.setRemainingAir(player.getMaximumAir());
        player.setExhaustion(0.0f);
        player.setCanPickupItems(true);
        player.resetPlayerTime();
        player.resetPlayerWeather();
        player.getEnderChest().clear();
        player.setRespawnLocation(null);
        player.setCompassTarget(player.getWorld().getSpawnLocation());
        Registry.ITEM.forEach(item -> {
            if (player.getCooldown(item.getKey()) > 0) {
                player.setCooldown(item.getKey(), 0);
            }
        });
        var bars = new ArrayList<BossBar>();
        player.activeBossBars().forEach(bars::add);
        bars.forEach(player::hideBossBar);
    }
}
