package me.blockcraft.story.game;

import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import me.blockcraft.api.presentation.ScreenEffects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The screen between two chapters: it fades to black under the next chapter's name, stays black while that chapter's
 * world loads and the members are moved there, and fades in once every member's client has loaded it.
 */
final class LoadingScreen implements Listener, AutoCloseable {

    record Settings(int fadeTicks, int holdTicks, int readyTimeoutTicks) { }

    private final Plugin plugin;
    private final Settings settings;
    private final Set<UUID> arriving = new HashSet<>();
    private BukkitTask task;

    LoadingScreen(Plugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Fades the members' screens to black under {@code title} and {@code subtitle}, keeps them black a moment, then runs {@code covered}. */
    void cover(Supplier<List<Player>> members, Component title, Component subtitle, Runnable covered) {
        stop();
        var shown = Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(250), Duration.ofMinutes(5), Duration.ofMillis(500)));
        members.get().forEach(member -> member.showTitle(shown));
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick;

            @Override
            public void run() {
                tick++;
                members.get().forEach(member -> ScreenEffects.frame(member, Math.min(1, (double) tick / settings.fadeTicks()), false));
                if (tick >= settings.fadeTicks() + settings.holdTicks()) {
                    stop();
                    covered.run();
                }
            }
        }, 1, 1);
    }

    /** The members have just been moved: once each one's client has loaded the world, or after the timeout, the screens fade in. */
    void reveal(Supplier<List<Player>> members, Runnable revealed) {
        stop();
        members.get().forEach(member -> arriving.add(member.getUniqueId()));
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick;
            private int fading = -1;

            @Override
            public void run() {
                tick++;
                if (fading < 0 && (tick >= settings.readyTimeoutTicks()
                        || members.get().stream().noneMatch(member -> arriving.contains(member.getUniqueId())))) {
                    fading = 0;
                    members.get().forEach(Player::clearTitle);
                }
                if (fading < 0) return;
                fading++;
                members.get().forEach(member -> ScreenEffects.frame(member, Math.max(0, 1 - (double) fading / settings.fadeTicks()), false));
                if (fading >= settings.fadeTicks()) {
                    stop();
                    arriving.clear();
                    revealed.run();
                }
            }
        }, 1, 1);
    }

    @EventHandler
    public void onLoaded(PlayerClientLoadedWorldEvent event) {
        arriving.remove(event.getPlayer().getUniqueId());
    }

    private void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    /** Ends a loading screen at once, with clear screens. */
    void clear(List<Player> members) {
        stop();
        arriving.clear();
        members.forEach(member -> {
            ScreenEffects.frame(member, 0, false);
            member.clearTitle();
        });
    }

    @Override
    public void close() {
        stop();
    }
}
