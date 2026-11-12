package net.weaver.core.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectiveItemDataAccessTest
{
    private final ReflectiveItemDataAccess access = new ReflectiveItemDataAccess();

    @Test
    void supportsNullIsFalse()
    {
        assertFalse(access.supports(null));
    }

    @Test
    void plainObjectHolderIsUnsupported()
    {
        Object holder = new Object();

        assertFalse(access.supports(holder));

        ItemDataException read = assertThrows(ItemDataException.class,
                () -> access.read(holder, "weaver:test"));
        assertTrue(read.getMessage().contains("ItemStack"), read.getMessage());

        ItemDataException write = assertThrows(ItemDataException.class,
                () -> access.write(holder, "weaver:test", new byte[] {1}));
        assertTrue(write.getMessage().contains("ItemStack"), write.getMessage());

        ItemDataException clear = assertThrows(ItemDataException.class,
                () -> access.clear(holder));
        assertTrue(clear.getMessage().contains("ItemStack"), clear.getMessage());
    }

    @Test
    void limitsAreSane()
    {
        assertTrue(ItemDataLimits.MAX_KEY_CHARS > 0);
        assertTrue(ItemDataLimits.MAX_VALUE_BYTES > 0);
        assertTrue(ItemDataLimits.MAX_ENTRIES > 0);
        assertTrue(ItemDataLimits.MAX_TOTAL_BYTES > 0);
        assertTrue(ItemDataLimits.MAX_TOTAL_BYTES >= ItemDataLimits.MAX_VALUE_BYTES);
        assertEquals("weaver:capabilities", ItemDataLimits.ROOT_KEY);
        assertFalse(ItemDataLimits.ROOT_KEY.isBlank());
    }

    @Test
    void exceptionConstructorsCarryMessageAndCause()
    {
        ItemDataException plain = new ItemDataException("plain failure");
        assertEquals("plain failure", plain.getMessage());
        assertNull(plain.getCause());

        IOException cause = new IOException("root cause");
        ItemDataException wrapped = new ItemDataException("wrapped failure", cause);
        assertEquals("wrapped failure", wrapped.getMessage());
        assertSame(cause, wrapped.getCause());
    }

    @Test
    void interfaceDefaultTotalBytesIsUnknown()
    {
        ItemDataAccess stub = new ItemDataAccess()
        {
            @Override
            public boolean supports(Object holder)
            {
                return false;
            }

            @Override
            public boolean isEmpty(Object holder)
            {
                return true;
            }

            @Override
            public byte[] read(Object holder, String key)
            {
                return null;
            }

            @Override
            public boolean contains(Object holder, String key)
            {
                return false;
            }

            @Override
            public void write(Object holder, String key, byte[] data)
            {
            }

            @Override
            public byte[] remove(Object holder, String key)
            {
                return null;
            }

            @Override
            public void clear(Object holder)
            {
            }

            @Override
            public int entryCount(Object holder)
            {
                return 0;
            }
        };

        assertEquals(-1, stub.totalBytes(new Object()));
    }
}
