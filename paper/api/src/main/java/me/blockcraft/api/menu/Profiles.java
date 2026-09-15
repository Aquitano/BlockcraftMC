package me.blockcraft.api.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.game.Stats;
import me.blockcraft.api.game.StatsRepository;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Opens a player's own profile card. The games it lists come from {@code profile.games} in BlockcraftAPI's
 * {@code config.yml} (menu key to legacy stats table), so a new game shows up without a code change.
 */
public final class Profiles {

    record GameStats(String key, Stats stats) {
    }

    record Snapshot(int coins, List<GameStats> games) {
    }

    private final Plugin plugin;
    private final CoinsRepository coins;
    private final Map<String, StatsRepository> games;

    public Profiles(Plugin plugin, CoinsRepository coins, Map<String, StatsRepository> games) {
        this.plugin = plugin;
        this.coins = coins;
        this.games = games;
    }

    /** {@code cosmetics} is the cosmetics menu of whatever server the player is on, where there is one. */
    public void open(Player player, Optional<Consumer<Player>> cosmetics) {
        var uuid = player.getUniqueId();
        BlockcraftApi.get().playerTasks().latest(player, () -> new Snapshot(coins.get(uuid), statsOf(uuid)),
                snapshot -> new ProfileMenu(this, player, snapshot, cosmetics).open(player));
    }

    private List<GameStats> statsOf(UUID player) {
        var loaded = new ArrayList<GameStats>(games.size());
        games.forEach((key, repository) -> loaded.add(new GameStats(key, repository.get(player))));
        return loaded;
    }
}
