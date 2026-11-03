package me.blockcraft.sternenfront.game;

import me.blockcraft.api.world.BlocksChangedEvent;
import me.blockcraft.sternenfront.arena.FrontMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Battlefield {

    private enum Activation { ACTIVATED, COOLDOWN, BLOCKED }

    private static final class State {
        private final FrontMap.ControlSite site;
        private final ControlCycle cycle = new ControlCycle();
        private final Map<Block, BlockData> originals = new LinkedHashMap<>();
        private final List<TextDisplay> labels = new ArrayList<>();
        private boolean closed;

        private State(FrontMap.ControlSite site) {
            this.site = site;
            site.panels().forEach(block -> originals.put(block, block.getBlockData()));
        }
    }

    private final SternenfrontGame game;
    private final List<State> states = new ArrayList<>();

    public Battlefield(SternenfrontGame game) {
        this.game = game;
    }

    public void start() {
        for (var site : game.map().controls()) {
            var state = new State(site);
            for (var button : site.switches()) {
                state.labels.add(button.getWorld().spawn(button.getLocation().add(0.5, 1, 0.5), TextDisplay.class, label -> {
                    label.setPersistent(false);
                    label.setBillboard(Display.Billboard.CENTER);
                    label.setSeeThrough(false);
                    label.setBrightness(new Display.Brightness(15, 15));
                    label.setShadowed(true);
                }));
            }
            states.add(state);
            draw(state, game.tick());
        }
    }

    public boolean use(Player player, Block button) {
        var found = states.stream().filter(state -> state.site.switches().contains(button)).findFirst();
        if (found.isEmpty()) {
            return false;
        }
        var state = found.get();
        if (!game.onField(player) || player.isInsideVehicle() || !player.getWorld().equals(button.getWorld())
                || player.getEyeLocation().distanceSquared(button.getLocation().add(0.5, 0.5, 0.5)) > 25) {
            return true;
        }
        var eye = player.getEyeLocation();
        var toward = button.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector());
        if (toward.lengthSquared() > 0.001 && eye.getWorld().rayTraceBlocks(eye, toward.clone().normalize(), toward.length(),
                FluidCollisionMode.NEVER, true) != null) {
            return true;
        }
        var activation = activate(state);
        if (activation == Activation.COOLDOWN) {
            player.sendMessage(game.messages().get("battlefield.cooldown", Formatter.number("seconds",
                    (state.cycle.remaining(game.tick()) + 19) / 20)));
            return true;
        }
        if (activation == Activation.BLOCKED) {
            player.sendMessage(game.messages().get("battlefield.blocked"));
            return true;
        }
        button.getWorld().playSound(button.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8f, 0.8f);
        player.sendMessage(game.messages().get("battlefield.activated", Placeholder.component("name",
                game.messages().get("battlefield." + key(state) + ".name"))));
        draw(state, game.tick());
        return true;
    }

    private Activation activate(State state) {
        if (state.cycle.phase(game.tick()) != ControlCycle.Phase.READY) {
            return Activation.COOLDOWN;
        }
        if (blocked(state)) {
            return Activation.BLOCKED;
        }
        String path = "battlefield." + key(state) + ".";
        var config = game.plugin().getConfig();
        int warning = state.site.control() == FrontMap.Control.VENT
                ? Math.max(1, config.getInt(path + "warning-seconds", 1)) * 20 : 0;
        int duration = Math.clamp(config.getInt(path + "seconds", state.site.control() == FrontMap.Control.VENT ? 6 : 8), 1, 30) * 20;
        int cooldown = Math.max(1, config.getInt(path + "cooldown-seconds", 20)) * 20;
        state.cycle.trigger(game.tick(), warning, duration, cooldown);
        if (state.site.control() != FrontMap.Control.VENT) {
            state.originals.keySet().forEach(block -> block.setType(closedMaterial(state), false));
            changed(state);
            state.closed = true;
        }
        return Activation.ACTIVATED;
    }

    private static boolean blocked(State state) {
        return state.originals.entrySet().stream().anyMatch(entry -> {
            var block = entry.getKey();
            var at = block.getLocation();
            var box = new BoundingBox(at.getX(), at.getY(), at.getZ(), at.getX() + 1, at.getY() + 1, at.getZ() + 1);
            return !block.getBlockData().equals(entry.getValue()) || block.getWorld().getNearbyEntities(box).stream()
                    .filter(LivingEntity.class::isInstance)
                    .anyMatch(entity -> entity.getBoundingBox().overlaps(box));
        });
    }

    public void tick(long tick) {
        for (var state : states) {
            var phase = state.cycle.phase(tick);
            if (state.closed && phase != ControlCycle.Phase.ACTIVE) {
                open(state);
            }
            if (tick % 5 == 0) {
                draw(state, tick);
                if (state.site.control() == FrontMap.Control.VENT
                        && (phase == ControlCycle.Phase.WARNING || phase == ControlCycle.Phase.ACTIVE)) {
                    var at = state.site.center();
                    at.getWorld().spawnParticle(phase == ControlCycle.Phase.WARNING ? Particle.SMOKE : Particle.CLOUD,
                            at.clone().add(0, 0.4, 0), 20, 2, 0.2, 1, phase == ControlCycle.Phase.WARNING ? 0.01 : 0.12);
                }
            }
            if (state.site.control() == FrontMap.Control.VENT && phase == ControlCycle.Phase.ACTIVE && tick % 20 == 0) {
                vent(state);
            }
        }
    }

    private void vent(State state) {
        var at = state.site.center();
        double damage = Math.max(0, game.plugin().getConfig().getDouble("battlefield.vent.damage", 2));
        for (var target : game.targets()) {
            var here = target.getLocation();
            if (!here.getWorld().equals(at.getWorld()) || Math.abs(here.getX() - at.getX()) > 3
                    || Math.abs(here.getZ() - at.getZ()) > 2 || here.getY() < at.getY() - 1 || here.getY() > at.getY() + 3
                    || game.isProtected(target)) {
                continue;
            }
            target.damage(damage, DamageSource.builder(DamageType.MAGIC).withDamageLocation(at).build());
            if (!target.isDead()) {
                target.setVelocity(target.getVelocity().setY(0.65));
            }
        }
        at.getWorld().playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.7f);
    }

    private void draw(State state, long tick) {
        String phase = state.cycle.phase(tick).name().toLowerCase(Locale.ROOT);
        var label = game.messages().get("battlefield.label", Placeholder.component("name",
                        game.messages().get("battlefield." + key(state) + ".name")),
                Placeholder.component("state", game.messages().get("battlefield.state." + phase,
                        Formatter.number("seconds", (state.cycle.remaining(tick) + 19) / 20))));
        state.labels.forEach(display -> display.text(label));
    }

    private static String key(State state) {
        return state.site.control().name().toLowerCase(Locale.ROOT);
    }

    private static Material closedMaterial(State state) {
        return state.site.control() == FrontMap.Control.BULKHEAD ? Material.IRON_BLOCK : Material.SPRUCE_PLANKS;
    }

    private static void changed(State state) {
        if (!state.originals.isEmpty()) {
            Bukkit.getPluginManager().callEvent(new BlocksChangedEvent(List.copyOf(state.originals.keySet())));
        }
    }

    private static void open(State state) {
        state.originals.forEach((block, original) -> {
            if (block.getType() == closedMaterial(state)) {
                block.setBlockData(original, false);
            }
        });
        changed(state);
        state.closed = false;
    }

    public void clear() {
        for (var state : states) {
            if (state.closed) {
                open(state);
            }
            state.labels.forEach(TextDisplay::remove);
        }
        states.clear();
    }
}
