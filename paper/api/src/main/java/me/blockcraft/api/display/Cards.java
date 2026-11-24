package me.blockcraft.api.display;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

/** Floating text on a dark card. */
public final class Cards {

    /** The pack's tooltip card, so the cards match the menus. */
    private static final Color CARD = Color.fromARGB(0xC8, 0x14, 0x17, 0x20);
    private static final Display.Brightness FULL = new Display.Brightness(15, 15);

    private Cards() {
    }

    /** One line of floating text; the caller decides whether it survives a restart. */
    public static TextDisplay line(Location location, Component text) {
        return location.getWorld().spawn(location, TextDisplay.class, display -> {
            display.text(text);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBillboard(Display.Billboard.CENTER);
            display.setDefaultBackground(false);
            display.setBackgroundColor(CARD);
            display.setShadowed(true);
            display.setBrightness(FULL);
        });
    }
}
