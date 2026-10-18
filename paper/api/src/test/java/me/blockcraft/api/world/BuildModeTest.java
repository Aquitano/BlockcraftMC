package me.blockcraft.api.world;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BuildModeTest {

    private final BuildMode buildMode = new BuildMode();
    private final Sign sign = unused(Sign.class);
    private final Block block = unused(Block.class);

    @Test
    public void signEditingRequiresBuildModeOnBothSidesEvenForCreativeStaff() {
        for (var mode : List.of(GameMode.SURVIVAL, GameMode.CREATIVE)) {
            var player = player(mode);
            for (var side : Side.values()) {
                var open = new PlayerOpenSignEvent(player, sign, side, PlayerOpenSignEvent.Cause.INTERACT);
                buildMode.onSignOpen(open);
                assertTrue(open.isCancelled());
                var change = change(player, side);
                buildMode.onSignChange(change);
                assertTrue(change.isCancelled());
            }
        }
    }

    @Test
    public void buildersCanEditWithoutOverridingOtherProtections() {
        var player = player(GameMode.SURVIVAL);
        buildMode.toggle(player);
        var open = new PlayerOpenSignEvent(player, sign, Side.FRONT, PlayerOpenSignEvent.Cause.INTERACT);
        var change = change(player, Side.FRONT);
        buildMode.onSignOpen(open);
        buildMode.onSignChange(change);
        assertFalse(open.isCancelled());
        assertFalse(change.isCancelled());
        open.setCancelled(true);
        change.setCancelled(true);
        buildMode.onSignOpen(open);
        buildMode.onSignChange(change);
        assertTrue(open.isCancelled());
        assertTrue(change.isCancelled());
    }

    @Test
    public void disablingBuildModeBeforeSubmittingRejectsTheEdit() {
        var player = player(GameMode.SURVIVAL);
        buildMode.toggle(player);
        var open = new PlayerOpenSignEvent(player, sign, Side.BACK, PlayerOpenSignEvent.Cause.INTERACT);
        buildMode.onSignOpen(open);
        assertFalse(open.isCancelled());
        buildMode.toggle(player);
        var change = change(player, Side.BACK);
        buildMode.onSignChange(change);
        assertTrue(change.isCancelled());
    }

    private SignChangeEvent change(Player player, Side side) {
        return new SignChangeEvent(block, player, List.of(Component.text("edited"), Component.empty(),
                Component.empty(), Component.empty()), side);
    }

    private static Player player(GameMode mode) {
        var id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (object, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getGameMode" -> mode;
                    case "hasPermission", "isOp" -> true;
                    default -> throw new AssertionError(method);
                });
    }

    private static <T> T unused(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (object, method, args) -> { throw new AssertionError(method); }));
    }
}
