package net.weaver.mixin.core;

/**
 * Thread-scoped bridge between the upstream audit trail and one transform call.
 *
 * <p>The Mixin engine reports {@code onApply}/{@code onPostProcess} with no target context, so the
 * engine installs a recorder on the transforming thread and reads it back when the woven bytes are
 * returned. Outside a transform every callback is a no-op.
 */
public final class Audit {

    /** Per-transform event sink. */
    public interface Recorder {
        /** Upstream signature: {@code onApply(targetName, "<configResource>:<mixinClass>")}. */
        void apply(String targetName, String mixinRef);

        void postProcess(String className);

        void generate(String configName, String className);
    }

    private static final ThreadLocal<Recorder> CURRENT = new ThreadLocal<Recorder>();

    private Audit() {
    }

    public static Recorder begin(Recorder recorder) {
        Recorder previous = CURRENT.get();
        CURRENT.set(recorder);
        return previous;
    }

    public static void end(Recorder previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    public static void onApply(String targetName, String mixinRef) {
        Recorder recorder = CURRENT.get();
        if (recorder != null) {
            recorder.apply(targetName, mixinRef);
        }
    }

    public static void onPostProcess(String className) {
        Recorder recorder = CURRENT.get();
        if (recorder != null) {
            recorder.postProcess(className);
        }
    }

    public static void onGenerate(String configName, String className) {
        Recorder recorder = CURRENT.get();
        if (recorder != null) {
            recorder.generate(configName, className);
        }
    }
}
