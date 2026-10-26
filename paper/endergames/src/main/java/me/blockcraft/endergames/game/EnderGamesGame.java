package me.blockcraft.endergames.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.LobbyHotbar;
import me.blockcraft.api.game.LootTable;
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
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.endergames.config.EnderGamesConfig;
import me.blockcraft.endergames.kit.Kit;
import me.blockcraft.endergames.kit.KitAbilities;
import me.blockcraft.endergames.kit.KitPlayers;
import me.blockcraft.endergames.menu.KitMenus;
import me.blockcraft.endergames.world.RoundWorld;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One server, one round of EnderGames, as GommeHD played it: survival games in a world nobody has seen before. Every
 * player brings a kit; after a short protection time it is every player for themselves until one is left. Ender
 * chests with loot wander the map, two players trade places now and then, and the border closes in towards the end.
 */
public final class EnderGamesGame implements KitPlayers {

    private static final Set<Integer> PROTECTION_ANNOUNCEMENTS = Set.of(60, 30, 10, 5, 4, 3, 2, 1);
    private static final int SECONDS_PER_MINUTE = 60;
    private static final long TICKS_PER_SECOND = 20L;

    private final Plugin plugin;
    private final EnderGamesConfig config;
    private final Messages messages;
    private final RoundWorld world;
    private final Location lobbySpawn;
    private final RoundSummary summary;
    private final Round round;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final GameSidebar sidebar;
    private final KitAbilities abilities;
    private final EnderChests enderChests;
    private final PlayerSwaps swaps;
    private final Map<UUID, Kit> kits = new HashMap<>();
    private final int startSeconds;
    private boolean frozen;
    private boolean protection;
    private boolean startWhenReady;
    private BukkitTask chestTask;
    private Countdown protectionTimer;
    private Countdown fightTimer;

