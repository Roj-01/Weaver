package net.weaver.api.game.lookup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleEnergyHandlerTest {

    @Test
    void insertAndExtractRespectCapacityAndLimits() {
        SimpleEnergyHandler handler = new SimpleEnergyHandler(100, 30, 20, 0);
        assertEquals(30, handler.insert(50, false));
        assertEquals(30, handler.insert(50, false));
        assertEquals(30, handler.insert(50, false));
        assertEquals(10, handler.insert(50, false));
        assertEquals(0, handler.insert(50, false));
        assertEquals(100, handler.stored());
        assertEquals(20, handler.extract(50, false));
        assertEquals(80, handler.stored());
    }

    @Test
    void simulateChangesNothing() {
        int[] changes = new int[1];
        SimpleEnergyHandler handler = new SimpleEnergyHandler(100) {
            @Override
            protected void onChanged() {
                changes[0]++;
            }
        };
        assertEquals(60, handler.insert(60, true));
        assertEquals(0, handler.stored());
        assertEquals(0, handler.extract(10, true));
        assertEquals(0, changes[0]);
        handler.insert(60, false);
        handler.extract(10, false);
        handler.extract(0, false);
        assertEquals(2, changes[0]);
    }

    @Test
    void negativeAmountsMoveNothingAndLimitsAreChecked() {
        SimpleEnergyHandler handler = new SimpleEnergyHandler(100, 0, 100, 500);
        assertEquals(100, handler.stored());
        assertEquals(0, handler.insert(-5, false));
        assertEquals(0, handler.extract(-5, false));
        assertFalse(handler.canInsert());
        assertTrue(handler.canExtract());
        handler.setStored(-3);
        assertEquals(0, handler.stored());
        assertThrows(IllegalArgumentException.class, () -> new SimpleEnergyHandler(-1));
    }
}
