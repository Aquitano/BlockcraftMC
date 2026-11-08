package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.sternenfront.config.FeelConfig;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;
import me.blockcraft.sternenfront.reinforcement.ReinforcementRoster;
import me.blockcraft.sternenfront.menu.DeployMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** The way back onto the field: the kill cam, the wait above the map with the deploy menu open, and the deployment. */
public final class Deployment {

    private static final long TICKS_PER_SECOND = 20;
    private static final int DEPLOY_SLOT = 4;
    private static final long KILLER_FOCUS_TICKS = 2;
    /** The keys a role's first and second ability sit on. */
    public static final List<String> CONTROL_KEYS = List.of("F", "Q");

    private final SternenfrontGame game;
    private final SpawnSpots spawnSpots;
    private final FeelConfig feel;
    private final BattlePresentation presentation;
    private final Runnable sidebarChanged;
    private final Map<UUID, DeployMenu> deployMenus = new HashMap<>();
    /** Whose kill a dead player watches before they choose where to deploy. */
    private final Map<UUID, UUID> killers = new HashMap<>();
    /** The abilities each player was last told about, so the chat explains them again only when they change. */
    private final Map<UUID, List<String>> controls = new HashMap<>();
    private final Map<UUID, Spawn> remembered = new HashMap<>();

    Deployment(SternenfrontGame game, SpawnSpots spawnSpots, FeelConfig feel, BattlePresentation presentation, Runnable sidebarChanged) {
        this.game = game;
        this.spawnSpots = spawnSpots;
        this.feel = feel;
        this.presentation = presentation;
        this.sidebarChanged = sidebarChanged;
    }

    void killedBy(Player victim, Player killer) {
        killers.put(victim.getUniqueId(), killer.getUniqueId());
    }

    void respawned(Player player) {
        var killer = Optional.ofNullable(killers.remove(player.getUniqueId())).map(Bukkit::getPlayer)
                .filter(game::onField).filter(ignored -> feel.killCamTicks() > 0);
        Tasks.sync(game.plugin(), () -> killer.ifPresentOrElse(by -> watchKiller(player, by), () -> beginChoosing(player)));
    }

