package me.blockcraft.knockbackffa;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.coins.Coins;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.game.HarmlessHit;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.Spectators;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.spectate.SpectateRequestEvent;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.vanish.VanishChangeEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerState;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class FfaGame implements Listener, AutoCloseable {


    private final Plugin plugin;
    private final FfaConfig config;
    private final Messages messages;
    private final FfaArena arena;
    private final StatsRepository stats;
    private final FfaScores scores;
    private final Set<UUID> playing = new LinkedHashSet<>();
    private final Set<UUID> respawning = new HashSet<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    /** Set by {@link #start}, since it registers its listeners with the running server. */
    private Spectators spectators;
    private final GameSidebar sidebar;
    private FfaKit kit = FfaKit.STANDARD;
    private int kitIn;
    private int launchIn;
    private int rotationIn;

    FfaGame(Plugin plugin, FfaConfig config, Messages messages, FfaArena arena, StatsRepository stats) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.arena = arena;
        this.stats = stats;
        scores = new FfaScores(config.creditWindow(), config.rewardCooldown());
        sidebar = new GameSidebar(messages.get("scoreboard.title"),
                () -> new GameSidebar.Headcount(playing.size(), spectators.count()), () -> false);
        kitIn = config.kits().intervalSeconds();
        launchIn = config.launchSeconds();
        rotationIn = config.rotationSeconds();
    }

    void start() {
        spectators = new Spectators(plugin, new Arena(messages, Sounds.JOIN), this::players, () -> false);
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
        player.sendMessage(messages.get("join.introduction", Formatter.number("seconds", config.kits().intervalSeconds())));
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
        spectators.release(player);
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
        spectators.watch(player);
        player.teleport(arena.watch());
        player.sendMessage(messages.get("join.spectator"));
        presence();
    }

    private List<Player> players() {
        return playing.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    private void spawn(Player player) {
        PlayerReset.reset(player, GameMode.SURVIVAL);
        PlayerReset.settle(player);
        CombatStyle.LEGACY.apply(player);
        removeProjectiles(player);
        equip(player);
        player.teleport(arena.spawn());
        protectedUntil.put(player.getUniqueId(), System.nanoTime() + config.spawnProtection().toNanos());
    }

    private void equip(Player player) {
        var inventory = player.getInventory();
        inventory.clear();
        var stick = FfaItem.STICK.build(Items.of(Material.STICK).name(messages.get("item.stick")));
        stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, kit == FfaKit.HEAVY ? config.kits().heavyKnockback() : config.knockback());
        inventory.setItem(0, BlockcraftApi.get().weapons().stick(player, stick));
        var kits = config.kits();
        switch (kit) {
            case STANDARD, HEAVY -> { }
            case ARCHER -> {
                inventory.setItem(1, unbreakable(Items.of(Material.BOW).name(messages.get("item.bow"))));
                inventory.setItem(2, FfaItem.TOOL.build(Items.of(Material.ARROW, kits.ammo()).name(messages.get("item.arrow"))));
            }
            case ANGLER -> inventory.setItem(1, unbreakable(Items.of(Material.FISHING_ROD).name(messages.get("item.rod"))));
            case SNOWMAN -> inventory.setItem(1, FfaItem.TOOL.build(Items.of(Material.SNOWBALL, kits.ammo()).name(messages.get("item.snowball"))));
            case GRAPPLE -> inventory.setItem(1, FfaItem.GRAPPLE.build(Items.of(Material.FISHING_ROD).name(messages.get("item.grapple"))
                    .lore(messages.get("item.grapple-hint")).edit(meta -> meta.setUnbreakable(true))));
            case ENDERMAN -> inventory.setItem(1, FfaItem.TOOL.build(Items.of(Material.ENDER_PEARL, kits.pearls()).name(messages.get("item.pearl"))));
        }
        inventory.setItem(7, FfaItem.WATCH.build(Items.of(Material.ENDER_EYE).name(messages.get("item.watch"))));
        inventory.setItem(8, FfaItem.HUB.build(Items.of(Material.MAGMA_CREAM).name(messages.get("item.hub"))));
    }

    private static ItemStack unbreakable(Items.Builder item) {
        return FfaItem.TOOL.build(item.edit(meta -> meta.setUnbreakable(true)));
    }

    private void removeProjectiles(Player shooter) {
        arena.world().getEntitiesByClass(Projectile.class).stream()
                .filter(projectile -> shooter.equals(projectile.getShooter())).forEach(Entity::remove);
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
        if (config.rotationSeconds() > 0 && !playing.isEmpty()) {
            if (--rotationIn <= 0) {
                rotate();
            } else if (rotationIn == 30 || rotationIn <= 5) {
                Bukkit.broadcast(messages.get("rotation.countdown", Formatter.number("seconds", rotationIn)));
            }
        }
        if (!playing.isEmpty() && --kitIn <= 0) {
            changeKit();
        }
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
            showSidebar(player);
            if (protectedNow(player) && active(player)) {
                BlockcraftApi.get().notifications().status(player, messages.get("spawn.protected"));
            }
            if (kitIn <= config.kits().warningSeconds() && active(player)) {
                BlockcraftApi.get().notifications().status(player, messages.get("kit.countdown", Formatter.number("seconds", kitIn)));
            }
            if (config.launch() && launchIn <= config.kits().warningSeconds() && active(player)) {
                BlockcraftApi.get().notifications().status(player, messages.get("launch.countdown", Formatter.number("seconds", launchIn)));
            }
        }
    }

    private void showSidebar(Player viewer) {
        var icons = GameSidebar.icons(viewer);
        var sections = new ArrayList<GameSidebar.Section>();
        sections.add(new GameSidebar.Section(messages.get("scoreboard.kit", icons), GameSidebar.value(messages.get("scoreboard.kit-value",
                Placeholder.component("kit", messages.get("kit." + kit.key())), Formatter.number("kit_seconds", kitIn)))));
        if (playing.contains(viewer.getUniqueId())) {
            var score = scores.score(viewer.getUniqueId());
            sections.add(new GameSidebar.Section(messages.get("scoreboard.score", icons), List.of(
                    messages.get("scoreboard.kills", Formatter.number("kills", score.kills())),
                    messages.get("scoreboard.deaths", Formatter.number("deaths", score.deaths())),
                    messages.get("scoreboard.streak", Formatter.number("streak", score.streak())))));
        }
        sidebar.round(viewer, config.capacity(), 0, messages.get("arena." + arena.name()), sections);
    }

    private void changeKit() {
        kit = kit.next(ThreadLocalRandom.current());
        kitIn = config.kits().intervalSeconds();
        var title = Title.title(messages.get("kit.title"), messages.get("kit.subtitle", Placeholder.component("kit", messages.get("kit." + kit.key()))));
        for (var player : Bukkit.getOnlinePlayers()) {
            if (active(player)) {
                removeProjectiles(player);
                equip(player);
                BlockcraftApi.get().notifications().important(player, title, Sounds.EQUIP);
            }
        }
    }

    private void rotate() {
        rotationIn = config.rotationSeconds();
        scores.clearHits();
        arena.world().getEntitiesByClass(Projectile.class).forEach(Entity::remove);
        Bukkit.getOnlinePlayers().stream().filter(this::active).forEach(player -> player.teleport(arena.watch()));
        arena.rotate();
        Bukkit.getOnlinePlayers().stream().filter(this::active).forEach(this::spawn);
        Bukkit.broadcast(messages.get("rotation.changed", Placeholder.component("map", messages.get("arena." + arena.name()))));
        launchIn = config.launchSeconds();
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
        spectators.forget(player.getUniqueId());
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
            // Paper drops vanilla knockback for a player hit that dealt no damage at all, as snowballs and rods do.
            if (event.getDamager() instanceof Projectile && !(event.getDamager() instanceof AbstractArrow)) {
                var away = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
                victim.knockback(config.kits().projectilePush(), -away.getX(), -away.getZ());
                victim.setVelocity(victim.getVelocity());
                victim.playHurtAnimation(attacker.getYaw());
            }
        }
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player
                && HotbarItem.of(player.getInventory().getItemInMainHand(), FfaItem.class).filter(item -> item == FfaItem.STICK).isPresent()) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && !(projectile instanceof EnderPearl)
                && projectile.getShooter() instanceof Player player) {
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
        var player = event.getPlayer();
        if (event.isVanished()) {
            if (playing.contains(player.getUniqueId()) && scores.inCombat(player.getUniqueId(), System.nanoTime())) {
                countDeath(player);
            }
            remove(player);
            spectators.forget(player.getUniqueId());
            presence();
        } else {
            // Staff mode restores flight after publishing the vanish event.
            Tasks.sync(plugin, () -> {
                if (player.isOnline() && !BlockcraftApi.get().isVanished(player) && !playing.contains(player.getUniqueId())) {
                    watch(player);
                }
            });
        }
    }

    /** Before {@link Spectators}, which takes the new spectator to the friend they came for. */
    @EventHandler(priority = EventPriority.LOW)
    public void onSpectate(SpectateRequestEvent event) {
        watch(event.getPlayer());
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
        switch (HotbarItem.of(event.getItem(), FfaItem.class).orElse(null)) {
            case TOOL, GRAPPLE -> {
                return;
            }
            case HUB -> ProxyMessaging.hub(plugin, player);
            case WATCH -> watch(player);
            case null, default -> { }
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        var player = event.getPlayer();
        if (event.getState() != PlayerFishEvent.State.IN_GROUND || !active(player)
                || HotbarItem.of(player.getInventory().getItemInMainHand(), FfaItem.class).filter(FfaItem.GRAPPLE::equals).isEmpty()) {
            return;
        }
        var pull = event.getHook().getLocation().toVector().subtract(player.getLocation().toVector());
        if (pull.lengthSquared() < 1) {
            return;
        }
        player.setVelocity(pull.normalize().multiply(config.kits().grapplePull()).setY(Math.max(pull.getY() / 4, config.kits().grappleLift())));
        player.setCooldown(Material.FISHING_ROD, config.kits().grappleCooldownTicks());
        Sounds.GRAPPLE.play(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectile(ProjectileHitEvent event) {
        if (event.getEntity() instanceof AbstractArrow arrow) {
            Tasks.later(plugin, 1L, arrow::remove);
        }
    }

    @Override
    public void close() {
        BlockcraftApi.get().presence().state(ServerState.RESTART);
        arena.world().getEntitiesByClass(Projectile.class).forEach(Entity::remove);
    }
}
