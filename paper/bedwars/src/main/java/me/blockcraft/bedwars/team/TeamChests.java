package me.blockcraft.bedwars.team;

import me.blockcraft.common.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.Lidded;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * An ender chest a team member places is a team chest: it opens the team's one shared inventory for its members
 * only, glitters in the team's colour, and anyone may break it, which the team hears about.
 */
public final class TeamChests implements Listener {

    private static final long GLITTER_TICKS = 10L;
    private static final int GLITTER_PARTICLES = 5;

    private final Plugin plugin;
    private final Messages messages;
    private final Teams teams;
    private final Map<Block, Team> chests = new HashMap<>();
    private final Map<Block, Set<UUID>> viewers = new HashMap<>();
    private BukkitTask glitter;

    public TeamChests(Plugin plugin, Messages messages, Teams teams) {
        this.plugin = plugin;
        this.messages = messages;
        this.teams = teams;
    }

    public void clear() {
        if (glitter != null) {
            glitter.cancel();
            glitter = null;
        }
        chests.clear();
        viewers.clear();
    }

    /** Breaks the chest as an explosion took it; false if the block was none. */
    public boolean blownUp(Block block) {
        var team = chests.get(block);
        if (team == null) {
            return false;
        }
        destroy(block, team);
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        var block = event.getBlockPlaced();
        if (block.getType() != Material.ENDER_CHEST) {
            return;
        }
        teams.of(event.getPlayer()).ifPresent(team -> {
            chests.put(block, team);
            if (glitter == null) {
                glitter = Bukkit.getScheduler().runTaskTimer(plugin, this::glitter, 0L, GLITTER_TICKS);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        var block = event.getClickedBlock();
        var team = chests.get(block);
        if (team == null) {
            return;
        }
        var player = event.getPlayer();
        if (player.isSneaking() && event.getItem() != null) {
            return;
        }
        event.setCancelled(true);
        if (!team.isMember(player)) {
            player.sendMessage(messages.get("team-chest.not-yours"));
            return;
        }
        if (viewers.computeIfAbsent(block, ignored -> new HashSet<>()).add(player.getUniqueId())) {
            lid(block, true);
        }
        player.openInventory(team.chest());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        var id = event.getPlayer().getUniqueId();
        viewers.forEach((block, watching) -> {
            if (watching.remove(id) && watching.isEmpty()) {
                lid(block, false);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        var team = chests.get(block);
        if (team == null) {
            return;
        }
        event.setCancelled(true);
        if (!team.isMember(event.getPlayer())) {
            team.members().forEach(member -> member.sendMessage(messages.get("team-chest.destroyed")));
        }
        destroy(block, team);
    }

    private void destroy(Block block, Team team) {
        chests.remove(block);
        var watching = viewers.remove(block);
        if (watching != null) {
            watching.stream().map(Bukkit::getPlayer).filter(player -> player != null
                    && player.getOpenInventory().getTopInventory().equals(team.chest())).forEach(Player::closeInventory);
        }
        block.getWorld().playEffect(block.getLocation(), Effect.DESTROY_BLOCK, block.getBlockData());
        block.setType(Material.AIR);
    }

    private void glitter() {
        List.copyOf(chests.entrySet()).forEach(entry -> {
            var center = entry.getKey().getLocation().add(0.5, 0.0, 0.5);
            center.getWorld().spawnParticle(Particle.ENTITY_EFFECT, center, GLITTER_PARTICLES, 0.25, 0.25, 0.25, 0,
                    entry.getValue().color().dye().getColor());
        });
    }

    private static void lid(Block block, boolean open) {
        if (block.getState() instanceof Lidded lidded) {
            if (open) {
                lidded.open();
            } else {
                lidded.close();
            }
        }
    }
}
