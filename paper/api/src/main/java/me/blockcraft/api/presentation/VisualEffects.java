package me.blockcraft.api.presentation;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.scene.Scenes;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public final class VisualEffects implements Listener, AutoCloseable {
    public enum Style { HOLOGRAM, PLASMA, PULSE, TEXT, CINEMATIC, HOLOSCREEN, STORM, PORTAL, FOCUS }
    public enum MaterialEffect {
        HOLOGRAM(1), PLASMA(2), PULSE(3);
        private final int marker;
        MaterialEffect(int marker) { this.marker = marker; }
        public Color color() { return Color.fromRGB(233, marker, 197); }
    }

    public record Settings(int durationTicks, double distance, double radius, double bobHeight, double spinDegrees,
                           int fadeTicks, double textSpacing) {
        public static Settings from(Map<String, Object> presentation) {
            var config = YamlFile.section(presentation, "effects");
            return new Settings((int) bounded(config, "duration-ticks", 200, 40, 1200),
                    bounded(config, "distance", 4, 2, 12), bounded(config, "radius", 0.8, 0.1, 3),
                    bounded(config, "bob-height", 0.15, 0, 1), bounded(config, "spin-degrees", 2, 0, 20),
                    (int) bounded(config, "fade-ticks", 20, 1, 100), bounded(config, "text-spacing", 0.35, 0.2, 1));
        }

        private static double bounded(Map<String, Object> config, String key, double fallback, double min, double max) {
            double value = config.get(key) instanceof Number number ? number.doubleValue() : fallback;
            return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
        }
    }

    private final Plugin plugin;
    private final ResourcePacks packs;
    private final Messages messages;
    private final Settings settings;
    private final boolean gameplayEnabled;
    private final double gameplayIntensity;
    private final int gameplayFadeTicks;
    private final int gameplayCooldownTicks;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Accent> accents = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final BukkitTask task;
    private long tick;
    private boolean closed;
    private Predicate<Player> unavailable = ignored -> false;

    public VisualEffects(Plugin plugin, ResourcePacks packs, Messages messages, Map<String, Object> presentation) {
        this.plugin = plugin;
        this.packs = packs;
        this.messages = messages;
        settings = Settings.from(presentation);
        var gameplay = YamlFile.section(presentation, "gameplay");
        gameplayEnabled = !Boolean.FALSE.equals(gameplay.get("enabled"));
        gameplayIntensity = Settings.bounded(gameplay, "intensity", 0.5, 0, 1);
        gameplayFadeTicks = (int) Settings.bounded(gameplay, "fade-ticks", 6, 1, 40);
        gameplayCooldownTicks = (int) Settings.bounded(gameplay, "cooldown-ticks", 40, 0, 1200);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void unavailableWhen(Predicate<Player> unavailable) {
        this.unavailable = unavailable;
    }

    public boolean active(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean accent(Player player, ScreenEffects.Atmosphere atmosphere, int durationTicks) {
        if (closed || !gameplayEnabled || gameplayIntensity == 0 || durationTicks <= 0 || !player.isOnline()
                || player.isDead() || !packs.applied(player) || active(player) || Scenes.isViewing(player)
                || unavailable.test(player) || accents.containsKey(player.getUniqueId())
                || cooldowns.getOrDefault(player.getUniqueId(), 0L) > tick) return false;
        accents.put(player.getUniqueId(), new Accent(player, player.getWorld(), atmosphere, Math.min(durationTicks, 200)));
        cooldowns.put(player.getUniqueId(), tick + gameplayCooldownTicks);
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

    public boolean demo(Player player, Style style) {
        var origin = player.getEyeLocation();
        origin.add(origin.getDirection().multiply(settings.distance()));
        origin.setYaw(0);
        origin.setPitch(0);
        return play(player, style, origin, settings.durationTicks());
    }

    public boolean play(Player player, Style style, Location origin, int durationTicks) {
        if (closed || !player.isOnline() || player.isDead() || !player.getWorld().equals(origin.getWorld())) return false;
        if (active(player) || Scenes.isViewing(player) || unavailable.test(player)) {
            player.sendMessage(messages.get("presentation.busy"));
            return false;
        }
        if (!packs.applied(player)) {
            player.sendMessage(messages.get("presentation.effects.pack-required"));
            return false;
        }
        clearAccent(player);
        var session = new Session(player, style, origin.clone(), Math.clamp(durationTicks, 40, 1200));
        sessions.put(player.getUniqueId(), session);
        try {
            switch (style) {
                case HOLOGRAM, PLASMA, PULSE -> spawnModels(session, MaterialEffect.valueOf(style.name()));
                case TEXT -> spawnText(session);
                case CINEMATIC, HOLOSCREEN, STORM, PORTAL, FOCUS -> { }
            }
            render(session);
            return true;
        } catch (RuntimeException failure) {
            plugin.getSLF4JLogger().warn("Could not start visual effect", failure);
            stop(player);
            return false;
        }
    }

    public static ItemStack model(String model, MaterialEffect effect) {
        var item = Items.of(Material.PAPER).model(Key.key("blockcraft", model)).build();
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColor(effect.color()).build());
        return item;
    }

    private void spawnModels(Session session, MaterialEffect effect) {
        for (var model : List.of("ring", "crystal", "flare")) {
            var entity = session.origin.getWorld().spawn(session.origin, ItemDisplay.class, display -> {
                setup(display);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.setItemStack(model("presentation/" + model, effect));
            });
            session.entities.add(entity);
            session.player.showEntity(plugin, entity);
        }
    }

    private void spawnText(Session session) {
        var motions = TextMotion.values();
        for (int i = 0; i < motions.length; i++) {
            var motion = motions[i];
            var at = session.origin.clone().add(0, (motions.length / 2.0 - i) * settings.textSpacing(), 0);
            var entity = at.getWorld().spawn(at, TextDisplay.class, display -> {
                setup(display);
                display.setBillboard(Display.Billboard.CENTER);
                display.setBackgroundColor(Color.fromARGB(100, 0, 0, 0));
                display.setShadowed(false);
                display.text(motion.apply(messages.get("presentation.effects.text." + motion.name().toLowerCase(Locale.ROOT))));
            });
            session.entities.add(entity);
            session.player.showEntity(plugin, entity);
        }
    }

    private static void setup(Display display) {
        display.setVisibleByDefault(false);
        display.setPersistent(false);
        display.setInterpolationDuration(1);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setDisplayWidth(8);
        display.setDisplayHeight(8);
    }

    private void tick() {
        tick++;
        cooldowns.values().removeIf(until -> until <= tick);
        for (var accent : List.copyOf(accents.values())) {
            var player = accent.player;
            if (!player.isOnline() || player.isDead() || !player.getWorld().equals(accent.world)
                    || !packs.applied(player) || active(player) || Scenes.isViewing(player) || unavailable.test(player)
                    || ++accent.age >= accent.duration) {
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
        for (var session : List.copyOf(sessions.values())) {
            if (!session.player.isOnline() || !session.player.getWorld().equals(session.origin.getWorld())
                    || !packs.applied(session.player) || ++session.tick >= session.duration) {
                stop(session.player);
                continue;
            }
            try {
                render(session);
            } catch (RuntimeException failure) {
                plugin.getSLF4JLogger().warn("Visual effect failed", failure);
                stop(session.player);
            }
        }
    }

    private void render(Session session) {
        switch (session.style) {
            case HOLOGRAM, PLASMA, PULSE -> {
                float angle = (float) Math.toRadians(session.tick * settings.spinDegrees());
                float bob = (float) (Math.sin(session.tick * 0.08) * settings.bobHeight());
                for (int i = 0; i < session.entities.size(); i++) {
                    var display = (ItemDisplay) session.entities.get(i);
                    float orbit = angle + i * (float) (Math.PI * 2 / 3);
                    var translation = new Vector3f((float) Math.cos(orbit) * (float) settings.radius(), bob,
                            (float) Math.sin(orbit) * (float) settings.radius());
                    display.setInterpolationDelay(0);
                    display.setTransformation(new Transformation(translation, new Quaternionf().rotationY(angle), new Vector3f(1), new Quaternionf()));
                }
            }
            case TEXT -> { }
            case CINEMATIC -> {
                int fadeTicks = Math.min(settings.fadeTicks(), session.duration / 2);
                double fade = Math.max(1.0 - (double) session.tick / fadeTicks,
                        1.0 - (double) (session.duration - session.tick) / fadeTicks);
                ScreenEffects.frame(session.player, Math.clamp(fade, 0, 1), true);
            }
            case HOLOSCREEN -> ScreenEffects.atmosphere(session.player, ScreenEffects.Atmosphere.HOLOGRAM);
            case STORM -> ScreenEffects.atmosphere(session.player, ScreenEffects.Atmosphere.STORM);
            case PORTAL -> ScreenEffects.atmosphere(session.player, ScreenEffects.Atmosphere.PORTAL);
            case FOCUS -> ScreenEffects.atmosphere(session.player, ScreenEffects.Atmosphere.FOCUS);
        }
    }

    public void stop(Player player) {
        clearAccent(player);
        var session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.entities.forEach(Entity::remove);
        if (session.style == Style.CINEMATIC || session.style == Style.HOLOSCREEN || session.style == Style.STORM || session.style == Style.PORTAL || session.style == Style.FOCUS) {
            ScreenEffects.clear(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) { stop(event.getPlayer()); }

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
        for (var session : List.copyOf(sessions.values())) stop(session.player);
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

    private static final class Session {
        final Player player;
        final Style style;
        final Location origin;
        final int duration;
        final List<Entity> entities = new ArrayList<>();
        int tick;

        Session(Player player, Style style, Location origin, int duration) {
            this.player = player;
            this.style = style;
            this.origin = origin;
            this.duration = duration;
        }
    }
}
