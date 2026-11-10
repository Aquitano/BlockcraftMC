package me.blockcraft.system.party;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public final class Party {

    private final Set<UUID> members = new CopyOnWriteArraySet<>();
    private volatile UUID leader;

    Party(UUID leader) {
        this.leader = leader;
    }

    public UUID leader() {
        return leader;
    }

    /** Everyone except the leader, in the order they joined. */
    public Set<UUID> members() {
        return members;
    }

    /** Hands the party to one of its members and returns the leader it had until now. */
    UUID promote(UUID member) {
        var former = leader;
        members.remove(member);
        leader = member;
        return former;
    }
}
