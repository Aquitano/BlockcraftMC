package me.blockcraft.lobby.activity;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StarWishesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void validWishesArePrivateAndClaimOneDiscoveryPerWindow() throws Exception {
        var clock = new AtomicLong();
        var discoveries = new ArrayList<UUID>();
        var wishes = new StarWishes(messages(), List.of("ich wünsche mir"),
                player -> discoveries.add(player.getUniqueId()), clock::get);
        var player = player();
        wishes.open(Duration.ofSeconds(15));
        for (int attempt = 0; attempt < 2; attempt++) {
            var event = chat(player, "ich wünsche mir einen goldenen Karpfen");
            wishes.onChat(event);
            assertTrue(event.isCancelled());
            assertTrue(event.viewers().isEmpty());
        }
        assertEquals(List.of(player.getUniqueId()), discoveries);

        clock.set(Duration.ofSeconds(15).toNanos());
        var late = chat(player, "ich wünsche mir etwas anderes");
        wishes.onChat(late);
        assertTrue(late.isCancelled());
        assertTrue(late.viewers().isEmpty());
        assertEquals(1, discoveries.size());
    }

    @Test
    public void lateWishesStayPrivateWhileNormalChatKeepsItsAudience() throws Exception {
        var discoveries = new ArrayList<UUID>();
        var wishes = new StarWishes(messages(), List.of("ich wünsche mir"),
                player -> discoveries.add(player.getUniqueId()), () -> 0);
        var player = player();
        var normal = chat(player, "Hallo zusammen");
        wishes.onChat(normal);
        assertFalse(normal.isCancelled());
        assertEquals(2, normal.viewers().size());

        var late = chat(player, "ICH WÜNSCHE MIR einen geheimen Ort");
        wishes.onChat(late);
        assertTrue(late.isCancelled());
        assertTrue(late.viewers().isEmpty());
        assertTrue(discoveries.isEmpty());
    }

    private Messages messages() throws Exception {
        return Messages.load(folder.newFolder().toPath(), () -> new ByteArrayInputStream("""
                shooting-star:
                  wish: 'Privat'
                  wish-late: 'Vorbei, aber privat'
                """.getBytes(StandardCharsets.UTF_8)));
    }

    private static Player player() {
        var id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> "Builder";
                    case "sendMessage" -> null;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == arguments[0];
                    default -> throw new AssertionError(method.getName());
                });
    }

    private static AsyncChatEvent chat(Player player, String text) {
        var content = Component.text(text);
        var audience = new HashSet<Audience>(List.of(player, Audience.empty()));
        return new AsyncChatEvent(true, player, audience, ChatRenderer.viewerUnaware((source, name, message) -> message),
                content, content, SignedMessage.system(text, content));
    }
}
