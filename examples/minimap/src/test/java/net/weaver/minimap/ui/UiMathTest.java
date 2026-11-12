package net.weaver.minimap.ui;

import net.weaver.minimap.ui.theme.ScissorGuard;
import net.weaver.minimap.ui.theme.UiMath;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UiMathTest
{
    @Test
    void roundedHitTestRespectsCorners()
    {
        assertTrue(UiMath.hitRoundedRect(60, 30, 10, 10, 100, 40, 8));
        assertTrue(UiMath.hitRoundedRect(10.5D, 30, 10, 10, 100, 40, 8));
        assertFalse(UiMath.hitRoundedRect(9.5D, 30, 10, 10, 100, 40, 8));
        assertFalse(UiMath.hitRoundedRect(11, 11, 10, 10, 100, 40, 8));
        assertTrue(UiMath.hitRoundedRect(16, 16, 10, 10, 100, 40, 8));
        assertTrue(UiMath.hitRoundedRect(60, 30, 10, 10, 100, 40, 0));
        assertFalse(UiMath.hitRoundedRect(0, 0, 10, 10, 0, 40, 8));
    }

    @Test
    void gridHelpersSnapToEight()
    {
        assertEquals(8, UiMath.grid(11));
        assertEquals(16, UiMath.grid(12));
        assertEquals(16, UiMath.gridCeil(9));
        assertEquals(8, UiMath.gridFloor(15));
        assertEquals(0, UiMath.grid(3));
    }

    @Test
    void ellipsisClipsAndKeepsWidth()
    {
        String clipped = UiMath.ellipsize("abcdefghij", 6, String::length);
        assertEquals("abcde\u2026", clipped);
        assertEquals("abc", UiMath.ellipsize("abc", 6, String::length));
        assertEquals("", UiMath.ellipsize("abc", 0, String::length));
        assertEquals("", UiMath.ellipsize("", 5, String::length));
    }

    @Test
    void ellipsisNeverSplitsSurrogatePairs()
    {
        String value = "\uD83D\uDE00abc";
        String clipped = UiMath.ellipsize(value, 2, text ->
                text.codePointCount(0, text.length()));
        assertEquals("\uD83D\uDE00\u2026", clipped);
    }

    @Test
    void scissorGuardIsBalancedAndIdempotent()
    {
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        ScissorGuard scissor = new ScissorGuard(opens::incrementAndGet,
                closes::incrementAndGet);
        assertEquals(1, opens.get());
        assertEquals(0, closes.get());
        assertTrue(scissor.isOpen());
        scissor.close();
        scissor.close();
        assertEquals(1, closes.get());
        assertTrue(scissor.isClosed());
        assertFalse(scissor.isOpen());
    }

    @Test
    void insideUsesHalfOpenIntervals()
    {
        assertTrue(UiMath.inside(10, 10, 10, 10, 5, 5));
        assertFalse(UiMath.inside(15, 10, 10, 10, 5, 5));
    }
}
