package me.blockcraft.api.pack;

import java.util.Locale;
import java.util.stream.IntStream;

public enum MenuTile {
    QUICKSG(MenuIcon.QUICKSG),
    EMERALDRUSH(MenuIcon.EMERALDRUSH),
    DUELL(MenuIcon.DUELL),
    BEDWARS(MenuIcon.BEDWARS),
    SKYWARS(MenuIcon.SKYWARS),
    TTT(MenuIcon.TTT),
    MLGRUSH(MenuIcon.MLGRUSH),
    CORES(MenuIcon.CORES),
    ENDERGAMES(MenuIcon.ENDERGAMES),
    ZOMBIES(MenuIcon.ZOMBIES),
    HEADS(MenuIcon.TOOL_HEADS),
    HATS(MenuIcon.TOOL_HATS),
    SHIRTS(MenuIcon.TOOL_SHIRTS),
    KILL_EFFECTS(MenuIcon.KILL_EFFECTS),
    WIN_EFFECTS(MenuIcon.WIN_EFFECTS),
    PREMIUM(MenuIcon.PREMIUM),
    LEVEL(MenuIcon.ACHIEVEMENTS),
    COINS(MenuIcon.TOOL_SHOP),
    RECAP(MenuIcon.RECAP),
    ACHIEVEMENTS(MenuIcon.ACHIEVEMENTS),
    GADGETS(MenuIcon.GADGETS),
    TRAILS(MenuIcon.TRAILS),
    TAGS(MenuIcon.CHAT_TAGS),
    LOBBIES(MenuIcon.LOBBIES);

    public enum Shape {
        WIDE(4, 1, 0xE400), LARGE(3, 2, 0xE600);

        private final int columns;
        private final int rows;
        private final int glyphBase;

        Shape(int columns, int rows, int glyphBase) {
            this.columns = columns;
            this.rows = rows;
            this.glyphBase = glyphBase;
        }

        public int columns() {
            return columns;
        }

        public int rows() {
            return rows;
        }

        public int width() {
            return columns * 18 - 2;
        }

        public int height() {
            return rows * 18 - 2;
        }
    }

    public record Placement(int slot, MenuTile tile, Shape shape) {
        public Placement {
            if (slot < 0 || slot % 9 + shape.columns() > 9 || slot / 9 + shape.rows() > 6) {
                throw new IllegalArgumentException("Tile outside the chest: " + slot + ", " + shape);
            }
        }

        public boolean covers(int candidate) {
            return candidate >= 0 && candidate / 9 >= slot / 9 && candidate / 9 < slot / 9 + shape.rows()
                    && candidate % 9 >= slot % 9 && candidate % 9 < slot % 9 + shape.columns();
        }

        public IntStream slots() {
            return IntStream.range(0, shape.rows()).flatMap(row ->
                    IntStream.range(0, shape.columns()).map(column -> slot + row * 9 + column));
        }

        public int iconSlot() {
            return slot + shape.columns() / 2;
        }

        public char glyph() {
            return (char) (shape.glyphBase + tile.ordinal() * 8 + slot / 9);
        }
    }

    private final MenuIcon icon;

    MenuTile(MenuIcon icon) {
        this.icon = icon;
    }

    public MenuIcon icon() {
        return icon;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
