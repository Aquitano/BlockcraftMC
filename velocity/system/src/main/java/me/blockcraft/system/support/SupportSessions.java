package me.blockcraft.system.support;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Open support requests, each either waiting or assigned to one supporter. In memory, like before. */
public final class SupportSessions {

    public enum State {
        NONE,
        WAITING,
        ASSIGNED
    }

    private final Map<UUID, Optional<UUID>> supporterByRequester = new ConcurrentHashMap<>();

    public State state(UUID requester) {
        var supporter = supporterByRequester.get(requester);
        if (supporter == null) {
            return State.NONE;
        }
        return supporter.isPresent() ? State.ASSIGNED : State.WAITING;
    }

    public Optional<UUID> supporter(UUID requester) {
        return supporterByRequester.getOrDefault(requester, Optional.empty());
    }

    public void open(UUID requester) {
        supporterByRequester.put(requester, Optional.empty());
    }

    public void assign(UUID requester, UUID supporter) {
        supporterByRequester.put(requester, Optional.of(supporter));
    }

    public void close(UUID requester) {
        supporterByRequester.remove(requester);
    }

    /** A leaving requester drops the request; a leaving supporter hands their requests back to the queue. */
    public void onDisconnect(UUID player) {
        supporterByRequester.remove(player);
        supporterByRequester.replaceAll((requester, supporter) ->
                supporter.filter(player::equals).isPresent() ? Optional.empty() : supporter);
    }
}
