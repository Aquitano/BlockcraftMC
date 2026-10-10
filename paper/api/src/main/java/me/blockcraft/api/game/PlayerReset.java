package me.blockcraft.api.game;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Puts a player back to a blank slate between lobby, round and spectating. */
public final class PlayerReset {

    private static final double DEFAULT_MAX_HEALTH = 20.0;

    /** The transient bonus /sethealth grants above the maximum; a reset takes it away with the rest. */
    public static final NamespacedKey EXTRA_HEALTH = new NamespacedKey("blockcraft", "extra_health");

    private PlayerReset() {
    }

    public static void reset(Player player, GameMode gameMode) {
        player.clearActivePotionEffects();
        player.setExp(0.0f);
        player.setLevel(0);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        resetHealth(player);
        CombatStyle.MODERN.apply(player);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setGameMode(gameMode);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }

    /** The state a player watching the rest of the round is in; the game keeps the bookkeeping. */
    public static void spectate(Player player) {
        player.setExp(0.0f);
        player.setLevel(0);
        resetHealth(player);
        CombatStyle.MODERN.apply(player);
        player.setFoodLevel(20);
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.SPECTATOR);
    }

    public static void resetHealth(Player player) {
        var maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH));
        maxHealth.removeModifier(EXTRA_HEALTH);
        maxHealth.setBaseValue(DEFAULT_MAX_HEALTH);
        player.setHealth(DEFAULT_MAX_HEALTH);
    }
}
