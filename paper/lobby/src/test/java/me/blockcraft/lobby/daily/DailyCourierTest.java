package me.blockcraft.lobby.daily;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DailyCourierTest {

    /** The network's colour tags come from BlockcraftAPI, which the test does not load, so they stay in the text. */
    private static final String NETWORK_TAG = "<[a-z]+>";

    private final Messages messages = Messages.load(Path.of("/nonexistent-lobby-messages"),
            () -> getClass().getResourceAsStream("/messages.yml"));

    @Test
    public void anOpenRewardComesWithItsButtonsAndATakenOneSaysWhenTheNextIsReady() {
        var open = plain(DailyCourier.lines(messages, new DailyRewards.Today(3, 20, false), Duration.ZERO,
                List.of(Component.text("[claim]"), Component.text("[days]"))));
        assertEquals(2, open.size());
        assertTrue(open.getFirst(), open.getFirst().contains("Tag 3") && open.getFirst().contains("20 Coins"));
        assertTrue(open.get(1), open.get(1).contains("[claim] [days]"));

        var taken = plain(DailyCourier.lines(messages, new DailyRewards.Today(3, 20, true), Duration.ofMinutes(4 * 60 + 5), List.of()));
        assertEquals(1, taken.size());
        assertTrue(taken.getFirst(), taken.getFirst().contains("4 Std. 5 Min."));
    }

    @Test
    public void theComingDaysFollowTodaysStreak() {
        var line = plain(List.of(DailyCourier.upcoming(messages, new DailyRewards(List.of(10, 15, 20, 25), 3, ""),
                new DailyRewards.Today(2, 15, false)))).getFirst();
        assertTrue(line, line.contains("Tag 3: 20 Coins, Tag 4: 25 Coins, Tag 5: 25 Coins"));
    }

    private static List<String> plain(List<Component> lines) {
        var text = lines.stream().map(line -> PlainTextComponentSerializer.plainText().serialize(line).replaceAll(NETWORK_TAG, "")).toList();
        text.forEach(line -> assertFalse(line, line.contains("missing")));
        return text;
    }
}
