package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.player.Arrival;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * What every game says at the moments they share, from BlockcraftAPI's {@code arena} texts but under the game's
 * own prefix: the arrival card, who joined and left the round, who a spectator came to watch, and the countdowns
 * that {@link LobbyCountdown} and {@link ServerRestart} run.
 */
public final class Arena {

    private final Component game;
    private final Messages messages;
    private final Sounds arrivalSound;
    private final Component introduction;

    /** {@code gameMessages} is the game's own file; it names the game as {@code game-name}. */
    public Arena(Messages gameMessages, Sounds arrivalSound) {
        this.arrivalSound = arrivalSound;
        this.introduction = gameMessages.get("join.introduction");
        this.game = gameMessages.get("game-name");
        this.messages = BlockcraftApi.get().messages().prefixedLike(gameMessages);
    }

    public void arrive(Player player) {
        Arrival.show(player, game, arrivalSound);
    }

    public void welcome(Player player, int online, int max) {
        arrive(player);
        player.sendMessage(introduction);
        broadcast("arena.joined", player, online, max);
    }

    /** A spectator who came for a friend: put next to them and told whose round they are watching. */
    public void follow(Player spectator, Player friend) {
        spectator.teleport(friend);
        spectator.sendMessage(messages.get("arena.spectating", Placeholder.unparsed("player", friend.getName())));
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
