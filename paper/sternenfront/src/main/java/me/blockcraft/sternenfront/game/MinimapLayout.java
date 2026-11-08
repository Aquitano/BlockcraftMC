package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.game.HudPanel.Corner;
import me.blockcraft.sternenfront.game.HudPanel.Glyph;
import me.blockcraft.sternenfront.game.HudPanel.Tint;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Where the top-left {@link HudPanel} draws what, in the pixels of the pack's {@code blockcraft:minimap} font: a disc 63
 * pixels across, north up around the player, and the squad under it, headed and named.
 */
final class MinimapLayout {

    static final Key FONT = Key.key("blockcraft", "minimap");
    static final int MAP = 63;
    static final int MIDDLE = 31;
    /** How far from the middle a dot may stand: inside the rim, and low enough that its cell starts on the panel. */
    static final int REACH = 27;
    /** How far a post's badge may stand, which keeps it whole inside the rim. */
    static final int BADGE_REACH = 25;
    private static final int STRIP = 16;
    private static final int STRIPS = 4;
    private static final int STRIP_ADVANCE = 64;
    private static final int MARK = 9;
    private static final int MARK_ADVANCE = 10;
    private static final int BADGE_HALF = 5;
    private static final int BADGE_ADVANCE = 11;
    private static final int CLASS_ADVANCE = 7;
    static final int HEADINGS = 16;
    private static final int LIST_TOP = MAP + 3;
    private static final int ROW = 8;
    static final int ROWS = 6;
    private static final int NAME_X = CLASS_ADVANCE;
    private static final int NAME_WIDTH = MAP - NAME_X;

    static final char DISC = '\uE000';
    static final char BADGE = '\uE010';
    /** The arrow's sixteen headings, then each {@link Mark}. */
    static final char ARROW = '\uE020';
    static final char TARGET = '\uE034';
    private static final int TARGET_HALF = 6;
    private static final int TARGET_ADVANCE = 14;
    static final char CLASS_MARK = '\uE040';

    /** What a dot on the map stands for, in the order the pack draws them after the arrow. */
    enum Mark {
        SQUADMATE, ALLY, ENEMY, HEADQUARTERS;

        char glyph() {
            return (char) (ARROW + HEADINGS + ordinal());
        }
    }

    /** What a squad member's mark shows, in the order the pack draws them. */
    enum Role {
        SOLDIER, HEAVY, SCOUT, MEDIC, HERO;

        static Role of(CombatClass combatClass) {
            return switch (combatClass) {
                case SOLDIER -> SOLDIER;
                case HEAVY -> HEAVY;
                case SCOUT -> SCOUT;
                case MEDIC -> MEDIC;
            };
        }
    }

    record Pixel(int x, int y) { }

    /** The map's scale: {@code radius} blocks from ({@code x}, {@code z}) reach the dots' rim. */
    record View(double x, double z, double radius) {

        /** Where a point stands on the map, or nothing beyond the radius. */
        Optional<Pixel> inside(double atX, double atZ) {
            return Math.hypot(atX - x, atZ - z) > radius ? Optional.empty() : Optional.of(clamped(atX, atZ, REACH));
        }

        /** Where a point stands on the map, pulled in along its bearing to {@code reach} pixels when further out. */
        Pixel clamped(double atX, double atZ, int reach) {
            double scale = REACH / radius;
            double dx = (atX - x) * scale;
            double dz = (atZ - z) * scale;
            double out = Math.hypot(dx, dz);
            double pull = out > reach ? reach / out : 1;
            return new Pixel(MIDDLE + (int) Math.round(dx * pull), MIDDLE + (int) Math.round(dz * pull));
        }
    }

    private MinimapLayout() {
    }

    static List<Glyph> disc() {
        return IntStream.range(0, STRIPS)
                .mapToObj(strip -> new Glyph(String.valueOf((char) (DISC + strip)), 0, strip * STRIP, Tint.WHITE, false, STRIP_ADVANCE))
                .toList();
    }

    static Glyph badge(PostBadge badge, Pixel at, Tint tint) {
        return new Glyph(String.valueOf(badge.glyph(BADGE)), at.x() - BADGE_HALF, at.y() - BADGE_HALF, tint, true, BADGE_ADVANCE);
    }

    /** Corners round the badge of the post the player should make for. */
    static Glyph target(Pixel badge) {
        return new Glyph(String.valueOf(TARGET), badge.x() - TARGET_HALF, badge.y() - TARGET_HALF, Tint.ACCENT, true, TARGET_ADVANCE);
    }

    static Glyph mark(Mark mark, Pixel at, Tint tint) {
        return centred(mark.glyph(), at, tint);
    }

    /** The player's arrow in the middle, turned to the nearest of sixteen headings clockwise from north. */
    static Glyph arrow(float yaw) {
        return centred((char) (ARROW + heading(yaw)), new Pixel(MIDDLE, MIDDLE), Tint.WHITE);
    }

    /** Minecraft's yaw is 0 facing south and grows clockwise, so north is 180. */
    static int heading(float yaw) {
        return Math.floorMod(Math.round((yaw + 180) / (360f / HEADINGS)), HEADINGS);
    }

    private static Glyph centred(char glyph, Pixel at, Tint tint) {
        return new Glyph(String.valueOf(glyph), at.x() - MARK / 2, at.y() - MARK / 2, tint, true, MARK_ADVANCE);
    }

    /** A line of the squad list under the map: the member's class mark, then their name. */
    static List<Glyph> member(int row, Role role, Tint mark, String name, boolean ready) {
        int y = LIST_TOP + row * ROW;
        var label = label(name);
        var tint = ready ? Tint.WHITE : Tint.DIMMED;
        return List.of(new Glyph(String.valueOf((char) (CLASS_MARK + role.ordinal())), 0, y, ready ? mark : Tint.DIMMED, true, CLASS_ADVANCE),
                HudPanel.label(label, NAME_X, y, tint));
    }

    /** The name in the menu tiles' capitals, cut to the panel's width, with a question mark for any letter they lack. */
    static String label(String name) {
        var letters = HudPanel.capitals(name);
        if (HudPanel.width(letters) <= NAME_WIDTH) {
            return letters;
        }
        int end = letters.length() - 1;
        while (end > 0 && HudPanel.width(letters.substring(0, end) + "…") > NAME_WIDTH) {
            end--;
        }
        return letters.substring(0, end) + "…";
    }

    /** A boss bar line that starts in the screen's middle whatever it shows, which the shader moves into the corner. */
    static Component render(List<Glyph> glyphs) {
        return HudPanel.render(Corner.TOP_LEFT, FONT, glyphs);
    }

    /** {@code heading} greyed on the first row, then the members in the order the panel stacks them, the viewer's own first. */
    static List<Glyph> list(String heading, List<Member> members) {
        var label = label(heading);
        var glyphs = new ArrayList<Glyph>();
        glyphs.add(HudPanel.label(label, 0, LIST_TOP, Tint.DIMMED));
        for (int row = 0; row < Math.min(ROWS, members.size()); row++) {
            var member = members.get(row);
            glyphs.addAll(member(row + 1, member.role(), member.mark(), member.name(), member.ready()));
        }
        return glyphs;
    }

    record Member(String name, Role role, Tint mark, boolean ready) { }
}
