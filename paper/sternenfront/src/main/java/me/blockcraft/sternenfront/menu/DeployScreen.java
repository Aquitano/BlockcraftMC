package me.blockcraft.sternenfront.menu;

import me.blockcraft.api.pack.UiFont;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.AbilityIcon;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Spawn;
import me.blockcraft.sternenfront.game.StatIcon;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The deploy screen as its chest title draws it: a window wider and taller than the chest from
 * {@code resourcepack/DeployArt.java}, the chosen role's loadout left of the slots, the spawns' names and what the
 * markers mean right of them, and the cards' pictures under the slots that answer clicks. The title is fixed once the
 * chest is open, so it shows only what changes on the player's own clicks or seldom; the markers show the rest.
 * <p>
 * Every part moves the cursor back by the width it measures for itself, so a part at a wrong x moves nothing else, but
 * one whose width is measured wrongly shifts every part after it. A text line uses the font the pack writes for its
 * height, since a font draws at one height only, and every letter comes from the pack's own sheets: a player's pack
 * that replaces the vanilla letters would otherwise give them widths the server does not know.
 */
final class DeployScreen {

    /** The chest's slots under the cards, which {@code DeployArt} draws at the same places; DeployScreenTest compares them. */
    static final List<Integer> CLASS_CARDS = List.of(9, 14, 27, 32);
    static final int CLASS_CARD_COLUMNS = 4;
    static final int CLASS_CARD_ROWS = 2;
    static final List<Integer> SPECIALS = List.of(45, 48, 51);
    static final int SPECIAL_COLUMNS = 3;
    /** The toolbar's buttons from these slots, each up to the next or the row's end: close, then deploy. */
    static final List<Integer> BUTTONS = List.of(0, 4);

    static final Key ART = Key.key("blockcraft", "deploy/art");
    private static final int COLUMNS = 9;
    private static final int SLOT = 18;
    private static final int SLOT_LEFT = 8;
    private static final int SLOT_TOP = 18;
    /** Where the chest's title starts, which every part measures from. */
    private static final int ORIGIN = 8;
    private static final int WINDOW_LEFT = -124;
    private static final int CHEST_WIDTH = 176;
    private static final int WINDOW_RIGHT = 300;
    /** Where the header's text starts, right of the window's accent bar. */
    private static final int HEADER_LEFT = -114;

    private static final int WINDOW = 0xE000;
    private static final int DOSSIERS = 0xE010;
    private static final int CARD_PORTRAITS = 0xE030;
    private static final int CARD_PORTRAIT_ROWS = 0x10;
    private static final int MINIS = 0xE050;
    private static final int CLASS_SELECTION = 0xE060;
    private static final int SPECIAL_SELECTION = 0xE062;
    private static final int LABEL_GLYPHS = 0xE000;
    private static final int ICON_GLYPHS = 0xE100;
    private static final int ABILITY_GLYPHS = 0xE120;
    private static final int BADGE_GLYPHS = 0xE140;
    private static final int TOOLBAR_GLYPHS = 0xE150;
    private static final int PORTRAIT_WELL = 30;

    private static final String LABELS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ÄÖÜ-.:!?…/+&%,()·";
    private static final int LABEL_ADVANCE = 4;
    private static final int ICON_ADVANCE = 10;
    private static final int ABILITY_ADVANCE = 17;
    private static final int BADGE_ADVANCE = 13;
    private static final int TOOLBAR_ADVANCE = 17;
    /** Where the list's rightmost mark stands, and how far apart its marks are. */
    private static final int MARKS_RIGHT = 285;
    private static final int MARK_PITCH = 11;

