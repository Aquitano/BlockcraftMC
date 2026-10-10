package me.blockcraft.common.network;

import me.blockcraft.common.nick.Nick;
import me.blockcraft.common.settings.PlayerSetting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What travels on {@link ProxyMessageCodec#CHANNEL}. Every message concerns the player it travelled with: a backend
 * asks the proxy to move them, run a command as them, learn what they look like to others or what they chose, or
 * deliver a notice on their behalf. The proxy speaks first only to answer, to say whether the client wears the
 * network's resource pack, to name the friend the player came to watch, to say whom the player just ignored and to
 * pass on a setting the player changed through a proxy command.
 */
public sealed interface ProxyMessage {

    sealed interface ToProxy extends ProxyMessage {
    }

    sealed interface ToBackend extends ProxyMessage {
    }

    record Play(String group) implements ToProxy {
    }

    record Hub() implements ToProxy {
    }

    /** Runs on the proxy with the player's own permissions; the line carries no leading slash. */
    record Command(String line) implements ToProxy {
    }

    /** Empty once the nick comes off. */
    record NickChange(Optional<Nick> nick) implements ToProxy {
    }

    record Vanish(boolean vanished) implements ToProxy {
    }

    record Afk(boolean afk) implements ToProxy {
    }

    /** The notice is a JSON component, because {@code common} carries no component serializer. */
    record StaffNotice(String permission, String noticeJson) implements ToProxy {
    }

    record Setting(PlayerSetting setting, String value) implements ToProxy, ToBackend {
    }

    record PackRequest() implements ToProxy {
    }

    record FriendsRequest(UUID id, Action action, String name) implements ToProxy {
        public enum Action { LIST, ADD, ACCEPT, DENY }
    }

    record FriendsReply(UUID id, Status status, List<Entry> friends, List<String> requests) implements ToBackend {
        public enum Status { OK, FAILED, FORBIDDEN }

        public record Entry(String name, boolean online, String server, boolean watching) {
        }
    }

    record Pack(PackStatus status) implements ToBackend {
    }

    /**
     * Sent when a player arrives on a server whose round is already running, because only the proxy knows that the
     * jump was meant as a watch.
     */
    record Spectate(UUID friend) implements ToBackend {
    }

    record Ignore(UUID other, boolean ignoring) implements ToBackend {
    }
}
