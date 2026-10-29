package me.blockcraft.api.pack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DisplayFontTest {

    @Test
    public void withoutThePackTheLetteringIsBoldTextInTheDefaultFont() {
        var title = MiniMessage.miniMessage().deserialize("<green><display>Gewonnen!</display><gray> in Duell", DisplayFont.TAG);
        assertEquals(DisplayFont.KEY, styles(title).get("Gewonnen!").font());

        var plain = styles(DisplayFont.plain(title));
        assertNull(plain.get("Gewonnen!").font());
        assertEquals(TextDecoration.State.TRUE, plain.get("Gewonnen!").decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.NOT_SET, plain.get(" in Duell").decoration(TextDecoration.BOLD));
    }

    /** Each text with the style it is drawn in, inherited from its parents. */
    private static Map<String, Style> styles(Component component) {
        var styles = new HashMap<String, Style>();
        collect(component, Style.empty(), styles);
        return styles;
    }

    private static void collect(Component component, Style inherited, Map<String, Style> styles) {
        var style = inherited.merge(component.style());
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            styles.put(text.content(), style);
        }
        component.children().forEach(child -> collect(child, style, styles));
    }
}
