package me.blockcraft.cores.game;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.Locale;

/** The two teams of Cores: red starts on the north end of the map, blue on the south end, mirrored. */
public enum Side {
    RED(NamedTextColor.RED, Color.RED, Material.RED_WOOL, Material.RED_CONCRETE, Material.RED_STAINED_GLASS, -1),
    BLUE(NamedTextColor.BLUE, Color.BLUE, Material.BLUE_WOOL, Material.BLUE_CONCRETE, Material.BLUE_STAINED_GLASS, 1);

    private final NamedTextColor text;
    private final Color dye;
    private final Material wool;
    private final Material concrete;
    private final Material glass;
    private final int north;

    Side(NamedTextColor text, Color dye, Material wool, Material concrete, Material glass, int north) {
        this.text = text;
        this.dye = dye;
        this.wool = wool;
        this.concrete = concrete;
        this.glass = glass;
        this.north = north;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public NamedTextColor text() {
        return text;
    }

    public Color dye() {
        return dye;
    }

    public Material wool() {
        return wool;
    }

    public Material concrete() {
        return concrete;
    }

    public Material glass() {
        return glass;
    }

    /** -1 for the team whose base lies at negative z, 1 for the other; the map mirrors one into the other with it. */
    public int direction() {
        return north;
    }

    public Side enemy() {
        return this == RED ? BLUE : RED;
    }

    /** "Rot" or "Blau", in the team's colour. */
    public Component displayName(Messages messages) {
        return messages.get("teams." + key()).colorIfAbsent(text);
    }
}
