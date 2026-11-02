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
        var og = new ArenaMap("classic", "Classic", List.of(), Material.PAPER, Era.OG, false, Optional.empty(), Optional.empty(),
                List.of(), Map.of(), Map.of(), Map.of());
        var staple = new ArenaMap("castle", "Castle", List.of(), Material.PAPER, Era.OG, true, Optional.empty(), Optional.empty(),
                List.of(), Map.of(), Map.of(), Map.of());

        assertEquals(List.of(og, staple), Era.OG.pick(List.of(current, og, staple)));
        assertEquals(List.of(current, staple), Era.CURRENT.pick(List.of(current, og, staple)));
        assertEquals(List.of(current), Era.OG.pick(List.of(current)));
    }
}
