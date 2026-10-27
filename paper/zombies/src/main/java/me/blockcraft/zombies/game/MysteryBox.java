package me.blockcraft.zombies.game;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.zombies.arena.ArenaMap;
import me.blockcraft.zombies.config.ZombiesConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Lidded;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A paid roll at a mystery station: a gun rises out of the ender chest and changes until it stops on the one rolled.
 * Only the buyer can take it, and only until it sinks back; one roll per box at a time.
 */
public final class MysteryBox {

    private static final int FIRST_GAP = 2;
    private static final int LAST_GAP = 12;
    private static final int LOWEST_TICK_NOTE = 8;
    private static final int HIGHEST_TICK_NOTE = 18;
    private static final int REVEAL_NOTE = 24;
    private static final double CHEST_TOP = 0.875;
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private final ZombiesGame game;
    private final ZombiesConfig.Mystery config;
    private final Map<ArenaMap.Station, Roll> rolls = new HashMap<>();

    MysteryBox(ZombiesGame game) {
        this.game = game;
        this.config = game.config().mystery();
    }

    public boolean busy(ArenaMap.Station station) {
        return rolls.containsKey(station);
    }

    public void roll(Player buyer, ArenaMap.Station station) {
        var survivor = game.survivor(buyer);
        var choices = Weapon.buyable().stream().filter(weapon -> !survivor.weapons().containsKey(weapon)).toList();
        var weapon = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        rolls.put(station, new Roll(buyer.getUniqueId(), station, weapon, game.tickNumber()));
    }

    /** Answers a click on a box that is rolling and returns true; a box at rest is left to its menu. */
    public boolean click(Player player, ArenaMap.Station station) {
        var roll = rolls.get(station);
        if (roll == null) {
            return false;
        }
        int age = game.tickNumber() - roll.started;
        if (!roll.buyer.equals(player.getUniqueId()) || age >= roll.sinkAt()) {
            game.feedback(player, "mystery.busy");
        } else if (age < config.rollTicks()) {
            game.feedback(player, "mystery.spinning");
        } else {
            rolls.remove(station);
            roll.remove();
            game.weapons().give(player, roll.weapon);
            Sounds.EQUIP.play(player);
        }
        return true;
    }

    /** The station whose rolling gun or click box this is. */
    public Optional<ArenaMap.Station> stationOf(Entity entity) {
        return rolls.values().stream().filter(roll -> roll.display.equals(entity) || roll.box.equals(entity))
                .map(roll -> roll.station).findFirst();
    }

    void tick(int tick) {
        rolls.values().removeIf(roll -> roll.tick(tick - roll.started));
    }

    void clear() {
        rolls.values().forEach(Roll::remove);
        rolls.clear();
    }

    /**
     * The ticks into a roll at which the box shows the next gun: every {@code FIRST_GAP} ticks at first, slowing
     * evenly to about {@code LAST_GAP}, the last one at {@code rollTicks}.
     */
    static List<Integer> swapTicks(int rollTicks) {
        int swaps = Math.max(1, Math.round(2f * rollTicks / (FIRST_GAP + LAST_GAP)));
        double unscaled = swaps * (FIRST_GAP + LAST_GAP) / 2.0;
        var ticks = new ArrayList<Integer>(swaps);
        double elapsed = 0;
        for (int swap = 0; swap < swaps; swap++) {
            elapsed += swaps == 1 ? unscaled : FIRST_GAP + (LAST_GAP - FIRST_GAP) * swap / (double) (swaps - 1);
            ticks.add((int) Math.round(rollTicks * elapsed / unscaled));
        }
        return List.copyOf(ticks);
    }

    /** The gun shown after {@code swap} of {@code swaps} changes, stepping through {@code pool} so the last is {@code rolled}. */
    static Weapon shown(List<Weapon> pool, Weapon rolled, int swap, int swaps) {
        return pool.get(Math.floorMod(pool.indexOf(rolled) - (swaps - swap), pool.size()));
    }

