package me.blockcraft.api.task;

import me.blockcraft.common.message.Messages;
import me.blockcraft.common.task.LatestRequests;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Database work behind a menu: {@code work} runs off the main thread, {@code complete} runs on it and only while the
 * player is online. A failure is logged and answered with one message instead of a stack trace and a silent menu.
 * {@link #latest} is for reads that build a screen, {@link #write} for changes whose outcome the player must hear.
 */
public final class PlayerTasks implements Listener {

    private final LatestRequests<UUID> requests = new LatestRequests<>();
    private final Plugin plugin;
    private final Messages messages;

    public PlayerTasks(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Completes only if the player is still looking at the same screen and has not started a newer request since. */
    public <T> void latest(Player player, Supplier<T> work, Consumer<T> complete) {
        var id = player.getUniqueId();
        var token = requests.begin(id);
        var view = player.getOpenInventory();
        run(player, work, complete, () -> requests.finish(id, token) && player.getOpenInventory() == view);
    }

    /** Completes whatever screen the player has moved on to: a purchase or a claim happened and they must hear it. */
    public <T> void write(Player player, Supplier<T> work, Consumer<T> complete) {
        run(player, work, complete, () -> true);
    }

    /** Opening or closing a screen makes every read started for the previous one stale. */
    public void invalidate(Player player) {
        requests.invalidate(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer());
    }

    private <T> void run(Player player, Supplier<T> work, Consumer<T> complete, BooleanSupplier stillWanted) {
        Tasks.async(plugin, () -> {
            T value;
            try {
                value = work.get();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Player operation failed for " + player.getUniqueId(), e);
                deliver(player, stillWanted, () -> player.sendMessage(messages.get("operation.failed")));
                return;
            }
            deliver(player, stillWanted, () -> complete.accept(value));
        });
    }

    private void deliver(Player player, BooleanSupplier stillWanted, Runnable completion) {
        if (!plugin.isEnabled()) {
            return;
        }
        Tasks.sync(plugin, () -> {
            if (player.isOnline() && stillWanted.getAsBoolean()) {
                completion.run();
            }
        });
    }
}
