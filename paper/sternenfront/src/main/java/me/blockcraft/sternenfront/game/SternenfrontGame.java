package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.Ballot;
import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.game.DeathLines;
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
import me.blockcraft.api.game.VoteMenu;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.arena.FrontMap;
import me.blockcraft.sternenfront.bot.BotConfig;
import me.blockcraft.sternenfront.bot.BotDifficulty;
import me.blockcraft.sternenfront.bot.Bots;
import me.blockcraft.sternenfront.combat.Combat;
import me.blockcraft.sternenfront.combat.BattleEffects;
import me.blockcraft.sternenfront.combat.CombatConfig;
import me.blockcraft.sternenfront.combat.Spectacle;
import me.blockcraft.sternenfront.vehicle.Speeders;
import me.blockcraft.sternenfront.vehicle.SpeederConfig;
import me.blockcraft.sternenfront.config.FeelConfig;
import me.blockcraft.sternenfront.config.SternenfrontConfig;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.hero.HeroAbility;
import me.blockcraft.sternenfront.hero.HeroConfig;
import me.blockcraft.sternenfront.hero.Heroes;
import me.blockcraft.sternenfront.hero.KillStreaks;
import me.blockcraft.sternenfront.menu.ClassMenu;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * One server, one round of Sternenfront: the Kolonien against the Hegemonie for the map's command posts. Players
 * pick a faction and a class in the lobby; in the round every death costs a ticket, the faction holding more posts
 * drains the other, and the dead watch their killer, then choose a class, a hero if their battle points pay for
 * one, and a spawn while they watch from above. The relic blade appears after a while and makes whoever takes it
 * the champion until they fall. Falling tickets and passing time carry the round through its three acts.
 */
public final class SternenfrontGame implements RoundConnections.Seating {

