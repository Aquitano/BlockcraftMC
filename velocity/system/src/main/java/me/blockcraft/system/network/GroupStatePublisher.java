package me.blockcraft.system.network;

import me.blockcraft.common.network.GroupState;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

final class GroupStatePublisher {

    private record Publication(GroupState state) {
    }

    private final Set<String> groups;
    private final Executor executor;
    private final BiConsumer<String, GroupState> write;
    private final Runnable retain;
    private final Logger logger;
    private final Map<String, Publication> pending = new HashMap<>();
    private boolean cleanup = true;
    private boolean queued;

    GroupStatePublisher(Set<String> groups, BiConsumer<String, GroupState> write, Runnable retain,
                        Executor executor, Logger logger) {
        this.groups = Set.copyOf(groups);
        this.executor = executor;
        this.write = write;
        this.retain = retain;
        this.logger = logger;
        retry();
    }

    synchronized void publish(String group, GroupState state) {
        if (!groups.contains(group)) {
            throw new IllegalArgumentException("Unknown on-demand group: " + group);
        }
        pending.put(group, new Publication(state));
        retry();
    }

    synchronized void retry() {
        if (!queued && (cleanup || !pending.isEmpty())) {
            queued = true;
            executor.execute(this::flush);
        }
    }

    private void flush() {
        Map<String, Publication> batch;
        boolean clean;
        synchronized (this) {
            batch = Map.copyOf(pending);
            clean = cleanup;
        }
        if (clean && attempt(retain)) {
            synchronized (this) {
                cleanup = false;
            }
        }
        batch.forEach((group, publication) -> {
            synchronized (this) {
                if (pending.get(group) != publication) {
                    return;
                }
            }
            if (attempt(() -> write.accept(group, publication.state()))) {
                synchronized (this) {
                    if (pending.get(group) == publication) {
                        pending.remove(group);
                    }
                }
            }
        });
        synchronized (this) {
            queued = false;
            if (pending.entrySet().stream().anyMatch(entry -> batch.get(entry.getKey()) != entry.getValue())) {
                retry();
            }
        }
    }

    private boolean attempt(Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException exception) {
            logger.warn("Could not write bc_server_groups; retrying on the next discovery poll", exception);
            return false;
        }
    }
}
