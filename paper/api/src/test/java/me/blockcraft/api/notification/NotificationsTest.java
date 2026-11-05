package me.blockcraft.api.notification;

import me.blockcraft.api.pack.ResourcePacks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
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
    public void hitAndKillMarkersAppearImmediatelyAndRestoreAnObjectiveSubtitleWithoutRestartingItsTitle() throws Exception {
        var tick = new AtomicInteger();
        var frame = new AtomicReference<Runnable>();
        var current = new AtomicReference<Title>();
        var titles = new AtomicInteger();
        var bars = new ArrayList<Component>();
        var id = UUID.randomUUID();
        var player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "isOnline" -> true;
            case "sendActionBar" -> { bars.add((Component) args[0]); yield null; }
            case "showTitle" -> { current.set((Title) args[0]); titles.incrementAndGet(); yield null; }
            case "sendTitlePart" -> {
                assertEquals(TitlePart.SUBTITLE, args[0]);
                current.set(Title.title(current.get().title(), (Component) args[1], current.get().times()));
                yield null;
            }
            case "clearTitle" -> { current.set(null); yield null; }
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
            var hud = Component.text("Health, heat and cooldowns");
            var objective = Title.title(Component.text("POST CAPTURED"), Component.text("Market"),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO));
            notices.status(player, hud);
            notices.important(player, objective);
            frame.get().run();
            var hit = Component.text("HIT");
            notices.markerNow(player, hit, 6);
            assertEquals(objective.title(), current.get().title());
            assertEquals(hit, current.get().subtitle());
            assertEquals(1, titles.get());
            assertEquals(hud, bars.getLast());
            tick.set(2);
            var kill = Component.text("SKULL");
            notices.markerNow(player, kill, 6);
            assertEquals(kill, current.get().subtitle());
            tick.set(6);
            frame.get().run();
            assertEquals(kill, current.get().subtitle());
            tick.set(8);
            frame.get().run();
            assertEquals(objective.subtitle(), current.get().subtitle());
            assertEquals(1, titles.get());
            tick.set(38);
            notices.markerNow(player, hit, 6);
            tick.set(40);
            frame.get().run();
            assertEquals(Component.empty(), current.get().title());
            assertEquals(hit, current.get().subtitle());
            tick.set(44);
            frame.get().run();
            assertEquals(null, current.get());
            notices.markerNow(player, hit, 6);
            notices.clearMarker(player);
            assertEquals(null, current.get());
            notices.markerNow(player, kill, 6);
            tick.set(50);
            frame.get().run();
            assertEquals(null, current.get());
            notices.suspendWhile(ignored -> true);
            notices.markerNow(player, hit, 6);
            assertEquals(null, current.get());
            notices.close();
        } finally {
            serverField.set(null, previous);
        }
    }

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
