package me.blockcraft.system.welcome;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class GreetersTest {

    @Test
    public void greetersBeyondTheListedOnesBecomeACount() {
        assertEquals(new Greeters(List.of("Lisa", "Tom"), 3), Greeters.of(List.of("Lisa", "Tom", "Kevos", "Mia", "Ben"), 2));
    }

    @Test
    public void aSingleGreeterLeftOverIsNamedInstead() {
        assertEquals(new Greeters(List.of("Lisa", "Tom", "Kevos"), 0), Greeters.of(List.of("Lisa", "Tom", "Kevos"), 2));
        assertEquals(new Greeters(List.of("Lisa"), 0), Greeters.of(List.of("Lisa"), 2));
    }
}
