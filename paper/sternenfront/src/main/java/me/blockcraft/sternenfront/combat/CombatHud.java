package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.api.scene.Scenes;
import me.blockcraft.sternenfront.game.Charges;
import me.blockcraft.sternenfront.game.Heat;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Hero;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.stream.IntStream;

public final class CombatHud {

    private final SternenfrontGame game;

    public CombatHud(SternenfrontGame game) {
        this.game = game;
    }

    /** Wide enough for the longest state, so the line does not slide when one appears. */
    static final int STATE_WIDTH = 30;
    /** A meter glyph's width with its spacing, which each vent window glyph laid over it shares. */
    private static final int METER_ADVANCE = 42;

    /** Whether a dialogue, a screen effect or a scene has the screen, which the HUD then leaves to it. */
    public static boolean covered(Player player) {
        var api = BlockcraftApi.get();
        return api.dialogues().active(player) || api.visuals().active(player) || Scenes.isViewing(player);
    }

    public void show(Player player, Guard guard) {
        if (covered(player)) return;
        game.deployment().controlsChanged(player);
        var fighter = game.fighter(player);
        boolean blade = fighter.hero().filter(Hero::bladed).isPresent();
        var heat = fighter.heat();
        boolean venting = heat.venting(game.tick());
        boolean hot = !blade && game.feedback().hot(player);
        var meter = game.messages().get(blade ? guard.broken(game.tick()) ? "combat.broken" : guard.raised()
                        ? "combat.guarding" : "combat.guard" : venting ? "combat.venting" : hot ? "combat.hot" : "combat.heat",
                Formatter.number("seconds", (heat.ventTicks(game.tick()) + 19) / 20));
        var line = game.messages().get("combat.hud",
                Placeholder.component("meter_icon", icon(blade ? 2 : 1)),
                Placeholder.component("meter", (blade ? bar(guard.level()) : venting ? vent(heat) : bar(heat.level()))
                        .color(hot || venting ? NamedTextColor.RED : NamedTextColor.GOLD)),
                Placeholder.component("state", UiFont.before(meter, STATE_WIDTH)),
                Placeholder.component("first", ability(player, 0)),
                Placeholder.component("second", ability(player, 1)));
        if (blade && game.combat().leapCooldown(player) > 0) {
            line = line.append(game.messages().get("combat.leap-cooldown",
                    Formatter.number("seconds", (game.combat().leapCooldown(player) + 19) / 20)));
        }
        if (game.strikeReady(player)) {
            line = line.append(game.messages().get("combat.strike-ready"));
        }
        game.feedback().show(player, line);
    }

    private Component ability(Player player, int index) {
        var fighter = game.fighter(player);
        var hero = fighter.hero();
        var faction = game.factionOf(player);
        var classAbility = faction.flatMap(side -> fighter.abilities(side).stream().skip(index).findFirst());
        var key = hero.isPresent() ? hero.get().abilities().stream().skip(index).findFirst().map(ability -> "hero.ability." + ability.key())
                : classAbility.flatMap(ability -> faction.map(ability::messageKey));
        if (key.isEmpty()) {
            return game.messages().get("combat.none");
        }
        var material = hero.isPresent() ? hero.get().abilities().get(index).material() : classAbility.orElseThrow().material();
        var charges = hero.isPresent() ? Optional.<Charges>empty() : classAbility.map(ability -> game.charges(fighter, ability))
                .filter(held -> held.capacity() > 1);
        int stored = charges.map(held -> held.stored(game.tick())).orElse(0);
        int cooldown = player.getCooldown(material);
        var status = stored > 0 ? game.messages().get("combat.charges", Formatter.number("stored", stored),
                Formatter.number("capacity", charges.get().capacity()))
                : cooldown == 0 ? game.messages().get("combat.ready")
                : game.messages().get("combat.cooldown", Formatter.number("seconds", (cooldown + 19) / 20));
        return game.messages().get("combat.ability", Placeholder.component("key", icon(index + 3)),
                Placeholder.component("label", game.messages().get(key.get() + ".name")), Placeholder.component("status", status));
    }

    private static Component icon(int index) {
        return UiFont.glyph((char) ('\uE220' + index));
    }

    private static Component bar(double level) {
        return meter((int) Math.round(Math.clamp(level, 0, 1) * Heat.METER_CELLS));
    }

    private static Component meter(int cells) {
        return UiFont.glyph((char) ('\uE240' + Math.clamp(cells, 0, Heat.METER_CELLS)));
    }

    /** The cooling meter with the vent's window bracketed over it, so a pull is timed against what the player sees. */
    private Component vent(Heat heat) {
        long tick = game.tick();
        var window = heat.window(tick).map(cells -> IntStream.range(cells.low(), cells.high())
                .mapToObj(cell -> UiFont.over((char) ('\uE260' + cell), METER_ADVANCE))
                .reduce(Component.empty(), Component::append)).orElse(Component.empty());
        return meter(heat.litCells(tick)).append(window.color(NamedTextColor.AQUA));
    }
}
