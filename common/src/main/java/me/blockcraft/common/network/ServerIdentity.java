package me.blockcraft.common.network;

/** How a backend is known to the proxy: its unique name, the group of interchangeable servers it belongs to, and where it listens. */
public record ServerIdentity(String name, String group, String host, int port) {
}
