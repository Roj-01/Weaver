package net.weaver.core.client;

import net.weaver.api.client.KeyModifiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyModifiersTest
{
    @Test
    void noRequirementAlwaysMatches()
    {
        assertTrue(KeyModifiers.matches(KeyModifiers.NONE, 0));
        assertTrue(KeyModifiers.matches(KeyModifiers.NONE, KeyModifiers.SHIFT));
        assertTrue(KeyModifiers.matches(KeyModifiers.NONE,
                KeyModifiers.CONTROL | KeyModifiers.ALT | KeyModifiers.SUPER));
    }

    @Test
    void everyRequiredBitMustBeHeld()
    {
        assertTrue(KeyModifiers.matches(KeyModifiers.SHIFT, KeyModifiers.SHIFT));
        assertTrue(KeyModifiers.matches(KeyModifiers.SHIFT,
                KeyModifiers.SHIFT | KeyModifiers.CONTROL));
        assertTrue(KeyModifiers.matches(KeyModifiers.SHIFT | KeyModifiers.CONTROL,
                KeyModifiers.SHIFT | KeyModifiers.CONTROL | KeyModifiers.ALT));
        assertFalse(KeyModifiers.matches(KeyModifiers.SHIFT, KeyModifiers.CONTROL));
        assertFalse(KeyModifiers.matches(KeyModifiers.SHIFT | KeyModifiers.CONTROL,
                KeyModifiers.SHIFT));
    }

    @Test
    void extraHeldModifiersDoNotBlock()
    {
        assertTrue(KeyModifiers.matches(KeyModifiers.ALT,
                KeyModifiers.ALT | KeyModifiers.NUM_LOCK | KeyModifiers.CAPS_LOCK));
    }
}
