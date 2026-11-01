package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The best players of this server's game on the board of the shared waiting island, at its layout's
 * {@code leaderboard} point: the top five by wins, each led by the player's face, which the client draws from the UUID
 * without the pack. Read again every minute; a read that fails keeps what the board showed.
 */
public final class WaitingBoard {

    private static final int PLACES = 5;
    private static final long REFRESH_TICKS = 20L * 60;
    private static final float SCALE = 1.4f;
    private static final int LINE_WIDTH = 400;

    private WaitingBoard() {
    }

    /** Does nothing on a server without the shared waiting island or without a board point in its layout. */
    public static void show(Plugin plugin, StatsRepository stats) {
        var api = BlockcraftApi.get();
        api.waitingLobby().flatMap(lobby -> lobby.point("leaderboard")).ifPresent(at -> {
            var messages = api.messages();
            String key = "games." + api.server().group() + ".name";
            var game = messages.has(key) ? messages.get(key) : Component.text(api.server().group());
            at.getChunk().addPluginChunkTicket(plugin);
            var board = at.getWorld().spawn(at, TextDisplay.class, display -> {
                display.setPersistent(false);
                display.setBillboard(Display.Billboard.FIXED);
                display.setLineWidth(LINE_WIDTH);
                display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(SCALE), new Quaternionf()));
                display.text(render(messages, game, List.of()));
            });
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                if (!board.isValid()) {
                    task.cancel();
                    return;
                }
                Tasks.async(plugin, () -> stats.leaders(Stat.WINS, PLACES), leaders -> {
                    if (board.isValid()) {
                        board.text(render(messages, game, leaders));
                    }
                });
            }, 1L, REFRESH_TICKS);
        });
    }

    private static Component render(Messages messages, Component game, List<Leader> leaders) {
        var lines = new ArrayList<Component>();
        lines.add(messages.get("waiting-board.title", Placeholder.component("game", game)));
        if (leaders.isEmpty()) {
            lines.add(messages.get("waiting-board.empty"));
        }
        int rank = 0;
        for (var leader : leaders) {
            rank++;
            lines.add(messages.get(rank == 1 ? "waiting-board.first" : "waiting-board.entry",
                    Formatter.number("rank", rank),
                    Placeholder.component("face", Component.object(leader.player().map(ObjectContents::playerHead)
                            .orElseGet(() -> ObjectContents.playerHead(leader.name())))),
                    Placeholder.unparsed("name", leader.name()),
                    Formatter.number("value", leader.value())));
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }
}
