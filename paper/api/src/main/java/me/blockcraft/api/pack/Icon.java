package me.blockcraft.api.pack;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The HUD icons, written as {@code <icon:name>} in a message: a glyph of the pack's font for a client that wears
 * it, the symbol under {@code icons.<name>} in BlockcraftAPI's messages for everyone else. Most glyphs are white and
 * take the colour of the text around them; a coloured one is drawn in its own colours.
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
    KILLS('\uE20E'),
    ERROR('\uE20F', true);

    private final char glyph;
    private final boolean coloured;

    Icon(char glyph) {
        this(glyph, false);
    }

    Icon(char glyph, boolean coloured) {
        this.glyph = glyph;
        this.coloured = coloured;
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
            if (!packApplied) {
                return Tag.selfClosingInserting(fallbacks.get("icons." + icon.key()));
            }
            var glyph = UiFont.glyph(icon.glyph());
            return Tag.selfClosingInserting(icon.coloured ? glyph.color(NamedTextColor.WHITE) : glyph);
        });
    }
}
