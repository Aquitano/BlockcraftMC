package me.blockcraft.knockbackffa;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.coins.Coins;
import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.HarmlessHit;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.scoreboard.Sidebar;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.spectate.SpectateRequestEvent;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.vanish.VanishChangeEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class FfaGame implements Listener, AutoCloseable {

    private static final NamespacedKey REPULSOR = new NamespacedKey("knockbackffa", "repulsor");

    private final Plugin plugin;
    private final FfaConfig config;
    private final Messages messages;
    private final FfaArena arena;
    private final StatsRepository stats;
    private final FfaScores scores;
    private final Set<UUID> playing = new HashSet<>();
    private final Set<UUID> respawning = new HashSet<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<Block, Integer> pickups = new HashMap<>();
    private final Sidebar sidebar;
    private int launchIn;

    FfaGame(Plugin plugin, FfaConfig config, Messages messages, FfaArena arena, StatsRepository stats) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.arena = arena;
        this.stats = stats;
        scores = new FfaScores(config.creditWindow(), config.rewardCooldown());
        sidebar = new Sidebar(messages.get("scoreboard.title"));
        launchIn = config.launchSeconds();
    }

    void start() {
        presence();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    void join(Player player) {
        if (BlockcraftApi.get().isVanished(player)) {
            player.setGameMode(GameMode.ADVENTURE);
            BlockcraftApi.get().flight().set(player, true);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setCollidable(false);
            player.teleport(arena.watch());
            presence();
            return;
        }
        play(player);
        player.sendMessage(messages.get("join.introduction"));
    }

    private void play(Player player) {
        if (BlockcraftApi.get().isVanished(player)) {
            return;
        }
        if (!playing.contains(player.getUniqueId()) && playing.size() >= config.capacity()) {
            watch(player);
            return;
        }
        if (playing.contains(player.getUniqueId())) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(null);
        }
        playing.add(player.getUniqueId());
        scores.join(player.getUniqueId());
        spawn(player);
        player.sendMessage(messages.get("join.playing"));
        presence();
    }

    private void watch(Player player) {
        if (playing.contains(player.getUniqueId()) && scores.inCombat(player.getUniqueId(), System.nanoTime())) {
            countDeath(player);
        }
        remove(player);
        PlayerReset.reset(player, GameMode.SPECTATOR);
        player.teleport(arena.watch());
        player.sendMessage(messages.get("join.spectator"));
        presence();
    }

    private void spawn(Player player) {
        PlayerReset.reset(player, GameMode.SURVIVAL);
        PlayerReset.settle(player);
        CombatStyle.LEGACY.apply(player);
        var stick = FfaItem.STICK.build(Items.of(Material.STICK).name(messages.get("item.stick")));
        stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, config.knockback());
        player.getInventory().setItem(0, BlockcraftApi.get().weapons().stick(player, stick));
        player.getInventory().setItem(7, FfaItem.WATCH.build(Items.of(Material.ENDER_EYE).name(messages.get("item.watch"))));
        player.getInventory().setItem(8, FfaItem.HUB.build(Items.of(Material.MAGMA_CREAM).name(messages.get("item.hub"))));
        player.teleport(arena.spawn());
        protectedUntil.put(player.getUniqueId(), System.nanoTime() + config.spawnProtection().toNanos());
    }

    private boolean active(Player player) {
        return playing.contains(player.getUniqueId()) && !respawning.contains(player.getUniqueId()) && !player.isDead()
                && player.getGameMode() == GameMode.SURVIVAL && player.getWorld().equals(arena.world())
                && !BlockcraftApi.get().isVanished(player) && !BlockcraftApi.get().buildMode().isEnabled(player);
    }

    private boolean protectedNow(Player player) {
        var until = protectedUntil.get(player.getUniqueId());
        return until != null && System.nanoTime() - until < 0;
    }

    private boolean canHit(Player attacker, Player victim) {
        return !attacker.equals(victim) && active(attacker) && active(victim) && !protectedNow(attacker) && !protectedNow(victim);
    }

    private void countDeath(Player victim) {
        var api = BlockcraftApi.get();
        var id = victim.getUniqueId();
        var name = api.realName(victim);
        Tasks.async(plugin, () -> stats.add(id, name, Stat.DEATHS, 1));
        scores.death(id, System.nanoTime()).ifPresentOrElse(kill -> {
            var killer = Bukkit.getPlayer(kill.killer());
            if (killer == null) {
                return;
            }
            var killerName = api.realName(killer);
            Tasks.async(plugin, () -> stats.add(kill.killer(), killerName, Stat.KILLS, 1));
            killer.sendMessage(messages.get("death.kill", Placeholder.unparsed("player", victim.getName())));
            victim.sendMessage(messages.get("death.killed", Placeholder.unparsed("player", killer.getName())));
            Sounds.NOTIFY.play(killer);
            int streak = scores.score(kill.killer()).streak();
            if (api.streaks().announces(streak)) {
                Bukkit.broadcast(messages.get("death.streak", Placeholder.unparsed("player", killer.getName()), Formatter.number("streak", streak)));
                Sounds.STREAK.play(killer);
            }
            if (kill.rewarded() && config.killCoins() > 0) {
                Coins.award(plugin, killer, config.killCoins());
                Tasks.async(plugin, () -> stats.add(kill.killer(), killerName, Stat.POINTS, config.killCoins()));
            }
        }, () -> victim.sendMessage(messages.get("death.fell")));
    }

    private void remove(Player player) {
        playing.remove(player.getUniqueId());
        scores.leave(player.getUniqueId());
        protectedUntil.remove(player.getUniqueId());
        respawning.remove(player.getUniqueId());
    }

    private void presence() {
        var presence = BlockcraftApi.get().presence();
        presence.maxPlayers(Math.min(Bukkit.getMaxPlayers(),
                Bukkit.getOnlinePlayers().size() + Math.max(0, config.capacity() - playing.size())));
        presence.state(ServerState.OPEN);
    }

    private void tick() {
        pickups.replaceAll((block, seconds) -> seconds - 1);
        pickups.entrySet().removeIf(entry -> {
            if (entry.getValue() > 0) {
                return false;
            }
            entry.getKey().setType(Material.EMERALD_BLOCK, false);
            return true;
        });
        if (config.launch() && --launchIn <= 0) {
            launchIn = config.launchSeconds();
            for (var player : Bukkit.getOnlinePlayers()) {
                if (active(player) && !protectedNow(player)) {
                    player.setVelocity(player.getVelocity().setY(1.4));
                    player.sendMessage(messages.get("launch.now"));
                }
            }
        }
        for (var player : Bukkit.getOnlinePlayers()) {
            var score = scores.score(player.getUniqueId());
            sidebar.show(player, messages.getLines("scoreboard.lines", Formatter.number("players", playing.size()),
                    Formatter.number("max", config.capacity()), Formatter.number("kills", score.kills()),
                    Formatter.number("deaths", score.deaths()), Formatter.number("streak", score.streak())));
            if (protectedNow(player) && active(player)) {
                BlockcraftApi.get().notifications().status(player, messages.get("spawn.protected"));
            }
            if (config.launch() && launchIn <= 5 && active(player)) {
                BlockcraftApi.get().notifications().status(player, messages.get("launch.countdown", Formatter.number("seconds", launchIn)));
            }
        }
    }

    LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("ffa").requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("command.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("play").executes(context -> {
                    play(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("spectate").executes(context -> {
                    watch(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                })).build();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        join(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        var player = event.getPlayer();
        if (playing.contains(player.getUniqueId()) && scores.inCombat(player.getUniqueId(), System.nanoTime())) {
            countDeath(player);
        }
        remove(player);
        Tasks.later(plugin, 1L, this::presence);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent hit) {
            var attacker = attacker(hit);
            if (attacker != null && canHit(attacker, victim)) {
                HarmlessHit.land(event, victim);
                return;
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        var attacker = attacker(event);
        if (attacker != null && event.getEntity() instanceof Player victim && canHit(attacker, victim)) {
            scores.hit(attacker.getUniqueId(), victim.getUniqueId(), System.nanoTime());
            if (event.getDamager() instanceof Snowball) {
                var direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0);
                if (direction.lengthSquared() < 0.01) {
                    direction = attacker.getLocation().getDirection().setY(0);
                }
                if (direction.lengthSquared() > 0.01) {
                    victim.setVelocity(direction.normalize().multiply(1.4).setY(0.5));
                    victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation(), 8, 0.3, 0.3, 0.3, 0);
                }
            }
        }
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player
                && HotbarItem.of(player.getInventory().getItemInMainHand(), FfaItem.class).filter(item -> item == FfaItem.STICK).isPresent()) {
            return player;
        }
        if (event.getDamager() instanceof Snowball snowball && snowball.getPersistentDataContainer().has(REPULSOR)
                && snowball.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition() && active(event.getPlayer()) && event.getTo().getY() < FfaArena.FALL) {
            countDeath(event.getPlayer());
            spawn(event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        var player = event.getPlayer();
        event.deathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (playing.contains(player.getUniqueId()) && respawning.add(player.getUniqueId())) {
            countDeath(player);
        }
        Tasks.later(plugin, 1L, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(arena.spawn());
        Tasks.later(plugin, 1L, () -> {
            var player = event.getPlayer();
            if (player.isOnline() && respawning.remove(player.getUniqueId()) && playing.contains(player.getUniqueId())) {
                spawn(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVanish(VanishChangeEvent event) {
        if (event.isVanished()) {
            var player = event.getPlayer();
            if (playing.contains(player.getUniqueId()) && scores.inCombat(player.getUniqueId(), System.nanoTime())) {
                countDeath(player);
            }
            remove(player);
            presence();
        }
    }

    @EventHandler
    public void onSpectate(SpectateRequestEvent event) {
        watch(event.getPlayer());
        var target = Bukkit.getPlayer(event.target());
        if (target != null && active(target) && event.getPlayer().canSee(target)) {
            event.getPlayer().teleport(target.getLocation());
            event.getPlayer().setSpectatorTarget(target);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()
                || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        var player = event.getPlayer();
        if (!active(player)) {
            return;
        }
        event.setCancelled(true);
        var item = HotbarItem.of(event.getItem(), FfaItem.class);
        if (item.isPresent()) {
            switch (item.get()) {
                case HUB -> ProxyMessaging.hub(plugin, player);
                case WATCH -> watch(player);
                case RESCUE -> {
                    player.getInventory().setItemInMainHand(null);
                    player.setFallDistance(0);
                    player.setVelocity(player.getVelocity().setY(1.15));
                    Sounds.TELEPORT.play(player);
                }
                case REPULSOR -> {
                    player.getInventory().setItemInMainHand(null);
                    var snowball = player.launchProjectile(Snowball.class);
                    snowball.getPersistentDataContainer().set(REPULSOR, PersistentDataType.BYTE, (byte) 1);
                    Tasks.later(plugin, 100L, snowball::remove);
                }
                default -> { }
            }
        }
        var block = event.getClickedBlock();
        if (!protectedNow(player) && block != null && arena.pickups().contains(block) && !pickups.containsKey(block)) {
            var held = player.getInventory().getItem(1);
            if (held != null && !held.isEmpty()) {
                player.sendMessage(messages.get("pickup.full"));
                return;
            }
            var special = ThreadLocalRandom.current().nextBoolean() ? FfaItem.RESCUE : FfaItem.REPULSOR;
            var material = special == FfaItem.RESCUE ? Material.BLAZE_ROD : Material.SNOWBALL;
            player.getInventory().setItem(1, special.build(Items.of(material).name(messages.get("item." + special.name().toLowerCase(java.util.Locale.ROOT)))));
            block.setType(Material.GRAY_CONCRETE, false);
            pickups.put(block, config.pickupSeconds());
            player.sendMessage(messages.get("pickup.received"));
            Sounds.NOTIFY.play(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectile(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !snowball.getPersistentDataContainer().has(REPULSOR)) {
            return;
        }
        Tasks.later(plugin, 1L, snowball::remove);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!BlockcraftApi.get().buildMode().isEnabled(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!BlockcraftApi.get().buildMode().isEnabled(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!BlockcraftApi.get().buildMode().isEnabled(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !BlockcraftApi.get().buildMode().isEnabled(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!BlockcraftApi.get().buildMode().isEnabled(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !BlockcraftApi.get().buildMode().isEnabled(player)
                && !(event.getView().getTopInventory().getHolder(false) instanceof Menu)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && !BlockcraftApi.get().buildMode().isEnabled(player)
                && !(event.getView().getTopInventory().getHolder(false) instanceof Menu)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void close() {
        BlockcraftApi.get().presence().state(ServerState.RESTART);
        arena.pickups().forEach(block -> block.setType(Material.EMERALD_BLOCK, false));
        arena.world().getEntitiesByClass(Snowball.class).stream()
                .filter(snowball -> snowball.getPersistentDataContainer().has(REPULSOR)).forEach(Snowball::remove);
    }
}
