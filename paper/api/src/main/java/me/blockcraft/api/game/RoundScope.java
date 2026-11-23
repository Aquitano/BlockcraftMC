package me.blockcraft.api.game;

import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The plugin as one round of a {@link RoundHost} sees it. Bukkit files listeners, tasks, chunk tickets, players and
 * entities hidden from each other and plugin channels under the plugin that registered them, so whatever the round
 * registers through its scope belongs to the round alone and goes when it closes; nothing a game builds has to
 * remember to clean up after itself. What the round spawned in the worlds that outlast it is removed as well, those
 * worlds' borders are put back, and the round's own worlds are discarded. A closed scope reports
 * itself disabled, so a callback of the round that arrives late does nothing.
 */
public final class RoundScope implements Plugin {

    private record Border(Location center, double size, double damage, double buffer, int warningDistance) {

        static Border of(WorldBorder border) {
            return new Border(border.getCenter(), border.getSize(), border.getDamageAmount(), border.getDamageBuffer(),
                    border.getWarningDistance());
        }

        /** Worlds saved by older versions hold a size above the most Paper accepts, so only a changed one is set. */
        void restore(WorldBorder border) {
            if (!border.getCenter().equals(center)) {
                border.setCenter(center);
            }
            if (border.getSize() != size) {
                border.setSize(Math.clamp(size, 1.0, border.getMaxSize()));
            }
            border.setDamageAmount(damage);
            border.setDamageBuffer(buffer);
            border.setWarningDistance(warningDistance);
        }
    }

    private final JavaPlugin plugin;
    private final RoundHost<?> host;
    private final Map<UUID, Border> borders = new HashMap<>();
    private final Set<UUID> spawned = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Consumer<World>> worlds = new HashMap<>();
    private final Deque<Runnable> closing = new ArrayDeque<>();
    private volatile boolean open = true;
    private boolean restartAfter;

    RoundScope(JavaPlugin plugin, RoundHost<?> host) {
        this.plugin = plugin;
        this.host = host;
        Bukkit.getWorlds().forEach(world -> borders.put(world.getUID(), Border.of(world.getWorldBorder())));
    }

    RoundHost<?> host() {
        return host;
    }

    /**
     * {@code world} is the round's own and goes with it: emptied, unloaded without saving, then handed to
     * {@code discarded}, which deletes its folder or copies the map into it anew for a later round.
     */
    public World own(World world, Consumer<World> discarded) {
        borders.remove(world.getUID());
        worlds.put(world.getUID(), discarded);
        return world;
    }

    public boolean owns(World world) {
        return worlds.containsKey(world.getUID());
    }

    public void listen(Listener... listeners) {
        for (var listener : listeners) {
            Bukkit.getPluginManager().registerEvents(listener, this);
        }
    }

    /**
     * The round changes what its scope cannot take back, such as blocks of a world that outlasts it, so the server
     * restarts after it as it did before rounds played in place.
     */
    public void restartAfter() {
        restartAfter = true;
    }

    boolean restartsAfter() {
        return restartAfter;
    }

    /** Runs when the round closes, before its registrations go, in the reverse order of registering. */
    public void onClose(Runnable action) {
        closing.push(action);
    }

    /** An entity the round brought into a world that outlasts it, which goes with the round. */
    void spawned(Entity entity) {
        if (open && !owns(entity.getWorld()) && !(entity instanceof Player)) {
            spawned.add(entity.getUniqueId());
        }
    }

    /**
     * The round cannot go on, such as one whose world never got ready: everyone goes back to a lobby and the server
     * restarts, as it did after every round before.
     */
    public void abandon(String reason) {
        if (open) {
            host.abandon(this, reason);
        }
    }

