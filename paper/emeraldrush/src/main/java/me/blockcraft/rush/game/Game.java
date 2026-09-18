package me.blockcraft.rush.game;

import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.world.MapBundle;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.Permissions;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.config.SpawnPoint;
import me.blockcraft.rush.config.Spawns;
import me.blockcraft.rush.scoreboard.GameScoreboard;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

public final class Game {

    private static final int MIN_TO_START = 2;

    private final Plugin plugin;
    private final RushConfig config;
    private final Spawns spawns;
    private final Messages messages;
    private final StatsRepository stats;
    private final Countdown countdown;
    private final LobbyCountdown lobbyCountdown;
    private final RoundSummary summary;
    private final GameScoreboard scoreboard;
    private final Arena arena;
    private Countdown restart;
    private TimerBar timer;

    private GamePhase phase = GamePhase.LOBBY;
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<UUID, Integer> emeralds = new HashMap<>();
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Map<UUID, Loadout> loadouts = new HashMap<>();

    private record Loadout(ItemStack[] contents, ItemStack[] armor) {
    }

    public Game(Plugin plugin, RushConfig config, Spawns spawns, Messages messages, StatsRepository stats, MapBundle map) {
        this.plugin = plugin;
        this.config = config;
        this.spawns = spawns;
        this.messages = messages;
        this.stats = stats;
        this.countdown = new Countdown(plugin);
        this.arena = new Arena(messages, Sounds.RUSH_JOIN);
        this.lobbyCountdown = new LobbyCountdown(plugin, arena, config.lobbyCountdown(), config.minPlayers(), alive::size, this::beginSearch);
        this.summary = new RoundSummary(plugin, messages,
                new RoundSummary.Rewards(config.killCoins(), config.winCoins()));
        this.scoreboard = new GameScoreboard(this, messages, map);
    }

    public GamePhase phase() {
        return phase;
    }

    public GameScoreboard scoreboard() {
        return scoreboard;
    }

    public int secondsLeft() {
        return countdown.remaining();
    }

