package net.weaver.core.modops;

import net.weaver.core.modops.update.InstalledMod;
import net.weaver.core.modops.update.ModUpdate;
import net.weaver.core.modops.update.UpdateCheckRequest;
import net.weaver.core.modops.update.UpdateCheckResult;
import net.weaver.core.modops.update.UpdateChecks;
import net.weaver.core.modops.update.UpdateStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateChecksTest {

    @AfterEach
    void resetHolder() {
        UpdateChecks.reset();
    }

    @Test
    void defaultCheckIsNotConfigured() {
        UpdateChecks.reset();
        UpdateCheckResult result = UpdateChecks.check(request());
        assertEquals(UpdateStatus.NOT_CONFIGURED, result.status());
        assertTrue(result.updates().isEmpty());
        assertTrue(result.message().contains("no update check"), result.message());
    }

    @Test
    void installedCheckReceivesTheRequestAndReturnsUpdates() {
        AtomicReference<UpdateCheckRequest> seen = new AtomicReference<>();
        ModUpdate update = new ModUpdate("example", "1.0.0", "1.1.0",
                "https://example.invalid/example-1.1.0.jar", "notes");
        UpdateChecks.install(request -> {
            seen.set(request);
            return new UpdateCheckResult(UpdateStatus.UPDATE_AVAILABLE, List.of(update), "");
        });

        UpdateCheckRequest request = request();
        UpdateCheckResult result = UpdateChecks.check(request);

        assertEquals(request, seen.get());
        assertEquals(UpdateStatus.UPDATE_AVAILABLE, result.status());
        assertEquals(List.of(update), result.updates());
    }

    @Test
    void failingCheckBecomesError() {
        UpdateChecks.install(request -> {
            throw new IllegalStateException("offline");
        });
        UpdateCheckResult result = UpdateChecks.check(request());
        assertEquals(UpdateStatus.ERROR, result.status());
        assertNotNull(result.message());
        assertTrue(result.message().contains("offline"), result.message());
    }

    @Test
    void nullResultBecomesError() {
        UpdateChecks.install(request -> null);
        assertEquals(UpdateStatus.ERROR, UpdateChecks.check(request()).status());
    }

    @Test
    void installNullResetsToDefault() {
        UpdateChecks.install(request ->
                new UpdateCheckResult(UpdateStatus.UP_TO_DATE, List.of(), ""));
        UpdateChecks.install(null);
        assertEquals(UpdateStatus.NOT_CONFIGURED, UpdateChecks.check(request()).status());
    }

    private static UpdateCheckRequest request() {
        return new UpdateCheckRequest("2.2.0", "26.3", "client",
                List.of(new InstalledMod("example", "1.0.0")));
    }
}
