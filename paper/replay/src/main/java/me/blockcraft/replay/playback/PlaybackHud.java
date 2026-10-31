package me.blockcraft.replay.playback;

import me.blockcraft.api.game.TimerBar;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** The timeline as a boss bar over the recording and the hotbar, whose labels follow pause and speed. */
final class PlaybackHud {

    private record Controls(boolean paused, boolean finished, double speed) { }

    private final Player viewer;
    private final Messages messages;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 0, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
    private Controls controls;

    PlaybackHud(Player viewer, Messages messages) {
        this.viewer = viewer;
        this.messages = messages;
    }

    void show() {
        viewer.showBossBar(bar);
    }

    void update(PlaybackManager.State state) {
        bar.name(label(state));
        bar.progress(Math.clamp(state.tick() / (float) Math.max(1, state.durationTicks()), 0, 1));
        var next = new Controls(state.paused(), state.finished(), state.speed());
        if (!next.equals(controls)) {
            controls = next;
            PlaybackControl.give(viewer, messages, state);
        }
    }

    void hide() {
        viewer.hideBossBar(bar);
    }

    private Component label(PlaybackManager.State state) {
        String key = state.preparing() ? "preparing" : state.finished() ? "finished" : state.paused() ? "paused" : "playing";
        return messages.get("playback.status",
                Placeholder.unparsed("time", TimerBar.clock(state.tick() / 20)),
                Placeholder.unparsed("duration", TimerBar.clock(state.durationTicks() / 20)),
                Formatter.number("speed", state.speed()),
                Placeholder.component("state", messages.get("playback.state." + key)));
    }
}
