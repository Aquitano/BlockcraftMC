package me.blockcraft.core.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class MentionsTest {

    private static Optional<String> marked(Component message) {
        return Mentions.highlight(message, "Thomas", written -> Component.text("[" + written + "]"))
                .map(PlainTextComponentSerializer.plainText()::serialize);
    }

    @Test
    public void aWholeWordInAnyCaseWithOrWithoutAtIsAMention() {
        assertEquals(Optional.of("hi [thomas], [@THOMAS]!"), marked(Component.text("hi thomas, @THOMAS!")));
    }

    @Test
    public void aNameInsideAnotherWordIsNone() {
        assertEquals(Optional.empty(), marked(Component.text("Thomas_2 und xthomas, mail@thomas")));
    }

    @Test
    public void aMentionIsFoundInPremiumColouredText() {
        var coloured = LegacyComponentSerializer.legacyAmpersand().deserialize("&cHallo &lThomas");
        var highlighted = Mentions.highlight(coloured, "thomas", written -> Component.text(written, NamedTextColor.GOLD));
        assertEquals(Optional.of("Hallo Thomas"), highlighted.map(PlainTextComponentSerializer.plainText()::serialize));
    }
}
