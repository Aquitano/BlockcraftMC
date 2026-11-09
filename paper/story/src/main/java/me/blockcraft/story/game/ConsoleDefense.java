package me.blockcraft.story.game;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.story.quest.ConsoleObjective;
import net.kyori.adventure.text.Component;
import org.betonquest.betonquest.api.QuestException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * The party holds the relay's console while droids come in waves. The upload advances only while a member stands
 * at the console, so the party cannot hide; when it is through, the droids fall back and the objective completes.
 * The droids wear Sternenfront's BetterModel bodies when BetterModel runs.
 */
final class ConsoleDefense implements Listener, AutoCloseable {

    record Settings(int waveTicks, int waveSize, int mostDroids, double droidHealth, double droidDamage, double droidSpread,
                    String droidModel) {
        static Settings from(ConfigurationSection section) {
            return new Settings(section.getInt("wave-seconds") * 20, section.getInt("wave-size"), section.getInt("most-droids"),
                    section.getDouble("droid-health"), section.getDouble("droid-damage"), section.getDouble("droid-spread"),
                    section.getString("droid-model", "hegemonie_soldier"));
        }
    }

    private final Plugin plugin;
    private final Settings settings;
    private final List<Location> spawns;
    private final Supplier<List<Player>> members;
    private final Blasters blasters;
    private final Component droidName;
    private final List<Skeleton> droids = new ArrayList<>();
    private BukkitTask task;
    private ConsoleObjective console;
    private int held;
    private int ticks;

    ConsoleDefense(Plugin plugin, Settings settings, List<Location> spawns, Supplier<List<Player>> members, Blasters blasters,
                   Component droidName) {
        this.plugin = plugin;
        this.settings = settings;
        this.spawns = spawns;
        this.members = members;
        this.blasters = blasters;
        this.droidName = droidName;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    boolean running() {
        return task != null;
    }

    void start(ConsoleObjective console, DoubleConsumer progress, Runnable done) {
        if (task != null) return;
        this.console = console;
        int total;
        try {
            total = Math.max(20, console.seconds() * 20);
        } catch (QuestException invalid) {
            plugin.getSLF4JLogger().warn("The console objective is misconfigured", invalid);
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks++;
            droids.removeIf(droid -> !droid.isValid());
            if (members.get().stream().anyMatch(this::holding)) held++;
            progress.accept(Math.min(1, (double) held / total));
            console.progress(held * 100 / total);
            if (ticks % settings.waveTicks() == 1) wave();
            if (ticks % 20 == 0) aim();
            if (held >= total) {
                close();
                done.run();
                console.completeFor(members.get());
            }
        }, 1, 1);
    }

    private boolean holding(Player player) {
        try {
            return console.holding(player);
        } catch (QuestException invalid) {
            return false;
        }
    }

    private void wave() {
        for (int i = 0; i < settings.waveSize() && droids.size() < settings.mostDroids(); i++) {
            var at = spawns.get(ThreadLocalRandom.current().nextInt(spawns.size()));
            droids.add(at.getWorld().spawn(at, Skeleton.class, droid -> {
                droid.setPersistent(false);
                droid.setRemoveWhenFarAway(false);
                droid.setShouldBurnInDay(false);
                droid.setCanPickupItems(false);
                droid.setSilent(true);
                droid.customName(droidName);
                droid.getAttribute(Attribute.MAX_HEALTH).setBaseValue(settings.droidHealth());
                droid.setHealth(settings.droidHealth());
                droid.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(48);
                droid.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                droid.getEquipment().setHelmet(PackModel.STERNENFRONT_HELMET_HEGEMONIE.item().build());
                droid.getEquipment().setItemInMainHandDropChance(0);
                droid.getEquipment().setHelmetDropChance(0);
            }));
            if (plugin.getServer().getPluginManager().isPluginEnabled("BetterModel")) {
                BetterModel.model(settings.droidModel()).ifPresent(model -> model.getOrCreate(BukkitAdapter.adapt(droids.getLast())));
            }
        }
    }

    private void aim() {
        for (var droid : droids) {
            members.get().stream().filter(member -> member.getWorld().equals(droid.getWorld()))
                    .min(Comparator.comparingDouble(member -> member.getLocation().distanceSquared(droid.getLocation())))
                    .ifPresent(droid::setTarget);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Skeleton droid) || !droids.contains(droid)) return;
        event.setCancelled(true);
        var target = droid.getTarget();
        if (target == null) return;
        var from = droid.getEyeLocation();
        var spread = settings.droidSpread();
        var random = ThreadLocalRandom.current();
        var aim = target.getLocation().add(0, 1.2, 0).toVector().subtract(from.toVector()).normalize()
                .add(new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).multiply(spread));
        blasters.fire(droid, from, aim, 48, settings.droidDamage(), Blasters.DROID_BOLT);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Skeleton droid && droids.contains(droid)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Skeleton droid && droids.contains(droid)) event.setCancelled(true);
    }

    @Override
    public void close() {
        if (task != null) task.cancel();
        droids.forEach(Skeleton::remove);
        droids.clear();
    }
}
