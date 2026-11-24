package me.blockcraft.api.task;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.task.LatestRequests;
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
                waiting -> new TaskMenu(messages, false, ignored -> latest(player, work, complete, parent), parent).showNow(waiting),
                failed -> new TaskMenu(messages, true, ignored -> latest(player, work, complete, parent), parent).open(failed));
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

    /** Replaces the screen next tick, invalidating pending reads and earlier navigation. */
    public void navigate(Player player, Consumer<Player> screen) {
        navigate(player, () -> true, screen);
    }

    /** Checks eligibility when the screen actually opens, since a game may start before the next tick. */
    public void navigate(Player player, BooleanSupplier eligible, Consumer<Player> screen) {
        invalidate(player);
        defer(player, eligible, screen);
    }

    /** Restyles the current screen next tick without invalidating its pending read. */
    public void update(Player player, Consumer<Player> screen) {
        defer(player, () -> true, screen);
    }

    private void defer(Player player, BooleanSupplier eligible, Consumer<Player> screen) {
        var current = currentScreen(player);
        Tasks.sync(plugin, () -> {
            if (current.getAsBoolean() && eligible.getAsBoolean()) {
                screen.accept(player);
            }
        });
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
        Tasks.async(plugin, "Player operation failed for " + player.getUniqueId(), work,
                value -> deliver(player, stillWanted, () -> complete.accept(value)),
                () -> deliver(player, stillWanted, () -> {
                    player.sendMessage(messages.get("operation.failed"));
                    onFailure.accept(player);
                }));
    }

    private void deliver(Player player, BooleanSupplier stillWanted, Runnable completion) {
        if (player.isOnline() && stillWanted.getAsBoolean()) {
            completion.run();
        }
    }

    private static final class TaskMenu extends Menu {
        private final Consumer<Player> retry;
        private final boolean failed;

        private TaskMenu(Messages messages, boolean failed, Consumer<Player> retry, InventoryView previous) {
            super(1, messages.get(failed ? "menu.load.failed" : "menu.load.title"));
            this.retry = retry;
            this.failed = failed;
            var status = (failed ? MenuIcon.REFRESH : MenuIcon.LOADING).item()
                    .name(messages.get(failed ? "menu.load.retry" : "menu.load.pending")).build();
            if (failed) {
                set(4, status);
            } else {
                info(4, status);
            }
            if (previous.getTopInventory().getHolder(false) instanceof Menu menu && !(menu instanceof TaskMenu)) {
                backButton(menu::open);
            } else {
                closeButton();
            }
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
