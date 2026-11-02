package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.LobbyEntrance;
import me.blockcraft.api.game.LobbyHotbar;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.RoundStart;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.ShotEvent;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.arena.FrontMap;
import me.blockcraft.sternenfront.config.SternenfrontConfig;
import me.blockcraft.sternenfront.menu.ClassMenu;
import me.blockcraft.sternenfront.menu.DeployMenu;
import me.blockcraft.sternenfront.menu.TeamMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * One server, one round of Sternenfront: the Kolonien against the Hegemonie for the map's command posts. Players
 * pick a faction and a class in the lobby; in the round every death costs a ticket, the faction holding more posts
 * drains the other, and the dead choose a class and a spawn while they watch from above. The relic blade appears
 * after a while and makes whoever takes it the champion until they fall.
 */
public final class SternenfrontGame implements RoundConnections.Seating {

    private static final long TICKS_PER_SECOND = 20;
    private static final long RESPAWN_TICKS = 1;
    private static final double DEFLECT_FACING = 0.3;
    private static final int HIT_NOTE = 12;
    private static final int HEADSHOT_NOTE = 20;
    private static final int DEPLOY_SLOT = 4;
    private static final Title.Times BANNER = Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500));

    private record Burst(Player shooter, Blaster blaster, long at) { }

    private final Plugin plugin;
    private final SternenfrontConfig config;
    private final Messages messages;
    private final FrontMap map;
    private final Location lobby;
    private final Teams teams;
    private final Loadouts loadouts;
    private final RoundSummary summary;
    private final Round round;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final LobbyEntrance entrance;
    private final GameSidebar sidebar;
    private final Posts posts;
    private final Bolts bolts;
    private final Gadgets gadgets;
    private final Map<UUID, Fighter> fighters = new HashMap<>();
    /** Who started the round for which faction; a win counts for those of them still on the server. */
    private final Map<UUID, Faction> roster = new HashMap<>();
    private final Map<UUID, Blaster> lastBolt = new HashMap<>();
    private final Map<UUID, DeployMenu> deployMenus = new HashMap<>();
    private final List<Burst> bursts = new ArrayList<>();
    private Reinforcements reinforcements;
    private Relic relic;
    private BukkitTask loop;
    private long tick;
    private int secondsLeft;
    private boolean dirty;
    private Optional<Faction> winner = Optional.empty();

    public SternenfrontGame(Plugin plugin, GameConfig gameConfig, SternenfrontConfig config, Messages messages,
                            FrontMap map, Location lobby, StatsRepository stats, StatsMenus statsMenus) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.map = map;
        this.lobby = lobby;
        this.teams = new Teams(config.playersPerTeam());
        this.loadouts = new Loadouts(messages, config);
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(config.rewards().kill(), config.rewards().win()), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.capacity(), gameConfig);
        this.countdown = new LobbyCountdown(plugin, round, gameConfig, this::start);
        this.hotbar = new LobbyHotbar(plugin, List.of(
                new LobbyHotbar.Choice(MenuIcon.TEAM, messages.get("item.faction"),
                        player -> new TeamMenu(messages, teams, config.playersPerTeam(), this::chooseFaction).open(player)),
                new LobbyHotbar.Choice(MenuIcon.LOADOUT, messages.get("item.class"),
                        player -> new ClassMenu(messages, loadouts, fighter(player).combatClass(), this::chooseClass).open(player))),
                statsMenus, countdown);
        this.sidebar = new GameSidebar(messages.get("scoreboard.title"), round);
        this.entrance = new LobbyEntrance(round, countdown, hotbar, () -> Optional.of(lobby),
                () -> Optional.of(phase() == GamePhase.INGAME ? map.spectatorSpawn() : lobby),
                OptionalInt.of(config.fullSeconds()), player -> { });
        this.posts = new Posts(messages, map.posts(), config.captureSeconds());
        this.bolts = new Bolts(this);
        this.gadgets = new Gadgets(this);
        this.reinforcements = new Reinforcements(config.tickets());
        this.relic = new Relic(this, map.relic(), Long.MAX_VALUE);
        round.playArea(map.world(), map.playArea());
    }

    public GamePhase phase() {
        return round.phase();
    }

    public LobbyCountdown countdown() {
        return countdown;
    }

    public LobbyHotbar hotbar() {
        return hotbar;
    }

    public FrontMap map() {
        return map;
    }

    public Messages messages() {
        return messages;
    }

    public SternenfrontConfig config() {
        return config;
    }

    public Loadouts loadouts() {
        return loadouts;
    }

    public Posts posts() {
        return posts;
    }

    public long tick() {
        return tick;
    }

    public Optional<Faction> factionOf(Player player) {
        return teams.of(player.getUniqueId());
    }

    public boolean allies(Player one, Player other) {
        return teams.allies(one.getUniqueId(), other.getUniqueId());
    }

    public boolean isPlaying(Player player) {
        return round.isPlaying(player);
    }

    public Fighter fighter(Player player) {
        return fighters.computeIfAbsent(player.getUniqueId(), ignored -> new Fighter(config.heat()));
    }

    /** In the running round, alive and deployed, as opposed to choosing a spawn or watching. */
    public boolean onField(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        return phase() == GamePhase.INGAME && round.isPlaying(player) && fighter != null && fighter.onField() && !player.isDead();
    }

    public boolean choosing(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        return phase() == GamePhase.INGAME && fighter != null && !fighter.onField();
    }

    /** Whether a bolt or blast of {@code faction} can hit the player. */
    public boolean hittable(Faction faction, Player target) {
        return onField(target) && factionOf(target).filter(own -> own != faction).isPresent();
    }

    public boolean isProtected(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        return fighter != null && fighter.isProtected(tick);
    }

    public void endProtection(Player player) {
        Optional.ofNullable(fighters.get(player.getUniqueId())).ifPresent(Fighter::endProtection);
    }

    public void hurt(Player player) {
        Optional.ofNullable(fighters.get(player.getUniqueId())).ifPresent(fighter -> fighter.hurt(tick));
    }

    /** Whether the fall that hurts the player now is one a leap promised to spare; the promise is used up. */
    public boolean sparesFall(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        if (fighter == null || !fighter.fallSafe(tick)) {
            return false;
        }
        fighter.fallSafeUntil(0);
        return true;
    }

    @Override
    public void join(Player player) {
        if (round.rejoin(player)) {
            teams.of(player.getUniqueId()).ifPresent(faction -> BlockcraftApi.get().tabTeams().color(player, faction.text()));
            if (player.isDead()) {
                round.respawn(player, RESPAWN_TICKS);
            } else {
                player.teleport(map.overview());
                beginChoosing(player);
            }
        } else {
            entrance.admit(player);
        }
        fighters.forEach((id, fighter) -> {
            var other = Bukkit.getPlayer(id);
            if (other != null && !other.equals(player) && phase() == GamePhase.INGAME && !fighter.onField()) {
                player.hidePlayer(plugin, other);
            }
        });
        refreshAll();
    }

    @Override
    public void quit(Player player) {
        dropRelic(player, Optional.empty());
        deployMenus.remove(player.getUniqueId());
        if (round.stepOut(player, this::forfeited)) {
            refreshAll();
            return;
        }
        leave(player);
    }

    private void forfeited(UUID player) {
        roster.remove(player);
        fighters.remove(player);
        var faction = teams.leave(player);
        if (faction.isPresent() && teams.members(faction.get()).isEmpty()) {
            endRound(Optional.of(faction.get().enemy()));
        }
        refreshAll();
    }

    private void leave(Player player) {
        boolean wasPlaying = round.leave(player);
        teams.leave(player.getUniqueId());
        fighters.remove(player.getUniqueId());
        BlockcraftApi.get().tabTeams().clearColor(player);
        if (phase() == GamePhase.INGAME && wasPlaying) {
            roster.remove(player.getUniqueId());
            Arrays.stream(Faction.values()).filter(faction -> teams.members(faction).isEmpty()).findFirst()
                    .ifPresent(faction -> endRound(Optional.of(faction.enemy())));
        }
        refreshAll();
    }

    @Override
    public void vanished(Player player) {
        if (phase() == GamePhase.INGAME && round.isPlaying(player)) {
            reveal(player);
            leave(player);
            round.watch(player);
        } else if (phase() == GamePhase.LOBBY) {
            teams.leave(player.getUniqueId());
            BlockcraftApi.get().tabTeams().clearColor(player);
            refreshAll();
        }
    }

    public void chooseFaction(Player player, Faction faction) {
        if (phase() != GamePhase.LOBBY || !round.isPlaying(player)) {
            return;
        }
        switch (teams.join(player.getUniqueId(), faction)) {
            case ALREADY -> player.sendMessage(messages.get("faction.already"));
            case FULL -> {
                player.sendMessage(messages.get("faction.full"));
                Sounds.MENU_DENY.play(player);
            }
            case JOINED -> {
                Sounds.EQUIP.play(player);
                BlockcraftApi.get().tabTeams().color(player, faction.text());
                loadouts.dressForLobby(player, faction);
                player.sendMessage(messages.get("faction.joined", factionName(faction)));
                refreshAll();
            }
        }
    }

    /** In the lobby, or while choosing a spawn, the class the player deploys as next. */
    public void chooseClass(Player player, CombatClass combatClass) {
        if (phase() == GamePhase.ENDING || !round.isPlaying(player)) {
            return;
        }
        fighter(player).combatClass(combatClass);
        Sounds.EQUIP.play(player);
        BlockcraftApi.get().notifications().feedback(player, messages.get("class.chosen", className(combatClass)));
        Optional.ofNullable(deployMenus.get(player.getUniqueId())).ifPresent(DeployMenu::refresh);
        dirty = true;
        if (phase() == GamePhase.LOBBY) {
            refreshAll();
        }
    }

    private void start() {
        teams.assignRest(round.players().stream().map(Player::getUniqueId).toList()).forEach((id, faction) -> {
            var player = Bukkit.getPlayer(id);
            if (player != null) {
                BlockcraftApi.get().tabTeams().color(player, faction.text());
                player.sendMessage(messages.get("faction.assigned", factionName(faction)));
            }
        });
        if (Arrays.stream(Faction.values()).anyMatch(faction -> teams.size(faction) == 0)) {
            Bukkit.broadcast(messages.get("lobby.no-opponent"));
            countdown.start();
            return;
        }
        round.enter(GamePhase.INGAME);
        tick = 0;
        reinforcements = new Reinforcements(config.tickets());
        relic = new Relic(this, map.relic(), config.relic().firstSeconds() * TICKS_PER_SECOND);
        posts.show();
        for (var player : round.players()) {
            var faction = teams.of(player.getUniqueId()).orElseThrow();
            roster.put(player.getUniqueId(), faction);
            PlayerReset.reset(player, GameMode.ADVENTURE);
            config.combat().apply(player);
            deploy(player, new Spawn.Headquarters());
        }
        round.spectators().forEach(spectator -> spectator.teleport(map.spectatorSpawn()));
        RoundStart.go(round.players());
        for (var faction : Faction.values()) {
            var names = teams.members(faction).stream().map(Bukkit::getPlayer).filter(Objects::nonNull).map(Player::getName).toList();
            Bukkit.broadcast(messages.get("round.faction", factionName(faction), Placeholder.unparsed("players", String.join(", ", names))));
        }
        messages.getLines("round.start").forEach(Bukkit::broadcast);
        secondsLeft = config.roundSeconds();
        round.clock(new TimerBar(messages, "timer.round", config.roundSeconds()), remaining -> {
            secondsLeft = remaining;
            round.announceEnd(remaining);
            dirty |= round.clockOnSidebar();
        }, () -> endRound(reinforcements.leader()));
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRound, 1L, 1L);
        refreshAll();
    }

    private void tickRound() {
        tick++;
        bolts.tick();
        gadgets.tick(tick);
        relic.tick(tick);
        fireBursts();
        for (var player : round.players()) {
            var fighter = fighters.get(player.getUniqueId());
            if (fighter == null) {
                continue;
            }
            if (fighter.onField()) {
                fighter.heat().cool(tick);
                player.setExp(fighter.heat().level());
            } else {
                deployWhenDue(player, fighter);
            }
        }
        if (tick % TICKS_PER_SECOND == 0) {
            second();
        }
    }

    private void second() {
        var field = new LinkedHashMap<Player, Faction>();
        round.players().stream().filter(this::onField)
                .forEach(player -> factionOf(player).ifPresent(faction -> field.put(player, faction)));
        posts.second(field).forEach(this::postChanged);
        if ((tick / TICKS_PER_SECOND) % config.bleedSeconds() == 0) {
            reinforcements.bleed(posts.held());
            dirty = true;
        }
        field.forEach(this::guardHeadquarters);
        field.keySet().forEach(this::regenerate);
        round.players().forEach(this::showChoosing);
        if (phase() == GamePhase.INGAME && reinforcements.defeated().isPresent()) {
            endRound(reinforcements.defeated().map(Faction::enemy));
            return;
        }
        if (dirty) {
            dirty = false;
            refreshAll();
        }
    }

    private void postChanged(Posts.Event event) {
        var name = posts.name(event.index());
        switch (event.change()) {
            case CommandPost.Change.Captured captured -> {
                Bukkit.broadcast(messages.get("post.captured", name, factionName(captured.by())));
                event.present().forEach(player -> summary.awardCoins(player, config.rewards().capture()));
                announce(captured.by(), Sounds.NOTIFY, Sounds.CORE_ALARM);
            }
            case CommandPost.Change.Neutralised neutralised -> {
                Bukkit.broadcast(messages.get("post.neutralised", name, factionName(neutralised.from())));
                announce(neutralised.from().enemy(), Sounds.NOTIFY, Sounds.CORE_ALARM);
            }
        }
        dirty = true;
    }

    private void announce(Faction gaining, Sounds forGaining, Sounds forLosing) {
        round.players().forEach(player -> factionOf(player)
                .ifPresent(faction -> (faction == gaining ? forGaining : forLosing).play(player)));
    }

    private void guardHeadquarters(Player player, Faction faction) {
        var enemyBase = map.headquarters().get(faction.enemy());
        if (enemyBase != null && enemyBase.zone().contains(player.getLocation().toVector())) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("headquarters.warning"));
            player.damage(config.headquartersDamage(), DamageSource.builder(DamageType.MAGIC).build());
        }
    }

    private void regenerate(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        if (fighter != null && tick - fighter.lastHurt() >= config.regen().delaySeconds() * TICKS_PER_SECOND) {
            heal(player, config.regen().perSecond());
        }
    }

    /** True if the player had health to gain. */
    public boolean heal(Player player, double amount) {
        var max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || player.isDead() || player.getHealth() >= max.getValue()) {
            return false;
        }
        player.setHealth(Math.min(max.getValue(), player.getHealth() + amount));
        return true;
    }

    public void pullTrigger(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        var faction = factionOf(player);
        if (!onField(player) || fighter == null || fighter.champion() || faction.isEmpty()) {
            return;
        }
        var blaster = fighter.combatClass().blaster();
        var stats = config.stats(blaster);
        switch (fighter.heat().fire(tick, stats.cooldownTicks(), stats.heat())) {
            case TOO_SOON -> {
                return;
            }
            case VENTING -> {
                BlockcraftApi.get().notifications().feedback(player, messages.get("blaster.venting"));
                return;
            }
            case OVERHEATED -> overheated(player);
            case FIRED -> { }
        }
        fighter.endProtection();
        Bukkit.getPluginManager().callEvent(new ShotEvent(player));
        shoot(player, faction.get(), blaster);
        for (int bolt = 1; bolt < stats.bolts(); bolt++) {
            bursts.add(new Burst(player, blaster, tick + (long) bolt * stats.burstTicks()));
        }
        player.setExp(fighter.heat().level());
    }

    private void shoot(Player player, Faction faction, Blaster blaster) {
        var stats = config.stats(blaster);
        bolts.fire(player, faction, blaster, stats);
        var eye = player.getEyeLocation();
        player.getWorld().playSound(eye, stats.sound(), 1f, stats.pitch());
    }

    private void fireBursts() {
        bursts.removeIf(burst -> {
            if (burst.at() > tick) {
                return false;
            }
            var fighter = fighters.get(burst.shooter().getUniqueId());
            if (onField(burst.shooter()) && fighter != null && !fighter.champion()) {
                factionOf(burst.shooter()).ifPresent(faction -> shoot(burst.shooter(), faction, burst.blaster()));
            }
            return true;
        });
    }

    private void overheated(Player player) {
        var eye = player.getEyeLocation();
        player.getWorld().playSound(eye, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.6f);
        player.getWorld().spawnParticle(Particle.SMOKE, eye.add(eye.getDirection().multiply(0.6)), 8, 0.1, 0.1, 0.1, 0.02);
        BlockcraftApi.get().notifications().feedback(player, messages.get("blaster.overheated"));
    }

    /** F uses a class's first ability, Q its second. */
    public void useAbility(Player player, int index) {
        var fighter = fighters.get(player.getUniqueId());
        if (fighter == null || !onField(player) || fighter.champion()) {
            return;
        }
        var abilities = fighter.combatClass().abilities();
        if (index < abilities.size()) {
            useAbility(player, abilities.get(index));
        }
    }

    public void useAbility(Player player, Ability ability) {
        var fighter = fighters.get(player.getUniqueId());
        var faction = factionOf(player);
        if (fighter == null || faction.isEmpty() || !onField(player) || fighter.champion()
                || !fighter.combatClass().abilities().contains(ability)) {
            return;
        }
        if (player.hasCooldown(ability.material())) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("ability.cooldown",
                    Placeholder.component("ability", messages.get("ability." + ability.key() + ".name")),
                    Formatter.number("seconds", (player.getCooldown(ability.material()) + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND)));
            return;
        }
        if (!gadgets.use(player, fighter, faction.get(), ability, tick)) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("ability.no-room"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        if (ability == Ability.GRENADE) {
            fighter.endProtection();
        }
        player.setCooldown(ability.material(), config.cooldownTicks(ability));
    }

    /** The champion's blade leaps forward on a right click. */
    public void leap(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        if (fighter == null || !fighter.champion() || !onField(player)) {
            return;
        }
        var blade = Material.NETHERITE_SWORD;
        if (player.hasCooldown(blade)) {
            return;
        }
        gadgets.leap(player, fighter, config.relic().leapForward(), config.relic().leapUp(), tick);
        player.setCooldown(blade, config.relic().leapCooldownSeconds() * (int) TICKS_PER_SECOND);
    }

    public void boltHit(Player shooter, Blaster blaster, Player victim, double damage, boolean headshot) {
        if (isProtected(victim)) {
            victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.1);
            return;
        }
        lastBolt.put(victim.getUniqueId(), blaster);
        var velocity = victim.getVelocity();
        victim.setNoDamageTicks(0);
        victim.damage(damage, shooter.isOnline()
                ? DamageSource.builder(DamageType.ARROW).withCausingEntity(shooter).withDirectEntity(shooter).build()
                : DamageSource.builder(DamageType.ARROW).build());
        victim.setVelocity(velocity);
        if (shooter.isOnline()) {
            Sounds.NOTE_PLING.note(shooter, headshot ? HEADSHOT_NOTE : HIT_NOTE);
        }
    }

    public void blast(Player thrower, Player victim, double damage, Location at) {
        if (isProtected(victim)) {
            return;
        }
        lastBolt.remove(victim.getUniqueId());
        var source = DamageSource.builder(DamageType.PLAYER_EXPLOSION).withDamageLocation(at);
        if (thrower.isOnline()) {
            source.withCausingEntity(thrower).withDirectEntity(thrower);
        }
        victim.setNoDamageTicks(0);
        victim.damage(damage, source.build());
    }

    /** A sneaking champion sends back the bolts that hit them from the front. */
    public boolean deflects(Player victim, Vector boltDirection) {
        var fighter = fighters.get(victim.getUniqueId());
        return fighter != null && fighter.champion() && victim.isSneaking()
                && victim.getLocation().getDirection().dot(boltDirection.clone().multiply(-1)) > DEFLECT_FACING;
    }

    public void deflected(Player champion, Location at) {
        champion.getWorld().playSound(at, Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f);
        champion.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 10, 0.2, 0.2, 0.2, 0.2);
    }

    public void relicAppeared() {
        var title = Title.title(messages.get("relic.title"), messages.get("relic.subtitle"), BANNER);
        Bukkit.getOnlinePlayers().forEach(player -> BlockcraftApi.get().notifications().important(player, title));
        Bukkit.broadcast(messages.get("relic.appeared"));
    }

    public void crown(Player player) {
        var fighter = fighter(player);
        fighter.champion(true);
        loadouts.arm(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        player.setGlowing(true);
        Bukkit.broadcast(messages.get("relic.taken", Placeholder.unparsed("player", player.getName()),
                factionOf(player).map(this::factionName).orElse(TagResolver.empty())));
        dirty = true;
    }

    private void dropRelic(Player champion, Optional<Player> killer) {
        var fighter = fighters.get(champion.getUniqueId());
        if (fighter == null || !fighter.champion()) {
            return;
        }
        fighter.champion(false);
        champion.setGlowing(false);
        relic.lost(tick, config.relic().respawnSeconds() * TICKS_PER_SECOND);
        killer.ifPresentOrElse(by -> {
            summary.awardCoins(by, config.rewards().champion());
            Bukkit.broadcast(messages.get("relic.slain", Placeholder.unparsed("player", champion.getName()),
                    Placeholder.unparsed("killer", by.getName())));
        }, () -> Bukkit.broadcast(messages.get("relic.lost", Placeholder.unparsed("player", champion.getName()))));
        dirty = true;
    }

    public void death(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        event.deathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        var blaster = Optional.ofNullable(lastBolt.remove(victim.getUniqueId()));
        if (phase() != GamePhase.INGAME || !round.isPlaying(victim)) {
            return;
        }
        var death = summary.resolveDeath(event);
        var line = death.killer().filter(killer -> blaster.isPresent()
                        && event.getDamageSource().getDamageType() == DamageType.ARROW)
                .map(killer -> messages.pick("death.blaster", Placeholder.unparsed("player", victim.getName()),
                        Placeholder.unparsed("killer", killer.getName()),
                        Placeholder.component("weapon", messages.get("blaster." + blaster.orElseThrow().key() + ".name"))))
                .orElse(death.line());
        Bukkit.broadcast(line);
        death.killer().ifPresent(killer -> summary.awardKill(killer, victim, Defeat.RESPAWN));
        summary.death(victim);
        dropRelic(victim, death.killer());
        factionOf(victim).ifPresent(faction -> reinforcements.lose(faction, 1));
        round.respawn(victim, RESPAWN_TICKS);
        dirty = true;
    }

    public void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        if (phase() != GamePhase.INGAME) {
            event.setRespawnLocation(lobby);
            return;
        }
        if (round.isPlaying(player) && factionOf(player).isPresent()) {
            event.setRespawnLocation(map.overview());
            Tasks.sync(plugin, () -> beginChoosing(player));
        } else {
            event.setRespawnLocation(map.spectatorSpawn());
            Tasks.sync(plugin, () -> round.watch(player));
        }
    }

    /** Dead and watching from above, unseen and unhurt, with the deploy menu open. */
    private void beginChoosing(Player player) {
        if (!player.isOnline() || phase() != GamePhase.INGAME || !round.isPlaying(player)) {
            return;
        }
        var fighter = fighter(player);
        PlayerReset.reset(player, GameMode.ADVENTURE);
        player.setGlowing(false);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        Bukkit.getOnlinePlayers().stream().filter(other -> !other.equals(player)).forEach(other -> other.hidePlayer(plugin, player));
        fighter.choosing(Optional.of(new Fighter.Choosing(tick + config.respawn().seconds() * TICKS_PER_SECOND,
                tick + config.respawn().autoSeconds() * TICKS_PER_SECOND, Optional.empty())));
        player.getInventory().setItem(DEPLOY_SLOT, loadouts.deployItem());
        player.getInventory().setHeldItemSlot(DEPLOY_SLOT);
        openDeploy(player);
        showChoosing(player);
    }

    public void openDeploy(Player player) {
        if (!choosing(player)) {
            return;
        }
        var menu = new DeployMenu(this, player);
        deployMenus.put(player.getUniqueId(), menu);
        menu.open(player);
    }

    /** A spawn chosen in the deploy menu: taken at once when the wait is over, else as soon as it is. */
    public void choose(Player player, Spawn spawn) {
        var fighter = fighters.get(player.getUniqueId());
        var faction = factionOf(player);
        if (fighter == null || fighter.choosing().isEmpty() || faction.isEmpty()) {
            return;
        }
        if (!available(faction.get(), spawn)) {
            Sounds.MENU_DENY.play(player);
            return;
        }
        var choosing = fighter.choosing().get().choose(spawn);
        fighter.choosing(Optional.of(choosing));
        if (tick >= choosing.readyTick()) {
            deploy(player, spawn);
        } else {
            showChoosing(player);
            Optional.ofNullable(deployMenus.get(player.getUniqueId())).ifPresent(DeployMenu::refresh);
        }
    }

    public boolean available(Faction faction, Spawn spawn) {
        return switch (spawn) {
            case Spawn.Headquarters ignored -> true;
            case Spawn.Post post -> post.index() < posts.size() && posts.safe(post.index(), faction);
        };
    }

    /** Ticks until the player may deploy, 0 once they may. */
    public long waitTicks(Player player) {
        return Optional.ofNullable(fighters.get(player.getUniqueId())).flatMap(Fighter::choosing)
                .map(choosing -> Math.max(0, choosing.readyTick() - tick)).orElse(0L);
    }

    private void deployWhenDue(Player player, Fighter fighter) {
        fighter.choosing().ifPresent(choosing -> {
            if (tick >= choosing.autoTick()) {
                deploy(player, choosing.chosen().filter(spawn -> factionOf(player).filter(faction -> available(faction, spawn)).isPresent())
                        .orElse(new Spawn.Headquarters()));
            } else if (tick >= choosing.readyTick() && choosing.chosen().isPresent()) {
                deploy(player, choosing.chosen().get());
            }
        });
    }

    private void showChoosing(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        if (fighter == null || fighter.choosing().isEmpty()) {
            return;
        }
        var choosing = fighter.choosing().get();
        long waiting = choosing.readyTick() - tick;
        var status = waiting > 0
                ? messages.get("deploy.waiting", Formatter.number("seconds", seconds(waiting)))
                : messages.get("deploy.ready", Formatter.number("seconds", seconds(choosing.autoTick() - tick)));
        BlockcraftApi.get().notifications().status(player, status);
        Optional.ofNullable(deployMenus.get(player.getUniqueId())).ifPresent(DeployMenu::refresh);
    }

    private static long seconds(long ticks) {
        return Math.max(0, (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }

    private void deploy(Player player, Spawn wanted) {
        var faction = factionOf(player);
        if (faction.isEmpty() || !player.isOnline() || player.isDead()) {
            return;
        }
        var spawn = available(faction.get(), wanted) ? wanted : new Spawn.Headquarters();
        if (!spawn.equals(wanted)) {
            player.sendMessage(messages.get("deploy.lost"));
        }
        var spots = switch (spawn) {
            case Spawn.Headquarters ignored -> map.headquarters().get(faction.get()).spawns();
            case Spawn.Post post -> map.posts().get(post.index()).spawns();
        };
        var spot = farthestFromEnemies(spots, faction.get());
        var fighter = fighter(player);
        if (deployMenus.remove(player.getUniqueId()) != null) {
            Menu.close(player);
        }
        reveal(player);
        PlayerReset.reset(player, GameMode.ADVENTURE);
        config.combat().apply(player);
        player.teleport(spot);
        fighter.deployed(config.heat(), tick + config.respawn().protectionSeconds() * TICKS_PER_SECOND);
        fighter.hurt(tick);
        loadouts.equip(player, faction.get(), fighter.combatClass());
        player.getInventory().setHeldItemSlot(0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        Sounds.TELEPORT.play(player);
        dirty = true;
    }

    /** The spot farthest from the nearest enemy, and among equals the one farthest from anyone, so a crowd spreads. */
    private Location farthestFromEnemies(List<Location> spots, Faction faction) {
        var field = round.players().stream().filter(this::onField).toList();
        var enemies = field.stream().filter(other -> factionOf(other).filter(faction.enemy()::equals).isPresent()).toList();
        return spots.stream().max(Comparator.comparingDouble((Location spot) -> nearest(spot, enemies))
                        .thenComparingDouble(spot -> nearest(spot, field)))
                .orElseThrow().clone();
    }

    private static double nearest(Location spot, List<Player> players) {
        return players.stream().map(Player::getLocation).filter(at -> at.getWorld().equals(spot.getWorld()))
                .mapToDouble(at -> at.distanceSquared(spot)).min().orElse(Double.MAX_VALUE);
    }

    /** Back to normal: seen by everyone, hurtable, on foot. */
    private void reveal(Player player) {
        player.setInvulnerable(false);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setCollidable(true);
        Bukkit.getOnlinePlayers().stream().filter(other -> !other.equals(player)).forEach(other -> other.showPlayer(plugin, player));
    }

    /** Below the map a fighter dies; anyone else is put back above it. */
    public void fell(Player player) {
        if (onField(player)) {
            player.damage(Float.MAX_VALUE, DamageSource.builder(DamageType.OUT_OF_WORLD).build());
        } else if (choosing(player)) {
            player.teleport(map.overview());
        } else if (round.isSpectator(player)) {
            player.teleport(map.spectatorSpawn());
        }
    }

    private void endRound(Optional<Faction> won) {
        if (phase() != GamePhase.INGAME) {
            return;
        }
        winner = won;
        cleanUp();
        won.ifPresent(faction -> roster.entrySet().stream().filter(entry -> entry.getValue() == faction)
                .map(entry -> Bukkit.getPlayer(entry.getKey())).filter(Objects::nonNull).forEach(summary::awardWin));
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(won.map(faction -> messages.get("round.winner", factionName(faction),
                        Formatter.number("tickets", reinforcements.left(faction))))
                .orElseGet(() -> messages.get("round.draw")));
        Bukkit.broadcast(Component.empty());
        for (var player : Bukkit.getOnlinePlayers()) {
            reveal(player);
            player.setGlowing(false);
            if (!player.isDead()) {
                PlayerReset.reset(player, GameMode.ADVENTURE);
                player.teleport(lobby);
                hotbar.giveHub(player);
            }
        }
        fighters.values().forEach(fighter -> fighter.choosing(Optional.empty()));
        round.end();
        refreshAll();
    }

    private void cleanUp() {
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
        bursts.clear();
        bolts.clear();
        gadgets.clear();
        relic.clear();
        posts.clear();
        deployMenus.clear();
    }

    public void stop() {
        countdown.cancel();
        cleanUp();
        round.stop();
    }

    @Override
    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    private void show(Player player) {
        var icons = GameSidebar.icons(player);
        var mapName = Component.text(map.name());
        switch (phase()) {
            case LOBBY -> sidebar.lobby(player, config.capacity(), mapName, List.of(
                    new GameSidebar.Section(messages.get("scoreboard.faction", icons), GameSidebar.value(factionOf(player)
                            .map(this::factionDisplay).orElseGet(() -> messages.get("scoreboard.no-faction")))),
                    new GameSidebar.Section(messages.get("scoreboard.class", icons),
                            GameSidebar.value(messages.get("class." + Optional.ofNullable(fighters.get(player.getUniqueId()))
                                    .map(Fighter::combatClass).orElse(CombatClass.SOLDIER).key() + ".name")))));
            case INGAME -> {
                var tickets = Arrays.stream(Faction.values()).map(faction -> messages.get("scoreboard.tickets-line",
                        factionName(faction), Formatter.number("tickets", reinforcements.left(faction)))).toList();
                var postLines = posts.indices().stream().map(index -> messages.get(posts.contested(index)
                                ? "scoreboard.post-contested" : "scoreboard.post", posts.name(index),
                        Placeholder.styling("tint", posts.owner(index).map(Faction::text)
                                .orElse(NamedTextColor.WHITE)))).toList();
                sidebar.round(player, config.capacity(), secondsLeft, mapName, List.of(
                        new GameSidebar.Section(messages.get("scoreboard.tickets", icons), tickets),
                        new GameSidebar.Section(messages.get("scoreboard.posts", icons), postLines),
                        new GameSidebar.Section(messages.get("scoreboard.kills", icons),
                                GameSidebar.value(Component.text(summary.kills(player))))));
            }
            case ENDING -> sidebar.end(player, winner.map(this::factionDisplay).stream().toList());
        }
    }

    private Component factionDisplay(Faction faction) {
        return messages.get("faction." + faction.key() + ".name").colorIfAbsent(faction.text());
    }

    private TagResolver factionName(Faction faction) {
        return Placeholder.component("faction", factionDisplay(faction));
    }

    private TagResolver className(CombatClass combatClass) {
        return Placeholder.component("class", messages.get("class." + combatClass.key() + ".name"));
    }
}