    /** How many lines of each kind the window has room for. */
    private static final int STAT_ROWS = 4;
    private static final int ABILITY_ROWS = 2;
    private static final int PRICE_ROWS = 2;
    /** Headquarters, posts A to E and the dropship. */
    private static final int MOST_PLACES = 7;
    /** Up to this many spawns the list takes the roomy pitch, more take the tight one. */
    private static final int ROOMY_PLACES = 5;
    private static final int ROOMY_PITCH = 20;
    private static final int TIGHT_PITCH = 15;
    private static final int LEGEND_PITCH = 10;
    /** The legend's marks end here, the countdown's sample number with them, and its lines start a gap further right. */
    private static final int LEGEND_MARKS_RIGHT = 192;
    private static final int LEGEND_TEXT = LEGEND_MARKS_RIGHT + 4;
    /** The widest a line under the map may be: the space between the side panels. */
    private static final int HINT_WIDTH = 176;

    private static final int MUTED = 0x9CA6B8;
    private static final int WHITE = 0xFFFFFF;
    private static final int GOLD = 0xFFD447;
    private static final int GREEN = 0x5BEA6B;
    private static final int RED = 0xFF5B4B;
    private static final int DIM = 0x5D6678;
    private static final int AMBER = 0xFFB830;
    /** The labels' colour on each toolbar button: white on the red, dark on the yellow. */
    private static final List<Integer> BUTTON_TEXT = List.of(WHITE, 0x1B1E26);

    /** The pack's portraits, in the order of its sheets. */
    enum Portrait {
        KOLONIEN_SOLDIER, KOLONIEN_HEAVY, KOLONIEN_SCOUT, KOLONIEN_MEDIC,
        HEGEMONIE_SOLDIER, HEGEMONIE_HEAVY, HEGEMONIE_SCOUT, HEGEMONIE_MEDIC,
        ARC_TROOPER, COMMANDO_DROID, JEDI, COMMANDER, SITH, BOUNTY_HUNTER;

        static Portrait of(Faction faction, CombatClass combatClass) {
            return valueOf(faction.name() + "_" + combatClass.name());
        }

        static Portrait of(Reinforcement unit) {
            return valueOf(unit.name());
        }

        static Portrait of(Hero hero) {
            return valueOf(hero.name());
        }
    }

    /** A line of the loadout read-out: what it is and how much, with the stat's symbol if it has one. */
    record Stat(String label, String value, Optional<StatIcon> icon) { }

    /** An ability with the key it sits on. */
    record Ability(String name, String key, AbilityIcon icon) { }

    /** Who the player deploys as next, in full. */
    record Role(String kind, String name, Portrait portrait, List<Stat> body, String weapon, List<Ability> abilities,
                List<Stat> price) { }

    record ClassCard(String name, Portrait portrait, List<AbilityIcon> abilities, boolean selected) { }

    /** A reinforcement or hero; {@code open} once the player could pay for it and a slot is free. */
    record Special(String name, Portrait portrait, int cost, boolean open, boolean selected) { }

    /** Whether the player's choice is this spawn: none, chosen but confirmed no longer, or confirmed. */
    enum Selection { NONE, PENDING, CONFIRMED }

    /**
     * A spawn as the list right of the slots names it, with the marks its marker on the map wears: the badge in its
     * owner's colour, outlined while locked, the star for the recommended spawn and the frame of the player's choice,
     * which a locked spawn hides.
     */
    record Place(Spawn spawn, String name, int colour, boolean open, boolean recommended, Selection selection) {

        private Place unmarked() {
            return new Place(spawn, name, colour, true, false, selection);
        }
    }

    /** What the line under the map tells the player: how to choose, what a click on a spawn costs, or why one was refused. */
    record Hint(String text, Tone tone) {
        enum Tone { HELP, COST, REFUSED }
    }

    record View(Faction faction, Component title, List<String> buttons, Role role, List<ClassCard> classes,
                List<Special> specials, List<Place> places, Hint hint) {

        /** This view without what the battle flips on its own: which spawns are locked and which one is recommended. */
        View withoutSpawnMarks() {
            return new View(faction, title, buttons, role, classes, specials, places.stream().map(Place::unmarked).toList(), hint);
        }
    }

    private final Messages messages;
    private final TextComponent.Builder title = Component.text();

    private DeployScreen(Messages messages) {
        this.messages = messages;
    }

