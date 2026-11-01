package me.blockcraft.knockbackffa;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.player.InventoryInspection;
import me.blockcraft.api.world.BuildMode;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FfaGameTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void staffMayEditTheInspectedInventoryWhileTheirOwnInventoryStaysLocked() throws Exception {
        var id = UUID.randomUUID();
        var viewer = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "hasPermission" -> true;
            case "equals" -> proxy == args[0];
            default -> throw new AssertionError(method.getName());
        });
        var target = fake(Player.class, (proxy, method, args) -> null);
        var messages = Messages.load(temporary.newFolder().toPath(), () -> new ByteArrayInputStream(
                "scoreboard:\n  title: Test\n".getBytes(StandardCharsets.UTF_8)));
        var config = new FfaConfig(24, 2, Duration.ofSeconds(10), Duration.ofSeconds(3), 2,
                Duration.ofSeconds(120), new FfaConfig.Kits(120, 16, 3, 0.6, 60, 3, 1.6, 0.4, 5), false, 100, 0);
        var game = new FfaGame(null, config, messages, null, null);
        var apiField = BlockcraftApi.class.getDeclaredField("instance");
        apiField.setAccessible(true);
        var previousApi = apiField.get(null);
        try {
            var values = Arrays.stream(BlockcraftApi.class.getRecordComponents())
                    .map(component -> component.getName().equals("buildMode") ? new BuildMode() : null).toArray();
            apiField.set(null, BlockcraftApi.class.getDeclaredConstructors()[0].newInstance(values));
            for (var holder : new Player[]{target, viewer}) {
                var inventory = fake(PlayerInventory.class, (proxy, method, args) -> switch (method.getName()) {
                    case "getHolder" -> holder;
                    default -> throw new AssertionError(method.getName());
                });
                var view = fake(InventoryView.class, (proxy, method, args) -> switch (method.getName()) {
                    case "getPlayer" -> viewer;
                    case "getTopInventory", "getInventory" -> inventory;
                    case "convertSlot" -> args[0];
                    default -> throw new AssertionError(method.getName());
                });
                if (holder == target) {
                    assertTrue(InventoryInspection.permitted(viewer, inventory));
                }
                var click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0,
                        ClickType.LEFT, InventoryAction.PICKUP_ALL);
                game.onClick(click);
                assertEquals(holder == viewer, click.isCancelled());
            }
        } finally {
            apiField.set(null, previousApi);
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
