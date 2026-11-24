package me.blockcraft.core.command;

import org.bukkit.GameMode;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class CommandArgumentsTest {

    @Test
    public void gamemodeAcceptsNumbersShortNamesAndFullNames() {
        assertEquals(Optional.of(GameMode.CREATIVE), GamemodeCommand.parse("1"));
        assertEquals(Optional.of(GameMode.CREATIVE), GamemodeCommand.parse("c"));
        assertEquals(Optional.of(GameMode.SPECTATOR), GamemodeCommand.parse("sp"));
        assertEquals(Optional.of(GameMode.ADVENTURE), GamemodeCommand.parse("Adventure"));
        assertEquals(Optional.empty(), GamemodeCommand.parse("4"));
    }

    @Test
    public void speedOneIsVanillaAndTenIsTheClientMaximum() {
        assertEquals(0.2f, SpeedCommand.Kind.WALK.speed(1), 1e-6f);
        assertEquals(0.1f, SpeedCommand.Kind.FLY.speed(1), 1e-6f);
        assertEquals(1f, SpeedCommand.Kind.WALK.speed(SpeedCommand.MAX_LEVEL), 1e-6f);
        assertEquals(1f, SpeedCommand.Kind.FLY.speed(SpeedCommand.MAX_LEVEL), 1e-6f);
    }
}
