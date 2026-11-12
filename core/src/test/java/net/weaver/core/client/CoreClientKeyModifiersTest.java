package net.weaver.core.client;

import net.weaver.api.client.ClientException;
import net.weaver.api.client.KeyMappingSpec;
import net.weaver.api.client.KeyModifiers;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.ClientTickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreClientKeyModifiersTest
{
    private CoreClientBackend backend;

    @BeforeEach
    void resetGlobals()
    {
        CoreClientBackend.resetInstalledForTests();
    }

    @AfterEach
    void cleanup()
    {
        if (backend != null)
        {
            backend.uninstall();
            backend = null;
        }
        CoreClientBackend.resetInstalledForTests();
    }

    @Test
    void clickIsConsumedButActionIsSkippedWhenRequiredModifierIsMissing()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        adapter.consumeClickResult = true;
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        AtomicInteger runs = new AtomicInteger();
        KeyMappingSpec spec = KeyMappingSpec.builder("demo", "shift_action", 71)
                .modifiers(KeyModifiers.SHIFT)
                .build();
        backend.registerKeyMapping(spec, runs::incrementAndGet);

        EventBus.GAME.post(new ClientTickEvent.Pre(1L));

        assertEquals(0, runs.get());
        assertEquals(1, adapter.consumeClickCalls);

        adapter.currentModifiers = KeyModifiers.SHIFT;
        EventBus.GAME.post(new ClientTickEvent.Pre(2L));

        assertEquals(1, runs.get());
        assertEquals(2, adapter.consumeClickCalls);
    }

    @Test
    void actionRunsWhenAllRequiredModifiersAreHeld()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        adapter.consumeClickResult = true;
        adapter.currentModifiers = KeyModifiers.CONTROL | KeyModifiers.ALT;
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        AtomicInteger runs = new AtomicInteger();
        KeyMappingSpec spec = KeyMappingSpec.builder("demo", "combo", 72)
                .modifiers(KeyModifiers.CONTROL | KeyModifiers.ALT)
                .build();
        backend.registerKeyMapping(spec, runs::incrementAndGet);

        EventBus.GAME.post(new ClientTickEvent.Pre(1L));

        assertEquals(1, runs.get());
    }

    @Test
    void unmodifiedMappingDoesNotReadTheModifierState()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        adapter.consumeClickResult = true;
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        AtomicInteger runs = new AtomicInteger();
        backend.registerKeyMapping(KeyMappingSpec.key("demo", "plain", 73),
                runs::incrementAndGet);

        EventBus.GAME.post(new ClientTickEvent.Pre(1L));

        assertEquals(1, runs.get());
        assertEquals(1, adapter.consumeClickCalls);
    }

    @Test
    void duplicateKeyMappingStillRejectsTheSameId()
    {
        FakeClientGameAdapter adapter = new FakeClientGameAdapter();
        backend = new CoreClientBackend(adapter, true);
        backend.install();
        KeyMappingSpec spec = KeyMappingSpec.key("demo", "same", 74);
        backend.registerKeyMapping(spec, () ->
        {
        });

        assertThrows(ClientException.class, () -> backend.registerKeyMapping(spec, () ->
        {
        }));
    }
}
