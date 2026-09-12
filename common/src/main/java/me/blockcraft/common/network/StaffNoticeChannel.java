package me.blockcraft.common.network;

/**
 * Plugin message channel a backend uses to reach staff on the whole network: the payload is the permission the
 * recipients need followed by the notice as a JSON component, and the proxy delivers it.
 */
public final class StaffNoticeChannel {

    public static final String NAME = "blockcraft:staff";

    private StaffNoticeChannel() {
    }
}
