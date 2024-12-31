package de.Blockcraft.BungeeSystem.Util;

import com.google.common.base.Charsets;

import java.util.UUID;

public class PlayerUtil {
    public static String getUUID(String playername) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playername).getBytes(Charsets.UTF_8)).toString();
    }
}