    static Component title(Messages messages, View view) {
        var screen = new DeployScreen(messages);
        screen.window(view);
        screen.loadout(view);
        screen.cards(view);
        screen.places(view);
        screen.title.append(UiFont.space(HEADER_LEFT - ORIGIN)).append(UiFont.measured(view.title()));
        return screen.title.build();
    }

    static int x(int slot) {
        return SLOT_LEFT + slot % COLUMNS * SLOT;
    }

    static int y(int slot) {
        return SLOT_TOP + slot / COLUMNS * SLOT;
    }

    static int buttonColumns(int button) {
        return (button + 1 < BUTTONS.size() ? BUTTONS.get(button + 1) : COLUMNS) - BUTTONS.get(button);
    }

    private void window(View view) {
        int pieces = WINDOW + view.faction().ordinal() * 3;
        art(WINDOW_LEFT, (char) pieces, -WINDOW_LEFT + 1);
        art(0, (char) (pieces + 1), CHEST_WIDTH + 1);
        art(CHEST_WIDTH, (char) (pieces + 2), WINDOW_RIGHT - CHEST_WIDTH + 1);
        label(HEADER_LEFT, -3, plain("faction." + view.faction().key() + ".name"), view.faction().text().value(), 280);
        for (int button = 0; button < view.buttons().size() && button < BUTTONS.size(); button++) {
            String name = view.buttons().get(button);
            int width = buttonColumns(button) * SLOT - 2;
            int content = Math.min(TOOLBAR_ADVANCE + 1 + labelWidth(name), width - 2);
            int left = x(BUTTONS.get(button)) + (width - content) / 2;
            glyph(left, y(BUTTONS.get(button)), (char) (TOOLBAR_GLYPHS + button), TOOLBAR_ADVANCE, WHITE);
            label(left + TOOLBAR_ADVANCE + 1, y(BUTTONS.get(button)) + 6, name, BUTTON_TEXT.get(button), width - TOOLBAR_ADVANCE - 3);
        }
        label(127, 130, plain("deploy.screen.map"), WHITE, 40);
        var hint = view.hint();
        int tone = switch (hint.tone()) {
            case HELP -> MUTED;
            case COST -> GOLD;
            case REFUSED -> RED;
        };
        label(CHEST_WIDTH / 2 - Math.min(labelWidth(hint.text()), HINT_WIDTH) / 2, 202, hint.text(), tone, HINT_WIDTH);
    }

    private void loadout(View view) {
        var role = view.role();
        label(-117, 21, plain("deploy.screen.loadout"), MUTED, 60);
        label(-7 - Math.min(labelWidth(role.kind()), 48), 21, role.kind(), view.faction().text().value(), 48);
        art(-117, (char) (DOSSIERS + role.portrait().ordinal()), 65);
        for (int index = 0; index < role.body().size() && index < STAT_ROWS; index++) {
            var stat = role.body().get(index);
            label(-48, 32 + index * 16, stat.label(), MUTED, 42);
            text(-48, 39 + index * 16, stat.value(), WHITE);
            int top = 38 + index * 16;
            int x = -46 + textWidth(stat.value());
            stat.icon().ifPresent(icon -> glyph(x, top, icon.glyph(), ICON_ADVANCE, icon.colour()));
        }
        text(-117, 99, role.name(), WHITE);
        label(-117, 114, plain("deploy.screen.weapon"), MUTED, 112);
        label(-117, 122, role.weapon(), WHITE, 112);
        label(-117, 134, plain("deploy.screen.abilities"), MUTED, 112);
        for (int index = 0; index < role.abilities().size() && index < ABILITY_ROWS; index++) {
            var ability = role.abilities().get(index);
            int top = 144 + index * 24;
            glyph(-116, top, (char) (ABILITY_GLYPHS + ability.icon().ordinal()), ABILITY_ADVANCE, WHITE);
            text(-94, top, ability.name(), WHITE);
            label(-94, top + 10, ability.key(), MUTED, 88);
        }
        for (int index = 0; index < role.price().size() && index < PRICE_ROWS; index++) {
            var line = role.price().get(index);
            label(-117, 196 + index * 10, line.label(), MUTED, 72);
            label(-7 - Math.min(labelWidth(line.value()), 50), 196 + index * 10, line.value(), index == 0 ? GOLD : WHITE, 50);
        }
    }

