package me.blockcraft.common.network.operation;

import me.blockcraft.common.moderation.PunishmentType;
import me.blockcraft.common.player.PlayerReference;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static me.blockcraft.common.network.WireFormat.uuid;
import static me.blockcraft.common.network.WireFormat.write;

/** One versioned format used by both platforms. No command syntax crosses this channel. */
public final class OperationCodec {

    public static final String CHANNEL = "blockcraft:operations";

    private static final int VERSION = 1;
    private static final int MAX_BYTES = 4096;

    public record Request(UUID id, NetworkRequest operation) {
    }

    public record Reply(UUID id, OperationResult result) {
    }

    private OperationCodec() {
    }

    public static byte[] encode(Request request) {
        return write(out -> {
            out.writeByte(VERSION);
            uuid(out, request.id());
            switch (request.operation()) {
                case NetworkRequest.SubmitReport report -> {
                    out.writeUTF("report");
                    out.writeUTF(report.target());
                    out.writeUTF(report.reason());
                }
                case NetworkRequest.TimedPunishment punishment -> {
                    out.writeUTF("timed-punishment");
                    target(out, punishment.target());
                    out.writeUTF(punishment.type().name());
                    out.writeLong(punishment.seconds());
                    out.writeUTF(punishment.reason());
                }
                case NetworkRequest.PresetPunishment punishment -> {
                    out.writeUTF("preset-punishment");
                    target(out, punishment.target());
                    out.writeInt(punishment.number());
                }
            }
        });
    }

    public static Request request(byte[] bytes) throws IOException {
        try (var in = input(bytes)) {
            var id = uuid(in);
            NetworkRequest operation = switch (in.readUTF()) {
                case "report" -> new NetworkRequest.SubmitReport(in.readUTF(), in.readUTF());
                case "timed-punishment" -> new NetworkRequest.TimedPunishment(target(in), PunishmentType.valueOf(in.readUTF()),
                        in.readLong(), in.readUTF());
                case "preset-punishment" -> new NetworkRequest.PresetPunishment(target(in), in.readInt());
                default -> throw new IOException("Unknown operation");
            };
            requireEnd(in);
            return new Request(id, operation);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid operation", e);
        }
    }

    public static byte[] encode(Reply reply) {
        return write(out -> {
            out.writeByte(VERSION);
            uuid(out, reply.id());
            out.writeUTF(reply.result().name());
        });
    }

    public static Reply reply(byte[] bytes) throws IOException {
        try (var in = input(bytes)) {
            var reply = new Reply(uuid(in), OperationResult.valueOf(in.readUTF()));
            requireEnd(in);
            return reply;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid reply", e);
        }
    }

    private static DataInputStream input(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) {
            throw new IOException("Oversized payload");
        }
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readUnsignedByte() != VERSION) {
            throw new IOException("Unsupported protocol version");
        }
        return in;
    }

    private static void requireEnd(DataInputStream in) throws IOException {
        if (in.available() != 0) {
            throw new IOException("Trailing payload");
        }
    }

    private static void target(DataOutputStream out, PlayerReference target) throws IOException {
        out.writeUTF(target.name());
        out.writeBoolean(target.uuid().isPresent());
        if (target.uuid().isPresent()) {
            uuid(out, target.uuid().get());
        }
    }

    private static PlayerReference target(DataInputStream in) throws IOException {
        String name = in.readUTF();
        return new PlayerReference(name, in.readBoolean() ? Optional.of(uuid(in)) : Optional.empty());
    }
}
