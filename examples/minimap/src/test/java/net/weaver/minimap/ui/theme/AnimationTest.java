package net.weaver.minimap.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnimationTest
{
    @Test
    void progressClampsBeforeAndAfterTheWindow()
    {
        Animation animation = new Animation(1_000L, 200L, 10.0D, 30.0D);
        assertEquals(0.0D, animation.progress(0L));
        assertEquals(10.0D, animation.value(0L));
        assertEquals(1.0D, animation.progress(5_000L));
        assertEquals(30.0D, animation.value(5_000L));
        assertFalse(animation.isDone(1_199L));
        assertTrue(animation.isDone(1_200L));
    }

    @Test
    void valueMovesMonotonicallyForCubicCurves()
    {
        Animation animation = new Animation(0L, 100L, 0.0D, 1.0D);
        double previous = -1.0D;
        for (int millis = 0; millis <= 100; millis += 5)
        {
            double value = animation.value(millis);
            assertTrue(value >= previous - 1.0E-9D);
            assertTrue(value >= 0.0D);
            assertTrue(value <= 1.0D);
            previous = value;
        }
    }

    @Test
    void resetRestartsTheAnimation()
    {
        Animation animation = new Animation(0L, 100L, 4.0D, 8.0D);
        animation.reset(500L);
        assertEquals(4.0D, animation.value(500L));
        assertEquals(8.0D, animation.value(600L));
    }

    @Test
    void easingEndpointsAreExact()
    {
        for (Easing easing : Easing.values())
        {
            assertEquals(0.0D, easing.apply(-1.0D));
            assertEquals(0.0D, easing.apply(0.0D));
            assertEquals(1.0D, easing.apply(1.0D));
            assertEquals(1.0D, easing.apply(4.0D));
        }
    }

    @Test
    void motionInterpolatesAndClamps()
    {
        Animation.Motion motion = new Animation.Motion(100L);
        motion.set(false, 0L);
        assertEquals(0.0D, motion.value(0L));
        motion.set(true, 0L);
        assertEquals(0.0D, motion.value(0L));
        assertEquals(1.0D, motion.value(100L));
        assertTrue(motion.target());
        motion.set(false, 1_000L);
        assertEquals(1.0D, motion.value(1_000L));
        assertEquals(0.0D, motion.value(5_000L));
    }

    @Test
    void springOutStaysNearTheAuthoredRange()
    {
        for (int step = 0; step <= 100; step++)
        {
            double value = Easing.SPRING_OUT.apply(step / 100.0D);
            assertTrue(value >= -0.001D, "spring underflow at " + step);
            assertTrue(value <= 1.02D, "spring overflow at " + step);
        }
    }
}
