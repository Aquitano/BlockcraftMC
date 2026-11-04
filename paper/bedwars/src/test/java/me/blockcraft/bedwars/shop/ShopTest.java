package me.blockcraft.bedwars.shop;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.bedwars.resource.Resource;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ShopTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void anUnaffordablePurchaseReadsInventoryOnceAndLeavesItUntouched() {
        var reads = new AtomicInteger();
        var writes = new AtomicInteger();
        var inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[] {PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getStorageContents" -> {
                        reads.incrementAndGet();
                        yield new ItemStack[36];
                    }
                    case "setStorageContents" -> {
                        writes.incrementAndGet();
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected inventory call: " + method.getName());
                });
        var sent = new ArrayList<Component>();
        var player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getInventory" -> inventory;
                    case "sendMessage" -> {
                        sent.add((Component) args[0]);
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected player call: " + method.getName());
                });
        var messages = Messages.load(temporary.getRoot().toPath(), () -> new ByteArrayInputStream("""
                shop:
                  not-enough: "Not enough <resource>."
                resources:
                  bronze:
                    name: "Bronze"
                """.getBytes(StandardCharsets.UTF_8)));
        var shop = new Shop(new ShopCatalog(List.of()), messages, null, ignored -> Optional.empty());
        var offer = new Offer("stone", Material.STONE, 1, Resource.BRONZE, 1, 64,
                Map.of(), Optional.empty(), 0, Offer.Coloring.NONE, Optional.empty(), false);

        assertEquals(Sounds.MENU_DENY, shop.buy(player, offer, false, -1));

        assertEquals(1, reads.get());
        assertEquals(0, writes.get());
        assertEquals(List.of(Component.text("Not enough ").append(Component.text("Bronze", NamedTextColor.RED))
                .append(Component.text("."))), sent);
    }
}
