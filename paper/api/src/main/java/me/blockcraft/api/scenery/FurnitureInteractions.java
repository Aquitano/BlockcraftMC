package me.blockcraft.api.scenery;

import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.function.Consumer;

public final class FurnitureInteractions implements Listener {

    private final Map<String, Consumer<Player>> actions;

    private FurnitureInteractions(Map<String, Consumer<Player>> actions) {
        this.actions = Map.copyOf(actions);
    }

    public static void register(Plugin plugin, Map<String, Consumer<Player>> actions) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("Nexo")) {
            plugin.getServer().getPluginManager().registerEvents(new FurnitureInteractions(actions), plugin);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(NexoFurnitureInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        var action = actions.get(event.getMechanic().getItemID());
        if (action == null) return;
        event.setCancelled(true);
        action.accept(event.getPlayer());
    }
}
