package me.blockcraft.core.staff;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class TrollModeTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void effectsExpireAndEitherParticipantLeavingCleansThemUp() {
        var fixture = new Fixture();
        var actor = new Target();
        var first = new Target();
        assertTrue(fixture.mode.apply(actor.player, first.player, TrollMode.Trick.LEVITATE));
        assertEquals(120, first.potions.get(PotionEffectType.LEVITATION).getDuration());
        fixture.tasks.getFirst().run();
        assertTrue(first.potions.isEmpty());

        var second = new Target();
        var third = new Target();
        assertTrue(fixture.mode.apply(actor.player, second.player, TrollMode.Trick.SLOW));
        assertTrue(fixture.mode.apply(actor.player, third.player, TrollMode.Trick.BLIND));
        fixture.mode.onQuit(new PlayerQuitEvent(second.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertTrue(second.potions.isEmpty());
        assertFalse(third.potions.isEmpty());
        fixture.mode.onQuit(new PlayerQuitEvent(actor.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertTrue(third.potions.isEmpty());
        assertEquals(3, fixture.cancelled);
    }

    @Test
    public void existingAndReplacementPotionsSurviveTrollCleanup() {
        var fixture = new Fixture();
        var actor = new Target();
        var target = new Target();
        var existing = new PotionEffect(PotionEffectType.SLOWNESS, 600, 2);
        target.potions.put(existing.getType(), existing);
        assertFalse(fixture.mode.apply(actor.player, target.player, TrollMode.Trick.SLOW));
        assertEquals(existing, target.potions.get(existing.getType()));

        assertTrue(fixture.mode.apply(actor.player, target.player, TrollMode.Trick.BLIND));
        var original = target.potions.get(PotionEffectType.BLINDNESS);
        var replacement = new PotionEffect(PotionEffectType.BLINDNESS, 600, 2);
        target.potions.put(replacement.getType(), replacement);
        fixture.mode.onPotionChange(new EntityPotionEffectEvent(target.player, original, replacement, null,
                EntityPotionEffectEvent.Cause.PLUGIN, EntityPotionEffectEvent.Action.CHANGED, true));
        fixture.mode.close();
        assertEquals(replacement, target.potions.get(PotionEffectType.BLINDNESS));
        assertEquals(existing, target.potions.get(PotionEffectType.SLOWNESS));
        assertEquals(1, fixture.cancelled);
    }

    @Test
    public void aWeakerPotionQueuedElsewhereAlsoRelinquishesCleanup() {
        var fixture = new Fixture();
        var target = new Target();
        assertTrue(fixture.mode.apply(new Target().player, target.player, TrollMode.Trick.SLOW));
        var original = target.potions.get(PotionEffectType.SLOWNESS);
        fixture.mode.onPotionChange(new EntityPotionEffectEvent(target.player, original,
                new PotionEffect(PotionEffectType.SLOWNESS, 600, 0), null,
                EntityPotionEffectEvent.Cause.PLUGIN, EntityPotionEffectEvent.Action.CHANGED, false));
        fixture.mode.close();
        assertEquals(original, target.potions.get(PotionEffectType.SLOWNESS));
        assertEquals(1, fixture.cancelled);
    }

    @Test
    public void cooldownAppliesAcrossActorsAndStopDoesNotRemoveOtherPotions() {
        var fixture = new Fixture();
        var target = new Target();
        var unrelated = new PotionEffect(PotionEffectType.SPEED, 600, 1);
        target.potions.put(unrelated.getType(), unrelated);
        assertTrue(fixture.mode.apply(new Target().player, target.player, TrollMode.Trick.LEVITATE));
        assertFalse(fixture.mode.apply(new Target().player, target.player, TrollMode.Trick.BLIND));
        fixture.mode.stop(target.player);
        assertEquals(Map.of(PotionEffectType.SPEED, unrelated), target.potions);

        var other = new Target();
        assertTrue(fixture.mode.apply(new Target().player, other.player, TrollMode.Trick.NAUSEA));
        fixture.mode.close();
        assertTrue(other.potions.isEmpty());
    }

    @Test
    public void lagPullsBackEveryOtherStepKeepingTheViewAndEndsAfterSixSeconds() {
        var fixture = new Fixture();
        var target = new Target();
        assertTrue(fixture.mode.apply(new Target().player, target.player, TrollMode.Trick.LAG));
        var step = fixture.tasks.getFirst();
        step.run();
        target.location = new Location(null, 3, 64, 0, 90, 10);
        step.run();
        assertEquals(new Location(null, 0, 64, 0, 90, 10), target.location);

        for (int run = 2; run < 11; run++) step.run();
        assertEquals(0, fixture.cancelled);
        step.run();
        assertEquals(1, fixture.cancelled);
    }

    @Test
    public void fakeOpReadsAsTheClientsOwnOperatorNotice() {
        var fixture = new Fixture();
        var target = new Target();
        assertTrue(fixture.mode.apply(new Target().player, target.player, TrollMode.Trick.FAKEOP));
        var notice = (TranslatableComponent) target.chat.getFirst();
        assertEquals("chat.type.admin", notice.key());
        assertTrue(notice.hasDecoration(TextDecoration.ITALIC));
        var success = (TranslatableComponent) notice.arguments().get(1).asComponent();
        assertEquals("commands.op.success", success.key());
        assertEquals("Target", ((TextComponent) success.arguments().getFirst().asComponent()).content());
    }

    private final class Fixture {
        final List<Runnable> tasks = new ArrayList<>();
        int cancelled;
        final BukkitScheduler scheduler = fake(BukkitScheduler.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "runTaskLater" -> assertEquals(120L, args[2]);
                case "runTaskTimer" -> assertArrayEquals(new Object[]{10L, 10L}, new Object[]{args[2], args[3]});
                default -> throw new AssertionError(method.getName());
            }
            tasks.add((Runnable) args[1]);
            return fake(BukkitTask.class, (task, called, values) -> {
                assertEquals("cancel", called.getName());
                cancelled++;
                return null;
            });
        });
        final Server server = fake(Server.class, (proxy, method, args) -> scheduler);
        final Plugin plugin = fake(Plugin.class, (proxy, method, args) -> server);
        final TrollMode mode = new TrollMode(plugin, messages());
    }

    private static final class Target {
        final UUID id = UUID.randomUUID();
        final Map<PotionEffectType, PotionEffect> potions = new HashMap<>();
        final List<Component> chat = new ArrayList<>();
        Location location = new Location(null, 0, 64, 0, 0, 0);
        final Player player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "hasPotionEffect" -> potions.containsKey(args[0]);
            case "addPotionEffect" -> {
                var effect = (PotionEffect) args[0];
                potions.put(effect.getType(), effect);
                yield true;
            }
            case "removePotionEffect" -> { potions.remove(args[0]); yield null; }
            case "getName" -> "Target";
            case "sendMessage" -> { chat.add((Component) args[0]); yield null; }
            case "getLocation" -> location.clone();
            case "teleport" -> { location = ((Location) args[0]).clone(); yield true; }
            default -> throw new AssertionError(method.getName());
        });
    }

    private Messages messages() {
        try {
            return Messages.load(folder.newFolder().toPath(), () -> TrollModeTest.class.getResourceAsStream("/messages.yml"),
                    Placeholder.parsed("muted", "<gray>"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static <T> T fake(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
