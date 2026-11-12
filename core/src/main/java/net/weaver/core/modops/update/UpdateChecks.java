package net.weaver.core.modops.update;

import java.util.List;
import java.util.Objects;

/**
 * Process-wide holder for the configured {@link UpdateCheck}.
 *
 * <p>The default check is a no-op that returns
 * {@link UpdateStatus#NOT_CONFIGURED}. This class intentionally contains no
 * network implementation and opens no sockets; a future HTTP-backed check can
 * be installed by the launcher or UI layer.
 *
 * <p>This Javadoc defines the eventual HTTP contract so implementations agree
 * on field names. Request body:
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "loaderApiVersion": "2.1.0",
 *   "gameVersion": "26.3",
 *   "side": "client",
 *   "installed": [
 *     {"id": "example", "version": "1.0.0"}
 *   ]
 * }
 * </pre>
 * Response body:
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "status": "UPDATE_AVAILABLE",
 *   "message": "",
 *   "updates": [
 *     {
 *       "id": "example",
 *       "installedVersion": "1.0.0",
 *       "availableVersion": "1.1.0",
 *       "downloadUrl": "https://example.invalid/example-1.1.0.jar",
 *       "notes": "release notes"
 *     }
 *   ]
 * }
 * </pre>
 * {@code status} is one of the {@link UpdateStatus} names. Unknown response
 * statuses should be mapped to {@link UpdateStatus#UNKNOWN}; transport or
 * parsing failures should be mapped to {@link UpdateStatus#ERROR}.
 */
public final class UpdateChecks {

    private static final UpdateCheck NOT_CONFIGURED = request -> new UpdateCheckResult(
            UpdateStatus.NOT_CONFIGURED, List.of(), "no update check installed");

    private static volatile UpdateCheck installed = NOT_CONFIGURED;

    private UpdateChecks() {
    }

    /**
     * Installs an update check for subsequent {@link #check(UpdateCheckRequest)}
     * calls.
     *
     * @param check check to install; {@code null} resets to the no-op default
     */
    public static void install(UpdateCheck check) {
        installed = check == null ? NOT_CONFIGURED : check;
    }

    /**
     * Resets to the no-op check that returns {@link UpdateStatus#NOT_CONFIGURED}.
     */
    public static void reset() {
        installed = NOT_CONFIGURED;
    }

    /**
     * Runs the configured check.
     *
     * <p>A {@code null} result or a thrown {@link RuntimeException} is converted
     * to an {@link UpdateStatus#ERROR} result so callers never observe a broken
     * update source as an exception.
     *
     * @param request immutable request
     * @return the check result, never {@code null}
     * @throws NullPointerException if {@code request} is {@code null}
     */
    public static UpdateCheckResult check(UpdateCheckRequest request) {
        Objects.requireNonNull(request, "request");
        UpdateCheck check = installed;
        try {
            UpdateCheckResult result = check.check(request);
            if (result == null) {
                return new UpdateCheckResult(UpdateStatus.ERROR, List.of(),
                        "update check returned null");
            }
            return result;
        } catch (RuntimeException failure) {
            return new UpdateCheckResult(UpdateStatus.ERROR, List.of(),
                    "update check failed: " + failure);
        }
    }
}
