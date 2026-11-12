package net.weaver.core.client;

import net.weaver.api.client.ClientThreadException;
import net.weaver.api.client.KeyMappingSpec;

import java.util.ArrayList;
import java.util.List;

final class FakeClientGameAdapter implements ClientGameAdapter
{
    boolean available = true;
    boolean clientThread = true;
    boolean consumeClickResult;
    int currentModifiers;
    int consumeClickCalls;
    Object minecraft = new Object();
    Object currentScreen;
    int guiWidth = 320;
    int guiHeight = 240;
    int literalCalls;

    final List<KeyMappingSpec> createdKeyMappings = new ArrayList<>();
    final List<Object> removedKeyMappings = new ArrayList<>();
    final List<Object> openedScreens = new ArrayList<>();
    final List<String> assertedOperations = new ArrayList<>();

    @Override
    public boolean isAvailable()
    {
        return available;
    }

    @Override
    public boolean canDetectClientThread()
    {
        return available;
    }

    @Override
    public void assertClientThread(String operation)
    {
        assertedOperations.add(operation);
        if (!available || !clientThread)
        {
            throw new ClientThreadException("fake adapter is not on the client thread; operation="
                    + operation);
        }
    }

    @Override
    public Object minecraft()
    {
        return minecraft;
    }

    @Override
    public Object currentScreen()
    {
        return currentScreen;
    }

    @Override
    public Object createKeyMapping(KeyMappingSpec spec)
    {
        createdKeyMappings.add(spec);
        return new FakeKeyMapping(spec);
    }

    @Override
    public void removeKeyMapping(Object mapping)
    {
        removedKeyMappings.add(mapping);
    }

    @Override
    public boolean consumeClick(Object mapping)
    {
        consumeClickCalls++;
        return consumeClickResult;
    }

    @Override
    public int currentModifiers()
    {
        return currentModifiers;
    }

    @Override
    public void openScreen(Object screen)
    {
        openedScreens.add(screen);
    }

    @Override
    public int guiWidth(Object graphics)
    {
        return guiWidth;
    }

    @Override
    public int guiHeight(Object graphics)
    {
        return guiHeight;
    }

    @Override
    public Object literal(String text)
    {
        literalCalls++;
        return new FakeComponent(text);
    }

    static final class FakeKeyMapping
    {
        private final KeyMappingSpec spec;

        FakeKeyMapping(KeyMappingSpec spec)
        {
            this.spec = spec;
        }

        KeyMappingSpec spec()
        {
            return spec;
        }
    }

    record FakeComponent(String text)
    {
    }
}
