package me.blockcraft.common.network;

/**
 * Plugin message channel between the backends and the proxy. Every payload starts with a subchannel name and
 * concerns the player the message travelled with: the proxy moves them ({@link #PLAY}, {@link #HUB}), runs a
 * command as them ({@link #COMMAND}), learns what they look like to others ({@link #NICK}, {@link #VANISH}) or
 * what they let others do to them ({@link #SETTING}), or delivers a notice on their behalf ({@link #STAFF_NOTICE}).
 * The proxy speaks first only to answer ({@link #FRIENDS}), to say whether the client wears the network's
 * resource pack ({@link #PACK}) and to name the friend the player came to watch ({@link #SPECTATE}).
 */
public final class ProxyChannel {

    public static final String NAME = "blockcraft:main";

    /** group */
    public static final String PLAY = "Play";

    /** no payload */
    public static final String HUB = "Hub";

    /** command line without the leading slash, run on the proxy with the player's own permissions */
    public static final String COMMAND = "Command";

    /** nick, or an empty string once the nick is off */
    public static final String NICK = "Nick";

    /** boolean */
    public static final String VANISH = "Vanish";

    /** permission, notice as a JSON component */
    public static final String STAFF_NOTICE = "StaffNotice";

    /** setting key and its new value, so the proxy's own view of the player's choices stays current */
    public static final String SETTING = "Setting";

    /** Correlated friends request or reply, encoded by FriendsMessage. */
    public static final String FRIENDS = "Friends";

    /** Applied boolean followed by PackStatus.name(). */
    public static final String PACK = "Pack";

    public static final String PACK_REQUEST = "PackRequest";

    /**
     * The UUID of the friend the player came to watch, as a string. Sent by the proxy when a player arrives on a
     * server whose round is already running, because only the proxy knows that the jump was meant as a watch.
     */
    public static final String SPECTATE = "Spectate";

    private ProxyChannel() {
    }
}
