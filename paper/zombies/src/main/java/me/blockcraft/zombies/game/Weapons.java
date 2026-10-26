package me.blockcraft.zombies.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class Weapons {

    private final ZombiesGame game;

    public Weapons(ZombiesGame game) {
        this.game = game;
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
        var world = player.getWorld();
        world.playSound(eye, weapon == Weapon.FLAMETHROWER ? Sound.ITEM_FIRECHARGE_USE : Sound.ENTITY_FIREWORK_ROCKET_BLAST,
                0.35f, weapon == Weapon.SNIPER ? 0.7f : 1.7f);
        var hits = new LinkedHashMap<Mob, Double>();
        boolean headshot = false;
        for (int pellet = 0; pellet < stats.pellets(); pellet++) {
            var random = ThreadLocalRandom.current();
            var direction = eye.getDirection();
            if (stats.spread() > 0) {
                direction.add(new org.bukkit.util.Vector(random.nextDouble(-stats.spread(), stats.spread()),
                        random.nextDouble(-stats.spread(), stats.spread()), random.nextDouble(-stats.spread(), stats.spread()))).normalize();
            }
            var wall = world.rayTraceBlocks(eye, direction, stats.range(), FluidCollisionMode.NEVER, true);
            double range = wall == null ? stats.range() : wall.getHitPosition().distance(eye.toVector());
            var hit = range <= 0 ? null : world.rayTraceEntities(eye, direction, range, 0.08,
                    entity -> entity instanceof Mob && game.enemies().contains(entity) && !entity.isDead());
            double length = hit == null ? range : hit.getHitPosition().distance(eye.toVector());
            for (double step = 0.5; step < length; step += weapon == Weapon.FLAMETHROWER ? 0.7 : 1.5) {
                world.spawnParticle(weapon == Weapon.FLAMETHROWER ? Particle.FLAME : Particle.CRIT,
                        eye.clone().add(direction.clone().multiply(step)), 1, 0, 0, 0, 0);
            }
            if (hit != null && hit.getHitEntity() instanceof Mob mob) {
                boolean head = hit.getHitPosition().getY() > mob.getLocation().getY() + mob.getHeight() * 0.8;
                headshot |= head;
                double damage = stats.damage() * (head ? game.config().headshotMultiplier() : 1) * (ammo.upgraded() ? stats.upgradeMultiplier() : 1);
                hits.merge(mob, damage, Double::sum);
            }
        }
        for (var entry : hits.entrySet()) {
            var mob = entry.getKey();
            game.addGold(player, game.config().hitGold());
            if (survivor.perks().contains(Perk.FROZEN_BULLETS)) {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
            game.enemies().hit(player, mob, game.instantKill() ? mob.getHealth() * 100 : entry.getValue());
        }
        if (!hits.isEmpty()) {
            Sounds.NOTE_PLING.note(player, headshot ? 20 : 12);
        }
        render(player);
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
