package me.blockcraft.api.pack;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * The pack's title lettering, {@code <display>} in any message: the {@code blockcraft:display} font, outlined capitals
 * as wide as a title allows only for a few words. A client without the pack has no such font, so
 * {@link me.blockcraft.api.notification.Notifications} hands it {@link #plain} titles; nothing else may carry the tag.
 */
public final class DisplayFont {

    public static final Key KEY = Key.key("blockcraft", "display");
    public static final TagResolver TAG = TagResolver.resolver("display", Tag.styling(style -> style.font(KEY)));

    private DisplayFont() {
    }

    /** The lettering as bold text in the default font. */
    public static Component plain(Component component) {
        var style = component.style();
        if (KEY.equals(style.font())) {
            style = style.font(null).decoration(TextDecoration.BOLD, true);
        }
        return component.style(style).children(component.children().stream().map(DisplayFont::plain).toList());
    }
}
