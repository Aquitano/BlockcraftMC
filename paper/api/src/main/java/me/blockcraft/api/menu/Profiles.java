package me.blockcraft.api.menu;

import me.blockcraft.api.achievement.Achievement;
import me.blockcraft.api.achievement.Achievements;
import me.blockcraft.api.game.Stats;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.player.RealNames;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.task.PlayerTasks;
import me.blockcraft.api.xp.Xp;
import me.blockcraft.common.coins.CoinsRepository;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
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

    record Snapshot(int coins, List<GameStats> games, int achievements) {
    }

    private Optional<Consumer<Player>> recentRound = Optional.empty();
    private Optional<Consumer<Player>> advent = Optional.empty();
    private Optional<Consumer<Player>> shop = Optional.empty();
    private final Plugin plugin;
    private final CoinsRepository coins;
    private final Map<String, StatsRepository> games;
    private final PlayerTasks playerTasks;
    private final Unlocks unlocks;
    private final Achievements achievements;
    private final Xp xp;
    private final Messages messages;
    private final RealNames realNames;

    public Profiles(Plugin plugin, CoinsRepository coins, Map<String, StatsRepository> games, PlayerTasks playerTasks,
                    Unlocks unlocks, Achievements achievements, Xp xp, Messages messages, RealNames realNames) {
        this.plugin = plugin;
        this.coins = coins;
        this.games = games;
        this.playerTasks = playerTasks;
        this.unlocks = unlocks;
        this.achievements = achievements;
        this.xp = xp;
        this.messages = messages;
        this.realNames = realNames;
    }

    public void recentRound(Consumer<Player> open) {
        recentRound = Optional.of(open);
    }

    public void shop(Consumer<Player> open) {
        shop = Optional.of(open);
    }

    public void clearShop() {
        shop = Optional.empty();
    }

    Optional<Consumer<Player>> shop() {
        return shop;
    }

    public void advent(Consumer<Player> open) {
        advent = Optional.of(open);
    }

    public void clearAdvent() {
        advent = Optional.empty();
    }

    Optional<Consumer<Player>> advent() {
        return me.blockcraft.api.BlockcraftApi.get().calendar().is(me.blockcraft.common.calendar.NetworkCalendar.Occasion.ADVENT)
                ? advent : Optional.empty();
    }

    public void clearRecentRound() {
        recentRound = Optional.empty();
    }

    Optional<Consumer<Player>> recentRound() {
        return recentRound;
    }

    Plugin plugin() {
        return plugin;
    }

    Achievements achievements() {
        return achievements;
    }

    Xp xp() {
        return xp;
    }

    Messages messages() {
        return messages;
    }

    RealNames realNames() {
        return realNames;
    }

    public void progression(Player player, Optional<Consumer<Player>> back) {
        var rewards = achievements.levelRewards();
        var uuid = player.getUniqueId();
        playerTasks.latest(player, () -> {
            var owned = new HashSet<String>();
            rewards.stream().flatMap(reward -> reward.cosmetic().stream())
                    .map(reward -> reward.item().substring(0, reward.item().lastIndexOf('.') + 1))
                    .distinct().forEach(prefix -> owned.addAll(unlocks.owned(uuid, prefix)));
            return owned;
        }, owned -> new ProgressionMenu(this, player, rewards, owned, back).open(player));
    }

    /** {@code cosmetics} is the cosmetics menu of whatever server the player is on, where there is one. */
    public void open(Player player, Optional<Consumer<Player>> cosmetics) {
        var uuid = player.getUniqueId();
        playerTasks.latest(player,
                () -> new Snapshot(coins.get(uuid), statsOf(uuid), unlocks.owned(uuid, Achievement.ITEM_PREFIX).size()),
                snapshot -> new ProfileMenu(this, player, snapshot, cosmetics).open(player));
    }

    private List<GameStats> statsOf(UUID player) {
        var loaded = new ArrayList<GameStats>(games.size());
        games.forEach((key, repository) -> loaded.add(new GameStats(key, repository.get(player))));
        return loaded;
    }
}
