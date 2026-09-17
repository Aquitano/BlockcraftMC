package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.coins.Coins;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.xp.XpReward;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What a round paid and who won it. A game feeds it while the round runs and calls {@link #show()} once at the
 * end; because every coin and every XP point of a round is handed out here, the recap names exactly what was
 * paid, and closes with the two ways on: another round of the same game, or the lobby. Kill streaks are counted
 * here too and announced at the lengths {@link Streaks} names.
 */
public final class RoundSummary {

    /** What a kill and a win pay; the amounts are the game's own {@code rewards} keys in its config.yml. */
    public record Rewards(int kill, int win) {
    }

    private static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700));

    /** The buttons stay clickable past the restart countdown, which every game's config.yml sets for itself. */
    private static final ClickCallback.Options BUTTON = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(1))
            .build();

    private final Plugin plugin;
    private final Component game;
    private final Messages messages;
    private final Rewards rewards;
    private final Map<UUID, Score> scores = new LinkedHashMap<>();
    private final Map<UUID, String> winners = new LinkedHashMap<>();

    /** {@code gameMessages} is the game's own file; it names the game as {@code game-name} and lends its prefix. */
    public RoundSummary(Plugin plugin, Messages gameMessages, Rewards rewards) {
        this.plugin = plugin;
        this.game = gameMessages.get("game-name");
        this.messages = BlockcraftApi.get().messages().prefixedLike(gameMessages);
        this.rewards = rewards;
    }

    /** Whoever joins the round as a player; everyone else is a spectator and only sees who won. */
    public void join(Player player) {
        score(player);
    }

    /** Pays the killer, plays their kill effect and returns the coins, for the game to book as points. */
    public int awardKill(Player killer, Player victim) {
        BlockcraftApi.get().effects().onKill(killer, victim);
        Sounds.KILL.play(killer);
        var score = score(killer);
        score.kills++;
        score.streak++;
        score.bestStreak = Math.max(score.bestStreak, score.streak);
        if (BlockcraftApi.get().streaks().announces(score.streak)) {
            Bukkit.broadcast(messages.get("summary.streak",
                    Placeholder.unparsed("player", killer.getName()), Formatter.number("streak", score.streak)));
            Sounds.STREAK.play(killer);
        }
        score.xp += BlockcraftApi.get().xp().award(killer, XpReward.KILL);
        return pay(killer, rewards.kill());
    }

    public int kills(Player player) {
        var score = scores.get(player.getUniqueId());
        return score == null ? 0 : score.kills;
    }

    public void death(Player victim) {
        var score = score(victim);
        score.deaths++;
        score.streak = 0;
    }

    /** Pays the winner, plays their win effect and returns the coins, for the game to book as points. */
    public int awardWin(Player winner) {
        BlockcraftApi.get().effects().onWin(winner);
        winners.put(winner.getUniqueId(), winner.getName());
        var score = score(winner);
        score.xp += BlockcraftApi.get().xp().award(winner, XpReward.WIN);
        return pay(winner, rewards.win());
    }

    /** Coins outside a kill or a win, such as a consolation for dying; they show up in the recap all the same. */
    public int awardCoins(Player player, int coins) {
        return pay(player, coins);
    }

    public void show() {
        var names = String.join(", ", winners.values());
        var rule = messages.get("summary.rule");
        var headline = messages.get("summary.game", Placeholder.component("game", game));
        var winnerLine = winners.isEmpty()
                ? messages.get("summary.draw")
                : messages.get("summary.winner", Placeholder.unparsed("winner", names));
        var subtitle = winners.isEmpty()
                ? messages.get("summary.title.subtitle-draw")
                : messages.get("summary.title.subtitle", Placeholder.unparsed("winner", names));
        var group = BlockcraftApi.get().server().group();
        var again = button("summary.again", player -> ProxyMessaging.play(plugin, player, group));
        var lobby = button("summary.lobby", player -> ProxyMessaging.hub(plugin, player));
        for (var player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(rule);
            player.sendMessage(headline);
            player.sendMessage(winnerLine);
            var score = scores.get(player.getUniqueId());
            if (score != null) {
                player.sendMessage(messages.get("summary.kills", Formatter.number("kills", score.kills)));
                player.sendMessage(messages.get("summary.deaths", Formatter.number("deaths", score.deaths)));
                if (score.bestStreak > 1) {
                    player.sendMessage(messages.get("summary.streak-best", Formatter.number("streak", score.bestStreak)));
                }
                player.sendMessage(messages.get("summary.coins", Formatter.number("coins", score.coins)));
                player.sendMessage(messages.get("summary.xp", Formatter.number("xp", score.xp)));
            }
            player.sendMessage(again);
            player.sendMessage(lobby);
            player.sendMessage(rule);
            if (score == null) {
                continue;
            }
            boolean won = winners.containsKey(player.getUniqueId());
            player.showTitle(Title.title(messages.get(titleKey(won)), subtitle, TIMES));
            (won ? Sounds.WIN : Sounds.LOSE).play(player);
        }
    }

    private Component button(String key, Consumer<Player> action) {
        return messages.get(key)
                .hoverEvent(HoverEvent.showText(messages.get(key + "-hover")))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player player) {
                        Sounds.MENU_CLICK.play(player);
                        action.accept(player);
                    }
                }, BUTTON));
    }

    private String titleKey(boolean won) {
        if (won) {
            return "summary.title.win";
        }
        return winners.isEmpty() ? "summary.title.draw" : "summary.title.lose";
    }

    private int pay(Player player, int coins) {
        if (coins > 0) {
            Coins.award(plugin, player, coins);
            score(player).coins += coins;
        }
        return coins;
    }

    private Score score(Player player) {
        return scores.computeIfAbsent(player.getUniqueId(), id -> new Score());
    }

    private static final class Score {
        private int kills;
        private int deaths;
        private int streak;
        private int bestStreak;
        private int coins;
        private int xp;
    }
}
