package me.blockcraft.core.welcome;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GreetingWordsTest {

    private static final GreetingWords WORDS = new GreetingWords(List.of("hallo", "hi", "moin", "grüß", "willkommen", "guten tag"));

    @Test
    public void aConfiguredWordInAnyCaseGreets() {
        assertTrue(WORDS.greets("HALLO zusammen"));
        assertTrue(WORDS.greets("na, Moin!"));
        assertTrue(WORDS.greets("herzlich willkommen auf dem Server"));
        assertTrue(WORDS.greets("Guten Tag allerseits"));
    }

    @Test
    public void aWordInsideAnotherIsNoGreeting() {
        assertFalse(WORDS.greets("Hilfe, ich brauche ein Schiff"));
        assertFalse(WORDS.greets("hi_there und moinsen"));
        assertFalse(WORDS.greets("wer kommt mit?"));
    }

    @Test
    public void umlautsCountHoweverTheyAreSpelled() {
        assertTrue(WORDS.greets("Grüß dich"));
        assertTrue(WORDS.greets("gruess euch"));
        assertTrue(WORDS.greets("GRUSS"));
        assertTrue(WORDS.greets("gruß gott"));
    }

    @Test
    public void stretchedLettersStillGreet() {
        assertTrue(WORDS.greets("hallooo"));
        assertTrue(WORDS.greets("hiii :)"));
        assertTrue(WORDS.greets("Wilkommen"));
    }
}
