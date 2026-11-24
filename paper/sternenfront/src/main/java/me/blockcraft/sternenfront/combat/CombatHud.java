package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.HudPanel.Tint;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.api.scene.Scenes;
import me.blockcraft.sternenfront.combat.AbilityPanel.Binding;
import me.blockcraft.sternenfront.combat.AbilityPanel.Charges;
import me.blockcraft.sternenfront.combat.AbilityPanel.Slot;
import me.blockcraft.sternenfront.combat.AbilityPanel.State;
import me.blockcraft.sternenfront.game.AbilityIcon;
import me.blockcraft.sternenfront.game.CombatFeedback;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Heat;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Hero;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/** The heat or guard meter on the action bar, and the ability tiles the pack moves into the bottom-right corner. */
public final class CombatHud {

    private final SternenfrontGame game;
    /** Each ability's name as the tiles spell it, parsed once instead of on every redraw. */
    private final Map<String, String> names = new HashMap<>();

    private record Meter(boolean blade, boolean red, String state, int seconds, int cells, Optional<Heat.Window> window) { }
    private record MeterDisplay(Component text, int width) { }
    private record Abilities(List<Slot> slots, Tint tint) { }

    private static final class Display {
        Meter meter;
        MeterDisplay meterDisplay;
        Abilities abilities;
        Component abilityDisplay = Component.empty();
    }

    private final Map<UUID, Display> displays = new HashMap<>();
    private final int fixedWidth;

    public CombatHud(SternenfrontGame game) {
        this.game = game;
        fixedWidth = UiFont.width(hud(Component.empty(), Component.empty(), Component.empty()))
                + METER_ICON_ADVANCE + METER_ADVANCE;
    }

    /** Wide enough for the longest state, so the line does not slide when one appears. */
    static final int STATE_WIDTH = 41;
    /** A meter glyph's width with its spacing, which each vent window glyph laid over it shares. */
    static final int METER_ADVANCE = 42;
    /** The flame's and the shield's width with their spacing. */
    static final int METER_ICON_ADVANCE = 11;
    private static final List<Binding> BINDINGS = List.of(Binding.F, Binding.Q);
    private static final char HEAT_ICON = '\uE221';
    private static final char GUARD_ICON = '\uE222';
    /** The meter with each number of its cells lit, from none up. */
    private static final char FIRST_METER = '\uE240';
    /** A vent window's bracket over each cell of the meter's lower half. */
    private static final char FIRST_VENT_CELL = '\uE260';

    public void show(Player player, Guard guard) {
        if (Scenes.isViewing(player)) return;
        var fighter = game.fighter(player);
        boolean blade = fighter.hero().filter(Hero::bladed).isPresent();
        var heat = fighter.heat();
        boolean venting = heat.venting(game.tick());
        boolean hot = !blade && game.feedback().hot(player);
        long tick = game.tick();
        String state = blade ? guard.broken(tick) ? "combat.broken" : guard.raised()
                        ? "combat.guarding" : "combat.guard" : venting ? "combat.venting" : hot ? "combat.hot" : "combat.heat";
        var meter = new Meter(blade, hot || venting, state, seconds(heat.ventTicks(tick)),
                blade ? cells(guard.level()) : venting ? heat.litCells(tick) : cells(heat.level()),
                !blade && venting ? heat.window(tick) : Optional.empty());
        var display = displays.computeIfAbsent(player.getUniqueId(), ignored -> new Display());
        if (!meter.equals(display.meter)) {
            display.meterDisplay = render(meter);
            display.meter = meter;
        }
        if (BlockcraftApi.get().resourcePack().applied(player)) {
            var abilities = new Abilities(slots(player, blade), game.factionOf(player).map(Faction::text).map(Tint::of).orElse(Tint.WHITE));
            if (!abilities.equals(display.abilities)) {
                display.abilityDisplay = AbilityPanel.render(abilities.slots(), abilities.tint());
                display.abilities = abilities;
            }
        } else {
            display.abilities = null;
            display.abilityDisplay = Component.empty();
        }
        game.feedback().show(player, new CombatFeedback.ActionBar(display.meterDisplay.text(),
                display.meterDisplay.width(), display.abilityDisplay));
    }

