package me.blockcraft.replay.playback;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;

/** The viewer's hotbar, laid out like a player: jumps beside play, the way out in the corner. */
public enum PlaybackControl implements HotbarItem {

    PLAYERS(0, MenuIcon.SPECTATE_PLAYERS),
    BACK(3, MenuIcon.PREVIOUS),
    PAUSE(4, MenuIcon.LOADING),
    FORWARD(5, MenuIcon.NEXT),
    SPEED(7, MenuIcon.TOOL_SETTINGS),
    EXIT(8, MenuIcon.TOOL_HOME);

    private final int slot;
    private final MenuIcon icon;

    PlaybackControl(int slot, MenuIcon icon) {
        this.slot = slot;
        this.icon = icon;
    }

    static void give(Player viewer, Messages messages, PlaybackManager.State state) {
        for (var control : values()) {
            String key = "controls." + switch (control) {
                case PLAYERS -> "players";
                case BACK -> "back";
                case PAUSE -> state.finished() ? "restart" : state.paused() ? "resume" : "pause";
                case FORWARD -> "forward";
                case SPEED -> "speed";
                case EXIT -> "exit";
            };
            var speed = Formatter.number("speed", state.speed());
            viewer.getInventory().setItem(control.slot, control.build(control.icon.item()
                    .name(messages.get(key + ".name", speed)).lore(messages.get(key + ".hint", speed))));
        }
    }

    static int home() {
        return PAUSE.slot;
    }
}
