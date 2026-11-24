package me.blockcraft.quicksg.game;

import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.world.MapBundle;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.kit.Kit;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/** The shared sidebar, with the kit the player chose while the lobby waits. */
public final class GameScoreboard {

    private final Messages messages;
    private final GameSidebar sidebar;
    private final Component map;
    private final int maxPlayers;

    public GameScoreboard(Messages messages, Round round, MapBundle map, int maxPlayers) {
        this.messages = messages;
        this.sidebar = new GameSidebar(messages.get("scoreboard.title"), round);
        this.map = Component.text(map.name());
        this.maxPlayers = maxPlayers;
    }

    public void showLobby(Player player, Kit kit) {
        sidebar.lobby(player, maxPlayers, map, List.of(new GameSidebar.Section(
                messages.get("scoreboard.kit", GameSidebar.icons(player)),
                GameSidebar.value(messages.get(kit.messageKey() + ".name")))));
    }

    public void showIngame(Player player, int secondsLeft) {
        sidebar.round(player, maxPlayers, secondsLeft, map, List.of());
    }

    public void showEnding(Player player, List<String> winners) {
        sidebar.end(player, winners.stream().<Component>map(Component::text).toList());
    }
}
