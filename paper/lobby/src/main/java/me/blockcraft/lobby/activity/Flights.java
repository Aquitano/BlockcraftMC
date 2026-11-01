package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.lobby.config.FlightsConfig;
import me.blockcraft.lobby.config.PropsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Now and then something crosses the sky above the lobby: one of the configured props pieces, an airship or a flock
 * of birds, on a straight line through the air above the spawn. Like the sleigh, the model rides one invisible
 * display that moves, so a flight costs one teleport per step whatever its size.
 */
public final class Flights implements AutoCloseable {

    private static final long STEP_TICKS = 4L;
    private static final long CHECK_TICKS = 20L * 15;
    private static final float VIEW_RANGE = 4f;
    private static final double SWAY = 1.5;

    private final Plugin plugin;
    private final FlightsConfig config;
    private final PropsConfig props;
    private final Supplier<Optional<Location>> spawn;
    private final List<Entity> craft = new ArrayList<>();
    private final BukkitTask check;
    private BukkitTask flight;
    private long next;

    public Flights(Plugin plugin, FlightsConfig config, PropsConfig props, Supplier<Optional<Location>> spawn) {
        this.plugin = plugin;
        this.config = config;
        this.props = props;
        this.spawn = spawn;
        this.next = System.nanoTime() + config.interval().toNanos();
        this.check = config.enabled() ? Bukkit.getScheduler().runTaskTimer(plugin, this::check, CHECK_TICKS, CHECK_TICKS) : null;
    }

    /** The configured models that exist as props pieces. */
    public List<String> models() {
        return config.models().stream().filter(props.props()::containsKey).toList();
    }

    private void check() {
        var api = BlockcraftApi.get();
        var models = models();
        if (flight != null || System.nanoTime() < next || models.isEmpty()
                || Bukkit.getOnlinePlayers().stream().allMatch(player -> api.isVanished(player) || api.isAfk(player))) {
            return;
        }
        next = System.nanoTime() + config.interval().toNanos();
        spawn.get().ifPresent(over -> launch(models.get(ThreadLocalRandom.current().nextInt(models.size())), over));
    }

    /** Sends {@code model} across the sky above {@code over}; false while another is still on its way or it is unknown. */
    public boolean launch(String model, Location over) {
        var prop = props.props().get(model);
        if (flight != null || prop == null) {
            return false;
        }
        var random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2);
        var direction = new Vector(Math.cos(angle), 0, Math.sin(angle));
        var aside = new Vector(-direction.getZ(), 0, direction.getX()).multiply(random.nextDouble(-config.length() / 6.0, config.length() / 6.0));
        var start = over.clone().add(direction.clone().multiply(-config.length() / 2.0)).add(aside).add(0, config.height(), 0);
        start.setDirection(direction);
        start.setPitch(0);
        var root = start.getWorld().spawn(start, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setTeleportDuration((int) STEP_TICKS);
        });
        craft.add(root);
        for (var part : prop.parts()) {
            var display = Props.display(start, part, VIEW_RANGE);
            root.addPassenger(display);
            craft.add(display);
        }
        flight = new BukkitRunnable() {
            private long tick;

            @Override
            public void run() {
                tick += STEP_TICKS;
                if (tick > config.flightTicks() || !root.isValid()) {
                    land();
                    return;
                }
                double progress = (double) tick / config.flightTicks();
                root.teleport(start.clone().add(direction.clone().multiply(progress * config.length()))
                        .add(0, Math.sin(progress * Math.PI * 3) * SWAY, 0));
            }
        }.runTaskTimer(plugin, STEP_TICKS, STEP_TICKS);
        return true;
    }

    private void land() {
        if (flight != null) {
            flight.cancel();
            flight = null;
        }
        craft.forEach(Entity::remove);
        craft.clear();
    }

    @Override
    public void close() {
        if (check != null) {
            check.cancel();
        }
        land();
    }
}
