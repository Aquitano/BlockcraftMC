package me.blockcraft.waker;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class RequestsTest {

    private final List<String> calls = new ArrayList<>();
    private final Requests requests = new Requests("secret", Set.of("bedwars", "ttt"), new Containers() {
        @Override
        public Optional<String> state(String service) {
            return service.equals("bedwars") ? Optional.of("running") : Optional.empty();
        }

        @Override
        public Outcome start(String service) {
            calls.add("start " + service);
            return Outcome.DONE;
        }

        @Override
        public Outcome stop(String service) {
            calls.add("stop " + service);
            return Outcome.ALREADY;
        }
    });

    @Test
    public void startsAndStopsOnlyListedServicesForTheTokensHolder() {
        assertEquals(204, requests.handle("POST", "/services/bedwars/start", Optional.of("Bearer secret")).status());
        assertEquals(204, requests.handle("POST", "/services/ttt/stop", Optional.of("Bearer secret")).status());
        assertEquals(List.of("start bedwars", "stop ttt"), calls);
    }

    @Test
    public void refusesWithoutTheTokenAndOutsideTheList() {
        assertEquals(401, requests.handle("POST", "/services/bedwars/start", Optional.empty()).status());
        assertEquals(401, requests.handle("POST", "/services/bedwars/start", Optional.of("Bearer wrong")).status());
        assertEquals(404, requests.handle("POST", "/services/mariadb/stop", Optional.of("Bearer secret")).status());
        assertEquals(404, requests.handle("POST", "/services/../start", Optional.of("Bearer secret")).status());
        assertEquals(405, requests.handle("GET", "/services/bedwars/start", Optional.of("Bearer secret")).status());
        assertEquals(List.of(), calls);
    }

    @Test
    public void listsTheStateOfEveryListedService() {
        var reply = requests.handle("GET", "/services", Optional.of("Bearer secret"));

        assertEquals(200, reply.status());
        assertEquals("{\"bedwars\":\"running\",\"ttt\":\"missing\"}", reply.body());
    }
}
