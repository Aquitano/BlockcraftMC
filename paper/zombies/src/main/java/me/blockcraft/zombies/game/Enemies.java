package me.blockcraft.zombies.game;

import me.blockcraft.zombies.arena.ArenaMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Enemies {

    private static final String OWNER_TAG = "blockcraft_zombies_enemy";
    private final ZombiesGame game;
    private final Map<UUID, Enemy> enemies = new LinkedHashMap<>();
    private final Map<String, Integer> breaking = new HashMap<>();
    private int nextSpawn;
    private int unspawned;
    private WavePlan plan;
    private boolean applyingDamage;

    private static final class Enemy {
        final Mob mob;
        final EnemyKind kind;
        final ArenaMap.Window window;
        boolean breached;
        UUID attacker;
        int specialAt;

        Enemy(Mob mob, EnemyKind kind, ArenaMap.Window window) {
            this.mob = mob;
            this.kind = kind;
            this.window = window;
        }
    }

    public Enemies(ZombiesGame game) {
        this.game = game;
        game.arena().world().getEntities().stream().filter(entity -> entity.getScoreboardTags().contains(OWNER_TAG)).forEach(Entity::remove);
    }

    public void begin(WavePlan plan) {
        this.plan = plan;
        this.unspawned = plan.enemies();
        this.nextSpawn = 0;
    }

    public int remaining() { return unspawned + enemies.size(); }
    public boolean contains(Entity entity) { return enemies.containsKey(entity.getUniqueId()); }
    public boolean breached(Entity entity) {
        var enemy = enemies.get(entity.getUniqueId());
        return enemy != null && enemy.breached;
    }
    public boolean applyingDamage() { return applyingDamage; }

    public void tick(int tick) {
        if (unspawned > 0 && tick >= nextSpawn && enemies.size() < game.config().waves().maxAlive()) {
            var windows = game.arena().activeWindows();
            if (!windows.isEmpty()) {
                spawn(windows.get(ThreadLocalRandom.current().nextInt(windows.size())));
                nextSpawn = tick + plan.spawnTicks();
            }
        }
        for (var enemy : new ArrayList<>(enemies.values())) {
            if (!enemy.mob.isValid() || enemy.mob.isDead()) {
                enemies.remove(enemy.mob.getUniqueId());
                continue;
            }
            if (!enemy.breached) {
                breach(enemy, tick);
            } else if (tick % 10 == 0) {
                hunt(enemy, tick);
            }
        }
    }

    private void spawn(ArenaMap.Window window) {
        EnemyKind kind = plan.boss() && unspawned == plan.enemies() ? EnemyKind.INFERNO : choose();
        var stats = game.config().enemies().get(kind);
        var mob = (Mob) game.arena().world().spawnEntity(window.spawn(), kind == EnemyKind.HELLHOUND ? EntityType.WOLF : EntityType.ZOMBIE,
                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!mob.isValid()) {
            mob.remove();
            return;
        }
        mob.addScoreboardTag(OWNER_TAG);
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.setAI(true);
        mob.setSilent(false);
        mob.customName(game.messages().get("enemy." + kind.key()));
        if (mob instanceof Zombie zombie) {
            zombie.setAdult();
            zombie.setShouldBurnInDay(false);
        }
        double bossScale = kind == EnemyKind.INFERNO ? 1 + (game.teamSize() - 1) * game.config().waves().extraPlayer() : 1;
        double health = stats.health() * plan.healthMultiplier() * bossScale;
        attribute(mob, Attribute.MAX_HEALTH, health);
        mob.setHealth(health);
        attribute(mob, Attribute.MOVEMENT_SPEED, stats.speed());
        attribute(mob, Attribute.ATTACK_DAMAGE, stats.damage());
        attribute(mob, Attribute.FOLLOW_RANGE, 100);
        var equipment = mob.getEquipment();
        if (equipment != null) {
            equipment.clear();
            if (kind == EnemyKind.TANK || kind == EnemyKind.INFERNO) {
                equipment.setHelmet(ItemStack.of(kind == EnemyKind.INFERNO ? Material.CARVED_PUMPKIN : Material.IRON_HELMET));
                equipment.setHelmetDropChance(0);
            }
        }
        enemies.put(mob.getUniqueId(), new Enemy(mob, kind, window));
        unspawned--;
    }

    private EnemyKind choose() {
        var candidates = game.config().enemies().entrySet().stream()
                .filter(entry -> entry.getKey() != EnemyKind.INFERNO && entry.getValue().firstWave() <= plan.wave()).toList();
        int total = candidates.stream().mapToInt(entry -> entry.getValue().weight()).sum();
        if (total == 0) {
            return EnemyKind.WALKER;
        }
        int selection = ThreadLocalRandom.current().nextInt(total);
        for (var candidate : candidates) {
            selection -= candidate.getValue().weight();
            if (selection < 0) {
                return candidate.getKey();
            }
        }
        return EnemyKind.WALKER;
    }

    private void breach(Enemy enemy, int tick) {
        var boards = enemy.window.boards().blocks(game.arena().world());
        var remaining = boards.stream().filter(block -> !block.getType().isAir()).toList();
        if (remaining.isEmpty()) {
            enemy.breached = true;
            enemy.mob.getPathfinder().moveTo(enemy.window.entry(), 1.0);
            return;
        }
        var approach = enemy.window.approach();
        if (tick % 10 == 0) {
            enemy.mob.getPathfinder().moveTo(approach, 1.0);
        }
        if (enemy.mob.getLocation().distanceSquared(approach) > 4) {
            return;
        }
        if (tick < breaking.getOrDefault(enemy.window.id(), 0)) {
            return;
        }
        var block = remaining.getFirst();
        block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, block.getBlockData());
        block.setType(Material.AIR, false);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.6f, 0.8f);
        breaking.put(enemy.window.id(), tick + game.config().breakTicks());
    }

    private void hunt(Enemy enemy, int tick) {
        var target = game.fighters().stream().min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(enemy.mob.getLocation())));
        if (target.isEmpty()) {
            return;
        }
        Player player = target.get();
        enemy.mob.setTarget(player);
        enemy.mob.getPathfinder().moveTo(player, 1.0);
        if (tick < enemy.specialAt) {
            return;
        }
        if (enemy.kind == EnemyKind.EXPLODER && enemy.mob.getLocation().distanceSquared(player.getLocation()) < 5) {
            enemy.specialAt = tick + 100;
            enemy.mob.getWorld().spawnParticle(Particle.EXPLOSION, enemy.mob.getLocation(), 1);
            enemy.mob.getWorld().playSound(enemy.mob.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
            for (var fighter : game.fighters()) {
                if (fighter.getLocation().distanceSquared(enemy.mob.getLocation()) < 12 && enemy.mob.hasLineOfSight(fighter)) {
                    fighter.damage(game.config().enemies().get(enemy.kind).damage(), enemy.mob);
                }
            }
            enemy.mob.setHealth(0);
        } else if (enemy.kind == EnemyKind.INFERNO && enemy.mob.hasLineOfSight(player)) {
            enemy.specialAt = tick + 80;
            enemy.mob.getWorld().spawnParticle(Particle.FLAME, enemy.mob.getEyeLocation(), 20, 0.3, 0.3, 0.3, 0.05);
            player.damage(game.config().enemies().get(enemy.kind).damage(), enemy.mob);
        }
    }

    public void hit(Player player, Mob mob, double damage) {
        var enemy = enemies.get(mob.getUniqueId());
        if (enemy == null || !game.fighting(player) || mob.isDead()) {
            return;
        }
        enemy.attacker = player.getUniqueId();
        mob.setNoDamageTicks(0);
        applyingDamage = true;
        try {
            mob.damage(damage, player);
        } finally {
            applyingDamage = false;
        }
    }

    /** Where what an enemy leaves behind can be reached: one still outside its window leaves it just inside. */
    public Location dropSpot(Mob mob) {
        var enemy = enemies.get(mob.getUniqueId());
        return enemy == null || enemy.breached ? mob.getLocation() : enemy.window.entry();
    }

    public Player killed(Mob mob) {
        var enemy = enemies.remove(mob.getUniqueId());
        return enemy == null || enemy.attacker == null ? null : org.bukkit.Bukkit.getPlayer(enemy.attacker);
    }

    public void clear() {
        enemies.values().forEach(enemy -> enemy.mob.remove());
        enemies.clear();
        breaking.clear();
        unspawned = 0;
    }

    private static void attribute(Mob mob, Attribute attribute, double value) {
        var instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
