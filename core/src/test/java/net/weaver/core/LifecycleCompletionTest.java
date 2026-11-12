package net.weaver.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When {@link LifecycleHooks} reports {@code LIFECYCLE COMPLETE}. */
class LifecycleCompletionTest {

    @TempDir
    Path temp;

    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        LifecycleHooks.reset();
    }

    @AfterEach
    void tearDown() {
        LifecycleHooks.reset();
    }

    @Test
    void aClientThatNeverStartsAnIntegratedServerCompletes() {
        install("client", true);

        LifecycleHooks.onClientSetup();
        LifecycleHooks.onClientTick();

        assertEquals(List.of("[weaver] LIFECYCLE COMPLETE expected-client=true expected-server=false"),
                completions());
        assertTrue(LifecycleHooks.serverAvailable(),
                "the client still hosts server-side hooks for a later integrated server");
    }

    @Test
    void aClientWaitsForAnIntegratedServerThatHasStarted() {
        install("client", true);

        LifecycleHooks.onClientSetup();
        LifecycleHooks.onServerSetup();
        LifecycleHooks.onClientTick();
        assertEquals(List.of(), completions());

        LifecycleHooks.onServerTick();
        assertEquals(List.of("[weaver] LIFECYCLE COMPLETE expected-client=true expected-server=true"),
                completions());
    }

    @Test
    void aClientDoesNotCompleteBeforeClientSetup() {
        install("client", true);

        LifecycleHooks.onClientTick();

        assertEquals(List.of(), completions());
    }

    @Test
    void aDedicatedServerWaitsForServerSetupAndTick() {
        install("server", false);

        LifecycleHooks.onServerTick();
        assertEquals(List.of(), completions());

        LifecycleHooks.onServerSetup();
        LifecycleHooks.onServerTick();
        assertEquals(List.of("[weaver] LIFECYCLE COMPLETE expected-client=false expected-server=true"),
                completions());
    }

    private void install(String side, boolean integratedServer) {
        LifecycleHooks.install(List.of(), getClass().getClassLoader(), side, integratedServer,
                temp.resolve("config"), temp.resolve("data"), log::add);
    }

    private List<String> completions() {
        return log.stream().filter(line -> line.contains("LIFECYCLE COMPLETE")).toList();
    }
}
