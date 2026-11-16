package me.blockcraft.zombies.menu;

import me.blockcraft.api.pack.UiFont;
import me.blockcraft.common.message.Messages;
import me.blockcraft.zombies.game.Perk;
import me.blockcraft.zombies.game.Weapon;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * A station's screen as its chest title draws it with the pack, from {@code resourcepack/StationArt.java}: Zombies' own
 * panel for the layout, a header with the station's emblem, its name and the buyer's gold, and on each card the offer's
 * picture, its name from the top, a line on what it does from the bottom, and a band with its price and its state. The
 * menu's slots under a card answer for it.
 * <p>
 * Every part is one glyph run in the font of the line it stands on, moved to its place and back by the width the pack
 * gives it, so each part starts from the title's start again; StationScreenTest checks those widths and the layout
 * against the pack.
 */
final class StationScreen {

    /** The cards' top-left slots and the chest's rows, as StationArt draws the panel for each. */
    enum Layout {
        ONE(3, List.of(2)),
        TWO(3, List.of(0, 4)),
        THREE(6, List.of(2, 27, 31)),
        FOUR(6, List.of(0, 4, 27, 31)),
        SWAP(6, List.of(2, 27, 31));

        final int rows;
        final List<Integer> cards;

        Layout(int rows, List<Integer> cards) {
            this.rows = rows;
            this.cards = cards;
        }

        /** The layout for a station with {@code offers} cards; a station with more keeps the chest. */
        static Optional<Layout> forOffers(int offers) {
            return switch (offers) {
                case 1 -> Optional.of(ONE);
                case 2 -> Optional.of(TWO);
                case 3 -> Optional.of(THREE);
                case 4 -> Optional.of(FOUR);
                default -> Optional.empty();
            };
        }
    }

    static final int CARD_COLUMNS = 4;
    static final int CARD_ROWS = 3;
    static final int ART_TOP = 0;
    static final List<Integer> TEXT_TOPS = List.of(22, 29, 36);
    static final int BAND_TOP = 43;
    static final int EMBLEM_TOP = 3;
    static final int HEADER_TOP = 5;

    /** The guns in the order of {@link Weapon}, whose side views the HUD draws, then StationArt's pictures in its order. */
    enum Art {
        PISTOL, RIFLE, SHOTGUN, SNIPER, FLAMETHROWER,
        AMMO, EXTRA_HEALTH, QUICK_RELOAD, QUICK_REVIVE, FROZEN_BULLETS, ARMOR_IRON, ARMOR_DIAMOND, POWER, UPGRADE, MYSTERY, TEAM_REVIVE;

        static Art of(Weapon weapon) {
            return valueOf(weapon.name());
        }

        static Art of(Perk perk) {
            return valueOf(perk.name());
        }

        boolean gun() {
            return ordinal() < Weapon.values().length;
        }

        int width() {
            return gun() ? GUN_WIDTH : PICTURE_WIDTH;
        }
    }

    /** How a card's band reads: the band's colour and its words'. */
    enum Tone {
        BUY(0x3D6B1F, 0xC8F58A),
        EXPENSIVE(0x6B2A1F, 0xFF9A84),
        SPENT(0x2E3329, 0xA3AD98),
        POWER(0x1F3F6B, 0x9CCBFF),
        MAXED(0x6B5414, 0xFFD86A),
        REPLACE(0x7A3A12, 0xFFB27A);

        final int band;
        final int words;

        Tone(int band, int words) {
            this.band = band;
            this.words = words;
        }
    }

    /** One offer's card, its words already in the label's capitals; a price shows beside the state when there is one. */
    record Card(Art art, String name, String line, OptionalInt price, String state, Tone tone) { }

    /** A glyph run in {@code font} whose left edge stands {@code x} pixels into the chest screen. */
    record Part(String text, Key font, int x, int colour, int advance) { }

    static final Key PANEL = Key.key("blockcraft", "zombies/station/panel");
    static final char COIN = '';
    static final char DAMAGE = '';
    static final char CLIP = '';
    private static final int ART_GLYPHS = 0xE010;
    private static final int EMBLEM_GLYPHS = 0xE030;
    private static final char BAND = '';
    private static final int PANEL_GLYPHS = 0xE000;