    /**
     * Takes back what the round registered, as far as it can. Everyone must have left the round's own worlds before;
     * false if a step failed that leaves something behind only a restart cleans up, such as a world that could not be
     * unloaded or discarded. Entities left over and borders not put back cost the next round nothing but looks.
     */
    boolean close(Location away) {
        if (!open) {
            return true;
        }
        boolean clean = true;
        while (!closing.isEmpty()) {
            clean &= run(closing.pop());
        }
        // A stopping server needs no one shown again, and Paper lets no disabled plugin hide anything.
        if (plugin.isEnabled()) {
            clean &= run(this::resetVisibility);
        }
        open = false;
        // An async task of a round is a one-shot write, such as a rejoin release, queued for the scheduler's next tick;
        // cancelling it would lose the write, and its continuation on the main thread does nothing once the scope closed.
        clean &= run(() -> Bukkit.getScheduler().getPendingTasks().stream()
                .filter(task -> task.getOwner() == this && task.isSync())
                .forEach(task -> task.cancel()));
        clean &= run(() -> HandlerList.unregisterAll(this));
        clean &= run(() -> {
            var messenger = Bukkit.getMessenger();
            messenger.unregisterOutgoingPluginChannel(this);
            messenger.unregisterIncomingPluginChannel(this);
        });
        for (var world : Bukkit.getWorlds()) {
            clean &= run(() -> world.removePluginChunkTickets(this));
            if (owns(world)) {
                continue;
            }
            run(() -> spawned.stream().map(Bukkit::getEntity)
                    .filter(entity -> entity != null && entity.getWorld().equals(world))
                    .forEach(Entity::remove));
            var border = borders.get(world.getUID());
            if (border != null) {
                run(() -> border.restore(world.getWorldBorder()));
            }
        }
        spawned.clear();
        for (var entry : new ArrayList<>(worlds.entrySet())) {
            var world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            clean &= run(() -> world.getPlayers().forEach(player -> player.teleport(away)));
            clean &= run(() -> {
                if (!Bukkit.unloadWorld(world, false)) {
                    throw new IllegalStateException("Could not unload the round's world " + world.getKey());
                }
                entry.getValue().accept(world);
            });
        }
        worlds.clear();
        return clean;
    }

    /**
     * Undoes what the round changed about who sees whom: Bukkit keeps a plugin's hiding and showing per plugin, and
     * only an enabled plugin may hide. An entity hidden by default stays hidden, one seen by default stays seen.
     */
    private void resetVisibility() {
        var online = List.copyOf(Bukkit.getOnlinePlayers());
        for (var viewer : online) {
            for (var other : online) {
                if (other.isVisibleByDefault()) {
                    viewer.showPlayer(this, other);
                } else {
                    viewer.hidePlayer(this, other);
                }
            }
        }
        for (var world : Bukkit.getWorlds()) {
            if (owns(world)) {
                continue;
            }
            for (var entity : world.getEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                for (var viewer : online) {
                    if (entity.isVisibleByDefault()) {
                        viewer.showEntity(this, entity);
                    } else {
                        viewer.hideEntity(this, entity);
                    }
                }
            }
        }
    }

    private boolean run(Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().warn("Closing the round failed in part", e);
            return false;
        }
    }

    @Override
    public boolean isEnabled() {
        return open && plugin.isEnabled();
    }

    @Override
    public String getName() {
        return plugin.getName();
    }

    @Override
    public String namespace() {
        return plugin.namespace();
    }

    @Override
    public File getDataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public Path getDataPath() {
        return plugin.getDataPath();
    }

    @SuppressWarnings("deprecation")
    @Override
    public PluginDescriptionFile getDescription() {
        return plugin.getDescription();
    }

    @Override
    public PluginMeta getPluginMeta() {
        return plugin.getPluginMeta();
    }

    @Override
    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    @Override
    public InputStream getResource(String filename) {
        return plugin.getResource(filename);
    }

    @Override
    public void saveConfig() {
        plugin.saveConfig();
    }

    @Override
    public void saveDefaultConfig() {
        plugin.saveDefaultConfig();
    }

    @Override
    public void saveResource(String resourcePath, boolean replace) {
        plugin.saveResource(resourcePath, replace);
    }

    @Override
    public void reloadConfig() {
        plugin.reloadConfig();
    }

    @SuppressWarnings({"deprecation", "removal"})
    @Override
    public PluginLoader getPluginLoader() {
        return plugin.getPluginLoader();
    }

    @Override
    public Server getServer() {
        return plugin.getServer();
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onLoad() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public boolean isNaggable() {
        return plugin.isNaggable();
    }

    @Override
    public void setNaggable(boolean canNag) {
        plugin.setNaggable(canNag);
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return plugin.getDefaultWorldGenerator(worldName, id);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(String worldName, String id) {
        return plugin.getDefaultBiomeProvider(worldName, id);
    }

    @Override
    public Logger getLogger() {
        return plugin.getLogger();
    }

    @Override
    public LifecycleEventManager<Plugin> getLifecycleManager() {
        return plugin.getLifecycleManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    @Override
    public String toString() {
        return plugin.getName() + " round";
    }
}