    public boolean isAlive(Player player) {
        return alive.contains(player.getUniqueId());
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public List<Player> alivePlayers() {
        return alive.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    private List<Player> spectatorPlayers() {
        return spectators.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    public int emeralds(Player player) {
        return emeralds.getOrDefault(player.getUniqueId(), 0);
    }

    public void setEmeralds(Player player, int amount) {
        emeralds.put(player.getUniqueId(), amount);
    }

    public void addEmeralds(Player player, int amount) {
        emeralds.merge(player.getUniqueId(), amount, Integer::sum);
    }

    public boolean withdrawEmeralds(Player player, int amount) {
        if (emeralds(player) < amount) {
            return false;
        }
        emeralds.put(player.getUniqueId(), emeralds(player) - amount);
        return true;
    }

    public int lives(Player player) {
        return lives.getOrDefault(player.getUniqueId(), 0);
    }

    public void join(Player player) {
        if (phase != GamePhase.LOBBY) {
            makeSpectator(player);
            spawns.teleport(player, SpawnPoint.SPECTATOR);
            arena.arrive(player);
            scoreboard.refreshAll();
            return;
        }
        alive.add(player.getUniqueId());
        summary.join(player);
        spawns.teleport(player, SpawnPoint.LOBBY);
        PlayerReset.reset(player, GameMode.ADVENTURE);
        giveLobbyItems(player);
        arena.welcome(player, alive.size(), Bukkit.getMaxPlayers());
        scoreboard.refreshAll();
        lobbyCountdown.start();
    }

    /** The player came to watch a friend, and the round has already put them among the spectators. */
    public void follow(Player spectator, UUID friend) {
        if (!isSpectator(spectator)) {
            return;
        }
        var target = Bukkit.getPlayer(friend);
        if (target != null) {
            arena.follow(spectator, target);
        }
    }

    public void quit(Player player) {
        var id = player.getUniqueId();
        boolean wasSpectator = spectators.remove(id);
        boolean wasAlive = alive.remove(id);
        emeralds.remove(id);
        lives.remove(id);
        loadouts.remove(id);
        if (wasAlive && phase != GamePhase.LOBBY && phase != GamePhase.ENDING) {
            var name = BlockcraftApi.get().realName(player);
            Tasks.async(plugin, () -> stats.add(id, name, Stat.LOSSES, 1));
            checkWinner();
        }
        if (!wasSpectator) {
            arena.farewell(player, alive.size(), Bukkit.getMaxPlayers());
        }
        hideTimer(player);
        scoreboard.refreshAll();
    }

    public void forceStart(Player requester) {
        if (alive.size() < MIN_TO_START) {
            requester.sendMessage(messages.get("command.start-not-enough-players"));
            return;
        }
        if (phase != GamePhase.LOBBY) {
            requester.sendMessage(messages.get("command.start-not-lobby"));
            return;
        }
        if (!lobbyCountdown.shorten(5)) {
            requester.sendMessage(messages.get("command.start-already-started"));
        }
    }

    public void skipSearch() {
        if (phase == GamePhase.SEARCH) {
            countdown.remaining(10);
        }
    }

    public void skipShopping() {
        if (phase == GamePhase.SHOPPING) {
            countdown.remaining(10);
        }
    }

    public void stop() {
        lobbyCountdown.cancel();
        countdown.cancel();
        hideTimer();
        if (restart != null) {
            restart.cancel();
        }
    }

    private void beginSearch() {
        if (alive.size() < MIN_TO_START) {
            broadcast("lobby.cancelled");
            lobbyCountdown.start();
            return;
        }
        setPhase(GamePhase.SEARCH);
        Sounds.COUNTDOWN_GO.playToAll();
        messages.getLines("search.started", Formatter.number("minutes", config.searchSeconds() / 60)).forEach(Bukkit::broadcast);
        var pickaxe = Items.of(Material.IRON_PICKAXE).name(messages.get("item.pickaxe")).edit(meta -> {
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        }).build();
        for (var player : Bukkit.getOnlinePlayers()) {
            player.getInventory().clear();
            player.setLevel(0);
            player.setExp(0.0f);
            player.setGameMode(GameMode.SURVIVAL);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 2);
            player.getInventory().addItem(pickaxe.clone());
            spawns.teleport(player, SpawnPoint.SEARCH);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
            config.combat().apply(player);
        }
        scoreboard.refreshAll();
        startPhase("timer.search", config.searchSeconds(), remaining -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(messages.get("search.actionbar", Formatter.number("emeralds", emeralds(player))));
            }
            scoreboard.refreshAll();
            switch (remaining) {
                case 120, 60 -> {
                    Sounds.NOTIFY.playToAll();
                    broadcast("search.ends-minutes", Formatter.number("minutes", remaining / 60));
                }
                case 15, 5 -> {
                    Sounds.NOTIFY.playToAll();
                    broadcast("search.ends-seconds", Formatter.number("seconds", remaining));
                }
                case 4, 3, 2, 1 -> Sounds.countdownTick(remaining);
                default -> {
                }
            }
        }, this::beginShopping);
    }

