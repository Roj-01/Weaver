package net.weaver.core.data;

import java.util.List;
import java.util.Map;
import net.weaver.api.capability.CapabilityException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectiveValueDataAccessTest
{
    private final ReflectiveValueDataAccess access = new ReflectiveValueDataAccess();

    @Test
    void supportsOnlyValueCarriers()
    {
        assertFalse(access.supportsOutput(null));
        assertFalse(access.supportsOutput(new Object()));
        assertFalse(access.supportsInput(null));
        assertFalse(access.supportsInput(new Object()));
    }

    @Test
    void unsupportedCarriersAreRejectedWithCapabilityMessages()
    {
        CapabilityException write = assertThrows(CapabilityException.class,
                () -> access.writeAll(new Object(), HolderDataLimits.ROOT_KEY,
                        Map.of("test:value", new byte[]{1})));
        assertTrue(write.getMessage().contains("ValueOutput"), write.getMessage());

        CapabilityException read = assertThrows(CapabilityException.class,
                () -> access.readAll(new Object(), HolderDataLimits.ROOT_KEY,
                        List.of("test:value")));
        assertTrue(read.getMessage().contains("ValueInput"), read.getMessage());
    }

    @Test
    void holderLimitsMatchTheItemBudget()
    {
        assertTrue(HolderDataLimits.MAX_KEY_CHARS > 0);
        assertTrue(HolderDataLimits.MAX_VALUE_BYTES > 0);
        assertTrue(HolderDataLimits.MAX_ENTRIES > 0);
        assertTrue(HolderDataLimits.MAX_TOTAL_BYTES >= HolderDataLimits.MAX_VALUE_BYTES);
        assertFalse(HolderDataLimits.ROOT_KEY.isBlank());
    }
}