    private static final int TITLE_X = 8;
    private static final int PANEL_WIDTH = 176;
    private static final int SLOT = 18;
    private static final int SLOT_LEFT = 8;
    private static final int SLOT_TOP = 18;
    private static final int GUN_WIDTH = 64;
    private static final int PICTURE_WIDTH = 20;
    private static final int EMBLEM_WIDTH = 10;
    private static final int LETTER_ADVANCE = UiFont.LABEL_ADVANCE;
    private static final int MARK_ADVANCE = 6;
    private static final int CARD_WIDTH = CARD_COLUMNS * SLOT - 2;
    /** The words keep three pixels from a card's sides, and its band one. */
    private static final int TEXT_WIDTH = CARD_WIDTH - 6;
    private static final int BAND_WIDTH = CARD_WIDTH - 2;
    private static final int BAND_PADDING = 3;
    private static final int NAME_LINES = 2;
    private static final int HEADER_LEFT = 6;
    private static final int HEADER_RIGHT = 168;

    /** A word, or the part of one up to and with a hyphen, and the spaces before it. */
    private static final Pattern PIECE = Pattern.compile("( *)([^ -]*-|[^ -]+)");

    private static final int HEADING = 0xA6D36B;
    private static final int NAME = 0xEDE6CC;
    private static final int LINE = 0x9DA88A;
    private static final int GOLD = 0xF2C230;
    private static final int PRICE = 0xFFE08A;
    private static final int WHITE = 0xFFFFFF;

    private StationScreen() {
    }

    static Component render(Layout layout, Art emblem, String heading, int gold, List<Card> cards) {
        var title = Component.text();
        for (var part : parts(layout, emblem, heading, gold, cards)) {
            title.append(UiFont.space(part.x() - TITLE_X))
                    .append(Component.text(part.text()).font(part.font()).color(TextColor.color(part.colour())))
                    .append(UiFont.space(TITLE_X - part.x() - part.advance()));
        }
        return title.build();
    }

    static List<Part> parts(Layout layout, Art emblem, String heading, int gold, List<Card> cards) {
        if (emblem.gun()) {
            throw new IllegalArgumentException("The header's emblem is a picture, not a gun: " + emblem);
        }
        var parts = new ArrayList<Part>();
        parts.add(new Part(String.valueOf((char) (PANEL_GLYPHS + layout.ordinal())), PANEL, 0, WHITE, PANEL_WIDTH + 1));
        parts.add(new Part(String.valueOf((char) (EMBLEM_GLYPHS + emblem.ordinal() - Weapon.values().length)), line(EMBLEM_TOP),
                HEADER_LEFT, WHITE, EMBLEM_WIDTH + 1));
        var balance = COIN + String.valueOf(gold);
        int balanceX = HEADER_RIGHT + 2 - width(balance);
        int headingX = HEADER_LEFT + EMBLEM_WIDTH + 3;
        parts.add(new Part(balance, line(HEADER_TOP), balanceX, GOLD, width(balance)));
        var shown = fit(heading, balanceX - LETTER_ADVANCE - headingX);
        parts.add(new Part(shown, line(HEADER_TOP), headingX, HEADING, width(shown)));
        for (int index = 0; index < Math.min(cards.size(), layout.cards.size()); index++) {
            card(parts, layout.cards.get(index), cards.get(index));
        }
        return parts;
    }

    private static void card(List<Part> parts, int slot, Card card) {
        int x = SLOT_LEFT + slot % 9 * SLOT;
        int top = SLOT_TOP + slot / 9 * SLOT;
        parts.add(new Part(String.valueOf((char) (ART_GLYPHS + card.art().ordinal())), line(top + ART_TOP),
                x + (CARD_WIDTH - card.art().width()) / 2, WHITE, card.art().width() + 1));
        var names = wrap(card.name(), NAME_LINES);
        var lines = wrap(card.line(), TEXT_TOPS.size() - names.size());
        for (int index = 0; index < names.size(); index++) {
            centred(parts, names.get(index), x, top + TEXT_TOPS.get(index), NAME);
        }
        for (int index = 0; index < lines.size(); index++) {
            centred(parts, lines.get(index), x, top + TEXT_TOPS.get(TEXT_TOPS.size() - lines.size() + index), LINE);
        }
        var band = line(top + BAND_TOP);
        parts.add(new Part(String.valueOf(BAND), band, x + 1, card.tone().band, BAND_WIDTH + 1));
        if (card.price().isEmpty()) {
            centred(parts, card.state(), x, top + BAND_TOP, card.tone().words);
            return;
        }
        var price = COIN + String.valueOf(card.price().getAsInt());
        parts.add(new Part(price, band, x + 1 + BAND_PADDING, PRICE, width(price)));
        var state = fit(card.state(), BAND_WIDTH - 2 * BAND_PADDING - width(price));
        parts.add(new Part(state, band, x + 1 + BAND_WIDTH - BAND_PADDING + 1 - width(state), card.tone().words, width(state)));
    }

