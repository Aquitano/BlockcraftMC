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
 * network's resource pack, to name the friend the player came to watch, to say whom the player just ignored, to pass
 * on a setting the player changed through a proxy command, to welcome a player who is new to the network and to tell
 * a server that greetings for such a newcomer count, which travels with whichever player is on that server.
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

    /** A line for every player of the network, such as the first find of a secret; JSON like a staff notice. */
    record Announcement(String noticeJson) implements ToProxy {
    }

    /** The player and the opponent beside them both asked for another round; the proxy seats the two together. */
    record Rematch(UUID opponent) implements ToProxy {
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

        public record Entry(String name, boolean online, String server, boolean watching, long days) {
            public Entry(String name, boolean online, String server, boolean watching) {
                this(name, online, server, watching, -1);
            }
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

    /**
     * Sent once, when a player's first join of the network reaches a lobby, because the backends' worlds forget
     * every player on restart. The proxy keeps the rules, so they travel along as a JSON component.
     */
    record Welcome(String rulesJson) implements ToBackend {
    }

    /** Someone joined the network for the first time; greetings written on this server count for them for {@code seconds}. */
    record Newcomer(UUID player, String name, int seconds) implements ToBackend {
    }
}
