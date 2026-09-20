package me.blockcraft.api.task;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.task.LatestRequests;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
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
        latest(player, work, complete, player.getOpenInventory());
    }

    private <T> void latest(Player player, Supplier<T> work, Consumer<T> complete, InventoryView parent) {
        latest(player, work, complete,
                waiting -> new TaskMenu(false, ignored -> latest(player, work, complete, parent), parent).showNow(waiting),
                failed -> new TaskMenu(true, ignored -> latest(player, work, complete, parent), parent).open(failed));
    }

    public <T> void latest(Player player, Supplier<T> work, Consumer<T> complete,
                           Consumer<Player> onLoading, Consumer<Player> onFailure) {
        var read = new Read(player);
        Tasks.later(plugin, 6L, () -> {
            if (read.getAsBoolean()) {
                onLoading.accept(player);
                read.view = player.getOpenInventory();
            }
        });
        run(player, work, complete, read::finish, onFailure);
    }

    public BooleanSupplier currentScreen(Player player) {
        var id = player.getUniqueId();
        var token = requests.snapshot(id);
        var view = player.getOpenInventory();
        return () -> player.isOnline() && requests.isCurrent(id, token) && sameScreen(player.getOpenInventory(), view);
    }

    private final class Read implements BooleanSupplier {
        private final Player player;
        private final Object token;
        private InventoryView view;
        private boolean pending = true;

        private Read(Player player) {
            this.player = player;
            token = requests.begin(player.getUniqueId());
            view = player.getOpenInventory();
        }

        @Override
        public boolean getAsBoolean() {
            return pending && player.isOnline() && requests.isCurrent(player.getUniqueId(), token)
                    && sameScreen(player.getOpenInventory(), view);
        }

        private boolean finish() {
            boolean wanted = getAsBoolean();
            pending = false;
            return wanted;
        }
    }

    private static boolean sameScreen(InventoryView current, InventoryView expected) {
        return current == expected || current.getTopInventory().getHolder(false) instanceof Menu menu
                && menu == expected.getTopInventory().getHolder(false);
    }

    /** Completes whatever screen the player has moved on to: a purchase or a claim happened and they must hear it. */
    public <T> void write(Player player, Supplier<T> work, Consumer<T> complete) {
        write(player, work, complete, ignored -> { });
    }

    public <T> void write(Player player, Supplier<T> work, Consumer<T> complete, Consumer<Player> onFailure) {
        run(player, work, complete, () -> true, onFailure);
    }

    /** Opening or closing a screen makes every read started for the previous one stale. */
    public void invalidate(Player player) {
        requests.invalidate(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer());
    }

    private <T> void run(Player player, Supplier<T> work, Consumer<T> complete, BooleanSupplier stillWanted, Consumer<Player> onFailure) {
        Tasks.async(plugin, () -> {
            T value;
            try {
                value = work.get();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Player operation failed for " + player.getUniqueId(), e);
                deliver(player, stillWanted, () -> {
                    player.sendMessage(messages.get("operation.failed"));
                    onFailure.accept(player);
                });
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

    private static final class TaskMenu extends Menu {
        private final Consumer<Player> retry;
        private final boolean failed;

        private TaskMenu(boolean failed, Consumer<Player> retry, InventoryView previous) {
            super(1, BlockcraftApi.get().messages().get(failed ? "menu.load.failed" : "menu.load.title"));
            this.retry = retry;
            this.failed = failed;
            var messages = BlockcraftApi.get().messages();
            var status = Items.of(failed ? Material.REDSTONE : Material.CLOCK)
                    .name(messages.get(failed ? "menu.load.retry" : "menu.load.pending")).build();
            if (failed) {
                set(4, status);
            } else {
                info(4, status);
            }
            if (previous.getTopInventory().getHolder(false) instanceof Menu menu && !(menu instanceof TaskMenu)) {
                backButton(0, menu::open);
            }
            closeButton(8);
        }

        private void showNow(Player player) {
            showLoading(player);
        }

        @Override
        protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
            if (failed && slot == 4) {
                retry.accept(player);
            }
        }
    }

}
