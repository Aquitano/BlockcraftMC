package me.blockcraft.common.network;

/**
 * Plugin message channel between the backends and the proxy. Every payload starts with a subchannel name; the
 * proxy answers {@link #PLAY} and {@link #HUB} itself and delivers {@link #STAFF_NOTICE} to every player holding the permission.
 */
public final class ProxyChannel {

    public static final String NAME = "blockcraft:main";

    /** group */
    public static final String PLAY = "Play";

    /** no payload */
    public static final String HUB = "Hub";

    /** permission, notice as a JSON component */
    public static final String STAFF_NOTICE = "StaffNotice";

    private ProxyChannel() {
    }
}