    private void cards(View view) {
        var rows = CLASS_CARDS.stream().map(DeployScreen::y).distinct().toList();
        for (int index = 0; index < view.classes().size() && index < CLASS_CARDS.size(); index++) {
            var card = view.classes().get(index);
            int x = x(CLASS_CARDS.get(index));
            int y = y(CLASS_CARDS.get(index));
            int row = rows.indexOf(y);
            int side = x + 1 + PORTRAIT_WELL + 2;
            art(x + 1, (char) (CARD_PORTRAITS + row * CARD_PORTRAIT_ROWS + card.portrait().ordinal()), PORTRAIT_WELL + 1);
            label(side, y + 4, card.name(), card.selected() ? WHITE : MUTED, 36);
            for (int key = 0; key < card.abilities().size() && key < ABILITY_ROWS; key++) {
                glyph(side + key * (ABILITY_ADVANCE + 1), y + 14, (char) (ABILITY_GLYPHS + card.abilities().get(key).ordinal()),
                        ABILITY_ADVANCE, card.selected() ? WHITE : MUTED);
            }
            if (card.selected()) {
                art(x - 1, (char) (CLASS_SELECTION + row), CLASS_CARD_COLUMNS * SLOT + 1);
            }
        }
        for (int index = 0; index < view.specials().size() && index < SPECIALS.size(); index++) {
            var special = view.specials().get(index);
            int x = x(SPECIALS.get(index));
            int y = y(SPECIALS.get(index));
            int side = x + SLOT - 1;
            art(x + 1, (char) (MINIS + special.portrait().ordinal()), SLOT - 3);
            label(side, y + 3, special.name(), special.selected() ? WHITE : MUTED, 34);
            String cost = String.valueOf(special.cost());
            label(side, y + 10, cost, special.open() ? GOLD : RED, 24);
            label(side + 2 + labelWidth(cost), y + 10, plain("deploy.screen.points"), DIM, 10);
            if (special.selected()) {
                art(x - 1, (char) SPECIAL_SELECTION, SPECIAL_COLUMNS * SLOT + 1);
            }
        }
    }

    /** A map's few spawns spread over the space above the key; five posts with the dropship need every line of it. */
    private void places(View view) {
        label(182, 21, plain("deploy.screen.places"), MUTED, 110);
        int count = Math.min(view.places().size(), MOST_PLACES);
        int pitch = count <= ROOMY_PLACES ? ROOMY_PITCH : TIGHT_PITCH;
        for (int index = 0; index < count; index++) {
            var place = view.places().get(index);
            int top = 32 + index * pitch;
            switch (place.spawn()) {
                case Spawn.Headquarters ignored -> icon(184, top + 2, Icon.HEADQUARTERS, place.colour());
                case Spawn.Post post -> glyph(182, top, (char) (BADGE_GLYPHS + post.index() * 2 + (place.open() ? 0 : 1)),
                        BADGE_ADVANCE, place.colour());
                case Spawn.Dropship ignored -> icon(184, top + 2, Icon.DROPSHIP, place.colour());
            }
            text(199, top + 3, place.name(), place.open() ? WHITE : MUTED);
            marks(top + 2, place);
        }
        label(182, 144, plain("deploy.screen.legend"), MUTED, 110);
        var keys = List.of(Icon.RECOMMENDED, Icon.CONFIRMED, Icon.PENDING, Icon.LOCKED, Icon.CONTESTED);
        var colours = List.of(GOLD, GREEN, AMBER, RED, WHITE);
        var lines = List.of("recommended", "confirmed", "pending", "locked", "contested");
        for (int index = 0; index < keys.size(); index++) {
            icon(182, 153 + index * LEGEND_PITCH, keys.get(index), colours.get(index));
            label(LEGEND_TEXT, 155 + index * LEGEND_PITCH, plain("deploy.screen.key." + lines.get(index)), WHITE, 100);
        }
        int countdown = 153 + keys.size() * LEGEND_PITCH;
        String sample = plain("deploy.screen.key.countdown-sample");
        text(LEGEND_MARKS_RIGHT - textWidth(sample), countdown + 1, sample, WHITE);
        label(LEGEND_TEXT, countdown + 2, plain("deploy.screen.key.countdown"), WHITE, 100);
        label(182, 214, plain("deploy.screen.orientation"), DIM, 110);
    }

