package me.blockcraft.common.network;

/**
 * Plugin message channel between the backends and the proxy. Every payload starts with a subchannel name and
 * concerns the player the message travelled with: the proxy moves them ({@link #PLAY}, {@link #HUB}), runs a
 * command as them ({@link #COMMAND}), learns what they look like to others ({@link #NICK}, {@link #VANISH}) or
 * what they let others do to them ({@link #SETTING}), or delivers a notice on their behalf ({@link #STAFF_NOTICE}).
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

    private ProxyChannel() {
    }
}
