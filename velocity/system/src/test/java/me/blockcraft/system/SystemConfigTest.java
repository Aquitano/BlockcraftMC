package me.blockcraft.system;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SystemConfigTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void changesMadeInGameLeaveTheRestOfTheFileAsItWas() throws IOException {
        var file = folder.getRoot().toPath().resolve("config.yml");
        Files.writeString(file, """
                # Edited in-game by /setmax and /motd.
                max-players: 100

                motd:
                  # Shown when nothing random applies.
                  default: "&cBlockcraftMC.net"
                  random:
                    - "&7Eins"

                # Never banned by /strafe.
                unbannable:
                  - Aquitano
                """);

        var config = SystemConfig.load(file, () -> getClass().getResourceAsStream("/config.yml"));
        config.setMaxPlayers(250);
        config.setDefaultMotd("&cNeu");
        config.addRandomMotd("&7Zwei");

        assertEquals("""
                # Edited in-game by /setmax and /motd.
                max-players: 250

                motd:
                  # Shown when nothing random applies.
                  default: "&cNeu"
                  random:
                    - "&7Eins"
                    - "&7Zwei"

                # Never banned by /strafe.
                unbannable:
                  - Aquitano
                """, Files.readString(file));
        assertFalse(Files.exists(file.resolveSibling("config.yml.tmp")));
    }
}
