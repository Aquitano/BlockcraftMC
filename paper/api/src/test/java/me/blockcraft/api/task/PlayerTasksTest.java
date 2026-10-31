package me.blockcraft.api.task;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlayerTasksTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final ArrayDeque<Runnable> main = new ArrayDeque<>();
    private final ArrayDeque<Runnable> workers = new ArrayDeque<>();
    private final ArrayDeque<Runnable> delayed = new ArrayDeque<>();
    private final List<Component> received = new ArrayList<>();
    private final List<LogRecord> logs = new ArrayList<>();
    private final UUID id = UUID.randomUUID();
    private boolean online = true;
    private boolean enabled = true;
    private InventoryView view;
    private Player player;
    private Plugin plugin;
    private PlayerTasks tasks;
    private Field serverField;
    private Object previousServer;

    @Before
    public void setUp() throws Exception {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logs.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        var task = fake(BukkitTask.class, (proxy, method, args) -> null);
        var scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "runTask" -> main.add((Runnable) args[1]);
                case "runTaskAsynchronously" -> workers.add((Runnable) args[1]);
                case "runTaskLater" -> delayed.add((Runnable) args[1]);
                default -> throw new AssertionError(method.getName());
            }
            return task;
        });
        var manager = fake(PluginManager.class, (proxy, method, args) -> {
            if (!method.getName().equals("registerEvents")) {
                throw new AssertionError(method.getName());
            }
            return null;
        });
        var server = fake(Server.class, (proxy, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "getPluginManager" -> manager;
            default -> throw new AssertionError(method.getName());
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, server);
        plugin = fake(Plugin.class, (proxy, method, args) -> switch (method.getName()) {
            case "getServer" -> server;
            case "isEnabled" -> enabled;
            case "getLogger" -> logger;
            default -> throw new AssertionError(method.getName());
        });
        player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "isOnline" -> online;
            case "getOpenInventory" -> view;
            case "sendMessage" -> { received.add((Component) args[0]); yield null; }
            default -> throw new AssertionError(method.getName());
        });
        view = view(null);
        var messages = Messages.load(folder.newFolder().toPath(), () -> new ByteArrayInputStream(
                "operation:\n  failed: 'Operation failed'\n".getBytes(StandardCharsets.UTF_8)));
        tasks = new PlayerTasks(plugin, messages);
    }

    @After
    public void restoreServer() throws Exception {
        if (serverField != null) {
            serverField.set(null, previousServer);
        }
    }

    @Test
    public void navigationIsDeferredAndTheLatestRequestWins() {
        var opened = new ArrayList<String>();
        tasks.navigate(player, ignored -> opened.add("old"));
        tasks.navigate(player, ignored -> opened.add("new"));
        assertTrue(opened.isEmpty());

        tick();

        assertEquals(List.of("new"), opened);
    }

    @Test
    public void eligibilityIsCheckedWhenTheScheduledScreenWouldOpen() {
        var eligible = new AtomicBoolean(true);
        var opened = new ArrayList<String>();
        tasks.navigate(player, eligible::get, ignored -> opened.add("old lobby"));
        eligible.set(false);
        tick();
        assertTrue(opened.isEmpty());

        tasks.navigate(player, eligible::get, ignored -> opened.add("new lobby"));
        eligible.set(true);
        tick();
        assertEquals(List.of("new lobby"), opened);
    }

    @Test
    public void aRestylePreservesThePendingReadAcrossViewsOfTheSameMenu() {
        var menu = new Menu(1, Component.empty()) {
            @Override
            protected void onClick(Player viewer, int slot, ItemStack clicked, InventoryClickEvent event) { }
        };
        view = view(menu);
        var completed = new ArrayList<Integer>();
        tasks.latest(player, () -> 42, completed::add, ignored -> { }, ignored -> { });
        tasks.update(player, ignored -> view = view(menu));
        tick();
        runWorkers();
        tick();

        assertEquals(List.of(42), completed);
    }

    @Test
    public void aLoadingScreenBecomesTheExpectedViewForItsRead() {
        var completed = new ArrayList<String>();
        tasks.latest(player, () -> "loaded", completed::add, ignored -> {
            completed.add("loading");
            view = view(null);
        }, ignored -> completed.add("failed"));
        drain(delayed);
        runWorkers();
        tick();

        assertEquals(List.of("loading", "loaded"), completed);
    }

    @Test
    public void navigationInvalidatesPendingReads() {
        var completed = new ArrayList<String>();
        tasks.latest(player, () -> "stale read", completed::add, ignored -> { }, ignored -> { });
        tasks.navigate(player, ignored -> completed.add("new screen"));
        runWorkers();
        tick();

        assertEquals(List.of("new screen"), completed);
    }

    @Test
    public void navigationInvalidatesCallbacksEvenWhenTheInventoryStaysTheSame() {
        var current = tasks.currentScreen(player);
        assertTrue(current.getAsBoolean());

        tasks.navigate(player, ignored -> { });

        assertFalse(current.getAsBoolean());
        tick();
        assertFalse(current.getAsBoolean());
        assertTrue(tasks.currentScreen(player).getAsBoolean());
    }

    @Test
    public void aCommittedWriteStillCompletesAfterNavigation() {
        var completed = new ArrayList<String>();
        tasks.write(player, () -> "saved", completed::add);
        tasks.navigate(player, ignored -> {
            view = view(null);
            completed.add("new screen");
        });
        runWorkers();
        tick();

        assertEquals(List.of("new screen", "saved"), completed);
    }

    @Test
    public void aNewerReadSuppressesBothTheOldResultAndFinishedLoadingIndicators() {
        var completed = new ArrayList<String>();
        tasks.latest(player, () -> "old", completed::add, ignored -> completed.add("old loading"), ignored -> { });
        tasks.latest(player, () -> "new", completed::add, ignored -> completed.add("new loading"), ignored -> { });
        runWorkers();
        tick();
        drain(delayed);

        assertEquals(List.of("new"), completed);
    }

    @Test
    public void aDifferentScreenSuppressesAPendingRead() {
        var completed = new ArrayList<Integer>();
        tasks.latest(player, () -> 42, completed::add, ignored -> { }, ignored -> { });
        view = view(null);
        runWorkers();
        tick();

        assertTrue(completed.isEmpty());
    }

    @Test
    public void disconnectSuppressesNavigationAndWriteFeedback() {
        var completed = new ArrayList<Integer>();
        tasks.navigate(player, ignored -> completed.add(1));
        tasks.write(player, () -> 2, completed::add);
        runWorkers();
        online = false;
        tick();

        assertTrue(completed.isEmpty());
    }

    @Test
    public void disconnectSuppressesAPendingRead() {
        var completed = new ArrayList<Integer>();
        tasks.latest(player, () -> 42, completed::add, ignored -> { }, ignored -> { });
        runWorkers();
        online = false;
        tick();

        assertTrue(completed.isEmpty());
    }

    @Test
    public void writeErrorsLogThePlayerAndDeliverOneFailureOnTheMainThread() {
        var failure = new IllegalStateException("database unavailable");
        var completed = new ArrayList<String>();
        tasks.write(player, () -> { throw failure; }, ignored -> completed.add("success"), ignored -> completed.add("failed"));
        runWorkers();
        assertTrue(completed.isEmpty());
        assertTrue(received.isEmpty());
        assertEquals(1, logs.size());
        assertTrue(logs.getFirst().getMessage().contains(id.toString()));
        assertSame(failure, logs.getFirst().getThrown());
        tick();

        assertEquals(List.of("failed"), completed);
        assertEquals(List.of(Component.text("Operation failed")), received);
    }

    @Test
    public void staleReadErrorsAreLoggedWithoutInterruptingTheNewScreen() {
        var failed = new ArrayList<String>();
        tasks.latest(player, () -> { throw new IllegalStateException("unavailable"); }, ignored -> failed.add("success"),
                ignored -> { }, ignored -> failed.add("failed"));
        tasks.navigate(player, ignored -> { });
        runWorkers();
        tick();

        assertEquals(1, logs.size());
        assertTrue(failed.isEmpty());
        assertTrue(received.isEmpty());
    }

    @Test
    public void disablingThePluginBeforeDeliverySuppressesTheQueuedCompletion() {
        var completed = new ArrayList<Integer>();
        tasks.write(player, () -> 42, completed::add);
        runWorkers();
        enabled = false;
        tick();

        assertTrue(completed.isEmpty());
    }

    @Test
    public void aWorkerFinishingAfterDisableDoesNotScheduleADelivery() {
        tasks.write(player, () -> 42, ignored -> { throw new AssertionError("disabled callback"); });
        enabled = false;
        runWorkers();

        assertTrue(main.isEmpty());
    }

    @Test
    public void ordinaryBackgroundErrorsKeepTheirDefaultContext() {
        var failed = new ArrayList<String>();
        Tasks.async(plugin, () -> { throw new IllegalArgumentException("invalid"); }, ignored -> failed.add("success"),
                () -> failed.add("failed"));
        runWorkers();
        tick();

        assertEquals(List.of("failed"), failed);
        assertEquals("Background task failed", logs.getFirst().getMessage());
    }

    private InventoryView view(InventoryHolder holder) {
        var inventory = fake(Inventory.class, (proxy, method, args) -> {
            if (!method.getName().equals("getHolder")) {
                throw new AssertionError(method.getName());
            }
            return holder;
        });
        return fake(InventoryView.class, (proxy, method, args) -> {
            if (!method.getName().equals("getTopInventory")) {
                throw new AssertionError(method.getName());
            }
            return inventory;
        });
    }

    private void tick() {
        var ready = List.copyOf(main);
        main.clear();
        ready.forEach(Runnable::run);
    }

    private void runWorkers() {
        drain(workers);
    }

    private static void drain(ArrayDeque<Runnable> queue) {
        while (!queue.isEmpty()) {
            queue.remove().run();
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
