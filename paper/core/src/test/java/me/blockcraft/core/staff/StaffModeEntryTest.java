package me.blockcraft.core.staff;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.player.FlightGrants;
import me.blockcraft.api.vanish.VanishChangeEvent;
import me.blockcraft.common.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class StaffModeEntryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void vanishListenersFinishBeforeTheStaffHotbarReplacesTheirInventory() throws Exception {
        var sequence = new ArrayList<String>();
        var scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> {
            assertEquals("runTaskAsynchronously", method.getName());
            return null;
        });
        var manager = fake(PluginManager.class, (proxy, method, args) -> {
            assertEquals("callEvent", method.getName());
            var event = (VanishChangeEvent) args[0];
            if (event.isVanished()) {
                sequence.add("game replaces the inventory with spectator tools");
            }
            return null;
        });
        var messenger = fake(Messenger.class, (proxy, method, args) -> true);
        var server = fake(Server.class, (proxy, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "getPluginManager" -> manager;
            case "getMessenger" -> messenger;
            case "getOnlinePlayers" -> List.of();
            default -> throw new AssertionError(method.getName());
        });
        var plugin = fake(Plugin.class, (proxy, method, args) -> switch (method.getName()) {
            case "getServer" -> server;
            default -> throw new AssertionError(method.getName());
        });
        var inventory = fake(PlayerInventory.class, (proxy, method, args) -> switch (method.getName()) {
            case "getContents" -> new ItemStack[0];
            case "clear" -> {
                sequence.add("staff replaces the inventory with moderation tools");
                throw new HotbarReached();
            }
            default -> throw new AssertionError(method.getName());
        });
        var id = UUID.randomUUID();
        var player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getInventory" -> inventory;
            case "getAllowFlight" -> false;
            case "sendPluginMessage" -> null;
            default -> throw new AssertionError(method.getName());
        });
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        var apiField = BlockcraftApi.class.getDeclaredField("instance");
        apiField.setAccessible(true);
        var previousServer = serverField.get(null);
        var previousApi = apiField.get(null);
        try {
            serverField.set(null, server);
            var values = Arrays.stream(BlockcraftApi.class.getRecordComponents())
                    .map(component -> component.getName().equals("flight") ? new FlightGrants() : null).toArray();
            apiField.set(null, BlockcraftApi.class.getDeclaredConstructors()[0].newInstance(values));
            var messages = Messages.load(folder.newFolder().toPath(), () -> new ByteArrayInputStream(new byte[0]));
            var staffMode = new StaffMode(plugin, messages, new VanishService(plugin, null), null, null, null, null, null);
            assertThrows(HotbarReached.class, () -> staffMode.toggle(player));
            assertEquals(List.of("game replaces the inventory with spectator tools", "staff replaces the inventory with moderation tools"), sequence);
        } finally {
            serverField.set(null, previousServer);
            apiField.set(null, previousApi);
        }
    }

    private static final class HotbarReached extends RuntimeException {
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
