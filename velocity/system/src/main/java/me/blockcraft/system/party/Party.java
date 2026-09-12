package me.blockcraft.system.party;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Party {

    private final UUID leader;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();

    Party(UUID leader) {
        this.leader = leader;
    }

    public UUID leader() {
        return leader;
    }

    /** Everyone except the leader. */
    public Set<UUID> members() {
        return members;
    }
}
