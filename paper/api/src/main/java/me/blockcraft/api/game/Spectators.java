package me.blockcraft.api.game;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.spectate.SpectateRequestEvent;
import me.blockcraft.api.task.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The spectator mode every game shares, with or without a {@link Round}: spectators fly unseen in adventure mode with
 * a hotbar to pick a player, step to the previous or next one and leave, only spectators see each other, and
 * {@link SpectatorGuard} keeps them off the game. While {@code separateChat} holds, what a spectator writes reaches
 * only the other spectators.
 */
public final class Spectators implements Listener {

    private static final Set<UUID> WATCHING = ConcurrentHashMap.newKeySet();

    private final Plugin plugin;
    private final BlockcraftApi api;
    private final Arena arena;
    private final Supplier<List<Player>> players;
    private final BooleanSupplier separateChat;
    // Chat reads these off the main thread.
    private final Set<UUID> spectators = new CopyOnWriteArraySet<>();
    private final Map<UUID, UUID> watched = new HashMap<>();

    /** {@code players} are those a spectator can watch, in the order the hotbar steps through them. */
    public Spectators(Plugin plugin, Arena arena, Supplier<List<Player>> players, BooleanSupplier separateChat) {
        this.plugin = plugin;
        this.api = BlockcraftApi.get();
        this.arena = arena;
        this.players = players;
        this.separateChat = separateChat;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(new SpectatorGuard(this::isSpectator), plugin);
    }

    /** Whether the player watches a game on this server, which their game mode no longer tells. */
    public static boolean isWatching(Player player) {
        return WATCHING.contains(player.getUniqueId());
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public List<Player> list() {
        return spectators.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    /** Vanished staff watch uncounted. */
    public int count() {
        return (int) list().stream().filter(player -> !api.isVanished(player)).count();
    }

    /** Counts the player as a spectator before {@link #watch} puts them into the state, as a death does until the respawn. */
    public void add(Player player) {
        spectators.add(player.getUniqueId());
    }

    /**
     * Puts a spectator into the state they watch in: flying unseen by the players, with the spectator hotbar. A dead
     * player is put into this state after respawning.
     */
    public void watch(Player player) {
        spectators.add(player.getUniqueId());
        WATCHING.add(player.getUniqueId());
        if (player.isDead()) {
            Tasks.later(plugin, 1L, () -> {
                if (player.isOnline() && player.isDead()) {
                    player.spigot().respawn();
                }
            });
            return;
        }
        PlayerReset.spectate(player);
        for (var other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            if (isSpectator(other)) {
                player.showPlayer(plugin, other);
            } else {
                other.hidePlayer(plugin, player);
            }
        }
        SpectatorItem.give(player, api.messages());
    }

    /** The spectator plays again: everyone sees them, and they no longer see the spectators. */
    public void release(Player player) {
        forget(player.getUniqueId());
        for (var other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.showPlayer(plugin, player);
                if (isSpectator(other)) {
                    player.hidePlayer(plugin, other);
                }
            }
        }
    }

    /** Who each spectator watched last no longer counts, as when a new round starts. */
    void clearTargets() {
        watched.clear();
    }

    /** No longer counted or followed, as when they leave the server. */
    public void forget(UUID id) {
        spectators.remove(id);
        watched.remove(id);
        WATCHING.remove(id);
    }

    /** Runs after core's chat format, whose line it marks rather than replaces. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!separateChat.getAsBoolean() || !isSpectator(event.getPlayer())) {
            return;
        }
        event.viewers().removeIf(viewer -> viewer instanceof Player reader && !isSpectator(reader));
        var tag = arena.spectatorChatTag().append(Component.space());
        var line = event.renderer();
        event.renderer((ChatRenderer) (source, displayName, message, viewer) ->
                tag.append(line.render(source, displayName, message, viewer)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        if (isSpectator(player) && isWatching(player)) {
            Tasks.sync(plugin, () -> {
                if (player.isOnline() && isSpectator(player)) {
                    watch(player);
                }
            });
        }
    }

    /** The spectator came to watch a friend, which the proxy names only after the join. */
    @EventHandler
    public void onSpectateRequest(SpectateRequestEvent event) {
        var spectator = event.getPlayer();
        if (!isSpectator(spectator)) {
            return;
        }
        var friend = event.target();
        var target = Bukkit.getPlayer(friend);
        if (target != null && spectator.canSee(target)) {
            watched.put(spectator.getUniqueId(), friend);
            arena.follow(spectator, target);
        }
    }

    @EventHandler
    public void onSpectatorItem(PlayerInteractEvent event) {
        var spectator = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick() || !isSpectator(spectator)) {
            return;
        }
        HotbarItem.of(event.getItem(), SpectatorItem.class).ifPresent(item -> {
            event.setCancelled(true);
            switch (item) {
                case PICK_PLAYER -> pick(spectator);
                case PREVIOUS_PLAYER -> step(spectator, -1);
                case NEXT_PLAYER -> step(spectator, 1);
                case LEAVE_ROUND -> ProxyMessaging.hub(plugin, spectator);
            }
        });
    }

    private void pick(Player spectator) {
        var targets = watchable(spectator);
        if (targets.isEmpty()) {
            nobodyToWatch(spectator);
            return;
        }
        new SpectatorMenu(api.messages(), targets, this::jump).open(spectator);
    }

    /** Goes through the players in join order from the one last watched; without one, from the first or the last. */
    private void step(Player spectator, int step) {
        var targets = watchable(spectator);
        if (targets.isEmpty()) {
            nobodyToWatch(spectator);
            return;
        }
        int current = targets.stream().map(Player::getUniqueId).toList().indexOf(watched.get(spectator.getUniqueId()));
        int next = current < 0 ? (step > 0 ? 0 : targets.size() - 1) : Math.floorMod(current + step, targets.size());
        jump(spectator, targets.get(next));
    }

    private void jump(Player spectator, Player target) {
        if (!isSpectator(spectator) || !players.get().contains(target) || !spectator.canSee(target)) {
            return;
        }
        watched.put(spectator.getUniqueId(), target.getUniqueId());
        arena.jump(spectator, target);
    }

    private List<Player> watchable(Player spectator) {
        return players.get().stream().filter(spectator::canSee).toList();
    }

    private void nobodyToWatch(Player spectator) {
        api.notifications().feedback(spectator, api.messages().get("spectator.nobody"));
        Sounds.MENU_DENY.play(spectator);
    }
}
