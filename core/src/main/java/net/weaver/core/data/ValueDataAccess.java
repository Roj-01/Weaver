package net.weaver.core.data;

import java.util.Collection;
import java.util.Map;

/**
 * Low-level byte access to Weaver capability data stored on a game value
 * carrier.
 *
 * <p>The game-free API passes carriers as {@link Object}. In 26.3 the
 * production implementation accepts {@code ValueOutput} for writes and
 * {@code ValueInput} for reads. Values live in one child keyed by
 * {@link HolderDataLimits#ROOT_KEY}; nothing outside that child is read,
 * written, or removed.</p>
 *
 * <p>Implementations are not required to be thread-safe.</p>
 */
public interface ValueDataAccess
{
    /**
     * Returns whether this implementation can write into the carrier.
     *
     * @param carrier candidate output carrier, possibly {@code null}
     * @return {@code true} only for a supported output carrier
     */
    boolean supportsOutput(Object carrier);

    /**
     * Returns whether this implementation can read from the carrier.
     *
     * @param carrier candidate input carrier, possibly {@code null}
     * @return {@code true} only for a supported input carrier
     */
    boolean supportsInput(Object carrier);

    /**
     * Writes all values into the Weaver child of an output carrier.
     *
     * <p>An empty map removes the Weaver child from the carrier. Every key and
     * value is checked against {@link HolderDataLimits} before any write.</p>
     *
     * @param output   supported output carrier
     * @param rootKey  key of the Weaver child
     * @param values   capability id to encoded value map, copied before writing
     * @throws net.weaver.api.capability.CapabilityException when the carrier is
     *         unsupported, the game classes cannot be reached, or a limit would
     *         be exceeded
     */
    void writeAll(Object output, String rootKey, Map<String, byte[]> values);

    /**
     * Reads the requested values from the Weaver child of an input carrier.
     *
     * <p>Missing keys and values that fail the per-value bound are skipped.
     * Unknown keys are not assumed to exist.</p>
     *
     * @param input   supported input carrier
     * @param rootKey key of the Weaver child
     * @param keys    capability ids to read
     * @return a mutable map of the values that were present and valid
     * @throws net.weaver.api.capability.CapabilityException when the carrier is
     *         unsupported or the game classes cannot be reached
     */
    Map<String, byte[]> readAll(Object input, String rootKey, Collection<String> keys);

    /**
     * Reads every value stored in the Weaver child, including keys that no
     * currently registered capability claims.
     *
     * <p>Values that fail the {@link HolderDataLimits} bounds are skipped as
     * in {@link #readAll(Object, String, Collection)}. Callers use the result
     * to carry data for capabilities that are not registered in this session
     * (or that fail to decode) through the next save unchanged.</p>
     *
     * <p>The default implementation cannot enumerate keys and returns
     * {@code null}; callers then fall back to
     * {@link #readAll(Object, String, Collection)}.</p>
     *
     * @param input   supported input carrier
     * @param rootKey key of the Weaver child
     * @return a mutable map of every present and valid value, or {@code null}
     *         when the carrier's keys cannot be enumerated
     * @throws net.weaver.api.capability.CapabilityException when the carrier is
     *         unsupported or the game classes cannot be reached
     */
    default Map<String, byte[]> readAllStored(Object input, String rootKey)
    {
        return null;
    }
}
