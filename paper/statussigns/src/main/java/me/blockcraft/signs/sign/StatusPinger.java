package me.blockcraft.signs.sign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Minimal Server List Ping client (handshake to status state, status request, one JSON response). */
public final class StatusPinger {

    private static final int HANDSHAKE_PACKET = 0x00;
    private static final int STATUS_REQUEST_PACKET = 0x00;
    private static final int STATUS_RESPONSE_PACKET = 0x00;
    private static final int STATUS_STATE = 1;
    private static final int ANY_PROTOCOL_VERSION = -1;
    private static final int MAX_RESPONSE_BYTES = 1 << 16;

    private final int timeoutMillis;

    public StatusPinger(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public ServerStatus ping(String host, int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            var out = new DataOutputStream(socket.getOutputStream());
            var in = new DataInputStream(socket.getInputStream());

            var handshake = new ByteArrayOutputStream();
            var handshakeData = new DataOutputStream(handshake);
            writeVarInt(handshakeData, HANDSHAKE_PACKET);
            writeVarInt(handshakeData, ANY_PROTOCOL_VERSION);
            writeString(handshakeData, host);
            handshakeData.writeShort(port);
            writeVarInt(handshakeData, STATUS_STATE);
            writePacket(out, handshake.toByteArray());
            writePacket(out, new byte[]{STATUS_REQUEST_PACKET});

            int length = readVarInt(in);
            if (length <= 0 || length > MAX_RESPONSE_BYTES || readVarInt(in) != STATUS_RESPONSE_PACKET) {
                return new ServerStatus.Offline();
            }
            return parse(readString(in));
        } catch (IOException | RuntimeException e) {
            return new ServerStatus.Offline();
        }
    }

    private static ServerStatus parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject players = root.getAsJsonObject("players");
        int online = players == null ? 0 : players.get("online").getAsInt();
        int max = players == null ? 0 : players.get("max").getAsInt();
        String motd = root.has("description")
                ? PlainTextComponentSerializer.plainText().serialize(
                        GsonComponentSerializer.gson().deserializeFromTree(root.get("description"))).strip()
                : "";
        return new ServerStatus.Online(motd, online, max);
    }

    private static void writePacket(DataOutputStream out, byte[] payload) throws IOException {
        writeVarInt(out, payload.length);
        out.write(payload);
        out.flush();
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > MAX_RESPONSE_BYTES) {
            throw new IOException("Unreasonable string length " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            byte b = in.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt too big");
            }
        }
    }
}
