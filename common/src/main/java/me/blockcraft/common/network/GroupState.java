package me.blockcraft.common.network;

/**
 * Where an on-demand game group stands, as the proxy decides it and {@code bc_server_groups} records it. Groups the
 * proxy does not manage have no state and are simply as alive as their servers.
 */
public enum GroupState {
    /** Its server runs; players join as usual. */
    AWAKE,
    /** Its server was asked to start; players who want in wait for it. */
    STARTING,
    /** Its idle server is being stopped; nobody new is seated there. */
    DRAINING,
    /** No server runs; the next player who wants in starts one. */
    ASLEEP
}
