package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

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
        BlockcraftApi.get().flight().set(player, false);
        player.setAllowFlight(false);
        player.setCollidable(true);
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }

    /**
     * Ends what was under way when a player dropped out of a round, a fall, a fire or a push, so that coming back is
     * a fresh start at the spawn like a respawn; inventory, health and effects stay.
     */
    public static void settle(Player player) {
        player.setFallDistance(0.0f);
        player.setFireTicks(0);
        player.setVelocity(new Vector());
    }

    /**
     * The state a player watching the rest of the round is in: flying in adventure mode, so that the spectator hotbar
     * works, and out of everyone's way. {@link Round} hides them, hands out the hotbar and keeps them off the round.
     */
    static void spectate(Player player) {
        reset(player, GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
    }

    public static void resetHealth(Player player) {
        var maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH));
        maxHealth.removeModifier(EXTRA_HEALTH);
        maxHealth.setBaseValue(DEFAULT_MAX_HEALTH);
        player.setHealth(DEFAULT_MAX_HEALTH);
    }
}
