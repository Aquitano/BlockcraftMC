package me.blockcraft.lobby.cosmetic;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.LobbyPermissions;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class CosmeticPreviews implements Listener {

    private final Plugin plugin;
    private final Messages messages;
    private final int durationTicks;
    private final NamespacedKey previewKey;
    private final Map<UUID, Preview> active = new HashMap<>();

    public CosmeticPreviews(Plugin plugin, Messages messages, int durationTicks) {
        this.plugin = plugin;
        this.messages = messages;
        this.durationTicks = durationTicks;
        previewKey = new NamespacedKey(plugin, "cosmetic_preview");
    }

    public void equipment(Player player, ItemStack item, EquipmentSlot slot, Consumer<Player> back) {
        startEntity(player, false, preview -> {
            var mannequin = preview.location.getWorld().spawn(preview.location, Mannequin.class, entity -> {
                prepare(entity);
                entity.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
                entity.setImmovable(true);
                entity.setDescription(null);
                entity.getEquipment().setArmorContents(player.getInventory().getArmorContents());
                entity.getEquipment().setItem(slot, item.clone(), true);
            });
            preview.show(mannequin);
        }, back);
    }

    public void pet(Player player, PetType type, Consumer<Player> back) {
        startEntity(player, true, preview -> {
            var pet = preview.location.getWorld().spawn(preview.location, type.entityClass(), entity -> {
                prepare(entity);
                if (type.baby()) {
                    entity.setBaby();
                }
                entity.setAgeLock(true);
            });
            preview.show(pet);
        }, back);
    }

    public void trail(Player player, Trail trail, Consumer<Player> back) {
        start(player, true, preview -> preview.animate = ticks -> {
            double progress = ticks / (double) durationTicks;
            var point = preview.location.clone().add(Math.sin(progress * Math.PI * 2) * 0.8, 0.2, 0);
            player.spawnParticle(trail.particle(), point, 3, 0.12, 0.12, 0.12, 0.02);
        }, back);
    }

    public void effect(Player player, Consumer<Player> effect, Consumer<Player> back) {
        start(player, false, preview -> effect.accept(player), back);
    }

    private void startEntity(Player player, boolean premium, Consumer<Preview> create, Consumer<Player> back) {
        var location = entityLocation(player);
        if (location.isEmpty()) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("cosmetics.preview-unavailable"));
            return;
        }
        start(player, premium, create, back, location);
    }

    private void start(Player player, boolean premium, Consumer<Preview> create, Consumer<Player> back) {
        start(player, premium, create, back, Optional.empty());
    }

    private void start(Player player, boolean premium, Consumer<Preview> create, Consumer<Player> back,
                       Optional<Location> entityLocation) {
        if (!allowed(player, premium)) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("cosmetics.preview-disabled"));
            return;
        }
        cancel(player);
        Menu.close(player, viewer -> {
            if (!allowed(viewer, premium)) {
                return;
            }
            var location = entityLocation.orElseGet(() -> position(viewer, 2.5));
            if (viewer.getWorld() != location.getWorld() || entityLocation.isPresent() && !hasSpace(location)) {
                BlockcraftApi.get().notifications().feedback(viewer, messages.get("cosmetics.preview-unavailable"));
                back.accept(viewer);
                return;
            }
            var preview = new Preview(viewer, premium, back, location);
            active.put(viewer.getUniqueId(), preview);
            try {
                create.accept(preview);
                preview.task = plugin.getServer().getScheduler().runTaskTimer(plugin, preview::tick, 1L, 1L);
                BlockcraftApi.get().notifications().feedback(viewer, messages.get("cosmetics.preview-active"));
            } catch (RuntimeException failure) {
                cancel(viewer);
                plugin.getSLF4JLogger().warn("Could not show cosmetic preview", failure);
                BlockcraftApi.get().notifications().feedback(viewer, messages.get("cosmetics.preview-failed"));
                back.accept(viewer);
            }
        });
    }

    private static Optional<Location> entityLocation(Player player) {
        for (double distance : new double[]{2.5, 1.5}) {
            var location = position(player, distance);
            if (hasSpace(location)) {
                return Optional.of(location);
            }
        }
        return Optional.empty();
    }

    private static Location position(Player player, double distance) {
        var location = player.getLocation();
        location.setPitch(0);
        location.add(location.getDirection().multiply(distance));
        location.setYaw(location.getYaw() + 180);
        return location;
    }

    private static boolean hasSpace(Location location) {
        var world = location.getWorld();
        for (int x = (int) Math.floor(location.getX() - 0.5); x <= (int) Math.floor(location.getX() + 0.5); x++) {
            for (int z = (int) Math.floor(location.getZ() - 0.5); z <= (int) Math.floor(location.getZ() + 0.5); z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    return false;
                }
                for (int y = location.getBlockY(); y <= (int) Math.floor(location.getY() + 1.99); y++) {
                    if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.getBlockAt(x, y, z).isPassable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean allowed(Player player, boolean premium) {
        return plugin.isEnabled() && player.isOnline()
                && BlockcraftApi.get().settings().enabled(player, PlayerSetting.EFFECTS)
                && (!premium || player.hasPermission(LobbyPermissions.PREMIUM));
    }

    private void prepare(LivingEntity entity) {
        entity.setVisibleByDefault(false);
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setGravity(false);
        entity.setAI(false);
        entity.setCollidable(false);
        entity.setCanPickupItems(false);
        entity.getPersistentDataContainer().set(previewKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            cancel(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        cancel(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getPersistentDataContainer().has(previewKey)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(previewKey)) {
            event.setCancelled(true);
        }
    }

    private void cancel(Player player) {
        var preview = active.remove(player.getUniqueId());
        if (preview != null) {
            preview.remove();
        }
    }

    public void close() {
        active.values().forEach(Preview::remove);
        active.clear();
    }

    private final class Preview {
        private final Player player;
        private final boolean premium;
        private final Consumer<Player> back;
        private final BooleanSupplier current;
        private final Location location;
        private final List<Entity> entities = new ArrayList<>();
        private IntConsumer animate = ignored -> { };
        private BukkitTask task;
        private int ticks;

        private Preview(Player player, boolean premium, Consumer<Player> back, Location location) {
            this.player = player;
            this.premium = premium;
            this.back = back;
            current = BlockcraftApi.get().playerTasks().currentScreen(player);
            this.location = location;
        }

        private void show(Entity entity) {
            entities.add(entity);
            player.showEntity(plugin, entity);
        }

        private void tick() {
            if (!allowed(player, premium) || !current.getAsBoolean() || player.getWorld() != location.getWorld()
                    || entities.stream().anyMatch(entity -> !entity.isValid())) {
                cancel(player);
                return;
            }
            if (ticks % 3 == 0) {
                animate.accept(ticks);
            }
            if (++ticks >= durationTicks) {
                cancel(player);
                back.accept(player);
            }
        }

        private void remove() {
            if (task != null) {
                task.cancel();
            }
            entities.forEach(Entity::remove);
        }
    }
}
