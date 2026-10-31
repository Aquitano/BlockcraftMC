package me.blockcraft.common.network;

import me.blockcraft.common.config.YamlFile;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code network} section of a config.yml, the addresses every message names as {@code <ip>}, {@code <website>}
 * and so on. {@code BLOCKCRAFT_NETWORK_<KEY>} environment variables override its entries, so one deployment setting
 * reaches every server's own copy of the file.
 */
public final class NetworkAddresses {

    private NetworkAddresses() {
    }

    public static Map<String, Object> fromYaml(Map<String, Object> root) {
        var addresses = new LinkedHashMap<>(YamlFile.section(root, "network"));
        addresses.replaceAll((key, value) -> {
            String override = System.getenv("BLOCKCRAFT_NETWORK_" + key.toUpperCase(Locale.ROOT));
            return override == null || override.isBlank() ? value : override;
        });
        return addresses;
    }
}