    void forget(Player player) {
        displays.remove(player.getUniqueId());
    }

    void clear() {
        displays.clear();
    }

    private MeterDisplay render(Meter meter) {
        var state = game.messages().get(meter.state(), Formatter.number("seconds", meter.seconds()));
        var window = meter.window().map(cells -> IntStream.range(cells.low(), cells.high())
                .mapToObj(cell -> UiFont.over((char) (FIRST_VENT_CELL + cell), METER_ADVANCE))
                .reduce(Component.empty(), Component::append)).orElse(Component.empty());
        var bar = meter(meter.cells());
        if (meter.state().equals("combat.venting")) bar = bar.append(window.color(NamedTextColor.AQUA));
        bar = bar.color(meter.red() ? NamedTextColor.RED : NamedTextColor.GOLD);
        return new MeterDisplay(hud(UiFont.glyph(meter.blade() ? GUARD_ICON : HEAT_ICON), bar,
                UiFont.leftAligned(state, STATE_WIDTH)), fixedWidth + Math.max(STATE_WIDTH, UiFont.width(state)));
    }

    private Component hud(Component icon, Component bar, Component state) {
        return game.messages().get("combat.hud", Placeholder.component("meter_icon", icon), Placeholder.component("meter", bar),
                Placeholder.component("state", state));
    }

    /** F against the corner and Q above it, then the leap of a bladed hero and a kill streak's strike while it waits. */
    private List<Slot> slots(Player player, boolean blade) {
        var fighter = game.fighter(player);
        var slots = new ArrayList<Slot>();
        fighter.hero().ifPresentOrElse(hero -> hero.abilities().stream().limit(2).forEach(ability -> {
            int total = game.heroes().config().cooldownTicks(ability);
            slots.add(new Slot(BINDINGS.get(slots.size()), ability.icon(), name("hero.ability." + ability.key() + ".name"),
                    cooling(player.getCooldown(ability.material()), total), Charges.ONE));
        }), () -> game.factionOf(player).ifPresent(side -> fighter.abilities(side).stream().limit(2).forEach(ability -> {
            var charges = fighter.charges(ability);
            int stored = charges.stored(game.tick());
            var state = stored > 0 ? new State.Ready() : cooling(player.getCooldown(ability.material()), game.config().cooldownTicks(ability));
            slots.add(new Slot(BINDINGS.get(slots.size()), ability.icon(), name(ability.messageKey(side) + ".name"), state,
                    new Charges(stored, charges.capacity())));
        })));
        if (blade) {
            slots.add(new Slot(Binding.DOUBLE_JUMP, AbilityIcon.LEAP, name("hero.leap.name"),
                    cooling(game.combat().leapCooldown(player), game.combat().leapTicks(player)), Charges.ONE));
        }
        if (game.strikeReady(player)) {
            slots.add(new Slot(Binding.SHIFT_F, AbilityIcon.ORBITAL_STRIKE, name("hero.ability.orbital-strike.name"), new State.Armed(),
                    Charges.ONE));
        }
        return slots;
    }

    private String name(String key) {
        return names.computeIfAbsent(key, ignored -> PlainTextComponentSerializer.plainText().serialize(game.messages().get(key)));
    }

    private static State cooling(int ticks, int total) {
        return ticks > 0 ? new State.Cooling(seconds(ticks), 1 - (double) ticks / Math.max(ticks, total)) : new State.Ready();
    }

    private static int seconds(int ticks) {
        return (ticks + SternenfrontGame.TICKS_PER_SECOND - 1) / SternenfrontGame.TICKS_PER_SECOND;
    }

    private static int cells(double level) {
        return (int) Math.round(Math.clamp(level, 0, 1) * Heat.METER_CELLS);
    }

    private static Component meter(int cells) {
        return UiFont.glyph((char) (FIRST_METER + Math.clamp(cells, 0, Heat.METER_CELLS)));
    }
}
