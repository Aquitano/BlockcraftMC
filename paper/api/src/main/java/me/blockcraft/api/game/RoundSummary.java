package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.coins.Coins;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.xp.XpReward;
import net.kyori.adventure.text.Component;
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

/**
 * What a round paid and who won it. A game feeds it while the round runs and calls {@link #show()} once at the
 * end; because every coin and every XP point of a round is handed out here, the recap names exactly what was
 * paid.
 */
public final class RoundSummary {

    /** What a kill and a win pay; the amounts are the game's own {@code rewards} keys in its config.yml. */
    public record Rewards(int kill, int win) {
    }

    private static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700));

    private final Plugin plugin;
    private final Component game;
    private final Rewards rewards;
    private final Map<UUID, Score> scores = new LinkedHashMap<>();
    private final Map<UUID, String> winners = new LinkedHashMap<>();

    public RoundSummary(Plugin plugin, Component game, Rewards rewards) {
        this.plugin = plugin;
        this.game = game;
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
        score.xp += BlockcraftApi.get().xp().award(killer, XpReward.KILL);
        return pay(killer, rewards.kill());
    }

    public void death(Player victim) {
        score(victim).deaths++;
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
        var messages = BlockcraftApi.get().messages();
        var names = String.join(", ", winners.values());
        var rule = messages.get("summary.rule");
        var headline = messages.get("summary.game", Placeholder.component("game", game));
        var winnerLine = winners.isEmpty()
                ? messages.get("summary.draw")
                : messages.get("summary.winner", Placeholder.unparsed("winner", names));
        var subtitle = winners.isEmpty()
                ? messages.get("summary.title.subtitle-draw")
                : messages.get("summary.title.subtitle", Placeholder.unparsed("winner", names));
        for (var player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(rule);
            player.sendMessage(headline);
            player.sendMessage(winnerLine);
            var score = scores.get(player.getUniqueId());
            if (score == null) {
                player.sendMessage(rule);
                continue;
            }
            player.sendMessage(messages.get("summary.kills", Formatter.number("kills", score.kills)));
            player.sendMessage(messages.get("summary.deaths", Formatter.number("deaths", score.deaths)));
            player.sendMessage(messages.get("summary.coins", Formatter.number("coins", score.coins)));
            player.sendMessage(messages.get("summary.xp", Formatter.number("xp", score.xp)));
            player.sendMessage(rule);
            boolean won = winners.containsKey(player.getUniqueId());
            player.showTitle(Title.title(messages.get(titleKey(won)), subtitle, TIMES));
            (won ? Sounds.WIN : Sounds.LOSE).play(player);
            if (score.coins > 0) {
                Sounds.COINS.play(player);
            }
        }
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
        private int coins;
        private int xp;
    }
}
