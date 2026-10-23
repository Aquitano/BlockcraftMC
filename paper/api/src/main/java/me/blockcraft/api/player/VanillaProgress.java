package me.blockcraft.api.player;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;

/** Kits and lobby items would otherwise pop vanilla advancement and recipe toasts on every server. */
public final class VanillaProgress implements Listener {

    @EventHandler
    public void onAdvancement(PlayerAdvancementCriterionGrantEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onRecipe(PlayerRecipeDiscoverEvent event) {
        event.setCancelled(true);
    }
}
