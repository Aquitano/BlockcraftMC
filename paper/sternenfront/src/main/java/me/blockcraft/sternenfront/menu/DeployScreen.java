package me.blockcraft.sternenfront.menu;

import me.blockcraft.api.pack.UiFont;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.AbilityIcon;
import me.blockcraft.sternenfront.game.Faction;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The deploy screen as its chest title draws it: a window wider and taller than the chest from
 * {@code resourcepack/DeployArt.java}, the chosen role's loadout left of the slots, the spawns' names and what the
 * markers mean right of them, and the cards' pictures under the slots that answer clicks. The title is fixed once the
 * chest is open, so it shows only what changes on the player's own clicks or seldom; the markers show the rest.
 * <p>
 * Every part is placed from the title's start and returns the cursor there, so a part placed wrongly moves nothing
 * else. A text line uses the font the pack writes for its height, since a font draws at one height only.
 */
final class DeployScreen {

    static final Key ART = Key.key("blockcraft", "deploy/art");
    private static final int ORIGIN = 8;
    private static final String LABELS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ÄÖÜ-.:!?…/+&%,()·";
    private static final int LABEL_ADVANCE = 4;
    private static final String DRAWN_TEXT = "äöüÄÖÜß·…";
    private static final Map<Character, Integer> DRAWN_ADVANCES = Map.of('·', 2, '…', 8);
    private static final int DRAWN_ADVANCE = 6;

    private static final int MUTED = 0x9CA6B8;
    private static final int WHITE = 0xFFFFFF;
    private static final int GOLD = 0xFFD447;
    private static final int GREEN = 0x5BEA6B;
    private static final int RED = 0xFF5B4B;
    private static final int DIM = 0x5D6678;

    /** The pack's portraits, in the order of its sheets. */
    enum Portrait {
        KOLONIEN_SOLDIER, KOLONIEN_HEAVY, KOLONIEN_SCOUT, KOLONIEN_MEDIC,
        HEGEMONIE_SOLDIER, HEGEMONIE_HEAVY, HEGEMONIE_SCOUT, HEGEMONIE_MEDIC,
        ARC_TROOPER, COMMANDO_DROID, JEDI, COMMANDER, SITH, BOUNTY_HUNTER
    }

    /** A line of the loadout read-out: what it is and how much, in hearts if {@code hearts}. */
    record Stat(String label, String value, boolean hearts) { }

    /** An ability with the key it sits on. */
    record Ability(String name, String key, AbilityIcon icon) { }

    /** Who the player deploys as next, in full. */
    record Role(String kind, String name, Portrait portrait, List<Stat> body, String weapon, List<Ability> abilities,
                List<Stat> price) { }

    record ClassCard(String name, Portrait portrait, List<AbilityIcon> abilities, boolean selected) { }

    /** A reinforcement or hero; {@code open} once the player could pay for it and a slot is free. */
    record Special(String name, Portrait portrait, int cost, boolean open, boolean selected) { }

    /** A spawn as the legend names it. */
    sealed interface Place {
        String name();

        record Headquarters(String name) implements Place { }

        record Post(int index, String name) implements Place { }

        record Landing(String name) implements Place { }
    }

