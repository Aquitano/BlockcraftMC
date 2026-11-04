package me.blockcraft.system;

import me.blockcraft.common.message.Messages;
import me.blockcraft.system.StaffButtons.Button;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class StaffButtonsTest {

    @Test
    public void aViewerOnlyGetsTheButtonsTheyMayRun() throws Exception {
        var proxy = new FakeProxy();
        var supporter = proxy.join("Supporter", SystemPermissions.TEAM);
        var moderator = proxy.join("Moderator", SystemPermissions.TEAM, SystemPermissions.GOTO);
        var messages = Messages.load(Files.createTempDirectory("staff-buttons"), () -> getClass().getResourceAsStream("/messages.yml"));
        var buttons = new StaffButtons(messages);

        assertEquals(buttons.of(supporter, "Target", Button.WHOIS), buttons.of(supporter, "Target", Button.GOTO, Button.WHOIS));
        assertNotEquals(buttons.of(moderator, "Target", Button.WHOIS), buttons.of(moderator, "Target", Button.GOTO, Button.WHOIS));
    }
}
