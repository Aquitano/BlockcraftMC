package me.blockcraft.api.dialogue;

import me.blockcraft.api.pack.ResourcePacks;
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
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class Dialogues implements Listener, AutoCloseable {
    private record Viewing(DialogueSession session, Messages text, Consumer<String> completed, long expires) {}

    private final Plugin plugin;
    private final Messages messages;
    private final ResourcePacks packs;
    private final Dialogue.Loaded demo;
    private final int charactersPerTick;
    private final int timeoutTicks;
    private final Map<UUID, Viewing> viewers = new HashMap<>();
    private BukkitTask task;
    private Predicate<Player> unavailable = ignored -> false;

    public Dialogues(Plugin plugin, Messages messages, ResourcePacks packs, Map<String, Object> config) {
        this.plugin = plugin;
        this.messages = messages;
        this.packs = packs;
        charactersPerTick = number(config, "characters-per-tick", 2, 1, 20);
        timeoutTicks = number(config, "timeout-seconds", 180, 15, 1800) * 20;
        var demoConfig = new HashMap<String, Object>();
        if (config.get("demo") instanceof Map<?, ?> fields) fields.forEach((key, value) -> demoConfig.put(String.valueOf(key), value));
        demo = Dialogue.load(demoConfig);
        if (!demo.valid()) plugin.getLogger().warning("Invalid dialogue demo: " + String.join("; ", demo.problems()));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void unavailableWhen(Predicate<Player> predicate) {
        unavailable = predicate;
    }

    public boolean active(Player player) {
        return viewers.containsKey(player.getUniqueId());
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
        if (!player.isOnline()) return false;
        if (unavailable.test(player)) {
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
        player.closeInventory();
        var session = new DialogueSession(dialogue, key -> DialogueView.plain(text, key));
        viewers.put(player.getUniqueId(), new Viewing(session, text, completed, now() + timeoutTicks));
        player.sendActionBar(DialogueView.draw(session, text, messages));
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        return true;
    }

    public void stop(Player player) {
        if (viewers.remove(player.getUniqueId()) != null) player.sendActionBar(Component.empty());
    }

    private void tick() {
        for (UUID id : java.util.List.copyOf(viewers.keySet())) {
            var player = Bukkit.getPlayer(id);
            if (player == null) {
                viewers.remove(id);
                continue;
            }
            var viewing = viewers.get(id);
            if (now() >= viewing.expires() || !packs.applied(player) || player.isDead()) {
                stop(player);
                player.sendMessage(messages.get("dialogue.cancelled"));
                continue;
            }
            boolean revealing = !viewing.session().revealed();
            viewing.session().reveal(charactersPerTick);
            if (revealing || now() % 20 == 0) {
                player.sendActionBar(DialogueView.draw(viewing.session(), viewing.text(), messages));
            }
        }
        if (viewers.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    public void advance(Player player) {
        var viewing = viewers.get(player.getUniqueId());
        if (viewing == null) return;
        viewing.session().advance();
        if (viewing.session().finished()) {
            stop(player);
            viewing.completed().accept(viewing.session().lineId());
        } else {
            player.sendActionBar(DialogueView.draw(viewing.session(), viewing.text(), messages));
        }
    }

    public void select(Player player, int delta) {
        var viewing = viewers.get(player.getUniqueId());
        if (viewing != null) {
            viewing.session().select(delta);
            player.sendActionBar(DialogueView.draw(viewing.session(), viewing.text(), messages));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && active(event.getPlayer())) advance(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onScroll(PlayerItemHeldEvent event) {
        if (!active(event.getPlayer())) return;
        int delta = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        select(event.getPlayer(), delta <= 4 ? 1 : -1);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!active(event.getPlayer())) return;
        event.setCancelled(true);
        stop(event.getPlayer());
        event.getPlayer().sendMessage(messages.get("dialogue.cancelled"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (active(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && active(player)) event.setCancelled(true);
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
    public void onQuit(PlayerQuitEvent event) { viewers.remove(event.getPlayer().getUniqueId()); }

    @Override
    public void close() {
        if (task != null) task.cancel();
        task = null;
        for (UUID id : java.util.List.copyOf(viewers.keySet())) {
            var player = Bukkit.getPlayer(id);
            if (player != null) stop(player);
        }
        viewers.clear();
    }

    private static long now() { return Integer.toUnsignedLong(Bukkit.getCurrentTick()); }

    private static int number(Map<String, Object> config, String key, int fallback, int min, int max) {
        return Math.clamp(config.get(key) instanceof Number value ? value.intValue() : fallback, min, max);
    }
}
