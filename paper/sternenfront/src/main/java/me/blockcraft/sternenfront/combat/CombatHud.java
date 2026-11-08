package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.api.scene.Scenes;
import me.blockcraft.sternenfront.combat.AbilityPanel.Binding;
import me.blockcraft.sternenfront.combat.AbilityPanel.Slot;
import me.blockcraft.sternenfront.combat.AbilityPanel.State;
import me.blockcraft.sternenfront.game.AbilityIcon;
import me.blockcraft.sternenfront.game.CombatFeedback;
import me.blockcraft.sternenfront.game.Heat;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Hero;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** The heat or guard meter on the action bar, and the ability tiles the pack moves into the bottom-right corner. */
public final class CombatHud {

    private final SternenfrontGame game;

    public CombatHud(SternenfrontGame game) {
        this.game = game;
    }

    /** Wide enough for the longest state, so the line does not slide when one appears. */
    static final int STATE_WIDTH = 30;
    /** A meter glyph's width with its spacing, which each vent window glyph laid over it shares. */
    static final int METER_ADVANCE = 42;
    /** The flame's and the shield's width with their spacing. */
    static final int METER_ICON_ADVANCE = 11;
    private static final int TICKS_PER_SECOND = 20;

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
        var state = game.messages().get(blade ? guard.broken(game.tick()) ? "combat.broken" : guard.raised()
                        ? "combat.guarding" : "combat.guard" : venting ? "combat.venting" : hot ? "combat.hot" : "combat.heat",
                Formatter.number("seconds", seconds(heat.ventTicks(game.tick()))));
        var meter = hud(icon(blade ? 2 : 1), (blade ? bar(guard.level()) : venting ? vent(heat) : bar(heat.level()))
                .color(hot || venting ? NamedTextColor.RED : NamedTextColor.GOLD), UiFont.before(state, STATE_WIDTH));
        int width = UiFont.width(hud(Component.empty(), Component.empty(), Component.empty()))
                + METER_ICON_ADVANCE + METER_ADVANCE + Math.max(STATE_WIDTH, UiFont.width(state));
        var corner = BlockcraftApi.get().resourcePack().applied(player) ? AbilityPanel.render(slots(player, blade)) : Component.empty();
        game.feedback().show(player, new CombatFeedback.Hud(meter, width, corner));
    }

    private Component hud(Component icon, Component bar, Component state) {
        return game.messages().get("combat.hud", Placeholder.component("meter_icon", icon), Placeholder.component("meter", bar),
                Placeholder.component("state", state));
    }

    /** Q against the corner and F beside it, then the leap while it recharges and a kill streak's strike while it waits. */
    private List<Slot> slots(Player player, boolean blade) {
        var slots = new ArrayList<>(List.of(slot(player, 1, Binding.Q), slot(player, 0, Binding.F)));
        int leap = game.combat().leapCooldown(player);
        if (blade && leap > 0) {
            slots.add(new Slot(Binding.DOUBLE_JUMP, Optional.of(AbilityIcon.LEAP), new State.Cooling(seconds(leap))));
        }
        if (game.strikeReady(player)) {
            slots.add(new Slot(Binding.SHIFT_F, Optional.of(AbilityIcon.ORBITAL_STRIKE), new State.Armed()));
        }
        return slots;
    }

    private Slot slot(Player player, int index, Binding binding) {
        var fighter = game.fighter(player);
        var hero = fighter.hero();
        if (hero.isPresent()) {
            return hero.get().abilities().stream().skip(index).findFirst()
                    .map(ability -> new Slot(binding, Optional.of(ability.icon()), cooling(player.getCooldown(ability.material()))))
                    .orElseGet(() -> new Slot(binding, Optional.empty(), new State.Ready()));
        }
        return game.factionOf(player).flatMap(side -> fighter.abilities(side).stream().skip(index).findFirst()).map(ability -> {
            var charges = game.charges(fighter, ability);
            int stored = charges.stored(game.tick());
            var state = charges.capacity() > 1 && stored > 0 ? new State.Charged(stored, charges.capacity())
                    : cooling(player.getCooldown(ability.material()));
            return new Slot(binding, Optional.of(ability.icon()), state);
        }).orElseGet(() -> new Slot(binding, Optional.empty(), new State.Ready()));
    }

    private static State cooling(int ticks) {
        return ticks > 0 ? new State.Cooling(seconds(ticks)) : new State.Ready();
    }

    private static int seconds(int ticks) {
        return (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
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
