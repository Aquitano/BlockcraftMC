package me.blockcraft.sternenfront.game;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Arrays;
import java.util.Locale;

/**
 * The symbol a stat wears beside its value, one per stat, on the deploy screen and in the cards' tooltips. Each is a
 * picture of the deploy screen's icon sheet, and a tooltip sets it from the pack's {@code blockcraft:deploy/stats} font.
 */
public enum StatIcon {
    HEALTH('', 0xFF5B4B), DAMAGE('', 0xFFB830), SPEED('', 0x5BEA6B), RANGE('', 0x5BC8FF);

    private static final Key FONT = Key.key("blockcraft", "deploy/stats");

    /** {@code <icon:damage>} and its kin in a message: the stat's symbol, for a tooltip's line. */
    public static final TagResolver TAG = TagResolver.resolver("icon", (arguments, context) -> {
        String name = arguments.popOr("icon needs a stat").lowerValue();
        return Tag.selfClosingInserting(Arrays.stream(values()).filter(icon -> icon.name().toLowerCase(Locale.ROOT).equals(name))
                .findFirst().orElseThrow(() -> context.newException("No stat icon " + name, arguments)).text());
    });

    private final char glyph;
    private final int colour;

    StatIcon(char glyph, int colour) {
        this.glyph = glyph;
        this.colour = colour;
    }

    public char glyph() {
        return glyph;
    }

    public int colour() {
        return colour;
    }

    /** The symbol in a line of a tooltip, in its own colour. */
    public Component text() {
        return Component.text(glyph, TextColor.color(colour)).font(FONT);
    }
}
