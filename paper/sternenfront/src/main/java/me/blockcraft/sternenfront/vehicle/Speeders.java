package me.blockcraft.sternenfront.vehicle;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.api.world.BlocksChangedEvent;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Hero;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Speeders implements Listener {

    private static final class Craft {
        private final Faction faction;
        private final Location home;
        private Pig body;
        private ItemDisplay model;
        private Interaction seat;
        private double speed;
        private Vector previous;
        private long nextSpawn;
        private long unattended;

        private Craft(Faction faction, Location home) {
            this.faction = faction;
            this.home = home;
        }
    }

    private final SternenfrontGame game;
    private final SpeederConfig config;
    private final Map<Faction, Craft> craft = new EnumMap<>(Faction.class);

    public Speeders(SternenfrontGame game, SpeederConfig config) {
        this.game = game;
        this.config = config;
        game.plugin().getServer().getPluginManager().registerEvents(this, game.plugin());
    }

    public void start() {
        if (!config.enabled()) {
            return;
        }
        for (var faction : Faction.values()) {
            var home = game.map().headquarters().get(faction).spawns().getFirst().clone().add(0, 0, 16);
            var blocks = new ArrayList<Block>();
            for (int x = -1; x <= 1; x++) {
                for (int z = -2; z <= 2; z++) {
                    var block = home.clone().add(x, -1, z).getBlock();
                    block.setType(x == 0 ? Material.LIGHT_GRAY_CONCRETE : Material.GRAY_CONCRETE, false);
                    blocks.add(block);
                }
            }
            craft.put(faction, new Craft(faction, home));
            Bukkit.getPluginManager().callEvent(new BlocksChangedEvent(List.copyOf(blocks)));
        }
    }

    public Optional<Faction> faction(Entity entity) {
        return craft.values().stream().filter(each -> entity.equals(each.body)).map(each -> each.faction).findFirst();
    }

    public List<Pig> entities() {
        return craft.values().stream().map(each -> each.body).filter(body -> body != null && body.isValid() && !body.isDead()).toList();
    }

    public void tick(long tick) {
        for (var each : craft.values()) {
            if (each.body == null) {
                if (tick >= each.nextSpawn) {
                    spawn(each, tick);
                }
                continue;
            }
            if (!each.body.isValid() || each.body.isDead()) {
                destroy(each, tick);
                continue;
            }
            var rider = each.body.getPassengers().stream().filter(Player.class::isInstance).map(Player.class::cast).findFirst();
            if (rider.isPresent() && (!game.onField(rider.get()) || game.factionOf(rider.get()).filter(each.faction::equals).isEmpty())) {
                each.body.eject();
                rider = Optional.empty();
            }
            if (rider.isPresent()) {
                double moved = each.body.getLocation().toVector().subtract(each.previous).setY(0).length();
                drive(each, rider.get());
                each.unattended = tick;
                if (tick % 10 == 0) {
                    BlockcraftApi.get().notifications().status(rider.get(), game.messages().get("speeder.driving",
                            Formatter.number("health", Math.ceil(each.body.getHealth())),
                            Formatter.number("speed", Math.round(moved * 20))));
                }
            } else {
                each.speed = 0;
                each.body.setVelocity(new Vector(0, each.body.getVelocity().getY(), 0));
                if (tick - each.unattended >= config.idleReturnTicks() && each.body.getLocation().distanceSquared(each.home) > 9) {
                    each.body.teleport(each.home);
                    each.unattended = tick;
                }
            }
            if (!game.map().playArea().contains(each.body.getLocation().toVector()) || each.body.isInWater()) {
                destroy(each, tick);
                continue;
            }
            var at = each.body.getLocation();
            each.previous = at.toVector();
            each.model.teleport(at.clone().add(0, 0.15, 0));
            each.seat.teleport(at);
        }
    }

    private void spawn(Craft each, long tick) {
        each.body = each.home.getWorld().spawn(each.home, Pig.class, pig -> {
            pig.setPersistent(false);
            pig.setSilent(true);
            pig.setInvisible(true);
            pig.setAdult();
            pig.setAgeLock(true);
            pig.setSaddle(true);
            pig.setCollidable(true);
            pig.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.health());
            pig.setHealth(config.health());
        });
        Bukkit.getMobGoals().removeAllGoals(each.body);
        each.previous = each.body.getLocation().toVector();
        each.model = each.home.getWorld().spawn(each.home, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(PackModel.STERNENFRONT_SPEEDER.item().build());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTeleportDuration(1);
            display.customName(game.messages().get("speeder.name", Placeholder.component("faction",
                    game.messages().get("faction." + each.faction.key() + ".name"))));
            display.setCustomNameVisible(true);
        });
        each.seat = each.home.getWorld().spawn(each.home, Interaction.class, interaction -> {
            interaction.setPersistent(false);
            interaction.setInteractionWidth(1.4f);
            interaction.setInteractionHeight(1.1f);
            interaction.setResponsive(true);
        });
        each.unattended = tick;
    }

    private void drive(Craft each, Player rider) {
        var input = rider.getCurrentInput();
        int throttle = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
        each.speed = SpeederMotion.speed(each.speed, throttle, config.speed(), config.acceleration());
        float yaw = each.body.getYaw() + (float) (((input.isLeft() ? -1 : 0) + (input.isRight() ? 1 : 0)) * config.turnDegrees());
        each.body.setRotation(yaw, 0);
        var velocity = each.body.getLocation().getDirection().setY(0).normalize().multiply(each.speed);
        var eye = each.body.getLocation().add(0, 0.7, 0);
        if (Math.abs(each.speed) > 0.02 && eye.getWorld().rayTraceBlocks(eye, velocity.clone().normalize(),
                Math.abs(each.speed) + 0.6, FluidCollisionMode.NEVER, true) != null) {
            each.speed = 0;
            velocity.zero();
        }
        velocity.setY(each.body.getVelocity().getY());
        each.body.setVelocity(velocity);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRide(PlayerInteractEntityEvent event) {
        var each = craft.values().stream().filter(value -> event.getRightClicked().equals(value.seat)
                || event.getRightClicked().equals(value.body)).findFirst();
        if (each.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        var player = event.getPlayer();
        var value = each.get();
        if (!game.onField(player) || game.factionOf(player).filter(value.faction::equals).isEmpty()
                || value.body == null || !value.body.getPassengers().isEmpty() || player.isInsideVehicle()) {
            return;
        }
        player.clearActiveItem();
        value.body.addPassenger(player);
        player.sendMessage(game.messages().get("speeder.controls"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        var each = craft.values().stream().filter(value -> event.getEntity().equals(value.seat)).findFirst();
        if (each.isPresent()) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player player && game.onField(player) && each.get().body != null
                    && player.getWorld().equals(each.get().body.getWorld())
                    && player.getEyeLocation().distanceSquared(each.get().body.getEyeLocation()) <= 16
                    && player.hasLineOfSight(each.get().body)) {
                player.attack(each.get().body);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        var own = faction(event.getEntity());
        if (own.isPresent() && event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Player player
                && game.heroOf(player).filter(Hero::bladed).isPresent()
                && (hit.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || hit.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            event.setCancelled(true);
            return;
        }
        if (own.isPresent() && event.getDamageSource().getCausingEntity() instanceof LivingEntity attacker
                && game.factionOf(attacker).filter(own.get()::equals).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        craft.values().stream().filter(each -> event.getEntity().equals(each.body)).findFirst().ifPresent(each -> {
            event.getDrops().clear();
            event.setDroppedExp(0);
            destroy(each, game.tick());
        });
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && faction(event.getDismounted()).isPresent()) {
            game.fighter(player).fallSafeUntil(game.tick() + 60);
        }
    }

    private void destroy(Craft each, long tick) {
        if (each.body != null) {
            each.body.getWorld().spawnParticle(Particle.SMOKE, each.body.getLocation(), 12, 0.5, 0.3, 0.5, 0.02);
            each.body.eject();
            each.body.remove();
            each.model.remove();
            each.seat.remove();
            each.body = null;
        }
        each.nextSpawn = tick + config.respawnTicks();
        each.speed = 0;
    }

    public void clear() {
        craft.values().forEach(each -> destroy(each, game.tick()));
        craft.clear();
    }
}
