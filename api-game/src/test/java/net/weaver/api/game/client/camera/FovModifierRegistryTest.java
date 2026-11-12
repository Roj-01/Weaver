package net.weaver.api.game.client.camera;

import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientRegistration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FovModifierRegistryTest
{
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        FovModifierRegistry.reset();
        calls.clear();
    }

    @AfterEach
    void tearDown()
    {
        FovModifierRegistry.reset();
    }

    @Test
    void noModifiersReturnTheVanillaValueUnchanged()
    {
        assertEquals(200.0F, FovModifierRegistry.apply(context(200.0F), 200.0F), 0.0001F);
    }

    @Test
    void factorsComposeMultiplicatively()
    {
        FovModifierRegistry.register(modifier("two", 0, 2.0F));
        FovModifierRegistry.register(modifier("half", 1, 0.5F));

        float result = FovModifierRegistry.apply(context(10.0F), 10.0F);

        assertEquals(10.0F, result, 0.0001F);
        assertEquals(List.of("two", "half"), calls);
    }

    @Test
    void resultIsClampedToSafeRange()
    {
        FovModifierRegistry.register(modifier("huge", 0, 1000.0F));

        assertEquals(179.0F, FovModifierRegistry.apply(context(70.0F), 70.0F), 0.0001F);
    }

    @Test
    void invalidFactorsAreIgnoredAndReportedOnce()
    {
        FovModifierRegistry.register(modifier("nan", 0, Float.NaN));
        FovModifierRegistry.register(modifier("good", 1, 2.0F));

        assertEquals(20.0F, FovModifierRegistry.apply(context(10.0F), 10.0F), 0.0001F);
        assertEquals(20.0F, FovModifierRegistry.apply(context(10.0F), 10.0F), 0.0001F);
    }

    @Test
    void unregisterRemovesOnlyThatModifier()
    {
        ClientRegistration first = FovModifierRegistry.register(modifier("first", 0, 2.0F));
        FovModifierRegistry.register(modifier("second", 0, 3.0F));

        first.unregister();

        assertFalse(first.isActive());
        assertEquals(30.0F, FovModifierRegistry.apply(context(10.0F), 10.0F), 0.0001F);
        assertEquals(1, FovModifierRegistry.listenerCount());
    }

    @Test
    void duplicateIdsAreRejected()
    {
        FovModifierRegistry.register(modifier("same", 0, 1.0F));

        assertThrows(ClientException.class, () -> FovModifierRegistry.register(
                modifier("same", 0, 1.0F)));
    }

    @Test
    void oneFailingModifierDoesNotStopLaterModifiers()
    {
        FovModifierRegistry.register(new FovModifier()
        {
            @Override
            public String id()
            {
                return "bad";
            }

            @Override
            public float fovMultiplier(FovContext context)
            {
                throw new IllegalStateException("boom");
            }
        });
        FovModifierRegistry.register(modifier("good", 0, 2.0F));

        assertEquals(20.0F, FovModifierRegistry.apply(context(10.0F), 10.0F), 0.0001F);
        assertEquals(List.of("good"), calls);
    }

    private FovContext context(float baseFov)
    {
        return new FovContext(null, null, 0.5F, baseFov);
    }

    private FovModifier modifier(String id, int order, float factor)
    {
        return new FovModifier()
        {
            @Override
            public String id()
            {
                return id;
            }

            @Override
            public int order()
            {
                return order;
            }

            @Override
            public float fovMultiplier(FovContext context)
            {
                calls.add(id);
                return factor;
            }
        };
    }
}
