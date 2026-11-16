package me.blockcraft.story.sound;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.story.debug.Trace;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The party's music and ambience, one track on each channel. A track loops by starting again once its recording has
 * played, as long as {@code soundtrack.properties} knows its length; a track in {@code soundtrack.once} plays once.
 * A member who joins, comes back or leaves a cutscene hears the current tracks from their start, and nobody hears one
 * before their resource pack has loaded, since a sound sent earlier is lost.
 */
public final class Soundtrack implements AutoCloseable {

    public enum Channel {
        MUSIC("music", Sound.Source.MUSIC), AMBIENCE("ambience", Sound.Source.AMBIENT);

        private final String key;
        private final Sound.Source source;

        Channel(String key, Sound.Source source) {
            this.key = key;
            this.source = source;
        }

        public String key() {
            return key;
        }
    }

    public record Settings(Map<Channel, Float> volume, Set<String> once) {
        public static Settings from(ConfigurationSection section) {
            var volume = new EnumMap<Channel, Float>(Channel.class);
            for (var channel : Channel.values()) volume.put(channel, (float) section.getDouble(channel.key() + "-volume", 0.5));
            return new Settings(volume, Set.copyOf(section.getStringList("once")));
        }
    }

    private record Track(String name, Key sound, Optional<Integer> ticks, boolean loops) { }

    private final Plugin plugin;
    private final Trace trace;
    private final Supplier<List<Player>> listeners;
    private final Map<String, Integer> lengths;
    private final Map<Channel, Track> playing = new EnumMap<>(Channel.class);
    private final Map<Channel, Map<UUID, Long>> next = new EnumMap<>(Channel.class);
    private final BukkitTask task;
    private Settings settings;

    public Soundtrack(Plugin plugin, Trace trace, Settings settings, Supplier<List<Player>> listeners) {
        this.plugin = plugin;
        this.trace = trace;
        this.settings = settings;
        this.listeners = listeners;
        this.lengths = lengths(plugin);
        for (var channel : Channel.values()) next.put(channel, new HashMap<>());
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }

    private static Map<String, Integer> lengths(Plugin plugin) {
        var properties = new Properties();
        try (var input = plugin.getResource("soundtrack.properties")) {
            if (input != null) properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read soundtrack.properties", failure);
        }
        var lengths = new HashMap<String, Integer>();
        properties.stringPropertyNames().forEach(key -> lengths.put(key, Integer.parseInt(properties.getProperty(key).trim())));
        return Map.copyOf(lengths);
    }

    public void settings(Settings settings) {
        this.settings = settings;
    }

    /** Starts {@code name} on the channel, unless it is already playing there. */
    public void play(Channel channel, String name) {
        var current = playing.get(channel);
        if (current != null && current.name().equals(name)) return;
        stop(channel);
        var key = channel.key() + "." + name;
        var ticks = Optional.ofNullable(lengths.get(key));
        if (ticks.isEmpty()) plugin.getSLF4JLogger().warn("soundtrack.properties has no length for {}, so it plays once", key);
        playing.put(channel, new Track(name, Key.key("blockcraft", "story." + key), ticks, ticks.isPresent() && !settings.once().contains(key)));
        trace.event("sound", channel.key() + " " + name);
        tick();
    }

    public void stop(Channel channel) {
        var current = playing.remove(channel);
        next.get(channel).clear();
        if (current == null) return;
        listeners.get().forEach(listener -> listener.stopSound(SoundStop.named(current.sound())));
        trace.event("sound", channel.key() + " stopped");
    }

    public void stopAll() {
        for (var channel : Channel.values()) stop(channel);
    }

    /** {@code player} hears the current looping tracks again from their start, as a cutscene has cut its ambience off. */
    public void restart(Player player) {
        playing.forEach((channel, track) -> {
            if (!track.loops()) return;
            player.stopSound(SoundStop.named(track.sound()));
            next.get(channel).remove(player.getUniqueId());
        });
    }

    public Optional<String> playing(Channel channel) {
        return Optional.ofNullable(playing.get(channel)).map(Track::name);
    }

    private void tick() {
        long now = Bukkit.getCurrentTick();
        var pack = BlockcraftApi.get().resourcePack();
        for (var listener : listeners.get()) {
            if (!pack.applied(listener)) {
                restart(listener);
                continue;
            }
            playing.forEach((channel, track) -> {
                var due = next.get(channel);
                var at = due.get(listener.getUniqueId());
                if (at != null && (now < at || !track.loops())) return;
                listener.playSound(Sound.sound(track.sound(), channel.source, settings.volume().get(channel), 1), Sound.Emitter.self());
                due.put(listener.getUniqueId(), track.loops() ? now + track.ticks().orElseThrow() : Long.MAX_VALUE);
            });
        }
    }

    @Override
    public void close() {
        task.cancel();
        stopAll();
    }
}
