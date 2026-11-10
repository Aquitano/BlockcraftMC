package me.blockcraft.api.game;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RivalriesTest {

    @Test
    public void closingWhileLoadingPersistsTheQueuedKillOnceWithoutOldRoundNotifications() throws Exception {
        var workers = new ArrayDeque<Runnable>();
        var main = new ArrayDeque<Runnable>();
        var scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "runTaskAsynchronously" -> workers.add((Runnable) args[1]);
                case "runTask" -> main.add((Runnable) args[1]);
                default -> throw new AssertionError(method.getName());
            }
            return null;
        });
        var server = fake(Server.class, (proxy, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "getPlayer" -> throw new AssertionError("Closed round tried to announce a nemesis");
            default -> throw new AssertionError(method.getName());
        });
        var plugin = fake(Plugin.class, (proxy, method, args) -> switch (method.getName()) {
            case "isEnabled" -> true;
            default -> throw new AssertionError(method.getName());
        });
        var killerId = UUID.randomUUID();
        var victimId = UUID.randomUUID();
        var persisted = new ArrayList<RivalryRepository.Rivalry>();
        var revenge = new ArrayList<Standings.Standing>();
        var rivalries = new Rivalries(plugin, null, null, ids -> {
            assertEquals(List.of(killerId, victimId), List.copyOf(ids));
            return List.of(new RivalryRepository.Rivalry(victimId, killerId, 5));
        }, (killer, victim) -> persisted.add(new RivalryRepository.Rivalry(killer, victim, 1)), 3);
        var killer = player(killerId);
        var victim = player(victimId);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        var previousServer = serverField.get(null);
        try {
            serverField.set(null, server);
            rivalries.load(List.of(killer, victim));
            var load = workers.remove();
            rivalries.kill(killer, victim, revenge::add);
            assertTrue(workers.isEmpty());
            assertTrue(persisted.isEmpty());

            rivalries.close();
            rivalries.close();
            load.run();
            main.remove().run();
            for (var write : List.copyOf(workers)) {
                write.run();
            }

            assertEquals(List.of(new RivalryRepository.Rivalry(killerId, victimId, 1)), persisted);
            assertTrue(revenge.isEmpty());
            assertTrue(main.isEmpty());
        } finally {
            serverField.set(null, previousServer);
        }
    }

    private static Player player(UUID id) {
        return fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            default -> throw new AssertionError(method.getName());
        });
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
