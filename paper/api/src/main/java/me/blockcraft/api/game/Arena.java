package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.player.Arrival;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * What every game says at the moments they share, from BlockcraftAPI's {@code arena} texts but under the game's
 * own prefix: the arrival card, who joined and left the round, and the countdowns that
 * {@link LobbyCountdown} and {@link ServerRestart} run.
 */
public final class Arena {

    private final Component game;
    private final Messages messages;

    /** {@code gameMessages} is the game's own file; it names the game as {@code game-name}. */
    public Arena(Messages gameMessages) {
        this.game = gameMessages.get("game-name");
        this.messages = BlockcraftApi.get().messages().prefixedLike(gameMessages);
    }

    public void arrive(Player player) {
        Arrival.show(player, game);
    }

    public void welcome(Player player, int online, int max) {
        arrive(player);
        broadcast("arena.joined", player, online, max);
    }

    public void farewell(Player player, int online, int max) {
        broadcast("arena.left", player, online, max);
    }

    void announceStart(int seconds) {
        Bukkit.broadcast(messages.get("arena.starting-in", Formatter.number("seconds", seconds)));
    }

    void announceRestart(int seconds) {
        Bukkit.broadcast(messages.get("arena.restart-in", Formatter.number("seconds", seconds)));
    }

    private void broadcast(String key, Player player, int online, int max) {
        Bukkit.broadcast(messages.get(key,
                Placeholder.unparsed("player", player.getName()),
                Formatter.number("online", online),
                Formatter.number("max", max)));
    }
}
