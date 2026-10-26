package me.blockcraft.skywars.game;

import me.blockcraft.api.BlockcraftApi;
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
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.skywars.config.SkyWarsConfig;
import me.blockcraft.skywars.kit.Kit;
import me.blockcraft.skywars.map.ArenaBuilder;
import me.blockcraft.skywars.map.MapVote;
import me.blockcraft.skywars.map.Ring;
import me.blockcraft.skywars.map.SkyArena;
import me.blockcraft.skywars.menu.KitMenus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One server, one round of SkyWars, as GommeHD played it: every player on their own island over the void with a
 * kit and three chests, the better loot on the semi-middles and the best in the middle. The chests fill once more
 * after a while, later the border closes on the middle, and the last player alive wins.
 */
public final class SkyWarsGame {

    private static final long TICKS_PER_SECOND = 20L;
    private static final Set<Integer> REFILL_WARNINGS = Set.of(60, 10);

    private final Plugin plugin;
    private final SkyWarsConfig config;
    private final Messages messages;
    private final World world;
    private final Location lobbySpawn;
    private final RoundSummary summary;
    private final Round round;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final GameSidebar sidebar;
    private final MapVote vote;
    private final Map<UUID, Kit> kits = new HashMap<>();
    private Optional<SkyArena> arena = Optional.empty();
    private Optional<Chests> chests = Optional.empty();
    private boolean frozen;
    private int elapsed;

