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

/** A mod's failure is logged with its summary line first, then its stack trace and causes. */
class LifecycleFailureLogTest {

    @TempDir
    Path temp;

    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        LifecycleHooks.reset();
        LifecycleHooks.install(List.of(), getClass().getClassLoader(), "client", false, temp.resolve("config"),
                temp.resolve("data"), log::add);
    }

    @AfterEach
    void tearDown() {
        LifecycleHooks.reset();
    }

    @Test
    void theSummaryLineStaysFirstAndTheCauseFollows() {
        IllegalStateException cause = new IllegalStateException("bound too large");
        RuntimeException failure = new RuntimeException("init", cause);

        LifecycleHooks.logFailure("[weaver] MOD INIT FAILED id=demo reason=" + failure, failure);

        assertEquals("[weaver] MOD INIT FAILED id=demo reason=java.lang.RuntimeException: init", log.get(0));
        assertTrue(log.stream().anyMatch(line -> line.contains("LifecycleFailureLogTest.theSummaryLineStaysFirstAndTheCauseFollows")),
                "the failing frame is in the log: " + log);
        assertTrue(log.stream().anyMatch(line -> line.contains("Caused by: java.lang.IllegalStateException: bound too large")),
                "the cause is in the log: " + log);
        assertTrue(log.stream().skip(1).allMatch(line -> line.startsWith("[weaver] ")), "every line is the loader's: " + log);
    }

    @Test
    void aVeryDeepTraceIsCutButItsCauseIsKept() {
        RuntimeException cause = new RuntimeException("the real reason");
        RuntimeException failure = new RuntimeException("deep", cause);
        StackTraceElement[] frames = new StackTraceElement[500];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new StackTraceElement("demo.Mod", "m" + i, "Mod.java", i);
        }
        failure.setStackTrace(frames);
        cause.setStackTrace(new StackTraceElement[] {new StackTraceElement("demo.Inner", "boom", "Inner.java", 7)});

        LifecycleHooks.logFailure("[weaver] MOD INIT FAILED id=demo reason=" + failure, failure);

        assertTrue(log.size() <= 22, "the summary, 16 frames, a count and the cause: " + log.size());
        assertTrue(log.contains("[weaver]     ... 484 more"), log.toString());
        assertTrue(log.contains("[weaver]   Caused by: java.lang.RuntimeException: the real reason"), log.toString());
        assertTrue(log.contains("[weaver]     at demo.Inner.boom(Inner.java:7)"), log.toString());
    }

    @Test
    void aCyclicCauseChainEnds() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);

        LifecycleHooks.logFailure("[weaver] MOD INIT FAILED id=demo reason=" + a, a);

        assertEquals(1, log.stream().filter(line -> line.contains("Caused by: java.lang.RuntimeException: b")).count());
    }
}
