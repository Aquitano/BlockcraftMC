package me.blockcraft.core.staff;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.task.SerialTasks;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PunishmentRepository;
import me.blockcraft.common.report.ReportRepository;
import me.blockcraft.core.command.FreezeCommand;
import me.blockcraft.core.command.InvseeCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff mode: vanished, flying, with a locked hotbar of moderation tools instead of the player's inventory.
 * The inventory is kept in memory and in {@code bc_staff_inventories}; leaving, quitting or rejoining after a
 * crash all hand it back.
 */
public final class StaffMode implements Listener {

    private static final String REPORTS_COMMAND = "reports";

    private record Session(ItemStack[] contents, boolean vanishedBefore, boolean flightBefore, boolean flightGrantedBefore) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final VanishService vanish;
    private final FreezeService freeze;
    private final StaffInventoryRepository inventories;
    private final SerialTasks<UUID> writes;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, byte[]> pendingRestore = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final StaffAudit audit;
    private final PlayerInfo info;
    private final Map<UUID, GameMode> spectating = new HashMap<>();

    public StaffMode(Plugin plugin, Messages messages, VanishService vanish, FreezeService freeze, StaffInventoryRepository inventories,
                     StaffAudit audit, ReportRepository reports, PunishmentRepository punishments) {
        this.plugin = plugin;
        this.messages = messages;
        this.vanish = vanish;
        this.freeze = freeze;
        this.inventories = inventories;
        this.writes = new SerialTasks<>(plugin);
        this.audit = audit;
        this.info = new PlayerInfo(plugin, reports, punishments, messages);
    }

    public boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Returns whether staff mode is active after the toggle. */
    public boolean toggle(Player player) {
        if (isActive(player)) {
            leave(player);
            audit.record(player, "staff.off");
            return false;
        }
        enter(player);
        audit.record(player, "staff.on");
        return true;
    }

    private void enter(Player player) {
        var uuid = player.getUniqueId();
        var contents = player.getInventory().getContents();
        var flight = BlockcraftApi.get().flight();
        sessions.put(uuid, new Session(contents, vanish.isVanished(player), player.getAllowFlight(), flight.granted(player)));
        byte[] serialized = ItemStack.serializeItemsAsBytes(contents);
        writes.run(uuid, () -> inventories.save(uuid, serialized));
        player.getInventory().clear();
        StaffTool.giveAll(player, messages);
        if (!vanish.isVanished(player)) {
            vanish.set(player, true);
        }
        flight.set(player, true);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.sendMessage(messages.get("staff.entered"));
    }

    private void leave(Player player) {
        var session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        restore(player, session);
        player.sendMessage(messages.get("staff.left"));
    }

    private void restore(Player player, Session session) {
        endSpectating(player);
        var uuid = player.getUniqueId();
        player.getInventory().setContents(session.contents());
        writes.run(uuid, () -> inventories.delete(uuid));
        if (!session.vanishedBefore()) {
            vanish.set(player, false);
        }
        BlockcraftApi.get().flight().set(player, session.flightGrantedBefore());
        player.setAllowFlight(session.flightBefore());
        player.setFlying(false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        LoginLookup.run(plugin, messages, event, "staff-mode inventory", () ->
                inventories.find(event.getUniqueId()).ifPresent(contents -> pendingRestore.put(event.getUniqueId(), contents)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        byte[] saved = pendingRestore.remove(player.getUniqueId());
        if (saved == null) {
            return;
        }
        player.getInventory().setContents(ItemStack.deserializeItemsFromBytes(saved));
        var uuid = player.getUniqueId();
        writes.run(uuid, () -> inventories.delete(uuid));
        player.sendMessage(messages.get("staff.inventory-restored"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            restore(event.getPlayer(), session);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        var player = event.getPlayer();
        if (!isActive(player) || event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        event.setCancelled(true);
        StaffTool.of(event.getItem()).ifPresent(tool -> {
            switch (tool) {
                case RANDOM -> teleportToRandomPlayer(player);
                case REPORTS -> openReports(player);
                case LEAVE -> toggle(player);
                default -> {
                }
            }
        });
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        var staff = event.getPlayer();
        if (!isActive(staff) || event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        event.setCancelled(true);
        StaffTool.of(staff.getInventory().getItemInMainHand()).ifPresent(tool -> {
            switch (tool) {
                case FREEZE -> FreezeCommand.toggle(staff, target, freeze, audit, messages);
                case INVSEE -> InvseeCommand.open(staff, target, audit, messages);
                case INFO -> info.show(staff, target);
                case SPECTATE -> spectate(staff, target);
                default -> {
                }
            }
        });
    }

    /** The camera follows the target until the staff member sneaks out of it or leaves staff mode. */
    private void spectate(Player staff, Player target) {
        spectating.putIfAbsent(staff.getUniqueId(), staff.getGameMode());
        staff.setGameMode(GameMode.SPECTATOR);
        staff.setSpectatorTarget(target);
        staff.sendMessage(messages.get("staff.spectating", Placeholder.unparsed("player", target.getName())));
    }

    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        var staff = event.getPlayer();
        if (spectating.containsKey(staff.getUniqueId())) {
            Tasks.sync(plugin, () -> endSpectating(staff));
        }
    }

    private void endSpectating(Player staff) {
        var previous = spectating.remove(staff.getUniqueId());
        if (previous == null) {
            return;
        }
        staff.setGameMode(previous);
        if (isActive(staff)) {
            staff.setAllowFlight(true);
            staff.setFlying(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isActive(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isActive(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isActive(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private void teleportToRandomPlayer(Player staff) {
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.equals(staff) && !isActive(other) && !vanish.isVanished(other))
                .map(other -> (Player) other)
                .toList();
        if (candidates.isEmpty()) {
            staff.sendMessage(messages.get("staff.nobody-online"));
            return;
        }
        var playing = candidates.stream()
                .filter(other -> other.getGameMode() != GameMode.SPECTATOR && !Round.isWatching(other)).toList();
        var pool = playing.isEmpty() ? candidates : playing;
        var target = pool.get(random.nextInt(pool.size()));
        staff.teleport(target);
        staff.sendMessage(messages.get("staff.teleported", Placeholder.unparsed("player", target.getName())));
    }

    private void openReports(Player staff) {
        if (Bukkit.getCommandMap().getCommand(REPORTS_COMMAND) == null) {
            staff.sendMessage(messages.get("staff.no-reports"));
            return;
        }
        staff.performCommand(REPORTS_COMMAND);
    }
}
