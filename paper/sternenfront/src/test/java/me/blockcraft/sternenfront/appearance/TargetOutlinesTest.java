package me.blockcraft.sternenfront.appearance;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class TargetOutlinesTest {

    @Test
    public void targetsAreSelectedRegardlessOfAimDirectionWithoutServerBlockRays() {
        var world = fake(World.class, (proxy, method, args) -> {
            throw new AssertionError(method.getName());
        });
        var target = fake(LivingEntity.class, (proxy, method, args) -> {
            if (method.getName().equals("getBoundingBox")) return new BoundingBox(9.7, 0, -10.3, 10.3, 1.8, -9.7);
            throw new AssertionError(method.getName());
        });
        var eye = new Location(world, 0, 1.6, 0);
        assertTrue(TargetOutlines.inRange(eye, target, 160));
        assertFalse(TargetOutlines.inRange(eye, target, 10));
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
