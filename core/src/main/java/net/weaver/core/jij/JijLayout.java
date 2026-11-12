package net.weaver.core.jij;

/**
 * Constants for the Weaver jar-in-jar layout.
 *
 * <p>A mod jar may embed other mod jars below {@link #ROOT}. An optional
 * {@link #MANIFEST} lists the embedded jars, their ids, versions, and whether
 * they are optional. When the manifest is absent, the embedded jars are
 * discovered by scanning {@link #ROOT} and reading each nested jar's
 * {@code weaver.mod.json}.
 *
 * <p>The manifest schema version is {@value #SCHEMA_VERSION}; the root object
 * also carries {@code firstWins: true}, and the {@code jars} array holds
 * objects of the form
 * {@code {"file": "...", "id": "...", "version": "...", "optional": false}}.
 */
public final class JijLayout {

    /** The jar-in-jar manifest path. */
    public static final String MANIFEST = "META-INF/weaver/jij.json";

    /** The directory that holds nested mod jars; always ends with a slash. */
    public static final String ROOT = "META-INF/weaver/jars/";

    /** The manifest schema version understood by this implementation. */
    public static final int SCHEMA_VERSION = 1;

    private JijLayout() {
    }

    /**
     * Returns whether {@code entryName} is a file below {@link #ROOT}.
     *
     * @param entryName a jar entry name
     * @return {@code true} when the name is a non-directory entry under the root
     */
    public static boolean isNestedEntry(String entryName) {
        return entryName != null
                && entryName.startsWith(ROOT)
                && entryName.length() > ROOT.length()
                && !entryName.endsWith("/");
    }
}
