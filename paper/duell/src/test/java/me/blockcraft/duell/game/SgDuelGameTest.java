package me.blockcraft.duell.game;

import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.LootTable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class SgDuelGameTest {

    @Test
    @SuppressWarnings("unchecked")
    public void freezeOpensLootWhileClosedGamesAndNonparticipantsAreIgnored() throws ReflectiveOperationException {
        var participant = UUID.randomUUID();
        var opened = new ArrayList<Inventory>();
        var player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> participant;
                    case "openInventory" -> {
                        opened.add((Inventory) args[0]);
                        yield null;
                    }
                    default -> throw new AssertionError(method.getName());
                });
        var location = new Location(null, 10, 64, 20);
        var block = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> Material.CHEST;
                    case "getLocation" -> location;
                    default -> throw new AssertionError(method.getName());
                });
        var inventory = (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getViewers" -> List.of();
                    default -> throw new AssertionError(method.getName());
                });
        var context = new DuelGames.Context(null, null, null, null, null,
                List.of(participant, UUID.randomUUID()), List.of(), null);
        var chests = new LootChests(new LootTable(1, 1, List.of()));
        var game = new SgDuelGame(context, chests);
        var cached = LootChests.class.getDeclaredField("opened");
        cached.setAccessible(true);
        ((Map<Location, Inventory>) cached.get(chests)).put(location, inventory);

        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND);
        assertFalse(game.active);
        game.onChest(event);

        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        assertEquals(1, opened.size());
        assertSame(inventory, opened.getFirst());

        var outsider = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> UUID.randomUUID();
                    default -> throw new AssertionError(method.getName());
                });
        var outsiderEvent = new PlayerInteractEvent(outsider, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND);
        game.onChest(outsiderEvent);
        assertEquals(Event.Result.ALLOW, outsiderEvent.useInteractedBlock());

        game.close();
        var closedEvent = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND);
        game.onChest(closedEvent);
        assertEquals(Event.Result.ALLOW, closedEvent.useInteractedBlock());
        assertEquals(1, opened.size());
    }
}
