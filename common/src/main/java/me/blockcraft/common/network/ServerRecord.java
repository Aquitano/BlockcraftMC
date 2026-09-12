package me.blockcraft.common.network;

/** One live row of {@code bc_servers}. */
public record ServerRecord(ServerIdentity identity, ServerStatus status) {

    public String name() {
        return identity.name();
    }

    public String group() {
        return identity.group();
    }
}
