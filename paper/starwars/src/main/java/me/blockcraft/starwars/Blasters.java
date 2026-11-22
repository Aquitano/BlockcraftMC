package me.blockcraft.starwars;

import me.blockcraft.api.scenery.FurnitureSolids;
import me.blockcraft.api.game.ShotEvent;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class Blasters implements Listener, AutoCloseable {
    private static final Particle.DustOptions BOLT = new Particle.DustOptions(Color.fromRGB(255, 65, 40), .65f);
    private static final class Heat {
        double value;
        int firedAt;
        int readyAt;
    }
    private static final class Projectile {
        final Player owner;
        final WeaponSpec weapon;
        final boolean loan;
        final ItemDisplay display;
        final Vector velocity;
        final int born;
        Location at;
        double distance;
        Projectile(Player owner, WeaponSpec weapon, boolean loan, ItemDisplay display, Vector velocity, int born, Location at) {
            this.owner = owner; this.weapon = weapon; this.loan = loan; this.display = display;
            this.velocity = velocity; this.born = born; this.at = at;
        }
    }
    private record Hit(Location at, Creatures.Actor actor) {}
    private final StarWarsPlugin plugin;
    private final Creatures creatures;
    private final Messages messages;
    private final Map<UUID, Heat> heat = new HashMap<>();
    private final List<Projectile> projectiles = new ArrayList<>();

    Blasters(StarWarsPlugin plugin, Creatures creatures, Messages messages) {
        this.plugin = plugin; this.creatures = creatures; this.messages = messages;
    }

    @EventHandler public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        var player = event.getPlayer();
        var item = player.getInventory().getItemInMainHand();
        var spec = plugin.weapon(item);
        if (spec == null) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        boolean loan = plugin.loan(item);
        if (loan && !plugin.inPractice(player.getLocation())) return;
        int now = plugin.getServer().getCurrentTick();
        var state = heat.computeIfAbsent(player.getUniqueId(), ignored -> new Heat());
        if (now < state.readyAt) return;
        if (state.value + spec.heat() > 100.01) {
            player.sendActionBar(messages.get("overheated"));
            return;
        }
        state.value += spec.heat(); state.firedAt = now; state.readyAt = now + spec.cooldown();
        player.sendActionBar(messages.get("heat", Placeholder.component("weapon", messages.get("weapons." + spec.id())),
                Placeholder.unparsed("heat", Integer.toString((int) state.value))));
        switch (spec.mode()) {
            case BOLT -> bolt(player, spec, loan);
            case BURST -> {
                bolt(player, spec, loan);
                for (int delay : new int[]{2, 4}) plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && !player.isDead() && plugin.weapon(player.getInventory().getItemInMainHand()) == spec
                            && (!loan || plugin.inPractice(player.getLocation()))) bolt(player, spec, loan);
                }, delay);
            }
            case ROCKET, GRENADE -> projectile(player, spec, item, loan, now);
        }
    }

    private Hit trace(Location start, Vector direction, double range, boolean loan) {
        var block = FurnitureSolids.rayTrace(start, direction, range);
        double distance = block == null ? range : block.getHitPosition().distance(start.toVector());
        Creatures.Actor target = null;
        for (var actor : creatures.actors()) {
            if (!actor.entity.isValid() || actor.resetAt > 0 || actor.entity.getWorld() != start.getWorld() || (loan && !actor.practice)) continue;
            var hit = actor.bounds().rayTrace(start.toVector(), direction, distance);
            if (hit == null) continue;
            double next = hit.getHitPosition().distance(start.toVector());
            if (next < distance) { distance = next; target = actor; }
        }
        return new Hit(start.clone().add(direction.clone().multiply(distance)), target);
    }

    private void bolt(Player player, WeaponSpec weapon, boolean loan) {
        plugin.getServer().getPluginManager().callEvent(new ShotEvent(player));
        var start = player.getEyeLocation();
        start.getWorld().playSound(start, Sound.ENTITY_FIREWORK_ROCKET_BLAST, .45f, 1.8f);
        for (int i = 0; i < weapon.pellets(); i++) {
            var direction = start.getDirection();
            double spread = weapon.spread() * (player.isSneaking() ? .35 : 1);
            var random = ThreadLocalRandom.current();
            if (spread > 0) direction.add(new Vector(random.nextDouble(-spread, spread), random.nextDouble(-spread, spread), random.nextDouble(-spread, spread))).normalize();
            var hit = trace(start, direction, weapon.range(), loan);
            double distance = start.distance(hit.at());
            for (double along = .7; along < distance; along += .7) {
                start.getWorld().spawnParticle(Particle.DUST, start.clone().add(direction.clone().multiply(along)), 1, 0, 0, 0, 0, BOLT);
            }
            if (hit.actor() != null) creatures.hit(hit.actor(), player, weapon.damage(), loan);
            hit.at().getWorld().spawnParticle(Particle.ELECTRIC_SPARK, hit.at(), 3, .05, .05, .05, .02);
        }
    }

    private void projectile(Player player, WeaponSpec weapon, ItemStack item, boolean loan, int now) {
        plugin.getServer().getPluginManager().callEvent(new ShotEvent(player));
        var at = player.getEyeLocation();
        boolean grenade = weapon.mode() == WeaponSpec.Mode.GRENADE;
        double speed = grenade ? plugin.settings().grenadeSpeed() : plugin.settings().rocketSpeed();
        var velocity = at.getDirection().multiply(speed);
        if (grenade) velocity.setY(velocity.getY() + .15);
        var display = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(item.asOne());
            entity.setViewRange(.75f);
            entity.setTeleportDuration(1);
            entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(grenade ? .3f : .16f), new Quaternionf()));
        });
        projectiles.add(new Projectile(player, weapon, loan, display, velocity, now, at));
        at.getWorld().playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, .6f, .8f);
    }

    void tick(int now) {
        double cooling = plugin.settings().coolPerTick();
        int delay = plugin.settings().coolDelayTicks();
        heat.values().forEach(value -> { if (now - value.firedAt > delay) value.value = Math.max(0, value.value - cooling); });
        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext();) {
            var p = it.next();
            if (!p.owner.isOnline() || p.owner.isDead() || p.owner.getWorld() != p.at.getWorld()
                    || (p.loan && (!plugin.inPractice(p.owner.getLocation()) || !plugin.inPractice(p.at)))) {
                p.display.remove(); it.remove(); continue;
            }
            boolean grenade = p.weapon.mode() == WeaponSpec.Mode.GRENADE;
            var hit = trace(p.at, p.velocity.clone().normalize(), p.velocity.length(), p.loan);
            double travelled = p.at.distance(hit.at());
            boolean collision = travelled < p.velocity.length() - .001 || hit.actor() != null;
            p.distance += travelled;
            p.at = hit.at();
            int fuse = plugin.settings().grenadeFuseTicks();
            if ((!grenade && collision) || (grenade && now - p.born >= fuse) || p.distance >= p.weapon.range() || now - p.born > 200) {
                explode(p); p.display.remove(); it.remove(); continue;
            }
            if (grenade) {
                if (collision) p.velocity.multiply(-.35).setY(Math.abs(p.velocity.getY()) * .35 + .04);
                p.velocity.setY(p.velocity.getY() - .035).multiply(.99);
            }
            p.display.teleport(p.at);
            p.at.getWorld().spawnParticle(Particle.SMOKE, p.at, 1, 0, 0, 0, 0);
        }
    }

    private void explode(Projectile p) {
        var world = p.at.getWorld();
        world.spawnParticle(Particle.EXPLOSION, p.at, 1);
        world.playSound(p.at, Sound.ENTITY_GENERIC_EXPLODE, .8f, 1);
        double radius = plugin.settings().explosionRadius();
        for (var actor : creatures.actors()) {
            if (!actor.entity.isValid() || actor.entity.getWorld() != world || (p.loan && !actor.practice)) continue;
            var center = actor.bounds().getCenter();
            double distance = center.distance(p.at.toVector());
            if (distance >= radius) continue;
            var direction = center.clone().subtract(p.at.toVector());
            if (distance > .01 && FurnitureSolids.rayTrace(p.at, direction.normalize(), distance) != null) continue;
            creatures.hit(actor, p.owner, p.weapon.damage() * (1 - distance / radius), p.loan);
        }
    }

    void forget(Player player) { heat.remove(player.getUniqueId()); }
    @Override public void close() { projectiles.forEach(p -> p.display.remove()); projectiles.clear(); heat.clear(); }
}
