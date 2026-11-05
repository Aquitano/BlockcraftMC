package me.blockcraft.api.presentation;

import org.bukkit.plugin.Plugin;
import org.junit.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PresentationScopeTest {
    @Test
    public void resourcesCloseOnceInReverseOrderAndLateResourcesCloseImmediately() {
        var released = new ArrayList<String>();
        var scope = scope(new ArrayList<>());
        assertFalse(scope.closed());
        scope.own(() -> released.add("first"));
        scope.own(() -> {
            assertTrue(scope.closed());
            released.add("second");
            scope.close();
        });
        scope.close();
        scope.close();
        scope.own(() -> released.add("late"));
        assertEquals(List.of("second", "first", "late"), released);
    }

    @Test
    public void failingCleanupIsLoggedWithoutKeepingOtherResourcesAlive() {
        var failures = new ArrayList<Throwable>();
        var released = new ArrayList<String>();
        var scope = scope(failures);
        var failure = new IllegalStateException("broken resource");
        scope.own(() -> released.add("first"));
        scope.own(() -> { throw failure; });
        scope.close();
        assertEquals(List.of("first"), released);
        assertEquals(List.of(failure), failures);
        scope.own(() -> { throw failure; });
        assertEquals(List.of(failure, failure), failures);
    }

    private static PresentationScope scope(List<Throwable> failures) {
        var logger = (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[]{Logger.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("warn")) throw new AssertionError(method.getName());
                    failures.add((Throwable) args[1]);
                    return null;
                });
        var plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getSLF4JLogger")) return logger;
                    throw new AssertionError(method.getName());
                });
        return new PresentationScope(plugin);
    }
}