    /**
     * The kill cam: the dead player first sees the round through their killer's eyes for a moment, already choosing,
     * so the wait for their next deploy runs meanwhile.
     */
    private void watchKiller(Player player, Player killer) {
        if (!player.isOnline() || game.phase() != GamePhase.INGAME || !game.isPlaying(player)) {
            return;
        }
        var fighter = game.fighter(player);
        fighter.choosing(Optional.of(newChoosing()));
        PlayerReset.reset(player, GameMode.SPECTATOR);
        player.teleport(killer.getLocation());
        BlockcraftApi.get().notifications().status(player, game.messages().get("killcam.watching", Placeholder.unparsed("killer", killer.getName())));
        Tasks.later(game.plugin(), KILLER_FOCUS_TICKS, () -> {
            if (player.getGameMode() == GameMode.SPECTATOR && killer.isValid()) {
                player.setSpectatorTarget(killer);
            }
        });
        Tasks.later(game.plugin(), feel.killCamTicks(), () -> {
            if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR && game.choosing(player)) {
                player.setSpectatorTarget(null);
                beginChoosing(player);
            }
        });
    }

    private Fighter.Choosing newChoosing() {
        var respawn = game.config().respawn();
        return Fighter.Choosing.begin(game.tick() + respawn.seconds() * TICKS_PER_SECOND,
                game.tick() + respawn.autoSeconds() * TICKS_PER_SECOND);
    }

    void rejoined(Player player) {
        game.fighter(player).choosing(Optional.of(newChoosing()));
        beginChoosing(player);
    }

    /** Dead and watching from above, unseen and unhurt, with the deploy menu open. */
    void beginChoosing(Player player) {
        if (!player.isOnline() || game.phase() != GamePhase.INGAME || !game.isPlaying(player)) {
            return;
        }
        var fighter = game.fighter(player);
        SternenfrontGame.unscope(player);
        PlayerReset.reset(player, GameMode.ADVENTURE);
        player.teleport(game.map().overview());
        player.setGlowing(false);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        Bukkit.getOnlinePlayers().stream().filter(other -> !other.equals(player)).forEach(other -> other.hidePlayer(game.plugin(), player));
        fighter.choosing(Optional.of(fighter.choosing().orElseGet(this::newChoosing)));
        player.getInventory().setItem(DEPLOY_SLOT, game.loadouts().deployItem());
        player.getInventory().setHeldItemSlot(DEPLOY_SLOT);
        openDeploy(player);
        showChoosing(player);
    }

    public void openDeploy(Player player) {
        if (!game.choosing(player)) {
            return;
        }
        var menu = new DeployMenu(game, player);
        deployMenus.put(player.getUniqueId(), menu);
        menu.open(player);
    }

    /** A spawn chosen in the deploy menu: taken at once when the wait is over, else as soon as it is. */
    public void choose(Player player, Spawn spawn) {
        choose(player, spawn, true);
    }

    private void choose(Player player, Spawn spawn, boolean remember) {
        var fighter = game.fighterIfAny(player);
        var faction = game.factionOf(player);
        if (fighter.isEmpty() || fighter.get().choosing().isEmpty() || faction.isEmpty()) {
            return;
        }
        if (!available(faction.get(), spawn)) {
            Sounds.MENU_DENY.play(player);
            return;
        }
        if (remember) remembered.put(player.getUniqueId(), spawn);
        var choosing = fighter.get().choosing().get().choose(spawn);
        fighter.get().choosing(Optional.of(choosing));
        if (game.tick() >= choosing.readyTick()) {
            deploy(player, spawn);
        } else {
            showChoosing(player);
            refreshMenu(player);
        }
    }

    public boolean available(Faction faction, Spawn spawn) {
        return switch (spawn) {
            case Spawn.Headquarters ignored -> true;
            case Spawn.Post post -> post.index() >= 0 && post.index() < game.posts().size()
                    && game.posts().owner(post.index()).filter(faction::equals).isPresent()
                    && safePost(faction, post.index());
            case Spawn.Dropship ignored -> game.front().filter(assault -> assault.trailing() == faction).isPresent();
        };
    }

    private boolean safePost(Faction faction, int index) {
        var site = game.map().posts().get(index);
        var enemies = game.combatants().stream().filter(other -> game.factionOf(other).filter(faction.enemy()::equals).isPresent())
                .map(org.bukkit.entity.LivingEntity::getLocation).toList();
        double clearance = Math.clamp(game.plugin().getConfig().getDouble("respawn.enemy-clearance"), 0, 64);
        return enemies.stream().noneMatch(site::contains) && SpawnSpots.hasClearSpawn(site.spawns(), enemies, clearance);
    }

    /** Seconds until the player may deploy, 0 once they may. */
    public long waitSeconds(Player player) {
        return game.fighterIfAny(player).flatMap(Fighter::choosing).map(choosing -> seconds(choosing.readyTick() - game.tick())).orElse(0L);
    }

    /** Seconds until the player deploys without confirming. */
    public long autoSeconds(Player player) {
        return game.fighterIfAny(player).flatMap(Fighter::choosing).map(choosing -> seconds(choosing.autoTick() - game.tick())).orElse(0L);
    }

    /** When the player deploys: after the wait once confirmed, else when they may or when the timer deploys them. */
    public Component status(Player player) {
        var messages = game.messages();
        var choosing = game.fighterIfAny(player).flatMap(Fighter::choosing);
        long wait = waitSeconds(player);
        return choosing.filter(Fighter.Choosing::confirmed).isPresent()
                ? messages.get("deploy.status-queued", Formatter.number("seconds", wait)) : wait > 0
                ? messages.get("deploy.status-waiting", Formatter.number("seconds", wait))
                : messages.get("deploy.status-ready", Formatter.number("seconds", autoSeconds(player)));
    }

    public Spawn recommended(Player player) {
        var faction = game.factionOf(player).orElseThrow();
        return BattlePlan.recommend(plannedPosts(faction), headquarters(faction), headquarters(faction.enemy()),
                Optional.ofNullable(remembered.get(player.getUniqueId())), spawn -> available(faction, spawn));
    }

    public Spawn suggested(Player player) {
        var faction = game.factionOf(player).orElseThrow();
        return game.fighter(player).choosing().flatMap(Fighter.Choosing::chosen)
                .filter(spawn -> available(faction, spawn)).orElseGet(() -> recommended(player));
    }

    public void quickDeploy(Player player) {
        if (game.choosing(player)) choose(player, suggested(player), false);
    }

    /** The post the minimap marks for the player: the assault's priority, the nearest one to take, or the one to hold. */
    public OptionalInt postToHeadFor(Player player) {
        return game.factionOf(player).flatMap(faction -> BattlePlan.postToHeadFor(plannedPosts(faction), point(player.getLocation()),
                headquarters(faction.enemy()))).map(target -> OptionalInt.of(target.index())).orElseGet(OptionalInt::empty);
    }

    private List<BattlePlan.Post> plannedPosts(Faction faction) {
        return game.posts().indices().stream().map(index -> new BattlePlan.Post(index, point(game.map().posts().get(index).center()),
                available(faction, new Spawn.Post(index)), game.posts().priority(index))).toList();
    }

    private BattlePlan.Point headquarters(Faction faction) {
        var center = game.map().headquarters().get(faction).zone().getCenter();
        return new BattlePlan.Point(center.getX(), center.getZ());
    }

    private static BattlePlan.Point point(Location location) {
        return new BattlePlan.Point(location.getX(), location.getZ());
    }

    void deployWhenDue(Player player, Fighter fighter) {
        fighter.choosing().ifPresent(choosing -> {
            if (choosing.manualReady(game.tick())) {
                deploy(player, choosing.chosen().orElseThrow());
            } else if (game.tick() >= choosing.autoTick()) {
                deploy(player, suggested(player));
            }
        });
    }

    void showChoosing(Player player) {
        var fighter = game.fighterIfAny(player);
        if (fighter.isEmpty() || fighter.get().choosing().isEmpty() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        BlockcraftApi.get().notifications().status(player, status(player));
        refreshMenu(player);
    }

    void refreshMenu(Player player) {
        Optional.ofNullable(deployMenus.get(player.getUniqueId())).ifPresent(DeployMenu::refresh);
    }

    private static long seconds(long ticks) {
        return Math.max(0, (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }

    void arrive(Player player, Spawn wanted) {
        deploy(player, wanted, true);
    }

    void deploy(Player player, Spawn wanted) {
        deploy(player, wanted, false);
    }

    private void deploy(Player player, Spawn wanted, boolean arrival) {
        if (game.intro().running() || game.phase() != GamePhase.INGAME || !game.isPlaying(player)
                || !player.isOnline() || player.isDead()) return;
        if (deployMenus.remove(player.getUniqueId()) != null) Menu.close(player);
        if (arrival) presentation.arrive(player, () -> deployNow(player, wanted));
        else presentation.deploy(player, () -> deployNow(player, wanted));
    }

    private void deployNow(Player player, Spawn wanted) {
        var faction = game.factionOf(player);
        if (game.intro().running() || faction.isEmpty() || !player.isOnline() || player.isDead()
                || game.phase() != GamePhase.INGAME || !game.isPlaying(player)) {
            return;
        }
        var messages = game.messages();
        var map = game.map();
        game.combat().forget(player);
        player.leaveVehicle();
        var spawn = BattlePlan.resolve(wanted, candidate -> available(faction.get(), candidate), recommended(player));
        if (!spawn.equals(wanted)) {
            player.sendMessage(messages.get("deploy.lost"));
        }
        var spot = switch (spawn) {
            case Spawn.Headquarters ignored -> spawnSpots.farthestFromEnemies(map.headquarters().get(faction.get()).spawns(), faction.get());
            case Spawn.Post post -> spawnSpots.farthestFromEnemies(map.posts().get(post.index()).spawns(), faction.get());
            case Spawn.Dropship ignored -> spawnSpots.leastCrowded(spawnSpots.dropshipSpots(game.front().orElseThrow()));
        };
        var picked = game.fighter(player).choosing().flatMap(Fighter.Choosing::confirmedReinforcement);
        if (picked.isPresent()) {
            var unit = picked.get();
            var result = game.reinforcementUnits().roster().deploy(player.getUniqueId(), faction.get(), unit,
                    () -> enter(player, faction.get(), spot, spawn, picked));
            if (result == ReinforcementRoster.Deployment.DEPLOYED) {
                player.sendMessage(messages.get("reinforcement.entered", Placeholder.component("unit", game.reinforcementUnits().name(unit))));
                return;
            }
            if (result == ReinforcementRoster.Deployment.FAILED) {
                deploymentFailed(player);
                return;
            }
            player.sendMessage(messages.get("reinforcement.missed", Placeholder.component("unit", game.reinforcementUnits().name(unit))));
        }
        if (!enter(player, faction.get(), spot, spawn, Optional.empty())) {
            deploymentFailed(player);
        }
    }

    private void deploymentFailed(Player player) {
        if (!player.isOnline() || game.phase() != GamePhase.INGAME || !game.isPlaying(player)) return;
        var fighter = game.fighter(player);
        fighter.choosing(Optional.of(fighter.choosing().orElseGet(this::newChoosing)
                .retry(game.tick() + game.config().respawn().autoSeconds() * TICKS_PER_SECOND)));
        player.sendMessage(game.messages().get("reinforcement.deployment-failed"));
        beginChoosing(player);
    }

    private boolean enter(Player player, Faction faction, Location spot, Spawn spawn, Optional<Reinforcement> unit) {
        if (!player.teleport(spot) || game.phase() != GamePhase.INGAME || !game.isPlaying(player) || !player.isOnline()) {
            return false;
        }
        var messages = game.messages();
        var config = game.config();
        var heroes = game.heroes();
        var fighter = game.fighter(player);
        var picked = fighter.choosing().flatMap(Fighter.Choosing::confirmedHero);
        var hero = picked.filter(chosen -> heroes.enlist(player, faction, chosen));
        if (picked.isPresent() && hero.isEmpty()) {
            player.sendMessage(messages.get("hero.missed", Placeholder.component("hero", heroes.name(picked.get()))));
        }
        if (deployMenus.remove(player.getUniqueId()) != null) {
            Menu.close(player);
        }
        reveal(player);
        PlayerReset.reset(player, GameMode.ADVENTURE);
        config.combat().apply(player);
        fighter.deployed(config.heat(), game.tick() + config.respawn().protectionSeconds() * TICKS_PER_SECOND, hero);
        fighter.reinforcement(unit);
        fighter.hurt(game.tick());
        hero.ifPresentOrElse(chosen -> {
            heroes.equip(player, faction, chosen, game.tick());
            Bukkit.broadcast(messages.get("hero.entered", game.factionName(faction), Placeholder.component("hero", heroes.name(chosen)),
                    Placeholder.unparsed("player", player.getName())));
            Bukkit.getOnlinePlayers().forEach(Sounds.NOTIFY::play);
        }, () -> unit.ifPresentOrElse(chosen -> game.reinforcementUnits().equip(player, chosen),
                () -> game.loadouts().equip(player, faction, fighter.combatClass())));
        player.getInventory().setHeldItemSlot(0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        Sounds.TELEPORT.play(player);
        controlsChanged(player);
        if (hero.filter(chosen -> chosen.weapon() instanceof Hero.Weapon.Saber).isPresent()) {
            player.sendMessage(messages.get("deploy.saber-controls"));
        }
        presentation.deployed(player, spawn);
        sidebarChanged.run();
        return true;
    }

    public void controlsChanged(Player player) {
        if (!game.onField(player)) return;
        var fighter = game.fighter(player);
        var faction = game.factionOf(player);
        if (faction.isEmpty()) return;
        var keys = fighter.hero().map(chosen -> chosen.abilities().stream().map(ability -> "hero.ability." + ability.key()).toList())
                .orElseGet(() -> fighter.abilities(faction.get()).stream().map(ability -> ability.messageKey(faction.get())).toList());
        var previous = controls.put(player.getUniqueId(), keys);
        if (keys.equals(previous)) return;
        var messages = game.messages();
        for (int index = 0; index < Math.min(keys.size(), CONTROL_KEYS.size()); index++) {
            var key = keys.get(index);
            player.sendMessage(messages.get("deploy.controls", Placeholder.unparsed("key", CONTROL_KEYS.get(index)),
                    Placeholder.component("name", messages.get(key + ".name")),
                    Placeholder.component("ability", messages.get("combat.hints." + key.substring(key.lastIndexOf('.') + 1)))));
        }
        if (previous == null) {
            player.sendMessage(messages.get("deploy.target"));
        }
    }

    /** Back to normal: seen by everyone, hurtable, on foot, at their own size and pace. */
    void reveal(Player player) {
        game.gadgets().release(player);
        Loadouts.natural(player);
        player.setInvulnerable(false);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setCollidable(true);
        Bukkit.getOnlinePlayers().stream().filter(other -> !other.equals(player)).forEach(other -> other.showPlayer(game.plugin(), player));
    }

    void forget(Player player) {
        deployMenus.remove(player.getUniqueId());
        killers.remove(player.getUniqueId());
    }

    void clear() {
        killers.clear();
        deployMenus.clear();
        remembered.clear();
        controls.clear();
    }
}
