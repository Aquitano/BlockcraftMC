package me.blockcraft.sternenfront.bot;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.sternenfront.combat.Melee;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Heat;
import me.blockcraft.sternenfront.game.Loadouts;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Sabers;
import me.blockcraft.sternenfront.hero.Hero;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
import org.bukkit.util.Vector;

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
    private static final double PEEK_REACH = 3;
    private static final double WOUNDED_SHARE = 0.3;
    /**
     * A skeleton already stands 1.99 blocks. Any taller and the pathfinder plans three-high paths, so a larger class
     * stays at that height: about what a player of that class measures, and it still fits the wreck's corridors.
     */
    private static final double TALLEST_BODY_SCALE = 1;

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
        private final CombatClass role;
        private Vector aim;
        private int burst;
        private long pauseUntil;
        private long healAt;
        private final Volley volley = new Volley();
        private Location lastSeen;
        private long seenUntil;
        private int waypoint;
        private Vector idleAim;
        private boolean moving;
        private UUID patient;
        private String activity = "deploy";
        private String navigationResult = "not-requested";
        private Location destination;
        private long navigatedAt;
        private long signatureAt;

        private Soldier(int number, Faction faction) {
            this.number = number;
            this.faction = faction;
            this.role = switch (number % 4) {
                case 2 -> CombatClass.HEAVY;
                case 3 -> CombatClass.MEDIC;
                default -> CombatClass.SOLDIER;
            };
        }
    }

    private final SternenfrontGame game;
    private final BotConfig config;
    private final List<Soldier> soldiers = new ArrayList<>();
    private final Map<UUID, Soldier> bodies = new HashMap<>();
    private final Map<UUID, Faction> identities = new HashMap<>();
    private double sightShare = 1;
    private int nextNumber;
    private BotConfig.Tuning tuning;

    public Bots(SternenfrontGame game, BotConfig config) {
        this.game = game;
        this.config = config;
        tuning = config.profiles().get(config.difficulty());
        Bukkit.getPluginManager().registerEvents(this, game.plugin());
    }

    public void difficulty(BotDifficulty difficulty) {
        tuning = config.profiles().get(difficulty);
    }

    public Optional<Vector> aim(Entity entity) {
        var soldier = bodies.get(entity.getUniqueId());
        return soldier == null ? Optional.empty() : Optional.of(soldier.aim.clone());
    }

    public Optional<CombatClass> role(Entity entity) {
        return Optional.ofNullable(bodies.get(entity.getUniqueId())).map(soldier -> soldier.role);
    }

    public double heat(Entity entity) {
        var soldier = bodies.get(entity.getUniqueId());
        return soldier == null ? 0 : soldier.heat.level();
    }

    public Optional<BotSnapshot> snapshot(Entity entity) {
        var soldier = bodies.get(entity.getUniqueId());
        if (soldier == null) {
            return Optional.empty();
        }
        var body = soldier.body;
        var at = body.getLocation();
        var box = body.getBoundingBox();
        var target = soldier.target == null ? null : Bukkit.getEntity(soldier.target);
        var patient = soldier.patient == null ? null : Bukkit.getEntity(soldier.patient);
        var path = body.getPathfinder().getCurrentPath();
        var motion = new BotSnapshot.Motion(BotSnapshot.Point.of(at), BotSnapshot.Point.of(body.getVelocity()),
                List.of(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ()),
                at.getYaw(), at.getPitch(), body.getBodyYaw(), body.isOnGround(), body.isJumping(), body.isInWater(),
                body.getFallDistance(), body.getHealth());
        var decision = new BotSnapshot.Decision(soldier.activity, soldier.navigationResult, soldier.navigatedAt,
                BotSnapshot.Point.of(soldier.destination), soldier.objective,
                soldier.objective < 0 ? "" : game.map().posts().get(soldier.objective).key(), soldier.waypoint,
                soldier.stuckTicks, BotSnapshot.Point.of(soldier.progress), soldier.target,
                target == null ? null : BotSnapshot.Point.of(target.getLocation()),
                target != null && target.isValid() && target.getWorld().equals(body.getWorld()) && body.hasLineOfSight(target),
                BotSnapshot.Point.of(soldier.aim), BotSnapshot.Point.of(soldier.lastSeen), soldier.chooseAt,
                soldier.reactAt, soldier.seenUntil, soldier.pauseUntil, soldier.volley.active(),
                BotSnapshot.Point.of(soldier.idleAim), soldier.patient,
                patient == null ? null : BotSnapshot.Point.of(patient.getLocation()),
                patient instanceof LivingEntity living ? living.getHealth() : -1);
        return Optional.of(new BotSnapshot(body.getUniqueId(), soldier.number, soldier.faction, soldier.role, motion,
                decision, new BotSnapshot.Path(body.getPathfinder().hasPath(), path != null && path.canReachFinalPoint(),
                path == null ? -1 : path.getNextPointIndex(), path == null ? List.of()
                : path.getPoints().stream().map(BotSnapshot.Point::of).toList()), tuning,
                soldier.heat.level(), soldier.heat.venting(game.tick())));
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
            if (soldier.idleAim != null) {
                soldier.aim = BotAim.turn(soldier.aim, soldier.idleAim, config.idleTurnDegrees());
            }
            fireVolley(soldier, tick);
            soldier.body.lookAt(soldier.body.getEyeLocation().add(soldier.aim.clone().multiply(10)));
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
                    Placeholder.component("role", game.messages().get(soldier.role.messageKey(soldier.faction) + ".name")),
                    Formatter.number("number", soldier.number)));
            skeleton.setCustomNameVisible(true);
            var stats = game.config().stats(soldier.faction, soldier.role);
            skeleton.getAttribute(Attribute.MAX_HEALTH).setBaseValue(stats.health());
            skeleton.setHealth(stats.health());
            skeleton.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(128);
            skeleton.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25);
            Loadouts.fit(skeleton, stats.speed(), Math.min(stats.scale(), TALLEST_BODY_SCALE));
            skeleton.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(config.stepHeight());
            var equipment = skeleton.getEquipment();
            equipment.setItemInMainHand(game.loadouts().blaster(soldier.role.blaster(soldier.faction)));
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
        soldier.aim = at.getDirection();
        soldier.burst = 0;
        soldier.pauseUntil = 0;
        soldier.healAt = tick;
        soldier.volley.clear();
        soldier.lastSeen = null;
        soldier.seenUntil = 0;
        soldier.waypoint = 0;
        soldier.idleAim = null;
        soldier.moving = false;
        soldier.patient = null;
        soldier.activity = "deploy";
        soldier.navigationResult = "not-requested";
        soldier.destination = null;
        soldier.navigatedAt = tick;
        soldier.signatureAt = tick;
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
        double sight = config.range() * sightShare;
        var visible = game.combatants().stream()
                .filter(other -> game.hittable(soldier.faction, other))
                .filter(other -> here.getWorld().equals(other.getWorld()))
                .filter(other -> here.distanceSquared(other.getLocation()) <= sight * sight)
                .filter(other -> other.getUniqueId().equals(soldier.target) && tick <= soldier.seenUntil
                        || BotSight.sees(soldier.aim, other.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector()), config.visionAngle()))
                .filter(mob::hasLineOfSight).toList();
        var chosen = BotTargeting.choose(visible.stream().map(other -> new BotTargeting.Target(other.getUniqueId(),
                here.distanceSquared(other.getLocation()))).toList(), soldier.target);
        var target = visible.stream().filter(other -> chosen.filter(other.getUniqueId()::equals).isPresent()).findFirst();
        soldier.idleAim = null;
        if (target.isPresent()) {
            var enemy = target.get();
            if (!enemy.getUniqueId().equals(soldier.target)) {
                soldier.volley.clear();
                soldier.target = enemy.getUniqueId();
                soldier.reactAt = tick + tuning.reactionTicks() + ThreadLocalRandom.current().nextInt(tuning.reactionTicks() + 1);
            }
            soldier.lastSeen = enemy.getLocation();
            soldier.seenUntil = tick + config.memoryTicks();
            var wanted = enemy.getLocation().add(0, enemy.getHeight() * 0.6, 0).toVector().subtract(mob.getEyeLocation().toVector());
            if (wanted.lengthSquared() > 0.01) {
                soldier.aim = BotAim.turn(soldier.aim, wanted, tuning.trackingDegrees());
            }
            if (tick >= soldier.reactAt) {
                fire(soldier, tick);
            }
        } else {
            soldier.volley.clear();
            if (soldier.lastSeen != null && tick <= soldier.seenUntil) {
                var toward = soldier.lastSeen.clone().add(0, 1, 0).toVector().subtract(mob.getEyeLocation().toVector());
                if (toward.lengthSquared() > 0.01) {
                    soldier.idleAim = toward;
                }
            } else {
                soldier.target = null;
                soldier.lastSeen = null;
                var movement = mob.getVelocity().setY(0);
                soldier.moving = BotAim.moving(soldier.moving, movement);
                if (soldier.moving) {
                    soldier.idleAim = movement;
                } else if (soldier.objective >= 0) {
                    var ahead = game.map().posts().get(soldier.objective).center().clone().add(0, 1.3, 0)
                            .toVector().subtract(mob.getEyeLocation().toVector());
                    if (ahead.lengthSquared() > 0.01) {
                        soldier.idleAim = ahead;
                    }
                }
            }
        }
        if (soldier.role == CombatClass.MEDIC) {
            support(soldier, tick);
        }
        if (soldier.role == CombatClass.HEAVY && tick >= soldier.signatureAt) {
            signature(soldier, target, tick);
        }
        if ((tick - soldier.number % THINK_TICKS) % ROUTE_TICKS == 0) {
            navigate(soldier, target, tick);
        }
    }

    private void signature(Soldier soldier, Optional<LivingEntity> target, long tick) {
        var ability = soldier.role.abilities(soldier.faction).getLast();
        boolean used = switch (ability) {
            case SIEGE_MODE -> siege(soldier, target, tick);
            case MINING_CHARGE -> throwCharge(soldier, target, tick);
            default -> false;
        };
        if (used) {
            soldier.signatureAt = tick + game.config().cooldownTicks(ability);
        }
    }

    private boolean siege(Soldier soldier, Optional<LivingEntity> target, long tick) {
        var mob = soldier.body;
        var here = mob.getLocation();
        double range = game.config().stats(soldier.role.blaster(soldier.faction)).range();
        boolean inRange = target.filter(enemy -> tick >= soldier.reactAt)
                .filter(enemy -> here.distanceSquared(enemy.getLocation()) <= range * range).isPresent();
        if (!HeavyTactics.sieges(holdingPost(soldier, here), inRange, soldier.heat.venting(tick), wounded(mob))
                || !game.gadgets().siege(mob, soldier.faction, tick)) {
            return false;
        }
        mob.getPathfinder().stopPathfinding();
        return true;
    }

    private boolean holdingPost(Soldier soldier, Location here) {
        if (soldier.objective < 0) {
            return false;
        }
        var post = game.map().posts().get(soldier.objective);
        var routes = post.routes().get(soldier.faction);
        return post.contains(here) || game.posts().owner(soldier.objective).filter(soldier.faction::equals).isPresent()
                && here.distanceSquared(routes.get(Math.floorMod(soldier.number, routes.size())).peek()) <= PEEK_REACH * PEEK_REACH;
    }

    private boolean throwCharge(Soldier soldier, Optional<LivingEntity> target, long tick) {
        var mob = soldier.body;
        var aimedAt = target.filter(enemy -> tick >= soldier.reactAt).map(LivingEntity::getLocation)
                .or(() -> target.isEmpty() && tick <= soldier.seenUntil ? Optional.ofNullable(soldier.lastSeen) : Optional.empty());
        if (aimedAt.isEmpty()) {
            return false;
        }
        var at = aimedAt.get();
        var charge = game.config().miningCharge();
        boolean inPost = game.map().posts().stream().anyMatch(post -> post.contains(at));
        if (!HeavyTactics.throwsCharge(inPost, mob.getLocation().distance(at), charge.radius())) {
            return false;
        }
        soldier.protectedUntil = 0;
        mob.swingMainHand();
        return game.gadgets().throwCharge(mob, soldier.faction,
                HeavyTactics.lob(at.toVector().subtract(mob.getEyeLocation().toVector()), charge.throwSpeed()), tuning.damageScale(), tick);
    }

    private static boolean wounded(Mob mob) {
        return mob.getHealth() < mob.getAttribute(Attribute.MAX_HEALTH).getValue() * WOUNDED_SHARE;
    }

    private void choose(Soldier soldier, long tick) {
        var here = soldier.body.getLocation();
        if (soldier.objective >= 0
                && game.posts().owner(soldier.objective).filter(soldier.faction::equals).isEmpty()) {
            soldier.chooseAt = tick + OBJECTIVE_TICKS;
            return;
        }
        var choices = game.posts().indices().stream().map(index -> new Objectives.Post(index,
                game.posts().owner(index).filter(soldier.faction::equals).isPresent(), game.posts().contested(index),
                here.distance(game.map().posts().get(index).center()) - (soldier.role == CombatClass.HEAVY
                        && game.posts().owner(index).filter(soldier.faction::equals).isPresent() ? 100 : 0),
                (int) soldiers.stream().filter(other -> other != soldier && other.body != null
                        && other.faction == soldier.faction && other.objective == index).count(),
                game.posts().priority(index) ? game.config().acts().priorityPull() : 0)).toList();
        int next = Objectives.choose(choices).orElse(-1);
        if (next != soldier.objective) {
            soldier.waypoint = 0;
            if (next >= 0) {
                var post = game.map().posts().get(next);
                var routes = post.routes().get(soldier.faction);
                var route = routes.get(Math.floorMod(soldier.number, routes.size()));
                soldier.waypoint = BotNavigation.enter(here.toVector(), post.center().toVector(),
                        route.approach().stream().map(Location::toVector).toList());
            }
        }
        soldier.objective = next;
        soldier.chooseAt = tick + OBJECTIVE_TICKS + soldier.number % 20;
    }

    private void navigate(Soldier soldier, Optional<LivingEntity> target, long tick) {
        var mob = soldier.body;
        var here = mob.getLocation();
        var post = soldier.objective < 0 ? null : game.map().posts().get(soldier.objective);
        boolean capturing = post != null && post.contains(here);
        var route = post == null ? null : post.routes().get(soldier.faction)
                .get(Math.floorMod(soldier.number, post.routes().get(soldier.faction).size()));
        boolean defending = route != null && soldier.role == CombatClass.HEAVY
                && game.posts().owner(soldier.objective).filter(soldier.faction::equals).isPresent();
        Location destination = null;
        String activity = "idle";
        boolean seekingPatient = false;
        var patient = soldier.role == CombatClass.MEDIC ? patient(soldier) : Optional.<LivingEntity>empty();
        boolean wounded = wounded(mob);
        if (game.gadgets().sieging(mob)) {
            activity = "siege";
            destination = here;
        } else if (defending && !game.posts().safe(soldier.objective, soldier.faction)) {
            activity = "contest-owned-post";
            destination = capturing ? here : route.capture();
        } else if (route != null && (soldier.heat.venting(tick) || wounded)
                && (target.isPresent() || soldier.lastSeen != null && tick <= soldier.seenUntil)) {
            activity = wounded ? "retreat-wounded" : "cover-venting";
            destination = wounded ? route.retreat() : route.cover();
        } else if (patient.isPresent()) {
            seekingPatient = here.distanceSquared(patient.get().getLocation()) > 9 || !mob.hasLineOfSight(patient.get());
            activity = seekingPatient ? "follow-patient" : "treat-patient";
            destination = seekingPatient ? patient.get().getLocation() : here;
        } else if (defending) {
            activity = "defend-peek";
            destination = route.peek();
        } else if (target.isPresent() && here.distanceSquared(target.get().getLocation()) < KEEP_DISTANCE * KEEP_DISTANCE
                && (!capturing || soldier.heat.venting(tick))) {
            activity = "keep-distance";
            destination = route == null ? null : route.cover();
        } else if (target.isEmpty() && soldier.lastSeen != null && tick <= soldier.seenUntil
                && post != null && soldier.lastSeen.distanceSquared(post.center()) < 28 * 28) {
            activity = "investigate-last-seen";
            destination = soldier.lastSeen;
        } else if (route != null && !capturing && soldier.waypoint < route.approach().size()) {
            activity = "approach";
            while (soldier.waypoint < route.approach().size()
                    && here.distanceSquared(route.approach().get(soldier.waypoint)) < 9) {
                soldier.waypoint++;
            }
            if (soldier.waypoint < route.approach().size()) {
                destination = route.approach().get(soldier.waypoint);
            } else {
                destination = route.capture();
            }
        } else if (post != null && !capturing) {
            activity = "capture";
            destination = route.capture();
        } else if (capturing && target.isPresent()) {
            activity = "strafe-in-post";
            double angle = tick * 0.025 + soldier.number;
            destination = post.center().clone().add(Math.cos(angle) * post.radius() * 0.45, 0, Math.sin(angle) * post.radius() * 0.45);
        }
        soldier.activity = activity;
        soldier.destination = destination == null ? null : destination.clone();
        soldier.navigatedAt = tick;
        if (destination == null || !game.map().playArea().contains(destination.toVector())) {
            soldier.navigationResult = "no-destination";
            mob.getPathfinder().stopPathfinding();
            soldier.stuckTicks = 0;
            return;
        }
        if (here.distanceSquared(destination) <= 2.25 && !seekingPatient) {
            soldier.navigationResult = "arrived";
            mob.getPathfinder().stopPathfinding();
            soldier.stuckTicks = 0;
            soldier.progress = here.clone();
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
            soldier.waypoint = 0;
        }
        soldier.navigationResult = mob.getPathfinder().moveTo(destination, 1.0) ? "accepted" : "unreachable";
    }

    private void fire(Soldier soldier, long tick) {
        if (tick < soldier.pauseUntil || soldier.volley.active()) {
            return;
        }
        var blaster = soldier.role.blaster(soldier.faction);
        var stats = game.firing(soldier.body, blaster);
        int pace = tuning.shotTicks() * stats.cooldownTicks() / game.config().stats(blaster).cooldownTicks();
        var fired = soldier.heat.fire(tick, Math.max(pace, stats.cooldownTicks()), stats.heat());
        if (fired != Heat.Shot.FIRED && fired != Heat.Shot.OVERHEATED) {
            return;
        }
        soldier.protectedUntil = 0;
        soldier.volley.start(tick, stats.bolts(), stats.burstTicks());
        if (tuning.burstShots() > 0 && ++soldier.burst >= tuning.burstShots()) {
            soldier.burst = 0;
            soldier.pauseUntil = tick + tuning.burstPauseTicks();
        }
    }

    private void fireVolley(Soldier soldier, long tick) {
        if (!soldier.volley.active()) {
            return;
        }
        var target = soldier.target == null ? null : Bukkit.getEntity(soldier.target);
        if (!(target instanceof LivingEntity enemy) || !game.hittable(soldier.faction, enemy)
                || !soldier.body.hasLineOfSight(enemy)) {
            soldier.volley.clear();
            return;
        }
        while (soldier.volley.next(tick)) {
            var blaster = soldier.role.blaster(soldier.faction);
            var aim = soldier.aim.clone();
            var random = ThreadLocalRandom.current();
            double spread = Math.max(tuning.spread(), game.config().stats(blaster).spread())
                    * (soldier.body.getVelocity().lengthSquared() > 0.005 ? 1.5 : 1);
            if (spread > 0) {
                aim.add(new Vector(random.nextDouble(-spread, spread), random.nextDouble(-spread, spread),
                        random.nextDouble(-spread, spread))).normalize();
            }
            game.fireBot(soldier.body, soldier.faction, blaster, aim, tuning.damageScale(), tuning.headshots());
            soldier.body.swingMainHand();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        var soldier = bodies.get(event.getEntity().getUniqueId());
        if (soldier != null && event.getFinalDamage() > 0
                && event.getDamageSource().getCausingEntity() instanceof LivingEntity attacker
                && game.hittable(soldier.faction, attacker) && attacker.getWorld().equals(soldier.body.getWorld())) {
            if (!attacker.getUniqueId().equals(soldier.target)) {
                // Each switch restarts the reaction time, so two attackers could keep a bot from ever firing.
                if (soldier.target != null && game.tick() <= soldier.seenUntil) {
                    return;
                }
                soldier.volley.clear();
                soldier.target = attacker.getUniqueId();
                soldier.reactAt = game.tick() + tuning.reactionTicks();
            }
            soldier.lastSeen = attacker.getLocation();
            soldier.seenUntil = game.tick() + config.memoryTicks();
        }
    }

    private Optional<LivingEntity> patient(Soldier soldier) {
        var here = soldier.body.getLocation();
        var candidates = game.combatants().stream().filter(ally -> !ally.equals(soldier.body)
                        && game.factionOf(ally).filter(soldier.faction::equals).isPresent())
                .filter(ally -> !(ally instanceof Player player) || game.heroOf(player).isEmpty())
                .filter(ally -> ally.getHealth() < ally.getAttribute(Attribute.MAX_HEALTH).getValue())
                .filter(ally -> here.distanceSquared(ally.getLocation()) <= 18 * 18).toList();
        var selected = candidates.stream().filter(ally -> ally.getUniqueId().equals(soldier.patient)).findFirst()
                .or(() -> candidates.stream().min(Comparator.comparingDouble(ally ->
                        ally.getHealth() / ally.getAttribute(Attribute.MAX_HEALTH).getValue())));
        soldier.patient = selected.map(Entity::getUniqueId).orElse(null);
        return selected;
    }

    private void support(Soldier soldier, long tick) {
        if (tick < soldier.healAt) {
            return;
        }
        patient(soldier).filter(ally -> ally.getLocation().distanceSquared(soldier.body.getLocation()) <= 16)
                .filter(soldier.body::hasLineOfSight).ifPresent(ally -> {
                    game.heal(ally, config.healAmount());
                    ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 1.8, 0), 2, 0.2, 0.1, 0.2, 0);
                    soldier.healAt = tick + config.healTicks();
                });
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
        if (event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Player player && Melee.swing(hit)) {
            var held = player.getInventory().getItemInMainHand();
            if (!game.onField(player) || player.isInsideVehicle() || Sabers.isSaber(held) && !Sabers.isLit(held)) {
                event.setCancelled(true);
            }
            if (game.heroOf(player).filter(Hero::bladed).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMelee(EntityDamageByEntityEvent event) {
        var soldier = bodies.get(event.getEntity().getUniqueId());
        if (soldier != null && event.getDamager() instanceof Player player && Melee.swing(event) && game.onField(player)) {
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
        var killer = event.getDamageSource().getCausingEntity() instanceof Player player
                ? Optional.of(player) : Optional.<Player>empty();
        game.botDied(event.getEntity(), soldier.faction, killer, config.killPoints());
    }

    private void remove(Soldier soldier) {
        if (soldier.body != null) {
            game.support().removed(soldier.body);
            bodies.remove(soldier.body.getUniqueId());
            soldier.body.remove();
            soldier.body = null;
        }
    }

    /** Every bot weighs its objective afresh, even one still on its way to a post it wanted before. */
    public void reconsider() {
        soldiers.forEach(soldier -> soldier.objective = -1);
    }

    public void sightShare(double share) {
        sightShare = share;
    }

    public void clear() {
        sightShare = 1;
        soldiers.forEach(this::remove);
        soldiers.clear();
        bodies.clear();
        identities.clear();
    }
}
