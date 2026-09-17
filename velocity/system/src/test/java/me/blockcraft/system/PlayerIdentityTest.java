package me.blockcraft.system;

import me.blockcraft.common.nick.Nick;
import me.blockcraft.system.report.Reports;
import org.junit.Test;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlayerIdentityTest {

    @Test
    public void suggestionsAndLookupRespectNickAndVanish() {
        var proxy = new FakeProxy();
        var viewer = proxy.join("Viewer");
        var target = proxy.join("Account");
        var visibility = new PlayerVisibility(proxy.server, null, null, null);
        var lookup = new PlayerLookup(proxy.server, visibility, null);

        visibility.setNick(target, Optional.of(new Nick("Nickname", "Notch", "default")));
        assertEquals(List.of("Viewer", "Nickname"), visibility.namesFor(viewer).toList());
        assertEquals(target.getUniqueId(), lookup.findVisible(viewer, "Nickname").orElseThrow().uuid());
        assertTrue(lookup.findVisible(viewer, "Account").isEmpty());

        visibility.setVanished(target, true);
        assertEquals(List.of("Viewer"), visibility.namesFor(viewer).toList());
        assertTrue(lookup.findVisible(viewer, "Nickname").isEmpty());
        assertTrue(lookup.findVisible(viewer, "Account").isEmpty());

        var staff = proxy.join("Staff", SystemPermissions.SEE_NICKS, SystemPermissions.SEE_VANISHED);
        assertTrue(visibility.namesFor(staff).toList().contains("Account"));
        assertEquals(target.getUniqueId(), lookup.findVisible(staff, "Account").orElseThrow().uuid());
    }

    @Test
    public void reportConfigImportKeepsOriginalsAndNeverOverwrites() throws Exception {
        var root = Files.createTempDirectory("reports-import");
        var source = Files.createDirectory(root.resolve("old"));
        var destination = root.resolve("new");
        try {
            Files.writeString(source.resolve("config.yml"), "report-cooldown-seconds: 15");
            Files.writeString(source.resolve("ignored-staff.yml"), "muted: []");
            Reports.importLegacy(source, destination);
            assertEquals("report-cooldown-seconds: 15", Files.readString(destination.resolve("config.yml")));

            Files.writeString(destination.resolve("config.yml"), "report-cooldown-seconds: 30");
            Reports.importLegacy(source, destination);
            assertEquals("report-cooldown-seconds: 30", Files.readString(destination.resolve("config.yml")));
            assertTrue(Files.exists(source.resolve("ignored-staff.yml")));
        } finally {
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }
}
