package me.blockcraft.api.player;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;

/**
 * Kits and lobby items would otherwise pop vanilla advancement and recipe toasts on every server. Recipes still unlock
 * through vanilla's hidden {@code recipes/} advancements, only silently, so the recipe book works where players craft.
 */
public final class VanillaProgress implements Listener {

    @EventHandler
    public void onAdvancement(PlayerAdvancementCriterionGrantEvent event) {
        var key = event.getAdvancement().getKey();
        if (!key.getNamespace().equals(NamespacedKey.MINECRAFT) || !key.getKey().startsWith("recipes/")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRecipe(PlayerRecipeDiscoverEvent event) {
        event.shouldShowNotification(false);
    }
}