    private void beginShopping() {
        setPhase(GamePhase.SHOPPING);
        var shop = Items.of(Material.CHEST).name(messages.get("item.shop")).build();
        var titleTimes = Title.Times.times(Duration.ofMillis(1250), Duration.ofMillis(1250), Duration.ofMillis(1250));
        for (var player : alivePlayers()) {
            Sounds.COUNTDOWN_GO.play(player);
            player.showTitle(Title.title(
                    messages.get("shopping.title", Formatter.number("emeralds", emeralds(player))),
                    messages.get("shopping.subtitle"), titleTimes));
            player.sendMessage(messages.get("shopping.info", Formatter.number("seconds", config.shoppingSeconds())));
            player.sendMessage(messages.get("shopping.hint"));
            player.getInventory().clear();
            player.getInventory().setItem(4, shop.clone());
            spawns.teleport(player, SpawnPoint.SHOPPING);
            lives.put(player.getUniqueId(), config.lives());
        }
        spectatorPlayers().forEach(spectator -> spawns.teleport(spectator, SpawnPoint.SHOPPING));
        scoreboard.refreshAll();
        startPhase("timer.shopping", config.shoppingSeconds(), remaining -> {
            switch (remaining) {
                case 15, 5 -> {
                    Sounds.NOTIFY.playToAll();
                    broadcast("shopping.ends", Formatter.number("seconds", remaining));
                }
                case 4, 3, 2, 1 -> Sounds.countdownTick(remaining);
                default -> {
                }
            }
        }, this::beginDeathmatch);
    }

