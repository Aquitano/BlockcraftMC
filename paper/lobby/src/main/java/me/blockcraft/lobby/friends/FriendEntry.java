package me.blockcraft.lobby.friends;

/** A friend as the proxy last described them; {@code server} is empty while they are offline. */
public record FriendEntry(String name, boolean online, String server) {
}