    public EnderGamesGame(Plugin plugin, GameConfig gameConfig, EnderGamesConfig config, Messages messages,
                          RoundWorld world, Location lobbySpawn, StatsRepository stats, StatsMenus statsMenus) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.world = world;
        this.lobbySpawn = lobbySpawn;
        this.startSeconds = gameConfig.startSeconds();
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(config.rewards().kill(), config.rewards().win()), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.maxPlayers(), gameConfig);
        this.countdown = new LobbyCountdown(plugin, round, gameConfig, this::start);
        var kitMenus = new KitMenus(messages, this, BlockcraftApi.get().unlocks(), config);
        this.hotbar = new LobbyHotbar(plugin, List.of(new LobbyHotbar.Choice(MenuIcon.ENDERGAMES, messages.get("item.kits"),
                kitMenus::open)), statsMenus, countdown);
        this.sidebar = new GameSidebar(messages.get("scoreboard.title"), round);
        this.abilities = new KitAbilities(this, config.abilities(), messages);
        this.enderChests = new EnderChests(world, config.enderChests(), this::loot);
        this.swaps = new PlayerSwaps(config.swap());
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

    public KitAbilities abilities() {
        return abilities;
    }

    public RoundWorld world() {
        return world;
    }

    public Location lobbySpawn() {
        return lobbySpawn;
    }

    /** Nobody is hurt while the players stand frozen, during the protection time and once the round is over. */
    public boolean peaceful() {
        return frozen || protection || phase() == GamePhase.ENDING;
    }

    public boolean frozen() {
        return frozen;
    }

    public boolean isPlaying(Player player) {
        return round.isPlaying(player);
    }

    public Kit kit(Player player) {
        return kits.getOrDefault(player.getUniqueId(), Kit.BARBAR);
    }

    /** Only in the lobby: a purchase that completes after the start must not swap the kit under a player's items. */
    public void selectKit(Player player, Kit kit) {
        if (!isLobby()) {
            return;
        }
        kits.put(player.getUniqueId(), kit);
        show(player);
    }

    @Override
    public Optional<Kit> activeKit(Player player) {
        return isIngame() && round.isPlaying(player) ? Optional.of(kit(player)) : Optional.empty();
    }

    @Override
    public List<Player> fighting() {
        return round.players();
    }

    public void join(Player player) {
        if (round.join(player)) {
            PlayerReset.reset(player, GameMode.ADVENTURE);
            player.teleport(lobbySpawn);
            hotbar.give(player);
            if (!world.ready()) {
                player.sendMessage(messages.get("lobby.world-loading"));
            }
            countdown.start();
            if (round.playerCount() >= config.maxPlayers()) {
                countdown.shorten(config.countdowns().full());
            }
        } else {
            player.teleport(isIngame() ? world.spectatorSpawn() : lobbySpawn);
        }
        refreshAll();
    }

    public void follow(Player spectator, UUID friend) {
        round.follow(spectator, friend);
    }

    public void quit(Player player) {
        boolean wasPlaying = round.leave(player);
        kits.remove(player.getUniqueId());
        abilities.forget(player);
        refreshAll();
        if (wasPlaying) {
            checkWinner(true);
        }
    }

    public void vanished(Player player) {
        if (isIngame() && round.isPlaying(player)) {
            quit(player);
            round.watch(player);
        }
    }

    /** A countdown that ran out while the world was loading starts again now, even below players.min after /start. */
    public void worldReady() {
        Bukkit.broadcast(messages.get("lobby.world-ready"));
        if (startWhenReady && isLobby() && !countdown.isRunning() && round.playerCount() >= LobbyCountdown.ROUND_MINIMUM) {
            countdown.shorten(startSeconds);
        }
        startWhenReady = false;
        refreshAll();
    }

    private void start() {
        if (!world.ready()) {
            Bukkit.broadcast(messages.get("lobby.world-loading"));
            startWhenReady = true;
            countdown.start();
            return;
        }
        round.enter(GamePhase.INGAME);
        var players = round.players();
        var spawns = world.ringSpawns(players.size());
        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            PlayerReset.reset(player, GameMode.SURVIVAL);
            config.combat().apply(player);
            player.teleport(spawns.get(i));
            kit(player).equip(player, messages);
            player.sendMessage(messages.get("round.kit", Placeholder.component("kit", kitName(kit(player)))));
        }
        round.spectators().forEach(spectator -> spectator.teleport(world.spectatorSpawn()));
        enderChests.spawn(config.enderChests().countFor(players.size()));
        chestTask = Bukkit.getScheduler().runTaskTimer(plugin, enderChests::tick, TICKS_PER_SECOND, TICKS_PER_SECOND);
        frozen = true;
        protection = true;
        refreshAll();
        round.timer(RoundStart.freeze(plugin, config.countdowns().freeze(), round::players, messages.get("round.subtitle"), () -> {
            frozen = false;
            startProtection();
        }));
    }

    private void startProtection() {
        int seconds = config.countdowns().protection();
        announceProtection(seconds);
        protectionTimer = round.clock(new TimerBar(messages, "timer.protection", seconds), remaining -> {
            if (PROTECTION_ANNOUNCEMENTS.contains(remaining)) {
                announceProtection(remaining);
            }
            if (round.clockOnSidebar()) {
                refreshAll();
            }
        }, () -> {
            protection = false;
            Bukkit.broadcast(messages.get("protection.over"));
            round.players().forEach(player -> {
                Sounds.PVP_ENABLED.play(player);
                BlockcraftApi.get().notifications().important(player, messages.get("protection.fight"));
            });
            startFight();
        });
    }

    private void announceProtection(int seconds) {
        if (seconds > 0) {
            Bukkit.broadcast(messages.get(seconds == 1 ? "protection.ends-in-second" : "protection.ends-in",
                    Formatter.number("seconds", seconds)));
        }
    }

    private void startFight() {
        int seconds = config.countdowns().round();
        int shrinkAfter = config.world().shrinkAfter();
        fightTimer = round.clock(new TimerBar(messages, "timer.round", seconds), remaining -> {
            int elapsed = seconds - remaining;
            if (elapsed == config.countdowns().trackerAfter()) {
                round.handOutTrackers(config.countdowns().trackerAfter() / SECONDS_PER_MINUTE, tracker());
            }
            if (elapsed == shrinkAfter && shrinkAfter < seconds) {
                shrinkBorder(remaining);
            }
            swaps.tick(round.players()).ifPresent(this::announceSwap);
            round.announceEnd(remaining);
            if (round.clockOnSidebar()) {
                refreshAll();
            }
        }, () -> {
            Bukkit.broadcast(messages.get("round.draw"));
            endRound();
        });
    }

    private void shrinkBorder(int remaining) {
        world.world().getWorldBorder().changeSize(config.world().finalRadius() * 2.0, remaining * TICKS_PER_SECOND);
        Bukkit.broadcast(messages.get("border.shrinking", Formatter.number("radius", config.world().finalRadius())));
        round.players().forEach(Sounds.NOTIFY::play);
    }

    private void announceSwap(SwapPairs<Player> swap) {
        Bukkit.broadcast(messages.get("swap.broadcast", Placeholder.unparsed("first", swap.first().getName()),
                Placeholder.unparsed("second", swap.second().getName())));
        for (var pair : List.of(List.of(swap.first(), swap.second()), List.of(swap.second(), swap.first()))) {
            BlockcraftApi.get().notifications().important(pair.getFirst(), messages.get("swap.you",
                    Placeholder.unparsed("player", pair.getLast().getName())));
            abilities.swapped(pair.getFirst());
        }
    }

    public Optional<Inventory> openEnderChest(Player player, Block block) {
        if (!isIngame() || !round.isPlaying(player)) {
            return Optional.empty();
        }
        return enderChests.open(block);
    }

    public boolean isEnderChest(Block block) {
        return isIngame() && enderChests.isRoamer(block);
    }

    public void death(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        event.deathMessage(null);
        if (!isIngame() || !round.isPlaying(victim)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        event.setDroppedExp(victim.calculateTotalExperiencePoints());
        var death = summary.resolveDeath(event);
        Bukkit.broadcast(death.line());
        death.killer().ifPresent(killer -> summary.awardKill(killer, victim, Defeat.OUT));
        summary.death(victim);
        round.eliminate(victim);
        abilities.forget(victim);
        round.respawn(victim, 1L);
        refreshAll();
        checkWinner(false);
    }

    /** The spectator state is applied after the respawn; touching a dead player breaks the respawn. */
    public void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        event.setRespawnLocation(isLobby() ? lobbySpawn : world.spectatorSpawn());
        if (round.isSpectator(player)) {
            Tasks.sync(plugin, () -> round.watch(player));
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
        protection = false;
        if (chestTask != null) {
            chestTask.cancel();
        }
        enderChests.clear();
        abilities.reset();
        round.players().forEach(player -> player.setAllowFlight(false));
        round.end();
        refreshAll();
    }

    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    private void show(Player player) {
        var icons = GameSidebar.icons(player);
        var map = messages.get("scoreboard.map");
        var kitSection = new GameSidebar.Section(messages.get("scoreboard.kit", icons), GameSidebar.value(kitName(kit(player))));
        switch (phase()) {
            case LOBBY -> sidebar.lobby(player, config.maxPlayers(), map, List.of(kitSection,
                    new GameSidebar.Section(messages.get("scoreboard.world", icons),
                            GameSidebar.value(messages.get(world.ready() ? "scoreboard.world-ready" : "scoreboard.world-loading")))));
            case INGAME -> sidebar.round(player, config.maxPlayers(), secondsLeft(), map, round.isPlaying(player)
                    ? List.of(kitSection, new GameSidebar.Section(messages.get("scoreboard.kills", icons),
                            GameSidebar.value(Component.text(summary.kills(player)))))
                    : List.of());
            case ENDING -> sidebar.end(player, summary.winnerNames().stream().<Component>map(Component::text).toList());
        }
    }

    /** Without boss bars the sidebar tells the time: the protection's while it lasts, then the fight's. */
    private int secondsLeft() {
        if (fightTimer != null) {
            return fightTimer.remaining();
        }
        if (protectionTimer != null) {
            return protectionTimer.remaining();
        }
        return config.countdowns().protection();
    }

    public Component kitName(Kit kit) {
        return messages.get("kit." + kit.key() + ".name");
    }

    public ItemStack tracker() {
        return Items.of(Material.COMPASS).name(messages.get("item.tracker.name")).lore(messages.get("item.tracker.lore")).build();
    }

    /** Potions in the ender chests heal when drunk and harm when thrown; everything else is the plain item. */
    private ItemStack loot(LootTable.Entry entry) {
        var item = ItemStack.of(entry.material(), entry.amount());
        if (item.getItemMeta() instanceof PotionMeta potion) {
            potion.setBasePotionType(entry.material() == Material.SPLASH_POTION ? PotionType.HARMING : PotionType.HEALING);
            item.setItemMeta(potion);
        }
        return item;
    }

    public void stop() {
        countdown.cancel();
        if (chestTask != null) {
            chestTask.cancel();
        }
        round.stop();
    }
}
