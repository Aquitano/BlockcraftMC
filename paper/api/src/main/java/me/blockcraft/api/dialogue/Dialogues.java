package me.blockcraft.api.dialogue;

import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.presentation.PresentationScope;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The pack's talking panel above the hotbar. A dialogue that asks something holds the player's input until they answer:
 * scrolling picks, sneaking confirms. One that only tells reads itself out and lets the player play on underneath, so
 * a greeting never stops a newcomer from using what it describes.
 */
public final class Dialogues implements Listener, AutoCloseable {
    private record Viewing(DialogueSession session, Messages text, Consumer<String> completed, long expires, PresentationScope scope,
                           boolean holds) {}
    private record Drawn(Component bar, long at) {}

    /** Undoes a registration; closing it cannot fail. */
    public interface Handle extends AutoCloseable {
        @Override
        void close();
    }

    /** How long an unchanged panel stays sent before it is sent again, well inside the time the client keeps showing it. */
    private static final long REFRESH_TICKS = 20;

    private final Plugin plugin;
    private final Messages messages;
    private final ResourcePacks packs;
    private final Dialogue.Loaded demo;
    private final int charactersPerTick;
    private final int timeoutTicks;
    private final int readTicks;
    private final Map<UUID, Viewing> viewers = new HashMap<>();
    private final Map<UUID, Long> readSince = new HashMap<>();
    private final Map<UUID, Drawn> drawn = new HashMap<>();
    private final List<Function<Player, Optional<Component>>> beside = new ArrayList<>();
    private BukkitTask task;
    private final List<Predicate<Player>> unavailable = new ArrayList<>();

