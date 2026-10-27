package me.blockcraft.bedwars.map;

import org.bukkit.Material;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class EraTest {

    @Test
    public void theVoteTakesTheMapsOfItsEraAndTheOthersWhenItHasNone() {
        var current = ArenaMap.empty("meteor");
        var og = new ArenaMap("classic", "Classic", List.of(), Material.PAPER, Era.OG, Optional.empty(), Optional.empty(),
                List.of(), Map.of(), Map.of(), Map.of());

        assertEquals(List.of(og), Era.OG.pick(List.of(current, og)));
        assertEquals(List.of(current), Era.CURRENT.pick(List.of(current, og)));
        assertEquals(List.of(current), Era.OG.pick(List.of(current)));
    }
}
