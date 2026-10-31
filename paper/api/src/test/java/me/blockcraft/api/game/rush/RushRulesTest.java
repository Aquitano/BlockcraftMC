package me.blockcraft.api.game.rush;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

public class RushRulesTest {

    @Test
    public void ordinaryBlocksAllowPlacementWhileBedsRemainUnusable() {
        var rules = new RushRules(null, null, player -> true);
        var bridge = interaction(Material.RED_TERRACOTTA);
        rules.onInteract(bridge);
        assertEquals(Event.Result.ALLOW, bridge.useInteractedBlock());

        var bed = interaction(Material.RED_BED);
        rules.onInteract(bed);
        assertEquals(Event.Result.DENY, bed.useInteractedBlock());

        var protectedClick = interaction(Material.RED_TERRACOTTA);
        protectedClick.setCancelled(true);
        rules.onInteract(protectedClick);
        assertEquals(Event.Result.DENY, protectedClick.useInteractedBlock());
        assertEquals(Event.Result.DENY, protectedClick.useItemInHand());
    }

    @Test
    public void outsidersAndOtherArenasKeepTheirInteractionState() {
        var rules = new RushRules(null, null, player -> false);
        var bed = interaction(Material.RED_BED);
        rules.onInteract(bed);
        assertEquals(Event.Result.ALLOW, bed.useInteractedBlock());
        assertEquals(Event.Result.DEFAULT, bed.useItemInHand());
    }

    private static PlayerInteractEvent interaction(Material material) {
        var block = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getType")) {
                        return material;
                    }
                    if (method.getName().equals("getBlockData")) {
                        var type = material == Material.RED_BED ? Bed.class : BlockData.class;
                        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                                (data, accessor, parameters) -> { throw new AssertionError(accessor.getName()); });
                    }
                    throw new AssertionError(method.getName());
                });
        var player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    throw new AssertionError(method.getName());
                });
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP);
    }
}
