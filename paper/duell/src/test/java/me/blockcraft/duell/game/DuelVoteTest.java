package me.blockcraft.duell.game;

import me.blockcraft.api.game.rush.MapPool;
import me.blockcraft.api.game.rush.RushConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DuelVoteTest {

    private static final Map<DuelMode, List<Integer>> PLAYABLE = Map.of(
            DuelMode.VS, List.of(1, 2, 3),
            DuelMode.SG, List.of(1, 2, 3),
            DuelMode.SUPERJUMP, List.of());

    /** Always takes the last of the tied options, which are in natural order. */
    private static final Random LAST = new Random() {
        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    };

    @Test
    public void theMostVotedModeAndMapWin() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SG, OptionalInt.of(2)));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SG, OptionalInt.empty()));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.of(1)));

        assertEquals(new DuelVote.Outcome(DuelMode.SG, 2), vote.tally(PLAYABLE, LAST));
    }

    @Test
    public void sumoVotesUseTheGeneratedRingAlongsideExistingModes() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SUMO, OptionalInt.of(1)));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SUMO, OptionalInt.empty()));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.of(2)));

        assertEquals(new DuelVote.Outcome(DuelMode.SUMO, 1), vote.tally(Map.of(
                DuelMode.VS, List.of(1, 2), DuelMode.SUMO, List.of(1)), LAST));
    }

    @Test
    public void rushUsesItsPinnedMapUnlessAPlayerVotesForAnotherEnabledMap() {
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/rush/maps.yml")), StandardCharsets.UTF_8));
        yaml.set("selection", "courtyard");
        var pool = MapPool.from(yaml, new RushConfig.ArenaSettings(4, 16, 64, 48));
        var games = new DuelGames(null, null, null, null, null, null, null, pool, null, null);
        var vote = new DuelVote(games.mapPresets());
        var playable = Map.of(DuelMode.MLGRUSH, games.rushMaps(), DuelMode.VS, List.of(1), DuelMode.SUMO, List.of(1));
        vote.force(DuelMode.MLGRUSH);

        for (var format : DuelSeries.Format.values()) {
            assertTrue(DuelSeries.plan(format, vote, playable, LAST).contains(new DuelVote.Outcome(DuelMode.MLGRUSH, 2)));
        }
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.MLGRUSH, OptionalInt.of(3)));
        for (var format : DuelSeries.Format.values()) {
            assertTrue(DuelSeries.plan(format, vote, playable, LAST).contains(new DuelVote.Outcome(DuelMode.MLGRUSH, 3)));
        }
        assertEquals(6, games.rushMaps().size());
    }

    @Test
    public void aTieFallsToTheRandom() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SG, OptionalInt.of(1)));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.of(2)));

        assertEquals(new DuelVote.Outcome(DuelMode.SG, 1), vote.tally(PLAYABLE, LAST));
    }

    @Test
    public void mapVotesForALosingModeAreIgnored() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.empty()));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.empty()));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SG, OptionalInt.of(1)));

        assertEquals(new DuelVote.Outcome(DuelMode.VS, 3), vote.tally(PLAYABLE, LAST));
    }

    @Test
    public void withoutVotesOnlyModesWithAPlayableMapComeUp() {
        assertEquals(new DuelVote.Outcome(DuelMode.SG, 3), new DuelVote().tally(PLAYABLE, LAST));
    }

    @Test
    public void votesForUnavailableModesCannotOverrideVotesForPlayableModes() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SUPERJUMP, OptionalInt.empty()));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SUPERJUMP, OptionalInt.empty()));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.of(2)));

        assertEquals(new DuelVote.Outcome(DuelMode.VS, 2), vote.tally(PLAYABLE, LAST));
    }

    @Test
    public void votesOnlyForUnavailableModesFallBackToPlayableModes() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SUPERJUMP, OptionalInt.empty()));

        assertEquals(new DuelVote.Outcome(DuelMode.SG, 3), vote.tally(PLAYABLE, LAST));
    }

    @Test
    public void aForcedModeOutvotesTheOthersWhileItsMapVotesStillCount() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.of(1)));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.of(1)));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SG, OptionalInt.of(1)));
        vote.force(DuelMode.SG);

        assertEquals(new DuelVote.Outcome(DuelMode.SG, 1), vote.tally(PLAYABLE, LAST));
    }

    @Test
    public void withoutAnyPlayableMapsTheVotedModeKeepsItsFallbackMap() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.of(2)));

        assertEquals(new DuelVote.Outcome(DuelMode.VS, 1), vote.tally(Map.of(), LAST));
    }

    @Test
    public void withoutAnyPlayableMapsOrVotesAnyModeKeepsItsFallbackMap() {
        assertEquals(new DuelVote.Outcome(DuelMode.MLGRUSH, 1), new DuelVote().tally(Map.of(), LAST));
    }

    @Test
    public void aTourRequiresBothParticipantsToChooseItAndEitherCanReturnToSingle() {
        var vote = new DuelVote();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var spectator = UUID.randomUUID();
        var participants = List.of(first, second);

        assertEquals(DuelSeries.Format.SINGLE, vote.formatChoice(first));
        assertEquals(DuelSeries.Format.SINGLE, vote.selectedFormat(participants));
        vote.chooseFormat(first, DuelSeries.Format.TOUR);
        vote.chooseFormat(spectator, DuelSeries.Format.TOUR);
        assertEquals(DuelSeries.Format.SINGLE, vote.selectedFormat(participants));
        assertEquals(DuelSeries.Format.SINGLE, vote.selectedFormat(List.of(first)));
        assertEquals(DuelSeries.Format.SINGLE, vote.selectedFormat(List.of(first, first)));
        vote.chooseFormat(second, DuelSeries.Format.TOUR);
        assertEquals(DuelSeries.Format.TOUR, vote.selectedFormat(participants));
        vote.chooseFormat(first, DuelSeries.Format.SINGLE);
        assertEquals(DuelSeries.Format.SINGLE, vote.selectedFormat(participants));
    }

    @Test
    public void ratingRequiresTheReleaseSwitchAndBothPlayersConsentIndependentlyOfFormat() {
        var vote = new DuelVote();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var spectator = UUID.randomUUID();
        var players = List.of(first, second);

        assertEquals(DuelRating.UNRATED, vote.selectedRating(players, true));
        vote.chooseRating(first, DuelRating.RATED);
        vote.chooseRating(spectator, DuelRating.RATED);
        assertEquals(DuelRating.UNRATED, vote.selectedRating(players, true));
        vote.chooseRating(second, DuelRating.RATED);
        assertEquals(DuelRating.UNRATED, vote.selectedRating(players, false));
        assertEquals(DuelRating.RATED, vote.selectedRating(players, true));
        assertEquals(DuelSeries.Format.SINGLE, vote.selectedFormat(players));
        vote.chooseFormat(first, DuelSeries.Format.TOUR);
        vote.chooseFormat(second, DuelSeries.Format.TOUR);
        assertEquals(DuelRating.RATED, vote.selectedRating(players, true));
        assertEquals(DuelRating.UNRATED, vote.selectedRating(List.of(first), true));
        assertEquals(DuelRating.UNRATED, vote.selectedRating(List.of(first, first), true));
        vote.withdraw(second);
        assertEquals(DuelRating.UNRATED, vote.ratingChoice(second));
        assertEquals(DuelRating.UNRATED, vote.selectedRating(players, true));
    }

    @Test
    public void leavingTheModesToChanceKeepsBothPlayersTourConsent() {
        var vote = new DuelVote();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        vote.cast(first, new DuelVote.Choice(DuelMode.SG, OptionalInt.of(2)));
        vote.chooseFormat(first, DuelSeries.Format.TOUR);
        vote.chooseFormat(second, DuelSeries.Format.TOUR);

        vote.clearChoice(first);

        assertTrue(vote.choiceOf(first).isEmpty());
        assertTrue(vote.nominations().isEmpty());
        assertEquals(DuelSeries.Format.TOUR, vote.selectedFormat(List.of(first, second)));
    }

    @Test
    public void aRematchRetainsThePairPreferencesButNeedsFreshRatingConsent() {
        var vote = new DuelVote();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var choice = new DuelVote.Choice(DuelMode.MLGRUSH, OptionalInt.of(2));
        vote.cast(first, choice);
        vote.chooseFormat(first, DuelSeries.Format.TOUR);
        vote.chooseFormat(second, DuelSeries.Format.TOUR);
        vote.chooseRating(first, DuelRating.RATED);
        vote.chooseRating(second, DuelRating.RATED);
        vote.force(DuelMode.SG);

        vote.clearRatings();
        vote.clearForced();

        assertEquals(java.util.Optional.of(choice), vote.choiceOf(first));
        assertEquals(DuelSeries.Format.TOUR, vote.selectedFormat(List.of(first, second)));
        assertEquals(DuelRating.UNRATED, vote.selectedRating(List.of(first, second), true));
        assertTrue(vote.forced().isEmpty());
    }

    @Test
    public void withdrawingClearsTheModeMapAndTourConsentForTheNextOpponent() {
        var vote = new DuelVote();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        vote.cast(first, new DuelVote.Choice(DuelMode.SG, OptionalInt.of(2)));
        vote.chooseFormat(first, DuelSeries.Format.TOUR);
        vote.chooseFormat(second, DuelSeries.Format.TOUR);

        vote.withdraw(first);

        assertTrue(vote.choiceOf(first).isEmpty());
        assertEquals(0, vote.votes(DuelMode.SG));
        assertEquals(DuelSeries.Format.SINGLE, vote.formatChoice(first));
        assertEquals(DuelSeries.Format.TOUR, vote.formatChoice(second));
        assertEquals(DuelSeries.Format.SINGLE, vote.selectedFormat(List.of(first, second)));
    }
}
