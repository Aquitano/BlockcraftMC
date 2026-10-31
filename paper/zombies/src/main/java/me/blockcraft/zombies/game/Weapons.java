package me.blockcraft.zombies.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.zombies.config.ZombiesConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class Weapons {

    private static final int FLAME_PARTICLES = 8;

    private final ZombiesGame game;
    private final Flames flames;

    public Weapons(ZombiesGame game) {
        this.game = game;
        this.flames = new Flames(game);
    }

    public Optional<Weapon> held(Player player) {
        return HotbarItem.of(player.getInventory().getItemInMainHand(), Weapon.class)
                .filter(weapon -> game.survivor(player) != null && game.survivor(player).weapons().containsKey(weapon));
    }

    public void give(Player player, Weapon weapon) {
        var stats = game.config().guns().get(weapon);
        var survivor = game.survivor(player);
        if (!survivor.weapons().containsKey(weapon) && survivor.weapons().size() == 2) {
            var replacing = held(player).orElse(survivor.weapons().keySet().stream().skip(1).findFirst().orElseThrow());
            survivor.weapons().remove(replacing);
        }
        survivor.weapons().put(weapon, new Ammo(stats.clip(), stats.reserve()));
        render(player);
    }

    public void render(Player player) {
        var survivor = game.survivor(player);
        if (survivor == null) {
            return;
        }
        int slot = 0;
        for (var entry : survivor.weapons().entrySet()) {
            var weapon = entry.getKey();
            var ammo = entry.getValue();
            var builder = weapon.model().item().name(game.messages().get(ammo.upgraded() ? "weapon.upgraded" : "weapon.name",
                            Placeholder.component("weapon", game.messages().get("weapon." + weapon.key()))))
                    .lore(game.messages().getLines("weapon.lore", Formatter.number("clip", ammo.clip()), Formatter.number("reserve", ammo.reserve())))
                    .hideAttributes().edit(meta -> meta.setUnbreakable(true));
            if (ammo.upgraded()) {
                builder.glow();
            }
            player.getInventory().setItem(slot++, weapon.build(builder));
        }
        if (slot < 2) {
            player.getInventory().setItem(1, null);
        }
        player.getInventory().setItem(2, Items.of(Material.IRON_SWORD).name(game.messages().get("weapon.knife"))
                .lore(game.messages().get("weapon.knife-lore")).hideAttributes().edit(meta -> meta.setUnbreakable(true)).build());
    }

    public void reload(Player player, int tick) {
        if (!game.fighting(player)) {
            return;
        }
        held(player).ifPresent(weapon -> {
            var survivor = game.survivor(player);
            var ammo = survivor.weapons().get(weapon);
            int duration = game.config().guns().get(weapon).reloadTicks();
            if (survivor.perks().contains(Perk.QUICK_RELOAD)) {
                duration = Math.max(1, duration / 2);
            }
            if (ammo.reload(tick, duration)) {
                Sounds.EQUIP.play(player);
            }
        });
    }

    public void shoot(Player player, int tick) {
        if (!game.fighting(player) || !game.inWave()) {
            return;
        }
        held(player).ifPresent(weapon -> shoot(player, weapon, tick));
    }

    private void shoot(Player player, Weapon weapon, int tick) {
        var survivor = game.survivor(player);
        var ammo = survivor.weapons().get(weapon);
        var stats = game.config().guns().get(weapon);
        if (!ammo.shoot(tick, stats.cooldownTicks())) {
            if (ammo.clip() == 0 && !ammo.reloading()) {
                reload(player, tick);
                if (ammo.reserve() == 0) {
                    BlockcraftApi.get().notifications().feedback(player, game.messages().get("weapon.empty"));
                }
            }
            return;
        }
        var eye = player.getEyeLocation();
        eye.getWorld().playSound(eye, weapon == Weapon.FLAMETHROWER ? Sound.ITEM_FIRECHARGE_USE : Sound.ENTITY_FIREWORK_ROCKET_BLAST,
                0.35f, weapon == Weapon.SNIPER ? 0.7f : 1.7f);
        double multiplier = ammo.upgraded() ? stats.upgradeMultiplier() : 1;
        var volley = weapon == Weapon.FLAMETHROWER ? spray(eye, stats, multiplier) : fire(eye, stats, multiplier);
        for (var entry : volley.hits().entrySet()) {
            var mob = entry.getKey();
            game.addGold(player, game.config().hitGold());
            if (survivor.perks().contains(Perk.FROZEN_BULLETS)) {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
            if (stats.burnDamage() > 0) {
                flames.ignite(player, mob, stats.burnDamage() * multiplier, stats.burnTicks(), tick);
            }
            game.enemies().hit(player, mob, entry.getValue());
        }
        if (!volley.hits().isEmpty()) {
            Sounds.NOTE_PLING.note(player, volley.headshot() ? 20 : 12);
        }
        render(player);
    }

    private record Volley(Map<Mob, Double> hits, boolean headshot) {
    }

    private Volley fire(Location eye, ZombiesConfig.Gun stats, double multiplier) {
        var world = eye.getWorld();
        var hits = new LinkedHashMap<Mob, Double>();
        boolean headshot = false;
        for (int pellet = 0; pellet < stats.pellets(); pellet++) {
            var random = ThreadLocalRandom.current();
            var direction = eye.getDirection();
            if (stats.spread() > 0) {
                direction.add(new Vector(random.nextDouble(-stats.spread(), stats.spread()),
                        random.nextDouble(-stats.spread(), stats.spread()), random.nextDouble(-stats.spread(), stats.spread()))).normalize();
            }
            var wall = world.rayTraceBlocks(eye, direction, stats.range(), FluidCollisionMode.NEVER, true);
            double range = wall == null ? stats.range() : wall.getHitPosition().distance(eye.toVector());
            var hit = range <= 0 ? null : world.rayTraceEntities(eye, direction, range, 0.08,
                    entity -> entity instanceof Mob && game.enemies().contains(entity) && !entity.isDead());
            double length = hit == null ? range : hit.getHitPosition().distance(eye.toVector());
            for (double step = 0.5; step < length; step += 1.5) {
                world.spawnParticle(Particle.CRIT, eye.clone().add(direction.clone().multiply(step)), 1, 0, 0, 0, 0);
            }
            if (hit != null && hit.getHitEntity() instanceof Mob mob) {
                boolean head = hit.getHitPosition().getY() > mob.getLocation().getY() + mob.getHeight() * 0.8;
                headshot |= head;
                hits.merge(mob, stats.damage() * (head ? game.config().headshotMultiplier() : 1) * multiplier, Double::sum);
            }
        }
        return new Volley(hits, headshot);
    }

    /** Every enemy in sight inside a cone whose half-angle is the spread, not only the first one in the flame's way. */
    private Volley spray(Location eye, ZombiesConfig.Gun stats, double multiplier) {
        var world = eye.getWorld();
        var direction = eye.getDirection();
        flameJet(eye, direction, stats);
        var hits = new LinkedHashMap<Mob, Double>();
        for (var entity : world.getNearbyEntities(eye, stats.range(), stats.range(), stats.range(),
                entity -> game.enemies().contains(entity) && !entity.isDead())) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            var toward = mob.getLocation().add(0, mob.getHeight() / 2, 0).toVector().subtract(eye.toVector());
            double distance = toward.length();
            if (distance == 0 || distance > stats.range() || direction.angle(toward) > stats.spread() + Math.atan2(mob.getWidth(), distance)
                    || world.rayTraceBlocks(eye, toward.normalize(), distance, FluidCollisionMode.NEVER, true) != null) {
                continue;
            }
            hits.put(mob, stats.damage() * multiplier);
        }
        return new Volley(hits, false);
    }

    /** Flames leave the muzzle at a speed that carries them, slowing, about as far as the gun reaches. */
    private static void flameJet(Location eye, Vector direction, ZombiesConfig.Gun stats) {
        var world = eye.getWorld();
        var random = ThreadLocalRandom.current();
        var right = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() > 0) {
            right.normalize().multiply(0.3);
        }
        var muzzle = eye.clone().add(direction.clone().multiply(0.7)).add(right).subtract(0, 0.25, 0);
        for (int particle = 0; particle < FLAME_PARTICLES; particle++) {
            var flight = direction.clone().add(new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                    .multiply(stats.spread() / 2)).normalize();
            world.spawnParticle(Particle.FLAME, muzzle, 0, flight.getX(), flight.getY(), flight.getZ(),
                    stats.range() * random.nextDouble(0.04, 0.06));
        }
        world.spawnParticle(Particle.SMOKE, muzzle, 0, direction.getX(), direction.getY(), direction.getZ(), 0.1);
    }

    public void burn(int tick) {
        flames.tick(tick);
    }

    public void tick(Player player, int tick) {
        var survivor = game.survivor(player);
        if (survivor != null && survivor.fighting()) {
            boolean changed = false;
            for (var ammo : survivor.weapons().values()) {
                changed |= ammo.tick(tick);
            }
            if (changed) {
                render(player);
                Sounds.EQUIP.play(player);
            }
        }
    }
}
