package net.weaver.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The boot-failure policy of {@link LoaderAgent}. */
class LoaderAgentFailureTest {

    @AfterEach
    void clear() {
        System.clearProperty(LoaderAgent.FAILURE_POLICY_PROPERTY);
    }

    @Test
    void aBootFailureIsFatalByDefaultAndReachesTheLog() {
        List<String> log = new ArrayList<>();
        IllegalStateException cause = new IllegalStateException("boom");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> LoaderAgent.onAgentFailure(cause, log::add));

        assertSame(cause, thrown);
        assertTrue(log.contains("[weaver] AGENT FAILED " + cause), String.join("\n", log));
        assertTrue(log.stream().anyMatch(line -> line.startsWith(
                "[weaver] AGENT FAILURE POLICY policy=fatal action=abort-jvm")), String.join("\n", log));
    }

    @Test
    void aCheckedFailureIsWrappedSoTheJvmStillAborts() {
        IOException cause = new IOException("disk");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> LoaderAgent.onAgentFailure(cause, line -> { }));

        assertSame(cause, thrown.getCause());
    }

    @Test
    void theWarnPolicyLogsAndContinues() {
        System.setProperty(LoaderAgent.FAILURE_POLICY_PROPERTY, "warn");
        List<String> log = new ArrayList<>();

        assertDoesNotThrow(() -> LoaderAgent.onAgentFailure(new IllegalStateException("boom"),
                log::add));

        assertTrue(log.stream().anyMatch(line -> line.startsWith("[weaver] AGENT FAILED ")),
                String.join("\n", log));
        assertTrue(log.contains("[weaver] AGENT FAILURE POLICY policy=warn"
                + " action=continue-without-weaver"), String.join("\n", log));
    }
}
