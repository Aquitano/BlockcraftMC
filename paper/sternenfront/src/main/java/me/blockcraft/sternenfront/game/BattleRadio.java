package me.blockcraft.sternenfront.game;

import me.blockcraft.api.presentation.PresentationScope;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class BattleRadio implements AutoCloseable {
    enum Event {
        ASSAULT("assault"), LAST_STAND("last-stand"), CAPTURED("captured"), LOST("lost");

        final String key;

        Event(String key) {
            this.key = key;
        }

        boolean act() {
            return this == ASSAULT || this == LAST_STAND;
        }
    }

    record Call(Event event, Faction faction, int post) {
        String sound() {
            return "blockcraft:sternenfront.radio." + faction.key() + "." + event.key;
        }

        String message() {
            return "radio." + faction.key() + "." + event.key;
        }
    }

    record Listener(Player player, Faction faction) { }

    private final Plugin plugin;
    private final ConfigurationSection config;
    private final BiConsumer<Player, Call> caption;
    private final Map<Faction, Call> pending = new EnumMap<>(Faction.class);
    private final Map<UUID, PresentationScope> playing = new HashMap<>();
    private long speechUntil;
    private long postAt;

    BattleRadio(Plugin plugin, ConfigurationSection config, BiConsumer<Player, Call> caption) {
        this.plugin = plugin;
        this.config = config;
        this.caption = caption;
    }

    void act(Event event, int post) {
        pending.clear();
        if (!enabled(event)) return;
        for (var faction : Faction.values()) pending.put(faction, new Call(event, faction, post));
    }

    void post(Event event, Faction faction, int post, long tick) {
        if (!enabled(event) || tick < postAt || tick < speechUntil
                || pending.values().stream().anyMatch(call -> call.event().act())) return;
        pending.putIfAbsent(faction, new Call(event, faction, post));
    }

    private boolean enabled(Event event) {
        return config.getBoolean("radio.enabled") && config.getBoolean("radio.events." + event.key)
                && (config.getBoolean("radio.captions") || config.getBoolean("radio.speech") && volume() > 0);
    }

    public boolean infantryReady(long tick) {
        return tick >= speechUntil && pending.isEmpty();
    }

    public void infantrySpoke(long tick) {
        speechUntil = tick + Math.clamp(config.getInt("radio.infantry-ticks"), 50, 200);
    }

    void tick(long tick, List<Listener> listeners) {
        var present = listeners.stream().map(listener -> listener.player().getUniqueId()).toList();
        for (var id : List.copyOf(playing.keySet())) {
            if (tick >= speechUntil || !present.contains(id)) forget(id);
        }
        if (tick < speechUntil || pending.isEmpty()) return;
        boolean delivered = false;
        for (var listener : listeners) {
            var call = pending.get(listener.faction());
            if (call == null || !enabled(call.event())) continue;
            var player = listener.player();
            if (config.getBoolean("radio.speech") && volume() > 0) {
                var scope = new PresentationScope(plugin);
                playing.put(player.getUniqueId(), scope);
                scope.own(() -> player.stopSound(call.sound(), SoundCategory.VOICE));
                player.playSound(player, call.sound(), SoundCategory.VOICE, volume(), 1);
            }
            if (config.getBoolean("radio.captions")) caption.accept(player, call);
            delivered = true;
        }
        pending.clear();
        if (delivered) {
            speechUntil = tick + Math.clamp(config.getInt("radio.call-ticks"), 80, 200);
            postAt = tick + Math.clamp(config.getInt("radio.post-cooldown-ticks"), 80, 2400);
        }
    }

    private float volume() {
        double value = config.getDouble("radio.volume");
        return Double.isFinite(value) ? (float) Math.clamp(value, 0, 1) : 0;
    }

    void forget(Player player) {
        forget(player.getUniqueId());
    }

    private void forget(UUID id) {
        var scope = playing.remove(id);
        if (scope != null) scope.close();
    }

    @Override
    public void close() {
        playing.values().forEach(PresentationScope::close);
        playing.clear();
        pending.clear();
        speechUntil = 0;
        postAt = 0;
    }
}
