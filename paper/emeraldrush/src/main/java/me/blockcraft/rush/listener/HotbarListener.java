package me.blockcraft.rush.listener;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GameItem;
import me.blockcraft.rush.game.GamePhase;
import me.blockcraft.rush.menu.RushMenus;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Comparator;

/** Right-clicks on the shop item of the shopping phase and the deathmatch compass. */
public final class HotbarListener implements Listener {

    private final Game game;
    private final RushMenus menus;
    private final RushConfig config;
    private final Messages messages;

    public HotbarListener(Game game, RushMenus menus, RushConfig config, Messages messages) {
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
        if (HotbarItem.of(item, GameItem.class).isPresent()) {
            event.setCancelled(true);
            menus.openShop(player);
        } else if (item.getType() == Material.COMPASS) {
            event.setCancelled(true);
            track(player);
        }
    }

    private void track(Player player) {
        if (game.phase() != GamePhase.DEATHMATCH || !game.isAlive(player)) {
            return;
        }
        var compass = player.getInventory().getItemInMainHand();
        if (player.hasCooldown(compass)) {
            return;
        }
        player.setCooldown(compass, config.trackerCooldownTicks());
        var origin = player.getLocation();
        var target = game.alivePlayers().stream()
                .filter(other -> !other.equals(player) && !other.isDead() && player.canSee(other))
                .filter(other -> other.getWorld().equals(origin.getWorld()))
                .filter(other -> {
                    var location = other.getLocation();
                    return Math.abs(location.getX() - origin.getX()) <= config.trackerRangeHorizontal()
                            && Math.abs(location.getZ() - origin.getZ()) <= config.trackerRangeHorizontal()
                            && Math.abs(location.getY() - origin.getY()) <= config.trackerRangeVertical();
                })
                .min(Comparator.comparingDouble(other -> origin.distanceSquared(other.getLocation())));
        target.ifPresentOrElse(near -> {
            player.setCompassTarget(near.getLocation());
            BlockcraftApi.get().notifications().feedback(player, messages.get("tracker.found",
                    Placeholder.unparsed("player", near.getName()),
                    Formatter.number("distance", (int) origin.distance(near.getLocation()))));
            Sounds.NOTIFY.play(player);
        }, () -> {
            player.setCompassTarget(origin);
            BlockcraftApi.get().notifications().feedback(player, messages.get("tracker.empty"));
            Sounds.MENU_DENY.play(player);
        });
    }
}
