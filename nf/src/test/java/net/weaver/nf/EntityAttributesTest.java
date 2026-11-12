package net.weaver.nf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.weaver.api.ModContext;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.ModLifecycleEvent;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Attribute registrations wait for common setup: the vanilla attribute holders are
 * not bound while mods register, so a supplier built then cannot be made.
 *
 * <p>(The library's state is static and lives for the test JVM: the waiting case runs first.)</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EntityAttributesTest {

    @Test
    @Order(1)
    void registrationsWaitForCommonSetupThenRunAtOnce() {
        List<String> ran = new ArrayList<>();
        EntityAttributes.register(event -> ran.add("early"));
        assertEquals(List.of(), ran, "nothing runs before common setup");

        EventBus.GAME.post(new ModLifecycleEvent(context(), ModLifecycleEvent.Phase.REGISTER));
        assertEquals(List.of(), ran, "the register phase is too early");

        EventBus.GAME.post(new ModLifecycleEvent(context(), ModLifecycleEvent.Phase.COMMON_SETUP));
        assertEquals(List.of("early"), ran, "pending registrations run at common setup");

        EventBus.GAME.post(new ModLifecycleEvent(context(), ModLifecycleEvent.Phase.COMMON_SETUP));
        assertEquals(List.of("early"), ran, "each runs once, however many mods reach common setup");

        EntityAttributes.register(event -> ran.add("late"));
        assertEquals(List.of("early", "late"), ran, "a registration after common setup runs now");
    }

    @Test
    @Order(2)
    void aFailingRegistrationDoesNotStopTheOthers() {
        List<String> ran = new ArrayList<>();
        EventBus.GAME.post(new ModLifecycleEvent(context(), ModLifecycleEvent.Phase.COMMON_SETUP));
        EntityAttributes.register(event -> {
            throw new IllegalStateException("unbound value");
        });
        EntityAttributes.register(event -> ran.add("after"));
        assertEquals(List.of("after"), ran);
    }

    private static ModContext context() {
        return new ModContext() {
            @Override
            public String id() {
                return "nf_test";
            }

            @Override
            public String side() {
                return "server";
            }

            @Override
            public Path configDir() {
                return Path.of(".");
            }

            @Override
            public Path dataDir() {
                return Path.of(".");
            }

            @Override
            public void log(String message) {
            }
        };
    }
}
