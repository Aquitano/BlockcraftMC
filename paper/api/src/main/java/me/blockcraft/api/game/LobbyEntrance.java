package me.blockcraft.api.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Where a joiner ends up. Whom the {@link Round} seats as a player starts afresh in adventure mode at the lobby spawn
 * with the lobby hotbar, gets what else the game hands a newcomer ({@code welcome}) and keeps the
 * {@link LobbyCountdown} going, which a full lobby runs down to {@code fullSeconds} if the game has them. Everyone
 * else watches from {@code watchSpawn}.
 */
public final class LobbyEntrance {

    private final Round round;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final Supplier<Optional<Location>> lobbySpawn;
    private final Supplier<Optional<Location>> watchSpawn;
    private final OptionalInt fullSeconds;
    private final Consumer<Player> welcome;

    public LobbyEntrance(Round round, LobbyCountdown countdown, LobbyHotbar hotbar, Supplier<Optional<Location>> lobbySpawn,
                         Supplier<Optional<Location>> watchSpawn, OptionalInt fullSeconds, Consumer<Player> welcome) {
        this.round = round;
        this.countdown = countdown;
        this.hotbar = hotbar;
        this.lobbySpawn = lobbySpawn;
        this.watchSpawn = watchSpawn;
        this.fullSeconds = fullSeconds;
        this.welcome = welcome;
    }

    public void admit(Player player) {
        if (!round.join(player)) {
            watchSpawn.get().ifPresent(player::teleport);
            return;
        }
        PlayerReset.reset(player, GameMode.ADVENTURE);
        lobbySpawn.get().ifPresent(player::teleport);
        hotbar.give(player);
        welcome.accept(player);
        countdown.start();
        if (round.full()) {
            fullSeconds.ifPresent(countdown::shorten);
        }
    }
}