    record View(Faction faction, Component title, Role role, List<ClassCard> classes, List<Special> specials, int points,
                List<Place> places) { }

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
        screen.title.append(UiFont.space(-114 - ORIGIN)).append(view.title());
        return screen.title.build();
    }

    private void window(View view) {
        int faction = view.faction().ordinal();
        art(-124, (char) (0xE000 + faction * 3), 125);
        art(0, (char) (0xE001 + faction * 3), 177);
        art(176, (char) (0xE002 + faction * 3), 125);
        label(-114, -3, plain("faction." + view.faction().key() + ".name"), view.faction().text().value(), 280);
        icon(120, 22, Icon.POINTS, GOLD);
        String points = String.valueOf(view.points());
        text(132, 23, points, WHITE);
        label(134 + textWidth(points), 25, plain("deploy.screen.points"), MUTED, 20);
        label(127, 130, plain("deploy.screen.places"), WHITE, 40);
        String hint = plain("deploy.screen.hint");
        label(88 - Math.min(labelWidth(hint), 160) / 2, 202, hint, MUTED, 160);
    }

    private void loadout(View view) {
        var role = view.role();
        label(-117, 21, plain("deploy.screen.loadout"), MUTED, 60);
        label(-7 - Math.min(labelWidth(role.kind()), 48), 21, role.kind(), view.faction().text().value(), 48);
        art(-117, (char) (0xE010 + role.portrait().ordinal()), 65);
        for (int index = 0; index < role.body().size() && index < 4; index++) {
            var stat = role.body().get(index);
            label(-48, 32 + index * 16, stat.label(), MUTED, 42);
            text(-48, 39 + index * 16, stat.value(), WHITE);
            if (stat.hearts()) {
                icon(-46 + textWidth(stat.value()), 38 + index * 16, Icon.HEART, RED);
            }
        }
        text(-117, 99, role.name(), WHITE);
        label(-117, 114, plain("deploy.screen.weapon"), MUTED, 112);
        label(-117, 122, role.weapon(), WHITE, 112);
        label(-117, 134, plain("deploy.screen.abilities"), MUTED, 112);
        for (int index = 0; index < role.abilities().size() && index < 2; index++) {
            var ability = role.abilities().get(index);
            int top = 144 + index * 24;
            glyph(-116, top, (char) (0xE120 + ability.icon().ordinal()), 17, WHITE);
            text(-94, top, ability.name(), WHITE);
            label(-94, top + 10, ability.key(), MUTED, 88);
        }
        for (int index = 0; index < role.price().size() && index < 2; index++) {
            var line = role.price().get(index);
            label(-117, 196 + index * 10, line.label(), MUTED, 60);
            label(-7 - Math.min(labelWidth(line.value()), 50), 196 + index * 10, line.value(), index == 0 ? GOLD : WHITE, 50);
        }
    }

    private void cards(View view) {
        for (int index = 0; index < view.classes().size() && index < 4; index++) {
            var card = view.classes().get(index);
            int x = 8 + index % 2 * 90;
            int y = 36 + index / 2 * 36;
            art(x + 1, (char) ((index < 2 ? 0xE030 : 0xE040) + card.portrait().ordinal()), 31);
            label(x + 33, y + 4, card.name(), card.selected() ? WHITE : MUTED, 36);
            for (int key = 0; key < card.abilities().size() && key < 2; key++) {
                glyph(x + 33 + key * 18, y + 14, (char) (0xE120 + card.abilities().get(key).ordinal()), 17,
                        card.selected() ? WHITE : MUTED);
            }
            if (card.selected()) {
                art(x - 1, index < 2 ? '\uE060' : '\uE061', 73);
            }
        }
        for (int index = 0; index < view.specials().size() && index < 3; index++) {
            var special = view.specials().get(index);
            int x = 8 + index * 54;
            art(x + 1, (char) (0xE050 + special.portrait().ordinal()), 15);
            label(x + 17, 111, special.name(), special.selected() ? WHITE : MUTED, 34);
            String cost = String.valueOf(special.cost());
            label(x + 17, 118, cost, special.open() ? GOLD : RED, 24);
            label(x + 19 + labelWidth(cost), 118, plain("deploy.screen.points"), DIM, 10);
            if (special.selected()) {
                art(x - 1, '\uE062', 55);
            }
        }
    }

    /** A map's few spawns spread over the space above the key; five posts with the landing need every line of it. */
    private void places(View view) {
        label(182, 21, plain("deploy.screen.places-legend"), MUTED, 110);
        int count = Math.min(view.places().size(), 7);
        int pitch = count <= 5 ? 20 : 15;
        for (int index = 0; index < count; index++) {
            var place = view.places().get(index);
            int top = 32 + index * pitch;
            switch (place) {
                case Place.Headquarters ignored -> icon(184, top + 2, Icon.HEADQUARTERS, view.faction().text().value());
                case Place.Post post -> glyph(182, top, (char) (0xE140 + post.index() * 2), 13, WHITE);
                case Place.Landing ignored -> icon(184, top + 2, Icon.LANDING, WHITE);
            }
            text(199, top + 3, place.name(), WHITE);
        }
        label(182, 144, plain("deploy.screen.legend"), MUTED, 110);
        Icon[] keys = {Icon.RECOMMENDED, Icon.PENDING, Icon.CONFIRMED, Icon.LOCKED, Icon.CONTESTED};
        int[] colours = {GOLD, 0xFFB830, GREEN, RED, WHITE};
        String[] lines = {"recommended", "pending", "confirmed", "locked", "contested"};
        for (int index = 0; index < keys.length; index++) {
            icon(182, 153 + index * 11, keys[index], colours[index]);
            label(194, 155 + index * 11, plain("deploy.screen.key." + lines[index]), WHITE, 98);
        }
        label(182, 214, plain("deploy.screen.orientation"), DIM, 110);
    }

    /** The pictures of the pack's icon sheet, in its order. */
    private enum Icon {
        KEY_F, KEY_Q, HEART, POINTS, RECOMMENDED, PENDING, CONFIRMED, LOCKED, CONTESTED, HEADQUARTERS, LANDING, FILLED, HOLLOW
    }

    private void icon(int x, int top, Icon icon, int colour) {
        glyph(x, top, (char) (0xE100 + icon.ordinal()), 10, colour);
    }

    private void glyph(int x, int top, char glyph, int advance, int colour) {
        at(x, Component.text(glyph).font(line(top)).color(TextColor.color(colour)), advance);
    }

    private void art(int x, char glyph, int advance) {
        at(x, Component.text(glyph).font(ART).color(TextColor.color(WHITE)), advance);
    }

    /** {@code text} in the menu tiles' capitals, cut short at {@code width}; whatever they cannot spell shows as a question mark. */
    private void label(int x, int top, String text, int colour, int width) {
        String fitted = text.length() * LABEL_ADVANCE <= width ? text : text.substring(0, Math.max(0, width / LABEL_ADVANCE - 1)) + "…";
        var spelled = new StringBuilder();
        fitted.toUpperCase(Locale.ROOT).chars().forEach(character -> spelled.append(character == ' ' ? ' '
                : (char) (0xE000 + (LABELS.indexOf(character) >= 0 ? LABELS.indexOf(character) : LABELS.indexOf('?')))));
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

    static int labelWidth(String text) {
        return text.length() * LABEL_ADVANCE;
    }

    /** The vanilla font's ASCII and the pack's own umlauts; anything else would draw at a width the server cannot know. */
    static String drawable(String text) {
        var drawable = new StringBuilder();
        text.chars().forEach(character -> drawable.append(character >= ' ' && character <= '~' || DRAWN_TEXT.indexOf(character) >= 0
                ? (char) character : '?'));
        return drawable.toString();
    }

    static int textWidth(String text) {
        return drawable(text).chars().map(character -> DRAWN_TEXT.indexOf(character) >= 0
                ? DRAWN_ADVANCES.getOrDefault((char) character, DRAWN_ADVANCE)
                : UiFont.width(Component.text(Character.toString(character)))).sum();
    }

    private String plain(String key) {
        return PlainTextComponentSerializer.plainText().serialize(messages.get(key));
    }
}