    public Dialogues(Plugin plugin, Messages messages, ResourcePacks packs, Map<String, Object> config) {
        this.plugin = plugin;
        this.messages = messages;
        this.packs = packs;
        charactersPerTick = number(config, "characters-per-tick", 2, 1, 20);
        timeoutTicks = number(config, "timeout-seconds", 180, 15, 1800) * 20;
        readTicks = number(config, "read-seconds", 5, 1, 60) * 20;
        var demoConfig = new HashMap<String, Object>();
        if (config.get("demo") instanceof Map<?, ?> fields) fields.forEach((key, value) -> demoConfig.put(String.valueOf(key), value));
        demo = Dialogue.load(demoConfig);
        if (!demo.valid()) plugin.getLogger().warning("Invalid dialogue demo: " + String.join("; ", demo.problems()));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Refuses a dialogue while {@code predicate} holds for the player, on top of the others; closing the handle drops it. */
    public AutoCloseable unavailableWhen(Predicate<Player> predicate) {
        unavailable.add(predicate);
        return () -> unavailable.remove(predicate);
    }

    /**
     * Draws what {@code panel} gives for the player, a {@link me.blockcraft.api.pack.HudPanel} line that takes no room,
     * beside the dialogue: the dialogue holds the action bar while it shows, and a game's own corner panel rides on it
     * too. Closing the handle drops it.
     */
    public Handle beside(Function<Player, Optional<Component>> panel) {
        beside.add(panel);
        return () -> beside.remove(panel);
    }

    public boolean active(Player player) {
        return viewers.containsKey(player.getUniqueId());
    }

    /** Whether the player's dialogue asks something and so keeps their clicks, scrolling and hits for itself. */
    public boolean holds(Player player) {
        var viewing = viewers.get(player.getUniqueId());
        return viewing != null && viewing.holds();
    }

    public boolean startDemo(Player player) {
        if (!demo.valid()) {
            player.sendMessage(messages.get("dialogue.invalid"));
            return false;
        }
        return start(player, demo.dialogue(), ending -> player.sendMessage(messages.get("dialogue.completed",
                Placeholder.unparsed("ending", ending))));
    }

    public boolean start(Player player, Dialogue dialogue, Consumer<String> completed) {
        return start(player, dialogue, messages, completed);
    }

    public boolean start(Player player, Dialogue dialogue, Messages text, Consumer<String> completed) {
        return start(player, dialogue, text, new PresentationScope(plugin), completed);
    }

    /** The panel draws only with the pack, and a client can be in the world before it loads; such a client gets {@code withoutPack}. */
    public boolean startOrElse(Player player, Dialogue dialogue, Messages text, Consumer<String> completed, Runnable withoutPack) {
        if (!packs.applied(player)) {
            withoutPack.run();
            return false;
        }
        return start(player, dialogue, text, completed);
    }

    public boolean start(Player player, Dialogue dialogue, Messages text, PresentationScope scope, Consumer<String> completed) {
        boolean started = false;
        try {
            started = begin(player, dialogue, text, scope, completed);
            return started;
        } catch (RuntimeException failure) {
            plugin.getSLF4JLogger().warn("Could not start dialogue", failure);
            return false;
        } finally {
            if (!started) scope.close();
        }
    }

    private boolean begin(Player player, Dialogue dialogue, Messages text, PresentationScope scope, Consumer<String> completed) {
        if (scope.closed() || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (unavailable.stream().anyMatch(predicate -> predicate.test(player))) {
            player.sendMessage(messages.get("presentation.busy"));
            return false;
        }
        if (!packs.applied(player)) {
            player.sendMessage(messages.get("dialogue.pack-required"));
            return false;
        }
        var problems = dialogue.problems();
        for (var line : dialogue.lines().values()) {
            if (!text.has(line.speaker()) || !text.has(line.text())
                    || line.choices().stream().anyMatch(choice -> !text.has(choice.text()))) {
                player.sendMessage(messages.get("dialogue.invalid"));
                return false;
            }
        }
        if (!problems.isEmpty()) {
            plugin.getLogger().warning("Invalid dialogue: " + String.join("; ", problems));
            player.sendMessage(messages.get("dialogue.invalid"));
            return false;
        }
        stop(player);
        if (scope.closed()) return false;
        player.closeInventory();
        var session = new DialogueSession(dialogue, key -> DialogueView.plain(text, key));
        var viewing = new Viewing(session, text, completed, now() + timeoutTicks, scope, dialogue.asks());
        viewers.put(player.getUniqueId(), viewing);
        scope.own(() -> {
            var current = viewers.get(player.getUniqueId());
            if (current != null && current.scope() == scope) stop(player);
        });
        draw(player, viewing);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        return true;
    }

    public void stop(Player player) {
        readSince.remove(player.getUniqueId());
        drawn.remove(player.getUniqueId());
        var viewing = viewers.remove(player.getUniqueId());
        if (viewing == null) return;
        try {
            player.sendActionBar(Component.empty());
        } finally {
            viewing.scope().close();
        }
    }

    private void tick() {
        for (UUID id : java.util.List.copyOf(viewers.keySet())) {
            var player = Bukkit.getPlayer(id);
            if (player == null) {
                drawn.remove(id);
                var removed = viewers.remove(id);
                if (removed != null) removed.scope().close();
                continue;
            }
            var viewing = viewers.get(id);
            if (viewing == null) continue;
            if (now() >= viewing.expires() || !packs.applied(player) || player.isDead()) {
                stop(player);
                player.sendMessage(messages.get("dialogue.cancelled"));
                continue;
            }
            viewing.session().reveal(charactersPerTick);
            if (!viewing.holds() && viewing.session().revealed()
                    && now() - readSince.computeIfAbsent(id, ignored -> now()) >= readTicks) {
                advance(player);
                continue;
            }
            draw(player, viewing);
        }
        if (viewers.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    public void advance(Player player) {
        var viewing = viewers.get(player.getUniqueId());
        if (viewing == null) return;
        readSince.remove(player.getUniqueId());
        viewing.session().advance();
        if (viewing.session().finished()) {
            stop(player);
            viewing.completed().accept(viewing.session().lineId());
        } else {
            draw(player, viewing);
        }
    }

    public void select(Player player, int delta) {
        var viewing = viewers.get(player.getUniqueId());
        if (viewing != null) {
            viewing.session().select(delta);
            draw(player, viewing);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && active(event.getPlayer())) advance(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onScroll(PlayerItemHeldEvent event) {
        if (!holds(event.getPlayer())) return;
        int delta = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        select(event.getPlayer(), delta <= 4 ? 1 : -1);
        event.setCancelled(true);
    }

    /** Games and the lobby lock swapping hands, so ending a dialogue cannot wait for an uncancelled swap. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!active(event.getPlayer())) return;
        event.setCancelled(true);
        stop(event.getPlayer());
        event.getPlayer().sendMessage(messages.get("dialogue.cancelled"));
    }

    /** First, so that hotbar items which skip a denied use stay still while the player reads. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (holds(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (holds(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && holds(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0) stop(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) stop(player);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) { stop(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { stop(event.getPlayer()); }

    @Override
    public void close() {
        if (task != null) task.cancel();
        task = null;
        for (UUID id : java.util.List.copyOf(viewers.keySet())) {
            var player = Bukkit.getPlayer(id);
            if (player != null) stop(player);
        }
        for (var viewing : java.util.List.copyOf(viewers.values())) viewing.scope().close();
        viewers.clear();
        drawn.clear();
    }

    /** Sends the panel with whatever is drawn beside it whenever either changes, and again before the client lets it fade. */
    private void draw(Player player, Viewing viewing) {
        var bar = Component.text().append(DialogueView.draw(viewing.session(), viewing.text(), messages));
        for (var panel : beside) panel.apply(player).ifPresent(bar::append);
        var line = bar.build();
        var last = drawn.get(player.getUniqueId());
        if (last == null || !last.bar().equals(line) || now() - last.at() >= REFRESH_TICKS) {
            player.sendActionBar(line);
            drawn.put(player.getUniqueId(), new Drawn(line, now()));
        }
    }

    private static long now() { return Integer.toUnsignedLong(Bukkit.getCurrentTick()); }

    private static int number(Map<String, Object> config, String key, int fallback, int min, int max) {
        return Math.clamp(config.get(key) instanceof Number value ? value.intValue() : fallback, min, max);
    }
}
