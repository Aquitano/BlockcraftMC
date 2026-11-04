package me.blockcraft.sternenfront.appearance;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ScopeTargetsTest {

    @Test
    public void onlyTargetsInsideTheScopesConeAndRangeAreSelected() {
        var facing = new Vector(0, 0, 1);
        double cosine = Math.cos(Math.toRadians(20));
        assertTrue(ScopeTargets.inView(facing, new Vector(2, 0, 10), 160, cosine));
        assertFalse(ScopeTargets.inView(facing, new Vector(10, 0, 10), 160, cosine));
        assertFalse(ScopeTargets.inView(facing, new Vector(0, 0, -10), 160, cosine));
        assertFalse(ScopeTargets.inView(facing, new Vector(0, 0, 161), 160, cosine));
        assertFalse(ScopeTargets.inView(facing, new Vector(), 160, cosine));
    }

    @Test
    public void coverRemovesTheOutlineButAVisibleHeadCanStillBeTargeted() {
        var blocked = new AtomicInteger(2);
        var world = fake(World.class, (proxy, method, args) -> {
            if (method.getName().equals("rayTraceBlocks")) {
                return blocked.getAndDecrement() > 0 ? new RayTraceResult(new Vector(0, 0, 1)) : null;
            }
            throw new AssertionError(method.getName());
        });
        var target = fake(LivingEntity.class, (proxy, method, args) -> switch (method.getName()) {
            case "getBoundingBox" -> new BoundingBox(-0.3, -1, 10, 0.3, 1, 11);
            case "getEyeLocation" -> new Location(world, 0, 0.8, 10.5);
            default -> throw new AssertionError(method.getName());
        });
        var eye = new Location(world, 0, 0, 0);
        double cosine = Math.cos(Math.toRadians(20));
        assertFalse(ScopeTargets.visible(eye, target, 160, cosine));
        blocked.set(1);
        assertTrue(ScopeTargets.visible(eye, target, 160, cosine));
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
