package net.weaver.api.game.extension;

/**
 * An answer that may defer to vanilla: {@code TRUE} and {@code FALSE} decide,
 * {@code DEFAULT} keeps vanilla's own answer.
 */
public enum TriState {
    TRUE,
    FALSE,
    DEFAULT;

    /** {@code TRUE} or {@code FALSE}. */
    public static TriState of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /** This answer, or {@code vanilla} when this is {@code DEFAULT}. */
    public boolean orElse(boolean vanilla) {
        return this == DEFAULT ? vanilla : this == TRUE;
    }
}
