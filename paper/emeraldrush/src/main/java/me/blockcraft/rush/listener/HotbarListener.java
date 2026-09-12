package me.blockcraft.rush.listener;

import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.Permissions;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GamePhase;
import me.blockcraft.rush.menu.RushMenus;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

/** Right-clicks on the hotbar items handed out by the game. */
public final class HotbarListener implements Listener {

    private final Plugin plugin;
    private final Game game;
    private final RushMenus menus;
    private final RushConfig config;
    private final Messages messages;

    public HotbarListener(Plugin plugin, Game game, RushMenus menus, RushConfig config, Messages messages) {
        this.plugin = plugin;
        this.game = game;
        this.menus = menus;
        this.config = config;
        this.messages = messages;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        var item = event.getItem();
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick() || item == null) {
            return;
        }
        var player = event.getPlayer();
        switch (item.getType()) {
            case SLIME_BALL -> {
                event.setCancelled(true);
                ProxyMessaging.connect(plugin, player, config.lobbyServer());
            }
            case DIAMOND -> {
                if (player.hasPermission(Permissions.START) && game.phase() == GamePhase.LOBBY) {
                    event.setCancelled(true);
                    game.forceStart(player);
                }
            }
            case PLAYER_HEAD -> {
                event.setCancelled(true);
                if (game.phase() == GamePhase.LOBBY) {
                    menus.openStats(player);
                }
            }
            case CHEST -> {
                event.setCancelled(true);
                switch (game.phase()) {
                    case SHOPPING -> menus.openShop(player);
                    case LOBBY -> menus.openPerks(player);
                    default -> {
                    }
                }
            }
            case COMPASS -> track(player);
            default -> {
            }
        }
    }

    private void track(Player player) {
        for (var entity : player.getNearbyEntities(100.0, 25.0, 100.0)) {
            if (entity instanceof Player near && !game.isSpectator(near)) {
                player.setCompassTarget(near.getLocation());
                player.sendMessage(messages.get("tracker.found",
                        Placeholder.unparsed("player", near.getName()),
                        Formatter.number("distance", (int) player.getLocation().distance(near.getLocation()))));
            }
        }
    }
}
