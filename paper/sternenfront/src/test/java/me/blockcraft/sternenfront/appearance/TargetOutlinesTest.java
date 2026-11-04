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

public class TargetOutlinesTest {

    @Test
    public void clearTargetsAreOutlinedRegardlessOfAimDirectionButNotBeyondRange() {
        var world = fake(World.class, (proxy, method, args) -> {
            if (method.getName().equals("rayTraceBlocks")) return null;
            throw new AssertionError(method.getName());
        });
        var target = fake(LivingEntity.class, (proxy, method, args) -> {
            if (method.getName().equals("getBoundingBox")) return new BoundingBox(9.7, 0, -10.3, 10.3, 1.8, -9.7);
            throw new AssertionError(method.getName());
        });
        var eye = new Location(world, 0, 1.6, 0);
        assertTrue(TargetOutlines.visible(eye, target, 160));
        assertFalse(TargetOutlines.visible(eye, target, 10));
    }

    @Test
    public void fullCoverAndPartialCoverBothRemoveTheWholeBodyOutline() {
        var blockedRay = new AtomicInteger(0);
        var ray = new AtomicInteger();
        var world = fake(World.class, (proxy, method, args) -> {
            if (method.getName().equals("rayTraceBlocks")) {
                return ray.getAndIncrement() == blockedRay.get() ? new RayTraceResult(new Vector(0, 0, 1)) : null;
            }
            throw new AssertionError(method.getName());
        });
        var target = fake(LivingEntity.class, (proxy, method, args) -> {
            if (method.getName().equals("getBoundingBox")) return new BoundingBox(-0.3, 0, 10, 0.3, 1.8, 11);
            throw new AssertionError(method.getName());
        });
        var eye = new Location(world, 0, 1.6, 0);
        assertFalse(TargetOutlines.visible(eye, target, 160));
        ray.set(0);
        blockedRay.set(1);
        assertFalse(TargetOutlines.visible(eye, target, 160));
        ray.set(0);
        blockedRay.set(-1);
        assertTrue(TargetOutlines.visible(eye, target, 160));
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
