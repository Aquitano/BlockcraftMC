package me.blockcraft.bedwars.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
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
import me.blockcraft.bedwars.config.BedWarsConfig;
import me.blockcraft.bedwars.item.SpecialItems;
import me.blockcraft.bedwars.lobby.CatchGame;
import me.blockcraft.bedwars.lobby.CpsTester;
import me.blockcraft.bedwars.lobby.JumpAndRun;
import me.blockcraft.bedwars.lobby.LobbyLayout;
import me.blockcraft.bedwars.lobby.TeamSelector;
import me.blockcraft.bedwars.map.ArenaMap;
import me.blockcraft.bedwars.map.ArenaWorlds;
import me.blockcraft.bedwars.map.Border;
import me.blockcraft.bedwars.map.MapPool;
import me.blockcraft.bedwars.perk.Perks;
import me.blockcraft.bedwars.perk.PerksMenu;
import me.blockcraft.bedwars.shop.Dealers;
import me.blockcraft.bedwars.shop.Shop;
import me.blockcraft.bedwars.shop.ShopCatalog;
import me.blockcraft.bedwars.team.Team;
import me.blockcraft.bedwars.team.TeamBed;
import me.blockcraft.bedwars.team.TeamChests;
import me.blockcraft.bedwars.team.TeamColor;
import me.blockcraft.bedwars.team.Teams;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * One server, one round of BedWars. The lobby seats the players, lets them pick a team and vote for a map; when its
 * countdown runs out the map loads, teams are filled up and everyone starts at their bed. A player whose bed still
 * stands comes back after a death; without it they are out, and the last team with anyone left wins.
 */
public final class Game {

    private static final long RESPAWN_TICKS = 20L;
    private static final long HIDDEN_AFTER_RESPAWN_TICKS = 20L;
    private static final float RESPAWN_SATURATION = 10.0f;
    private static final long WINNER_FIREWORK_TICKS = 70L;
    private static final int WINNER_FIREWORK_POWER = 10;
    private static final int BED_SPIRAL_PER_STRAND = 15;
    private static final Title.Times BED_TITLE = Title.Times.times(Duration.ofMillis(250),
            Duration.ofSeconds(2), Duration.ofMillis(500));
    private static final Set<Material> DECORATION = Set.of(Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN,
            Material.LARGE_FERN, Material.DEAD_BUSH, Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY);

    private final Plugin plugin;
    private final BedWarsConfig config;
    private final Messages messages;
    private final LobbyLayout layout;
    private final Teams teams;
    private final Perks perks;
    private final Shop shop;
    private final Dealers dealers;
    private final TeamChests chests;
    private final SpecialItems specials;
    private final RoundSummary summary;
    private final Round round;
    private final LobbyCountdown lobbyCountdown;
    private final LobbyHotbar lobbyHotbar;
    private final GameScoreboard scoreboard;
    private final TeamSelector selector;
    private final CpsTester cps;
    private final Optional<JumpAndRun> jumpAndRun;
    private final CatchGame catchGame;
    private final MapChoice maps;
    private final Set<Block> placed = new HashSet<>();
    /** Who started the round for which team; a win counts for all of them, also those out before the end. */
    private final Map<UUID, Team> roster = new HashMap<>();
    private Match match;
    private Optional<Team> winner = Optional.empty();
    private int secondsLeft;

