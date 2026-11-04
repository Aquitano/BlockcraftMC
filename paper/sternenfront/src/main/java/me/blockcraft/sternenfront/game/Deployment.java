package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.sternenfront.config.FeelConfig;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.menu.DeployMenu;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The way back onto the field: the kill cam, the wait above the map with the deploy menu open, and the deployment. */
public final class Deployment {

    private static final long TICKS_PER_SECOND = 20;
    private static final int DEPLOY_SLOT = 4;
    private static final long KILLER_FOCUS_TICKS = 2;

    private final SternenfrontGame game;
    private final SpawnSpots spawnSpots;
    private final FeelConfig feel;
    private final BattlePresentation presentation;
    private final Runnable sidebarChanged;
    private final Map<UUID, DeployMenu> deployMenus = new HashMap<>();
    /** Whose kill a dead player watches before they choose where to deploy. */
    private final Map<UUID, UUID> killers = new HashMap<>();

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
        return new Fighter.Choosing(game.tick() + respawn.seconds() * TICKS_PER_SECOND,
                game.tick() + respawn.autoSeconds() * TICKS_PER_SECOND, Optional.empty(), Optional.empty());
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
        var fighter = game.fighterIfAny(player);
        var faction = game.factionOf(player);
        if (fighter.isEmpty() || fighter.get().choosing().isEmpty() || faction.isEmpty()) {
            return;
        }
        if (!available(faction.get(), spawn)) {
            Sounds.MENU_DENY.play(player);
            return;
        }
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
            case Spawn.Post post -> post.index() < game.posts().size() && game.posts().safe(post.index(), faction);
            case Spawn.Dropship ignored -> game.front().filter(assault -> assault.trailing() == faction).isPresent();
        };
    }

    /** Ticks until the player may deploy, 0 once they may. */
    public long waitTicks(Player player) {
        return game.fighterIfAny(player).flatMap(Fighter::choosing)
                .map(choosing -> Math.max(0, choosing.readyTick() - game.tick())).orElse(0L);
    }

    void deployWhenDue(Player player, Fighter fighter) {
        fighter.choosing().ifPresent(choosing -> {
            if (game.tick() >= choosing.autoTick()) {
                deploy(player, choosing.chosen().filter(spawn -> game.factionOf(player).filter(faction -> available(faction, spawn)).isPresent())
                        .orElse(new Spawn.Headquarters()));
            } else if (game.tick() >= choosing.readyTick() && choosing.chosen().isPresent()) {
                deploy(player, choosing.chosen().get());
            }
        });
    }

    void showChoosing(Player player) {
        var fighter = game.fighterIfAny(player);
        if (fighter.isEmpty() || fighter.get().choosing().isEmpty() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        var choosing = fighter.get().choosing().get();
        long waiting = choosing.readyTick() - game.tick();
        var status = waiting > 0
                ? game.messages().get("deploy.waiting", Formatter.number("seconds", seconds(waiting)))
                : game.messages().get("deploy.ready", Formatter.number("seconds", seconds(choosing.autoTick() - game.tick())));
        BlockcraftApi.get().notifications().status(player, status);
        refreshMenu(player);
    }

    void refreshMenu(Player player) {
        Optional.ofNullable(deployMenus.get(player.getUniqueId())).ifPresent(DeployMenu::refresh);
    }

    private static long seconds(long ticks) {
        return Math.max(0, (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }

    void deploy(Player player, Spawn wanted) {
        if (game.intro().running() || game.phase() != GamePhase.INGAME || !game.isPlaying(player)
                || !player.isOnline() || player.isDead()) return;
        if (deployMenus.remove(player.getUniqueId()) != null) Menu.close(player);
        presentation.deploy(player, () -> deployNow(player, wanted));
    }

    private void deployNow(Player player, Spawn wanted) {
        var faction = game.factionOf(player);
        if (game.intro().running() || faction.isEmpty() || !player.isOnline() || player.isDead()
                || game.phase() != GamePhase.INGAME || !game.isPlaying(player)) {
            return;
        }
        var messages = game.messages();
        var config = game.config();
        var heroes = game.heroes();
        var map = game.map();
        game.combat().forget(player);
        player.leaveVehicle();
        var spawn = available(faction.get(), wanted) ? wanted : new Spawn.Headquarters();
        if (!spawn.equals(wanted)) {
            player.sendMessage(messages.get("deploy.lost"));
        }
        var spot = switch (spawn) {
            case Spawn.Headquarters ignored -> spawnSpots.farthestFromEnemies(map.headquarters().get(faction.get()).spawns(), faction.get());
            case Spawn.Post post -> spawnSpots.farthestFromEnemies(map.posts().get(post.index()).spawns(), faction.get());
            case Spawn.Dropship ignored -> spawnSpots.leastCrowded(spawnSpots.dropshipSpots(game.front().orElseThrow()));
        };
        var fighter = game.fighter(player);
        var picked = fighter.choosing().flatMap(Fighter.Choosing::hero);
        var hero = picked.filter(chosen -> heroes.enlist(player, faction.get(), chosen));
        if (picked.isPresent() && hero.isEmpty()) {
            player.sendMessage(messages.get("hero.missed", Placeholder.component("hero", heroes.name(picked.get()))));
        }
        if (deployMenus.remove(player.getUniqueId()) != null) {
            Menu.close(player);
        }
        reveal(player);
        PlayerReset.reset(player, GameMode.ADVENTURE);
        config.combat().apply(player);
        player.teleport(spot);
        fighter.deployed(config.heat(), game.tick() + config.respawn().protectionSeconds() * TICKS_PER_SECOND, hero);
        fighter.hurt(game.tick());
        hero.ifPresentOrElse(chosen -> {
            heroes.equip(player, faction.get(), chosen, game.tick());
            Bukkit.broadcast(messages.get("hero.entered", game.factionName(faction.get()), Placeholder.component("hero", heroes.name(chosen)),
                    Placeholder.unparsed("player", player.getName())));
            Bukkit.getOnlinePlayers().forEach(Sounds.NOTIFY::play);
        }, () -> game.loadouts().equip(player, faction.get(), fighter.combatClass()));
        player.getInventory().setHeldItemSlot(0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        Sounds.TELEPORT.play(player);
        var abilities = hero.map(chosen -> chosen.abilities().stream()
                .map(ability -> messages.get("hero.ability." + ability.key() + ".name")).toList())
                .orElseGet(() -> fighter.combatClass().abilities(faction.get()).stream()
                        .map(ability -> messages.get(ability.messageKey(faction.get()) + ".name")).toList());
        player.sendMessage(messages.get("deploy.controls",
                Placeholder.component("first", abilities.isEmpty() ? messages.get("deploy.no-ability") : abilities.getFirst()),
                Placeholder.component("second", abilities.size() < 2 ? messages.get("deploy.no-ability") : abilities.get(1))));
        if (hero.filter(chosen -> chosen.weapon() instanceof Hero.Weapon.Saber).isPresent()) {
            player.sendMessage(messages.get("deploy.saber-controls"));
        }
        presentation.deployed(player, spawn);
        sidebarChanged.run();
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

    void forgetMenu(Player player) {
        deployMenus.remove(player.getUniqueId());
    }

    void clear() {
        killers.clear();
        deployMenus.clear();
    }
}
