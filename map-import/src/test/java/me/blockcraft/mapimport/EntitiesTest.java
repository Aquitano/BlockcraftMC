package me.blockcraft.mapimport;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class EntitiesTest {

    private static final UUID STAND = new UUID(0x1111L, 0x2222L);
    private static final UUID HORSE = new UUID(0x3333L, 0x4444L);
    private static final UUID RIDER = new UUID(0x5555L, 0x6666L);

    @Test
    public void keepsWhatTheOldWorldStoredExceptLooseItems() {
        var original = Set.of(STAND, HORSE);

        assertEquals(Entities.Fate.KEPT, Entities.fate(modern("minecraft:armor_stand", STAND), original));
        assertEquals(Entities.Fate.LOOSE_ITEM, Entities.fate(modern("minecraft:item", HORSE), original));
        assertEquals(Entities.Fate.ADDED, Entities.fate(modern("minecraft:cow", UUID.randomUUID()), original));
    }

    @Test
    public void collectsLegacyUuidsOfMountsAndPassengers() {
        var horse = legacy("EntityHorse", HORSE);
        var rider = CompoundBinaryTag.builder().put(legacy("Zombie", RIDER)).put("Riding", horse).build();
        var stand = CompoundBinaryTag.builder().put(modern("minecraft:armor_stand", STAND))
                .put("Passengers", ListBinaryTag.from(List.of(rider))).build();

        assertEquals(List.of(STAND, RIDER, HORSE), Entities.uuids(stand).toList());
    }

    private static CompoundBinaryTag modern(String id, UUID uuid) {
        return CompoundBinaryTag.builder().putString("id", id).putIntArray("UUID", new int[]{
                (int) (uuid.getMostSignificantBits() >> 32), (int) uuid.getMostSignificantBits(),
                (int) (uuid.getLeastSignificantBits() >> 32), (int) uuid.getLeastSignificantBits()}).build();
    }

    private static CompoundBinaryTag legacy(String id, UUID uuid) {
        return CompoundBinaryTag.builder().putString("id", id)
                .putLong("UUIDMost", uuid.getMostSignificantBits()).putLong("UUIDLeast", uuid.getLeastSignificantBits()).build();
    }
}
