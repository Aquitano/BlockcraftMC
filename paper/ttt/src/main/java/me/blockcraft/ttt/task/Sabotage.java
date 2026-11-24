package me.blockcraft.ttt.task;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.BlockChanges;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.arena.TaskLayout;
import me.blockcraft.ttt.config.TttConfig;
import me.blockcraft.ttt.game.Channels;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The traitors' sabotage, one at a time with a rest between. A blackout puts the map's lights out and darkens
 * everyone but the traitors until it is mended at the generator or runs out; shut doors lock every door of the town
 * for a few seconds; a broken tester tests nobody until it is mended at its button or runs out. Anyone can mend.
 */
public final class Sabotage {

    private static final int DARKNESS_TICKS = 50;

    public enum Kind {
        BLACKOUT(Material.REDSTONE_LAMP), DOORS(Material.IRON_DOOR), TESTER(Material.REDSTONE_TORCH);

        private final Material icon;

        Kind(Material icon) {
            this.icon = icon;
        }

        public Material icon() {
            return icon;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final TttConfig.Sabotage config;
    private final Messages messages;
    private final TaskLayout layout;
    private final Block testerButton;
    private final Channels channels;
    private final Predicate<Player> traitor;
    private final Supplier<List<Player>> alive;
    private final Map<Block, BlockData> darkened = new HashMap<>();
    private final Set<Block> locked = new HashSet<>();
    private final BossBar bar;
    private final Runnable changed;
    private Optional<Kind> active = Optional.empty();
    private int length;
    private int left;
    private int resting;

    Sabotage(TttConfig.Sabotage config, Messages messages, TaskLayout layout, Block testerButton, Channels channels,
             Predicate<Player> traitor, Supplier<List<Player>> alive, BossBar bar, Runnable changed) {
        this.config = config;
        this.messages = messages;
        this.layout = layout;
        this.testerButton = testerButton;
        this.channels = channels;
        this.traitor = traitor;
        this.alive = alive;
        this.bar = bar;
        this.changed = changed;
    }

    Optional<Kind> active() {
        return active;
    }

    /** Seconds until the next sabotage, counting the one still running. */
    public int secondsUntilReady() {
        return active.isPresent() ? left + config.cooldownSeconds() : resting;
    }

    public void trigger(Player saboteur, Kind kind) {
        if (secondsUntilReady() > 0) {
            BlockcraftApi.get().notifications().feedback(saboteur,
                    messages.get("tasks.sabotage.resting", Formatter.number("seconds", secondsUntilReady())));
            Sounds.MENU_DENY.play(saboteur);
            return;
        }
        active = Optional.of(kind);
        switch (kind) {
            case BLACKOUT -> {
                left = config.blackoutSeconds();
                layout.lights().forEach(light -> {
                    darkened.put(light, light.getBlockData());
                    BlockChanges.set(light, unlit(light.getBlockData()));
                });
                darken();
            }
            case DOORS -> {
                left = config.doorsSeconds();
                layout.doors().forEach(door -> {
                    shut(door);
                    shut(door.getRelative(BlockFace.UP));
                });
            }
            case TESTER -> left = config.testerSeconds();
        }
        length = left;
        showBar();
        Bukkit.getOnlinePlayers().forEach(player -> player.showBossBar(bar));
        var title = Title.title(messages.get("tasks.sabotage.started." + kind.key() + ".title"),
                messages.get("tasks.sabotage.started." + kind.key() + ".hint"));
        Bukkit.getOnlinePlayers().forEach(player -> BlockcraftApi.get().notifications().important(player, title));
        Sounds.NOTIFY.playToAll();
        changed.run();
    }

    public boolean locked(Block block) {
        return locked.contains(block);
    }

    /** Where the running sabotage is mended, if it can be. */
    Optional<Block> repairSpot() {
        return active.flatMap(kind -> switch (kind) {
            case BLACKOUT -> Optional.of(layout.generator());
            case TESTER -> Optional.of(testerButton);
            case DOORS -> Optional.empty();
        });
    }

    /** A click on the generator in a blackout, or on the broken tester's button, starts mending it. */
    boolean mend(Player player, Block block) {
        var kind = active.filter(running -> running == Kind.BLACKOUT && block.equals(layout.generator())
                || running == Kind.TESTER && block.equals(testerButton));
        if (kind.isEmpty()) {
            return false;
        }
        if (!channels.busy(player)) {
            var label = messages.get("tasks.sabotage.mending." + kind.get().key());
            channels.start(player, block.getLocation().add(0.5, 0.5, 0.5), config.repairSeconds(), label, () -> {
                if (active.equals(kind)) {
                    end();
                }
            });
        }
        return true;
    }

    /** Once a second: the blackout keeps everyone in the dark, and a sabotage that runs out ends. */
    void tick() {
        if (active.isPresent()) {
            if (active.get() == Kind.BLACKOUT) {
                darken();
            }
            if (--left <= 0) {
                end();
            } else {
                showBar();
            }
        } else if (resting > 0) {
            resting--;
        }
    }

    void stop() {
        active.ifPresent(kind -> restore());
        active = Optional.empty();
        Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(bar));
    }

    private void showBar() {
        var kind = active.orElseThrow();
        bar.color(BossBar.Color.RED).overlay(BossBar.Overlay.PROGRESS);
        bar.name(messages.get("tasks.sabotage.bar", Placeholder.component("sabotage",
                messages.get("tasks.sabotage.started." + kind.key() + ".title")), Formatter.number("seconds", left)));
        bar.progress(Math.clamp(left / (float) Math.max(1, length), 0f, 1f));
    }

    private void end() {
        var kind = active.orElseThrow();
        restore();
        active = Optional.empty();
        resting = config.cooldownSeconds();
        var ended = messages.get("tasks.sabotage.ended." + kind.key());
        Bukkit.getOnlinePlayers().forEach(player -> BlockcraftApi.get().notifications().reward(player, ended));
        changed.run();
    }

    private void restore() {
        BlockChanges.set(Map.copyOf(darkened));
        darkened.clear();
        locked.clear();
        alive.get().forEach(player -> player.removePotionEffect(PotionEffectType.DARKNESS));
    }

    private void darken() {
        alive.get().stream().filter(traitor.negate()).forEach(player ->
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, DARKNESS_TICKS, 0, false, false, false)));
    }

    private void shut(Block half) {
        if (half.getBlockData() instanceof Openable door) {
            door.setOpen(false);
            BlockChanges.set(half, door);
            locked.add(half);
        }
    }

    private static BlockData unlit(BlockData light) {
        if (light instanceof Lightable lightable) {
            var off = (Lightable) lightable.clone();
            off.setLit(false);
            return off;
        }
        return Material.AIR.createBlockData();
    }
}
