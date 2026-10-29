package me.blockcraft.replay.storage;

import me.blockcraft.replay.data.ReplayData;
import me.blockcraft.replay.data.ReplayEvent;
import me.blockcraft.replay.data.ReplayHeader;
import me.blockcraft.replay.data.ReplayLimits;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class ReplayCodecTest {
    private static final ReplayLimits LIMITS = new ReplayLimits(2, 1200, 4, 16, 8, 1, 1,
            64, 2, 10_000, 4_000_000, 8_000_000, 2, 4096);

    @Test
    public void reusesUnchangedEquipmentAcrossAbsenceAndKeepsHistoricalChanges() throws Exception {
        var header = header();
        var first = actor(header, new byte[]{1});
        var changed = actor(header, new byte[]{3});
        var decoded = decode(encode(header, List.of(
                new ReplayEvent.Frame(0, List.of(first)),
                new ReplayEvent.Frame(1, List.of(first)),
                new ReplayEvent.Frame(2, List.of()),
                new ReplayEvent.Frame(3, List.of(first)),
                new ReplayEvent.Frame(4, List.of(changed)),
                new ReplayEvent.Frame(5, List.of(changed)))));
        var initial = equipment(decoded, 0);
        assertSame(initial, equipment(decoded, 1));
        assertSame(initial, equipment(decoded, 3));
        var updated = equipment(decoded, 4);
        assertSame(updated, equipment(decoded, 5));
        assertArrayEquals(new byte[]{1}, initial.getFirst());
        assertArrayEquals(new byte[]{3}, updated.getFirst());
        assertNotSame(initial.getFirst(), updated.getFirst());
        assertSame(initial.get(1), updated.get(1));
        assertThrows(UnsupportedOperationException.class, () -> initial.add(new byte[0]));
    }

    @Test
    public void rejectsEquipmentReferencesWithoutHistoryAndUnknownSlotBits() throws Exception {
        var header = header();
        byte[] encoded = encode(header, List.of(new ReplayEvent.Frame(0, List.of(actor(header, new byte[]{1})))));
        int maskOffset;
        try (var in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            ReplayCodec.readHeader(in, LIMITS);
            in.readUnsignedByte();
            in.readInt();
            in.readInt();
            in.skipNBytes(16 + 16 + 24 + 8);
            in.skipNBytes(in.readInt());
            maskOffset = encoded.length - in.available();
        }
        for (int invalid : List.of(0, 1, 64, 255)) {
            byte[] malformed = encoded.clone();
            malformed[maskOffset] = (byte) invalid;
            assertThrows(IOException.class, () -> decode(malformed));
        }
    }

    @Test
    public void framesCarryPropsTheirFirstAppearanceAndAnimations() throws Exception {
        var header = header();
        var actor = actor(header, new byte[]{1});
        var zombie = new ReplayEvent.Prop(UUID.randomUUID(), new UUID(0, 1), 2.5, 64, -3, 90, 10);
        var frame = new ReplayEvent.Frame(0, List.of(actor), List.of(zombie),
                List.of(new ReplayEvent.Appearance(zombie.id(), "{id:\"minecraft:zombie\"}")),
                List.of(new ReplayEvent.Animation(actor.id(), ReplayEvent.Motion.SWING_MAIN_HAND),
                        new ReplayEvent.Animation(zombie.id(), ReplayEvent.Motion.DEATH)));
        var decoded = (ReplayEvent.Frame) decode(encode(header, List.of(frame))).events().getFirst();
        assertEquals(frame.props(), decoded.props());
        assertEquals(frame.appearances(), decoded.appearances());
        assertEquals(frame.animations(), decoded.animations());
    }

    @Test
    public void readsVersionTwoArchivesAsFramesWithoutProps() throws Exception {
        var header = header();
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            ReplayCodec.writeHeader(out, header);
            out.writeByte(1);
            out.writeInt(0);
            out.writeInt(0);
            new ReplayCodec(LIMITS, header).writeFooter(out, 10, "completed");
        }
        byte[] encoded = bytes.toByteArray();
        encoded[7] = 2;
        var frame = (ReplayEvent.Frame) decode(encoded).events().getFirst();
        assertEquals(List.of(), frame.actors());
        assertEquals(List.of(), frame.props());
    }

    private static ReplayEvent.Actor actor(ReplayHeader header, byte[] hand) {
        return new ReplayEvent.Actor(header.participants().getFirst().id(), new UUID(0, 1),
                1, 65, 1, 0, 0, "STANDING",
                List.of(hand, new byte[]{2}, new byte[0], new byte[0], new byte[0], new byte[0]));
    }

    private static List<byte[]> equipment(ReplayData data, int event) {
        return ((ReplayEvent.Frame) data.events().get(event)).actors().getFirst().equipment();
    }

    private static byte[] encode(ReplayHeader header, List<ReplayEvent> events) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            ReplayCodec.writeHeader(out, header);
            var codec = new ReplayCodec(LIMITS, header);
            for (var event : events) codec.writeEvent(out, event);
            codec.writeFooter(out, 10, "completed");
        }
        return bytes.toByteArray();
    }

    private static ReplayData decode(byte[] bytes) throws IOException {
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var header = ReplayCodec.readHeader(in, LIMITS);
            return new ReplayCodec(LIMITS, header).read(in, header, true);
        }
    }

    private static ReplayHeader header() {
        return new ReplayHeader(UUID.randomUUID(), "rush", "test", "26.3", "test", "arena",
                Instant.ofEpochMilli(1234), List.of(new ReplayHeader.Participant(UUID.randomUUID(), "Thomas", "", "")));
    }
}
