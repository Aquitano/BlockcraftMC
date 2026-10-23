package me.blockcraft.api.game;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class RoundSummaryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void theCompactResultContainsOnlyTheGameResultAndPaidRewards() throws Exception {
        var score = new RoundSummary.Score("Steve");
        score.kills = 4;
        score.deaths = 2;
        score.coins = 19;
        score.xp = 11;
        score.notes.add(Component.text("Makellos"));
        var messages = messages();

        var line = score.compact(messages, Component.text("Duell"), messages.get("summary.result.win"));

        assertEquals("» Duell · Sieg · +19 Coins · +11 XP", plain(line));
    }

    @Test
    public void requestedDetailsKeepAllStatsRewardsAndNotesAsASnapshot() throws Exception {
        var score = new RoundSummary.Score("Steve");
        score.kills = 4;
        score.deaths = 2;
        score.bestStreak = 3;
        score.coins = 19;
        score.xp = 11;
        score.notes.add(Component.text("Makellos"));

        var details = score.details(messages());
        score.coins = 100;
        score.notes.add(Component.text("Nachteule"));

        assertEquals(List.of("» Kills: 4", "» Tode: 2", "» Beste Serie: 3 Kills", "» Coins: +19", "» XP: +11", "Makellos"),
                details.stream().map(RoundSummaryTest::plain).toList());
    }

    @Test
    public void theResultCongratulatesEveryWinnerAndNamesThemToEveryoneElse() throws Exception {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var game = Component.text("BedWars");
        var messages = messages();
        var winners = new LinkedHashMap<UUID, String>();
        winners.put(first, "Steve");

        var personal = RoundSummary.resultTitle(messages, game, winners, first);
        assertEquals("Du hast gewonnen!", plain(personal.title()));
        assertEquals("BedWars", plain(personal.subtitle()));
        var watching = RoundSummary.resultTitle(messages, game, winners, viewer);
        assertEquals("Runde vorbei!", plain(watching.title()));
        assertEquals("Steve hat gewonnen!", plain(watching.subtitle()));

        winners.put(second, "Alex");
        assertEquals("Du hast gewonnen!", plain(RoundSummary.resultTitle(messages, game, winners, second).title()));
        assertEquals("Steve, Alex haben gewonnen!", plain(RoundSummary.resultTitle(messages, game, winners, viewer).subtitle()));
    }

    @Test
    public void aDrawNamesNoWinnerInTheResultTitle() throws Exception {
        var title = RoundSummary.resultTitle(messages(), Component.text("Duell"), Map.of(), UUID.randomUUID());

        assertEquals("Unentschieden!", plain(title.title()));
        assertEquals("Duell", plain(title.subtitle()));
    }

    @Test
    public void everyoneOutOrBeatenBooksOneLossAndADrawCostsTheStandingNone() {
        var knockedOut = new RoundSummary.Score("Steve");
        knockedOut.eliminated = true;
        knockedOut.coins = 7;
        var standing = new RoundSummary.Score("Alex");

        assertEquals(new Stats(0, 0, 0, 1, 7), knockedOut.stats(false, true));
        assertEquals(new Stats(0, 0, 0, 1, 7), knockedOut.stats(false, false));
        assertEquals(new Stats(0, 0, 0, 1, 0), standing.stats(false, true));
        assertEquals(new Stats(0, 0, 1, 0, 0), standing.stats(true, true));
        assertEquals(Stats.EMPTY, standing.stats(false, false));
    }

    @Test
    public void aWinnerOutBeforeTheEndBooksNoLoss() {
        var teammate = new RoundSummary.Score("Steve");
        teammate.eliminated = true;

        assertEquals(new Stats(0, 0, 1, 0, 0), teammate.stats(true, true));
    }

    @Test
    public void heartsReadLikeTheHealthBarWithADecimalComma() {
        assertEquals("2,5", RoundSummary.hearts(5.0));
        assertEquals("3", RoundSummary.hearts(6.0));
        assertEquals("2,5", RoundSummary.hearts(4.2));
        assertEquals("0,5", RoundSummary.hearts(0.3));
    }

    private Messages messages() throws Exception {
        var messages = Messages.load(folder.newFolder().toPath(), () -> getClass().getResourceAsStream("/messages.yml"));
        return messages.with(messages.tagsOf("palette"));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
