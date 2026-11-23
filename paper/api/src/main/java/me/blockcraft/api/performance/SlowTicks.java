package me.blockcraft.api.performance;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class SlowTicks implements Listener {

    @EventHandler
    public void onTickEnd(ServerTickEndEvent tick) {
        if (tick.getTickDuration() <= 50) return;

        var event = new SlowTick();
        event.tick = tick.getTickNumber();
        event.tickTime = Math.round(tick.getTickDuration() * 1_000_000);
        event.commit();
    }

    @Enabled(false)
    @Name("blockcraft.SlowTick")
    @StackTrace(false)
    private static final class SlowTick extends Event {
        int tick;
        @Timespan(Timespan.NANOSECONDS)
        long tickTime;
    }
}
