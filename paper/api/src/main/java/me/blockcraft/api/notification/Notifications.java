package me.blockcraft.api.notification;

import me.blockcraft.api.pack.DisplayFont;
import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.xp.LevelUpEvent;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Consumer;

import static me.blockcraft.api.notification.NoticeQueue.Priority;

public final class Notifications implements Listener {
    public record Timing(long status, long feedback, long reward, long important, long maxWait,
                         long xpWindow, long soundWindow, long soundGap, long soundLifetime) {
        public static Timing from(Map<String, Object> config) {
            return new Timing(ticks(config, "status-ticks", 25), ticks(config, "feedback-ticks", 40),
                    ticks(config, "reward-ticks", 40), ticks(config, "important-ticks", 60),
                    ticks(config, "max-wait-ticks", 200), ticks(config, "xp-window-ticks", 10),
                    ticks(config, "sound-window-ticks", 4), ticks(config, "sound-gap-ticks", 30),
                    ticks(config, "sound-lifetime-ticks", 40));
        }

        private static long ticks(Map<String, Object> config, String key, long fallback) {
            return Math.clamp(config.get(key) instanceof Number value ? value.longValue() : fallback, 1, 1200);
        }
    }

    private record TitleNotice(Title title, Sounds sound) {}
    private record Marker(Component text, long until) {}

    private static final class State {
        final NoticeQueue<Component> bar = new NoticeQueue<>();
        final NoticeQueue<TitleNotice> titles = new NoticeQueue<>();
        final RewardBatch xp = new RewardBatch();
        NoticeQueue.Notice<Component> shownBar;
        NoticeQueue.Notice<TitleNotice> shownTitle;
        Marker marker;
        Marker shownMarker;
        final RewardSounds sounds;
        long barSent;
        Component instantBar;
        long instantUntil;

        State(Timing timing) {
            sounds = new RewardSounds(timing.soundWindow(), timing.soundGap(), timing.soundLifetime());
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final ResourcePacks resourcePacks;
    private final Timing timing;
    private final Map<UUID, State> players = new HashMap<>();
    private BukkitTask task;
    private Predicate<Player> suspended = ignored -> false;
    private Consumer<Player> levelUpEffect = ignored -> {};

    public Notifications(Plugin plugin, Messages messages, ResourcePacks resourcePacks, Timing timing) {
        this.plugin = plugin;
        this.messages = messages;
        this.resourcePacks = resourcePacks;
        this.timing = timing;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void suspendWhile(Predicate<Player> predicate) {
        suspended = predicate;
    }

    public void levelUpEffect(Consumer<Player> effect) {
        levelUpEffect = effect;
    }

    @EventHandler
    public void onLevelUp(LevelUpEvent event) {
        levelUpEffect.accept(event.getPlayer());
    }

    public void status(Player player, Component message) {
        bar(player, message, Priority.STATUS, timing.status());
    }

    public void feedback(Player player, Component message) {
        bar(player, message, Priority.FEEDBACK, timing.feedback());
    }

    public void feedbackNow(Player player, Component message, long ticks) {
        if (player.isOnline() && !suspended.test(player)) {
            var state = state(player);
            state.instantBar = message;
            state.instantUntil = now() + Math.max(1, ticks);
            state.shownBar = null;
            player.sendActionBar(message);
        }
    }

    public void markerNow(Player player, Component marker, long ticks) {
        if (!player.isOnline() || suspended.test(player)) return;
        var state = state(player);
        long now = now();
        state.marker = new Marker(marker, now + Math.clamp(ticks, 1, 1200));
        showTitle(player, state, now);
    }

    public void clearMarker(Player player) {
        var state = players.get(player.getUniqueId());
        if (state == null || state.marker == null) return;
        state.marker = null;
        if (player.isOnline() && !suspended.test(player)) showTitle(player, state, now());
        else state.shownMarker = null;
    }

    public void reward(Player player, Component message) {
        bar(player, message, Priority.REWARD, timing.reward());
    }

    public void important(Player player, Component message) {
        bar(player, message, Priority.IMPORTANT, timing.important());
    }

    public void important(Player player, Title title) {
        title(player, title, null, Priority.IMPORTANT);
    }

    public void important(Player player, Title title, Sounds sound) {
        title(player, title, sound, Priority.IMPORTANT);
    }

    public void reward(Player player, Title title, Sounds sound) {
        title(player, title, sound, Priority.REWARD);
    }

    public void xp(Player player, int amount, int reachedLevel) {
        if (player.isOnline() && amount > 0) {
            state(player).xp.add(amount, reachedLevel, now(), timing.xpWindow());
        }
    }

    public void rewardSound(Player player, Sounds sound) {
        if (!player.isOnline()) {
            return;
        }
        state(player).sounds.offer(sound, now());
    }

    private void bar(Player player, Component message, Priority priority, long duration) {
        if (player.isOnline()) {
            state(player).bar.offer(message, priority, now(), duration,
                    priority == Priority.STATUS ? timing.status() : timing.maxWait());
        }
    }

    private void title(Player player, Title title, Sounds sound, Priority priority) {
        if (player.isOnline()) {
            var times = title.times();
            if (times == null) {
                times = Title.Times.times(Duration.ZERO, Duration.ofMillis(timing.important() * 50), Duration.ZERO);
                title = Title.title(title.title(), title.subtitle(), times);
            }
            long duration = Math.max(1, times.fadeIn().plus(times.stay()).plus(times.fadeOut()).toMillis() / 50);
            state(player).titles.offer(new TitleNotice(title, sound), priority, now(), duration, timing.maxWait());
        }
    }

    /** Decided when the title shows rather than when it was queued, since the pack can arrive in between. */
    private Title drawnFor(Player player, Title title) {
        if (resourcePacks.applied(player)) {
            return title;
        }
        return Title.title(DisplayFont.plain(title.title()), DisplayFont.plain(title.subtitle()), title.times());
    }

    private State state(Player player) {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
        return players.computeIfAbsent(player.getUniqueId(), ignored -> new State(timing));
    }

    private void tick() {
        long now = now();
        var entries = players.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            var player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                entries.remove();
                continue;
            }
            var state = entry.getValue();
            var xp = state.xp.take(now);
            if (xp != null) {
                reward(player, messages.get("xp.gained", Formatter.number("amount", xp.amount())));
                if (xp.level() > 0) {
                    player.sendMessage(messages.get("xp.level-up", Formatter.number("level", xp.level())));
                    rewardSound(player, Sounds.LEVEL_UP);
                }
            }
            if (suspended.test(player)) {
                state.shownBar = null;
                state.shownTitle = null;
                state.marker = null;
                state.shownMarker = null;
            } else {
                showTitle(player, state, now);
                showBar(player, state, now);
            }
            var sound = state.sounds.take(now);
            if (sound != null) {
                sound.play(player);
            }
            if (state.instantBar == null && state.marker == null && state.bar.isEmpty() && state.titles.isEmpty()
                    && state.xp.isEmpty() && state.sounds.isIdle(now)) {
                entries.remove();
            }
        }
        if (players.isEmpty()) {
            task.cancel();
            task = null;
        }
    }

