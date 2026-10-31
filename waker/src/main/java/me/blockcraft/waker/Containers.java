package me.blockcraft.waker;

import java.util.Optional;

/** The compose services the waker may touch, as Docker sees them. */
interface Containers {

    /** What became of a start or stop. */
    enum Outcome { DONE, ALREADY, MISSING, FAILED }

    /** Docker's state of the service's container ({@code running}, {@code exited}, ...), empty if it has none. */
    Optional<String> state(String service);

    Outcome start(String service);

    Outcome stop(String service);
}
