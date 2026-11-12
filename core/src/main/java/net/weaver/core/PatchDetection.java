package net.weaver.core;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the optional patched-game artifact through the game loader.
 *
 * <p>The patched jars add {@code net.weaver.patched.PatchedGame} with the
 * {@code weaver$patchLevel()I} method. This class deliberately has no
 * compile-time dependency on that class: it resolves the name with
 * {@link Class#forName(String, boolean, ClassLoader)} and reflection so an
 * unpatched game still boots. Every failure is contained and reported through
 * {@link Result#reason()}; detection never throws.
 */
public final class PatchDetection {

    /** The patched marker class added by the patcher. */
    public static final String PATCHED_GAME_CLASS = "net.weaver.patched.PatchedGame";

    /** The patch-level method on {@link #PATCHED_GAME_CLASS}. */
    public static final String PATCH_LEVEL_METHOD = "weaver$patchLevel";

    /** The optional patch metadata resource. */
    public static final String PATCH_METADATA = "META-INF/weaver/patch.json";

    private static final int MAX_METADATA_BYTES = 8192;

    private static final Pattern PATCH_LEVEL_PATTERN =
            Pattern.compile("\"patchLevel\"\\s*:\\s*(\\d+)");

    private static final Pattern[] PATCH_SET_PATTERNS = {
        Pattern.compile("\"patchSet\"\\s*:\\s*\"([^\"]+)\""),
        Pattern.compile("\"patch-set\"\\s*:\\s*\"([^\"]+)\""),
        Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"")
    };

    private record Metadata(Integer level, String patchSet) {
        private static final Metadata EMPTY = new Metadata(null, null);
    }

    /** The classification of one patch probe. */
    public enum Status {
        /** The patched marker class is present and reported a level. */
        PATCHED,
        /** The patched marker class is absent. */
        UNPATCHED,
        /** The probe could not complete. */
        UNAVAILABLE
    }

    /**
     * The outcome of one patch probe.
     *
     * @param status   the classification
     * @param level    the reported patch level, or {@code 0}
     * @param reason   the failure description when {@link Status#UNAVAILABLE},
     *                 otherwise {@code null}
     * @param patchSet the optional {@code patch-set} identifier, or {@code null}
     */
    public record Result(Status status, int level, String reason, String patchSet) {

        /**
         * Validates the status.
         *
         * @throws IllegalArgumentException when {@code status} is
         *                                  {@code null}
         */
        public Result {
            if (status == null) {
                throw new IllegalArgumentException("status");
            }
        }

        /**
         * Reports whether the patched marker class was found and queried.
         *
         * @return {@code true} for {@link Status#PATCHED}
         */
        public boolean patched() {
            return status == Status.PATCHED;
        }

        /**
         * Reports whether the probe failed instead of classifying the artifact.
         *
         * @return {@code true} for {@link Status#UNAVAILABLE}
         */
        public boolean unavailable() {
            return status == Status.UNAVAILABLE;
        }
    }

    private PatchDetection() {
    }

    /**
     * Classifies one game loader.
     *
     * <p>A present marker class is queried with
     * {@code Class.forName(name, false, loader)} and the
     * {@code weaver$patchLevel()} method. A static probe is invoked directly;
     * an instance-form probe takes its level from the optional
     * {@code META-INF/weaver/patch.json} resource, which is also the source of
     * the patch-set id. A missing class is {@link Status#UNPATCHED}; a broken
     * probe is {@link Status#UNAVAILABLE} with a reason. Metadata read
     * failures never fail the classification.
     *
     * @param loader the game loader to inspect, or {@code null}
     * @return the classification; never {@code null}
     */
    public static Result classify(ClassLoader loader) {
        if (loader == null) {
            return new Result(Status.UNPATCHED, 0, null, null);
        }
        try {
            Class<?> type = Class.forName(PATCHED_GAME_CLASS, false, loader);
            Method method = type.getMethod(PATCH_LEVEL_METHOD);
            Metadata metadata = readMetadata(loader);
            int level;
            if (Modifier.isStatic(method.getModifiers())) {
                Object value = method.invoke(null);
                level = (Integer) value;
            } else if (metadata.level() != null) {
                level = metadata.level();
            } else {
                return new Result(Status.UNAVAILABLE, 0,
                        "patch level method is not static and " + PATCH_METADATA
                                + " has no patchLevel", metadata.patchSet());
            }
            return new Result(Status.PATCHED, level, null, metadata.patchSet());
        } catch (ClassNotFoundException absent) {
            return new Result(Status.UNPATCHED, 0, null, null);
        } catch (Throwable failure) {
            return new Result(Status.UNAVAILABLE, 0, describe(failure),
                    readMetadata(loader).patchSet());
        }
    }

    private static Metadata readMetadata(ClassLoader loader) {
        try (InputStream input = loader.getResourceAsStream(PATCH_METADATA)) {
            if (input == null) {
                return Metadata.EMPTY;
            }
            String text = new String(input.readNBytes(MAX_METADATA_BYTES), StandardCharsets.UTF_8);
            return new Metadata(parseLevel(text), parsePatchSet(text));
        } catch (Throwable ignored) {
            return Metadata.EMPTY;
        }
    }

    private static Integer parseLevel(String text) {
        Matcher matcher = PATCH_LEVEL_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException overflow) {
            return null;
        }
    }

    private static String parsePatchSet(String text) {
        for (Pattern pattern : PATCH_SET_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String describe(Throwable failure) {
        Throwable cause = failure;
        if (failure instanceof InvocationTargetException invocation
                && invocation.getCause() != null) {
            cause = invocation.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getName()
                : cause.getClass().getName() + ": " + message;
    }
}