    private final class Roll {

        private final UUID buyer;
        private final ArenaMap.Station station;
        private final Weapon weapon;
        private final int started;
        private final List<Integer> swaps = swapTicks(config.rollTicks());
        private final List<Weapon> pool = Weapon.buyable();
        private final Location centre;
        private final ItemDisplay display;
        private final Interaction box;
        private int swapped;

        Roll(UUID buyer, ArenaMap.Station station, Weapon weapon, int started) {
            this.buyer = buyer;
            this.station = station;
            this.weapon = weapon;
            this.started = started;
            var block = station.location().getBlock();
            centre = block.getLocation().add(0.5, 0.5, 0.5);
            if (block.getBlockData() instanceof Directional chest) {
                centre.setDirection(chest.getFacing().getDirection());
            }
            display = block.getWorld().spawn(centre, ItemDisplay.class, spawned -> {
                spawned.setItemStack(shown(pool, weapon, 0, swaps.size()).model().item().build());
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                spawned.setTransformation(lifted(0));
                spawned.setBrightness(LIT);
                spawned.setPersistent(false);
            });
            box = block.getWorld().spawn(block.getLocation().add(0.5, CHEST_TOP, 0.5), Interaction.class, spawned -> {
                spawned.setInteractionWidth(config.scale());
                spawned.setInteractionHeight((float) (0.5 + config.riseHeight() + config.scale() / 2 - CHEST_TOP));
                spawned.setResponsive(true);
                spawned.setPersistent(false);
            });
            lid(true);
        }

        int sinkAt() {
            return config.rollTicks() + config.takeTicks();
        }

        /** Advances the roll to {@code age} ticks and returns true once it is over and removed. */
        boolean tick(int age) {
            if (age == 1) {
                move(lifted(config.riseHeight()), config.riseTicks());
            }
            while (swapped < swaps.size() && swaps.get(swapped) <= age) {
                swapped++;
                display.setItemStack(shown(pool, weapon, swapped, swaps.size()).model().item().build());
                if (swapped == swaps.size()) {
                    reveal();
                } else {
                    int note = LOWEST_TICK_NOTE + (HIGHEST_TICK_NOTE - LOWEST_TICK_NOTE) * swapped / swaps.size();
                    centre.getWorld().getPlayers().forEach(player -> Sounds.NOTE_BIT.note(player, note, centre));
                }
            }
            if (age == sinkAt()) {
                move(lifted(0), config.sinkTicks());
                buyer().ifPresent(player -> player.sendMessage(game.messages().get("mystery.gone", weaponName())));
            }
            if (age >= sinkAt() + config.sinkTicks()) {
                remove();
                return true;
            }
            return false;
        }

        private void reveal() {
            centre.getWorld().getPlayers().forEach(player -> Sounds.NOTE_PLING.note(player, REVEAL_NOTE, centre));
            buyer().ifPresent(player -> player.sendMessage(game.messages().get("mystery.result", weaponName(),
                    Formatter.number("seconds", config.takeTicks() / 20))));
        }

        private Optional<Player> buyer() {
            return Optional.ofNullable(Bukkit.getPlayer(buyer));
        }

        private TagResolver weaponName() {
            return Placeholder.component("weapon", game.messages().get("weapon." + weapon.key()));
        }

        private void move(Transformation to, int ticks) {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(ticks);
            display.setTransformation(to);
        }

        private Transformation lifted(double height) {
            return new Transformation(new Vector3f(0, (float) height, 0), new Quaternionf(), new Vector3f(config.scale()), new Quaternionf());
        }

        private void lid(boolean open) {
            if (station.location().getBlock().getState() instanceof Lidded lidded) {
                if (open) {
                    lidded.open();
                } else {
                    lidded.close();
                }
            }
        }

        void remove() {
            display.remove();
            box.remove();
            lid(false);
        }
    }
}
