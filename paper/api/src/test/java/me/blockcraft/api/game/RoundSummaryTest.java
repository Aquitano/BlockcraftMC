package me.blockcraft.api.game;

import me.blockcraft.api.pack.DisplayFont;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class RoundSummaryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void theCompactResultContainsOnlyTheGameResultPaidRewardsAndDetails() throws Exception {
        var score = new RoundSummary.Score("Steve");
        score.kills = 4;
        score.deaths = 2;
        score.coins = 19;
        score.xp = 11;
        score.notes.add(Component.text("Makellos"));
        var messages = messages();

        var line = score.compact(messages, Component.text("Duell"), messages.get("summary.result.win"),
                messages.get("summary.details"));

        assertEquals("» Duell · Sieg · +19 Coins · +11 XP · [Details]", plain(line));
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
    public void theResultCongratulatesEveryWinnerAndNamesThemToWhoeverEarnedNothing() throws Exception {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var messages = messages();
        var winners = new LinkedHashMap<UUID, String>();
        winners.put(first, "Steve");

        var personal = title(messages, winners, first, Optional.empty());
        assertEquals("Gewonnen!", plain(personal.title()));
        assertEquals("", plain(personal.subtitle()));
        var watching = title(messages, winners, viewer, Optional.empty());
        assertEquals("Runde vorbei!", plain(watching.title()));
        assertEquals("Steve hat gewonnen!", plain(watching.subtitle()));

        winners.put(second, "Alex");
        assertEquals("Gewonnen!", plain(title(messages, winners, second, Optional.empty()).title()));
        assertEquals("Steve, Alex haben gewonnen!", plain(title(messages, winners, viewer, Optional.empty()).subtitle()));
    }

    @Test
    public void everyoneWhoEarnedSomethingReadsTheirOwnRewardsUnderTheResult() throws Exception {
        var messages = messages();
        var winner = UUID.randomUUID();
        var winners = Map.of(winner, "Steve");
        var score = new RoundSummary.Score("Alex");
        score.coins = 41;
        score.xp = 65;

        assertEquals("+41 Coins · +65 XP", plain(title(messages, winners, winner, score.rewards(messages)).subtitle()));
        assertEquals("+41 Coins · +65 XP", plain(title(messages, winners, UUID.randomUUID(), score.rewards(messages)).subtitle()));
        assertEquals("+41 Coins · +65 XP", plain(title(messages, Map.of(), UUID.randomUUID(), score.rewards(messages)).subtitle()));
        score.coins = 0;
        assertEquals("+65 XP", plain(score.rewards(messages).orElseThrow()));
        score.xp = 0;
        assertEquals(Optional.empty(), score.rewards(messages));
    }

    @Test
    public void aDrawNamesNoWinnerInTheResultTitle() throws Exception {
        var title = title(messages(), Map.of(), UUID.randomUUID(), Optional.empty());

        assertEquals("Gleichstand!", plain(title.title()));
        assertEquals("", plain(title.subtitle()));
    }

    @Test
    public void aCooperativeDefeatNamesTheEnemyInsteadOfShowingADraw() throws Exception {
        var title = RoundSummary.resultTitle(messages(), Map.of(), List.of("Die Horde"), UUID.randomUUID(), Optional.empty());

        assertEquals("Runde vorbei!", plain(title.title()));
        assertEquals("Die Horde hat gewonnen!", plain(title.subtitle()));
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

    private static Title title(Messages messages, Map<UUID, String> winners, UUID viewer, Optional<Component> rewards) {
        return RoundSummary.resultTitle(messages, winners, List.copyOf(winners.values()), viewer, rewards);
    }

    private Messages messages() throws Exception {
        var messages = Messages.load(folder.newFolder().toPath(), () -> getClass().getResourceAsStream("/messages.yml"));
        return messages.with(messages.tagsOf("palette"), DisplayFont.TAG);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
