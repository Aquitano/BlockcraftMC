package me.blockcraft.core.staff;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.core.command.InvseeCommand;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

    private static final int SLOT_RANDOM = 0;
    private static final int SLOT_FREEZE = 1;
    private static final int SLOT_INVSEE = 2;
    private static final int SLOT_REPORTS = 3;
    private static final int SLOT_LEAVE = 8;
    private static final String REPORTS_COMMAND = "reports";

    private record Session(ItemStack[] contents, boolean vanishedBefore, boolean flightBefore) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final VanishService vanish;
    private final FreezeService freeze;
    private final StaffInventoryRepository inventories;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, byte[]> pendingRestore = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public StaffMode(Plugin plugin, Messages messages, VanishService vanish, FreezeService freeze, StaffInventoryRepository inventories) {
        this.plugin = plugin;
        this.messages = messages;
        this.vanish = vanish;
        this.freeze = freeze;
        this.inventories = inventories;
    }

    public boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Returns whether staff mode is active after the toggle. */
    public boolean toggle(Player player) {
        if (isActive(player)) {
            leave(player);
            return false;
        }
        enter(player);
        return true;
    }

    private void enter(Player player) {
        var uuid = player.getUniqueId();
        var contents = player.getInventory().getContents();
        sessions.put(uuid, new Session(contents, vanish.isVanished(player), player.getAllowFlight()));
        byte[] serialized = ItemStack.serializeItemsAsBytes(contents);
        Tasks.async(plugin, () -> inventories.save(uuid, serialized));
        player.getInventory().clear();
        var inventory = player.getInventory();
        inventory.setItem(SLOT_RANDOM, Items.of(Material.COMPASS).name(messages.get("staff.item.random")).build());
        inventory.setItem(SLOT_FREEZE, Items.of(Material.PACKED_ICE).name(messages.get("staff.item.freeze")).build());
        inventory.setItem(SLOT_INVSEE, Items.of(Material.CHEST).name(messages.get("staff.item.invsee")).build());
        inventory.setItem(SLOT_REPORTS, Items.of(Material.BOOK).name(messages.get("staff.item.reports")).build());
        inventory.setItem(SLOT_LEAVE, Items.of(Material.BARRIER).name(messages.get("staff.item.leave")).build());
        if (!vanish.isVanished(player)) {
            vanish.set(player, true);
        }
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
        var uuid = player.getUniqueId();
        player.getInventory().setContents(session.contents());
        Tasks.async(plugin, () -> inventories.delete(uuid));
        if (!session.vanishedBefore()) {
            vanish.set(player, false);
        }
        player.setAllowFlight(session.flightBefore());
        player.setFlying(false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        inventories.find(event.getUniqueId()).ifPresent(contents -> pendingRestore.put(event.getUniqueId(), contents));
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
        Tasks.async(plugin, () -> inventories.delete(uuid));
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
        switch (player.getInventory().getHeldItemSlot()) {
            case SLOT_RANDOM -> teleportToRandomPlayer(player);
            case SLOT_REPORTS -> openReports(player);
            case SLOT_LEAVE -> leave(player);
            default -> {
            }
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        var staff = event.getPlayer();
        if (!isActive(staff) || event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        event.setCancelled(true);
        switch (staff.getInventory().getHeldItemSlot()) {
            case SLOT_FREEZE -> staff.sendMessage(messages.get(freeze.toggle(target, staff) ? "freeze.frozen" : "freeze.unfrozen",
                    Placeholder.unparsed("player", target.getName())));
            case SLOT_INVSEE -> InvseeCommand.open(staff, target, messages);
            default -> {
            }
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
        var target = candidates.get(random.nextInt(candidates.size()));
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
