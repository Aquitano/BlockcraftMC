package me.blockcraft.sternenfront.bot;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Heat;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Sabers;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Bots implements Listener {

    private static final int THINK_TICKS = 4;
    private static final int ROUTE_TICKS = 20;
    private static final int OBJECTIVE_TICKS = 60;
    private static final double KEEP_DISTANCE = 8;

    private static final class Soldier {
        private final int number;
        private final Faction faction;
        private Mob body;
        private Heat heat;
        private long deployAt;
        private long protectedUntil;
        private UUID target;
        private long reactAt;
        private int objective = -1;
        private long chooseAt;
        private Location progress;
        private int stuckTicks;

        private Soldier(int number, Faction faction) {
            this.number = number;
            this.faction = faction;
        }
    }

    private final SternenfrontGame game;
    private final BotConfig config;
    private final List<Soldier> soldiers = new ArrayList<>();
    private final Map<UUID, Soldier> bodies = new HashMap<>();
    private final Map<UUID, Faction> identities = new HashMap<>();
    private int nextNumber;

    public Bots(SternenfrontGame game, BotConfig config) {
        this.game = game;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, game.plugin());
    }

    public boolean enabled() {
        return config.enabled();
    }

    public List<Mob> entities() {
        return bodies.values().stream().map(soldier -> soldier.body).filter(body -> body.isValid() && !body.isDead()).toList();
    }

    public Optional<Faction> faction(Entity entity) {
        return Optional.ofNullable(identities.get(entity.getUniqueId()));
    }

    public boolean protectedFromDamage(Entity entity) {
        var soldier = bodies.get(entity.getUniqueId());
        return soldier != null && game.tick() < soldier.protectedUntil;
    }

    public void fill() {
        for (var faction : Faction.values()) {
            int needed = config.needed(game.humans(faction));
            var factionSoldiers = soldiers.stream().filter(soldier -> soldier.faction == faction)
                    .sorted(Comparator.comparing((Soldier soldier) -> soldier.body != null).reversed()).toList();
            for (int index = needed; index < factionSoldiers.size(); index++) {
                var soldier = factionSoldiers.get(index);
                remove(soldier);
                soldiers.remove(soldier);
            }
            for (int index = factionSoldiers.size(); index < needed; index++) {
                soldiers.add(new Soldier(++nextNumber, faction));
            }
        }
    }

    public void tick(long tick) {
        if (!enabled()) {
            return;
        }
        if (tick % 20 == 1) {
            fill();
        }
        for (var soldier : List.copyOf(soldiers)) {
            if (soldier.body == null) {
                if (tick >= soldier.deployAt) {
                    deploy(soldier, tick);
                }
                continue;
            }
            if (!soldier.body.isValid() || soldier.body.isDead()) {
                remove(soldier);
                soldier.deployAt = tick + game.config().respawn().seconds() * 20L;
                continue;
            }
            soldier.heat.cool(tick);
            if (soldier.body.getLocation().getY() < game.map().playArea().getMinY() - 5) {
                soldier.body.setHealth(0);
                continue;
            }
            if (tick % THINK_TICKS == soldier.number % THINK_TICKS) {
                think(soldier, tick);
            }
        }
    }

    private void deploy(Soldier soldier, long tick) {
        var spawns = game.map().headquarters().get(soldier.faction).spawns();
        var at = spawns.get((soldier.number - 1) % spawns.size());
        var mob = at.getWorld().spawn(at, Skeleton.class, skeleton -> {
            skeleton.setPersistent(false);
            skeleton.setRemoveWhenFarAway(false);
            skeleton.setCanPickupItems(false);
            skeleton.setShouldBurnInDay(false);
            skeleton.setSilent(true);
            skeleton.customName(game.messages().get("bot.name",
                    Placeholder.styling("tint", soldier.faction.text()),
                    Placeholder.component("faction", game.messages().get("faction." + soldier.faction.key() + ".name")),
                    Formatter.number("number", soldier.number)));
            skeleton.setCustomNameVisible(true);
            double health = game.config().health(CombatClass.SOLDIER);
            skeleton.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
            skeleton.setHealth(health);
            skeleton.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(128);
            skeleton.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25);
            var equipment = skeleton.getEquipment();
            equipment.setItemInMainHand(game.loadouts().blaster(Blaster.CARBINE));
            equipment.setHelmet((soldier.faction == Faction.KOLONIEN ? PackModel.STERNENFRONT_HELMET_KOLONIEN
                    : PackModel.STERNENFRONT_HELMET_HEGEMONIE).item().build());
            equipment.setChestplate(armour(Material.LEATHER_CHESTPLATE, soldier.faction));
            equipment.setLeggings(armour(Material.LEATHER_LEGGINGS, soldier.faction));
            equipment.setBoots(armour(Material.LEATHER_BOOTS, soldier.faction));
            equipment.setItemInMainHandDropChance(0);
            equipment.setHelmetDropChance(0);
            equipment.setChestplateDropChance(0);
            equipment.setLeggingsDropChance(0);
            equipment.setBootsDropChance(0);
        });
        Bukkit.getMobGoals().removeAllGoals(mob);
        soldier.body = mob;
        soldier.heat = new Heat(game.config().heat());
        soldier.protectedUntil = tick + game.config().respawn().protectionSeconds() * 20L;
        soldier.target = null;
        soldier.objective = -1;
        soldier.chooseAt = tick;
        soldier.progress = at.clone();
        soldier.stuckTicks = 0;
        bodies.put(mob.getUniqueId(), soldier);
        identities.put(mob.getUniqueId(), soldier.faction);
    }

    private static ItemStack armour(Material material, Faction faction) {
        var item = Items.of(material).color(faction.armour()).hideAttributes().edit(meta -> meta.setUnbreakable(true)).build();
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build());
        return item;
    }

    private void think(Soldier soldier, long tick) {
        var mob = soldier.body;
        var here = mob.getLocation();
        if (tick >= soldier.chooseAt || soldier.objective < 0) {
            choose(soldier, tick);
        }
        var target = game.combatants().stream()
                .filter(other -> game.hittable(soldier.faction, other))
                .filter(other -> here.getWorld().equals(other.getWorld()))
                .filter(other -> here.distanceSquared(other.getLocation()) <= config.range() * config.range())
                .filter(mob::hasLineOfSight)
                .min(Comparator.comparingDouble(other -> here.distanceSquared(other.getLocation())));
        if (target.isPresent()) {
            var enemy = target.get();
            if (!enemy.getUniqueId().equals(soldier.target)) {
                soldier.target = enemy.getUniqueId();
                soldier.reactAt = tick + config.reactionTicks() + ThreadLocalRandom.current().nextInt(config.reactionTicks() + 1);
            }
            mob.lookAt(enemy);
            if (tick >= soldier.reactAt) {
                fire(soldier, enemy, tick);
            }
        } else {
            soldier.target = null;
        }
        if ((tick - soldier.number % THINK_TICKS) % ROUTE_TICKS == 0) {
            navigate(soldier, target, tick);
        }
    }

    private void choose(Soldier soldier, long tick) {
        var here = soldier.body.getLocation();
        if (soldier.objective >= 0 && game.map().posts().get(soldier.objective).contains(here)
                && game.posts().owner(soldier.objective).filter(soldier.faction::equals).isEmpty()) {
            soldier.chooseAt = tick + OBJECTIVE_TICKS;
            return;
        }
        var choices = game.posts().indices().stream().map(index -> new Objectives.Post(index,
                game.posts().owner(index).filter(soldier.faction::equals).isPresent(), game.posts().contested(index),
                here.distance(game.map().posts().get(index).center()),
                (int) soldiers.stream().filter(other -> other != soldier && other.body != null
                        && other.faction == soldier.faction && other.objective == index).count())).toList();
        soldier.objective = Objectives.choose(choices).orElse(-1);
        soldier.chooseAt = tick + OBJECTIVE_TICKS + soldier.number % 20;
    }

    private void navigate(Soldier soldier, Optional<LivingEntity> target, long tick) {
        var mob = soldier.body;
        var here = mob.getLocation();
        var post = soldier.objective < 0 ? null : game.map().posts().get(soldier.objective);
        boolean capturing = post != null && post.contains(here);
        Location destination = null;
        if (target.isPresent() && here.distanceSquared(target.get().getLocation()) < KEEP_DISTANCE * KEEP_DISTANCE && !capturing) {
            var away = here.toVector().subtract(target.get().getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 0.01) {
                destination = here.clone().add(away.normalize().multiply(4));
            }
        } else if (post != null && !capturing) {
            double angle = soldier.number * 2.4 + soldier.stuckTicks * 0.03;
            destination = post.center().clone().add(Math.cos(angle) * post.radius() * 0.55, 0, Math.sin(angle) * post.radius() * 0.55);
        } else if (capturing && target.isPresent()) {
            double angle = tick * 0.025 + soldier.number;
            destination = post.center().clone().add(Math.cos(angle) * post.radius() * 0.45, 0, Math.sin(angle) * post.radius() * 0.45);
        }
        if (destination == null || !game.map().playArea().contains(destination.toVector())) {
            mob.getPathfinder().stopPathfinding();
            soldier.stuckTicks = 0;
            return;
        }
        if (here.distanceSquared(soldier.progress) < 0.5) {
            soldier.stuckTicks += ROUTE_TICKS;
        } else {
            soldier.stuckTicks = 0;
            soldier.progress = here.clone();
        }
        if (soldier.stuckTicks >= 100) {
            soldier.chooseAt = tick;
            if (game.posts().size() > 1) {
                soldier.objective = (soldier.objective + 1) % game.posts().size();
                soldier.chooseAt = tick + OBJECTIVE_TICKS;
            }
            soldier.stuckTicks = 0;
        }
        mob.getPathfinder().moveTo(destination, 1.0);
    }

    private void fire(Soldier soldier, LivingEntity target, long tick) {
        var stats = game.config().stats(Blaster.CARBINE);
        var fired = soldier.heat.fire(tick, Math.max(config.shotTicks(), stats.cooldownTicks()), stats.heat());
        if (fired != Heat.Shot.FIRED && fired != Heat.Shot.OVERHEATED) {
            return;
        }
        var from = soldier.body.getEyeLocation();
        var aim = target.getLocation().add(0, target.getHeight() * 0.65, 0).toVector().subtract(from.toVector());
        if (aim.lengthSquared() < 0.01) {
            return;
        }
        aim.normalize();
        var random = ThreadLocalRandom.current();
        double spread = config.spread();
        if (spread > 0) {
            aim.add(new org.bukkit.util.Vector(random.nextDouble(-spread, spread), random.nextDouble(-spread, spread),
                    random.nextDouble(-spread, spread))).normalize();
        }
        soldier.protectedUntil = 0;
        game.fireBot(soldier.body, soldier.faction, aim);
        soldier.body.swingMainHand();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        var soldier = bodies.get(event.getEntity().getUniqueId());
        if (soldier == null) {
            return;
        }
        if (protectedFromDamage(soldier.body)) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamageSource().getCausingEntity() instanceof LivingEntity attacker
                && game.factionOf(attacker).filter(faction -> faction != soldier.faction).isEmpty()) {
            event.setCancelled(true);
        }
        if (event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Player player
                && (hit.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || hit.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            var held = player.getInventory().getItemInMainHand();
            if (!game.onField(player) || Sabers.isSaber(held) && !Sabers.isLit(held)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMelee(EntityDamageByEntityEvent event) {
        var soldier = bodies.get(event.getEntity().getUniqueId());
        if (soldier != null && event.getDamager() instanceof Player player
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) && game.onField(player)) {
            game.endProtection(player);
            game.landed(player, soldier.body);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        var soldier = bodies.remove(event.getEntity().getUniqueId());
        if (soldier == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        soldier.body = null;
        soldier.deployAt = game.tick() + game.config().respawn().seconds() * 20L;
        var killer = event.getDamageSource().getCausingEntity() instanceof Player player && game.onField(player)
                ? Optional.of(player) : Optional.<Player>empty();
        game.botDied(soldier.faction, killer, config.killPoints());
    }

    private void remove(Soldier soldier) {
        if (soldier.body != null) {
            bodies.remove(soldier.body.getUniqueId());
            soldier.body.remove();
            soldier.body = null;
        }
    }

    public void clear() {
        soldiers.forEach(this::remove);
        soldiers.clear();
        bodies.clear();
        identities.clear();
    }
}