    public SkyWarsGame(Plugin plugin, GameConfig gameConfig, SkyWarsConfig config, Messages messages, World world,
                       Location lobbySpawn, StatsRepository stats, StatsMenus statsMenus) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.world = world;
        this.lobbySpawn = lobbySpawn;
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(config.rewards().kill(), config.rewards().win()), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.maxPlayers(), gameConfig);
        this.countdown = new LobbyCountdown(plugin, round, gameConfig, this::start);
        this.vote = new MapVote(messages);
        var kitMenus = new KitMenus(messages, this, BlockcraftApi.get().unlocks(), config);
        this.hotbar = new LobbyHotbar(plugin, List.of(
                new LobbyHotbar.Choice(MenuIcon.SKYWARS, messages.get("item.kits"), kitMenus::open),
                new LobbyHotbar.Choice(MenuIcon.VOTE, messages.get("item.vote"), this::isLobby, this::openVote)),
                statsMenus, countdown);
        this.sidebar = new GameSidebar(messages.get("scoreboard.title"), round);
    }

    public GamePhase phase() {
        return round.phase();
    }

    public boolean isLobby() {
        return phase() == GamePhase.LOBBY;
    }

    public boolean isIngame() {
        return phase() == GamePhase.INGAME;
    }

    public LobbyCountdown countdown() {
        return countdown;
    }

    public LobbyHotbar hotbar() {
        return hotbar;
    }

    public MapVote vote() {
        return vote;
    }

    public SkyWarsConfig config() {
        return config;
    }

    /** Nobody is hurt, throws, places or uses anything while the players wait in their cages. */
    public boolean frozen() {
        return frozen;
    }

    public boolean isPlaying(Player player) {
        return round.isPlaying(player);
    }

    public boolean inArena(World other) {
        return world.equals(other);
    }

    public Kit kit(Player player) {
        return kits.getOrDefault(player.getUniqueId(), Kit.STANDARD);
    }

    /** Only in the lobby: a purchase that completes after the start must not hand out a kit nobody equipped. */
    public void selectKit(Player player, Kit kit) {
        if (!isLobby()) {
            return;
        }
        kits.put(player.getUniqueId(), kit);
        show(player);
    }

    public Component kitName(Kit kit) {
        return messages.get("kit." + kit.key() + ".name");
    }

    public void join(Player player) {
        if (round.join(player)) {
            PlayerReset.reset(player, GameMode.ADVENTURE);
            player.teleport(lobbySpawn);
            hotbar.give(player);
            countdown.start();
            if (round.playerCount() >= config.maxPlayers()) {
                countdown.shorten(config.countdowns().full());
            }
        } else {
            player.teleport(arena.map(SkyArena::spectatorSpawn).filter(spawn -> isIngame()).orElse(lobbySpawn));
        }
        refreshAll();
    }

    public void follow(Player spectator, UUID friend) {
        round.follow(spectator, friend);
    }

    public void quit(Player player) {
        boolean wasPlaying = round.leave(player);
        kits.remove(player.getUniqueId());
        vote.withdraw(player);
        refreshAll();
        if (wasPlaying) {
            checkWinner(true);
        }
    }

    public void vanished(Player player) {
        if (isIngame() && round.isPlaying(player)) {
            quit(player);
            round.watch(player);
        } else if (isLobby()) {
            vote.withdraw(player);
        }
    }

    private void openVote(Player player) {
        if (isLobby()) {
            vote.open(player);
        }
    }

    /** /forcemap: staff choose the map while the lobby waits. */
    public Optional<Component> forceMap(String key) {
        return vote.force(key);
    }

    private void start() {
        var theme = vote.decide();
        var built = ArenaBuilder.build(world, theme);
        arena = Optional.of(built);
        var border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(built.borderRadius() * 2.0);
        var filled = new Chests(config.loot(), built.chests());
        filled.fill();
        chests = Optional.of(filled);
        round.enter(GamePhase.INGAME);
        var players = new ArrayList<>(round.players());
        Collections.shuffle(players);
        var slots = Ring.spread(players.size(), built.spawns().size());
        for (int index = 0; index < players.size(); index++) {
            var player = players.get(index);
            PlayerReset.reset(player, GameMode.SURVIVAL);
            config.combat().apply(player);
            player.teleport(built.spawns().get(slots.get(index)));
            kit(player).equip(player);
            player.sendMessage(messages.get("round.kit", Placeholder.component("kit", kitName(kit(player)))));
        }
        round.spectators().forEach(spectator -> spectator.teleport(built.spectatorSpawn()));
        frozen = true;
        elapsed = 0;
        refreshAll();
        round.timer(RoundStart.freeze(plugin, config.countdowns().freeze(), round::players, messages.get("round.subtitle"), () -> {
            frozen = false;
            openCages(built);
            Bukkit.broadcast(messages.get("round.start"));
            startClock();
        }));
    }

    /** Nobody could touch a cage while the players stood frozen, so all of it goes. */
    private static void openCages(SkyArena built) {
        built.cages().forEach(block -> block.setType(Material.AIR, false));
    }

    private void startClock() {
        int seconds = config.countdowns().round();
        round.clock(new TimerBar(messages, "timer.round", seconds), remaining -> {
            elapsed = seconds - remaining;
            int untilRefill = config.countdowns().refillAfter() - elapsed;
            if (REFILL_WARNINGS.contains(untilRefill)) {
                Bukkit.broadcast(messages.get("refill.soon", Formatter.number("seconds", untilRefill)));
            }
            if (untilRefill == 0) {
                refill();
            }
            if (elapsed == config.countdowns().borderAfter()) {
                shrinkBorder(remaining);
            }
            round.announceEnd(remaining);
            refreshAll();
        }, () -> {
            Bukkit.broadcast(messages.get("round.draw"));
            endRound();
        });
    }

    private void refill() {
        chests.ifPresent(Chests::fill);
        Bukkit.broadcast(messages.get("refill.done"));
        Sounds.NOTIFY.playToAll();
    }

    private void shrinkBorder(int remaining) {
        world.getWorldBorder().changeSize(config.arena().finalRadius() * 2.0, remaining * TICKS_PER_SECOND);
        Bukkit.broadcast(messages.get("border.shrinking", Formatter.number("radius", config.arena().finalRadius())));
        round.players().forEach(Sounds.NOTIFY::play);
    }

    public void death(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        event.deathMessage(null);
        if (!isIngame() || !round.isPlaying(victim)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        var death = summary.resolveDeath(event);
        Bukkit.broadcast(death.line());
        death.killer().ifPresent(killer -> summary.awardKill(killer, victim, Defeat.OUT));
        summary.death(victim);
        round.eliminate(victim);
        round.respawn(victim, 1L);
        refreshAll();
        checkWinner(false);
    }

    /** The spectator state is applied after the respawn; touching a dead player breaks the respawn. */
    public void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        event.setRespawnLocation(isLobby() ? lobbySpawn : arena.map(SkyArena::spectatorSpawn).orElse(lobbySpawn));
        if (round.isSpectator(player)) {
            Tasks.sync(plugin, () -> round.watch(player));
        }
    }

    /** Below the void line a player dies, falling from whoever hit them last; a spectator is put back above the map. */
    public void fell(Player player) {
        if (round.isPlaying(player) && !player.isDead()) {
            player.damage(Float.MAX_VALUE, DamageSource.builder(DamageType.OUT_OF_WORLD).build());
        } else if (round.isSpectator(player)) {
            arena.ifPresent(built -> player.teleport(built.spectatorSpawn()));
        }
    }

    private void checkWinner(boolean opponentLeft) {
        if (!isIngame() || round.playerCount() > 1) {
            return;
        }
        var winners = round.players();
        winners.forEach(summary::awardWin);
        winners.stream().findFirst().ifPresentOrElse(
                winner -> Bukkit.broadcast(messages.get(opponentLeft ? "round.winner-left" : "round.winner",
                        Placeholder.component("player", winner.displayName()))),
                () -> Bukkit.broadcast(messages.get("round.draw")));
        endRound();
    }

    private void endRound() {
        frozen = false;
        round.end();
        refreshAll();
    }

    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    private void show(Player player) {
        var icons = GameSidebar.icons(player);
        var kit = new GameSidebar.Section(messages.get("scoreboard.kit", icons), GameSidebar.value(kitName(kit(player))));
        switch (phase()) {
            case LOBBY -> sidebar.lobby(player, config.maxPlayers(), messages.get("scoreboard.map-vote"), List.of(kit));
            case INGAME -> {
                var map = arena.map(built -> vote.name(built.theme())).orElseGet(Component::empty);
                int untilRefill = config.countdowns().refillAfter() - elapsed;
                var refill = new GameSidebar.Section(messages.get("scoreboard.refill", icons), GameSidebar.value(untilRefill > 0
                        ? Component.text(TimerBar.clock(untilRefill)) : messages.get("scoreboard.refilled")));
                var sections = round.isPlaying(player)
                        ? List.of(new GameSidebar.Section(messages.get("scoreboard.kills", icons),
                        GameSidebar.value(Component.text(summary.kills(player)))), refill)
                        : List.of(refill);
                sidebar.round(player, config.maxPlayers(), config.countdowns().round() - elapsed, map, sections);
            }
            case ENDING -> sidebar.end(player, summary.winnerNames().stream().<Component>map(Component::text).toList());
        }
    }

    public void stop() {
        countdown.cancel();
        round.stop();
    }
}
