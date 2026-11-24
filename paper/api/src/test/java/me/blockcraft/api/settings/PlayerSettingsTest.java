package me.blockcraft.api.settings;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.proxy.NetworkOperations;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.operation.NetworkRequest.SetWeaponStyle;
import me.blockcraft.common.network.operation.OperationCodec;
import me.blockcraft.common.network.operation.OperationResult;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerSettingsTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void reselectingTheOldWeaponStyleWhileASaveIsPendingIsRejected() throws Exception {
        var task = fake(BukkitTask.class, (proxy, method, args) -> null);
        var scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> {
            if (method.getName().equals("runTaskAsynchronously")) {
                ((Runnable) args[1]).run();
            }
            return task;
        });
        var manager = fake(PluginManager.class, (proxy, method, args) -> null);
        var messenger = fake(Messenger.class, (proxy, method, args) -> null);
        var server = fake(Server.class, (proxy, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "getPluginManager" -> manager;
            case "getMessenger" -> messenger;
            default -> throw new AssertionError(method.getName());
        });
        var plugin = fake(Plugin.class, (proxy, method, args) -> switch (method.getName()) {
            case "getServer" -> server;
            case "isEnabled" -> true;
            default -> throw new AssertionError(method.getName());
        });
        var sent = new ArrayList<byte[]>();
        var id = UUID.randomUUID();
        var player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "isOnline" -> true;
            case "sendMessage" -> null;
            case "sendPluginMessage" -> {
                sent.add((byte[]) args[2]);
                yield null;
            }
            default -> throw new AssertionError(method.getName());
        });
        var apiField = BlockcraftApi.class.getDeclaredField("instance");
        apiField.setAccessible(true);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        var previousApi = apiField.get(null);
        var previousServer = serverField.get(null);
        try {
            serverField.set(null, server);
            var messages = Messages.load(temporary.newFolder().toPath(), () -> new ByteArrayInputStream(new byte[0]));
            var operations = new NetworkOperations(plugin, messages);
            var values = Arrays.stream(BlockcraftApi.class.getRecordComponents())
                    .map(component -> component.getName().equals("operations") ? operations : null).toArray();
            apiField.set(null, BlockcraftApi.class.getDeclaredConstructors()[0].newInstance(values));
            var settings = new PlayerSettings(plugin, null, messages);
            settings.changedOnProxy(player, PlayerSetting.WEAPON_STYLE, "bone");
            var first = settings.set(player, PlayerSetting.WEAPON_STYLE, "feather");
            assertFalse(first.isDone());
            assertFalse(settings.set(player, PlayerSetting.WEAPON_STYLE, "bone").get());
            assertEquals(1, sent.size());

            settings.changedOnProxy(player, PlayerSetting.WEAPON_STYLE, "feather");
            var request = OperationCodec.request(sent.getFirst());
            operations.onPluginMessageReceived(OperationCodec.CHANNEL, player,
                    OperationCodec.encode(new OperationCodec.Reply(request.id(), OperationResult.APPLIED)));
            assertTrue(first.get());
            var restored = settings.set(player, PlayerSetting.WEAPON_STYLE, "bone");
            assertFalse(restored.isDone());
            assertEquals("bone", ((SetWeaponStyle)
                    OperationCodec.request(sent.getLast()).operation()).style());
        } finally {
            apiField.set(null, previousApi);
            serverField.set(null, previousServer);
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
