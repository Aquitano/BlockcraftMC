package me.blockcraft.duell.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SumoDuelGameTest {

    @Test
    public void nativeKnockbackUsesTheAttackersDirectionAndSynchronizesTheResult() {
        var calls = new ArrayList<String>();
        var resultingVelocity = new Vector(0.4, 0.4, 0);
        var attacker = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLocation" -> new Location(null, -2.5, 65, 0.5);
                    case "getYaw" -> 45.0f;
                    default -> throw new AssertionError(method.getName());
                });
        var victim = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLocation" -> new Location(null, 0.5, 65, 0.5);
                    case "knockback" -> {
                        assertEquals(0.4, (double) args[0], 0);
                        assertEquals(-3, (double) args[1], 0);
                        assertEquals(0, (double) args[2], 0);
                        calls.add("native");
                        yield null;
                    }
                    case "getVelocity" -> {
                        calls.add("read");
                        yield resultingVelocity.clone();
                    }
                    case "setVelocity" -> {
                        assertEquals(resultingVelocity, args[0]);
                        calls.add("sync");
                        yield null;
                    }
                    case "playHurtAnimation" -> {
                        assertEquals(45.0f, (float) args[0], 0);
                        calls.add("hurt");
                        yield null;
                    }
                    default -> throw new AssertionError(method.getName());
                });

        SumoDuelGame.knockback(victim, attacker, 0.4);

        assertEquals(List.of("native", "read", "sync", "hurt"), calls);
    }
}
