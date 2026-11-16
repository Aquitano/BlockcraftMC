package me.blockcraft.common.network;

import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import me.blockcraft.common.elo.EloFormula;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NetworkConfigTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void anOperatorFileOverridesSingleKeysAndTheEnvironmentOverridesAddresses() throws Exception {
        var directory = folder.getRoot().toPath();
        Files.writeString(directory.resolve("network.yml"), """
                calendar:
                  halloween:
                    from: 10-25
                """);

        var network = NetworkConfig.load(directory, Map.of("BLOCKCRAFT_NETWORK_IP", "Example.test"));

        assertFalse(network.calendar().occasions(LocalDate.of(2026, 10, 24)).contains(Occasion.HALLOWEEN));
        assertTrue(network.calendar().occasions(LocalDate.of(2026, 10, 31)).contains(Occasion.HALLOWEEN));
        assertEquals(new EloFormula(1000, 32), network.elo());
        var mini = MiniMessage.miniMessage();
        assertEquals(mini.deserialize("<blue>Example.test <gray>https://BlockcraftMC.net"),
                mini.deserialize("<primary><ip> <muted><website>", network.tags()));
    }
}
