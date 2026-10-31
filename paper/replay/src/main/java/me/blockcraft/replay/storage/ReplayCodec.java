package me.blockcraft.replay.storage;

import me.blockcraft.replay.data.ReplayData;
import me.blockcraft.replay.data.ReplayEvent;
import me.blockcraft.replay.data.ReplayHeader;
import me.blockcraft.replay.data.ReplayLimits;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ReplayCodec {

    /** The event would take the recording past its object budget; everything before it still fits. */
    static final class BudgetExceeded extends IOException {
        BudgetExceeded() {
            super("Replay object budget exceeded");
        }
    }

    private static final int MAGIC = 0x42435250;
    private static final int VERSION = 3;
    private static final int FIRST_READABLE_VERSION = 2;
    private static final int MAX_TEXT_BYTES = 32_768;
    private static final int MAX_ITEM_BYTES = 65_536;
    private static final int ALL_EQUIPMENT_SLOTS = 63;
    private static final byte[] EMPTY_ITEM = new byte[0];
    private static final ReplayEvent.Motion[] MOTIONS = ReplayEvent.Motion.values();
    private final ReplayLimits limits;
    private final Set<UUID> participants;
    private final Map<UUID, List<byte[]>> equipmentByActor = new HashMap<>();
    private final Map<UUID, UUID> uuids = new HashMap<>();
    private int eventCount;
    private int chunkCount;
    private int previousTick;
    private long objects;

    ReplayCodec(ReplayLimits limits, ReplayHeader header) throws IOException {
        this.limits = limits;
        count(header.participants().size(), limits.maxParticipants(), "participants");
        participants = new HashSet<>();
        for (ReplayHeader.Participant participant : header.participants()) {
            require(participants.add(participant.id()), "Duplicate participant");
        }
    }

    static void writeHeader(DataOutputStream out, ReplayHeader header) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        uuid(out, header.id());
        text(out, header.game());
        text(out, header.server());
        text(out, header.minecraft());
        text(out, header.build());
        text(out, header.map());
        out.writeLong(header.startedAt().toEpochMilli());
        out.writeInt(header.participants().size());
        for (ReplayHeader.Participant participant : header.participants()) {
            uuid(out, participant.id());
            text(out, participant.name());
            text(out, participant.texture());
            text(out, participant.signature());
        }
    }

    static ReplayHeader readHeader(DataInputStream in, ReplayLimits limits) throws IOException {
        require(in.readInt() == MAGIC, "Invalid replay magic");
        int version = in.readInt();
        require(version >= FIRST_READABLE_VERSION && version <= VERSION, "Unsupported replay version");
        UUID id = uuid(in);
        String game = text(in);
        String server = text(in);
        String minecraft = text(in);
        String build = text(in);
        String map = text(in);
        Instant startedAt = Instant.ofEpochMilli(in.readLong());
        int size = count(in.readInt(), limits.maxParticipants(), "participants");
        List<ReplayHeader.Participant> participants = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            participants.add(new ReplayHeader.Participant(uuid(in), text(in), text(in), text(in)));
        }
        return new ReplayHeader(id, game, server, minecraft, build, map, startedAt, participants);
    }

    void writeEvent(DataOutputStream out, ReplayEvent event) throws IOException {
        checkTick(event.tick());
        switch (event) {
            case ReplayEvent.Frame frame -> {
                checkFrame(frame);
                out.writeByte(4);
                out.writeInt(frame.tick());
                out.writeInt(frame.actors().size());
                for (ReplayEvent.Actor actor : frame.actors()) {
                    uuid(out, actor.id());
                    uuid(out, actor.world());
                    out.writeDouble(actor.x());
                    out.writeDouble(actor.y());
                    out.writeDouble(actor.z());
                    out.writeFloat(actor.yaw());
                    out.writeFloat(actor.pitch());
                    text(out, actor.pose());
                    writeEquipment(out, actor.id(), actor.equipment());
                }
                out.writeInt(frame.props().size());
                for (ReplayEvent.Prop prop : frame.props()) {
                    uuid(out, prop.id());
                    uuid(out, prop.world());
                    out.writeDouble(prop.x());
                    out.writeDouble(prop.y());
                    out.writeDouble(prop.z());
                    out.writeFloat(prop.yaw());
                    out.writeFloat(prop.pitch());
                }
                out.writeInt(frame.appearances().size());
                for (ReplayEvent.Appearance appearance : frame.appearances()) {
                    uuid(out, appearance.id());
                    text(out, appearance.entity());
                }
                out.writeInt(frame.animations().size());
                for (ReplayEvent.Animation animation : frame.animations()) {
                    uuid(out, animation.subject());
                    out.writeByte(animation.motion().ordinal());
                }
            }
            case ReplayEvent.Chunk chunk -> {
                checkChunk(chunk);
                out.writeByte(2);
                out.writeInt(chunk.tick());
                uuid(out, chunk.world());
                out.writeInt(chunk.x());
                out.writeInt(chunk.z());
                out.writeInt(chunk.minY());
                out.writeInt(chunk.height());
                out.writeInt(chunk.palette().size());
                for (String block : chunk.palette()) text(out, block);
                for (int index : chunk.blocks()) out.writeInt(index);
            }
            case ReplayEvent.Blocks changes -> {
                count(changes.blocks().size(), limits.maxBlockChangesPerTick(), "block changes");
                addObjects(changes.blocks().size());
                out.writeByte(3);
                out.writeInt(changes.tick());
                out.writeInt(changes.blocks().size());
                for (ReplayEvent.Block block : changes.blocks()) {
                    uuid(out, block.world());
                    out.writeInt(block.x());
                    out.writeInt(block.y());
                    out.writeInt(block.z());
                    text(out, block.data());
                }
            }
        }
    }

    ReplayData read(DataInputStream in, ReplayHeader header, boolean retainEvents) throws IOException {
        List<ReplayEvent> events = new ArrayList<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) {
                int duration = in.readInt();
                checkDuration(duration);
                String reason = text(in);
                require(in.read() == -1, "Trailing replay data");
                return new ReplayData(header, events, duration, reason);
            }
            int tick = in.readInt();
            checkTick(tick);
            ReplayEvent event = switch (type) {
                case 1 -> {
                    var frame = new ReplayEvent.Frame(tick, readActors(in));
                    checkFrame(frame);
                    yield frame;
                }
                case 4 -> {
                    var actors = readActors(in);
                    int propCount = count(in.readInt(), limits.maxEntities(), "props");
                    List<ReplayEvent.Prop> props = new ArrayList<>(propCount);
                    for (int i = 0; i < propCount; i++) {
                        props.add(new ReplayEvent.Prop(shared(uuid(in)), shared(uuid(in)), in.readDouble(), in.readDouble(),
                                in.readDouble(), in.readFloat(), in.readFloat()));
                    }
                    int appearanceCount = count(in.readInt(), limits.maxEntities(), "appearances");
                    List<ReplayEvent.Appearance> appearances = new ArrayList<>(appearanceCount);
                    for (int i = 0; i < appearanceCount; i++) {
                        appearances.add(new ReplayEvent.Appearance(shared(uuid(in)), text(in)));
                    }
                    int animationCount = count(in.readInt(), maxAnimations(), "animations");
                    List<ReplayEvent.Animation> animations = new ArrayList<>(animationCount);
                    for (int i = 0; i < animationCount; i++) {
                        animations.add(new ReplayEvent.Animation(shared(uuid(in)),
                                MOTIONS[count(in.readUnsignedByte(), MOTIONS.length - 1, "motion")]));
                    }
                    var frame = new ReplayEvent.Frame(tick, actors, props, appearances, animations);
                    checkFrame(frame);
                    yield frame;
                }
                case 2 -> {
                    require(++chunkCount <= limits.maxChunks(), "Too many chunks");
                    UUID world = uuid(in);
                    int x = in.readInt();
                    int z = in.readInt();
                    int minY = in.readInt();
                    int height = in.readInt();
                    int blockCount = blockCount(minY, height);
                    int paletteSize = count(in.readInt(), Math.min(blockCount, 65_536), "palette entries");
                    require(paletteSize > 0, "Empty chunk palette");
                    addObjects(paletteSize);
                    List<String> palette = new ArrayList<>(paletteSize);
                    for (int i = 0; i < paletteSize; i++) palette.add(text(in));
                    int[] blocks = new int[blockCount];
                    for (int i = 0; i < blockCount; i++) {
                        blocks[i] = count(in.readInt(), paletteSize - 1, "palette index");
                    }
                    yield new ReplayEvent.Chunk(tick, world, x, z, minY, height, palette, blocks);
                }
                case 3 -> {
                    int size = count(in.readInt(), limits.maxBlockChangesPerTick(), "block changes");
                    addObjects(size);
                    List<ReplayEvent.Block> blocks = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        blocks.add(new ReplayEvent.Block(uuid(in), in.readInt(), in.readInt(), in.readInt(), text(in)));
                    }
                    yield new ReplayEvent.Blocks(tick, blocks);
                }
                default -> throw new IOException("Unknown replay event type: " + type);
            };
            if (retainEvents) events.add(event);
        }
    }

    private List<ReplayEvent.Actor> readActors(DataInputStream in) throws IOException {
        int size = count(in.readInt(), limits.maxParticipants(), "actors");
        List<ReplayEvent.Actor> actors = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID id = shared(uuid(in));
            UUID world = shared(uuid(in));
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            float yaw = in.readFloat();
            float pitch = in.readFloat();
            String pose = text(in);
            require(participants.contains(id), "Unknown equipment actor");
            List<byte[]> equipment = readEquipment(in, id);
            actors.add(new ReplayEvent.Actor(id, world, x, y, z, yaw, pitch, pose, equipment));
        }
        return actors;
    }

    /** One instance per id, so the worlds and props repeated in every frame cost their memory once. */
    private UUID shared(UUID id) {
        var known = uuids.putIfAbsent(id, id);
        return known == null ? id : known;
    }

    private int maxAnimations() {
        return MOTIONS.length * (limits.maxParticipants() + limits.maxEntities());
    }

    void writeFooter(DataOutputStream out, int duration, String reason) throws IOException {
        checkDuration(duration);
        out.writeByte(0);
        out.writeInt(duration);
        text(out, reason);
    }

    private void writeEquipment(DataOutputStream out, UUID id, List<byte[]> equipment) throws IOException {
        var previous = equipmentByActor.get(id);
        int changed = 0;
        int totalBytes = 0;
        for (int slot = 0; slot < 6; slot++) {
            byte[] item = equipment.get(slot);
            count(item.length, MAX_ITEM_BYTES - totalBytes, "equipment bytes");
            totalBytes += item.length;
            if (previous == null || !Arrays.equals(item, previous.get(slot))) changed |= 1 << slot;
        }
        out.writeByte(changed);
        if (changed == 0) return;
        addObjects(2);
        var retained = new ArrayList<byte[]>(6);
        for (int slot = 0; slot < 6; slot++) {
            if ((changed & (1 << slot)) == 0) {
                retained.add(previous.get(slot));
                continue;
            }
            byte[] item = equipment.get(slot);
            out.writeInt(item.length);
            out.write(item);
            if (item.length > 0) addObjects(1);
            retained.add(item.length == 0 ? EMPTY_ITEM : item.clone());
        }
        equipmentByActor.put(id, List.copyOf(retained));
    }

    private List<byte[]> readEquipment(DataInputStream in, UUID id) throws IOException {
        int changed = count(in.readUnsignedByte(), ALL_EQUIPMENT_SLOTS, "equipment slot mask");
        var previous = equipmentByActor.get(id);
        require(previous != null || changed == ALL_EQUIPMENT_SLOTS, "Equipment reference has no previous state");
        if (changed == 0) return previous;
        addObjects(2);
        var equipment = new ArrayList<byte[]>(6);
        int totalBytes = 0;
        for (int slot = 0; slot < 6; slot++) {
            byte[] item;
            if ((changed & (1 << slot)) == 0) {
                item = previous.get(slot);
                count(item.length, MAX_ITEM_BYTES - totalBytes, "equipment bytes");
            } else {
                int length = count(in.readInt(), MAX_ITEM_BYTES - totalBytes, "equipment bytes");
                if (length > 0) addObjects(1);
                item = length == 0 ? EMPTY_ITEM : new byte[length];
                in.readFully(item);
            }
            totalBytes += item.length;
            equipment.add(item);
        }
        var retained = List.copyOf(equipment);
        equipmentByActor.put(id, retained);
        return retained;
    }

    private void checkTick(int tick) throws IOException {
        require(++eventCount <= limits.maxEvents(), "Too many replay events");
        require(tick >= previousTick && tick <= limits.maxDurationTicks(), "Invalid replay tick");
        previousTick = tick;
        addObjects(1);
    }

    private void checkDuration(int duration) throws IOException {
        require(duration >= previousTick && duration <= limits.maxDurationTicks(), "Invalid replay duration");
    }

    private void checkFrame(ReplayEvent.Frame frame) throws IOException {
        count(frame.actors().size(), limits.maxParticipants(), "actors");
        count(frame.props().size(), limits.maxEntities(), "props");
        count(frame.appearances().size(), limits.maxEntities(), "appearances");
        count(frame.animations().size(), maxAnimations(), "animations");
        addObjects(frame.actors().size() + frame.props().size() + frame.appearances().size() + frame.animations().size());
        Set<UUID> seen = new HashSet<>();
        for (ReplayEvent.Actor actor : frame.actors()) {
            require(participants.contains(actor.id()) && seen.add(actor.id()), "Unknown or duplicate actor");
            checkPosition(actor);
            require(actor.equipment().size() == 6, "Expected six equipment slots");
        }
        for (ReplayEvent.Prop prop : frame.props()) {
            require(seen.add(prop.id()), "Duplicate prop");
            checkPosition(prop);
        }
    }

    private static void checkPosition(ReplayEvent.Body<?> body) throws IOException {
        require(Double.isFinite(body.x()) && Double.isFinite(body.y()) && Double.isFinite(body.z())
                && Float.isFinite(body.yaw()) && Float.isFinite(body.pitch()), "Invalid position");
    }

    private void checkChunk(ReplayEvent.Chunk chunk) throws IOException {
        require(++chunkCount <= limits.maxChunks(), "Too many chunks");
        int size = blockCount(chunk.minY(), chunk.height());
        require(chunk.blocks().length == size, "Invalid chunk block count");
        int paletteSize = count(chunk.palette().size(), Math.min(size, 65_536), "palette entries");
        require(paletteSize > 0, "Empty chunk palette");
        addObjects(paletteSize);
        for (int index : chunk.blocks()) count(index, paletteSize - 1, "palette index");
    }

    private int blockCount(int minY, int height) throws IOException {
        require(height > 0 && height <= 1024 && height % 16 == 0 && minY % 16 == 0
                && minY >= -2048 && (long) minY + height <= 2048, "Invalid chunk height");
        require((long) height * 256 * Integer.BYTES <= limits.maxDecodedBytes(), "Chunk exceeds decoded budget");
        return height * 256;
    }

    private void addObjects(long count) throws IOException {
        objects += count;
        if (objects > limits.objectBudget()) throw new BudgetExceeded();
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        require(value != null, "Null replay text");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        count(bytes.length, MAX_TEXT_BYTES, "text bytes");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String text(DataInputStream in) throws IOException {
        int length = count(in.readInt(), MAX_TEXT_BYTES, "text bytes");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException failure) {
            throw new IOException("Invalid replay UTF-8", failure);
        }
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID uuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static int count(int count, int maximum, String label) throws IOException {
        require(count >= 0 && count <= maximum, "Invalid " + label + ": " + count);
        return count;
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
