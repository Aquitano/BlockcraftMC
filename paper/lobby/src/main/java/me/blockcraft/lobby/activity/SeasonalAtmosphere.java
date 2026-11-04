package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.config.SeasonTheme;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** The sounds and falling particles of the active {@link SeasonTheme}s around every lobby player. */
public final class SeasonalAtmosphere implements AutoCloseable {

    private static final long PERIOD_TICKS = 10L;
    private static final double PARTICLE_RADIUS = 10;
    private static final double PARTICLE_HEIGHT = 8;
    private static final double SOUND_DISTANCE = 6;

    private final NetworkCalendar calendar;
    private final Map<Occasion, SeasonTheme> themes;
    private final BukkitTask task;

    public SeasonalAtmosphere(Plugin plugin, NetworkCalendar calendar, Map<Occasion, SeasonTheme> themes) {
        this.calendar = calendar;
        this.themes = themes;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    private void tick() {
        var active = themes.entrySet().stream().filter(theme -> calendar.is(theme.getKey())).map(Map.Entry::getValue).toList();
        if (active.isEmpty()) {
            return;
        }
        var settings = BlockcraftApi.get().settings();
        var random = ThreadLocalRandom.current();
        for (var player : Bukkit.getOnlinePlayers()) {
            boolean effects = settings.enabled(player, PlayerSetting.EFFECTS);
            boolean sounds = settings.enabled(player, PlayerSetting.SOUNDS);
            var at = player.getLocation();
            for (var theme : active) {
                if (effects && theme.particle().isPresent() && theme.particleCount() > 0) {
                    player.spawnParticle(theme.particle().orElseThrow(), at.clone().add(0, PARTICLE_HEIGHT / 2, 0),
                            theme.particleCount(), PARTICLE_RADIUS, PARTICLE_HEIGHT / 2, PARTICLE_RADIUS, 0);
                }
                if (sounds && !theme.sounds().isEmpty() && random.nextDouble() < PERIOD_TICKS / (theme.soundEvery().toSeconds() * 20.0)) {
                    double angle = random.nextDouble(Math.PI * 2);
                    player.playSound(theme.sounds().get(random.nextInt(theme.sounds().size())),
                            at.getX() + Math.cos(angle) * SOUND_DISTANCE, at.getY() + random.nextDouble(-2, 4),
                            at.getZ() + Math.sin(angle) * SOUND_DISTANCE);
                }
            }
        }
    }

    @Override
    public void close() {
        task.cancel();
    }
}
