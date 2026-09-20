package me.blockcraft.lobby.gadget;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.item.LobbyItem;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class Gadgets {

    private static final List<Material> PAINT = List.of(
            Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.BLUE_WOOL, Material.PURPLE_WOOL, Material.MAGENTA_WOOL, Material.PINK_WOOL, Material.CYAN_WOOL);
    private static final int PAINT_RADIUS = 1;
    private static final long PAINT_TICKS = 100L;

    private final Plugin plugin;
    private final Messages messages;
    private final int cooldownTicks;
    private final Map<Location, BlockData> painted = new HashMap<>();
    private final Random random = new Random();

    public Gadgets(Plugin plugin, Messages messages, Duration cooldown) {
        this.plugin = plugin;
        this.messages = messages;
        cooldownTicks = Math.toIntExact((cooldown.toMillis() + 49) / 50);
    }

    public void select(Player player, Gadget gadget) {
        player.getInventory().setItem(LobbyItem.GADGET.slot(), LobbyItem.GADGET.tag(Items.of(gadget.material())
                .name(messages.get("gadgets.name." + gadget.key()))
                .lore(messages.get("gadgets.held-lore"))
                .build()));
    }

    public void clear(Player player) {
        player.getInventory().setItem(LobbyItem.GADGET.slot(), null);
    }

    public boolean tryUse(Player player) {
        int remaining = player.getCooldown(player.getInventory().getItemInMainHand());
        if (remaining > 0) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("gadgets.cooldown", Formatter.number("seconds", (remaining + 19L) / 20)));
            return false;
        }
        for (var gadget : Gadget.values()) {
            player.setCooldown(gadget.material(), cooldownTicks);
        }
        return true;
    }

    /** Colours the solid blocks around the hit and schedules the wash-off; blocks with a tile entity stay as they are. */
    public void paint(Block hit) {
        var changed = new ArrayList<Location>();
        for (int x = -PAINT_RADIUS; x <= PAINT_RADIUS; x++) {
            for (int y = -PAINT_RADIUS; y <= PAINT_RADIUS; y++) {
                for (int z = -PAINT_RADIUS; z <= PAINT_RADIUS; z++) {
                    var block = hit.getRelative(x, y, z);
                    if (!block.getType().isSolid() || block.getState() instanceof TileState) {
                        continue;
                    }
                    painted.putIfAbsent(block.getLocation(), block.getBlockData());
                    block.setType(PAINT.get(random.nextInt(PAINT.size())), false);
                    changed.add(block.getLocation());
                }
            }
        }
        Tasks.later(plugin, PAINT_TICKS, () -> changed.forEach(this::restore));
    }

    public void restorePaint() {
        List.copyOf(painted.keySet()).forEach(this::restore);
    }

    private void restore(Location location) {
        var original = painted.remove(location);
        if (original != null) {
            location.getBlock().setBlockData(original, false);
        }
    }
}
