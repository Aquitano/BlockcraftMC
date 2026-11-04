package me.blockcraft.api.notification;

import me.blockcraft.api.pack.ResourcePacks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class NotificationsTest {

    @Test
    public void combatFeedbackAppearsImmediatelyWithoutReplacingAnObjectiveTitleAndThenRestoresStatus() throws Exception {
        var tick = new AtomicInteger();
        var frame = new AtomicReference<Runnable>();
        var bars = new ArrayList<Component>();
        var titles = new ArrayList<Component>();
        var id = UUID.randomUUID();
        var player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "isOnline" -> true;
            case "sendActionBar" -> { bars.add((Component) args[0]); yield null; }
            case "showTitle" -> { titles.add(((Title) args[0]).title()); yield null; }
            default -> throw new AssertionError(method.getName());
        });
        var task = fake(BukkitTask.class, (proxy, method, args) -> null);
        var scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> {
            frame.set((Runnable) args[1]);
            return task;
        });
        var manager = fake(PluginManager.class, (proxy, method, args) -> null);
        var server = fake(Server.class, (proxy, method, args) -> switch (method.getName()) {
            case "getCurrentTick" -> tick.get();
            case "getPlayer" -> player;
            case "getScheduler" -> scheduler;
            case "getPluginManager" -> manager;
            default -> throw new AssertionError(method.getName());
        });
        var plugin = fake(Plugin.class, (proxy, method, args) -> server);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        var previous = serverField.get(null);
        try {
            serverField.set(null, server);
            var notices = new Notifications(plugin, null, new ResourcePacks(null), Notifications.Timing.from(Map.of()));
            var hud = Component.text("Heat 40%");
            var hit = Component.text("HEADSHOT +100");
            var objective = Component.text("POST CAPTURED");
            notices.status(player, hud);
            notices.important(player, Title.title(objective, Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO)));
            notices.feedbackNow(player, hit, 6);
            assertEquals(List.of(hit), bars);
            frame.get().run();
            assertEquals(List.of(objective), titles);
            assertEquals(List.of(hit), bars);
            tick.set(6);
            frame.get().run();
            assertEquals(hud, bars.getLast());
            assertEquals(List.of(objective), titles);
            notices.suspendWhile(ignored -> true);
            notices.feedbackNow(player, hit, 6);
            assertEquals(hud, bars.getLast());
            notices.close();
        } finally {
            serverField.set(null, previous);
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
