package me.blockcraft.zombies.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ShotEvent;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.zombies.config.ZombiesConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class Weapons {

    private static final int FLAME_PARTICLES = 8;
    private static final int MAX_WEAPONS = 2;

    private final ZombiesGame game;
    private final Flames flames;
    private final Map<Weapon, Component> names = new EnumMap<>(Weapon.class);
    private final Map<Weapon, Component> upgradedNames = new EnumMap<>(Weapon.class);

    public Weapons(ZombiesGame game) {
        this.game = game;
        this.flames = new Flames(game);
    }

    public Optional<Weapon> held(Player player) {
        return HotbarItem.of(player.getInventory().getItemInMainHand(), Weapon.class)
                .filter(weapon -> game.survivor(player) != null && game.survivor(player).weapons().containsKey(weapon));
    }

    /** Hands out the gun; a player with two guns loses the held one, or the newer one when holding neither. */
    public void give(Player player, Weapon weapon) {
        var survivor = game.survivor(player);
        give(player, weapon, held(player).orElse(survivor.weapons().keySet().stream().skip(1).findFirst().orElse(weapon)));
    }

    /** Hands out the gun in the slot of {@code replaced}, which only matters for a player who already has two. */
    public void give(Player player, Weapon weapon, Weapon replaced) {
        var stats = game.config().guns().get(weapon);
        var owned = game.survivor(player).weapons();
        var ammo = new Ammo(stats.clip(), stats.reserve());
        if (!owned.containsKey(weapon) && owned.size() == MAX_WEAPONS && owned.containsKey(replaced)) {
            var kept = new LinkedHashMap<>(owned);
            owned.clear();
            kept.forEach((gun, rounds) -> owned.put(gun == replaced ? weapon : gun, gun == replaced ? ammo : rounds));
        } else {
            owned.put(weapon, ammo);
        }
        render(player);
    }

    /** Whether another gun would have to take one of the player's place. */
    public boolean full(Player player) {
        return game.survivor(player).weapons().size() >= MAX_WEAPONS;
    }

    public Component displayName(Player player, Weapon weapon) {
        var ammo = game.survivor(player).weapons().get(weapon);
        return displayName(weapon, ammo != null && ammo.upgraded());
    }

    private Component displayName(Weapon weapon, boolean upgraded) {
        return (upgraded ? upgradedNames : names).computeIfAbsent(weapon, gun ->
                game.messages().get(upgraded ? "weapon.upgraded" : "weapon.name",
                        Placeholder.component("weapon", game.messages().get("weapon." + gun.key()))));
    }

    public void render(Player player) {
        var survivor = game.survivor(player);
        if (survivor == null) {
            return;
        }
        int slot = 0;
        for (var entry : survivor.weapons().entrySet()) {
            renderGun(player, slot++, entry.getKey(), entry.getValue());
        }
        if (slot < MAX_WEAPONS) {
            player.getInventory().setItem(1, null);
        }
        player.getInventory().setItem(2, PackModel.ZOMBIES_KNIFE.item().name(game.messages().get("weapon.knife"))
                .lore(game.messages().get("weapon.knife-lore")).hideAttributes().edit(meta -> meta.setUnbreakable(true)).build());
    }

    private void renderGun(Player player, int slot, Weapon weapon, Ammo ammo) {
        var builder = weapon.model().item().name(displayName(weapon, ammo.upgraded()))
                .lore(game.messages().getLines("weapon.lore", Formatter.number("clip", ammo.clip()), Formatter.number("reserve", ammo.reserve())))
                .hideAttributes().edit(meta -> meta.setUnbreakable(true));
        if (ammo.upgraded()) {
            builder.glow();
        }
        player.getInventory().setItem(slot, weapon.build(builder));
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
        if (game.enemies().abilities().pinned(player)) {
            BlockcraftApi.get().notifications().feedback(player, game.messages().get("pouncer.pinned"));
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
        Bukkit.getPluginManager().callEvent(new ShotEvent(player));
        var eye = player.getEyeLocation();
        eye.getWorld().playSound(eye, weapon == Weapon.FLAMETHROWER ? Sound.ITEM_FIRECHARGE_USE : Sound.ENTITY_FIREWORK_ROCKET_BLAST,
                0.35f, weapon == Weapon.SNIPER ? 0.7f : 1.7f);
        double multiplier = ammo.upgraded() ? stats.upgradeMultiplier() : 1;
        var volley = weapon == Weapon.FLAMETHROWER ? spray(eye, stats, multiplier) : fire(eye, stats, multiplier);
        for (var entry : volley.hits().entrySet()) {
            var mob = entry.getKey();
            var hit = entry.getValue();
            game.addGold(player, game.config().hitGold());
            if (survivor.perks().contains(Perk.FROZEN_BULLETS)) {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
            if (stats.burnDamage() > 0) {
                flames.ignite(player, mob, stats.burnDamage() * multiplier, stats.burnTicks(), tick);
            }
            game.enemies().hit(player, mob, hit.body(), hit.head());
        }
        if (!volley.hits().isEmpty()) {
            Sounds.NOTE_PLING.note(player, volley.hits().values().stream().anyMatch(hit -> hit.head() > 0) ? 20 : 12);
        }
        int slot = 0;
        for (var entry : survivor.weapons().entrySet()) {
            if (entry.getKey() == weapon) {
                renderGun(player, slot, weapon, entry.getValue());
                break;
            }
            slot++;
        }
    }

    private record Hit(double body, double head) {

        Hit plus(Hit other) {
            return new Hit(body + other.body, head + other.head);
        }
    }

    private record Volley(Map<Mob, Hit> hits) {
    }

    private Volley fire(Location eye, ZombiesConfig.Gun stats, double multiplier) {
        var world = eye.getWorld();
        var hits = new LinkedHashMap<Mob, Hit>();
        for (int pellet = 0; pellet < stats.pellets(); pellet++) {
            var random = ThreadLocalRandom.current();
            var direction = eye.getDirection();
            if (stats.spread() > 0) {
                direction.add(new Vector(random.nextDouble(-stats.spread(), stats.spread()),
                        random.nextDouble(-stats.spread(), stats.spread()), random.nextDouble(-stats.spread(), stats.spread()))).normalize();
            }
            var wall = world.rayTraceBlocks(eye, direction, stats.range(), FluidCollisionMode.NEVER, true);
            double range = wall == null ? stats.range() : wall.getHitPosition().distance(eye.toVector());
            var shot = range <= 0 ? Optional.<Enemies.Shot>empty() : game.enemies().trace(eye.toVector(), direction, range);
            double length = shot.map(hit -> hit.at().distance(eye.toVector())).orElse(range);
            for (double step = 0.5; step < length; step += 1.5) {
                world.spawnParticle(Particle.CRIT, eye.clone().add(direction.clone().multiply(step)), 1, 0, 0, 0, 0);
            }
            shot.ifPresent(hit -> {
                double damage = stats.damage() * multiplier;
                hits.merge(hit.mob(), hit.head() ? new Hit(0, damage * game.config().headshotMultiplier()) : new Hit(damage, 0), Hit::plus);
            });
        }
        return new Volley(hits);
    }

    /** Every enemy in sight inside a cone whose half-angle is the spread, not only the first one in the flame's way. */
    private Volley spray(Location eye, ZombiesConfig.Gun stats, double multiplier) {
        var world = eye.getWorld();
        var direction = eye.getDirection();
        flameJet(eye, direction, stats);
        var hits = new LinkedHashMap<Mob, Hit>();
        for (var entity : world.getNearbyEntities(eye, stats.range(), stats.range(), stats.range(),
                entity -> game.enemies().contains(entity) && !entity.isDead())) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            var box = game.enemies().bounds(mob);
            var toward = box.getCenter().subtract(eye.toVector());
            double distance = toward.length();
            if (distance == 0 || distance > stats.range() || direction.angle(toward) > stats.spread() + Math.atan2(Math.max(box.getWidthX(), box.getWidthZ()), distance)
                    || world.rayTraceBlocks(eye, toward.normalize(), distance, FluidCollisionMode.NEVER, true) != null) {
                continue;
            }
            hits.put(mob, new Hit(stats.damage() * multiplier, 0));
        }
        return new Volley(hits);
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

    public void struck(Entity damager, Player player) {
        flames.struck(damager, player);
    }

    public void tick(Player player, int tick) {
        var survivor = game.survivor(player);
        if (survivor != null && survivor.fighting()) {
            boolean changed = false;
            int slot = 0;
            for (var entry : survivor.weapons().entrySet()) {
                if (entry.getValue().tick(tick)) {
                    renderGun(player, slot, entry.getKey(), entry.getValue());
                    changed = true;
                }
                slot++;
            }
            if (changed) {
                Sounds.EQUIP.play(player);
            }
        }
    }
}