    private static final long TICKS_PER_SECOND = 20;
    private static final long RESPAWN_TICKS = 1;
    private static final float SHOT_VOLUME = 2f;
    private static final float MARKER_VOLUME = 0.6f;
    private static final NamespacedKey SCOPE = new NamespacedKey("sternenfront", "scope");
    private static final Title.Times BANNER = Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500));
    private static final Title.Times MARKER = Title.Times.times(Duration.ZERO, Duration.ofMillis(200), Duration.ofMillis(150));

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
    private final BattleEffects effects;
    private final Spectacle spectacle;
    private final BattleMusic music;
    private final Bots bots;
    private final BotConfig botConfig;
    private final Ballot<BotDifficulty> botDifficultyVotes = new Ballot<>();
    private final Combat combat;
    private final Speeders speeders;
    private final SpawnSpots spawnSpots;
    private final Deployment deployment;
    private final Battlefield battlefield;
    private final SupportRewards support;
    private final Gadgets gadgets;
    private final Heroes heroes;
    private final FeelConfig feel;
    private final KillStreaks streaks;
    private final Map<UUID, Fighter> fighters = new HashMap<>();
    /** While the round deals damage itself, which marks its own hits. */
    private boolean dealing;
    private UUID selfExplosion;
    /** Who started the round for which faction; a win counts for those of them still on the server. */
    private final Map<UUID, Faction> roster = new HashMap<>();
    private final Map<UUID, Blaster> lastBolt = new HashMap<>();
    private final Set<UUID> strikesReady = new HashSet<>();
    private final List<Burst> bursts = new ArrayList<>();
    private Reinforcements reinforcements;
    private RoundActs acts;
    /** From the assault on, who trails and which post they land next to. */
    private Optional<PriorityPost> front = Optional.empty();
    /** What the sidebar counts: bot kills too, which the round summary's stats and coins leave out on purpose. */
    private final Map<UUID, Integer> fieldKills = new HashMap<>();
    private Relic relic;
    private BukkitTask loop;
    private long tick;
    private int secondsLeft;
    private boolean dirty;
    private Optional<Faction> winner = Optional.empty();

    public SternenfrontGame(JavaPlugin plugin, GameConfig gameConfig, SternenfrontConfig config, HeroConfig heroConfig,
                            FeelConfig feel, Messages messages, FrontMap map, Location lobby, StatsRepository stats,
                            StatsMenus statsMenus) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.map = map;
        this.lobby = lobby;
        this.teams = new Teams(config.playersPerTeam());
        this.loadouts = new Loadouts(messages, config);
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(config.rewards().kill(), config.rewards().win()), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.capacity(), gameConfig);
        this.botConfig = BotConfig.from(plugin.getConfig(), config.playersPerTeam());
        this.countdown = new LobbyCountdown(plugin, round, gameConfig, botConfig.enabled() ? 1 : 2, remaining -> { }, this::start);
        this.hotbar = new LobbyHotbar(plugin, List.of(
                new LobbyHotbar.Choice(MenuIcon.TEAM, messages.get("item.faction"),
                        player -> new TeamMenu(messages, teams, config.playersPerTeam(), this::chooseFaction).open(player)),
                new LobbyHotbar.Choice(MenuIcon.LOADOUT, messages.get("item.class"), this::openClassMenu)),
                statsMenus, countdown);
        this.sidebar = new GameSidebar(messages.get("scoreboard.title"), round);
        this.entrance = new LobbyEntrance(round, countdown, hotbar, () -> Optional.of(lobby),
                () -> Optional.of(phase() == GamePhase.INGAME ? map.spectatorSpawn() : lobby),
                OptionalInt.of(config.fullSeconds()), player -> { });
        this.posts = new Posts(messages, map.posts(), config.captureSeconds());
        this.bolts = new Bolts(this);
        this.effects = new BattleEffects(this);
        this.spectacle = new Spectacle(this);
        this.music = new BattleMusic(this);
        this.bots = new Bots(this, botConfig);
        this.gadgets = new Gadgets(this);
        this.heroes = new Heroes(this, plugin, messages, loadouts, heroConfig);
        this.feel = feel;
        this.combat = new Combat(this, CombatConfig.from(plugin.getConfig()));
        this.speeders = new Speeders(this, SpeederConfig.from(plugin.getConfig()));
        this.spawnSpots = new SpawnSpots(this);
        this.deployment = new Deployment(this, spawnSpots, feel, () -> dirty = true);
        this.battlefield = new Battlefield(this);
        this.support = new SupportRewards(this);
        this.streaks = new KillStreaks(feel.orbitalStreak());
        this.reinforcements = new Reinforcements(config.tickets());
        this.acts = new RoundActs(config.acts(), config.tickets());
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

    public Bots bots() {
        return bots;
    }

    public BattleEffects effects() {
        return effects;
    }

    public Gadgets gadgets() {
        return gadgets;
    }

    public Optional<PriorityPost> front() {
        return front;
    }

    public Battlefield battlefield() {
        return battlefield;
    }

    public Deployment deployment() {
        return deployment;
    }

    public Speeders speeders() {
        return speeders;
    }

    public SupportRewards support() {
        return support;
    }

    public void supportPoints(Player player, int points) {
        heroes.earn(player, points);
        dirty = true;
    }

    public int humans(Faction faction) {
        return (int) round.players().stream().filter(player -> factionOf(player).filter(faction::equals).isPresent()).count();
    }

    public List<LivingEntity> combatants() {
        var field = new ArrayList<LivingEntity>();
        round.players().stream().filter(this::onField).forEach(field::add);
        field.addAll(bots.entities());
        return field;
    }

    public List<LivingEntity> targets() {
        var targets = new ArrayList<>(combatants());
        targets.addAll(speeders.entities());
        return targets;
    }

    public long tick() {
        return tick;
    }

    public Plugin plugin() {
        return plugin;
    }

    public Heroes heroes() {
        return heroes;
    }

    public Optional<Hero> heroOf(Player player) {
        return Optional.ofNullable(fighters.get(player.getUniqueId())).flatMap(Fighter::hero);
    }

    /** Only a fighter who is no hero yet takes the relic. */
    public boolean mayTakeRelic(Player player) {
        return onField(player) && heroOf(player).isEmpty();
    }

    public Optional<Faction> factionOf(Player player) {
        return teams.of(player.getUniqueId());
    }

    public Optional<Faction> factionOf(LivingEntity entity) {
        return entity instanceof Player player ? factionOf(player) : bots.faction(entity).or(() -> speeders.faction(entity));
    }

    /** The player's faction, or before they picked one, the one that would seat them now. */
    public Faction factionOrSmallest(Player player) {
        return factionOf(player).orElseGet(teams::smallest);
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

    Optional<Fighter> fighterIfAny(Player player) {
        return Optional.ofNullable(fighters.get(player.getUniqueId()));
    }

    public CombatClass combatClassOf(LivingEntity actor) {
        return actor instanceof Player player ? fighter(player).combatClass() : bots.role(actor).orElse(CombatClass.SOLDIER);
    }

    /** In the running round, alive and deployed, as opposed to choosing a spawn or watching. */
    public boolean onField(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        return phase() == GamePhase.INGAME && round.isPlaying(player) && fighter != null && fighter.onField() && !player.isDead();
    }

    public boolean onField(LivingEntity entity) {
        return entity instanceof Player player ? onField(player)
                : phase() == GamePhase.INGAME && entity.isValid() && !entity.isDead() && bots.faction(entity).isPresent();
    }

    public boolean choosing(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        return phase() == GamePhase.INGAME && fighter != null && !fighter.onField();
    }

    /** Whether a bolt or blast of {@code faction} can hit the player. */
    public boolean hittable(Faction faction, Player target) {
        return onField(target) && factionOf(target).filter(own -> own != faction).isPresent();
    }

    public boolean hittable(Faction faction, LivingEntity target) {
        return (onField(target) || speeders.faction(target).isPresent()) && factionOf(target).filter(own -> own != faction).isPresent();
    }

    public boolean isProtected(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        return fighter != null && fighter.isProtected(tick);
    }

    public boolean isProtected(LivingEntity entity) {
        return entity instanceof Player player ? isProtected(player) : bots.protectedFromDamage(entity);
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
                deployment.beginChoosing(player);
            }
        } else if (bots.enabled() && phase() == GamePhase.INGAME && round.joinRunning(player)) {
            var faction = Arrays.stream(Faction.values()).min(Comparator.comparingInt(teams::size)).orElseThrow();
            teams.join(player.getUniqueId(), faction);
            roster.put(player.getUniqueId(), faction);
            BlockcraftApi.get().tabTeams().color(player, faction.text());
            PlayerReset.reset(player, GameMode.ADVENTURE);
            config.combat().apply(player);
            deployment.deploy(player, new Spawn.Headquarters());
            bots.fill();
        } else {
            entrance.admit(player);
        }
        heroes.showBars(player);
        heroes.sabers().refresh(player);
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
        music.forget(player);
        botDifficultyVotes.withdraw(player.getUniqueId());
        combat.forget(player);
        support.forget(player);
        strikesReady.remove(player.getUniqueId());
        gadgets.release(player);
        player.leaveVehicle();
        fallen(player, Optional.empty());
        unscope(player);
        deployment.forgetMenu(player);
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
        if (!bots.enabled() && faction.isPresent() && teams.members(faction.get()).isEmpty()) {
            endRound(Optional.of(faction.get().enemy()));
        }
        refreshAll();
    }

    private void leave(Player player) {
        boolean wasPlaying = round.leave(player);
        teams.leave(player.getUniqueId());
        fighters.remove(player.getUniqueId());
        heroes.forget(player);
        BlockcraftApi.get().tabTeams().clearColor(player);
        if (phase() == GamePhase.INGAME && wasPlaying) {
            roster.remove(player.getUniqueId());
            Arrays.stream(Faction.values()).filter(faction -> !bots.enabled() && teams.members(faction).isEmpty()).findFirst()
                    .ifPresent(faction -> endRound(Optional.of(faction.enemy())));
        }
        refreshAll();
    }

    @Override
    public void vanished(Player player) {
        if (phase() == GamePhase.INGAME && round.isPlaying(player)) {
            deployment.reveal(player);
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

    private void openClassMenu(Player player) {
        new ClassMenu(messages, loadouts, factionOrSmallest(player), factionOf(player).isPresent(), () -> fighter(player).combatClass(), this::chooseClass,
                bots.enabled() ? Optional.of(this::openBotDifficulty) : Optional.empty()).open(player);
    }

    private void openBotDifficulty(Player player) {
        if (phase() != GamePhase.LOBBY || !round.isPlaying(player) || !bots.enabled()) {
            VoteMenu.closed(player, messages);
            return;
        }
        var options = Arrays.stream(BotDifficulty.values()).map(difficulty -> new VoteMenu.Option(
                Optional.of(difficulty.key()), messages.get("bot.difficulty." + difficulty.key() + ".name"),
                Items.of(difficulty.icon()).build(), messages.getLines("bot.difficulty." + difficulty.key() + ".lore"),
                botDifficultyVotes.votes(Optional.of(difficulty)))).toList();
        var own = botDifficultyVotes.choiceOf(player.getUniqueId()).map(choice -> choice.map(BotDifficulty::key));
        new VoteMenu(messages.get("bot.difficulty.title"), options, Optional.empty(), own,
                Optional.of(this::openClassMenu), (voter, key) -> {
                    if (phase() != GamePhase.LOBBY || !round.isPlaying(voter)) {
                        VoteMenu.closed(voter, messages);
                        return;
                    }
                    key.flatMap(BotDifficulty::named).ifPresent(difficulty -> {
                        var previous = botDifficultyVotes.cast(voter.getUniqueId(), Optional.of(difficulty));
                        VoteMenu.confirm(voter, messages, messages.get("bot.difficulty." + difficulty.key() + ".name"), previous.isPresent());
                        Menu.close(voter);
                    });
                }).open(player);
    }

    /** In the lobby, or while choosing a spawn, the class the player deploys as next. */
    public void chooseClass(Player player, CombatClass combatClass) {
        if (phase() == GamePhase.ENDING || !round.isPlaying(player)) {
            return;
        }
        var fighter = fighter(player);
        fighter.combatClass(combatClass);
        fighter.choosing().ifPresent(choosing -> fighter.choosing(Optional.of(choosing.pick(Optional.empty()))));
        Sounds.EQUIP.play(player);
        BlockcraftApi.get().notifications().feedback(player, messages.get("class.chosen", className(factionOrSmallest(player), combatClass)));
        deployment.refreshMenu(player);
        dirty = true;
        if (phase() == GamePhase.LOBBY) {
            refreshAll();
        }
    }

    /** While choosing, the hero the player deploys as next, if their battle points and their faction's slots allow. */
    public void chooseHero(Player player, Hero hero) {
        var fighter = fighters.get(player.getUniqueId());
        var faction = factionOf(player);
        if (fighter == null || fighter.choosing().isEmpty() || faction.isEmpty()) {
            return;
        }
        if (!heroes.affordable(player, faction.get(), hero)) {
            BlockcraftApi.get().notifications().feedback(player, messages.get(heroes.open(faction.get(), hero)
                    ? "hero.too-few-points" : "hero.taken", Placeholder.component("hero", heroes.name(hero))));
            Sounds.MENU_DENY.play(player);
            return;
        }
        fighter.choosing(Optional.of(fighter.choosing().get().pick(Optional.of(hero))));
        Sounds.EQUIP.play(player);
        BlockcraftApi.get().notifications().feedback(player, messages.get("hero.chosen", Placeholder.component("hero", heroes.name(hero))));
        deployment.refreshMenu(player);
    }

    private void start() {
        teams.assignRest(round.players().stream().map(Player::getUniqueId).toList()).forEach((id, faction) -> {
            var player = Bukkit.getPlayer(id);
            if (player != null) {
                BlockcraftApi.get().tabTeams().color(player, faction.text());
                player.sendMessage(messages.get("faction.assigned", factionName(faction)));
            }
        });
        if (!bots.enabled() && Arrays.stream(Faction.values()).anyMatch(faction -> teams.size(faction) == 0)) {
            Bukkit.broadcast(messages.get("lobby.no-opponent"));
            countdown.start();
            return;
        }
        round.enter(GamePhase.INGAME);
        tick = 0;
        fieldKills.clear();
        reinforcements = new Reinforcements(config.tickets());
        acts = new RoundActs(config.acts(), config.tickets());
        front = Optional.empty();
        relic = new Relic(this, map.relic(), config.relic().firstSeconds() * TICKS_PER_SECOND);
        posts.show();
        posts.holograms(plugin);
        var difficulties = List.of(BotDifficulty.values());
        boolean voted = difficulties.stream().anyMatch(difficulty -> botDifficultyVotes.votes(Optional.of(difficulty)) > 0);
        var difficulty = voted ? botDifficultyVotes.winner(difficulties, ThreadLocalRandom.current()).orElse(botConfig.difficulty())
                : botConfig.difficulty();
        bots.difficulty(difficulty);
        if (bots.enabled()) {
            Bukkit.broadcast(messages.get("bot.difficulty.selected", Placeholder.component("difficulty",
                    messages.get("bot.difficulty." + difficulty.key() + ".name"))));
        }
        bots.fill();
        speeders.start();
        battlefield.start();
        for (var player : round.players()) {
            var faction = teams.of(player.getUniqueId()).orElseThrow();
            roster.put(player.getUniqueId(), faction);
            PlayerReset.reset(player, GameMode.ADVENTURE);
            config.combat().apply(player);
            deployment.deploy(player, new Spawn.Headquarters());
        }
        round.spectators().forEach(spectator -> spectator.teleport(map.spectatorSpawn()));
        RoundStart.go(round.players());
        for (var faction : Faction.values()) {
            var names = teams.members(faction).stream().map(Bukkit::getPlayer).filter(Objects::nonNull).map(Player::getName).toList();
            Bukkit.broadcast(messages.get("round.faction", factionName(faction), Placeholder.unparsed("players", String.join(", ", names))));
        }
        messages.getLines("round.start").forEach(Bukkit::broadcast);
        if (plugin.getConfig().getBoolean("music.enabled", true)) {
            round.players().forEach(player -> player.sendMessage(messages.get("round.music-credit")));
        }
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
        effects.tick(tick);
        spectacle.tick(tick);
        posts.tick(tick);
        playMusic();
        bots.tick(tick);
        bolts.tick();
        gadgets.tick(tick);
        relic.tick(tick);
        heroes.tick(tick);
        speeders.tick(tick);
        battlefield.tick(tick);
        fireBursts();
        for (var player : round.players()) {
            var fighter = fighters.get(player.getUniqueId());
            if (fighter == null) {
                continue;
            }
            if (fighter.onField()) {
                fighter.heat().cool(tick);
                player.setExp(fighter.heat().level());
                scope(player, fighter);
                combat.tick(player);
            } else {
                deployment.deployWhenDue(player, fighter);
            }
        }
        if (tick % TICKS_PER_SECOND == 0) {
            second();
        }
    }

    private void playMusic() {
        music.tick(tick, secondsLeft, lowestTickets(), acts.act() == RoundActs.Act.LAST_STAND);
    }

    private int lowestTickets() {
        return Math.min(reinforcements.left(Faction.KOLONIEN), reinforcements.left(Faction.HEGEMONIE));
    }

    private void second() {
        var field = new LinkedHashMap<LivingEntity, Faction>();
        combatants().forEach(entity -> factionOf(entity).ifPresent(faction -> field.put(entity, faction)));
        int alertCooldown = Math.clamp(plugin.getConfig().getInt("posts.radio-cooldown-seconds", 15), 1, 120) * 20;
        var update = posts.second(field, tick, alertCooldown);
        update.changes().forEach(this::postChanged);
        update.alerts().forEach(alert -> round.players().stream()
                .filter(player -> factionOf(player).filter(alert.defender()::equals).isPresent()).forEach(player -> {
                    player.sendMessage(messages.get("post.under-attack", posts.name(alert.index())));
                    Sounds.CORE_ALARM.play(player);
                }));
        if ((tick / TICKS_PER_SECOND) % config.bleedSeconds() == 0) {
            reinforcements.bleed(posts.owners(), post -> acts.bleedWeight(posts.priority(post)));
            dirty = true;
        }
        field.forEach(this::guardHeadquarters);
        field.keySet().stream().filter(Player.class::isInstance).map(Player.class::cast).forEach(this::regenerate);
        round.players().forEach(deployment::showChoosing);
        if (phase() == GamePhase.INGAME && reinforcements.defeated().isPresent()) {
            endRound(reinforcements.defeated().map(Faction::enemy));
            return;
        }
        acts.second(lowestTickets(), tick / TICKS_PER_SECOND).ifPresent(this::actBegins);
        if (dirty) {
            dirty = false;
            refreshAll();
        }
    }

    private void actBegins(RoundActs.Act act) {
        switch (act) {
            case SKIRMISH -> { }
            case ASSAULT -> assaultBegins();
            case LAST_STAND -> lastStandBegins();
        }
        dirty = true;
    }

    private void assaultBegins() {
        var picked = PriorityPost.pick(reinforcements, posts.owners());
        front = Optional.of(picked);
        posts.priority(OptionalInt.of(picked.post()));
        bots.reconsider();
        spectacle.assaultBegins(picked.trailing(), spawnSpots.dropshipSpot(picked, 0, 0));
        announceAct("assault", posts.name(picked.post()), factionName(picked.trailing()),
                Formatter.number("weight", config.acts().priorityBleed()));
    }

    /** The last stand aims its comeback at whoever trails now, which need not be who trailed when the assault began. */
    private void lastStandBegins() {
        var trailing = PriorityPost.trailing(reinforcements, posts.owners());
        boolean turned = front.filter(assault -> assault.trailing() != trailing).isPresent();
        front = front.map(assault -> new PriorityPost(trailing, assault.post()));
        if (turned) {
            front.ifPresent(assault -> spectacle.assaultBegins(trailing, spawnSpots.dropshipSpot(assault, 0, 0)));
        }
        posts.priority(OptionalInt.empty());
        heroes.comeback(trailing);
        bots.sightShare(config.acts().stormSightShare());
        playMusic();
        spectacle.lastStandBegins();
        announceAct("last-stand", factionName(trailing), Formatter.number("weight", config.acts().lastStandBleed()),
                Formatter.number("cost", heroes.cost(trailing)));
    }

    private void announceAct(String act, TagResolver... details) {
        var title = Title.title(messages.get("act." + act + ".title"), messages.get("act." + act + ".subtitle", details), BANNER);
        Bukkit.getOnlinePlayers().forEach(player -> BlockcraftApi.get().notifications().important(player, title));
        Bukkit.broadcast(messages.get("act." + act + ".chat", details));
    }

    private void postChanged(Posts.Event event) {
        var name = posts.name(event.index());
        switch (event.change()) {
            case CommandPost.Change.Captured captured -> {
                Bukkit.broadcast(messages.get("post.captured", name, factionName(captured.by())));
                event.present().forEach(player -> {
                    summary.awardCoins(player, config.rewards().capture());
                    heroes.earn(player, heroes.config().points().capture());
                });
                announce(captured.by(), Sounds.NOTIFY, Sounds.CORE_ALARM);
            }
            case CommandPost.Change.Neutralised neutralised -> {
                Bukkit.broadcast(messages.get("post.neutralised", name, factionName(neutralised.from())));
                event.present().forEach(player -> heroes.earn(player, heroes.config().points().neutralise()));
                announce(neutralised.from().enemy(), Sounds.NOTIFY, Sounds.CORE_ALARM);
            }
        }
        dirty = true;
    }

    private void announce(Faction gaining, Sounds forGaining, Sounds forLosing) {
        round.players().forEach(player -> factionOf(player)
                .ifPresent(faction -> (faction == gaining ? forGaining : forLosing).play(player)));
    }

    private void guardHeadquarters(LivingEntity player, Faction faction) {
        var enemyBase = map.headquarters().get(faction.enemy());
        if (enemyBase != null && enemyBase.zone().contains(player.getLocation().toVector())) {
            if (player instanceof Player human) {
                BlockcraftApi.get().notifications().feedback(human, messages.get("headquarters.warning"));
            }
            player.damage(config.headquartersDamage(), DamageSource.builder(DamageType.MAGIC).build());
        }
    }

    private void regenerate(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        if (fighter != null && tick - fighter.lastHurt() >= config.regen().delaySeconds() * TICKS_PER_SECOND) {
            heal(player, config.regen().perSecond());
        }
    }

    /** True if the player had health to gain. Heroes gain none, neither out of combat nor at a med station. */
    public boolean heal(Player player, double amount) {
        return heal((LivingEntity) player, amount);
    }

    public boolean heal(LivingEntity entity, double amount) {
        return heal(entity, amount, Optional.empty());
    }

    public boolean heal(Player healer, LivingEntity entity, double amount) {
        return heal(entity, amount, Optional.of(healer));
    }

    private boolean heal(LivingEntity entity, double amount, Optional<Player> healer) {
        var max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (!onField(entity) || max == null || amount <= 0 || entity.getHealth() >= max.getValue()
                || entity instanceof Player player && heroOf(player).isPresent()) {
            return false;
        }
        double before = entity.getHealth();
        entity.setHealth(Math.min(max.getValue(), before + amount));
        support.healed(entity, healer, entity.getHealth() - before);
        return true;
    }

    public void fireBot(Mob shooter, Faction faction, Blaster blaster, Vector aim, double damageScale, boolean headshots) {
        effects.fire(shooter, faction, blaster, aim, bots.heat(shooter));
        var stats = config.stats(blaster);
        bolts.fire(shooter, faction, blaster, new Blaster.Stats(stats.damage() * damageScale, headshots ? stats.headshot() : 1, stats.heat(),
                stats.cooldownTicks(), stats.bolts(), stats.burstTicks(), stats.spread(), stats.speed(), stats.range()), aim);
        SoundCue.shot(blaster).play(shooter.getEyeLocation(), SHOT_VOLUME);
    }

    public void botDied(LivingEntity victim, Faction faction, Optional<Player> killer, int points) {
        support.killed(victim, killer, points);
        reinforcements.lose(faction, 1);
        killer.filter(this::isPlaying).filter(player -> factionOf(player).filter(own -> own != faction).isPresent()).ifPresent(player -> {
            heroes.earn(player, points);
            fieldKills.merge(player.getUniqueId(), 1, Integer::sum);
            mark(player, "kill", SoundCue.KILL);
        });
        dirty = true;
    }

    public void pullTrigger(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        var faction = factionOf(player);
        var held = fighter == null ? Optional.<Blaster>empty() : faction.flatMap(fighter::blaster);
        if (!onField(player) || held.isEmpty() || faction.isEmpty() || player.isInsideVehicle()) {
            return;
        }
        var blaster = held.get();
        var stats = firing(player, blaster);
        if (!heatAllows(player, fighter, stats)) {
            return;
        }
        fighter.endProtection();
        Bukkit.getPluginManager().callEvent(new ShotEvent(player));
        shoot(player, faction.get(), blaster);
        for (int bolt = 1; bolt < stats.bolts(); bolt++) {
            bursts.add(new Burst(player, blaster, tick + (long) bolt * stats.burstTicks()));
        }
        player.setExp(fighter.heat().level());
    }

    /** The blaster as it fires in {@code shooter}'s hands now, which siege mode changes. */
    public Blaster.Stats firing(LivingEntity shooter, Blaster blaster) {
        var stats = config.stats(blaster);
        return gadgets.sieging(shooter) ? config.siegeMode().apply(stats) : stats;
    }

    /** A vehicle's forward gun shares the rider's heat and experience bar, so boarding cannot skip a vent. */
    public boolean fireCannon(Player rider, Faction faction, Blaster.Stats stats, Location muzzle, Vector nose) {
        var fighter = fighters.get(rider.getUniqueId());
        if (fighter == null || !onField(rider) || !heatAllows(rider, fighter, stats)) {
            return false;
        }
        fighter.endProtection();
        Bukkit.getPluginManager().callEvent(new ShotEvent(rider));
        bolts.fire(rider, faction, Blaster.SPEEDER_CANNON, stats, muzzle, nose);
        SoundCue.shot(Blaster.SPEEDER_CANNON).play(muzzle, SHOT_VOLUME);
        rider.setExp(fighter.heat().level());
        return true;
    }

    private boolean heatAllows(Player player, Fighter fighter, Blaster.Stats stats) {
        return switch (fighter.heat().fire(tick, stats.cooldownTicks(), stats.heat())) {
            case TOO_SOON -> false;
            case VENTING -> {
                BlockcraftApi.get().notifications().feedback(player, messages.get("blaster.venting"));
                yield false;
            }
            case OVERHEATED -> {
                overheated(player);
                yield true;
            }
            case FIRED -> true;
        };
    }

    private void shoot(Player player, Faction faction, Blaster blaster) {
        effects.fire(player, faction, blaster, player.getEyeLocation().getDirection(), fighter(player).heat().level());
        var stats = config.stats(blaster);
        bolts.fire(player, faction, blaster, stats);
        SoundCue.shot(blaster).play(player.getEyeLocation(), SHOT_VOLUME);
    }

    private void fireBursts() {
        bursts.removeIf(burst -> {
            if (burst.at() > tick) {
                return false;
            }
            var fighter = fighters.get(burst.shooter().getUniqueId());
            if (onField(burst.shooter()) && fighter != null) {
                factionOf(burst.shooter()).filter(faction -> fighter.blaster(faction).filter(burst.blaster()::equals).isPresent())
                        .ifPresent(faction -> shoot(burst.shooter(), faction, burst.blaster()));
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

    /** F uses the first ability of a class or a hero, Q the second. */
    public void useAbility(Player player, int index) {
        var fighter = fighters.get(player.getUniqueId());
        if (fighter == null || !onField(player) || player.isInsideVehicle()) {
            return;
        }
        if (fighter.hero().isPresent()) {
            if (fighter.hero().get() == Hero.CHAMPION && index == 0) {
                leap(player);
                return;
            }
            var abilities = fighter.hero().get().abilities();
            if (index < abilities.size()) {
                useHeroAbility(player, abilities.get(index));
            }
            return;
        }
        var abilities = factionOf(player).map(fighter.combatClass()::abilities).orElse(List.of());
        if (index < abilities.size()) {
            useAbility(player, abilities.get(index));
        }
    }

    private void useHeroAbility(Player player, HeroAbility ability) {
        var hero = heroOf(player);
        var faction = factionOf(player);
        if (hero.isEmpty() || faction.isEmpty() || !onField(player) || player.isInsideVehicle() || !hero.get().abilities().contains(ability)) {
            return;
        }
        if (player.hasCooldown(ability.material())) {
            cooling(player, messages.get("hero.ability." + ability.key() + ".name"), player.getCooldown(ability.material()));
            return;
        }
        if (!heroes.powers().use(player, faction.get(), hero.get(), ability, tick)) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("hero.no-target"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        fighter(player).endProtection();
        player.setCooldown(ability.material(), heroes.config().cooldownTicks(ability));
    }

    /** The one orbital strike a kill streak earned: thrown like the Commander's, then gone. */
    public void throwStreakStrike(Player player) {
        var faction = factionOf(player);
        if (faction.isEmpty() || !onField(player) || player.isInsideVehicle()) {
            return;
        }
        if (!strikesReady.remove(player.getUniqueId())) {
            return;
        }
        fighter(player).endProtection();
        heroes.powers().orbital(player, faction.get(), tick);
    }

    private void cooling(Player player, Component ability, int ticks) {
        BlockcraftApi.get().notifications().feedback(player, messages.get("ability.cooldown",
                Placeholder.component("ability", ability), Formatter.number("seconds", (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND)));
    }

    public boolean strikeReady(Player player) {
        return strikesReady.contains(player.getUniqueId());
    }

    public void useAbility(Player player, Ability ability) {
        var fighter = fighters.get(player.getUniqueId());
        var faction = factionOf(player);
        if (fighter == null || faction.isEmpty() || !onField(player) || player.isInsideVehicle() || fighter.hero().isPresent()
                || !fighter.combatClass().abilities(faction.get()).contains(ability)) {
            return;
        }
        if (player.hasCooldown(ability.material())) {
            cooling(player, messages.get(ability.messageKey(faction.get()) + ".name"), player.getCooldown(ability.material()));
            return;
        }
        if (!gadgets.use(player, fighter, faction.get(), ability, tick)) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("ability.no-room"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        if (ability == Ability.GRENADE || ability == Ability.MINING_CHARGE) {
            fighter.endProtection();
        }
        player.setCooldown(ability.material(), config.cooldownTicks(ability));
    }

    /** The second jump, or the champion's F ability, leaps with the blade. */
    public void leap(Player player) {
        var fighter = fighters.get(player.getUniqueId());
        var hero = fighter == null ? Optional.<Hero>empty() : fighter.hero().filter(Hero::bladed);
        if (hero.isEmpty() || !onField(player) || player.isInsideVehicle() || !Combat.holdsBlade(player)) {
            return;
        }
        if (combat.leapCooldown(player) > 0) {
            return;
        }
        var saber = heroes.config().saber();
        boolean champion = hero.get() == Hero.CHAMPION;
        gadgets.leap(player, fighter, champion ? config.relic().leapForward() : saber.leapForward(),
                champion ? config.relic().leapUp() : saber.leapUp(), tick, SoundCue.FORCE_PUSH);
        int seconds = champion ? config.relic().leapCooldownSeconds() : saber.leapCooldownSeconds();
        combat.leapCooldown(player, seconds * (int) TICKS_PER_SECOND);
    }

    public void boltHit(LivingEntity shooter, Blaster blaster, LivingEntity victim, double damage, boolean headshot) {
        if (isProtected(victim)) {
            victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.1);
            return;
        }
        if (victim instanceof Player) {
            lastBolt.put(victim.getUniqueId(), blaster);
        }
        var velocity = victim.getVelocity();
        hurt(shooter, victim, damage, DamageSource.builder(DamageType.ARROW), headshot);
        victim.setVelocity(velocity);
    }

    /** Hurts the victim in {@code by}'s name, so the kill is theirs, and shows them the hit. */
    public boolean strike(LivingEntity by, LivingEntity victim, double damage, DamageType type) {
        if (isProtected(victim)) {
            return false;
        }
        double before = victim.getHealth() + victim.getAbsorptionAmount();
        lastBolt.remove(victim.getUniqueId());
        hurt(by, victim, damage, DamageSource.builder(type), false);
        return victim.getHealth() + victim.getAbsorptionAmount() < before;
    }

    public void blast(LivingEntity thrower, LivingEntity victim, double damage, Location at) {
        if (isProtected(victim)) {
            return;
        }
        lastBolt.remove(victim.getUniqueId());
        var previous = selfExplosion;
        if (thrower.equals(victim)) {
            selfExplosion = victim.getUniqueId();
        }
        try {
            hurt(thrower, victim, damage, DamageSource.builder(DamageType.PLAYER_EXPLOSION).withDamageLocation(at), false);
        } finally {
            selfExplosion = previous;
        }
    }

    /** Hurts everyone {@code blast} reaches from {@code at} with no wall for cover; {@code struck} then gets each of them and the way from {@code at} to them. */
    public void blastAround(Location at, Blast blast, BiConsumer<LivingEntity, Vector> struck) {
        for (var target : targets()) {
            if (!target.getWorld().equals(at.getWorld())) {
                continue;
            }
            var away = target.getLocation().add(0, target.getHeight() / 2, 0).toVector().subtract(at.toVector());
            double distance = away.length();
            boolean own = blast.hurtsOwner() && target.equals(blast.by()) && onField(target);
            if (distance > blast.radius() || isProtected(target) || !own && !hittable(blast.faction(), target) || !LineOfSight.clear(at, away)) {
                continue;
            }
            blast(blast.by(), target, blast.damageAt(distance), at);
            struck.accept(target, away);
        }
    }

    private void hurt(LivingEntity by, LivingEntity victim, double damage, DamageSource.Builder source, boolean headshot) {
        if (!by.equals(victim) && (by instanceof Player player ? player.isOnline() : bots.faction(by).isPresent())) {
            source.withCausingEntity(by).withDirectEntity(by);
        }
        victim.setNoDamageTicks(0);
        dealing = true;
        try {
            victim.damage(damage, source.build());
        } finally {
            dealing = false;
        }
        if (by instanceof Player player && player.isOnline() && !player.equals(victim) && !victim.isDead()) {
            mark(player, headshot ? "headshot" : "hit", headshot ? SoundCue.HEADSHOT : SoundCue.HITMARKER);
        }
    }

    /** A melee hit that landed, such as a lightsaber's swing, marks like a bolt. */
    public void landed(Player attacker, LivingEntity victim) {
        if (!dealing && !victim.isDead() && onField(attacker)) {
            mark(attacker, "hit", SoundCue.HITMARKER);
        }
    }

    /** The hit marker under the crosshair and its sound. */
    private void mark(Player shooter, String kind, SoundCue sound) {
        var title = Title.title(Component.empty(), messages.get("hit." + kind, GameSidebar.icons(shooter)), MARKER);
        BlockcraftApi.get().notifications().reward(shooter, title, null);
        if (sound != null) {
            sound.playTo(shooter, MARKER_VOLUME);
        }
    }

    public void swing(Player player) {
        combat.swing(player);
    }

    public Combat combat() {
        return combat;
    }

    public void deflected(Player champion, Location at) {
        deflected(champion, at, SoundCue.SABER_DEFLECT);
    }

    public void deflected(Player champion, Location at, SoundCue contact) {
        contact.play(at, 1f);
        champion.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 10, 0.2, 0.2, 0.2, 0.2);
    }

    public void relicAppeared() {
        var title = Title.title(messages.get("relic.title"), messages.get("relic.subtitle"), BANNER);
        Bukkit.getOnlinePlayers().forEach(player -> BlockcraftApi.get().notifications().important(player, title));
        Bukkit.broadcast(messages.get("relic.appeared"));
    }

    /** The relic makes its taker a hero of their own: the champion, with the blade, until they fall. */
    public void crown(Player player) {
        var faction = factionOf(player);
        if (faction.isEmpty() || !heroes.enlist(player, faction.get(), Hero.CHAMPION)) {
            return;
        }
        gadgets.release(player);
        Loadouts.natural(player);
        fighter(player).hero(Optional.of(Hero.CHAMPION));
        heroes.equip(player, faction.get(), Hero.CHAMPION, tick);
        Bukkit.broadcast(messages.get("relic.taken", Placeholder.unparsed("player", player.getName()), factionName(faction.get())));
        dirty = true;
    }

    /** A hero fell or left: their slot opens, a champion's relic goes home, and whoever beat them is paid. */
    private void fallen(Player victim, Optional<Player> killer) {
        var fighter = fighters.get(victim.getUniqueId());
        if (fighter == null || fighter.hero().isEmpty()) {
            return;
        }
        var hero = fighter.hero().get();
        fighter.hero(Optional.empty());
        heroes.discharge(victim);
        victim.setGlowing(false);
        var name = Placeholder.unparsed("player", victim.getName());
        if (hero == Hero.CHAMPION) {
            relic.lost(tick, config.relic().respawnSeconds() * TICKS_PER_SECOND);
            killer.ifPresentOrElse(by -> {
                summary.awardCoins(by, config.rewards().champion());
                Bukkit.broadcast(messages.get("relic.slain", name, Placeholder.unparsed("killer", by.getName())));
            }, () -> Bukkit.broadcast(messages.get("relic.lost", name)));
        } else {
            var heroName = Placeholder.component("hero", heroes.name(hero));
            killer.ifPresentOrElse(by -> {
                summary.awardCoins(by, heroes.config().reward());
                Bukkit.broadcast(messages.get("hero.slain", name, heroName, Placeholder.unparsed("killer", by.getName())));
            }, () -> Bukkit.broadcast(messages.get("hero.fallen", name, heroName)));
        }
        dirty = true;
    }

    public void death(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        combat.forget(victim);
        strikesReady.remove(victim.getUniqueId());
        victim.leaveVehicle();
        event.deathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        var blaster = Optional.ofNullable(lastBolt.remove(victim.getUniqueId()));
        if (phase() != GamePhase.INGAME || !round.isPlaying(victim)) {
            return;
        }
        var resolved = summary.resolveDeath(event);
        var source = event.getDamageSource().getCausingEntity();
        var death = victim.getUniqueId().equals(selfExplosion)
                ? new DeathLines.Death(Optional.empty(), messages.get("death.self-grenade", Placeholder.unparsed("player", victim.getName())))
                : source != null && bots.faction(source).isPresent()
                ? new DeathLines.Death(Optional.empty(), messages.get("death.bot", Placeholder.unparsed("player", victim.getName()),
                        Placeholder.component("killer", source.customName() == null ? Component.text(source.getName()) : source.customName())))
                : resolved;
        var line = death.killer().filter(killer -> blaster.isPresent()
                        && event.getDamageSource().getDamageType() == DamageType.ARROW)
                .map(killer -> messages.pick("death.blaster", Placeholder.unparsed("player", victim.getName()),
                        Placeholder.unparsed("killer", killer.getName()),
                        Placeholder.component("weapon", messages.get("blaster." + blaster.orElseThrow().key() + ".name"))))
                .orElse(death.line());
        Bukkit.broadcast(line);
        support.killed(victim, death.killer().filter(killer -> !killer.equals(victim)), heroes.config().points().kill());
        death.killer().filter(killer -> !killer.equals(victim)).ifPresent(killer -> {
            summary.awardKill(killer, victim, Defeat.RESPAWN);
            fieldKills.merge(killer.getUniqueId(), 1, Integer::sum);
            heroes.earn(killer, heroes.config().points().kill());
            mark(killer, "kill", SoundCue.KILL);
            deployment.killedBy(victim, killer);
            if (streaks.kill(killer.getUniqueId())) {
                earnStrike(killer);
            }
        });
        streaks.death(victim.getUniqueId());
        summary.death(victim);
        unscope(victim);
        fallen(victim, death.killer());
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
            deployment.respawned(player);
        } else {
            event.setRespawnLocation(map.spectatorSpawn());
            Tasks.sync(plugin, () -> round.watch(player));
        }
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
        music.finish(won);
        won.ifPresent(faction -> roster.entrySet().stream().filter(entry -> entry.getValue() == faction)
                .map(entry -> Bukkit.getPlayer(entry.getKey())).filter(Objects::nonNull).forEach(summary::awardWin));
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(won.map(faction -> messages.get("round.winner", factionName(faction),
                        Formatter.number("tickets", reinforcements.left(faction))))
                .orElseGet(() -> messages.get("round.draw")));
        Bukkit.broadcast(Component.empty());
        for (var player : Bukkit.getOnlinePlayers()) {
            deployment.reveal(player);
            unscope(player);
            player.setGlowing(false);
            if (!player.isDead()) {
                PlayerReset.reset(player, GameMode.ADVENTURE);
                player.teleport(lobby);
                hotbar.giveHub(player);
            }
        }
        fighters.values().forEach(fighter -> {
            fighter.choosing(Optional.empty());
            fighter.hero(Optional.empty());
        });
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
        effects.clear();
        spectacle.clear();
        music.clear();
        bots.clear();
        combat.clear();
        speeders.clear();
        battlefield.clear();
        support.clear();
        gadgets.clear();
        relic.clear();
        posts.clear();
        heroes.clear();
        streaks.clear();
        strikesReady.clear();
        deployment.clear();
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
                            GameSidebar.value(messages.get(Optional.ofNullable(fighters.get(player.getUniqueId()))
                                    .map(Fighter::combatClass).orElse(CombatClass.SOLDIER).messageKey(factionOrSmallest(player)) + ".name")))));
            case INGAME -> {
                var tickets = Arrays.stream(Faction.values()).map(faction -> messages.get("scoreboard.tickets-line",
                        factionName(faction), Formatter.number("tickets", reinforcements.left(faction)))).toList();
                var postLines = posts.indices().stream().map(this::postLine).toList();
                sidebar.round(player, config.capacity(), secondsLeft, mapName, List.of(
                        new GameSidebar.Section(messages.get("scoreboard.tickets", icons), tickets),
                        new GameSidebar.Section(messages.get("scoreboard.posts", icons), postLines),
                        new GameSidebar.Section(messages.get("scoreboard.kills", icons), List.of(
                                GameSidebar.value(Component.text(fieldKills.getOrDefault(player.getUniqueId(), 0))), battleLine(player)))));
            }
            case ENDING -> sidebar.end(player, winner.map(this::factionDisplay).stream().toList());
        }
    }

    private Component postLine(int index) {
        var line = messages.get(posts.contested(index) ? "scoreboard.post-contested" : "scoreboard.post", posts.name(index),
                Placeholder.styling("tint", posts.owner(index).map(Faction::text).orElse(NamedTextColor.WHITE)));
        return posts.priority(index) ? messages.get("scoreboard.post-priority", Placeholder.component("line", line)) : line;
    }

    /** The player's battle points towards a hero, or the hero they fight as. */
    private Component battleLine(Player player) {
        var hero = heroOf(player);
        if (hero.isPresent()) {
            return GameSidebar.value(messages.get("hero.sidebar.hero", Placeholder.component("hero", heroes.name(hero.get()))));
        }
        int points = heroes.points(player);
        int cost = factionOf(player).map(heroes::cost).orElse(heroes.config().cost());
        return GameSidebar.value(messages.get(points >= cost ? "hero.sidebar.ready" : "hero.sidebar.points",
                Formatter.number("points", points), Formatter.number("cost", cost)));
    }

    /**
     * A kill streak's reward: one orbital strike, thrown with Shift+F, kept until it is thrown or its bearer falls. The
     * round summary announces the streak itself.
     */
    private void earnStrike(Player killer) {
        if (!onField(killer)) {
            return;
        }
        strikesReady.add(killer.getUniqueId());
        BlockcraftApi.get().notifications().reward(killer, messages.get("streak.earned", Formatter.number("kills", feel.orbitalStreak())));
    }

    /** Sneaking with the long rifle slows the walk, which the client draws as a zoom; anything else lets go of it. */
    private void scope(Player player, Fighter fighter) {
        var speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean aiming = player.isSneaking() && factionOf(player).flatMap(fighter::blaster).filter(Blaster::precision).isPresent()
                && HotbarItem.of(player.getInventory().getItemInMainHand(), Blaster.class).filter(Blaster::precision).isPresent();
        boolean scoped = speed.getModifier(SCOPE) != null;
        if (aiming && !scoped) {
            AttributeScale.apply(player, Attribute.MOVEMENT_SPEED, SCOPE, -feel.scopeSlowdown());
        } else if (!aiming && scoped) {
            AttributeScale.remove(player, Attribute.MOVEMENT_SPEED, SCOPE);
        }
    }

    static void unscope(Player player) {
        if (player.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            AttributeScale.remove(player, Attribute.MOVEMENT_SPEED, SCOPE);
        }
    }

    private Component factionDisplay(Faction faction) {
        return messages.get("faction." + faction.key() + ".name").colorIfAbsent(faction.text());
    }

    TagResolver factionName(Faction faction) {
        return Placeholder.component("faction", factionDisplay(faction));
    }

    private TagResolver className(Faction faction, CombatClass combatClass) {
        return Placeholder.component("class", messages.get(combatClass.messageKey(faction) + ".name"));
    }
}
