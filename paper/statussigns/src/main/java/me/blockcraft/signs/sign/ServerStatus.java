package me.blockcraft.signs.sign;

public sealed interface ServerStatus {

    record Online(String motd, int online, int max) implements ServerStatus {
    }

    record Offline() implements ServerStatus {
    }

    default boolean isOnline() {
        return this instanceof Online;
    }
}
