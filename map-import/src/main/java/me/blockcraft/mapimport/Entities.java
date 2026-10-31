package me.blockcraft.mapimport;

import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Which entities of the finished world belong in the map: everything the old world stored except loose items, which are
 * drops nobody picked up. Whatever has a UUID the old world never stored was spawned while the finishing server
 * generated the chunk, and stays out too.
 */
public final class Entities {

    public enum Fate { KEPT, LOOSE_ITEM, ADDED }

    private static final String ITEM = "minecraft:item";

    private Entities() {
    }

    public static Fate fate(CompoundBinaryTag entity, Set<UUID> original) {
        if (entity.getString("id").equals(ITEM)) {
            return Fate.LOOSE_ITEM;
        }
        return uuid(entity).filter(original::contains).isPresent() ? Fate.KEPT : Fate.ADDED;
    }

    /**
     * The entities a chunk or entity chunk stores, in any format since 1.8: inside {@code Level} before 1.18, in a
     * half converted chunk's {@code entities}, and in an entity chunk's {@code Entities}.
     */
    public static Stream<CompoundBinaryTag> stored(CompoundBinaryTag chunk) {
        return Stream.of(chunk.getCompound("Level").getList("Entities", BinaryTagTypes.COMPOUND),
                        chunk.getList("entities", BinaryTagTypes.COMPOUND), chunk.getList("Entities", BinaryTagTypes.COMPOUND))
                .flatMap(ListBinaryTag::stream)
                .map(CompoundBinaryTag.class::cast);
    }

    /** The UUIDs of an entity and of everything riding it or ridden by it; 1.8 stored the mount inside its rider. */
    public static Stream<UUID> uuids(CompoundBinaryTag entity) {
        var passengers = entity.getList("Passengers", BinaryTagTypes.COMPOUND).stream().map(CompoundBinaryTag.class::cast);
        var mount = entity.get("Riding") instanceof CompoundBinaryTag riding ? Stream.of(riding) : Stream.<CompoundBinaryTag>empty();
        return Stream.concat(uuid(entity).stream(), Stream.concat(passengers, mount).flatMap(Entities::uuids));
    }

    public static Optional<UUID> uuid(CompoundBinaryTag entity) {
        int[] parts = entity.getIntArray("UUID");
        if (parts.length == 4) {
            return Optional.of(new UUID((long) parts[0] << 32 | parts[1] & 0xFFFFFFFFL, (long) parts[2] << 32 | parts[3] & 0xFFFFFFFFL));
        }
        if (entity.contains("UUIDMost") && entity.contains("UUIDLeast")) {
            return Optional.of(new UUID(entity.getLong("UUIDMost"), entity.getLong("UUIDLeast")));
        }
        return Optional.empty();
    }
}
