package me.blockcraft.sternenfront.bot;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import me.blockcraft.api.game.ShotEvent;
import me.blockcraft.api.replay.RecordedModel;
import me.blockcraft.sternenfront.appearance.BodyRigs;
import me.blockcraft.sternenfront.arena.FrontMap;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.sternenfront.combat.Melee;
import me.blockcraft.sternenfront.config.SternenfrontConfig;
import me.blockcraft.sternenfront.game.Ability;
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
import org.bukkit.SoundCategory;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class Bots implements Listener {

    private static final int THINK_TICKS = 4;
    private static final int ROUTE_TICKS = 20;
    private static final int AVOID_TICKS = 600;
    private static final int REGROUP_TICKS = 100;
    private static final double REGROUP_PACE = 0.5;
    private static final double RETREAT_SLACK = 2;
    private static final int SOLO_TICKS = 200;
    private static final int TRAIL_STEPS = 24;
    private static final double TRAIL_STEP = 1.5;
    private static final double ASSAULT_REACH = 4;
    private static final double SUPPRESSING_SPREAD = 2.5;
    private static final double[] SECTORS = {0, 50, -50, 150};
    private static final int GUARD_SHIFT_TICKS = 300;
    private static final double PEEK_REACH = 3;
    private static final double WOUNDED_SHARE = 0.3;
    private static final Set<String> HOLDING_GROUND = Set.of("overwatch", "assault", "strafe-in-post", "defend-peek",
            "contest-owned-post", "hold-for-team");
    /**
     * A skeleton already stands 1.99 blocks. Any taller and the pathfinder plans three-high paths, so a larger class
     * stays at that height: about what a player of that class measures, and it still fits the wreck's corridors.
     */
    private static final double TALLEST_BODY_SCALE = 1;
    private static final List<CombatClass> ROTATION = List.of(CombatClass.SOLDIER, CombatClass.HEAVY, CombatClass.MEDIC,
            CombatClass.SCOUT);

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
        private Fireteam team;
        private int guarding = -1;
        private long soloUntil;
        private int bound = -1;
        private boolean boundDone;
        private Location boundFrom;
        private Location suppress;
        private long acquiredAt;
        private double glanceYaw;
        private long glanceAt;
        private long shiftAt;
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
        private final BotNavigation navigation = new BotNavigation();
        private Vector idleAim;
        private boolean moving;
        private final BotPatients patients = new BotPatients();
        private String activity = "deploy";
        private String navigationResult = "not-requested";
        private Location destination;
        private long navigatedAt;
        private final Map<Ability, Long> readyAt = new EnumMap<>(Ability.class);
        private Location cover;
        private Location peek;
        private long coverPhaseUntil;
        private boolean peeking;
        private long voiceAt;
        private Location heard;
        private long heardUntil;
        private final BotRecovery recovery = new BotRecovery();

        private Soldier(int number, Faction faction, CombatClass role) {
            this.number = number;
            this.faction = faction;
            this.role = role;
        }
    }

    /** The class a faction lacks most, so each side fields the same mix whatever the other side's count. */
    static CombatClass infantryClass(List<CombatClass> fielded) {
        return ROTATION.stream().min(Comparator.comparingLong(role -> fielded.stream().filter(role::equals).count())).orElseThrow();
    }

    private final SternenfrontGame game;
    private final BotConfig config;
    private final List<Soldier> soldiers = new ArrayList<>();
    private final Map<UUID, Soldier> bodies = new HashMap<>();
    private final Map<UUID, Faction> identities = new HashMap<>();
    private final List<Fireteam> teams = new ArrayList<>();
    private double sightShare = 1;
    private int nextNumber;
    private int nextTeam;
    private BotConfig.Tuning tuning;
    private long voiceAt;

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
        var patient = soldier.patients.selected() == null ? null : Bukkit.getEntity(soldier.patients.selected());
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
                BotSnapshot.Point.of(soldier.aim), BotSnapshot.Point.of(soldier.lastSeen),
                soldier.team == null ? -1 : soldier.team.id, soldier.guarding,
                soldier.reactAt, soldier.seenUntil, soldier.pauseUntil, soldier.volley.active(),
                BotSnapshot.Point.of(soldier.idleAim), soldier.patients.selected(),
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
            boolean changed = false;
            int needed = config.needed(game.humans(faction));
            var factionSoldiers = soldiers.stream().filter(soldier -> soldier.faction == faction)
                    .sorted(Comparator.comparing((Soldier soldier) -> soldier.body != null).reversed()).toList();
            for (int index = needed; index < factionSoldiers.size(); index++) {
                var soldier = factionSoldiers.get(index);
                remove(soldier);
                soldiers.remove(soldier);
                changed = true;
            }
            for (int index = factionSoldiers.size(); index < needed; index++) {
                var fielded = soldiers.stream().filter(soldier -> soldier.faction == faction).map(soldier -> soldier.role).toList();
                soldiers.add(new Soldier(++nextNumber, faction, infantryClass(fielded)));
                changed = true;
            }
            if (changed) {
                regroup(faction);
            }
        }
    }

    /** Only the faction whose bots changed starts its fireteams afresh; the other keeps its leaders and objectives. */
    private void regroup(Faction faction) {
        teams.removeIf(team -> team.faction == faction);
        var members = soldiers.stream().filter(soldier -> soldier.faction == faction)
                .map(soldier -> new Fireteams.Member(soldier.number, soldier.role)).toList();
        for (var numbers : Fireteams.assign(members, config.squad().fireteamSize())) {
            var team = new Fireteam(nextTeam++, faction, numbers);
            teams.add(team);
            members(team).forEach(soldier -> soldier.team = team);
        }
    }

    private List<Soldier> members(Fireteam team) {
        return team.members.stream().flatMap(number -> soldiers.stream().filter(soldier -> soldier.number == number)).toList();
    }

    private static boolean deployed(Soldier soldier) {
        return soldier.body != null && soldier.body.isValid() && !soldier.body.isDead();
    }

    public void tick(long tick) {
        if (!enabled()) {
            return;
        }
        if (tick % 20 == 1) {
            fill();
        }
        if (tick % ROUTE_TICKS == 0) {
            teams.forEach(team -> command(team, tick));
        }
        if (tick % THINK_TICKS == 0) {
            teams.forEach(this::mark);
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
            if (soldier.recovery.expired(game.map().outsideBattlefield(soldier.body.getLocation()), tick,
                    config.boundaryGraceTicks())) {
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
            RecordedModel.mark(skeleton, BodyRigs.family(soldier.faction, soldier.role));
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
        soldier.reactAt = 0;
        soldier.objective = -1;
        soldier.guarding = -1;
        soldier.soloUntil = 0;
        soldier.bound = -1;
        soldier.boundDone = false;
        soldier.boundFrom = null;
        soldier.suppress = null;
        soldier.acquiredAt = tick;
        soldier.glanceAt = tick;
        soldier.shiftAt = tick + GUARD_SHIFT_TICKS;
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
        soldier.patients.clear();
        soldier.activity = "deploy";
        soldier.navigationResult = "not-requested";
        soldier.destination = null;
        soldier.navigatedAt = tick;
        soldier.readyAt.clear();
        soldier.cover = null;
        soldier.peek = null;
        soldier.coverPhaseUntil = 0;
        soldier.peeking = false;
        soldier.voiceAt = tick;
        soldier.heard = null;
        soldier.heardUntil = 0;
        soldier.recovery.clear();
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
                soldier.acquiredAt = tick;
                call(soldier, "contact", tick);
            }
            soldier.lastSeen = enemy.getLocation();
            soldier.seenUntil = tick + config.memoryTicks();
            soldier.suppress = null;
            var wanted = enemy.getLocation().add(0, enemy.getHeight() * 0.6, 0).toVector().subtract(mob.getEyeLocation().toVector());
            if (wanted.lengthSquared() > 0.01) {
                soldier.aim = BotAim.turn(soldier.aim, wanted, tuning.trackingDegrees());
            }
            if (tick >= soldier.reactAt) {
                fire(soldier, tick);
            }
        } else {
            soldier.suppress = suppression(soldier, tick).orElse(null);
            if (soldier.suppress != null) {
                var wanted = soldier.suppress.toVector().subtract(mob.getEyeLocation().toVector());
                if (wanted.lengthSquared() > 0.01) {
                    soldier.aim = BotAim.turn(soldier.aim, wanted, tuning.trackingDegrees());
                }
                fire(soldier, tick);
            } else {
                soldier.volley.clear();
            }
            if (soldier.suppress != null) {
                soldier.idleAim = null;
            } else if (remembering(soldier, tick)) {
                var toward = soldier.lastSeen.clone().add(0, 1, 0).toVector().subtract(mob.getEyeLocation().toVector());
                if (toward.lengthSquared() > 0.01) {
                    soldier.idleAim = toward;
                }
            } else {
                soldier.target = null;
                soldier.lastSeen = null;
                var movement = mob.getVelocity().setY(0);
                soldier.moving = BotAim.moving(soldier.moving, movement);
                var team = soldier.team;
                var alert = team != null && team.threat != null && soldier.guarding < 0 ? team.threat
                        : tick < soldier.heardUntil ? soldier.heard : null;
                if (alert != null) {
                    var toward = alert.clone().add(0, 1, 0).toVector().subtract(mob.getEyeLocation().toVector());
                    if (toward.lengthSquared() > 0.01) {
                        soldier.idleAim = toward;
                    }
                } else {
                    glance(soldier, tick);
                    if (soldier.moving) {
                        soldier.idleAim = movement.rotateAroundY(Math.toRadians(soldier.glanceYaw / 2));
                    } else if (soldier.objective >= 0) {
                        var ahead = game.map().posts().get(soldier.objective).center().clone().add(0, 1.3, 0)
                                .toVector().subtract(mob.getEyeLocation().toVector()).setY(0);
                        if (ahead.lengthSquared() > 0.01) {
                            soldier.idleAim = ahead.rotateAroundY(Math.toRadians(soldier.glanceYaw));
                        }
                    }
                }
            }
        }
        if (soldier.role == CombatClass.MEDIC) {
            support(soldier, tick);
        }
        useAbility(soldier, target, tick);
        if ((tick - soldier.number % THINK_TICKS) % ROUTE_TICKS == 0) {
            navigate(soldier, target, tick);
        }
    }

    /** Each member watches its own side of the team, the last one behind it; a guard looks all round. */
    private static void glance(Soldier soldier, long tick) {
        if (tick < soldier.glanceAt) {
            return;
        }
        var random = ThreadLocalRandom.current();
        int slot = soldier.guarding >= 0 || soldier.team == null ? random.nextInt(SECTORS.length)
                : soldier.team.members.indexOf(soldier.number) % SECTORS.length;
        soldier.glanceYaw = SECTORS[slot] + random.nextDouble(-35, 35);
        soldier.glanceAt = tick + 30 + random.nextInt(50);
    }

    /** Where an overwatching bot fires to keep the enemy's heads down while the other element bounds, if it can. */
    private Optional<Location> suppression(Soldier soldier, long tick) {
        var team = soldier.team;
        if (team == null || team.threat == null || soldier.guarding >= 0 || team.flanker == soldier.number
                || team.bounding.moving() == Bounding.NONE || team.element(soldier.number) == team.bounding.moving()
                || tick < soldier.reactAt) {
            return Optional.empty();
        }
        var at = (remembering(soldier, tick) ? soldier.lastSeen : team.threat).clone().add(0, 1.2, 0);
        return BotCover.suppressible(soldier.body.getEyeLocation(), at) ? Optional.of(at) : Optional.empty();
    }

    /** At most one ability a think, the first in the class's own order whose moment has come. */
    private void useAbility(Soldier soldier, Optional<LivingEntity> target, long tick) {
        var mob = soldier.body;
        for (var ability : soldier.role.abilities(soldier.faction)) {
            if (tick < soldier.readyAt.getOrDefault(ability, 0L)) {
                continue;
            }
            boolean used = switch (ability) {
                case SIEGE_MODE -> siege(soldier, target, tick);
                case MINING_CHARGE -> lob(soldier, target, tick, game.config().miningCharge(),
                        direction -> game.gadgets().throwCharge(mob, soldier.faction, direction, tuning.damageScale(), tick));
                case GRENADE -> lob(soldier, target, tick, game.config().grenade(),
                        direction -> game.gadgets().throwGrenade(mob, soldier.faction, direction, tuning.damageScale(), tick));
                case SHIELD_WALL -> wall(soldier, target, tick);
                default -> false;
            };
            if (used) {
                soldier.readyAt.put(ability, tick + game.config().cooldownTicks(ability));
                return;
            }
        }
    }

    /** A heavy holding open ground under fire puts up a wall between its team and the enemy. */
    private boolean wall(Soldier soldier, Optional<LivingEntity> target, long tick) {
        var mob = soldier.body;
        var threat = target.map(LivingEntity::getLocation).or(() -> remembering(soldier, tick) ? Optional.of(soldier.lastSeen)
                : Optional.ofNullable(soldier.team == null ? null : soldier.team.threat));
        if (threat.isEmpty() || !threat.get().getWorld().equals(mob.getWorld())) {
            return false;
        }
        boolean holdingGround = HOLDING_GROUND.contains(soldier.activity);
        var toward = threat.get().toVector().subtract(mob.getLocation().toVector()).setY(0);
        if (!BotTactics.raisesWall(holdingGround, soldier.cover != null, toward.length())
                || !game.gadgets().raiseWall(mob, soldier.faction, toward, tick)) {
            return false;
        }
        soldier.protectedUntil = 0;
        mob.swingMainHand();
        call(soldier, "cover", tick);
        return true;
    }

    private boolean siege(Soldier soldier, Optional<LivingEntity> target, long tick) {
        var mob = soldier.body;
        var here = mob.getLocation();
        double range = game.config().stats(soldier.role.blaster(soldier.faction)).range();
        boolean inRange = target.filter(enemy -> tick >= soldier.reactAt)
                .filter(enemy -> here.distanceSquared(enemy.getLocation()) <= range * range).isPresent();
        if (!BotTactics.sieges(holdingPost(soldier, here), inRange, soldier.heat.venting(tick), wounded(mob))
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
                && here.distanceSquared(routes.get(soldier.navigation.route()).peek()) <= PEEK_REACH * PEEK_REACH;
    }

    /** Lobs an explosive at an enemy dug in where blaster fire cannot reach it, as {@code toss} throws it. */
    private boolean lob(Soldier soldier, Optional<LivingEntity> target, long tick, SternenfrontConfig.Grenade stats,
                        Predicate<Vector> toss) {
        var mob = soldier.body;
        var aimedAt = target.filter(enemy -> tick >= soldier.reactAt).map(LivingEntity::getLocation)
                .or(() -> target.isEmpty() && tick <= soldier.seenUntil ? Optional.ofNullable(soldier.lastSeen) : Optional.empty());
        if (aimedAt.isEmpty()) {
            return false;
        }
        var at = aimedAt.get();
        boolean dugIn = target.isEmpty() || game.map().posts().stream().anyMatch(post -> post.contains(at));
        if (!BotTactics.throwsAt(dugIn, mob.getLocation().distance(at), stats.radius())) {
            return false;
        }
        soldier.protectedUntil = 0;
        mob.swingMainHand();
        return toss.test(BotTactics.lob(at.toVector().subtract(mob.getEyeLocation().toVector()), stats.throwSpeed()));
    }

    private static boolean wounded(Mob mob) {
        return mob.getHealth() < mob.getAttribute(Attribute.MAX_HEALTH).getValue() * WOUNDED_SHARE;
    }

    private void command(Fireteam team, long tick) {
        var everyone = members(team);
        for (var soldier : everyone) {
            if (soldier.guarding >= 0 && game.posts().owner(soldier.guarding).filter(team.faction::equals).isEmpty()) {
                soldier.guarding = -1;
            }
        }
        var squad = everyone.stream().filter(Bots::deployed).filter(soldier -> soldier.guarding < 0).toList();
        if (squad.isEmpty()) {
            team.leader = -1;
            team.trail.clear();
            return;
        }
        var leader = lead(team, squad);
        var at = leader.body.getLocation();
        int previous = team.objective;
        int next = followed(team, at, tick).orElseGet(() -> objectiveFor(team, at, tick));
        if (next != previous) {
            if (previous >= 0) {
                call(leader, "advance", tick);
            }
            if (previous >= 0 && game.posts().owner(previous).filter(team.faction::equals).isPresent()
                    && everyone.stream().noneMatch(soldier -> soldier.guarding >= 0 && deployed(soldier))
                    && soldiers.stream().noneMatch(soldier -> soldier.faction == team.faction && soldier.guarding == previous && deployed(soldier))) {
                Fireteams.guard(squad.stream().map(soldier -> new Fireteams.Member(soldier.number, soldier.role)).toList(), leader.number)
                        .ifPresent(number -> squad.stream().filter(soldier -> soldier.number == number)
                                .forEach(soldier -> soldier.guarding = previous));
            }
            team.objective = next;
            team.route = ThreadLocalRandom.current().nextInt(64);
        }
        for (var soldier : squad) {
            objective(soldier, soldier.guarding < 0 ? team.objective : soldier.guarding, team.route);
        }
        var post = team.objective < 0 ? null : game.map().posts().get(team.objective);
        team.assaulting = post != null && Fireteam.left(at.toVector(), post.center().toVector()) <= post.radius() + ASSAULT_REACH;
        var settings = config.squad();
        if (squad.stream().noneMatch(soldier -> soldier.number == team.flanker)) {
            team.flanker = -1;
        }
        contact(team, squad.stream().filter(soldier -> soldier.body.getLocation().distance(at) < settings.catchUpDistance()).toList(), tick);
        if (team.threat != null && odds(team.faction, at).outnumbered()) {
            if (tick >= team.fallBackUntil) {
                call(leader, "cover", tick);
            }
            team.fallBackUntil = tick + settings.fallBackTicks();
        }
        boolean behind = !team.assaulting && team.threat == null && post != null && squad.stream().filter(soldier -> soldier != leader)
                .anyMatch(soldier -> place(leader, soldier, post) == Fireteam.Place.STRAGGLING);
        if (!behind) {
            team.waitingSince = -1;
        } else if (team.waitingSince < 0) {
            team.waitingSince = tick;
        }
        team.regrouping = behind && tick - team.waitingSince < REGROUP_TICKS;
    }

    private Fireteams.Odds odds(Faction faction, Location around) {
        double range = config.squad().fallBackRange();
        var near = game.combatants().stream().filter(other -> other.getWorld().equals(around.getWorld())
                && other.getLocation().distanceSquared(around) <= range * range).toList();
        return new Fireteams.Odds((int) near.stream().filter(other -> game.factionOf(other).filter(faction::equals).isPresent()).count(),
                (int) near.stream().filter(other -> game.hittable(faction, other)).count());
    }

    /** The post's nearest retreat point that brings the bot no nearer the enemy, so it never runs into them. */
    private Optional<Location> retreat(Soldier soldier, Location here, FrontMap.PostSite post, Location threat) {
        double exposure = here.distance(threat) - RETREAT_SLACK;
        return post.routes().get(soldier.faction).stream().map(FrontMap.Route::retreat)
                .filter(point -> point.distance(threat) >= exposure)
                .min(Comparator.comparingDouble(here::distanceSquared));
    }

    private Fireteam.Place place(Soldier leader, Soldier member, FrontMap.PostSite post) {
        var settings = config.squad();
        return Fireteam.place(leader.body.getLocation().toVector(), member.body.getLocation().toVector(), post.center().toVector(),
                settings.spacing(), settings.regroupDistance(), settings.catchUpDistance());
    }

    /** {@code squad} holds only the members near the leader; anyone further is still on the way and fights alone. */
    private void contact(Fireteam team, List<Soldier> squad, long tick) {
        team.threat = squad.stream().filter(soldier -> remembering(soldier, tick))
                .max(Comparator.comparingLong(soldier -> soldier.seenUntil)).map(soldier -> soldier.lastSeen.clone())
                .orElse(tick < team.alarmUntil ? team.alarm : null);
        if (team.threat == null) {
            team.contactSince = -1;
        } else if (team.contactSince < 0) {
            team.contactSince = tick;
        }
        if (team.threat == null || team.assaulting || team.objective < 0) {
            team.bounding.reset();
            team.flanker = -1;
            return;
        }
        var settings = config.squad();
        var routes = game.map().posts().get(team.objective).routes().get(team.faction).size();
        if (team.flanker < 0 && squad.size() >= 3 && routes > 1 && tick - team.contactSince >= settings.flankAfterTicks()) {
            squad.stream().filter(soldier -> soldier.number != team.leader).filter(soldier -> soldier.role == CombatClass.SCOUT).findFirst()
                    .or(() -> Optional.of(squad.getLast())).filter(soldier -> soldier.number != team.leader).ifPresent(flanker -> {
                        team.flanker = flanker.number;
                        flanker.objective = -1;
                        objective(flanker, team.objective, team.route + 1);
                        call(flanker, "advance", tick);
                    });
        }
        var bounders = squad.stream().filter(soldier -> soldier.number != team.flanker).toList();
        var present = new boolean[2];
        bounders.forEach(soldier -> present[team.element(soldier.number)] = true);
        int moving = team.bounding.moving();
        boolean arrived = bounders.stream().filter(soldier -> team.element(soldier.number) == moving)
                .allMatch(soldier -> soldier.bound == team.bounding.bound() && soldier.boundDone);
        team.bounding.update(present, arrived, tick, settings.boundTicks());
    }

    /** The team keeps its leader while it lives; a new one is whoever is nearest the objective, so nobody turns back. */
    private Soldier lead(Fireteam team, List<Soldier> squad) {
        var kept = squad.stream().filter(soldier -> soldier.number == team.leader).findFirst();
        if (kept.isPresent()) {
            return kept.get();
        }
        var leader = team.objective < 0 ? squad.getFirst() : squad.stream().min(Comparator.comparingDouble((Soldier soldier) ->
                soldier.body.getLocation().distanceSquared(game.map().posts().get(team.objective).center()))).orElseThrow();
        team.leader = leader.number;
        team.trail.clear();
        return leader;
    }

    private void mark(Fireteam team) {
        leader(team).ifPresent(leader -> {
            var at = leader.body.getLocation();
            var last = team.trail.peekFirst();
            if (last == null || !last.getWorld().equals(at.getWorld()) || last.distance(at) >= TRAIL_STEP) {
                team.trail.addFirst(at);
                while (team.trail.size() > TRAIL_STEPS) {
                    team.trail.removeLast();
                }
            }
        });
    }

    private Optional<Soldier> leader(Fireteam team) {
        return members(team).stream().filter(soldier -> soldier.number == team.leader).filter(Bots::deployed)
                .filter(soldier -> soldier.guarding < 0).findFirst();
    }

    /** The order of the nearest player of the team's faction within reach of its leader, which the team takes as its own. */
    private Optional<Integer> followed(Fireteam team, Location leader, long tick) {
        double range = config.squad().followRange();
        return game.combatants().stream().filter(Player.class::isInstance).map(Player.class::cast).filter(game::onField)
                .filter(player -> game.factionOf(player).filter(team.faction::equals).isPresent())
                .filter(player -> player.getWorld().equals(leader.getWorld()) && player.getLocation().distanceSquared(leader) <= range * range)
                .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(leader)))
                .map(game.deployment()::order).filter(OptionalInt::isPresent).map(OptionalInt::getAsInt)
                .filter(post -> !team.avoiding(post, tick));
    }

    private int objectiveFor(Fireteam team, Location from, long tick) {
        var enemy = game.map().headquarters().get(team.faction.enemy()).zone().getCenter();
        var choices = game.posts().indices().stream().filter(index -> !team.avoiding(index, tick)).map(index -> {
            var center = game.map().posts().get(index).center();
            return new Objectives.Post(index, hold(index, team.faction), from.distance(center),
                    Math.hypot(center.getX() - enemy.getX(), center.getZ() - enemy.getZ()),
                    (int) teams.stream().filter(other -> other != team && other.faction == team.faction && other.objective == index).count(),
                    game.posts().priority(index) ? game.config().acts().priorityPull() : 0);
        }).toList();
        return Objectives.choose(choices, team.objective, config.squad().switchMargin(), config.squad().reinforcePull()).orElse(-1);
    }

    private Objectives.Hold hold(int post, Faction faction) {
        if (game.posts().owner(post).filter(faction::equals).isEmpty()) {
            return Objectives.Hold.OPEN;
        }
        return game.posts().safe(post, faction) ? Objectives.Hold.HELD : Objectives.Hold.THREATENED;
    }

    private void objective(Soldier soldier, int next, int route) {
        if (next != soldier.objective) {
            soldier.waypoint = 0;
            if (next >= 0) {
                var post = game.map().posts().get(next);
                var routes = post.routes().get(soldier.faction);
                soldier.navigation.start(route, routes.size());
                soldier.waypoint = enterRoute(soldier, post, routes.get(soldier.navigation.route()));
            }
        }
        soldier.objective = next;
    }

    private int enterRoute(Soldier soldier, FrontMap.PostSite post, FrontMap.Route route) {
        var mob = soldier.body;
        mob.getPathfinder().stopPathfinding();
        return BotNavigation.enter(mob.getLocation().toVector(), post.center().toVector(),
                route.approach().stream().map(Location::toVector).toList(), point -> {
                    var path = mob.getPathfinder().findPath(point.toLocation(mob.getWorld()));
                    return path != null && path.canReachFinalPoint();
                });
    }

    private void navigate(Soldier soldier, Optional<LivingEntity> target, long tick) {
        var mob = soldier.body;
        var here = mob.getLocation();
        var post = soldier.objective < 0 ? null : game.map().posts().get(soldier.objective);
        boolean capturing = post != null && post.contains(here);
        var route = post == null ? null : post.routes().get(soldier.faction)
                .get(soldier.navigation.route());
        boolean defending = route != null && soldier.guarding >= 0;
        var team = defending ? null : soldier.team;
        var leader = team == null ? Optional.<Soldier>empty() : leader(team);
        var settings = config.squad();
        var place = leader.filter(other -> other != soldier && post != null).map(other -> place(other, soldier, post))
                .orElse(Fireteam.Place.IN_COLUMN);
        boolean withTeam = place != Fireteam.Place.AWAY;
        boolean following = withTeam && leader.filter(other -> other != soldier).isPresent() && tick >= soldier.soloUntil;
        boolean flanking = team != null && team.flanker == soldier.number;
        boolean underFire = withTeam && team != null && team.threat != null && !team.assaulting;
        boolean bounding = underFire && !flanking && team.element(soldier.number) == team.bounding.moving();
        if (bounding && soldier.bound != team.bounding.bound()) {
            soldier.bound = team.bounding.bound();
            soldier.boundDone = false;
            soldier.boundFrom = here.clone();
            soldier.cover = null;
            soldier.peek = null;
            soldier.peeking = false;
            soldier.coverPhaseUntil = 0;
            call(soldier, "advance", tick);
        }
        if (bounding && !soldier.boundDone && (capturing
                || Math.hypot(here.getX() - soldier.boundFrom.getX(), here.getZ() - soldier.boundFrom.getZ()) >= settings.boundDistance())) {
            soldier.boundDone = true;
        }
        boolean advancing = flanking || bounding && !soldier.boundDone;
        double speed = settings.walkSpeed();
        Location destination = null;
        String activity = "idle";
        boolean seekingPatient = false;
        var patient = soldier.role == CombatClass.MEDIC ? patient(soldier, tick, true) : Optional.<LivingEntity>empty();
        boolean wounded = wounded(mob);
        var threat = target.map(LivingEntity::getEyeLocation)
                .or(() -> remembering(soldier, tick) ? Optional.of(soldier.lastSeen.clone().add(0, 1.6, 0)) : Optional.empty())
                .or(() -> underFire ? Optional.of(team.threat.clone().add(0, 1.6, 0)) : Optional.empty());
        boolean teamFallsBack = team != null && post != null && tick < team.fallBackUntil && withTeam && !flanking;
        var fallBack = defending && odds(soldier.faction, post.center()).outnumbered() ? Optional.of(route.retreat())
                : teamFallsBack ? threat.or(() -> Optional.ofNullable(team.threat)).flatMap(enemy -> retreat(soldier, here, post, enemy))
                : Optional.<Location>empty();
        boolean exposed = advancing || team != null && team.assaulting;
        var protectedPosition = threat.filter(enemy -> fallBack.isEmpty() && (!exposed || wounded || soldier.heat.venting(tick)))
                .flatMap(enemy -> cover(soldier, enemy, tick));
        if (threat.isEmpty()) {
            soldier.cover = null;
            soldier.peek = null;
        }
        if (game.gadgets().sieging(mob)) {
            activity = "siege";
            destination = here;
        } else if (fallBack.isPresent()) {
            activity = "fall-back";
            destination = fallBack.get();
            speed = settings.sprintSpeed();
            if (defending && !destination.equals(soldier.destination)) {
                call(soldier, "cover", tick);
            }
        } else if (defending && !game.posts().safe(soldier.objective, soldier.faction)) {
            activity = "contest-owned-post";
            destination = capturing ? here : route.capture();
        } else if (protectedPosition.isPresent() && (wounded || soldier.heat.venting(tick) || patient.isEmpty())) {
            activity = soldier.peeking ? "peek-from-cover" : wounded ? "retreat-wounded" : "hold-cover";
            destination = protectedPosition.get();
            speed = settings.sprintSpeed();
        } else if (patient.isPresent()) {
            seekingPatient = here.distanceSquared(patient.get().getLocation()) > 9 || !mob.hasLineOfSight(patient.get());
            activity = seekingPatient ? "follow-patient" : "treat-patient";
            destination = seekingPatient ? patient.get().getLocation() : here;
        } else if (defending) {
            activity = "defend-peek";
            if (tick >= soldier.shiftAt) {
                var routes = post.routes().get(soldier.faction);
                soldier.navigation.start(soldier.navigation.route() + 1, routes.size());
                route = routes.get(soldier.navigation.route());
                soldier.shiftAt = tick + GUARD_SHIFT_TICKS + ThreadLocalRandom.current().nextInt(GUARD_SHIFT_TICKS);
            }
            destination = route.peek();
        } else if (team != null && team.assaulting && post != null) {
            activity = target.isPresent() ? "strafe-in-post" : "assault";
            var squad = members(team).stream().filter(Bots::deployed).filter(other -> other.guarding < 0).toList();
            double angle = Math.PI * 2 * squad.indexOf(soldier) / Math.max(1, squad.size()) + team.id
                    + (target.isPresent() ? tick * 0.025 : 0);
            destination = post.center().clone().add(Math.cos(angle) * post.radius() * 0.45, 0, Math.sin(angle) * post.radius() * 0.45);
            speed = capturing ? settings.walkSpeed() : settings.sprintSpeed();
        } else if (advancing && route != null) {
            activity = flanking ? "flank" : "bound";
            destination = along(soldier, here, route);
            speed = settings.sprintSpeed();
        } else if (underFire) {
            activity = "overwatch";
        } else if (place == Fireteam.Place.AHEAD && !flanking) {
            activity = "hold-for-team";
        } else if (following) {
            activity = "follow";
            var column = members(team).stream().filter(Bots::deployed).filter(other -> other.guarding < 0 && other != leader.get()).toList();
            int rank = column.indexOf(soldier);
            var lead = leader.get().body.getLocation();
            double gap = settings.spacing() * (rank + 1);
            var slot = Fireteam.behind(lead, team.trail, gap);
            // Aiming a step past its place while the leader walks keeps a member from arriving and halting every second.
            var aimed = BotAim.moving(false, leader.get().body.getVelocity()) ? Fireteam.behind(lead, team.trail, gap - TRAIL_STEP) : slot;
            destination = stagger(aimed, lead, rank % 2 == 0 ? 1 : -1);
            speed = Fireteam.followPace(Fireteam.left(here.toVector(), slot.toVector()), settings.spacing(),
                    settings.walkSpeed(), settings.sprintSpeed());
        } else if (team != null && team.regrouping && route != null && leader.filter(soldier::equals).isPresent()) {
            activity = "regroup";
            destination = along(soldier, here, route);
            speed = settings.walkSpeed() * REGROUP_PACE;
        } else if (route != null && !capturing && soldier.waypoint < route.approach().size()) {
            activity = leader.filter(other -> other != soldier).isPresent() ? "catch-up" : "approach";
            if (activity.equals("catch-up")) {
                speed = settings.sprintSpeed();
            }
            destination = along(soldier, here, route);
        } else if (post != null && !capturing) {
            activity = "capture";
            destination = route.capture();
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
        boolean usingCover = protectedPosition.filter(destination::equals).isPresent();
        boolean arrived = usingCover
                ? BotCover.arrived(here, mob.getEyeLocation(), destination, threat.orElseThrow(), soldier.peeking)
                : here.distanceSquared(destination) <= 2.25;
        boolean keepingPace = activity.equals("follow") && BotAim.moving(false, leader.get().body.getVelocity());
        if (arrived && !seekingPatient && !keepingPace) {
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
        if (soldier.stuckTicks >= 100 && (activity.equals("assault") || activity.equals("strafe-in-post"))) {
            soldier.stuckTicks = 0;
            soldier.progress = here.clone();
        }
        if (soldier.stuckTicks >= 100 && activity.equals("follow")) {
            soldier.soloUntil = tick + SOLO_TICKS;
            soldier.stuckTicks = 0;
            soldier.progress = here.clone();
            mob.getPathfinder().stopPathfinding();
            soldier.navigationResult = "leave-column";
            return;
        }
        if (soldier.stuckTicks >= 100) {
            soldier.boundDone = true;
            boolean followingRoute = activity.equals("approach") || activity.equals("capture") || activity.equals("catch-up")
                    || activity.equals("bound") || activity.equals("flank")
                    || activity.equals("defend-peek") || activity.equals("contest-owned-post");
            if (followingRoute && soldier.navigation.retry(post.routes().get(soldier.faction).size())) {
                var alternate = post.routes().get(soldier.faction).get(soldier.navigation.route());
                soldier.waypoint = enterRoute(soldier, post, alternate);
                soldier.navigationResult = "retry-route";
            } else {
                abandon(soldier, tick);
                soldier.navigationResult = "abandon-route";
            }
            soldier.stuckTicks = 0;
            soldier.progress = here.clone();
            soldier.cover = null;
            soldier.peek = null;
            soldier.peeking = false;
            soldier.coverPhaseUntil = 0;
            mob.getPathfinder().stopPathfinding();
            return;
        }
        double pace = speed * (1 + (soldier.number * 7 % 9 - 4) * 0.015);
        soldier.navigationResult = mob.getPathfinder().moveTo(destination, pace) ? "accepted" : "unreachable";
    }

    /** A follower's place a little to one side of the leader's steps, where it has ground and a clear line to them. */
    private Location stagger(Location slot, Location leader, int side) {
        var ahead = leader.toVector().subtract(slot.toVector()).setY(0);
        double offset = config.squad().stagger();
        if (offset <= 0 || ahead.lengthSquared() < 0.01) {
            return slot;
        }
        var aside = slot.clone().add(new Vector(-ahead.getZ(), 0, ahead.getX()).normalize().multiply(offset * side));
        var eye = new Vector(0, 1.5, 0);
        return game.map().playArea().contains(aside.toVector()) && BotCover.standingSpace(aside)
                && !BotCover.blocked(slot.clone().add(eye), aside.clone().add(eye)) ? aside : slot;
    }

    private static Location along(Soldier soldier, Location here, FrontMap.Route route) {
        while (soldier.waypoint < route.approach().size() && here.distanceSquared(route.approach().get(soldier.waypoint)) < 9) {
            soldier.waypoint++;
        }
        return soldier.waypoint < route.approach().size() ? route.approach().get(soldier.waypoint) : route.capture();
    }

    private void abandon(Soldier soldier, long tick) {
        var team = soldier.team;
        if (soldier.guarding >= 0) {
            soldier.guarding = -1;
        } else if (team != null && members(team).stream().filter(Bots::deployed).filter(other -> other.guarding < 0)
                .findFirst().filter(soldier::equals).isPresent()) {
            team.avoid(soldier.objective, tick + AVOID_TICKS);
        }
        soldier.objective = -1;
    }

    private boolean remembering(Soldier soldier, long tick) {
        long allowance = soldier.cover == null ? 0 : config.cover().holdTicks() + config.cover().peekTicks() + ROUTE_TICKS;
        return soldier.lastSeen != null && tick <= soldier.seenUntil + allowance;
    }

    private Optional<Location> cover(Soldier soldier, Location threat, long tick) {
        var mob = soldier.body;
        var here = mob.getLocation();
        var settings = config.cover();
        if (soldier.cover == null || !BotCover.shields(soldier.cover, mob.getEyeHeight(), threat)) {
            var preferred = new ArrayList<Location>();
            if (soldier.objective >= 0) {
                for (var route : game.map().posts().get(soldier.objective).routes().get(soldier.faction)) {
                    preferred.add(route.cover());
                    preferred.add(route.retreat());
                }
            }
            soldier.cover = BotCover.find(mob, here, preferred, settings.searchRadius(), at ->
                    game.map().playArea().contains(at.toVector()) && BotCover.shields(at, mob.getEyeHeight(), threat)
                            && soldiers.stream().noneMatch(other -> other != soldier && other.body != null
                            && other.cover != null && other.cover.distanceSquared(at) < 2.25)).orElse(null);
            soldier.peek = null;
            soldier.peeking = false;
            soldier.coverPhaseUntil = 0;
            if (soldier.cover == null) {
                return Optional.empty();
            }
            call(soldier, "cover", tick);
        }
        if (soldier.heat.venting(tick) || wounded(mob)
                || soldier.peeking && !BotCover.clearPeek(soldier.peek, mob.getEyeHeight(), threat)) {
            soldier.peeking = false;
            soldier.coverPhaseUntil = 0;
        }
        var destination = soldier.peeking ? soldier.peek : soldier.cover;
        boolean arrived = BotCover.arrived(here, mob.getEyeLocation(), destination, threat, soldier.peeking);
        if (arrived) {
            if (soldier.coverPhaseUntil == 0) {
                soldier.coverPhaseUntil = tick + (soldier.peeking ? settings.peekTicks() : settings.holdTicks());
            } else if (tick >= soldier.coverPhaseUntil) {
                if (soldier.peeking) {
                    soldier.peeking = false;
                } else {
                    var preferred = soldier.objective < 0 ? List.<Location>of()
                            : game.map().posts().get(soldier.objective).routes().get(soldier.faction).stream()
                            .map(FrontMap.Route::peek).toList();
                    soldier.peek = BotCover.find(mob, soldier.cover, preferred, Math.min(4, settings.searchRadius()), at ->
                            game.map().playArea().contains(at.toVector()) && at.distanceSquared(soldier.cover) >= 1
                                    && BotCover.clearPeek(at, mob.getEyeHeight(), threat)).orElse(null);
                    soldier.peeking = soldier.peek != null;
                }
                soldier.coverPhaseUntil = 0;
            }
        }
        return Optional.of(soldier.peeking ? soldier.peek : soldier.cover);
    }

    private void call(Soldier soldier, String cue, long tick) {
        var voice = config.voice();
        if (!voice.enabled() || voice.volume() <= 0 || tick < voiceAt || tick < soldier.voiceAt || !game.radio().infantryReady(tick)) {
            return;
        }
        var at = soldier.body.getEyeLocation();
        var listeners = game.combatants().stream().filter(Player.class::isInstance).map(Player.class::cast)
                .filter(player -> player.getWorld().equals(at.getWorld()) && player.getLocation().distanceSquared(at) <= voice.range() * voice.range())
                .toList();
        if (listeners.isEmpty()) {
            return;
        }
        String sound = "blockcraft:sternenfront.bot." + soldier.faction.key() + "." + cue;
        listeners.forEach(player -> player.playSound(at, sound, SoundCategory.VOICE, voice.volume(), 1));
        game.radio().infantrySpoke(tick);
        voiceAt = tick + voice.globalCooldownTicks();
        soldier.voiceAt = tick + voice.soldierCooldownTicks();
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
        boolean sighted = target instanceof LivingEntity enemy && game.hittable(soldier.faction, enemy)
                && soldier.body.hasLineOfSight(enemy);
        if (!sighted && soldier.suppress == null) {
            soldier.volley.clear();
            return;
        }
        while (soldier.volley.next(tick)) {
            var blaster = soldier.role.blaster(soldier.faction);
            var aim = soldier.aim.clone();
            var random = ThreadLocalRandom.current();
            double spread = Math.max(tuning.spread(), game.config().stats(blaster).spread())
                    * (soldier.body.getVelocity().lengthSquared() > 0.005 ? 1.5 : 1)
                    * (sighted ? BotAim.settle(tick - soldier.acquiredAt, tuning.settleTicks(), tuning.settleSpread()) : SUPPRESSING_SPREAD);
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
                soldier.acquiredAt = game.tick();
            }
            soldier.lastSeen = attacker.getLocation();
            soldier.seenUntil = game.tick() + config.memoryTicks();
        }
    }

    private Optional<LivingEntity> patient(Soldier soldier, long tick, boolean checkPath) {
        var here = soldier.body.getLocation();
        var candidates = game.combatants().stream().filter(ally -> !ally.equals(soldier.body)
                        && game.factionOf(ally).filter(soldier.faction::equals).isPresent())
                .filter(ally -> !(ally instanceof Player player) || game.heroOf(player).isEmpty())
                .filter(ally -> here.getWorld().equals(ally.getWorld()))
                .filter(ally -> ally.getHealth() < ally.getAttribute(Attribute.MAX_HEALTH).getValue())
                .filter(ally -> here.distanceSquared(ally.getLocation()) <= 18 * 18).toList();
        var patients = candidates.stream().map(ally -> new BotPatients.Patient(ally.getUniqueId(),
                ally.getHealth() / ally.getAttribute(Attribute.MAX_HEALTH).getValue(),
                here.distanceSquared(ally.getLocation()) <= 16 && soldier.body.hasLineOfSight(ally))).toList();
        var selected = soldier.patients.choose(patients, tick, config.medic(), id -> {
            if (!checkPath) {
                return true;
            }
            var ally = candidates.stream().filter(candidate -> candidate.getUniqueId().equals(id)).findFirst().orElseThrow();
            soldier.body.getPathfinder().stopPathfinding();
            var path = soldier.body.getPathfinder().findPath(ally.getLocation());
            return path != null && path.canReachFinalPoint();
        });
        return candidates.stream().filter(ally -> selected.filter(ally.getUniqueId()::equals).isPresent()).findFirst();
    }

    private void support(Soldier soldier, long tick) {
        if (tick < soldier.healAt) {
            return;
        }
        patient(soldier, tick, false).filter(ally -> ally.getLocation().distanceSquared(soldier.body.getLocation()) <= 16)
                .filter(soldier.body::hasLineOfSight).ifPresent(ally -> {
                    game.heal(ally, config.healAmount());
                    ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 1.8, 0), 2, 0.2, 0.1, 0.2, 0);
                    soldier.healAt = tick + config.healTicks();
                    call(soldier, "heal", tick);
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
        soldier.recovery.clear();
        soldier.patients.clear();
        soldier.deployAt = game.tick() + game.config().respawn().seconds() * 20L;
        var killer = event.getDamageSource().getCausingEntity() instanceof Player player
                ? Optional.of(player) : Optional.<Player>empty();
        game.botDied(event.getEntity(), soldier.faction, killer, config.killPoints());
        if (soldier.team != null && soldier.guarding < 0 && event.getDamageSource().getCausingEntity() instanceof LivingEntity enemy
                && game.hittable(soldier.faction, enemy)) {
            alarm(soldier.team, event.getEntity().getLocation(), enemy.getLocation());
        }
    }

    /** Teammates close enough to have seen it turn on where the killer stood, and the nearest of them calls it. */
    private void alarm(Fireteam team, Location fallen, Location killer) {
        long tick = game.tick();
        double reach = config.squad().catchUpDistance();
        members(team).stream().filter(Bots::deployed).filter(other -> other.guarding < 0)
                .filter(other -> other.body.getWorld().equals(fallen.getWorld()) && other.body.getLocation().distanceSquared(fallen) <= reach * reach)
                .min(Comparator.comparingDouble(other -> other.body.getLocation().distanceSquared(fallen)))
                .ifPresent(nearest -> {
                    team.alarm = killer.clone();
                    team.alarmUntil = tick + config.alarmTicks();
                    call(nearest, "contact", tick);
                });
    }

    @EventHandler
    public void onShot(ShotEvent event) {
        var shooter = event.shooter();
        var faction = game.factionOf(shooter);
        if (faction.isEmpty()) {
            return;
        }
        var at = shooter.getLocation();
        double range = config.hearingRange();
        long tick = game.tick();
        soldiers.stream().filter(Bots::deployed).filter(soldier -> soldier.faction != faction.get())
                .filter(soldier -> soldier.body.getWorld().equals(at.getWorld()) && soldier.body.getLocation().distanceSquared(at) <= range * range)
                .forEach(soldier -> {
                    soldier.heard = at;
                    soldier.heardUntil = tick + config.alarmTicks();
                });
    }

    private void remove(Soldier soldier) {
        if (soldier.body != null) {
            game.support().removed(soldier.body);
            bodies.remove(soldier.body.getUniqueId());
            soldier.body.remove();
            soldier.body = null;
        }
    }

    /** Every fireteam weighs its objective afresh, even one still on its way to a post it wanted before. */
    public void reconsider() {
        teams.forEach(team -> team.objective = -1);
    }

    public void sightShare(double share) {
        sightShare = share;
    }

    public void clear() {
        for (var player : Bukkit.getOnlinePlayers()) {
            for (var faction : Faction.values()) {
                for (var cue : List.of("contact", "cover", "advance", "heal")) {
                    player.stopSound("blockcraft:sternenfront.bot." + faction.key() + "." + cue, SoundCategory.VOICE);
                }
            }
        }
        voiceAt = 0;
        sightShare = 1;
        soldiers.forEach(this::remove);
        soldiers.clear();
        teams.clear();
        bodies.clear();
        identities.clear();
    }
}
