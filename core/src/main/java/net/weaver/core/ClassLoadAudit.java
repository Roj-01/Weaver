package net.weaver.core;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reports which Minecraft classes are already loaded at a boot milestone.
 *
 * <p>The audit is intentionally diagnostic: it never loads a class and never
 * fails boot. The {@code before-all-transformers} snapshot must be empty; a
 * non-empty list identifies a class that can no longer receive a transformer.
 */
public final class ClassLoadAudit {

    private static final String GAME_PREFIX = "net.minecraft.";

    private ClassLoadAudit() {
    }

    /**
     * Returns the loaded Minecraft classes, sorted by binary name.
     *
     * @param instrumentation the agent instrumentation, possibly {@code null}
     * @return an immutable sorted list
     */
    public static List<String> gameClasses(Instrumentation instrumentation) {
        if (instrumentation == null) {
            return List.of();
        }
        return Arrays.stream(instrumentation.getAllLoadedClasses())
                .filter(type -> type != null && type.getName().startsWith(GAME_PREFIX))
                .map(Class::getName)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Logs one audit snapshot and returns the number of game classes found.
     *
     * @param instrumentation the agent instrumentation, possibly {@code null}
     * @param log             the diagnostic sink, not {@code null}
     * @param phase           the boot milestone name
     * @return the number of loaded game classes
     */
    public static int log(Instrumentation instrumentation, Consumer<String> log, String phase) {
        List<String> classes = gameClasses(instrumentation);
        log.accept("[weaver] CLASSLOAD AUDIT phase=" + phase
                + " gameClasses=" + classes.size());
        for (String name : classes) {
            log.accept("[weaver] CLASSLOAD AUDIT phase=" + phase + " class=" + name);
        }
        return classes.size();
    }
}
