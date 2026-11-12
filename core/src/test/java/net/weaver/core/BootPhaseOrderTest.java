package net.weaver.core;

import net.weaver.core.capability.CapabilitySyncInstall;
import net.weaver.core.network.NetworkBridgeInstall;
import net.weaver.core.network.NetworkFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootPhaseOrderTest {

    @AfterEach
    void reset() {
        BootPhaseOrder.resetForTests();
        NetworkFeature.uninstall();
    }

    @Test
    void bridgeActivationBeforeTransformersIsRejected() {
        BootPhaseOrder.resetForTests();
        List<String> log = new ArrayList<>();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> NetworkBridgeInstall.activate(null, false, log::add));

        assertTrue(failure.getMessage().contains("before all class-file transformers"),
                failure.getMessage());
    }

    @Test
    void capabilitySyncBeforeTransformersIsRejected() {
        BootPhaseOrder.resetForTests();
        List<String> log = new ArrayList<>();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> CapabilitySyncInstall.install(null, log::add));

        assertTrue(failure.getMessage().contains("before all class-file transformers"),
                failure.getMessage());
    }

    @Test
    void installingTheLateTransformersMarksThePhase() {
        BootPhaseOrder.resetForTests();
        List<String> log = new ArrayList<>();

        LoaderAgent.installLateTransformers(fakeInstrumentation(),
                ClassLoader.getSystemClassLoader(), "client", log::add);

        assertTrue(BootPhaseOrder.transformersInstalled());
        assertTrue(log.stream().anyMatch(line -> line.contains("TRANSFORMERS INSTALLED")),
                String.join("\n", log));
    }

    @Test
    void bridgeActivationAfterTransformersReachesTheNormalFallback() {
        BootPhaseOrder.markTransformersInstalled();
        List<String> log = new ArrayList<>();

        assertDoesNotThrow(() -> {
            boolean installed = NetworkBridgeInstall.activate(null, false, log::add);
            assertFalse(installed);
        });
        assertTrue(log.stream().anyMatch(line -> line.contains("no-game-loader")),
                String.join("\n", log));
    }

    private static Instrumentation fakeInstrumentation() {
        return (Instrumentation) Proxy.newProxyInstance(
                BootPhaseOrderTest.class.getClassLoader(),
                new Class<?>[]{Instrumentation.class},
                (proxy, method, args) -> {
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }
}
