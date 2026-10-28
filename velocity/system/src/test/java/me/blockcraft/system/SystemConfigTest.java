package me.blockcraft.system;

import me.blockcraft.common.network.GroupState;
import me.blockcraft.system.network.OnDemand;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SystemConfigTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void composeGroupsWakeArchiveEvenWithAnOlderSavedList() throws IOException {
        var file = folder.getRoot().toPath().resolve("config.yml");
        var saved = "on-demand:\n  enabled: true\n  groups: [bedwars]\n";
        Files.writeString(file, saved);
        var config = SystemConfig.load(file, () -> getClass().getResourceAsStream("/config.yml"),
                Map.of("BLOCKCRAFT_ON_DEMAND_GROUPS", "bedwars, ARCHIVE, lobby, , archive"));

        assertTrue(config.onDemand().enabled());
        assertEquals(Set.of("bedwars", "archive"), config.onDemand().settings().groups());
        var servers = new OnDemand(config.onDemand().settings());
        var wake = servers.request("archive", UUID.randomUUID(), Instant.EPOCH).orElseThrow();
        assertEquals(List.of(new OnDemand.Effect.Publish("archive", GroupState.STARTING),
                new OnDemand.Effect.Start("archive")), wake.effects());
        assertEquals(saved, Files.readString(file));
    }

    @Test
    public void withoutAComposeOverrideTheSavedGroupsAndEnableSwitchRemainInControl() throws IOException {
        var file = folder.getRoot().toPath().resolve("config.yml");
        Files.writeString(file, "on-demand:\n  enabled: false\n  groups: [bedwars]\n");
        var config = SystemConfig.load(file, () -> getClass().getResourceAsStream("/config.yml"), Map.of());

        assertFalse(config.onDemand().enabled());
        assertEquals(Set.of("bedwars"), config.onDemand().settings().groups());
    }

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
