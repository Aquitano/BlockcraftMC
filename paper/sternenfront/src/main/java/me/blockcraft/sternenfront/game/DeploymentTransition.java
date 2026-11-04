package me.blockcraft.sternenfront.game;

import java.util.function.DoubleConsumer;

final class DeploymentTransition implements AutoCloseable {
    private final int fadeTicks;
    private final Runnable teleport;
    private final DoubleConsumer frame;
    private int tick;
    private boolean finished;

    DeploymentTransition(int fadeTicks, Runnable teleport, DoubleConsumer frame) {
        this.fadeTicks = fadeTicks;
        this.teleport = teleport;
        this.frame = frame;
    }

    boolean tick() {
        if (finished) return true;
        int teleportTick = fadeTicks + 2;
        double opacity = tick <= fadeTicks ? (double) tick / fadeTicks
                : 1.0 - (double) Math.max(0, tick - teleportTick) / fadeTicks;
        frame.accept(Math.clamp(opacity, 0, 1));
        if (tick == teleportTick) teleport.run();
        finished |= tick++ >= teleportTick + fadeTicks;
        return finished;
    }

    @Override
    public void close() {
        finished = true;
    }
}
