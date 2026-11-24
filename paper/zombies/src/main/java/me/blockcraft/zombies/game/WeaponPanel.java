package me.blockcraft.zombies.game;

import me.blockcraft.api.pack.HudPanel;
import me.blockcraft.api.pack.HudPanel.Corner;
import me.blockcraft.api.pack.HudPanel.Glyph;
import me.blockcraft.api.pack.HudPanel.Tint;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The held gun in the screen's bottom-right corner, in the pixels of the pack's {@code blockcraft:zombies/weapon} font:
 * the gun's side on dark glass, the rounds in its clip over its reserve, and a bar that shows the clip, or the reload
 * while one runs. The panel's origin is its top-right corner, so every glyph lies left of it and below it.
 */
final class WeaponPanel {

    static final Key FONT = Key.key("blockcraft", "zombies/weapon");
    static final int WIDTH = 64;
    static final int HEIGHT = 30;
    /** The panel stands on the corner's margin, below the top the shader gives a full column of Sternenfront's tiles. */
    private static final int TOP = HudPanel.BOTTOM_RIGHT_HEIGHT - HEIGHT;
    private static final int PANEL_ADVANCE = WIDTH + 1;
    private static final int INSET = 4;
    private static final int GUN_TOP = 5;
    private static final int GUN_ADVANCE = 33;
    private static final int CLIP_TOP = 3;
    private static final int DIGIT_ADVANCE = 9;
    /** A digit's dark rim overlaps the next one's. */
    private static final int DIGIT_PITCH = 7;
    private static final int DIGIT_WIDTH = 8;
    private static final int RESERVE_TOP = 15;
    private static final int COUNT_ADVANCE = 6;
    private static final int COUNT_PITCH = 4;
    private static final int COUNT_WIDTH = 5;
    /** The bar's cell, whose three drawn rows lie at its foot. */
    private static final int BAR_TOP = 20;
    private static final int BAR_ADVANCE = 57;
    private static final int BAR_LEVELS = 28;
    /** The clip turns the bar red once it holds no more than this share of the capacity. */
    private static final double LOW_CLIP = 0.25;
    private static final int MAX_ROUNDS = 999;
    static final char BACKING = '';
    static final char FRAME = '';
    static final char FIRST_GUN = '';
    static final char FIRST_DIGIT = '';
    static final char FIRST_COUNT = '';
    static final char SLASH = '';
    static final char TRACK = '';

    private WeaponPanel() {
    }

    static Component render(Weapon weapon, Ammo ammo, int tick) {
        return HudPanel.render(Corner.BOTTOM_RIGHT, FONT, layout(weapon, ammo, tick));
    }

    /** An upgraded gun is framed in gold; an empty clip or reserve turns red. */
    static List<Glyph> layout(Weapon weapon, Ammo ammo, int tick) {
        var glyphs = new ArrayList<Glyph>();
        glyphs.add(glyph(BACKING, 0, 0, Tint.WHITE, PANEL_ADVANCE));
        glyphs.add(glyph(FRAME, 0, 0, ammo.upgraded() ? Tint.GOLD : Tint.DIMMED, PANEL_ADVANCE));
        glyphs.add(glyph((char) (FIRST_GUN + weapon.ordinal()), INSET, GUN_TOP, Tint.WHITE, GUN_ADVANCE));
        var clip = String.valueOf(Math.min(ammo.clip(), MAX_ROUNDS));
        int clipLeft = WIDTH - INSET - DIGIT_WIDTH - (clip.length() - 1) * DIGIT_PITCH;
        for (int index = 0; index < clip.length(); index++) {
            glyphs.add(glyph((char) (FIRST_DIGIT + clip.charAt(index) - '0'), clipLeft + index * DIGIT_PITCH, CLIP_TOP,
                    ammo.clip() == 0 ? Tint.RED : Tint.WHITE, DIGIT_ADVANCE));
        }
        var reserve = String.valueOf(Math.min(ammo.reserve(), MAX_ROUNDS));
        int reserveLeft = WIDTH - INSET - COUNT_WIDTH - reserve.length() * COUNT_PITCH;
        var reserveTint = ammo.reserve() == 0 ? Tint.RED : Tint.DIMMED;
        glyphs.add(glyph(SLASH, reserveLeft, RESERVE_TOP, reserveTint, COUNT_ADVANCE));
        for (int index = 0; index < reserve.length(); index++) {
            glyphs.add(glyph((char) (FIRST_COUNT + reserve.charAt(index) - '0'), reserveLeft + (index + 1) * COUNT_PITCH, RESERVE_TOP,
                    reserveTint, COUNT_ADVANCE));
        }
        glyphs.add(glyph(TRACK, INSET, BAR_TOP, Tint.WHITE, BAR_ADVANCE));
        int level = ammo.reloading() ? (int) Math.round(ammo.reloaded(tick) * BAR_LEVELS)
                : ammo.clip() > 0 ? Math.max(1, (int) Math.round((double) ammo.clip() / ammo.capacity() * BAR_LEVELS)) : 0;
        if (level > 0) {
            var tint = ammo.reloading() ? Tint.GOLD : ammo.clip() <= LOW_CLIP * ammo.capacity() ? Tint.RED : Tint.WHITE;
            glyphs.add(glyph((char) (TRACK + Math.min(level, BAR_LEVELS)), INSET, BAR_TOP, tint, BAR_ADVANCE));
        }
        return glyphs;
    }

    /** {@code glyph} at {@code x} pixels from the panel's left and {@code y} below its top. */
    private static Glyph glyph(char glyph, int x, int y, Tint tint, int advance) {
        return new Glyph(String.valueOf(glyph), x - WIDTH, TOP + y, tint, false, advance);
    }
}