    /** The marks a spawn's marker wears on the map, from the right: the player's choice, then the star; or the padlock. */
    private void marks(int top, Place place) {
        if (!place.open()) {
            icon(MARKS_RIGHT, top, Icon.LOCKED, RED);
            return;
        }
        int x = MARKS_RIGHT;
        if (place.selection() != Selection.NONE) {
            boolean confirmed = place.selection() == Selection.CONFIRMED;
            icon(x, top, confirmed ? Icon.CONFIRMED : Icon.PENDING, confirmed ? GREEN : AMBER);
            x -= MARK_PITCH;
        }
        if (place.recommended()) {
            icon(x, top, Icon.RECOMMENDED, GOLD);
        }
    }

    /** The pictures of the pack's icon sheet, in the order {@code DeployArt} names them. */
    enum Icon {
        HEART, RECOMMENDED, PENDING, CONFIRMED, LOCKED, CONTESTED, HEADQUARTERS, DROPSHIP, DAMAGE, SPEED, RANGE
    }

    private void icon(int x, int top, Icon icon, int colour) {
        glyph(x, top, (char) (ICON_GLYPHS + icon.ordinal()), ICON_ADVANCE, colour);
    }

    private void glyph(int x, int top, char glyph, int advance, int colour) {
        at(x, Component.text(glyph).font(line(top)).color(TextColor.color(colour)), advance);
    }

    private void art(int x, char glyph, int advance) {
        at(x, Component.text(glyph).font(ART).color(TextColor.color(WHITE)), advance);
    }

    /** {@code text} in the menu tiles' capitals, cut short at {@code width}; whatever they cannot spell shows as a question mark. */
    private void label(int x, int top, String text, int colour, int width) {
        String capitals = text.toUpperCase(Locale.ROOT);
        String fitted = labelWidth(capitals) <= width ? capitals : capitals.substring(0, Math.max(0, width / LABEL_ADVANCE - 1)) + "…";
        var spelled = new StringBuilder();
        fitted.chars().forEach(character -> spelled.append(character == ' ' ? ' '
                : (char) (LABEL_GLYPHS + (LABELS.indexOf(character) >= 0 ? LABELS.indexOf(character) : LABELS.indexOf('?')))));
        at(x, Component.text(spelled.toString()).font(line(top)).color(TextColor.color(colour)), labelWidth(fitted));
    }

    private void text(int x, int top, String text, int colour) {
        String drawable = drawable(text);
        at(x, Component.text(drawable).font(line(top)).color(TextColor.color(colour)), textWidth(drawable));
    }

    private void at(int x, Component part, int advance) {
        title.append(UiFont.space(x - ORIGIN)).append(part).append(UiFont.space(ORIGIN - x - advance));
    }

    static Key line(int top) {
        return Key.key("blockcraft", "deploy/line_" + top);
    }

    /** In capitals, as a label spells it: an ß takes two letters. */
    static int labelWidth(String text) {
        return text.toUpperCase(Locale.ROOT).length() * LABEL_ADVANCE;
    }

    /** The pack's copy of the vanilla ASCII letters and its umlauts; anything else would draw at a width the server cannot know. */
    static String drawable(String text) {
        var drawable = new StringBuilder();
        text.chars().forEach(character -> drawable.append(character >= ' ' && character <= '~' || UiFont.TEXT_ACCENTS.indexOf(character) >= 0
                ? (char) character : '?'));
        return drawable.toString();
    }

    static int textWidth(String text) {
        return UiFont.width(Component.text(drawable(text)));
    }

    private String plain(String key) {
        return PlainTextComponentSerializer.plainText().serialize(messages.get(key));
    }
}
