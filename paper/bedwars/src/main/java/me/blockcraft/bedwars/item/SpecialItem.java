package me.blockcraft.bedwars.item;

import me.blockcraft.api.item.HotbarItem;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The shop's items that do something when used, recognised by their tag, not by their material. */
public enum SpecialItem implements HotbarItem {
    TELEPORTER,
    PARACHUTE,
    RESCUE_PLATFORM,
    MOBILE_SHOP;

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<SpecialItem> byKey(String key) {
        return Arrays.stream(values()).filter(item -> item.key().equals(key)).findFirst();
    }
}