    public Game(Plugin plugin, GameConfig gameConfig, BedWarsConfig config, Messages messages, MapPool pool,
                LobbyLayout layout, ShopCatalog catalog, StatsRepository stats, StatsMenus statsMenus) {
        var api = BlockcraftApi.get();
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.layout = layout;
        this.teams = new Teams(config.teams(), config.playersPerTeam(), messages, api.tabTeams());
        this.perks = new Perks(api.settings());
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(config.killCoins(), config.winCoins()), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.capacity(), gameConfig);
        this.shop = new Shop(catalog, messages, perks, teams::colorOf);
        this.dealers = new Dealers(messages, shop, round::isPlaying);
        this.chests = new TeamChests(plugin, messages, teams);
        this.specials = new SpecialItems(plugin, messages, config, dealers, teams, round::isPlaying, Game::isDecoration,
                placed::add);
        this.maps = new MapChoice(plugin, config, messages, pool);
        this.lobbyCountdown = new LobbyCountdown(plugin, round, gameConfig, this::lobbySecond, this::start);
        this.lobbyHotbar = new LobbyHotbar(plugin, List.of(
                new LobbyHotbar.Choice(MenuIcon.BEDWARS, messages.get("item.perks"), this::openPerks),
                new LobbyHotbar.Choice(MenuIcon.VOTE, messages.get("item.vote"), () -> maps.chosen().isEmpty(), this::openVote)),
                statsMenus, lobbyCountdown);
        this.scoreboard = new GameScoreboard(this, messages, round);
        this.selector = new TeamSelector(plugin, layout, teams, messages, this::chooseTeam);
        this.cps = new CpsTester(plugin, layout.get(LobbyLayout.CPS_TESTER), messages);
        this.jumpAndRun = layout.get(LobbyLayout.JUMP_START).flatMap(start -> layout.get(LobbyLayout.JUMP_FINISH)
                .map(finish -> new JumpAndRun(plugin, start, finish, messages, player -> GameItem.JUMP_RESET.build(player,
                        MenuIcon.JUMP.item().name(messages.get("item.jump-reset"))))));
        this.catchGame = new CatchGame(plugin, messages, round::players);
    }

    /** Everything that hears the game's events besides the round, which registers itself. */
    public List<Listener> listeners() {
        var listeners = new ArrayList<Listener>(List.of(lobbyHotbar, dealers, chests, specials, selector, cps, catchGame));
        jumpAndRun.ifPresent(listeners::add);
        return listeners;
    }

    public void open() {
        selector.open();
        cps.start();
        jumpAndRun.ifPresent(JumpAndRun::open);
        catchGame.open();
        if (!maps.hasMaps()) {
            plugin.getLogger().severe("No map of the pool seats the teams " + config.teams() + "; the lobby will wait for one.");
        }
    }

    public void stop() {
        lobbyCountdown.cancel();
        round.stop();
        if (match != null) {
            match.stop();
        }
        specials.clear();
        chests.clear();
        selector.close();
        cps.stop();
        jumpAndRun.ifPresent(JumpAndRun::close);
        catchGame.close();
    }

    public GamePhase phase() {
        return round.phase();
    }

    public LobbyCountdown lobbyCountdown() {
        return lobbyCountdown;
    }

    public Teams teams() {
        return teams;
    }

    public Perks perks() {
        return perks;
    }

    public Shop shop() {
        return shop;
    }

    public TeamChests chests() {
        return chests;
    }

    public GameScoreboard scoreboard() {
        return scoreboard;
    }

    public Optional<JumpAndRun> jumpAndRun() {
        return jumpAndRun;
    }

    public int capacity() {
        return config.capacity();
    }

    public int secondsLeft() {
        return secondsLeft;
    }

    public int kills(Player player) {
        return summary.kills(player);
    }

    public Optional<String> mapName() {
        return maps.chosen().map(ArenaMap::name);
    }

    public Optional<Team> winner() {
        return winner;
    }

    /** The blocks players put on the map, the only ones besides beds and decoration that break. */
    public Set<Block> placedBlocks() {
        return placed;
    }

    public Optional<Border> border() {
        return Optional.ofNullable(match).map(Match::border);
    }

    public Optional<Location> lobbySpawn() {
        return layout.get(LobbyLayout.SPAWN).or(() -> Optional.of(Bukkit.getWorlds().getFirst().getSpawnLocation()));
    }

    /** Where a joining player appears: in the lobby, or above the map while a round is played on it. */
    private Optional<Location> arrival() {
        return phase() == GamePhase.INGAME && match != null ? Optional.of(match.spectatorSpawn()) : lobbySpawn();
    }

    private Optional<Location> spectatorSpawn() {
        return Optional.ofNullable(match).map(Match::spectatorSpawn);
    }

    public void join(Player player) {
        if (round.join(player)) {
            lobbySpawn().ifPresent(player::teleport);
            PlayerReset.reset(player, GameMode.ADVENTURE);
            lobbyHotbar.give(player);
            cps.show(player);
            catchGame.joined();
            lobbyCountdown.start();
        } else {
            arrival().ifPresent(player::teleport);
        }
        scoreboard.refreshAll();
    }

    public void follow(Player spectator, UUID friend) {
        round.follow(spectator, friend);
    }

    public void quit(Player player) {
        leaveLobbyActivities(player);
        boolean wasPlaying = round.leave(player);
        var team = teams.of(player);
        var emptied = teams.leave(player);
        if (phase() == GamePhase.LOBBY) {
            team.ifPresent(selector::show);
        } else if (phase() == GamePhase.INGAME && wasPlaying) {
            roster.remove(player.getUniqueId());
            emptied.ifPresent(this::announceOut);
            if (!checkWinner()) {
                announceRemaining();
            }
        }
        scoreboard.refreshAll();
    }

    /** Staff who vanish in the lobby watch instead of playing, so they leave their team, the vote and the lobby games. */
    public void vanished(Player player) {
        if (phase() != GamePhase.LOBBY) {
            return;
        }
        leaveLobbyActivities(player);
        var team = teams.of(player);
        teams.leave(player);
        team.ifPresent(selector::show);
        scoreboard.refreshAll();
    }

    private void leaveLobbyActivities(Player player) {
        maps.withdraw(player);
        cps.hide(player);
        catchGame.left(player);
        jumpAndRun.ifPresent(run -> run.forget(player));
    }

    private void chooseTeam(Player player, TeamColor color) {
        if (phase() != GamePhase.LOBBY || !round.isPlaying(player)) {
            return;
        }
        teams.get(color).ifPresent(team -> {
            var previous = teams.of(player);
            switch (teams.join(player, team)) {
                case ALREADY -> player.sendMessage(messages.get("team.already"));
                case FULL -> player.sendMessage(messages.get("team.full"));
                case JOINED -> {
                    Sounds.EQUIP.play(player);
                    player.sendMessage(messages.get("team.joined", teamName(team)));
                    dress(player, team);
                    previous.ifPresent(selector::show);
                    selector.show(team);
                    scoreboard.refreshAll();
                }
            }
        });
    }

    private void openVote(Player player) {
        if (phase() == GamePhase.LOBBY) {
            maps.openMenu(player);
        }
    }

    private void openPerks(Player player) {
        new PerksMenu(messages, perks, player).open(player);
    }

    public List<String> playableMaps() {
        return maps.playable();
    }

    /** Staff may force a map, also over a decided or forced one, until the vote's last seconds. */
    public boolean mapForceable() {
        return phase() == GamePhase.LOBBY
                && !(lobbyCountdown.isRunning() && lobbyCountdown.remaining() <= config.voteClosesAt());
    }

    /** Ends the vote with the playable map {@code key}, and names it; empty for an unknown map. */
    public Optional<Component> forceMap(String key) {
        var map = maps.playable(key);
        map.ifPresent(forced -> {
            maps.force(forced);
            voteChanged();
        });
        return map.map(forced -> Component.text(forced.name()));
    }

    /** The vote closes {@code vote-closes-at} seconds before the start, and opens again if the clock goes back up. */
    private void lobbySecond(int remaining) {
        if (remaining > config.voteClosesAt()) {
            if (maps.reopen()) {
                voteChanged();
            }
        } else if (maps.decide()) {
            voteChanged();
        }
    }

    /** The vote's item comes and goes with the vote. */
    private void voteChanged() {
        round.players().forEach(lobbyHotbar::refreshChoices);
        scoreboard.refreshAll();
    }

    private void start() {
        if (maps.decide()) {
            voteChanged();
        }
        if (maps.chosen().isEmpty()) {
            broadcast("lobby.no-map");
            lobbyCountdown.start();
            return;
        }
        ArenaMap map;
        try {
            map = maps.awaitCopy();
            match = new Match(plugin, ArenaWorlds.load(map.key()), map, messages, config, dealers);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load the map " + maps.chosen().map(ArenaMap::key).orElse("?"), e);
            broadcast("lobby.map-failed");
            maps.redraw();
            voteChanged();
            lobbyCountdown.start();
            return;
        }
        var world = match.world();
        teams.assignRest(round.players()).forEach((player, team) -> player.sendMessage(messages.get("team.assigned", teamName(team))));
        teams.splitLoneTeam().forEach((player, team) -> player.sendMessage(messages.get("team.moved", teamName(team))));
        for (var team : teams.all()) {
            var color = team.color();
            var bedSpot = map.beds().get(color);
            if (team.size() == 0) {
                TeamBed.remove(world, bedSpot);
            } else {
                team.settle(map.spawns().get(color).in(world), TeamBed.place(world, bedSpot, color));
            }
        }
        selector.close();
        cps.stop();
        jumpAndRun.ifPresent(JumpAndRun::close);
        catchGame.close();
        round.enter(GamePhase.INGAME);
        for (var player : round.players()) {
            var team = teams.of(player);
            var spawn = team.flatMap(Team::spawn);
            if (spawn.isEmpty()) {
                round.eliminate(player);
                round.watch(player);
                player.teleport(match.spectatorSpawn());
                continue;
            }
            roster.put(player.getUniqueId(), team.get());
            PlayerReset.reset(player, GameMode.SURVIVAL);
            config.combat().apply(player);
            player.teleport(spawn.get());
        }
        round.spectators().forEach(spectator -> spectator.teleport(match.spectatorSpawn()));
        RoundStart.go(round.players());
        announceTeams();
        match.start();
        secondsLeft = config.roundSeconds();
        round.clock(new TimerBar(messages, "timer.round", config.roundSeconds()), remaining -> {
            secondsLeft = remaining;
            round.announceEnd(remaining);
            if (round.clockOnSidebar()) {
                scoreboard.refreshAll();
            }
        }, () -> endRound(Optional.empty()));
        scoreboard.refreshAll();
    }

    public void death(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        event.deathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (phase() != GamePhase.INGAME || !round.isPlaying(victim)) {
            return;
        }
        boolean out = teams.of(victim).map(team -> !team.hasBed()).orElse(true);
        var death = summary.resolveDeath(event);
        Bukkit.broadcast(death.line());
        death.killer().ifPresent(killer -> {
            summary.awardKill(killer, victim, out ? Defeat.OUT : Defeat.RESPAWN);
            perks.killSound(killer).ifPresent(sound -> sound.play(killer));
        });
        summary.death(victim);
        if (out) {
            round.eliminate(victim);
            teams.leave(victim).ifPresent(this::announceOut);
            if (!checkWinner()) {
                announceRemaining();
            }
        }
        round.respawn(victim, RESPAWN_TICKS);
        scoreboard.refreshAll();
    }

    public void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        if (phase() != GamePhase.INGAME) {
            lobbySpawn().ifPresent(event::setRespawnLocation);
            if (phase() == GamePhase.ENDING) {
                Tasks.sync(plugin, () -> backToLobby(player));
            }
            return;
        }
        if (round.isPlaying(player)) {
            teams.of(player).flatMap(Team::spawn).ifPresent(event::setRespawnLocation);
            Tasks.sync(plugin, () -> {
                config.combat().apply(player);
                player.setSaturation(RESPAWN_SATURATION);
                hideBriefly(player);
            });
        } else if (match != null) {
            event.setRespawnLocation(match.spectatorSpawn());
            Tasks.sync(plugin, () -> round.watch(player));
        }
    }

    /** Below the void line a player dies, falling from whoever hit them last; a spectator is put back above the map. */
    public void fell(Player player) {
        if (round.isPlaying(player) && !player.isDead()) {
            player.damage(Float.MAX_VALUE, DamageSource.builder(DamageType.OUT_OF_WORLD).build());
        } else if (round.isSpectator(player)) {
            spectatorSpawn().ifPresent(player::teleport);
        }
    }

    public double voidHeight() {
        return config.voidHeight();
    }

    /** The team whose standing bed has {@code block} as one of its halves. */
    public Optional<Team> bedAt(Block block) {
        return teams.all().stream().filter(team -> team.bed().filter(bed -> bed.isStanding() && bed.contains(block)).isPresent()).findFirst();
    }

    public void breakBed(BlockBreakEvent event, Team owner) {
        event.setCancelled(true);
        var breaker = event.getPlayer();
        if (!round.isPlaying(breaker)) {
            return;
        }
        if (owner.isMember(breaker)) {
            breaker.sendMessage(messages.get("bed.own"));
            return;
        }
        var bed = owner.bed().orElseThrow();
        var block = event.getBlock();
        block.getWorld().playEffect(block.getLocation(), Effect.DESTROY_BLOCK, block.getBlockData());
        bed.destroy();
        Spiral.draw(bed.center(), Particle.WITCH, null, BED_SPIRAL_PER_STRAND, 1.0);
        Bukkit.broadcast(messages.get("bed.destroyed", teamName(owner), Placeholder.component("player", breaker.displayName())));
        Bukkit.getOnlinePlayers().forEach(player -> perks.bedSound(player).play(player));
        var title = Title.title(messages.get("bed.title"), messages.get("bed.subtitle"), BED_TITLE);
        owner.members().forEach(member -> BlockcraftApi.get().notifications().important(member, title));
        scoreboard.refreshAll();
    }

    /** Close enough to a standing bed that a cobweb there stays and costs no wait. */
    public boolean guardsBed(Location location, double maxDistanceSquared) {
        return teams.all().stream().flatMap(team -> team.bed().stream())
                .anyMatch(bed -> bed.isStanding() && bed.distanceSquared(location) < maxDistanceSquared);
    }

    /** The spawn block of a team that can still respawn, or the block right above it. */
    public boolean blocksSpawn(Block placed) {
        var below = placed.getRelative(BlockFace.DOWN);
        return teams.all().stream().filter(Team::hasBed).flatMap(team -> team.spawn().stream())
                .map(Location::getBlock)
                .anyMatch(spawn -> spawn.equals(placed) || spawn.equals(below));
    }

    public boolean sameTeam(Player one, Player other) {
        return teams.sameTeam(one, other);
    }

    /** Grass and flowers, the only blocks of a map a player may break, and nothing drops from them. */
    public static boolean isDecoration(Block block) {
        return DECORATION.contains(block.getType()) || Tag.SMALL_FLOWERS.isTagged(block.getType());
    }

    private boolean checkWinner() {
        if (phase() != GamePhase.INGAME) {
            return true;
        }
        var standing = teams.standing();
        if (standing.size() > 1) {
            return false;
        }
        endRound(standing.stream().findFirst());
        return true;
    }

    private void endRound(Optional<Team> won) {
        if (phase() != GamePhase.INGAME) {
            return;
        }
        winner = won;
        won.ifPresent(team -> roster.entrySet().stream().filter(entry -> entry.getValue() == team)
                .map(entry -> Bukkit.getPlayer(entry.getKey())).filter(Objects::nonNull).forEach(summary::awardWin));
        match.stop();
        specials.clear();
        chests.clear();
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(won.map(team -> messages.get("round.winner", teamName(team))).orElseGet(() -> messages.get("round.draw")));
        Bukkit.broadcast(Component.empty());
        for (var player : Bukkit.getOnlinePlayers()) {
            if (!player.isDead()) {
                backToLobby(player);
            }
        }
        won.ifPresent(team -> layout.joiner(team.color()).ifPresent(spot -> Tasks.later(plugin, WINNER_FIREWORK_TICKS, () ->
                spot.getWorld().spawn(spot, Firework.class, firework -> {
                    var meta = firework.getFireworkMeta();
                    meta.setPower(WINNER_FIREWORK_POWER);
                    meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BURST).withTrail()
                            .withColor(team.color().dye().getColor()).build());
                    firework.setFireworkMeta(meta);
                }))));
        round.end();
        scoreboard.refreshAll();
    }

    private void announceTeams() {
        var header = messages.get("teams-list.header");
        Bukkit.broadcast(header);
        for (var team : teams.standing()) {
            var names = team.members().stream().map(Player::getName).collect(Collectors.joining(", "));
            Bukkit.broadcast(messages.get("teams-list.team", teamName(team), Placeholder.unparsed("players", names)));
        }
        Bukkit.broadcast(header);
    }

    private void announceOut(Team team) {
        broadcast("round.team-out", teamName(team));
    }

    private void announceRemaining() {
        broadcast("round.remaining", Formatter.number("teams", teams.standing().size()), Formatter.number("players", teams.playerCount()));
    }

    private void hideBriefly(Player player) {
        Bukkit.getOnlinePlayers().forEach(other -> {
            if (!other.equals(player)) {
                other.hidePlayer(plugin, player);
            }
        });
        Tasks.later(plugin, HIDDEN_AFTER_RESPAWN_TICKS, () -> {
            if (!player.isOnline()) {
                return;
            }
            boolean watching = round.isSpectator(player);
            Bukkit.getOnlinePlayers().stream().filter(other -> !watching || round.isSpectator(other))
                    .forEach(other -> other.showPlayer(plugin, player));
        });
    }

    /** After the round everyone meets in the lobby again, seen by all, with nothing but the way out. */
    private void backToLobby(Player player) {
        Bukkit.getOnlinePlayers().forEach(other -> other.showPlayer(plugin, player));
        PlayerReset.reset(player, GameMode.ADVENTURE);
        lobbySpawn().ifPresent(player::teleport);
        lobbyHotbar.giveHub(player);
    }

    private void dress(Player player, Team team) {
        var armor = team.armor();
        var inventory = player.getInventory();
        inventory.setBoots(armor[0]);
        inventory.setLeggings(armor[1]);
        inventory.setChestplate(armor[2]);
    }

    private TagResolver teamName(Team team) {
        return Placeholder.component("team", team.color().displayName(messages));
    }

    private void broadcast(String key, TagResolver... resolvers) {
        Bukkit.broadcast(messages.get(key, resolvers));
    }
}
