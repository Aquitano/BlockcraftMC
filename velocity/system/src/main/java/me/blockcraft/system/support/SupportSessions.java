package me.blockcraft.system.support;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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

    public record Request(UUID requester, Instant openedAt, Optional<UUID> supporter) {

        public State state() {
            return supporter.isPresent() ? State.ASSIGNED : State.WAITING;
        }
    }

    private final Map<UUID, Request> requests = new ConcurrentHashMap<>();

    public State state(UUID requester) {
        var request = requests.get(requester);
        return request == null ? State.NONE : request.state();
    }

    public Optional<UUID> supporter(UUID requester) {
        return Optional.ofNullable(requests.get(requester)).flatMap(Request::supporter);
    }

    /** Waiting requests first, oldest first within each state. */
    public List<Request> open() {
        return requests.values().stream().sorted(Comparator.comparing(Request::state)
                .thenComparing(Request::openedAt)).toList();
    }

    public long waitingCount() {
        return requests.values().stream().filter(request -> request.state() == State.WAITING).count();
    }

    public boolean open(UUID requester) {
        return requests.putIfAbsent(requester, new Request(requester, Instant.now(), Optional.empty())) == null;
    }

    public boolean assign(UUID requester, UUID supporter) {
        var request = requests.get(requester);
        return request != null && request.state() == State.WAITING
                && requests.replace(requester, request, new Request(requester, request.openedAt(), Optional.of(supporter)));
    }

    public void close(UUID requester) {
        requests.remove(requester);
    }

    public boolean close(UUID requester, UUID supporter) {
        var request = requests.get(requester);
        return request != null && request.supporter().filter(supporter::equals).isPresent()
                && requests.remove(requester, request);
    }

    /** A leaving requester drops the request; a leaving supporter hands their requests back to the queue. */
    public void onDisconnect(UUID player) {
        requests.remove(player);
        requests.replaceAll((requester, request) -> request.supporter().filter(player::equals).isPresent()
                ? new Request(requester, request.openedAt(), Optional.empty())
                : request);
    }
}
