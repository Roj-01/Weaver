package net.weaver.core.modops.update;

/**
 * Strategy for checking whether installed mods have updates.
 *
 * <p>Contract:
 * <ul>
 *   <li>Implementations must be non-blocking, or explicitly document in their
 *       own class Javadoc that {@link #check(UpdateCheckRequest)} may block and
 *       on which thread it must be called.</li>
 *   <li>Implementations must never mutate mod state, the class path, the mod
 *       list, or files under the mods directory. The request is read-only and
 *       the result is the only output.</li>
 *   <li>{@link #check(UpdateCheckRequest)} should return a non-null result and
 *       must not throw for ordinary network or service failures; use
 *       {@link UpdateStatus#ERROR} instead.</li>
 * </ul>
 */
public interface UpdateCheck {

    /**
     * Checks for updates.
     *
     * @param request immutable request describing the running environment
     * @return the check result; implementations should return
     *         {@link UpdateStatus#ERROR} rather than throwing on failure
     */
    UpdateCheckResult check(UpdateCheckRequest request);
}