    private void beginDeathmatch() {
        setPhase(GamePhase.DEATHMATCH);
        broadcast("deathmatch.started");
        int spawnIndex = 0;
        for (var player : alivePlayers()) {
            Sounds.COUNTDOWN_GO.play(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 10));
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().remove(Material.CHEST);
            saveLoadout(player);
            spawns.teleport(player, spawns.deathmatch(spawnIndex++));
        }
        spectatorPlayers().forEach(spectator -> spawns.teleport(spectator, SpawnPoint.DEATHMATCH_1));
        scoreboard.refreshAll();
        int trackerAt = config.deathmatchSeconds() - config.trackerAfterSeconds();
        startPhase("timer.deathmatch", config.deathmatchSeconds(), remaining -> {
            if (remaining == trackerAt) {
                giveTrackers();
            } else if (remaining == 600 || remaining == 300 || remaining == 180) {
                broadcast("deathmatch.ends-minutes", Formatter.number("minutes", remaining / 60));
            } else if (remaining == 60) {
                broadcast("deathmatch.ends-minute");
            }
        }, () -> {
            broadcast("deathmatch.draw");
            endRound();
        });
    }

    private void giveTrackers() {
        messages.getLines("deathmatch.tracker", Formatter.number("minutes", config.trackerAfterSeconds() / 60)).forEach(Bukkit::broadcast);
        var compass = Items.of(Material.COMPASS).name(messages.get("item.compass")).lore(messages.get("item.compass-lore")).build();
        for (var player : Bukkit.getOnlinePlayers()) {
            player.getInventory().addItem(compass.clone());
            Sounds.NOTIFY.play(player);
        }
    }

    public void handleDeath(Player victim, Optional<Player> killer) {
        if (phase != GamePhase.DEATHMATCH) {
            return;
        }
        var victimName = Placeholder.component("victim", victim.playerListName());
        killer.filter(slayer -> !slayer.equals(victim)).ifPresentOrElse(slayer -> {
            broadcast("deathmatch.killed", victimName, Placeholder.component("killer", slayer.playerListName()));
            int coins = summary.awardKill(slayer, victim);
            Tasks.async(plugin, () -> {
                stats.add(slayer.getUniqueId(), BlockcraftApi.get().realName(slayer), Stat.KILLS, 1);
                stats.add(slayer.getUniqueId(), BlockcraftApi.get().realName(slayer), Stat.POINTS, coins);
            });
            slayer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1));
        }, () -> broadcast("deathmatch.died", victimName));
        summary.death(victim);
        Tasks.async(plugin, () -> stats.add(victim.getUniqueId(), BlockcraftApi.get().realName(victim), Stat.DEATHS, 1));
        int remaining = Math.max(0, lives(victim) - 1);
        lives.put(victim.getUniqueId(), remaining);
        if (remaining == 0) {
            alive.remove(victim.getUniqueId());
            spectators.add(victim.getUniqueId());
            Tasks.async(plugin, () -> stats.add(victim.getUniqueId(), BlockcraftApi.get().realName(victim), Stat.LOSSES, 1));
        }
        scoreboard.refreshAll();
        if (remaining == 0) {
            checkWinner();
        }
    }

    public void respawn(PlayerRespawnEvent event) {
        if (!phase.allowsDamage()) {
            return;
        }
        var player = event.getPlayer();
        if (spectators.contains(player.getUniqueId())) {
            spawns.get(SpawnPoint.DEATHMATCH_1).ifPresent(event::setRespawnLocation);
            Tasks.sync(plugin, () -> makeSpectator(player));
            return;
        }
        if (!alive.contains(player.getUniqueId())) {
            return;
        }
        spawns.get(spawns.randomDeathmatch()).ifPresent(event::setRespawnLocation);
        Tasks.sync(plugin, () -> {
            config.combat().apply(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 5));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 5));
            var loadout = loadouts.get(player.getUniqueId());
            if (loadout != null) {
                player.getInventory().setContents(loadout.contents());
                player.getInventory().setArmorContents(loadout.armor());
            }
        });
    }

    public void saveLoadout(Player player) {
        var inventory = player.getInventory();
        loadouts.put(player.getUniqueId(), new Loadout(copy(inventory.getContents()), copy(inventory.getArmorContents())));
    }

    private static ItemStack[] copy(ItemStack[] stacks) {
        return Arrays.stream(stacks).map(stack -> stack == null ? null : stack.clone()).toArray(ItemStack[]::new);
    }

    private void checkWinner() {
        if (phase == GamePhase.LOBBY || phase == GamePhase.ENDING || alive.size() > 1) {
            return;
        }
        for (var winner : alivePlayers()) {
            int coins = summary.awardWin(winner);
            Tasks.async(plugin, () -> {
                stats.add(winner.getUniqueId(), BlockcraftApi.get().realName(winner), Stat.WINS, 1);
                stats.add(winner.getUniqueId(), BlockcraftApi.get().realName(winner), Stat.POINTS, coins);
            });
        }
        endRound();
    }

    private void endRound() {
        setPhase(GamePhase.ENDING);
        scoreboard.refreshAll();
        countdown.cancel();
        hideTimer();
        summary.show();
        restart = ServerRestart.start(plugin, arena, config.restartSeconds());
    }

    /** Phases announce {@code seconds .. 1} and end one second after the last announcement. */
    private void startPhase(String barKey, int seconds, IntConsumer onSecond, Runnable onFinish) {
        var bar = new TimerBar(messages, barKey, seconds);
        timer = bar;
        countdown.start(seconds + 1, remaining -> {
            if (remaining > 0) {
                bar.update(remaining);
                onSecond.accept(remaining);
            }
        }, () -> {
            bar.hide();
            onFinish.run();
        });
    }

    private void hideTimer() {
        if (timer != null) {
            timer.hide();
        }
    }

    private void hideTimer(Player player) {
        if (timer != null) {
            timer.hide(player);
        }
    }

    private void makeSpectator(Player player) {
        alive.remove(player.getUniqueId());
        spectators.add(player.getUniqueId());
        PlayerReset.spectate(player);
    }


    private void giveLobbyItems(Player player) {
        var inventory = player.getInventory();
        inventory.setItem(0, Items.of(Material.CHEST).name(messages.get("item.perks")).build());
        if (player.hasPermission(Permissions.START)) {
            inventory.setItem(1, Items.of(Material.DIAMOND).name(messages.get("item.start")).build());
        }
        inventory.setItem(4, Items.of(Material.PLAYER_HEAD).name(messages.get("item.stats")).build());
        inventory.setItem(8, Items.of(Material.SLIME_BALL).name(messages.get("item.hub")).build());
    }

    private void broadcast(String key, TagResolver... resolvers) {
        Bukkit.broadcast(messages.get(key, resolvers));
    }

    private void setPhase(GamePhase phase) {
        this.phase = phase;
        BlockcraftApi.get().presence().state(phase.serverState());
    }
}
