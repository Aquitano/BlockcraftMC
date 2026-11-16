package me.blockcraft.api.presentation;

import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.scene.Scenes;
import me.blockcraft.api.xp.LevelUpEvent;
import me.blockcraft.common.config.YamlFile;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VisualEffects implements Listener, AutoCloseable {
    public enum MaterialEffect {
        HOLOGRAM(1), PLASMA(2), PULSE(3),
        /** The model's own colours at full light, without the world's shading of item faces. */
        UNLIT(4);
        private final int marker;
        MaterialEffect(int marker) { this.marker = marker; }
        public Color color() { return Color.fromRGB(233, marker, 197); }
    }

    private final Plugin plugin;
    private final ResourcePacks packs;
    private final boolean gameplayEnabled;
    private final double gameplayIntensity;
    private final int gameplayFadeTicks;
    private final int gameplayCooldownTicks;
    private final int levelUpTicks;
    private final Map<UUID, Accent> accents = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final BukkitTask task;
    private long tick;
    private boolean closed;

    public VisualEffects(Plugin plugin, ResourcePacks packs, Map<String, Object> presentation) {
        this.plugin = plugin;
        this.packs = packs;
        var gameplay = YamlFile.section(presentation, "gameplay");
        gameplayEnabled = !Boolean.FALSE.equals(gameplay.get("enabled"));
        gameplayIntensity = bounded(gameplay, "intensity", 0.5, 0, 1);
        gameplayFadeTicks = (int) bounded(gameplay, "fade-ticks", 6, 1, 40);
        gameplayCooldownTicks = (int) bounded(gameplay, "cooldown-ticks", 40, 0, 1200);
        levelUpTicks = (int) bounded(gameplay, "level-up-ticks", 24, 0, 200);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private static double bounded(Map<String, Object> config, String key, double fallback, double min, double max) {
        double value = config.get(key) instanceof Number number ? number.doubleValue() : fallback;
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }

    public boolean accent(Player player, ScreenEffects.Atmosphere atmosphere, int durationTicks) {
        if (closed || !gameplayEnabled || gameplayIntensity == 0 || durationTicks <= 0 || !player.isOnline()
                || player.isDead() || !packs.applied(player) || Scenes.isViewing(player)
                || accents.containsKey(player.getUniqueId())
                || cooldowns.getOrDefault(player.getUniqueId(), 0L) > tick) return false;
        accents.put(player.getUniqueId(), new Accent(player, player.getWorld(), atmosphere, Math.min(durationTicks, 200)));
        cooldowns.put(player.getUniqueId(), tick + gameplayCooldownTicks);
        return true;
    }

    public boolean accent(PresentationScope owner, Player player, ScreenEffects.Atmosphere atmosphere, int durationTicks) {
        if (owner.closed() || !accent(player, atmosphere, durationTicks)) return false;
        var accent = accents.get(player.getUniqueId());
        owner.own(() -> {
            if (accents.remove(player.getUniqueId(), accent)) ScreenEffects.clearAccent(player);
        });
        return true;
    }

    public void clearAccent(Player player) {
        if (accents.remove(player.getUniqueId()) != null) ScreenEffects.clearAccent(player);
    }

    static double accentIntensity(int age, int duration, int fadeTicks, double peak) {
        int fade = Math.min(fadeTicks, Math.max(1, duration / 2));
        double envelope = Math.clamp(Math.min((double) age / fade, (double) (duration - age) / fade), 0, 1);
        return peak * envelope * envelope * (3 - 2 * envelope);
    }

    private void tick() {
        tick++;
        cooldowns.values().removeIf(until -> until <= tick);
        for (var accent : List.copyOf(accents.values())) {
            var player = accent.player;
            if (!player.isOnline() || player.isDead() || !player.getWorld().equals(accent.world)
                    || !packs.applied(player) || Scenes.isViewing(player) || ++accent.age >= accent.duration) {
                clearAccent(player);
                continue;
            }
            try {
                ScreenEffects.accent(player, accent.atmosphere,
                        accentIntensity(accent.age, accent.duration, gameplayFadeTicks, gameplayIntensity));
            } catch (RuntimeException failure) {
                plugin.getSLF4JLogger().warn("Gameplay screen effect failed", failure);
                clearAccent(player);
            }
        }
    }

    @EventHandler
    public void onLevelUp(LevelUpEvent event) {
        if (levelUpTicks > 0) accent(event.getPlayer(), ScreenEffects.Atmosphere.FOCUS, levelUpTicks);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearAccent(event.getPlayer());
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) { clearAccent(event.getPlayer()); }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) { clearAccent(event.getPlayer()); }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) close();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        task.cancel();
        for (var accent : List.copyOf(accents.values())) clearAccent(accent.player);
        cooldowns.clear();
        HandlerList.unregisterAll(this);
    }

    private static final class Accent {
        final Player player;
        final World world;
        final ScreenEffects.Atmosphere atmosphere;
        final int duration;
        int age;

        Accent(Player player, World world, ScreenEffects.Atmosphere atmosphere, int duration) {
            this.player = player;
            this.world = world;
            this.atmosphere = atmosphere;
            this.duration = duration;
        }
    }
}
