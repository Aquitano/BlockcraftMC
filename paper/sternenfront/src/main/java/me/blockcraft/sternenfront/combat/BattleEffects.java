package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BattleEffects {

    private record Shot(LivingEntity actor, Blaster blaster, long tick) { }
    private record Wave(Location origin, Vector normal, double range, double cone, Color color, long started, int ticks) { }
    private record Tether(Player player, Location anchor, long until) { }

    private final SternenfrontGame game;
    private final Map<UUID, Shot> shots = new HashMap<>();
    private final List<Wave> waves = new ArrayList<>();
    private final List<Tether> tethers = new ArrayList<>();
    private final boolean enabled;

    public BattleEffects(SternenfrontGame game) {
        this.game = game;
        enabled = game.plugin().getConfig().getBoolean("presentation.effects", true);
    }

    public void fire(LivingEntity actor, Faction faction, Blaster blaster, Vector direction, double heat) {
        if (!enabled) {
            return;
        }
        shots.put(actor.getUniqueId(), new Shot(actor, blaster, game.tick()));
        var held = actor.getEquipment().getItemInMainHand();
        if (HotbarItem.of(held, Blaster.class).filter(blaster::equals).isPresent()) {
            blaster.firingModel().mark(held);
            actor.getEquipment().setItemInMainHand(held);
        }
        var eye = actor.getEyeLocation();
        var side = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() > .001) {
            side.normalize().multiply(.2);
        }
        var offset = direction.clone().multiply(.8).add(side).add(new Vector(0, -.2, 0));
        double distance = offset.length();
        var wall = eye.getWorld().rayTraceBlocks(eye, offset.clone().normalize(), distance, FluidCollisionMode.NEVER, true);
        var muzzle = wall == null ? eye.clone().add(offset)
                : wall.getHitPosition().subtract(offset.normalize().multiply(.04)).toLocation(eye.getWorld());
        if (muzzle.getBlock().isPassable()) {
            dust(muzzle, Color.WHITE, .65f);
            muzzle.getWorld().spawnParticle(Particle.DUST, muzzle, 4, .035, .035, .035, 0,
                    new Particle.DustOptions(faction.bolt(), .8f));
            if (heat > .65) {
                muzzle.getWorld().spawnParticle(Particle.SMOKE, muzzle, 1, .02, .02, .02, .005);
            }
        }
    }

    public double recoil(LivingEntity actor) {
        var shot = shots.get(actor.getUniqueId());
        return shot == null ? 0 : Math.clamp(1 - (game.tick() - shot.tick()) / 3.0, 0, 1);
    }

    public void push(Location origin, Vector facing, double range, double angle) {
        if (enabled) {
            waves.add(new Wave(origin.clone(), facing.clone().normalize(), range, Math.tan(Math.toRadians(angle / 2)),
                    Color.fromRGB(0xB8E5FF), game.tick(), 8));
        }
    }

    public void rally(Location origin, Faction faction, double radius) {
        if (enabled) {
            waves.add(new Wave(origin.clone().add(0, .15, 0), new Vector(0, 1, 0), radius, 0,
                    faction.bolt(), game.tick(), 12));
        }
    }

    public void impact(Location at, Vector normal, Color color) {
        if (enabled) {
            waves.add(new Wave(at.clone().add(normal.clone().multiply(.04)), normal.clone(), .9, -1,
                    color, game.tick(), 8));
        }
    }

    public void grapple(Player player, Location anchor) {
        if (enabled) {
            tethers.removeIf(tether -> tether.player().equals(player));
            tethers.add(new Tether(player, anchor.clone(), game.tick() + 20));
        }
    }

    public void tick(long tick) {
        shots.values().removeIf(shot -> {
            if (!shot.actor().isValid() || tick - shot.tick() >= 3) {
                restore(shot);
                return true;
            }
            return false;
        });
        waves.removeIf(wave -> tick - wave.started() >= wave.ticks());
        tethers.removeIf(tether -> !game.onField(tether.player()) || tick >= tether.until()
                || !tether.player().getWorld().equals(tether.anchor().getWorld())
                || tether.player().getEyeLocation().distanceSquared(tether.anchor()) < 2);
        if (tick % 2 != 0) {
            return;
        }
        waves.forEach(wave -> draw(wave, tick));
        for (var tether : tethers) {
            var from = tether.player().getEyeLocation().subtract(0, .2, 0);
            var line = tether.anchor().toVector().subtract(from.toVector());
            double length = line.length();
            var direction = line.normalize();
            var hit = from.getWorld().rayTraceBlocks(from, direction, length, FluidCollisionMode.NEVER, true);
            double reach = hit == null ? length : Math.min(length, hit.getHitPosition().distance(from.toVector()));
            for (double along = 0; along < reach; along += .3) {
                dust(from.clone().add(direction.clone().multiply(along)), Color.fromRGB(0xA2B6C3), .5f);
            }
        }
    }

    private static void draw(Wave wave, long tick) {
        double progress = (tick - wave.started() + 1.0) / wave.ticks();
        double distance = wave.range() * progress;
        boolean cone = wave.cone() > 0;
        var center = cone ? wave.origin().clone().add(wave.normal().clone().multiply(distance)) : wave.origin();
        double radius = cone ? distance * wave.cone() : distance;
        var reference = Math.abs(wave.normal().getY()) < .9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        var right = wave.normal().clone().crossProduct(reference).normalize();
        var up = right.clone().crossProduct(wave.normal()).normalize();
        for (int point = 0; point < 32; point++) {
            double angle = point * Math.PI * 2 / 32;
            var at = center.clone().add(right.clone().multiply(Math.cos(angle) * radius)).add(up.clone().multiply(Math.sin(angle) * radius));
            if (wave.cone() < 0 ? at.getBlock().isPassable() : visible(wave.origin(), at)) {
                dust(at, wave.color(), .7f);
            }
        }
    }

    private static boolean visible(Location from, Location to) {
        var line = to.toVector().subtract(from.toVector());
        double distance = line.length();
        return to.getBlock().isPassable() && (distance < .001
                || from.getWorld().rayTraceBlocks(from, line.normalize(), distance, FluidCollisionMode.NEVER, true) == null);
    }

    private static void dust(Location at, Color color, float size) {
        at.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, size));
    }

    private static void restore(Shot shot) {
        if (shot.actor().isValid()) {
            if (shot.actor() instanceof Player player) {
                var inventory = player.getInventory();
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    var item = inventory.getItem(slot);
                    if (restore(item, shot.blaster())) {
                        inventory.setItem(slot, item);
                    }
                }
            } else {
                var held = shot.actor().getEquipment().getItemInMainHand();
                if (restore(held, shot.blaster())) {
                    shot.actor().getEquipment().setItemInMainHand(held);
                }
            }
        }
    }

    private static boolean restore(ItemStack item, Blaster blaster) {
        if (HotbarItem.of(item, Blaster.class).filter(blaster::equals).isPresent()
                && item.getItemMeta().getCustomModelDataComponent().getStrings().contains(blaster.firingModel().key())) {
            blaster.model().mark(item);
            return true;
        }
        return false;
    }

    public void clear() {
        shots.values().forEach(BattleEffects::restore);
        shots.clear();
        waves.clear();
        tethers.clear();
    }
}
