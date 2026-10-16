package me.blockcraft.rush.game;

import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

public final class Game {

    private static final int MIN_TO_START = 2;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final long RESPAWN_DELAY_TICKS = 1L;

    private final Plugin plugin;
    private final RushConfig config;
    private final Spawns spawns;
    private final Messages messages;
    private final RoundSummary summary;
    private final Round round;
    private final Countdown countdown;
    private final LobbyCountdown lobbyCountdown;
    private final GameScoreboard scoreboard;

    private GamePhase phase = GamePhase.LOBBY;
    private final Map<UUID, Integer> emeralds = new HashMap<>();
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Map<UUID, Loadout> loadouts = new HashMap<>();
    private final Set<UUID> spentEmeralds = new HashSet<>();
    private final Set<UUID> purchased = new HashSet<>();

    private record Loadout(ItemStack[] contents, ItemStack[] armor) {
    }

    public Game(Plugin plugin, RushConfig config, Spawns spawns, Messages messages, StatsRepository stats, MapBundle map) {
        this.plugin = plugin;
        this.config = config;
        this.spawns = spawns;
        this.messages = messages;
        this.summary = new RoundSummary(plugin, messages,
                new RoundSummary.Rewards(config.killCoins(), config.winCoins()), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.RUSH_JOIN), summary, config.maxPlayers());
        this.countdown = round.timer(new Countdown(plugin));
        this.lobbyCountdown = new LobbyCountdown(plugin, round.arena(), config.lobbyCountdown(), config.minPlayers(),
                round::playerCount, this::beginSearch);
        this.scoreboard = new GameScoreboard(this, messages, map, config.maxPlayers());
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
        return round.isPlaying(player);
    }

    public List<Player> alivePlayers() {
        return round.players();
    }

    public int emeralds(Player player) {
        return emeralds.getOrDefault(player.getUniqueId(), 0);
    }

    public void setEmeralds(Player player, int amount) {
        emeralds.put(player.getUniqueId(), amount);
    }

    public void addEmeralds(Player player, int amount) {
        emeralds.merge(player.getUniqueId(), amount, Integer::sum);
        summary.minedEmeralds(player, amount);
    }

    public boolean withdrawEmeralds(Player player, int amount) {
        if (emeralds(player) < amount) {
            return false;
        }
        emeralds.put(player.getUniqueId(), emeralds(player) - amount);
        if (amount > 0) {
            spentEmeralds.add(player.getUniqueId());
        }
        return true;
    }

    public void bought(Player player) {
        purchased.add(player.getUniqueId());
    }

    public List<String> winnerNames() {
        return summary.winnerNames();
    }

    public int lives(Player player) {
        return lives.getOrDefault(player.getUniqueId(), 0);
    }

    public void join(Player player) {
        if (!round.join(player)) {
            spawns.teleport(player, SpawnPoint.SPECTATOR);
            scoreboard.refreshAll();
            return;
        }
        spawns.teleport(player, SpawnPoint.LOBBY);
        PlayerReset.reset(player, GameMode.ADVENTURE);
        giveLobbyItems(player);
        scoreboard.refreshAll();
        lobbyCountdown.start();
    }

    public void returnToPhaseSpawn(Player player) {
        spawns.teleport(player, switch (phase) {
            case LOBBY -> SpawnPoint.LOBBY;
            case SEARCH -> SpawnPoint.SEARCH;
            case SHOPPING -> SpawnPoint.SHOPPING;
            case DEATHMATCH, ENDING -> SpawnPoint.DEATHMATCH_1;
        });
    }

    public void follow(Player spectator, UUID friend) {
        round.follow(spectator, friend);
    }

    public void quit(Player player) {
        var id = player.getUniqueId();
        round.leave(player);
        emeralds.remove(id);
        lives.remove(id);
        loadouts.remove(id);
        checkWinner();
        scoreboard.refreshAll();
    }

    public void forceStart(Player requester) {
        if (round.playerCount() < MIN_TO_START) {
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
        round.stop();
    }

    private void beginSearch() {
        if (round.playerCount() < MIN_TO_START) {
            broadcast("lobby.cancelled");
            lobbyCountdown.start();
            return;
        }
        setPhase(GamePhase.SEARCH);
        Sounds.COUNTDOWN_GO.playToAll();
        messages.getLines("search.started", Formatter.number("minutes", config.searchSeconds() / SECONDS_PER_MINUTE)).forEach(Bukkit::broadcast);
        var pickaxe = Items.of(Material.IRON_PICKAXE).name(messages.get("item.pickaxe")).edit(meta -> {
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        }).build();
        for (var player : alivePlayers()) {
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
        round.spectators().forEach(spectator -> spawns.teleport(spectator, SpawnPoint.SEARCH));
        scoreboard.refreshAll();
        startPhase("timer.search", config.searchSeconds(), remaining -> {
            for (var player : alivePlayers()) {
                BlockcraftApi.get().notifications().status(player, messages.get("search.actionbar", Formatter.number("emeralds", emeralds(player))));
            }
            scoreboard.refreshAll();
            switch (remaining) {
                case 120, 60 -> {
                    Sounds.NOTIFY.playToAll();
                    broadcast("search.ends-minutes", Formatter.number("minutes", remaining / SECONDS_PER_MINUTE));
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
        var titleTimes = Title.Times.times(Duration.ofMillis(1250), Duration.ofMillis(1250), Duration.ofMillis(1250));
        for (var player : alivePlayers()) {
            BlockcraftApi.get().notifications().important(player, Title.title(
                    messages.get("shopping.title", Formatter.number("emeralds", emeralds(player))),
                    messages.get("shopping.subtitle"), titleTimes), Sounds.COUNTDOWN_GO);
            player.sendMessage(messages.get("shopping.info", Formatter.number("seconds", config.shoppingSeconds())));
            player.sendMessage(messages.get("shopping.hint"));
            player.getInventory().clear();
            player.getInventory().setItem(4, hotbar(player, GameItem.SHOP));
            spawns.teleport(player, SpawnPoint.SHOPPING);
            lives.put(player.getUniqueId(), config.lives());
        }
        round.spectators().forEach(spectator -> spawns.teleport(spectator, SpawnPoint.SHOPPING));
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
            removeHotbarItems(player);
            saveLoadout(player);
            spawns.teleport(player, spawns.deathmatch(spawnIndex++));
        }
        round.spectators().forEach(spectator -> spawns.teleport(spectator, SpawnPoint.DEATHMATCH_1));
        scoreboard.refreshAll();
        int trackerAfter = config.trackerAfterSeconds();
        startPhase("timer.deathmatch", config.deathmatchSeconds(), remaining -> {
            if (remaining == config.deathmatchSeconds() - trackerAfter) {
                round.handOutTrackers(trackerAfter / SECONDS_PER_MINUTE,
                        Items.of(Material.COMPASS).name(messages.get("item.compass")).lore(messages.get("item.compass-lore")).build());
            }
            round.announceEnd(remaining);
        }, () -> {
            broadcast("deathmatch.draw");
            endRound();
        });
    }

    public void handleDeath(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        if (phase != GamePhase.DEATHMATCH || !isAlive(victim)) {
            return;
        }
        var death = summary.resolveDeath(event);
        Bukkit.broadcast(death.line());
        int remaining = Math.max(0, lives(victim) - 1);
        death.killer().ifPresent(slayer -> {
            summary.awardKill(slayer, victim, remaining == 0 ? Defeat.OUT : Defeat.LIFE);
            slayer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1));
        });
        summary.death(victim);
        lives.put(victim.getUniqueId(), remaining);
        if (remaining == 0) {
            round.eliminate(victim);
        }
        round.respawn(victim, RESPAWN_DELAY_TICKS);
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
        if (round.isSpectator(player)) {
            spawns.get(SpawnPoint.DEATHMATCH_1).ifPresent(event::setRespawnLocation);
            Tasks.sync(plugin, () -> round.watch(player));
            return;
        }
        if (!isAlive(player)) {
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
        if (phase == GamePhase.LOBBY || phase == GamePhase.ENDING || round.playerCount() > 1) {
            return;
        }
        alivePlayers().forEach(winner -> {
            summary.awardWin(winner);
            if (!purchased.contains(winner.getUniqueId())) {
                summary.moment(winner, "emeraldrush", "minimalist");
            }
            if (!spentEmeralds.contains(winner.getUniqueId()) && emeralds(winner) >= config.dagobertEmeralds()) {
                summary.moment(winner, "emeraldrush", "dagobert", Formatter.number("emeralds", emeralds(winner)));
            }
        });
        endRound();
    }

    private void endRound() {
        phase = GamePhase.ENDING;
        round.end(config.restartSeconds());
        scoreboard.refreshAll();
    }

    /** Phases announce {@code seconds .. 1} and end one second after the last announcement. */
    private void startPhase(String barKey, int seconds, IntConsumer onSecond, Runnable onFinish) {
        var bar = round.bar(new TimerBar(messages, barKey, seconds));
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

    private void giveLobbyItems(Player player) {
        var inventory = player.getInventory();
        inventory.setItem(0, hotbar(player, GameItem.PERKS));
        if (player.hasPermission(Permissions.START)) {
            inventory.setItem(1, hotbar(player, GameItem.START));
        }
        inventory.setItem(4, hotbar(player, GameItem.STATS));
        inventory.setItem(8, hotbar(player, GameItem.HUB));
    }

    private ItemStack hotbar(Player viewer, GameItem item) {
        return item.build(viewer, switch (item) {
            case PERKS -> MenuIcon.EMERALDRUSH.item().name(messages.get("item.perks"));
            case START -> MenuIcon.START.item().name(messages.get("item.start"));
            case STATS -> MenuIcon.TOOL_PROFILE.mark(Items.head(viewer.getUniqueId(), BlockcraftApi.get().realName(viewer)))
                    .name(messages.get("item.stats"));
            case HUB -> MenuIcon.TOOL_HOME.item().name(messages.get("item.hub"));
            case SHOP -> MenuIcon.TOOL_SHOP.item().name(messages.get("item.shop"));
        });
    }

    private static void removeHotbarItems(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (HotbarItem.of(inventory.getItem(slot), GameItem.class).isPresent()) {
                inventory.setItem(slot, null);
            }
        }
    }

    private void broadcast(String key, TagResolver... resolvers) {
        Bukkit.broadcast(messages.get(key, resolvers));
    }

    private void setPhase(GamePhase phase) {
        this.phase = phase;
        round.enter(phase.roundPhase());
    }
}
