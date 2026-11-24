package me.blockcraft.api.cosmetic;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ChatTagChoiceTest {

    @Test
    public void aYearlyTagNeedsItsYearAndOnlyItHasOne() {
        assertEquals(Optional.of(ChatTagChoice.of(ChatTag.NEW_YEAR, 2027)), ChatTagChoice.parse("neujahr-2027"));
        assertEquals(Optional.empty(), ChatTagChoice.parse("neujahr"));
        assertEquals(Optional.empty(), ChatTagChoice.parse("og-2027"));
        assertEquals(Optional.of(ChatTagChoice.of(ChatTag.OG)), ChatTagChoice.parse("og"));
    }

    @Test
    public void earnedTagsComeFromUnlocksAndPremiumTagsFromTheRank() {
        var owned = Set.of("tag.neujahr-2028", "tag.og", "tag.neujahr-2027", "tag.pro", "lobby.secret.zimmer");

        assertEquals(List.of(ChatTagChoice.of(ChatTag.OG), ChatTagChoice.of(ChatTag.NEW_YEAR, 2027), ChatTagChoice.of(ChatTag.NEW_YEAR, 2028)),
                ChatTagChoice.available(false, owned));
        assertEquals(7 + 3, ChatTagChoice.available(true, owned).size());
    }
}
