package me.blockcraft.rush.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerState;
import me.blockcraft.rush.Permissions;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.config.SpawnPoint;
import me.blockcraft.rush.config.Spawns;
import me.blockcraft.rush.scoreboard.GameScoreboard;
import me.blockcraft.rush.storage.StatCategory;
import me.blockcraft.rush.storage.StatsRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

public final class Game {

    private static final int LIVES = 3;
    private static final int KILL_COINS = 5;
    private static final int WIN_COINS = 15;
    private static final float SOUND_VOLUME = 4.0f;
    private static final float SOUND_PITCH = 4.0f;

    private final Plugin plugin;
    private final RushConfig config;
    private final Spawns spawns;
    private final Messages messages;
    private final StatsRepository stats;
    private final CoinsRepository coins;
    private final Countdown countdown;
    private final GameScoreboard scoreboard;

    private GamePhase phase = GamePhase.LOBBY;
    private boolean forcedStart;
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Set<UUID> builders = new HashSet<>();
    private final Map<UUID, Integer> emeralds = new HashMap<>();
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Map<UUID, Loadout> loadouts = new HashMap<>();

    private record Loadout(ItemStack[] contents, ItemStack[] armor) {
    }

    public Game(Plugin plugin, RushConfig config, Spawns spawns, Messages messages, StatsRepository stats,
                CoinsRepository coins) {
        this.plugin = plugin;
        this.config = config;
        this.spawns = spawns;
        this.messages = messages;
        this.stats = stats;
        this.coins = coins;
        this.countdown = new Countdown(plugin);
        this.scoreboard = new GameScoreboard(this, messages, config);
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

    public boolean isBuilder(Player player) {
        return builders.contains(player.getUniqueId());
    }

    public boolean toggleBuilder(Player player) {
        if (builders.remove(player.getUniqueId())) {
            return false;
        }
        builders.add(player.getUniqueId());
        return true;
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
        for (var world : Bukkit.getWorlds()) {
            Worlds.clearWeather(world);
            Worlds.setTime(world, 1000L);
        }
        if (phase != GamePhase.LOBBY) {
            makeSpectator(player);
            spawns.teleport(player, SpawnPoint.SPECTATOR);
            scoreboard.refreshAll();
            return;
        }
        alive.add(player.getUniqueId());
        spawns.teleport(player, SpawnPoint.LOBBY);
        resetPlayer(player, GameMode.ADVENTURE);
        messages.getLines("lobby.welcome", Formatter.number("emeralds", config.emeraldsPerOre())).forEach(player::sendMessage);
        giveLobbyItems(player);
        broadcast("lobby.joined", Placeholder.unparsed("player", player.getName()));
        scoreboard.refreshAll();
        if (Bukkit.getOnlinePlayers().size() >= config.minPlayers()) {
            startLobbyCountdown();
        } else if (!forcedStart) {
            countdown.cancel();
            Bukkit.getOnlinePlayers().forEach(online -> online.setLevel(config.lobbyCountdown()));
        }
    }

    /** Returns whether the player left as a spectator, whose departure is not announced. */
    public boolean quit(Player player) {
        var id = player.getUniqueId();
        boolean wasSpectator = spectators.remove(id);
        boolean wasAlive = alive.remove(id);
        builders.remove(id);
        emeralds.remove(id);
        lives.remove(id);
        loadouts.remove(id);
        if (wasAlive && phase != GamePhase.LOBBY && phase != GamePhase.ENDING) {
            Tasks.async(plugin, () -> stats.increment(id, player.getName(), StatCategory.LOSES, 1));
            checkWinner();
        }
        scoreboard.refreshAll();
        return wasSpectator;
    }

    public void forceStart(Player requester) {
        if (alive.size() < 2) {
            requester.sendMessage(messages.get("command.start-not-enough-players"));
            return;
        }
        if (phase != GamePhase.LOBBY) {
            requester.sendMessage(messages.get("command.start-not-lobby"));
            return;
        }
        if (countdown.isRunning() && countdown.remaining() <= 5) {
            requester.sendMessage(messages.get("command.start-already-started"));
            return;
        }
        startLobbyCountdown();
        forcedStart = true;
        countdown.remaining(6);
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
        countdown.cancel();
    }

    private void startLobbyCountdown() {
        if (countdown.isRunning()) {
            return;
        }
        forcedStart = false;
        startPhase(config.lobbyCountdown(), remaining -> {
            if (!forcedStart && Bukkit.getOnlinePlayers().size() < config.minPlayers()) {
                countdown.remaining(config.lobbyCountdown());
            }
            int seconds = countdown.remaining();
            Bukkit.getOnlinePlayers().forEach(online -> online.setLevel(seconds));
            if (seconds == 15 || seconds == 10 || seconds <= 5) {
                broadcast("lobby.countdown", Formatter.number("seconds", seconds));
            }
            if (seconds <= 5) {
                playSoundToAll(Sound.UI_BUTTON_CLICK);
            }
        }, this::beginSearch);
    }

    private void beginSearch() {
        if (alive.size() < 2) {
            forcedStart = false;
            broadcast("lobby.cancelled");
            Bukkit.getOnlinePlayers().forEach(online -> online.setLevel(config.lobbyCountdown()));
            return;
        }
        setPhase(GamePhase.SEARCH);
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
            player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, SOUND_VOLUME, SOUND_PITCH);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 2);
            player.getInventory().addItem(pickaxe.clone());
            spawns.teleport(player, SpawnPoint.SEARCH);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
        }
        scoreboard.refreshAll();
        startPhase(config.searchSeconds(), remaining -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(messages.get("search.actionbar", Formatter.number("emeralds", emeralds(player))));
            }
            scoreboard.refreshAll();
            switch (remaining) {
                case 120, 60 -> {
                    playSoundToAll(Sound.UI_BUTTON_CLICK);
                    broadcast("search.ends-minutes", Formatter.number("minutes", remaining / 60));
                }
                case 15, 5 -> {
                    playSoundToAll(Sound.UI_BUTTON_CLICK);
                    broadcast("search.ends-seconds", Formatter.number("seconds", remaining));
                }
                case 4, 3, 2, 1 -> playSoundToAll(Sound.ENTITY_PLAYER_LEVELUP);
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
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SOUND_VOLUME, SOUND_PITCH);
            player.showTitle(Title.title(
                    messages.get("shopping.title", Formatter.number("emeralds", emeralds(player))),
                    messages.get("shopping.subtitle"), titleTimes));
            player.sendMessage(messages.get("shopping.info", Formatter.number("seconds", config.shoppingSeconds())));
            player.sendMessage(messages.get("shopping.hint"));
            player.getInventory().clear();
            player.getInventory().setItem(4, shop.clone());
            spawns.teleport(player, SpawnPoint.SHOPPING);
            lives.put(player.getUniqueId(), LIVES);
        }
        spectatorPlayers().forEach(spectator -> spawns.teleport(spectator, SpawnPoint.SHOPPING));
        scoreboard.refreshAll();
        startPhase(config.shoppingSeconds(), remaining -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(messages.get("shopping.actionbar", Formatter.number("seconds", remaining)));
            }
            switch (remaining) {
                case 15, 5 -> {
                    playSoundToAll(Sound.UI_BUTTON_CLICK);
                    broadcast("shopping.ends", Formatter.number("seconds", remaining));
                }
                case 4, 3, 1 -> playSoundToAll(Sound.UI_BUTTON_CLICK);
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
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SOUND_VOLUME, SOUND_PITCH);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 10));
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().remove(Material.CHEST);
            saveLoadout(player);
            spawns.teleport(player, spawns.deathmatch(spawnIndex++));
        }
        spectatorPlayers().forEach(spectator -> spawns.teleport(spectator, SpawnPoint.DEATHMATCH_1));
        scoreboard.refreshAll();
        int trackerAt = config.deathmatchSeconds() - config.trackerAfterSeconds();
        startPhase(config.deathmatchSeconds(), remaining -> {
            if (remaining == trackerAt) {
                giveTrackers();
            } else if (remaining == 600 || remaining == 300 || remaining == 180) {
                broadcast("deathmatch.ends-minutes", Formatter.number("minutes", remaining / 60));
            } else if (remaining == 60) {
                broadcast("deathmatch.ends-minute");
            }
        }, () -> {
            broadcast("deathmatch.draw");
            Bukkit.shutdown();
        });
    }

    private void giveTrackers() {
        messages.getLines("deathmatch.tracker", Formatter.number("minutes", config.trackerAfterSeconds() / 60)).forEach(Bukkit::broadcast);
        var compass = Items.of(Material.COMPASS).name(messages.get("item.compass")).lore(messages.get("item.compass-lore")).build();
        for (var player : Bukkit.getOnlinePlayers()) {
            player.getInventory().addItem(compass.clone());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SOUND_VOLUME, SOUND_PITCH);
        }
    }

    public void handleDeath(Player victim, Optional<Player> killer) {
        if (phase != GamePhase.DEATHMATCH) {
            return;
        }
        var victimName = Placeholder.component("victim", victim.playerListName());
        killer.filter(slayer -> !slayer.equals(victim)).ifPresentOrElse(slayer -> {
            broadcast("deathmatch.killed", victimName, Placeholder.component("killer", slayer.playerListName()));
            Tasks.async(plugin, () -> {
                coins.add(slayer.getUniqueId(), slayer.getName(), KILL_COINS);
                stats.increment(slayer.getUniqueId(), slayer.getName(), StatCategory.KILLS, 1);
                stats.increment(slayer.getUniqueId(), slayer.getName(), StatCategory.POINTS, KILL_COINS);
            });
            slayer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1));
        }, () -> broadcast("deathmatch.died", victimName));
        Tasks.async(plugin, () -> stats.increment(victim.getUniqueId(), victim.getName(), StatCategory.DEATHS, 1));
        int remaining = Math.max(0, lives(victim) - 1);
        lives.put(victim.getUniqueId(), remaining);
        if (remaining == 0) {
            alive.remove(victim.getUniqueId());
            spectators.add(victim.getUniqueId());
            Tasks.async(plugin, () -> stats.increment(victim.getUniqueId(), victim.getName(), StatCategory.LOSES, 1));
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
        setPhase(GamePhase.ENDING);
        for (var winner : alivePlayers()) {
            broadcast("ending.winner", Placeholder.unparsed("player", winner.getName()));
            Tasks.async(plugin, () -> {
                stats.increment(winner.getUniqueId(), winner.getName(), StatCategory.WINS, 1);
                coins.add(winner.getUniqueId(), winner.getName(), WIN_COINS);
                stats.increment(winner.getUniqueId(), winner.getName(), StatCategory.POINTS, WIN_COINS);
            });
            var firework = winner.getWorld().spawn(winner.getLocation(), Firework.class);
            var meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().withColor(Color.RED).withFade(Color.BLUE).with(FireworkEffect.Type.BALL_LARGE).build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        }
        scoreboard.refreshAll();
        startPhase(config.restartSeconds(), remaining -> {
            if (remaining == 10 || remaining <= 5) {
                broadcast("ending.restart", Formatter.number("seconds", remaining));
            }
            if (remaining == 5) {
                clearDroppedItems();
            }
        }, () -> {
            broadcast("ending.restart-now");
            Bukkit.shutdown();
        });
    }

    /** Phases announce {@code seconds .. 1} and end one second after the last announcement. */
    private void startPhase(int seconds, IntConsumer onSecond, Runnable onFinish) {
        countdown.start(seconds + 1, remaining -> {
            if (remaining > 0) {
                onSecond.accept(remaining);
            }
        }, onFinish);
    }

    private void makeSpectator(Player player) {
        alive.remove(player.getUniqueId());
        spectators.add(player.getUniqueId());
        player.setExp(0.0f);
        player.setLevel(0);
        resetHealth(player);
        player.setFoodLevel(20);
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void resetPlayer(Player player, GameMode mode) {
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setExp(0.0f);
        player.setLevel(0);
        resetHealth(player);
        player.setGameMode(mode);
        player.setFoodLevel(20);
        player.getInventory().clear();
    }

    private static void resetHealth(Player player) {
        var maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH));
        maxHealth.setBaseValue(20.0);
        player.setHealth(20.0);
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

    private static void clearDroppedItems() {
        for (var world : Bukkit.getWorlds()) {
            world.getEntitiesByClass(Item.class).forEach(Entity::remove);
        }
    }

    private void broadcast(String key, TagResolver... resolvers) {
        Bukkit.broadcast(messages.get(key, resolvers));
    }

    private static void playSoundToAll(Sound sound) {
        for (var player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, SOUND_VOLUME, SOUND_PITCH);
        }
    }

    private void setPhase(GamePhase phase) {
        this.phase = phase;
        BlockcraftApi.get().presence().state(switch (phase) {
            case LOBBY -> ServerState.LOBBY;
            case SEARCH, SHOPPING, DEATHMATCH -> ServerState.INGAME;
            case ENDING -> ServerState.RESTART;
        });
    }
}
