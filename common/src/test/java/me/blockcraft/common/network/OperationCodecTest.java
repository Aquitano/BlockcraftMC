package me.blockcraft.common.network;

import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.common.network.operation.NetworkRequest;
import me.blockcraft.common.network.operation.OperationCodec;
import me.blockcraft.common.network.operation.OperationResult;
import me.blockcraft.common.player.PlayerReference;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class OperationCodecTest {

    @Test
    public void everyRequestAndReplySurvivesTheRoundTrip() throws Exception {
        var account = PlayerReference.known(UUID.randomUUID(), "Player");
        var operations = List.of(
                new NetworkRequest.SubmitReport("Nick", "Werbung"),
                new NetworkRequest.TimedPunishment(account, PunishmentType.MUTE, 3600, "Spam"),
                new NetworkRequest.PresetPunishment(PlayerReference.named("Legacy"), 3));
        for (var operation : operations) {
            var request = new OperationCodec.Request(UUID.randomUUID(), operation);
            assertEquals(request, OperationCodec.request(OperationCodec.encode(request)));
        }
        for (var result : OperationResult.values()) {
            var reply = new OperationCodec.Reply(UUID.randomUUID(), result);
            assertEquals(reply, OperationCodec.reply(OperationCodec.encode(reply)));
        }
    }

    @Test
    public void rejectsOtherVersionsTruncationAndTrailingBytes() {
        var bytes = OperationCodec.encode(new OperationCodec.Request(UUID.randomUUID(), new NetworkRequest.SubmitReport("Player", "Reason")));
        for (int size = 0; size < bytes.length; size++) {
            var truncated = Arrays.copyOf(bytes, size);
            assertThrows(IOException.class, () -> OperationCodec.request(truncated));
        }
        assertThrows(IOException.class, () -> OperationCodec.request(Arrays.copyOf(bytes, bytes.length + 1)));
        bytes[0] = 99;
        assertThrows(IOException.class, () -> OperationCodec.request(bytes));
    }

    @Test
    public void requestsValidateTheirFields() {
        assertThrows(IllegalArgumentException.class, () -> new NetworkRequest.SubmitReport("Player", "\ncommand"));
        assertThrows(IllegalArgumentException.class, () -> new NetworkRequest.SubmitReport("Player", "x".repeat(201)));
        assertThrows(IllegalArgumentException.class, () -> new NetworkRequest.SubmitReport("foo.bar", "Reason"));
        assertThrows(IllegalArgumentException.class,
                () -> new NetworkRequest.TimedPunishment(PlayerReference.named("Player"), PunishmentType.BAN, -1, "Reason"));
    }
}