    private static void centred(List<Part> parts, String text, int cardX, int top, int colour) {
        if (!text.isEmpty()) {
            parts.add(new Part(text, line(top), cardX + (CARD_WIDTH - ink(text)) / 2, colour, width(text)));
        }
    }

    static Key line(int top) {
        return Key.key("blockcraft", "zombies/station/line_" + top);
    }

    /** {@code text} in the label's capitals, which the line fonts draw. */
    static String capitals(Component text) {
        return UiFont.capitals(PlainTextComponentSerializer.plainText().serialize(text), UiFont.LABEL_LETTERS).strip();
    }

    /** A gun's name on a card, which may break a long name where the gun's own does not. */
    static String name(Messages messages, Weapon weapon) {
        var card = "weapon.card." + weapon.key();
        return capitals(messages.get(messages.has(card) ? card : "weapon." + weapon.key()));
    }

    /** A gun's damage per shot and its clip, each after its mark. */
    static String stats(double damage, int clip) {
        return DAMAGE + String.valueOf(Math.round(damage)) + " " + CLIP + clip;
    }

    /**
     * {@code text} in lines no wider than a card's words, broken between words or after a hyphen, a word too long for a
     * line broken with a hyphen of its own, and the last of {@code most} lines cut with an ellipsis when the rest does not fit.
     */
    static List<String> wrap(String text, int most) {
        if (most <= 0) {
            return List.of();
        }
        record Piece(String glue, String text) { }
        var pieces = new ArrayDeque<Piece>();
        var matcher = PIECE.matcher(text.strip());
        while (matcher.find()) {
            pieces.add(new Piece(matcher.group(1).isEmpty() ? "" : " ", matcher.group(2)));
        }
        var lines = new ArrayList<String>();
        var line = "";
        while (!pieces.isEmpty()) {
            var piece = pieces.removeFirst();
            var joined = line.isEmpty() ? piece.text() : line + piece.glue() + piece.text();
            if (ink(joined) <= TEXT_WIDTH) {
                line = joined;
            } else if (!line.isEmpty()) {
                lines.add(line);
                line = "";
                pieces.addFirst(piece);
            } else {
                int cut = piece.text().length() - 1;
                while (cut > 1 && ink(piece.text().substring(0, cut) + "-") > TEXT_WIDTH) {
                    cut--;
                }
                lines.add(piece.text().substring(0, cut) + "-");
                pieces.addFirst(new Piece("", piece.text().substring(cut)));
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        if (lines.size() <= most) {
            return lines;
        }
        var kept = new ArrayList<>(lines.subList(0, most));
        kept.set(most - 1, fit(kept.get(most - 1) + " " + lines.get(most), TEXT_WIDTH));
        return kept;
    }

    /** {@code text} cut with an ellipsis until its ink is no wider than {@code room}. */
    static String fit(String text, int room) {
        if (ink(text) <= room) {
            return text;
        }
        var cut = text;
        while (!cut.isEmpty() && ink(cut + "…") > room) {
            cut = cut.substring(0, cut.length() - 1).stripTrailing();
        }
        return cut + "…";
    }

    /** How far the line fonts move the cursor for {@code text}: a letter or a space four pixels, a mark six. */
    static int width(String text) {
        return text.chars().map(character -> character >= COIN && character <= CLIP ? MARK_ADVANCE : LETTER_ADVANCE).sum();
    }

    /** {@code text}'s width without the spacing after its last glyph. */
    private static int ink(String text) {
        return Math.max(0, width(text) - 1);
    }
}
