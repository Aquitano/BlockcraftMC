package me.blockcraft.api.presentation;

import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class PresentationScope implements AutoCloseable {
    private final Plugin plugin;
    private final Deque<Runnable> cleanups = new ArrayDeque<>();
    private boolean closed;

    public PresentationScope(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    public void own(Runnable cleanup) {
        Objects.requireNonNull(cleanup);
        if (closed) release(cleanup);
        else cleanups.addFirst(cleanup);
    }

    public boolean closed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        while (!cleanups.isEmpty()) release(cleanups.removeFirst());
    }

    private void release(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException failure) {
            plugin.getSLF4JLogger().warn("Could not release presentation resource", failure);
        }
    }
}
