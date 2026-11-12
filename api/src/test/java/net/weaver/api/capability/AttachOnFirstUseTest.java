package net.weaver.api.capability;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Attach rules compute a holder's first value once and store it. */
class AttachOnFirstUseTest
{
    static final class Machine
    {
        final String name;

        Machine(String name)
        {
            this.name = name;
        }
    }

    static final class Counter
    {
        int value;
    }

    @Test
    void computesOnceStoresAndReturnsTheSameObject()
    {
        AttachmentRegistry registry = new AttachmentRegistry();
        AttachmentType<Counter> energy = AttachmentType.runtime("test:energy", Counter.class);
        AtomicInteger calls = new AtomicInteger();
        registry.attach(energy, Machine.class, machine -> {
            calls.incrementAndGet();
            return new Counter();
        });
        Machine machine = new Machine("a");
        assertFalse(registry.has(machine, energy));
        Counter first = registry.get(machine, energy);
        first.value = 7;
        assertSame(first, registry.get(machine, energy));
        assertEquals(7, registry.get(machine, energy).value);
        assertTrue(registry.has(machine, energy));
        assertEquals(1, calls.get());
    }

    @Test
    void onlyMatchingHoldersAndFailuresFallBackToTheDefault()
    {
        AttachmentRegistry registry = new AttachmentRegistry();
        AttachmentType<String> label = AttachmentType.builder("test:label", String.class).defaultValue("none").build();
        registry.attach(label, Machine.class, machine -> {
            if (machine.name.equals("bad")) {
                throw new IllegalStateException("boom");
            }
            return "machine:" + machine.name;
        });
        assertEquals("machine:x", registry.get(new Machine("x"), label));
        assertEquals("none", registry.get("not a machine", label));
        assertEquals("none", registry.get(new Machine("bad"), label));
        assertTrue(registry.diagnostics().stream().anyMatch(line -> line.contains("attach failed")));
        assertEquals(1, registry.attacherCount(label));
        assertNull(registry.find(new Machine("y"), label).orElse(null));
    }
}
