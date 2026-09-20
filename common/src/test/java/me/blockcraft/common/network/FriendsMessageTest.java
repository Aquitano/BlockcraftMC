package me.blockcraft.common.network;

import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class FriendsMessageTest {
    @Test
    public void requestAndReplyPreserveCorrelationActionsAndAllFriends() throws IOException {
        var id = UUID.randomUUID();
        var request = new FriendsMessage.Request(id, FriendsMessage.Action.ACCEPT, "Thomas");
        assertEquals(request, FriendsMessage.request(FriendsMessage.encode(request)));
        var entries = java.util.stream.IntStream.range(0, 50)
                .mapToObj(index -> new FriendsMessage.Entry("friend" + index, index % 2 == 0, "lobby", false)).toList();
        var reply = new FriendsMessage.Reply(id, FriendsMessage.Status.OK, entries, List.of("Thomas"));
        assertEquals(reply, FriendsMessage.reply(FriendsMessage.encode(reply)));
        var failed = new FriendsMessage.Reply(id, FriendsMessage.Status.FAILED, List.of(), List.of());
        assertNotEquals(new FriendsMessage.Reply(id, FriendsMessage.Status.OK, List.of(), List.of()),
                FriendsMessage.reply(FriendsMessage.encode(failed)));
    }
    @Test
    public void malformedOrOversizedPacketsAreRejected() {
        var request = new FriendsMessage.Request(UUID.randomUUID(), FriendsMessage.Action.ADD, "Thomas");
        var bytes = FriendsMessage.encode(request);
        assertThrows(IOException.class, () -> FriendsMessage.request(java.util.Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IOException.class, () -> FriendsMessage.request(java.util.Arrays.copyOf(bytes, bytes.length + 1)));
        assertThrows(IOException.class, () -> FriendsMessage.reply(new byte[32_767]));
        assertThrows(IOException.class, () -> FriendsMessage.request(FriendsMessage.encode(
                new FriendsMessage.Request(request.id(), FriendsMessage.Action.ADD, "Thomas extra"))));
        var oversized = new FriendsMessage.Reply(request.id(), FriendsMessage.Status.OK, List.of(),
                java.util.Collections.nCopies(4096, "SixteenCharNameXX"));
        assertThrows(IllegalArgumentException.class, () -> FriendsMessage.encode(oversized));
    }
}
