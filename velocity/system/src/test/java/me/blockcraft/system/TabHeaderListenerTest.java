package me.blockcraft.system;

import me.blockcraft.common.message.Messages;
import me.blockcraft.system.listener.TabHeaderListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class TabHeaderListenerTest {
    @Test
    public void unchangedHeadersAreNotSentAndVanishCountsRespectTheViewer() throws Exception {
        var proxy = new FakeProxy();
        var viewer = proxy.join("Viewer");
        var hidden = proxy.join("Hidden");
        var staff = proxy.join("Staff", SystemPermissions.SEE_VANISHED);
        var visibility = new PlayerVisibility(proxy.server, null, null, null);
        var directory = Files.createTempDirectory("tab-header");
        var config = SystemConfig.load(directory.resolve("config.yml"), () -> getClass().getResourceAsStream("/config.yml"));
        var messages = Messages.load(directory, () -> getClass().getResourceAsStream("/messages.yml"));
        var listener = new TabHeaderListener(proxy.server, visibility, config, messages);

        listener.refreshAll();
        listener.refreshAll();
        assertEquals(1, proxy.received(viewer).size());

        visibility.setVanished(hidden, true);
        listener.refreshAll();
        assertEquals(header(messages, config, 2), proxy.received(viewer).getLast());
        assertEquals(header(messages, config, 3), proxy.received(hidden).getLast());
        assertEquals(header(messages, config, 3), proxy.received(staff).getLast());
        assertEquals(1, proxy.received(staff).size());

        proxy.leave(hidden);
        listener.refreshAll();
        assertEquals(2, proxy.received(viewer).size());
        assertEquals(header(messages, config, 2), proxy.received(staff).getLast());
    }

    private Component header(Messages messages, SystemConfig config, int online) {
        return Component.join(JoinConfiguration.newlines(), messages.getLines("tab.header",
                Formatter.number("online", online), Formatter.number("max", config.maxPlayers())));
    }
}
