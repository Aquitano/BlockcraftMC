package me.blockcraft.rush.listener;

import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GameItem;
import me.blockcraft.rush.game.GamePhase;
import me.blockcraft.rush.menu.RushMenus;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-clicks on the shop item of the shopping phase and the deathmatch compass. */
public final class HotbarListener implements Listener {

    private final Game game;
    private final RushMenus menus;
    private final CompassTracker compass;

    public HotbarListener(Game game, RushMenus menus, CompassTracker compass) {
        this.game = game;
        this.menus = menus;
        this.compass = compass;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        var item = event.getItem();
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick() || item == null) {
            return;
        }
        var player = event.getPlayer();
        if (HotbarItem.of(item, GameItem.class).isPresent()) {
            event.setCancelled(true);
            menus.openShop(player);
        } else if (item.getType() == Material.COMPASS) {
            event.setCancelled(true);
            if (game.phase() == GamePhase.DEATHMATCH && game.isAlive(player)) {
                compass.track(player, game.alivePlayers());
            }
        }
    }
}
