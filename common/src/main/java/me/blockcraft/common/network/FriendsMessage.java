package me.blockcraft.common.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FriendsMessage {
    private static final int MAX_PAYLOAD_BYTES = 32_766;
    public enum Action { LIST, ADD, ACCEPT, DENY }
    public enum Status { OK, FAILED, FORBIDDEN }
    public record Entry(String name, boolean online, String server, boolean watching) {}
    public record Request(UUID id, Action action, String name) {}
    public record Reply(UUID id, Status status, List<Entry> friends, List<String> requests) {}

    private FriendsMessage() {}

    public static byte[] encode(Request request) {
        return encode(out -> {
            out.writeUTF(request.id().toString());
            out.writeUTF(request.action().name());
            out.writeUTF(request.name());
        });
    }

    public static byte[] encode(Reply reply) {
        return encode(out -> {
            out.writeUTF(reply.id().toString());
            out.writeUTF(reply.status().name());
            out.writeInt(reply.friends().size());
            for (var friend : reply.friends()) {
                out.writeUTF(friend.name());
                out.writeBoolean(friend.online());
                out.writeUTF(friend.server());
                out.writeBoolean(friend.watching());
            }
            out.writeInt(reply.requests().size());
            for (var name : reply.requests()) out.writeUTF(name);
        });
    }

    public static Request request(byte[] bytes) throws IOException {
        try (var in = input(bytes)) {
            var request = new Request(UUID.fromString(in.readUTF()), Action.valueOf(in.readUTF()), in.readUTF());
            if (request.action() == Action.LIST ? !request.name().isEmpty() : !request.name().matches("[A-Za-z0-9_]{3,16}")) {
                throw new IOException("Invalid friend request name");
            }
            end(in);
            return request;
        }
    }

    public static Reply reply(byte[] bytes) throws IOException {
        try (var in = input(bytes)) {
            var id = UUID.fromString(in.readUTF());
            var status = Status.valueOf(in.readUTF());
            var friends = new ArrayList<Entry>();
            for (int count = count(in); count > 0; count--) {
                friends.add(new Entry(in.readUTF(), in.readBoolean(), in.readUTF(), in.readBoolean()));
            }
            var requests = new ArrayList<String>();
            for (int count = count(in); count > 0; count--) requests.add(in.readUTF());
            end(in);
            return new Reply(id, status, List.copyOf(friends), List.copyOf(requests));
        }
    }

    private static void end(DataInputStream in) throws IOException {
        if (in.available() != 0) {
            throw new IOException("Trailing friends payload");
        }
    }

    private static int count(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 4096) throw new IOException("Invalid friends count");
        return count;
    }

    private static DataInputStream input(byte[] bytes) throws IOException {
        if (bytes.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Friends payload exceeds plugin message limit");
        }
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (!in.readUTF().equals(ProxyChannel.FRIENDS)) throw new IOException("Invalid friends channel");
        return in;
    }

    private interface Writer { void write(DataOutputStream out) throws IOException; }

    private static byte[] encode(Writer writer) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            out.writeUTF(ProxyChannel.FRIENDS);
            writer.write(out);
            if (bytes.size() > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Friends payload exceeds plugin message limit");
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
