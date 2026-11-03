package me.blockcraft.ttt.arena;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.helpers.NOPLogger;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class MapVoteTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void aFailedSelectedMapUsesAndAnnouncesTheBuiltInFallback() throws Exception {
        var announcements = new ArrayList<Component>();
        var server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getOnlinePlayers" -> List.of();
                    case "broadcast" -> {
                        announcements.add((Component) args[0]);
                        yield 0;
                    }
                    default -> throw new AssertionError(method.getName());
                });
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        var previous = serverField.get(null);
        try {
            serverField.set(null, server);
            var messages = Messages.load(temporary.newFolder().toPath(), () -> new ByteArrayInputStream(
                    "vote:\n  result: '<map>'\n".getBytes(StandardCharsets.UTF_8)));
            var fallback = new Town("Fallback", null, List.of(), null, null, List.of(), Optional.empty());
            var vote = new MapVote(messages, List.of(
                    new MapVote.Choice("fallback", "Fallback", List.of(), Material.OAK_SAPLING, () -> fallback),
                    new MapVote.Choice("broken", "Broken", List.of(), Material.PAPER, () -> {
                        throw new IllegalStateException("unreadable world");
                    })), NOPLogger.NOP_LOGGER);
            vote.force("broken");

            assertSame(fallback, vote.decide());
            assertEquals(List.of(Component.text("Fallback")), announcements);
        } finally {
            serverField.set(null, previous);
        }
    }
}