    private void showBar(Player player, State state, long now) {
        if (state.instantBar != null) {
            if (now < state.instantUntil) {
                return;
            }
            state.instantBar = null;
            player.sendActionBar(Component.empty());
        }
        var selected = state.bar.next(now).orElse(null);
        if (selected == null) {
            if (state.shownBar != null) {
                player.sendActionBar(Component.empty());
            }
        } else if (selected != state.shownBar || now - state.barSent >= 20) {
            player.sendActionBar(selected.value);
            state.barSent = now;
        }
        state.shownBar = selected;
    }

    private void showTitle(Player player, State state, long now) {
        var selected = state.titles.next(now).orElse(null);
        if (state.marker != null && now >= state.marker.until()) state.marker = null;
        var marker = state.marker;
        if (selected != null && selected != state.shownTitle) {
            var title = drawnFor(player, selected.value.title());
            player.showTitle(marker == null ? title : Title.title(title.title(), subtitle(title, marker.text()), title.times()));
            if (selected.priority == Priority.IMPORTANT) {
                state.sounds.blockUntil(selected.until);
                if (selected.value.sound() != null) {
                    selected.value.sound().play(player);
                }
            } else if (selected.value.sound() != null) {
                rewardSound(player, selected.value.sound());
            }
        } else if (marker != state.shownMarker || marker != null && selected != state.shownTitle) {
            if (selected != null) {
                var title = drawnFor(player, selected.value.title());
                player.sendTitlePart(TitlePart.SUBTITLE, marker == null ? title.subtitle() : subtitle(title, marker.text()));
            } else if (marker != null) {
                player.showTitle(Title.title(Component.empty(), marker.text(), Title.Times.times(Duration.ZERO,
                        Duration.ofMillis((marker.until() - now) * 50), Duration.ZERO)));
            } else if (state.shownMarker != null) {
                player.clearTitle();
            }
        }
        state.shownTitle = selected;
        state.shownMarker = marker;
    }

    /** A marker replaces a title's subtitle for a moment, but joins a one-line notice, which must not inherit the marker's font. */
    static Component subtitle(Title title, Component marker) {
        return title.title().equals(Component.empty()) && !title.subtitle().equals(Component.empty())
                ? Component.empty().append(marker).append(title.subtitle()) : marker;
    }

    private static long now() {
        return Integer.toUnsignedLong(Bukkit.getCurrentTick());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        players.remove(event.getPlayer().getUniqueId());
    }

    public void close() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        players.clear();
    }
}
