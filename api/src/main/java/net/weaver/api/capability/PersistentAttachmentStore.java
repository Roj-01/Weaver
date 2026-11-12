package net.weaver.api.capability;

/**
 * An {@link AttachmentStore} that can persist its values into a game carrier.
 *
 * <p>The carrier is a game-free {@link Object} supplied by the loader or the
 * game layer. Weaver core passes the game's {@code ValueOutput} and
 * {@code ValueInput} objects, but the API never names a game type. A store
 * implementation decides how to encode its values into that carrier and how to
 * read them back.</p>
 *
 * <p>{@link #saveTo(Object, Object)} and {@link #loadFrom(Object, Object)} must
 * isolate failures per capability: a broken codec or malformed payload is
 * logged and skipped so a save or load never fails because of one value.
 * {@link #supportsHolder(Object)} is the lightweight type check used by the
 * loader before it asks the store to persist anything.</p>
 */
public interface PersistentAttachmentStore extends AttachmentStore {

    /**
     * Tests whether this store persists values for a holder.
     *
     * <p>This is a type check only; it does not require an explicit value to be
     * present.
     *
     * @param holder the holder to test
     * @return {@code true} when this store owns values for the holder type
     */
    boolean supportsHolder(Object holder);

    /**
     * Writes every registered, codec-backed explicit value of a holder into the
     * supplied output carrier.
     *
     * <p>Capabilities without a codec and capabilities without an explicit
     * value are skipped. Encoding and bound failures for one capability are
     * isolated and must not prevent the remaining values from being written.
     *
     * @param holder the holder whose values are written
     * @param output the game carrier that receives the values
     */
    void saveTo(Object holder, Object output);

    /**
     * Reads persisted values from the supplied input carrier and applies them
     * to a holder.
     *
     * <p>Only registered, codec-backed capabilities are read. Unknown ids are
     * ignored. Decoding, bound, and type failures for one capability are
     * isolated and must not prevent the remaining values from being restored.
     *
     * @param holder the holder that receives the values
     * @param input  the game carrier that supplies the values
     */
    void loadFrom(Object holder, Object input);

    /**
     * Whether {@link #saveTo} could write anything for {@code holder}: an
     * explicit value or retained bytes. Returning {@code true} is always safe;
     * returning {@code false} lets callers skip building a carrier.
     *
     * @param holder the holder to test
     * @return {@code false} only when the holder has nothing to save
     */
    default boolean hasData(Object holder) {
        return true;
    }
}
