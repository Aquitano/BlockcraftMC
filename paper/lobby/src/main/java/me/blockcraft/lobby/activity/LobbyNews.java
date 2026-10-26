package me.blockcraft.lobby.activity;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A boss bar over the lobby that cycles through the lines of {@code news.lines}, one every {@code seconds}: what is
 * new, what is on this week. The bar empties while a line shows, so the next one does not come as a surprise.
 */
public final class LobbyNews implements Listener, AutoCloseable {

    private static final long TICKS = 10L;

    private final Messages messages;
    private final BossBar bar;
    private final int ticksPerLine;
    private final BukkitTask task;
    private int line;
    private int shown;

    private LobbyNews(Plugin plugin, Messages messages, BossBar.Color color, int seconds) {
        this.messages = messages;
        this.ticksPerLine = seconds * 20;
        this.bar = BossBar.bossBar(current(), 1.0f, color, BossBar.Overlay.PROGRESS);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICKS, TICKS);
        Bukkit.getOnlinePlayers().forEach(player -> player.showBossBar(bar));
    }

    /** The news, or nothing when {@code news.enabled} is off or messages.yml has no lines. */
    public static Optional<LobbyNews> start(Plugin plugin, Messages messages, Map<String, Object> config) {
        if (Boolean.FALSE.equals(config.get("enabled")) || messages.getLines("news.lines").isEmpty()) {
            return Optional.empty();
        }
        var color = BossBar.Color.NAMES.valueOr(String.valueOf(config.getOrDefault("color", "purple")).toLowerCase(Locale.ROOT),
                BossBar.Color.PURPLE);
        int seconds = Math.clamp(config.get("seconds") instanceof Number number ? number.intValue() : 12, 3, 600);
        return Optional.of(new LobbyNews(plugin, messages, color, seconds));
    }

    private Component current() {
        var lines = messages.getLines("news.lines");
        return lines.get(Math.floorMod(line, lines.size()));
    }

    private void tick() {
        shown += (int) TICKS;
        if (shown >= ticksPerLine) {
            shown = 0;
            line++;
            bar.name(current());
        }
        bar.progress(Math.clamp(1.0f - (float) shown / ticksPerLine, 0.0f, 1.0f));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().showBossBar(bar);
    }

    @Override
    public void close() {
        task.cancel();
        Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(bar));
    }
}
