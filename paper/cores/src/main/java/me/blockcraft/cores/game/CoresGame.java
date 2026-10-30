package me.blockcraft.cores.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.coins.Coins;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.LobbyHotbar;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.game.RoundStart;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.cores.arena.CoresMap;
import me.blockcraft.cores.config.CoresConfig;
import me.blockcraft.cores.menu.TeamMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One server, one round of Cores, as GommeHD played it: red against blue, two cores each. Players pick a team in
 * the lobby; in the round they respawn without end, mine and craft their gear from what the map holds, and the
 * first team to break every enemy core wins. An enemy near a core sounds its team's alarm and mines it slowly.
 */
public final class CoresGame {

    private static final long RESPAWN_TICKS = 10L;
    private static final long WATCH_TICKS = 10L;
    private static final float RESPAWN_SATURATION = 10.0f;
    private static final Duration ALARM_HOLD = Duration.ofSeconds(2);
    private static final int FATIGUE_TICKS = 30;
    private static final Title.Times CORE_TITLE = Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500));

    private final Plugin plugin;
    private final CoresConfig config;
    private final Messages messages;
    private final CoresMap map;
    private final Teams teams;
    private final Kit kit;
    private final RoundSummary summary;
    private final Round round;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final GameSidebar sidebar;
    /** Who started the round for which team; a win counts for those of them still on the server. */
    private final Map<UUID, Side> roster = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private BukkitTask watch;
    private Optional<Side> winner = Optional.empty();
    private int secondsLeft;

    public CoresGame(Plugin plugin, GameConfig gameConfig, CoresConfig config, Messages messages, CoresMap map,
                     StatsRepository stats, StatsMenus statsMenus) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.map = map;
        this.teams = new Teams(config.playersPerTeam());
        this.kit = new Kit(config.kit());
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(config.killCoins(), config.winCoins()), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.capacity(), gameConfig);
        this.countdown = new LobbyCountdown(plugin, round, gameConfig, this::start);
        this.hotbar = new LobbyHotbar(plugin, List.of(new LobbyHotbar.Choice(MenuIcon.TEAM, messages.get("item.team"),
                player -> new TeamMenu(messages, teams, config.playersPerTeam(), this::choose).open(player))),
                statsMenus, countdown);
        this.sidebar = new GameSidebar(messages.get("scoreboard.title"), round);
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

    public CoresMap map() {
        return map;
    }

    public int buildHeight() {
        return config.buildHeight();
    }

    public int voidHeight() {
        return config.voidHeight();
    }

    public boolean sameTeam(Player one, Player other) {
        return teams.sameSide(one.getUniqueId(), other.getUniqueId());
    }

    public Optional<Side> sideOf(Player player) {
        return teams.of(player.getUniqueId());
    }

    public List<Player> members(Side side) {
        return teams.members(side).stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    public boolean isPlaying(Player player) {
        return round.isPlaying(player);
    }

    /** Freshly respawned players cannot be hurt by other players for a few seconds, or until they hit someone. */
    public boolean isSpawnProtected(Player player) {
        var until = protectedUntil.get(player.getUniqueId());
        return until != null && System.nanoTime() - until < 0;
    }

    public void endSpawnProtection(Player player) {
        protectedUntil.remove(player.getUniqueId());
    }

    public void join(Player player) {
        if (round.rejoin(player)) {
            rejoined(player);
        } else if (round.join(player)) {
            PlayerReset.reset(player, GameMode.ADVENTURE);
            player.teleport(map.lobbySpawn());
            hotbar.give(player);
            player.sendMessage(messages.get("join.introduction"));
            countdown.start();
            if (round.playerCount() >= config.capacity()) {
                countdown.shorten(config.fullSeconds());
            }
        } else {
            player.teleport(phase() == GamePhase.INGAME ? map.spectatorSpawn() : map.lobbySpawn());
        }
        refreshAll();
    }

    public void follow(Player spectator, UUID friend) {
        round.follow(spectator, friend);
    }

    /** Back within the time the round kept their place: at the side's spawn with the kit, as after a death. */
    private void rejoined(Player player) {
        if (player.isDead()) {
            round.respawn(player, 1L);
            return;
        }
        teams.of(player.getUniqueId()).ifPresent(side -> {
            player.teleport(map.spawn(side));
            player.setGameMode(GameMode.SURVIVAL);
            config.combat().apply(player);
            dress(player, side);
            kit.give(player, side);
            protectedUntil.put(player.getUniqueId(), System.nanoTime() + Duration.ofSeconds(config.protectionSeconds()).toNanos());
        });
    }

    /** A player who drops out of the running round keeps their place for a while. */
    public void quit(Player player) {
        if (round.stepOut(player, this::forfeited)) {
            protectedUntil.remove(player.getUniqueId());
            refreshAll();
            return;
        }
        leave(player);
    }

    private void forfeited(UUID player) {
        roster.remove(player);
        var side = teams.leave(player);
        if (side.isPresent() && teams.members(side.get()).isEmpty()) {
            endRound(Optional.of(side.get().enemy()));
        }
        refreshAll();
    }

    private void leave(Player player) {
        boolean wasPlaying = round.leave(player);
        teams.leave(player.getUniqueId());
        BlockcraftApi.get().tabTeams().clearColor(player);
        protectedUntil.remove(player.getUniqueId());
        if (phase() == GamePhase.INGAME && wasPlaying) {
            roster.remove(player.getUniqueId());
            for (var side : Side.values()) {
                if (members(side).isEmpty()) {
                    endRound(Optional.of(side.enemy()));
                    break;
                }
            }
        }
        refreshAll();
    }

    public void vanished(Player player) {
        if (phase() == GamePhase.INGAME && round.isPlaying(player)) {
            leave(player);
            round.watch(player);
        } else if (phase() == GamePhase.LOBBY) {
            teams.leave(player.getUniqueId());
            BlockcraftApi.get().tabTeams().clearColor(player);
            refreshAll();
        }
    }

    private void choose(Player player, Side side) {
        if (phase() != GamePhase.LOBBY || !round.isPlaying(player)) {
            return;
        }
        switch (teams.join(player.getUniqueId(), side)) {
            case ALREADY -> player.sendMessage(messages.get("team.already"));
            case FULL -> {
                player.sendMessage(messages.get("team.full"));
                Sounds.MENU_DENY.play(player);
            }
            case JOINED -> {
                Sounds.EQUIP.play(player);
                dress(player, side);
                player.sendMessage(messages.get("team.joined", teamName(side)));
                refreshAll();
            }
        }
    }

    private void start() {
        teams.assignRest(round.players().stream().map(Player::getUniqueId).toList()).forEach((id, side) -> {
            var player = Bukkit.getPlayer(id);
            if (player != null) {
                dress(player, side);
                player.sendMessage(messages.get("team.assigned", teamName(side)));
            }
        });
        if (Arrays.stream(Side.values()).anyMatch(side -> teams.size(side) == 0)) {
            Bukkit.broadcast(messages.get("lobby.no-opponent"));
            countdown.start();
            return;
        }
        round.enter(GamePhase.INGAME);
        for (var player : round.players()) {
            var side = teams.of(player.getUniqueId()).orElseThrow();
            roster.put(player.getUniqueId(), side);
            PlayerReset.reset(player, GameMode.SURVIVAL);
            config.combat().apply(player);
            kit.give(player, side);
            player.teleport(map.spawn(side));
        }
        round.spectators().forEach(spectator -> spectator.teleport(map.spectatorSpawn()));
        RoundStart.go(round.players());
        for (var side : Side.values()) {
            var names = members(side).stream().map(Player::getName).toList();
            Bukkit.broadcast(messages.get("round.team", teamName(side), Placeholder.unparsed("players", String.join(", ", names))));
        }
        Bukkit.broadcast(messages.get("round.start"));
        secondsLeft = config.roundSeconds();
        round.clock(new TimerBar(messages, "timer.round", config.roundSeconds()), remaining -> {
            secondsLeft = remaining;
            round.announceEnd(remaining);
            if (round.clockOnSidebar()) {
                refreshAll();
            }
        }, () -> endRound(Optional.empty()));
        watch = Bukkit.getScheduler().runTaskTimer(plugin, this::watchCores, WATCH_TICKS, WATCH_TICKS);
        refreshAll();
    }

    /** Alarms and slow hands: every enemy near a core rings its team's bell and mines it with Mining Fatigue. */
    private void watchCores() {
        long now = System.nanoTime();
        double alarm = (double) config.alarmRadius() * config.alarmRadius();
        double fatigue = (double) config.fatigueRadius() * config.fatigueRadius();
        boolean changed = false;
        for (var core : map.cores()) {
            if (core.isDestroyed()) {
                continue;
            }
            boolean wasAttacked = core.isUnderAttack(now);
            var center = core.center();
            for (var enemy : members(core.side().enemy())) {
                if (!enemy.getWorld().equals(center.getWorld()) || enemy.isDead()) {
                    continue;
                }
                double distance = enemy.getLocation().distanceSquared(center);
                if (distance <= fatigue) {
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, FATIGUE_TICKS,
                            config.fatigueLevel(), true, false, true));
                }
                if (distance <= alarm && core.sound(now, ALARM_HOLD.toNanos(), Duration.ofSeconds(config.alarmSeconds()).toNanos())) {
                    members(core.side()).forEach(member -> member.sendMessage(messages.get("core.attacked", coreName(core))));
                }
            }
            if (core.isUnderAttack(now)) {
                members(core.side()).forEach(Sounds.CORE_ALARM::play);
            }
            changed |= wasAttacked != core.isUnderAttack(now);
        }
        if (changed) {
            refreshAll();
        }
    }

    public Optional<Core> coreAt(Block block) {
        return map.cores().stream().filter(core -> !core.isDestroyed() && core.block().equals(block)).findFirst();
    }

    public void breakCore(BlockBreakEvent event, Core core) {
        var breaker = event.getPlayer();
        var side = teams.of(breaker.getUniqueId());
        if (side.isEmpty() || side.get() == core.side() || !round.isPlaying(breaker)) {
            event.setCancelled(true);
            if (side.isPresent() && side.get() == core.side()) {
                breaker.sendMessage(messages.get("core.own"));
            }
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        var block = core.block();
        block.getWorld().playEffect(block.getLocation(), Effect.DESTROY_BLOCK, Material.BEACON.createBlockData());
        core.destroy();
        Coins.award(plugin, breaker, config.coreCoins());
        Bukkit.broadcast(messages.get("core.destroyed", coreName(core), Placeholder.component("player", breaker.displayName())));
        Sounds.CORE_DESTROYED.playToAll();
        var title = Title.title(messages.get("core.title"), messages.get("core.subtitle", coreName(core)), CORE_TITLE);
        members(core.side()).forEach(member -> BlockcraftApi.get().notifications().important(member, title));
        if (map.cores().stream().filter(each -> each.side() == core.side()).allMatch(Core::isDestroyed)) {
            endRound(Optional.of(core.side().enemy()));
        }
        refreshAll();
    }

    public void death(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        event.deathMessage(null);
        event.getDrops().removeIf(Kit::isKit);
        if (phase() != GamePhase.INGAME || !round.isPlaying(victim)) {
            event.getDrops().clear();
            return;
        }
        var death = summary.resolveDeath(event);
        Bukkit.broadcast(death.line());
        death.killer().ifPresent(killer -> {
            summary.awardKill(killer, victim, Defeat.RESPAWN);
            if (event.getDamageSource().getDirectEntity() instanceof AbstractArrow && config.bowKillArrows() > 0) {
                killer.getInventory().addItem(ItemStack.of(Material.ARROW, config.bowKillArrows()));
            }
        });
        summary.death(victim);
        round.respawn(victim, RESPAWN_TICKS);
        refreshAll();
    }

    public void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        if (phase() != GamePhase.INGAME) {
            event.setRespawnLocation(map.lobbySpawn());
            return;
        }
        var side = teams.of(player.getUniqueId());
        if (round.isPlaying(player) && side.isPresent()) {
            event.setRespawnLocation(map.spawn(side.get()));
            Tasks.sync(plugin, () -> {
                config.combat().apply(player);
                kit.give(player, side.get());
                player.setSaturation(RESPAWN_SATURATION);
                protectedUntil.put(player.getUniqueId(), System.nanoTime() + Duration.ofSeconds(config.protectionSeconds()).toNanos());
            });
        } else {
            event.setRespawnLocation(map.spectatorSpawn());
            Tasks.sync(plugin, () -> round.watch(player));
        }
    }

    /** Below the void line a player dies, falling from whoever hit them last; a spectator is put back above the map. */
    public void fell(Player player) {
        if (round.isPlaying(player) && !player.isDead()) {
            player.damage(Float.MAX_VALUE, DamageSource.builder(DamageType.OUT_OF_WORLD).build());
        } else if (round.isSpectator(player)) {
            player.teleport(map.spectatorSpawn());
        }
    }

    private void endRound(Optional<Side> won) {
        if (phase() != GamePhase.INGAME) {
            return;
        }
        winner = won;
        if (watch != null) {
            watch.cancel();
        }
        won.ifPresent(side -> roster.entrySet().stream().filter(entry -> entry.getValue() == side)
                .map(entry -> Bukkit.getPlayer(entry.getKey())).filter(Objects::nonNull).forEach(summary::awardWin));
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(won.map(side -> messages.get("round.winner", teamName(side))).orElseGet(() -> messages.get("round.draw")));
        Bukkit.broadcast(Component.empty());
        for (var player : Bukkit.getOnlinePlayers()) {
            if (!player.isDead()) {
                PlayerReset.reset(player, GameMode.ADVENTURE);
                player.teleport(map.lobbySpawn());
                hotbar.giveHub(player);
            }
        }
        round.end();
        refreshAll();
    }

    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    private void show(Player player) {
        var icons = GameSidebar.icons(player);
        var mapName = Component.text(map.name());
        switch (phase()) {
            case LOBBY -> sidebar.lobby(player, config.capacity(), mapName, List.of(new GameSidebar.Section(
                    messages.get("scoreboard.team", icons), GameSidebar.value(teams.of(player.getUniqueId())
                    .map(side -> side.displayName(messages)).orElseGet(() -> messages.get("scoreboard.no-team"))))));
            case INGAME -> {
                long now = System.nanoTime();
                var lines = new ArrayList<Component>();
                for (var core : map.cores()) {
                    var state = core.isDestroyed() ? "scoreboard.core-destroyed"
                            : core.isUnderAttack(now) ? "scoreboard.core-attacked" : "scoreboard.core-standing";
                    lines.add(messages.get(state, coreName(core)));
                }
                sidebar.round(player, config.capacity(), secondsLeft, mapName, List.of(
                        new GameSidebar.Section(messages.get("scoreboard.cores", icons), lines),
                        new GameSidebar.Section(messages.get("scoreboard.kills", icons),
                                GameSidebar.value(Component.text(summary.kills(player))))));
            }
            case ENDING -> sidebar.end(player, winner.map(side -> messages.get("scoreboard.winner-team", teamName(side))).stream().toList());
        }
    }

    private void dress(Player player, Side side) {
        BlockcraftApi.get().tabTeams().color(player, side.text());
        var inventory = player.getInventory();
        inventory.setChestplate(Items.of(Material.LEATHER_CHESTPLATE).color(side.dye()).build());
    }

    private TagResolver teamName(Side side) {
        return Placeholder.component("team", side.displayName(messages));
    }

    private TagResolver coreName(Core core) {
        return TagResolver.resolver(teamName(core.side()), Placeholder.component("core", messages.get("core." + core.key())));
    }

    public void stop() {
        countdown.cancel();
        if (watch != null) {
            watch.cancel();
        }
        round.stop();
    }
}
