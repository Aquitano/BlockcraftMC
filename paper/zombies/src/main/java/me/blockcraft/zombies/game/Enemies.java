package me.blockcraft.zombies.game;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import me.blockcraft.api.game.BlockChanges;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.zombies.arena.ArenaMap;
import me.blockcraft.zombies.config.ZombiesConfig;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Enemies {

    private static final int HUNT_INTERVAL = 10;
    private static final Set<EnemyKind> HUNT_ABILITIES = EnumSet.of(EnemyKind.EXPLODER, EnemyKind.TOXIC, EnemyKind.INFERNO,
            EnemyKind.FROST, EnemyKind.ARSONIST, EnemyKind.ELECTRIC, EnemyKind.SHADOW, EnemyKind.POUNCER, EnemyKind.RAM);
    private static final String OWNER_TAG = "blockcraft_zombies_enemy";
    private static final Color RUNNER_COLOR = Color.fromRGB(0xE0, 0x6C, 0x1F);
    /** The server rejects a higher max health (spigot.yml, settings.attribute.maxHealth.max). */
    private static final double HEALTH_LIMIT = 1024;
    /** How much a shot may miss an enemy's box by and still hit it, as {@code rayTraceEntities} allowed before. */
    private static final double SHOT_MARGIN = 0.08;
    private static final Set<EnemyKind> EXPLAINED = EnumSet.of(EnemyKind.POUNCER, EnemyKind.RAM, EnemyKind.SCAVENGER);
    private final ZombiesGame game;
    private final Abilities abilities;
    private final Bodies bodies = Bodies.forServer();
    private final Set<EnemyKind> introduced = EnumSet.noneOf(EnemyKind.class);
    private final Map<UUID, Enemy> enemies = new LinkedHashMap<>();
    private final Map<String, Integer> breaking = new HashMap<>();
    private final Map<String, List<Block>> boards = new HashMap<>();
    private int nextNavigationSlot;
    private int nextSpawn;
    private int unspawned;
    private WavePlan plan;
    private boolean applyingDamage;

    static final class Enemy {
        final Mob mob;
        final EnemyKind kind;
        final ArenaMap.Window window;
        final double toughness;
        /** The model it wears, absent for a plain mob and wherever BetterModel is missing. */
        final Optional<CreatureBody> body;
        final double scale;
        int navigationSlot;
        boolean breached;
        UUID attacker;
        int specialAt;
        int fuseAt;

        /** {@code toughness} divides every hit, standing in for the health above the server's limit. */
        Enemy(Mob mob, EnemyKind kind, ArenaMap.Window window, double toughness, Optional<CreatureBody> body, double scale) {
            this.mob = mob;
            this.kind = kind;
            this.window = window;
            this.toughness = toughness;
            this.body = body;
            this.scale = scale;
        }

        BoundingBox bounds() {
            return body.map(drawn -> drawn.bounds(mob.getLocation().toVector(), mob.getBodyYaw(), scale)).orElseGet(mob::getBoundingBox);
        }

        Vector facing() {
            return CreatureBody.facing(mob.getBodyYaw());
        }
    }

    /** Where a shot struck an enemy, and whether it struck the head. */
    record Shot(Mob mob, Vector at, boolean head) {
    }

    public Enemies(ZombiesGame game) {
        this.game = game;
        this.abilities = new Abilities(game, this);
        game.arena().world().getEntities().stream().filter(entity -> entity.getScoreboardTags().contains(OWNER_TAG)).forEach(Entity::remove);
    }

    public void begin(WavePlan plan) {
        this.plan = plan;
        this.unspawned = plan.enemies();
        this.nextSpawn = 0;
    }

    public int remaining() { return unspawned + enemies.size(); }
    public double spawnedShare() { return plan == null ? 0 : 1 - unspawned / (double) plan.enemies(); }
    public List<Mob> living() { return enemies.values().stream().map(enemy -> enemy.mob).filter(Mob::isValid).toList(); }
    public boolean contains(Entity entity) { return enemies.containsKey(entity.getUniqueId()); }
    public boolean breached(Entity entity) {
        var enemy = enemies.get(entity.getUniqueId());
        return enemy != null && enemy.breached;
    }
    public boolean applyingDamage() { return applyingDamage; }
    List<Enemy> of(EnemyKind kind) { return enemies.values().stream().filter(enemy -> enemy.kind == kind).toList(); }
    public Abilities abilities() { return abilities; }
    Enemy enemy(Entity entity) { return enemies.get(entity.getUniqueId()); }
    double damage(Enemy enemy) { return stats(enemy).damage() * plan.damageMultiplier(); }

    public void tick(int tick) {
        var waves = game.config().waves();
        if (unspawned > 0 && (tick >= nextSpawn || enemies.isEmpty()) && enemies.size() < waves.maxAlive()) {
            var windows = game.arena().activeWindows();
            if (!windows.isEmpty()) {
                spawn(windows.get(ThreadLocalRandom.current().nextInt(windows.size())));
                nextSpawn = tick + plan.spawnTicks();
            }
        }
        boards.clear();
        abilities.tick(tick);
        boolean stragglers = unspawned == 0 && enemies.size() <= waves.stragglers();
        for (var enemy : new ArrayList<>(enemies.values())) {
            if (!enemy.mob.isValid() || enemy.mob.isDead()) {
                enemies.remove(enemy.mob.getUniqueId());
                continue;
            }
            if (abilities.of(enemy.kind).holds(enemy)) {
                continue;
            }
            if (enemy.fuseAt > 0) {
                if (tick >= enemy.fuseAt) {
                    explode(enemy);
                    continue;
                }
                if (tick % 2 == 0) {
                    var fuse = enemy.mob.getEyeLocation().add(0, 0.5, 0);
                    enemy.mob.getWorld().spawnParticle(Particle.SMOKE, fuse, 2, 0.1, 0.1, 0.1, 0.01);
                    enemy.mob.getWorld().spawnParticle(Particle.FLAME, fuse, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
            if (stragglers && enemy.kind != EnemyKind.INFERNO) {
                hurry(enemy.mob, waves.stragglerSpeed());
            }
            if (!enemy.breached) {
                breach(enemy, tick);
            } else {
                boolean navigate = navigates(enemy, tick);
                boolean useAbility = tick % HUNT_INTERVAL == 0 && HUNT_ABILITIES.contains(enemy.kind);
                if (navigate || useAbility) {
                    hunt(enemy, tick, navigate, useAbility);
                }
            }
        }
    }

    private void spawn(ArenaMap.Window window) {
        EnemyKind kind = plan.boss() && unspawned == plan.enemies() ? EnemyKind.INFERNO : plan.hounds() ? EnemyKind.HELLHOUND : choose();
        if (EXPLAINED.contains(kind) && introduced.add(kind)) {
            game.broadcast("enemy-hint." + kind.key());
        }
        int pack = Math.min(abilities.of(kind).pack(), game.config().waves().maxAlive() - enemies.size());
        for (int member = 0; member < pack && unspawned > 0; member++) {
            if (summon(kind, window, window.spawn(), false) != null) {
                unspawned--;
            }
        }
    }

    /** Adds an enemy to the running wave, which clears only once it is dead, whether or not its plan counted it. */
    Enemy summon(EnemyKind kind, ArenaMap.Window window, Location at, boolean breached) {
        var stats = game.config().enemies().get(kind);
        var mob = (Mob) game.arena().world().spawnEntity(at, kind.entity(),
                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!mob.isValid()) {
            mob.remove();
            return null;
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
        if (mob instanceof Wolf wolf) {
            wolf.setVariant(Wolf.Variant.BLACK);
            wolf.setSoundVariant(Wolf.SoundVariant.ANGRY);
            wolf.setAngry(true);
        }
        double bossScale = kind == EnemyKind.INFERNO ? 1 + (game.teamSize() - 1) * game.config().waves().extraPlayer() : 1;
        double health = stats.health() * plan.healthMultiplier() * bossScale;
        double toughness = Math.max(1, health / HEALTH_LIMIT);
        attribute(mob, Attribute.MAX_HEALTH, health / toughness);
        mob.setHealth(health / toughness);
        attribute(mob, Attribute.MOVEMENT_SPEED, stats.speed());
        attribute(mob, Attribute.ATTACK_DAMAGE, stats.damage() * plan.damageMultiplier());
        attribute(mob, Attribute.FOLLOW_RANGE, 100);
        var body = kind.body().filter(model -> bodies.draw(mob, model.model(), stats.scale()));
        attribute(mob, Attribute.SCALE, kind.body().map(CreatureBody::mobScale).orElse(stats.scale()));
        var equipment = mob.getEquipment();
        if (equipment != null) {
            equipment.clear();
            dress(kind, equipment);
            abilities.of(kind).equip(mob, equipment);
        }
        var enemy = new Enemy(mob, kind, window, toughness, body, stats.scale());
        enemy.breached = breached;
        enemy.navigationSlot = nextNavigationSlot;
        nextNavigationSlot = (nextNavigationSlot + 1) % HUNT_INTERVAL;
        enemies.put(mob.getUniqueId(), enemy);
        return enemy;
    }

    private EnemyKind choose() {
        var weights = game.config().mix(plan.wave());
        if (!game.powered()) {
            weights.remove(EnemyKind.SCAVENGER);
        }
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return EnemyKind.WALKER;
        }
        double selection = ThreadLocalRandom.current().nextDouble(total);
        for (var weight : weights.entrySet()) {
            selection -= weight.getValue();
            if (selection < 0) {
                return weight.getKey();
            }
        }
        return EnemyKind.WALKER;
    }

    /** The head slot asks for the pack's model; the rest reads without the pack. Gear adds no armour beyond the config's health. */
    private static void dress(EnemyKind kind, EntityEquipment equipment) {
        switch (kind) {
            case RUNNER -> {
                equipment.setHelmet(PackModel.ZOMBIES_RUNNER_GOGGLES.item().build());
                equipment.setChestplate(gear(Items.of(Material.LEATHER_CHESTPLATE).color(RUNNER_COLOR)));
                equipment.setBoots(gear(Items.of(Material.LEATHER_BOOTS).color(RUNNER_COLOR)));
            }
            case TANK -> {
                equipment.setHelmet(PackModel.ZOMBIES_RIOT_HELMET.item().build());
                equipment.setChestplate(gear(Items.of(Material.IRON_CHESTPLATE)));
                equipment.setLeggings(gear(Items.of(Material.IRON_LEGGINGS)));
                equipment.setBoots(gear(Items.of(Material.IRON_BOOTS)));
            }
            case EXPLODER -> equipment.setHelmet(PackModel.ZOMBIES_DYNAMITE.item().build());
            case TOXIC -> equipment.setHelmet(PackModel.ZOMBIES_GAS_MASK.item().build());
            case INFERNO -> {
                equipment.setHelmet(PackModel.ZOMBIES_INFERNO.item().build());
                equipment.setChestplate(gear(Items.of(Material.NETHERITE_CHESTPLATE)));
            }
            case WALKER, HELLHOUND, POUNCER, RAM, SCAVENGER -> { }
        }
    }

    static ItemStack gear(Items.Builder item) {
        var stack = item.build();
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build());
        return stack;
    }

    private static void hurry(Mob mob, double speed) {
        var movement = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement != null && movement.getBaseValue() < speed) {
            movement.setBaseValue(speed);
        }
    }

    private static boolean navigates(Enemy enemy, int tick) {
        return tick % HUNT_INTERVAL == enemy.navigationSlot;
    }

    private void breach(Enemy enemy, int tick) {
        var remaining = boards.computeIfAbsent(enemy.window.id(), ignored -> enemy.window.boards().blocks(game.arena().world())
                .stream().filter(block -> !block.getType().isAir()).toList());
        if (remaining.isEmpty()) {
            enemy.breached = true;
            enemy.mob.getPathfinder().moveTo(enemy.window.entry(), 1.0);
            return;
        }
        var approach = enemy.window.approach();
        if (navigates(enemy, tick)) {
            enemy.mob.getPathfinder().moveTo(approach, 1.0);
        }
        if (enemy.mob.getLocation().distanceSquared(approach) > 4) {
            return;
        }
        if (tick < breaking.getOrDefault(enemy.window.id(), 0)) {
            return;
        }
        if (abilities.of(enemy.kind).smash(enemy, remaining, tick)) {
            return;
        }
        var block = remaining.getFirst();
        block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, block.getBlockData());
        BlockChanges.set(block, Material.AIR);
        boards.remove(enemy.window.id());
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.6f, 0.8f);
        breaking.put(enemy.window.id(), tick + game.config().breakTicks());
    }

    private void hunt(Enemy enemy, int tick, boolean navigate, boolean useAbility) {
        var at = enemy.mob.getLocation();
        var target = game.fighters().stream().min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(at)));
        if (target.isEmpty()) {
            return;
        }
        Player player = target.get();
        if (abilities.of(enemy.kind).hunt(enemy, player, tick, navigate, useAbility)) {
            return;
        }
        if (navigate) {
            enemy.mob.setTarget(player);
            enemy.mob.getPathfinder().moveTo(player, 1.0);
        }
        if (!useAbility || tick < enemy.specialAt) {
            return;
        }
        var exploder = game.config().exploder();
        if (enemy.kind == EnemyKind.EXPLODER && enemy.fuseAt == 0
                && enemy.mob.getLocation().distanceSquared(player.getLocation()) < exploder.triggerRange() * exploder.triggerRange()) {
            enemy.fuseAt = tick + exploder.fuseTicks();
            enemy.mob.setGlowing(true);
            enemy.mob.getWorld().playSound(enemy.mob.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.4f, 0.8f);
            hurry(enemy.mob, exploder.rushSpeed());
        } else if (enemy.kind == EnemyKind.TOXIC && stats(enemy).slowTicks() > 0
                && enemy.mob.getLocation().distanceSquared(player.getLocation()) < 6 && enemy.mob.hasLineOfSight(player)) {
            int slowTicks = stats(enemy).slowTicks();
            enemy.specialAt = tick + slowTicks;
            player.getWorld().spawnParticle(Particle.ITEM_SLIME, player.getLocation().add(0, 1, 0), 16, 0.3, 0.5, 0.3, 0);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SLIME_SQUISH, 0.8f, 0.7f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 1));
        } else if (enemy.kind == EnemyKind.INFERNO && enemy.mob.hasLineOfSight(player)
                && enemy.mob.getLocation().distanceSquared(player.getLocation()) <= Math.pow(game.config().inferno().blastRange(), 2)) {
            enemy.specialAt = tick + 80;
            blast(enemy, player);
        }
    }

    private void blast(Enemy enemy, Player player) {
        var world = enemy.mob.getWorld();
        var from = enemy.mob.getEyeLocation();
        var line = player.getLocation().add(0, player.getHeight() / 2, 0).toVector().subtract(from.toVector());
        int steps = (int) Math.ceil(line.length() / 0.4);
        world.spawnParticle(Particle.FLAME, from, 20, 0.3, 0.3, 0.3, 0.05);
        for (int step = 1; step <= steps; step++) {
            world.spawnParticle(Particle.FLAME, from.clone().add(line.clone().multiply(step / (double) steps)), 2, 0.05, 0.05, 0.05, 0.01);
        }
        world.playSound(from, Sound.ENTITY_BLAZE_SHOOT, 2, 0.6f);
        player.damage(damage(enemy), enemy.mob);
    }

    /** Nobody earns an exploder that blew itself up, even if someone hit it before. */
    private void explode(Enemy enemy) {
        var settings = game.config().exploder();
        var mob = enemy.mob;
        var center = mob.getLocation();
        mob.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        mob.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.8f);
        for (var fighter : game.fighters()) {
            double distance = fighter.getLocation().distance(center);
            if (distance > settings.radius() || !mob.hasLineOfSight(fighter)) {
                continue;
            }
            double closeness = 1 - distance / settings.radius();
            fighter.damage(damage(enemy) * (0.4 + 0.6 * closeness), mob);
            var push = fighter.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (push.lengthSquared() > 0) {
                push.normalize();
            }
            fighter.setVelocity(push.multiply(settings.knockback() * closeness).setY(0.3 + 0.4 * closeness));
        }
        enemy.attacker = null;
        mob.setHealth(0);
    }

    /** The nearest enemy whose box the ray from {@code eye} crosses within {@code range}. */
    Optional<Shot> trace(Vector eye, Vector direction, double range) {
        Shot nearest = null;
        double reach = range;
        for (var enemy : enemies.values()) {
            if (!enemy.mob.isValid() || enemy.mob.isDead()) {
                continue;
            }
            var box = enemy.bounds().expand(SHOT_MARGIN);
            var hit = box.contains(eye) ? eye : Optional.ofNullable(box.rayTrace(eye, direction, reach)).map(RayTraceResult::getHitPosition).orElse(null);
            if (hit == null || hit.distance(eye) > reach) {
                continue;
            }
            reach = hit.distance(eye);
            nearest = new Shot(enemy.mob, hit, head(enemy, hit));
        }
        return Optional.ofNullable(nearest);
    }

    private static boolean head(Enemy enemy, Vector hit) {
        var feet = enemy.mob.getLocation().toVector();
        return enemy.body.map(body -> body.isHead(feet, enemy.mob.getBodyYaw(), enemy.scale, hit))
                .orElseGet(() -> hit.getY() > feet.getY() + enemy.mob.getHeight() * 0.8);
    }

    /** The space an enemy fills: its model's box, or its own. */
    BoundingBox bounds(Mob mob) {
        var enemy = enemies.get(mob.getUniqueId());
        return enemy == null ? mob.getBoundingBox() : enemy.bounds();
    }

    void play(Enemy enemy, String animation) {
        if (enemy.body.isPresent()) {
            bodies.play(enemy.mob, animation);
        }
    }

    public void hit(Player player, Mob mob, double damage) {
        hit(player, mob, damage, 0);
    }

    public void hit(Player player, Mob mob, double bodyDamage, double headDamage) {
        var enemy = enemies.get(mob.getUniqueId());
        if (enemy == null || !game.fighting(player) || mob.isDead()) {
            return;
        }
        enemy.attacker = player.getUniqueId();
        mob.setNoDamageTicks(0);
        var ability = abilities.of(enemy.kind);
        double dealt = (bodyDamage > 0 ? ability.shot(enemy, player, bodyDamage, false) : 0)
                + (headDamage > 0 ? ability.shot(enemy, player, headDamage, true) : 0);
        applyingDamage = true;
        try {
            mob.damage(game.instantKill() && enemy.kind != EnemyKind.INFERNO ? mob.getHealth() * 100 : dealt / enemy.toughness, player);
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
        if (enemy != null) {
            play(enemy, "death");
            abilities.of(enemy.kind).died(enemy);
        }
        return enemy == null || enemy.attacker == null ? null : org.bukkit.Bukkit.getPlayer(enemy.attacker);
    }

    public void clear() {
        enemies.values().forEach(enemy -> enemy.mob.remove());
        enemies.clear();
        abilities.clear();
        bodies.clear();
        breaking.clear();
        unspawned = 0;
    }

    private ZombiesConfig.Enemy stats(Enemy enemy) {
        return game.config().enemies().get(enemy.kind);
    }

    private static void attribute(Mob mob, Attribute attribute, double value) {
        var instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
