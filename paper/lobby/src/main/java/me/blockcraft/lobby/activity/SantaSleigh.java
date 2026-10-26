package me.blockcraft.lobby.activity;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.coins.Coins;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.lobby.config.SleighConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Now and then during its occasions, Santa's sleigh crosses the sky above a lobby player: five reindeer led by
 * Rudolph, bells ringing Jingle Bells from the sleigh, and presents falling out that anyone may catch, one per flight.
 * The model is block displays riding one invisible display that moves, so a flight costs one teleport per step.
 */
public final class SantaSleigh implements Listener, AutoCloseable {

    private record Part(BlockData block, Vector3f from, Vector3f size, boolean glowing) { }
    private record Present(ItemDisplay display, Interaction target) { }

    private static final long STEP_TICKS = 2L;
    private static final long CHECK_TICKS = 20L * 20;
    private static final long MAX_FALL_TICKS = 59L;
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);
    private static final Vector3f RUDOLPHS_NOSE = new Vector3f(0, 0.91f, 7.9f);
    private static final Vector3f BEHIND = new Vector3f(0, 0.2f, -1.6f);
    private static final Particle.DustOptions NOSE_GLOW = new Particle.DustOptions(Color.RED, 1.5f);

    private final Plugin plugin;
    private final SleighConfig config;
    private final Messages messages;
    private final NetworkCalendar calendar;
    private final List<Part> model = model();
    private final List<ItemStack> wrapping = List.of(Items.head("MHF_Present1").build(), Items.head("MHF_Present2").build());
    private final List<Entity> sleigh = new ArrayList<>();
    private final Map<UUID, Present> presents = new HashMap<>();
    private final Set<UUID> caught = new HashSet<>();
    private final BukkitTask check;
    private BukkitTask flight;
    private long next = System.nanoTime();

    public SantaSleigh(Plugin plugin, SleighConfig config, Messages messages, NetworkCalendar calendar) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.calendar = calendar;
        this.check = config.enabled() ? Bukkit.getScheduler().runTaskTimer(plugin, this::check, CHECK_TICKS, CHECK_TICKS) : null;
    }

    private void check() {
        var api = BlockcraftApi.get();
        if (flight != null || System.nanoTime() < next || config.during().stream().noneMatch(calendar::is)) {
            return;
        }
        Bukkit.getOnlinePlayers().stream().filter(player -> !api.isVanished(player) && !api.isAfk(player)).findAny()
                .ifPresent(observer -> {
                    next = System.nanoTime() + config.interval().toNanos();
                    launch(observer.getLocation());
                });
    }

    /** Sends the sleigh across the sky above {@code center}, whatever the date; false while it is still on its way. */
    public boolean launch(Location center) {
        if (flight != null) {
            return false;
        }
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        var direction = new Vector(Math.cos(angle), 0, Math.sin(angle));
        var start = center.clone().add(direction.clone().multiply(-config.length() / 2.0)).add(0, config.height(), 0);
        start.setDirection(direction);
        var root = spawn(start);
        caught.clear();
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(messages.get("seasonal.sleigh-announce")));
        var drops = new HashSet<Long>();
        for (int i = 1; i <= config.presents(); i++) {
            long tick = Math.round((0.2 + 0.6 * i / (config.presents() + 1)) * config.flightTicks() / STEP_TICKS) * STEP_TICKS;
            drops.add(tick);
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
                var at = start.clone().add(direction.clone().multiply(progress * config.length()))
                        .add(0, Math.sin(progress * Math.PI * 4) * 0.6, 0);
                root.teleport(at);
                ring(at, tick);
                if (drops.contains(tick)) {
                    drop(at);
                }
            }
        }.runTaskTimer(plugin, STEP_TICKS, STEP_TICKS);
        return true;
    }

    private Entity spawn(Location start) {
        var world = start.getWorld();
        var root = world.spawn(start, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setTeleportDuration((int) STEP_TICKS);
        });
        sleigh.add(root);
        for (var part : model) {
            var block = world.spawn(start, BlockDisplay.class, display -> {
                display.setBlock(part.block());
                display.setTransformation(new Transformation(part.from(), new Quaternionf(), part.size(), new Quaternionf()));
                display.setBrightness(LIT);
                display.setViewRange(2);
                display.setPersistent(false);
                if (part.glowing()) {
                    display.setGlowing(true);
                    display.setGlowColorOverride(Color.RED);
                }
            });
            root.addPassenger(block);
            sleigh.add(block);
        }
        var santa = world.spawn(start, Mannequin.class, figure -> {
            figure.setProfile(SeasonalEvents.SANTA);
            figure.setDescription(null);
            figure.setInvulnerable(true);
            figure.setImmovable(true);
            figure.setGravity(false);
            figure.setSilent(true);
            figure.setPersistent(false);
        });
        root.addPassenger(santa);
        sleigh.add(santa);
        return root;
    }

    private void ring(Location at, long tick) {
        var settings = BlockcraftApi.get().settings();
        var nose = local(at, RUDOLPHS_NOSE);
        var behind = local(at, BEHIND);
        var notes = JingleBells.REFRAIN.stream().filter(note -> note.tick() == tick % JingleBells.LOOP_TICKS).toList();
        for (var player : at.getWorld().getPlayers()) {
            if (settings.enabled(player, PlayerSetting.EFFECTS)) {
                player.spawnParticle(Particle.DUST, nose, 2, 0.05, 0.05, 0.05, 0, NOSE_GLOW);
                player.spawnParticle(Particle.FIREWORK, behind, 3, 0.3, 0.1, 0.3, 0.01);
                player.spawnParticle(Particle.SNOWFLAKE, behind, 2, 0.4, 0.2, 0.4, 0);
            }
            notes.forEach(note -> Sounds.SLEIGH_BELLS.note(player, note.semitone(), at));
        }
    }

    private void drop(Location at) {
        var from = local(at, BEHIND);
        var world = from.getWorld();
        var hit = world.rayTraceBlocks(from, new Vector(0, -1, 0), config.height() + 32.0, FluidCollisionMode.ALWAYS, true);
        if (hit == null) {
            return;
        }
        var ground = hit.getHitPosition().toLocation(world);
        long fall = Math.clamp((long) (from.getY() - ground.getY()) * 2, 10, MAX_FALL_TICKS);
        var item = wrapping.get(ThreadLocalRandom.current().nextInt(wrapping.size()));
        var display = world.spawn(from, ItemDisplay.class, present -> {
            present.setItemStack(item);
            present.setBrightness(LIT);
            present.setPersistent(false);
            present.setTeleportDuration((int) fall);
        });
        var target = world.spawn(ground, Interaction.class, interaction -> {
            interaction.setInteractionWidth(0.7f);
            interaction.setInteractionHeight(0.6f);
            interaction.setResponsive(true);
            interaction.setPersistent(false);
        });
        presents.put(target.getUniqueId(), new Present(display, target));
        Tasks.later(plugin, 1L, () -> display.teleport(ground.clone().add(0, 0.25, 0)));
        Tasks.later(plugin, config.presentLifetime().toSeconds() * 20, () -> unwrap(target.getUniqueId()));
    }

    @EventHandler
    public void onCatch(PlayerInteractEntityEvent event) {
        var present = presents.get(event.getRightClicked().getUniqueId());
        if (present != null) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                take(event.getPlayer(), present);
            }
        }
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        var present = presents.get(event.getAttacked().getUniqueId());
        if (present != null) {
            event.setCancelled(true);
            take(event.getPlayer(), present);
        }
    }

    private void take(Player player, Present present) {
        if (!caught.add(player.getUniqueId())) {
            player.sendMessage(messages.get("seasonal.sleigh-greedy"));
            return;
        }
        unwrap(present.target().getUniqueId());
        Coins.award(plugin, player, config.presentCoins());
        player.sendMessage(messages.get("seasonal.sleigh-present", Formatter.number("coins", config.presentCoins())));
    }

    private void unwrap(UUID target) {
        var present = presents.remove(target);
        if (present != null) {
            present.display().remove();
            present.target().remove();
        }
    }

    private void land() {
        if (flight != null) {
            flight.cancel();
            flight = null;
        }
        sleigh.forEach(Entity::remove);
        sleigh.clear();
    }

    /** Where a point of the model is in the world: displays turn their model so local +Z points along their yaw. */
    private static Location local(Location origin, Vector3f offset) {
        double yaw = Math.toRadians(origin.getYaw());
        double aheadX = -Math.sin(yaw);
        double aheadZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        return origin.clone().add(rightX * offset.x + aheadX * offset.z, offset.y, rightZ * offset.x + aheadZ * offset.z);
    }

    /** Local blocks, +Z ahead, with the root where Santa sits. */
    private static List<Part> model() {
        var parts = new ArrayList<Part>();
        var red = Material.RED_CONCRETE;
        var gold = Material.GOLD_BLOCK;
        add(parts, red, -0.8f, -0.15f, -0.9f, 1.6f, 0.2f, 2.5f);
        add(parts, red, -0.9f, -0.15f, -0.9f, 0.1f, 0.85f, 2.5f);
        add(parts, red, 0.8f, -0.15f, -0.9f, 0.1f, 0.85f, 2.5f);
        add(parts, red, -0.9f, -0.15f, -1.0f, 1.8f, 1.25f, 0.1f);
        add(parts, red, -0.9f, -0.15f, 1.6f, 1.8f, 0.65f, 0.1f);
        add(parts, gold, -0.95f, 0.7f, -1.0f, 0.2f, 0.06f, 2.7f);
        add(parts, gold, 0.75f, 0.7f, -1.0f, 0.2f, 0.06f, 2.7f);
        add(parts, gold, -0.95f, 1.1f, -1.05f, 1.9f, 0.06f, 0.2f);
        add(parts, gold, -0.95f, 0.5f, 1.55f, 1.9f, 0.06f, 0.2f);
        for (float x : new float[]{-0.7f, 0.62f}) {
            add(parts, gold, x, -0.45f, -0.6f, 0.08f, 0.3f, 0.08f);
            add(parts, gold, x, -0.45f, 1.2f, 0.08f, 0.3f, 0.08f);
        }
        for (float x : new float[]{-0.78f, 0.66f}) {
            add(parts, gold, x, -0.5f, -1.1f, 0.12f, 0.06f, 3.1f);
            add(parts, gold, x, -0.5f, 2.0f, 0.12f, 0.45f, 0.08f);
        }
        add(parts, Material.LIME_CONCRETE, 0.3f, 0.05f, -0.85f, 0.4f, 0.4f, 0.4f);
        add(parts, Material.WHITE_CONCRETE, 0.35f, 0.45f, -0.8f, 0.3f, 0.3f, 0.3f);
        for (float x : new float[]{-0.5f, 0.47f}) {
            add(parts, Material.BLACK_CONCRETE, x, 0.25f, 1.7f, 0.03f, 0.03f, 5.6f);
        }
        for (float z : new float[]{3.0f, 5.0f}) {
            reindeer(parts, -0.6f, z, false);
            reindeer(parts, 0.6f, z, false);
        }
        reindeer(parts, 0, 7.0f, true);
        return List.copyOf(parts);
    }

    private static void reindeer(List<Part> parts, float x, float z, boolean rudolph) {
        var fur = Material.BROWN_TERRACOTTA;
        float y = -0.3f;
        add(parts, fur, x - 0.22f, y + 0.55f, z - 0.5f, 0.44f, 0.42f, 1.0f);
        for (float legX : new float[]{x - 0.2f, x + 0.1f}) {
            for (float legZ : new float[]{z - 0.45f, z + 0.35f}) {
                add(parts, fur, legX, y, legZ, 0.1f, 0.55f, 0.1f);
            }
        }
        add(parts, fur, x - 0.1f, y + 0.85f, z + 0.35f, 0.2f, 0.35f, 0.2f);
        add(parts, fur, x - 0.15f, y + 1.1f, z + 0.4f, 0.3f, 0.28f, 0.45f);
        add(parts, Material.DEAD_BUSH, x - 0.3f, y + 1.3f, z + 0.35f, 0.6f, 0.55f, 0.3f);
        add(parts, Material.WHITE_WOOL, x - 0.06f, y + 0.85f, z - 0.58f, 0.12f, 0.12f, 0.1f);
        var nose = new Part(rudolph ? Material.REDSTONE_BLOCK.createBlockData() : Material.BLACK_CONCRETE.createBlockData(),
                new Vector3f(x - 0.06f, y + 1.15f, z + 0.84f), new Vector3f(0.12f, 0.12f, 0.08f), rudolph);
        parts.add(nose);
    }

    private static void add(List<Part> parts, Material block, float x, float y, float z, float width, float height, float depth) {
        parts.add(new Part(block.createBlockData(), new Vector3f(x, y, z), new Vector3f(width, height, depth), false));
    }

    @Override
    public void close() {
        if (check != null) {
            check.cancel();
        }
        land();
        Set.copyOf(presents.keySet()).forEach(this::unwrap);
    }
}
