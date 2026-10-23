package me.blockcraft.api.pack;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The HUD icons, written as {@code <icon:name>} in a message: a glyph of the pack's font for a client that wears
 * it, the symbol under {@code icons.<name>} in BlockcraftAPI's messages for everyone else.
 */
public enum Icon {

    PROFILE('\uE200'),
    COINS('\uE201'),
    LEVEL('\uE202'),
    CHAT('\uE203'),
    SERVER('\uE204'),
    PLAYERS('\uE205'),
    MAP('\uE206'),
    KIT('\uE207'),
    SPECTATOR('\uE208'),
    TIME('\uE209'),
    EMERALD('\uE20A'),
    WINNER('\uE20B'),
    MODE('\uE20C'),
    OPPONENT('\uE20D'),
    KILLS('\uE20E');

    private final char glyph;

    Icon(char glyph) {
        this.glyph = glyph;
    }

    public char glyph() {
        return glyph;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Icon> byKey(String key) {
        return Arrays.stream(values()).filter(icon -> icon.key().equals(key)).findFirst();
    }

    public static TagResolver resolver(boolean packApplied, Messages fallbacks) {
        return TagResolver.resolver("icon", (arguments, context) -> {
            String key = arguments.popOr("icon name").value();
            var icon = byKey(key).orElseThrow(() -> context.newException("Unknown icon " + key, arguments));
            return Tag.selfClosingInserting(packApplied ? UiFont.glyph(icon.glyph()) : fallbacks.get("icons." + icon.key()));
        });
    }
}
