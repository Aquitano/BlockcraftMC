package me.blockcraft.bedwars.item;

import me.blockcraft.bedwars.shop.Dealers;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/** A dealer set down on the ground for a few seconds, the time it has left floating above it. */
final class MobileShop {

    private static final double LABEL_HEIGHT = 2.2;

    private record Stall(Entity dealer, TextDisplay label, BukkitTask task) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Dealers dealers;
    private final int seconds;
    private final List<Stall> stalls = new ArrayList<>();

    MobileShop(Plugin plugin, Messages messages, Dealers dealers, int seconds) {
        this.plugin = plugin;
        this.messages = messages;
        this.dealers = dealers;
        this.seconds = seconds;
    }

    /** False unless the player clicked the top of a block; the item is used up otherwise. */
    boolean use(Player player, Block clicked, BlockFace face) {
        if (clicked == null || face != BlockFace.UP) {
            player.sendMessage(messages.get("items.mobile-shop.ground"));
            return false;
        }
        var spot = clicked.getLocation().add(0.5, 1.0, 0.5);
        spot.setDirection(player.getLocation().toVector().subtract(spot.toVector()));
        var dealer = dealers.spawn(spot);
        var label = spot.getWorld().spawn(spot.clone().add(0, LABEL_HEIGHT, 0), TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
        });
        int[] left = {seconds};
        var stall = new Stall[1];
        var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (left[0] <= 0) {
                close(stall[0]);
                stalls.remove(stall[0]);
                return;
            }
            label.text(messages.get(left[0] == 1 ? "items.mobile-shop.left-second" : "items.mobile-shop.left",
                    Formatter.number("seconds", left[0])));
            left[0]--;
        }, 0L, 20L);
        stall[0] = new Stall(dealer, label, task);
        stalls.add(stall[0]);
        return true;
    }

    void clear() {
        stalls.forEach(this::close);
        stalls.clear();
    }

    private void close(Stall stall) {
        stall.task().cancel();
        var location = stall.dealer().getLocation();
        dealers.remove(stall.dealer());
        stall.label().remove();
        location.getWorld().playEffect(location, Effect.SMOKE_SHOOT, BlockFace.UP);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }
}
